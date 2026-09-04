package org.tavall.agentwebmcp.provider.job;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.tavall.agentwebmcp.operation.OperationExecution;
import org.tavall.agentwebmcp.operation.OperationExecutionStatus;
import org.tavall.agentwebmcp.operation.OperationInvoker;
import org.tavall.agentwebmcp.provider.ProviderException;
import org.tavall.agentwebmcp.provider.codex.CodexExecution;
import org.tavall.agentwebmcp.provider.codex.CodexCliProvider;
import org.tavall.agentwebmcp.provider.service.ServiceProvider;
import org.tavall.dependency.IDependencyAccess;
import org.tavall.dependency.annotations.DelegatesTo;
import org.tavall.internal.utils.concurrent.AsyncTask;
import org.tavall.scheduler.interfaces.ICustomScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

@DelegatesTo(JobProvider.class)
public final class LocalJobProvider implements JobProvider, IDependencyAccess {
    private static final Duration SHUTDOWN_WAIT = Duration.ofSeconds(2);

    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object stateLock = new Object();
    private final ConcurrentHashMap<String, ScheduledFuture<?>> scheduled = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ActiveRun> activeRuns = new ConcurrentHashMap<>();

    @Override
    public String providerName() {
        return "local-durable-jobs";
    }

    @Override
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        synchronized (stateLock) {
            for (JobRecord job : repository().list()) {
                if (job.state() == JobState.RUNNING) {
                    Instant now = Instant.now();
                    repository().update(job.id(), current -> copy(
                            current, JobState.FAILED, current.startedAt(), now, current.execution(), current.output(),
                            "Runtime stopped before job completed",
                            new JobLogEntry(now, "ERROR", "Recovered interrupted RUNNING job as failed"),
                            current.nextRunAt()
                    ));
                } else if (job.state() == JobState.SCHEDULED || job.state() == JobState.QUEUED) {
                    scheduleLocked(job);
                }
            }
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        List<ActiveRun> active;
        synchronized (stateLock) {
            for (ScheduledFuture<?> future : scheduled.values()) {
                scheduler().cancelTask(future);
            }
            scheduled.clear();
            active = List.copyOf(activeRuns.values());
            active.forEach(ActiveRun::cancel);
        }

        long deadlineNanos = System.nanoTime() + SHUTDOWN_WAIT.toNanos();
        for (ActiveRun run : active) {
            run.awaitUntil(deadlineNanos);
        }
    }

    @Override
    public List<JobSummary> listJobs(int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
        ensureStarted();
        return repository().list().stream()
                .sorted(Comparator.comparing(JobRecord::createdAt).reversed())
                .limit(limit)
                .map(LocalJobProvider::summary)
                .toList();
    }

    @Override
    public JobDetails inspectJob(String jobId) {
        ensureStarted();
        return details(repository().read(jobId));
    }

    @Override
    public JobLogSlice readLogs(String jobId, int lines, Optional<String> cursor) {
        if (lines < 1 || lines > 1000) {
            throw new IllegalArgumentException("lines must be between 1 and 1000");
        }
        ensureStarted();
        JobRecord job = repository().read(jobId);
        int start = cursor == null ? Math.max(0, job.logs().size() - lines) : cursor
                .filter(value -> !value.isBlank())
                .map(value -> parseCursor(value, job.logs().size()))
                .orElse(Math.max(0, job.logs().size() - lines));
        int end = Math.min(job.logs().size(), start + lines);
        return new JobLogSlice(job.id(), List.copyOf(job.logs().subList(start, end)), Integer.toString(end), lines);
    }

    @Override
    public JobSubmission submit(JobRequest request) {
        ensureStarted();
        if (closed.get()) {
            throw new ProviderException("JOB_PROVIDER_CLOSED", "Job provider is shutting down", 503);
        }
        String id = "job-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Instant now = Instant.now();
        Instant requested = request.runAt().orElse(now);
        Instant nextRun = requested.isBefore(now) ? now : requested;
        JobState state = nextRun.isAfter(now) ? JobState.SCHEDULED : JobState.QUEUED;
        JobRecord job = new JobRecord(
                id, request.targetId(), request.serviceId(), request.kind(), request.operationId(), request.prompt(), request.agentId(),
                state, now, nextRun, nextRun, null, null, request.repeatEverySeconds(), request.timeoutSeconds(), request.input().deepCopy(),
                null, "", "",
                List.of(new JobLogEntry(now, "INFO", state == JobState.SCHEDULED ? "Scheduled job" : "Queued job"))
        );
        synchronized (stateLock) {
            if (closed.get()) {
                throw new ProviderException("JOB_PROVIDER_CLOSED", "Job provider is shutting down", 503);
            }
            repository().write(job);
            scheduleLocked(job);
        }
        return submission(job);
    }

    @Override
    public JobDetails cancel(String jobId) {
        ensureStarted();
        synchronized (stateLock) {
            JobRecord current = repository().read(jobId);
            if (current.state() == JobState.RUNNING) {
                throw runningCancellationUnsupported();
            }
            if (terminal(current.state())) {
                return details(current);
            }
            if (current.state() != JobState.SCHEDULED && current.state() != JobState.QUEUED) {
                throw new ProviderException("JOB_NOT_CANCELLABLE", "Job is not in a cancellable state: " + current.state(), 409);
            }

            ScheduledFuture<?> future = scheduled.remove(jobId);
            if (future != null) {
                boolean cancelled = scheduler().cancelTask(future);
                if (!cancelled) {
                    scheduled.put(jobId, future);
                    JobRecord observed = repository().read(jobId);
                    if (observed.state() == JobState.RUNNING) {
                        throw runningCancellationUnsupported();
                    }
                    if (terminal(observed.state())) {
                        return details(observed);
                    }
                    throw new ProviderException("JOB_NOT_CANCELLABLE", "The scheduled task has already begun and cannot be cancelled safely", 409);
                }
            }

            Instant now = Instant.now();
            return details(repository().update(jobId, job -> copy(
                    job, JobState.CANCELLED, job.startedAt(), now, job.execution(), job.output(), "Cancelled by operator",
                    new JobLogEntry(now, "WARN", "Job cancelled before execution"), job.nextRunAt()
            )));
        }
    }

    private void scheduleLocked(JobRecord job) {
        if (closed.get()) {
            return;
        }
        long delayMillis = Math.max(0, Duration.between(Instant.now(), job.nextRunAt()).toMillis());
        ScheduledFuture<?> future = scheduler().runTaskLaterAsync(() -> run(job.id()), delayMillis);
        ScheduledFuture<?> replaced = scheduled.put(job.id(), future);
        if (replaced != null && replaced != future) {
            scheduler().cancelTask(replaced);
        }
    }

    private void run(String jobId) {
        JobRecord running;
        synchronized (stateLock) {
            ScheduledFuture<?> future = scheduled.remove(jobId);
            if (future != null) {
                scheduler().removeTask(future);
            }
            if (closed.get()) {
                return;
            }
            JobRecord current = repository().read(jobId);
            if (current.state() != JobState.SCHEDULED && current.state() != JobState.QUEUED) {
                return;
            }
            Instant now = Instant.now();
            running = repository().update(jobId, job -> copy(
                    job, JobState.RUNNING, now, null, job.execution(), job.output(), "",
                    new JobLogEntry(now, "INFO", "Job execution started"), job.nextRunAt()
            ));
        }

        RunOutcome outcome = executeBounded(running);
        synchronized (stateLock) {
            JobRecord current = repository().read(jobId);
            if (current.state() != JobState.RUNNING) {
                return;
            }
            if (closed.get()) {
                Instant now = Instant.now();
                repository().update(jobId, job -> copy(
                        job, JobState.FAILED, job.startedAt(), now, outcome.execution(), outcome.output(),
                        "Runtime shut down during job execution",
                        new JobLogEntry(now, "ERROR", "Job interrupted by runtime shutdown"),
                        job.nextRunAt()
                ));
                return;
            }
            completeRunLocked(current, outcome);
        }
    }

    private RunOutcome executeBounded(JobRecord running) {
        FutureTask<RunOutcome> future = new FutureTask<>(() -> invokeRun(running));
        Thread worker = AsyncTask.namingThreadFactory("agent-webmcp-job").newThread(future);
        ActiveRun activeRun = new ActiveRun(future, worker);

        synchronized (stateLock) {
            if (closed.get()) {
                return RunOutcome.failed("Runtime is shutting down");
            }
            activeRuns.put(running.id(), activeRun);
            worker.start();
        }

        try {
            return future.get(running.timeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            activeRun.cancel();
            return RunOutcome.timedOut("Execution exceeded " + running.timeoutSeconds() + " second timeout");
        } catch (CancellationException exception) {
            return RunOutcome.failed("Job execution was cancelled during shutdown");
        } catch (InterruptedException exception) {
            activeRun.cancel();
            Thread.currentThread().interrupt();
            return RunOutcome.failed("Job execution was interrupted");
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof ProviderException providerException) {
                if (providerException.code().contains("TIMEOUT")) {
                    return RunOutcome.timedOut(providerException.code() + ": " + providerException.getMessage());
                }
                return RunOutcome.failed(providerException.code() + ": " + providerException.getMessage());
            }
            String message = cause == null || cause.getMessage() == null ? "Job execution failed" : cause.getMessage();
            return RunOutcome.failed(message);
        } finally {
            activeRuns.remove(running.id(), activeRun);
        }
    }

    private RunOutcome invokeRun(JobRecord running) {
        services().inspectService(running.serviceId());
        if (running.kind() == JobKind.CODEX_PROMPT) {
            CodexExecution codexExecution = codex().executeServicePrompt(
                    services().inspectService(running.serviceId()),
                    running.prompt().orElseThrow(),
                    Duration.ofSeconds(running.timeoutSeconds())
            );
            return RunOutcome.succeeded(null, codexExecution.output());
        }

        ObjectNode input = running.input().deepCopy();
        input.put("serviceId", running.serviceId());
        if (!"local".equals(running.targetId())) {
            input.put("targetId", running.targetId());
        }
        OperationExecution execution = invoker().execute(running.operationId(), input);
        if (execution.status() != OperationExecutionStatus.SUCCESS) {
            String reason = execution.error() == null
                    ? "Operation failed"
                    : execution.error().code() + ": " + execution.error().message();
            return RunOutcome.failed(execution, reason);
        }
        return RunOutcome.succeeded(execution, "");
    }

    private void completeRunLocked(JobRecord current, RunOutcome outcome) {
        Instant now = Instant.now();
        if (current.repeatEverySeconds().isPresent()) {
            long cadence = current.repeatEverySeconds().orElseThrow();
            Instant next = nextCadence(current.nextRunAt(), cadence, now);
            String message = outcome.success()
                    ? "Run succeeded; next execution scheduled for " + next
                    : (outcome.timedOut() ? "Run timed out; next execution scheduled for " : "Run failed; next execution scheduled for ") + next;
            JobRecord rescheduled = repository().update(current.id(), job -> copy(
                    job, JobState.SCHEDULED, job.startedAt(), now, outcome.execution(), outcome.output(), outcome.failureReason(),
                    new JobLogEntry(now, outcome.success() ? "INFO" : "ERROR", message), next
            ));
            scheduleLocked(rescheduled);
            return;
        }

        JobState finalState = outcome.success() ? JobState.SUCCEEDED : outcome.timedOut() ? JobState.TIMED_OUT : JobState.FAILED;
        String message = outcome.success() ? "Job succeeded" : outcome.failureReason();
        repository().update(current.id(), job -> copy(
                job, finalState, job.startedAt(), now, outcome.execution(), outcome.output(), outcome.failureReason(),
                new JobLogEntry(now, outcome.success() ? "INFO" : "ERROR", message), job.nextRunAt()
        ));
    }

    private void ensureStarted() {
        if (!started.get()) {
            start();
        }
    }

    private static Instant nextCadence(Instant previous, long cadenceSeconds, Instant now) {
        Instant next = previous.plusSeconds(cadenceSeconds);
        while (!next.isAfter(now)) {
            next = next.plusSeconds(cadenceSeconds);
        }
        return next;
    }

    private static JobRecord copy(
            JobRecord job,
            JobState state,
            Instant started,
            Instant completed,
            OperationExecution execution,
            String output,
            String failure,
            JobLogEntry entry,
            Instant nextRunAt
    ) {
        List<JobLogEntry> logs = new ArrayList<>(job.logs());
        logs.add(entry);
        return new JobRecord(
                job.id(), job.targetId(), job.serviceId(), job.kind(), job.operationId(), job.prompt(), job.agentId(), state,
                job.createdAt(), job.scheduledFor(), nextRunAt, started, completed, job.repeatEverySeconds(), job.timeoutSeconds(),
                job.input(), execution, output, failure, List.copyOf(logs)
        );
    }

    private static JobSubmission submission(JobRecord job) {
        return new JobSubmission(
                job.id(), job.serviceId(), job.kind(), job.operationId(), job.agentId(), job.state(), job.nextRunAt(),
                job.repeatEverySeconds(), job.timeoutSeconds()
        );
    }

    private static JobSummary summary(JobRecord job) {
        return new JobSummary(
                job.id(), job.serviceId(), job.kind(), job.operationId(), job.agentId(), job.state(), job.createdAt(),
                job.nextRunAt(), job.completedAt(), job.repeatEverySeconds()
        );
    }

    private static JobDetails details(JobRecord job) {
        return new JobDetails(
                job.id(), job.targetId(), job.serviceId(), job.kind(), job.operationId(), job.prompt(), job.agentId(), job.state(),
                job.createdAt(), job.scheduledFor(), job.nextRunAt(), job.startedAt(), job.completedAt(), job.repeatEverySeconds(),
                job.timeoutSeconds(), job.input(), job.execution(), job.output(), job.failureReason()
        );
    }

    private static boolean terminal(JobState state) {
        return state == JobState.SUCCEEDED || state == JobState.FAILED || state == JobState.TIMED_OUT || state == JobState.CANCELLED;
    }

    private static int parseCursor(String value, int size) {
        try {
            int cursor = Integer.parseInt(value);
            if (cursor < 0 || cursor > size) {
                throw new IllegalArgumentException("cursor is outside the current job log range");
            }
            return cursor;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("cursor must be a numeric job log offset");
        }
    }

    private static ProviderException runningCancellationUnsupported() {
        return new ProviderException("JOB_NOT_CANCELLABLE", "Running jobs cannot be cancelled safely because Agent WebMCP does not expose an owned cancellable process handle for the complete operation", 409);
    }

    private JobRepository repository() { return getInstance(JobRepository.class); }
    private ICustomScheduler scheduler() { return getInstance(ICustomScheduler.class); }
    private CodexCliProvider codex() { return getInstance(CodexCliProvider.class); }
    private ServiceProvider services() { return getInstance(ServiceProvider.class); }
    private OperationInvoker invoker() { return getInstance(OperationInvoker.class); }

    private record ActiveRun(FutureTask<RunOutcome> future, Thread worker) {
        private void cancel() {
            future.cancel(true);
            worker.interrupt();
        }

        private void awaitUntil(long deadlineNanos) {
            long remaining = Math.max(0L, deadlineNanos - System.nanoTime());
            if (remaining == 0L || !worker.isAlive()) {
                return;
            }
            try {
                long millis = TimeUnit.NANOSECONDS.toMillis(remaining);
                int nanos = (int) (remaining - TimeUnit.MILLISECONDS.toNanos(millis));
                worker.join(millis, nanos);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private record RunOutcome(
            boolean success,
            boolean timedOut,
            OperationExecution execution,
            String output,
            String failureReason
    ) {
        private static RunOutcome succeeded(OperationExecution execution, String output) {
            return new RunOutcome(true, false, execution, output == null ? "" : output, "");
        }

        private static RunOutcome failed(String reason) {
            return failed(null, reason);
        }

        private static RunOutcome failed(OperationExecution execution, String reason) {
            return new RunOutcome(false, false, execution, "", reason == null ? "Job execution failed" : reason);
        }

        private static RunOutcome timedOut(String reason) {
            return new RunOutcome(false, true, null, "", reason);
        }
    }
}

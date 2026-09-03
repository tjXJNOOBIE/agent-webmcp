package org.tavall.agentwebmcp.provider.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.tavall.agentwebmcp.operation.OperationExecution;
import org.tavall.agentwebmcp.operation.OperationExecutionStatus;
import org.tavall.agentwebmcp.operation.OperationInvoker;
import org.tavall.dependency.IDependencyAccess;
import org.tavall.dependency.annotations.DelegatesTo;
import org.tavall.internal.utils.concurrent.AsyncTask;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

@DelegatesTo(JobProvider.class)
public final class LocalJobProvider implements JobProvider, IDependencyAccess {
    private static final Pattern JOB_ID = Pattern.compile("job-[a-f0-9]{12}");

    private final AtomicBoolean recovered = new AtomicBoolean();
    private final Object recoveryLock = new Object();

    @Override
    public String providerName() {
        return "local-durable-jobs";
    }

    @Override
    public List<JobSummary> listJobs(int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
        ensureRecovered();
        return jobRepository().list().stream()
                .sorted(Comparator.comparing(JobRecord::createdAt).reversed())
                .limit(limit)
                .map(LocalJobProvider::summary)
                .toList();
    }

    @Override
    public JobDetails inspectJob(String jobId) {
        ensureRecovered();
        return details(jobRepository().read(requireJobId(jobId)));
    }

    @Override
    public JobLogSlice readLogs(String jobId, int lines, Optional<String> cursor) {
        if (lines < 1 || lines > 1000) {
            throw new IllegalArgumentException("lines must be between 1 and 1000");
        }
        ensureRecovered();
        JobRecord job = jobRepository().read(requireJobId(jobId));
        List<JobLogEntry> logs = job.logs();
        int start = cursor.filter(value -> !value.isBlank())
                .map(value -> parseCursor(value, logs.size()))
                .orElse(Math.max(0, logs.size() - lines));
        int end = Math.min(logs.size(), start + lines);
        return new JobLogSlice(job.id(), List.copyOf(logs.subList(start, end)), Integer.toString(end), lines);
    }

    @Override
    public JobSubmission submit(String operationId, JsonNode input, Duration timeout, OperationInvoker operationInvoker) {
        Objects.requireNonNull(operationInvoker, "operationInvoker");
        if (operationId == null || operationId.isBlank()) {
            throw new IllegalArgumentException("operationId is required");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofMinutes(15)) > 0) {
            throw new IllegalArgumentException("timeout must be between 1 second and 15 minutes");
        }
        ensureRecovered();

        String jobId = "job-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Instant now = Instant.now();
        JobRecord queued = new JobRecord(
                jobId, operationId, JobState.QUEUED, now, null, null, Math.toIntExact(timeout.toSeconds()),
                input == null ? objectMapper().createObjectNode() : input.deepCopy(), null, null,
                List.of(new JobLogEntry(now, "INFO", "Queued operation " + operationId))
        );
        jobRepository().write(queued);
        AsyncTask.runAsync(() -> executeJob(jobId, operationInvoker));
        return new JobSubmission(jobId, operationId, JobState.QUEUED, queued.timeoutSeconds());
    }

    private void executeJob(String jobId, OperationInvoker operationInvoker) {
        JobRecord running = jobRepository().update(jobId, queued -> {
            Instant now = Instant.now();
            return transition(queued, JobState.RUNNING, now, null, null, null,
                    new JobLogEntry(now, "INFO", "Executing operation with timeout " + queued.timeoutSeconds() + "s"));
        });

        FutureTask<OperationExecution> future = new FutureTask<>(
                () -> operationInvoker.execute(running.operationId(), running.input())
        );
        Thread worker = AsyncTask.namingThreadFactory("agent-webmcp-job").newThread(future);
        worker.start();
        try {
            OperationExecution execution = future.get(running.timeoutSeconds(), TimeUnit.SECONDS);
            if (execution.status() == OperationExecutionStatus.SUCCESS) {
                finish(jobId, JobState.SUCCEEDED, execution, null, "INFO");
            } else {
                String reason = execution.error() == null
                        ? "Operation failed"
                        : execution.error().code() + ": " + execution.error().message();
                finish(jobId, JobState.FAILED, execution, reason, "ERROR");
            }
        } catch (TimeoutException exception) {
            future.cancel(true);
            finish(jobId, JobState.TIMED_OUT, null, "Operation exceeded " + running.timeoutSeconds() + " second timeout", "ERROR");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            finish(jobId, JobState.FAILED, null, "Job execution was interrupted", "ERROR");
        } catch (ExecutionException exception) {
            String message = exception.getCause() == null || exception.getCause().getMessage() == null
                    ? "Operation execution failed"
                    : exception.getCause().getMessage();
            finish(jobId, JobState.FAILED, null, message, "ERROR");
        }
    }

    private void finish(String jobId, JobState state, OperationExecution execution, String failureReason, String level) {
        jobRepository().update(jobId, current -> {
            Instant now = Instant.now();
            String message = switch (state) {
                case SUCCEEDED -> "Operation completed successfully";
                case TIMED_OUT -> failureReason;
                case FAILED -> failureReason == null ? "Operation failed" : failureReason;
                default -> state.name();
            };
            return transition(current, state, current.startedAt(), now, execution, failureReason,
                    new JobLogEntry(now, level, message));
        });
    }

    private void ensureRecovered() {
        if (recovered.get()) {
            return;
        }
        synchronized (recoveryLock) {
            if (!recovered.compareAndSet(false, true)) {
                return;
            }
            for (JobRecord job : jobRepository().list()) {
                if (job.state() == JobState.QUEUED || job.state() == JobState.RUNNING) {
                    jobRepository().update(job.id(), current -> {
                        Instant now = Instant.now();
                        return transition(current, JobState.FAILED, current.startedAt(), now, current.execution(),
                                "Runtime stopped before job completed",
                                new JobLogEntry(now, "ERROR", "Recovered interrupted job as failed"));
                    });
                }
            }
        }
    }

    private ObjectMapper objectMapper() {
        return getInstance(ObjectMapper.class);
    }

    private JobRepository jobRepository() {
        return getInstance(JobRepository.class);
    }

    private static JobRecord transition(
            JobRecord job,
            JobState state,
            Instant startedAt,
            Instant completedAt,
            OperationExecution execution,
            String failureReason,
            JobLogEntry log
    ) {
        List<JobLogEntry> logs = new ArrayList<>(job.logs());
        logs.add(log);
        return new JobRecord(job.id(), job.operationId(), state, job.createdAt(), startedAt, completedAt,
                job.timeoutSeconds(), job.input(), execution, failureReason, List.copyOf(logs));
    }

    private static JobSummary summary(JobRecord job) {
        return new JobSummary(job.id(), job.operationId(), job.state(), job.createdAt(), job.completedAt());
    }

    private static JobDetails details(JobRecord job) {
        return new JobDetails(job.id(), job.operationId(), job.state(), job.createdAt(), job.startedAt(), job.completedAt(),
                job.timeoutSeconds(), job.input(), job.execution(), job.failureReason());
    }

    private static String requireJobId(String jobId) {
        if (jobId == null || !JOB_ID.matcher(jobId).matches()) {
            throw new IllegalArgumentException("jobId is invalid");
        }
        return jobId;
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
}

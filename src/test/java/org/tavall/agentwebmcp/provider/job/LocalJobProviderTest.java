package org.tavall.agentwebmcp.provider.job;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tavall.agentwebmcp.AgentWebMcpRuntime;
import org.tavall.agentwebmcp.operation.OperationExecution;
import org.tavall.agentwebmcp.operation.OperationExecutionStatus;
import org.tavall.agentwebmcp.provider.ProviderException;
import org.tavall.agentwebmcp.support.FakeCodexCliProvider;
import org.tavall.agentwebmcp.support.FakeServiceProvider;
import org.tavall.scheduler.interfaces.ICustomScheduler;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class LocalJobProviderTest {
    @TempDir Path dataDirectory;

    @Test
    void executesImmediateDeterministicServiceJobAndPersistsLogs() throws Exception {
        FakeServiceProvider services = new FakeServiceProvider();
        String jobId;
        try (AgentWebMcpRuntime runtime = runtime(services, new FakeCodexCliProvider(), null)) {
            enroll(runtime);
            OperationExecution submission = executeJob(runtime, "{\"serviceId\":\"demo.service\",\"operationId\":\"service.restart\",\"input\":{},\"timeoutSeconds\":5,\"agentId\":\"agent:test\"}");
            assertEquals(OperationExecutionStatus.SUCCESS, submission.status());
            jobId = submission.output().path("jobId").asText();
            JobDetails completed = awaitTerminal(runtime.context().jobProvider(), jobId);
            assertEquals(JobState.SUCCEEDED, completed.state());
            assertEquals("service.restart", completed.operationId());
            assertEquals("demo.service", completed.serviceId());
            assertEquals(Optional.of("agent:test"), completed.agentId());
            assertEquals("restart", services.lastAction());
            Path jobsDirectory = dataDirectory.resolve("jobs");
            Path jobFile = jobsDirectory.resolve(jobId + ".json");
            assertTrue(Files.isRegularFile(jobFile));
            if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
                assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(jobsDirectory));
                assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(jobFile));
            }
            JobLogSlice logs = runtime.context().jobProvider().readLogs(jobId, 100, Optional.of("0"));
            assertTrue(logs.entries().stream().anyMatch(entry -> entry.message().contains("started")));
            assertTrue(logs.entries().stream().anyMatch(entry -> entry.message().contains("succeeded")));
        }
        try (AgentWebMcpRuntime reloaded = runtime(services, new FakeCodexCliProvider(), null)) {
            assertEquals(JobState.SUCCEEDED, reloaded.context().jobProvider().inspectJob(jobId).state());
        }
    }

    @Test
    void executesOneShotCodexPromptAsServiceBoundJob() throws Exception {
        FakeCodexCliProvider codex = new FakeCodexCliProvider();
        codex.setOutput("inspection complete");
        try (AgentWebMcpRuntime runtime = runtime(new FakeServiceProvider(), codex, null)) {
            enroll(runtime);
            OperationExecution submission = executeJob(runtime, "{\"serviceId\":\"demo.service\",\"prompt\":\"inspect only this service\",\"input\":{},\"timeoutSeconds\":5}");
            JobDetails completed = awaitTerminal(runtime.context().jobProvider(), submission.output().path("jobId").asText());
            assertEquals(JobState.SUCCEEDED, completed.state());
            assertEquals(JobKind.CODEX_PROMPT, completed.kind());
            assertEquals("inspection complete", completed.output());
            assertEquals("demo.service", codex.lastServiceId());
        }
    }

    @Test
    void futureJobSurvivesRuntimeRestartAndExecutes() throws Exception {
        FakeServiceProvider services = new FakeServiceProvider();
        String jobId;
        Instant runAt = Instant.now().plusMillis(700);
        AgentWebMcpRuntime first = runtime(services, new FakeCodexCliProvider(), null);
        enroll(first);
        OperationExecution submission = executeJob(first, "{\"serviceId\":\"demo.service\",\"operationId\":\"service.restart\",\"input\":{},\"runAt\":\"" + runAt + "\",\"timeoutSeconds\":5}");
        jobId = submission.output().path("jobId").asText();
        assertEquals(JobState.SCHEDULED, first.context().jobProvider().inspectJob(jobId).state());
        first.close();

        try (AgentWebMcpRuntime recovered = runtime(services, new FakeCodexCliProvider(), null)) {
            JobDetails completed = awaitTerminal(recovered.context().jobProvider(), jobId);
            assertEquals(JobState.SUCCEEDED, completed.state());
            assertEquals("restart", services.lastAction());
        }
    }

    @Test
    void recurringDeterministicJobReschedulesAfterSuccessfulRun() throws Exception {
        FakeServiceProvider services = new FakeServiceProvider();
        try (AgentWebMcpRuntime runtime = runtime(services, new FakeCodexCliProvider(), null)) {
            enroll(runtime);
            JobSubmission submission = runtime.context().jobProvider().submit(new JobRequest(
                    "local", "demo.service", JobKind.SERVICE_OPERATION, "service.restart",
                    JsonNodeFactory.instance.objectNode(), Optional.empty(), Optional.empty(), Optional.of(1L), 5, Optional.empty()));
            JobDetails rescheduled = awaitRescheduled(runtime.context().jobProvider(), submission.jobId());
            assertEquals(JobState.SCHEDULED, rescheduled.state());
            assertEquals(Optional.of(1L), rescheduled.repeatEverySeconds());
            assertNotNull(rescheduled.startedAt());
            assertTrue(runtime.context().jobProvider().readLogs(submission.jobId(), 100, Optional.of("0")).entries().stream()
                    .anyMatch(entry -> entry.message().contains("next execution scheduled")));
            assertEquals(JobState.CANCELLED, runtime.context().jobProvider().cancel(submission.jobId()).state());
        }
    }

    @Test
    void recoversInterruptedRunningRecordAsFailed() {
        FakeServiceProvider services = new FakeServiceProvider();
        String jobId = "job-0123456789ab";
        AgentWebMcpRuntime first = runtime(services, new FakeCodexCliProvider(), null);
        Instant created = Instant.now().minusSeconds(10);
        JobRepository repository = first.dependencyMap().getInstance(JobRepository.class);
        repository.write(new JobRecord(
                jobId, "local", "demo.service", JobKind.SERVICE_OPERATION, "service.restart", Optional.empty(), Optional.empty(),
                JobState.RUNNING, created, created, created, created.plusSeconds(1), null, Optional.empty(), 60,
                JsonNodeFactory.instance.objectNode(), null, "", "", List.of()));
        first.close();

        try (AgentWebMcpRuntime recovered = runtime(services, new FakeCodexCliProvider(), null)) {
            JobDetails details = recovered.context().jobProvider().inspectJob(jobId);
            assertEquals(JobState.FAILED, details.state());
            assertEquals("Runtime stopped before job completed", details.failureReason());
            assertTrue(recovered.context().jobProvider().readLogs(jobId, 20, Optional.of("0")).entries().stream()
                    .anyMatch(entry -> entry.message().contains("Recovered interrupted RUNNING job")));
        }
    }

    @Test
    void cancelsQueuedAndScheduledJobsBeforeExecution() {
        HoldingScheduler scheduler = new HoldingScheduler();
        try (AgentWebMcpRuntime runtime = runtime(new FakeServiceProvider(), new FakeCodexCliProvider(), scheduler)) {
            JobProvider jobs = runtime.context().jobProvider();
            JobSubmission queued = jobs.submit(request(Optional.empty(), Optional.empty(), 5));
            JobSubmission scheduled = jobs.submit(request(Optional.of(Instant.now().plusSeconds(60)), Optional.empty(), 5));
            assertEquals(JobState.QUEUED, jobs.inspectJob(queued.jobId()).state());
            assertEquals(JobState.SCHEDULED, jobs.inspectJob(scheduled.jobId()).state());
            assertEquals(JobState.CANCELLED, jobs.cancel(queued.jobId()).state());
            assertEquals(JobState.CANCELLED, jobs.cancel(scheduled.jobId()).state());
        }
    }

    @Test
    void refusesToLieAboutCancellationOnceJobIsRunning() throws Exception {
        FakeServiceProvider services = new FakeServiceProvider();
        services.setLifecycleDelayMillis(700);
        try (AgentWebMcpRuntime runtime = runtime(services, new FakeCodexCliProvider(), null)) {
            enroll(runtime);
            OperationExecution submission = executeJob(runtime, "{\"serviceId\":\"demo.service\",\"operationId\":\"service.restart\",\"input\":{},\"timeoutSeconds\":5}");
            String jobId = submission.output().path("jobId").asText();
            awaitState(runtime.context().jobProvider(), jobId, JobState.RUNNING);
            ProviderException refusal = assertThrows(ProviderException.class, () -> runtime.context().jobProvider().cancel(jobId));
            assertEquals("JOB_NOT_CANCELLABLE", refusal.code());
            assertEquals(JobState.SUCCEEDED, awaitTerminal(runtime.context().jobProvider(), jobId).state());
        }
    }

    @Test
    void runtimeCloseInterruptsAndWaitsForRunningWorker() throws Exception {
        FakeServiceProvider services = new FakeServiceProvider();
        services.setLifecycleDelayMillis(5_000);
        String jobId;
        AgentWebMcpRuntime runtime = runtime(services, new FakeCodexCliProvider(), null);
        enroll(runtime);
        OperationExecution submission = executeJob(runtime, "{\"serviceId\":\"demo.service\",\"operationId\":\"service.restart\",\"input\":{},\"timeoutSeconds\":30}");
        jobId = submission.output().path("jobId").asText();
        awaitState(runtime.context().jobProvider(), jobId, JobState.RUNNING);
        Thread.sleep(50);

        Instant started = Instant.now();
        runtime.close();
        assertTrue(Duration.between(started, Instant.now()).compareTo(Duration.ofSeconds(3)) < 0);

        try (AgentWebMcpRuntime recovered = runtime(services, new FakeCodexCliProvider(), null)) {
            JobDetails details = recovered.context().jobProvider().inspectJob(jobId);
            assertEquals(JobState.FAILED, details.state());
            String failureReason = details.failureReason().toLowerCase(java.util.Locale.ROOT);
            assertTrue(failureReason.contains("shutdown") || failureReason.contains("shut down") || failureReason.contains("stopped"));
        }
    }

    @Test
    void timesOutAndInterruptsBoundedServiceExecution() throws Exception {
        FakeServiceProvider services = new FakeServiceProvider();
        services.setLifecycleDelayMillis(5_000);
        try (AgentWebMcpRuntime runtime = runtime(services, new FakeCodexCliProvider(), null)) {
            enroll(runtime);
            OperationExecution submission = executeJob(runtime, "{\"serviceId\":\"demo.service\",\"operationId\":\"service.restart\",\"input\":{},\"timeoutSeconds\":1}");
            JobDetails completed = awaitTerminal(runtime.context().jobProvider(), submission.output().path("jobId").asText());
            assertEquals(JobState.TIMED_OUT, completed.state());
            assertTrue(completed.failureReason().contains("timeout"));
        }
    }

    private AgentWebMcpRuntime runtime(FakeServiceProvider services, FakeCodexCliProvider codex, ICustomScheduler scheduler) {
        AgentWebMcpRuntime.Builder builder = AgentWebMcpRuntime.builder().serviceProvider(services).codexCliProvider(codex).dataDirectory(dataDirectory);
        if (scheduler != null) builder.scheduler(scheduler);
        return builder.build();
    }

    private static JobRequest request(Optional<Instant> runAt, Optional<Long> repeat, int timeout) {
        return new JobRequest("local", "demo.service", JobKind.SERVICE_OPERATION, "service.restart",
                JsonNodeFactory.instance.objectNode(), Optional.empty(), runAt, repeat, timeout, Optional.empty());
    }

    private static void enroll(AgentWebMcpRuntime runtime) {
        OperationExecution enrollment = runtime.executor().execute("service.add", JsonNodeFactory.instance.objectNode().put("serviceId", "demo.service"));
        assertEquals(OperationExecutionStatus.SUCCESS, enrollment.status());
    }

    private static OperationExecution executeJob(AgentWebMcpRuntime runtime, String json) throws Exception {
        return runtime.executor().execute("job.execute", runtime.objectMapper().readTree(json));
    }

    private static JobDetails awaitTerminal(JobProvider provider, String jobId) throws Exception {
        for (int attempt = 0; attempt < 240; attempt++) {
            JobDetails details = provider.inspectJob(jobId);
            if (details.state() != JobState.QUEUED && details.state() != JobState.SCHEDULED && details.state() != JobState.RUNNING) return details;
            Thread.sleep(25);
        }
        throw new AssertionError("job did not reach terminal state");
    }

    private static JobDetails awaitRescheduled(JobProvider provider, String jobId) throws Exception {
        for (int attempt = 0; attempt < 240; attempt++) {
            JobDetails details = provider.inspectJob(jobId);
            if (details.state() == JobState.SCHEDULED && details.startedAt() != null) return details;
            Thread.sleep(25);
        }
        throw new AssertionError("recurring job did not reschedule");
    }

    private static void awaitState(JobProvider provider, String jobId, JobState expected) throws Exception {
        for (int attempt = 0; attempt < 160; attempt++) {
            if (provider.inspectJob(jobId).state() == expected) return;
            Thread.sleep(10);
        }
        throw new AssertionError("job did not reach state " + expected);
    }

    private static final class HoldingScheduler implements ICustomScheduler {
        @Override public ScheduledFuture<?> runTaskLaterAsync(Runnable task, long delayMs) { return new HeldFuture(delayMs); }
        @Override public boolean cancelTask(ScheduledFuture<?> task) { return task.cancel(false); }
    }

    private static final class HeldFuture implements ScheduledFuture<Object> {
        private final long delayMs;
        private volatile boolean cancelled;
        private HeldFuture(long delayMs) { this.delayMs = delayMs; }
        @Override public long getDelay(TimeUnit unit) { return unit.convert(delayMs, TimeUnit.MILLISECONDS); }
        @Override public int compareTo(Delayed other) { return Long.compare(getDelay(TimeUnit.MILLISECONDS), other.getDelay(TimeUnit.MILLISECONDS)); }
        @Override public boolean cancel(boolean mayInterruptIfRunning) { cancelled = true; return true; }
        @Override public boolean isCancelled() { return cancelled; }
        @Override public boolean isDone() { return cancelled; }
        @Override public Object get() { throw new UnsupportedOperationException(); }
        @Override public Object get(long timeout, TimeUnit unit) { throw new UnsupportedOperationException(); }
    }
}

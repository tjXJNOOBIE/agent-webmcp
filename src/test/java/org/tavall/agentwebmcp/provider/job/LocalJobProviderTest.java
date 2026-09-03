package org.tavall.agentwebmcp.provider.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tavall.agentwebmcp.operation.OperationExecution;
import org.tavall.agentwebmcp.operation.OperationExecutionStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalJobProviderTest {
    @TempDir
    Path dataDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void persistsCanonicalExecutionAndCursorBasedLogs() throws Exception {
        LocalJobProvider provider = provider();
        JobSubmission submission = provider.submit(
                "system.status",
                objectMapper.createObjectNode().put("probe", true),
                Duration.ofSeconds(2),
                (operationId, input) -> success(operationId, input)
        );

        JobDetails completed = awaitTerminal(provider, submission.jobId());
        assertEquals(JobState.SUCCEEDED, completed.state());
        assertEquals("system.status", completed.operationId());
        assertEquals("system.status", completed.execution().operationId());
        assertTrue(Files.isRegularFile(dataDirectory.resolve("jobs").resolve(submission.jobId() + ".json")));

        JobLogSlice first = provider.readLogs(submission.jobId(), 1, Optional.of("0"));
        assertEquals(1, first.entries().size());
        JobLogSlice continuation = provider.readLogs(submission.jobId(), 100, Optional.of(first.cursor()));
        assertFalse(continuation.entries().isEmpty());

        LocalJobProvider reloaded = provider();
        JobDetails durable = reloaded.inspectJob(submission.jobId());
        assertEquals(JobState.SUCCEEDED, durable.state());
        assertNotNull(durable.execution());
    }

    @Test
    void recoversInterruptedPersistedJobAsFailed() throws Exception {
        Path jobs = Files.createDirectories(dataDirectory.resolve("jobs"));
        String jobId = "job-0123456789ab";
        Instant createdAt = Instant.now().minusSeconds(10);
        Instant startedAt = Instant.now().minusSeconds(9);
        String persisted = """
                {
                  "id": "%s",
                  "operationId": "system.status",
                  "state": "RUNNING",
                  "createdAt": "%s",
                  "startedAt": "%s",
                  "completedAt": null,
                  "timeoutSeconds": 60,
                  "input": {},
                  "execution": null,
                  "failureReason": null,
                  "logs": []
                }
                """.formatted(jobId, createdAt, startedAt);
        Files.writeString(jobs.resolve(jobId + ".json"), persisted);

        JobDetails recovered = provider().inspectJob(jobId);

        assertEquals(JobState.FAILED, recovered.state());
        assertNotNull(recovered.completedAt());
        assertEquals("Runtime stopped before job completed", recovered.failureReason());
        JobLogSlice logs = provider().readLogs(jobId, 10, Optional.of("0"));
        assertTrue(logs.entries().stream().anyMatch(entry -> entry.message().contains("Recovered interrupted job")));
    }

    @Test
    void timesOutBoundedExecution() throws Exception {
        LocalJobProvider provider = provider();
        JobSubmission submission = provider.submit(
                "slow.operation",
                objectMapper.createObjectNode(),
                Duration.ofSeconds(1),
                (operationId, input) -> {
                    try {
                        Thread.sleep(Duration.ofSeconds(10));
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                    return success(operationId, input);
                }
        );

        JobDetails completed = awaitTerminal(provider, submission.jobId());
        assertEquals(JobState.TIMED_OUT, completed.state());
        assertTrue(completed.failureReason().contains("timeout"));
    }

    private LocalJobProvider provider() {
        return LocalJobProvider.builder().objectMapper(objectMapper).dataDirectory(dataDirectory).build();
    }

    private static JobDetails awaitTerminal(LocalJobProvider provider, String jobId) throws Exception {
        for (int attempt = 0; attempt < 120; attempt++) {
            JobDetails details = provider.inspectJob(jobId);
            if (details.state() != JobState.QUEUED && details.state() != JobState.RUNNING) {
                return details;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("job did not reach terminal state");
    }

    private static OperationExecution success(String operationId, com.fasterxml.jackson.databind.JsonNode input) {
        Instant now = Instant.now();
        return new OperationExecution(operationId, OperationExecutionStatus.SUCCESS, now, now, 0, input, null);
    }
}

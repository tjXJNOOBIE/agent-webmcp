package org.tavall.agentwebmcp.provider.job;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tavall.agentwebmcp.AgentWebMcpRuntime;
import org.tavall.agentwebmcp.operation.OperationExecution;
import org.tavall.agentwebmcp.operation.OperationExecutionStatus;
import org.tavall.agentwebmcp.support.FakeCodexCliProvider;
import org.tavall.agentwebmcp.support.FakeServiceProvider;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalJobProviderShutdownTest {
    @TempDir Path dataDirectory;

    @Test
    void shutdownInterruptsOwnedActiveWorkerAndLeavesRecoverableTerminalState() throws Exception {
        FakeServiceProvider services = new FakeServiceProvider();
        services.setLifecycleDelayMillis(5_000);
        String jobId;

        AgentWebMcpRuntime runtime = AgentWebMcpRuntime.builder()
                .serviceProvider(services)
                .codexCliProvider(new FakeCodexCliProvider())
                .dataDirectory(dataDirectory)
                .build();
        try {
            OperationExecution enrollment = runtime.executor().execute(
                    "service.add", runtime.objectMapper().createObjectNode().put("serviceId", "demo.service"));
            assertEquals(OperationExecutionStatus.SUCCESS, enrollment.status());

            OperationExecution submission = runtime.executor().execute("job.execute", runtime.objectMapper().readTree(
                    "{\"serviceId\":\"demo.service\",\"operationId\":\"service.restart\",\"input\":{},\"timeoutSeconds\":30}"));
            jobId = submission.output().path("jobId").asText();
            awaitRunning(runtime.context().jobProvider(), jobId);

            Instant shutdownStarted = Instant.now();
            runtime.close();
            assertTrue(Duration.between(shutdownStarted, Instant.now()).compareTo(Duration.ofSeconds(3)) < 0);
        } finally {
            runtime.close();
        }

        try (AgentWebMcpRuntime recovered = AgentWebMcpRuntime.builder()
                .serviceProvider(services)
                .codexCliProvider(new FakeCodexCliProvider())
                .dataDirectory(dataDirectory)
                .build()) {
            JobDetails details = recovered.context().jobProvider().inspectJob(jobId);
            assertEquals(JobState.FAILED, details.state());
            assertTrue(details.failureReason().contains("Runtime"));
        }
    }

    private static void awaitRunning(JobProvider provider, String jobId) throws Exception {
        for (int attempt = 0; attempt < 200; attempt++) {
            if (provider.inspectJob(jobId).state() == JobState.RUNNING) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("job did not reach RUNNING state");
    }
}

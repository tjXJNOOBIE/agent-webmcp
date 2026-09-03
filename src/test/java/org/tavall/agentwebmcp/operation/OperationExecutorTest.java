package org.tavall.agentwebmcp.operation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tavall.agentwebmcp.AgentWebMcpRuntime;
import org.tavall.agentwebmcp.provider.job.JobDetails;
import org.tavall.agentwebmcp.provider.job.JobState;
import org.tavall.agentwebmcp.provider.target.LocalTargetProvider;
import org.tavall.agentwebmcp.support.FakeCodexCliProvider;
import org.tavall.agentwebmcp.support.FakeServiceProvider;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class OperationExecutorTest {
    @TempDir Path dataDirectory;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void executesTypedMutatingOperationThroughProvider() throws Exception {
        FakeServiceProvider serviceProvider = new FakeServiceProvider();
        try (AgentWebMcpRuntime runtime = runtime(serviceProvider, new FakeCodexCliProvider())) {
            enrollDemoService(runtime);
            OperationExecution execution = runtime.executor().execute(
                    "service.restart", objectMapper.readTree("{\"serviceId\":\"demo.service\"}"));
            assertEquals(OperationExecutionStatus.SUCCESS, execution.status());
            assertEquals("restart", serviceProvider.lastAction());
            assertEquals("demo.service", serviceProvider.lastServiceId());
            assertEquals("restart", execution.output().get("action").asText());
        }
    }

    @Test
    void projectsServiceStatusAndSystemMetrics() throws Exception {
        try (AgentWebMcpRuntime runtime = runtime(new FakeServiceProvider(), new FakeCodexCliProvider())) {
            enrollDemoService(runtime);
            OperationExecution status = runtime.executor().execute(
                    "service.status", objectMapper.readTree("{\"serviceId\":\"demo.service\"}"));
            assertEquals(OperationExecutionStatus.SUCCESS, status.status());
            assertEquals("RUNNING", status.output().get("state").asText());
            OperationExecution metrics = runtime.executor().execute("metrics.snapshot", objectMapper.createObjectNode());
            assertEquals(OperationExecutionStatus.SUCCESS, metrics.status());
            assertEquals("local", metrics.output().get("targetId").asText());
            assertTrue(metrics.output().path("metrics").path("availableProcessors").asInt() >= 1);
        }
    }

    @Test
    void executesAllowedServiceOperationAsDurableJob() throws Exception {
        FakeServiceProvider services = new FakeServiceProvider();
        try (AgentWebMcpRuntime runtime = runtime(services, new FakeCodexCliProvider())) {
            enrollDemoService(runtime);
            OperationExecution submission = runtime.executor().execute(
                    "job.execute", objectMapper.readTree("{\"serviceId\":\"demo.service\",\"operationId\":\"service.restart\",\"input\":{},\"timeoutSeconds\":5}"));
            assertEquals(OperationExecutionStatus.SUCCESS, submission.status());
            JobDetails job = awaitTerminal(runtime, submission.output().get("jobId").asText());
            assertEquals(JobState.SUCCEEDED, job.state());
            assertEquals("service.restart", job.execution().operationId());
            assertEquals("restart", services.lastAction());
        }
    }

    @Test
    void rejectsNonLifecycleDeterministicJobOperation() throws Exception {
        try (AgentWebMcpRuntime runtime = runtime(new FakeServiceProvider(), new FakeCodexCliProvider())) {
            enrollDemoService(runtime);
            OperationExecution execution = runtime.executor().execute(
                    "job.execute", objectMapper.readTree("{\"serviceId\":\"demo.service\",\"operationId\":\"system.status\",\"input\":{}}"));
            assertEquals(OperationExecutionStatus.FAILURE, execution.status());
            assertEquals(400, execution.error().httpStatus());
            assertEquals("JOB_OPERATION_NOT_ALLOWED", execution.error().code());
        }
    }

    @Test
    void rejectsPromptAndOperationTogetherAndRecurringAi() throws Exception {
        try (AgentWebMcpRuntime runtime = runtime(new FakeServiceProvider(), new FakeCodexCliProvider())) {
            enrollDemoService(runtime);
            OperationExecution ambiguous = runtime.executor().execute("job.execute", objectMapper.readTree(
                    "{\"serviceId\":\"demo.service\",\"operationId\":\"service.restart\",\"prompt\":\"also use AI\",\"input\":{}}"));
            assertEquals("INVALID_INPUT", ambiguous.error().code());
            OperationExecution recurringAi = runtime.executor().execute("job.execute", objectMapper.readTree(
                    "{\"serviceId\":\"demo.service\",\"prompt\":\"inspect\",\"input\":{},\"repeatEverySeconds\":60}"));
            assertEquals("INVALID_INPUT", recurringAi.error().code());
        }
    }

    @Test
    void rejectsAiJobWhenCodexIsUnavailable() throws Exception {
        FakeCodexCliProvider codex = new FakeCodexCliProvider();
        codex.setAvailable(false);
        try (AgentWebMcpRuntime runtime = runtime(new FakeServiceProvider(), codex)) {
            enrollDemoService(runtime);
            OperationExecution execution = runtime.executor().execute("job.execute", objectMapper.readTree(
                    "{\"serviceId\":\"demo.service\",\"prompt\":\"inspect\",\"input\":{}}"));
            assertEquals(OperationExecutionStatus.FAILURE, execution.status());
            assertEquals("CODEX_UNAVAILABLE", execution.error().code());
        }
    }

    @Test
    void rejectsUnknownOperationAndInvalidTypedInput() {
        try (AgentWebMcpRuntime runtime = runtime(new FakeServiceProvider(), new FakeCodexCliProvider())) {
            OperationExecution missing = runtime.executor().execute("missing.operation", objectMapper.createObjectNode());
            assertEquals(404, missing.error().httpStatus());
            assertEquals("OPERATION_NOT_FOUND", missing.error().code());
            OperationExecution invalid = runtime.executor().execute("service.inspect", objectMapper.createObjectNode());
            assertEquals(400, invalid.error().httpStatus());
            assertEquals("INVALID_INPUT", invalid.error().code());
        }
    }

    private void enrollDemoService(AgentWebMcpRuntime runtime) {
        assertEquals(OperationExecutionStatus.SUCCESS, runtime.executor().execute(
                "service.add", objectMapper.createObjectNode().put("serviceId", "demo.service")).status());
    }

    private AgentWebMcpRuntime runtime(FakeServiceProvider serviceProvider, FakeCodexCliProvider codex) {
        return AgentWebMcpRuntime.builder().serviceProvider(serviceProvider).codexCliProvider(codex)
                .targetProvider(new LocalTargetProvider()).dataDirectory(dataDirectory).build();
    }

    private static JobDetails awaitTerminal(AgentWebMcpRuntime runtime, String jobId) throws Exception {
        for (int attempt = 0; attempt < 160; attempt++) {
            JobDetails job = runtime.context().jobProvider().inspectJob(jobId);
            if (job.state() != JobState.QUEUED && job.state() != JobState.SCHEDULED && job.state() != JobState.RUNNING) return job;
            Thread.sleep(25);
        }
        throw new AssertionError("job did not reach terminal state");
    }
}

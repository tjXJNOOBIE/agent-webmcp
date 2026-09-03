package org.tavall.agentwebmcp.operation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tavall.agentwebmcp.AgentWebMcpRuntime;
import org.tavall.agentwebmcp.provider.job.JobDetails;
import org.tavall.agentwebmcp.provider.job.JobState;
import org.tavall.agentwebmcp.provider.target.LocalTargetProvider;
import org.tavall.agentwebmcp.support.FakeServiceProvider;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationExecutorTest {
    @TempDir
    Path dataDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void executesTypedMutatingOperationThroughProvider() throws Exception {
        FakeServiceProvider serviceProvider = new FakeServiceProvider();
        AgentWebMcpRuntime runtime = runtime(serviceProvider);
        enrollDemoService(runtime);

        OperationExecution execution = runtime.executor().execute(
                "service.restart",
                objectMapper.readTree("{\"serviceId\":\"demo.service\"}")
        );

        assertEquals(OperationExecutionStatus.SUCCESS, execution.status());
        assertEquals("restart", serviceProvider.lastAction());
        assertEquals("demo.service", serviceProvider.lastServiceId());
        assertEquals("restart", execution.output().get("action").asText());
    }

    @Test
    void projectsServiceStatusAndSystemMetrics() throws Exception {
        AgentWebMcpRuntime runtime = runtime(new FakeServiceProvider());
        enrollDemoService(runtime);

        OperationExecution status = runtime.executor().execute(
                "service.status",
                objectMapper.readTree("{\"serviceId\":\"demo.service\"}")
        );
        assertEquals(OperationExecutionStatus.SUCCESS, status.status());
        assertEquals("RUNNING", status.output().get("state").asText());

        OperationExecution metrics = runtime.executor().execute("metrics.snapshot", objectMapper.createObjectNode());
        assertEquals(OperationExecutionStatus.SUCCESS, metrics.status());
        assertEquals("local", metrics.output().get("targetId").asText());
        assertTrue(metrics.output().path("metrics").path("availableProcessors").asInt() >= 1);
    }

    @Test
    void executesCanonicalOperationAsDurableJob() throws Exception {
        AgentWebMcpRuntime runtime = runtime(new FakeServiceProvider());

        OperationExecution submission = runtime.executor().execute(
                "job.execute",
                objectMapper.readTree("{\"operationId\":\"system.status\",\"input\":{},\"timeoutSeconds\":5}")
        );
        assertEquals(OperationExecutionStatus.SUCCESS, submission.status());
        String jobId = submission.output().get("jobId").asText();

        JobDetails job = awaitTerminal(runtime, jobId);
        assertEquals(JobState.SUCCEEDED, job.state());
        assertEquals("system.status", job.execution().operationId());
        assertEquals("NO_AUTH", job.execution().output().get("authMode").asText());
    }

    @Test
    void rejectsRecursiveDurableJobExecution() throws Exception {
        AgentWebMcpRuntime runtime = runtime(new FakeServiceProvider());
        OperationExecution execution = runtime.executor().execute(
                "job.execute",
                objectMapper.readTree("{\"operationId\":\"job.execute\",\"input\":{}}")
        );

        assertEquals(OperationExecutionStatus.FAILURE, execution.status());
        assertEquals(400, execution.error().httpStatus());
        assertEquals("RECURSIVE_JOB_EXECUTION", execution.error().code());
    }

    @Test
    void rejectsUnknownOperation() {
        AgentWebMcpRuntime runtime = runtime(new FakeServiceProvider());
        OperationExecution execution = runtime.executor().execute("missing.operation", objectMapper.createObjectNode());

        assertEquals(OperationExecutionStatus.FAILURE, execution.status());
        assertNotNull(execution.error());
        assertEquals(404, execution.error().httpStatus());
        assertEquals("OPERATION_NOT_FOUND", execution.error().code());
    }

    @Test
    void rejectsInvalidTypedInput() {
        AgentWebMcpRuntime runtime = runtime(new FakeServiceProvider());
        OperationExecution execution = runtime.executor().execute("service.inspect", objectMapper.createObjectNode());

        assertEquals(OperationExecutionStatus.FAILURE, execution.status());
        assertEquals(400, execution.error().httpStatus());
        assertEquals("INVALID_INPUT", execution.error().code());
    }

    private void enrollDemoService(AgentWebMcpRuntime runtime) {
        OperationExecution enrollment = runtime.executor().execute(
                "service.add",
                objectMapper.createObjectNode().put("serviceId", "demo.service")
        );
        assertEquals(OperationExecutionStatus.SUCCESS, enrollment.status());
    }

    private AgentWebMcpRuntime runtime(FakeServiceProvider serviceProvider) {
        return AgentWebMcpRuntime.builder()
                .serviceProvider(serviceProvider)
                .targetProvider(new LocalTargetProvider())
                .dataDirectory(dataDirectory)
                .build();
    }

    private static JobDetails awaitTerminal(AgentWebMcpRuntime runtime, String jobId) throws Exception {
        for (int attempt = 0; attempt < 120; attempt++) {
            JobDetails job = runtime.context().jobProvider().inspectJob(jobId);
            if (job.state() != JobState.QUEUED && job.state() != JobState.RUNNING) {
                return job;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("job did not reach terminal state");
    }
}

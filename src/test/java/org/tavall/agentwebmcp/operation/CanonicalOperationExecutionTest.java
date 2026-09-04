package org.tavall.agentwebmcp.operation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tavall.agentwebmcp.AgentWebMcpRuntime;
import org.tavall.agentwebmcp.support.FakeCodexCliProvider;
import org.tavall.agentwebmcp.support.FakeServiceProvider;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CanonicalOperationExecutionTest {
    @TempDir Path dataDirectory;

    @Test
    void executesEveryCanonicalOperationThroughTheSharedExecutor() {
        FakeServiceProvider services = new FakeServiceProvider();
        FakeCodexCliProvider codex = new FakeCodexCliProvider();
        try (AgentWebMcpRuntime runtime = AgentWebMcpRuntime.builder()
                .serviceProvider(services)
                .codexCliProvider(codex)
                .dataDirectory(dataDirectory)
                .build()) {
            Set<String> executed = new LinkedHashSet<>();

            execute(runtime, executed, "system.status", object(runtime));
            execute(runtime, executed, "metrics.snapshot", object(runtime));
            execute(runtime, executed, "target.list", object(runtime));
            execute(runtime, executed, "target.inspect", object(runtime).put("targetId", "local"));
            execute(runtime, executed, "agent.list", object(runtime));
            execute(runtime, executed, "agent.inspect", object(runtime).put("agentId", "codex:local"));

            execute(runtime, executed, "service.list", object(runtime));
            execute(runtime, executed, "service.add", object(runtime).put("serviceId", "vendor.service"));
            execute(runtime, executed, "service.remove", object(runtime).put("serviceId", "vendor.service"));
            execute(runtime, executed, "service.discover", object(runtime));
            execute(runtime, executed, "service.inspect", service(runtime));
            execute(runtime, executed, "service.status", service(runtime));
            execute(runtime, executed, "service.logs", service(runtime).put("lines", 20));
            execute(runtime, executed, "service.diagnostics", service(runtime));
            execute(runtime, executed, "service.start", service(runtime));
            execute(runtime, executed, "service.stop", service(runtime));
            execute(runtime, executed, "service.restart", service(runtime));
            execute(runtime, executed, "service.reload", service(runtime));

            ObjectNode job = service(runtime);
            job.put("operationId", "service.restart");
            job.set("input", object(runtime));
            job.put("runAt", Instant.now().plusSeconds(60).toString());
            job.put("timeoutSeconds", 5);
            OperationExecution submission = execute(runtime, executed, "job.execute", job);
            String jobId = submission.output().path("jobId").asText();

            execute(runtime, executed, "job.list", object(runtime).put("limit", 100));
            execute(runtime, executed, "job.inspect", object(runtime).put("jobId", jobId));
            execute(runtime, executed, "job.logs", object(runtime).put("jobId", jobId).put("lines", 100));
            execute(runtime, executed, "job.cancel", object(runtime).put("jobId", jobId));

            Set<String> catalogIds = runtime.catalog().registrations().stream()
                    .map(registration -> registration.descriptor().id().value())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            assertEquals(23, catalogIds.size());
            assertEquals(catalogIds, executed);
        }
    }

    @Test
    void rejectsInvalidServiceSyntaxBeforeProviderExecution() {
        FakeServiceProvider services = new FakeServiceProvider();
        try (AgentWebMcpRuntime runtime = AgentWebMcpRuntime.builder()
                .serviceProvider(services)
                .codexCliProvider(new FakeCodexCliProvider())
                .dataDirectory(dataDirectory)
                .build()) {
            OperationExecution execution = runtime.executor().execute(
                    "service.restart", object(runtime).put("serviceId", "demo.service;rm"));
            assertEquals(OperationExecutionStatus.FAILURE, execution.status());
            assertEquals("INVALID_INPUT", execution.error().code());
            assertEquals("", services.lastAction());
        }
    }

    private static ObjectNode object(AgentWebMcpRuntime runtime) {
        return runtime.objectMapper().createObjectNode();
    }

    private static ObjectNode service(AgentWebMcpRuntime runtime) {
        return object(runtime).put("serviceId", "demo.service");
    }

    private static OperationExecution execute(
            AgentWebMcpRuntime runtime,
            Set<String> executed,
            String operationId,
            JsonNode input
    ) {
        OperationExecution execution = runtime.executor().execute(operationId, input);
        assertEquals(OperationExecutionStatus.SUCCESS, execution.status(),
                () -> operationId + " failed: " + execution.error());
        executed.add(operationId);
        return execution;
    }
}

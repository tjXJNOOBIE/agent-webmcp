package org.tavall.agentwebmcp.operation;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tavall.agentwebmcp.AgentWebMcpRuntime;
import org.tavall.agentwebmcp.support.FakeCodexCliProvider;
import org.tavall.agentwebmcp.support.FakeServiceProvider;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServiceDiscoveryAndDiagnosticsOperationTest {
    @TempDir Path dataDirectory;

    @Test
    void deterministicDiscoveryRegistersOnlyOperatorOwnedServicePaths() {
        FakeServiceProvider services = new FakeServiceProvider();
        try (AgentWebMcpRuntime runtime = runtime(services, new FakeCodexCliProvider())) {
            OperationExecution discovery = runtime.executor().execute("service.discover", JsonNodeFactory.instance.objectNode());
            assertEquals(OperationExecutionStatus.SUCCESS, discovery.status());
            assertEquals(List.of("demo.service", "opt-worker.service"), values(discovery.output().path("candidates")));
            assertTrue(values(discovery.output().path("skipped")).containsAll(List.of("vendor.service", "systemd-journald.service")));
            assertEquals(List.of("demo.service", "opt-worker.service"), values(discovery.output().path("registered")));
            assertEquals(2, runtime.executor().execute("service.list", JsonNodeFactory.instance.objectNode()).output().size());
        }
    }

    @Test
    void explicitAiDiscoveryReinspectsIdsAndRejectsHallucinations() {
        FakeCodexCliProvider codex = new FakeCodexCliProvider();
        codex.setDiscoveredServiceIds(List.of("vendor.service", "ghost.service"));
        try (AgentWebMcpRuntime runtime = runtime(new FakeServiceProvider(), codex)) {
            OperationExecution discovery = runtime.executor().execute(
                    "service.discover", JsonNodeFactory.instance.objectNode().put("includeAi", true));
            assertEquals(OperationExecutionStatus.SUCCESS, discovery.status());
            assertEquals(List.of("vendor.service"), values(discovery.output().path("aiCandidates")));
            assertEquals(List.of("ghost.service"), values(discovery.output().path("rejected")));
            assertTrue(values(discovery.output().path("registered")).contains("vendor.service"));
        }
    }

    @Test
    void aiDiscoveryFailsTypedWhenCodexIsUnavailable() {
        FakeCodexCliProvider codex = new FakeCodexCliProvider();
        codex.setAvailable(false);
        try (AgentWebMcpRuntime runtime = runtime(new FakeServiceProvider(), codex)) {
            OperationExecution discovery = runtime.executor().execute(
                    "service.discover", JsonNodeFactory.instance.objectNode().put("includeAi", true));
            assertEquals(OperationExecutionStatus.FAILURE, discovery.status());
            assertEquals("CODEX_UNAVAILABLE", discovery.error().code());
            assertEquals(503, discovery.error().httpStatus());
        }
    }

    @Test
    void diagnosticsDistinguishHealthyAndDegradedProviderEvidence() {
        FakeServiceProvider services = new FakeServiceProvider();
        try (AgentWebMcpRuntime runtime = runtime(services, new FakeCodexCliProvider())) {
            assertEquals(OperationExecutionStatus.SUCCESS, runtime.executor().execute(
                    "service.add", JsonNodeFactory.instance.objectNode().put("serviceId", "demo.service")).status());

            OperationExecution healthy = runtime.executor().execute(
                    "service.diagnostics", JsonNodeFactory.instance.objectNode().put("serviceId", "demo.service"));
            assertTrue(healthy.output().path("healthy").asBoolean());
            assertEquals(0, healthy.output().path("findings").size());
            assertTrue(healthy.output().path("recentLogs").path("output").asText().contains("line one"));

            services.setUnitFileState("masked");
            services.setFailLogs(true);
            OperationExecution degraded = runtime.executor().execute(
                    "service.diagnostics", JsonNodeFactory.instance.objectNode().put("serviceId", "demo.service"));
            assertFalse(degraded.output().path("healthy").asBoolean());
            assertTrue(values(degraded.output().path("findings")).stream().anyMatch(value -> value.contains("masked")));
            assertTrue(values(degraded.output().path("findings")).stream().anyMatch(value -> value.contains("SERVICE_LOGS_FAILED")));
        }
    }

    private AgentWebMcpRuntime runtime(FakeServiceProvider services, FakeCodexCliProvider codex) {
        return AgentWebMcpRuntime.builder().serviceProvider(services).codexCliProvider(codex).dataDirectory(dataDirectory).build();
    }

    private static List<String> values(com.fasterxml.jackson.databind.JsonNode array) {
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return List.copyOf(values);
    }
}

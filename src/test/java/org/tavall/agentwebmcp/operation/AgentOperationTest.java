package org.tavall.agentwebmcp.operation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tavall.agentwebmcp.AgentWebMcpRuntime;
import org.tavall.agentwebmcp.support.FakeCodexCliProvider;
import org.tavall.agentwebmcp.support.FakeServiceProvider;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AgentOperationTest {
    @TempDir Path dataDirectory;

    @Test
    void listsAndInspectsInstalledCodexAsObservedLocalRuntimeAgent() {
        FakeCodexCliProvider codex = new FakeCodexCliProvider();
        try (AgentWebMcpRuntime runtime = AgentWebMcpRuntime.builder()
                .serviceProvider(new FakeServiceProvider())
                .codexCliProvider(codex)
                .dataDirectory(dataDirectory)
                .build()) {
            OperationExecution list = runtime.executor().execute("agent.list", runtime.objectMapper().createObjectNode());
            assertEquals(OperationExecutionStatus.SUCCESS, list.status());
            assertEquals(1, list.output().size());
            assertEquals("codex:local", list.output().get(0).path("id").asText());
            assertEquals("ONLINE", list.output().get(0).path("state").asText());
            assertEquals("local", list.output().get(0).path("targetId").asText());
            assertEquals("codex-cli test", list.output().get(0).path("runtimeVersion").asText());
            assertFalse(list.output().get(0).path("lastHeartbeatAt").asText().isBlank());

            OperationExecution inspect = runtime.executor().execute("agent.inspect",
                    runtime.objectMapper().createObjectNode().put("agentId", "codex:local"));
            assertEquals(OperationExecutionStatus.SUCCESS, inspect.status());
            assertEquals("CODEX_CLI", inspect.output().path("runtimeKind").asText());
            assertTrue(inspect.output().path("capabilities").toString().contains("service-job.prompt"));
            assertTrue(inspect.output().path("capabilities").toString().contains("service-discovery.read-only"));
        }
    }

    @Test
    void hidesUnavailableCodexAndRejectsUnknownAgent() {
        FakeCodexCliProvider codex = new FakeCodexCliProvider();
        codex.setAvailable(false);
        try (AgentWebMcpRuntime runtime = AgentWebMcpRuntime.builder()
                .serviceProvider(new FakeServiceProvider())
                .codexCliProvider(codex)
                .dataDirectory(dataDirectory)
                .build()) {
            OperationExecution list = runtime.executor().execute("agent.list", runtime.objectMapper().createObjectNode());
            assertEquals(OperationExecutionStatus.SUCCESS, list.status());
            assertTrue(list.output().isEmpty());

            OperationExecution inspect = runtime.executor().execute("agent.inspect",
                    runtime.objectMapper().createObjectNode().put("agentId", "codex:local"));
            assertEquals(OperationExecutionStatus.FAILURE, inspect.status());
            assertEquals("AGENT_NOT_FOUND", inspect.error().code());
            assertEquals(404, inspect.error().httpStatus());
        }
    }
}

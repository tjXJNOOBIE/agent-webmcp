package org.tavall.agentwebmcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tavall.agentwebmcp.provider.agent.AgentSummary;
import org.tavall.agentwebmcp.support.FakeCodexCliProvider;
import org.tavall.agentwebmcp.support.FakeServiceProvider;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentWebMcpRuntimeTest {
    @TempDir Path dataDirectory;

    @Test
    void serializesJavaTimeAsIsoDateTimeTextForBrowserAndMcpContracts() throws Exception {
        try (AgentWebMcpRuntime runtime = AgentWebMcpRuntime.builder()
                .serviceProvider(new FakeServiceProvider())
                .codexCliProvider(new FakeCodexCliProvider())
                .dataDirectory(dataDirectory)
                .build()) {
            Instant heartbeat = Instant.parse("2026-09-04T05:00:00.123Z");
            String json = runtime.objectMapper().writeValueAsString(new AgentSummary(
                    "codex:local", "Installed Codex CLI", "ONLINE", "local", "test", heartbeat));
            var tree = runtime.objectMapper().readTree(json);
            assertEquals(heartbeat.toString(), tree.path("lastHeartbeatAt").asText());
            assertFalse(tree.path("lastHeartbeatAt").isNumber());
        }
    }
}

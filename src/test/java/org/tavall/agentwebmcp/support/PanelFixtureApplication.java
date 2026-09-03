package org.tavall.agentwebmcp.support;

import org.tavall.agentwebmcp.AgentWebMcpRuntime;
import org.tavall.agentwebmcp.http.AgentWebMcpHttpServer;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public final class PanelFixtureApplication {
    private PanelFixtureApplication() { }

    public static void main(String[] args) throws Exception {
        Path dataDirectory = Path.of(System.getenv().getOrDefault("AGENT_WEBMCP_DATA_DIR", "test-results/panel-runtime-data"));
        int port = Integer.parseInt(System.getenv().getOrDefault("AGENT_WEBMCP_PORT", "7188"));
        FakeCodexCliProvider codex = new FakeCodexCliProvider();
        codex.setDiscoveredServiceIds(List.of("vendor.service"));
        AgentWebMcpRuntime runtime = AgentWebMcpRuntime.builder()
                .serviceProvider(new FakeServiceProvider())
                .codexCliProvider(codex)
                .dataDirectory(dataDirectory)
                .build();
        AgentWebMcpHttpServer server = AgentWebMcpHttpServer.builder().host("127.0.0.1").port(port).build();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.close();
            runtime.close();
        }, "panel-fixture-shutdown"));
        try (server; runtime) {
            server.start();
            new CountDownLatch(1).await();
        }
    }
}

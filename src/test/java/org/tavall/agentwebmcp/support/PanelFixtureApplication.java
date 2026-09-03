package org.tavall.agentwebmcp.support;

import org.tavall.agentwebmcp.AgentWebMcpRuntime;
import org.tavall.agentwebmcp.http.AgentWebMcpHttpServer;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

public final class PanelFixtureApplication {
    private PanelFixtureApplication() {
    }

    public static void main(String[] args) throws Exception {
        Path dataDirectory = Path.of(System.getenv().getOrDefault("AGENT_WEBMCP_DATA_DIR", "test-results/panel-runtime-data"));
        int port = Integer.parseInt(System.getenv().getOrDefault("AGENT_WEBMCP_PORT", "7193"));
        AgentWebMcpRuntime.builder()
                .serviceProvider(new FakeServiceProvider())
                .dataDirectory(dataDirectory)
                .build();
        try (AgentWebMcpHttpServer server = AgentWebMcpHttpServer.builder().host("127.0.0.1").port(port).build()) {
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(server::close, "panel-fixture-shutdown"));
            new CountDownLatch(1).await();
        }
    }
}

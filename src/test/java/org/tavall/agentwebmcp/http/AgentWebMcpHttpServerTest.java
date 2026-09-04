package org.tavall.agentwebmcp.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tavall.agentwebmcp.AgentWebMcpRuntime;
import org.tavall.agentwebmcp.operation.OperationExecutionStatus;
import org.tavall.agentwebmcp.support.FakeCodexCliProvider;
import org.tavall.agentwebmcp.support.FakeServiceProvider;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentWebMcpHttpServerTest {
    @TempDir Path dataDirectory;

    @Test
    void servesCatalogAndCanonicalExecutionFromLightweightJavaTransport() throws Exception {
        try (AgentWebMcpRuntime runtime = AgentWebMcpRuntime.builder().serviceProvider(new FakeServiceProvider())
                .codexCliProvider(new FakeCodexCliProvider()).dataDirectory(dataDirectory).build();
             AgentWebMcpHttpServer server = AgentWebMcpHttpServer.builder().port(0).build()) {
            server.start();
            assertEquals("127.0.0.1", server.host());
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + server.port();

            HttpResponse<String> health = client.send(HttpRequest.newBuilder(URI.create(base + "/health")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, health.statusCode());
            assertTrue(health.body().contains("\"webServer\":\"jdk-httpserver\""));
            assertTrue(health.body().contains("\"authMode\":\"NO_AUTH\""));
            assertTrue(health.body().contains("\"operationCount\":23"));

            HttpResponse<String> catalog = client.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/operations")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, catalog.statusCode());
            assertTrue(catalog.body().contains("agent.list"));
            assertTrue(catalog.body().contains("agent.inspect"));
            assertTrue(catalog.body().contains("service.discover"));
            assertTrue(catalog.body().contains("service.diagnostics"));
            assertTrue(catalog.body().contains("job.cancel"));

            HttpResponse<String> execution = post(client, base, "system.status", "{}");
            assertEquals(200, execution.statusCode());
            assertTrue(execution.body().contains("\"status\":\"SUCCESS\""));
            assertTrue(execution.body().contains("NO_AUTH"));

            assertEquals(OperationExecutionStatus.SUCCESS, runtime.executor().execute(
                    "service.add", runtime.objectMapper().createObjectNode().put("serviceId", "demo.service")).status());
            HttpResponse<String> disallowed = post(client, base, "job.execute",
                    "{\"serviceId\":\"demo.service\",\"operationId\":\"system.status\",\"input\":{}}");
            assertEquals(400, disallowed.statusCode());
            assertTrue(disallowed.body().contains("JOB_OPERATION_NOT_ALLOWED"));
        }
    }

    @Test
    void noAuthRefusesNonLoopbackBinding() {
        try (AgentWebMcpRuntime runtime = AgentWebMcpRuntime.builder().serviceProvider(new FakeServiceProvider())
                .codexCliProvider(new FakeCodexCliProvider()).dataDirectory(dataDirectory).build()) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> AgentWebMcpHttpServer.builder().host("0.0.0.0").port(0).build());
            assertTrue(failure.getMessage().contains("NO_AUTH requires a loopback bind host"));
        }
    }

    @Test
    void mutationEndpointRejectsCrossOriginAndNonJsonBrowserRequests() throws Exception {
        try (AgentWebMcpRuntime runtime = AgentWebMcpRuntime.builder().serviceProvider(new FakeServiceProvider())
                .codexCliProvider(new FakeCodexCliProvider()).dataDirectory(dataDirectory).build();
             AgentWebMcpHttpServer server = AgentWebMcpHttpServer.builder().port(0).build()) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            URI endpoint = URI.create("http://127.0.0.1:" + server.port() + "/api/v1/operations/system.status");

            HttpResponse<String> crossOrigin = client.send(HttpRequest.newBuilder(endpoint)
                    .header("Origin", "https://attacker.example")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(403, crossOrigin.statusCode());

            HttpResponse<String> textPlain = client.send(HttpRequest.newBuilder(endpoint)
                    .header("Content-Type", "text/plain")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(415, textPlain.statusCode());
        }
    }

    private static HttpResponse<String> post(HttpClient client, String base, String operationId, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/operations/" + operationId))
                .header("content-type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}

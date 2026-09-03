package org.tavall.agentwebmcp.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tavall.agentwebmcp.AgentWebMcpRuntime;
import org.tavall.agentwebmcp.http.AgentWebMcpHttpServer;
import org.tavall.agentwebmcp.support.FakeServiceProvider;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpHttpHandlerTest {
    @TempDir
    Path dataDirectory;

    @Test
    void exposesBoundedOperationsThroughStreamableHttpMcp() throws Exception {
        FakeServiceProvider services = new FakeServiceProvider();
        AgentWebMcpRuntime runtime = AgentWebMcpRuntime.builder()
                .serviceProvider(services)
                .dataDirectory(dataDirectory)
                .build();

        try (AgentWebMcpHttpServer server = AgentWebMcpHttpServer.builder().port(0).build()) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            String endpoint = "http://127.0.0.1:" + server.port() + "/mcp";

            HttpResponse<String> initialize = post(client, endpoint, null, """
                    {"jsonrpc":"2.0","id":"init-1","method":"initialize","params":{
                      "protocolVersion":"2025-06-18","capabilities":{},
                      "clientInfo":{"name":"agent-webmcp-test","version":"1"}
                    }}
                    """);
            assertEquals(200, initialize.statusCode());
            String sessionId = initialize.headers().firstValue("Mcp-Session-Id").orElse(null);
            assertNotNull(sessionId);
            JsonNode initializeBody = mapper().readTree(initialize.body());
            assertEquals("2025-06-18", initializeBody.path("result").path("protocolVersion").asText());
            assertEquals("agent-webmcp", initializeBody.path("result").path("serverInfo").path("name").asText());

            HttpResponse<String> initialized = post(client, endpoint, sessionId, """
                    {"jsonrpc":"2.0","method":"notifications/initialized","params":{}}
                    """);
            assertEquals(202, initialized.statusCode());

            HttpResponse<String> list = post(client, endpoint, sessionId, """
                    {"jsonrpc":"2.0","id":"tools-1","method":"tools/list","params":{}}
                    """);
            assertEquals(200, list.statusCode());
            JsonNode tools = mapper().readTree(list.body()).path("result").path("tools");
            assertEquals(McpToolPolicy.exposedToolCount(), tools.size());
            Set<String> names = new java.util.HashSet<>();
            tools.forEach(tool -> names.add(tool.path("name").asText()));
            assertTrue(names.contains("system.status"));
            assertTrue(names.contains("service.logs"));
            assertTrue(names.contains("service.restart"));
            assertTrue(names.contains("job.list"));
            assertTrue(names.contains("job.inspect"));
            assertTrue(names.contains("job.logs"));
            assertFalse(names.contains("job.execute"));
            assertFalse(names.contains("target.inspect"));
            assertFalse(names.contains("service.add"));
            assertFalse(names.contains("service.remove"));

            HttpResponse<String> status = post(client, endpoint, sessionId, """
                    {"jsonrpc":"2.0","id":"call-1","method":"tools/call","params":{
                      "name":"system.status","arguments":{}
                    }}
                    """);
            JsonNode statusBody = mapper().readTree(status.body());
            assertEquals(200, status.statusCode());
            assertEquals("SUCCESS", statusBody.path("result").path("structuredContent").path("status").asText());
            assertFalse(statusBody.path("result").path("isError").asBoolean());

            assertEquals("SUCCESS", runtime.executor().execute(
                    "service.add",
                    runtime.objectMapper().createObjectNode().put("serviceId", "demo.service")
            ).status().name());

            HttpResponse<String> restart = post(client, endpoint, sessionId, """
                    {"jsonrpc":"2.0","id":"call-2","method":"tools/call","params":{
                      "name":"service.restart","arguments":{"serviceId":"demo.service"}
                    }}
                    """);
            JsonNode restartBody = mapper().readTree(restart.body());
            assertEquals(200, restart.statusCode());
            assertEquals("SUCCESS", restartBody.path("result").path("structuredContent").path("status").asText());
            assertEquals("restart", services.lastAction());

            HttpResponse<String> internal = post(client, endpoint, sessionId, """
                    {"jsonrpc":"2.0","id":"call-3","method":"tools/call","params":{
                      "name":"job.execute","arguments":{}
                    }}
                    """);
            assertEquals(-32602, mapper().readTree(internal.body()).path("error").path("code").asInt());
        }
    }

    @Test
    void rejectsUntrustedOriginsAndDoesNotOfferSseListener() throws Exception {
        AgentWebMcpRuntime.builder()
                .serviceProvider(new FakeServiceProvider())
                .dataDirectory(dataDirectory)
                .build();
        try (AgentWebMcpHttpServer server = AgentWebMcpHttpServer.builder().port(0).build()) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            URI endpoint = URI.create("http://127.0.0.1:" + server.port() + "/mcp");

            HttpResponse<String> get = client.send(
                    HttpRequest.newBuilder(endpoint).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(405, get.statusCode());

            HttpResponse<String> badOrigin = client.send(
                    HttpRequest.newBuilder(endpoint)
                            .header("Origin", "https://attacker.example")
                            .header("Accept", "application/json, text/event-stream")
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("""
                                    {"jsonrpc":"2.0","id":"init-2","method":"initialize","params":{
                                      "protocolVersion":"2025-06-18","capabilities":{},
                                      "clientInfo":{"name":"bad-origin","version":"1"}
                                    }}
                                    """))
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(403, badOrigin.statusCode());
        }
    }

    private static HttpResponse<String> post(HttpClient client, String endpoint, String sessionId, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
                .header("Accept", "application/json, text/event-stream")
                .header("Content-Type", "application/json")
                .header("Mcp-Protocol-Version", "2025-06-18");
        if (sessionId != null) {
            builder.header("Mcp-Session-Id", sessionId);
        }
        return client.send(
                builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private static ObjectMapper mapper() {
        return new ObjectMapper();
    }
}

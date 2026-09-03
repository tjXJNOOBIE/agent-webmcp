package org.tavall.agentwebmcp.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tavall.agentwebmcp.AgentWebMcpRuntime;
import org.tavall.agentwebmcp.http.AgentWebMcpHttpServer;
import org.tavall.agentwebmcp.support.FakeCodexCliProvider;
import org.tavall.agentwebmcp.support.FakeServiceProvider;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class McpHttpHandlerTest {
    @TempDir Path dataDirectory;

    @Test
    void exposesBoundedOperationsThroughStreamableHttpMcp() throws Exception {
        FakeServiceProvider services = new FakeServiceProvider();
        try (AgentWebMcpRuntime runtime = AgentWebMcpRuntime.builder()
                .serviceProvider(services)
                .codexCliProvider(new FakeCodexCliProvider())
                .dataDirectory(dataDirectory)
                .build();
             AgentWebMcpHttpServer server = AgentWebMcpHttpServer.builder().port(0).build()) {
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

            assertEquals(202, post(client, endpoint, sessionId, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\",\"params\":{}}").statusCode());
            HttpResponse<String> list = post(client, endpoint, sessionId, "{\"jsonrpc\":\"2.0\",\"id\":\"tools-1\",\"method\":\"tools/list\",\"params\":{}}");
            JsonNode tools = mapper().readTree(list.body()).path("result").path("tools");
            assertEquals(16, McpToolPolicy.exposedToolCount());
            assertEquals(16, tools.size());
            Set<String> names = new java.util.HashSet<>();
            tools.forEach(tool -> names.add(tool.path("name").asText()));
            assertTrue(names.containsAll(Set.of("system.status", "metrics.snapshot", "agent.list", "agent.inspect", "service.list", "service.inspect", "service.status", "service.logs", "service.diagnostics", "service.start", "service.stop", "service.restart", "service.reload", "job.list", "job.inspect", "job.logs")));
            assertFalse(names.contains("service.add"));
            assertFalse(names.contains("service.remove"));
            assertFalse(names.contains("service.discover"));
            assertFalse(names.contains("job.execute"));
            assertFalse(names.contains("job.cancel"));
            assertFalse(names.stream().anyMatch(name -> name.startsWith("target.")));

            HttpResponse<String> status = post(client, endpoint, sessionId, "{\"jsonrpc\":\"2.0\",\"id\":\"call-1\",\"method\":\"tools/call\",\"params\":{\"name\":\"system.status\",\"arguments\":{}}}");
            JsonNode statusBody = mapper().readTree(status.body());
            assertEquals(200, status.statusCode());
            assertEquals("SUCCESS", statusBody.path("result").path("structuredContent").path("status").asText());
            assertFalse(statusBody.path("result").path("isError").asBoolean());

            assertEquals("SUCCESS", runtime.executor().execute("service.add", runtime.objectMapper().createObjectNode().put("serviceId", "demo.service")).status().name());
            HttpResponse<String> diagnostics = post(client, endpoint, sessionId, "{\"jsonrpc\":\"2.0\",\"id\":\"call-2\",\"method\":\"tools/call\",\"params\":{\"name\":\"service.diagnostics\",\"arguments\":{\"serviceId\":\"demo.service\"}}}");
            assertEquals(200, diagnostics.statusCode());
            assertTrue(mapper().readTree(diagnostics.body()).path("result").path("structuredContent").path("output").path("healthy").asBoolean());

            HttpResponse<String> internal = post(client, endpoint, sessionId, "{\"jsonrpc\":\"2.0\",\"id\":\"call-3\",\"method\":\"tools/call\",\"params\":{\"name\":\"job.execute\",\"arguments\":{}}}");
            assertEquals(-32602, mapper().readTree(internal.body()).path("error").path("code").asInt());
        }
    }

    @Test
    void rejectsUntrustedOriginsAndDoesNotOfferSseListener() throws Exception {
        try (AgentWebMcpRuntime runtime = AgentWebMcpRuntime.builder().serviceProvider(new FakeServiceProvider()).codexCliProvider(new FakeCodexCliProvider()).dataDirectory(dataDirectory).build();
             AgentWebMcpHttpServer server = AgentWebMcpHttpServer.builder().port(0).build()) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            URI endpoint = URI.create("http://127.0.0.1:" + server.port() + "/mcp");
            assertEquals(405, client.send(HttpRequest.newBuilder(endpoint).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            HttpResponse<String> badOrigin = client.send(HttpRequest.newBuilder(endpoint)
                    .header("Origin", "https://attacker.example")
                    .header("Accept", "application/json, text/event-stream")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":\"init-2\",\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},\"clientInfo\":{\"name\":\"bad-origin\",\"version\":\"1\"}}}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(403, badOrigin.statusCode());
        }
    }

    private static HttpResponse<String> post(HttpClient client, String endpoint, String sessionId, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
                .header("Accept", "application/json, text/event-stream")
                .header("Content-Type", "application/json")
                .header("Mcp-Protocol-Version", "2025-06-18");
        if (sessionId != null) builder.header("Mcp-Session-Id", sessionId);
        return client.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static ObjectMapper mapper() { return new ObjectMapper(); }
}

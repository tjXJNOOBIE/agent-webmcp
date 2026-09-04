package org.tavall.agentwebmcp.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class McpHttpHandlerTest {
    @TempDir Path dataDirectory;

    @Test
    void exposesBoundedOperationsAndMcpAppResourceThroughStreamableHttpMcp() throws Exception {
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

            HttpResponse<String> initialize = post(client, endpoint, null, "2025-06-18", """
                    {"jsonrpc":"2.0","id":"init-1","method":"initialize","params":{
                      "protocolVersion":"2025-06-18","capabilities":{},
                      "clientInfo":{"name":"agent-webmcp-test","version":"1"}
                    }}
                    """);
            assertEquals(200, initialize.statusCode());
            String sessionId = initialize.headers().firstValue("Mcp-Session-Id").orElse(null);
            assertNotNull(sessionId);
            JsonNode initializeResult = mapper().readTree(initialize.body()).path("result");
            assertEquals("2025-06-18", initializeResult.path("protocolVersion").asText());
            assertTrue(initializeResult.path("capabilities").has("resources"));
            assertEquals(
                    McpAppResource.MIME_TYPE,
                    initializeResult.path("capabilities").path("extensions").path(McpAppResource.EXTENSION_ID)
                            .path("mimeTypes").get(0).asText()
            );

            assertEquals(202, post(client, endpoint, sessionId, "2025-06-18",
                    "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\",\"params\":{}}").statusCode());

            HttpResponse<String> list = post(client, endpoint, sessionId, "2025-06-18",
                    "{\"jsonrpc\":\"2.0\",\"id\":\"tools-1\",\"method\":\"tools/list\",\"params\":{}}");
            JsonNode tools = mapper().readTree(list.body()).path("result").path("tools");
            assertEquals(16, McpToolPolicy.exposedToolCount());
            assertEquals(16, tools.size());
            Set<String> names = new HashSet<>();
            JsonNode statusTool = null;
            for (JsonNode tool : tools) {
                String name = tool.path("name").asText();
                names.add(name);
                assertTrue(tool.path("_meta").path("ui").path("visibility").toString().contains("model"));
                assertTrue(tool.path("_meta").path("ui").path("visibility").toString().contains("app"));
                assertTrue(tool.path("_meta").path("openai/widgetAccessible").asBoolean());
                if ("system.status".equals(name)) {
                    statusTool = tool;
                } else {
                    assertFalse(tool.path("_meta").path("ui").has("resourceUri"), name + " must not remount the app");
                    assertFalse(tool.path("_meta").has("openai/outputTemplate"), name + " must not carry a render template");
                }
            }
            assertNotNull(statusTool);
            assertEquals(McpAppResource.URI, statusTool.path("_meta").path("ui").path("resourceUri").asText());
            assertEquals(McpAppResource.URI, statusTool.path("_meta").path("openai/outputTemplate").asText());
            assertTrue(names.containsAll(Set.of(
                    "system.status", "metrics.snapshot", "agent.list", "agent.inspect",
                    "service.list", "service.inspect", "service.status", "service.logs", "service.diagnostics",
                    "service.start", "service.stop", "service.restart", "service.reload",
                    "job.list", "job.inspect", "job.logs"
            )));
            assertFalse(names.contains("service.add"));
            assertFalse(names.contains("service.remove"));
            assertFalse(names.contains("service.discover"));
            assertFalse(names.contains("job.execute"));
            assertFalse(names.contains("job.cancel"));
            assertFalse(names.stream().anyMatch(name -> name.startsWith("target.")));

            HttpResponse<String> resources = post(client, endpoint, sessionId, "2025-06-18",
                    "{\"jsonrpc\":\"2.0\",\"id\":\"resources-1\",\"method\":\"resources/list\",\"params\":{}}");
            JsonNode resource = mapper().readTree(resources.body()).path("result").path("resources").get(0);
            assertEquals(McpAppResource.URI, resource.path("uri").asText());
            assertEquals(McpAppResource.MIME_TYPE, resource.path("mimeType").asText());
            assertTrue(resource.path("_meta").path("ui").path("prefersBorder").asBoolean());
            assertEquals(0, resource.path("_meta").path("ui").path("csp").path("connectDomains").size());
            assertEquals(0, resource.path("_meta").path("ui").path("csp").path("resourceDomains").size());
            assertFalse(resource.path("_meta").path("openai/widgetDescription").asText().isBlank());

            HttpResponse<String> read = post(client, endpoint, sessionId, "2025-06-18", """
                    {"jsonrpc":"2.0","id":"resource-read","method":"resources/read","params":{"uri":"ui://agent-webmcp/fleet-cockpit-v1"}}
                    """);
            JsonNode content = mapper().readTree(read.body()).path("result").path("contents").get(0);
            assertEquals(McpAppResource.MIME_TYPE, content.path("mimeType").asText());
            String html = content.path("text").asText();
            assertTrue(html.contains("ui/initialize"));
            assertTrue(html.contains("ui/notifications/initialized"));
            assertTrue(html.contains("ui/notifications/tool-result"));
            assertTrue(html.contains("tools/call"));
            assertTrue(html.contains("service.list"));
            assertTrue(html.contains("agent.list"));
            assertTrue(html.contains("job.list"));
            assertTrue(html.contains("metrics.snapshot"));
            assertFalse(html.contains("fetch('/api"));
            assertFalse(html.contains("fetch(\"/api"));

            HttpResponse<String> missing = post(client, endpoint, sessionId, "2025-06-18",
                    "{\"jsonrpc\":\"2.0\",\"id\":\"resource-missing\",\"method\":\"resources/read\",\"params\":{\"uri\":\"ui://agent-webmcp/missing\"}}");
            assertEquals(-32602, mapper().readTree(missing.body()).path("error").path("code").asInt());

            HttpResponse<String> status = post(client, endpoint, sessionId, "2025-06-18",
                    "{\"jsonrpc\":\"2.0\",\"id\":\"call-1\",\"method\":\"tools/call\",\"params\":{\"name\":\"system.status\",\"arguments\":{}}}");
            JsonNode statusBody = mapper().readTree(status.body());
            assertEquals(200, status.statusCode());
            assertEquals("SUCCESS", statusBody.path("result").path("structuredContent").path("status").asText());
            assertFalse(statusBody.path("result").path("isError").asBoolean());

            assertEquals("SUCCESS", runtime.executor().execute("service.add",
                    runtime.objectMapper().createObjectNode().put("serviceId", "demo.service")).status().name());
            HttpResponse<String> diagnostics = post(client, endpoint, sessionId, "2025-06-18",
                    "{\"jsonrpc\":\"2.0\",\"id\":\"call-2\",\"method\":\"tools/call\",\"params\":{\"name\":\"service.diagnostics\",\"arguments\":{\"serviceId\":\"demo.service\"}}}");
            assertEquals(200, diagnostics.statusCode());
            assertTrue(mapper().readTree(diagnostics.body()).path("result").path("structuredContent").path("output").path("healthy").asBoolean());

            HttpResponse<String> internal = post(client, endpoint, sessionId, "2025-06-18",
                    "{\"jsonrpc\":\"2.0\",\"id\":\"call-3\",\"method\":\"tools/call\",\"params\":{\"name\":\"job.execute\",\"arguments\":{}}}");
            assertEquals(-32602, mapper().readTree(internal.body()).path("error").path("code").asInt());
        }
    }

    @Test
    void modernDiscoveryAdvertisesResourcesAndSupportsStatelessMcpAppRead() throws Exception {
        try (AgentWebMcpRuntime runtime = AgentWebMcpRuntime.builder()
                .serviceProvider(new FakeServiceProvider())
                .codexCliProvider(new FakeCodexCliProvider())
                .dataDirectory(dataDirectory)
                .build();
             AgentWebMcpHttpServer server = AgentWebMcpHttpServer.builder().port(0).build()) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            String endpoint = "http://127.0.0.1:" + server.port() + "/mcp";

            HttpResponse<String> discover = modernPost(client, endpoint, "server/discover", null,
                    "{\"jsonrpc\":\"2.0\",\"id\":\"discover-1\",\"method\":\"server/discover\",\"params\":{}}");
            assertEquals(200, discover.statusCode());
            JsonNode result = mapper().readTree(discover.body()).path("result");
            assertEquals("complete", result.path("resultType").asText());
            assertEquals(1, result.path("supportedVersions").size());
            assertEquals("2026-07-28", result.path("supportedVersions").get(0).asText());
            assertEquals(0, result.path("ttlMs").asInt());
            assertEquals("private", result.path("cacheScope").asText());
            assertTrue(result.path("capabilities").has("tools"));
            assertTrue(result.path("capabilities").has("resources"));
            assertEquals(McpAppResource.MIME_TYPE,
                    result.path("capabilities").path("extensions").path(McpAppResource.EXTENSION_ID)
                            .path("mimeTypes").get(0).asText());
            assertEquals("agent-webmcp",
                    result.path("_meta").path("io.modelcontextprotocol/serverInfo").path("name").asText());

            HttpResponse<String> resources = modernPost(client, endpoint, "resources/list", null,
                    "{\"jsonrpc\":\"2.0\",\"id\":\"resources-modern\",\"method\":\"resources/list\",\"params\":{}}");
            assertEquals(200, resources.statusCode());
            JsonNode resourcesResult = mapper().readTree(resources.body()).path("result");
            assertEquals("complete", resourcesResult.path("resultType").asText());
            assertEquals(0, resourcesResult.path("ttlMs").asInt());
            assertEquals("private", resourcesResult.path("cacheScope").asText());
            assertEquals(McpAppResource.URI, resourcesResult.path("resources").get(0).path("uri").asText());

            HttpResponse<String> read = modernPost(client, endpoint, "resources/read", McpAppResource.URI,
                    "{\"jsonrpc\":\"2.0\",\"id\":\"read-modern\",\"method\":\"resources/read\",\"params\":{\"uri\":\""
                            + McpAppResource.URI + "\"}}");
            assertEquals(200, read.statusCode());
            JsonNode readResult = mapper().readTree(read.body()).path("result");
            assertEquals("complete", readResult.path("resultType").asText());
            assertTrue(readResult.path("contents").get(0).path("text").asText().contains("ui/initialize"));

            HttpResponse<String> tools = modernPost(client, endpoint, "tools/list", null,
                    "{\"jsonrpc\":\"2.0\",\"id\":\"tools-modern\",\"method\":\"tools/list\",\"params\":{}}");
            JsonNode toolsResult = mapper().readTree(tools.body()).path("result");
            assertEquals("complete", toolsResult.path("resultType").asText());
            assertEquals(16, toolsResult.path("tools").size());

            HttpResponse<String> call = modernPost(client, endpoint, "tools/call", "system.status",
                    "{\"jsonrpc\":\"2.0\",\"id\":\"call-modern\",\"method\":\"tools/call\",\"params\":{\"name\":\"system.status\",\"arguments\":{}}}");
            JsonNode callResult = mapper().readTree(call.body()).path("result");
            assertEquals("complete", callResult.path("resultType").asText());
            assertEquals("SUCCESS", callResult.path("structuredContent").path("status").asText());
            assertFalse(callResult.has("ttlMs"));
        }
    }

    @Test
    void modernTransportRejectsHeaderAndMetadataMismatches() throws Exception {
        try (AgentWebMcpRuntime runtime = AgentWebMcpRuntime.builder()
                .serviceProvider(new FakeServiceProvider())
                .codexCliProvider(new FakeCodexCliProvider())
                .dataDirectory(dataDirectory)
                .build();
             AgentWebMcpHttpServer server = AgentWebMcpHttpServer.builder().port(0).build()) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            String endpoint = "http://127.0.0.1:" + server.port() + "/mcp";
            String body = modernBody("{\"jsonrpc\":\"2.0\",\"id\":\"bad-header\",\"method\":\"tools/list\",\"params\":{}}");

            HttpResponse<String> badMethod = client.send(HttpRequest.newBuilder(URI.create(endpoint))
                    .header("Accept", "application/json, text/event-stream")
                    .header("Content-Type", "application/json")
                    .header("Mcp-Protocol-Version", "2026-07-28")
                    .header("Mcp-Method", "resources/list")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(400, badMethod.statusCode());
            assertEquals(-32020, mapper().readTree(badMethod.body()).path("error").path("code").asInt());

            HttpResponse<String> missingName = client.send(HttpRequest.newBuilder(URI.create(endpoint))
                    .header("Accept", "application/json, text/event-stream")
                    .header("Content-Type", "application/json")
                    .header("Mcp-Protocol-Version", "2026-07-28")
                    .header("Mcp-Method", "tools/call")
                    .POST(HttpRequest.BodyPublishers.ofString(modernBody(
                            "{\"jsonrpc\":\"2.0\",\"id\":\"missing-name\",\"method\":\"tools/call\",\"params\":{\"name\":\"system.status\",\"arguments\":{}}}")))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(400, missingName.statusCode());
            assertEquals(-32020, mapper().readTree(missingName.body()).path("error").path("code").asInt());
        }
    }

    @Test
    void rejectsUntrustedOriginsAndDoesNotOfferSseListener() throws Exception {
        try (AgentWebMcpRuntime runtime = AgentWebMcpRuntime.builder()
                .serviceProvider(new FakeServiceProvider())
                .codexCliProvider(new FakeCodexCliProvider())
                .dataDirectory(dataDirectory).build();
             AgentWebMcpHttpServer server = AgentWebMcpHttpServer.builder().port(0).build()) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            URI endpoint = URI.create("http://127.0.0.1:" + server.port() + "/mcp");
            assertEquals(405, client.send(HttpRequest.newBuilder(endpoint).GET().build(),
                    HttpResponse.BodyHandlers.ofString()).statusCode());
            HttpResponse<String> badOrigin = client.send(HttpRequest.newBuilder(endpoint)
                    .header("Origin", "https://attacker.example")
                    .header("Accept", "application/json, text/event-stream")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"jsonrpc\":\"2.0\",\"id\":\"init-2\",\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},\"clientInfo\":{\"name\":\"bad-origin\",\"version\":\"1\"}}}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(403, badOrigin.statusCode());

            HttpResponse<String> simpleRequest = client.send(HttpRequest.newBuilder(endpoint)
                    .header("Accept", "application/json, text/event-stream")
                    .header("Content-Type", "text/plain")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":\"simple\",\"method\":\"initialize\",\"params\":{}}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(415, simpleRequest.statusCode());
        }
    }


    private static HttpResponse<String> modernPost(
            HttpClient client,
            String endpoint,
            String method,
            String name,
            String body
    ) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
                .header("Accept", "application/json, text/event-stream")
                .header("Content-Type", "application/json")
                .header("Mcp-Protocol-Version", "2026-07-28")
                .header("Mcp-Method", method);
        if (name != null) {
            builder.header("Mcp-Name", name);
        }
        return client.send(builder.POST(HttpRequest.BodyPublishers.ofString(modernBody(body))).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static String modernBody(String body) throws Exception {
        ObjectNode request = (ObjectNode) mapper().readTree(body);
        JsonNode existingParams = request.get("params");
        ObjectNode params;
        if (existingParams instanceof ObjectNode objectNode) {
            params = objectNode;
        } else {
            params = request.putObject("params");
        }
        ObjectNode meta = params.putObject("_meta");
        meta.put("io.modelcontextprotocol/protocolVersion", "2026-07-28");
        meta.putObject("io.modelcontextprotocol/clientCapabilities");
        return mapper().writeValueAsString(request);
    }

    private static HttpResponse<String> post(
            HttpClient client,
            String endpoint,
            String sessionId,
            String protocolVersion,
            String body
    ) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
                .header("Accept", "application/json, text/event-stream")
                .header("Content-Type", "application/json")
                .header("Mcp-Protocol-Version", protocolVersion);
        if (sessionId != null) {
            builder.header("Mcp-Session-Id", sessionId);
        }
        return client.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static ObjectMapper mapper() {
        return new ObjectMapper();
    }
}

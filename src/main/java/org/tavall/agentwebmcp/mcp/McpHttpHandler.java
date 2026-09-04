package org.tavall.agentwebmcp.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.tavall.agentwebmcp.AgentWebMcpRuntime;
import org.tavall.agentwebmcp.http.HttpRequestSecurityPolicy;
import org.tavall.agentwebmcp.operation.OperationAccess;
import org.tavall.agentwebmcp.operation.OperationExecution;
import org.tavall.agentwebmcp.operation.OperationExecutionStatus;
import org.tavall.agentwebmcp.operation.OperationRegistration;
import org.tavall.agentwebmcp.operation.schema.RecordJsonSchema;
import org.tavall.dependency.DependencyAccess;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Streamable HTTP MCP projection over the canonical Agent WebMCP operation runtime.
 *
 * <p>This adapter owns protocol translation only. Tool authority, input contracts, execution,
 * provider behavior, and error semantics remain owned by the canonical operation layer.</p>
 */
public final class McpHttpHandler implements HttpHandler, DependencyAccess<AgentWebMcpRuntime> {
    public static final String DEFAULT_PROTOCOL_VERSION = "2025-06-18";
    private static final int MAX_REQUEST_BYTES = 1_048_576;
    private static final Set<String> SUPPORTED_PROTOCOL_VERSIONS = Set.of(
            "2025-03-26",
            "2025-06-18",
            "2025-11-25",
            "2026-07-28"
    );

    private final McpSessionCache sessions = new McpSessionCache();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!HttpRequestSecurityPolicy.originAllowed(exchange)) {
            writeJson(exchange, 403, rpcError(null, -32000, "Origin is not allowed"));
            return;
        }

        switch (exchange.getRequestMethod()) {
            case "POST" -> handlePost(exchange);
            case "GET" -> methodNotAllowed(exchange, "POST");
            case "DELETE" -> terminateSession(exchange);
            default -> methodNotAllowed(exchange, "POST, GET, DELETE");
        }
    }

    private void handlePost(HttpExchange exchange) throws IOException {
        if (!HttpRequestSecurityPolicy.hasJsonContentType(exchange)) {
            writeJson(exchange, 415, rpcError(null, -32600, "MCP POST requests require application/json"));
            return;
        }

        JsonNode request;
        try {
            request = readJson(exchange);
        } catch (RequestTooLargeException exception) {
            writeJson(exchange, 413, rpcError(null, -32600, exception.getMessage()));
            return;
        } catch (IOException exception) {
            writeJson(exchange, 400, rpcError(null, -32700, "Request body must be valid JSON"));
            return;
        }

        if (!request.isObject() || !"2.0".equals(request.path("jsonrpc").asText())) {
            writeJson(exchange, 400, rpcError(request.get("id"), -32600, "Invalid JSON-RPC request"));
            return;
        }

        JsonNode id = request.get("id");
        String method = request.path("method").asText("");
        if (method.isBlank()) {
            writeAccepted(exchange);
            return;
        }

        if (id == null || id.isNull()) {
            writeAccepted(exchange);
            return;
        }

        if ("initialize".equals(method)) {
            initialize(exchange, id, request.path("params"));
            return;
        }

        String protocolVersion = protocolVersion(exchange);
        if (!SUPPORTED_PROTOCOL_VERSIONS.contains(protocolVersion)) {
            writeJson(exchange, 400, rpcError(id, -32004, "Unsupported MCP protocol version: " + protocolVersion));
            return;
        }
        if (!"2026-07-28".equals(protocolVersion) && !validSession(exchange)) {
            writeJson(exchange, 404, rpcError(id, -32001, "Unknown or missing MCP session"));
            return;
        }

        switch (method) {
            case "ping" -> writeJson(exchange, 200, rpcResult(id, Map.of()));
            case "tools/list" -> listTools(exchange, id);
            case "tools/call" -> callTool(exchange, id, request.path("params"));
            default -> writeJson(exchange, 200, rpcError(id, -32601, "Method not found: " + method));
        }
    }

    private void initialize(HttpExchange exchange, JsonNode id, JsonNode params) throws IOException {
        String requestedVersion = params.path("protocolVersion").asText(DEFAULT_PROTOCOL_VERSION);
        String negotiatedVersion = SUPPORTED_PROTOCOL_VERSIONS.contains(requestedVersion)
                ? requestedVersion
                : DEFAULT_PROTOCOL_VERSION;

        String sessionId = "mcp-" + UUID.randomUUID();
        if (!sessions.open(sessionId, negotiatedVersion)) {
            writeJson(exchange, 503, rpcError(id, -32005, "MCP session capacity exceeded"));
            return;
        }
        exchange.getResponseHeaders().set("Mcp-Session-Id", sessionId);
        exchange.getResponseHeaders().set("Mcp-Protocol-Version", negotiatedVersion);

        Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("name", "agent-webmcp");
        serverInfo.put("version", AgentWebMcpRuntime.VERSION);
        serverInfo.put("description", "Local service health, logs, metrics, and lifecycle control through Tavall Agent WebMCP.");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", negotiatedVersion);
        result.put("capabilities", Map.of("tools", Map.of("listChanged", false)));
        result.put("serverInfo", serverInfo);
        writeJson(exchange, 200, rpcResult(id, result));
    }

    private void listTools(HttpExchange exchange, JsonNode id) throws IOException {
        List<Map<String, Object>> tools = runtime().catalog().registrations().stream()
                .filter(McpToolPolicy::allows)
                .map(this::toolView)
                .toList();
        writeJson(exchange, 200, rpcResult(id, Map.of("tools", tools)));
    }

    private void callTool(HttpExchange exchange, JsonNode id, JsonNode params) throws IOException {
        String name = params.path("name").asText("");
        if (!McpToolPolicy.allows(name)) {
            writeJson(exchange, 200, rpcError(id, -32602, "Tool is not exposed through MCP: " + name));
            return;
        }

        JsonNode arguments = params.get("arguments");
        if (arguments == null || arguments.isNull()) {
            arguments = JsonNodeFactory.instance.objectNode();
        }
        OperationExecution execution = runtime().executor().execute(name, arguments);
        JsonNode structured = runtime().objectMapper().valueToTree(execution);
        String text = runtime().objectMapper().writeValueAsString(execution);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", List.of(Map.of("type", "text", "text", text)));
        result.put("structuredContent", structured);
        result.put("isError", execution.status() != OperationExecutionStatus.SUCCESS);
        writeJson(exchange, 200, rpcResult(id, result));
    }

    private Map<String, Object> toolView(OperationRegistration<?, ?> registration) {
        boolean readOnly = registration.descriptor().access() == OperationAccess.READ_ONLY;
        Map<String, Object> annotations = new LinkedHashMap<>();
        annotations.put("readOnlyHint", readOnly);
        annotations.put("destructiveHint", !readOnly);
        annotations.put("idempotentHint", readOnly);
        annotations.put("openWorldHint", true);

        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", registration.descriptor().id().value());
        tool.put("description", registration.descriptor().description());
        tool.put("inputSchema", RecordJsonSchema.forType(registration.inputType()));
        tool.put("annotations", annotations);
        return tool;
    }

    private void terminateSession(HttpExchange exchange) throws IOException {
        String sessionId = exchange.getRequestHeaders().getFirst("Mcp-Session-Id");
        if (!sessions.closeSession(sessionId)) {
            writeJson(exchange, 404, rpcError(null, -32001, "Unknown MCP session"));
            return;
        }
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    private boolean validSession(HttpExchange exchange) {
        String sessionId = exchange.getRequestHeaders().getFirst("Mcp-Session-Id");
        return sessions.protocolVersion(sessionId) != null;
    }

    private String protocolVersion(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Mcp-Protocol-Version");
        if (header != null && !header.isBlank()) {
            return header;
        }
        String sessionId = exchange.getRequestHeaders().getFirst("Mcp-Session-Id");
        if (sessionId == null) {
            return DEFAULT_PROTOCOL_VERSION;
        }
        String negotiated = sessions.protocolVersion(sessionId);
        return negotiated == null ? DEFAULT_PROTOCOL_VERSION : negotiated;
    }

    private JsonNode readJson(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
        if (bytes.length > MAX_REQUEST_BYTES) {
            throw new RequestTooLargeException("MCP request exceeds 1 MiB");
        }
        if (bytes.length == 0) {
            throw new IOException("empty body");
        }
        return objectMapper().readTree(bytes);
    }

    private void methodNotAllowed(HttpExchange exchange, String allowed) throws IOException {
        exchange.getResponseHeaders().set("Allow", allowed);
        writeJson(exchange, 405, rpcError(null, -32600, "Method not allowed"));
    }

    private void writeAccepted(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(202, -1);
        exchange.close();
    }

    private void writeJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = objectMapper().writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static Map<String, Object> rpcResult(JsonNode id, Object result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", id);
        body.put("result", result);
        return body;
    }

    private static Map<String, Object> rpcError(JsonNode id, int code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", id);
        body.put("error", error);
        return body;
    }

    private AgentWebMcpRuntime runtime() {
        return getInstance();
    }

    private ObjectMapper objectMapper() {
        return runtime().objectMapper();
    }

    private static final class RequestTooLargeException extends IOException {
        private RequestTooLargeException(String message) {
            super(message);
        }
    }
}

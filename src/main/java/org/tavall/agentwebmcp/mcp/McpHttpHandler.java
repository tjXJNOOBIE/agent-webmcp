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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Streamable HTTP MCP projection over the canonical Agent WebMCP operation runtime.
 *
 * <p>This adapter owns protocol and MCP Apps presentation translation only. Tool authority,
 * input contracts, execution, provider behavior, and error semantics remain owned by the
 * canonical operation layer.</p>
 */
public final class McpHttpHandler implements HttpHandler, DependencyAccess<AgentWebMcpRuntime> {
    public static final String DEFAULT_PROTOCOL_VERSION = "2025-06-18";
    private static final String MODERN_PROTOCOL_VERSION = "2026-07-28";
    private static final int MAX_REQUEST_BYTES = 1_048_576;
    private static final Set<String> LEGACY_PROTOCOL_VERSIONS = Set.of(
            "2025-03-26",
            "2025-06-18",
            "2025-11-25"
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
            writeJson(exchange, 415, rpcError(null, -32600, "Content-Type must be application/json"));
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
        if (MODERN_PROTOCOL_VERSION.equals(protocolVersion)) {
            if (!validateModernRequest(exchange, request, id, method)) {
                return;
            }
        } else {
            if (!LEGACY_PROTOCOL_VERSIONS.contains(protocolVersion)) {
                writeJson(exchange, 400, unsupportedProtocolVersion(id, protocolVersion));
                return;
            }
            if (!validSession(exchange)) {
                writeJson(exchange, 404, rpcError(id, -32001, "Unknown or missing MCP session"));
                return;
            }
        }

        switch (method) {
            case "server/discover" -> {
                if (!MODERN_PROTOCOL_VERSION.equals(protocolVersion)) {
                    writeJson(exchange, 200, rpcError(id, -32601, "Method not found: " + method));
                } else {
                    discover(exchange, id);
                }
            }
            case "ping" -> writeProtocolResult(exchange, id, Map.of(), false);
            case "tools/list" -> listTools(exchange, id);
            case "tools/call" -> callTool(exchange, id, request.path("params"));
            case "resources/list" -> listResources(exchange, id);
            case "resources/read" -> readResource(exchange, id, request.path("params"));
            default -> writeJson(exchange, MODERN_PROTOCOL_VERSION.equals(protocolVersion) ? 404 : 200,
                    rpcError(id, -32601, "Method not found: " + method));
        }
    }

    private void initialize(HttpExchange exchange, JsonNode id, JsonNode params) throws IOException {
        String requestedVersion = params.path("protocolVersion").asText(DEFAULT_PROTOCOL_VERSION);
        String negotiatedVersion = LEGACY_PROTOCOL_VERSIONS.contains(requestedVersion)
                ? requestedVersion
                : DEFAULT_PROTOCOL_VERSION;

        String sessionId = "mcp-" + UUID.randomUUID();
        if (!sessions.open(sessionId, negotiatedVersion)) {
            writeJson(exchange, 503, rpcError(id, -32005, "MCP session capacity exceeded"));
            return;
        }
        exchange.getResponseHeaders().set("Mcp-Session-Id", sessionId);
        exchange.getResponseHeaders().set("Mcp-Protocol-Version", negotiatedVersion);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", negotiatedVersion);
        result.put("capabilities", capabilities());
        result.put("serverInfo", serverInfo());
        writeJson(exchange, 200, rpcResult(id, result));
    }

    private void discover(HttpExchange exchange, JsonNode id) throws IOException {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("supportedVersions", List.of(MODERN_PROTOCOL_VERSION));
        result.put("capabilities", capabilities());
        result.put("instructions", "Use the bounded Agent WebMCP tools for managed-service observation and lifecycle control, metrics, runtime-agent monitoring, and durable job evidence. The system.status tool can render the Fleet Cockpit MCP App.");
        writeProtocolResult(exchange, id, result, true);
    }

    private Map<String, Object> capabilities() {
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("tools", Map.of("listChanged", false));
        capabilities.put("resources", Map.of("subscribe", false, "listChanged", false));
        capabilities.put("extensions", Map.of(McpAppResource.EXTENSION_ID, McpAppResource.extensionSettings()));
        return capabilities;
    }

    private Map<String, Object> serverInfo() {
        Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("name", "agent-webmcp");
        serverInfo.put("version", AgentWebMcpRuntime.VERSION);
        serverInfo.put("description", "Local service health, logs, metrics, lifecycle control, runtime agents, and durable job evidence through Tavall Agent WebMCP.");
        return serverInfo;
    }

    private void listTools(HttpExchange exchange, JsonNode id) throws IOException {
        List<Map<String, Object>> tools = runtime().catalog().registrations().stream()
                .filter(McpToolPolicy::allows)
                .map(this::toolView)
                .toList();
        writeProtocolResult(exchange, id, Map.of("tools", tools), true);
    }

    private void listResources(HttpExchange exchange, JsonNode id) throws IOException {
        writeProtocolResult(exchange, id, Map.of("resources", List.of(McpAppResource.descriptor())), true);
    }

    private void readResource(HttpExchange exchange, JsonNode id, JsonNode params) throws IOException {
        String uri = params.path("uri").asText("");
        if (!McpAppResource.URI.equals(uri)) {
            writeJson(exchange, 200, rpcError(id, -32602, "Unknown MCP resource URI: " + uri));
            return;
        }
        writeProtocolResult(exchange, id, Map.of("contents", List.of(McpAppResource.content())), true);
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
        writeProtocolResult(exchange, id, result, false);
    }


    private boolean validateModernRequest(HttpExchange exchange, JsonNode request, JsonNode id, String method) throws IOException {
        String methodHeader = exchange.getRequestHeaders().getFirst("Mcp-Method");
        if (!method.equals(methodHeader)) {
            writeJson(exchange, 400, headerMismatch(id, "Mcp-Method", method, methodHeader));
            return false;
        }

        JsonNode params = request.path("params");
        JsonNode meta = params.path("_meta");
        String bodyVersion = meta.path("io.modelcontextprotocol/protocolVersion").asText("");
        String headerVersion = exchange.getRequestHeaders().getFirst("Mcp-Protocol-Version");
        if (!MODERN_PROTOCOL_VERSION.equals(bodyVersion) || !bodyVersion.equals(headerVersion)) {
            writeJson(exchange, 400, headerMismatch(id, "Mcp-Protocol-Version", bodyVersion, headerVersion));
            return false;
        }
        if (!meta.path("io.modelcontextprotocol/clientCapabilities").isObject()) {
            writeJson(exchange, 400, rpcError(id, -32602,
                    "params._meta.io.modelcontextprotocol/clientCapabilities is required and must be an object"));
            return false;
        }

        String expectedName = switch (method) {
            case "tools/call" -> params.path("name").asText("");
            case "resources/read" -> params.path("uri").asText("");
            default -> null;
        };
        if (expectedName != null) {
            String encoded = exchange.getRequestHeaders().getFirst("Mcp-Name");
            String actualName;
            try {
                actualName = decodeHeaderValue(encoded);
            } catch (IllegalArgumentException exception) {
                writeJson(exchange, 400, headerMismatch(id, "Mcp-Name", expectedName, encoded));
                return false;
            }
            if (!expectedName.equals(actualName)) {
                writeJson(exchange, 400, headerMismatch(id, "Mcp-Name", expectedName, actualName));
                return false;
            }
        }
        return true;
    }

    private static String decodeHeaderValue(String value) {
        if (value == null) {
            return null;
        }
        if (!value.startsWith("=?base64?") || !value.endsWith("?=")) {
            return value;
        }
        String encoded = value.substring("=?base64?".length(), value.length() - 2);
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    }

    private Map<String, Object> headerMismatch(JsonNode id, String header, String expected, String actual) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", -32020);
        error.put("message", "HeaderMismatch: " + header + " does not match the JSON-RPC request");
        error.put("data", Map.of(
                "header", header,
                "expected", expected == null ? "" : expected,
                "actual", actual == null ? "" : actual
        ));
        return rpcError(id, error);
    }

    private Map<String, Object> unsupportedProtocolVersion(JsonNode id, String requested) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", -32022);
        error.put("message", "UnsupportedProtocolVersion: " + requested);
        error.put("data", Map.of(
                "supported", List.of(MODERN_PROTOCOL_VERSION),
                "requested", requested == null ? "" : requested
        ));
        return rpcError(id, error);
    }

    private void writeProtocolResult(
            HttpExchange exchange,
            JsonNode id,
            Map<String, ?> result,
            boolean cacheable
    ) throws IOException {
        if (!MODERN_PROTOCOL_VERSION.equals(protocolVersion(exchange))) {
            writeJson(exchange, 200, rpcResult(id, result));
            return;
        }
        Map<String, Object> modern = new LinkedHashMap<>();
        modern.putAll(result);
        modern.put("resultType", "complete");
        if (cacheable) {
            modern.put("ttlMs", 0);
            modern.put("cacheScope", "private");
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        Object existingMeta = modern.get("_meta");
        if (existingMeta instanceof Map<?, ?> existing) {
            existing.forEach((key, value) -> metadata.put(String.valueOf(key), value));
        }
        metadata.put("io.modelcontextprotocol/serverInfo", serverInfo());
        modern.put("_meta", metadata);
        writeJson(exchange, 200, rpcResult(id, modern));
    }

    private Map<String, Object> toolView(OperationRegistration<?, ?> registration) {
        boolean readOnly = registration.descriptor().access() == OperationAccess.READ_ONLY;
        Map<String, Object> annotations = new LinkedHashMap<>();
        annotations.put("readOnlyHint", readOnly);
        annotations.put("destructiveHint", !readOnly);
        annotations.put("idempotentHint", readOnly);
        annotations.put("openWorldHint", true);

        String operationId = registration.descriptor().id().value();
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", operationId);
        tool.put("description", registration.descriptor().description());
        tool.put("inputSchema", RecordJsonSchema.forType(registration.inputType()));
        tool.put("annotations", annotations);
        tool.put("_meta", McpAppResource.toolMetadata("system.status".equals(operationId)));
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
        return rpcError(id, error);
    }

    private static Map<String, Object> rpcError(JsonNode id, Map<String, Object> error) {
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

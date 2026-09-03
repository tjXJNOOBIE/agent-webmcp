package org.tavall.agentwebmcp.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.tavall.agentwebmcp.AgentWebMcpRuntime;
import org.tavall.agentwebmcp.operation.OperationExecution;
import org.tavall.agentwebmcp.operation.OperationExecutionStatus;
import org.tavall.agentwebmcp.mcp.McpHttpHandler;
import org.tavall.dependency.DependencyAccess;
import org.tavall.internal.utils.concurrent.AsyncTask;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight Java HTTP transport for Agent WebMCP.
 *
 * <p>The JDK server owns sockets and HTTP contexts only. Tavall DI owns runtime dependencies,
 * Tavall Concurrency owns request dispatch, and the canonical operation executor owns product
 * behavior. This class must not become a second operation/service framework.</p>
 */
public final class AgentWebMcpHttpServer implements AutoCloseable, DependencyAccess<AgentWebMcpRuntime> {
    public static final String IMPLEMENTATION = "jdk-httpserver";
    public static final String TRANSPORT = "http-json";
    private static final int MAX_REQUEST_BYTES = 1_048_576;

    private final ObjectMapper objectMapper;
    private final HttpServer server;

    private AgentWebMcpHttpServer(Builder builder) {
        this.objectMapper = runtime().objectMapper();
        try {
            this.server = HttpServer.create(new InetSocketAddress(builder.host, builder.port), 0);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to bind Agent WebMCP HTTP server", exception);
        }
        server.setExecutor(command -> AsyncTask.runAsync(command));
        server.createContext("/health", this::health);
        server.createContext("/api/v1/operations", this::operations);
        server.createContext("/mcp", new McpHttpHandler());
        server.createContext("/assets/agent-webmcp-webmcp.js", exchange -> staticResource(
                exchange,
                "/web/agent-webmcp-webmcp.js",
                "text/javascript; charset=utf-8"
        ));
        server.createContext("/assets/agent-webmcp-dashboard.css", exchange -> staticResource(
                exchange,
                "/web/dashboard.css",
                "text/css; charset=utf-8"
        ));
        server.createContext("/assets/agent-webmcp-dashboard.js", exchange -> staticResource(
                exchange,
                "/web/dashboard.js",
                "text/javascript; charset=utf-8"
        ));
        server.createContext("/", exchange -> staticResource(
                exchange,
                "/web/index.html",
                "text/html; charset=utf-8"
        ));
    }

    public static Builder builder() {
        return new Builder();
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public String host() {
        return server.getAddress().getHostString();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private AgentWebMcpRuntime runtime() {
        return getInstance();
    }

    private void health(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            methodNotAllowed(exchange, "GET");
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("version", AgentWebMcpRuntime.VERSION);
        body.put("webServer", IMPLEMENTATION);
        body.put("transport", TRANSPORT);
        body.put("authMode", runtime().context().authMode().name());
        body.put("operationCount", runtime().catalog().size());
        body.put("serviceProvider", runtime().context().serviceProvider().providerName());
        body.put("serviceProviderAvailable", runtime().context().serviceProvider().available());
        body.put("metricsProvider", runtime().context().metricsProvider().providerName());
        body.put("jobProvider", runtime().context().jobProvider().providerName());
        writeJson(exchange, 200, body);
    }

    private void operations(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/api/v1/operations") || path.equals("/api/v1/operations/")) {
            if (!"GET".equals(exchange.getRequestMethod())) {
                methodNotAllowed(exchange, "GET");
                return;
            }
            List<OperationView> operations = runtime().catalog().registrations().stream()
                    .map(OperationView::from)
                    .toList();
            writeJson(exchange, 200, Map.of("operations", operations));
            return;
        }

        if (!path.startsWith("/api/v1/operations/")) {
            writeJson(exchange, 404, errorBody("NOT_FOUND", "Resource not found"));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            methodNotAllowed(exchange, "POST");
            return;
        }

        String encodedId = path.substring("/api/v1/operations/".length());
        String operationId = URLDecoder.decode(encodedId, StandardCharsets.UTF_8);
        JsonNode input;
        try {
            input = readJsonBody(exchange);
        } catch (RequestTooLargeException exception) {
            writeJson(exchange, 413, errorBody("REQUEST_TOO_LARGE", exception.getMessage()));
            return;
        } catch (IOException exception) {
            writeJson(exchange, 400, errorBody("INVALID_JSON", "Request body must be valid JSON"));
            return;
        }

        OperationExecution execution = runtime().executor().execute(operationId, input);
        int status = execution.status() == OperationExecutionStatus.SUCCESS
                ? 200
                : execution.error().httpStatus();
        writeJson(exchange, status, execution);
    }

    private JsonNode readJsonBody(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
        if (bytes.length > MAX_REQUEST_BYTES) {
            throw new RequestTooLargeException("Operation input exceeds 1 MiB");
        }
        if (bytes.length == 0) {
            return JsonNodeFactory.instance.objectNode();
        }
        return objectMapper.readTree(bytes);
    }

    private void staticResource(HttpExchange exchange, String resource, String contentType) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            methodNotAllowed(exchange, "GET");
            return;
        }
        try (InputStream input = AgentWebMcpHttpServer.class.getResourceAsStream(resource)) {
            if (input == null) {
                writeJson(exchange, 404, errorBody("NOT_FOUND", "Resource not found"));
                return;
            }
            byte[] body = input.readAllBytes();
            addCommonHeaders(exchange);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        }
    }

    private void methodNotAllowed(HttpExchange exchange, String allowed) throws IOException {
        exchange.getResponseHeaders().set("Allow", allowed);
        writeJson(exchange, 405, errorBody("METHOD_NOT_ALLOWED", "Allowed method: " + allowed));
    }

    private void writeJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = objectMapper.writeValueAsBytes(body);
        addCommonHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private void addCommonHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("X-Agent-WebMCP-Auth-Mode", runtime().context().authMode().name());
    }

    private static Map<String, Object> errorBody(String code, String message) {
        return Map.of("error", Map.of("code", code, "message", message));
    }

    private static final class RequestTooLargeException extends IOException {
        private RequestTooLargeException(String message) {
            super(message);
        }
    }

    public static final class Builder {
        private String host = "127.0.0.1";
        private int port = 7188;

        private Builder() {
        }

        public Builder host(String host) {
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("host is required");
            }
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            if (port < 0 || port > 65535) {
                throw new IllegalArgumentException("port must be between 0 and 65535");
            }
            this.port = port;
            return this;
        }

        public AgentWebMcpHttpServer build() {
            return new AgentWebMcpHttpServer(this);
        }
    }
}

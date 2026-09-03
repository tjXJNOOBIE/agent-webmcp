# Lightweight Java HTTP Transport

> **Status:** Active

Agent WebMCP uses the JDK `jdk.httpserver` module (`com.sun.net.httpserver.HttpServer`) as its embedded web server. The web server is intentionally a small transport edge, not an application framework.

## Responsibility boundary

The Java HTTP server owns only:

- socket binding and HTTP contexts;
- bounded request-body reading;
- JSON/static-resource response transport;
- HTTP method/status/header projection;
- dispatch into the canonical Java runtime.

It does not own dependency composition, operation registration, service lifecycle behavior, durable job semantics, scheduling, caching, database state, or a second authorization model.

```text
Browser / WebMCP / MCP / HTTP client
        -> JDK HttpServer
        -> HTTP transport adaptation
        -> canonical OperationExecutor
        -> OperationCatalog
        -> operation handlers
        -> providers / repositories
```

## Tavall Java Tools integration

- Tavall DI owns runtime/application dependency composition.
- Tavall Registry owns the canonical operation registry.
- Tavall Concurrency supplies HTTP dispatch and background operation execution.
- Tavall Logging owns application/runtime logging.
- Tavall Cache, Database, EventBus, Reflection, and Scheduler are adopted when Agent WebMCP actually owns those concerns.

The HTTP transport must not introduce Spring, Netty, Jetty, Undertow, another DI container, or another executor framework merely to serve the current API. A richer server dependency is justified only by a concrete transport requirement the JDK server cannot responsibly satisfy, such as a future protocol/streaming need, and requires an explicit architecture change.

## Current routes

- `GET /health`
- `GET /api/v1/operations`
- `POST /api/v1/operations/{operationId}`
- `POST /mcp` for Streamable HTTP MCP JSON-RPC
- `DELETE /mcp` to terminate a session-oriented MCP session
- `GET /mcp` returns `405` because Agent WebMCP does not require a standalone server-to-client SSE listener
- `GET /assets/agent-webmcp-webmcp.js`
- `GET /`

The health payload identifies `webServer=jdk-httpserver` and `transport=http-json` so deployed/runtime evidence shows which edge implementation is active.

## MCP transport

`McpHttpHandler` translates MCP JSON-RPC into the canonical operation executor. It supports the deployed session-oriented MCP protocol versions used by tunnel clients and accepts the current stateless protocol header. Session IDs are opaque and transport-only; they do not become application authorization. `tools/list` and `tools/call` are constrained by `McpToolPolicy`.

The MCP endpoint validates `Origin` when supplied, caps bodies at 1 MiB, returns JSON-RPC errors for protocol failures, and accepts notification requests without manufacturing responses. The normal ChatGPT path is OpenAI Secure MCP Tunnel targeting the private `http://127.0.0.1:7188/mcp` endpoint.

## Security

Default bind is `127.0.0.1`. `NO_AUTH` is intentionally supported only behind an operator-controlled trusted local/private tunnel boundary. For ChatGPT, the intended boundary is OpenAI Secure MCP Tunnel with the tunnel client initiating outbound HTTPS; Agent WebMCP itself remains private. The transport does not emit permissive CORS policy. Operation request bodies are capped at 1 MiB and invalid JSON is rejected before operation execution.

## Lifecycle

`AgentWebMcpHttpServer` owns the JDK server lifecycle. The application shutdown hook closes the server and then shuts down Tavall Concurrency. The JDK server delegates request execution to Tavall Concurrency rather than allocating its own pool.

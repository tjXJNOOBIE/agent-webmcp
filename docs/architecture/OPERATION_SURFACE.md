# Canonical operation surface

## Goal

Agent WebMCP exposes real remote operations to humans and browser agents without implementing the same behavior in four places. The Java operation catalog is the canonical application boundary; console, CLI, HTTP/JSON, and WebMCP are projections.

```text
Targets / service providers
          │
          ▼
Canonical Java Operation Catalog
          │
   ┌──────┼────────┬─────────┐
   ▼      ▼        ▼         ▼
 Web UI   CLI    HTTP/JSON  WebMCP
                              │
                              ▼
                    document.modelContext
```

## Operation contract

The implementation should converge on strong typed equivalents of these concepts rather than map-shaped command blobs:

- **OperationId** — stable namespaced identity such as `service.restart`.
- **OperationDescriptor** — human/agent description, category, safety/read-only annotations, and availability metadata.
- **Input contract** — typed input plus a schema projection suitable for HTTP/WebMCP discovery.
- **Result contract** — typed result with machine-readable status and bounded evidence.
- **OperationContext** — selected target and any capability/authority context required to resolve execution.
- **OperationHandler<I,R>** — one concrete operation responsibility.
- **OperationCatalog** — discovery and lookup of registered typed operations; not an execution god object.
- **OperationExecutor** — admission, context resolution, bounded execution, status/evidence handling, and failure normalization.

Surfaces depend on the catalog/executor contract. A WebMCP tool registration and an HTTP route can describe the same operation differently for transport purposes, but neither reimplements restart/list/log behavior.

## Initial operation families

```text
target.list          target.inspect
service.list         service.inspect       service.status
service.start        service.stop          service.restart      service.reload
job.list             job.inspect           job.logs             job.execute
system.status        metrics.snapshot
```

Exact names may evolve before the Java API is frozen, but stable identity and typed semantics are non-negotiable.

## Authority and lifecycle

Provider adapters own actual host/service interaction. Operations remain bounded and return verified results/evidence. Logs use bounded reads/cursors rather than pretending a browser is a shell. Lifecycle actions remain separate typed operations. This generalizes the useful Tavall Cloud boundary without importing CONTROL, DEVELOPMENT, lane, or environment semantics.

## `NO_AUTH`

`NO_AUTH` means the deployment intentionally performs no Agent WebMCP authentication. The effective security boundary is therefore localhost/private networking plus the user's tunnel/exposure configuration. Every product surface must make that mode visible, especially beside mutating operations.

## WebMCP projection

The browser adapter registers eligible catalog operations through the current secure-context `document.modelContext` imperative API. Registration metadata is derived from the operation descriptor/schema. Read-only operations project the appropriate read-only annotation; mutating operations remain visibly mutating in both human and agent surfaces. Tool execution calls the same operation executor used by other surfaces.

No separate `WebMcpRestartService` implementation should ever exist beside `service.restart`. Humanity has enough duplicate business logic already.

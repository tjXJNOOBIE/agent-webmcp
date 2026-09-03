# Canonical Operation Surface

> **Status:** Active

Agent WebMCP exposes one canonical typed Java operation surface. CLI, HTTP/JSON, WebMCP, MCP, and future operator clients project this same catalog and executor. Transport layers may adapt request/response representation, but they do not own service lifecycle, job scheduling, target discovery, metrics behavior, or independent copies of operation validation.

## Ownership

`OperationCatalog` is the domain registry and is built on Tavall Registry. `OperationExecutor` resolves managed dependencies through Tavall DI and invokes the registered stateless handler. Provider interfaces own external authority. Tavall Concurrency owns generic asynchronous execution.

The current operation IDs are:

- `system.status`
- `metrics.snapshot`
- `target.list`
- `target.inspect`
- `service.list`
- `service.add`
- `service.remove`
- `service.inspect`
- `service.status`
- `service.logs`
- `service.start`
- `service.stop`
- `service.restart`
- `service.reload`
- `job.list`
- `job.inspect`
- `job.logs`
- `job.execute`

New operations must have a stable ID, description, access classification, typed input schema, stateless handler, and typed output/error behavior. Surface-specific operation classes that reimplement an existing operation are forbidden.

## Access

Operations are classified as read-only or mutating in catalog metadata. WebMCP annotations and other projections derive that classification from the catalog rather than maintaining a second list.

## ChatGPT / MCP projection

The canonical catalog remains larger than the public app surface. `McpToolPolicy` exposes 13 tools: system health/metrics, managed-service lifecycle/observability, and read-only durable job state. The job read projection is `job.list`, `job.inspect`, and `job.logs`; machine-side agents may create jobs internally with an optional `agentId`, but `job.execute` remains excluded from MCP.

`service.add` and `service.remove` are canonical panel/HTTP/WebMCP management operations but are excluded from ChatGPT MCP so the remote app cannot enlarge its own managed-service authority. `target.list`, `target.inspect`, and `job.execute` also remain internal. The MCP adapter must not expose generic shell execution, filesystem mutation, arbitrary process launch, or durable job submission. `service.logs` is bounded cursor-based near-live journal access, not an unbounded terminal stream.

The MCP layer is a projection policy and protocol adapter. It does not register replacement handlers or bypass `OperationExecutor`.

## Managed services

The Fleet Cockpit uses a durable managed-service inventory. `service.add` validates that the provider can inspect the requested unit before persisting its service ID. `service.list` returns only enrolled services. `service.remove` only removes the ID from Agent WebMCP state and does not stop, disable, delete, or rewrite the underlying systemd unit.
Enrollment is an authority boundary, not a display filter. Service inspect/status/log/lifecycle operations require the requested service ID to be present in `ManagedServiceRepository` and reject unenrolled IDs with `SERVICE_NOT_MANAGED` before invoking the provider. This prevents MCP-visible lifecycle operations from bypassing service enrollment. A stale enrolled service that the provider reports as not found remains in `service.list` as `UNKNOWN / not-found` until explicitly removed.

Lifecycle operations still act on the authoritative provider and return provider-observed state. A system-level installation therefore needs operating-system authority to perform systemd mutations; an ordinary user-service installation may be read-only depending on PolicyKit. The supported full-control installation mode is the explicit protected system service documented in the repository README.

## Durable jobs

`job.execute` schedules an existing canonical operation. Jobs may carry an optional bounded `agentId` linking the durable record to the machine agent that created it. It is not a generic shell or process-execution escape hatch. Recursive `job.execute` is rejected. Durable job state is owned by `JobRepository`; the default file repository persists atomic JSON records and recovery metadata beneath the configured data directory.

## Validation

At minimum, changes to the canonical surface require unit coverage for catalog/executor behavior plus HTTP, WebMCP, and MCP projection coverage when exposed through those transports. The MCP allowlist requires regression coverage proving internal operations remain undiscoverable and uncallable through the app surface.

# Canonical Operation Surface

> **Status:** Active

Agent WebMCP exposes one canonical typed Java operation surface. CLI, HTTP/JSON, WebMCP, and future operator clients project this same catalog and executor. Transport layers may adapt request/response representation, but they do not own service lifecycle, job scheduling, target discovery, metrics behavior, or independent copies of operation validation.

## Ownership

`OperationCatalog` is the domain registry and is built on Tavall Registry. `OperationExecutor` resolves managed dependencies through Tavall DI and invokes the registered stateless handler. Provider interfaces own external authority. Tavall Concurrency owns generic asynchronous execution.

The current operation IDs are:

- `system.status`
- `metrics.snapshot`
- `target.list`
- `target.inspect`
- `service.list`
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

## Durable jobs

`job.execute` schedules an existing canonical operation. It is not a generic shell or process-execution escape hatch. Recursive `job.execute` is rejected. Durable job state is owned by `JobRepository`; the default file repository persists atomic JSON records and recovery metadata beneath the configured data directory.

## Validation

At minimum, changes to the canonical surface require unit coverage for catalog/executor behavior plus HTTP and WebMCP projection coverage when exposed through those transports.

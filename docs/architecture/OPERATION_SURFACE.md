# Canonical Operation Surface

> **Status:** Active

Agent WebMCP owns one typed Java operation catalog. CLI, HTTP/JSON, WebMCP, MCP, and the Fleet Cockpit project the same `OperationCatalog` and execute through the same `OperationExecutor`. Transport code may adapt representation, but it must not duplicate lifecycle, discovery, job, target, metrics, or validation behavior.

## Ownership

- Tavall DI owns runtime dependency composition and lifecycle-owned providers.
- Tavall Registry owns the canonical operation registry.
- Tavall Concurrency owns bounded asynchronous execution.
- Tavall Scheduler owns future and recurring durable-job scheduling.
- Tavall Logging owns runtime/application logging.
- Provider interfaces own external authority such as systemd and the user's existing Codex CLI.

The catalog contains **21 operations**, **9 mutating**:

- `system.status`
- `metrics.snapshot`
- `target.list`
- `target.inspect`
- `service.list`
- `service.add`
- `service.remove`
- `service.discover`
- `service.inspect`
- `service.status`
- `service.logs`
- `service.diagnostics`
- `service.start`
- `service.stop`
- `service.restart`
- `service.reload`
- `job.list`
- `job.inspect`
- `job.logs`
- `job.execute`
- `job.cancel`

New operations require a stable ID, description, access classification, typed input, stateless handler, typed output/errors, and projection-policy tests. Surface-specific replacement handlers are forbidden.

## Projection policy

HTTP exposes the canonical operation endpoint. Machine-facing projections are intentionally narrower.

**WebMCP exposes 16 operations.** It includes bounded service inventory/observability/lifecycle plus read-only job state, but excludes `target.*`, `service.discover`, `job.execute`, and `job.cancel`. `WebMcpToolPolicy` is the explicit allowlist, and the browser registers a catalog operation only when its surfaces include `WEBMCP`.

**MCP exposes 14 operations.** It includes health/metrics, managed-service observability/lifecycle including diagnostics, and read-only durable job state. It excludes `target.*`, `service.add`, `service.remove`, `service.discover`, `job.execute`, and `job.cancel`.

Discovery, job creation/cancellation, target mutation, generic shell execution, arbitrary process launch, and filesystem mutation are therefore not remotely projected by default merely because the canonical HTTP runtime can perform an operator workflow.

## Managed services and discovery

`ManagedServiceRepository` is the authority boundary for service inspect/status/log/diagnostic/lifecycle operations. Guessing a provider unit ID cannot bypass enrollment: unenrolled IDs fail with `SERVICE_NOT_MANAGED` before provider mutation. `service.remove` removes only Agent WebMCP state; it does not stop, disable, delete, or rewrite the unit.

`service.discover` performs deterministic discovery by listing provider services, re-inspecting each candidate, and accepting only operator/custom paths allowed by `ServiceDiscoveryPolicy` such as `/etc/systemd/system`, `/usr/local/lib/systemd/system`, and `/opt`. Obvious system/vendor services remain skipped.

AI-assisted discovery is explicit opt-in only. It uses the user's already-installed Codex CLI in read-only sandbox mode with a strict structured result containing service IDs only. Every returned ID is re-inspected through the real provider; hallucinated or missing IDs are rejected. Agent WebMCP never installs Codex.

## Service diagnostics

`service.diagnostics` combines provider-observed service details with bounded recent logs and concrete findings. Non-running lifecycle state, impossible RUNNING-without-PID state, masked units, and log-read failures produce findings and make the result unhealthy. Diagnostics never invent synthetic health history.

## Durable jobs

Jobs are service-bound workflows, not arbitrary catalog schedulers. A job chooses exactly one of:

- deterministic `service.start`, `service.stop`, `service.restart`, or `service.reload`; or
- one one-shot Codex prompt scoped to the selected managed service.

A service is mandatory. Prompt and deterministic operation are mutually exclusive. Recurring AI jobs are rejected. Future and recurring deterministic jobs are durably recorded before Tavall Scheduler receives them. Cancellation is allowed for queued/scheduled jobs; a currently running job is not falsely reported cancelled. See [JOBS.md](JOBS.md).

## Validation

Catalog or projection changes require unit coverage plus transport/browser coverage for every affected edge. Current required invariants are 21 canonical operations, 9 mutating, 16 WebMCP, and 14 MCP. Validation must also prove hidden workflow operations remain undiscoverable through WebMCP/MCP.

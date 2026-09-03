# Backend Port and E2E Baseline

This branch is intentionally independent from the dashboard A/B/C design work.

## Runtime boundary

Agent WebMCP is a lightweight Java runtime. The embedded web edge uses the JDK `jdk.httpserver` implementation rather than an application framework. HTTP is transport only: Tavall DI owns composition, Tavall Registry owns the canonical operation catalog, Tavall Concurrency owns generic async execution, and Tavall Logging owns runtime logging. CLI, HTTP, WebMCP, and MCP all project the same `OperationCatalog` and `OperationExecutor`. MCP applies a narrower exposure policy without owning replacement handlers.

The first provider is the local Linux systemd provider. Commands are executed as validated argument vectors through `ProcessBuilder`; no shell command strings are accepted. Provider subprocesses are individually timed, force-terminated on timeout, and terminated when the invoking operation is interrupted.

Current operations:

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

`job.execute` schedules an existing canonical catalog operation rather than accepting shell commands. Jobs are bounded to 1–900 seconds, persisted beneath the Agent WebMCP data directory, and recovered as failed if the runtime terminates while they are queued or running. Job logs are bounded and cursor-based. Recursive `job.execute` scheduling is rejected. Registered operation implementations are required to be locally bounded and interruption-aware; the current systemd provider enforces that contract at its subprocess boundary.

Durable job persistence is separated from scheduling/execution behind `JobRepository`; the default `FileJobRepository` owns bounded file discovery, ID validation, atomic replacement and JSON persistence.
Managed-service enrollment is separately persisted through `ManagedServiceRepository`. All service inspect/status/log/lifecycle operations require enrollment before provider access; `service.add` is the controlled enrollment path and `service.remove` only drops Agent WebMCP state. Missing externally deleted units remain visible as stale `UNKNOWN / not-found` entries so they can be cleaned up from the panel.

`metrics.snapshot` uses JVM and operating-system MXBeans. It does not shell out to platform monitoring utilities.

`NO_AUTH` is first-class and is the only auth mode in this slice. The default bind address is `127.0.0.1`. Users are responsible for the trusted local/private tunnel boundary in front of it. `AGENT_WEBMCP_DATA_DIR` overrides the default durable data directory (`~/.agent-webmcp`).

## Current evidence

The current runtime compiles against the real Tavall DI/Concurrency/Registry/Logging source/API surface and has passed 27 JUnit tests. The canonical catalog contains 18 operations. Streamable HTTP MCP exposes 13 bounded app-facing tools and keeps `service.add`, `service.remove`, `target.*`, and `job.execute` outside the ChatGPT projection. Durable jobs can carry an optional `agentId`, while job creation remains internal.

A clean-user install was exercised from an assembled distribution into a fresh HOME. The installer generated private config and the installed binary served health/MCP from its installed path. The default user-service setup and the explicit `--system-service` setup were both exercised with sandboxed `systemctl` shims. System mode creates a protected root service for the full systemd mutation path; ordinary user mode may be read-only for system services depending on PolicyKit.

OpenAI Secure MCP Tunnel itself is not end-to-end connected in this evidence because no tunnel ID/runtime API key is available in the repository or validation environment. ChatGPT write-action testing additionally depends on an eligible Business/Enterprise/Edu workspace.

The canonical Gradle wrapper path is currently blocked in the Tavall host-local sandbox before project configuration because the Gradle single-use daemon cannot reconnect over sandbox loopback. Direct Java 25 compilation and repository-owned tests are used as bounded evidence until that sandbox/Gradle IPC limitation is repaired. Do not report `./gradlew check` as passing on this state.

## E2E

Repository-owned Playwright tests run against the real lightweight Java HTTP runtime on `http://127.0.0.1`. The WebMCP polyfill is used only by the test browser to provide the current `document.modelContext` contract when the installed browser does not expose native WebMCP. Production page code talks only to `document.modelContext`.

The ordinary browser suite verifies the Java transport identity, 18-operation catalog discovery, canonical WebMCP execution, agent-linked durable job execution/inspection, metrics, recursive-job rejection, unknown-operation rejection, and a real MCP initialize/tools-list/tools-call flow with the 13-tool policy enforced. A dedicated Fleet Cockpit Playwright test clicks Add, Stop, Start, Restart, Logs, and Remove against the real Java HTTP/canonical-operation stack with a stateful external-provider fixture. Separately, the DEVELOPMENT `e2e-chatgpt-web.service` validation canary was driven through real stop/start/restart transitions and cursor-based journal reads through Tavall CONTROL, ending RUNNING. The sandbox cannot grant the Agent WebMCP test process root/PolicyKit authority, so these two evidence tracks are intentionally reported separately rather than falsely claiming the panel process caused the host-level canary mutation.

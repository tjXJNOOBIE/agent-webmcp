# Backend Port and E2E Baseline

This branch is intentionally independent from the dashboard A/B/C design work.

## Runtime boundary

Agent WebMCP is a lightweight Java runtime. The embedded web edge uses the JDK `jdk.httpserver` implementation rather than an application framework. HTTP is transport only: Tavall DI owns composition, Tavall Registry owns the canonical operation catalog, Tavall Concurrency owns generic async execution, and Tavall Logging owns runtime logging. CLI, HTTP and WebMCP all project the same `OperationCatalog` and `OperationExecutor`.

The first provider is the local Linux systemd provider. Commands are executed as validated argument vectors through `ProcessBuilder`; no shell command strings are accepted. Provider subprocesses are individually timed, force-terminated on timeout, and terminated when the invoking operation is interrupted.

Current operations:

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

`job.execute` schedules an existing canonical catalog operation rather than accepting shell commands. Jobs are bounded to 1–900 seconds, persisted beneath the Agent WebMCP data directory, and recovered as failed if the runtime terminates while they are queued or running. Job logs are bounded and cursor-based. Recursive `job.execute` scheduling is rejected. Registered operation implementations are required to be locally bounded and interruption-aware; the current systemd provider enforces that contract at its subprocess boundary.

Durable job persistence is separated from scheduling/execution behind `JobRepository`; the default `FileJobRepository` owns bounded file discovery, ID validation, atomic replacement and JSON persistence.

`metrics.snapshot` uses JVM and operating-system MXBeans. It does not shell out to platform monitoring utilities.

`NO_AUTH` is first-class and is the only auth mode in this slice. The default bind address is `127.0.0.1`. Users are responsible for the trusted local/private tunnel boundary in front of it. `AGENT_WEBMCP_DATA_DIR` overrides the default durable data directory (`~/.agent-webmcp`).

## Current evidence

The Tavall quality migration has been compiled against actual public source snapshots of `tavall-di`, `tavall-concurrency`, and the Tavall Registry base API. The migrated runtime has passed 24 JUnit tests and 7 Playwright Chromium E2E tests. CLI projection has also returned all 16 operations and successful `system.status` execution.

The canonical Gradle wrapper path is currently blocked in the Tavall host-local sandbox before project configuration because the Gradle single-use daemon cannot reconnect over sandbox loopback. Direct Java 25 compilation and repository-owned tests are used as bounded evidence until that sandbox/Gradle IPC limitation is repaired. Do not report `./gradlew check` as passing on this state.

## E2E

Repository-owned Playwright tests run against the real lightweight Java HTTP runtime on `http://127.0.0.1`. The WebMCP polyfill is used only by the test browser to provide the current `document.modelContext` contract when the installed browser does not expose native WebMCP. Production page code talks only to `document.modelContext`.

The browser suite verifies the Java transport identity, catalog discovery, canonical WebMCP execution, durable job execution/inspection, metrics, recursive-job rejection, and unknown-operation rejection.

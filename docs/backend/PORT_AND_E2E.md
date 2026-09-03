# Backend Port and E2E Baseline

This branch is intentionally independent from the dashboard A/B/C design work.

## Ported runtime boundary

The Java runtime owns one canonical typed operation catalog. CLI, HTTP and WebMCP project that catalog instead of implementing lifecycle behavior independently. The first provider is the local Linux systemd provider. Commands are executed as argument vectors through `ProcessBuilder`; no shell command strings are accepted. Provider subprocesses are individually timed, force-terminated on timeout, and terminated when the invoking operation is interrupted.

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

`job.execute` schedules an existing canonical catalog operation rather than accepting shell commands. Jobs are bounded to 1–900 seconds, persisted beneath the Agent WebMCP data directory, and recovered as failed if the runtime terminates while they are queued or running. Job logs are bounded and cursor-based. Recursive `job.execute` scheduling is rejected. Registered operation implementations are required to be locally bounded and interruption-aware; the current systemd provider enforces that contract at its subprocess boundary. The job runner marks a timed-out operation terminal without waiting indefinitely on an uncooperative worker thread.

Durable file storage is separated from job scheduling/execution behind `JobStore`; the default `FileJobStore` owns bounded file discovery, ID validation, atomic replacement and JSON persistence.

`metrics.snapshot` uses JVM and operating-system MXBeans. It does not shell out to platform monitoring utilities.

`NO_AUTH` is first-class and is the only auth mode in this slice. The default bind address is `127.0.0.1`. Users are responsible for the trusted local/private tunnel boundary in front of it. `AGENT_WEBMCP_DATA_DIR` overrides the default durable data directory (`~/.agent-webmcp`).

## E2E

Repository-owned Playwright tests run against the installed Java distribution on a real `http://127.0.0.1` origin. The WebMCP polyfill is used only by the test browser to provide the current `document.modelContext` contract when the installed browser does not expose native WebMCP. Production page code talks only to `document.modelContext`.

`./gradlew test` runs Java unit tests. `./gradlew e2e` installs the pinned browser dependency and runs the browser contract. `./gradlew check` includes both.

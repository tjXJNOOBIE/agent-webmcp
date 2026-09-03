# Durable Service Jobs

> **Status:** Active

Agent WebMCP jobs are durable, service-bound work records. They are designed for scheduled service lifecycle work and optional one-shot use of the operator's existing Codex CLI without turning the runtime into a remote shell.

## Contract

Every job has a target, managed service, structured input, timeout, optional future schedule, optional agent attribution, and exactly one execution mode:

1. **Deterministic service operation:** `service.start`, `service.stop`, `service.restart`, or `service.reload`. These may be immediate, future, or recurring.
2. **Codex prompt:** a one-shot prompt executed through the user's existing Codex CLI for that service. Recurrence is rejected.

The browser never supplies a working directory. For Codex jobs the service must expose a real provider-owned `WorkingDirectory`; Agent WebMCP validates it and passes it with `codex exec -C`. The prompt is sent over stdin, argv is fixed/validated, execution is bounded, and stdout/stderr are bounded. Missing Codex returns `CODEX_UNAVAILABLE`; Agent WebMCP never installs it.

## Lifecycle

Persisted states are `QUEUED`, `SCHEDULED`, `RUNNING`, `SUCCEEDED`, `FAILED`, `TIMED_OUT`, and `CANCELLED`.

Submission is durable before scheduling. Immediate work is queued, future work is scheduled through Tavall Scheduler, and deterministic recurring work returns to `SCHEDULED` only after the completed run is durably recorded. Runtime restart recovers future work. A record left `RUNNING` by an interrupted runtime is recovered as `FAILED` with explicit evidence rather than pretending the process completed.

Execution uses a bounded interruptible worker from Tavall Concurrency. Timeout cancels/interupts that worker and persists `TIMED_OUT`. Queued/scheduled jobs may be cancelled before execution. Once a job is `RUNNING`, `job.cancel` returns `JOB_NOT_CANCELLABLE` because this runtime cannot truthfully guarantee rollback of a service operation already in progress.

## Evidence and UI

Job records and bounded log entries are stored under the configured Agent WebMCP data directory. `job.list`, `job.inspect`, and `job.logs` are read-only projections available to the Fleet Cockpit, WebMCP, and MCP policy. `job.execute` and `job.cancel` are operator HTTP workflows and intentionally remain outside WebMCP/MCP.

The Fleet Cockpit Jobs workspace provides Create/View modes, service selection, deterministic action or optional Codex prompt, now/future/recurring scheduling, Codex readiness, durable history, cancellation for cancellable states, and an Execution Trace backed only by `job.logs`.

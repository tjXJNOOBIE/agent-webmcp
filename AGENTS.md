# Agent WebMCP repository instructions

This repository is the authoritative source for Agent WebMCP. `main` is production source; merge and deployment are separate decisions.

## Mandatory quality preflight

Every human or AI session that may inspect, propose, implement, modify, test, review, merge, or deploy repository changes must begin by reading every regular file beneath `docs/quality` recursively. Run from the repository root:

```bash
bash scripts/quality-preflight.sh --print
```

Record `QUALITY_DOCUMENT_COUNT` and `QUALITY_MANIFEST_SHA256` in the first visible work update and in any pull-request or review validation record. Rerun the complete preflight whenever the branch head changes during the session or any file beneath `docs/quality` changes. If the script cannot run, perform the equivalent deterministic recursive enumeration, full read, Git-blob manifest, and SHA-256 calculation manually.

A session that did not complete the preflight must not claim architecture compliance, review completeness, or merge readiness.

## Required references

After the quality preflight, read and follow:

- `docs/architecture/OPERATION_SURFACE.md` for the canonical operation model;
- `docs/architecture/HTTP_TRANSPORT.md` for the lightweight Java web-server boundary;
- `docs/backend/PORT_AND_E2E.md` for current backend/runtime scope and validation boundaries;
- relevant design documents beneath `docs/design`;
- open and closed GitHub issues and pull requests that overlap the work.

Inspect the existing implementation before proposing a replacement. Extend the owning system instead of creating a parallel implementation. Verify APIs, dependencies, schemas, and platform behavior against real source. Generated confidence is not an API.

## Git and review

`docs/quality/GIT_WORKFLOW.md` is the shared Tavall Studios workflow and applies in full. In particular:

- work normally continues on a focused `working/*` branch and its existing pull request;
- GitHub is the authoritative review/work ledger;
- push coherent checkpoints rather than leaving useful source only in DEVELOPMENT workspaces;
- keep pull requests Draft while implementation, validation, dependencies, or descriptions are incomplete;
- preserve explicit stack ancestry instead of flattening dependent work;
- every commit uses the Tavall typed subject plus `Reason`, `Changes`, and `Validation` body;
- validation claims must state exactly what ran, including `Not run: <reason>` where applicable;
- accountable human review or Owner Self-Review is separate from advisory AI review;
- merge is separate from production deployment.

No GitHub-hosted execution compute is used for Agent WebMCP validation. Repository-owned validation runs on Tavall/local infrastructure; GitHub is SCM, review, issue, and check-status authority.

## Tavall Java platform

Agent WebMCP is a Tavall-owned Java consumer and follows `docs/quality/JAVA_TOOLS_ADOPTION.md`.

- Tavall DI is the required first-party runtime composition baseline.
- Tavall Registry owns reusable typed keyed runtime catalogs.
- Tavall Concurrency owns generic asynchronous execution and coordination.
- Tavall Logging owns application/runtime logging.
- Other Tavall Java tools are adopted when this repository actually owns their concern.
- Do not create project-local replacements for a concern already owned by a Tavall Java tool. If a required shared capability is missing, improve or stack on the owning tool instead.

Ordinary Tavall-managed production behavior classes do not constructor-inject Tavall-managed application dependencies. Runtime/bootstrap code may register externally created configuration, adapters, or test overrides into the owning Tavall DI map. Builders assemble typed configuration/output; they are not an alternate container.

## Product architecture

The Java typed operation catalog is canonical. CLI, HTTP/JSON, the human web console, and WebMCP project the same descriptors, schemas, executor, and results. Surface-specific copies of lifecycle behavior are forbidden.

The embedded web server is the JDK `HttpServer`. Keep it a transport adapter. It may bind sockets, map HTTP contexts, bound request bodies, and serialize responses, but it may not become an application framework or duplicate operation/provider behavior. Do not add Spring, Netty, Jetty, Undertow, a second DI container, or another executor framework without a concrete transport requirement and an explicit architecture change. HTTP dispatch uses Tavall Concurrency.

Providers retain real operating-system/service authority. The operation layer validates and delegates; it does not invent runtime state. Production fixtures and fake service state are forbidden. Test fakes are permitted only at genuine external/provider boundaries.

Use precise role names such as `Service`, `Handler`, `Orchestrator`, `Router`, `Resolver`, `Registry`, `Repository`, `Builder`, `Reader`, `Writer`, `Runtime`, and `Bootstrap`. Do not introduce `*Manager`, `util`, `misc`, or `common` ownership buckets.

Durable state that survives restart has an explicit persistence/repository boundary and documented corruption/recovery behavior. Cache and registry state are not durable truth. Async work has an owner, timeout/cancellation semantics, shutdown behavior, and bounded resource use.

`NO_AUTH` is a first-class deployment mode, not a pretend security feature. Default bind remains local. Clearly warn that an operator choosing `NO_AUTH` is responsible for placing the service behind a trusted local/private tunnel boundary they control. Do not quietly broaden network exposure.

Raw shell command strings are not an execution API. External commands use validated argument vectors, bounded timeouts, bounded output, and explicit failure mapping. Never add a generic shell escape hatch to satisfy a product operation.

## Testing and evidence

Use JUnit 5 and real production behavior where practical. Test fakes represent external boundaries, not the thing under test. Test packages mirror production packages and normal test classes use `<ProductionClass>Test`.

For touched behavior, cover meaningful success, rejection, provider/infrastructure failure, cleanup, timeout/cancellation, restart/recovery, and serialization/projection boundaries as applicable. WebMCP/browser tests must execute the real HTTP/runtime projection, not a fixture-only duplicate.

Architecture tests are executable policy. New platform or architecture rules should fail on a deliberately invalid fixture or source shape and pass on the intended shape where practical.

## Durable continuation

Assume interactive sessions and execution windows can terminate unexpectedly. Prefer small bounded durable operations with one concrete deliverable. Before continuation, inspect existing jobs, lanes/environments, workspaces, Git state, PR heads, and evidence. Resume valid work instead of recreating it.

Checkpoint branch/PR, exact SHA, validation evidence, blocker, and next action frequently. Source existing in a workspace is not completion. A source-changing task normally finishes by committing, pushing, reconciling the owning PR, recording exact-head evidence, and performing intended staging fan-in. When GitHub credentials block push/reconciliation, record the exact credential/provider blocker and keep the local commit explicitly `UNRECONCILED_WORK`; do not repeatedly burn execution time against the same failure.

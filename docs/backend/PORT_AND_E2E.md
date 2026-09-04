# Backend Port and E2E Baseline

## Runtime boundary

Agent WebMCP uses the JDK `HttpServer` only as a lightweight transport edge. Tavall DI owns composition, Tavall Registry owns the 23-operation catalog, Tavall Concurrency owns bounded async execution, Tavall Scheduler owns future/recurring jobs, and Tavall Logging owns runtime logging. CLI, HTTP, WebMCP, MCP, and the Fleet Cockpit all execute the same canonical handlers.

The local systemd provider runs validated argv through `ProcessBuilder`; production code never invokes shell command strings. Process stdin/stdout/stderr are bounded to 1 MiB, timeouts terminate provider processes, and interrupted job workers propagate interruption to provider execution. Metrics use JVM/OS MXBeans rather than shell utilities.

The canonical catalog contains **23 operations / 9 mutating**. WebMCP projects **18** operations. Streamable HTTP MCP projects **16**. `agent.list` and `agent.inspect` are read-only machine-facing projections. `service.discover`, `job.execute`, `job.cancel`, and target workflows are not exposed to browser agents; MCP is narrower still and excludes managed-inventory mutation.

## Discovery, diagnostics, and jobs

Deterministic service discovery inspects actual provider metadata and registers only operator/custom service paths. Optional Codex discovery is manual, read-only, schema-constrained, ID-only, and re-inspected against the provider. Codex absence is a typed optional-capability state and does not prevent installation or deterministic discovery.

`service.diagnostics` reports provider details, bounded logs, health, and concrete findings such as lifecycle/PID mismatch, masked unit state, or log-read failure.

Jobs require a managed service and either one deterministic lifecycle operation or a one-shot Codex prompt. Future/recurring deterministic jobs use Tavall Scheduler and durable state. Restart recovery, interrupted-RUNNING recovery, queued/scheduled cancellation, RUNNING cancellation refusal, timeout interruption, durable logs, and Codex service scoping are covered by repository tests.

## Reproducible Tavall source dependencies

Tavall repositories currently do not provide Git tags matching the transitive `org.tavall:*:1.0.0` coordinates required by Gradle source control. Repository-owned bootstrap avoids an environment-only workaround:

- `scripts/ci/tavall-source-deps.tsv` pins audited repository SHAs;
- `scripts/ci/prepare-tavall-sources` clones/fetches those exact commits into ignored `.tavall-source-deps/` and verifies each checkout;
- `settings.gradle.kts` conditionally includes the prepared composite builds while retaining source-control fallback mappings;
- `scripts/ci/run` is the canonical full validation entrypoint.

## E2E evidence

Repository Playwright tests run against the real Java HTTP runtime. The browser-only WebMCP polyfill supplies `document.modelContext` in Chromium when native WebMCP is absent; production code talks only to `document.modelContext`.

The runtime suite proves health/catalog identity, 23/9 counts, exact 18-tool WebMCP projection, exact 16-tool MCP projection, live metrics, managed-service authority rejection, unsafe job-input rejection, and unknown-operation rejection.

The stateful Fleet Cockpit suite exercises the approved Round 4/7 production surfaces with real browser behavior: deterministic and opt-in AI discovery, Service Control with bounded console/diagnostics and truthful current lifecycle evidence, Jobs Create execution summary, Jobs View history/trace/cancellation, Operations, Catalog, Activity plus current-session resource timeline, Agents A with real installed-Codex inventory, heartbeat/runtime/capability inspection, Target Switcher C with Target/Agent/Services/Heartbeat columns, Settings tabs, and document-overflow/internal-scroll acceptance at 390px.

A dedicated stateful provider fixture drives the accepted Fleet Cockpit through deterministic and AI discovery, Service Control lifecycle/diagnostics/logs, immediate/future/recurring/Codex jobs, execution trace, cancellation, operation registry, projection matrix, activity/resource timeline, real Codex-agent registry with target/heartbeat comparison, Settings tabs, and mobile horizontal-overflow acceptance.

The install contract additionally runs the built distribution with a fake systemd provider and a PATH that contains no Codex binary. It proves deterministic install-time discovery succeeds, config/state permissions are owner-only, and Agent WebMCP installation does not depend on Codex. A separate tunnel bundle verification downloads the pinned official OpenAI `tunnel-client` v0.0.14 release, verifies its SHA-256, checks the expected binary/license metadata, and executes the client quickstart surface without using real tunnel credentials.

The full validation command is `bash scripts/ci/run`. Do not report completion from a commit other than the exact pushed PR head.

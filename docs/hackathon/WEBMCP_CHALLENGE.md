# WebMCP Challenge submission evidence

Agent WebMCP is being built for the OpenAI WebMCP Challenge submission window that runs from August 25, 2026 through September 3, 2026 at 1:00 PM Pacific Time. This document exists so judges and maintainers can distinguish the browser-native WebMCP work from the separate MCP transport and verify the implementation without relying on marketing language.

## What is WebMCP in this project?

The human operations page served by the Java runtime loads `src/main/resources/web/agent-webmcp-webmcp.js`. That production script reads the canonical operation catalog from `GET /api/v1/operations` and registers each operation with the browser using `document.modelContext.registerTool(...)`. Tool execution calls the same canonical Java operation endpoint used by the human console instead of reimplementing service behavior in JavaScript.

This is distinct from the optional `/mcp` Streamable HTTP endpoint. The `/mcp` endpoint exists for ordinary MCP clients; the hackathon browser surface uses `document.modelContext` directly.

## Why WebMCP is a strong fit

Traditional infrastructure panels force an agent to infer intent from buttons, tables, labels, and DOM state. Agent WebMCP exposes the exact typed operation surface the human UI already uses, including JSON Schema input contracts and read-only annotations. A person can keep the visual Fleet Cockpit open while an agent discovers and executes the same bounded operations directly, without brittle click automation or a second shadow API.

The result is a shared operations surface: humans keep context, visibility, and control while agents can reliably inspect runtime health, read service state and logs, and invoke explicitly allowed lifecycle operations.

## Current WebMCP implementation

Production registration lives in:

- `src/main/resources/web/agent-webmcp-webmcp.js`

The registration flow:

1. Requires `document.modelContext` from a WebMCP-capable browser.
2. Fetches the runtime-owned operation catalog from `/api/v1/operations`.
3. Registers every catalog operation with `document.modelContext.registerTool(...)`.
4. Uses the canonical operation description and JSON Schema as the WebMCP tool contract.
5. Derives `annotations.readOnlyHint` from canonical operation access metadata.
6. Forwards WebMCP cancellation through the supplied `AbortSignal` to the HTTP execution request.
7. Executes through `POST /api/v1/operations/{operationId}`, preserving the same validation and provider path used by the human console.

Browser E2E coverage lives under `e2e/`. The test browser uses the WebMCP polyfill only when native WebMCP is unavailable in the installed automation browser; production page code itself only talks to `document.modelContext`.

## Submission-window provenance

The repository itself was created during the challenge period. Its initial commit is:

- `1d9c6eaf90f6d59c39e6b3a4f25999a4855c743a` — 2026-09-02T21:30:34-07:00 — Initial commit

The implementation branch then records these dated commits during the submission period:

- `fb815fdd7288c1235e6a8dc6ab4d40218737a664` — 2026-09-03T12:52:47Z — Port operation runtime and browser E2E
- `d8a1e5fcc87177a629c72860a7e0c436bc188a0b` — 2026-09-03T13:44:02Z — Refactor: Align runtime with Tavall Java architecture
- `9a7f587e458caa5d8e5cbf8b1f8750f25ab28805` — 2026-09-03T14:06:51Z — Added: Expose bounded MCP app
- `8edbd949a1c6536f9122e5c6aa0c27ddb5e6776c` — 2026-09-03T14:58:23Z — Added: Manage service fleet and agent jobs

The final compliance/standards commit follows these commits on the same branch.

## Validation evidence

A September 3 validation pass compiled the current application source together with fresh `main` source snapshots of the public Tavall Logging, DI, Concurrency, Registry, and `abstract-cache-system` dependencies using Java 25. This avoided relying on stale compiled validation output.

Current evidence:

- Java 25 / Gradle compilation and JUnit suite: 48/48 tests passed, 0 failed.
- Runtime Playwright suite: 6/6 passed, including the 23-operation catalog, exact 18-tool WebMCP projection, exact 16-tool MCP projection, canonical execution, metrics, managed-service authority, and unsafe-operation rejection.
- Stateful Fleet Cockpit Playwright suite: 3/3 passed. It covers deterministic and explicit-AI discovery, Service Control logs/diagnostics/lifecycle evidence, deterministic/future/recurring/Codex jobs, execution trace and cancellation, Operations, Catalog, Activity/real-session metrics, real installed-Codex Agent Registry state and capabilities, the Target/Agent/Services/Heartbeat comparison, Settings, and 390px mobile overflow.
- `scripts/verify-webmcp-challenge.sh`: passed.
- Production WebMCP JavaScript syntax and generated test polyfill syntax: passed.
- `git diff --check`: passed.

The repository-owned `scripts/ci/run` path now prepares pinned Tavall source composites, runs Gradle tests, validates installation without Codex, executes both Playwright suites, runs the WebMCP challenge static checks, and finishes with `git diff --check`.

## Judge verification

For normal local verification through the repository-owned Gradle/Tavall composite wrapper:

```bash
bash ./scripts/gradle test
bash ./scripts/gradle e2e
sh scripts/verify-webmcp-challenge.sh
```

For browser verification, open the deployed live URL in ChatGPT's in-app browser or a WebMCP-enabled Chrome build, then have the agent discover the page tools and invoke a read-only operation such as `system.status` before testing any service mutation.

The repository is intentionally public and carries a root `LICENSE` file so the submission can be evaluated and built without private source access.

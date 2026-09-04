# Agent WebMCP

Agent WebMCP is a lightweight Java operations server that exposes one canonical Tavall-owned operation runtime through CLI, HTTP/JSON, WebMCP, and a bounded Model Context Protocol (MCP) app surface.

The application deliberately stops short of becoming a remote shell. The app-facing MCP surface is for **managed service control, bounded near-live service logs, general system/service health, and read-only machine-agent job state**.

## WebMCP Challenge build

This repository contains a browser-native WebMCP implementation for the OpenAI WebMCP Challenge. The production page registers the canonical operation catalog with `document.modelContext.registerTool(...)`; it does **not** treat the separate `/mcp` endpoint as a substitute for WebMCP. The browser tools reuse the same JSON Schemas, access metadata, validation, and Java operation executor as the human Fleet Cockpit.

Submission/implementation evidence, dated commit provenance, and judge verification steps are documented in [`docs/hackathon/WEBMCP_CHALLENGE.md`](docs/hackathon/WEBMCP_CHALLENGE.md). The repository is released under the root [`LICENSE`](LICENSE).

![Browser-Native WebMCP Registration](docs/hackathon/devpost/webmcp-registration-flow.svg)

## Current capabilities

The canonical runtime owns **23 operations / 9 mutating**. WebMCP exposes an explicit **18-tool** browser projection, while the narrower ChatGPT/MCP app exposes **16 tools** for health/metrics, managed-service observability/lifecycle, diagnostics, and read-only job state.

| MCP tool | Purpose | Access |
| --- | --- | --- |
| `system.status` | Runtime/provider/auth health summary | Read |
| `metrics.snapshot` | JVM/OS metrics snapshot | Read |
| `agent.list` | List observed runtime agents and heartbeat/target state | Read |
| `agent.inspect` | Inspect one runtime agent, version, target, heartbeat, and capabilities | Read |
| `service.list` | List services enrolled in Agent WebMCP | Read |
| `service.inspect` | Inspect one service in detail | Read |
| `service.status` | Read current service state | Read |
| `service.logs` | Read bounded cursor-based journal output | Read |
| `service.diagnostics` | Run bounded provider-backed service diagnostics | Read |
| `service.start` | Start a service | Write |
| `service.stop` | Stop a service | Write |
| `service.restart` | Restart a service | Write |
| `service.reload` | Reload a service | Write |
| `job.list` | List recent durable machine-agent jobs | Read |
| `job.inspect` | Inspect one job and its linked agent/result | Read |
| `job.logs` | Read bounded job lifecycle logs | Read |

The canonical catalog is intentionally larger than either machine-facing projection. WebMCP exposes 18 explicitly allowed operations, including read-only agent inventory/inspection. MCP is narrower at 16 tools: it includes `service.diagnostics`, service lifecycle/observability, system metrics/status, read-only runtime-agent state, and read-only durable job state, while keeping discovery, job execution/cancellation, target workflows, and managed-inventory mutation outside the remote app surface.

![One Catalog, Multiple Surfaces](docs/hackathon/devpost/one-catalog-multiple-surfaces.svg)

Managed-service enrollment remains the lifecycle authority boundary. `service.inspect`, `service.status`, `service.logs`, `service.diagnostics`, `service.start`, `service.stop`, `service.restart`, and `service.reload` reject unenrolled IDs with `SERVICE_NOT_MANAGED` before provider access. `service.discover` can register deterministic custom/operator candidates; optional Codex-assisted discovery is explicit, read-only, accepts only service IDs, and re-inspects every returned ID through the real provider.

Jobs are service-bound. Deterministic jobs can run only `service.start`, `service.stop`, `service.restart`, or `service.reload`; one-shot AI jobs use the user's existing Codex CLI with a provider-owned service working directory. Recurring AI jobs are rejected. There is no generic shell tool, arbitrary browser-supplied working directory, or MCP filesystem/process escape hatch.

![Bounded Safety Model](docs/hackathon/devpost/bounded-safety-model.svg)

![Services and Durable Jobs](docs/hackathon/devpost/service-job-architecture.svg)

`service.logs` is near-live through bounded repeated reads and cursors. It is not an unbounded interactive terminal stream.

### Fleet Cockpit interaction flow

![Human + Agent Operations Flow](docs/hackathon/devpost/human-agent-operations-flow.svg)

![Human-Agent Collaboration Loop](docs/hackathon/devpost/human-agent-collaboration-loop.svg)


## Architecture

The JDK server is intentionally only a transport edge. Tavall Java tools own application concerns.


The local HTTP endpoints are:

- `GET /health`
- `GET /api/v1/operations`
- `POST /api/v1/operations/{operationId}`
- `POST /mcp` for Streamable HTTP MCP JSON-RPC
- `GET /mcp` returns `405` because this server does not need a standalone server-to-client SSE listener
- `/` and `/assets/...` for the browser/WebMCP surface

The MCP adapter supports the deployed session-oriented MCP versions used by current tunnel clients and accepts the newer stateless protocol version header. It validates browser `Origin` when present and defaults to loopback binding.

## Install the application

### Requirements

- Linux with systemd for the default service-provider path
- Java 25 or newer
- Git
- Network access during the Gradle source-dependency build
- `curl` for the verification helper
- `curl`, `unzip`, and `sha256sum` when installing the bundled OpenAI Secure MCP Tunnel companion

Clone and install:

```bash
git clone https://github.com/tjXJNOOBIE/agent-webmcp.git
cd agent-webmcp
./scripts/install.sh
```

The default install writes:

```text
~/.local/share/agent-webmcp/             application distribution
~/.config/agent-webmcp/agent-webmcp.env configuration
~/.local/state/agent-webmcp/             durable state
~/.config/systemd/user/agent-webmcp.service
```

Default configuration:

```text
AGENT_WEBMCP_HOST=127.0.0.1
AGENT_WEBMCP_PORT=7188
AGENT_WEBMCP_DATA_DIR=$HOME/.local/state/agent-webmcp
```

The installer enables and starts the systemd **user** service. Verify it with:

```bash
systemctl --user status agent-webmcp.service
curl -fsS http://127.0.0.1:7188/health
./scripts/verify-install.sh
```

The user-service mode is suitable for health, metrics, catalog, jobs, and whatever systemd reads the account is allowed to perform. On a normal Linux host, system-level `start`/`stop`/`restart`/`reload` is usually denied by PolicyKit. For the **full system-service control path** on a dedicated operations machine, build as your normal user and then install the already-built distribution as a protected root system service:

```bash
bash ./scripts/gradle installDist
sudo ./scripts/install.sh \
  --dist "$PWD/build/install/agent-webmcp" \
  --system-service
sudo systemctl status agent-webmcp.service
curl -fsS http://127.0.0.1:7188/health
```

System mode defaults to `/opt/agent-webmcp`, `/etc/agent-webmcp`, and `/var/lib/agent-webmcp`, and installs `/etc/systemd/system/agent-webmcp.service`. The unit keeps `NoNewPrivileges`, `PrivateTmp`, `ProtectHome`, and `ProtectSystem=strict`, with only the Agent WebMCP state directory writable. It nevertheless has root service-control authority, so keep the HTTP/MCP bind on loopback and use the private tunnel boundary. **Do not expose a `NO_AUTH` root control plane directly to the network.**

For a portable/test install without systemd:

```bash
./scripts/install.sh --no-service
set -a
. ~/.config/agent-webmcp/agent-webmcp.env
set +a
~/.local/share/agent-webmcp/bin/agent-webmcp serve
```

You can override the install paths and bind address with `--prefix`, `--config-dir`, `--state-dir`, `--host`, and `--port`. Keep `127.0.0.1` unless you have an explicit private-network design. The intended ChatGPT path uses Secure MCP Tunnel instead of a public bind.


## Create the ChatGPT app and test the connection

Current ChatGPT custom-app setup scans the MCP server tools and creates a draft app. With Secure MCP Tunnel, choose the **Tunnel** connection mode and select/paste your `tunnel_id`; do not paste the private `127.0.0.1` URL into ChatGPT.

1. Verify `http://127.0.0.1:7188/health`.
2. Verify `tunnel-client doctor --explain`.
3. Verify `agent-webmcp-tunnel.service` is running and its logs show the tunnel runtime is healthy.
4. Only then enable Developer Mode in ChatGPT where your plan/workspace supports it.
5. Open **Settings / Workspace settings → Apps → Create**.
6. Name the app **Agent WebMCP**.
7. Choose **Tunnel** and select or paste the created `tunnel_...` ID.
8. Use **No Auth** for the MCP target. **No Auth is the supported default for Agent WebMCP behind Secure MCP Tunnel.** Agent WebMCP does not expose an OAuth authorization server; the tunnel runtime key authenticates only the local `tunnel-client` to OpenAI and must never be pasted into ChatGPT app authentication fields.
9. Click **Scan Tools**. The scan should discover exactly the 16 app-facing tools listed above.
10. Click **Create**. The app should appear as a draft/development app.
11. Start a new chat with Agent WebMCP selected and test a read-only call first, for example: `Show the current Agent WebMCP system status.`
12. Then test logs: `Show the latest logs for demo.service.`
13. Only after read paths work, test a write action such as restart against a disposable/non-production service.

If the tunnel exists in Platform but does not appear or scan in ChatGPT, check these in order:

1. the tunnel is scoped to the same ChatGPT workspace;
2. the operator/runtime-key principal has **Tunnels Read + Use**;
3. `agent-webmcp.service` is healthy on `127.0.0.1:7188`;
4. `agent-webmcp-tunnel.service` is actually running;
5. `tunnel-client doctor --explain` succeeds with the same runtime key used by the daemon.


### Plan/workspace note

OpenAI currently documents full custom MCP write/modify actions for Business and Enterprise/Edu workspaces. Pro can connect custom MCP with read/fetch permissions in developer mode. **Plus is not currently listed as supporting custom MCP developer-mode apps**, so a personal Plus workspace may reject app creation even when the local server and Secure MCP Tunnel are healthy. A Business/Enterprise/Edu workspace is the supported path for testing `service.start/stop/restart/reload` from ChatGPT today.

GitHub **plugin marketplace import is also a workspace-admin/owner workflow currently documented for Business and Enterprise/Edu workspaces**. The public Plugins Directory is visible more broadly, but that does not grant a personal Plus workspace the ability to import this repository as a custom GitHub marketplace. If Workspace settings does not expose **Plugins → Add → Import marketplace**, use a supported Business/Enterprise/Edu workspace for the ChatGPT plugin test; the same repository package remains usable as a local Codex plugin independently.

## Plugin package

This repository includes an installable plugin package at:

```text
plugins/agent-webmcp/
.agents/plugins/marketplace.json
```

The package follows the current OpenAI plugin repository shape with `.codex-plugin/plugin.json`, marketplace metadata, and a focused Agent WebMCP skill. It intentionally does **not** declare `.mcp.json`: current OpenAI behavior can mark imported plugins that declare MCP servers as Desktop only, which prevents the ChatGPT-web experience we want. Until a real workspace app ID exists, the package is intentionally **skill-only and independently installable**; app-backed tools are added only after `.app.json` can contain the real Agent WebMCP app ID.

A ChatGPT plugin that wraps the **workspace custom app** needs the app ID assigned by ChatGPT after the draft app is created. That ID cannot be truthfully pre-generated in this repository. `plugins/agent-webmcp/app-binding.example.json` contains the exact binding shape. Once the app exists, copy it to `.app.json`, replace the placeholder with the real workspace app ID, and add `"apps": "./.app.json"` to the plugin manifest.

### Make the plugin clickable in a workspace

A workspace admin can import the marketplace from **Workspace settings → Plugins → Add → Import marketplace**. Use `https://github.com/tjXJNOOBIE/agent-webmcp` as **Source**, leave **Path empty** because `.agents/plugins/marketplace.json` is at the repository root, and set **Branch** to `working/backend-operation-e2e-20260903` for the current PR #3 build (or the promoted staging branch once that merge is pushed). Do not put the branch into the Source URL. After import, open the imported plugin and set its workspace **Installation policy** to **Available** so eligible members see **Install plugin**. GitHub marketplace import and plugin installation are separate from creating/authorizing the underlying Agent WebMCP custom app.


## Development and validation

Run the required quality preflight first:

```bash
bash scripts/quality-preflight.sh --print
```

Canonical Gradle validation uses the repository wrapper so Tavall composite dependencies resolve through stable JAR variants:

```bash
bash ./scripts/gradle test
bash ./scripts/gradle e2e
bash ./scripts/gradle check
```

The repository wrapper isolates Gradle user/project caches and forces JAR variants across the pinned Tavall composite build. This keeps validation independent of warm included-build class directories and unrelated workspace cache locks.

See `docs/architecture/OPERATION_SURFACE.md`, `docs/architecture/HTTP_TRANSPORT.md`, and `docs/backend/PORT_AND_E2E.md` for the owning runtime contracts.

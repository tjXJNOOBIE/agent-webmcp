# Agent WebMCP engineering guidance

This repository follows Tavall engineering, architecture, quality, validation, and Git conventions. Keep this file short; detailed rationale belongs in focused project docs.

## Working rules

- Inspect repository, branch/PR, workspace/job, and applicable Tavall guidance before changing source. Resume valid work instead of recreating it.
- Use `working/*` or `agent/*` branches and preserve exact-head discipline before validation, commits, pushes, and PR mutations.
- Long work uses small bounded durable jobs. Checkpoint branch/PR, exact SHA, job/workspace identity, validation evidence, blockers, and next action.
- GitHub is SCM/review authority; Tavall/local infrastructure owns builds, tests, validation, and execution. Do not add GitHub-hosted execution as the normal compute path.
- Tests and validation are required evidence, but authoring order is flexible. Self-review complete changes and fix findings in the same pass.

## Architecture

- The canonical typed Java operation catalog is the behavior boundary. Web UI, CLI, HTTP/JSON, and WebMCP are adapters over it, never parallel implementations.
- Operations have stable identity, description, typed input/result contracts, target/context requirements, availability, and execution/status semantics.
- Prefer small Service / Handler / Orchestrator / Router / Registry responsibilities. Avoid generic `*Manager`, `util`, `misc`, and god-service dumping grounds.
- Keep authority and side effects behind explicit provider/service boundaries. Surfaces may validate/project/render, but must not acquire hidden host authority.
- Use Tavall DI conventions for application dependencies. Do not turn dependency-bearing constructors into general application DI; builder-owned construction and proper Tavall DI access are preferred.
- Use canonical `tavall-java-tools` modules as dependencies where applicable. Do not copy shared utilities into this repository.

## Product constraints

- `NO_AUTH` is a supported deployment mode, not a TODO. UI/docs must make its exposure risk unambiguous.
- Production product surfaces never use design fixtures as runtime service data.
- WebMCP tools are projections of canonical operations. Do not create separate AI-only behavior.
- Keep bounded operations, logs, and job execution explicit. A successful process start is not equivalent to completed work.

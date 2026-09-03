# Agent WebMCP

Agent WebMCP is a Java-based remote operations surface for humans and browser agents. One canonical typed operation catalog is intended to power the web console, CLI, HTTP/JSON API, and WebMCP adapter so behavior is implemented once and projected into each surface.

## Current phase

The repository is in its dashboard design/foundation phase. The `design/` directory contains three intentionally different desktop operations-console directions for review. The sample target, service, job, and metric values in those pages are **design-only fixtures** and are not a production data source.

The production architecture will bind those views to the canonical Java operation catalog rather than duplicating action behavior in JavaScript. See `docs/architecture/OPERATION_SURFACE.md`.

## Deployment model

The initial deployment mode is deliberately `NO_AUTH`: run Agent WebMCP on localhost or a trusted private machine and expose it only through a tunnel or boundary you control.

> **Danger: `NO_AUTH` grants the exposed client the operation authority of Agent WebMCP. Do not expose it directly to an untrusted network or public Internet endpoint.**

Agent WebMCP does not attempt to become an authentication or tunneling platform in this phase.

## WebMCP

The browser adapter targets the current imperative WebMCP surface exposed through `document.modelContext`. Browser tools will be projections of the same canonical operation definitions used by the human console, CLI, and HTTP API.

## Design review

Open any of the following in a browser:

- `design/dashboard-a.html` — context-first fleet cockpit
- `design/dashboard-b.html` — operation-first command surface
- `design/dashboard-c.html` — attention-first service board

All three are preserved until a direction is selected or combined.

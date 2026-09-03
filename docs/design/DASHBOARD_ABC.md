# Dashboard A/B/C design intelligence

Status: **three candidate directions; no winner selected**.

This is the product-scoped design record for the first Agent WebMCP desktop console pass. It exists in-repository because no dedicated persisted design-intelligence capability is exposed by the current Tavall Cloud catalog for this workspace.

## Product truths carried into every direction

- One canonical operation catalog powers humans and agents. UI labels use operation identities that can map directly to the Java catalog.
- Target/context selection is always visible before a mutating action.
- `NO_AUTH` is first-class and visibly dangerous, not hidden in Settings.
- WebMCP connection state and registered/exposed tool count are operator data, not marketing decoration.
- Jobs are bounded operations with state/evidence/logs, not a faux terminal session.
- Real product data will come from the canonical operation/runtime projections. Values under `design/` are explicitly review fixtures only.

## Accepted visual baseline

- Near-black foundation with layered charcoal surfaces and soft separators.
- Rounded controls and inputs, readable mixed-case typography, restrained semantic state color.
- Hover transitions should fade cleanly with a subtle outer glow; selected states use grayscale first, semantic color second.
- Dense enough for operators without reducing the page to a spreadsheet or terminal cosplay.
- Technical identifiers use monospace selectively; prose and controls do not.

## Rejected patterns

- Giant KPI/marketing cards.
- Generic four-card analytics dashboards.
- Decorative gradients as information hierarchy.
- Permanent fake-terminal chrome.
- Uppercase-everything labels.
- Separate labels/implementations for an “AI version” of an operation.
- Production code consuming design fixture data.

## Direction A — Fleet cockpit

**Philosophy:** choose context first, then operate. A persistent target/service navigator anchors the left, selected service health/details dominate the middle, and operation + job context stays available on the right. This is strongest when operators spend time inside one target and move among its services.

## Direction B — Operation command surface

**Philosophy:** choose intent first, then scope it. Searchable operation discovery is the primary canvas; target context is a compact strip and the execution inspector makes parameters/impact explicit. This favors expert operators who arrive knowing “restart service” or “inspect job” rather than browsing a fleet tree.

## Direction C — Attention board

**Philosophy:** problems first. Health/warning state forms the first information band, services are grouped into a dense operational board, and WebMCP/catalog state is an attached inspector rather than the central navigation device. This favors triage and multi-service situational awareness.

## Review-fixture boundary

Fixtures intentionally include healthy, degraded, stopped, and failed states so hierarchy and action affordances can be judged. They are defined only in the design implementation and must be deleted or isolated from the production runtime when the selected direction is integrated.

# Agent WebMCP Documentation Standards

> **Status:** Active

Documentation is part of implementation and must match evidence. Do not let source silently redefine accepted design.

## Document responsibilities

- Design documents own intended UX/product behavior.
- Architecture/final documents own durable runtime requirements, ownership, trust boundaries, failure behavior, and required validation.
- Progression/status documents own current implementation state, evidence, gaps, blockers, and the next coherent slice.
- Delegated docs own exact commands, schemas, operations, formats, deployment, or UI details.

Do not turn design/final docs into work logs, and do not claim production verification in a progression document because a unit test passed.

## Evidence statuses

Use these meanings consistently when status labels are needed:

```text
Planned
Designed
Designed - NHV
Implementation Started
Implemented
Automated Tests Passed
Integration Tested
Production Verified
Human Verified
Fully Complete
```

`Implemented` is not synonymous with `Verified`. When validation was not performed, state `Not run: <reason>`.

## Updates

Update owning docs in the same coherent change when operation IDs, input/result contracts, trust boundaries, provider authority, durable state, failure/recovery behavior, build/validation commands, or UI behavior materially change.

Do not create empty placeholder documents merely to satisfy a presumed template. Update indexes/links when documents move or become authoritative/superseded.

## Current product split

- `docs/architecture/OPERATION_SURFACE.md` owns canonical operation architecture.
- `docs/architecture/HTTP_TRANSPORT.md` owns the embedded lightweight Java HTTP boundary.
- `docs/backend/PORT_AND_E2E.md` owns the current backend implementation/validation slice.
- `docs/design/*` owns dashboard visual/product exploration.
- `docs/quality/*` owns engineering process and architecture constraints.

Keep fixture-only design evidence explicitly labeled as such. Production runtime data claims require real provider/runtime evidence.

# Tavall Java Tools Consumer Adoption - Agent WebMCP

> **Status:** Active
> **Canonical policy source:** `TavallStudios/tavall-java-tools/docs/CONSUMER_ADOPTION.md`

Agent WebMCP is a Tavall-owned Java application/runtime repository. The individual Tavall Studios tool repositories are authoritative; `tavall-java-tools` is a synchronized aggregate/policy surface, not a monolithic runtime artifact.

## Concern map

| Concern | Canonical tool | Agent WebMCP rule |
| --- | --- | --- |
| first-party dependency composition/lifecycle | `tavall-di` | Required baseline. No hand-built container or first-party `ServiceLoader`. |
| asynchronous execution/coordination | `tavall-concurrency` | Required where generic async work exists. No feature-local executor framework. |
| typed keyed runtime catalogs | `tavall-registry` | Required for reusable keyed catalogs such as canonical operations. |
| runtime/application logging | `tavall-logging` | Required. Direct streams only for explicit CLI/protocol output. |
| bounded/expiring cache semantics | `tavall-cache` | Adopt if cache behavior is introduced. |
| database persistence | `tavall-database` | Adopt if PostgreSQL/Redis/JPA persistence is introduced. |
| typed in-process generic events | `tavall-eventbus` | Adopt if a generic event boundary is introduced. |
| reusable reflection/scanning | `tavall-reflection` | Adopt if reusable reflective discovery is introduced. |
| durable/repeating scheduled Java work | `tavall-scheduler` | Adopt if scheduled/repeating lifecycle work is introduced. |

The repository audits all nine concerns but depends only on tools it actually needs. A local implementation is not justified merely because the corresponding shared tool is small.

## DI baseline

Tavall DI applies even if a runtime currently has few dependencies. Bootstrap/configuration code may create external adapters or immutable configuration and register them into the owning dependency map. Production behavior resolves Tavall-managed collaborators through DI. Tests may register replacements or scoped fixtures.

## Registry vs DI

DI owns object construction/lifecycle/lookup by dependency token. Registry owns domain/runtime identity lookup and enumeration. The operation catalog is domain registry state and should use Tavall Registry while the registry instance itself participates in Tavall DI composition.

## Concurrency

Use Tavall Concurrency for HTTP task dispatch, background canonical-operation jobs, and shared async helper work. Do not allocate per-feature virtual-thread executors merely because Java makes doing so pleasantly easy. Process adapters may use the shared concurrency layer to consume stdout/stderr concurrently while retaining ownership of the external process lifecycle.

## Capability gaps

If Agent WebMCP requires shared behavior the owning Tavall tool cannot provide, improve that tool first or stack the consumer work on the tool PR. Temporary compatibility seams must be explicit, tested, and carry a removal path.

## Regression enforcement

Architecture tests should reject new project-local DI containers, unmanaged executor creation, local map-backed registry frameworks, runtime logging wrappers, generic event buses, schedulers, cache frameworks, and reflection scanners when a Tavall tool owns the concern. Legitimate small method-local collections and platform adapters are not parallel frameworks.

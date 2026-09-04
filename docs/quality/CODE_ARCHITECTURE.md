# Agent WebMCP Code Architecture

> **Status:** Active
> **Source policy:** Platform-neutral rules adopted from the Tavall Studios / Project Novus quality architecture and the Tavall Java Tools consumer adoption contract.

## Goals

Code should have clear ownership, typed data flow, small coherent responsibilities, stable lifecycle, Tavall-platform reuse, testable boundaries, and honest failure/recovery behavior. Architecture exists to make operational behavior understandable, not to maximize class count.

## Canonical operation boundary

The typed operation catalog is the only product operation definition authority. An operation consists of a stable ID, description, access classification, typed input contract/schema, stateless handler, and typed result/error projection.

CLI, HTTP/JSON, WebMCP, and the human web console discover/project this same catalog. They may adapt transport and presentation, but may not implement independent service lifecycle or job behavior. A class such as `WebMcpRestartService` that re-implements `service.restart` is an architecture defect.

Provider adapters own external-system authority. Operation handlers validate/resolve inputs and delegate through the runtime context. Providers observe and mutate the real system; production code does not manufacture fixture state.

## Tavall Java Tool ownership

Tavall DI is the universal first-party composition baseline for this repository. Managed application/runtime dependencies resolve through the owning `DependencyMap`/`IDependencyMap`; project-local service locators or hand-built containers are forbidden. Bootstrap may create external configuration/adapters and register them into DI. Test fixtures may register explicit replacements.

Tavall Registry owns reusable typed keyed catalogs. The operation catalog may add domain validation and typed helpers, but it must build on Tavall Registry rather than a parallel project-local map framework.

Tavall Concurrency owns generic async execution/coordination. Do not create feature-local executors or thread pools where the shared async boundary applies. Platform APIs that require an `Executor` should adapt to Tavall Concurrency rather than owning another pool.

Tavall Logging is the runtime/application logging surface. `System.out`/`PrintStream` is acceptable only for explicit CLI/protocol output.

Adopt Cache, Database, EventBus, Reflection, and Scheduler only when this repository owns matching concerns. Do not add all artifacts ceremonially.

## Lightweight Java HTTP transport

The embedded web edge uses the JDK `jdk.httpserver` module. `AgentWebMcpHttpServer` owns socket binding, HTTP contexts, bounded request parsing, transport status/headers, static resources, and delegation into the canonical runtime. Tavall Concurrency owns request dispatch. Tavall DI and Tavall Registry remain above the network edge.

Do not introduce Spring, Netty, Jetty, Undertow, another DI container, or a transport-owned executor merely to serve the current API. A richer server dependency requires a concrete protocol/streaming requirement the JDK server cannot responsibly satisfy and an explicit architecture decision. HTTP handlers do not reimplement catalog operations or provider behavior.

## Dependency injection

Ordinary Tavall-managed production behavior classes do not constructor-inject Tavall-managed application dependencies. Prefer `DependencyAccess<...>` for small reusable dependency sets, domain bundles for coherent larger sets, or direct `IDependencyMap#getInstance` in bootstrap/infrastructure.

Constructors remain valid for immutable object state, typed configuration, builder inputs, and genuinely external handles. A class ending in `Builder` does not gain permission to constructor-inject managed collaborators. Builders assemble typed configuration or output and should resolve managed collaborators through DI when they need them.

Required dependencies fail at the lookup boundary. Do not turn a required collaborator into optional state to hide bootstrap failure.

## Classes and naming

One class should have one coherent reason to change. Split real mixed concerns, but do not create forwarding wrappers solely to satisfy a line-count superstition.

Prefer precise roles: `Handler`, `Service`, `Orchestrator`, `Router`, `Resolver`, `Registry`, `Cache`, `Repository`, `Builder`, `Mapper`, `Serializer`, `Reader`, `Writer`, `Publisher`, `Consumer`, `Listener`, `Bootstrap`, `Runtime`, `Timer`. Avoid `Manager`, `util`, `misc`, and `common` buckets.

Use interfaces where there is a real substitution boundary, such as operating-system providers, durable repositories, or testable external adapters. Do not create one interface per concrete class by ceremony.

## State ownership

Classify state before implementing it:

- durable state surviving restart -> repository/persistence boundary;
- disposable/expiring state -> cache;
- typed loaded/keyed runtime lookup -> registry;
- in-flight execution/cancellation/retry -> operation/task state;
- static operator configuration -> configuration/file reader.

File-backed durable state must document atomicity, corruption behavior, concurrent access, recovery, and authority. A file is not exempt from persistence design merely because it has fewer syllables than PostgreSQL.

Registry state is not durable truth. Cache state is not durable truth.

## Lifecycle and concurrency

Every long-lived runtime resource has an owner and explicit create/start/close/replacement behavior. Partial startup must either roll back owned resources or leave a precise recoverable state.

Asynchronous work has bounded execution, cancellation semantics, failure propagation, and shutdown behavior. Reuse Tavall Concurrency rather than allocating unmanaged per-feature executors. A timeout that marks a result timed out while an unbounded worker continues indefinitely is not a timeout contract.

## External command execution

External programs use validated argument vectors. Do not concatenate user-controlled strings into a shell command and do not expose generic shell execution as an operation.

Command execution must bound timeout and output, terminate owned processes on timeout/interruption, preserve interruption, and map provider failures to typed operation/provider errors. Validate service IDs and other platform identifiers before process creation.

## Security and trust boundaries

`NO_AUTH` is intentionally supported. It is dangerous when exposed beyond the operator-controlled trusted boundary. Default bind is loopback. The UI/docs/runtime warning must make the trust assumption visible.

Do not silently fall back from required authentication or origin policy to broader access. WebMCP tool exposure must follow the current browser specification and registered operation access classification.

Mutating operations stay visibly distinguishable from read-only operations. WebMCP annotations derive from catalog access metadata rather than hand-maintained lists.

## Errors and fallback

Expected user/input/provider rejections use stable typed errors with useful HTTP/status mapping. Infrastructure failures preserve the underlying reason without leaking secrets. Fallback behavior must be an explicit part of the product contract and must not hide required failure.

## Testing

Use JUnit 5. Test real data/contracts/concrete behavior where practical. Fake only genuine external/provider boundaries. Never mock the thing under test or private methods. Test packages mirror production packages; normal test names are `<ProductionClass>Test`.

For touched behavior, cover success, invalid input/rejection, provider failure, timeout/cancellation, cleanup/shutdown, durable recovery/corruption boundaries, and projection through HTTP/WebMCP where applicable. Architecture/source-contract tests should reject regressions into manual DI, project-local registries, unmanaged executors, shell execution, `*Manager` naming, and duplicated surface behavior.

Validation evidence must distinguish `Implemented`, `Automated Tests Passed`, `Integration Tested`, `Production Verified`, and `Human Verified`. Source existing is not verification.

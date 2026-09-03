# Tavall Studios Code Review, Git Hygiene, and Engineering Issue Workflow - Agent WebMCP adoption

> **Status:** Active
> **Canonical source:** `TavallStudios/tavall-project-novus/docs/quality/GIT_WORKFLOW.md`
> **Applies to:** Contributors, maintainers, repository owners, automation, and AI-assisted development

This repository adopts the shared Tavall Studios workflow. The canonical Project Novus copy inspected for this adoption is the source policy; this local document keeps the rules needed to execute Agent WebMCP work without requiring cross-repository access. Repository-specific rules in `AGENTS.md` may be stricter but may not silently remove accountable review, truthful validation, traceability, or production safety.

## Core policy

GitHub is the authoritative record for review and active integration work. An open pull request is a durable work surface for its remote branch: implementation, review history, dependency relationships, validation, handoff, and reconciliation continue there until the scope is merged, superseded, or intentionally abandoned.

There is no organization-wide open-PR cap and no global reconciliation freeze. Inspect relevant active work, then classify it as independent, dependent/stacked, overlapping, superseded, or same-scope. Reuse an existing same-scope PR instead of manufacturing `v2`, `replacement`, or `fixed` PRs.

Ordinary contributor changes do not enter `main` without accountable GitHub review. Accountable review is either independent qualified human approval or an authorized owner posting a current **Owner Self-Review** for an owner-authored PR. AI/bot reviews and checks are advisory and do not substitute for either path. Reviewable changes invalidate stale approval/self-review.

`main` is production source. Merge does not authorize or perform production deployment. Deployment is a separate explicit action with known source, validation, risks, rollback/recovery, and post-deployment checks.

## Branches and pull requests

Normal work begins on a focused branch such as `working/<short-description>`. Once a PR exists, the branch and PR are one durable unit of work.

- Continue meaningful work on the existing PR branch.
- Push coherent checkpoints while work is in progress.
- Do not leave the authoritative or most useful source only in a local worktree, Tavall workspace, or disposable sandbox.
- Keep the PR body, dependencies, validation, linked issues, and stack relationships current.
- Keep the PR Draft while implementation, validation, dependency reconciliation, or description is incomplete.
- Large coherent changes are allowed. Do not fragment one system merely to satisfy arbitrary diff-size preferences.

A working branch contains one coherent feature, fix, refactor, documentation change, investigation outcome, or dependency layer. Avoid unrelated drive-by cleanup.

### Stacking

Stacking is first-class. When one reviewable change depends on another unmerged change, the child PR targets the parent PR branch. Name the relationship and expected merge order in the PR body/staging state.

After a parent merges: update/rebase the child from the parent's destination, retarget the same child PR, verify its diff contains only intended child work, rerun affected validation, and update dependency/staging metadata. Do not duplicate parent commits merely to flatten the graph.

### Staging

Staging is integration state, not a requirement that every change pass through one permanent junk-drawer branch. A repository may use staging manifests and/or `staging/*` branches when combined validation is useful. GitHub PRs remain authoritative work surfaces; staging state describes how they compose.

## Before implementation

- Complete the repository quality preflight from `AGENTS.md`.
- Read this workflow, `CODE_ARCHITECTURE.md`, relevant design/progression/operational docs, and owning system docs.
- Search open and closed issues/PRs for blockers, accepted/rejected directions, overlapping work, and investigations.
- Inspect existing implementation before proposing new classes or systems.
- Identify ownership, expected tests, migration risk, failure/recovery behavior, rollback, and likely PR dependencies.
- Create/link an issue when work blocks progress, may change architecture, exposes a platform limitation, affects multiple systems, creates security/production risk, records a temporary compromise, or needs durable investigation context.

## During implementation

- Keep scope focused and extend established systems rather than creating parallel implementations.
- Commit at meaningful working checkpoints.
- Push coherent checkpoints to the remote PR branch while work is ongoing when credentials allow.
- Keep tests and docs with the behavior they explain.
- Treat generated code/APIs as untrusted until verified.
- Prefer explicit PR stacking over copying/reimplementing an unmerged dependency.
- Update linked issues/PR dependency metadata when evidence changes direction.

## Before requesting review

The author reviews the complete diff, removes debug/dead/accidental generated artifacts, runs relevant checks, performs required manual/bot verification, documents untested paths honestly, synchronizes with target/parent, updates documentation/progression evidence, links issues accurately, and names stack parents/children/merge order.

Use `Closes`/`Fixes` only when the PR fully resolves an issue; otherwise use `Refs`.

Pull requests created by ChatGPT/Codex/Tavall automation on behalf of TJ attribute `tjXJNOOBIE` as human owner/requester and name materially authoring automation. Request TJ as reviewer when they are not the author. Owner-authored PRs use Owner Self-Review because GitHub cannot approve one's own PR. Codex automatic review is the expected advisory bot review; use `@codex review` when automatic review is missing and treat integration failure as a blocker to record/fix rather than silently skipping it.

## Review priorities

Review the current diff for correctness, architecture/quality compliance, duplicate-system risk, stack/dependency correctness, failure/recovery, persistence/migration safety, concurrency/lifecycle, permission/security boundaries, performance/operational impact, logging/audit behavior, test quality, naming/maintainability, unrelated work, fabricated APIs, and incomplete rollback/validation.

An Owner Self-Review records:

```text
Owner Self-Review

Scope:
- What is being promoted.

Validation:
- Checks, tests, harnesses, and manual verification actually completed.

Untested:
- Known validation not performed.

Risks:
- Material production, data, security, migration, or operational risks.

Rollback:
- Exact revert or recovery approach.

Decision:
- Ready for merge on the current reviewed pull-request state.
```

## Commit hygiene

Each commit is one understandable, reviewable, revertible change. Atomic does not mean artificially tiny. Related source, tests, and documentation belong together when they describe the same boundary. Avoid mixing unrelated formatting, refactors, dependency upgrades, or fixes.

Every commit uses one or more allowed typed subject lines followed by a structured body:

```text
Type: Capitalized concise action

Reason:
- Why the change is needed.

Changes:
- What changed.

Validation:
- What was run or inspected.
```

Allowed types: `Build`, `Added`, `Changed`, `Removed`, `Fixed`, `Refactor`, `Clean`, `Test`, `Docs`, `License`, `TODO`, `Misc`. `Meta` is not allowed. Use `Type: Action`, not Conventional Commit syntax. Describe the result, avoid vague subjects, and use `Not run: <reason>` when validation was not performed.

Before commit, inspect branch, `git status`, staged diff, temporary/generated artifacts, line-ending churn, ignored environment state, and secrets. Never commit API keys, tokens, passwords, private keys, production credentials, unredacted user data, sensitive dumps, or local-only environment state. If a secret enters history, rotate/invalidate it.

Never rewrite shared branch history without coordination. Prefer `--force-with-lease` only for personal branches when rewriting is authorized.

## AI-assisted work

The human contributor remains responsible for committed source regardless of who typed it. AI agents must complete repository discovery, follow the quality preflight, inspect active PR/staging/stack state, verify referenced APIs/dependencies, run tests rather than trusting generated claims, inspect the full diff, disclose uncertainty, preserve established ownership, and push meaningful checkpoints instead of trapping progress in an ephemeral execution environment.

AI-authored corrections are new reviewable changes and invalidate stale approval/self-review. Automated review does not satisfy accountable human review.

## Production promotion

A promotion record explains release scope, included issues/PRs, stack relationships, important behavior changes, configuration/migrations, automated/manual evidence, risks/untested paths, rollback, and post-deployment verification.

Before merge verify target/source, stack ancestry, staging evidence where used, current accountable review, configured checks or explicit owner override, resolved blocking conversations, linked issues/PRs, documented migration/configuration/rollback, and separation of deployment authorization.

Before deployment identify exact source/artifact, review/validation state, untested paths, configuration/secrets/migrations, backups/recovery, deployment owner, affected services, post-deployment checks, and reconciliation of any direct production corrections into active PR/staging branches.

## Issues

Use GitHub Issues as durable engineering context for blockers, architecture changes, platform limitations, investigations, multi-system effects, production/security risk, temporary compromises, and important tradeoffs. Direction-setting issues should record summary, current behavior, evidence, constraints, options, current direction, acceptance criteria, related work, rejected approaches, and implementation context. Do not create issues for every trivial edit.

## Author checklist

- Branch/base/target are correct and same-scope PRs were reused.
- Parent/child stack relationships and staging state are accurate.
- Complete diff was self-reviewed and scope remains coherent.
- Quality/architecture/system docs and relevant issues were reviewed.
- Tests/checks/manual verification actually ran and are recorded truthfully.
- Untested paths, risks, migration/configuration, rollback/recovery are documented.
- No secrets or accidental artifacts are present.
- Generated APIs/code were verified.
- Meaningful work is pushed to the remote PR branch when remote authority is healthy.
- Human owner and materially authoring automation are attributed.
- Codex advisory review ran or its integration failure is recorded.

## Exceptions

An authorized repository owner may exercise repository-specific direct-main, ruleset/check bypass, force-push, merge-method, release-scope, or deployment authority where stricter synchronization rules do not prohibit it. Exceptions must be explicit, narrow, attributable, and reviewable when practical. Automation cannot grant itself an exception.

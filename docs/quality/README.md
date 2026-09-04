# Agent WebMCP quality contract

> **Status:** Active

Agent WebMCP adopts Tavall Studios engineering policy from the canonical quality system maintained with Project Novus, while keeping product-specific architecture separate from Minecraft-specific implementation guidance.

## Precedence

1. `AGENTS.md` is the repository entry point and may impose stricter Agent WebMCP safety rules.
2. `GIT_WORKFLOW.md` is the shared Tavall Studios Git/review workflow and is vendored from the canonical Project Novus quality source.
3. `CODE_ARCHITECTURE.md` applies the platform-neutral Tavall architecture rules to Agent WebMCP.
4. `JAVA_TOOLS_ADOPTION.md` maps cross-cutting Java concerns to their owning Tavall tools.
5. `DOCUMENTATION_STANDARDS.md` owns documentation lifecycle/evidence rules.
6. Owning design, backend, and operation-surface documents define product behavior.

Do not interpret a shorter repository-specific document as permission to ignore a stronger shared rule. Do not import Minecraft/Paper/JPA-specific rules where Agent WebMCP does not own those concerns.

## Canonical source consumed for this adoption

The adoption pass consumed all 22 regular files beneath `TavallStudios/tavall-project-novus/docs/quality` from the inspected canonical workspace snapshot.

```text
QUALITY_DOCUMENT_COUNT=22
QUALITY_MANIFEST_SHA256=50bf4791ccde1872851c8d7e9d171bf75c3b65e5e59ab2679154596f7ef3448c
```

The repository's own preflight hash is intentionally different because this repository contains a product-specific quality subset. Run `scripts/quality-preflight.sh --print` for the current Agent WebMCP contract rather than reusing the source manifest above.

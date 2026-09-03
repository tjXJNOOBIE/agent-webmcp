# Agent WebMCP Video Pipeline

This is the default creation flow for the next Agent WebMCP video generation. The goal is a reproducible local pipeline with no mandatory creative-service accounts.

## 1. Inputs and timing

- Treat the supplied narration as the primary story/timing source unless the brief explicitly changes that rule.
- Treat supplied music as a supporting mix layer; do not replace it merely because another generator exists.
- Inspect existing captures, renders, and generated assets before making new ones.

## 2. Asset routing

Use the cheapest deterministic layer that can produce the required result:

1. **Animotion MCP** for icons, arrows, status glyphs, spinners, checkmarks, micro-interactions, and existing CSS animation patterns.
2. **PinePaper MCP** for bespoke animated SVG/vector scenes, diagrams, procedural backgrounds, chart motion, and branded callouts.
3. **HyperFrames** for HTML-native motion graphics, UI scenes, kinetic typography, overlays, and product-demo composition.
4. **Remotion** for React/timecode-heavy sequences and standard transition choreography. Prefer its transition package for ordinary fades/wipes/slides/zooms rather than recreating them.

Generated vector/motion assets should be exported to the repo and referenced locally by the composition. Never depend on a live network request during rendering.

## 3. Composition

The current launch video remains HyperFrames-first. Build the hero layout before animation, keep timing deterministic, and preserve the existing visual identity. Remotion is complementary rather than a required rewrite: use it for isolated sequences or future edits when it is clearly the cleaner timeline model.

## 4. Finishing

Use **Kinocut** as the deterministic finishing/FFmpeg surface for operations such as:

- trim / split / concat
- codec/container conversion
- final audio normalization or muxing
- caption burn-in when required
- inspection and quality gates
- video receipts / release checkpoint

`Kinocut` requires FFmpeg on `PATH`. This project supplies a wrapper that resolves the pinned local `ffmpeg-static` binary before starting Kinocut, so a machine-level FFmpeg install is not required.

## 5. Required preflight

```bash
npm run tools:bootstrap
npm run tools:doctor
```

The bootstrap is intentionally local and deterministic after its first network-enabled install:

- Puppeteer is retained because PinePaper uses its API, but Puppeteer browser downloads are disabled.
- Playwright supplies the project-local Chromium used by both PinePaper and HyperFrames.
- Kinocut 1.15.1, Python 3.11, FFmpeg, and FFprobe are resolved into ignored project-local caches/binaries.
- Browser caches, UV/Python caches, `node_modules`, and render outputs are never committed.
- The composition vendors pinned runtime assets such as GSAP under `assets/generated/vendor/`; final renders must not fetch runtime assets from the network.

**PinePaper locality note:** the MCP server and browser execution are local and require no account, but PinePaper 1.6.7 does not ship a self-hostable Studio frontend. Browser-backed authoring therefore loads the public PinePaper Studio editor page unless a compatible local Studio URL is supplied. Exported assets must be saved into this repository and final rendering must remain network-independent. If an authoring pass requires strict zero-network operation, use Animotion/HyperFrames or a future self-hostable PinePaper Studio instead of silently falling back to hosted authoring.

A generation pass should not continue blindly if an MCP/tool is unavailable. Fall back by capability, not by rebuilding everything manually:

- Animotion unavailable -> PinePaper or local SVG/CSS asset
- PinePaper unavailable -> HyperFrames-native SVG/HTML motion
- Kinocut unavailable -> local FFmpeg only as an explicit fallback; record the missing quality-gate surface

## 6. Required validation

Before final export:

```bash
npm run check
npm run render
npm run tools:doctor
```

Then run Kinocut inspection/quality/release checks against the rendered MP4. Human visual/audio review remains required before the hackathon submission is treated as final.

## Tool ownership

- **Animotion/PinePaper:** asset creation
- **HyperFrames/Remotion:** motion composition and transitions
- **Kinocut:** deterministic finishing and media QA

Do not force one tool to impersonate the others. That is how pipelines become six wrappers deep and nobody remembers why a fade requires Python.

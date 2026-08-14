# HAVENLINE Gameplay Sandbox

A Three.js browser sandbox for rapidly exercising HAVENLINE gameplay behavior against the same runtime contract used by Unity. It is a development and QA surface, not a replacement shipping engine.

## Authority

The sandbox loads:

`HAVENLINE_WEB/shared/HAVENLINE_REFERENCE_CONTRACT.json`

CI requires that file to be byte-identical to:

`HAVENLINE_UNITY/Assets/Havenline/Reference/HAVENLINE_REFERENCE_CONTRACT.json`

The sandbox refuses to boot when the contract is structurally invalid. Unity remains authoritative for production scene authoring, humanoid animation/deformation, native Android behavior, save/auth integrations, final performance, and APK delivery.

## Current playable loop

- Orthographic follow camera using the current runtime size, offset, look-ahead and follow sharpness.
- Screen-relative keyboard and touch movement.
- Walk/run acceleration and world bounds from the contract.
- Character 1 or Character 2 as lead.
- Exactly three core companions: unselected lead, Character 3 and Character 4.
- Companion formation offsets from the onboarding runtime contract.
- Exact wood, stone, metal and fuel node placement used by premium scene authoring.
- Automatic nearby gathering with per-resource production timings.
- Carry capacity of eight.
- Automatic furnace deposit and repair.
- Furnace Level 2 gate at 18 wood + 6 stone.
- Warmth expansion by furnace level.
- Survivor rescue only after furnace Level 2.
- North and south barricade construction requirements of 8 wood + 3 stone.
- First wolf wave gated behind Furnace Level 2 + rescued survivor + completed north defense.
- Three-wolf first wave and subsequent wave-delay rules.
- Local GLB loading for the selected lead so gameplay can be tested before the model is committed to Unity production.
- Debug teleports/range visualization and an evidence log for fast iteration.

## Deterministic QA

`sim-core.js` contains engine-independent gameplay rules used by the browser page. `contract-qa.mjs` imports that same module and runs deterministic checks in Node:

```bash
node contract-qa.mjs
```

The runner fails if the Unity and browser JSON contracts differ or if the critical opening-loop checks fail.

## Run locally

Serve `HAVENLINE_WEB` from an HTTP server so the sandbox can fetch the shared contract:

```bash
cd HAVENLINE_WEB
python -m http.server 8080
```

Open:

`http://localhost:8080/gameplay-sandbox/`

## Production boundary

A browser QA pass does **not** make a release candidate. The shipping gate still requires approved C1-C4 production character assets, Unity scene authoring, Unity tests, rendered evidence, Android ARM64 build evidence, verified Google OAuth/signing configuration, and physical Galaxy Z Fold acceptance.

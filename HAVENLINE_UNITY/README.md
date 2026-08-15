# HAVENLINE — authoritative Unity rebuild

This is the active HAVENLINE Unity project at `HAVENLINE_UNITY` on branch `havenline-unity-reference-rebuild`.

## Two separate authorities

HAVENLINE intentionally separates gameplay truth from visual truth:

- **Gameplay authority:** the preserved verified HAVENLINE reference game and `Assets/Havenline/Reference/HAVENLINE_REFERENCE_CONTRACT.json`.
- **Visual-style authority:** the approved example video and `Assets/Havenline/Reference/HAVENLINE_VISUAL_DIRECTION_CONTRACT.json`.
- **Hero identity authority:** the approved 2D turnaround artwork for Characters 1–4.

The gameplay reference does **not** authorize a realistic survival-game appearance. The example video and approved character artwork control how the final game must look.

## Technical identity

- Reference APK: `HAVENLINE-v0.3.0-reference-final-ARM64.apk`
- Reference APK size: `107,695,534` bytes
- Reference APK SHA-256: `17996ba270e6b56505d3273fca1915f977f6d892b4949f37c66098ac6efcfa67`
- Unity editor: `6000.3.18f1`
- Render pipeline: URP 17.3
- Android: ARM64, IL2CPP, landscape, API 26+

## Required gameplay retained

- close three-quarter orthographic/isometric camera;
- screen-relative keyboard, controller and touch movement;
- bounded world and fall recovery;
- automatic nearby gathering;
- visible carried supplies;
- automatic furnace delivery and repair;
- furnace upgrades and warmth expansion;
- active four-character crew behavior;
- autonomous helper behavior;
- repairable/buildable barricades;
- wolf attacks and escalating pressure;
- compact safe-area-aware Galaxy Z Fold HUD;
- adaptive 60/90/120 Hz operation.

## Required visual direction

HAVENLINE must read as a **stylized animated 3D survival game**, not a realistic human survival game.

The following are automatic visual rejection conditions:

- photorealistic or real-world survival presentation;
- generic Unity asset-pack visual identity;
- realistic marketplace heroes replacing the approved crew;
- characters and environment using mismatched art styles;
- technically valid models that fail to match the approved 2D identities;
- prototype/blockout-looking world art being treated as production-ready.

Characters, terrain, snow/ice, trees, rocks, buildings, props, wolves, furnace, VFX, lighting and HUD must belong to one coherent animated world.

## Character production

Characters 1–4 are built from the approved 2D identities. A custom mesh or a heavily remodeled rigged base is acceptable when it reproduces the approved design. Fab/marketplace hero replacement is not acceptable. Fab may support animation and non-hero environment/prop work.

Production path:

`approved 2D -> custom/remodeled stylized 3D -> stylized materials -> humanoid rig -> Unity four-view proof -> gameplay/deformation proof -> human approval`

## Rejected legacy art chain

The former `ProceduralArtStudio -> R31 -> R32 -> VisualRecovery` production generation chain is retired from clean-checkout CI/build preparation because its output drifted toward the wrong realistic/prototype survival presentation.

The old source files may remain for history or selective technical reuse, but the authoritative build entry point must not execute that chain. The production-art manifest is intentionally blocked until a replacement stylized set receives visual approval.

## Build target

- repository: `ksolo21-web/Hummer`
- branch: `havenline-unity-reference-rebuild`
- project path: `HAVENLINE_UNITY`
- editor: `6000.3.18f1`
- platform: Android ARM64

Android proof/release builds are intentionally fail-closed while production art is blocked. Opening the Unity project and continuing source/art development does not require pretending the rejected art set is approved.

## Truth gate

Source compilation and automated tests are not visual approval. A release remains blocked until the exact commit produces acceptable Unity gameplay frames, approved Characters 1–4, an installable ARM64 APK, machine-readable evidence, and successful physical Galaxy Z Fold acceptance.

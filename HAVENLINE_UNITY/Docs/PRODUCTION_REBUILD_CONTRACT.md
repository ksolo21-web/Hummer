# HAVENLINE Production Rebuild Contract

## Purpose

Rebuild HAVENLINE correctly from the original frozen-survival reference and the previously established owner requirements. Do not reproduce the failed generated-project approach.

## Visual target

- Close three-quarter orthographic/isometric camera.
- Readable human-scale survivor; never tiny beside oversized scenery.
- Compact frozen outpost centered on a furnace/heat source.
- Detailed stylized 3D characters, props, snow, structures, lighting, animation, and effects.
- No block-built or Minecraft-like final art.
- The scene must resemble the original reference in composition, scale, pacing, clarity, and satisfying ad-style interaction loop—not merely share the same genre.
- Minimal mobile HUD with a compact two-line objective card and safe-area support.

## Required vertical-slice loop

1. Move with smooth screen-relative touch controls.
2. Automatically gather nearby resources.
3. Show gathering/chopping animation and visible carried materials.
4. Automatically deliver resources to storage or the furnace.
5. Upgrade the furnace and visibly expand the warm/safe area.
6. Rescue a survivor who becomes an automated helper.
7. Build and repair defenses.
8. Show visible incoming wolf/enemy pressure with animated reactions.
9. Save and restore only valid in-bounds player state.

## World and gameplay requirements

- Snowy frozen-outpost environment with clear warm/cold contrast.
- Furnace, heat boundary, resources, storage, helper, barricades, and threats visible without clutter.
- Natural difficulty growth without turning the loop into a complex strategy UI.
- Player cannot leave or fall through the playable area; recovery returns to the last safe grounded position.
- Helper roles must be designed for later expansion into gather, carry, repair, fuel, guard, build, heal, and scout behaviors.
- Architecture must support future connected biomes, vehicles, tunnels, rail, airship, and underwater travel without implementing all biomes in this first slice.

## Production engineering rules

- Use Unity-authored scenes, prefabs, ScriptableObjects, animation controllers, materials, and input assets.
- Do not generate the entire game scene at import time as a substitute for authored Unity assets.
- Do not download essential production art during gameplay or during a normal player build.
- Keep third-party assets under explicit license/provenance control and treat them as foundations, not as the final HAVENLINE identity.
- Preserve serialization safety and `.meta` files.
- Use assembly definitions with clear runtime, editor, and test boundaries.
- Use the Input System, URP, AI Navigation, and Unity Test Framework only when confirmed in the fresh project.
- Target stable 60 fps first, with 90/120 Hz support when the device sustains it.
- Android review builds require IL2CPP and ARM64.

## Prohibited shortcuts

- No browser/WebGL deliverable as the production game.
- No Godot runtime in the new Unity project.
- No placeholder primitives presented as final art.
- No fake APK, repackaged old APK, or archive-only verification.
- No claim that the game is built because scripts exist.
- No claim of visual fidelity without Unity-rendered captures.
- No claim of device performance without physical-device testing.
- No automated retry loop that repeatedly rebuilds after failure.

## Required evidence gates

### Gate 1 — project foundation

- Valid Unity project root with `Assets`, `Packages/manifest.json`, and `ProjectSettings/ProjectVersion.txt`.
- Unity version, render pipeline, input, packages, assemblies, scenes, and available MCP tooling documented in `Docs/AI/UnityProjectContext.md`.

### Gate 2 — editor compilation

- Unity import and C# compilation complete.
- Exact Console errors and warnings recorded.
- No new unresolved compile errors.

### Gate 3 — authored content

- Frozen-outpost scene committed as a real `.unity` asset.
- Player, furnace, helper, resources, barricades, wolf/enemy, camera, HUD, and systems committed as real prefabs/assets.
- Serialized references inspected and valid.

### Gate 4 — runtime proof

Play Mode must demonstrate:

- screen-relative movement;
- automatic gathering;
- visible carrying and deposit;
- furnace upgrade and heat expansion;
- helper rescue and automation;
- defense construction/repair;
- visible animated enemy pressure;
- bounds and fall recovery;
- save/reload of valid state.

### Gate 5 — Android artifact

- Development ARM64 APK produced by Unity.
- APK archive integrity and package metadata verified.
- SHA-256 recorded.
- Two Unity-rendered review captures produced from the exact build scene.

### Gate 6 — owner and device acceptance

- Visual comparison against the original reference.
- Installed and tested on the target Galaxy Z Fold in folded and unfolded modes.
- Touch, safe areas, camera framing, stutter, heat, and sustained frame rate reviewed.

## Completion definition

HAVENLINE is not complete until all six gates have evidence. A blocked gate must be reported as blocked; it must never be described as passed.
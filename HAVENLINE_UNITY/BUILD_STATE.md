# HAVENLINE Premium Production State

Last updated: 2026-08-05

## Locked product

HAVENLINE is a premium stylized 3D cartoon survival game for Android based on the supplied Whiteout Survival playable-ad interaction language, expanded into a complete connected-world game.

The player controls movement. Nearby gathering, combat, pickup, delivery, rescue, construction and repair actions begin automatically. The camera remains close, the character stays large and readable, carried resources are visible, and progression changes the world directly.

## Implemented production runtime

- Central proximity-driven automatic-action targeting with range, priority, facing and hysteresis.
- Movement-only touch control with automatic running at sustained full-stick input.
- Automatic chopping, mining, salvage, fuel collection, combat, delivery, rescue, construction and repair.
- One physical mixed-resource carry stack with no overlapping wood, stone, metal or fuel models.
- Animation-event impact synchronization with deterministic fallback timing.
- Four-stage furnace progression with visibly increasing warmth, fire, lighting and thaw reach.
- Furnace durability, automatic proximity repair and outpost-recovery behavior.
- Rescuable helper with persistent position, inventory and gathering, delivery, construction, repair and defense jobs.
- Staged barricade construction, persistent partial delivery, damage, repair and separate north/south identity.
- Pooled wolf enemies with independent player-hit and enemy-attack timing.
- Wave completion based on defeating all attackers rather than merely spawning them.
- Connected-area gate unlock only after furnace, rescue, defense and wave requirements are actually complete.
- Versioned atomic save/resume covering player, carried inventory, furnace, helper, construction, defenses and waves.
- Close cartoon isometric camera, minimal contextual HUD and foldable-safe aspect adaptation.
- Adaptive 60/90/120 Hz frame targeting with sustained frame-time measurement and reversible quality tiers.
- CPU/GPU frame timing, P95/P99 frame time, memory and device telemetry.
- Vulkan primary graphics API with OpenGLES3 fallback.
- Separate premium device-test and physically verified release-candidate build stages.
- Deterministic runtime/settings/art fingerprint tying physical evidence to the tested package.

## Verified source state

Validation run `31006040098` passed on commit `d8fcf556ae75b41365c636079d6f2a0dce1c3976`.

- Unity EditMode compilation and premium contract tests: passed.
- Unity PlayMode automatic-action and opening-loop tests: passed.
- Tested behaviors include automatic target acquisition, inventory capacity, mixed visible carrying, one-at-a-time furnace delivery, furnace upgrading, furnace damage/repair, warmth reaching the frozen survivor, rescue completion and in-world defense construction.

## Premium release blockade

No APK is currently approved for delivery.

The production manifest remains intentionally blocked because the final coherent cartoon character, wolf, four furnace stages, structures, environment, materials, animation, UI, VFX and audio assets have not yet been committed and visually approved.

The build system will not substitute generic models, use old superhero/reference assets, download random packs during CI, or export the prototype scene as a premium candidate.

## Acceptance path

1. Complete and commit the approved production art/audio library.
2. Pass the production-content and actual shipping-scene quality gates.
3. Re-run Unity EditMode and PlayMode validation against the finished scene.
4. Produce the premium ARM64 device-test APK.
5. Complete separate sustained 15-minute profiles at 60 FPS/Ultra, 90 FPS/High and 120 FPS/High on the target Android device tier.
6. Pass thermals, save/resume, suspend/resume, fold/unfold, complete-opening-loop and crash-free checks.
7. Bind the physical report to the exact deterministic source-and-art fingerprint.
8. Only then promote the package to `HAVENLINE-premium-release-candidate-arm64.apk`.

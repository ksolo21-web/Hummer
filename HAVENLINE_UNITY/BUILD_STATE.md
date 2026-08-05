# HAVENLINE Premium Production State

Last updated: 2026-08-05

## Locked product

HAVENLINE is a premium stylized 3D cartoon survival game for Android. The player controls movement while nearby gathering, combat, pickup, delivery, rescue, construction and repair begin automatically. The close camera keeps the survivor readable on phones, tablets and foldables, carried resources remain visible, and progression changes the connected world directly.

## Implemented production runtime

- Proximity-driven automatic actions with range, priority, facing and hysteresis.
- Movement-only touch control with automatic running at sustained full-stick input.
- Automatic gathering, combat, delivery, rescue, construction and repair.
- One physical mixed-resource carry stack with no overlapping cargo.
- Animation-event impact timing with deterministic fallback.
- Four-stage furnace progression, expanding warmth, damage and automatic repair.
- Persistent helper gathering, delivery, construction, repair and defense jobs.
- Persistent staged barricades with separate north/south identities.
- Pooled wolves, independent attack timing and defeat-based wave completion.
- Connected-area gate unlock only after the real furnace, rescue, defense and wave conditions pass.
- Versioned atomic save/resume covering the complete opening loop.
- Close cartoon isometric camera, contextual HUD and foldable-safe adaptation.
- Adaptive 60/90/120 Hz targeting, frame-time measurement, reversible quality tiers and device telemetry.
- Vulkan primary graphics with OpenGLES3 fallback.
- Separate premium device-test and physically verified release-candidate stages.

## Deterministic premium art studio

The repository now contains an owned, reproducible winter-cartoon art pipeline rather than a dependency on random asset packs. It authors custom survivor and wolf geometry, four distinct furnace stages, structures, resources, environment dressing, textured materials, animation controllers, VFX, HUD/menu prefabs and routed audio.

The studio EditMode gate must:

1. Generate the complete production library from committed source.
2. Author the actual shipping frozen-outpost scene.
3. Pass the premium scene and functionality contracts.
4. Render wide, close-phone and foldable proof frames.
5. Upload the report and proof frames for visual inspection.

Studio review run `31009607455` reached real asset generation while PlayMode remained green. Its only reported failure was a same-path Snow/Ice material self-copy; that source defect was removed in commit `80391de0c23724cf6ceb568a95a977ad1ae8e32a` before the next review run.

## Verified gameplay source

- Validation run `31006040098` passed on commit `d8fcf556ae75b41365c636079d6f2a0dce1c3976`.
- The expanded studio source and existing gameplay suites passed compilation and PlayMode validation in run `31009209057`.
- Automatic targeting, mixed carrying, furnace delivery/upgrading/repair, warmth reach, rescue and construction remain covered by live Unity tests.

## Premium release blockade

No APK is approved for delivery yet. The manifest remains blocked until the newly generated review frames are visually accepted and the complete production-content and shipping-scene gates pass. Prototype, superhero, generic fallback or remotely downloaded content cannot be substituted.

After visual approval, the premium ARM64 device-test APK must complete separate sustained 15-minute profiles at 60 FPS/Ultra, 90 FPS/High and 120 FPS/High, plus thermals, memory, save/resume, suspend/resume, fold/unfold, complete-loop and crash-free checks. Only fingerprint-matched physical evidence can promote the package to `HAVENLINE-premium-release-candidate-arm64.apk`.

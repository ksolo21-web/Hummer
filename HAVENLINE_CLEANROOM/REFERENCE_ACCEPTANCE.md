# HAVENLINE Clean-Room Reference Acceptance Contract

This branch starts from the last pre-HAVENLINE repository commit. No Godot runtime, prior Unity framework, generated scene builder, arbitrary model auto-picker, or local auto-build script is carried forward.

## Primary reference

Original gameplay reference supplied by Kaleb:

- `https://youtube.com/shorts/JicjHsoUj68`

The reference is the acceptance bar for framing, scale, density, movement readability, environmental polish, automatic interactions, visible progression, and mobile simplicity. “Same genre” is not acceptance.

## Required visual identity

- Close three-quarter orthographic/isometric camera.
- Readable stylized 3D survivor at mobile screen size; never tiny or hidden between oversized props.
- Compact furnace-centered frozen outpost with deliberate composition, not a scattered sandbox.
- Dense snow, ice, rocks, trees, tents, storage, work areas, barricades, warm light, shadows, particles, and visible paths.
- Strong cold-versus-warm color and lighting contrast.
- Properly authored characters, props, materials, animation, VFX, and UI. No cubes, capsules, generic primitives, block-built characters, or arbitrary asset-pack substitutions as review art.
- Clean minimal mobile HUD that preserves the world view.

## Required first playable loop

1. Smooth screen-relative joystick movement.
2. Automatic resource interaction when the player reaches a valid target.
3. Visible gathering animation and feedback.
4. Supplies visibly carried on the player.
5. Automatic deposit at storage/furnace.
6. Furnace upgrade with a visible physical change and expanding warmth area.
7. Survivor rescue followed by helper gathering/delivery automation.
8. Barricade construction/repair.
9. Visible incoming wolf pressure with locomotion, attacks, reactions, and damage feedback.
10. Save/recovery that never traps or restores the player outside the playable area.

## Device and presentation requirements

- Android application, ARM64, landscape.
- Galaxy Z Fold 7 folded and unfolded layouts.
- Safe-area-aware controls and HUD.
- Target 60 fps minimum with 90/120 Hz presentation where supported.
- Camera must not clip or expose unfinished borders.
- Objective card no more than two lines during normal play.

## Evidence required before any “built” claim

A build is not complete because source code exists or Unity compiles. Every review milestone must include:

- the committed authored Unity scene and stable prefab/material/animation assets;
- exact Unity-rendered screenshots from the scene;
- a short Unity-captured gameplay video showing the full core loop;
- an installable ARM64 APK and checksum;
- compile/build logs;
- a device-test report for the target phone.

Until those artifacts exist and are inspected, status must say `SOURCE IN PROGRESS`, not `GAME BUILT`.

## Prohibited shortcuts

- Reusing the failed Godot or Unity attempts as the production foundation.
- Procedurally selecting the first asset whose filename contains “character,” “wolf,” or “tent.”
- Generating the whole production scene from one editor script and treating that as authored visual design.
- Calling a framework, workflow, branch, or unexecuted build script a playable game.
- Requiring Kaleb to perform routine development work that can be automated or completed in the repository.
- Restarting or changing engines after implementation begins without an evidenced technical reason and explicit approval.

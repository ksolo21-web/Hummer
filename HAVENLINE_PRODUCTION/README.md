# HAVENLINE — Clean Production Rebuild

This directory replaces the rejected open-plane camp prototype with a clean mobile production vertical slice.

## Non-negotiable reference target

The game is designed around the same close, readable three-quarter/isometric presentation and compact survival rhythm as the reference supplied by the owner, while using original HAVENLINE characters, props, structures, UI, and world design.

The first accepted slice must visibly contain:

- Smooth, rounded stylized characters—never cubes or block people.
- A dense frozen outpost framed around a central furnace rather than a large empty plane.
- Screen-relative joystick movement: pushing upward moves toward the top of the screen.
- Physical perimeter collisions, position clamping, and automatic last-safe-position recovery.
- Automatic nearby gathering, visible carried inventory, and automatic delivery at the furnace.
- Furnace fuel, upgrades, and a visibly expanding warmth radius.
- Survivor rescue and helper automation.
- Barricade defense and animated wolf pressure.
- A compact mobile HUD that preserves the play area.
- A 60 Hz physics simulation and a 120 FPS rendering target.

## Source integrity

`source/HAVENLINE-production-rebuild-source.zip.b64` contains the complete project. `source/manifest.json` pins the archive and every project file by SHA-256. `ci/assemble_source.py` reconstructs and verifies the project before any engine command is allowed to run.

## Acceptance pipeline

The production workflow performs these gates in order:

1. Reconstruct and verify the exact project.
2. Import it with the official Godot 4.7.1 editor.
3. Reject any script, shader, or resource import error.
4. Boot the real scene and test orthographic framing, screen-forward movement, map recovery, resource density, furnace heat, helper automation, and defense systems.
5. Capture a validation frame from the exact rendered scene.
6. Only after those gates pass, install official Android export templates and export an ARM64 debug APK.
7. Verify APK archive integrity and publish the APK, checksum, logs, and rendered frame together.

An APK is not called final or production-ready until the exact artifact is installed and profiled on the target Fold hardware.

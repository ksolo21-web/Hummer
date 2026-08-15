# HAVENLINE Authoritative Build State

Last updated: 2026-08-15

## Locked product

HAVENLINE is a stylized animated 3D survival game for Android. The preserved reference game remains the gameplay authority: the player controls movement while nearby gathering, combat, pickup, delivery, rescue, construction and repair begin automatically. The close isometric camera keeps the crew readable on phones, tablets and foldables; carried resources remain visible; progression changes the connected world directly.

## Visual authority

The approved example video is the visual-style authority. The four approved 2D character turnaround sheets are the identity authority for Characters 1–4.

The target is one unified animated visual language across:

- the four main characters;
- snow, ice, terrain, rocks and trees;
- buildings, tents, barricades, storage, furnace and props;
- wolves and other creatures;
- fire, smoke, snowfall, gathering, building and combat effects;
- lighting, color separation and mobile-game readability.

Photorealistic/real-world survival styling and a generic Unity asset-pack survival appearance are rejected.

## Character production

The approved 2D heroes are to be reproduced as custom-built or remodeled stylized 3D production characters. Existing rigged GLBs may be used as technical/remodeling bases when useful, but they are not the identity authority. Fab/marketplace characters may not replace Characters 1–4. Fab is optional support for animation and non-hero assets only.

Required production path:

`approved 2D turnaround -> custom/remodeled stylized 3D -> stylized materials -> humanoid rig -> Unity front/3/4/side/back proof -> deformation/gameplay proof -> human visual approval`

All four current character approval entries remain blocked until that proof is accepted.

## Implemented gameplay runtime retained

- Proximity-driven automatic actions with range, priority, facing and hysteresis.
- Movement-only touch control with automatic running at sustained full-stick input.
- Automatic gathering, combat, delivery, rescue, construction and repair.
- Visible mixed-resource carrying.
- Four-stage furnace progression, expanding warmth, damage and automatic repair.
- Persistent helper gathering, delivery, construction, repair and defense jobs.
- Persistent staged barricades with separate north/south identities.
- Wolf pressure and wave progression.
- Connected-area gate progression.
- Versioned save/resume for the opening loop.
- Close isometric camera and foldable-safe HUD behavior.
- Adaptive 60/90/120 Hz targets.
- Vulkan primary graphics with OpenGLES3 fallback.

## Rejected art generation path retired

The former deterministic art chain:

`HavenlineProceduralArtStudio -> HavenlineR31ProductionArtUpgrade -> HavenlineR32ProductionArtUpgrade -> HavenlineR32VisualRecoveryPass`

is no longer allowed to run from clean-checkout CI or the Android build entry points. It produced the realistic/prototype survival-game drift that failed the visual target. Its source remains only as historical/reference material while the replacement animated production set is authored.

The previous R28/R31/R32 production-art manifest is explicitly `approved=false` and its `artVersion` is marked `blocked`. Builds must fail closed rather than silently recreate or promote that rejected presentation.

## Unity repository hygiene

`Library`, `Temp`, `Obj`, `Logs`, `UserSettings`, `.utmp`, `.cxx`, `.externalNativeBuild`, Gradle/build output and IDE-generated files are ignored.

Unity source validation also imports the project and commits previously-untracked Unity `.meta` files so a fresh checkout does not immediately generate hundreds of source-tree metadata changes.

## Release blockade

No APK is visually approved for delivery.

Promotion stays blocked until all of the following are true:

1. Characters 1–4 faithfully match the approved 2D identities in stylized 3D.
2. The full environment matches the approved example video's animated visual language.
3. Actual Unity gameplay frames pass explicit human visual review.
4. Production-art, character, scene, functional and visual-direction gates pass on the exact source commit.
5. An installable ARM64 device-test build passes Galaxy Z Fold gameplay review.
6. Required 60/90/120 Hz, thermal, memory, save/resume, suspend/resume, fold/unfold, complete-loop and crash-free physical acceptance is complete.

Automated tests are necessary but are never sufficient evidence of visual approval.

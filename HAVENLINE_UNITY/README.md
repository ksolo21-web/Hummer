# HAVENLINE — clean Unity reference rebuild

This is the production restart of HAVENLINE in the repository path already connected to the Unity project: `HAVENLINE_UNITY`.

The folder name is retained for Unity connectivity, but its implementation is entirely new. It does **not** reuse the abandoned generator, Godot scenes, Godot runtime, generated prefabs, local auto-build scripts, or previous build outputs.

## Reference identity

- Reference APK: `HAVENLINE-v0.3.0-reference-final-ARM64.apk`
- Reference APK size: `107,695,534` bytes
- Reference APK SHA-256: `17996ba270e6b56505d3273fca1915f977f6d892b4949f37c66098ac6efcfa67`
- Unity editor: `6000.3.18f1`
- Render pipeline: URP 17.3
- Android: ARM64, IL2CPP, landscape, API 26+

The machine-readable reference is `Assets/Havenline/Reference/HAVENLINE_REFERENCE_CONTRACT.json`.

## Required gameplay

The first vertical slice contains the original compact frozen-outpost presentation and loop:

- close three-quarter orthographic camera;
- screen-relative keyboard, controller, and touch movement;
- bounded world and safe fall recovery;
- automatic nearby gathering;
- visible carried supplies;
- automatic furnace delivery;
- furnace upgrades and visible warmth expansion;
- rescueable survivor and autonomous helper behavior;
- repairable barricades;
- animated wolf attacks and escalating waves;
- compact safe-area-aware Galaxy Z Fold HUD.

## Deterministic scene and build

The project authors the exact Unity scene from normal source files and checksum-locked CC0 reference art:

- scene authoring: `Havenline.Editor.HavenlineSceneAuthoring.Author`
- asset bootstrap: `Havenline.Editor.HavenlineAssetBootstrap.Bootstrap`
- Android build: `Havenline.Editor.HavenlineBuildPipeline.BuildAndroidReviewCandidate`

Unity Build Automation target:

- repository: `ksolo21-web/Hummer`
- branch: `havenline-unity-reference-rebuild`
- project path: `HAVENLINE_UNITY`
- editor: `6000.3.18f1`
- platform: Android
- custom build method: `Havenline.Editor.HavenlineBuildPipeline.BuildAndroidReviewCandidate`

Expected outputs:

- `Builds/Android/HAVENLINE-Unity-reference-review-arm64.apk`
- `Builds/Android/HAVENLINE-Unity-reference-review-arm64.apk.sha256`
- `Builds/Review/HAVENLINE-reference-frozen-outpost.png`
- `Builds/Review/HAVENLINE-reference-close-camera.png`
- `Builds/Review/HAVENLINE-evidence.json`

## Truth gate

Source code is not a completed game build. This project remains unapproved until the same commit produces a compiled Unity scene, two Unity-rendered frames, an installable APK, build evidence, and a successful physical Galaxy Z Fold test.

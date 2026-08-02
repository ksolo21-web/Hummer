# HAVENLINE local Unity build

This path uses the Unity Personal entitlement already active in Unity Hub. It does not require a GitHub `UNITY_LICENSE`, Unity password, or Google password.

## Open the correct project

In Unity Hub, add or open the folder named `HAVENLINE_UNITY`. Do not open the repository root as the Unity project.

The expected editor version is Unity `6000.3.18f1` with these modules installed:

- Android Build Support
- Android SDK & NDK Tools
- OpenJDK

## Build

1. Sign into Unity Hub normally with Google and confirm the Personal license is active.
2. Pull the latest `agent/havenline-unity6-urp-rebuild` branch.
3. Open `HAVENLINE_UNITY` in Unity.
4. Wait for package import and script compilation to finish.
5. From the Unity menu, select **HAVENLINE > Build Android Review APK Locally**.

The command will:

- switch to Android;
- fetch/use the imported production assets already present in the project;
- create the frozen-outpost prefabs, animation controllers, URP configuration, NavMesh, HUD, and authored Unity scene;
- capture the review frames;
- export the ARM64 development APK.

## Outputs

- `HAVENLINE_UNITY/Builds/Android/HAVENLINE-Unity6-review-candidate-arm64.apk`
- `HAVENLINE_UNITY/Builds/Review/HAVENLINE-unity-frozen-outpost.png`
- `HAVENLINE_UNITY/Builds/Review/HAVENLINE-unity-close-camera.png`

Do not treat the APK as production-approved until the rendered scene is visually accepted and the APK is tested on the target Galaxy Z Fold hardware.

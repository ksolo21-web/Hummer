# KREATIV Studio 0.1

A single adaptive Android art studio codebase for Olivia: drawing and painting, active-pen input when hardware supports it, touch precision for foldables, layers, vector-like shapes, text and object movement, lesson paths, hybrid local/cloud AI, attachments, journaling, replay, Google/Firebase identity and recovery, and five dark owl themes.

## Integrated 0.1 capabilities

- Adaptive phone, foldable, tabletop, and tablet interface with vertical and horizontal overflow protection.
- Royal Owl, Midnight Owl, Ember Owl, Moonfeather, and Forest Nocturne dark themes with high-contrast typography.
- Olivia owner recognition by exact Firebase UID, personalized welcome, and private From Kaleb message.
- Pressure, tilt, orientation, hover, palm rejection, touch precision, pan, zoom, rotation, stabilization, symmetry, and perspective guides.
- Pencil, ink, marker, charcoal, watercolor, smudge, eraser, text, fill, selection, layers, replay, and custom brush memory.
- Perfect lines, rectangles, ellipses, triangles, polygons, stars, arcs, and arrows.
- Nine guided art-learning paths with progress recovery and KREATIV Mentor local/cloud routing.
- Local-first autosave, undo/redo, project version data, cloud recovery, attachment backup/restore, journals, PNG export, and portable `.kreativ.json` projects.

## Verified Android build

KREATIV Studio 0.1 is an installable Android project, not a source-only handoff. The integrated project was compiled with Android SDK 37.0 and Build Tools 36.0.0; unit tests passed and `app-debug.apk` was assembled successfully. `BUILD_VERIFICATION.txt` records the command, APK identity, checksum, and the remaining device/Firebase verification boundary. The included GitHub Actions workflow repeats lint, unit tests, and APK assembly on every pull request.

## Open and build

Open the root folder in a current Android Studio installation, or run `./gradlew assembleDebug` (`gradlew.bat assembleDebug` on Windows). The included launcher downloads and checksum-verifies Gradle 9.4.1 when it is not already cached.

Private values belong in `~/.gradle/gradle.properties`, never source control:

```properties
KREATIV_GOOGLE_WEB_CLIENT_ID=
KREATIV_OLIVIA_FIREBASE_UID=
KREATIV_FIREBASE_API_KEY=
KREATIV_FIREBASE_APP_ID=
KREATIV_FIREBASE_PROJECT_ID=
KREATIV_FIREBASE_STORAGE_BUCKET=
KREATIV_FIREBASE_AI_MODEL=gemini-3.6-flash
```

Olivia's special owner experience is activated only when the authenticated Firebase UID exactly matches `KREATIV_OLIVIA_FIREBASE_UID`. The local Olivia preview is intentionally marked as a preview.

The built-in art-teaching coach works without a network or Firebase configuration. With private Firebase values configured, the Mentor can check and download Gemini Nano on supported Android devices, prefer on-device inference, and use the configured Firebase AI cloud model when appropriate. Model availability and download are managed by Android's on-device AI service and must be verified on supported hardware.

Deploy `firebase/firestore.rules` and `firebase/storage.rules` before enabling production sync. They restrict every user's studio data to that authenticated user.

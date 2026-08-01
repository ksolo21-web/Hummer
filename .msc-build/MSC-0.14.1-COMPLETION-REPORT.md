# My Study Companion 0.14.1 — Completion Report

This report applies to the exact pull-request head whose source, application, security, backend, and stable-signing workflows are green.

## Completed application scope

- Existing canonical Android package `com.mystudycompanion.app` retained; no second debug application identity was created.
- Phone, tablet, Galaxy Z Fold/Z Flip, landscape, split-screen, and desktop-windowed layouts retain safe status-bar, cutout, navigation-bar, and hinge foundations.
- Unified Study Reader covers Daily Text, scriptures, Watchtower study material, meeting material, Bible journeys, Family Worship, event material, imported official pages, and interactive workbooks.
- Official pages are transformed into readable semantic headings, scriptures, questions, and paragraphs through an HTTPS-only JW.org/Watchtower Online Library reader with redirect, content-type, response-size, timeout, and hostname protections.
- Android and web include document and section reading, material-note and paragraph-note reading, play/resume, pause, stop, repeat, previous/next, speed controls, dictation, bookmarks, highlights, and extra-large high-contrast glasses mode.
- Reader state uses per-document monotonic revisions with conflict-safe offline/cloud reconciliation, including intentional note, highlight, and bookmark deletions.
- Family and household management remains consolidated under Family Hub; duplicate household controls were removed from Companion/More.
- Weekly Family Worship preparation and start reminders use WorkManager and reopen Family Hub through the app deep link.
- Circuit Assembly, Convention, and Family Worship workbooks retain drawing, coloring, color-by-number, matching, crossword and word work, notes, checks, completion, offline storage, page synchronization, blank/completed PDF export, print, and Wear companion actions.
- Wear OS retains Daily Text, field-service pointer, journey, event, workbook, Study Reader progress, previous/next, voice-note, completion, and playback handoff actions.
- The PWA retains Today, Journeys, Events, workbook cards, installability, offline cache, Firebase authentication/sync, revision-aware conflict handling, Media Session and wearable bridge controls, runtime Firebase configuration loading, and same-origin official-content reading.
- The private service reports release `0.14.1`, mounts the official reader and private MCP surface, preserves App Check and user-auth enforcement for private routes, and retains signed content, AI Study, device, push, and household endpoints.
- A protected manual deployment workflow is included for Google Workload Identity Federation, Cloud Secret Manager, Cloud Run, Firebase Hosting, runtime web configuration, and tested Firestore rules.

## Exact-head verification requirements

The release head must pass all of these together:

- Exact deterministic source reconstruction and source artifact packaging.
- Android phone/tablet unit tests, Kotlin compilation, debug APK, and canonical private-alpha APK assembly.
- Wear OS unit tests, Kotlin compilation, debug APK, and canonical private-alpha APK assembly.
- Stable private-alpha signer restoration, certificate fingerprint verification, package/version verification, 16-KiB APK alignment, and artifact checksums.
- Web JavaScript syntax checks and revision-aware merge tests.
- Complete PWA packaging and content-preservation audit.
- Official-reader and private-service Python test suite.
- 26 Firestore authorization, ownership, payload, workbook, reader, and cross-household isolation tests.
- Fold-safe layout, Family Hub, reminder, speech, dictation, repeat, note-reading, backend, wearable, runtime-config, and deployment markers.

## Protected external acceptance

These actions require protected accounts, hardware, or offline keys and cannot be performed by source control alone:

1. Run `Deploy My Study Companion 0.14.1 Private Stack` after the required Google Cloud/Firebase environment variables, Workload Identity provider, runtime/deploy service accounts, and Secret Manager entries are present.
2. Install the stable private-alpha phone APK over the currently installed app. Android preserves the app and its data only when package ID and installed signer match the verified private-alpha signer.
3. Install and pair the Wear APK, then perform physical interaction checks.
4. Verify Z Fold cover/unfolded, landscape, split-screen, keyboard, cutout, notification-bar, hinge, and sustained-frame-rate behavior on Kaleb's actual device.
5. Verify Meta glasses/browser accessibility, speech, microphone permission, and handoff behavior on the actual glasses and browser versions.
6. Use the protected offline permanent release/Play key for the eventual permanent release; that key is intentionally absent from GitHub.

No temporary CI-signed APK should be represented as the permanent Play/release build.

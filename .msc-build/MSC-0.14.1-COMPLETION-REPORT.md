# My Study Companion 0.14.1 — Release-Candidate Completion Report

This report applies to the exact pull-request head only after the source, application, security, backend, theme, and stable-signing workflows are green. A green source-control build proves the release-candidate implementation; it does not replace protected cloud deployment or physical-device acceptance.

## Completed implementation scope

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

## Expanded appearance system

- All previously approved themes remain available.
- The permanent gallery now contains 23 themes plus Automatic.
- The 13 new illustrated concepts are Waterfall Serenity, Rainforest Harmony, Ocean Majesty, Celestial Wonder, Mountain Sunrise, Creation Garden, Bible Sketch Study, Parable Line Panels, Noah’s Ark, Red Sea Deliverance, Creation Sky, Bible Timeline, and Bible Map.
- Each new illustrated concept has deterministic original app-owned scenery for Android, Wear OS, widgets, and the PWA; no publication artwork is copied into the app.
- Calm Light, Premium Dark, bright-white Warm Editorial, Owl, Fox, Lion, Tiger, and the previously added animal variants remain preserved.
- Android and web use a visual hue/saturation color wheel with brightness control for custom accent and background colors. The user is no longer required to type a hexadecimal color code.
- Theme selection and custom color state are designed to remain consistent across supported application surfaces and synchronization.

## Exact-head verification requirements

The release-candidate head must pass all of these together:

- Exact deterministic source reconstruction, original theme-scene generation, and source artifact packaging.
- Android phone/tablet unit tests, Kotlin compilation, debug APK, and canonical private-alpha APK assembly.
- Wear OS unit tests, Kotlin compilation, debug APK, and canonical private-alpha APK assembly.
- Stable private-alpha signer restoration, certificate fingerprint verification, package/version verification, 16-KiB APK alignment, and artifact checksums.
- Web JavaScript syntax, appearance persistence, visual color-wheel, and revision-aware merge tests.
- Complete PWA packaging, theme-scene inclusion, service-worker cache, and content-preservation audit.
- Official-reader and private-service Python test suite.
- 26 Firestore authorization, ownership, payload, workbook, reader, and cross-household isolation tests.
- Fold-safe layout, Family Hub, reminder, speech, dictation, repeat, note-reading, theme, widget, backend, wearable, runtime-config, and deployment markers.

## Protected live and physical acceptance still required

The following are part of the build’s final acceptance and must not be silently treated as completed by CI:

1. Register or verify the Firebase Web App, generate its actual runtime configuration, deploy the updated PWA, and confirm the live sign-in state changes correctly.
2. Deploy the private HTTPS official-content/backend service with the protected content-signing key, Firebase/App Check configuration, FCM, Google authentication, service accounts, and required Secret Manager entries.
3. Configure the Android backend URL and Google web client ID in the signed build used on the device.
4. Run and verify scheduled Daily Text, weekly meeting, Watchtower, and Family Worship refreshes without replacing notes, bookmarks, workbook progress, or deliberate deletions.
5. Use real Firebase accounts to test Android/web sign-in, household invitations, joining, roles, permissions, removal, and Family Hub data migration.
6. Verify notes, highlights, bookmarks, reading position, theme settings, and workbook pages synchronize among phone, tablet, PWA, and Wear OS, including offline edits and reconnection conflicts.
7. Validate every promised Study Reader content type and every speech target: whole document, section, paragraph, scripture, question, answer, material note, and personal note.
8. Verify Watchtower questions, paragraphs, answers, anchors, old saved notes, and reading positions survive reopen, upgrade, and cross-device synchronization.
9. Test the supported Meta glasses/browser accessibility, speech, microphone, large-text view, and handoff workflow on the actual glasses. No unsupported direct Meta AI API control is claimed.
10. Install on Kaleb’s Z Fold 7 and test cover/unfolded layouts, landscape, split-screen, tablet/desktop-window behavior, keyboard, status/navigation bars, camera cutout, hinge, and measured 60–120 Hz interaction quality.
11. Review every theme—including all 13 new concepts—on the real Fold displays, Wear device, phone/tablet widgets, and Flip cover-screen foundation; correct contrast, crop, scaling, oversized UI, or visual inconsistency discovered there.
12. Physically test all workbook tools: drawing, handwriting, coloring, color-by-number, matching, crossword/word activities, checkmarks, typed/free notes, age variants, autosave, reopen, offline use, page sync, PDF export, printing, Wear progress, completion, and voice notes.
13. Physically verify Family Hub topic ideas, voting, organizer choice, scheduling, repeat settings, reminders, membership, invitations, roles, permissions, and preservation of previously saved family data.
14. Install the stable private-alpha phone APK over the currently installed app and confirm one application icon, preserved database/user data, and upgrade compatibility. Install and pair the Wear APK and verify its update path.
15. Use the protected offline permanent release/Play key for the eventual permanent release; that key is intentionally absent from GitHub.

No temporary CI-signed APK should be represented as the permanent Play/release build, and the release must not be called physically accepted until the protected deployment and real-device cycle above is complete.

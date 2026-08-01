# My Study Companion 0.14.1 — Last Major Build Scope Lock

This checklist is a release gate. New work must extend the verified application; it must not remove, regress, rename away, or silently bypass previously approved functionality.

## Identity and upgrade safety

- Update the existing Android app only. Do not create a second debug application.
- Preserve package `com.mystudycompanion.app`, application data, database migrations, and the established signing/update identity.
- Keep the Wear OS application as the paired companion package and preserve upgrade compatibility.
- Keep the web application installable as a PWA.

## Supported devices and layout

- Android phones, Galaxy Z Fold/Z Flip, tablets, landscape, split-screen, and desktop-windowed Android.
- Correct compact, medium, expanded, large, and extra-large layouts.
- Respect status bar, camera cutout, navigation bar, hinge, and display-feature insets. No content beneath the Z Fold notification/status bar.
- Preserve smooth 60–120 Hz interaction foundations; do not introduce avoidable blocking or jank.
- Wear OS remains a functional companion, not a static mockup.

## Official content policy

- Spiritual source material is limited to JW.org and Watchtower Online Library.
- Scriptures use the New World Translation Study Edition.
- Preserve exact dated Daily Text, scripture, publication, meeting-week, and JW Library/JW.org destinations.
- Do not replace exact content with generic category pages or unrelated search results.

## Unified Study Reader and connected notes

- Provide one readable study surface for Daily Text, scriptures, Watchtower study material, meeting material, Bible journeys, family-worship material, event programs, and interactive workbooks.
- Display the actual available study text in readable, selectable, accessible sections instead of only showing an external-link description.
- Add read-aloud controls for the current document, section, paragraph, scripture, question, answer, and user note.
- Include play, pause, resume, stop, repeat, previous/next section, and reading-speed controls.
- Add a large-text, high-contrast glasses-friendly mode with proper semantic headings and focus order.
- Expose supported browser accessibility and speech controls so the web surface can work with Meta glasses workflows where Meta permits it. Do not claim unsupported direct Meta control without verification.
- Allow text notes, voice-originated notes, highlights/bookmarks, and paragraph-level notes anchored to the exact material location.
- Preserve note anchors when reopening material and when moving among phone/tablet, web, and Wear OS.
- Keep user notes private to the user/household according to the selected sharing setting.
- Provide offline storage and safe synchronization with conflict handling.

## Daily Text and study content

- Show the full available Daily Text scripture, comments, and reference in the application/web reader.
- Keep the dated official destination available.
- Preserve weekly meeting deep dives, Watchtower preparation, scriptures, questions, answers, and personal notes.
- Preserve Bible Story, Theme, and Timeline Journeys and saved progress.
- Preserve the daily Field Service Pointer for Everyone.
- Preserve AI Study with the established official-source grounding rules.

## Family Hub consolidation

- Move family/household settings out of Companion and into the Family section.
- Rename the consolidated destination to `Family Hub` unless a later UI review establishes a clearer name.
- Keep Family Worship topic ideas, voting, organizer choice, schedule, repeat settings, reminders, household membership, invitations, permissions, and family synchronization together.
- Do not duplicate family settings in Companion after migration.
- Preserve current family data during navigation/storage migration.

## Interactive workbook engine

- Keep Circuit Assembly, Regional Convention/District Convention, and Family Worship interactive workbooks.
- Preserve child, preteen, and teen content levels and their age-appropriate generated activities.
- Support drawing, coloring, color-by-number, matching, crossword/word activities, lines, checkmarks, text notes, free-form notes, and completion tracking.
- Preserve offline editing, autosave, per-page synchronization, resume position, and PDF export/print behavior.
- Keep the workbooks available in the main Android app, web app, and useful Wear companion actions.

## Web application

- Preserve working Today, Journeys, and Events navigation.
- Preserve sign-in state after successful authentication; do not leave `Sign in & sync` displayed after sign-in.
- Keep offline PWA installation and service-worker behavior.
- Add the unified Study Reader, connected notes, read-aloud, and glasses-friendly mode without removing existing content cards.
- Keep full content readable without requiring the user to leave the web app for every paragraph.
- Preserve page-sized Firebase synchronization and household isolation.

## Wear OS

- Show current study item, active section/page, progress, next action, bookmarks, and completion controls.
- Support quick voice-note capture and phone/web handoff.
- Provide remote read-aloud controls where practical without pretending the watch is the full publication reader.
- Preserve Daily Text, pointer, journey, event, and workbook companion surfaces.

## Themes, scenery, contrast, and widgets

- Preserve Calm Light, Premium Dark, and bright-white Warm Editorial.
- Preserve Owl, Fox, Lion, and Tiger themes with the intended animal/scenery artwork.
- Theme background, cards, text, greeting, widgets, and system bars must maintain readable contrast.
- Preserve user-selectable theme colors and background colors.
- Preserve phone/tablet widgets and Flip cover/front-screen widget foundations.

## Authentication, Firebase, and security

- Preserve Google/Firebase sign-in and correct UI state transitions.
- Preserve household invitations/join flows and authorization boundaries.
- Keep Firestore payload limits, ownership validation, cross-household isolation, and workbook integrity tests.
- Do not weaken tests to obtain a green build.
- Keep local data usable when offline and reconcile safely after reconnection.

## Release verification gates

The release is not complete until all applicable gates pass on the exact final head:

1. Android phone/tablet unit tests and compilation.
2. Wear OS unit tests and compilation.
3. Canonical Android and Wear APK assembly with correct identities.
4. Web JavaScript syntax, navigation, PWA, accessibility, read-aloud, and content-preservation checks.
5. Unified reader and paragraph-anchored note tests.
6. Fold/status-bar/cutout inset checks.
7. Interactive workbook generation, editing, persistence, sync, and PDF-export checks.
8. Firestore authorization, integrity, payload-limit, and cross-household tests.
9. Upgrade/data-preservation verification; no second application install.
10. Final artifact packaging and checksum report.

No item above may be silently deferred while labeling this the last major build.

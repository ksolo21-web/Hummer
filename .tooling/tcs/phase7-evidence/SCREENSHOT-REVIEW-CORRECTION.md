# Visual evidence correction

Inspection of run 36491525561 screenshots found an emulator Pixel Launcher ANR dialog covering the actual application. The Compose assertions and saved JSON still established executed app actions, but those screenshots were NOT acceptable visible UI evidence. Do not call them visually passed or use them for card approval.

Patch 08 adds before/after active-window package witnesses bound to the exact full-screen PNG SHA-256. An obstructing or missing window makes the screenshot fail, retaining both the PNG and a rejected receipt for diagnosis. The isolated screen harness now includes system-bar insets, matching the production Scaffold's safe content area. No screenshot is cropped to hide an error and no in-test blocking dialog is dismissed.

The CI emulator's unrelated Pixel Launcher is explicitly disabled before Phase 7 tests, with setup and restoration logs, only after confirming ro.kernel.qemu=1. Target-app ANR events are captured and fail the run. The runner verifies all 20 screenshot hashes and active-window receipts in addition to the existing positive/negative software tests. This is emulator test isolation, not a modification to the user's device or app's production behavior.

Eight integration patches verify thirteen exact Kotlin source files. Phase 7 remains open and no card is approved by these regression tests.

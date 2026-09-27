# Phase 2J repair history

- Pre-CI independent source review identified missing bitmap ownership cleanup in Workspace preview. Corrected cancellation-safe IO handoff cleanup and replacement/disposal cleanup. Independent critic verified the correction before source ad4cc8209828af06c56edda1e66249fb3deba35a entered CI.
- Authoritative initial run: 36304697541. Reconstruction, core/app/test compile, APK/AAB packaging and toolchain passed; runtime completed 57 tests with 5 failures (all five new Phase 2J tests passed). Failures: old 2D-B light capture, 2E themed capture, 2F screenshot null, and two 2G DocumentsUI accessibility waits. Logs show the picker Activity displayed and no app crash; rendering/accessibility presentation failure is under investigation. Retry keeps all assertions, explicitly wakes/unlocks/keeps the emulator display on, and records failure screenshot/window/power diagnostics.
- Follow-through identified inherited candidate-only footer/recovery wording in the newly supported approved fullscreen PDF path. Prepared a scoped wording correction and explicit approved-page assertion; do not accept or freeze until this exact correction is validated.

All 51 core source files remain unchanged from Phase 2I. No prior milestone revoked; no reserved territory commissioned. Phase 2J remains IN_PROGRESS.


## Phase 2J current-chat repair

- Retry 36305358958 passed all 57 connected tests and both capture runs. Freeze correctly rejected 20 screenshots with only 18 unique hashes: wide candidate/approved pairs contained only the same synthetic PDF front page, with state labels outside the viewport.
- Independent critic also found a phone capture racing a lazy-item PDF reload and a missing theme background in the isolated screenshot harness.
- Bounded the Workspace PDF preview height without changing PDF bytes or aspect ratio; capture tests now include and assert the candidate/approved label, re-wait for the PDF after scrolling, and use the production theme Surface background.
- Full verification provenance is no longer ellipsized, and inventory provenance exposes exact territory/mode/imported-source/prepared-input bindings. Both inventory-mode tests assert those identities.
- All 57 connected tests, all 20 actual screenshots, source/APK/AAB integrity, reserved-territory sweep, and independent critic remain mandatory. Phase 2J remains OPEN until these exact repairs pass.

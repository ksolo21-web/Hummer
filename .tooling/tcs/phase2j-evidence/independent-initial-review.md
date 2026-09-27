# Phase 2J independent initial review
Status: BLOCKED; score 7/10 against complete Phase 2J acceptance. No milestone approval.

Evidence reviewed: requirements.md; WorkspaceCurrentUi.kt; WorkspaceModeUi.kt; WorkspacePreparedSnapshot.kt; AndroidBuildWorkflowCoordinator.kt; VerificationWorkflowUi.kt; five Phase2J tests; actual prior-run phone candidate-light, addresses-light, telephone-dark, verification-light and wide candidate-dark PNGs.

Mandatory defects:
1. Wide candidate/approved identity is outside frame; enlarged PDF alone occupies viewport and duplicate fixture bytes yield identical evidence. Bound inline image height and capture visible state label + PDF.
2. Phone candidate-light screenshot has Checking current PDF and no bitmap. Lazy scroll can dispose/recreate item after initial wait. Recheck displayed bitmap after final scroll before screenshot.
3. Light screenshots use dark gray background with low-contrast black/dark text outside cards. Test theme lacks Surface background; compare production wrapping and correct harness to render actual theme faithfully.

Source findings: coordinator revalidates source/input/inventory hashes and returns detached rows; approved bytes independently verified; categories use bound prepared data and candidate field release remains separate. No confirmed authority escalation or core changes observed. UI snapshots refresh at workflow/source/lifecycle/manual boundaries; arbitrary external mutation while screen remains active is not observed automatically. Clarify intended refresh boundaries; existing resume test covers supported lifecycle.

Next: inspect repaired exact-source runtime captures and test/package evidence. All mandatory checks must pass before >9 score.

## Repaired source review (before CI)
Reviewed repair source submitted as a27aa735172efaa29dbfaf9657234bcaa6b80abf. Bounded 320dp Fit preserves aspect ratio; tagged approval label and bitmap visibility assertions protect capture purpose; post-scroll page wait addresses lazy recompose; Surface wrapping matches production themed Scaffold (verified in recovered Phase2I ProductionUi.kt). Complete source/input identities added to both inventory provenance lists with test assertions. Verification text no longer ellipsized. No source-level blocker identified. Runtime not yet accepted.

## Final acceptance checklist
- Exact submitted source hashes match packaged source and evidence.
- Core/previous gates unchanged; six reserved territories remain without PDFs.
- 57/57 connected tests, phone and wide capture runs pass.
- 20 actual PNGs unique with matching phone/wide dimensions.
- Phone/wide Light/Dark candidate/approved frames include correct identity and rendered PDF.
- Address/telephone records and unavailable number readable; inventories preserve identity/provenance.
- Verification theme contrast corrected and Build/field-release separation visible.
- Source/input/inventory mutation, lifecycle invalidation, exact approved independence and tamper rejection tests pass.
- APK/AAB package integrity, version0.2.10/code35, signing and exact hashes pass.
- Checkpoint/ledger do not promote field approval or aggregate Phase2; durable evidence saved/readback.

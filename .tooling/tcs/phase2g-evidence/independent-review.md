# Independent review — Phase 2G guarded approved-card export

**PASS — 10/10 against the bounded milestone requirements. All mandatory gates passed.**

Source: `c75f34998e6d0d9054502828a5ecc1e2edcd54fb`.
CI run: `36292365557`. Version: 0.2.7 / code 32.

## Independently inspected evidence

- Read requirements, export service/UI/integration and positive/negative tests. Reviewed the two test-only synchronization repairs against actual earlier failure XML.
- Parsed final connected-test XML: exactly 41 cases, zero failures/errors/skips; includes all 33 prior cases and eight export cases.
- Core, Android compilation, packaging and connected-test logs report successful builds. Dedicated actual-picker capture log reports `OK (1 test)`.
- Verified source archive hash against its manifest; every Phase 2G source manifest entry matches reviewed local files and the corresponding archived bytes.
- Read source-preservation report: 79 of 82 prior source/test files unchanged. Two production integration files changed, and one prior Phase 2F capture test gained keyboard-dismissal/dialog readiness waits. Prior cases/assertions are preserved, but that test is not byte-unchanged. Frozen rendering, artifact, intake, Build, Preview and Review service behavior is preserved.
- Reserved-territory PDF sweep is empty.
- Independently hashed the actual exported PDF: 81,696 bytes; SHA-256 `8a9d452727931e4c42ed51db647a7aeb760274c9de55b84720a3652af40973b6`. This matches the exact approved fixture authority and the displayed success hash.

## Functional acceptance

Frozen exact-approved authorization governs attach, resolve and export. Unknown/reserved/needs-new-card slots and locally approved generated candidates remain blocked. Attachment is bounded to 16 MiB and must match authority before replacement; bad, oversized and failed reads preserve the previous original. Export revalidates the ticket after the picker, writes a verified immutable snapshot, closes output, reads back and compares byte count/hash before success. Failure paths do not report success; cleanup is limited to the fresh destination and explicitly reports failure to remove it. Pending tickets are consumed; restoration without the unsaved ticket rejects export. Territory/service changes reset screen-local state.

Tests cover exact-byte save and restart, authorization exclusions, attachment failures, stale/wrong/missing/expired tickets with no write, provider write/readback/cleanup failures, actual system picker save/cancel/attachment/stale return, screen restoration while picker is active and real workspace navigation without commissioning.

## Nine actual screenshots inspected individually

- Missing dark: exact identity/hash displayed, missing approved copy explained, Save disabled and attach available.
- Ready dark: verified exact copy, byte count and Save enabled.
- System picker: genuine Android Downloads/CreateDocument screen with canonical suggested PDF filename and Save action.
- Saved dark: success includes byte count/hash and explicit readback verification.
- Cancelled dark: cancellation states that no PDF was exported; no success card.
- Stale blocked dark: hash mismatch blocks Save; failure explicitly reports removal of the newly created destination.
- Ready light: same identity/availability hierarchy and enabled save in light theme.
- Saved light: verified success remains readable, with exact hash and byte count.
- Reserved blocked light: territory T250/250T explicitly blocked as needs_new_card; no attach action and disabled Save.

All nine frames show their intended actual states. Required text/controls are readable and reachable through scrolling. No blank frame, overlapping controls, or candidate/local-approval-to-field-export confusion observed. Existing approved card export is explicitly independent of candidate workspace mode.

## Scope limits

This approval covers unchanged copies of existing KB-approved artifacts only. Synthetic approved authority exists only in androidTest. It does not authorize generated candidate export, promote authority, commission real territories, automatically share documents or certify the overall app complete. Historical approved artifact size is preserved unchanged within the bounded attachment/export limit. No mandatory defect or evidence gap remains for this milestone. Persist checkpoint/ledger and this review for closeout.

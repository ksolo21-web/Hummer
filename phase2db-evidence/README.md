# Phase 2D-B evidence scope

This increment adds a guarded Build screen and a session coordinator around the frozen production render-model adapter and Phase 2D-A PDF service. Production generation requires a source-map copy whose bytes match its intake record, a prepared validated render input, and (for Page 2) a mode-correct typed inventory. The UI cannot fabricate any of these inputs.

The preparation API is intended for the later editing/commissioning pipeline. The shipped Knowledge Base currently has no UI producer of prepared inputs, so real workspaces remain blocked. Tests use isolated synthetic 999a and T998a assignments in androidTest only, with synthetic provider evidence. They demonstrate integration behavior, not real geographical verification or field approval.

The regular mode builds a front candidate only. Letter Writing and Telephone modes can generate a two-page candidate after the front and inventory pass validation. Neither operation approves or exports a field card. Prepared readiness is session-only; process restart requires fresh preparation.

Inputs and artifact bytes are rechecked on each action. Changed source records, mutated prepared input or inventory, and corrupted artifacts invalidate affected readiness/results. A source-map storage recovery defect found during independent review was fixed by atomically replacing an existing hash-named file with the freshly validated import; the corruption/reimport test exercises the repair.

Validation and independent visual review results are recorded separately after the live CI run completes. All six reserved commissioning territories remain untouched.

## Completed validation

Phase 2D-B PASSED. Version 0.2.4 (29), target SDK 37. [CI run 36286928229](https://github.com/ksolo21-web/Hummer/actions/runs/36286928229) succeeded at source commit `4e0eb218670f5565aca05416d297d0b9828b5ef8`. All 19 connected tests and the dedicated capture test passed; six real light/dark display captures were reviewed. Independent review: 10/10.

The full source/APK/AAB archive is in CI artifact 10921390468, expiring 2026-10-11. The repository retains reconstruction overlays, workflow, hashes, compact reports and screenshots. See closeout.json and independent-review.md for exact evidence and scope.

Next bounded task: actual vector PDF Preview integration, preserving exact artifact identity and hash. Real preparation/commissioning, approval and release remain later gates.

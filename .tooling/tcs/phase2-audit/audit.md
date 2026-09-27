# Phase 2 production UI closeout audit

Decision: **NOT READY for aggregate Phase 2 closeout.** Phase 2A–2G retain their bounded PASS decisions. This audit adds missing acceptance work; it does not revoke evidence for unchanged implementations or authorize real commissioning.

Baseline: Android 0.2.7 / code 32; source c75f34998e6d0d9054502828a5ecc1e2edcd54fb; closeout 95302faf1e3aa53bc1a3fac65241bce55ddc2f50. Run 36292365557 is successful. Its 41 connected cases contain no failures/errors/skips. Tests demonstrate bounded services and screens, not completion of every requirement in the overall plan.

Authority: ANDROID_PORT_PLAN.md, Library libfile_aa4a3221671c8191b1b85115ff7bb91a, version 1, modified 2026-09-26T18:45:47.849510Z; all 147 lines read. The adjacent snapshot preserves its content. Its old Phase 1 status annotations are historical; the current checkpoint governs completed milestones.

## Acceptance matrix

| Requirement | Finding | Evidence / remaining work |
| --- | --- | --- |
| Shared Light/Dark design and System preference | Passed for implemented screens | Phase 2A evidence and current preference tests; subsequent light/dark captures. New screens still need both themes. |
| Dashboard and territory-specific tabs | Partial | ProductionUi.kt models status and search; WorkspaceModeUi.kt supplies the required mode tabs. Explicit status filters and browsable unassigned-screening records are absent. |
| Dashboard → Workspace → Import → Verify | Passed bounded navigation | Preserved Phase 2B/2C tests. Intake is hash-bound and evidence-only. Verification remains an honest blocked/pending summary. |
| Build and Generate Page 2 call proven engine | Passed bounded service/UI | Phase 2D tests build both synthetic modes through production adapter. Actual user preparation depends on later controlled editing; no production caller of coordinator.prepare exists. |
| Actual PDF preview, navigation and zoom | Passed bounded preview | Phase 2E tests compare pixels to exact stored PDF and reject stale data. This does not satisfy the separate Workspace map requirement. |
| Explicit version/hash-bound Approve/Reject | Passed local decision UI | Phase 2F receipts, confirmation and invalidation tests. Authority promotion is correctly excluded until Phase 4. |
| Guarded exact-approved PDF export | Passed bounded export | Phase 2G actual picker, exact byte readback, cancellation and negative cases. Audit output is absent. Generated candidate field export stays blocked. |
| Knowledge Base destination | Missing | ProductionRoute contains only TERRITORIES and WORKSPACE. No browsable Knowledge Base screen for patches, conflicts, rule versions and source records. |
| Dominant map/card preview in Workspace | Missing | WorkspaceMapSurface is a text summary directing the user to Build. No image/PDF surface is rendered there. |
| Source/provenance drill-down and complete item access | Partial | Details lists at most four source hashes; Streets at most 20 segments; Buildings at most 12. Additional-count text has no action. Readiness represents conflict presence as 0/1 rather than a full finding count. Verification has category summaries, not complete item drill-down. |
| Defects associated with affected category/item | Partial | Verification categories exist, but PdfPreviewScreen exposes generic errors and no defect collection or navigation to an affected item. |
| Letter/Phone inventory display | Missing populated UI | WorkspaceAddressInventorySurface and WorkspacePhoneInventorySurface always display unavailable, even when a coordinator has verified inventory. Build displays only totals. Editing/acquiring records can remain Phase 3; read-only populated presentation is Phase 2. |
| Verification reflects current prepared state | Partial | VerificationWorkflowModel hardcodes candidate labels pending and inventory blocked without reading current prepared Build state. Bind presentation to authoritative current results, while leaving absent inputs blocked. This does not authorize commissioning. |
| Combined PDF and audit export UI | Partial | Current export is only exact existing approved PDF; no audit document. Candidate promotion remains Phase 4 and final output validation remains Phase 5. |
| Complete core navigation including Knowledge Base | Incomplete | Component tests and synthetic routes exist; missing destination prevents complete Phase 2 UI navigation coverage. Full real commissioning remains Phases 3–7. |

## Ordered remaining packages

1. **2H Knowledge Base and provenance navigation**: implement a read-only destination with real frozen KB records, territory status/reference/source detail, permanent patches, conflicts/overlap warnings, rule versions, and safe return navigation. This closes the wholly missing destination first.
2. **2I Dashboard filtering and complete browsing**: explicit status filters, unassigned-screening records without invented territory identities, and complete road/building/source access. Preserve status/release distinctions.
3. **2J Workspace exact preview, populated inventory and verification views**: render an available validated artifact using the existing preview boundary; show an honest unavailable state otherwise. Read verified inventory snapshots and bind verification categories to current prepared results without allowing edits, authority promotion or synthetic production data.
4. **2K Defect/category/item and audit-output UI**: structured findings tied to source categories/items, navigation from relevant surfaces, and explicitly scoped audit export with exact identity/hash provenance. Preserve all existing PDF authorization boundaries.
5. **Final Phase 2 audit**: rerun integrated navigation and both themes for the complete interface, independently review actual screenshots, verify source/APK/AAB freeze and evidence, then decide aggregate closeout against this unchanged plan.

These are acceptance packages within existing Phase 2, not new overall phases. Each needs its own explicit acceptance checklist before implementation. The full candidate preparation pipeline, live provider execution, authority promotion, real generated field export, and real commissioning remain their original later gates; never bypass them to make the UI demonstration look complete.

## Validation and scope

Audited the exact Kotlin sources extracted from the verified 0.2.7 freeze, current checkpoint/ledger, all 41 test case names, latest run conclusion, and prior milestone evidence. No app source or binary changed in this audit. No extra full build is warranted for this documentation-only decision. No real territory was commissioned. All six reserved territories remain untouched. Independent audit findings are recorded separately.

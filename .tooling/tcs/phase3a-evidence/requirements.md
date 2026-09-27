Status: Phase3A PASSED at independent10/10; aggregatePhase3 IN_PROGRESS. Final evidence run36334431100. See acceptance-checklist.md and verification.json.

# Phase 3: controlled editing / commissioning

Authority: original ANDROID_PORT_PLAN.md; Phase2 accepted at a61f98f2ce92b0337970ee9bac93da32250b64ea. All Phase1–2 approvals remain frozen absent changed dependencies or exact defects.

## Dependency sequence
- 3A: durable, non-authoritative editing draft service and revision integrity. Current bounded task.
- 3B: Light/Dark adaptive editor UI using this service; review staged edits, save/conflicts, reopen/restore/discard; actual screenshots and navigation evidence.
- 3C: validated preparation bridge and additional controlled geometry/assignment/inventory editing, with authoritative evidence and existing engine gates; no silent promotion.
- 3D: aggregate editing workflow, lifecycle/source changes and integration audit. Full Phase3 remains open until all requirements pass.
- Phases4–9 retain original scope. Six real commissioning territories remain untouched until Phase7.

## 3A mandatory acceptance
1. Create/reopen app-private drafts bound to exact territory/mode, KB revision/reference, verified imported source, locked road/building base data. Unknown territory/mode or absent source fails closed.
2. Initial edit payload supports proposed road/building label corrections to known exact item IDs, with before-value, rationale and evidence SHA-256. Changes are proposals only, explicitly UNVALIDATED; no authority/approval/build output mutation.
3. Persist bounded revision history atomically; verify hash-chain and parse/schema bounds on read. Corruption is an explicit error, never silently an empty draft.
4. Optimistic revision token rejects stale/concurrent saves, including multiple service instances. Changed source/base blocks saves and restore; stale drafts remain readable with explicit status for recovery.
5. Restore creates a new revision retaining history. Discard requires matching latest token. Reject malformed/unknown items, duplicate edits, invalid hashes, oversized payload/history and no-op edits.
6. Targeted on-device tests prove persistence, mode isolation, stale binding, optimistic conflicts, restore/discard, corruption, limits and unchanged authority/reference/build state. Preserve67 passing Phase2 identities without rerunning them.
7. Exact source/APK/AAB freeze, compiler/signature/version checks, independent critic strictly above9 (target10), durable checkpoint and readback. Phase3A acceptance does not imply Phase3 completion or field release.


Concurrency contract: journal mutations are serialized across store instances within the application's single process. Source imports are external operations. A source change observed before commit rejects save/restore; a concurrent change during/after commit makes the saved proposal stale when returned/read and blocks subsequent saves/restore. The store does not claim an atomic transaction across source import and draft persistence. Stale drafts may be discarded using their latest revision token. No stale proposal can grant build/approval authority through this service.

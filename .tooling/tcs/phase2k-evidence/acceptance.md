Final outcome: Phase2K and aggregate Phase2 accepted10/10. See independent-final-review.md, aggregate-audit.md and verification.json for run/source provenance.

# Phase 2K and aggregate Phase 2 acceptance

Source: phase2-audit/audit.md and ANDROID_PORT_PLAN.md at accepted Phase 2J commit 436a631a0a74614c3adbe4d8f528a8cd232f158e.

1. Structured findings retain actual territory, category, affected item (road/building/record or explicit category-level unknown), status, original reason, source authority, and exact current context. Never turn a category summary into invented per-item evidence.
2. Verification and PDF preview reach the finding list; a finding opens the corresponding Workspace tab, with item identity displayed where no exact in-tab anchor exists. The same navigation works on phone/wide and Light/Dark.
3. Export a scoped audit JSON through Android CreateDocument only after revalidating current territory, mode, KB revision, imported source hash, prepared input/inventory hash, candidate/packet hash and approval scope. Include explicit null for unavailable evidence and keep candidate field release locked. Save/read back exact bytes/hash, delete failed writes when possible.
4. Keep approved-PDF export byte-identical and independently authorized; no generated candidate PDF export or authority mutation.
5. Exercise real empty/blocked, valid prepared, stale/mutation, overlap/item and readback/failure paths with meaningful Android tests; preserve prior 57 cases. Capture and inspect phone/wide Light/Dark finding and audit surfaces, then independent critic >9/10.
6. Only after 2K passes, audit the complete Phase 2 interface navigation and both themes against the original plan and verify exact source/APK/AAB binding, all applicable regressions and independent critic >9/10. Keep Phases 3–9 and reserved territory commissioning open.

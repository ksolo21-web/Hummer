# Territory Card Studio — Android Port Plan

## Recommendation
Build a native Android app that shares the exact Territory Knowledge Base, standards, source hashes, identity grammar, overlap policy, and regression fixtures with the Python desktop app. Do not create a second independent set of territory rules.

## Execution source of truth
This file is the durable overall build plan for BOTH manual work and the hourly completion loop. Every work session must recover this plan plus `TerritoryCardStudio-Android-COMPLETION-LEDGER.json` and `TerritoryCardStudio-Android-CHECKPOINT.json`, then work only the first open dependency-valid milestone. The hourly automation is an execution mechanism, not a separate plan. Manual work may advance the same milestones at any time; after any state-changing manual run, update the checkpoint and completion ledger so the next hourly run resumes from the new durable state instead of repeating work.

Passed milestones stay frozen unless a touched dependency or an exact newly proven defect reopens them. Never weaken source truth, exact-approved preservation, junction-to-junction road colors, 300/301 rules, provider authority, canonical PDF semantics, or the >9.0 quality gate.

## Architecture
- Kotlin + Jetpack Compose UI.
- Shared immutable JSON/ZIP rules bundle generated from the same `embedded_refs` source used by the Python release.
- JTS Topology Suite for road/building topology, polygon intersection, duplicate-work checks, and label-clearance geometry.
- Android `PdfDocument` or a vetted PDF library for vector PDF output; preserve approved PDF bytes exactly when the exact approved artifact is selected.
- ML Kit Text Recognition or bundled Tesseract for local OCR of image-only source maps.
- OkHttp/Retrofit for Oakland County GIS, Census TIGERweb, OpenStreetMap/Overpass, and optional Google Geocoding verification.
- Android Storage Access Framework for map/PDF import and PDF/audit export.
- No API keys compiled into the APK. Google API key, if used, is entered/stored through Android secure preferences/keystore.

## Anti-drift rule
The Android app must consume the same versioned rules/data package as Python. Every rule that can affect output must have a cross-platform golden regression fixture. A Kotlin implementation cannot silently reinterpret a rule.

## Mandatory parity gates
1. Identity grammar: A, T, TA, lowercase split suffix.
2. 768 x 480.5 pt card geometry and canonical style tokens.
3. Junction-to-junction road-color roles only.
4. 300/301 apartment/condo footprint standard.
5. Mobile/manufactured-home site/number standard.
6. Label 3 pt target / 2–4 pt hard range, continuous curved baseline, exterior perimeter placement, zero object overlap.
7. Direct → curved → same-road relocation → attached-arrow callout → detail placement hierarchy.
8. Cross-territory duplicate-work audit.
9. Source-truth fail-closed behavior.
10. Required live verification for field-use release.
11. Exact-approved same-territory artifacts preserved byte-for-byte.
12. `needs_new_card` territories create candidates but cannot field-release until explicit approval.
13. Page 1 and Page 2 of a territory packet share one territory identity/version/assignment/source/approval/hash contract; Page 2 is never an unrelated attachment.
14. Letter Writing address inventories reconcile to the locked working area and neighboring territories with provenance, verification, deduplication, deterministic hashing, and fail-closed conflict handling.
15. Telephone Territory inventories support address + authorized/verified telephone data with provenance, verification status/date, deduplication, deterministic hashing, and fail-closed assignment/source conflicts. Never mass-scrape or infer private resident contact data.
16. Regular, Letter Writing, and Telephone packet modes must pass the same canonical PDF identity, approval, overflow, page-order, print-readiness, and regression gates.
17. Light and dark Android themes must preserve identical information hierarchy, territory status colors, map semantics, accessibility, and field-readiness behavior.

## Current forward-only phase plan

### Phase 1 — Renderer, packet, and parity foundation
- **1A `dedicated_detail`** — PASSED / frozen.
- **1B-1 `split_detail`** — PASSED / frozen.
- **1B-2 `full_plus_detail`** — PASSED / frozen.
- **1B-3 `site_building_assignment`** — current open gate at the time of this plan revision. Implement explicit site/building diagram region plus optional verified access inset, preserving 300/301 member/label bindings and preventing ordinary-residential footprint leakage.
- **1C multi-label single-footprint 300/301 preservation** — after 1B.

#### Phase 1D — Canonical two-page packet semantics
Phase 1D is mandatory and sequential. Do not skip directly to Phase 1E or Phase 2.

- **1D-A Core canonical two-page packet semantics**
  - Page 1 = territory map/card.
  - Page 2 = territory-specific working information.
  - Both pages share territory ID, version, assignment, source record, approval state, deterministic hashes, and canonical filename.
  - Export fails closed on mismatched identity/version/source/approval or invalid packet construction.

- **1D-B Letter Writing Page 2**
  - Implement a verified address inventory for Letter Writing territories.
  - Model territory number, street address, apartment/unit where applicable, city/state/ZIP where available, verification status, source/provenance, territory assignment, duplicate detection, deterministic inventory hash, and change tracking.
  - Reconcile every address to the locked working area and neighboring territories.
  - Fail closed on unresolved boundary conflicts, duplicate territory assignment, insufficient source evidence, or Page-2 overflow/render failure.
  - Generate a clean professional Page 2 matching the front territory-card standard and established apartment/building conventions.

- **1D-C Telephone Territory Page 2**
  - Implement a parallel Telephone Territory inventory and renderer with address + telephone number, verification status/date, source/provenance, territory assignment, duplicate detection, deterministic inventory hash, and change tracking.
  - Telephone data must come from records the user is authorized to use/provides or other permitted sources; do not mass-scrape or infer private resident contact data.
  - Support missing/unavailable-number status without falsely inventing a number.
  - Fail closed on ambiguous territory assignment, conflicting duplicate records, unverified ownership/source binding, or Page-2 rendering failure.
  - Generate a clean professional Page 2 matching the front territory-card standard.

- **1D-D Cross-mode packet validation**
  - Prove three canonical packet modes: Regular Territory, Letter Writing Territory, Telephone Territory.
  - Validate deterministic PDF generation, identity/version/hash linkage, duplicate/overlap checks, overflow handling, page order, print readiness, exact approval binding, and regression fixtures.
  - No packet mode may bypass canonical validation.

- **1E Final all-mode renderer parity**
  - Verify parity across normal residential, split/detail, site/building, 300/301, Letter Writing, and Telephone modes.
  - Earlier passed gates remain frozen unless a touched dependency or exact defect reopens them.

### Phase 2 — Production Android UI
Implement the planned native Kotlin + Jetpack Compose production interface only after Phase 1 passes.

- Support BOTH **Light** and **Dark** themes from the same design system.
- Core flow: **Territories Dashboard → Territory Workspace → Import Map → Verify → Build → Preview → Approve/Reject → Export PDF → Knowledge Base**.
- Normal territories: **Map | Streets | Buildings | Details** as applicable.
- Letter Writing territories: **Map | Addresses | Buildings | Details**.
- Telephone territories: **Map | Phone List | Buildings | Details**.
- Show verified counts, needs-review counts, duplicates/conflicts, source/provenance drill-down, and exact field-release state.
- Provide **Generate Page 2** for Letter Writing and Telephone territories.
- Preview the actual vector PDF packet rather than a visual approximation.
- Associate defects with the affected category/item.
- Bind approval to the exact candidate/version/hash; later changes invalidate stale approval.
- UI must call the proven Phase 1 engine; do not reimplement territory rules inside Compose.

### Phase 3 — Editing / commissioning workflow
Implement controlled territory editing/commissioning without bypassing source-truth or release gates.

### Phase 4 — Candidate lifecycle / approval
Implement candidate creation, explicit approval/rejection, exact-hash promotion, approval invalidation after change, and audit history.

### Phase 5 — Final PDF / output validation
Complete production packet validation, output integrity, print readiness, naming, export, and audit evidence.

### Phase 6 — End-to-end Android integration
Prove the complete Android workflow against the shared engine and durable data contracts.

### Phase 7 — Six real commissioning cases through the app only
Commission the six reserved `needs_new_card` territories only after prior gates are complete. Do not manually create/repair these cards outside the app.

### Phase 8 — Production release hardening / signing
Complete production signing, security, reliability, accessibility, performance, and release hardening.

### Phase 9 — Final release / install gate
Pass final install, field-use, regression, artifact, and release checks. The completion loop may be disabled only after Phase 9 genuinely passes.

## Six current cards needing replacement
- T250 / canonical filename `Territory - 250T.pdf`
- A257 / canonical filename `Territory - 257A.pdf`
- 297 / canonical filename `Territory - 297.pdf`
- A298 / canonical filename `Territory - 298A.pdf`
- 299 / canonical filename `Territory - 299.pdf`
- TA347 / canonical filename `Territory - 347TA.pdf`

Their legacy files are reference evidence only. Android and Python must show the same state and must not treat those legacy PDFs as approved field cards.

## Approval workflow
A generated card for a `needs_new_card` territory is saved as `CANDIDATE-UNAPPROVED` with its audit. After human review, an explicit Approve action promotes the exact candidate hash into the local current-reference overlay. Approval must never be inferred from successful validators alone.

## Production Android screens
- Territory Dashboard: Approved / Needs new card / Re-audit required / overlap-conflict / unassigned-screening states, search, filter, and field-readiness status.
- Territory Workspace: dominant map/card preview with locked assignment context and territory-specific tabs.
- Import Map: source intake tied to territory identity and authoritative evidence.
- Verification: source truth, live GIS, overlap, labels, buildings, colors, template, address/phone inventory checks where applicable.
- Build: deterministic generation through the shared engine.
- Preview: exact vector front/back PDF packet with zoom and defects associated to affected items/categories.
- Approval: explicit approve/reject with candidate version/hash and stale-approval invalidation.
- Export: canonical combined PDF and audit output, blocked when required gates are unresolved.
- Knowledge Base: territory status, current reference hash, source/provenance, permanent patches, conflicts, overlap warnings, and rule versions.
- Letter Writing Addresses tab: verified address inventory, review/conflict status, provenance, Page-2 generation.
- Telephone Phone List tab: address + authorized/verified phone inventory, missing/unavailable state, review/conflict status, provenance, Page-2 generation.

## Release strategy
Keep Python 1.x as the reference implementation while Android reaches parity. Android is field-use eligible only after it reproduces the golden regression corpus and all mandatory gates with no known divergence.

# Outlined-area map input — authorized 2026-09-28

User requirement: a black-outlined, shaded-area map picture must be another supported input for creating/updating a territory card to the existing standard.

Source: 26435.jpg; exact immutable source retained as source-26435.jpg. No real territory identity or field assignment is commissioned by this development task.

Accepted baseline: Phase6 e851c625, 32 core checks,11 native cases, critic9.5; checkpoint a70b0f4e8a93a4322ef06f9e654d863b39269cb4. Preserve colored-map interpretation and existing approvals except specific shared dependencies reopened by changes.

Dependencies and acceptance:
1. Detect one complete closed black polygon, preserve narrow stem/concavities; reject open/clipped/competing outlines. Actual JPG and negative fixtures required.
2. Separate neutral road extraction from basemap colors. Parks, schools, hospital fills and water must never become work-status evidence. OCR names need visible road geometry; uncertain or occluded roads remain explicit findings.
3. Store source-space polygon, extraction mode, source hash, candidate road relationships (interior/exterior/following/crossing), typed coverage decisions and omissions. Source/boundary/geometry/coverage edits invalidate preparation/approval.
4. Native source overlay for boundary confirmation and targeted per-road coverage review. Exterior context stays neutral; red requires explicit exclusion. Inside-only sides require source review. No generic checkbox may resolve missing geometry or ownership.
5. Complete synthetic-identity native import->proposal->boundary/coverage review->registration->fresh preparation->build->PDF preview->approval->SAF readback->KB journey, plus negative registration cases and affected colored-map regressions.
6. Independent critic inspects actual source/output, labels, work sides, coverage, context, template, final PDF sizes/hash receipts; all mandatory gates pass and score>9.0. Save tested build/evidence/checkpoint.

Current status: IN_PROGRESS, NOT ACCEPTED. Boundary detection, neutral road proposals, exact source-span coverage, native source overlay and persisted outlined review are implemented. At commit399512f, 55 core tests passed and Android compiled; actual-source OCR failed. Split/join controls and additional provenance/visibility tests are being validated. Missing or obscured road correction, practical perimeter correspondence and final actual-source export acceptance remain outstanding. No updated release APK is approved.

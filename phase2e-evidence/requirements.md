# Phase 2E — exact candidate PDF preview

Depends on passed Phase 2D-B 0.2.4, source 4e0eb218670f5565aca05416d297d0b9828b5ef8 and closeout ca91842780a2d291c4f2de59f09f46c5a7d73b44.

Acceptance: render exact generated front/two-page PDF bytes through Android PdfRenderer; bind requested identity/mode/kind/filename/SHA/page count to current coordinator state; reject missing, stale, altered or unsupported inputs; white PDF colors unchanged by UI theme; front/back navigation, zoom/pan/fit; remove stale frames and recheck on resume; close native resources/delete temporary copies; preserve frozen core, Page2 service and reserved six territories; pass prior 19 and six new runtime tests; eight real screenshots plus two synthetic PDF evidence files; version0.2.5/code30 freeze; independent score strictly above9 with all mandatory checks passed; save checkpoint and ledger.

Scope excludes real source-truth preparation/commissioning, approved-card preview import, field approval/export and overall release. Synthetic 998/999 inputs are androidTest only. PDF service references the current Build coordinator; it cannot preview arbitrary imported source PDFs. No layout recreation from geometry.

Official API references: https://developer.android.com/reference/android/graphics/pdf/PdfRenderer and https://developer.android.com/reference/android/graphics/pdf/PdfRenderer.Page .

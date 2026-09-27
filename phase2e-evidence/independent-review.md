# Independent review — Phase 2E exact candidate PDF preview

Result: **PASS — 10/10 against the bounded preview scope.**

Source commit: `22d9e12a88f4147268b3ca4903c4d5b5c574f47f`.
Evidence run: `36288560989`.

## Evidence inspected independently

- Reviewed preview service, Compose UI, additive coordinator resolution, Build links, integration and all six new tests.
- Core, Android compile, package and connected-test logs report successful builds.
- Parsed connected-test XML: exactly 25 cases, no failures, errors or skipped cases. Prior 19 tests and six preview tests pass.
- Dedicated preview capture instrumentation reports `OK (1 test)`.
- Verified frozen source archive SHA-256 against its manifest, all source hash entries against reviewed local files, and each corresponding source/test file inside the archive byte-for-byte.
- Reserved-territory PDF sweep is empty.
- Both evidence PDFs are below 300 KB. Letter packet is 86,457 bytes, SHA-256 `7d95b756b8c57d28165c47870aaad3821688691f6954aef2f85a599e0841a06c`; Telephone packet is 86,891 bytes, SHA-256 `1221439b836c045fea90749e45bbea7dc4ba6f36a59b6c2bd6f60bd451bc4160`. These match the hashes visibly displayed in their preview captures.

## Functional checks

Runtime tests establish pixel equality with direct Android PdfRenderer output from the exact stored PDFs, preserved front-page equality in front-only and combined packet forms, both inventory modes, and unchanged original PDF hashes. Missing artifacts, wrong identity/hash/mode/kind, invalid page or size, deleted/tampered files, replaced sources, mutated inventory and restarted unprepared coordinator are rejected. Repeated renders at multiple sizes stay bounded and leave no temporary snapshots. Renderer/page/descriptor cleanup is present in source. UI tests cover Build navigation, front-only navigation limits, next/previous pages, fit/zoom, pan, source recheck and pause/resume tamper rejection with the old frame removed.

## Eight actual screenshots inspected individually

- Dark front: complete white PDF, correct page 1/2, legible candidate warning, correct synthetic territory and frozen road colors.
- Dark zoomed front: actual enlarged and horizontally panned PDF content; fixed navigation remains available.
- Dark Letter Writing back: page 2/2, 24 address records and matching packet hash.
- Dark zoomed Letter Writing back: visibly enlarged readable records and preserved white PDF.
- Dark stale-input state: old PDF frame absent, navigation/zoom disabled, clear return-to-Build instruction.
- Light front: same white PDF treatment with correct Telephone identity and fixed road colors.
- Light Telephone back: page 2/2, addresses plus telephone numbers and explicit UNAVAILABLE rows.
- Light zoomed Telephone back: enlarged readable number/unavailable content, same packet hash and candidate-only warning.

All required captures show their intended state. No blank frame, overlapping controls, theme recoloring of the PDF, or stale-image display observed. Zoomed content is intentionally clipped to the pan viewport; fit views show the full page.

## Scope and closeout

Approval covers exact existing candidate PDF preview only. It does not approve any territory for field use, commission real territories, add export, provide approved-card import preview, or complete upstream preparation. Synthetic 998/999 inputs remain test-only. No mandatory defect or missing evidence remains for this increment. Persist the checkpoint/ledger and this review for closeout.

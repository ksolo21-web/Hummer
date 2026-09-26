# Territory Card Studio Android 0.1.19 - Phase 1C Validation

## Scope
Phase 1C only: preserve multiple authoritative 300/301 labels inside one physical building footprint without flattening, guessed placement, ambiguity, or ordinary-residential footprint leakage. Phase 1D and later phases were not started.

## Durable source state
- Branch: `tcs-phase1c-019-work`
- Freeze workflow run: `36259288791`
- Freeze workflow head: `8ccc247a376c5223c783581537d76d05ac367395`
- Evidence artifact ID: `10911942234`
- Evidence artifact ZIP SHA-256: `cbc5689e35bbaa8017f5bbda63d5ddcaa9d245304abded29b8d3e33290cf7080`
- Reconstructed Phase 1B-3 overlay SHA-256 remained `85bf31b214098df041b43f1aad65f947c59876fbace92e256bb1e26d73946946`.

## Phase 1C implementation
- Candidate renderer now carries verified building label-item text, center, exact text origin, rotation, and font size into the PDF render model.
- One physical footprint may preserve multiple verified labels; the synthetic regression renders both `300` and `301` inside one footprint.
- Multi-label footprints require explicit verified text origins. Missing origins, missing member labels, duplicate labels, escaped labels, member/label inventory drift, or ambiguous binding fail closed.
- Production adapter preserves `BuildingGeometry.sourceMembers` and independently positioned `BuildingLabelItem` records rather than flattening them into a single display string.
- Ordinary residential individual-footprint leakage remains blocked.
- Split/full-plus building detail paths now recheck member/label bindings when those building labels become render-visible.

## Source hashes
- Transform: `7844aac05b3b91cd4bde8b812d2e75af89c68bcb3dfc8258cb91b706c568bcf1`
- Source patch: `1406287c353a818ec24eb4becbdd7abe9ee345315518466daa0a393561621073`
- CandidatePdfRenderer.kt: `cf3dd6d4f9be48c9afda23ec4d428715e48f26b276454bfae235e0bb9a9bc9bc`
- ProductionRenderModelAdapter.kt: `67050950ae6bfcc9673a02b675a202bb7fdab1f5948958026fcfa95fc1068d0d`
- CandidatePdfRendererTest.kt: `b911527953a2f4e5e450e898d768a77f8b9cd03f1b2fcd2fb5e6c731a5a429bd`
- ProductionRenderModelAdapterTest.kt: `063a2fb94f1d96b9611d41c96e9c369e654c5d451b29aae3840c1c9151fc1351`
- Frozen source package: `47d25b73b60bf4bcce8d6282690c18c62af5c466ba378f863d3ffcb19a7e6698`
- Extracted source manifest: `0551591262e26c9d0c55e163f3a3bfa665a2bb3e446dac1cb0a7794f7d4fbe20`

## Synthetic NOT-FOR-FIELD-USE regression
- Card: `A994a`
- PDF: `Territory - 994Aa - NOT FOR FIELD USE.pdf`
- Exact PDF SHA-256: `6714060b793c5a39a96fdea11ef28c864f53e9579b0e89c922440d3679fad9a0`
- PDF size: 82,439 bytes
- One physical footprint contains both verified labels `300` and `301`.
- 1x Poppler evidence Git blob: `1932e5e87c97224bf5cb975cb6e7acf539d382e5`
- 4x Poppler evidence Git blob: `74cf8132b3583176b430581678fc09f5359183ee`
- 1x MuPDF evidence Git blob: `5da4ad31c69c8f5212d5f657e2bfb7d57828a85d`
- Poppler/MuPDF changed-channel fraction: `0.23882175`; direct visual inspection found no semantic layout, label, color, footprint, or inset drift.

## Validators and visual review
- R48 style/token validator: PASS.
- Canonical PDF identity: PASS for `A994a` / `Territory - 994Aa.pdf`.
- R51 label-metrics gate: PASS; 3 street labels and one intentional repeated Access Rd navigation group.
- R52 bounded label-navigation gate: PASS. This is bounded regression evidence, not whole-card release certification.
- Visual hard-override gate: PASS.
- Actual 1x model visual inspection: PASS.
- Actual 4x model visual inspection: PASS.
- MuPDF/Poppler semantic parity inspection: PASS with rasterization differences.
- No clipped or overlapping 300/301 labels; both remain independently legible inside the single footprint.
- No reserved commissioning territory was rendered or modified.

## Android freeze
- `:core:test`: PASS.
- `:app:compileDebugKotlin`: PASS.
- `:app:assembleDebug`: PASS.
- `:app:bundleDebug`: PASS.
- APK v2 signature: PASS.
- AAB JAR verification: PASS.
- APK/AAB ZIP integrity: PASS.
- versionName/versionCode: `0.1.19 / 19`.
- targetSdk: `37`.
- APK SHA-256: `319950f68faa14e6d8206a8b4957fc0e4bf5c4fea9bfa7ff63137528ec491b7f`.
- AAB SHA-256: `e7e72bb2815c0adbe284879808611d5948f952cc632f276783682cefb0ad551d`.
- Reserved commissioning PDF sweep: `0`.

## Blocker history
Earlier Phase 1C attempts were not treated as passes. The last substantive failed route reached strict compilation but used an incomplete synthetic building/site inventory against locked A264 authority and failed closed. The successful route bound the synthetic adapter regression to A264's exact locked member inventory and reran the complete gate.

## Review
Internal review only; no separately callable independent critic runtime was available. Phase 1C score: **10.0/10 - PASS**. All mandatory Phase 1C source, fail-closed, renderer, validator, visual, Android-build, artifact-integrity, and reserved-territory gates passed. Overall application approval remains false because Phase 1D and later phases remain open.

## Next open gate
Phase 1D only: canonical front/back PDF semantics.

# Territory Card Studio Android 0.1.24 — Phase 1E Validation

## Scope
Phase 1E only: final all-mode renderer and packet parity across the frozen Phase 1 corpus. No previously approved visual fixture was regenerated for subjective review because all relevant production/rendering source hashes remained frozen and every golden artifact hash reproduced exactly.

## Final parity surfaces
1. Normal residential
2. Curved/callout label behavior
3. Dedicated detail
4. Split/detail
5. Full-plus detail
6. Site/building assignment + exact multi-label 300/301
7. Letter Writing canonical packet
8. Telephone canonical packet

## Frozen renderer golden hashes
- Normal residential: `5474c57cdba333802a1aedfc577b7e156413346e7f107a6afd321b0a3b505e33`
- Curved/callout: `ddbce80bcf9f6d4378150a9ef16ae6a885d4dda2c7227de369dac2548af26b4e`
- Dedicated detail: `ce741fa6142a16e8df27a927da6b5eb988cad30935887ab64689c6da31fc35fb`
- Split/detail: `0bb14819899794da7ff546c8805c27fcbf2966af85b2363dc9d4be95dd469175`
- Full-plus detail: `d83dd01f11f5096f9b987417a86187061409bd5598fc73278db8a14bc54eb133`
- Site/building + 300/301: `6714060b793c5a39a96fdea11ef28c864f53e9579b0e89c922440d3679fad9a0`
- Letter Writing: `c8678a6f66b1479a0808b07c0dab16383edbd8ba4ee8cdc7c417996101842232`
- Telephone: `01f704feb3e8253d443ceb3d373993b5dc57d41a7464a286e66e86fdacc9350e`

## Frozen source authority
The final parity run proved exact source hashes before executing the corpus:
- CandidatePdfRenderer.kt: `cf3dd6d4f9be48c9afda23ec4d428715e48f26b276454bfae235e0bb9a9bc9bc`
- ProductionRenderModelAdapter.kt: `67050950ae6bfcc9673a02b675a202bb7fdab1f5948958026fcfa95fc1068d0d`
- CanonicalFrontBackPdf.kt: `9d926f5ea84621e3d12900c3f7833d01f69875410106d21eac5ecc868299c708`
- LetterWritingAddressInventory.kt: `c084581bb87ce9a7c9dbfa4a160946aab72c416304f443fa0164fbfccf7a2a64`
- TelephoneTerritoryInventory.kt: `59d14ff4cf9a1fe9bf15348d1808fe2480c931c1ba67e6a374fea0ad5b7f5fa7`
- CrossModePacketValidation.kt: `b594629cf9d6afb17a3205814ae65ffd4f81c028096cd22bd8fe59594e64ee69`
- FinalAllModeParityRegression.kt: `a4133369a06e8ccb1f5ecc0603eaf2fd3a7bef468cf09e251b093840ee6df9f5`

## Parity results
- Deterministic renderer PDF hashes: PASS
- Deterministic render-spec hashes: PASS
- Identity + canonical filename semantics: PASS
- Normal residential rules: PASS
- Label gap/callout semantics: PASS
- Dedicated detail rules: PASS
- Split/detail rules: PASS
- Full-plus explicit mapping rules: PASS
- Site/building assignment rules: PASS
- 300/301 multi-label single-footprint rules: PASS
- Letter Writing packet hash/page-role parity: PASS
- Telephone packet hash/page-role parity: PASS
- Frozen cross-mode approval/overflow contract: PASS
- Prior visual evidence reuse: PASS
- Reserved commissioning territory PDF sweep: 0

## Android 0.1.24 freeze
Workflow run: `36271293182`
Workflow head: `4659c1d728b4ce6d54d7a62b8b05b1276daa2611`
Evidence artifact ID: `10915422787`
Evidence artifact ZIP SHA-256: `174cb02daaa5449c133fd4e86bc584e5549203a07d1ebaf0859b2ebfa843f85f`

- versionName/versionCode: `0.1.24 / 24`
- targetSdk: `37`
- APK SHA-256: `b757ca287f9d1b16e39b3810be9e6910c8644a3a0bbb5c7da3c3b02c0d89f789`
- AAB SHA-256: `313763b1332bc5e352a6d7c219ac4161b38f20a7d82223d0180838b49282e3d0`
- Source package SHA-256: `227c0b136919be72a89f8af5e06fd291a0c5f65341fc444e969009981b5e26b7`
- Extracted source manifest SHA-256: `682372ff9eafef6c3747363d37a4d01d2458a866d95fe3ee1b20ce9c713f304a`
- Final parity log SHA-256: `46632927a900b7c1c2326d554dfb59f4bdac83a50d6b7303bc797cb4c0a36bd7`
- Core Gradle tests: PASS
- App compile: PASS
- APK assemble: PASS
- AAB bundle: PASS
- APK v2 signature: PASS
- AAB JAR verification: PASS
- APK/AAB ZIP integrity: PASS

## Visual handling
No new subjective visual re-review was repeated in Phase 1E because renderer/adapter source hashes stayed frozen and every prior golden PDF hash reproduced exactly. Existing 1x/4x and Poppler/MuPDF evidence from the passed milestones remains authoritative.

## Review
Internal review only; no separately callable independent critic runtime was available. **Phase 1E score: 10.0/10 — PASS.**

## Result
Phase 1A through 1E are passed. Renderer, canonical packet semantics, Letter Writing Page 2, Telephone Page 2, cross-mode packet validation, and final all-mode parity are frozen.

## Next open gate
**Phase 2 — Production Android UI** using the proven Phase 1 engine. Light/Dark/System themes, territory dashboard/workspace, mode-specific tabs, vector-PDF preview, verification/provenance/conflict state, Generate Page 2, exact-version approval/export surfaces, and no duplication of territory rules inside Compose.

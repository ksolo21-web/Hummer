# Territory Card Studio Android 0.1.23 — Phase 1D-D Validation

## Scope
Phase 1D-D only: cross-mode packet validation for Regular, Letter Writing, and Telephone modes. This milestone adds a shared release-contract validator and does not change the already-frozen card geometry or Page-2 visual design.

## Canonical packet modes proved
- Regular Territory: one-page `FRONT_MAP` artifact.
- Letter Writing Territory: `FRONT_MAP -> BACK_ADDRESS_LIST`.
- Telephone Territory: `FRONT_MAP -> BACK_PHONE_LIST`.

All three modes are evaluated by one common packet contract for identity, candidate version, Knowledge Base revision, assignment authority, exact front/packet hashes, page roles, print readiness, and explicit approval binding.

## Deterministic regression hashes
- Regular PDF: `d83dd01f11f5096f9b987417a86187061409bd5598fc73278db8a14bc54eb133`
- Letter Writing PDF: `c8678a6f66b1479a0808b07c0dab16383edbd8ba4ee8cdc7c417996101842232`
- Telephone PDF: `01f704feb3e8253d443ceb3d373993b5dc57d41a7464a286e66e86fdacc9350e`

Manifest SHA-256 values:
- Regular: `5251faf6bec5d73123fa1f28136758d1a40beee6da32b2edd8e1d43223389e11`
- Letter Writing: `315d984291ef1868351de40848d3cf73d486808d699032425b8131ba8a170103`
- Telephone: `991277a1e4a0c29f6f596e38b6f2fdaede199891b67b4a0e11897c190a96b8e9`

## Common fail-closed gates
The cross-mode regression passed 12 targeted mutations:
- 5 exact-approval mutations: stale candidate version, wrong exact PDF hash, wrong manifest hash, invalidated approval, and cross-mode approval reuse.
- 3 duplicate/overlap mutations: regular topology-overlap failure, Letter Writing duplicate address, Telephone duplicate verified number/address path.
- 2 Page-2 overflow mutations: Letter Writing and Telephone.
- 2 print/version mutations: invalid regular page geometry and missing candidate version.

A passing validator never infers approval. Release requires a separately supplied `EXPLICITLY_APPROVED` receipt bound to the exact packet manifest, mode, territory identity, canonical filename, candidate version, and PDF hash.

## Core evidence
Core cross-mode workflow:
- Run: `36268920427`
- Head: `ceabe617cfe49bcb4bd268ce18ad4483829611ad`
- Artifact ID: `10914539021`
- Artifact ZIP SHA-256: `a4201bbda276565f1e94b51bc41cc93d501e0769fc20d04554803430f7668602`
- Core test log SHA-256: `086e1fa2c0dae351eb6281d6bc973447725e0de34854e7c12eb05a9fbb697757`
- Cross-mode regression log SHA-256: `0b35224af4c285e4716271a7edc606b26537910326e39ad23538c59decb5af35`

Source hashes:
- CrossModePacketValidation.kt: `b594629cf9d6afb17a3205814ae65ffd4f81c028096cd22bd8fe59594e64ee69`
- CrossModePacketValidationRegression.kt: `2715fa7b7799844b77e701c64cf2a69ccdeb36366dcadf1c9f0a1de49079d361`
- CanonicalFrontBackPdf.kt: `9d926f5ea84621e3d12900c3f7833d01f69875410106d21eac5ecc868299c708`
- LetterWritingAddressInventory.kt: `c084581bb87ce9a7c9dbfa4a160946aab72c416304f443fa0164fbfccf7a2a64`
- TelephoneTerritoryInventory.kt: `59d14ff4cf9a1fe9bf15348d1808fe2480c931c1ba67e6a374fea0ad5b7f5fa7`

## Print-readiness route correction
The first external print-preflight route stopped after the core PASS because it treated every non-embedded font as a failure. Inspection showed the same font table in all three frozen PDFs:
- standard PDF Base-14 `Helvetica`: Type 1, intentionally non-embedded;
- DejaVuSans, DejaVuSansCondensed-Bold, DejaVuSans-Bold: embedded and subset.

No PDF, geometry, hash, page-order, or rendering defect was found. The reviewer route was corrected to permit only recognized PDF Base-14 fonts to be non-embedded while still requiring all custom fonts to be embedded.

Corrected print-readiness result for all modes:
- canonical 768 × 480.5 pt page geometry: PASS;
- expected page counts: PASS;
- encryption disabled: PASS;
- custom fonts embedded: PASS;
- Base-14 Helvetica allowance: PASS.

## Android 0.1.23 closeout
Targeted closeout workflow:
- Run: `36269250879`
- Workflow head: `28a968ea333e4c70648d5f884ac9530e8a6182fd`
- Artifact ID: `10915132196`
- Artifact ZIP SHA-256: `b0b5a06c2f9a5f5d9df13fcfd2e22bca897f04f6dab4cbc3d58083378ce18e82`

The evidence commit inside CI was rejected only because the branch advanced during the run. The uploaded artifact was recovered and its compact evidence was then persisted against the newest branch head without rerunning the already-green build.

Android/package results:
- versionName/versionCode: `0.1.23 / 23`
- targetSdk: `37`
- APK SHA-256: `74f1cb6f8b4b850ca03d71da9cea7e72b9c36ab11edcf25617f0ee896bfb8ccd`
- AAB SHA-256: `70bdd4f528ab04a0a7c4cfcfa0c6683945c831e3e22cc0bffea5143972cf33ca`
- Source package SHA-256: `4fffebf6354fe97b14e117c3fb5fdaba18322241ba31b200697181481e5aa619`
- Extracted source manifest SHA-256: `43b421701ec79051ccfd48458e5bfddfb5de1440f3674c88a11224c842cbbc03`
- app compile log SHA-256: `36c9ef6bbd6dccbb0fc7b2450966b3609936a3b14e886e2347bade23f1f99ce7`
- assemble log SHA-256: `1ffabb0f0ecebb167ff0714ebcfb61b20dc50bfce446b1d34a6306fecccff1c4`
- bundle log SHA-256: `1f22704a7ac72f0a473fadcbd341c6e1f3620099dbd91e71c4a59e3b83cb5d2b`
- APK v2 signature: PASS
- AAB JAR verification: PASS
- APK/AAB ZIP integrity: PASS
- Reserved commissioning territory PDF sweep: `0`

## Review
Internal review only; no separately callable independent critic runtime was available. **Phase 1D-D score: 10.0/10 — PASS.**

No visual re-review was repeated because 1D-D changed validation only; the exact frozen Regular, Letter Writing, and Telephone PDFs were hash-locked and already had their milestone visual reviews.

## Result
Phase 1D-A, 1D-B, 1D-C, and 1D-D are all passed. Aggregate Phase 1D is complete.

## Next open gate
**Phase 1E — Final all-mode renderer parity** across normal residential, split/detail, site/building, 300/301, Letter Writing, and Telephone modes. Earlier passed gates remain frozen unless a touched dependency or exact defect reopens them.

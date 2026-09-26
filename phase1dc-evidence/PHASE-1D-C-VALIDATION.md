# Territory Card Studio Android 0.1.22 — Phase 1D-C Validation

## Scope
Phase 1D-C only: Telephone Territory address + authorized/verified phone inventory + canonical Page 2. The implementation extends the Phase 1D-A canonical two-page packet with a BACK_PHONE_LIST presentation mode while preserving the already-passed Letter Writing output byte-for-byte.

## What passed
- Telephone inventory contract with territory identity, assignment authority, Knowledge Base revision, locked source-truth state, boundary evidence hash, neighbor-territory conflict handling, address verification state/date, phone verification state/date, address↔phone binding, provenance, deterministic hashing, and change tracking.
- Explicit phone states: VERIFIED_NUMBER and UNAVAILABLE.
- UNAVAILABLE never invents a number.
- Telephone Page 2 is blocked when a record is unverified, ambiguous, conflicted, unbound to its address, missing a required verification timestamp, has invalid boundary evidence, or lacks authorized/permitted source binding.
- Duplicate Telephone addresses fail closed.
- Duplicate verified Telephone numbers fail closed.
- Deterministic Telephone inventory hashing and change tracking passed.
- Telephone Page 2 uses the same canonical two-page assembler as Letter Writing and introduces BACK_PHONE_LIST without modifying frozen 1D-B output.
- Frozen Letter Writing byte regression passed against SHA-256 `c8678a6f66b1479a0808b07c0dab16383edbd8ba4ee8cdc7c417996101842232`.
- Telephone Page 2 deterministic repeated generation passed.
- Page order: FRONT_MAP → BACK_PHONE_LIST.
- Exact front bytes remain the combined PDF prefix.
- Actual Poppler 1x/4x and MuPDF 1x Telephone Page 2 visual review passed.
- Android 0.1.22 core, compile, APK/AAB, signature, and ZIP-integrity gates passed.
- Reserved commissioning territory PDF sweep remained 0.

## Telephone regression
- Fail-closed mutations: 19
- Verified phone count in fixture: 12
- Explicit unavailable count in fixture: 3
- Inventory SHA-256: `fcd07fcf4ec9c67fe411781e72a043221001059929576cfc89776e41d03331b7`
- Telephone regression log SHA-256: `e83cf7ce399ebd2ebf584e3e1ae23802d7c5057679ba4be8fae47a955e28a53d`
- Core test log SHA-256: `7a378f211651fa1a05c0160d7647264a881d066551a4b880bd8f098a63c6ef16`
- Extracted source manifest SHA-256: `7b24fbd707b01144de1b703924360edc0895e95bc7affb2c43b291ac5dfb64b6`

## PDF / Android freeze
- Combined Telephone PDF SHA-256: `01f704feb3e8253d443ceb3d373993b5dc57d41a7464a286e66e86fdacc9350e`
- Exact front PDF SHA-256: `5978c93dd04ddd9cb138e01aa75ae3854d400a488e0dbab789317ab968a190a4`
- APK SHA-256: `e42e7b099bc06875a8027ca594db36271373880ea0743ce6a9410a9409f62f92`
- AAB SHA-256: `61b91ff5a0125b6ba1a67a6d0f561e18054e62a8767c4edcaf2b5852ba6a33f2`
- Source package SHA-256: `3c641de8ee90b863a9f1b93118f4f3d5fbe9e0c3416b59d6a18345bf37b2e7e6`
- versionName/versionCode: `0.1.22 / 22`
- targetSdk: 37

## Source hashes
- CanonicalFrontBackPdf.kt: `9d926f5ea84621e3d12900c3f7833d01f69875410106d21eac5ecc868299c708`
- TelephoneTerritoryInventory.kt: `59d14ff4cf9a1fe9bf15348d1808fe2480c931c1ba67e6a374fea0ad5b7f5fa7`
- TelephoneTerritoryInventoryRegression.kt: `780399cb17ad2f6cc967587845148170c4abbaf33713eb7600a897cdbf68647f`
- TelephonePage2FixtureGenerator.kt: `fdd5f1927e31e76d32d37958b0c8381c76a5a52e32eef58903f3bd85b7d1dc2b`
- Transform script SHA-256: `e39de2ade626224deb5ccbda6eaba338f02d96522afa17fd2858069c426686a8`

## CI freeze
- Workflow run: `36267860690`
- Workflow head: `a13885eed0c1a92887b94f1baa2c87c18fe2ba2e`
- Evidence commit: `d33cca68f0a8ead847fba98bc8e5e805519c8a90`
- Artifact ID: `10915110152`
- Artifact ZIP SHA-256: `a96bdd2b3d3af25a46bac523e64125f4bc0b29e4ae218dd4bd712be385efd21b`

## Visual review
Internal review of the actual saved Telephone Page 2:
- T996a identity clear and correctly formatted.
- PHONE LIST sidebar label clear.
- ADDRESS + PHONE heading clear.
- Verified numbers and UNAVAILABLE rows legible at 1x.
- No clipping or overlapping rows.
- 4x close-up confirms consistent spacing and alignment.
- MuPDF and Poppler agree semantically.

## Review
Internal review only; no separately callable independent critic runtime was available. **Phase 1D-C score: 10.0/10 — PASS.**

## Next open gate
**1D-D — Cross-mode packet validation** across Regular, Letter Writing, and Telephone modes: deterministic generation, identity/version/hash linkage, overlap/duplicate checks, overflow handling, page order, print readiness, exact approval binding, and regression parity.

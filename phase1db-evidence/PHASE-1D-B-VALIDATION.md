# Territory Card Studio Android 0.1.21 — Phase 1D-B Validation

## Scope
Phase 1D-B only: Letter Writing verified address inventory + canonical Page 2. The implementation adds a fail-closed inventory contract in front of the already-passed Phase 1D-A two-page packet assembler and wires the Android service to that same path.

## What passed
- Deterministic Letter Writing inventory hashing.
- Territory identity binding.
- Assignment-authority hash binding.
- Knowledge Base revision binding.
- Locked source-truth reconciliation required.
- Per-record verification status + verification timestamp required.
- Per-record locked-working-area confirmation required.
- Per-record deterministic boundary/neighbor evidence SHA-256 required.
- Neighbor-territory conflicts fail closed.
- Provenance records carry source label/type/timestamp/source hash and field-use eligibility.
- Missing or non-field-use provenance fails closed.
- Duplicate record IDs fail closed.
- Duplicate normalized mailing addresses fail closed.
- City/state/ZIP fields are carried when available and validated.
- Change tracking supports ADDED / REMOVED / CHANGED with previous-inventory SHA binding.
- Page 2 generation uses the verified inventory hash as the canonical sourceInventorySha256.
- Letter Writing Page 2 is generated through the same canonical front/back assembler and Android storage boundary as 1D-A.
- Output remains two pages: FRONT_MAP then BACK_ADDRESS_LIST.
- Exact front bytes remain preserved as the combined PDF prefix.
- Byte-deterministic repeated generation passed.
- Poppler 1x/4x and MuPDF 1x visual evidence passed.
- Android 0.1.21 core test, app compile, APK, AAB, signatures, and ZIP integrity passed.
- Reserved commissioning territory PDF sweep remained 0.

## Regression evidence
- Core fail-closed mutations: 11
- Change tracking: PASS
- Fixture verified records: 18
- Fixture inventory SHA-256: `4d03576de7c7065ff57d3a5e7ad49046922de96d1cf12911ed47d13b623152d6`
- Combined Letter Writing PDF SHA-256: `c8678a6f66b1479a0808b07c0dab16383edbd8ba4ee8cdc7c417996101842232`
- Exact front PDF SHA-256: `d83dd01f11f5096f9b987417a86187061409bd5598fc73278db8a14bc54eb133`
- APK SHA-256: `5c5724bb1fb0e81244d321f316f30e0da1023c54dbadcb2d83a99711f67d763d`
- AAB SHA-256: `eefda0cdc139b477037c857b48f142e0861a8652aa205e44e520a1b4b9175bf0`
- Source package SHA-256: `ed880186f3903edd00c89a3faf3375b9c8356f422cb23c9a65577dce2630cce9`
- Core test log SHA-256: `56e20d32cf3240e7adf6ae22f8fd6ea10df3027bb71ef77325f0c6a1a9912f83`
- Extracted source manifest SHA-256: `5aa1f1e44fad50fbe641080073653af5b1c2314ac91108afa103e1fa19a99395`

## Source hashes
- LetterWritingAddressInventory.kt: `c084581bb87ce9a7c9dbfa4a160946aab72c416304f443fa0164fbfccf7a2a64`
- LetterWritingAddressInventoryRegression.kt: `1f5e61ab5ca2ebc2d02486cf68e6dc61a267c822deea10c50085851405797b18`
- LetterWritingPage2FixtureGenerator.kt: `02525e4d7f940029ac9b9793ceed1c74e3721fce0f2938e323f326af061f0b6d`
- AndroidPdfArtifactService.kt: `2bbb55fd85bb39ca072b1de5c80bfb7151d4899f69ae54fefb99dcf7cca5f4cd`
- CoreContractSmokeTest.kt: `314b3a1173c65bd9fd5090293256d8fa582a0670405d5932a16b6328d5ab1cf8`

## CI freeze
- Workflow run: `36265465111`
- Workflow head: `a8aa8f0b695f9e91893e3297395eddd3b8f29e46`
- Evidence commit: `0e17f727ed8296052f68621a38facbdc288b428f`
- Artifact ID: `10913536925`
- Artifact ZIP SHA-256: `89bcb718ca1510636ed5454037b66230b6d95114507408f36384520deb4b02f2`
- versionName/versionCode: `0.1.21 / 21`
- targetSdk: 37

## Visual review
Internal review of the actual saved Page 2 at 1x and 4x:
- address rows remain readable and aligned;
- no clipped or overlapping text;
- sidebar identity and updated date remain clear;
- canonical Page 2 shell is consistent with the front-card family;
- synthetic NOT FOR FIELD USE marker is clear;
- MuPDF and Poppler agree semantically.

## Review
Internal review only; no separately callable independent critic runtime was available. **Phase 1D-B score: 10.0/10 — PASS.**

## Next open gate
**1D-C — Telephone Territory Page 2**: address + authorized/verified phone inventory, missing/unavailable state, verification date, source/provenance, assignment binding, deduplication, deterministic hashing, change tracking, and fail-closed rendering.

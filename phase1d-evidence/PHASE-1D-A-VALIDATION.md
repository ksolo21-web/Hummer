# Territory Card Studio Android 0.1.20 — Phase 1D-A Closeout

## Scope
Phase 1D-A only: canonical two-page packet foundation. Page 1 is the exact validated territory map/card; Page 2 is territory-specific working information. This closeout adds the missing runtime proof that a back-page address inventory which exceeds the one-page capacity fails closed rather than shrinking below the locked legibility floor or silently creating another page.

## Existing 0.1.20 freeze evidence preserved
- Canonical combined PDF: `140170231e7bcff2e799725758b8f8d9a3a5949de0d0eaa862039dd06518ce3a`
- Exact front PDF: `d83dd01f11f5096f9b987417a86187061409bd5598fc73278db8a14bc54eb133`
- Page count: 2
- Page order: `FRONT_MAP, BACK_ADDRESS_LIST`
- Exact front-byte prefix preservation: PASS
- Canonical filename/identity binding: PASS
- Address source-inventory hash binding: PASS
- Duplicate-address rejection: PASS
- Unverified production-inventory rejection: PASS
- Front R48 style/identity validation: PASS
- Front/back 1x and 4x visual review: PASS
- MuPDF/Poppler semantic parity: PASS
- APK SHA-256: `05b8da05c265d569dcb11c581bfce1533182694a2f1e6a81ac1c7792b0db079d`
- AAB SHA-256: `f2936dc0877568624f97ec27271bdca99e57c02ef9bbf3a4fadb903f2919a58c`
- Source package SHA-256: `449b2cf69f077956320dd3eb62a7ba049bf6c80a0a7b501b0dbd5dab686d6e4d`
- versionName/versionCode: `0.1.20 / 20`
- targetSdk: 37
- Reserved commissioning territory PDF sweep: 0

## Targeted overflow runtime proof
Workflow run: `36264215014`
Workflow head: `13556c3354dff8aa47309453d33b7e51b9479cde`
Evidence commit: `7f5bf746632a47f13cd0f7afa0a848fc087d2c7e`
Artifact ID: `10913805330`
Artifact ZIP SHA-256: `c1342c9cd9155e6019815ead771c60b7ce048b018501f8237ba2049d30246bfb`

The exact reconstructed Phase 1C + Phase 1D 0.1.20 source was compiled, then `CanonicalFrontBackOverflowRegressionKt` executed under `-Xmx192m`.

Runtime result:
- `phase1d_back_overflow_fail_closed=PASS`
- `phase1d_back_overflow_entry_count=241`

Evidence hashes:
- Overflow regression source SHA-256: `f64c3e1c8b43eba231002c0a281c1a6986ee3e3a7b1e7aea72098b558d193134`
- Runtime log SHA-256: `cc1b9c2dd45c77fd4b4b154ea2e4e024a2235288d03efb2d62527fec16ea857a`
- Compile-test log SHA-256: `273cdbcaa78c16d46d9db7de8a1b390c855ac5873084ce744b98f6af7706940e`

The 241-entry overflow therefore fails closed at runtime. No third page is silently emitted and no capacity rule is bypassed.

## Updated overall plan reconciliation
The persistent `ANDROID_PORT_PLAN.md` now decomposes Phase 1D into 1D-A through 1D-D. Therefore this closeout marks only **1D-A PASSED**. Aggregate Phase 1D remains in progress.

Next open gate:
**1D-B — Letter Writing: verified address inventory + Page 2.**

1D-C Telephone and 1D-D cross-mode validation remain dependency-locked. Phase 1E remains dependency-locked behind all of Phase 1D.

## Review
Internal review only; no separately callable independent critic runtime was available. **Phase 1D-A score: 10.0/10 — PASS.** Every mandatory 1D-A packet-foundation, ordering, identity/hash binding, overflow, visual, build, integrity, and reserved-territory gate is now evidenced.

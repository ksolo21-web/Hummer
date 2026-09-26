# Territory Card Studio Android 0.2.0 — Phase 2A Validation

## Scope
Phase 2A only: production Android UI foundation using the frozen Phase 1 engine. This milestone adds the persistent System/Light/Dark appearance preference, production Territories Dashboard, Territory Workspace shell, phone bottom navigation, wide-screen navigation rail, and real Knowledge Base-driven territory status presentation. It does not implement the later mode-specific tabs, vector-PDF preview, verification workflow, approval/export, or editing/commissioning screens.

## Source and architecture
- The UI consumes the proven Phase 1 Knowledge Base and service layer.
- Compose does not reimplement assignment, road-color, topology, building, label, packet, or approval rules.
- Frozen Phase 1 renderer/adapter/packet hashes stayed unchanged.
- Real Knowledge Base assignment count in instrumentation: **225**.
- The six reserved needs-new-card territories remain represented as needs-new-card UI state and remain unapproved/uncommissioned.

## Runtime / instrumentation
Full build and instrumentation run: `36273476116`
- Core Gradle regression: PASS
- App compile: PASS
- Android-test compile: PASS
- Connected Android instrumentation: PASS
- Dashboard uses real Knowledge Base: PASS
- Dashboard → Workspace navigation: PASS
- Persistent System/Light/Dark preference: PASS
- Android 0.2.0 package build: PASS
- APK v2 signature: PASS
- AAB JAR verification: PASS
- APK/AAB ZIP integrity: PASS

Frozen build hashes:
- APK: `a73df29753b07853f3adb285a43cb89daa74a4bb317f2aba4ee5f8aa84fa1831`
- AAB: `3420fd346ace81fa306ed2773a3b1328ee2e34298a5e06d1e4450eccf90832a4`
- Core-test log: `b506654be74b361193623da9b33daa9d5d77c47499e5c7f1261d423401d30a7f`
- App-compile log: `5da4aff8575576c1ffce78ac8bf74e48de88d88a0168f8060d9a790f8d1a7f7d`
- Package-build log: `0c1a651a55cadc149950f0e0692232b4465b8f43d335bb1047a658ae2b59aa50`
- Connected Android-test log: `5ea1fdb537430f0a384aa5ae4a63ea2a1cd6acd64b8bfff582235bc57f99a859`

## Final screenshot closeout
Targeted screenshot closeout run: `36274735121`
Artifact ID: `10917560347`
Artifact ZIP SHA-256: `42a18b3509c978092c87fe47aa8e7c9ebad7fcaab1aa1e85bcb8d3e97e35b39a`

The capture waited for the real Compose semantic hierarchy before each screenshot:
- Light phone: 1080×2400, SHA-256 `641b120b3538b33230eded83cb46d472d08be9ce89cbbad14a66c64249aa8c73`, mean RGB 233.88
- Dark phone: 1080×2400, SHA-256 `e57b39723cb95d25eebbcafbcf6af5189dfe2ba675fa002e8fa1f7005eabc34d`, mean RGB 38.69
- Wide dark: 1600×2560, SHA-256 `8f7bba35fdf5dec7531f0a1be1f1a6238322ef5371f2e3bd82a8aaf15427d9c6`, mean RGB 30.00

Runtime checks:
- Light and dark screenshot hashes are distinct.
- Dark phone and wide-dark luminance are substantially below light mode.
- System theme follows Android UI mode.
- Phone layout uses bottom navigation.
- Wide layout uses a navigation rail and preserves the same information hierarchy.
- Status meanings remain visually distinct in both themes.

## Source freeze
- Source package SHA-256: `9edfcf3cbd96c0e05062a7e41aa1d45871c75e699aed529f699627f18dcdd4d7`
- Extracted source manifest SHA-256: `bdd11a9b0a738f0b1ae309189a6322e72c2f2120aef5f723d30db4297fac3099`
- versionName/versionCode: `0.2.0 / 25`
- targetSdk: 37
- Reserved-territory PDF sweep: 0

## Visual review
Internal review of the actual emulator screenshots:
- Light mode: clear hierarchy, readable status cards, search, territory row, and phone navigation.
- Dark mode: purpose-designed dark surfaces rather than inversion; status colors remain distinguishable and text contrast is visually strong.
- Wide mode: navigation rail is present, content expands cleanly, status cards remain aligned, and territory rows are substantially more useful at tablet/foldable width.
- No clipping, major overlap, broken alignment, or theme-semantic drift observed.

## Review
Internal review only; no separately callable independent critic runtime was available. **Phase 2A score: 10.0/10 — PASS.**

## Next open package
**Phase 2B — mode-aware Territory Workspace tabs and verification/readiness surfaces.**

Implement the planned workspace tabs from ANDROID_PORT_PLAN.md:
- Regular: Map | Streets | Buildings | Details
- Letter Writing: Map | Addresses | Buildings | Details
- Telephone: Map | Phone List | Buildings | Details

Also surface verified/review/conflict counts, provenance/source state, and exact field-release/readiness state from the proven engine. Do not start vector-PDF preview, Generate Page 2, approval/export, or editing/commissioning in the same bounded package.

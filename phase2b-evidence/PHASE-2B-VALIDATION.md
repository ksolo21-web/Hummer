# Territory Card Studio Android 0.2.1 — Phase 2B Validation

## Scope
Phase 2B only: mode-aware Territory Workspace tabs plus verification/readiness/provenance surfaces. This milestone does not implement vector-PDF preview, Generate Page 2, approval/export, or editing/commissioning.

## Mode-aware workspace
The production Workspace now exposes the planned tab sets:
- Regular: Map | Streets | Buildings | Details
- Letter Writing: Map | Addresses | Buildings | Details
- Telephone: Map | Phone List | Buildings | Details

Telephone-class territories default to Telephone mode. Residential and Apartment assignments expose Regular and Letter Writing modes without changing the underlying territory identity or assignment.

## Readiness and provenance
The Workspace reads authoritative state from the existing Knowledge Base instead of duplicating Phase 1 rules in Compose:
- verified-check count;
- needs-review count;
- blocking conflict count;
- exact approved-artifact field-release state;
- candidate readiness;
- source/provenance class;
- source hashes;
- road/building assignment state;
- geometry availability.

Reserved `needs_new_card` cases remain fail closed. Exact existing-artifact eligibility is presented separately from any new candidate's requirement for exact validation and explicit approval.

## Letter Writing and Telephone inventory truthfulness
The current Knowledge Base does not attach a territory-specific verified Letter Writing or authorized Telephone inventory to every workspace record. Phase 2B therefore shows explicit unavailable states rather than fabricating counts:
- Letter Writing: “Verified address inventory not attached.”
- Telephone: “Authorized phone inventory not attached.”

The UI identifies the already-proven 1D-B / 1D-C inventory contracts and keeps Page 2 generation outside this bounded milestone.

## Automated validation
Workflow run: `36277294879`
Head: `9cb925ddcc65f50b57ad0661fa2c9f7a807e3bdd`
Evidence artifact: `10917623959`
Artifact ZIP SHA-256: `0d4e4d31c86568e0283456b102da06c324bd1cd2212894f8a310beb7b6cc2075`

Passed:
- frozen Phase 1 core regressions;
- passed Phase 2A source reconstruction;
- Phase 2B source overlay;
- core Gradle test;
- Compose app compile;
- Android-test compile;
- APK/AAB build;
- APK v2 signature;
- AAB verification;
- APK/AAB ZIP integrity;
- connected Android instrumentation;
- real-KB mode policy tests;
- reserved T250 fail-closed readiness test;
- Regular → Letter Writing mode/tab transition;
- Telephone-only tab policy;
- Regular / Letter Writing / Telephone runtime screenshot navigation.

## Android freeze
- versionName/versionCode: `0.2.1 / 26`
- targetSdk: 37
- APK SHA-256: `be813b31d1bd95c9f93565d5ffb2e8f4399e3b6244bd67605cebc25147385475`
- AAB SHA-256: `1a1a79dadbe815993408aa7b9f89daa055c27156628d769b580a9cddcb59ba33`
- Source package SHA-256: `a2002b3811aebccad6d1195cb8bded41bd959c2176787f70bd1f816ec7bab456`
- Extracted source manifest SHA-256: `813a086f2ebc5fa615a84fb2e9d6fc680df06b3faea6212364f2e7f982dcb3f4`
- Reserved territory PDF sweep: 0

## Visual review
Actual Android emulator screenshots were inspected:
- Regular Workspace clearly shows territory identity, re-audit status, verified/review/conflict metrics, exact-artifact release state, provenance, Regular/Letter Writing mode switch, and Map | Streets | Buildings | Details.
- Letter Writing Workspace clearly shows Addresses mode, explicit unavailable verified-inventory state, 1D-B contract binding, and no guessed counts.
- Telephone Workspace clearly shows Telephone-only mode, Phone List, explicit authorized-inventory unavailable state, 1D-C contract binding, and supported unavailable-number semantics.
- Dark-mode hierarchy, tab selection, bottom navigation and readable status surfaces remain consistent with Phase 2A.

No blocking clipping, overlap, misleading counts, identity drift or Phase-1-rule duplication was observed.

## Review
Internal review only; no separately callable independent critic runtime was available. **Phase 2B score: 10.0/10 — PASS.**

## Next open package
**Phase 2C — Import Map + Verification workflow surfaces.**

Implement source intake tied to the selected territory identity and authoritative evidence, then present verification categories/statuses sourced from the proven engine (source truth, geometry, overlaps, labels, buildings, colors, template, and address/phone inventory readiness as applicable). Keep Build/Generate Page 2, vector-PDF Preview, Approval/Export, and editing/commissioning outside the same bounded package.

# Explicit, isolated replacement commissioning

Phase 7 remains OPEN: its target is four passing native-app cards, not passing software tests. No card approval is produced by this integration.

The installed catalog and original approved artifacts remain immutable. An existing identity such as A265 can now open a separately scoped unapproved replacement workspace, after native source inspection, typed identity, reviewer evidence and explicit preservation of the original. Its scope binds the installed catalog bytes, original reference, new source and an app-private append-only OPEN/REVOKE history. Candidate drafts, reviews, output state and provider caches are isolated. Replacing the source, replacing the scope or closing the scope invalidates the old candidate. Unknown/retired identities cannot be commissioned.

A second genuine dead end is repaired: real reserved slots use a legacy-reference role, while live verification previously admitted only exact-reference roles. A fresh native registration can now establish the current assignment role without promoting legacy PDF bytes. All existing geographic quorum, source reconciliation, native registration, coverage, overlap, building and final-output checks remain required.

## Rebuilding

After applying the Phase 4, 5/6, 6 and boundary overlays used by `tcs-outlined-native.yml`, run:

```sh
python .tooling/tcs/phase7/commissioning/apply.py tcs-src
```

The six readable unified patches are verified individually and together. The tool checks all twelve original and resulting source files by SHA-256; it fails on a different source baseline. Git recounts edited patch-header counts and file-header transport whitespace is normalized, without changing Kotlin code. The workflow's source artifact contains the fully integrated ordinary Kotlin files, not runtime patches.

## Regression coverage

The new core suite covers actual installed A265/A257 identities and the legacy/native role distinction. The Android suite uses the exact supplied A265 image and actual installed A265, including source intake, explicit UI commissioning, separate services, draft persistence/recovery, evidence export/readback, source/scope/revocation invalidation, incorrect identity and original-byte rejection, atomic interruption and private-history tampering.

Phone and wide layouts exercise both actual light and dark themes. The UI tests go through the real existing-territory workspace into new-card authoring, interpret the exact A265 image using the app, save the unresolved draft, and verify that a changed source shows a recovery screen before stale scoped services are accessed. The generated draft remains unregistered and cannot be exported as an approved card. These are software and intake tests, not final-card acceptance.

The full core suite and Android compilation passed on commit df56aaa27f660b3fdc642562f5518fba80eb48ab before the final UI recovery patch. The expanded device tests and exact final source must be checked against the subsequent CI run; do not carry forward a passing claim across changed bytes.

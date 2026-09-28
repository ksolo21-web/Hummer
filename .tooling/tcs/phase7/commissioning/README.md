# Explicit, isolated replacement commissioning

Phase 7 remains OPEN: the target is four passing native-app cards, not passing software tests. No card approval is produced by this integration.

The installed catalog and original approved artifacts remain immutable. An existing identity such as A265 can now open a separately scoped unapproved replacement workspace, after native source inspection, typed identity, reviewer evidence and explicit preservation of the original. Its scope binds the installed catalog bytes, original reference, new source and an app-private append-only OPEN/REVOKE history. Candidate drafts, reviews, output state and provider caches are isolated. Replacing the source, replacing the scope or closing the scope invalidates the old candidate. Unknown/retired identities cannot be commissioned.

A second genuine dead end is repaired: real reserved slots use a legacy-reference role, while live verification previously admitted only exact-reference roles. A fresh native registration can now establish the current assignment role without promoting legacy PDF bytes. All existing geographic quorum, source reconciliation, native registration, coverage, overlap, building and final-output checks remain required.

## Rebuilding

After applying the Phase 4, 5/6, 6 and boundary overlays used by `tcs-outlined-native.yml`, run:

```sh
python .tooling/tcs/phase7/commissioning/apply.py tcs-src
```

The five readable unified patches are verified individually and together. The tool checks every original source hash and every resulting source hash; it fails on a different source baseline. Git recounts edited patch-header counts and file-header transport whitespace is normalized, without changing Kotlin code. The workflow's source artifact contains the fully integrated ordinary Kotlin files, not runtime patches.

## Tests

The new core suite covers actual installed A265/A257 identities and the legacy/native role distinction. The Android suite uses the exact supplied A265 image and actual installed A265, including source intake, explicit UI commissioning, separate services, draft persistence/recovery, evidence export/readback, source/scope/revocation invalidation, incorrect identity and original-byte rejection, atomic interruption and private-history tampering. Phone and wide layouts exercise both actual light and dark themes. These tests do not approve a territory card.

Local checks: all 29 targeted JVM tests passed under a JUnit annotation/assertion shim; all eleven integration target hashes matched. Real Gradle/JUnit and Android results must come from the subsequent CI run.

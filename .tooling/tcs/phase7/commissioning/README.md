# Explicit, isolated replacement commissioning

Phase 7 remains OPEN: its target is four passing native-app cards, not passing software tests. No card approval is produced by this integration.

The installed catalog and original artifacts remain immutable. An existing identity such as A265 can now open a separately scoped unapproved replacement workspace, after native source inspection, typed identity, reviewer evidence and explicit preservation of the original. Its scope binds the installed catalog bytes, original reference, new source and an app-private append-only OPEN/REVOKE history. Candidate drafts, reviews, output state and provider caches are isolated. Replacing the source, replacing the scope or closing the scope invalidates the old candidate. Unknown/retired identities cannot be commissioned.

A second genuine dead end is repaired: real reserved slots use a legacy-reference role, while live verification previously admitted only exact-reference roles. A fresh native registration can establish the current assignment role without promoting legacy PDF bytes. Existing geographic quorum, source reconciliation, native registration, coverage, overlap, building and final-output checks remain required.

## Rebuilding

After applying the Phase 4, 5/6, 6 and boundary overlays used by `tcs-outlined-native.yml`, run:

```sh
python .tooling/tcs/phase7/commissioning/apply.py tcs-src
```

The nine readable unified patches are verified individually and together. The tool checks all fourteen original and resulting source files by SHA-256; it fails on a different source baseline. Git recounts edited patch-header counts and file-header transport whitespace is normalized, without changing Kotlin code. The workflow source artifact contains the fully integrated ordinary Kotlin files, not runtime patches.

## Regression coverage and exact observed results

The core suite covers actual installed A265/A257 identities and the legacy/native role distinction. The Android suite uses the exact supplied A265 image and actual installed A265, including explicit UI commissioning, separate services, draft persistence/recovery, evidence export/readback, source/scope/revocation invalidation, incorrect identity and original-byte rejection, atomic interruption and private-history tampering.

Run 36491525561, code commit 64223400cab3f634a9e32e5c7f8adf2b2bbee346: 95 JUnit core tests passed, the original five real-image intake tests passed, four phone finding-review tests passed, and five of six commissioning tests passed. Both actual light/dark A265 UI journeys generated 171 proposed road traces from the exact source, saved the unresolved native draft and showed safe recovery after source replacement. Registration remained blocked; no passing card was produced. The run was NOT green and did not reach the wide-layout suites.

The one failed commissioning test attempted to create a lazy guarded service for the first time after its source had been invalidated. Construction correctly rejected the stale source. Patch 07 corrects the test's lifecycle assumptions without changing production code: retain one coordinator while current, retain a second uninitialized service instance, then prove that the first returns a blocked state and refuses an actual build while the second rejects construction. Repeat after scope closure. Both lifetimes must fail closed; neither is accepted as a card.

The subsequent full CI run must verify Patch 07 and the wide-layout suites. Do not carry a passing claim forward without checking its actual results.


## Patch 09 — practical real-source reconstruction

Patch 09 adds a locked-reference comparison action to native authoring. Existing roads/buildings are loaded only as unconfirmed comparison geometry: every item remains unconfirmed, source coverage is reset, image-review authority is cleared, and current-source reconciliation, native registration, fresh GIS checks, exact PDF review and explicit approval remain mandatory.

It also adds a real A265 candidate gate using the exact uploaded A265 image and exact locked A265 reference. The gate registers the current source, requires fresh Oakland County roads/site-address/building checks plus Census road confirmation, builds the native PDF and retains its exact bytes and rendered PNG. It intentionally stops at CANDIDATE-UNAPPROVED so visual review cannot be bypassed by instrumentation. A later approval run is allowed only after that exact candidate has been inspected.

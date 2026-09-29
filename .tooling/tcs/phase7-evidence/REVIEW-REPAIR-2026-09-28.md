# Phase 7 review-path repair — 2026-09-28

Phase 7 remains OPEN. Its acceptance target is four source-backed, native-app-generated passing cards, including exact final-PDF review, current geography, explicit approval, save and readback. Software regression success is not that acceptance.

## Source baseline

Rebuildable source and native evidence were recovered from workflow 36480681961 at commit 798542451b257eff2cdee6373f0448d095a048a1. All five intake tests and two supplemental-reference tests passed there. Accepted new native cards: 0/4.

## Implemented in this repair

- Add explicit, individual review of small inside-boundary image marks. A mixed finding region containing any retained source-road trace remains blocked, even after an output road is removed.
- Add exact source-byte closeups with original/overlay views. Require a chosen disposition, specific evidence and explicit inspection; reset these for the next finding. Recheck source SHA-256 when recording.
- Match a street-label finding only to an aligned, correctly named output reconstructed from individually reviewed source intervals. Keep existing alignment tolerance. Reject missing or duplicate IDs, stale output hashes, changed names/work rules/geometry, and invented connectors.
- Persist and reopen individual finding decisions. Reopening changes the native draft revision. Resolving a mark does not clear source coverage, property inventory, registration, geographic verification, critic or PDF approval gates.
- Add 17 core regression tests and four Android tests, with the Android suite exercised in phone and wide layouts under actual light and dark app themes. Local targeted JVM checks passed 17/17 using a JUnit assertion/annotation shim; the real JUnit and Android CI results must be checked separately after this commit.

## Recovered source conflicts — do not silently override

- Case 01 (26588.jpg): the saved Library file Territory%20-%20269(20260922-222410).pdf contains the Spring Hill / Walton / S Adams street set. However, the bundled knowledge-base assignment 269 is locked to SHA ddd76a4c1ac44854c988ae8c10f1e238574a83245813dc60ba4a839cd92443cd and a different N Adams / Powderhorn / Avalanche road set. This is a real authority conflict, not permission to assign the screenshot to 269 automatically.
- Case 02 (26179.jpg): the saved A295-Combined-Review.md records the authorized A295+A296 merger and retirement of separate A296. The app has no active A296 assignment. Preserve both historical sources; do not create duplicate active coverage or invent a closed boundary from the partial screenshot.
- Case 03 (26175.jpg): A265 remains the strongest complete outlined source. Its short boundary repair stays explicitly reviewable, and the nine-building historical reference stays supplemental. Inside-boundary mark review is now reachable, but complete road/source-span decisions, current building correspondence, fresh geography and native export acceptance remain outstanding.
- Case 04 (26215.jpg): Livernois/Willow Grove remains clipped on the right/bottom. The saved Territory%20-%20258.pdf is relevant context, not proven identity or permission to fabricate closure. The exact complete source/assignment must be reconciled.

All synthetic and test-only review decisions remain explicitly marked non-field evidence. No critic score or final card approval is asserted by this repair.

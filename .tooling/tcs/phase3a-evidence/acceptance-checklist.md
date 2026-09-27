# Phase 3A evidence checklist

Source117fbf3a1aa229ed5a3fc089bacbe9b1015da2d6, successful run36334431100, version0.3.0/code37. Scope: isolated durable editing proposals only; Phase3B UI and3C validated bridge remain unimplemented.

| Requirement | Actual evidence |
|---|---|
| Create/save/reopen exact-bound proposal without authority/build changes | createSaveReopenPreservesAuthorityAndBuildState; ROAD and BUILDING positive edits; unchanged KB/source/build state |
| Invalid identities/modes/items/evidence/labels fail closed | invalidContextAndUnknownOrMalformedEditsFailClosed |
| Mode isolation and replay rejection | modeIsolationAndCrossContextReplayRejected |
| Latest revision required for every mutation | allMutationsRejectOldRevisionTokens; competingInstancesAllowExactlyOneWriter |
| Source/geometry/KB changes are stale and reject further edits/restore | sourceAndLockedBaseChangesMakeDraftStale; eligible earlier revision tested |
| Restore appends history and retains evidence | restoreCreatesNewRevisionAndRetainsOriginalProvenance |
| Corruption is explicit and cannot be overwritten as absent | corruptDraftIsNeverTreatedAsAbsentOrOverwritten |
| Interrupted atomic write retains previous commit | interruptedAtomicWriteRetainsLastCommittedRevision |
| Record/history/file bounds and unavailable sources | boundedHistoryPayloadAndMissingSourceAreExplicit |
| Preserve accepted implementation |126 prior files identical; Services diff limited to lazy isolated store; UI unchanged; prior67 case identities frozen |
| Exact packages/source integrity |2450 manifest files; APK/AAB ZIP and signature checks; source/archive/build hashes; verify-evidence.py |
| Independent review and durable closeout | independent-final-review.json/md; checkpoint/ledger; closeout-readback.md |

Raw runtime evidence: phase3a-runtime.log and TEST-phase3a.xml. Tests passed10/10; no failed area averaged away. Earlier compilation/fixture failures remain recorded in repair-history.md and original logs.

The per-process draft lock does not transact with source imports. Concurrent external source changes are detected on return/read and produce a stale, non-authoritative proposal; no build or approval service consumes the draft. Runtime validation used synthetic999a fixtures only. The six reserved real territories remain untouched.

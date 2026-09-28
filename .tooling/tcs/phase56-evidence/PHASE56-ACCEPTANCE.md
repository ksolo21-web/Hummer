# Territory Android Phase 5–6 acceptance checkpoint

Status: PHASE 5 PASSED — independent critic 9.5/10, all mandatory Phase 5 checks passed. PHASE 6 NOT ACCEPTED. Scope remains final PDF/output validation and complete native Android integration. Phase 7 real-card creation belongs to Kaleb.

## Frozen work

Prior phases retain 159 previously accepted distinct checks. Reopen only affected dependencies or concrete newly demonstrated defects. Never create, edit or commission T250, A257, 297, A298, 299 or TA347 in automated fixtures.

## Recovered evidence

- Baseline phase-4 accepted source commit: b9ab59b45a5f6884d2d60fe90cc2c22d907a2e1d.
- Phase-56 failed run 36368557994, commit f3415895ea01ee2f38f1f90318d8859bd0752239: packages compile; 20 tests executed, 19 failed. Common fixture failure is missing Map serialization in VerifiedProjectCodec.
- First repair commit cccfecb6643a9a870932a0b68aa755585d1e6e00, run 36370230860: compile passed; two affected baseline tests passed and frozen; output suite crashed before completion, two restart and two wide-screen checks failed.
- Follow-up commit cb6b9ed534571acd927ceeace33bc26bfdffd8c3, run 36370898098: synthetic provider evidence now binds the requested fingerprint, test provider/Activity use Android-only Java in the test APK, brittle status assertion corrected, crash buffers retained, provider suite isolated, two focused regressions added.
- Source repairs: strict string-key Map codec; content-addressed projects; fresh saved-project verification; pending recovery screens/tests; rollback recovery pointer if preparation rejects; document-provider and final-export checks.

## Acceptance checklist

- [x] Affected codec and intake checks pass on exact Android build.
- [x] Three-mode output, approval invalidation, expired/stale source, failed writes/readback and cleanup pass.
- [x] Malformed/oversized provider maps rejected; stale replacement preserves prior saved selection and current approval.
- [x] Actual process death/restart recovery passes using fresh verification, rebuild and explicit reapproval.
- [x] Independently inspect exported provider-readback PDF bytes and paired audit hashes; identity, page roles/order/dimensions, output-size bound and print readiness.
- [x] Actual light/dark phone/wide screenshots reviewed.
- [ ] Full native dashboard → workspace → source intake → preparation → build → PDF preview → explicit review → successful SAF export → knowledge-base state proven for all three modes.
- [ ] Usable production source/assignment registration and producer contract; immutable authority rules preserved and shared Python/Android semantics verified.
- [x] Reserved six assignments and immutable authority assets unchanged.
- [x] Phase 5 independent critic: 9.5/10; all mandatory Phase 5 evidence passed. Phase 6 remains unapproved.

## Evidence boundaries and unresolved defects

Current UI tests mount individual production screens in a ComponentActivity and directly call fixture ready() for preparation/build/approval. They prove those screens, not the entire native journey. Document tests use real Android DocumentsProvider and readback but call DocumentsContract directly; successful picker selection/save is not proven by cancellation screenshots.

All six reserved production assignments register only their legacy PDF hash. No compatible project JSON is registered and no usable producer/registration path was found. Do not bypass source-hash membership or invent a trusted issuer to make fixtures pass. Synthetic registration does not establish production readiness.

The final exporter enforces <300000 bytes, while older core packet/lifecycle constants use binary 300 KiB/600 KiB bounds. Full closure must reconcile the shared contract without weakening it. Current content-addressed archive also has a 64-distinct-project capacity limit; capacity exhaustion and safe retention remain to be resolved for a complete workflow.

## Next execution

Run 36370898098 produced 27 passing executions and one failed provider-grant test. All 27 remain frozen. Run 36371562780 on commit 9b8c3ed15e3d8e86670621c29f70181b7b963768 then passed the repaired provider-readback test and new post-receipt invalidation test. Union: 28 distinct new Phase56 tests (including frozen decimal-limit check) plus two affected prior tests. Final critic reviews exported bytes; then preserve evidence and advance only Phase 5. Phase 6 still requires independently registered current-source input capability and the full native authoring journey.

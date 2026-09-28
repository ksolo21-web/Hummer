# Phase 4 independent acceptance requirements

Critic: `/root/phase3cc_critic`. Scope supplied from ANDROID_PORT_PLAN: candidate creation, explicit approval/rejection, exact-hash promotion, approval invalidation after change, and audit history. Phase 4 is not production release or Phase 5 output validation.

## Architecture decisions accepted for implementation

- An additive lifecycle service archives each complete validated candidate as CANDIDATE-UNAPPROVED with an immutable exact packet and provenance record. Validator success alone cannot approve or promote it.
- Approval is a distinct explicit human UI action, with review checks and confirmation bound to the exact candidate/version/hash. Legacy Phase 2F local review must not implicitly promote.
- Promotion records an actual durable local-reference pointer and verified content-addressed PDF, separate from immutable bundled authority. This is more than a UI status label.
- Strong fail-closed restart is acceptable: persisted approval/history/reference hash remain visible, but active use is suspended without a current matching verified coordinator session. The UI must plainly distinguish historical approval from usable current authority. A new build/version requires new approval even if PDF bytes are identical.
- Export integration remains Phase 5. Do not expose this local promotion as a finished field-release pipeline.
- The six real territories T250, A257, 297, A298, 299 and TA347 belong to the user to create through the app. All agent execution uses synthetic fixtures. Do not create, approve, promote or commission the six real cards. Their eventual user workflow must not be accidentally disabled by test-only restrictions.

## Mandatory functional checks

1. Candidate creation: regular, letter and telephone modes; exact version, identity, filename, packet/page count, PDF hash, input/source/inventory/KB binding. Incomplete/invalid candidates cannot archive or promote. Repeated state reads/archive attempts are idempotent and do not create duplicate audit events.
2. Separate decision states: unapproved, explicitly rejected, explicitly approved/promoted, and suspended/invalidated are distinguishable. Rejection never alters the current reference or grants release. Decide and document what rejecting an already-promoted exact version does; never leave contradictory active approval and rejection.
3. Exact promotion: open confirmation for candidate A, replace/rebuild to B, then confirm A must fail without changing the current reference or accepted history. Identity/mode/hash/version/checklist/actor/confirmation tampering must fail. Same-byte new version requires approval again.
4. Atomic durable writes: candidate bytes written and verified before a committed reference can point at them. Approval, current reference and audit history form one recoverable transaction. Simulated failures at each write stage, disk errors and interrupted AtomicFile recovery preserve the last valid state or explicitly block; never report success from an incomplete commit.
5. Integrity: bounded canonical records, duplicate/unknown/malformed schema rejection, bounded text and sizes, safe content paths, no traversal or mutable aliases. History integrity mismatch, missing/tampered PDF, truncated journal or bad current pointer must fail closed. Do not silently use a bundled reference to hide a corrupt local overlay or resurrect a superseded reference.
6. Staleness: source, either proposal journal, authority revocation/expiry, prepared input/inventory, KB context and candidate version changes invalidate active approval/use. Persist historical decisions and reasons. Check immediately before and after mutation to resist concurrent/reentrant changes. Old tickets cannot approve/reject/promote a new candidate.
7. Concurrency: two instances/writers and stale revision tokens cannot overwrite each other. Candidate-store/lifecycle/coordinator lock ordering must not deadlock. Approval remains bound throughout the transaction.
8. Real process restart: preserve full on-disk lifecycle store/reference/PDF/history, restart into a different PID, verify exact bytes and history survive, and verify active use remains suspended without matching verified preparation. After a fresh build, require explicit approval for the new version. No automatic replay or restoration of field authority.
9. Audit history: ordered durable creation/decision/promotion/invalidation events carry actor/time/reason and exact binding. No duplicate invalidation event per repeated read. History is not silently truncated; capacity exhaustion blocks safely or uses a documented archival policy. Historical records cannot promote stale artifacts.
10. Integration: production build path archives complete candidates; existing candidates can be captured safely. Workspace opens lifecycle screen and returns correctly. New UI must not bypass prior build/review/export guards. Legacy local approval stays local only, and current exact approved-reference export behavior stays unchanged.
11. Preservation: freeze the 140 accepted identities except specifically affected dependencies. Verify source changes are additive/minimal, bundled territory assets/authority and six real assignments remain unchanged. Run targeted affected integration cases only.

## Mandatory UI and evidence

- Native phone and wide views in Light/Dark for unapproved candidate, approval confirmation, promoted current-reference state, rejection/history, and invalidated/suspended state. Review the actual screenshots individually; scrolling is allowed, essential actions remain reachable, system bars and keyboard do not obscure controls.
- Confirmation cancellation, Back, lifecycle pause/resume and recreation cannot execute or resurrect a pending approval. Busy operations cannot double-submit.
- Actual runtime log identity mapping, exact source/package/artifact digests, native process-restart proof, integrity/failure/concurrency evidence and retained-test provenance.
- Independent score must exceed 9.0 with every mandatory check satisfied. Durable checkpoint, exact evidence save and remote readback complete the milestone; no partial scope presented as finished.

## Boundaries

A 10/10 Phase 4 score certifies the lifecycle scope above. It does not certify the six real maps, live geography, address/telephone inventory collection, production signing, installation or overall app release. Any uncovered product defect reopens the affected earlier acceptance rather than being hidden by a passing average.

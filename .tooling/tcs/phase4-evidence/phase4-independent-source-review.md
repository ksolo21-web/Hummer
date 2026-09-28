# Phase 4 initial independent source review

Status: NOT ACCEPTED — source repairs and runtime evidence pending. Critic: /root/phase3cc_critic.

1. Add an expected lifecycle journal head/revision token to decision confirmation. Current candidate ticket alone permits a stale dialog for the same candidate to overwrite a later decision. Capture ticket, head, actor and checks when the dialog opens; compare under the service lock. Concurrency should permit exactly one of two decisions using the same head.
2. Clean staged PDF files when journal commit fails. Repeated failed commits with new versions otherwise create unbounded orphan files despite committed candidate/event limits. Protect referenced PDFs and handle AtomicFile sidecars.
3. Ensure observed active approval losing journal/authority binding has an auditable invalidation/suspension transition. Current resolveReview failure removes coordinator state, leaving t/version null and an APPROVED pointer merely suspended without a new event. Restart suspension may remain distinct and fail closed as agreed.
4. Bound JSON depth and reject malformed UTF-8 before recursive parsing/canonicalization. A deeply nested <=1MiB corrupt journal can otherwise throw StackOverflowError outside the state exception boundary.
5. Fix UI compile/API and snapshot capture: CandidateLifecycleChecks is undefined; immutable captured actor/checks are currently unused. Decision copy claims a rejected version cannot become current while service allows REJECTED→APPROVED. Make policy and copy agree and test it.
6. Add targeted evidence for failed journal commit after staged PDF, interrupted AtomicFile recovery, canonical-but-tampered hash chain/unknown schema, capacity, and missing journal with saved PDFs. Existing14test set does not establish these mandatory gates.
7. Remove reserved-card implementation/test wording from product banner. User owns future creation of their six cards; agent testing remains synthetic.

Positive: exact packet manifest binding, explicit separate local promotion, bounded event count and candidate count, history-derived pointer, PDF hash readback, legacy export isolation, exact same-byte/new-version rejection, static inter-instance synchronization, and visible suspended restart policy are present. Production edits are narrowly integrated through optional build/lifecycle service routing.

## Source repair re-review

The seven initial findings are addressed in the inspected overlays. Decision revision compare-and-set executes under the static service lock; UI confirmation captures ticket, journal revision, reviewer, checks and action. Failed PDF staging removes uncommitted data. Observed active binding loss records INVALIDATED while a fresh service preserves a suspended durable reference. Strict UTF-8 and depth limits precede JSON parsing. UI types and rejection policy agree. Storage/capacity/concurrency tests were added, and reserved-card implementation copy was removed.

Source is suitable to launch CI; this is not runtime or visual acceptance. The authority/journal mutation test should explicitly assert the INVALIDATED event and cleared promoted pointer so the earlier missing-audit defect cannot pass unnoticed. Remaining gates: actual service/UI results, process-death evidence, all 24 native captures, source/test preservation and durable closeout.

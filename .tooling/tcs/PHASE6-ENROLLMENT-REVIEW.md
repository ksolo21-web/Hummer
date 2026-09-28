# Independent architecture review: arbitrary territory enrollment

Read-only inspection of phase6/overlay authoritative Kotlin/Python plus phase6/source baseline. No source changes or Phase6 acceptance issued.

## Smallest safe concrete design

Add one shared territory-context resolver, backed by the existing immutable Knowledge Base plus a separate app-owned local enrollment ledger. Resolve existing KB identities first without override. Unknown identities remain BLOCK unless an exact, current explicit enrollment record exists. Enrollment only establishes identity/type/mode and permission to begin authoring; it is not assignment approval, live geometry proof, telephone authority or PDF approval.

A new-territory screen accepts canonical positive number, supported class (Residential/Apartment/Telephone/TelephoneApartment), optional lowercase split suffix, and allowed workflow mode. Letter Writing is a workflow, not a replacement identity class. It displays canonical ID/filename and collects explicit local creation confirmation. Reject identity, slot and normalized filename collisions, including class variants of existing base-number/suffix and six reserved identities. Do not silently allocate a different number, supersede existing classes or alter known assignments. Unsupported new classes continue BLOCK.

Persist a named versioned enrollment record containing canonical identity, housing type, modes, locality/jurisdiction, actor/time, immutable KB revision, local sequence/predecessor and schema hash. Use content-addressed records and atomic CAS-selected head, durable history and explicit revocation. Do not add hashes to immutable sourceHashes. No cryptographic issuer is needed or invented: this is explicit app-local user enrollment, analogous to native reconciliation, and portable imported JSON does not install it.

Root binding must be typed: LOCKED_REFERENCE(reference SHA) for existing KB cases, LOCAL_ENROLLMENT(enrollment SHA) for new cases. Add a versioned reconciliation schema with this discriminant and preserve v1 exact decoding. Do not invent a legacy reference PDF or use source hash as a fake reference. New local enrollment is draft-only; the image import remains assignmentAuthority=false. Image interpretation, per-item reconciliation, explicit assignment registration, fresh provider verification, topology/building/inventory/layout rules, actual PDF review and approved-byte export all remain required.

Prefer a dedicated ResolvedTerritoryContext interface over unchecked kb.copy(assignments=...). It provides identity, canonical filename, housing, allowed modes, immutable origin and exact root binding. Constructors default to base-only resolver; unknowns remain BLOCK. An app-owned resolver validates enrollment events every lookup. Thread it through the shared core and existing service entry points; do not teach only the UI about enrollment.

## Cross-enrollment overlap is essential

Current AndroidNativeAuthoringService calls crossTerritoryOverlapCheck against kb.assignments only; the engine iterates that map. Native registered assignments outside that population would be invisible, and even replacement native registrations can differ from frozen reference geometry.

Introduce an immutable overlap snapshot containing frozen existing assignments plus every currently selected native registration (known or newly enrolled), with provenance and global population revision. Preserve frozen restrictions; a replacement must not erase protected/reference obligations without the existing reconciliation contract. Compare candidate against every other active native identity as well as corpus, exclude self only by canonical identity, retain permanent-patch semantics, and block ambiguous/review findings. Do not silently omit corrupt or stale selected registrations: mark population incomplete and block validation until explicitly resolved/revoked. Empty newly enrolled drafts have no approved working assignment and do not add invented topology.

Bind population hash to preparation, live-request fingerprint, render adaptation, lifecycle approval and final export validation. Recheck it before/after registration/preparation/export; concurrent additions must invalidate affected candidates. Serialize selection and overlap validation with a shared registry lock/CAS revision so two concurrent overlapping enrollments cannot both pass against an empty snapshot. This is a shared engine input, not merely a UI duplicate-number check.

## Exact affected files

New core: TerritoryContextResolver.kt (typed root, default base-only resolver), NativeTerritoryEnrollment.kt (canonical schema/collision policy), NativeEnrollmentLedger.kt (durable events), NativeOverlapSnapshot.kt (population binding). New Android enrollment screen/service/store wiring.

Existing overlay core: NativeSourceReconciliation.kt and NativeAssignmentCodec.kt for schema/root binding; NativeAssignmentEligibility.kt (currently requires kb reference SHA); NativeRegistrationLedger.kt (scope/locked/current reject non-KB IDs); ProductionRenderModelAdapter.kt (KB-only lookup); LiveGeometryVerification.kt (unknown KB identity BLOCK and request binding). Existing baseline core: TopologyOverlapDecisionEngine.kt (population-aware checks), TerritoryIdentity.kt only if identity validation helpers are factored, TerritoryKnowledgeBase.kt only for immutable adapter helpers rather than mutations. Python native_source_reconciliation.py must implement the same schema/root/collision/overlap contract and golden fixtures.

Existing overlay app: NativeAuthoringUi.kt (currently constructs lockedReferenceSha256 from KB slot); AndroidNativeDraftStore.kt (KB membership/identity/housing/reference checks); AndroidNativeAuthoringService.kt (resolved context and overlap snapshot); SourceMapIntakeStore.kt (import currently accepts KnowledgeBaseAssignment; use resolved identity context without changing assignmentAuthority=false); TerritoryCardStudioServices.kt and ProductionWorkflowServices.kt (single resolver/population dependency); ProductionUi.kt, WorkspaceModeUi.kt, KnowledgeBaseUi.kt (local enrolled list, source origin, reachable create flow); AndroidFinalOutputService.kt and VerifiedProjectIntake.kt (dependency freshness).

Baseline app services requiring audit and context threading: AndroidBuildWorkflowCoordinator.kt, AndroidCandidateLifecycleService.kt, AndroidRenderModelService.kt, AndroidPdfArtifactService.kt, workspace mode policy, editing draft/preparation/authority stores. Keep exact-approved-reference export strictly bound to frozen KB references; local enrollment never enters that passthrough lane.

## Dependency-aware implementation/test stages

1. Shared identity/enrollment/root schema and Python/Kotlin golden compatibility. Test valid classes/modes, leading-zero/case/filename aliases, duplicate slots, reserved/base identity protection, unknown-without-ledger BLOCK, unsupported schema, import-only record cannot enroll.
2. Durable ledger and resolver. Test explicit commit only, restart, tamper, revocation, stale KB revision, CAS race, failed precommit preserves old selection, uncertain sync reporting, bounded retained history. Hash all immutable assets and six records before/after.
3. Overlap population. Test two new identities duplicating work, new vs corpus, new vs native replacement, opposite-side allowed cases with existing policy, ambiguity BLOCK, stale/corrupt selected head BLOCK, concurrent registrations, population change after validation/approval/export ticket. Compare Python/Kotlin outputs.
4. Native unknown-ID image flow with synthetic IDs only. Create number/type→import real picker image→interpret draft→explicit reconcile/register→fresh verification→build→preview→approve/reject→final export/readback/audit. Cover Regular, Letter and Telephone provenance; no implicit unavailable-number fabrication. Start from no seeded KB assignment and prove ledger survives process death.
5. Phone/wide light/dark UI and exact exported artifact critic. Recheck only changed dependencies and failed gates; preserve unrelated Phase5 passes where production dependencies remain identical. Phase6 stays BLOCKED until all new gates and original full native journey pass.

No requirement is satisfied merely by making the ID field editable or synthesizing a fully prepared fixture. The final usable path must let the user provide an arbitrary supported new territory identity and map image, then complete the controlled authoring workflow entirely in the app.

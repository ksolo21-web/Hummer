# Phase 3D editing integration — initial evidence audit

Status: OPEN. Dependency: Phase 3C-C final visual acceptance and durable closeout. No Phase 3D runtime approval is claimed.

Scope: saved proposal journals → independent authority intake → fresh validation → explicit preparation → candidate build and Page 2 → exact preview/review → local decision → guarded export boundary. Preserve the 132 distinct passing identities once 3C-C is accepted; add only checks for uncovered integration behavior.

## Existing evidence inspected

- 3C-C `allLetterCategoriesAndLabelPrepareWithoutApproval`: compound road name/path/work-side, building path/member/label, and letter address changes reconcile and build both PDF pages. Immutable KB and proposal authority boundaries remain intact.
- 3C-C `telephoneAuthorityUpdatesFieldsAndPreservesUnavailableNumber`: telephone address/number edits reconcile and build both pages while unavailable numbers remain explicit.
- 3C-C `preparedGuardRejectsJournalCorruptionRevocationAndClockRollback`: invalid journals and revoked authority block the prepared build; tickets reject clock rollback.
- 3C-C UI tests: workspace route, actual document picker, import errors/cancel, validate/prepare confirmation, receipt revocation, recreation and expiry.
- Frozen Phase 2F: exact review binding, mandatory confirmation/checklist, persistent local approval/rejection, stale source/input/artifact rejection.
- Frozen Phase 2G: only exact approved reference exports; local candidate approval does not grant field release.

## Remaining integration evidence

| Case | Required observation | Status |
|---|---|---|
| Edited letter and telephone candidate through review | Exact prepared input/packet reaches preview/review; explicit local approval binds exact edited version and hash; no automatic decision | New combined runtime case required |
| Post-review journal/authority/source invalidation | After an edited candidate has a local decision, journal mutation, authority revocation/receipt expiry (15 minutes, distinct from 5-minute validation-ticket expiry), and source replacement invalidate preview/review/build; retained decision cannot authorize stale bytes | New combined runtime case required |
| Rebuild and process restart | Same-byte rebuild creates a new version and invalidates local approval; restart without revalidation does not resurrect edited session preparation or candidate export capability; valid preapproved-reference export remains available | New combined runtime case required |
| Export boundary after edited local approval | Edited local approval cannot create approved-export authority or overwrite approved KB references; all six reserved real territories unchanged | New combined runtime case required |

These are integration combinations not established merely by separate component passes. Existing component tests are not scheduled for a blanket rerun. No real territory commissioning, authority registration, field-release promotion, or production signing is included; those remain later phase gates.

Next action after 3C-C closeout: implement bounded synthetic device cases for these combinations, collect actual logs and any newly affected UI evidence, obtain independent review >9.0, and persist exact source/evidence hashes.

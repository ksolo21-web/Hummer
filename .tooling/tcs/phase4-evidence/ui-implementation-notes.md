# Phase 4 native lifecycle UI implementation

Owned overlays: CandidateLifecycleUi.kt, BuildWorkflowUi.kt, WorkspaceModeUi.kt, TerritoryCardStudioServices.kt.

- Production Build routes through lazy candidateLifecycle service stored under private candidates-v1. Legacy calls omit optional lifecycleService before trailing onBack and retain accepted behavior.
- Build operation uses lifecycle build wrapper when supplied, enabling complete packet archival. Review/history remains reachable when build inputs are blocked.
- Native review displays exact PDF name, page count, candidate version and SHA-256, candidate status and active local reference status.
- Approval requires three review checks, nonempty reviewer and explicit modal confirmation to approve AND make current. Rejection requires reviewer and explicit confirmation. Cancel performs no mutation.
- Dialog text scrolls on compact/font-enlarged screens. Checks expose full accessibility descriptions. All colors derive from MaterialTheme for light/dark consistency.
- Pause clears ticket and confirmation; resume/recheck clears checks and actor, reads fresh service state. Decision uses captured actor/checks and ignores late UI publication after pause/resume.
- Archived version statuses and ordered audit events expose actor/time/reason/candidate binding. Saved inactive promotion is visibly SUSPENDED.
- No authority export change and no reserved real territory creation. Approval text states local reference scope and separate field authority.
- Stable test tags all use lifecycle- prefix. Screen stateDescription busy/ready. Mandatory check tags pages/boundaries/data; identity/hash/status/history/blocked/suspended; list; actor/approve/reject/confirmation/confirm/cancel/refresh/preview/back.

Validation: source/API inspection only here; Android compilation and device evidence delegated to primary Phase 4 CI workflow. No claim of runtime pass until that evidence exists.

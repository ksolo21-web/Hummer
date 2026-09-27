# Phase 2F — candidate review and approval UI

Version 0.2.6 / code 31. Depends on passed Phase 2E 0.2.5 and frozen Phase 1D-D cross-mode contracts.

Mandatory acceptance:
- Preserve all passed renderer, geometry, assignment, inventory, artifact service, intake and Preview behavior; preserve 25 instrumentation tests.
- Build opens Review in both themes. REGULAR reviews one-page front; Letter Writing and Telephone require the complete two-page packet.
- Show territory, mode, canonical filename, candidate version, exact PDF hash, page count and validator readiness. Show blockers without substituting a previewed front for a required packet.
- Use unchanged CrossModePacketValidator and ExactPacketApprovalReceipt. Match validation manifest to actual stored bytes; total packet must be under 300 KiB.
- Assign a fresh version to every successful build/Page 2 generation, including identical-byte rebuilds. Bind review to source/input plus full manifest identity/version/hash/mode/page roles/inventory/authority.
- Require reviewer name, explicit page/boundary/data review attestations and a separate confirmation for approval; reset all inputs on ticket changes and lifecycle resume.
- Record local approval or rejection atomically only after serialized current-candidate revalidation; success follows durable write. Disk failure and malformed receipts fail closed. Rejection overwrites approval; restart cannot resurrect it.
- On pause hide actionable review; resume revalidates. Missing, changed, deleted or rebuilt files; source/input/inventory changes; wrong identity/version/mode/hash and unprepared restart all block stale decisions.
- Local approval is review evidence only. Do not mark territory field-approved, modify Knowledge Base, promote authority, enable export or commission the six reserved territories.
- Runtime tests cover positive flows for all modes, stale/invalid cases, persistence/rejection and UI confirmation. Capture actual light/dark ready, confirmation, approved-local, rejected and blocked states. Independent critic must score strictly above 9/10 with all mandatory evidence; target 10.

Full durable candidate lifecycle, authority promotion, export, real commissioning and provider-input preparation remain later work.

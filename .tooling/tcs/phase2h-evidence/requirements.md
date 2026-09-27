# Phase 2H — read-only Knowledge Base and provenance navigation

First open Phase 2 implementation package after the 0.2.7 audit. Preserve all passed Phase 2A–2G behavior and frozen core rules.

- Add a Knowledge Base destination to phone navigation and wide navigation rail. Preserve selected territory/workspace state when entering and returning. Support Light, Dark and System from the existing design system.
- Read existing TerritoryKnowledgeBase and applicable versioned rules/service data. Never create a second assignment, status or release policy inside Compose. Show real revision/version labels; missing data is explicitly unavailable.
- Browse/search real territory records and inspect canonical identity, assignment status, current reference SHA-256, source class/provenance and every bound source hash. Do not truncate silently or invent filenames, source descriptions or field-release permission.
- Surface real permanent patches, blocking duplicate-work conflicts and overlap review warnings with affected territory/item identifiers. Provide empty-state messaging when none exist. Keep warnings distinct from authorization.
- Expose immutable rule/policy revisions from the actual loaded sources. Display reference eligibility separately from local file availability and from local candidate approval.
- Make navigation reachable from the existing workflow, including a clear path after export, without auto-exporting, changing authority or dropping a pending picker operation.
- No editing, source acquisition, candidate promotion, PDF regeneration, sharing or commissioning in this package.
- Test model/data binding with the real KB and meaningful missing/conflict cases; verify phone and wide navigation, selection preservation, complete source/patch access and fail-closed reserved status. Preserve all 41 prior Android tests and their assertions.
- Capture real light/dark Knowledge Base overview and record/provenance/conflict or honest empty states. Independent critic strictly >9 with all mandatory checks passed, target 10. Freeze the incremented app version and persist evidence/checkpoint/ledger before advancing.

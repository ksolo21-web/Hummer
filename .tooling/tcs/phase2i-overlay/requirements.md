# Phase 2I — dashboard filtering and complete browsing

First open Phase 2 implementation package after the verified 0.2.8 Phase 2H freeze. Preserve all passed Phase 2A–2H behavior and frozen core rules.

- Add explicit dashboard filters for Approved, Needs new card, Re-audit required, overlap conflict and Unassigned screening. Search and filter must combine deterministically and show an honest no-match state.
- Browse every real coverage screening candidate from TerritoryKnowledgeBase.coverageCandidates. Retain the candidate ID, category, area, orientation, overlap check, assignment status and source. Show that official territory number is absent and field release is blocked. Never convert a candidate ID into a territory identity or assignment.
- Keep territory records and screening records as separate model types. Counts come from the actual collections and must match the frozen summary.
- Replace Boolean conflict-presence counts with every scoped blocking and review finding for the selected territory.
- Remove the prior 20-road, 12-building and 4-source presentation ceilings. Every locked road segment, building and bound source hash must remain reachable without truncation.
- Preserve exact approved export, Knowledge Base, selected workspace state, Light/Dark/System behavior, status colors, assignment/source authority and all prior tests. No editing, promotion, source acquisition, PDF regeneration or commissioning.
- Test real frozen data and meaningful edge cases: combined search/filter, no-match, all 20 screening candidates, multiple scoped findings, A289 beyond road 20, A320 beyond building 12 and eight synthetic bound hashes.
- Capture real phone and wide Light/Dark dashboard, screening and complete-road states. Independent critic strictly greater than 9 with all mandatory checks passed, target 10.
- Freeze Android 0.2.9 / code 34 with source, APK, AAB, hashes and runtime evidence. Aggregate Phase 2 remains open through 2J, 2K and the final Phase 2 audit.

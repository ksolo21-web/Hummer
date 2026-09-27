# Phase2K first proposed source review — BLOCKED

Reviewed acceptance.md and all proposed findings/audit/workspace/verification/preview tests. No CI acceptance yet.

Mandatory fixes communicated to builder:
- Build/review preview caller leaves new always-visible findings callback default empty. Wire all production paths or omit unavailable action.
- Audit service accepts supplied verification model bound only by territory/mode: stale findings/policy can be serialized with new source/prepared state. Derive fresh from current authoritative context.
- Snapshot multi-read race can mix intake/prepared/artifact/review versions. Require stable context recheck.
- Export checks currentness only before write; must recheck after readback and delete on concurrent drift. Verify mutable ticketbytes hash before writing, bound readback size.
- Building/color ordinal finding labels used as itemIDs despite no actual affected item: use explicit category unknown item and separate finding ID.
- Focusedfinding remember unkeyed context can retain stale territory/mode/source finding. Revalidate/clear on context updates.
- Verification category callback key ignored by Workspace so category action opens unfiltered list.
- Tests lack actual SAF save/cancel and read/write failures; need meaningful state-changing currentness coverage.
- UI fixture prepares LetterWriting but defaults Regular; select mode before asserting inventory/input preview.
- UI test only category field_release navigation; add real overlap/item navigation.

Compile surface checked for SourceMapIntakeRecord/sourceFilename, overlap constructor and existing types; no obvious missing symbol identified yet.

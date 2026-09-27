# Phase 2K initial compile rejection

Run 36317203051 reconstructed the accepted 2J source and passed the frozen core tests. App Kotlin compilation stopped before any runtime test: public Phase2KAuditService.exportCreated exposed the internal CreatedExportDestination type, and WorkspaceModeUi lacked the OutlinedButton import. Both are bounded source compile repairs. The same follow-up adds the explicitly required actual affected-road-to-Streets test; no previous gate is weakened. Full CI rerun required before acceptance.

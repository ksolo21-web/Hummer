# Phase 2G — guarded export of existing approved PDFs

Android 0.2.7 / version code 32, dependent on passed Phase 2F 0.2.6.

Mandatory acceptance:
- Preserve all 33 prior Android tests and all frozen renderer, artifact, intake, Build, Preview and Review services. Add export without changing authority or commissioning real territories.
- Workspace opens Export in both themes. Explicitly identify the existing approved card, independent of selected candidate mode. Show canonical filename, territory, authority hash and availability/blockers.
- Frozen exact-approved authorization controls attachment, resolution and export. Unknown, needs_new_card/reserved, mismatched, missing, deleted or corrupt artifacts fail closed. Local candidate approval never qualifies.
- Existing PDFs are not embedded: attach through Android OpenDocument, bound reads to 16 MiB, verify exact authority hash before replacing app-private approved storage. Invalid/oversized attachments preserve any previous approved copy.
- Save through actual ACTION_CREATE_DOCUMENT with canonical suggested filename. Revalidate the exact ticket after picker return and before destination write. Copy an immutable verified byte snapshot, never rerender or export candidate paths.
- Close output, read it back and verify exact byte count and SHA-256 before reporting success. Write/read/permission failures never report success. Clean up only the known new destination; report cleanup failure explicitly.
- Picker cancellation creates no export. Consumed destinations/tickets cannot be reused. Recreation or stale callback without a pending ticket must not auto-export. Lifecycle pause hides stale readiness; resume rechecks authority and bytes.
- Test backend authorization and failure cases, all prior workflows, actual Android system-picker save/cancel, and both themed screens. Inspect actual screenshots for missing/ready/success/cancel/blocked/error states.
- Freeze source/APK/AAB and evidence hashes, independent critic strictly >9/10 with every mandatory check passed; target 10/10.

Limits: exact approved cards pass through unchanged even if their historical size exceeds the new-candidate limit. No generated candidate export, authority promotion, automatic sharing, broad storage permissions, or real commissioning. Durable candidate lifecycle remains later work.

Android references: https://developer.android.com/training/data-storage/shared/documents-files and https://developer.android.com/reference/android/content/ContentResolver . ACTION_CREATE_DOCUMENT creates a new document; output uses explicit truncation and verifies readback because provider semantics vary.

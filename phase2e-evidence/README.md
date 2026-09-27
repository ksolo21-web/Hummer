# Phase 2E — exact candidate PDF Preview

PASSED at version 0.2.5 (30), target SDK37. [Successful CI run](https://github.com/ksolo21-web/Hummer/actions/runs/36288560989), source `22d9e12a88f4147268b3ca4903c4d5b5c574f47f`.

The app opens the current coordinator-bound front or two-page PDF, verifies its identity and SHA-256, and renders those exact bytes with Android PdfRenderer. Page selection, zoom/pan/fit and resume revalidation are implemented. A changed or missing source, prepared input, inventory or artifact blocks preview and removes the old page. PDF colors remain unchanged by UI theme. Native resources close and temporary copies are deleted after rendering.

All 25 connected tests passed, including six new preview tests. Direct native render pixel parity against stored PDFs passes in both modes; front pixels are preserved in the assembled packet. The dedicated capture test passed. Eight actual display screenshots and two synthetic two-page PDFs (each below300KB) were independently inspected. Critic score: 10/10. All2296 archived file hashes and APK/AAB hashes verified. Frozen core/assets/Page2 service/source intake unchanged.

Synthetic 998/999 inputs are test-only and not field-approved. Production workspaces still require prepared verified inputs from the later commissioning pipeline. Preview does not approve or export anything; no real territory was commissioned. The six reserved territories remain untouched.

The full source/APK/AAB archive is in artifact10921617832, expiring2026-10-11. This repository retains the workflow/reconstruction overlays, compact reports, hashes, screenshots and synthetic PDF evidence for resumption. See requirements.md, closeout.json and independent-review.md.

Next bounded task: candidate review and approval UI, using existing artifact/validation contracts and explicit exact-artifact approval gates. Real commissioning and release remain separate later gates.

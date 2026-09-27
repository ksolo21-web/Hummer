# Phase 2I repair history

- Run 36299262252: existing Phase 2E Compose setup race; stabilized test content setup without removing assertions.
- Run 36299738121: all 52 tests ran; two Phase 2I selectors were made exact (scroll A289 before click; assert child semantics directly).
- Run 36300307965: all 52 tests and capture methods passed; artifact pull failed because emulator-runner executes script lines independently. Replaced shell loop with explicit adb pulls.
- Run 36300810227: 51/52 passed; an existing Phase 2F tagged button was intermittently only in the unmerged Compose tree. Click helper now targets that exact tagged node; assertions unchanged.
- Run 36301484325: 52 tests and eight captures passed. Freeze found a stale 0.2.8 archive-name reference; independent review also rejected phone screenshots obstructed by IME.
- Run 36302122636: cancelled by concurrency guard before replacement; no overlap with final run.
- Run 36302386950: PASS. Correct 0.2.9 archive hash, unobstructed full screening records, 52/52 tests, eight captures, complete freeze and artifact upload.

No production rule, approval, export, renderer, canonical packet, or authority assertion was weakened.

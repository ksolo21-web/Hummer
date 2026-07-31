#!/usr/bin/env bash
set -euo pipefail

# Run the complete pinned verifier through the narrowly scoped safe-scroll
# adapter. All crash, exact-target, official-app, and return-state assertions
# remain in the pinned verifier.
exec bash .msc-build/run-installed-phone-jw-safe-scroll.sh "$@"

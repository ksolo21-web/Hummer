#!/usr/bin/env bash
set -euo pipefail

# Run the complete pinned verifier through the narrowly scoped Bible Journey
# return adapter. All official-app, exact-target, crash, and state assertions
# remain in the pinned verifier.
exec bash .msc-build/run-installed-phone-jw-core-return.sh "$@"

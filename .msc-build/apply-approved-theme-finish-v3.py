#!/usr/bin/env python3
from __future__ import annotations

import subprocess

# The exact approved-theme finisher at the previous immutable branch head is
# correct except that its legacy integration child shares the finisher process
# group. That child can terminate the parent before the decoder-free clean
# renderer rewrites the manifest. Load the exact source, isolate that one child,
# and then execute the corrected finisher without altering theme content.
SOURCE_COMMIT = "3c3536a7f91e51874b47850c0db306b61b33fa15"
SOURCE_PATH = ".msc-build/apply-approved-theme-finish-v3.py"

subprocess.run(
    ["git", "fetch", "--no-tags", "--depth=1", "origin", SOURCE_COMMIT],
    check=True,
    stdout=subprocess.DEVNULL,
)
source = subprocess.run(
    ["git", "show", f"{SOURCE_COMMIT}:{SOURCE_PATH}"],
    check=True,
    stdout=subprocess.PIPE,
    text=True,
).stdout

old = "integration_result = subprocess.run([sys.executable, str(integration_script)])"
new = (
    "integration_result = subprocess.run("
    "[sys.executable, str(integration_script)], start_new_session=True)"
)
if source.count(old) != 1:
    raise SystemExit("Unable to isolate the legacy approved-theme integration child.")
source = source.replace(old, new, 1)

# Execute as the real finisher so all 13 approved static themes, the manifest,
# Android/Wear/PWA assets, and independent visual checks remain authoritative.
exec(compile(source, SOURCE_PATH, "exec"), globals(), globals())

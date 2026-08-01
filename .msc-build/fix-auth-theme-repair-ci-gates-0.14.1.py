#!/usr/bin/env python3
from pathlib import Path

current_marker = 'msc-web-v0144-auth-theme-repair'
legacy_markers = (
    'msc-web-v0140-interactive-workbooks',
    'msc-web-v0141-unified-study-reader',
    'msc-web-v0142-complete-reader',
    'msc-web-v0143-theme-gallery',
)

# Reconstruction can restore an older service-worker gate in the shared build
# runner. Normalize that runner here; the dedicated unified-reader gate script
# then appends and validates the complete auth/theme release checks. Do not
# rewrite the validator itself—doing so made the previous gate order-dependent.
runner = Path('.msc-build/run-interactive-workbooks-0.14.0-ci.sh')
source = runner.read_text(encoding='utf-8')
for marker in legacy_markers:
    source = source.replace(marker, current_marker)
runner.write_text(source, encoding='utf-8')

for marker in legacy_markers:
    if marker in source:
        raise SystemExit(f'Stale service-worker marker remains in build runner: {marker}')

print('Normalized reconstructed build-runner service-worker identity for the 0.14.1 Google-auth and approved-theme repair.')

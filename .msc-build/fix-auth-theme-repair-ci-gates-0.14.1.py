#!/usr/bin/env python3
from pathlib import Path

current_marker = 'msc-web-v0144-auth-theme-repair'
# Keep this split so the preceding generic overlay cannot rewrite the validator's
# intentionally obsolete marker before this final narrow repair runs.
legacy_stale_marker = 'msc-web-v0141-' + 'unified-study-reader'

# The generic repair intentionally updates the runnable CI script to the new
# service-worker marker. Keep the validator's stale-marker list pointed at the
# genuinely obsolete version instead of accidentally rejecting the repair.
validator = Path('.msc-build/fix-unified-study-reader-ci-gate-0.14.1.py')
source = validator.read_text(encoding='utf-8')
if current_marker in source:
    source = source.replace(current_marker, legacy_stale_marker)
validator.write_text(source, encoding='utf-8')

runner = Path('.msc-build/run-interactive-workbooks-0.14.0-ci.sh')
source = runner.read_text(encoding='utf-8')
source = source.replace(legacy_stale_marker, current_marker)
source = source.replace('msc-web-v0143-theme-gallery', current_marker)
runner.write_text(source, encoding='utf-8')

print('Corrected the repaired service-worker CI marker without weakening stale-build detection.')

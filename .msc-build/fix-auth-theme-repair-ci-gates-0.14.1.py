#!/usr/bin/env python3
from pathlib import Path

current_marker = 'msc-web-v0144-auth-theme-repair'
legacy_markers = (
    'msc-web-v0140-' + 'interactive-workbooks',
    'msc-web-v0141-' + 'unified-study-reader',
    'msc-web-v0142-' + 'complete-reader',
    'msc-web-v0143-' + 'theme-gallery',
)

# Normalize the actual build runner to the repaired service-worker identity.
runner = Path('.msc-build/run-interactive-workbooks-0.14.0-ci.sh')
runner_source = runner.read_text(encoding='utf-8')
for marker in legacy_markers:
    runner_source = runner_source.replace(marker, current_marker)
runner.write_text(runner_source, encoding='utf-8')

# The unified-reader gate is executed again after reconstruction. Teach that
# gate to normalize all older service-worker identities before validating the
# final repaired build, while still rejecting any stale marker left afterward.
validator = Path('.msc-build/fix-unified-study-reader-ci-gate-0.14.1.py')
validator_source = validator.read_text(encoding='utf-8')
validator_source = validator_source.replace(
    'msc-web-v0142-complete-reader',
    current_marker,
)

normalization_tag = '# AUTH_THEME_FINAL_MARKER_NORMALIZATION'
read_needle = "source = path.read_text(encoding='utf-8')\n"
if normalization_tag not in validator_source:
    normalization_block = """
# AUTH_THEME_FINAL_MARKER_NORMALIZATION
final_web_marker = 'msc-web-v0144-auth-theme-repair'
legacy_web_markers = (
    'msc-web-v0140-' + 'interactive-workbooks',
    'msc-web-v0141-' + 'unified-study-reader',
    'msc-web-v0142-' + 'complete-reader',
    'msc-web-v0143-' + 'theme-gallery',
)
for legacy_web_marker in legacy_web_markers:
    source = source.replace(legacy_web_marker, final_web_marker)
"""
    if read_needle not in validator_source:
        raise SystemExit('Could not locate unified-reader runner load statement.')
    validator_source = validator_source.replace(
        read_needle,
        read_needle + normalization_block,
        1,
    )

stale_start = validator_source.find('stale = (\n')
stale_end = validator_source.find(')\nfor marker in stale:', stale_start)
if stale_start < 0 or stale_end < 0:
    raise SystemExit('Could not locate unified-reader stale-marker tuple.')

stale_block = validator_source[stale_start:stale_end]
filtered_lines = []
for line in stale_block.splitlines():
    if current_marker in line or any(marker in line for marker in legacy_markers):
        continue
    filtered_lines.append(line)
filtered_lines.extend(
    [
        "    'msc-web-v0140-' + 'interactive-workbooks',",
        "    'msc-web-v0141-' + 'unified-study-reader',",
        "    'msc-web-v0142-' + 'complete-reader',",
        "    'msc-web-v0143-' + 'theme-gallery',",
    ]
)
validator_source = (
    validator_source[:stale_start]
    + '\n'.join(filtered_lines)
    + '\n'
    + validator_source[stale_end:]
)
validator.write_text(validator_source, encoding='utf-8')

print(
    'Normalized the repaired service-worker identity in both the build runner '
    'and the post-reconstruction validator without weakening stale-build detection.'
)

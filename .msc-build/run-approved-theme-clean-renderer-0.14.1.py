#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
from pathlib import Path

ROOT = Path('.')
MANIFEST = ROOT / '.msc-build/approved-theme-finish-v2-manifest.json'
TARGETS = (
    ROOT / 'MyStudyCompanion/app/src/main/res/drawable-nodpi',
    ROOT / 'MyStudyCompanion/wear/src/main/res/drawable-nodpi',
    ROOT / 'MyStudyCompanionWeb/assets',
)

if not MANIFEST.is_file():
    raise SystemExit('The deterministic theme finisher did not write its manifest.')
manifest = json.loads(MANIFEST.read_text(encoding='utf-8'))
themes = manifest.get('themes', {})
if len(themes) != 13:
    raise SystemExit(f'Expected 13 rebuilt approved themes, found {len(themes)}.')

for slug, entry in themes.items():
    if entry.get('source_mode') != 'approved_static_archive':
        raise SystemExit(f'Unexpected theme source mode for {slug}: {entry.get("source_mode")}')
    for kind, key in (('scene', 'scene_sha256'), ('preview', 'preview_sha256')):
        expected = entry.get(key)
        copies = tuple(root / f'theme_{kind}_{slug}.webp' for root in TARGETS)
        if not all(path.is_file() and path.stat().st_size > 20_000 for path in copies):
            raise SystemExit(f'Missing or undersized {kind} asset for {slug}.')
        actual = {hashlib.sha256(path.read_bytes()).hexdigest() for path in copies}
        if actual != {expected}:
            raise SystemExit(f'Cross-surface {kind} mismatch for {slug}: {actual}')

print('PASS: deterministic theme finalizer output verified; no second renderer was run.')

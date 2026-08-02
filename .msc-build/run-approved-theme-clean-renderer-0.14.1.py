#!/usr/bin/env python3
from __future__ import annotations

import base64
import hashlib
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

finisher_path = Path(os.environ['MSC_THEME_FINISHER_V3'])
source = finisher_path.read_text(encoding='utf-8')
marker = "SPRITE_JPEG = Path('/tmp/msc-approved-static-theme-sprite-v6.jpg')"
position = source.find(marker)
if position < 0:
    raise SystemExit('Unable to locate the clean approved-theme renderer in the exact finisher.')
renderer = source[position:]
exec(compile(renderer, str(finisher_path), 'exec'), globals(), globals())

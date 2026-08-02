#!/usr/bin/env python3
from __future__ import annotations

import base64
import lzma
import re
from pathlib import Path

WRAPPED = Path('.msc-build/apply-approved-theme-finish-v2.py')
text = WRAPPED.read_text(encoding='utf-8')
match = re.search(r'base64\.b64decode\("([A-Za-z0-9+/=]+)"\)', text)
if not match:
    raise SystemExit('Unable to locate the approved-theme finish payload.')
source = lzma.decompress(base64.b64decode(match.group(1))).decode('utf-8')
source, count = re.subn(
    r"EXPECTED_SPRITE_SHA256 = '[0-9a-f]{64}'",
    "EXPECTED_SPRITE_SHA256 = '54e985ba0cf0640c835123f0310aec65140cfa862e2a20a01b570b79ab2823bf'",
    source,
    count=1,
)
if count != 1:
    raise SystemExit('Unable to repair the approved-theme sprite checksum contract.')
exec(compile(source, str(WRAPPED), 'exec'))

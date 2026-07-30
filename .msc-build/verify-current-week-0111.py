#!/usr/bin/env python3
import html
import re
import sys
from pathlib import Path

path = Path(sys.argv[1])
raw = path.read_text(encoding='utf-8', errors='replace')
text = html.unescape(re.sub(r'<[^>]+>', ' ', raw))
for dash in ('\u2010', '\u2011', '\u2012', '\u2013', '\u2014', '\u2212'):
    text = text.replace(dash, '-')
text = ' '.join(text.replace('\u00a0', ' ').split())
assert '202026244' in raw
assert re.search(r'Jeremiah\s*20\s*-\s*21', text, re.I), text[:500]
assert re.search(r'Jer(?:emiah)?\s*20:7\s*-\s*18', text, re.I), text[:500]
print('Verified active week document 202026244, Jeremiah 20-21, and Jeremiah 20:7-18.')

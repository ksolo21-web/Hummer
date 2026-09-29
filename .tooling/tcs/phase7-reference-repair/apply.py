#!/usr/bin/env python3
"""Hash-locked follow-on repair; preserves the original 01-09 integration chain."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys

if len(sys.argv) != 2:
    raise SystemExit('Usage: apply.py <fully integrated source directory>')
target = Path(sys.argv[1]).resolve(strict=True)
here = Path(__file__).resolve().parent
def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()
rows = [
    ('NativeReferenceComparison.kt', 'core/src/main/kotlin/com/koenterprises/territorycardstudio/core/NativeReferenceComparison.kt', 'adad42a580b299538b565315c5397e540d571d422d333cf7d2529db0507c2771'),
    ('NativeReferenceComparisonTest.kt', 'core/src/test/kotlin/com/koenterprises/territorycardstudio/core/NativeReferenceComparisonTest.kt', '2b2a18b5fabf6347bfd722735d7a58f313e74170ca7cf63abd45bfd529404e1b'),
    ('Phase7ReferenceComparisonInstrumentationTest.kt', 'app/src/androidTest/java/com/koenterprises/territorycardstudio/Phase7ReferenceComparisonInstrumentationTest.kt', 'd639b0c44632b7c3f5280d5a9f46a7370ff187c6b4021644cd94baf0868a9188')
]
ui = target / 'app/src/main/java/com/koenterprises/territorycardstudio/NativeAuthoringUi.kt'
assert sha(ui) == '28e1591157b10712f54a588bc6296633b32f277b7baaf18ec04299cf29b9795e', 'Native UI baseline changed'
patch = here / 'native-ui.patch'
assert sha(patch) == '1720d439c3104b5a48f77659db35e0e7c3b508f8f551a54b7b7ff9bedc3946e1', 'Native UI patch changed'
for source, name, expected in rows:
    assert sha(here/source) == expected, source
    assert not (target/name).exists(), name
command = ['git', 'apply', '--whitespace=error', '--directory='+target.name]
subprocess.run(command+['--check', str(patch)], cwd=target.parent, check=True)
subprocess.run(command+[str(patch)], cwd=target.parent, check=True)
for source, name, expected in rows:
    path = target/name
    path.write_bytes((here/source).read_bytes())
    assert sha(path) == expected, name
assert sha(ui) == 'db5be2020c5c3ad680e46427ec7d02bd249e3b5659032f26570c007c684e8095', 'Native UI output changed'
print(json.dumps({'filesVerified':4, 'phase7Complete':False, 'approvedCards':0, 'scope':'unconfirmed reference comparison repair'}, indent=2))

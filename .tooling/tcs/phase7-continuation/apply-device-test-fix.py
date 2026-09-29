#!/usr/bin/env python3
import hashlib, subprocess, sys
from pathlib import Path
if len(sys.argv)!=2: raise SystemExit("Usage: apply-device-test-fix.py <integrated-source>")
target=Path(sys.argv[1]).resolve(strict=True)
file=target/"app/src/androidTest/java/com/koenterprises/territorycardstudio/Phase7AncillaryUiInstrumentationTest.kt"
here=Path(__file__).resolve().parent
patch=here/"device-test-time.patch"
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(file)=="62498998ad4b270dbec737da54f5f4c90bde319f56453669e1033acafac5a2e5", "Ancillary test baseline changed"
assert sha(patch)=="b4b6d4dd8cfc49f8e2427a1eff283f1d4c79c5b456940411154a564ec669d88f", "Ancillary test repair changed"
cmd=["git","apply","--whitespace=error","--directory="+target.name]
subprocess.run(cmd+["--check",str(patch)],cwd=target.parent,check=True)
subprocess.run(cmd+[str(patch)],cwd=target.parent,check=True)
assert sha(file)=="fee46e01e30118f346d748b1280bcf063de7a4e646c5bda89ac2d6a69639b3df", "Ancillary test repair output changed"
print("Phase 7 ancillary test timestamp fixture repaired")

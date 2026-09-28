from pathlib import Path
import hashlib,json,tarfile,sys
# Usage: python verify-preservation.py EVIDENCE_DIR EXTRACT_DIR ACCEPTED_PHASE3CC_SOURCE_DIR
if len(sys.argv)!=4:raise SystemExit('Expected evidence, extraction, and accepted Phase3CC source directories')
e,s,old=map(Path,sys.argv[1:4])
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
s.mkdir(exist_ok=True)
with tarfile.open(e/'phase3d-source.tar.xz') as t:t.extractall(s,filter='data')
unchanged=[]; added=[]
for subtree in ['app/src','core/src']:
 for p in (old/subtree).rglob('*'):
  if p.is_file():
   rel=p.relative_to(old);assert (s/rel).is_file() and sha(p)==sha(s/rel),str(rel);unchanged.append(str(rel))
 for p in (s/subtree).rglob('*'):
  if p.is_file() and not (old/p.relative_to(s)).exists():added.append(str(p.relative_to(s)))
assert len(added)==2 and all('Phase3DIntegration' in x for x in added),added
for rel,expected in [('app/build/outputs/apk/debug/app-debug.apk','70d635ada9d2b1a9ca9a4ae205565e71c019830145aa7411b36ff3244bfce641'),('app/build/outputs/bundle/debug/app-debug.aab','6d6b03170e2d251cd6dc89b724ee5383eb4bebf4de3a4a30a81aeff7385fd58c')]:assert sha(s/rel)==expected,rel
manifest=0
for line in (e/'source-files.sha256').read_text().splitlines():
 h,rel=line.split('  ',1);assert sha(s/rel)==h,rel;manifest+=1
report=dict(unchanged_source_files=len(unchanged),added_test_files=added,manifest_files=manifest,apk_aab_unchanged=True,source_package_sha256=sha(e/'phase3d-source.tar.xz'))
(e/'independent-preservation-verification.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))

import zipfile
with zipfile.ZipFile(s/'app/build/outputs/apk/debug/app-debug.apk') as za, zipfile.ZipFile(e/'emulator-app.apk') as zb:
 def entries(z):
  assert len(z.namelist())==len(set(z.namelist())),"Duplicate ZIP entries"
  return {n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if not (n.upper()=='META-INF/MANIFEST.MF' or (n.startswith('META-INF/') and n.upper().endswith(('.SF','.RSA','.DSA','.EC'))))}
 a,b=entries(za),entries(zb);assert a==b,'Non-signing APK entry changed'
 report['exact_non_signing_apk_entries']=len(a)
 report['emulator_apk_sha256']=sha(e/'emulator-app.apk')
(e/'independent-preservation-verification.json').write_text(json.dumps(report,indent=2)+'\n')

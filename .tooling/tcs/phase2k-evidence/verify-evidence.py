from pathlib import Path
import hashlib, json, re, tarfile, zipfile
import xml.etree.ElementTree as ET
from PIL import Image

base=Path('verified-source');ev=Path('phase2k-evidence');src=Path('phase2k-source')
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
meta=json.loads(Path('phase2k-artifact-meta.json').read_text())
assert meta['run_conclusion']=='success'
expected=lambda name:(ev/name).read_text().split()[0]
archive=ev/'TerritoryCardStudio-Android-0.2.11-phase2k-source.tar.xz'
assert sha(archive)==expected('source-package.sha256')
src.mkdir(exist_ok=True)
with tarfile.open(archive) as t:t.extractall(src,filter='data')
assert sha(ev/'extracted-source-files.sha256')==expected('extracted-source-manifest.sha256')
count=0
for line in (ev/'extracted-source-files.sha256').read_text().splitlines():
 h,name=line.split('  ',1);assert sha(src/name)==h,name;count+=1
frozen=[]
for folder,pattern in [('core/src','*.kt'),('app/src/main/assets','*')]:
 for p in (base/folder).rglob(pattern):
  if p.is_file():assert sha(p)==sha(src/p.relative_to(base)),p;frozen.append(str(p.relative_to(base)))
assert sha(base/'core/build.gradle.kts')==sha(src/'core/build.gradle.kts')
prior=list((base/'app/src/androidTest').rglob('*.kt'))
test_sync_overlays={'Phase2FReviewUiInstrumentationTest.kt'}
for p in prior:
 if p.name not in test_sync_overlays:assert sha(p)==sha(src/p.relative_to(base)),p
allowed={p.name for p in Path('repo/.tooling/tcs/phase2k-overlay').glob('*.kt') if p.name not in {'Phase2KInstrumentationTest.kt','Phase2KUiInstrumentationTest.kt'}}
changed=[]
for p in (base/'app/src/main/java').rglob('*.kt'):
 if sha(p)!=sha(src/p.relative_to(base)):changed.append(p.name)
assert set(changed)<=allowed,(changed,allowed)
for p in Path('repo/.tooling/tcs/phase2k-overlay').glob('*.kt'):
 kind='androidTest' if p.name.endswith('InstrumentationTest.kt') else 'main'
 target=src/f'app/src/{kind}/java/com/koenterprises/territorycardstudio'/p.name
 assert sha(p)==sha(target),p
apk=src/'app/build/outputs/apk/debug/app-debug.apk';aab=src/'app/build/outputs/bundle/debug/app-debug.aab'
assert sha(apk)==expected('apk.sha256') and sha(aab)==expected('aab.sha256')
for p in [apk,aab]:
 with zipfile.ZipFile(p) as z:assert z.testzip() is None
badging=(ev/'apk-badging.txt').read_text()
assert "versionCode='36'" in badging and "versionName='0.2.11'" in badging and "targetSdkVersion:'37'" in badging
assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in (ev/'apk-signature.txt').read_text()
assert 'jar verified.' in (ev/'aab-jar-verify.txt').read_text()
cases=[c for p in (ev/'android-test-results').glob('TEST*.xml') for c in ET.parse(p).getroot().iter('testcase')]
assert len(cases)==67
assert len({(c.get("classname"),c.get("name")) for c in cases})==67
assert all(c.find('failure') is None and c.find('error') is None and c.find('skipped') is None for c in cases)
for log in ['core-test.log','app-compile.log','package-build.log']:
 assert 'BUILD SUCCESSFUL' in (ev/log).read_text(),log
for log in ['visual-phone-light.log','visual-phone-dark.log','visual-wide-light.log','visual-wide-dark.log','retry-captureFindingsAndAuditAndNavigateFromPreviewAcrossThemes.log','retry-captureFindingsAndAuditAndNavigateFromPreviewDark.log']:
 assert 'OK (1 test)' in (ev/log).read_text(),log
for log in (ev).glob('targeted-*.log'):
 assert 'OK (' in log.read_text(),log
assert len(list(ev.glob('targeted-*.log')))==5
shots=sorted((ev/'ui').glob('*.png'))
assert len(shots)==len({sha(p) for p in shots})==12
for p in shots:
 with Image.open(p) as im:assert im.size==((1920,1200) if 'wide-' in p.name else (1080,2400)),p
reserved=[str(p) for p in src.rglob('*.pdf') if re.search(r'(?:Territory\s*-\s*|^)(?:250T|T250|257A|A257|297|298A|A298|299|347TA|TA347)(?:\D|$)',p.name)]
assert not reserved,reserved
report=dict(status='MECHANICAL_PASS_PENDING_INDEPENDENT_2K_AND_AGGREGATE_AUDIT',run_id=meta['run_id'],source_commit=meta['run_head_sha'],version='0.2.11',code=36,connected_tests=len(cases),frozen_runtime_provenance={"original62_run":36323755251,"theme2_run":36328681508,"retained_unaffected":59,"reopened_cases":5,"new_regressions":3,"current_targeted":8},prior_visual_run=36310455841,prior_visual_count=20,failures=0,skipped=0,manifest_files=count,frozen_core_assets=len(frozen)+1,prior_tests=len(prior),app_changes=changed,source_sha256=sha(archive),apk_sha256=sha(apk),aab_sha256=sha(aab),screenshots={p.name:sha(p) for p in shots},reserved_pdf_sweep=0)
Path('phase2k-verification.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))

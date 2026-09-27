from pathlib import Path
import hashlib,json,tarfile,zipfile,xml.etree.ElementTree as E
base=Path('phase2k-source');src=Path('phase3a-source');ev=Path('phase3a-evidence')
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
m=json.loads(Path('phase3a-artifact-meta.json').read_text());assert m['run_conclusion']=='success'
archive=ev/'TerritoryCardStudio-Android-0.3.0-phase3a-source.tar.xz'
assert sha(archive)==(ev/'source-package.sha256').read_text().split()[0]
src.mkdir(exist_ok=True)
with tarfile.open(archive) as t:t.extractall(src,filter='data')
assert sha(ev/'extracted-source-files.sha256')==(ev/'extracted-source-manifest.sha256').read_text().split()[0]
manifest=0
for line in (ev/'extracted-source-files.sha256').read_text().splitlines():
 h,n=line.split('  ',1);assert sha(src/n)==h,n;manifest+=1
frozen=[]
for folder in ['core/src','app/src/main/assets','app/src/main/res','app/src/androidTest']:
 for p in (base/folder).rglob('*'):
  if p.is_file():assert sha(p)==sha(src/p.relative_to(base)),p;frozen.append(str(p.relative_to(base)))
for name in ['app/src/main/AndroidManifest.xml','core/build.gradle.kts']:
 assert sha(base/name)==sha(src/name);frozen.append(name)
app='app/src/main/java/com/koenterprises/territorycardstudio'
for p in (base/app).glob('*.kt'):
 if p.name!='TerritoryCardStudioServices.kt':assert sha(p)==sha(src/app/p.name),p;frozen.append(str(p.relative_to(base)))
assert {p.name for p in (src/app).glob('*.kt')}-{p.name for p in (base/app).glob('*.kt')}=={'AndroidEditingDraftStore.kt'}
old=(base/app/'TerritoryCardStudioServices.kt').read_text();new=(src/app/'TerritoryCardStudioServices.kt').read_text()
addition='    val editingDrafts: AndroidEditingDraftStore by lazy {\n        AndroidEditingDraftStore(File(privateRoot, "editing-drafts-v1"), knowledgeBase, SourceMapIntakeStore(appContext))\n    }\n\n'
assert new.replace(addition,'')==old
for p in Path('repo/.tooling/tcs/phase3a-overlay').glob('*.kt'):
 folder='app/src/androidTest/java/com/koenterprises/territorycardstudio' if p.name.endswith('InstrumentationTest.kt') else app
 assert sha(p)==sha(src/folder/p.name),p
cases=list(E.parse(ev/'TEST-phase3a.xml').getroot().iter('testcase'))
assert len(cases)==len({(c.get('classname'),c.get('name')) for c in cases})==10
assert all(c.find('failure') is None and c.find('error') is None and c.find('skipped') is None for c in cases)
assert 'OK (10 tests)' in (ev/'phase3a-runtime.log').read_text()
for n in ['app-compile.log','package-build.log','android-test-package.log']:assert 'BUILD SUCCESSFUL' in (ev/n).read_text()
packages={}
for ext,name in [('apk','app/build/outputs/apk/debug/app-debug.apk'),('aab','app/build/outputs/bundle/debug/app-debug.aab')]:
 p=src/name;assert sha(p)==(ev/(ext+'.sha256')).read_text().split()[0]
 with zipfile.ZipFile(p) as z:assert z.testzip() is None
 packages[ext+'_sha256']=sha(p)
assert "versionCode='37'" in (ev/'apk-badging.txt').read_text() and "versionName='0.3.0'" in (ev/'apk-badging.txt').read_text()
assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in (ev/'apk-signature.txt').read_text()
assert 'jar verified.' in (ev/'aab-jar-verify.txt').read_text()
assert (ev/'reserved-territory-pdf-sweep.txt').read_text()==''
r=dict(status='MECHANICAL_PASS_PENDING_INDEPENDENT_REVIEW',run_id=m['run_id'],source_commit=m['run_head_sha'],version='0.3.0',code=37,new_phase3a_tests=10,frozen_phase2_tests=67,frozen_phase2_run=36330985449,combined_distinct_identities=77,full_prior_suite_rerun=False,failures=0,manifest_files=manifest,frozen_files=len(frozen),source_sha256=sha(archive),**packages,ui_changes=0,reserved_territories='UNTOUCHED',phase3='IN_PROGRESS',next='3B_editor_ui')
Path('phase3a-verification.json').write_text(json.dumps(r,indent=2)+'\n');print(json.dumps(r,indent=2))

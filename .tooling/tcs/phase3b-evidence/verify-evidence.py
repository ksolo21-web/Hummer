from pathlib import Path
import hashlib,json,tarfile,zipfile,xml.etree.ElementTree as E
base=Path('phase3a-source');src=Path('phase3b-source');ev=Path('phase3b-evidence')
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
m=json.loads(Path('phase3b-artifact-meta.json').read_text());assert m['run_conclusion']=='success'
archive=ev/'TerritoryCardStudio-Android-0.3.1-phase3b-source.tar.xz'
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
 if p.name!='WorkspaceModeUi.kt':assert sha(p)==sha(src/app/p.name),p;frozen.append(str(p.relative_to(base)))
assert {p.name for p in (src/app).glob('*.kt')}-{p.name for p in (base/app).glob('*.kt')}=={'DraftEditorUi.kt'}
old=(base/app/'WorkspaceModeUi.kt').read_text();new=(src/app/'WorkspaceModeUi.kt').read_text()
addition1=',\n    editingDraftStore: AndroidEditingDraftStore? = null'
addition2='    if (workflowScreen == "DRAFT_EDITOR") {\n        DraftEditorScreen(modifier, assignment, mode, editingDraftStore ?: application.services.editingDrafts, sourceStore) { workflowScreen = "WORKSPACE" }\n        return\n    }\n'
addition3='        item {\n            OutlinedButton(onClick = { workflowScreen = "DRAFT_EDITOR" }, modifier = Modifier.fillMaxWidth().testTag("workspace-draft-editor")) { Text("Draft label corrections") }\n        }\n\n'
assert new.replace(addition1,'').replace(addition2,'').replace(addition3,'')==old
for p in Path('repo/.tooling/tcs/phase3b-overlay').glob('*.kt'):
 folder='app/src/androidTest/java/com/koenterprises/territorycardstudio' if p.name.endswith('InstrumentationTest.kt') else app
 assert sha(p)==sha(src/folder/p.name),p
cases=list(E.parse(ev/'TEST-phase3b.xml').getroot().iter('testcase'))
assert len(cases)==len({(c.get('classname'),c.get('name')) for c in cases})==10
assert all(c.find('failure') is None and c.find('error') is None and c.find('skipped') is None for c in cases)
assert 'OK (2 tests)' in (ev/'phase3b-runtime.log').read_text()
results=json.loads((ev/'individual-results.json').read_text())
prior=json.loads(Path('repo/.tooling/tcs/phase3b-evidence/attempt1-results.json').read_text())
second=json.loads(Path('repo/.tooling/tcs/phase3b-evidence/attempt2-results.json').read_text())
assert len(results['retained'])==8 and results['retained']==prior['retained']+second['passed']
assert set(map(tuple,results['retained'])).issubset(set(map(tuple,prior['passed']+second['passed'])))
assert set(map(tuple,results['retained'])).isdisjoint(set(map(tuple,results['passed'])))
assert not results['failed'] and len(results['passed'])==2
with tarfile.open('phase3b-attempt1/TerritoryCardStudio-Android-0.3.1-phase3b-source.tar.xz') as oldarchive:
 oldtest=oldarchive.extractfile('./app/src/androidTest/java/com/koenterprises/territorycardstudio/Phase3BEditorUiInstrumentationTest.kt').read().decode()
newtest=(src/'app/src/androidTest/java/com/koenterprises/territorycardstudio/Phase3BEditorUiInstrumentationTest.kt').read_text()
def method_body(text,name):
 start=text.index('@Test fun '+name);end=text.find('\n    @Test ',start+1)
 return text[start:end if end!=-1 else len(text)].split('\n    private fun shot')[0]
for _,name in results['retained']:assert method_body(oldtest,name)==method_body(newtest,name),name

oldlog=Path('repo/.tooling/tcs/phase3b-evidence/attempt1-runtime.log').read_text()
assert 'Tests run: 10,  Failures: 2' in oldlog
assert 'Tests run: 3,  Failures: 2' in Path('repo/.tooling/tcs/phase3b-evidence/attempt2-runtime.log').read_text()
def actual_passes(text):
 passed=set();cls=name=None
 for line in text.splitlines():
  if line.startswith('INSTRUMENTATION_STATUS: class='):cls=line.split('=',1)[1]
  if line.startswith('INSTRUMENTATION_STATUS: test='):name=line.split('=',1)[1]
  if line=='INSTRUMENTATION_STATUS_CODE: 0':
   assert cls and name;passed.add((cls,name));cls=name=None
 return passed
assert set(map(tuple,prior['retained'])).issubset(actual_passes(oldlog))
assert set(map(tuple,second['passed']))==actual_passes(Path('repo/.tooling/tcs/phase3b-evidence/attempt2-runtime.log').read_text())
assert set(map(tuple,results['passed']))==actual_passes((ev/'phase3b-runtime.log').read_text())


for n in ['app-compile.log','package-build.log','android-test-package.log']:assert 'BUILD SUCCESSFUL' in (ev/n).read_text()
packages={}
for ext,name in [('apk','app/build/outputs/apk/debug/app-debug.apk'),('aab','app/build/outputs/bundle/debug/app-debug.aab')]:
 p=src/name;assert sha(p)==(ev/(ext+'.sha256')).read_text().split()[0]
 with zipfile.ZipFile(p) as z:assert z.testzip() is None
 packages[ext+'_sha256']=sha(p)
assert "versionCode='38'" in (ev/'apk-badging.txt').read_text() and "versionName='0.3.1'" in (ev/'apk-badging.txt').read_text()
assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in (ev/'apk-signature.txt').read_text()
assert 'jar verified.' in (ev/'aab-jar-verify.txt').read_text()
assert (ev/'reserved-territory-pdf-sweep.txt').read_text()==''
from PIL import Image,ImageStat
images=json.loads((ev/'screenshot-manifest.json').read_text())
assert len(images)==14
for row in images:
 p=ev/row['file'];assert sha(p)==row['sha256']
 im=Image.open(p).convert('RGB');assert im.size==(row['width'],row['height']);assert max(ImageStat.Stat(im).stddev)>5
assert 'OK (2 tests)' in (ev/'phase3b-wide-captures.log').read_text()
r=dict(status='MECHANICAL_PASS_PENDING_INDEPENDENT_REVIEW',run_id=m['run_id'],source_commit=m['run_head_sha'],version='0.3.1',code=38,new_phase3b_tests=10,frozen_prior_tests=77,frozen_phase3a_run=36334431100,combined_distinct_identities=87,full_prior_suite_rerun=False,failures=0,retained_phase3b_tests=8,retained_method_bodies_verified=8,retried_capture_cases=2,retained_phase3b_runs=[36337841251,36338690162],manifest_files=manifest,frozen_files=len(frozen),source_sha256=sha(archive),**packages,screenshots=images,reserved_territories='UNTOUCHED',phase3='IN_PROGRESS',next='3C_validated_preparation_bridge')
Path('phase3b-verification.json').write_text(json.dumps(r,indent=2)+'\n');print(json.dumps(r,indent=2))

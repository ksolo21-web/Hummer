from pathlib import Path
import hashlib,json,tarfile,zipfile,re,xml.etree.ElementTree as E
from PIL import Image,ImageStat
base=Path('phase3ca-source');src=Path('phase3cb-source');ev=Path('phase3cb-evidence')
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
m=json.loads(Path('phase3cb-artifact-meta.json').read_text());assert m['run_conclusion']=='success'
assert sha(Path('phase3cb-evidence.zip'))==m['artifact_sha256']
archive=ev/'TerritoryCardStudio-Android-0.3.3-phase3cb-source.tar.xz';assert sha(archive)==(ev/'source-package.sha256').read_text().split()[0]
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
 if p.name not in {'TerritoryCardStudioServices.kt','WorkspaceModeUi.kt'}:assert sha(p)==sha(src/app/p.name),p;frozen.append(str(p.relative_to(base)))
assert {p.name for p in (src/app).glob('*.kt')}-{p.name for p in (base/app).glob('*.kt')}=={'ExtendedDraftValues.kt','ExtendedDraftForm.kt','AndroidExtendedDraftStore.kt','ExtendedDraftEditorUi.kt'}
old=(base/app/'TerritoryCardStudioServices.kt').read_text();new=(src/app/'TerritoryCardStudioServices.kt').read_text()
addition='    val extendedDrafts: AndroidExtendedDraftStore by lazy {\n        AndroidExtendedDraftStore(File(privateRoot, "extended-drafts-v1"), knowledgeBase, SourceMapIntakeStore(appContext), buildWorkflow)\n    }\n\n'
assert new.replace(addition,'')==old
old=(base/app/'WorkspaceModeUi.kt').read_text();new=(src/app/'WorkspaceModeUi.kt').read_text()
new=new.replace('editingDraftStore: AndroidEditingDraftStore? = null,\n    extendedDraftStore: AndroidExtendedDraftStore? = null','editingDraftStore: AndroidEditingDraftStore? = null')
new=new.replace('    if (workflowScreen == "EXTENDED_EDITOR") {\n        ExtendedDraftEditorScreen(modifier, assignment.displayId, mode, extendedDraftStore ?: application.services.extendedDrafts) { workflowScreen = "WORKSPACE" }\n        return\n    }\n','')
new=new.replace('            OutlinedButton(onClick = { workflowScreen = "EXTENDED_EDITOR" }, modifier = Modifier.fillMaxWidth().testTag("workspace-extended-editor")) { Text("Propose territory changes") }\n','')
assert new==old
assert (src/'app/build.gradle.kts').read_text().replace('versionCode = 40','versionCode = 39').replace('versionName = "0.3.3"','versionName = "0.3.2"')==(base/'app/build.gradle.kts').read_text()
for p in Path('repo/.tooling/tcs/phase3cb-overlay').glob('*.kt'):
 folder='app/src/androidTest/java/com/koenterprises/territorycardstudio' if p.name.startswith('Phase3CB') else app
 assert sha(p)==sha(src/folder/p.name),p
cases=list(E.parse(ev/'TEST-phase3cb.xml').getroot().iter('testcase'))
assert len(cases)==len({(c.get('classname'),c.get('name')) for c in cases})==16
assert all(c.find('failure') is None and c.find('error') is None and c.find('skipped') is None for c in cases)
proof=json.loads((ev/'retained-results.json').read_text())
assert sha(ev/'retained-runtime.log')==proof['runtime_sha256']
def parse(raw):
 passed=[];failed=[];cls=None;name=None
 for line in raw.splitlines():
  if line.startswith('INSTRUMENTATION_STATUS: class='):cls=line.split('=',1)[1]
  if line.startswith('INSTRUMENTATION_STATUS: test='):name=line.split('=',1)[1]
  if line.startswith('INSTRUMENTATION_STATUS_CODE:'):
   code=int(line.rsplit(' ',1)[1])
   if code<=0 and name:(passed if code==0 else failed).append((cls,name));cls=None;name=None
 return passed,failed
oldpass,oldfail=parse((ev/'retained-runtime.log').read_text())
retained=[tuple(x) for x in proof['retained']];retry=[tuple(x) for x in proof['retry']]
assert sha(ev/'retained-runtime-2.log')==proof['second_run']['runtime_sha256']
secondpass,secondfail=parse((ev/'retained-runtime-2.log').read_text())
assert set(secondpass)=={tuple(x) for x in proof['second_run']['passed']}
assert sha(ev/'retained-runtime-3.log')==proof['third_run']['runtime_sha256']
thirdpass,thirdfail=parse((ev/'retained-runtime-3.log').read_text())
assert set(thirdpass)=={tuple(x) for x in proof['third_run']['passed']}
assert set(retained)=={tuple(x) for x in proof['previous_retained']}|set(secondpass)|set(thirdpass)
assert {tuple(x) for x in proof['previous_retained']}<=set(oldpass)
assert not set(retained)&set(thirdfail)
raw=(ev/'phase3cb-runtime.log').read_text();fresh,failed=parse(raw)
assert 'OK (1 test)' in raw and not failed
assert len(fresh)==len(set(fresh))==1 and set(fresh)==set(retry)
assert len(retained)==len(set(retained))==15 and not set(retained)&set(fresh)
passed=retained+fresh
assert len(passed)==len(set(passed))==16
assert set(passed)=={(c.get('classname'),c.get('name')) for c in cases}
for row in proof['screenshots']:assert sha(ev/row['file'])==row['sha256']
# Retained visuals are valid because UI/form/integration source is byte-identical.
with tarfile.open('phase3cb-attempt1/TerritoryCardStudio-Android-0.3.3-phase3cb-source.tar.xz') as t:
 for name in ['ExtendedDraftEditorUi.kt','ExtendedDraftForm.kt','WorkspaceModeUi.kt','TerritoryCardStudioServices.kt']:
  assert t.extractfile('./'+app+'/'+name).read()==(src/app/name).read_bytes(),name
expected=set()
for p in Path('repo/.tooling/tcs/phase3cb-overlay').glob('*InstrumentationTest.kt'):
 expected.update(('com.koenterprises.territorycardstudio.'+p.stem,n) for n in re.findall(r'@Test fun (\w+)',p.read_text()))
assert set(passed)==expected
assert 'OK (2 tests)' in (ev/'phase3cb-wide-captures.log').read_text()
for n in ['app-compile.log','package-build.log','android-test-package.log']:assert 'BUILD SUCCESSFUL' in (ev/n).read_text()
packages={}
for ext,name in [('apk','app/build/outputs/apk/debug/app-debug.apk'),('aab','app/build/outputs/bundle/debug/app-debug.aab')]:
 p=src/name;assert sha(p)==(ev/(ext+'.sha256')).read_text().split()[0]
 with zipfile.ZipFile(p) as z:assert z.testzip() is None
 packages[ext+'_sha256']=sha(p)
assert "versionCode='40'" in (ev/'apk-badging.txt').read_text() and "versionName='0.3.3'" in (ev/'apk-badging.txt').read_text() and "targetSdkVersion:'37'" in (ev/'apk-badging.txt').read_text()
assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in (ev/'apk-signature.txt').read_text();assert 'jar verified.' in (ev/'aab-jar-verify.txt').read_text()
assert (ev/'reserved-territory-pdf-sweep.txt').read_text()==''
images=json.loads((ev/'screenshot-manifest.json').read_text());assert len(images)==34
expected_images={f'phase3cb-{wide}{kind}-{theme}.png' for wide in ['', 'wide-'] for kind in ['geometry-preview','geometry-form','building-preview','building-form','letter-form','phone-form','review','history'] for theme in ['light','dark']}|{f'phase3cb-keyboard-{theme}.png' for theme in ['light','dark']}
assert {x['file'] for x in images}==expected_images
for x in images:
 p=ev/x['file'];assert sha(p)==x['sha256'];im=Image.open(p).convert('RGB');assert im.size==(x['width'],x['height'])==((1920,1200) if '-wide-' in p.name else (1080,2400));assert max(ImageStat.Stat(im).stddev)>5
r=dict(status='MECHANICAL_PASS_PENDING_INDEPENDENT_REVIEW',run_id=m['run_id'],source_commit=m['run_head_sha'],version='0.3.3',code=40,new_phase3cb_tests=16,retained_phase3cb_tests=15,fresh_phase3cb_tests=1,retained_source_commit=proof['source_commit'],frozen_prior_tests=100,frozen_phase3ca_run=36341715486,combined_distinct_identities=116,full_prior_suite_rerun=False,failures=0,manifest_files=manifest,frozen_files=len(frozen),source_sha256=sha(archive),**packages,screenshots=images,reserved_territories='UNTOUCHED',phase3='IN_PROGRESS',next='3C-C_independent_reconciliation_and_preparation_UI',phase3c_complete=False)
Path('phase3cb-verification.json').write_text(json.dumps(r,indent=2)+'\n');print(json.dumps({k:v for k,v in r.items() if k!='screenshots'},indent=2))

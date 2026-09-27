from pathlib import Path
import hashlib,json,tarfile,zipfile,xml.etree.ElementTree as E
base=Path('phase3b-source');src=Path('phase3ca-source');ev=Path('phase3ca-evidence')
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
m=json.loads(Path('phase3ca-artifact-meta.json').read_text());assert m['run_conclusion']=='success'
assert sha(Path('phase3ca-evidence.zip'))==m['artifact_sha256']
archive=ev/'TerritoryCardStudio-Android-0.3.2-phase3ca-source.tar.xz'
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
 if p.name not in {'TerritoryCardStudioServices.kt','AndroidBuildWorkflowCoordinator.kt'}:assert sha(p)==sha(src/app/p.name),p;frozen.append(str(p.relative_to(base)))
assert {p.name for p in (src/app).glob('*.kt')}-{p.name for p in (base/app).glob('*.kt')}=={'AndroidEditingPreparationService.kt','EditingSnapshots.kt'}
old=(base/app/'TerritoryCardStudioServices.kt').read_text();new=(src/app/'TerritoryCardStudioServices.kt').read_text()
addition='    val editingPreparation: AndroidEditingPreparationService by lazy {\n        AndroidEditingPreparationService(knowledgeBase, activePolicy, editingDrafts, SourceMapIntakeStore(appContext),\n            buildWorkflow, renderModels, fetchEvidence = { request ->\n            liveVerification.verify(request, ProviderCacheMode.BYPASS).evidence\n        })\n    }\n\n'
assert new.replace(addition,'')==old
# Existing coordinator behavior is unchanged outside one guarded invalidation condition.
a=(base/app/'AndroidBuildWorkflowCoordinator.kt').read_text()
b=(src/app/'AndroidBuildWorkflowCoordinator.kt').read_text()
start=b.index('    @Synchronized\n    internal fun editingBaseline')
end=b.index('    @Synchronized\n    fun state',start)
b=b[:start]+b[end:]
b=b.replace('var packet: StoredCanonicalFrontBackPdf? = null,\n        var editingBindingCurrent: (() -> Boolean)? = null','var packet: StoredCanonicalFrontBackPdf? = null')
b=b.replace(' || p.editingBindingCurrent?.let { !runCatching(it).getOrDefault(false) }==true','')
assert b==a, 'Unexpected coordinator change outside guarded editing installation'
for p in Path('repo/.tooling/tcs/phase3ca-overlay').glob('*.kt'):
 folder='app/src/androidTest/java/com/koenterprises/territorycardstudio' if p.name.endswith('InstrumentationTest.kt') else app
 assert sha(p)==sha(src/folder/p.name),p
cases=list(E.parse(ev/'TEST-phase3ca.xml').getroot().iter('testcase'))
assert len(cases)==len({(c.get('classname'),c.get('name')) for c in cases})==13
assert all(c.find('failure') is None and c.find('error') is None and c.find('skipped') is None for c in cases)
raw=(ev/'phase3ca-runtime.log').read_text()
assert 'OK (13 tests)' in raw
passed=[];cls=None;name=None
for line in raw.splitlines():
 if line.startswith('INSTRUMENTATION_STATUS: class='):cls=line.split('=',1)[1]
 if line.startswith('INSTRUMENTATION_STATUS: test='):name=line.split('=',1)[1]
 if line=='INSTRUMENTATION_STATUS_CODE: 0':passed.append((cls,name));cls=None;name=None
assert len(passed)==len(set(passed))==13
assert set(passed)=={(c.get('classname'),c.get('name')) for c in cases}
assert all(c=='com.koenterprises.territorycardstudio.Phase3CAEditingPreparationInstrumentationTest' for c,n in passed)

for n in ['app-compile.log','package-build.log','android-test-package.log']:assert 'BUILD SUCCESSFUL' in (ev/n).read_text()
packages={}
for ext,name in [('apk','app/build/outputs/apk/debug/app-debug.apk'),('aab','app/build/outputs/bundle/debug/app-debug.aab')]:
 p=src/name;assert sha(p)==(ev/(ext+'.sha256')).read_text().split()[0]
 with zipfile.ZipFile(p) as z:assert z.testzip() is None
 packages[ext+'_sha256']=sha(p)
assert "versionCode='39'" in (ev/'apk-badging.txt').read_text() and "versionName='0.3.2'" in (ev/'apk-badging.txt').read_text()
assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in (ev/'apk-signature.txt').read_text()
assert 'jar verified.' in (ev/'aab-jar-verify.txt').read_text()
assert (ev/'reserved-territory-pdf-sweep.txt').read_text()==''
r=dict(status='MECHANICAL_PASS_PENDING_INDEPENDENT_REVIEW',run_id=m['run_id'],source_commit=m['run_head_sha'],version='0.3.2',code=39,new_phase3ca_tests=13,frozen_prior_tests=87,frozen_phase3b_run=36339456333,combined_distinct_identities=100,full_prior_suite_rerun=False,failures=0,manifest_files=manifest,frozen_files=len(frozen),source_sha256=sha(archive),**packages,ui_changes=0,reserved_territories='UNTOUCHED',phase3='IN_PROGRESS',next='3C-B_authority_aware_extended_editing',phase3c_complete=False)
Path('phase3ca-verification.json').write_text(json.dumps(r,indent=2)+'\n');print(json.dumps(r,indent=2))

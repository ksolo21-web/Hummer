from pathlib import Path
import hashlib,json,tarfile,zipfile,re,xml.etree.ElementTree as E
from PIL import Image,ImageStat
base=Path('phase3cb-source');src=Path('phase3cc-source');ev=Path('phase3cc-evidence');overlay=Path('repo/.tooling/tcs/phase3cc-overlay')
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
m=json.loads(Path('phase3cc-artifact-meta.json').read_text());assert m['run_conclusion']=='success'
assert sha(Path('phase3cc-evidence.zip'))==m['artifact_sha256']
archive=ev/'TerritoryCardStudio-Android-0.3.4-phase3cc-source.tar.xz';assert sha(archive)==(ev/'source-package.sha256').read_text().split()[0]
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
changed={'TerritoryCardStudioServices.kt','WorkspaceModeUi.kt','AndroidEditingDraftStore.kt','AndroidExtendedDraftStore.kt'}
for p in (base/app).glob('*.kt'):
 if p.name not in changed:assert sha(p)==sha(src/app/p.name),p;frozen.append(str(p.relative_to(base)))
assert {p.name for p in (src/app).glob('*.kt')}-{p.name for p in (base/app).glob('*.kt')}=={'EditingJournalWitness.kt','AndroidEditingAuthorityStore.kt','EditingFactReconciliation.kt','AndroidExtendedPreparationService.kt','EditingPreparationUi.kt'}
witness='''    /** Capture under the journal lock; checking the resulting witness never acquires that lock. */
    internal fun revisionWitness(id:String,mode:WorkspaceMode):EditingJournalWitness=synchronized(LOCK) {
        readRaw(id,mode) // Validate/recover only here, before entering coordinator locks.
        EditingJournalWitness.capture(file(id,mode).baseFile,MAX_BYTES)
    }

'''
for name in ['AndroidEditingDraftStore.kt','AndroidExtendedDraftStore.kt']:assert (src/app/name).read_text().replace(witness,'')==(base/app/name).read_text()
old=(base/app/'TerritoryCardStudioServices.kt').read_text();new=(src/app/'TerritoryCardStudioServices.kt').read_text()
addition='''    val editingAuthority: AndroidEditingAuthorityStore by lazy {
        AndroidEditingAuthorityStore(File(privateRoot, "editing-authority-v1"), knowledgeBase, SourceMapIntakeStore(appContext), buildWorkflow)
    }

    val extendedPreparation: AndroidExtendedPreparationService by lazy {
        AndroidExtendedPreparationService(knowledgeBase, activePolicy, editingDrafts, extendedDrafts, editingAuthority,
            SourceMapIntakeStore(appContext), buildWorkflow, renderModels, fetchEvidence = { request ->
                liveVerification.verify(request, ProviderCacheMode.BYPASS).evidence
            })
    }

'''
assert new.replace(addition,'')==old
old=(base/app/'WorkspaceModeUi.kt').read_text();new=(src/app/'WorkspaceModeUi.kt').read_text()
new=new.replace('extendedDraftStore: AndroidExtendedDraftStore? = null,\n    authorityStore: AndroidEditingAuthorityStore? = null,\n    extendedPreparation: AndroidExtendedPreparationService? = null','extendedDraftStore: AndroidExtendedDraftStore? = null')
new=new.replace('''    if (workflowScreen == "EDITING_PREPARATION") {
        EditingPreparationScreen(modifier, assignment.displayId, mode, authorityStore ?: application.services.editingAuthority,
            extendedPreparation ?: application.services.extendedPreparation) { workflowScreen = "WORKSPACE" }
        return
    }
''','')
new=new.replace('            OutlinedButton(onClick = { workflowScreen = "EDITING_PREPARATION" }, modifier = Modifier.fillMaxWidth().testTag("workspace-editing-preparation")) { Text("Validate and prepare changes") }\n','');assert new==old
assert (src/'app/build.gradle.kts').read_text().replace('versionCode = 41','versionCode = 40').replace('versionName = "0.3.4"','versionName = "0.3.3"')==(base/'app/build.gradle.kts').read_text()
for p in overlay.glob('*.kt'):
 folder='app/src/androidTest/java/com/koenterprises/territorycardstudio' if p.name.startswith('Phase3CC') else app
 assert sha(p)==sha(src/folder/p.name),p
cases=list(E.parse(ev/'TEST-phase3cc.xml').getroot().iter('testcase'))
assert len(cases)==len({(c.get('classname'),c.get('name')) for c in cases})==16
assert all(c.find('failure') is None and c.find('error') is None and c.find('skipped') is None for c in cases)
def parse(raw):
 passed=[];failed=[];cls=None;name=None
 for line in raw.splitlines():
  if line.startswith('INSTRUMENTATION_STATUS: class='):cls=line.split('=',1)[1]
  if line.startswith('INSTRUMENTATION_STATUS: test='):name=line.split('=',1)[1]
  if line.startswith('INSTRUMENTATION_STATUS_CODE:'):
   code=int(line.rsplit(' ',1)[1])
   if code<=0 and name:(passed if code==0 else failed).append((cls,name));cls=None;name=None
 return passed,failed
proof=json.loads((ev/'retained-results.json').read_text())
assert sha(ev/'retained-runtime.log')==proof['runtime_sha256']
assert sha(ev/'retained-wide-captures.log')==proof['wide_runtime_sha256']
passed,failed=parse((ev/'retained-runtime.log').read_text());assert not failed
assert set(passed)=={tuple(x) for x in proof['retained']}
assert 'OK (16 tests)' in (ev/'retained-runtime.log').read_text()
assert 'OK (2 tests)' in (ev/'retained-wide-captures.log').read_text()
fresh,failed=parse((ev/'phase3cc-runtime.log').read_text());assert not failed
assert len(fresh)==2 and {n for c,n in fresh}=={'captureDark','captureLight'}
for row in proof['screenshots']:assert sha(ev/row['file'])==row['sha256']
# Only screenshot timing/selection changed. Every production file and functional test body remains exact.
with tarfile.open('phase3cc-attempt1/TerritoryCardStudio-Android-0.3.4-phase3cc-source.tar.xz') as t:
 for p in (src/app).glob('*.kt'):assert t.extractfile('./'+app+'/'+p.name).read()==p.read_bytes(),p.name
 test='app/src/androidTest/java/com/koenterprises/territorycardstudio/'
 for name in ['Phase3CCFixture.kt','Phase3CCReconciliationInstrumentationTest.kt']:assert t.extractfile('./'+test+name).read()==(src/test/name).read_bytes()
 old=t.extractfile('./'+test+'Phase3CCPreparationInstrumentationTest.kt').read().decode();new=(src/test/'Phase3CCPreparationInstrumentationTest.kt').read_text()
 assert old[:old.index('    private fun shot(')]==new[:new.index('    private fun shot(')]
assert len(proof['screenshots'])==22
assert len(passed)==len(set(passed))==16
expected=set()
for p in overlay.glob('*InstrumentationTest.kt'):expected.update(('com.koenterprises.territorycardstudio.'+p.stem,n) for n in re.findall(r'@Test fun (\w+)',p.read_text()))
assert set(passed)==expected=={(c.get('classname'),c.get('name')) for c in cases}
assert 'OK (2 tests)' in (ev/'phase3cc-wide-captures.log').read_text()
for n in ['app-compile.log','package-build.log','android-test-package.log']:assert 'BUILD SUCCESSFUL' in (ev/n).read_text()
packages={}
for ext,name in [('apk','app/build/outputs/apk/debug/app-debug.apk'),('aab','app/build/outputs/bundle/debug/app-debug.aab')]:
 p=src/name;assert sha(p)==(ev/(ext+'.sha256')).read_text().split()[0]
 with zipfile.ZipFile(p) as z:assert z.testzip() is None
 packages[ext+'_sha256']=sha(p)
assert "versionCode='41'" in (ev/'apk-badging.txt').read_text() and "versionName='0.3.4'" in (ev/'apk-badging.txt').read_text() and "targetSdkVersion:'37'" in (ev/'apk-badging.txt').read_text()
assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in (ev/'apk-signature.txt').read_text();assert 'jar verified.' in (ev/'aab-jar-verify.txt').read_text()
assert (ev/'reserved-territory-pdf-sweep.txt').read_text()==''
images=json.loads((ev/'screenshot-manifest.json').read_text());assert len(images)==24
expected_images={f'phase3cc-{wide}{kind}-{theme}.png' for wide in ['', 'wide-'] for kind in ['blocked','authority','validated','confirmation','prepared','stale'] for theme in ['light','dark']}
assert {x['file'] for x in images}==expected_images
for x in images:
 p=ev/x['file'];assert sha(p)==x['sha256'];im=Image.open(p).convert('RGB');assert im.size==(x['width'],x['height'])==((1920,1200) if '-wide-' in p.name else (1080,2400));assert max(ImageStat.Stat(im).stddev)>5
r=dict(status='MECHANICAL_PASS_PENDING_INDEPENDENT_REVIEW',run_id=m['run_id'],source_commit=m['run_head_sha'],version='0.3.4',code=41,new_phase3cc_tests=16,retained_phase3cc_tests=16,retried_captures=2,frozen_prior_tests=116,frozen_phase3cb_run=36349473357,combined_distinct_identities=132,full_prior_suite_rerun=False,failures=0,manifest_files=manifest,frozen_files=len(frozen),source_sha256=sha(archive),**packages,screenshots=images,reserved_territories='UNTOUCHED',phase3='IN_PROGRESS',next='3D_aggregate_integration',real_territory_authority_commissioning=False)
Path('phase3cc-verification.json').write_text(json.dumps(r,indent=2)+'\n');print(json.dumps({k:v for k,v in r.items() if k!='screenshots'},indent=2))

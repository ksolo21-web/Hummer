from pathlib import Path
import hashlib, json, re, tarfile, zipfile
import xml.etree.ElementTree as ET
from PIL import Image

base = Path('baseline/source')
ev = Path('verified-evidence')
src = Path('verified-source')
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(Path('phase2i-baseline.zip')) == 'f7a50f6c9e9ec25e5878fa0de162402e161cc7dfc1ca917f6f740bbe1cc5ea19'
with zipfile.ZipFile('phase2i-baseline.zip') as baseline_zip:
    for line in baseline_zip.read('extracted-source-files.sha256').decode().splitlines():
        h, name = line.split('  ', 1)
        assert sha(base / name) == h, ('baseline', name)
def expected(name):
    return (ev / name).read_text().split()[0]
archive = ev / 'TerritoryCardStudio-Android-0.2.10-phase2j-source.tar.xz'
assert sha(archive) == expected('source-package.sha256')
src.mkdir(exist_ok=True)
with tarfile.open(archive) as t:
    t.extractall(src, filter='data')
assert sha(ev / 'extracted-source-files.sha256') == expected('extracted-source-manifest.sha256')
checked = 0
for line in (ev / 'extracted-source-files.sha256').read_text().splitlines():
    h, name = line.split('  ', 1)
    p = src / name
    assert p.is_relative_to(src)
    assert sha(p) == h, name
    checked += 1
frozen = {}
for label, folder, pattern in [('core', 'core/src', '*.kt'), ('assets', 'app/src/main/assets', '*'), ('prior_tests', 'app/src/androidTest', '*.kt')]:
    paths = [p for p in (base / folder).rglob(pattern) if p.is_file()]
    if label == 'core': paths.append(base / 'core/build.gradle.kts')
    for p in paths:
        relative = p.relative_to(base)
        if label == 'prior_tests' and p.name == 'Phase2FReviewUiInstrumentationTest.kt':
            repaired = (src / relative).read_text()
            assert all(line.strip() in repaired for line in p.read_text().splitlines() if 'assert' in line)
            assert 'clearFocus(force = true)' in repaired and 'now - stableSince >= 250' in repaired
            continue
        assert sha(p) == sha(src / relative), relative
    frozen[label] = len(paths) - (1 if label == 'prior_tests' else 0)
app_changes = []
for p in (base / 'app/src/main/java').rglob('*.kt'):
    if sha(p) != sha(src / p.relative_to(base)):
        app_changes.append(p.name)
allowed = {'AndroidBuildWorkflowCoordinator.kt', 'AndroidPdfPreviewService.kt', 'TerritoryCardStudioServices.kt', 'PdfPreviewUi.kt', 'WorkspaceModeUi.kt', 'VerificationWorkflowUi.kt'}
assert set(app_changes) <= allowed, app_changes
for p in Path('repo/.tooling/tcs/phase2j-overlay').glob('*.kt'):
    kind = 'androidTest' if p.name.startswith(('Phase2J', 'Phase2F')) else 'main'
    target = src / f'app/src/{kind}/java/com/koenterprises/territorycardstudio' / p.name
    assert sha(p) == sha(target), p.name
apk = src / 'app/build/outputs/apk/debug/app-debug.apk'
aab = src / 'app/build/outputs/bundle/debug/app-debug.aab'
assert sha(apk) == expected('apk.sha256')
assert sha(aab) == expected('aab.sha256')
for p in [apk, aab]:
    with zipfile.ZipFile(p) as z:
        assert z.testzip() is None
badging = (ev / 'apk-badging.txt').read_text()
assert "versionCode='35'" in badging and "versionName='0.2.10'" in badging and "targetSdkVersion:'37'" in badging
assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in (ev / 'apk-signature.txt').read_text()
assert 'jar verified.' in (ev / 'aab-jar-verify.txt').read_text()
cases = [c for p in (ev / 'android-test-results').glob('TEST*.xml') for c in ET.parse(p).getroot().iter('testcase')]
assert len(cases) == 57
assert all(c.find('failure') is None and c.find('error') is None and c.find('skipped') is None for c in cases)
for log in ['core-test.log', 'app-compile.log', 'package-build.log', 'connected-android-test.log']:
    assert 'BUILD SUCCESSFUL' in (ev / log).read_text(), log
for log in ['capture-test.log', 'capture-wide-test.log']:
    assert 'OK (1 test)' in (ev / log).read_text(), log
shots = sorted((ev / 'ui').glob('*.png'))
assert len(shots) == len({sha(p) for p in shots}) == 20
for p in shots:
    with Image.open(p) as im:
        assert im.size == ((1920, 1200) if 'wide-' in p.name else (1080, 2400))
reserved = [str(p) for p in src.rglob('*.pdf') if re.search(r'(?:Territory\s*-\s*|^)(?:250T|T250|257A|A257|297|298A|A298|299|347TA|TA347)(?:\D|$)', p.name)]
assert not reserved, reserved
meta = json.loads(Path('phase2j-final-artifact-meta.json').read_text())
assert meta['run_conclusion'] == 'success'
report = dict(status='MECHANICAL_PASS_PENDING_INDEPENDENT_VISUAL_REVIEW', source_commit=meta['run_head_sha'], run_id=meta['run_id'], version='0.2.10', code=35, connected_tests=len(cases), failures=0, errors=0, skipped=0, source_manifest_files=checked, frozen_files=frozen, prior_test_sync_repairs={'Phase2FReviewUiInstrumentationTest.kt':'focusclear_and_stable_bounds; all_original_assertions_and_physical_click_retained'}, app_changed_files=app_changes, source_sha256=sha(archive), apk_sha256=sha(apk), aab_sha256=sha(aab), source_manifest_sha256=sha(ev/'extracted-source-files.sha256'), screenshots={p.name: sha(p) for p in shots}, reserved_pdf_sweep=0)
Path('phase2j-verification.json').write_text(json.dumps(report, indent=2)+'\n')
print(json.dumps(report, indent=2))

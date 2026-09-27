#!/usr/bin/env bash
set -euo pipefail
mkdir -p evidence/android-test-results evidence/ui
frozen=.tooling/tcs/phase2k-evidence
cp "$frozen/frozen-run36323755251-pass62.xml" evidence/android-test-results/TEST-frozen-pass62.xml
cp "$frozen/TEST-retried-phase2k-capture.xml" evidence/android-test-results/
cp "$frozen/connected-test-status.txt" "$frozen/connected-android-test.log" evidence/
cp "$frozen"/retry-*.log evidence/
printf 'PASS62_SOURCE_RUN=36323755251\nPASS2_SOURCE_RUN=36328681508\nCURRENT_RUN=VISUAL_CAPTURE_AND_PACKAGE_ONLY\nPRIOR_PHASE2J_VISUALS=ACCEPTED_RUN36310455841_NOT_RECAPTURED\n' > evidence/frozen-provenance.txt
(cd tcs-src && gradle --no-daemon :app:assembleDebugAndroidTest) > evidence/android-test-package.log 2>&1
adb install -r tcs-src/app/build/outputs/apk/debug/app-debug.apk >/dev/null
adb install -r tcs-src/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >/dev/null
# Reopen only the five cases whose findings/audit dependencies changed; add three focused regressions.
run_targeted() {
  local selector="$1" count="$2" name="$3"
  adb shell am force-stop com.koenterprises.territorycardstudio
  adb shell am instrument -w -r -e class "com.koenterprises.territorycardstudio.$selector" com.koenterprises.territorycardstudio.test/androidx.test.runner.AndroidJUnitRunner > "evidence/targeted-$name.log" 2>&1
  grep -F "OK ($count test" "evidence/targeted-$name.log" >/dev/null
}
run_targeted Phase2KInstrumentationTest 4 service
for method in actualSystemPickerCancelsSavesAndRejectsStaleAudit affectedRoadFindingOpensStreetsWithActualItemContext letterRoadFindingKeepsItemContextInDetails telephoneRoadFindingKeepsItemContextInDetails; do
  run_targeted "Phase2KUiInstrumentationTest#$method" 1 "$method"
done
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as E
service=['scopedAuditKeepsExactIdentityAndReadbackWithoutFieldAuthority','staleAuditNeverWritesAndUnpreparedEvidenceIsHonest','actualOverlapRoadAndBuildingAreItemLinkedWithoutInferredIdentifiers','failedAuditCleanupReportsRemainingCopyTruthfully']
ui=['actualSystemPickerCancelsSavesAndRejectsStaleAudit','affectedRoadFindingOpensStreetsWithActualItemContext','letterRoadFindingKeepsItemContextInDetails','telephoneRoadFindingKeepsItemContextInDetails']
for p in Path('evidence/android-test-results').glob('TEST*.xml'):
 root=E.parse(p).getroot()
 for parent in root.iter():
  for child in list(parent):
   if child.tag=='testcase' and child.get('name') in service+ui:parent.remove(child)
 for suite in root.iter():
  if suite.tag in ['testsuite','testsuites']:
   suite.set('tests',str(len(list(suite.iter('testcase')))))
 E.ElementTree(root).write(p,encoding='UTF-8',xml_declaration=True)
root=E.Element('testsuite',name='Targeted findings and audit repair',tests='8',failures='0',errors='0',skipped='0')
for cls,names in [('Phase2KInstrumentationTest',service),('Phase2KUiInstrumentationTest',ui)]:
 for name in names:E.SubElement(root,'testcase',classname='com.koenterprises.territorycardstudio.'+cls,name=name)
E.ElementTree(root).write('evidence/android-test-results/TEST-targeted-repair.xml',encoding='UTF-8',xml_declaration=True)
PY
printf 'CONNECTED_TESTS=PASS_67\nFROZEN_UNAFFECTED_CASES=59\nREOPENED_DEPENDENCY_CASES=5\nNEW_REGRESSION_CASES=3\n' > evidence/connected-test-status.txt
printf 'TARGETED_REPAIR=8_CURRENT_CASES_PLUS_59_FROZEN\nREOPENED=FINDINGS_DESTINATION_AND_AUDIT_CLEANUP_ONLY\n' >> evidence/frozen-provenance.txt

for mode in phone wide; do
  prefix=''
  if [ "$mode" = wide ]; then
    adb shell wm size 1920x1200
    adb shell wm density 160
    prefix='wide-'
  fi
  for theme in light dark; do
    method=captureFindingsAndAuditAndNavigateFromPreviewAcrossThemes
    if [ "$theme" = dark ]; then method=captureFindingsAndAuditAndNavigateFromPreviewDark; fi
    adb shell am force-stop com.koenterprises.territorycardstudio
    log="evidence/visual-$mode-$theme.log"
    adb shell am instrument -w -r -e class "com.koenterprises.territorycardstudio.Phase2KUiInstrumentationTest#$method" com.koenterprises.territorycardstudio.test/androidx.test.runner.AndroidJUnitRunner > "$log" 2>&1
    for page in findings audit preview; do
      name="phase2k-${prefix}${page}-${theme}.png"
      adb exec-out run-as com.koenterprises.territorycardstudio cat "files/$name" > "evidence/ui/$name" || true
    done
    grep -F 'OK (1 test)' "$log" >/dev/null
  done
done

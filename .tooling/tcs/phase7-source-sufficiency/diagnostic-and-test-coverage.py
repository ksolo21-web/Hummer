#!/usr/bin/env python3
"""Keep source error evidence and execute all independent map assessments."""
from pathlib import Path
import json,sys
root=Path(sys.argv[1]).resolve(strict=True)
assessment=root/'app/src/main/java/com/koenterprises/territorycardstudio/NativeSourceAssessment.kt'
s=assessment.read_text()
needle='        val location=SourceLocation(sha);val facts=mutableListOf<SourceFact>()\n'
replacement=needle+'''        // OCR/triage diagnostic rectangles can extend beyond the bitmap or contain
        // non-finite sentinel coordinates. Preserve the finding, but only attach a
        // pixel highlight when the source rectangle itself is finite and valid.
        // This never changes boundary/work facts or upgrades source readiness.
        fun sourceRegion(box:AxisAlignedRect?):SourcePixelRegion?=box?.let {
            if(!listOf(it.left,it.top,it.right,it.bottom).all(Double::isFinite)) return@let null
            val left=maxOf(0.0,it.left);val top=maxOf(0.0,it.top)
            SourcePixelRegion(left,top,maxOf(left,it.right),maxOf(top,it.bottom))
        }
'''
assert s.count(needle)==1;s=s.replace(needle,replacement)
old='review.bounds(id)?.let {SourcePixelRegion(it.left,it.top,it.right,it.bottom)}'
assert s.count(old)==1;s=s.replace(old,'sourceRegion(review.bounds(id))')
old='f.sourceBounds?.let {SourcePixelRegion(it.left,it.top,it.right,it.bottom)}'
assert s.count(old)==1;s=s.replace(old,'sourceRegion(f.sourceBounds)')
assessment.write_text(s)
# Give each authoring section a fresh LazyListState. Reusing one scroll state across
# sections with different interval counts can transiently ask Compose to measure a
# stale index after a tab switch (for example index 4 when the new content has 4).
# This is a real UI race; do not weaken the reference comparison regression.
ui=root/'app/src/main/java/com/koenterprises/territorycardstudio/NativeAuthoringUi.kt'
s=ui.read_text()
old='import androidx.compose.foundation.lazy.LazyColumn\n'
new='import androidx.compose.foundation.lazy.LazyColumn\nimport androidx.compose.foundation.lazy.rememberLazyListState\n'
assert s.count(old)==1;s=s.replace(old,new)
old='    LazyColumn(modifier.fillMaxSize().testTag("native-authoring-screen"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {'
new='    val authoringListState=key(section) { rememberLazyListState() }\n    LazyColumn(modifier.fillMaxSize().testTag("native-authoring-screen"),state=authoringListState,contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {'
assert s.count(old)==1;s=s.replace(old,new)
ui.write_text(s)
store=root/'app/src/main/java/com/koenterprises/territorycardstudio/AndroidNativeDraftStore.kt'
s=store.read_text()
old='(error.message ?: error.javaClass.simpleName).take(4000),"Source checking failed. Retry or repair the processing/storage error; the map has not been judged insufficient."'
new='("${error.javaClass.simpleName}: ${error.message ?: "Source assessment failed"} at ${error.stackTrace.take(6).joinToString(" <- ")}").take(4000),"Source checking failed. Retry or repair the processing/storage error; the map has not been judged insufficient."'
assert s.count(old)==1;s=s.replace(old,new);store.write_text(s)
test=root/'app/src/androidTest/java/com/koenterprises/territorycardstudio/Phase7SourceSufficiencyInstrumentationTest.kt'
s=test.read_text()
old='assertNotEquals("Software errors must be repaired, not counted as insufficient-map passes: ${readFailure?.message}",SourceReadiness.PROCESSING_ERROR,report.status)'
new='save("actual-$id-diagnostic",output(report).put("actualUploadedMap",true).put("asset",asset).put("readFailure",readFailure?.stackTraceToString() ?: JSONObject.NULL))\n                // Keep processing remaining independent maps when one has a software defect.'
assert s.count(old)==1;s=s.replace(old,new)
needle='            .put("note","These are actual intake/hold decisions, not completed source reconciliation or field-card approvals. Positive lifecycle coverage is a separate synthetic control."))'
assert s.count(needle)==1;s=s.replace(needle,needle+'\n        val softwareErrors=(0 until reports.length()).map {reports.getJSONObject(it)}.filter {it.getString("status")=="PROCESSING_ERROR"}\n        assertTrue("Software defects are not inadequate-source passes: ${softwareErrors.joinToString()}",softwareErrors.isEmpty())')
test.write_text(s)
here=Path(__file__).resolve().parent
runner=here/'run-device-tests.sh';s=runner.read_text()
old='methods=(positiveSourceCompletesTheExistingApprovalAndExportLifecycle '
new='methods=(reanalysisCannotEraseAHardHoldOrAnObservedIdentity aHeldOrCorruptSavedCandidateDoesNotPreventASupportedCard positiveSourceCompletesTheExistingApprovalAndExportLifecycle '
assert s.count(old)==1;s=s.replace(old,new).replace('run_case phone 9 ', 'run_case phone 11 ').replace("'nativeTests':13", "'nativeTests':15")
needle="assert read('source-replacement')['oldAssessmentInvalidated']"
assert s.count(needle)==1;s=s.replace(needle,needle+"\nh=read('reanalysis-hold'); assert h['status']=='NEEDS_SOURCE'\nassert all(h[k] for k in ['holdCannotBeClearedByModeSwitch','processingFailureCannotErasePriorHold','observedIdentityRetained','replacementSourceAssessedSeparately'])\nm=read('mixed-saved-batch'); assert all(m[k] for k in ['supportedCandidateGenerated','heldCandidateNotRegistered','corruptCandidateIsolated'])\nassert m['fieldCardApproved'] is False")
runner.write_text(s)
print(json.dumps({'diagnosticsPreserved':True,'newNativeTestCount':15,'phase7Complete':False}))

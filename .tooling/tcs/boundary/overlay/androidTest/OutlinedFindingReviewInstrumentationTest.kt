package com.koenterprises.territorycardstudio

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.koenterprises.territorycardstudio.core.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class OutlinedFindingReviewInstrumentationTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    companion object {
        private const val HASH="8b17f90abfac5afe4e3de08f696494199ae88f97cfbb5d1fb035a0153e08a29d"
        private lateinit var draft:InterpretedMapDraft
        @JvmStatic @BeforeClass fun importExactRealSourceOncePerDeviceConfiguration() {
            val source=source("analysis")
            try {draft=AndroidMapImageInterpreter().interpretOutlined(source,HASH)} finally {source.delete()}
        }
        private fun source(suffix:String):File {
            val i=InstrumentationRegistry.getInstrumentation()
            val f=File(i.targetContext.cacheDir,"finding-a265-$suffix.jpg")
            i.context.assets.open("phase7-03-26175.jpg").use {input->f.outputStream().use {input.copyTo(it)}}
            assertEquals(HASH,f.inputStream().use(BundleIntegrity::sha256));return f
        }
    }
    private val viewport get()=InstrumentationRegistry.getArguments().getString("phase7Viewport","phone")
    private fun report(name:String,body:JSONObject) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir,"phase7-finding-$name-$viewport.json").writeText(body.put("sourceSha256",HASH)
            .put("viewport",viewport).put("testOnly",true).put("cardApproved",false).toString(2))
    }
    private fun screenshot(name:String) {
        val i=InstrumentationRegistry.getInstrumentation()
        rule.runOnUiThread {(rule.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .hideSoftInputFromWindow(rule.activity.window.decorView.windowToken,0)}
        rule.waitForIdle()
        requireNotNull(i.uiAutomation.takeScreenshot()).let {image->
            File(i.targetContext.filesDir,"phase7-finding-$name-$viewport.png").outputStream().use {assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))};image.recycle()
        }
    }
    @Test fun exactInteriorFindingPersistsWithoutRemovingCoverageOrApprovingACard() {
        val review=OutlinedNativeReview.from(draft)
        val finding=review.extraction.findings.first {f->f.id.startsWith("component-") && f.sourceBounds?.let {box->
            OutlinedMapBoundaryDetector.inside(Point2D((box.left+box.right)/2,(box.top+box.bottom)/2),review.extraction.boundary.polygon) &&
                runCatching {review.reviewFinding(f.id,"MAP_SYMBOL","Test-only source review of one isolated mark; no field approval",emptyList(),draft.roads)}.isSuccess
        }==true}
        val initial=review.unresolvedFindings(draft.roads).size
        val updated=review.reviewFinding(finding.id,"MAP_SYMBOL","Test-only source review of this isolated mark; not an approved card decision",emptyList(),draft.roads)
        assertEquals(initial-1,updated.unresolvedFindings(draft.roads).size)
        assertEquals(review.extraction,updated.extraction);assertEquals(review.spans,updated.spans)
        assertEquals(review.coverageFailures(draft.roads),updated.coverageFailures(draft.roads))
        assertFalse(updated.complete(draft.roads));assertEquals(updated,OutlinedNativeReview(updated.document))
        assertEquals(review.document,updated.clearFinding(finding.id).document)
        val stale=JSONObject(updated.document);stale.getJSONArray("resolutions").getJSONObject(0).put("outputSha256","0".repeat(64))
        assertTrue(OutlinedNativeReview(ExtendedValues.canonical(stale)).unresolvedFindings(draft.roads).any {it.id==finding.id})
        val duplicate=JSONObject(updated.document);duplicate.getJSONArray("resolutions").put(duplicate.getJSONArray("resolutions").getJSONObject(0))
        assertTrue(runCatching {OutlinedNativeReview(ExtendedValues.canonical(duplicate))}.isFailure)
        val file=source("persistence")
        try {Phase6NativeFixture(WorkspaceMode.REGULAR).use {x->
            val imported=x.sources.importFromStream(x.slot,"real-a265-finding.jpg","image/jpeg",file.inputStream())
            val assignment=CurrentAuthoritativeAssignmentState(x.id,x.identity,x.identity.canonicalFilename,x.kb.revision,"current_authoritative_assignment","0".repeat(64),imported.sourceFilename,
                "Rochester","9/28/2026",listOf("Directions: Test-only native source-review persistence; not for field use."),"full_map",x.slot.housingType,
                RenderCoordinateSpace.LOCKED_R48_PAGE_POINTS_TOP_ORIGIN,draft.roads,emptyList())
            val reconciliation=NativeSourceReconciliation(x.id,x.mode.name,x.kb.revision,HASH,x.slot.referenceSha256,"current_assignment_map","Instrumentation reviewer",
                "2026-09-28T21:00:00Z",NativeSourceReconciliationContract.assignmentContentSha256(assignment),null,false,false,false,false,draft.observations,emptyList(),
                "00000000-0000-0000-0000-000000000070",null,updated.sha256)
            val saved=x.drafts.save(NativeAuthoringDraft(assignment,reconciliation,VerificationJurisdiction("Oakland County","Michigan","United States"),emptyList(),outlinedReview=updated),null)
            val resumed=AndroidNativeDraftStore(x.app,x.kb,x.sources,File(x.root,"native")).read(x.id,x.mode)
            assertEquals(saved,resumed);assertEquals(updated,resumed?.outlinedReview)
            assertTrue(runCatching {x.drafts.register(x.id,x.mode,saved.revisionSha256)}.isFailure)
            val reopened=updated.clearFinding(finding.id)
            val changed=x.drafts.save(saved.copy(outlinedReview=reopened,reconciliation=saved.reconciliation.copy(imageInterpretationSha256=reopened.sha256)),saved.revisionSha256)
            assertNotEquals(saved.revisionSha256,changed.revisionSha256)
            assertTrue(runCatching {x.drafts.register(x.id,x.mode,saved.revisionSha256)}.isFailure)
            assertTrue(runCatching {x.drafts.register(x.id,x.mode,changed.revisionSha256)}.isFailure)
            report("persistence",JSONObject().put("findingId",finding.id).put("initialUnresolved",initial).put("afterReviewUnresolved",initial-1)
                .put("resumedExactDraft",saved==resumed).put("coverageUnchanged",true).put("reopeningChangesRevision",true).put("registrationStillBlocked",true))
        }} finally {file.delete()}
    }
    private fun uiReview(appearance:AppearanceMode) {
        val source=source(appearance.name.lowercase())
        val original=OutlinedNativeReview.from(draft);val state=mutableStateOf(original)
        try {
            rule.setContent {TerritoryCardStudioTheme(appearance) {Surface {Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                OutlinedFindingReviewPanel(state.value,draft.roads,source){next,roads->assertEquals(draft.roads,roads);state.value=next}
            }}}}
            rule.onNodeWithTag("outlined-finding-record").performScrollTo().assertIsNotEnabled()
            rule.onNodeWithTag("outlined-finding-kind-OUTSIDE_CONTEXT").performScrollTo().performClick()
            rule.onNodeWithTag("outlined-finding-evidence").performScrollTo().performTextInput("Test-only review: this exact small source region is outside the outlined assignment")
            rule.onNodeWithTag("outlined-finding-record").performScrollTo().assertIsNotEnabled()
            rule.waitUntil(30000){runCatching {rule.onNodeWithTag("outlined-finding-inspected").assertIsEnabled();true}.getOrDefault(false)}
            rule.onNodeWithTag("outlined-finding-inspected").performScrollTo().performClick()
            rule.onNodeWithTag("outlined-finding-record").performScrollTo().assertIsEnabled()
            // Changing the selected source finding must reset the inspection and evidence.
            rule.onNodeWithTag("outlined-finding-next").performScrollTo().performClick()
            rule.onNodeWithTag("outlined-finding-record").performScrollTo().assertIsNotEnabled()
            rule.onNodeWithTag("outlined-finding-inspected").assertIsOff()
            rule.onNodeWithTag("outlined-finding-previous").performScrollTo().performClick()
            rule.onNodeWithTag("outlined-finding-record").performScrollTo().assertIsNotEnabled()
            rule.onNodeWithTag("outlined-finding-kind-OUTSIDE_CONTEXT").performScrollTo().performClick()
            rule.onNodeWithTag("outlined-finding-evidence").performScrollTo().performTextReplacement("Test-only review: this exact small source region is outside the outlined assignment")
            rule.waitUntil(30000){runCatching {rule.onNodeWithTag("outlined-finding-inspected").assertIsEnabled();true}.getOrDefault(false)}
            rule.onNodeWithTag("outlined-finding-inspected").performScrollTo().performClick()
            rule.onNodeWithTag("outlined-finding-crop").performScrollTo();screenshot(appearance.name.lowercase())
            rule.onNodeWithTag("outlined-finding-record").performScrollTo().performClick()
            rule.runOnIdle {assertEquals(1,state.value.findingResolutions().size);assertFalse(state.value.complete(draft.roads))}
            rule.onNodeWithTag("outlined-findings-recorded").performScrollTo().performClick()
            rule.onNodeWithTag("outlined-finding-clear").performScrollTo().performClick()
            rule.runOnIdle {assertEquals(original.document,state.value.document)}
            report(appearance.name.lowercase(),JSONObject().put("explicitEvidenceAndInspectionRequired",true).put("newFindingResetsInspection",true).put("reopenedExactOriginalReview",true))
            assertEquals(HASH,source.inputStream().use(BundleIntegrity::sha256))
        } finally {source.delete()}
    }
    @Test fun lightThemeReviewRequiresExactCropInspectionAndSupportsReopening()=uiReview(AppearanceMode.LIGHT)
    @Test fun darkThemeReviewRequiresExactCropInspectionAndSupportsReopening()=uiReview(AppearanceMode.DARK)
    @Test fun aReplacedSourceCannotReuseItsPreviousReadableCrop() {
        val source=source("tamper");val original=OutlinedNativeReview.from(draft);var calls=0
        try {
            rule.setContent {TerritoryCardStudioTheme(AppearanceMode.LIGHT) {Surface {Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                OutlinedFindingReviewPanel(original,draft.roads,source){_,_->calls++}
            }}}}
            rule.onNodeWithTag("outlined-finding-kind-OUTSIDE_CONTEXT").performScrollTo().performClick()
            rule.onNodeWithTag("outlined-finding-evidence").performScrollTo().performTextInput("The visible source region was inspected before this test replaced the source bytes")
            rule.waitUntil(30000){runCatching {rule.onNodeWithTag("outlined-finding-inspected").assertIsEnabled();true}.getOrDefault(false)}
            rule.onNodeWithTag("outlined-finding-inspected").performScrollTo().performClick()
            rule.onNodeWithTag("outlined-finding-record").performScrollTo().assertIsEnabled()
            source.writeBytes(byteArrayOf(1,2,3,4))
            rule.onNodeWithTag("outlined-finding-record").performClick()
            rule.onNodeWithTag("outlined-finding-status").performScrollTo().assertTextContains("source changed",substring=true)
            rule.runOnIdle {assertEquals(0,calls)}
            report("source-replacement",JSONObject().put("samePathReplacementRejected",true).put("decisionCallbacks",calls))
        } finally {source.delete()}
    }
}

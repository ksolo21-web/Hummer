package com.koenterprises.territorycardstudio

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.koenterprises.territorycardstudio.core.BundleIntegrity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exact user-supplied images, separate from synthetic Phase 6 acceptance fixtures.
 * These tests measure intake and fail-closed behavior; they do not certify a field card. */
@RunWith(AndroidJUnit4::class)
class Phase7RealImageIntakeInstrumentationTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private fun inspect(caseId: String, asset: String, expectedHash: String): InterpretedMapDraft? {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val source = File(context.cacheDir, "phase7-$caseId.jpg")
        instrumentation.context.assets.open(asset).use { input ->
            source.outputStream().use { output -> input.copyTo(output) }
        }
        val actualHash = source.inputStream().use(BundleIntegrity::sha256)
        assertEquals("The exact supplied source image changed", expectedHash, actualHash)
        var result: InterpretedMapDraft? = null
        var failure: Throwable? = null
        try {
            result = AndroidMapImageInterpreter().interpretOutlined(source, actualHash)
            return result
        } catch (error: Throwable) {
            failure = error
            return null
        } finally {
            val boundary = result?.outlined?.boundary
            val report = JSONObject().put("case", caseId).put("sourceSha256", actualHash)
                .put("inputKind", "OUTLINED_AREA")
                .put("decoderError", failure?.let { "${it.javaClass.simpleName}: ${it.message}" } ?: JSONObject.NULL)
                .put("boundary", boundary?.let { b -> JSONObject()
                    .put("width", b.width).put("height", b.height)
                    .put("enclosedPixels", b.enclosedPixels)
                    .put("bounds", JSONArray(listOf(b.sourceBounds.left, b.sourceBounds.top,
                        b.sourceBounds.right, b.sourceBounds.bottom)))
                    .put("points", JSONArray(b.polygon.map { JSONArray(listOf(it.x, it.y)) }))
                    .put("repairs",JSONArray(b.gapRepairs.map {r->JSONObject().put("id",r.id).put("algorithm",r.algorithm)
                        .put("start",JSONArray(listOf(r.start.x,r.start.y))).put("end",JSONArray(listOf(r.end.x,r.end.y)))
                        .put("addedPixels",JSONArray(r.addedPixels.map {JSONArray(listOf(it.x,it.y))}))}))
                } ?: JSONObject.NULL)
                .put("recognizedText", JSONArray(result?.recognizedText.orEmpty().map { text ->
                    JSONObject().put("text", text.text).put("bounds", JSONArray(listOf(
                        text.bounds.left, text.bounds.top, text.bounds.right, text.bounds.bottom)))
                }))
                .put("roads", JSONArray(result?.roads.orEmpty().map { road -> JSONObject()
                    .put("id", road.segmentId).put("name", road.name).put("status", road.status)
                    .put("role", road.role) }))
                .put("findings", JSONArray(result?.findings.orEmpty().map { finding -> JSONObject()
                    .put("id", finding.id).put("message", finding.message) }))
                .put("registrationReadyWithoutReview", result?.let {
                    it.outlined?.let { _ -> OutlinedNativeReview.from(it).complete(it.roads) } ?: false
                } ?: false)
            File(context.filesDir, "phase7-$caseId-intake.json").writeText(report.toString(2))
            source.delete()
        }
    }

    @Test fun a265IsAUsableOutlinedSourceButStillNeedsVerification() {
        val result = inspect("a265", "phase7-03-26175.jpg",
            "8b17f90abfac5afe4e3de08f696494199ae88f97cfbb5d1fb035a0153e08a29d")
        assertNotNull("A-265 exact source could not be interpreted; see the saved intake report", result)
        val boundary = requireNotNull(result!!.outlined?.boundary)
        assertTrue("A-265 source polygon was not recovered", boundary.polygon.size >= 4)
        assertEquals("Exact A265 must expose one repair, never hide it",1,boundary.gapRepairs.size)
        val pending=OutlinedNativeReview.from(result)
        assertEquals(pending,OutlinedNativeReview(pending.document))
        assertTrue(runCatching {pending.confirmBoundary(true)}.isFailure)
        assertTrue(pending.coverageFailures(result.roads).contains("BOUNDARY_REPAIR_UNREVIEWED:gap-1"))
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        val source=File(context.cacheDir,"a265-review-source.jpg")
        instrumentation.context.assets.open("phase7-03-26175.jpg").use {input->source.outputStream().use {input.copyTo(it)}}
        val ui=mutableStateOf(pending)
        rule.setContent {Column(Modifier.verticalScroll(rememberScrollState())) {
            OutlinedNativeReviewPanel(ui.value,result.roads,source){r,_->ui.value=r}
        }}
        rule.onNodeWithTag("outlined-boundary-confirm").assertIsNotEnabled()
        rule.onNodeWithTag("boundary-repair-evidence").performScrollTo().performTextInput("Reviewed the short connection across the Timberlea Dr label against both visible stroke ends")
        rule.waitUntil(30000){runCatching {rule.onNodeWithTag("boundary-repair-accept").assertIsEnabled();true}.getOrDefault(false)}
        rule.onNodeWithTag("boundary-repair-closeup").performScrollTo()
        rule.runOnUiThread {
            val view=rule.activity.window.decorView
            (rule.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(view.windowToken,0)
        }
        rule.waitForIdle()
        requireNotNull(instrumentation.uiAutomation.takeScreenshot()).let {bitmap->File(context.filesDir,"phase7-a265-repair-pending.png").outputStream().use {assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()}
        rule.onNodeWithTag("boundary-repair-accept").performScrollTo().performClick()
        rule.onNodeWithTag("outlined-boundary-confirm").performScrollTo().performClick()
        rule.runOnIdle {assertTrue(ui.value.boundaryConfirmed);assertFalse(ui.value.complete(result.roads))}
        val reviewed=ui.value
        val retained=File(context.filesDir,"phase7-a265-review.json");retained.writeText(reviewed.document)
        assertEquals(reviewed,OutlinedNativeReview(retained.readText()))
        val tampered=JSONObject(reviewed.document)
        tampered.getJSONObject("analysis").getJSONArray("repairs").getJSONObject(0).getJSONArray("addedPixels").getJSONArray(0).put(0,1.0)
        assertTrue(runCatching {OutlinedNativeReview(ExtendedValues.canonical(tampered))}.isFailure)
        rule.onNodeWithTag("boundary-repair-reject").performScrollTo().performClick()
        rule.runOnIdle {assertFalse(ui.value.boundaryConfirmed);assertTrue(ui.value.boundaryRepairEvidence.isEmpty())}
        rule.onNodeWithTag("outlined-boundary-confirm").assertIsNotEnabled()
        assertEquals(result.sourceSha256,source.inputStream().use(BundleIntegrity::sha256))
        File(context.filesDir,"phase7-a265-repair-review.json").writeText(JSONObject().put("sourceSha256",result.sourceSha256)
            .put("pendingBlocked",true).put("explicitReviewEnabledBoundaryConfirmation",true).put("roundTripMatched",true)
            .put("tamperRejected",true).put("rejectionInvalidatedConfirmation",true).put("cardApproved",false).toString(2))

        assertTrue("Gray basemap must not imply work status", result.roads.all {
            it.status == "context" && it.role == "context"
        })
        assertFalse("The source cannot register without road and boundary review",
            OutlinedNativeReview.from(result).complete(result.roads))
    }

    @Test fun aOrR296MustNotResolveItsConflictingIdentitySilently() {
        val result = inspect("296-conflict", "phase7-02-26179.jpg",
            "a2274a530803efd6d8684ed5c202771d94b53ffe937ce64b2cc7b20ab174d439")
        if (result != null) assertFalse("Unreviewed 296 source was marked ready",
            result.outlined?.let { OutlinedNativeReview.from(result).complete(result.roads) } ?: false)
    }

    @Test fun unidentifiedNeighborhoodCannotAcquireAnInventedIdentity() {
        val result = inspect("unidentified", "phase7-01-26588.jpg",
            "9b6d18b5fa608bb95b3fe7c995775b8bb695e533a8dd3ff073f097184ab60180")
        if (result != null) assertFalse("Unreviewed unidentified source was marked ready",
            result.outlined?.let { OutlinedNativeReview.from(result).complete(result.roads) } ?: false)
    }

    @Test fun clippedOccludedSourceCannotProduceAnApprovedAssignment() {
        val result = inspect("clipped", "phase7-04-26215.jpg",
            "122322dc29130ff1cd37c6884b7f27cb8b8c7be1616ce6d77c5e3c85ccbc3190")
        assertNull("Clipped source must never receive a completed outline proposal",result)
        val report=JSONObject(File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,"phase7-clipped-intake.json").readText())
        assertTrue(report.getString("decoderError").contains("touches the image edge"))
    }
}

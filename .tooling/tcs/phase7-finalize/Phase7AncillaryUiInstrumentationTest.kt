package com.koenterprises.territorycardstudio

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.koenterprises.territorycardstudio.core.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.UUID

/** Synthetic, unapproved editor regression only. Does not commission A265 or replace the
 * four real-map Phase 7 acceptance cases. Run on phone/wide in both themes before UI approval. */
@RunWith(AndroidJUnit4::class)
class Phase7AncillaryUiInstrumentationTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private val screen="native-authoring-screen"
    private val viewport get()=InstrumentationRegistry.getArguments().getString("phase7Viewport","phone")
    private fun click(tag:String) {rule.onNodeWithTag(screen).performScrollToNode(hasTestTag(tag));rule.onNodeWithTag(tag).performClick();rule.waitForIdle()}
    private fun field(tag:String,value:String) {rule.onNodeWithTag(screen).performScrollToNode(hasTestTag(tag));rule.onNodeWithTag(tag).performTextReplacement(value);rule.waitForIdle()}
    private fun textClick(value:String) {rule.onNodeWithTag(screen).performScrollToNode(hasText(value));rule.onNodeWithText(value).performClick();rule.waitForIdle()}
    private fun capture(x:Phase6NativeFixture,name:String) {
        rule.runOnUiThread {rule.activity.window.insetsController?.hide(android.view.WindowInsets.Type.ime())}
        rule.waitUntil(10000){!rule.activity.window.decorView.rootWindowInsets.isVisible(android.view.WindowInsets.Type.ime())}
        rule.waitForIdle()
        Phase7ScreenEvidence.capture(File(x.app.filesDir,"phase7-ancillary-$name-$viewport.png"))
    }
    private fun source():ByteArray {
        val doc=PdfDocument()
        try {
            val page=doc.startPage(PdfDocument.PageInfo.Builder(768,480,1).create())
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=12f}
            page.canvas.drawColor(Color.WHITE)
            page.canvas.drawText("SYNTHETIC SOURCE - NOT FOR FIELD USE",30f,35f,paint)
            page.canvas.drawText("Gray structure: ancillary garage. Red residence: 103 excluded.",30f,60f,paint)
            paint.color=Color.rgb(227,231,233);page.canvas.drawRect(260f,130f,335f,200f,paint)
            paint.color=Color.rgb(255,20,53);paint.style=Paint.Style.STROKE;paint.strokeWidth=1f
            page.canvas.drawRect(405f,130f,480f,200f,paint)
            paint.style=Paint.Style.FILL;paint.color=Color.BLACK;page.canvas.drawText("103",430f,168f,paint)
            paint.color=Color.GRAY;paint.strokeWidth=4f;page.canvas.drawLine(240f,260f,710f,260f,paint)
            paint.color=Color.BLACK;page.canvas.drawText("Test Road - context only",380f,250f,paint)
            doc.finishPage(page)
            return java.io.ByteArrayOutputStream().also{doc.writeTo(it)}.toByteArray()
        } finally {doc.close()}
    }
    private fun exercise(theme:AppearanceMode)=Phase6NativeFixture(WorkspaceMode.REGULAR,multiUnit=true).use {x->
        val imported=x.sources.importFromStream(x.slot,"ancillary-test-source.pdf","application/pdf",source().inputStream())
        val road=RoadGeometry("test-road","Test Road","test road","context","context","",false,"termination","termination",4.0,
            listOf(Point2D(240.0,260.0),Point2D(710.0,260.0)))
        val red=x.authoring.sourceBuilding("excluded-test",x.slot.housingType,false,false,listOf("103"),
            listOf(Point2D(405.0,130.0),Point2D(480.0,130.0),Point2D(480.0,200.0),Point2D(405.0,200.0)))
        val assignment=CurrentAuthoritativeAssignmentState(x.id,x.identity,x.identity.canonicalFilename,x.kb.revision,
            "current_authoritative_assignment","0".repeat(64),imported.sourceFilename,"NOT FOR FIELD USE","9/29/2026",
            listOf("Directions: Synthetic editor regression. NOT FOR FIELD USE."),"full_map",x.slot.housingType,
            RenderCoordinateSpace.LOCKED_R48_PAGE_POINTS_TOP_ORIGIN,listOf(road),listOf(red))
        val facts=NativeSourceReconciliation(x.id,x.mode.name,x.kb.revision,imported.sha256,x.slot.referenceSha256,"current_assignment_map",
            "Synthetic regression reviewer",Instant.now().toString(),NativeSourceReconciliationContract.assignmentContentSha256(assignment),null,
            false,false,false,false,
            listOf(SourceSegmentObservation(road.segmentId,road.name,road.status,road.role,road.insideSide,road.accessOnly,road.endpointAKind,road.endpointBKind,"Synthetic source context road; not registered",false)),
            listOf(SourceBuildingObservation(red.buildingId,red.sourceMembers,false,"Synthetic source red residence; not registered",false)),UUID.randomUUID().toString(),null)
        val initial=x.drafts.save(NativeAuthoringDraft(assignment,facts,VerificationJurisdiction("Oakland County","Michigan","United States"),emptyList()),null)
        rule.setContent {TerritoryCardStudioTheme(theme){Surface(Modifier.safeDrawingPadding()) {
            NativeAuthoringScreen(Modifier,x.slot,x.mode,x.kb,x.drafts,x.authoring,x.sources,onRegisteredImport={},onBack={})
        }}}
        rule.waitUntil(30000){rule.onAllNodesWithTag("native-tab-Buildings").fetchSemanticsNodes().isNotEmpty()}
        click("native-tab-Buildings")
        field("native-points","260,130;335,130;335,200;260,200");textClick("Apply points")
        field("native-item-id","garage-test");field("native-building-members","101")
        click("native-building-assigned");click("native-building-ancillary")
        rule.onNodeWithTag("native-building-members").assertTextContains("101",substring=true)
        rule.onNodeWithTag("native-building-assigned").assertIsOn()
        field("native-evidence-note","Synthetic page 1 gray garage. Intentionally conflicting inputs must not be silently cleared.")
        click("native-item-confirmed");click("native-save-item")
        rule.onNodeWithTag(screen).performScrollToIndex(0)
        rule.onNodeWithTag("native-status").assertTextContains("Ancillary context",substring=true)
        assertEquals(initial,x.drafts.read(x.id,x.mode))
        click("native-building-assigned");field("native-building-members","")
        click("native-item-confirmed");click("native-save-item")
        val saved=requireNotNull(x.drafts.read(x.id,x.mode))
        val gray=saved.assignment.buildings.single{it.buildingId=="garage-test"}
        assertEquals("ancillary",gray.housingType);assertFalse(gray.assigned);assertTrue(gray.sourceMembers.isEmpty())
        assertEquals(red,saved.assignment.buildings.single{it.buildingId==red.buildingId})
        assertFalse(saved.reconciliation.sourceCoverageComplete)
        assertEquals(saved,AndroidNativeDraftStore(x.app,x.kb,x.sources,File(x.root,"native")).read(x.id,x.mode))
        assertTrue(runCatching{x.drafts.register(x.id,x.mode,saved.revisionSha256)}.isFailure)
        textClick("Edit garage-test")
        rule.onNodeWithTag(screen).performScrollToNode(hasTestTag("native-building-ancillary"))
        rule.onNodeWithTag("native-building-ancillary").assertIsOn()
        click("native-item-confirmed");click("native-building-ancillary")
        rule.onNodeWithTag(screen).performScrollToNode(hasTestTag("native-item-confirmed"))
        rule.onNodeWithTag("native-item-confirmed").assertIsOff()
        assertEquals(saved,x.drafts.read(x.id,x.mode)) // Editing is not an implicit save.
        capture(x,theme.name.lowercase()+"-kind-edit")
        rule.onNodeWithTag(screen).performScrollToNode(hasTestTag("native-trace"))
        capture(x,theme.name.lowercase()+"-trace")
        File(x.app.filesDir,"phase7-ancillary-${theme.name.lowercase()}-$viewport.json").writeText(JSONObject()
            .put("testOnly",true).put("cardApproved",false).put("conflictingFactsRetainedAndRejected",true)
            .put("explicitKindPersisted",true).put("redResidenceUnchanged",true).put("kindEditResetsConfirmation",true)
            .put("nativeRegistrationStillBlocked",true).put("sourceSha256",imported.sha256).toString(2))
    }
    @Test fun lightThemeExplicitAncillaryKindAndSourceReview()=exercise(AppearanceMode.LIGHT)
    @Test fun darkThemeExplicitAncillaryKindAndSourceReview()=exercise(AppearanceMode.DARK)
}

package com.koenterprises.territorycardstudio

import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.koenterprises.territorycardstudio.core.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Actual A265/source/UI/persistence regression. Comparison geometry is deliberately NOT
 * represented as current, registered, geographically verified or approved card evidence. */
@RunWith(AndroidJUnit4::class)
class Phase7ReferenceComparisonInstrumentationTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private val viewport get()=InstrumentationRegistry.getArguments().getString("phase7Viewport","phone")
    private val id="A265"
    private val sourceHash="8b17f90abfac5afe4e3de08f696494199ae88f97cfbb5d1fb035a0153e08a29d"

    private fun exercise(appearance:AppearanceMode) {
        val app=ApplicationProvider.getApplicationContext<TerritoryCardStudioApplication>()
        val baseline=app.services.knowledgeBase
        val original=baseline.assignments.getValue(id)
        val originalExport=app.services.approvedExport.state(id)
        val sources=SourceMapIntakeStore(app)
        val previous=sources.verifiedRecord(id)
        val previousBytes=previous?.let {sources.verifiedFile(id).readBytes()}
        val root=File(app.cacheDir,"phase7-comparison-${UUID.randomUUID()}").apply {mkdirs()}
        val commissioning=AndroidCommissioningStore(app,baseline,root,sources)
        var candidateRoot:File?=null
        try {
            val imported=sources.importFromStream(original,"exact-a265-comparison.jpg","image/jpeg",
                InstrumentationRegistry.getInstrumentation().context.assets.open("phase7-03-26175.jpg"))
            assertEquals(sourceHash,imported.sha256)
            val scope=commissioning.start(id,sourceHash,"Instrumentation reviewer",
                "Test-only A265 comparison persistence; no current geometry or field approval",id,true,null)
            candidateRoot=File(app.noBackupFilesDir,"territory-card-studio/commissioned/${scope.sha256}")
            val services=TerritoryCardStudioServices(app,scope,commissioning)
            val slot=services.knowledgeBase.assignments.getValue(id)
            val proposed=NativeReferenceComparison.propose(slot)
            assertEquals(6,proposed.buildings.size)
            assertTrue(proposed.textChanges.isNotEmpty())
            rule.setContent {TerritoryCardStudioTheme(appearance) {Surface(Modifier.safeDrawingPadding()) {
                NativeAuthoringScreen(Modifier,slot,WorkspaceMode.REGULAR,services.knowledgeBase,
                    services.nativeDrafts,services.nativeAuthoring,services.sourceMapStore(),{}, {})
            }}}
            val screen=rule.onNodeWithTag("native-authoring-screen")
            fun fill(tag:String,text:String) {
                screen.performScrollToNode(hasTestTag(tag))
                rule.onNodeWithTag(tag).performTextReplacement(text)
            }
            fill("native-locality","Rochester Hills")
            fill("native-updated","9/28/2026")
            fill("native-directions","Directions: Test-only reference comparison; not for field use.")
            fill("native-author","Instrumentation reviewer")
            fill("native-county","Oakland County")
            fill("native-state","Michigan")
            fill("native-country","United States")
            screen.performScrollToNode(hasTestTag("native-seed-reference-comparison"))
            rule.waitUntil(30000) {runCatching {rule.onNodeWithTag("native-seed-reference-comparison").assertIsEnabled();true}.getOrDefault(false)}
            rule.onNodeWithTag("native-seed-reference-comparison").performClick()
            screen.performScrollToIndex(0)
            rule.onNodeWithTag("native-status").assertTextContains("Nothing was accepted as current",substring=true)
            rule.onNodeWithTag("native-tab-Review").performClick()
            screen.performScrollToNode(hasTestTag("native-save-draft"))
            rule.onNodeWithTag("native-save-draft").assertIsEnabled().performClick()
            screen.performScrollToIndex(0)
            rule.onNodeWithTag("native-status").assertTextContains("Draft saved",substring=true)
            val saved=requireNotNull(services.nativeDrafts.read(id,WorkspaceMode.REGULAR))
            assertEquals(sourceHash,saved.reconciliation.importedSourceSha256)
            assertEquals(proposed.buildings,saved.assignment.buildings)
            assertEquals(proposed.roads,saved.assignment.roads)
            assertEquals(proposed.buildingObservations,saved.reconciliation.buildings)
            assertEquals(proposed.segments,saved.reconciliation.segments)
            assertFalse(saved.reconciliation.sourceCoverageComplete)
            assertFalse(saved.reconciliation.explicitAssignmentConfirmation)
            assertTrue(saved.reconciliation.buildings.none {it.confirmed || it.supplementalReference!=null})
            assertTrue(saved.reconciliation.segments.none {it.confirmed})
            assertTrue("An unconfirmed comparison must never register",runCatching {
                services.nativeDrafts.register(id,WorkspaceMode.REGULAR,saved.revisionSha256)
            }.isFailure)
            val reopened=TerritoryCardStudioServices(app,scope,commissioning)
            assertEquals(saved,reopened.nativeDrafts.read(id,WorkspaceMode.REGULAR))
            assertFalse(reopened.buildWorkflow.state(id,WorkspaceMode.REGULAR).canBuild)
            assertEquals(original,app.services.knowledgeBase.assignments.getValue(id))
            assertEquals(originalExport,app.services.approvedExport.state(id))
            rule.runOnUiThread {rule.activity.window.insetsController?.hide(WindowInsets.Type.ime())}
            rule.waitUntil(10000) {!rule.activity.window.decorView.rootWindowInsets.isVisible(WindowInsets.Type.ime())}
            rule.waitForIdle()
            val name="phase7-reference-comparison-${appearance.name.lowercase()}-$viewport"
            Phase7ScreenEvidence.capture(File(app.filesDir,"$name.png"))
            File(app.filesDir,"$name.json").writeText(JSONObject()
                .put("sourceSha256",sourceHash).put("territory",id).put("viewport",viewport)
                .put("buildingCount",saved.assignment.buildings.size).put("punctuationChanges",proposed.textChanges.size)
                .put("exactDraftReadback",true).put("registrationBlocked",true).put("originalUnchanged",true)
                .put("testOnly",true).put("cardApproved",false).toString(2))
        } finally {
            val head=commissioning.headSha256(id)
            if(head!=null && commissioning.recordedScope(id)!=null)commissioning.close(id,"Instrumentation reviewer",head)
            candidateRoot?.deleteRecursively();root.deleteRecursively()
            if(previous==null)sources.clear(id) else sources.importFromStream(original,previous.sourceFilename,
                previous.mimeType,requireNotNull(previousBytes).inputStream(),previous.importedAtUtc)
        }
    }
    @Test fun lightThemeSavesAndResumesSixUnconfirmedBuildings()=exercise(AppearanceMode.LIGHT)
    @Test fun darkThemeSavesAndResumesSixUnconfirmedBuildings()=exercise(AppearanceMode.DARK)
}

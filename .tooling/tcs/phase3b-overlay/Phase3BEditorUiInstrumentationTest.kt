package com.koenterprises.territorycardstudio

import android.graphics.Bitmap
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.koenterprises.territorycardstudio.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class Phase3BEditorUiInstrumentationTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private class Fixture(phone:Boolean=false,empty:Boolean=false):AutoCloseable {
        val f=Phase2DBFixture(phone)
        val root=File(f.root,"drafts")
        val roads=if(empty) emptyList() else f.input.assignment.roads
        val buildings=if(empty) emptyList() else listOf(BuildingGeometry("building-1","1","apartment",true,"",listOf("1"),emptyList(),listOf(Point2D(0.0,0.0),Point2D(1.0,0.0),Point2D(0.0,1.0))))
        val slot=f.slot.copy(roadCount=roads.size,namedRoadCount=roads.map{it.name}.distinct().size,roadNameInventory=roads.map{it.name},buildingCount=buildings.size,roads=roads,buildings=buildings)
        val kb=f.kb.copy(assignments=f.kb.assignments+(f.identity.displayId to slot))
        val id=f.identity.displayId
        val mode=f.mode
        val store=AndroidEditingDraftStore(root,kb,f.sources)
        fun read()=store.read(id,mode)!!
        fun edit(label:String="Alpha Road")=DraftLabelEdit(DraftLabelKind.ROAD,"adapter-alpha","Alpha Rd",label,"Checked against imported source",f.source.sha256)
        fun building()=DraftLabelEdit(DraftLabelKind.BUILDING,"building-1","1","Building 1","Source building label",f.source.sha256)
        fun seed() { val d=store.create(id,mode);store.save(id,mode,d.latest.token,listOf(edit(),building())) }
        override fun close()=f.close()
    }
    @Composable private fun Content(x:Fixture,theme:AppearanceMode=AppearanceMode.LIGHT,onBack:()->Unit={}) {
        TerritoryCardStudioTheme(theme) { Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            DraftEditorScreen(Modifier.fillMaxSize().safeDrawingPadding(),x.slot,x.mode,x.store,x.f.sources,onBack)
        } }
    }
    private fun show(x:Fixture) { rule.setContent { Content(x) };ready() }
    private fun wait(tag:String) { rule.waitUntil(20000) {rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()};rule.waitForIdle() }
    private fun ready() { wait("draft-status");rule.waitUntil(20000) { runCatching {rule.onNodeWithTag("draft-status").assertTextContains("Checking",substring=true)}.isFailure };rule.waitForIdle() }
    private fun scroll(tag:String) { rule.onNodeWithTag("draft-editor").performScrollToNode(hasTestTag(tag)) }
    private fun click(tag:String) {scroll(tag);rule.onNodeWithTag(tag).performClick();rule.waitForIdle()}
    private fun dialog(tag:String) {wait(tag);rule.onNodeWithTag(tag).performClick();rule.waitForIdle()}
    private fun select(kind:String="ROAD",id:String="adapter-alpha") {click("draft-tab-Items");click("draft-item-$kind-$id")}
    private fun type(tag:String,value:String) { scroll(tag);rule.onNodeWithTag(tag).performTextReplacement(value) }
    private fun propose(x:Fixture,label:String="Alpha Road",reason:String="Checked against imported source") {
        type("draft-proposed",label);type("draft-rationale",reason);click("draft-evidence-"+x.f.source.sha256)
    }
    private fun saved(x:Fixture,n:Int) {rule.waitUntil(20000){runCatching{x.read().latest.number==n}.getOrDefault(false)};rule.waitUntil(20000){rule.onAllNodesWithTag("draft-busy").fetchSemanticsNodes().isEmpty()};ready()}
    private fun resumeWith(change:()->Unit) {rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED);change();rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED);ready()}

    @Test fun workspaceEditsPreserveOthersAndSupportHistory() {
        Fixture().use { x ->
            x.f.prepare();x.f.build();x.f.page2();val before=x.f.state()
            val item=TerritoryDashboardModel.from(x.kb).items.first{it.assignment.displayId==x.id}
            val preview=AndroidPdfPreviewService(x.f.coordinator,File(x.f.root,"preview"),x.f.service)
            rule.setContent {TerritoryCardStudioTheme(AppearanceMode.LIGHT) {Surface(Modifier.fillMaxSize()) {
                ModeAwareTerritoryWorkspace(Modifier.fillMaxSize().safeDrawingPadding(),item,x.kb.revision,{},x.kb,x.f.coordinator,preview,x.store)
            }}}
            wait("territory-workspace")
            rule.onNodeWithTag("territory-workspace").performScrollToNode(hasTestTag("workspace-mode-Letter-Writing"))
            rule.onNodeWithTag("workspace-mode-Letter-Writing").performClick()
            rule.onNodeWithTag("territory-workspace").performScrollToNode(hasTestTag("workspace-draft-editor"))
            rule.onNodeWithTag("workspace-draft-editor").performClick();ready()
            click("draft-create");saved(x,0);select();propose(x);click("draft-save");saved(x,1)
            select("BUILDING","building-1");propose(x,"Building 1");click("draft-save");saved(x,2)
            assertEquals(2,x.read().latest.edits.size)
            click("draft-remove-ROAD-adapter-alpha");dialog("draft-cancel");assertEquals(2,x.read().latest.edits.size)
            click("draft-remove-ROAD-adapter-alpha");dialog("draft-confirm");saved(x,3)
            assertEquals(DraftLabelKind.BUILDING,x.read().latest.edits.single().kind)
            click("draft-tab-History");click("draft-restore-2");dialog("draft-confirm");saved(x,4)
            assertEquals(2,x.read().latest.edits.size);assertEquals(2,x.read().latest.restoredFrom)
            click("draft-discard");dialog("draft-cancel");assertEquals(4,x.read().latest.number)
            click("draft-back");wait("territory-workspace")
            rule.onNodeWithTag("territory-workspace").performScrollToNode(hasTestTag("workspace-draft-editor"))
            rule.onNodeWithTag("workspace-draft-editor").performClick();ready()
            click("draft-tab-Review");scroll("draft-review-BUILDING-building-1");rule.onNodeWithTag("draft-review-BUILDING-building-1").assertExists()
            click("draft-discard");dialog("draft-confirm");wait("draft-create")
            assertNull(x.store.read(x.id,x.mode));assertEquals(before,x.f.state())
        }
    }
    @Test fun dirtyInputGuardsItemAndBothBackRoutes() {
        Fixture().use { x ->
            x.seed();var left=false;rule.setContent {Content(x,onBack={left=true})};ready()
            select();type("draft-proposed","Unsaved Road")
            click("draft-tab-Items");click("draft-item-BUILDING-building-1");dialog("draft-cancel")
            click("draft-tab-Edit");scroll("draft-proposed");rule.onNodeWithTag("draft-proposed").assertTextContains("Unsaved Road")
            click("draft-back");dialog("draft-cancel");assertFalse(left)
            rule.runOnIdle {rule.activity.onBackPressedDispatcher.onBackPressed()};dialog("draft-cancel");assertFalse(left)
            click("draft-back");dialog("draft-confirm");assertTrue(left);assertEquals("Alpha Road",x.read().latest.edits.first().proposed)
        }
    }
    @Test fun restorationRetainsDirtyFieldsAndOriginalToken() {
        Fixture().use { x ->
            x.seed();val restoration=StateRestorationTester(rule);restoration.setContent {Content(x)};ready()
            select();propose(x,"My unsaved road","My unsaved reason")
            x.store.save(x.id,x.mode,x.read().latest.token,listOf(x.edit("External Road"),x.building()))
            restoration.emulateSavedInstanceStateRestore();ready()
            scroll("draft-conflict");rule.onNodeWithTag("draft-conflict").assertExists()
            scroll("draft-proposed");rule.onNodeWithTag("draft-proposed").assertTextContains("My unsaved road")
            scroll("draft-rationale");rule.onNodeWithTag("draft-rationale").assertTextContains("My unsaved reason")
            scroll("draft-save");rule.onNodeWithTag("draft-save").assertIsNotEnabled()
            click("draft-reload");dialog("draft-cancel");scroll("draft-proposed");rule.onNodeWithTag("draft-proposed").assertTextContains("My unsaved road")
            click("draft-reload");dialog("draft-confirm");ready();select()
            scroll("draft-proposed");rule.onNodeWithTag("draft-proposed").assertTextContains("External Road")
        }
    }
    @Test fun confirmationsRejectRevisionChangedWhileOpenAndCleanFormRefreshes() {
        Fixture().use { x ->
            x.seed();show(x);select()
            resumeWith { x.store.save(x.id,x.mode,x.read().latest.token,listOf(x.edit("New Road"),x.building())) }
            scroll("draft-proposed");rule.onNodeWithTag("draft-proposed").assertTextContains("New Road")
            click("draft-tab-Review")
            for(action in listOf("draft-remove-ROAD-adapter-alpha","draft-discard","draft-restore-0")) {
                if(action.startsWith("draft-restore"))click("draft-tab-History") else click("draft-tab-Review")
                click(action);wait("draft-confirm")
                var expected:EditingDraft?=null
                rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
                expected=x.store.save(x.id,x.mode,x.read().latest.token,listOf(x.edit("External "+x.read().latest.number),x.building()))
                rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED);rule.waitForIdle()
                dialog("draft-confirm");wait("draft-message")
                rule.waitUntil(20000) { runCatching {rule.onNodeWithTag("draft-message").assertTextContains("Draft changed",substring=true)}.isSuccess }
                assertEquals(expected,x.read())
            }
        }
    }
    @Test fun lifecycleSourceChangeAndCorruptDraftFailClosed() {
        Fixture().use { x ->
            x.seed();show(x);select();propose(x,"Unsaved Road")
            resumeWith {x.f.importSource("changed")}
            scroll("draft-status");rule.onNodeWithTag("draft-status").assertTextContains("Stale",substring=true)
            scroll("draft-save");rule.onNodeWithTag("draft-save").assertIsNotEnabled()
            resumeWith {x.root.listFiles()!!.single{it.extension=="json"}.writeText("corrupt")}
            scroll("draft-read-error");rule.onNodeWithTag("draft-read-error").assertExists()
            rule.onNodeWithTag("draft-create").assertDoesNotExist()
            assertEquals("corrupt",x.root.listFiles()!!.single{it.extension=="json"}.readText())
            scroll("draft-dirty");rule.onNodeWithTag("draft-dirty").assertExists()
        }
    }
    @Test fun missingSourceAndEmptyItemsStayHonest() {
        Fixture(empty=true).use {x ->
            x.f.sources.clear(x.id);show(x)
            scroll("draft-source-missing");rule.onNodeWithTag("draft-source-missing").assertExists()
            scroll("draft-no-items");rule.onNodeWithTag("draft-no-items").assertExists()
            scroll("draft-create");rule.onNodeWithTag("draft-create").assertIsNotEnabled()
            resumeWith {x.f.importSource("restored")}
            scroll("draft-create");rule.onNodeWithTag("draft-create").assertIsNotEnabled()
            assertNull(x.store.read(x.id,x.mode))
        }
    }
    @Test fun telephoneModeBindsCorrectDraft() {
        Fixture(phone=true).use {x ->
            show(x);click("draft-create");saved(x,0);select("BUILDING","building-1");propose(x,"Tower One");click("draft-save");saved(x,1)
            assertEquals(WorkspaceMode.TELEPHONE,x.read().mode);assertEquals(x.id,x.read().territoryId)
            assertEquals("Tower One",x.read().latest.edits.single().proposed);assertFalse(x.read().grantsAuthority)
        }
    }
    @Test fun invalidInputKeepsDraftAndInput() {
        Fixture().use {x ->
            x.seed();show(x);select();propose(x," bad ")
            click("draft-save");wait("draft-message")
            rule.waitUntil(20000){runCatching{rule.onNodeWithTag("draft-busy").assertDoesNotExist()}.isSuccess}
            scroll("draft-proposed");rule.onNodeWithTag("draft-proposed").assertTextContains(" bad ")
            assertEquals(1,x.read().latest.number)
            type("draft-proposed","");scroll("draft-save");rule.onNodeWithTag("draft-save").assertIsNotEnabled()
            type("draft-proposed","x".repeat(257));scroll("draft-proposed");rule.onNodeWithTag("draft-proposed").assertTextContains("")
            assertEquals(1,x.read().latest.number)
        }
    }
    private fun shot(name:String) {
        var old:Bitmap?=null;var accepted:Bitmap?=null
        try {
            rule.waitUntil(20000) {
                rule.waitForIdle();rule.onRoot().captureToImage()
                val b=requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
                var stable=old!=null && old!!.width==b.width && old!!.height==b.height
                if(stable)for(y in 80 until b.height-48 step 8)for(x in 0 until b.width step 8)if(old!!.getPixel(x,y)!=b.getPixel(x,y))stable=false
                old?.recycle();old=b
                if(stable){accepted=b;old=null;true}else false
            }
            val b=requireNotNull(accepted);val wide=rule.activity.resources.configuration.screenWidthDp>=840
            File(rule.activity.filesDir,"phase3b-${if(wide)"wide-" else ""}$name.png").outputStream().use{assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))}
        }finally{old?.recycle();accepted?.recycle()}
    }
    @Test fun captureLight()=capture(AppearanceMode.LIGHT)
    @Test fun captureDark()=capture(AppearanceMode.DARK)
    private fun capture(theme:AppearanceMode) {
        Fixture().use {x ->
            x.seed()
            rule.activity.runOnUiThread {rule.activity.enableEdgeToEdge()}
            rule.setContent {Content(x,theme)};ready()
            rule.runOnIdle {
                val mask=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                rule.activity.window.insetsController?.setSystemBarsAppearance(if(theme==AppearanceMode.LIGHT)mask else 0,mask)
            }
            select();propose(x,"Alpha Road — North Entrance and Apartment Courtyard","The imported source labels the north entrance and apartment courtyard as part of Alpha Road. Retain the locked geometry and assignment until this proposal is independently validated.")
            click("draft-evidence-"+x.f.source.sha256)
            // Hide IME through normal tab navigation while retaining unsaved input.
            click("draft-tab-Review");click("draft-tab-Edit");scroll("draft-proposed")
            shot("editor-"+theme.name.lowercase())
            if(rule.activity.resources.configuration.screenWidthDp<840) {
                scroll("draft-rationale");rule.onNodeWithTag("draft-rationale").performClick()
                rule.waitForIdle();shot("keyboard-"+theme.name.lowercase())
                click("draft-tab-Review");click("draft-tab-Edit")
            }
            click("draft-save");saved(x,2);scroll("draft-review-ROAD-adapter-alpha");shot("review-"+theme.name.lowercase())
            click("draft-tab-History");scroll("draft-restore-1");shot("history-"+theme.name.lowercase())
        }
    }
}

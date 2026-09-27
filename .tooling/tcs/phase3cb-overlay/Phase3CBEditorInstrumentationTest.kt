package com.koenterprises.territorycardstudio

import android.graphics.Bitmap
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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
class Phase3CBEditorInstrumentationTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    @Composable private fun Content(x:Phase3CBFixture,theme:AppearanceMode=AppearanceMode.LIGHT,onBack:()->Unit={}) {
        TerritoryCardStudioTheme(theme){Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){ExtendedDraftEditorScreen(Modifier.fillMaxSize().safeDrawingPadding(),x.id,x.mode,x.store,onBack)}}
    }
    private fun ready() {scroll("extended-status");rule.waitUntil(20000){rule.onAllNodesWithTag("extended-status").fetchSemanticsNodes().singleOrNull()?.config?.getOrNull(SemanticsProperties.Text)?.joinToString()?.let {!it.contains("Loading")}==true};rule.waitForIdle()}
    private fun scroll(tag:String) {rule.waitUntil(20000){runCatching{rule.onNodeWithTag("extended-editor").performScrollToNode(hasTestTag(tag))}.isSuccess}}
    private fun click(tag:String) {scroll(tag);rule.waitUntil(20000){runCatching{rule.onNodeWithTag(tag).assertIsEnabled()}.isSuccess};rule.onNodeWithTag(tag).performClick();rule.waitForIdle()}
    private fun dialog(tag:String) {rule.onNodeWithTag(tag).performClick();rule.waitForIdle()}
    private fun type(index:Int,value:String) {scroll("extended-field-$index");rule.onNodeWithTag("extended-field-$index").performTextReplacement(value)}
    private fun select(kind:ExtendedKind,id:String) {click("extended-tab-Items");click("extended-item-${kind.name}:$id")}
    private fun evidence(x:Phase3CBFixture,kind:ExtendedKind,id:String) {val e=x.catalog().items.single {it.kind==kind && it.id==id}.evidence.first();click("extended-evidence-${e.role}-${e.sha256}")}
    private fun reason() {scroll("extended-reason");rule.onNodeWithTag("extended-reason").performTextReplacement("Checked source reference; independent reconciliation required")}
    private fun saved(x:Phase3CBFixture,n:Int) {rule.waitUntil(20000){runCatching{x.draft().latest.number==n}.getOrDefault(false)};rule.waitUntil(20000){rule.onAllNodesWithTag("extended-busy").fetchSemanticsNodes().isEmpty()};ready()}
    private fun commit(x:Phase3CBFixture,kind:ExtendedKind,id:String,n:Int) {reason();evidence(x,kind,id);click("extended-save");saved(x,n)}
    @Test fun workspaceRouteEditsAllLetterCategoriesAndPreservesOtherProposals() {
        Phase3CBFixture().use {x->val before=x.f.state();val item=TerritoryDashboardModel.from(x.kb).items.first {it.assignment.displayId==x.id}
            rule.setContent {TerritoryCardStudioTheme(AppearanceMode.LIGHT){Surface(Modifier.fillMaxSize()){ModeAwareTerritoryWorkspace(Modifier.fillMaxSize().safeDrawingPadding(),item,x.kb.revision,{},x.kb,x.f.coordinator,extendedDraftStore=x.store)}}}
            rule.onNodeWithTag("territory-workspace").performScrollToNode(hasTestTag("workspace-mode-Letter-Writing"));rule.onNodeWithTag("workspace-mode-Letter-Writing").performClick()
            rule.onNodeWithTag("territory-workspace").performScrollToNode(hasTestTag("workspace-extended-editor"));rule.onNodeWithTag("workspace-extended-editor").performClick();ready();click("extended-create");saved(x,0)
            select(ExtendedKind.ROAD_PATH,"adapter-alpha");type(0,"227");commit(x,ExtendedKind.ROAD_PATH,"adapter-alpha",1)
            select(ExtendedKind.ROAD_WORK,"adapter-alpha");click("extended-work-yellow-right");commit(x,ExtendedKind.ROAD_WORK,"adapter-alpha",2)
            select(ExtendedKind.BUILDING_PATH,"building-1");type(0,"302");commit(x,ExtendedKind.BUILDING_PATH,"building-1",3)
            select(ExtendedKind.BUILDING_MEMBERS,"building-1");type(1,"2");type(2,"2");commit(x,ExtendedKind.BUILDING_MEMBERS,"building-1",4)
            select(ExtendedKind.LETTER_ADDRESS,"address-1");type(0,"102 Verified Example Way");commit(x,ExtendedKind.LETTER_ADDRESS,"address-1",5)
            assertEquals(5,x.draft().latest.proposals.size);click("extended-tab-History");click("extended-restore-1");dialog("extended-confirm");saved(x,6);assertEquals(1,x.draft().latest.proposals.size)
            click("extended-tab-History");click("extended-restore-5");dialog("extended-confirm");saved(x,7);assertEquals(5,x.draft().latest.proposals.size)
            click("extended-back");rule.onNodeWithTag("territory-workspace").assertExists();assertEquals(before,x.f.state())
        }
    }
    @Test fun phoneFormsPreserveUnavailableAndUnknownWithoutApproval() {
        Phase3CBFixture(true).use {x->x.create();rule.setContent {Content(x)};ready()
            select(ExtendedKind.PHONE_ADDRESS,"phone-1");type(0,"102 Verified Example Way");commit(x,ExtendedKind.PHONE_ADDRESS,"phone-1",1)
            select(ExtendedKind.PHONE_NUMBER,"phone-2");scroll("extended-field-1");rule.onNodeWithTag("extended-field-1").assertIsNotEnabled()
            click("extended-phone-UNKNOWN");commit(x,ExtendedKind.PHONE_NUMBER,"phone-2",2)
            assertEquals(ProposedPhoneState.UNKNOWN,(x.draft().latest.proposals.last().proposed as PhoneValue).state);assertFalse(x.draft().grantsAuthority)
            click("extended-remove-PHONE_NUMBER:phone-2");dialog("extended-cancel");assertEquals(2,x.draft().latest.proposals.size)
            click("extended-remove-PHONE_NUMBER:phone-2");dialog("extended-confirm");saved(x,3);assertEquals(1,x.draft().latest.proposals.size)
        }
    }
    @Test fun dirtyRecreationAndBothBackRoutesRetainOriginalToken() {
        Phase3CBFixture().use {x->x.seed();var left=false;val restoration=StateRestorationTester(rule);restoration.setContent {Content(x,onBack={left=true})};ready()
            select(ExtendedKind.LETTER_ADDRESS,"address-1");type(0,"103 Unsaved Way")
            click("extended-back");dialog("extended-cancel");assertFalse(left)
            rule.runOnIdle {rule.activity.onBackPressedDispatcher.onBackPressed()};dialog("extended-cancel");assertFalse(left)
            val d=x.draft();x.store.save(x.id,x.mode,d.latest.token,d.latest.proposals.dropLast(1));restoration.emulateSavedInstanceStateRestore();ready()
            scroll("extended-conflict");rule.onNodeWithTag("extended-conflict").assertExists();scroll("extended-field-0");rule.onNodeWithTag("extended-field-0").assertTextContains("103 Unsaved Way");scroll("extended-save");rule.onNodeWithTag("extended-save").assertIsNotEnabled()
            click("extended-reload");dialog("extended-cancel");scroll("extended-field-0");rule.onNodeWithTag("extended-field-0").assertTextContains("103 Unsaved Way")
            click("extended-reload");dialog("extended-confirm");ready();select(ExtendedKind.LETTER_ADDRESS,"address-1");scroll("extended-field-0");rule.onNodeWithTag("extended-field-0").assertTextContains("100 Example Way")
        }
    }
    @Test fun confirmationRaceAndOversizedGeometryFailWithoutLosingInput() {
        Phase3CBFixture().use {x->x.seed();rule.setContent {Content(x)};ready()
            select(ExtendedKind.ROAD_PATH,"adapter-alpha");type(0,"1e300");scroll("extended-input-error");rule.onNodeWithTag("extended-input-error").assertExists();scroll("extended-save");rule.onNodeWithTag("extended-save").assertIsNotEnabled()
            click("extended-tab-Items");click("extended-item-BUILDING_MEMBERS:building-1");dialog("extended-cancel");click("extended-tab-Edit");scroll("extended-field-0");rule.onNodeWithTag("extended-field-0").assertTextContains("1e300")
            click("extended-tab-History");click("extended-restore-0");val d=x.draft();x.store.save(x.id,x.mode,d.latest.token,d.latest.proposals.dropLast(1));dialog("extended-confirm");ready()
            scroll("extended-message");rule.onNodeWithTag("extended-message").assertTextContains("Draft changed");assertEquals(2,x.draft().latest.number)
            click("extended-reload");dialog("extended-confirm");ready();click("extended-discard");dialog("extended-confirm");rule.waitUntil(20000){x.store.read(x.id,x.mode)==null};ready();rule.onNodeWithTag("extended-create").assertExists()
        }
    }
    @Test fun missingInventoryStaleAndCorruptStatesRemainBlocked() {
        Phase3CBFixture(inventory=false).use {x->x.create();rule.setContent {Content(x)};ready();scroll("extended-no-inventory");rule.onNodeWithTag("extended-no-inventory").assertExists()
            x.f.importSource("changed");rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED);rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED);ready();scroll("extended-status");rule.onNodeWithTag("extended-status").assertTextContains("Stale")
            x.root.listFiles()!!.single {it.extension=="json"}.appendText("bad");click("extended-reload");ready();scroll("extended-error");rule.onNodeWithTag("extended-error").assertExists();rule.onNodeWithTag("extended-create").assertDoesNotExist()
        }
    }
    private fun shot(name:String) {
        var old:Bitmap?=null;var accepted:Bitmap?=null
        try {rule.waitUntil(20000) {rule.waitForIdle();val b=requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot());var stable=old!=null && old!!.width==b.width && old!!.height==b.height
            if(stable)for(y in 80 until b.height-48 step 8)for(x in 0 until b.width step 8)if(old!!.getPixel(x,y)!=b.getPixel(x,y))stable=false
            old?.recycle();old=b;if(stable){accepted=b;old=null;true}else false}
            val b=requireNotNull(accepted);val wide=rule.activity.resources.configuration.screenWidthDp>=840
            File(rule.activity.filesDir,"phase3cb-${if(wide)"wide-" else ""}$name.png").outputStream().use {assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))}
        }finally {old?.recycle();accepted?.recycle()}
    }
    @Test fun captureLight()=capture(AppearanceMode.LIGHT)
    @Test fun captureDark()=capture(AppearanceMode.DARK)
    private fun capture(theme:AppearanceMode) {
        Phase3CBFixture().use {letter->Phase3CBFixture(true).use {phone->
            letter.seed();phone.seed();var phoneMode by mutableStateOf(false)
            rule.activity.runOnUiThread {rule.activity.enableEdgeToEdge()};rule.setContent {Content(if(phoneMode)phone else letter,theme)};ready()
            rule.runOnIdle {val mask=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;rule.activity.window.insetsController?.setSystemBarsAppearance(if(theme==AppearanceMode.LIGHT)mask else 0,mask)}
            val suffix=theme.name.lowercase()
            select(ExtendedKind.ROAD_PATH,"adapter-alpha");scroll("extended-map-preview");shot("geometry-preview-$suffix");scroll("extended-field-0");shot("geometry-form-$suffix")
            select(ExtendedKind.BUILDING_MEMBERS,"building-1");scroll("extended-map-preview");shot("building-preview-$suffix");scroll("extended-field-1");shot("building-form-$suffix")
            select(ExtendedKind.LETTER_ADDRESS,"address-1");scroll("extended-field-0");shot("letter-form-$suffix")
            if(rule.activity.resources.configuration.screenWidthDp<840) {
                scroll("extended-reason");rule.onNodeWithTag("extended-reason").performClick();rule.onNodeWithTag("extended-reason").assertIsFocused()
                rule.runOnIdle {val view=requireNotNull(rule.activity.currentFocus);val imm=rule.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager;imm.showSoftInput(view,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);rule.activity.window.insetsController?.show(android.view.WindowInsets.Type.ime())}
                rule.waitUntil(20000){rule.activity.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime())==true};shot("keyboard-$suffix")
            }
            click("extended-tab-Review");scroll("extended-review-count");shot("review-$suffix")
            click("extended-tab-History");scroll("extended-restore-0");shot("history-$suffix")
            rule.runOnIdle {phoneMode=true};ready();select(ExtendedKind.PHONE_NUMBER,"phone-2");scroll("extended-phone-UNAVAILABLE");shot("phone-form-$suffix")
        }}
    }
}

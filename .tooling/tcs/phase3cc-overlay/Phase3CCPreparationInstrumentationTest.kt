package com.koenterprises.territorycardstudio

import android.graphics.Bitmap
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class Phase3CCPreparationInstrumentationTest {
    @get:Rule val diagnostics=object:org.junit.rules.TestWatcher(){override fun failed(e:Throwable,d:org.junit.runner.Description){
        val dir=InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        File(dir,"phase3cc-debug-${d.methodName}.txt").writeText(e.stackTraceToString()+"\n"+runCatching{rule.onRoot().printToString()}.getOrDefault("No Compose tree"))
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let {b->File(dir,"phase3cc-debug-${d.methodName}.png").outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
    }}
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    @Composable private fun Content(x:Phase3CCFixture,theme:AppearanceMode=AppearanceMode.LIGHT,onBack:()->Unit={}) {
        TerritoryCardStudioTheme(theme){Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){EditingPreparationScreen(Modifier.fillMaxSize().safeDrawingPadding(),x.id,x.mode,x.authority,x.preparation,onBack)}}
    }
    private fun scroll(tag:String) {rule.waitUntil(20000){runCatching{rule.onNodeWithTag("editing-preparation").performScrollToNode(hasTestTag(tag));rule.waitForIdle();rule.onNodeWithTag(tag).assertExists()}.isSuccess}}
    private fun click(tag:String) {scroll(tag);rule.onNodeWithTag(tag).assertIsEnabled().performClick();rule.waitForIdle()}
    private fun idle() {rule.waitUntil(30000){rule.onAllNodesWithTag("preparation-busy").fetchSemanticsNodes().isEmpty()};rule.waitForIdle()}
    private fun ready(available:Boolean=true) {scroll("preparation-status");rule.waitUntil(20000){runCatching{rule.onNodeWithTag("preparation-status").assertTextEquals(if(available)"Independent facts available" else "Independent facts required")}.isSuccess}}
    private fun validated() {click("preparation-validate");idle();scroll("preparation-ticket");rule.onNodeWithTag("preparation-ticket").assertExists()}
    private fun dialog(tag:String) {rule.onNodeWithTag(tag).performClick();rule.waitForIdle();idle()}
    @Test fun workspaceRouteValidatesAndExplicitlyPreparesWithoutApproval() {
        Phase3CCFixture().use {x->x.seed();x.importAll();val before=x.coordinator.currentCandidateVersion(x.id,x.mode)
            val item=TerritoryDashboardModel.from(x.kb).items.first {it.assignment.displayId==x.id}
            rule.setContent {TerritoryCardStudioTheme(AppearanceMode.LIGHT){Surface(Modifier.fillMaxSize()){ModeAwareTerritoryWorkspace(Modifier.fillMaxSize().safeDrawingPadding(),item,x.kb.revision,{},x.kb,x.coordinator,extendedDraftStore=x.extended,authorityStore=x.authority,extendedPreparation=x.preparation)}}}
            rule.onNodeWithTag("territory-workspace").performScrollToNode(hasTestTag("workspace-mode-Letter-Writing"));rule.onNodeWithTag("workspace-mode-Letter-Writing").performClick()
            rule.onNodeWithTag("territory-workspace").performScrollToNode(hasTestTag("workspace-editing-preparation"));rule.onNodeWithTag("workspace-editing-preparation").performClick();ready()
            validated();assertEquals(before,x.coordinator.currentCandidateVersion(x.id,x.mode));click("preparation-prepare");dialog("preparation-cancel");assertEquals(before,x.coordinator.currentCandidateVersion(x.id,x.mode))
            click("preparation-prepare");dialog("preparation-confirm");scroll("preparation-message");rule.onNodeWithTag("preparation-message").assertTextEquals("Prepared for build • not approved")
            assertNotEquals(before,x.coordinator.currentCandidateVersion(x.id,x.mode));assertTrue(x.coordinator.state(x.id,x.mode).canBuild)
            click("preparation-back");rule.onNodeWithTag("territory-workspace").assertExists()
        }
    }
    private fun nativeNode(text:String):android.view.accessibility.AccessibilityNodeInfo? {
        val root=InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow ?: return null
        fun find(n:android.view.accessibility.AccessibilityNodeInfo):android.view.accessibility.AccessibilityNodeInfo? {
            if(n.text?.toString()==text || n.contentDescription?.toString()==text)return n
            for(i in 0 until n.childCount){val c=n.getChild(i) ?: continue;find(c)?.let {return it}}
            return null
        }
        return find(root)
    }
    private fun nativeClick(text:String) {rule.waitUntil(15000){nativeNode(text)!=null};var n=nativeNode(text)!!;while(!n.isClickable && n.parent!=null)n=n.parent;assertTrue(n.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))}
    private fun document(x:Phase3CCFixture,bytes:ByteArray):android.net.Uri {
        val values=android.content.ContentValues().apply {put(android.provider.MediaStore.Downloads.DISPLAY_NAME,"phase3cc-${java.util.UUID.randomUUID()}.json");put(android.provider.MediaStore.Downloads.MIME_TYPE,"application/json");put(android.provider.MediaStore.Downloads.RELATIVE_PATH,"Download/")}
        val uri=requireNotNull(x.f.app.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values))
        x.f.app.contentResolver.openOutputStream(uri)!!.use {it.write(bytes)};return uri
    }
    private fun selectDocument(x:Phase3CCFixture,uri:android.net.Uri) {
        val name=x.f.app.contentResolver.query(uri,arrayOf(android.provider.MediaStore.Downloads.DISPLAY_NAME),null,null,null)!!.use {it.moveToFirst();it.getString(0)}
        click("preparation-import")
        rule.waitUntil(15000){nativeNode("Show roots")!=null || nativeNode(name)!=null}
        if(nativeNode(name)==null){nativeClick("Show roots");nativeClick("Downloads")}
        nativeClick(name);idle()
    }
    @Test fun missingAuthorityAndFreshProviderFailurePreserveCandidate() {
        Phase3CCFixture().use {x->x.seed();val before=x.coordinator.currentCandidateVersion(x.id,x.mode);rule.setContent {Content(x)};ready(false)
            scroll("preparation-validate");rule.onNodeWithTag("preparation-validate").assertIsNotEnabled()
            click("preparation-import");rule.waitUntil(15000){nativeNode("Show roots")!=null}
            assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK));rule.waitForIdle();ready(false)
            val uris=listOf(document(x,"{}".toByteArray()),document(x,x.assignmentBytes),document(x,x.inventoryBytes))
            try {selectDocument(x,uris[0]);scroll("preparation-message");ready(false);selectDocument(x,uris[1]);rule.waitUntil(20000){x.authority.status(x.id,x.mode).sourceCount==1};ready();selectDocument(x,uris[2]);rule.waitUntil(20000){x.authority.status(x.id,x.mode).sourceCount==2};ready();assertEquals(2,x.authority.status(x.id,x.mode).sourceCount)}finally{uris.forEach {x.f.app.contentResolver.delete(it,null,null)}}
            x.evidenceTransform={emptyList()};click("preparation-validate");idle()
            scroll("preparation-message");rule.onNodeWithTag("preparation-message").assertTextContains("Fresh independent geometry verification blocked preparation")
            assertEquals(before,x.coordinator.currentCandidateVersion(x.id,x.mode));rule.onNodeWithTag("preparation-prepare").assertDoesNotExist()
        }
    }
    @Test fun confirmationRaceAndReceiptRevocationFailClosed() {
        Phase3CCFixture().use {x->x.seed();x.importAll();val before=x.coordinator.currentCandidateVersion(x.id,x.mode);rule.setContent {Content(x)};ready();validated();click("preparation-prepare")
            val d=x.extended.read(x.id,x.mode)!!;x.extended.save(x.id,x.mode,d.latest.token,d.latest.proposals.dropLast(1));dialog("preparation-confirm")
            scroll("preparation-message");rule.onNodeWithTag("preparation-message").assertTextContains("Validation changed or expired; validate again");assertEquals(before,x.coordinator.currentCandidateVersion(x.id,x.mode))
            click("preparation-revoke");dialog("preparation-cancel");assertTrue(x.authority.status(x.id,x.mode).available)
            click("preparation-revoke");dialog("preparation-confirm");ready(false);assertFalse(x.authority.status(x.id,x.mode).available)
        }
    }
    @Test fun backCancellationRecreationAndExpiryRequireFreshValidation() {
        Phase3CCFixture().use {x->x.seed();x.importAll();var left=false;val restore=StateRestorationTester(rule);restore.setContent {Content(x,onBack={left=true})};ready();validated()
            click("preparation-back");dialog("preparation-cancel");assertFalse(left)
            restore.emulateSavedInstanceStateRestore();ready();rule.onNodeWithTag("preparation-ticket").assertDoesNotExist();validated()
            x.now+=300001;click("preparation-refresh");idle();scroll("preparation-message");rule.onNodeWithTag("preparation-message").assertTextContains("Validation expired or its source/proposals changed. Validate again.")
            validated();rule.runOnIdle {rule.activity.onBackPressedDispatcher.onBackPressed()};dialog("preparation-confirm");assertTrue(left)
        }
    }
    private fun shot(name:String) {
        if(InstrumentationRegistry.getArguments().getString("captureOnly")=="repair" && name !in setOf("blocked-dark","prepared-light"))return
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        rule.waitForIdle();automation.waitForIdle(500,10000)
        var prior:Bitmap?=null;var accepted:Bitmap?=null;var stableSince=0L
        try {rule.waitUntil(20000) {
            val b=requireNotNull(automation.takeScreenshot());val dark=name.endsWith("-dark")
            val background=b.getPixel(8,b.height/2);val luminance=(android.graphics.Color.red(background)+android.graphics.Color.green(background)+android.graphics.Color.blue(background))/3
            var stable=prior!=null && prior!!.width==b.width && prior!!.height==b.height && (if(dark)luminance<80 else luminance>180)
            val insets=rule.activity.window.decorView.rootWindowInsets
            stable=stable && insets?.isVisible(android.view.WindowInsets.Type.statusBars())==true
            if(stable)for(y in 0 until b.height step 4)for(x in 0 until b.width step 8)if(prior!!.getPixel(x,y)!=b.getPixel(x,y))stable=false
            val now=android.os.SystemClock.elapsedRealtime()
            if(!stable)stableSince=0L else if(stableSince==0L)stableSince=now
            prior?.recycle();prior=b;if(stable && now-stableSince>=1000){accepted=b;prior=null;true}else false
        }
        val b=requireNotNull(accepted);val wide=rule.activity.resources.configuration.screenWidthDp>=840
        File(rule.activity.filesDir,"phase3cc-${if(wide)"wide-" else ""}$name.png").outputStream().use {assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))}
        }finally{prior?.recycle();accepted?.recycle()}
    }
    @Test fun captureLight()=capture(AppearanceMode.LIGHT)
    @Test fun captureDark()=capture(AppearanceMode.DARK)
    private fun capture(theme:AppearanceMode) {
        Phase3CCFixture().use {x->x.seed();rule.activity.runOnUiThread {rule.activity.enableEdgeToEdge()};rule.setContent {Content(x,theme)};ready(false)
            rule.runOnIdle {val mask=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;rule.activity.window.insetsController?.setSystemBarsAppearance(if(theme==AppearanceMode.LIGHT)mask else 0,mask)}
            val suffix=theme.name.lowercase();shot("blocked-$suffix");if(InstrumentationRegistry.getArguments().getString("captureOnly")=="repair" && theme==AppearanceMode.DARK)return
            x.importAll();click("preparation-refresh");ready();shot("authority-$suffix")
            validated();scroll("preparation-ticket");shot("validated-$suffix")
            click("preparation-prepare");shot("confirmation-$suffix");dialog("preparation-confirm");scroll("preparation-message");rule.onNodeWithTag("preparation-message").assertTextEquals("Prepared for build • not approved");shot("prepared-$suffix");if(InstrumentationRegistry.getArguments().getString("captureOnly")=="repair")return
            click("preparation-validate");idle();scroll("preparation-message");rule.onNodeWithTag("preparation-message").assertTextContains("Prepared baseline changed; import authority facts again");shot("stale-$suffix")
        }
    }
}

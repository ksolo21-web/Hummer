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
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class Phase3DIntegrationUiInstrumentationTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    @get:Rule val diagnostic=object:org.junit.rules.TestWatcher(){override fun failed(e:Throwable,d:org.junit.runner.Description){
        val dir=InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        File(dir,"phase3d-debug-${d.methodName}.txt").writeText(e.stackTraceToString()+"\n"+runCatching{rule.onRoot().printToString()}.getOrDefault("No tree"))
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let {b->File(dir,"phase3d-debug-${d.methodName}.png").outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
    }}
    private fun ready() {rule.waitUntil(30000){runCatching {rule.onNodeWithTag("candidate-review-screen").fetchSemanticsNode().config.getOrElse(SemanticsProperties.StateDescription){""}=="ready"}.getOrDefault(false)};rule.waitForIdle()}
    private fun show(tag:String){rule.onNodeWithTag("review-list").performScrollToNode(hasTestTag(tag));rule.waitForIdle()}
    private fun click(tag:String){show(tag);rule.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled().performClick();rule.waitForIdle()}
    private fun shot(name:String) {
        val auto=InstrumentationRegistry.getInstrumentation().uiAutomation
        rule.waitForIdle();auto.waitForIdle(500,10000)
        var prior:Bitmap?=null;var accepted:Bitmap?=null;var stableSince=0L
        try {rule.waitUntil(20000){val b=requireNotNull(auto.takeScreenshot());val now=android.os.SystemClock.elapsedRealtime()
            var stable=prior!=null && prior!!.width==b.width && prior!!.height==b.height && auto.rootInActiveWindow?.packageName?.toString()==rule.activity.packageName && rule.activity.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.statusBars())==true
            if(stable)for(y in 0 until b.height step 4)for(x in 0 until b.width step 8)if(prior!!.getPixel(x,y)!=b.getPixel(x,y))stable=false
            if(!stable)stableSince=0L else if(stableSince==0L)stableSince=now
            prior?.recycle();prior=b;if(stable && now-stableSince>=1000){accepted=b;prior=null;true}else false
        };val b=requireNotNull(accepted);val wide=rule.activity.resources.configuration.screenWidthDp>=840
            File(rule.activity.filesDir,"phase3d-${if(wide)"wide-" else ""}$name.png").outputStream().use {assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))}
        }finally{prior?.recycle();accepted?.recycle()}
    }
    @Test fun editedReviewLight()=flow(AppearanceMode.LIGHT)
    @Test fun editedReviewDark()=flow(AppearanceMode.DARK)
    private fun flow(theme:AppearanceMode) {
        Phase3CCFixture().use {x->x.seed();x.importAll();x.preparation.prepare(x.validate());assertNull(x.coordinator.buildFront(x.id,x.mode).error);assertNull(x.coordinator.generatePage2(x.id,x.mode).error)
            val review=AndroidCandidateReviewService(x.coordinator,File(x.f.root,"3d-ui-reviews"));val export=AndroidApprovedExportService(x.kb,x.pdf)
            var exportShown by mutableStateOf(false)
            rule.activity.runOnUiThread {rule.activity.enableEdgeToEdge()}
            rule.setContent {TerritoryCardStudioTheme(theme){Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){
                if(exportShown)ApprovedExportScreen(Modifier.fillMaxSize().safeDrawingPadding(),x.id,export){exportShown=false}
                else CandidateReviewScreen(Modifier.fillMaxSize().safeDrawingPadding(),x.id,x.mode,review,{}, {})
            }}}
            rule.runOnIdle {val mask=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;rule.activity.window.insetsController?.setSystemBarsAppearance(if(theme==AppearanceMode.LIGHT)mask else 0,mask)}
            ready();show("review-identity");rule.onNodeWithTag("review-hash").assertTextEquals(x.coordinator.state(x.id,x.mode).packet!!.sha256);shot("edited-review-${theme.name.lowercase()}")
            click("review-pages");click("review-boundaries");click("review-data");show("review-actor");rule.onNodeWithTag("review-actor").performTextReplacement("Integration reviewer")
            rule.runOnIdle {rule.activity.currentFocus?.clearFocus();rule.activity.window.insetsController?.hide(android.view.WindowInsets.Type.ime())}
            rule.waitUntil(10000){rule.activity.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime())!=true}
            click("review-approve");rule.onNodeWithTag("review-confirm").performClick();ready()
            rule.waitUntil(20000){review.state(x.id,x.mode).decision!=null};show("review-decision");shot("local-decision-${theme.name.lowercase()}")
            assertNull(export.state(x.id).ticket);rule.runOnIdle {exportShown=true};rule.waitUntil(20000){rule.onAllNodesWithTag("export-blocker").fetchSemanticsNodes().isNotEmpty()};shot("export-blocked-${theme.name.lowercase()}")
            x.authority.revoke(x.id,x.mode,x.authority.status(x.id,x.mode).receiptId!!);rule.runOnIdle {exportShown=false};ready();show("review-blocked");shot("stale-review-${theme.name.lowercase()}")
            assertNull(review.state(x.id,x.mode).decision)
        }
    }
}

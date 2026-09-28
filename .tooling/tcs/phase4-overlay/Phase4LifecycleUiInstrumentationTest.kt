package com.koenterprises.territorycardstudio

import android.graphics.Bitmap
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
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
class Phase4LifecycleUiInstrumentationTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    @get:Rule val diagnostic=object:org.junit.rules.TestWatcher(){override fun failed(e:Throwable,d:org.junit.runner.Description){
        val dir=InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        File(dir,"phase4-debug-${d.methodName}.txt").writeText(e.stackTraceToString()+"\n"+runCatching{rule.onRoot().printToString()}.getOrDefault("No tree"))
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let {b->File(dir,"phase4-debug-${d.methodName}.png").outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
    }}
    private var focusManager: FocusManager? = null
    private fun ready() {rule.waitUntil(30000){runCatching {rule.onNodeWithTag("lifecycle-screen").fetchSemanticsNode().config.getOrElse(SemanticsProperties.StateDescription){""}=="ready"}.getOrDefault(false)};rule.waitForIdle()}
    private fun show(tag:String){rule.onNodeWithTag("lifecycle-list").performScrollToNode(hasTestTag(tag));rule.waitForIdle()}
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
            File(rule.activity.filesDir,"phase4-${if(wide)"wide-" else ""}$name.png").outputStream().use {assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))}
        }finally{prior?.recycle();accepted?.recycle()}
    }
    @Test fun lifecycleLight()=flow(AppearanceMode.LIGHT)
    @Test fun lifecycleDark()=flow(AppearanceMode.DARK)
    private fun reviewer() {
        show("lifecycle-actor")
        rule.onNodeWithTag("lifecycle-actor").performTextReplacement("Phase 4 reviewer")
        rule.runOnIdle {
            requireNotNull(focusManager).clearFocus(force=true)
            rule.activity.window.decorView.clearFocus()
            rule.activity.window.insetsController?.hide(android.view.WindowInsets.Type.ime())
        }
        rule.waitUntil(10000){rule.activity.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime())!=true}
        rule.waitForIdle()
    }
    private fun reviewChecks() {
        click("lifecycle-pages");click("lifecycle-boundaries");click("lifecycle-data");reviewer()
    }
    private fun confirm() {rule.onNodeWithTag("lifecycle-confirm").assertIsDisplayed().performClick();ready()}
    private fun enterReview() {
        rule.waitUntil(30000){rule.onAllNodesWithTag("build-readiness").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("build-screen").performScrollToNode(hasTestTag("review-candidate"))
        rule.onNodeWithTag("review-candidate").assertIsDisplayed().assertIsEnabled().performClick();ready()
    }
    private fun flow(theme:AppearanceMode) {
        Phase3CCFixture().use {x->
            x.seed();x.importAll();x.preparation.prepare(x.validate())
            val directory=File(x.f.root,"phase4-ui-lifecycle")
            val original=AndroidCandidateLifecycleService(x.kb,x.coordinator,x.f.sources,directory)
            assertNull(original.build(x.id,x.mode,false).error)
            assertNull(original.build(x.id,x.mode,true).error)
            var service by mutableStateOf(original)
            var coordinator by mutableStateOf(x.coordinator)
            val preview=AndroidPdfPreviewService(x.coordinator,File(x.f.root,"phase4-preview"),x.pdf)
            rule.activity.runOnUiThread {rule.activity.enableEdgeToEdge()}
            rule.setContent {TerritoryCardStudioTheme(theme){
                val currentFocusManager=LocalFocusManager.current
                SideEffect {focusManager=currentFocusManager}
                Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){
                    key(service) {BuildWorkflowScreen(Modifier.fillMaxSize().safeDrawingPadding(),x.id,x.mode,coordinator,
                        previewService=preview,lifecycleService=service,onBack={})}
                }
            }}
            rule.runOnIdle {val mask=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                rule.activity.window.insetsController?.setSystemBarsAppearance(if(theme==AppearanceMode.LIGHT)mask else 0,mask)}
            enterReview()
            val unapproved=original.state(x.id,x.mode)
            assertFalse(unapproved.active);assertNull(unapproved.promoted)
            assertEquals("CANDIDATE-UNAPPROVED",unapproved.candidates.single().status)
            show("lifecycle-identity")
            rule.onNodeWithTag("lifecycle-hash").assertTextEquals(unapproved.ticket!!.manifest.packetPdfSha256)
            shot("unapproved-${theme.name.lowercase()}")
            reviewChecks();click("lifecycle-approve")
            rule.onNodeWithTag("lifecycle-confirmation").assertIsDisplayed()
            shot("confirmation-${theme.name.lowercase()}")
            rule.onNodeWithTag("lifecycle-cancel").performClick();ready()
            assertEquals(unapproved.history,original.state(x.id,x.mode).history)
            assertFalse(original.state(x.id,x.mode).active)
            click("lifecycle-approve")
            val intervening=original.state(x.id,x.mode)
            original.decide(intervening.ticket!!,"Concurrent reviewer",CandidateReviewChecks(false,false,false),false,true,intervening.revision)
            confirm()
            val staleRejected=original.state(x.id,x.mode)
            assertFalse(staleRejected.active)
            assertEquals("REJECTED",staleRejected.candidates.single().status)
            assertEquals("Concurrent reviewer",staleRejected.history.last().actor)
            show("lifecycle-error");rule.onNodeWithTag("lifecycle-error").assertIsDisplayed()
            reviewChecks();click("lifecycle-approve");confirm()
            val approved=original.state(x.id,x.mode)
            assertTrue(approved.active);assertEquals(unapproved.ticket,approved.promoted!!.ticket)
            assertEquals("APPROVED",approved.promoted!!.status)
            show("lifecycle-status")
            shot("approved-${theme.name.lowercase()}")
            reviewer();click("lifecycle-reject");confirm()
            val rejected=original.state(x.id,x.mode)
            assertFalse(rejected.active);assertNull(rejected.promoted)
            assertEquals("REJECTED",rejected.candidates.single().status)
            assertEquals("Phase 4 reviewer",rejected.history.last().actor)
            assertEquals("REJECTED",rejected.history.last().action)
            show("lifecycle-history");shot("rejected-${theme.name.lowercase()}")
            reviewChecks();click("lifecycle-approve");confirm();assertTrue(original.state(x.id,x.mode).active)
            val oldVersion=original.state(x.id,x.mode).promoted!!.ticket.manifest.candidateVersion
            assertNull(original.build(x.id,x.mode,true).error)
            click("lifecycle-refresh");ready()
            val invalidated=original.state(x.id,x.mode)
            assertFalse(invalidated.active);assertNull(invalidated.promoted)
            assertTrue(invalidated.candidates.any {it.ticket.manifest.candidateVersion==oldVersion && it.status=="INVALIDATED"})
            assertNotEquals(oldVersion,invalidated.ticket!!.manifest.candidateVersion)
            show("lifecycle-history");shot("invalidated-${theme.name.lowercase()}")
            reviewChecks();click("lifecycle-approve");confirm()
            val before=original.state(x.id,x.mode)
            assertTrue(before.active)
            val freshCoordinator=AndroidBuildWorkflowCoordinator(x.kb,x.models,x.pdf,x.f.sources)
            val fresh=AndroidCandidateLifecycleService(x.kb,freshCoordinator,x.f.sources,directory)
            val restored=fresh.state(x.id,x.mode)
            assertFalse(restored.active);assertNull(restored.ticket)
            assertEquals(before.promoted,restored.promoted);assertEquals(before.history,restored.history)
            rule.runOnIdle {coordinator=freshCoordinator;service=fresh}
            enterReview();show("lifecycle-suspended")
            rule.onAllNodesWithTag("lifecycle-approve").assertCountEquals(0)
            shot("suspended-${theme.name.lowercase()}")
        }
    }
}

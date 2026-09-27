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
    @Test fun missingAuthorityAndFreshProviderFailurePreserveCandidate() {
        Phase3CCFixture().use {x->x.seed();val before=x.coordinator.currentCandidateVersion(x.id,x.mode);rule.setContent {Content(x)};ready(false)
            scroll("preparation-validate");rule.onNodeWithTag("preparation-validate").assertIsNotEnabled()
            x.importAll();click("preparation-refresh");ready();x.evidenceTransform={emptyList()};click("preparation-validate");idle()
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
        rule.waitForIdle();val b=requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {val wide=rule.activity.resources.configuration.screenWidthDp>=840;File(rule.activity.filesDir,"phase3cc-${if(wide)"wide-" else ""}$name.png").outputStream().use {assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))}}finally{b.recycle()}
    }
    @Test fun captureLight()=capture(AppearanceMode.LIGHT)
    @Test fun captureDark()=capture(AppearanceMode.DARK)
    private fun capture(theme:AppearanceMode) {
        Phase3CCFixture().use {x->x.seed();rule.activity.runOnUiThread {rule.activity.enableEdgeToEdge()};rule.setContent {Content(x,theme)};ready(false)
            rule.runOnIdle {val mask=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;rule.activity.window.insetsController?.setSystemBarsAppearance(if(theme==AppearanceMode.LIGHT)mask else 0,mask)}
            val suffix=theme.name.lowercase();shot("blocked-$suffix")
            x.importAll();click("preparation-refresh");ready();shot("authority-$suffix")
            validated();scroll("preparation-ticket");shot("validated-$suffix")
            click("preparation-prepare");shot("confirmation-$suffix");dialog("preparation-confirm");scroll("preparation-message");rule.onNodeWithTag("preparation-message").assertTextEquals("Prepared for build • not approved");shot("prepared-$suffix")
            click("preparation-validate");idle();scroll("preparation-message");rule.onNodeWithTag("preparation-message").assertTextContains("Prepared baseline changed; import authority facts again");shot("stale-$suffix")
        }
    }
}

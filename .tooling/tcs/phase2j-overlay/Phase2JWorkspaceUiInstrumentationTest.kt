package com.koenterprises.territorycardstudio

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class Phase2JWorkspaceUiInstrumentationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun waitFor(tag: String) { rule.waitUntil(20000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }; rule.waitForIdle() }
    private fun scroll(tag: String) { rule.onNodeWithTag("territory-workspace").performScrollToNode(hasTestTag(tag)) }
    private fun show(f: Phase2DBFixture, theme: AppearanceMode) {
        val preview = AndroidPdfPreviewService(f.coordinator, File(f.root, "workspace-preview"), f.service)
        val item = TerritoryDashboardModel.from(f.kb).items.first { it.assignment.displayId == f.identity.displayId }
        rule.runOnIdle { rule.activity.setContent { TerritoryCardStudioTheme(theme) {
            ModeAwareTerritoryWorkspace(Modifier.fillMaxSize().safeDrawingPadding(), item, f.kb.revision, {}, f.kb, f.coordinator, preview)
        } } }
        waitFor("territory-workspace")
        if (!f.telephoneMode) { scroll("workspace-mode-Letter-Writing"); rule.onNodeWithTag("workspace-mode-Letter-Writing").performClick() }
    }
    private fun shot(name: String) {
        rule.waitForIdle(); InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val wide = rule.activity.resources.configuration.screenWidthDp >= 840
        File(rule.activity.filesDir, "phase2j-${if (wide) "wide-" else ""}$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
    }
    @Test fun resumeRemovesStalePreviewAndInventory() {
        waitFor("territories-dashboard")
        Phase2DBFixture(false).use { f ->
            f.prepare(); f.build(); f.page2(); show(f, AppearanceMode.LIGHT)
            scroll("workspace-surface-Map"); waitFor("workspace-pdf-page")
            f.importSource("stale")
            rule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
            rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            waitFor("workspace-preview-unavailable")
            rule.onNodeWithTag("workspace-pdf-page").assertDoesNotExist()
            scroll("workspace-tab-Addresses"); rule.onNodeWithTag("workspace-tab-Addresses").performClick()
            scroll("letter-inventory-unavailable"); rule.onNodeWithTag("letter-inventory-unavailable").assertExists()
            rule.onNodeWithTag("workspace-inventory-counts").assertDoesNotExist()
        }
    }
    @Test fun captureCurrentWorkspacePreviewInventoriesAndVerification() {
        waitFor("territories-dashboard")
        for (theme in listOf(AppearanceMode.LIGHT, AppearanceMode.DARK)) {
            val suffix = theme.name.lowercase()
            Phase2DBFixture(false).use { f ->
                f.prepare(); f.build(); f.page2(); show(f, theme)
                scroll("workspace-surface-Map"); waitFor("workspace-pdf-page")
                scroll("workspace-pdf-page"); shot("candidate-$suffix")
                scroll("workspace-open-preview"); rule.onNodeWithTag("workspace-open-preview").performClick()
                waitFor("preview-page"); rule.onNodeWithTag("preview-next").performClick(); waitFor("preview-page")
                rule.onNodeWithTag("preview-page-number").assertTextEquals("2 / 2")
                rule.onNodeWithTag("preview-back").performClick(); waitFor("territory-workspace")
                scroll("workspace-tab-Addresses"); rule.onNodeWithTag("workspace-tab-Addresses").performClick()
                scroll("workspace-inventory-record-address-1"); shot("addresses-$suffix")
                rule.onNodeWithText("100 Example Way").assertExists()
                scroll("workspace-verification"); rule.onNodeWithTag("workspace-verification").performClick(); waitFor("verification-screen")
                rule.onNodeWithText("Prepared inputs ready for Build").assertExists(); shot("verification-$suffix")
            }
            Phase2DBFixture(true).use { f ->
                f.prepare(); show(f, theme)
                scroll("workspace-tab-Phone-List"); rule.onNodeWithTag("workspace-tab-Phone-List").performClick()
                scroll("workspace-inventory-record-phone-2"); rule.onNodeWithText("UNAVAILABLE").assertExists(); shot("telephone-$suffix")
            }
            Phase2GExportFixture().use { f ->
                f.attach()
                val item = TerritoryDashboardModel.from(f.kb).items.first { it.assignment.displayId == f.id }
                val preview = AndroidPdfPreviewService(f.source.coordinator, File(f.source.root,"approved-preview"), f.artifacts)
                rule.runOnIdle { rule.activity.setContent { TerritoryCardStudioTheme(theme) {
                    ModeAwareTerritoryWorkspace(Modifier.fillMaxSize().safeDrawingPadding(), item, f.kb.revision, {}, f.kb, f.source.coordinator, preview)
                } } }
                waitFor("territory-workspace"); scroll("workspace-surface-Map"); waitFor("workspace-pdf-page")
                scroll("workspace-pdf-page"); shot("approved-$suffix")
                scroll("workspace-open-preview"); rule.onNodeWithTag("workspace-open-preview").performClick()
                waitFor("preview-page"); rule.onNodeWithText("Original approved document • page 1").assertExists()
                rule.onNodeWithTag("preview-back").performClick(); waitFor("territory-workspace")
            }
        }
    }
}

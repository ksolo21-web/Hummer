package com.koenterprises.territorycardstudio

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
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
class Phase2IDashboardUiInstrumentationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as TerritoryCardStudioApplication
    private val kb get() = app.services.knowledgeBase
    private fun waitFor(tag: String) { rule.waitUntil(20000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }; rule.waitForIdle() }
    private fun hideKeyboard() {
        rule.activity.runOnUiThread {
            val manager = rule.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            manager.hideSoftInputFromWindow(rule.activity.window.decorView.windowToken, 0)
            rule.activity.window.decorView.clearFocus()
        }
        rule.waitUntil(10000) {
            rule.activity.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) != true
        }
        rule.waitForIdle()
    }
    private fun shot(name: String) {
        rule.waitForIdle(); InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val b = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(rule.activity.filesDir, name).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; b.recycle()
    }

    @Test fun combinedSearchFilterNoMatchAndCompleteScreeningAreReachable() {
        waitFor("territories-dashboard")
        rule.onNodeWithTag("territory-filters").performScrollToNode(hasTestTag("territory-filter-SCREENING"))
        rule.onNodeWithTag("territory-filter-SCREENING").performClick()
        rule.onNodeWithTag("territory-search").performTextInput("OU academic")
        rule.onNodeWithTag("territories-dashboard").performScrollToNode(hasTestTag("screening-row-C20"))
        rule.onNodeWithText("None — unassigned").assertIsDisplayed()
        rule.onNodeWithText("Blocked").assertIsDisplayed()
        rule.onNodeWithText("Source: master_sources/Meadowbrook-Possible-New-Territories.pdf").assertIsDisplayed()
        rule.onNodeWithTag("territory-search").performTextClearance()
        rule.onNodeWithTag("territory-search").performTextInput("NO_SUCH_RECORD")
        rule.onNodeWithTag("territory-no-results").assertExists()
    }

    @Test fun roadsBuildingsAndSyntheticSourcesRemainCompletelyBrowsable() {
        waitFor("territories-dashboard")
        rule.onNodeWithTag("territory-search").performTextInput("A289")
        rule.onNodeWithTag("territories-dashboard").performScrollToNode(hasTestTag("territory-row-A289"))
        rule.onNodeWithTag("territory-row-A289").performClick(); waitFor("territory-workspace")
        rule.onNodeWithTag("territory-workspace").performScrollToNode(hasTestTag("workspace-tab-Streets"))
        rule.onNodeWithTag("workspace-tab-Streets").performClick()
        rule.onNodeWithTag("territory-workspace").performScrollToNode(hasTestTag("workspace-road-179"))
        rule.onNodeWithTag("workspace-road-179").assertExists()

        val base = TerritoryDashboardModel.from(kb).items.first { it.assignment.displayId == "A320" }
        val hashes = (1..8).map { it.toString().padStart(64, '0') }
        val fixture = base.copy(assignment = base.assignment.copy(sourceHashes = hashes))
        rule.runOnIdle { rule.activity.setContent {
            TerritoryCardStudioTheme(AppearanceMode.DARK) {
                ModeAwareTerritoryWorkspace(Modifier.fillMaxSize().safeDrawingPadding(), fixture, kb.revision, {}, kb)
            }
        } }
        waitFor("territory-workspace")
        rule.onNodeWithTag("territory-workspace").performScrollToNode(hasTestTag("workspace-tab-Buildings"))
        rule.onNodeWithTag("workspace-tab-Buildings").performClick()
        rule.onNodeWithTag("territory-workspace").performScrollToNode(hasTestTag("workspace-building-29"))
        rule.onNodeWithTag("workspace-building-29").assertExists()
        rule.onNodeWithTag("territory-workspace").performScrollToNode(hasTestTag("workspace-tab-Details"))
        rule.onNodeWithTag("workspace-tab-Details").performClick()
        rule.onNodeWithTag("territory-workspace").performScrollToNode(hasTestTag("workspace-source-7"))
        rule.onNodeWithText(hashes.last()).assertExists()
    }

    @Test fun captureDashboardFiltersScreeningAndCompleteRoadBrowsing() {
        val wide = rule.activity.resources.configuration.screenWidthDp >= 840
        for (mode in listOf(AppearanceMode.LIGHT, AppearanceMode.DARK)) {
            rule.runOnIdle { app.appearancePreferences.setMode(mode) }
            waitFor("territories-dashboard")
            hideKeyboard()
            val theme = mode.name.lowercase()
            shot("phase2i-${if (wide) "wide-" else ""}dashboard-$theme.png")
            if (!wide || mode == AppearanceMode.DARK) {
                rule.onNodeWithTag("territory-filters").performScrollToNode(hasTestTag("territory-filter-SCREENING"))
                rule.onNodeWithTag("territory-filter-SCREENING").performClick()
                rule.onNodeWithTag("territory-search").performTextInput("C20")
                hideKeyboard()
                rule.onNodeWithTag("territories-dashboard").performScrollToNode(
                    hasText("Source: master_sources/Meadowbrook-Possible-New-Territories.pdf")
                )
                shot("phase2i-${if (wide) "wide-" else ""}screening-$theme.png")
                rule.onNodeWithTag("territory-search").performTextClearance()
                rule.onNodeWithTag("territory-filters").performScrollToNode(hasTestTag("territory-filter-ALL"))
                rule.onNodeWithTag("territory-filter-ALL").performClick()
            }
            if (!wide && mode == AppearanceMode.DARK) {
                rule.onNodeWithTag("territory-search").performTextInput("A289")
                rule.onNodeWithTag("territory-row-A289").performClick(); waitFor("territory-workspace")
                rule.onNodeWithTag("territory-workspace").performScrollToNode(hasTestTag("workspace-tab-Streets"))
                rule.onNodeWithTag("workspace-tab-Streets").performClick()
                rule.onNodeWithTag("territory-workspace").performScrollToNode(hasTestTag("workspace-road-179"))
                shot("phase2i-roads-complete-dark.png")
                rule.onNodeWithTag("territory-workspace").performScrollToNode(hasText("← Back to territories"))
                rule.onNodeWithText("← Back to territories").performClick(); waitFor("territories-dashboard")
            }
        }
    }
}

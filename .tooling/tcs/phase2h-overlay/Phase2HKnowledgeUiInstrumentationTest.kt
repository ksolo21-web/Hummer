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
class Phase2HKnowledgeUiInstrumentationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as TerritoryCardStudioApplication
    private val services get() = app.services
    private fun waitFor(tag: String) { rule.waitUntil(20000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }; rule.waitForIdle() }
    private fun shot(name: String) {
        rule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val b = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(rule.activity.filesDir, name).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; b.recycle()
    }
    private fun open() { rule.onNodeWithTag("nav-knowledge").performClick(); waitFor("knowledge-screen") }
    private fun record(id: String) {
        rule.onNodeWithTag("knowledge-list").performScrollToNode(hasTestTag("knowledge-record-$id"))
        rule.onNodeWithTag("knowledge-record-$id").performClick(); waitFor("knowledge-identity")
    }
    @Test fun workspaceAndExportStateSurviveReadOnlyDestination() {
        waitFor("territories-dashboard")
        rule.onNodeWithTag("territories-dashboard").performScrollToNode(hasTestTag("territory-row-T250"))
        rule.onNodeWithTag("territory-row-T250").performClick()
        waitFor("territory-workspace")
        rule.onNodeWithTag("territory-workspace").performScrollToNode(hasTestTag("workspace-export"))
        rule.onNodeWithTag("workspace-export").performClick(); waitFor("approved-export-screen")
        open()
        rule.onNodeWithTag("knowledge-selected").assertTextContains("T250").performClick()
        rule.onNodeWithTag("knowledge-identity").assertExists()
        rule.onNodeWithTag("knowledge-close").performClick()
        waitFor("approved-export-screen")
        rule.onNodeWithTag("export-list").performScrollToNode(hasTestTag("export-save"))
        rule.onNodeWithTag("export-save").assertIsNotEnabled()
        rule.onNodeWithTag("nav-knowledge").assertExists()
    }
    @Test fun allSourceHashesAndMissingSearchRemainAccessible() {
        val kb = services.knowledgeBase
        val a = kb.assignments.values.first()
        val hashes = (1..8).map { it.toString().padStart(64, '0') }
        val fixture = kb.copy(assignments = kb.assignments + (a.displayId to a.copy(sourceHashes = hashes)))
        rule.activity.runOnUiThread { rule.activity.setContent {
            TerritoryCardStudioTheme(AppearanceMode.DARK) {
                KnowledgeBaseScreen(Modifier.fillMaxSize().safeDrawingPadding(), fixture, services.activePolicy.revision,
                    services.endpointConfig.revision, services.approvedExport, null) {}
            }
        } }
        waitFor("knowledge-list"); record(a.displayId)
        rule.onNodeWithTag("knowledge-detail-list").performScrollToNode(hasTestTag("knowledge-source-7"))
        rule.onNodeWithText(hashes.last()).assertIsDisplayed()
        rule.onNodeWithTag("knowledge-back").performClick()
        rule.onNodeWithTag("knowledge-search").performTextInput("NO_SUCH_TERRITORY")
        rule.onNodeWithTag("knowledge-empty").assertExists()
        assertEquals(hashes, requireNotNull(KnowledgeRecords.resolve(fixture, a.displayId)).assignment.sourceHashes)
    }
    @Test fun captureKnowledgeLightDarkRecordsRulesAndConflicts() {
        val wide = rule.activity.resources.configuration.screenWidthDp >= 840
        for (mode in listOf(AppearanceMode.LIGHT, AppearanceMode.DARK)) {
            rule.runOnIdle { app.appearancePreferences.setMode(mode) }
            waitFor("nav-knowledge"); open()
            val theme = mode.name.lowercase()
            shot("phase2h-${if (wide) "wide-" else ""}overview-$theme.png")
            if (!wide) {
                if (mode == AppearanceMode.DARK) {
                    record("T250")
                    shot("phase2h-reserved-dark.png")
                    rule.onNodeWithTag("knowledge-detail-list").performScrollToNode(hasTestTag("knowledge-provenance"))
                    shot("phase2h-provenance-dark.png")
                    rule.onNodeWithTag("knowledge-back").performClick()
                    rule.onNodeWithTag("knowledge-tab-Rules").performClick()
                    rule.onNodeWithTag("knowledge-list").performScrollToNode(hasTestTag("knowledge-patch-GLOBAL-INTERSECTION-COLOR"))
                    shot("phase2h-patches-dark.png")
                } else {
                    rule.onNodeWithTag("knowledge-tab-Conflicts").performClick()
                    shot("phase2h-conflicts-light.png")
                }
            }
            rule.onNodeWithTag("knowledge-close").performClick()
            waitFor("territories-dashboard")
        }
    }
}

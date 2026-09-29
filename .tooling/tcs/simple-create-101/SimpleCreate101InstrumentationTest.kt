package com.koenterprises.territorycardstudio

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SimpleCreate101InstrumentationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<TerritoryCardStudioApplication>()
    private val assignment by lazy {
        TerritoryDashboardModel.from(app.services.knowledgeBase).items
            .first { it.assignment.displayId == "T250" }
    }

    @After fun cleanup() {
        runCatching { app.services.sourceMapStore().clear(assignment.assignment.displayId) }
    }

    private fun show(theme: AppearanceMode) {
        val dashboard = TerritoryDashboardModel.from(app.services.knowledgeBase)
        rule.activity.runOnUiThread {
            rule.activity.setContent {
                TerritoryCardStudioTheme(theme) {
                    Surface(Modifier.fillMaxSize()) {
                        ModeAwareTerritoryWorkspace(
                            modifier = Modifier.fillMaxSize(),
                            item = assignment,
                            knowledgeBaseRevision = dashboard.revision,
                            onBack = {}
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun seedMap() {
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
        testAssets.open("phase7-01-26588.jpg").use { input ->
            app.services.sourceMapStore().importFromStream(
                assignment = assignment.assignment,
                sourceFilename = "my-territory-map.jpg",
                mimeType = "image/jpeg",
                input = input
            )
        }
    }

    private fun screenshot(name: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(rule.activity.filesDir, name).outputStream().use {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test fun defaultNewTerritoryFlowIsSimpleAndDoesNotExposeVerificationEngine() {
        cleanup()
        show(AppearanceMode.LIGHT)
        rule.onNodeWithTag("simple-territory-workspace").assertIsDisplayed()
        rule.onNodeWithText("Create Territory T250").assertIsDisplayed()
        rule.onNodeWithText("Add the territory map and the app will do the rest.").assertIsDisplayed()
        rule.onNodeWithTag("simple-create-card").assertTextContains("Choose Map")
        rule.onAllNodesWithText("Build remains locked").assertDoesNotExist()
        rule.onAllNodesWithText("Live geometry").assertDoesNotExist()
        rule.onAllNodesWithText("Source truth").assertDoesNotExist()
        screenshot("simple-create-101-light-empty.png")
    }

    @Test fun oneImportedMapOffersCreateCardWithoutProviderQuotaLanguage() {
        cleanup()
        seedMap()
        show(AppearanceMode.DARK)
        rule.onNodeWithTag("simple-territory-workspace").assertIsDisplayed()
        rule.onNodeWithText("Map added ✓").assertIsDisplayed()
        rule.onNodeWithText("my-territory-map.jpg").assertIsDisplayed()
        rule.onNodeWithTag("simple-create-card").assertTextContains("Create Card From This Map")
        rule.onAllNodesWithText("minimum 2 independent geometry sources").assertDoesNotExist()
        rule.onAllNodesWithText("Build remains locked").assertDoesNotExist()
        rule.onNodeWithTag("show-advanced-tools").assertExists()
        screenshot("simple-create-101-dark-map-ready.png")
    }
}

package com.koenterprises.territorycardstudio

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class Phase2DBCaptureInstrumentationTest {
    @get:Rule val composeRule = createComposeRule()
    private data class Screen(val id: String, val mode: WorkspaceMode,
        val coordinator: AndroidBuildWorkflowCoordinator, val appearance: AppearanceMode)
    @Test fun captureLightDarkBlockedAndGeneratedBuildScreens() {
        val app = ApplicationProvider.getApplicationContext<TerritoryCardStudioApplication>()
        SourceMapIntakeStore(app).clear("T14")
        Phase2DBFixture(false).use { letter -> Phase2DBFixture(true).use { telephone ->
            letter.prepare(); telephone.prepare()
            var screen by mutableStateOf(Screen("T14", WorkspaceMode.TELEPHONE, app.services.buildWorkflow, AppearanceMode.DARK))
            composeRule.setContent {
                TerritoryCardStudioTheme(appearanceMode = screen.appearance) {
                    key(screen) { BuildWorkflowScreen(Modifier.fillMaxSize().safeDrawingPadding(), screen.id, screen.mode, screen.coordinator) {} }
                }
            }
            awaitReadiness()
            capture("phase2db-blocked-dark.png")
            composeRule.runOnIdle { screen = screen.copy(appearance = AppearanceMode.LIGHT) }
            awaitReadiness()
            capture("phase2db-blocked-light.png")
            for ((fixture, appearance, name) in listOf(Triple(letter, AppearanceMode.DARK, "letter-dark"),
                Triple(telephone, AppearanceMode.LIGHT, "telephone-light"))) {
                composeRule.runOnIdle { screen = Screen(fixture.identity.displayId, fixture.mode, fixture.coordinator, appearance) }
                awaitReadiness()
                composeRule.onNodeWithTag("build-screen").performScrollToNode(hasTestTag("build-front"))
                composeRule.onNodeWithTag("build-front").assertIsEnabled().performClick()
                composeRule.waitUntil(20000) { fixture.state().front != null }
                composeRule.waitForIdle()
                composeRule.onNodeWithTag("build-screen").performScrollToNode(hasTestTag("generate-page2"))
                composeRule.onNodeWithTag("generate-page2").assertIsEnabled().performClick()
                composeRule.waitUntil(20000) { fixture.state().packet != null }
                composeRule.waitForIdle()
                composeRule.onNodeWithTag("build-screen").performScrollToNode(hasTestTag("build-result"))
                composeRule.onNodeWithText("Two-page candidate generated").assertIsDisplayed()
                composeRule.onNodeWithText("Awaiting review and explicit approval").assertIsDisplayed()
                capture("phase2db-generated-$name.png")
            }
        } }
    }
    private fun awaitReadiness() {
        composeRule.waitUntil(10000) { composeRule.onAllNodesWithTag("build-readiness").fetchSemanticsNodes().isNotEmpty() }
        composeRule.waitForIdle()
    }
    private fun capture(name: String) {
        composeRule.waitForIdle()
        val inst = InstrumentationRegistry.getInstrumentation()
        inst.waitForIdleSync()
        val bitmap = requireNotNull(inst.uiAutomation.takeScreenshot())
        File(inst.targetContext.filesDir, name).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}

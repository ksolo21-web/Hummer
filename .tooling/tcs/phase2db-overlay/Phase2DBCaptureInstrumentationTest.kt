package com.koenterprises.territorycardstudio

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.compose.setContent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class Phase2DBCaptureInstrumentationTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()
    private data class Screen(val id: String, val mode: WorkspaceMode,
        val coordinator: AndroidBuildWorkflowCoordinator, val appearance: AppearanceMode)
    @Test fun captureLightDarkBlockedAndGeneratedBuildScreens() {
        val app = ApplicationProvider.getApplicationContext<TerritoryCardStudioApplication>()
        SourceMapIntakeStore(app).clear("T14")
        Phase2DBFixture(false).use { letter -> Phase2DBFixture(true).use { telephone ->
            letter.prepare(); telephone.prepare()
            var screen by mutableStateOf(Screen("T14", WorkspaceMode.TELEPHONE, app.services.buildWorkflow, AppearanceMode.DARK))
            composeRule.activity.runOnUiThread { composeRule.activity.setContent {
                TerritoryCardStudioTheme(appearanceMode = screen.appearance) {
                    key(screen) { BuildWorkflowScreen(Modifier.fillMaxSize().safeDrawingPadding(), screen.id, screen.mode, screen.coordinator) {} }
                }
            } }
            awaitReadiness()
            capture("phase2db-blocked-dark.png", dark = true)
            captureBlockedControls("dark", true)
            composeRule.runOnIdle { screen = screen.copy(appearance = AppearanceMode.LIGHT) }
            awaitReadiness()
            capture("phase2db-blocked-light.png", dark = false)
            captureBlockedControls("light", false)
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
                capture("phase2db-generated-$name.png", dark = appearance == AppearanceMode.DARK)
            }
        } }
    }
    private fun captureBlockedControls(name: String, dark: Boolean) {
        composeRule.onNodeWithTag("build-screen").performScrollToNode(hasTestTag("generate-page2"))
        composeRule.onNodeWithTag("generate-page2").assertIsNotEnabled().assertIsDisplayed()
        composeRule.onNodeWithTag("build-front").assertIsNotEnabled()
        capture("phase2db-blocked-controls-$name.png", dark)
    }
    private fun awaitReadiness() {
        composeRule.waitUntil(10000) { composeRule.onAllNodesWithTag("build-readiness").fetchSemanticsNodes().isNotEmpty() }
        composeRule.waitForIdle()
    }
    private fun capture(name: String, dark: Boolean) {
        composeRule.waitForIdle()
        val inst = InstrumentationRegistry.getInstrumentation()
        inst.waitForIdleSync()
        composeRule.onNodeWithTag("build-screen").captureToImage() // Wait for actual node drawing / PixelCopy.
        // Compose semantics can be ready before SurfaceFlinger presents the initial frame.
        // Accept only real display pixels with the requested theme and visible content.
        val deadline = android.os.SystemClock.uptimeMillis() + 10000
        var rendered: Bitmap? = null
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            val candidate = requireNotNull(inst.uiAutomation.takeScreenshot())
            val background = candidate.getPixel(10, candidate.height / 2)
            val red = android.graphics.Color.red(background)
            var low = 255
            var high = 0
            for (y in 200 until candidate.height - 150 step 20) {
                for (x in 30 until candidate.width - 30 step 20) {
                    val pixel = candidate.getPixel(x, y)
                    val value = (android.graphics.Color.red(pixel) + android.graphics.Color.green(pixel) + android.graphics.Color.blue(pixel)) / 3
                    low = minOf(low, value); high = maxOf(high, value)
                }
            }
            if ((if (dark) red < 40 else red > 220) && high - low > 80) {
                rendered = candidate
                break
            }
            candidate.recycle()
            android.os.SystemClock.sleep(100)
        }
        val bitmap = requireNotNull(rendered) { "Expected nonblank displayed frame was not presented: $name" }
        File(inst.targetContext.filesDir, name).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}

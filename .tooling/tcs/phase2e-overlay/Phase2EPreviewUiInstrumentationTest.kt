package com.koenterprises.territorycardstudio

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.koenterprises.territorycardstudio.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class Phase2EPreviewUiInstrumentationTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    private fun content(f: Phase2DBFixture, appearance: AppearanceMode): AndroidPdfPreviewService {
        val service = AndroidPdfPreviewService(f.coordinator, File(f.root, "preview-ui"))
        composeRule.activity.runOnUiThread { composeRule.activity.setContent {
            TerritoryCardStudioTheme(appearance) {
                BuildWorkflowScreen(Modifier.fillMaxSize().safeDrawingPadding(), f.identity.displayId,
                    f.mode, f.coordinator, service) {}
            }
        } }
        return service
    }
    private fun open(tag: String) {
        composeRule.waitUntil(15000) { composeRule.onAllNodesWithTag("build-readiness").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("build-screen").performScrollToNode(hasTestTag(tag))
        composeRule.onNodeWithTag(tag).performClick()
        awaitPage()
    }
    private fun awaitPage() {
        composeRule.waitUntil(20000) { composeRule.onAllNodesWithTag("preview-page").fetchSemanticsNodes().isNotEmpty() }
        composeRule.waitForIdle()
    }
    private fun prepareCapture(f: Phase2DBFixture) {
        val inventory = when (val v = f.inventory) {
            is Page2Inventory.LetterWriting -> Page2Inventory.LetterWriting(v.inventory.copy(records = (1..24).map {
                v.inventory.records.first().copy(recordId = "capture-$it", streetAddress = "${100 + it} Example Way")
            }))
            is Page2Inventory.Telephone -> Page2Inventory.Telephone(v.inventory.copy(records = (1..24).map {
                v.inventory.records.first().copy(recordId = "capture-$it", streetAddress = "${100 + it} Example Way",
                    phoneState = if (it % 3 == 0) TelephoneNumberState.UNAVAILABLE else TelephoneNumberState.VERIFIED_NUMBER,
                    phoneNumber = if (it % 3 == 0) null else "20255501" + it.toString().padStart(2, '0'))
            }))
        }
        f.coordinator.prepare(f.identity.displayId, f.mode, f.source.sha256, f.input, inventory)
        assertNull(f.build().error); assertNull(f.page2().error)
    }

    @Test fun buildNavigatesFrontAndPacketPreviewAndResumeRejectsChangedBytes() {
        Phase2DBFixture(false).use { f ->
            f.prepare(); f.build(); f.page2(); content(f, AppearanceMode.DARK)
            open("preview-front")
            composeRule.onNodeWithText("1 / 1").assertIsDisplayed()
            composeRule.onNodeWithTag("preview-next").assertIsNotEnabled()
            composeRule.onNodeWithTag("preview-back").performClick()
            open("preview-packet")
            composeRule.onNodeWithTag("preview-next").performClick(); awaitPage()
            composeRule.onNodeWithText("2 / 2").assertIsDisplayed()
            composeRule.onNodeWithTag("preview-previous").performClick(); awaitPage()
            composeRule.onNodeWithText("1 / 2").assertIsDisplayed()
            composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            f.state().packet!!.file.appendText("changed while paused")
            composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            composeRule.waitUntil(20000) { composeRule.onAllNodesWithTag("preview-error").fetchSemanticsNodes().isNotEmpty() }
            composeRule.onNodeWithTag("preview-page").assertDoesNotExist()
            composeRule.onNodeWithTag("preview-next").assertIsNotEnabled()
            assertTrue(File(f.root, "preview-ui").listFiles().orEmpty().isEmpty())
        }
    }

    @Test fun captureActualPdfFrontBackZoomAndBlockedStates() {
        Phase2DBFixture(false).use { f ->
            prepareCapture(f); content(f, AppearanceMode.DARK)
            val files = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
            f.state().packet!!.file.copyTo(File(files, "phase2e-synthetic-letter.pdf"), overwrite = true)
            open("preview-packet")
            capture("phase2e-front-dark.png", true)
            composeRule.onNodeWithTag("preview-zoom").performClick(); awaitPage()
            composeRule.onNodeWithTag("preview-pan").performTouchInput { swipeLeft() }
            capture("phase2e-front-zoom-dark.png", true)
            composeRule.onNodeWithTag("preview-fit").performClick(); awaitPage()
            composeRule.onNodeWithTag("preview-next").performClick(); awaitPage()
            capture("phase2e-letter-back-dark.png", true)
            composeRule.onNodeWithTag("preview-zoom").performClick(); awaitPage()
            capture("phase2e-letter-back-zoom-dark.png", true)
            f.importSource("changed-during-preview")
            composeRule.onNodeWithTag("preview-refresh").performClick()
            composeRule.waitUntil(20000) { composeRule.onAllNodesWithTag("preview-error").fetchSemanticsNodes().isNotEmpty() }
            composeRule.onNodeWithTag("preview-page").assertDoesNotExist()
            capture("phase2e-stale-blocked-dark.png", true)
        }
        Phase2DBFixture(true).use { f ->
            prepareCapture(f); content(f, AppearanceMode.LIGHT)
            val files = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
            f.state().packet!!.file.copyTo(File(files, "phase2e-synthetic-telephone.pdf"), overwrite = true)
            open("preview-packet")
            capture("phase2e-front-light.png", false)
            composeRule.onNodeWithTag("preview-next").performClick(); awaitPage()
            capture("phase2e-telephone-back-light.png", false)
            composeRule.onNodeWithTag("preview-zoom").performClick(); awaitPage()
            capture("phase2e-telephone-back-zoom-light.png", false)
        }
    }

    private fun capture(name: String, dark: Boolean) {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("pdf-preview-screen").captureToImage()
        val inst = InstrumentationRegistry.getInstrumentation()
        inst.waitForIdleSync()
        val deadline = android.os.SystemClock.uptimeMillis() + 10000
        var rendered: Bitmap? = null
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            val candidate = requireNotNull(inst.uiAutomation.takeScreenshot())
            val red = android.graphics.Color.red(candidate.getPixel(10, candidate.height / 2))
            if (if (dark) red < 40 else red > 220) { rendered = candidate; break }
            candidate.recycle(); android.os.SystemClock.sleep(100)
        }
        val bitmap = requireNotNull(rendered) { "Expected themed preview frame was not presented" }
        File(inst.targetContext.filesDir, name).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}

package com.koenterprises.territorycardstudio

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class Phase2FReviewUiInstrumentationTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()
    private fun content(f: Phase2DBFixture, appearance: AppearanceMode) {
        val preview = AndroidPdfPreviewService(f.coordinator, File(f.root, "preview"))
        val review = AndroidCandidateReviewService(f.coordinator, File(f.root, "reviews"))
        composeRule.activity.runOnUiThread { composeRule.activity.setContent {
            TerritoryCardStudioTheme(appearance) {
                BuildWorkflowScreen(Modifier.fillMaxSize().safeDrawingPadding(), f.identity.displayId,
                    f.mode, f.coordinator, preview, review) {}
            }
        } }
    }
    private fun await(tag: String) {
        composeRule.waitUntil(20000) { composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        composeRule.waitForIdle()
    }
    private fun awaitReady() {
        composeRule.waitUntil(20000) {
            composeRule.onNodeWithTag("candidate-review-screen").fetchSemanticsNode()
                .config.getOrElse(SemanticsProperties.StateDescription) { "" } == "ready"
        }
        composeRule.waitForIdle()
    }
    private fun awaitDecision() { awaitReady(); show("review-decision"); await("review-decision") }
    private fun show(tag: String) { composeRule.onNodeWithTag("review-list").performScrollToNode(hasTestTag(tag)) }
    private fun click(tag: String) { show(tag); composeRule.onNodeWithTag(tag).performClick() }
    private fun open() {
        await("build-readiness")
        composeRule.onNodeWithTag("build-screen").performScrollToNode(hasTestTag("review-candidate"))
        composeRule.onNodeWithTag("review-candidate").performClick()
        await("review-identity")
    }
    private fun ready(f: Phase2DBFixture) { f.prepare(); assertNull(f.build().error); assertNull(f.page2().error) }
    private fun checks() {
        click("review-pages"); click("review-boundaries"); click("review-data")
        show("review-actor"); composeRule.onNodeWithTag("review-actor").performTextInput("Synthetic Reviewer")
        composeRule.activity.runOnUiThread {
            val manager = composeRule.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            manager.hideSoftInputFromWindow(composeRule.activity.window.decorView.windowToken, 0)
            composeRule.activity.window.decorView.clearFocus()
        }
        composeRule.waitForIdle()
    }
    @Test fun confirmationCancelAndResumeRevalidationProtectCurrentDecision() {
        Phase2DBFixture(false).use { f ->
            ready(f); content(f, AppearanceMode.DARK); open()
            show("review-approve"); composeRule.onNodeWithTag("review-approve").assertIsNotEnabled()
            checks(); click("review-approve"); await("review-confirmation")
            composeRule.onNodeWithTag("review-cancel").performClick()
            composeRule.onNodeWithTag("review-decision").assertDoesNotExist()
            click("review-approve"); composeRule.onNodeWithTag("review-confirm").performClick()
            awaitDecision()
            show("review-decision"); composeRule.onNodeWithText("Local approval recorded").assertIsDisplayed()
            show("review-pages"); composeRule.onNodeWithTag("review-pages").assertIsOff()
            click("review-preview"); await("preview-page")
            composeRule.onNodeWithTag("preview-back").performClick(); await("review-identity")
            composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            f.importSource("changed-while-review-paused")
            composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            await("review-blocked")
            composeRule.onNodeWithTag("review-approve").assertDoesNotExist()
            composeRule.onNodeWithTag("review-decision").assertDoesNotExist()
        }
    }

    @Test fun candidateChangedWhileConfirmationOpenCannotBeApproved() {
        Phase2DBFixture(true).use { f ->
            ready(f); content(f, AppearanceMode.LIGHT); open(); checks(); click("review-approve")
            await("review-confirmation"); f.page2()
            composeRule.onNodeWithTag("review-confirm").performClick()
            awaitReady(); show("review-error"); await("review-error")
            show("review-error"); composeRule.onNodeWithTag("review-error").assertTextContains("Candidate changed", substring = true)
            composeRule.onNodeWithTag("review-decision").assertDoesNotExist()
            show("review-pages"); composeRule.onNodeWithTag("review-pages").assertIsOff()
        }
    }

    @Test fun captureReviewReadyConfirmationApprovalRejectionAndBlocked() {
        Phase2DBFixture(false).use { f ->
            ready(f); content(f, AppearanceMode.DARK); open()
            capture("phase2f-ready-dark.png", true)
            checks(); show("review-approve"); capture("phase2f-checklist-dark.png", true)
            click("review-approve"); await("review-confirmation")
            capture("phase2f-confirm-dark.png", true)
            composeRule.onNodeWithTag("review-confirm").performClick(); awaitDecision()
            show("review-decision"); capture("phase2f-approved-local-dark.png", true)
            show("review-actor"); composeRule.onNodeWithTag("review-actor").performTextInput("Synthetic Reviewer")
            click("review-reject"); composeRule.onNodeWithTag("review-confirm").performClick(); awaitDecision()
            show("review-decision"); composeRule.onNodeWithText("Candidate rejected").assertIsDisplayed()
            capture("phase2f-rejected-dark.png", true)
            f.state().packet!!.file.appendText("tampered")
            click("review-refresh"); await("review-blocked")
            show("review-blocked"); capture("phase2f-blocked-dark.png", true)
        }
        Phase2DBFixture(true).use { f ->
            ready(f); content(f, AppearanceMode.LIGHT); open()
            capture("phase2f-ready-light.png", false)
            checks(); click("review-approve"); composeRule.onNodeWithTag("review-confirm").performClick()
            awaitDecision(); show("review-decision")
            capture("phase2f-approved-local-light.png", false)
        }
    }

    private fun capture(name: String, dark: Boolean) {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("candidate-review-screen").captureToImage()
        val inst = InstrumentationRegistry.getInstrumentation(); inst.waitForIdleSync()
        val deadline = android.os.SystemClock.uptimeMillis() + 10000
        var rendered: Bitmap? = null
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            val candidate = requireNotNull(inst.uiAutomation.takeScreenshot())
            val red = android.graphics.Color.red(candidate.getPixel(10, candidate.height / 2))
            if (if (dark) red < 40 else red > 220) { rendered = candidate; break }
            candidate.recycle(); android.os.SystemClock.sleep(100)
        }
        val bitmap = requireNotNull(rendered)
        File(inst.targetContext.filesDir, name).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}

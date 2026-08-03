package com.kreativstudio.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.kreativstudio.app.MainActivity
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class PrimaryRoutesTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mentorRouteRendersCanvasAwareExperience() {
        enterOliviaPreview()

        composeRule.onNodeWithText("Ask Mentor").performScrollTo().performClick()
        composeRule.waitUntilAtLeastOneExists(
            hasText("Canvas-aware teacher and critic"),
            timeoutMillis = 10_000,
        )
        composeRule.onNodeWithText("Canvas-aware teacher and critic").assertIsDisplayed()
        composeRule.onNodeWithText("Analyze current canvas").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back to Atelier").assertExists()
    }

    @Test
    fun lessonRouteRendersInstructionAndCanvasWorkspace() {
        enterOliviaPreview()

        composeRule.onNodeWithText("Start lesson").performScrollTo().performClick()
        composeRule.waitUntilAtLeastOneExists(
            hasText("KREATIV Mentor Academy"),
            timeoutMillis = 10_000,
        )
        composeRule.onAllNodesWithText("Begin lesson")[0].performScrollTo().performClick()

        composeRule.waitUntilAtLeastOneExists(
            hasContentDescription("Back to lessons"),
            timeoutMillis = 15_000,
        )
        composeRule.onNodeWithContentDescription("Back to lessons").assertIsDisplayed()
        composeRule.onNodeWithText("Objective").assertExists()
        composeRule.onNodeWithText("Check my work").assertExists()
    }

    @Test
    fun newCanvasRouteRendersAdaptiveStudio() {
        enterOliviaPreview()

        composeRule.onNodeWithText("New canvas").performScrollTo().performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("Create canvas"), timeoutMillis = 10_000)
        composeRule.onNodeWithText("Create canvas").performClick()

        composeRule.waitUntilAtLeastOneExists(
            hasContentDescription("Back to Atelier"),
            timeoutMillis = 15_000,
        )
        composeRule.onNodeWithContentDescription("Back to Atelier").assertIsDisplayed()
        composeRule.onNodeWithText("Controls").assertExists()
    }

    private fun enterOliviaPreview() {
        composeRule.waitUntilAtLeastOneExists(
            hasText("Open Olivia's private preview"),
            timeoutMillis = 10_000,
        )
        composeRule.onNodeWithText("Open Olivia's private preview").performScrollTo().performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("Ask Mentor"), timeoutMillis = 10_000)
    }
}

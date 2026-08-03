package com.kreativstudio.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
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
    fun mentorLessonAndNewCanvasRoutesRenderWithoutClosing() {
        composeRule.onNodeWithText("Open Olivia's private preview").performScrollTo().performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("Ask Mentor"), timeoutMillis = 10_000)

        composeRule.onNodeWithText("Ask Mentor").performScrollTo().performClick()
        composeRule.onNodeWithText("Canvas-aware teacher and critic").assertIsDisplayed()
        composeRule.onNodeWithText("Analyze current canvas").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Back to Atelier").performClick()
        composeRule.onNodeWithText("Start lesson").performScrollTo().performClick()
        composeRule.onNodeWithText("KREATIV Mentor Academy").assertIsDisplayed()
        composeRule.onAllNodesWithText("Begin lesson")[0].performClick()

        composeRule.waitUntilAtLeastOneExists(hasText("Objective"), timeoutMillis = 15_000)
        composeRule.onNodeWithText("Objective").assertIsDisplayed()
        composeRule.onNodeWithText("Show me").assertIsDisplayed()
        composeRule.onNodeWithText("Check my work").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Back to lessons").performClick()
        composeRule.onNodeWithText("Atelier").performClick()
        composeRule.onNodeWithText("New canvas").performScrollTo().performClick()
        composeRule.onNodeWithText("Create canvas").performClick()

        composeRule.waitUntilAtLeastOneExists(
            hasContentDescription("Back to Atelier"),
            timeoutMillis = 15_000,
        )
        composeRule.onNodeWithContentDescription("Back to Atelier").assertIsDisplayed()
        composeRule.onNodeWithText("Controls").assertIsDisplayed()
    }
}

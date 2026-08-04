package com.kreativstudio.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
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
    fun studioTabWithoutOpenProjectShowsRecoveryUi() {
        composeRule.waitUntilAtLeastOneExists(
            hasText("Open Olivia's private preview"),
            timeoutMillis = 15_000,
        )
        composeRule.onNodeWithText("Open Olivia's private preview").performScrollTo().performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("Studio"), timeoutMillis = 15_000)

        composeRule.onNodeWithText("Studio").performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("No canvas is open"), timeoutMillis = 15_000)
        composeRule.waitUntilAtLeastOneExists(hasText("Create canvas"), timeoutMillis = 15_000)
        composeRule.waitUntilAtLeastOneExists(hasText("Back to Atelier"), timeoutMillis = 15_000)

        composeRule.onNodeWithText("Back to Atelier").performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("New canvas"), timeoutMillis = 15_000)
    }

    @Test
    fun mentorLessonAndNewCanvasRoutesStayOpen() {
        composeRule.waitUntilAtLeastOneExists(
            hasText("Open Olivia's private preview"),
            timeoutMillis = 15_000,
        )
        composeRule.onNodeWithText("Open Olivia's private preview").performScrollTo().performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("Ask Mentor"), timeoutMillis = 15_000)

        composeRule.onNodeWithText("Ask Mentor").performScrollTo().performClick()
        composeRule.waitUntilAtLeastOneExists(
            hasContentDescription("Back to Atelier"),
            timeoutMillis = 15_000,
        )
        composeRule.waitUntilAtLeastOneExists(hasText("KREATIV Mentor"), timeoutMillis = 15_000)
        composeRule.onNodeWithContentDescription("Back to Atelier").performClick()

        composeRule.waitUntilAtLeastOneExists(hasText("Start lesson"), timeoutMillis = 15_000)
        composeRule.onNodeWithText("Start lesson").performScrollTo().performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("Begin lesson"), timeoutMillis = 15_000)
        composeRule.onAllNodesWithText("Begin lesson")[0].performScrollTo().performClick()
        composeRule.waitUntilAtLeastOneExists(
            hasContentDescription("Back to lessons"),
            timeoutMillis = 20_000,
        )
        composeRule.onNodeWithContentDescription("Back to lessons").performClick()

        composeRule.waitUntilAtLeastOneExists(hasText("Atelier"), timeoutMillis = 15_000)
        composeRule.onNodeWithText("Atelier").performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("New canvas"), timeoutMillis = 15_000)
        composeRule.onNodeWithText("New canvas").performScrollTo().performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("Create canvas"), timeoutMillis = 10_000)
        composeRule.onNodeWithText("Create canvas").performClick()
        composeRule.waitUntilAtLeastOneExists(
            hasContentDescription("Back to Atelier"),
            timeoutMillis = 20_000,
        )
    }
}

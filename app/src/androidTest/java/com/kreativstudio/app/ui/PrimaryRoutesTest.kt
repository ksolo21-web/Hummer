package com.kreativstudio.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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
    fun mentorAndNewCanvasRoutesRenderWithoutClosing() {
        composeRule.onNodeWithText("Open Olivia's private preview").performScrollTo().performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("Ask Mentor"), timeoutMillis = 10_000)

        composeRule.onNodeWithText("Ask Mentor").performScrollTo().performClick()
        composeRule.onNodeWithText("KREATIV Mentor").assertIsDisplayed()

        composeRule.onNodeWithText("Atelier").performClick()
        composeRule.onNodeWithText("New canvas").performScrollTo().performClick()
        composeRule.onNodeWithText("Create canvas").performClick()

        composeRule.waitUntilAtLeastOneExists(
            hasContentDescription("Back to Atelier"),
            timeoutMillis = 15_000,
        )
        composeRule.onNodeWithContentDescription("Back to Atelier").assertIsDisplayed()
        composeRule.onNodeWithText("Controls").assertIsDisplayed()
        composeRule.onNodeWithText("Atelier").assertDoesNotExist()
    }
}

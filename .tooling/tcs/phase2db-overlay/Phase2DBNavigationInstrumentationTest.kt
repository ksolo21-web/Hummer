package com.koenterprises.territorycardstudio

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase2DBNavigationInstrumentationTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()
    @Test fun realWorkspaceOpensBlockedBuildWithoutCommissioningTerritory() {
        composeRule.onNodeWithTag("territories-dashboard").performScrollToNode(hasTestTag("territory-row-1"))
        composeRule.onNodeWithTag("territory-row-1").performClick()
        composeRule.onNodeWithTag("territory-workspace").performScrollToNode(hasTestTag("workspace-build"))
        composeRule.onNodeWithTag("workspace-build").performClick()
        composeRule.waitUntil(10000) { composeRule.onAllNodesWithTag("build-readiness").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("build-screen").performScrollToNode(hasTestTag("build-front"))
        composeRule.onNodeWithTag("build-front").assertIsNotEnabled()
        composeRule.onNodeWithText("The approved card is preserved. Creating a replacement requires the commissioning workflow.").assertExists()
    }
}

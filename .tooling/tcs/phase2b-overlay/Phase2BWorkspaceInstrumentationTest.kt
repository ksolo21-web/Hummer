package com.koenterprises.territorycardstudio

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase2BWorkspaceInstrumentationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun realKnowledgeBaseModesAndTabsMatchPlan() {
        val app = ApplicationProvider.getApplicationContext<TerritoryCardStudioApplication>()
        val dashboard = TerritoryDashboardModel.from(app.services.knowledgeBase)

        val regular = dashboard.items.first { it.assignment.displayId == "1" }
        assertEquals(
            listOf(WorkspaceMode.REGULAR, WorkspaceMode.LETTER_WRITING),
            WorkspaceModePolicy.allowedModes(regular.assignment)
        )
        assertEquals(
            listOf(WorkspaceTab.MAP, WorkspaceTab.STREETS, WorkspaceTab.BUILDINGS, WorkspaceTab.DETAILS),
            WorkspaceModePolicy.tabs(WorkspaceMode.REGULAR)
        )
        assertEquals(
            listOf(WorkspaceTab.MAP, WorkspaceTab.ADDRESSES, WorkspaceTab.BUILDINGS, WorkspaceTab.DETAILS),
            WorkspaceModePolicy.tabs(WorkspaceMode.LETTER_WRITING)
        )

        val telephone = dashboard.items.first { it.assignment.displayId == "T14" }
        assertEquals(listOf(WorkspaceMode.TELEPHONE), WorkspaceModePolicy.allowedModes(telephone.assignment))
        assertEquals(
            listOf(WorkspaceTab.MAP, WorkspaceTab.PHONE_LIST, WorkspaceTab.BUILDINGS, WorkspaceTab.DETAILS),
            WorkspaceModePolicy.tabs(WorkspaceMode.TELEPHONE)
        )
    }

    @Test
    fun needsNewCardReadinessStaysFailClosed() {
        val app = ApplicationProvider.getApplicationContext<TerritoryCardStudioApplication>()
        val dashboard = TerritoryDashboardModel.from(app.services.knowledgeBase)
        val needsNew = dashboard.items.first { it.assignment.displayId == "T250" }
        val readiness = WorkspaceReadinessModel.from(needsNew)

        assertTrue(readiness.reviewChecks > 0)
        assertEquals("Blocked — new card required", readiness.exactArtifactReleaseLabel)
        assertTrue(readiness.candidateReadinessLabel.contains("explicit approval"))
        assertTrue(readiness.provenanceLabel.contains("Legacy reference"))
    }

    @Test
    fun regularWorkspaceCanSwitchToLetterWritingTabsWithoutChangingAssignment() {
        composeRule.onNodeWithTag("territories-dashboard").assertIsDisplayed()
        composeRule.onNodeWithTag("territories-dashboard").performScrollToNode(hasTestTag("territory-row-1"))
        composeRule.onNodeWithTag("territory-row-1").performClick()
        composeRule.onNodeWithTag("territory-workspace").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace-readiness").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace-tab-Streets").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace-mode-Letter-Writing").performClick()
        composeRule.onNodeWithTag("workspace-tab-Addresses").assertIsDisplayed()
        composeRule.onAllNodesWithTag("workspace-tab-Streets").assertCountEquals(0)
        composeRule.onNodeWithTag("workspace-tab-Addresses").performClick()
        composeRule.onNodeWithTag("letter-inventory-unavailable").assertIsDisplayed()
    }

    @Test
    fun telephoneWorkspaceUsesPhoneListAndNeverShowsRegularModeSwitch() {
        val app = ApplicationProvider.getApplicationContext<TerritoryCardStudioApplication>()
        val dashboard = TerritoryDashboardModel.from(app.services.knowledgeBase)
        val telephone = dashboard.items.first { it.assignment.displayId == "T14" }

        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                TerritoryCardStudioTheme(AppearanceMode.LIGHT) {
                    ModeAwareTerritoryWorkspace(
                        modifier = Modifier.fillMaxSize(),
                        item = telephone,
                        knowledgeBaseRevision = dashboard.revision,
                        onBack = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("workspace-tab-Phone-List").assertIsDisplayed()
        composeRule.onAllNodesWithTag("workspace-mode-Letter-Writing").assertCountEquals(0)
        composeRule.onNodeWithTag("workspace-tab-Phone-List").performClick()
        composeRule.onNodeWithTag("phone-inventory-unavailable").assertIsDisplayed()
    }
}

package com.koenterprises.territorycardstudio

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream

@RunWith(AndroidJUnit4::class)
class Phase2CImportVerificationInstrumentationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun sourceMapIntakeIsPersistedHashBoundAndNeverAssignmentAuthority() {
        val app = ApplicationProvider.getApplicationContext<TerritoryCardStudioApplication>()
        val item = TerritoryDashboardModel.from(app.services.knowledgeBase).items.first {
            it.assignment.displayId == "1"
        }
        val store = SourceMapIntakeStore(app)
        store.clear(item.assignment.displayId)

        val bytes = "%PDF-1.4\n% synthetic authorized test source\n".toByteArray()
        val record = store.importFromStream(
            assignment = item.assignment,
            sourceFilename = "territory-1-source.pdf",
            mimeType = "application/pdf",
            input = ByteArrayInputStream(bytes),
            importedAtUtc = "2026-09-26T22:59:00Z"
        )

        assertEquals(item.assignment.displayId, record.territoryDisplayId)
        assertEquals(item.assignment.canonicalFilename, record.canonicalFilename)
        assertEquals("user_provided_source_map", record.provenanceType)
        assertFalse(record.assignmentAuthority)
        assertEquals(record, SourceMapIntakeStore(app).get(item.assignment.displayId))
    }

    @Test
    fun verificationModelUsesFrozenEngineStateAndRemainsFailClosed() {
        val app = ApplicationProvider.getApplicationContext<TerritoryCardStudioApplication>()
        val dashboard = TerritoryDashboardModel.from(app.services.knowledgeBase)
        val item = dashboard.items.first { it.assignment.displayId == "1" }
        val store = SourceMapIntakeStore(app)
        store.clear(item.assignment.displayId)
        val record = store.importFromStream(
            assignment = item.assignment,
            sourceFilename = "territory-1-map.png",
            mimeType = "image/png",
            input = ByteArrayInputStream(byteArrayOf(
                0x89.toByte(), 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A,
                1, 2, 3, 4
            )),
            importedAtUtc = "2026-09-26T23:00:00Z"
        )

        val regular = VerificationWorkflowModel.from(
            item = item,
            mode = WorkspaceMode.REGULAR,
            intake = record,
            knowledgeBase = app.services.knowledgeBase,
            onlinePolicy = app.services.activePolicy
        )
        assertFalse(regular.buildMayProceed)
        assertEquals(
            VerificationUiState.NEEDS_REVIEW,
            regular.categories.first { it.key == "source_truth" }.state
        )
        assertEquals(
            VerificationUiState.PENDING,
            regular.categories.first { it.key == "geometry" }.state
        )
        assertEquals(
            VerificationUiState.NOT_APPLICABLE,
            regular.categories.first { it.key == "inventory" }.state
        )
        assertTrue(regular.policyRevision.isNotBlank())

        val letter = VerificationWorkflowModel.from(
            item = item,
            mode = WorkspaceMode.LETTER_WRITING,
            intake = record,
            knowledgeBase = app.services.knowledgeBase,
            onlinePolicy = app.services.activePolicy
        )
        assertEquals(
            VerificationUiState.BLOCKED,
            letter.categories.first { it.key == "inventory" }.state
        )

        val needsNewItem = dashboard.items.first { it.assignment.displayId == "T250" }
        val needsNew = VerificationWorkflowModel.from(
            item = needsNewItem,
            mode = WorkspaceMode.TELEPHONE,
            intake = null,
            knowledgeBase = app.services.knowledgeBase,
            onlinePolicy = app.services.activePolicy
        )
        assertEquals(
            VerificationUiState.BLOCKED,
            needsNew.categories.first { it.key == "field_release" }.state
        )
        assertFalse(needsNew.buildMayProceed)

        val telephoneItem = dashboard.items.first { it.assignment.displayId == "T14" }
        val telephone = VerificationWorkflowModel.from(
            item = telephoneItem,
            mode = WorkspaceMode.TELEPHONE,
            intake = null,
            knowledgeBase = app.services.knowledgeBase,
            onlinePolicy = app.services.activePolicy
        )
        assertEquals(
            VerificationUiState.BLOCKED,
            telephone.categories.first { it.key == "source_truth" }.state
        )
        assertEquals(
            VerificationUiState.BLOCKED,
            telephone.categories.first { it.key == "inventory" }.state
        )
    }

    @Test
    fun intakeRejectsMimeSignatureMismatch() {
        val app = ApplicationProvider.getApplicationContext<TerritoryCardStudioApplication>()
        val item = TerritoryDashboardModel.from(app.services.knowledgeBase).items.first {
            it.assignment.displayId == "1"
        }
        val store = SourceMapIntakeStore(app)
        val failure = runCatching {
            store.importFromStream(
                assignment = item.assignment,
                sourceFilename = "not-really-a-pdf.pdf",
                mimeType = "application/pdf",
                input = ByteArrayInputStream("not a pdf".toByteArray()),
                importedAtUtc = "2026-09-26T23:00:30Z"
            )
        }
        assertTrue(failure.isFailure)
        assertTrue(failure.exceptionOrNull()?.message?.contains("signature") == true)
    }

    @Test
    fun workspaceNavigatesImportToVerificationWithPersistedSource() {
        val app = ApplicationProvider.getApplicationContext<TerritoryCardStudioApplication>()
        val dashboard = TerritoryDashboardModel.from(app.services.knowledgeBase)
        val item = dashboard.items.first { it.assignment.displayId == "1" }
        val store = SourceMapIntakeStore(app)
        store.clear(item.assignment.displayId)
        val record = store.importFromStream(
            assignment = item.assignment,
            sourceFilename = "territory-1-ui-source.pdf",
            mimeType = "application/pdf",
            input = ByteArrayInputStream("%PDF-1.4\nsynthetic ui source".toByteArray()),
            importedAtUtc = "2026-09-26T23:01:00Z"
        )
        assertNotNull(record)

        composeRule.onNodeWithTag("territories-dashboard").assertIsDisplayed()
        composeRule.onNodeWithTag("territories-dashboard").performScrollToNode(hasTestTag("territory-row-1"))
        composeRule.onNodeWithTag("territory-row-1").performClick()
        composeRule.onNodeWithTag("territory-workspace").assertIsDisplayed()

        composeRule.onNodeWithTag("territory-workspace").performScrollToNode(hasTestTag("workspace-import-map"))
        composeRule.onNodeWithTag("workspace-import-map").performClick()
        composeRule.onNodeWithTag("import-map-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("import-source-card").assertIsDisplayed()
        composeRule.onNodeWithTag("continue-verification").performClick()

        composeRule.onNodeWithTag("verification-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("verification-source_truth").assertIsDisplayed()
        composeRule.onNodeWithTag("verification-geometry").assertIsDisplayed()
    }
}

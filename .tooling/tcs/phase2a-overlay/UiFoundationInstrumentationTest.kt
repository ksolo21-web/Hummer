package com.koenterprises.territorycardstudio

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiFoundationInstrumentationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun dashboardUsesRealKnowledgeBaseAndNavigatesToWorkspace() {
        val app = ApplicationProvider.getApplicationContext<TerritoryCardStudioApplication>()
        val dashboard = TerritoryDashboardModel.from(app.services.knowledgeBase)
        assertEquals(225, dashboard.items.size)
        assertEquals(
            setOf("T250", "A257", "297", "A298", "299", "TA347"),
            dashboard.items
                .filter { it.status == TerritoryUiStatus.NEEDS_NEW_CARD }
                .map { it.assignment.displayId }
                .toSet()
        )

        composeRule.onNodeWithTag("territories-dashboard").assertIsDisplayed()
        composeRule.onNodeWithTag("territory-row-1").performClick()
        composeRule.onNodeWithTag("territory-workspace").assertIsDisplayed()
    }

    @Test
    fun appearancePreferencePersistsAcrossStoreInstances() {
        val app = ApplicationProvider.getApplicationContext<TerritoryCardStudioApplication>()
        val store = AppearancePreferenceStore(app)
        store.setMode(AppearanceMode.DARK)
        assertEquals(AppearanceMode.DARK, AppearancePreferenceStore(app).mode.value)
        store.setMode(AppearanceMode.LIGHT)
        assertEquals(AppearanceMode.LIGHT, AppearancePreferenceStore(app).mode.value)
        store.setMode(AppearanceMode.SYSTEM)
        assertEquals(AppearanceMode.SYSTEM, AppearancePreferenceStore(app).mode.value)
        assertTrue(AppearanceMode.entries.map { it.label }.containsAll(listOf("System", "Light", "Dark")))
    }
}

package com.koenterprises.territorycardstudio

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.roundToInt
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class Phase2JWorkspaceUiInstrumentationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun waitFor(tag: String) { rule.waitUntil(20000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }; rule.waitForIdle() }
    private fun scroll(tag: String) { rule.onNodeWithTag("territory-workspace").performScrollToNode(hasTestTag(tag)) }
    private fun show(f: Phase2DBFixture, theme: AppearanceMode) {
        val preview = AndroidPdfPreviewService(f.coordinator, File(f.root, "workspace-preview"), f.service)
        val item = TerritoryDashboardModel.from(f.kb).items.first { it.assignment.displayId == f.identity.displayId }
        rule.runOnIdle { rule.activity.setContent { TerritoryCardStudioTheme(theme) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            ModeAwareTerritoryWorkspace(Modifier.fillMaxSize().safeDrawingPadding(), item, f.kb.revision, {}, f.kb, f.coordinator, preview)
        } } } }
        waitFor("territory-workspace")
        if (!f.telephoneMode) { scroll("workspace-mode-Letter-Writing"); rule.onNodeWithTag("workspace-mode-Letter-Writing").performClick() }
    }
    private fun shot(name: String) {
        val pdf = name.startsWith("candidate-") || name.startsWith("approved-")
        if (pdf) {
            // Frame both the identity label and the entire PDF, including its footer.
            scroll("workspace-pdf-hash"); scroll("workspace-preview-kind")
            waitFor("workspace-pdf-page")
        }
        var accepted: Bitmap? = null
        var previous: Bitmap? = null
        try {
            rule.waitUntil(20000) {
                rule.waitForIdle()
                // PixelCopy waits for a drawn Compose frame, not only semantics publication.
                rule.onRoot().captureToImage()
                val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
                var ready = true
                if (pdf) {
                    val node = rule.onNodeWithTag("workspace-pdf-page").fetchSemanticsNode()
                    val position = node.positionInWindow
                    val left = position.x.roundToInt(); val top = position.y.roundToInt()
                    val right = left + node.size.width; val bottom = top + node.size.height
                    val label = rule.onNodeWithTag("workspace-preview-kind").fetchSemanticsNode()
                    ready = label.positionInWindow.y >= 24 &&
                        label.positionInWindow.y + label.size.height < bitmap.height - 24 && left >= 0 && top >= 0 &&
                        right <= bitmap.width && bottom < bitmap.height - 24 && node.size.height > 200
                    if (ready) {
                        val colors = IntArray(3)
                        for (y in top until bottom) for (x in left until right) {
                            val c = bitmap.getPixel(x,y)
                            val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
                            if (r > 200 && g > 170 && b < 90) colors[0]++
                            if (g > 140 && r < 130 && b < 130) colors[1]++
                            if (r > 210 && g < 100 && b < 140) colors[2]++
                        }
                        ready = colors.all { it > 40 }
                    }
                }
                // Two successive actual frames must agree below system chrome.
                val before = previous
                var stable = before != null && before.width == bitmap.width && before.height == bitmap.height
                if (stable) {
                    for (y in 80 until bitmap.height - 48 step 8) for (x in 0 until bitmap.width step 8) {
                        if (before!!.getPixel(x,y) != bitmap.getPixel(x,y)) stable = false
                    }
                }
                previous?.recycle(); previous = bitmap
                if (ready && stable) { accepted = bitmap; previous = null; true } else false
            }
            val bitmap = requireNotNull(accepted)
            val wide = rule.activity.resources.configuration.screenWidthDp >= 840
            File(rule.activity.filesDir, "phase2j-${if (wide) "wide-" else ""}$name.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))
            }
        } finally { previous?.recycle(); accepted?.recycle() }
    }
    @Test fun resumeRemovesStalePreviewAndInventory() {
        waitFor("territories-dashboard")
        Phase2DBFixture(false).use { f ->
            f.prepare(); f.build(); f.page2(); show(f, AppearanceMode.LIGHT)
            scroll("workspace-surface-Map"); waitFor("workspace-pdf-page")
            f.importSource("stale")
            rule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
            rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            waitFor("workspace-preview-unavailable")
            rule.onNodeWithTag("workspace-pdf-page").assertDoesNotExist()
            scroll("workspace-tab-Addresses"); rule.onNodeWithTag("workspace-tab-Addresses").performClick()
            scroll("letter-inventory-unavailable"); rule.onNodeWithTag("letter-inventory-unavailable").assertExists()
            rule.onNodeWithTag("workspace-inventory-counts").assertDoesNotExist()
        }
    }
    @Test fun captureCurrentWorkspacePreviewInventoriesAndVerification() {
        waitFor("territories-dashboard")
        for (theme in listOf(AppearanceMode.LIGHT, AppearanceMode.DARK)) {
            val suffix = theme.name.lowercase()
            Phase2DBFixture(false).use { f ->
                f.prepare(); f.build(); f.page2(); show(f, theme)
                scroll("workspace-surface-Map"); waitFor("workspace-pdf-page")
                scroll("workspace-preview-kind"); waitFor("workspace-pdf-page")
                rule.onNodeWithTag("workspace-preview-kind").assertTextEquals("Current candidate • not approved for field use").assertIsDisplayed()
                rule.onNodeWithTag("workspace-pdf-page").assertIsDisplayed()
                shot("candidate-$suffix")
                scroll("workspace-open-preview"); rule.onNodeWithTag("workspace-open-preview").performClick()
                waitFor("preview-page"); rule.onNodeWithTag("preview-next").performClick(); waitFor("preview-page")
                rule.onNodeWithTag("preview-page-number").assertTextEquals("2 / 2")
                rule.onNodeWithTag("preview-back").performClick(); waitFor("territory-workspace")
                scroll("workspace-tab-Addresses"); rule.onNodeWithTag("workspace-tab-Addresses").performClick()
                scroll("workspace-inventory-record-address-1"); shot("addresses-$suffix")
                rule.onNodeWithText("100 Example Way").assertExists()
                scroll("workspace-verification"); rule.onNodeWithTag("workspace-verification").performClick(); waitFor("verification-screen")
                rule.onNodeWithText("Prepared inputs ready for Build").assertExists(); shot("verification-$suffix")
            }
            Phase2DBFixture(true).use { f ->
                f.prepare(); show(f, theme)
                scroll("workspace-tab-Phone-List"); rule.onNodeWithTag("workspace-tab-Phone-List").performClick()
                scroll("workspace-inventory-record-phone-2"); rule.onNodeWithText("UNAVAILABLE").assertExists(); shot("telephone-$suffix")
            }
            Phase2GExportFixture().use { f ->
                f.attach()
                val item = TerritoryDashboardModel.from(f.kb).items.first { it.assignment.displayId == f.id }
                val preview = AndroidPdfPreviewService(f.source.coordinator, File(f.source.root,"approved-preview"), f.artifacts)
                rule.runOnIdle { rule.activity.setContent { TerritoryCardStudioTheme(theme) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ModeAwareTerritoryWorkspace(Modifier.fillMaxSize().safeDrawingPadding(), item, f.kb.revision, {}, f.kb, f.source.coordinator, preview)
                } } } }
                waitFor("territory-workspace"); scroll("workspace-surface-Map"); waitFor("workspace-pdf-page")
                scroll("workspace-preview-kind"); waitFor("workspace-pdf-page")
                rule.onNodeWithTag("workspace-preview-kind").assertTextEquals("Exact approved reference").assertIsDisplayed()
                rule.onNodeWithTag("workspace-pdf-page").assertIsDisplayed()
                shot("approved-$suffix")
                scroll("workspace-open-preview"); rule.onNodeWithTag("workspace-open-preview").performClick()
                waitFor("preview-page"); rule.onNodeWithText("Original approved document • page 1").assertExists()
                rule.onNodeWithTag("preview-back").performClick(); waitFor("territory-workspace")
            }
        }
    }
}

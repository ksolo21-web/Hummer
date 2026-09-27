package com.koenterprises.territorycardstudio

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class Phase2GExportUiInstrumentationTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private fun content(id: String, service: AndroidApprovedExportService, appearance: AppearanceMode) {
        composeRule.activity.runOnUiThread { composeRule.activity.setContent {
            TerritoryCardStudioTheme(appearance) { ApprovedExportScreen(Modifier.fillMaxSize().safeDrawingPadding(), id, service) {} }
        } }
        ready()
    }
    private fun ready() {
        composeRule.waitUntil(20000) {
            composeRule.onAllNodesWithTag("approved-export-screen").fetchSemanticsNodes().firstOrNull()
                ?.config?.getOrElse(SemanticsProperties.StateDescription) { "" } == "ready"
        }
        composeRule.waitForIdle()
    }
    private fun show(tag: String) { composeRule.onNodeWithTag("export-list").performScrollToNode(hasTestTag(tag)) }
    private fun click(tag: String) { show(tag); composeRule.onNodeWithTag(tag).performClick() }
    private fun node(test: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        fun visit(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (n == null) return null
            if (test(n)) return n
            for (i in 0 until n.childCount) { val found = visit(n.getChild(i)); if (found != null) return found }
            return null
        }
        return visit(inst.uiAutomation.rootInActiveWindow)
    }
    private fun awaitNode(test: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            node(test)?.let { return it }
            SystemClock.sleep(100)
        }
        error("Expected system document control was not found")
    }
    private fun picker() {
        awaitNode { it.packageName?.toString()?.contains("documentsui") == true }
        assertNotNull(awaitNode { it.className?.toString() == "android.widget.EditText" && it.text?.contains("999a") == true })
    }
    private fun save() {
        val control = awaitNode { it.isClickable && it.isEnabled && it.text?.toString()?.equals("Save", true) == true }
        assertTrue(control.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        ready()
    }
    private fun snapshot(name: String, external: Boolean = false) {
        if (!external) { composeRule.waitForIdle(); composeRule.onNodeWithTag("approved-export-screen").captureToImage() }
        inst.waitForIdleSync()
        val bitmap = requireNotNull(inst.uiAutomation.takeScreenshot())
        File(inst.targetContext.filesDir, name).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
    private fun shell(command: String): ByteArray {
        // Send script through stdin: executeShellCommand does not parse shell quoting/operators.
        val pipes = inst.uiAutomation.executeShellCommandRw("sh")
        ParcelFileDescriptor.AutoCloseOutputStream(pipes[1]).use { it.write((command + "\n").toByteArray()) }
        return ParcelFileDescriptor.AutoCloseInputStream(pipes[0]).use { it.readBytes() }
    }

    @Test fun realWorkspaceNavigatesToGuardedExportWithoutCommissioning() {
        composeRule.onNodeWithTag("territories-dashboard").performScrollToNode(hasTestTag("territory-row-1"))
        composeRule.onNodeWithTag("territory-row-1").performClick()
        composeRule.onNodeWithTag("territory-workspace").performScrollToNode(hasTestTag("workspace-export"))
        composeRule.onNodeWithTag("workspace-export").performClick(); ready()
        show("export-save"); composeRule.onNodeWithTag("export-save").assertIsNotEnabled()
        composeRule.onNodeWithText("Existing approved card only").assertExists()
        click("export-back")
        composeRule.onNodeWithTag("territory-workspace").assertExists()
    }

    @Test fun restoredScreenRejectsPendingPickerTicketWithoutWriting() {
        Phase2GExportFixture().use { f ->
            f.attach()
            composeRule.activity.runOnUiThread { composeRule.activity.findViewById<android.view.ViewGroup>(android.R.id.content).removeAllViews() }
            val restoration = StateRestorationTester(composeRule)
            restoration.setContent {
                TerritoryCardStudioTheme(AppearanceMode.DARK) {
                    ApprovedExportScreen(Modifier.fillMaxSize().safeDrawingPadding(), f.id, f.service) {}
                }
            }
            ready(); click("export-save"); picker()
            restoration.emulateSavedInstanceStateRestore()
            save(); show("export-error")
            composeRule.onNodeWithTag("export-error").assertTextContains("Export session expired", substring = true)
            composeRule.onNodeWithTag("export-success").assertDoesNotExist()
            assertNotNull(f.service.state(f.id).ticket)
        }
    }

    @Test fun actualSystemPickerSaveCancelAttachAndStaleReturnWithCaptures() {
        Phase2GExportFixture().use { f ->
            // Only this synthetic test's own export is removed from the isolated emulator.
            shell("rm -f '/sdcard/Download/Territory - 999a.pdf'")
            content(f.id, f.service, AppearanceMode.DARK)
            show("export-save"); composeRule.onNodeWithTag("export-save").assertIsNotEnabled()
            snapshot("phase2g-missing-dark.png")
            f.attach(); click("export-refresh"); ready()
            show("export-save"); composeRule.onNodeWithTag("export-save").assertIsEnabled()
            snapshot("phase2g-ready-dark.png")
            click("export-save"); picker(); snapshot("phase2g-system-picker.png", true)
            repeat(3) {
                if (node { it.packageName?.toString()?.contains("documentsui") == true } != null) {
                    assertTrue(inst.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
                    SystemClock.sleep(400)
                }
            }
            ready(); show("export-message")
            composeRule.onNodeWithTag("export-message").assertTextContains("Save cancelled", substring = true)
            snapshot("phase2g-cancelled-dark.png")
            assertTrue(shell("test ! -e '/sdcard/Download/Territory - 999a.pdf' && echo absent").toString(Charsets.UTF_8).contains("absent"))
            click("export-save"); picker(); save(); show("export-success")
            composeRule.onNodeWithText("Saved and verified").assertIsDisplayed()
            snapshot("phase2g-saved-dark.png")
            val exported = shell("cat '/sdcard/Download/Territory - 999a.pdf'")
            assertArrayEquals(f.bytes, exported)
            File(inst.targetContext.filesDir, "phase2g-exact-export.pdf").writeBytes(exported)

            // Exercise the real OpenDocument route using the synthetic PDF just exported.
            assertTrue(f.artifacts.resolveExactApproved(f.id)!!.file.delete())
            click("export-refresh"); ready(); click("export-attach")
            awaitNode { it.packageName?.toString()?.contains("documentsui") == true }
            node { it.contentDescription?.toString() == "Show roots" }?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            val downloads = awaitNode { it.text?.toString() == "Downloads" }
            var clickable = downloads
            while (!clickable.isClickable && clickable.parent != null) clickable = clickable.parent
            clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            val document = awaitNode { it.text?.toString() == "Territory - 999a.pdf" }
            var entry = document
            while (!entry.isClickable && entry.parent != null) entry = entry.parent
            assertTrue(entry.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            ready(); show("export-message")
            composeRule.onNodeWithTag("export-message").assertTextContains("attached and verified", substring = true)
            assertEquals(f.attach().ticket, f.service.state(f.id).ticket)

            click("export-save"); picker()
            f.artifacts.resolveExactApproved(f.id)!!.file.appendText("tampered while choosing destination")
            save(); show("export-error")
            composeRule.onNodeWithTag("export-error").assertTextContains("Save failed", substring = true)
            composeRule.onNodeWithTag("export-error").assertTextContains("new destination was removed", substring = true)
            snapshot("phase2g-stale-blocked-dark.png")
            composeRule.onNodeWithTag("export-success").assertDoesNotExist()

            val reserved = f.source.original.assignments.values.first { it.needsNewCard }
            content(reserved.displayId, f.source.app.services.approvedExport, AppearanceMode.LIGHT)
            show("export-save"); composeRule.onNodeWithTag("export-save").assertIsNotEnabled()
            composeRule.onNodeWithTag("export-attach").assertDoesNotExist()
            snapshot("phase2g-reserved-blocked-light.png")
            f.attach(); content(f.id, f.service, AppearanceMode.LIGHT)
            show("export-save"); snapshot("phase2g-ready-light.png")
            click("export-save"); picker(); save(); show("export-success")
            snapshot("phase2g-saved-light.png")
        }
    }
}

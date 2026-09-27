package com.koenterprises.territorycardstudio

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.accessibilityservice.AccessibilityService
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import com.koenterprises.territorycardstudio.core.OverlapCandidate
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class Phase2KUiInstrumentationTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private fun wait(tag:String) { rule.waitUntil(20000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() };rule.waitForIdle() }
    private fun scroll(tag:String) { rule.onNodeWithTag("territory-workspace").performScrollToNode(hasTestTag(tag)) }
    private fun shot(name:String) {
        var previous:Bitmap?=null
        var accepted:Bitmap?=null
        try {
            rule.waitUntil(20000) {
                rule.waitForIdle();rule.onRoot().captureToImage()
                val current=requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
                val old=previous
                var stable=old!=null && old.width==current.width && old.height==current.height
                if(stable) for(y in 80 until current.height-48 step 8) for(x in 0 until current.width step 8)
                    if(old!!.getPixel(x,y)!=current.getPixel(x,y)) stable=false
                previous?.recycle();previous=current
                if(stable) { accepted=current;previous=null;true } else false
            }
            val b=requireNotNull(accepted)
            val wide=rule.activity.resources.configuration.screenWidthDp>=840
            File(rule.activity.filesDir,"phase2k-${if(wide)"wide-" else ""}$name.png").outputStream().use {
                assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))
            }
        } finally { previous?.recycle();accepted?.recycle() }
    }
    private fun node(test:(AccessibilityNodeInfo)->Boolean):AccessibilityNodeInfo? {
        fun visit(n:AccessibilityNodeInfo?):AccessibilityNodeInfo? {
            if(n==null)return null
            if(test(n))return n
            for(i in 0 until n.childCount) visit(n.getChild(i))?.let{return it}
            return null
        }
        return visit(InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow)
    }
    private fun awaitNode(test:(AccessibilityNodeInfo)->Boolean):AccessibilityNodeInfo {
        val deadline=SystemClock.uptimeMillis()+15000
        while(SystemClock.uptimeMillis()<deadline) {
            node(test)?.let{return it};SystemClock.sleep(100)
        }
        error("Expected system document control missing")
    }
    private fun shell(command:String):ByteArray {
        val pipes=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommandRw("sh")
        ParcelFileDescriptor.AutoCloseOutputStream(pipes[1]).use { it.write((command+"\n").toByteArray()) }
        return ParcelFileDescriptor.AutoCloseInputStream(pipes[0]).use { it.readBytes() }
    }
    @Test fun actualSystemPickerCancelsSavesAndRejectsStaleAudit() {
        Phase2DBFixture(false).use { f ->
            f.prepare(); f.build(); f.page2()
            val item=TerritoryDashboardModel.from(f.kb).items.first { it.assignment.displayId==f.identity.displayId }
            val service=Phase2KAuditService(f.kb,f.coordinator,f.sources,f.app.services.activePolicy)
            val expected=service.snapshot(item,f.mode)
            val path="/sdcard/Download/Territory - ${f.identity.displayId} - Audit.json"
            shell("rm -f '$path'")
            rule.runOnIdle { rule.activity.setContent { TerritoryCardStudioTheme(AppearanceMode.DARK) {
                Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
                    Phase2KAuditScreen(Modifier.fillMaxSize().safeDrawingPadding(),item,f.mode,service) {}
                }
            } } }
            wait("audit-save");rule.waitUntil(20000) { runCatching { rule.onNodeWithTag("audit-save").assertIsEnabled() }.isSuccess };rule.onNodeWithTag("audit-save").performClick()
            awaitNode { it.packageName?.toString()?.contains("documentsui")==true }
            assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
            wait("audit-message");rule.onNodeWithTag("audit-message").assertTextContains("cancelled",substring=true)
            assertTrue(shell("test ! -e '$path' && echo absent").toString(Charsets.UTF_8).contains("absent"))
            rule.onNodeWithTag("audit-save").performClick()
            awaitNode { it.packageName?.toString()?.contains("documentsui")==true }
            val save=awaitNode { it.isClickable && it.isEnabled && it.text?.toString()?.equals("Save",true)==true }
            assertTrue(save.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            rule.waitUntil(20000) { runCatching { rule.onNodeWithTag("audit-message").assertTextContains("saved and read back",substring=true) }.isSuccess }
            val saved=shell("cat '$path'")
            assertArrayEquals(expected.bytes,saved)
            assertEquals(f.identity.displayId,JSONObject(String(saved)).getString("territory"))
            shell("rm -f '$path'")
            rule.onNodeWithTag("audit-save").performClick()
            awaitNode { it.packageName?.toString()?.contains("documentsui")==true }
            f.importSource("changed-in-picker")
            val staleSave=awaitNode { it.isClickable && it.isEnabled && it.text?.toString()?.equals("Save",true)==true }
            assertTrue(staleSave.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            rule.waitUntil(20000) { runCatching { rule.onNodeWithTag("audit-message").assertTextContains("changed",substring=true) }.isSuccess }
            assertTrue(shell("test ! -e '$path' && echo absent").toString(Charsets.UTF_8).contains("absent"))
            shell("rm -f '$path'")
        }
    }
    @Test fun affectedRoadFindingOpensStreetsWithActualItemContext() {
        Phase2DBFixture(false).use { f ->
            val overlap=OverlapCandidate("Alpha Rd",listOf(f.identity.displayId,"273"),"review","Review the shared junction")
            val kb=f.kb.copy(crossTerritoryOverlapAudit=f.kb.crossTerritoryOverlapAudit.copy(reviewCandidates=listOf(overlap)))
            val item=TerritoryDashboardModel.from(kb).items.first { it.assignment.displayId==f.identity.displayId }
            val preview=AndroidPdfPreviewService(f.coordinator,File(f.root,"2k-finding-preview"),f.service)
            rule.runOnIdle { rule.activity.setContent { TerritoryCardStudioTheme(AppearanceMode.LIGHT) {
                Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
                    ModeAwareTerritoryWorkspace(Modifier.fillMaxSize().safeDrawingPadding(),item,kb.revision,{},kb,f.coordinator,preview)
                }
            } } }
            wait("territory-workspace")
            scroll("workspace-findings");rule.onNodeWithTag("workspace-findings").performClick()
            wait("findings-screen")
            rule.onNodeWithTag("finding-overlap-Alpha Rd").assertExists()
            rule.onNodeWithTag("finding-open-overlap").performClick()
            wait("finding-target")
            rule.onNodeWithTag("workspace-tab-Streets").assertExists()
            rule.onNodeWithText("Finding • overlap • Alpha Rd").assertExists()
            rule.onNodeWithText("Review the shared junction").assertExists()
        }
    }
    @Test fun captureFindingsAndAuditAndNavigateFromPreviewAcrossThemes() {
        wait("territories-dashboard")
        for (theme in listOf(AppearanceMode.LIGHT,AppearanceMode.DARK)) Phase2DBFixture(false).use { f ->
            f.prepare();f.build();f.page2()
            val item=TerritoryDashboardModel.from(f.kb).items.first { it.assignment.displayId==f.identity.displayId }
            val preview=AndroidPdfPreviewService(f.coordinator,File(f.root,"2k-preview"),f.service)
            rule.runOnIdle { rule.activity.setContent { TerritoryCardStudioTheme(theme) {
                Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
                    ModeAwareTerritoryWorkspace(Modifier.fillMaxSize().safeDrawingPadding(),item,f.kb.revision,{},f.kb,f.coordinator,preview)
                }
            } } }
            wait("territory-workspace")
            scroll("workspace-mode-Letter-Writing");rule.onNodeWithTag("workspace-mode-Letter-Writing").performClick()
            scroll("workspace-verification");rule.onNodeWithTag("workspace-verification").performClick()
            wait("verification-screen");rule.onNodeWithTag("verification-findings").performClick()
            wait("findings-screen");rule.onNodeWithTag("finding-field_release-category").assertExists()
            shot("findings-${theme.name.lowercase()}")
            rule.onNodeWithTag("finding-open-field_release").performClick()
            wait("finding-target");rule.onNodeWithTag("workspace-tab-Details").assertExists()
            scroll("workspace-audit");rule.onNodeWithTag("workspace-audit").performClick()
            wait("audit-screen")
            rule.waitUntil(20000) { runCatching { rule.onNodeWithTag("audit-save").assertIsEnabled() }.isSuccess }
            rule.onNodeWithText("Prepared input SHA-256 ${f.input.canonicalSha256()}").assertExists()
            rule.onNodeWithText("Inventory SHA-256 ${requireNotNull(f.coordinator.workspaceSnapshot(f.identity.displayId,f.mode)?.inventory).hash}").assertExists()
            shot("audit-${theme.name.lowercase()}")
            rule.onNodeWithTag("audit-save").assertIsEnabled()
            rule.onNodeWithText("← Back").performClick()
            wait("territory-workspace")
            scroll("workspace-tab-Map");rule.onNodeWithTag("workspace-tab-Map").performClick()
            scroll("workspace-open-preview");wait("workspace-open-preview");rule.onNodeWithTag("workspace-open-preview").performClick()
            wait("pdf-preview-screen");rule.onNodeWithTag("preview-findings").performClick()
            wait("findings-screen");rule.onNodeWithTag("finding-open-field_release").assertExists()
        }
    }
}

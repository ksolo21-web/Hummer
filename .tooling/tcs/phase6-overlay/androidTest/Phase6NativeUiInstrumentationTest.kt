package com.koenterprises.territorycardstudio

import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.koenterprises.territorycardstudio.core.BundleIntegrity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class Phase6NativeUiInstrumentationTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val automation get()=instrumentation.uiAutomation
    private val native="native-authoring-screen"
    private val root=DocumentsContract.buildDocumentUri(Phase56SyntheticDocumentsProvider.AUTHORITY,"root")
    @get:Rule val diagnostic=object:org.junit.rules.TestWatcher(){override fun failed(e:Throwable,d:org.junit.runner.Description){
        File(instrumentation.targetContext.filesDir,"phase6-debug-${d.methodName}.txt").writeText(e.stackTraceToString()+"\n"+runCatching{rule.onRoot().printToString()}.getOrDefault("No Compose tree")+"\n"+nodes().joinToString("\n"){"${it.viewIdResourceName} | ${it.text} | ${it.contentDescription}"})
        screenshot("failure-${d.methodName}")
    }}
    private fun nodes():List<AccessibilityNodeInfo> {val out=mutableListOf<AccessibilityNodeInfo>();fun walk(n:AccessibilityNodeInfo?){if(n==null)return;out+=n;for(i in 0 until n.childCount)walk(n.getChild(i))};walk(automation.rootInActiveWindow);return out}
    private fun waitTag(tag:String){rule.waitUntil(30000){rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()};rule.waitForIdle()}
    private fun click(list:String,tag:String){rule.onNodeWithTag(list).performScrollToNode(hasTestTag(tag));rule.onNodeWithTag(tag).assertIsEnabled().performClick();rule.waitForIdle()}
    private fun text(list:String,tag:String,value:String){rule.onNodeWithTag(list).performScrollToNode(hasTestTag(tag));rule.onNodeWithTag(tag).performTextReplacement(value)
        rule.activity.runOnUiThread {(rule.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(rule.activity.window.decorView.windowToken,0)};rule.waitForIdle()}
    private fun textClick(list:String,label:String){rule.onNodeWithTag(list).performScrollToNode(hasText(label));rule.onNodeWithText(label).performClick();rule.waitForIdle()}
    private fun statusContains(text:String){rule.onNodeWithTag(native).performScrollToNode(hasTestTag("native-status"));rule.waitUntil(30000){runCatching {rule.onNodeWithTag("native-status").assertTextContains(text,substring=true);true}.getOrDefault(false)}}
    private fun systemClick(find:(AccessibilityNodeInfo)->Boolean) {
        var selected:AccessibilityNodeInfo?=null
        rule.waitUntil(15000){selected=nodes().firstOrNull(find);selected!=null}
        var n=requireNotNull(selected);while(!n.isClickable && n.parent!=null)n=n.parent
        check(n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {"System picker action failed"}
        automation.waitForIdle(500,10000)
    }
    private fun chooseRoot() {
        systemClick {it.contentDescription?.toString()?.let {s->s.contains("Show roots",true)||s.contains("navigation drawer",true)}==true || it.viewIdResourceName=="android:id/home"}
        systemClick {it.text?.toString()=="Synthetic test files"}
    }
    private fun chooseInput(name:String) {
        rule.waitUntil(15000){automation.rootInActiveWindow?.packageName?.toString()?.contains("documentsui")==true}
        chooseRoot();systemClick {it.text?.toString()==name};waitTag("territory-workspace")
    }
    private fun chooseContact(name:String) {
        rule.waitUntil(15000){automation.rootInActiveWindow?.packageName?.toString()?.contains("documentsui")==true}
        chooseRoot();systemClick {it.text?.toString()==name};waitTag(native)
    }
    private fun savePicker() {
        rule.waitUntil(15000){automation.rootInActiveWindow?.packageName?.toString()?.contains("documentsui")==true}
        chooseRoot();systemClick {it.text?.toString()?.equals("Save",true)==true && it.isEnabled}
        waitTag("final-output-screen")
        rule.onNodeWithTag("final-output-list").performScrollToNode(hasTestTag("final-output-message"))
        rule.waitUntil(30000){runCatching {rule.onNodeWithTag("final-output-message").assertTextContains("saved and read back successfully",substring=true);true}.getOrDefault(false)}
    }
    private fun screenshot(name:String) {
        runCatching {rule.waitForIdle();automation.waitForIdle(500,10000)}
        automation.takeScreenshot()?.let {b->val wide=rule.activity.resources.configuration.screenWidthDp>=840
            File(instrumentation.targetContext.filesDir,"phase6-${if(wide)"wide-" else ""}$name.png").outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
    }
    private fun grant() {
        instrumentation.context.startActivity(android.content.Intent().setClassName(instrumentation.context.packageName,Phase56GrantActivity::class.java.name).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        rule.waitUntil(15000){runCatching {instrumentation.targetContext.contentResolver.query(root,null,null,null,null)?.use {it.moveToFirst()}==true}.getOrDefault(false)}
    }
    private fun document(name:String,mime:String,bytes:ByteArray):Uri {
        val resolver=instrumentation.targetContext.contentResolver;val uri=requireNotNull(DocumentsContract.createDocument(resolver,root,mime,name))
        resolver.openOutputStream(uri,"wt")!!.use {it.write(bytes)};return uri
    }
    @Test fun fullRegularLight()=flow(WorkspaceMode.REGULAR,AppearanceMode.LIGHT)
    @Test fun fullLetterDark()=flow(WorkspaceMode.LETTER_WRITING,AppearanceMode.DARK)
    @Test fun fullTelephoneLight()=flow(WorkspaceMode.TELEPHONE,AppearanceMode.LIGHT)
    @Test fun fullRegularDark()=flow(WorkspaceMode.REGULAR,AppearanceMode.DARK)
    @Test fun fullLetterLight()=flow(WorkspaceMode.LETTER_WRITING,AppearanceMode.LIGHT)
    @Test fun fullTelephoneDark()=flow(WorkspaceMode.TELEPHONE,AppearanceMode.DARK)

    private fun flow(mode:WorkspaceMode,theme:AppearanceMode) {Phase6NativeFixture(mode).use {x->
        grant()
        val sourceName="phase6-${mode.name}-${theme.name}-current.pdf";val contactsName="phase6-${mode.name}-${theme.name}-records.txt"
        document(sourceName,"application/pdf",x.sourcePdf())
        document(contactsName,"text/plain","SYNTHETIC USER PROVIDED RECORDS - NOT FOR FIELD USE\n100 Example Way | 2025550101\n101 Example Way | UNAVAILABLE\n".toByteArray())
        val prefix="${mode.name.lowercase()}-${theme.name.lowercase()}"
        x.app.appearancePreferences.setMode(theme)
        rule.activity.runOnUiThread {rule.activity.enableEdgeToEdge()}
        rule.setContent {TerritoryCardStudioProductionApp(x.kb,x.app.appearancePreferences,x.services)}
        waitTag("territories-dashboard");text("territories-dashboard","territory-search",x.id);click("territories-dashboard","territory-row-${x.id}")
        waitTag("territory-workspace")
        if(mode==WorkspaceMode.LETTER_WRITING)click("territory-workspace","workspace-mode-Letter-Writing")
        assertFalse(x.coordinator.state(x.id,mode).inputReady)
        click("territory-workspace","workspace-import-map");click("import-map-screen","choose-source-map")
        // Actual Android OpenDocument selection, not a direct intake call.
        rule.waitUntil(15000){automation.rootInActiveWindow?.packageName?.toString()?.contains("documentsui")==true}
        chooseRoot();systemClick {it.text?.toString()==sourceName};waitTag("import-map-screen")
        textClick("import-map-screen","← Back to workspace")
        click("territory-workspace","workspace-prepare-new");waitTag(native)
        text(native,"native-locality","Oakland Township");text(native,"native-updated","9/28/2026")
        text(native,"native-directions","Directions: Synthetic source only.\nNOT FOR FIELD USE.")
        text(native,"native-author","Synthetic author");text(native,"native-county","Oakland County");text(native,"native-state","Michigan");text(native,"native-country","United States")
        click(native,"native-tab-Roads")
        listOf(Triple("alpha","Alpha Rd",115),Triple("beta","Beta Dr",200),Triple("gamma","Gamma Ct",285)).forEachIndexed {i,(id,name,y)->
            text(native,"native-points","225,$y;650,$y");textClick(native,"Apply points")
            text(native,"native-item-id",id);text(native,"native-road-name",name)
            click(native,"native-road-status-"+listOf("yellow","green","red")[i]);click(native,"native-road-role-"+listOf("perimeter","interior","excluded")[i])
            if(i==0)click(native,"native-road-side-left")
            click(native,"native-end-a-"+if(i==0)"junction" else "termination");click(native,"native-end-b-"+if(i==0)"junction" else "termination")
            text(native,"native-evidence-note","Page 1, $name: explicit work instruction and endpoints")
            click(native,"native-item-confirmed");click(native,"native-save-item");statusContains("Draft saved")
        }
        assertFalse(x.coordinator.state(x.id,mode).inputReady)
        if(mode!=WorkspaceMode.REGULAR) {
            click(native,"native-tab-Addresses");click(native,"native-import-contacts");chooseContact(contactsName)
            val count=if(mode==WorkspaceMode.TELEPHONE)2 else 1
            repeat(count) {i->text(native,"native-contact-id","record-$i");text(native,"native-address","${100+i} Example Way")
                click(native,"native-address-confirmed");click(native,"native-boundary-confirmed")
                if(i==0)click(native,"native-address-authorized")
                if(mode==WorkspaceMode.TELEPHONE) {
                    click(native,"native-phone-state-"+if(i==0)"VERIFIED_NUMBER" else "UNAVAILABLE")
                    if(i==0) {text(native,"native-phone","2025550101");click(native,"native-phone-authorized");click(native,"native-phone-binding")}
                }
                click(native,"native-save-contact");statusContains("Draft saved")
            }
        }
        click(native,"native-tab-Review");click(native,"native-source-complete");click(native,"native-save-draft");statusContains("Draft saved")
        screenshot("$prefix-reconciliation")
        click(native,"native-register");statusContains("Local assignment registered")
        val registered=requireNotNull(x.drafts.ledger.active(x.id,mode.name));assertFalse(registered.assignment.authoritySha256 in x.slot.sourceHashes)
        assertFalse(x.coordinator.state(x.id,mode).inputReady)
        click(native,"native-prepare");statusContains("Prepared using fresh independent sources")
        assertTrue(x.coordinator.state(x.id,mode).inputReady)
        textClick(native,"Back to workspace");click("territory-workspace","workspace-build")
        click("build-screen","build-front");rule.waitUntil(30000){x.coordinator.state(x.id,mode).front!=null}
        if(mode!=WorkspaceMode.REGULAR){click("build-screen","generate-page2");rule.waitUntil(30000){x.coordinator.state(x.id,mode).packet!=null}}
        click("build-screen",if(mode==WorkspaceMode.REGULAR)"preview-front" else "preview-packet");waitTag("preview-page");screenshot("$prefix-pdf-page1")
        if(mode!=WorkspaceMode.REGULAR){rule.onNodeWithTag("preview-next").performClick();rule.onNodeWithTag("preview-page-number").assertTextEquals("2 / 2");waitTag("preview-page");screenshot("$prefix-pdf-page2")}
        rule.onNodeWithTag("preview-back").performClick();click("build-screen","review-candidate");waitTag("lifecycle-list")
        listOf("lifecycle-pages","lifecycle-boundaries","lifecycle-data").forEach {click("lifecycle-list",it)}
        text("lifecycle-list","lifecycle-actor","Synthetic PDF reviewer");click("lifecycle-list","lifecycle-approve");waitTag("lifecycle-confirmation");rule.onNodeWithTag("lifecycle-confirm").performClick()
        rule.waitUntil(30000){x.lifecycle.state(x.id,mode).active}
        click("lifecycle-list","lifecycle-back");textClick("build-screen","← Back to workspace");click("territory-workspace","workspace-export")
        click("final-output-list","final-output-validate");waitTag("final-output-ready");screenshot("$prefix-final-validation")
        click("final-output-list","final-output-pdf");savePicker();screenshot("$prefix-saved-pdf")
        // SAF pause/resume may invalidate a UI ticket; always request a fresh final check for audit.
        click("final-output-list","final-output-validate");waitTag("final-output-ready");click("final-output-list","final-output-audit");savePicker()
        val resolver=instrumentation.targetContext.contentResolver
        val child=DocumentsContract.buildChildDocumentsUri(Phase56SyntheticDocumentsProvider.AUTHORITY,"root")
        val files=mutableMapOf<String,ByteArray>()
        resolver.query(child,arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME),null,null,null)!!.use {c->while(c.moveToNext()){val name=c.getString(1);if(name==x.identity.canonicalFilename || name==x.identity.canonicalFilename.removeSuffix(".pdf")+" - audit.json"){
            val uri=DocumentsContract.buildDocumentUri(Phase56SyntheticDocumentsProvider.AUTHORITY,c.getString(0));files[name]=resolver.openInputStream(uri)!!.use {it.readBytes()};DocumentsContract.deleteDocument(resolver,uri)
        }}}
        val pdf=requireNotNull(files[x.identity.canonicalFilename]);assertArrayEquals(x.lifecycle.readCurrentPdf(x.id,mode),pdf);assertTrue(pdf.size<300000)
        val audit=requireNotNull(files[x.identity.canonicalFilename.removeSuffix(".pdf")+" - audit.json"]);val json=JSONObject(audit.toString(Charsets.UTF_8))
        assertEquals(BundleIntegrity.sha256(pdf.inputStream()),json.getString("pdfSha256"));assertEquals("explicit_local_user_reconciliation",json.getString("assignmentAuthorization"))
        assertEquals(registered.reconciliation.registrationId,json.getJSONObject("nativeReconciliation").getString("registrationId"))
        File(x.app.filesDir,"phase6-$prefix.pdf").writeBytes(pdf);File(x.app.filesDir,"phase6-$prefix-audit.json").writeBytes(audit)
        rule.onNodeWithTag("nav-knowledge").performClick();waitTag("knowledge-selected");rule.onNodeWithTag("knowledge-selected").performClick()
        rule.onNodeWithTag("knowledge-detail-list").performScrollToNode(hasTestTag("knowledge-local-cards"));rule.onNodeWithText("Current approved local reference",substring=true).assertExists();screenshot("$prefix-knowledge")
        x.original.knowledgeBase.needsNewCardQueue.keys.forEach {assertEquals(x.original.knowledgeBase.assignments[it],x.kb.assignments[it])}
        assertEquals(2,File(x.root,"output").listFiles().orEmpty().count {it.extension=="json"})
    }}
}

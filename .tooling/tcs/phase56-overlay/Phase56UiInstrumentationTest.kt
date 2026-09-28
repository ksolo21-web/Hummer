package com.koenterprises.territorycardstudio

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class) class Phase56UiInstrumentationTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    @get:Rule val diagnostic=object:org.junit.rules.TestWatcher(){override fun failed(e:Throwable,d:org.junit.runner.Description){
        val dir=InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        File(dir,"phase56-debug-${d.methodName}.txt").writeText(e.stackTraceToString()+"\n"+runCatching{rule.onRoot().printToString()}.getOrDefault("No tree"))
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let{b->File(dir,"phase56-debug-${d.methodName}.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
    }}
    private fun waitTag(tag:String){rule.waitUntil(30000){rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()};rule.waitForIdle()}
    private fun click(list:String,tag:String){waitTag(tag);rule.onNodeWithTag(list).performScrollToNode(hasTestTag(tag));rule.onNodeWithTag(tag).assertIsEnabled().performClick();rule.waitForIdle()}
    private fun shot(name:String){val auto=InstrumentationRegistry.getInstrumentation().uiAutomation;rule.waitForIdle();auto.waitForIdle(500,10000)
        var prior:Bitmap?=null;var chosen:Bitmap?=null;var since=0L
        try{rule.waitUntil(20000){val b=requireNotNull(auto.takeScreenshot());val now=android.os.SystemClock.elapsedRealtime()
            var same=prior!=null && prior!!.width==b.width && prior!!.height==b.height && auto.rootInActiveWindow?.packageName?.toString()==rule.activity.packageName
            if(same)for(y in 0 until b.height step 4)for(x in 0 until b.width step 8)if(prior!!.getPixel(x,y)!=b.getPixel(x,y))same=false
            if(!same)since=0L else if(since==0L)since=now
            prior?.recycle();prior=b;if(same && now-since>=1000){chosen=b;prior=null;true}else false}
            val b=requireNotNull(chosen);val wide=rule.activity.resources.configuration.screenWidthDp>=840
            File(rule.activity.filesDir,"phase56-${if(wide)"wide-" else ""}$name.png").outputStream().use{assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))}
        }finally{prior?.recycle();chosen?.recycle()}}
    @Test fun outputLight()=flow(AppearanceMode.LIGHT)
    @Test fun outputDark()=flow(AppearanceMode.DARK)
    private fun flow(theme:AppearanceMode){Phase56Fixture(WorkspaceMode.LETTER_WRITING).use{x->
        var route by mutableStateOf("EXPORT")
        rule.activity.runOnUiThread{rule.activity.enableEdgeToEdge()}
        rule.setContent{TerritoryCardStudioTheme(theme){Surface(Modifier.fillMaxSize()){
            if(route=="IMPORT")VerifiedProjectScreen(Modifier.fillMaxSize().safeDrawingPadding(),x.id,x.mode,x.intake){route="EXPORT"}
            else key(route){FinalOutputScreen(Modifier.fillMaxSize().safeDrawingPadding(),x.id,x.mode,x.output){route="IMPORT"}}
        }}}
        waitTag("final-output-status");rule.waitUntil(30000){rule.onNodeWithTag("final-output-status").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString().contains("Build")}
        rule.onNodeWithTag("final-output-validate").assertIsNotEnabled();shot("blocked-${theme.name.lowercase()}")
        rule.runOnIdle{route="IMPORT"};waitTag("verified-project-import");shot("intake-${theme.name.lowercase()}")
        x.ready();rule.runOnIdle{route="READY"};waitTag("final-output-validate")
        rule.waitUntil(30000){runCatching{rule.onNodeWithTag("final-output-validate").assertIsEnabled();true}.getOrDefault(false)}
        click("final-output-list","final-output-validate");waitTag("final-output-ready")
        rule.onNodeWithTag("final-output-list").performScrollToNode(hasTestTag("final-output-ready"));shot("ready-${theme.name.lowercase()}")
        click("final-output-list","final-output-pdf")
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        rule.waitUntil(15000){automation.rootInActiveWindow?.packageName?.toString()!=rule.activity.packageName}
        // Exercise a real SAF cancel result; no synthetic output is shared or field released.
        automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        waitTag("final-output-message");rule.onNodeWithTag("final-output-message").assertTextContains("cancelled",substring=true)
        rule.onNodeWithTag("final-output-list").performScrollToNode(hasTestTag("final-output-message"));shot("cancelled-${theme.name.lowercase()}")
    }}
}

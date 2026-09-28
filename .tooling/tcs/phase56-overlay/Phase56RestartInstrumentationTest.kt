package com.koenterprises.territorycardstudio

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.koenterprises.territorycardstudio.core.BundleIntegrity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class) class Phase56RestartInstrumentationTest {
    private fun root()=File(ApplicationProvider.getApplicationContext<TerritoryCardStudioApplication>().filesDir,"phase56-restart")
    private fun hashes(root:File)=root.walkTopDown().filter{it.isFile}.associate{it.relativeTo(root).path to BundleIntegrity.sha256(it.inputStream())}.toSortedMap()
    @Test fun stageBeforeProcessDeath(){val root=root();root.deleteRecursively();assertTrue(root.mkdirs());val state=JSONObject().put("producerPid",android.os.Process.myPid())
        for(mode in WorkspaceMode.entries)Phase56Fixture(mode).use{x->x.ready();x.output.validate(x.id,mode)
            val dir=File(root,mode.name).apply{mkdirs()};val approval=x.lifecycle.state(x.id,mode)
            assertTrue(x.f.root.copyRecursively(File(dir,"runtime")))
            val sourceDir=File(x.f.app.noBackupFilesDir,"territory-card-studio/source-intake-v1/${x.id}")
            assertTrue(sourceDir.copyRecursively(File(dir,"source")))
            state.put(mode.name,JSONObject().put("sourceRaw",x.f.app.getSharedPreferences("territory-card-studio-source-intake-v1",0).getString("source_map_${x.id}",null))
                .put("runtime",JSONObject(hashes(x.f.root))).put("sourceFiles",JSONObject(hashes(sourceDir)))
                .put("manifest",approval.promoted!!.ticket.manifest.canonicalSha256()).put("history",approval.history.size))
        };File(root,"state.json").writeText(state.toString())
    }
    @Test fun recoverSavedProjectRebuildReapproveAndExportInNewProcess(){val root=root();val state=JSONObject(File(root,"state.json").readText());val pid=android.os.Process.myPid()
        assertNotEquals(state.getInt("producerPid"),pid)
        for(mode in WorkspaceMode.entries)Phase56Fixture(mode).use{x->val expected=state.getJSONObject(mode.name);val dir=File(root,mode.name)
            x.f.root.deleteRecursively();assertTrue(File(dir,"runtime").copyRecursively(x.f.root))
            assertEquals(expected.getJSONObject("runtime").let{j->j.keys().asSequence().associateWith{j.getString(it)}},hashes(x.f.root))
            val sourceDir=File(x.f.app.noBackupFilesDir,"territory-card-studio/source-intake-v1/${x.id}")
            sourceDir.deleteRecursively();assertTrue(File(dir,"source").copyRecursively(sourceDir))
            assertEquals(expected.getJSONObject("sourceFiles").let{j->j.keys().asSequence().associateWith{j.getString(it)}},hashes(sourceDir))
            assertTrue(x.f.app.getSharedPreferences("territory-card-studio-source-intake-v1",0).edit().putString("source_map_${x.id}",expected.getString("sourceRaw")).commit())
            val suspended=x.lifecycle.state(x.id,mode);assertFalse(suspended.active);assertEquals(expected.getInt("history"),suspended.history.size)
            assertEquals(expected.getString("manifest"),suspended.promoted!!.ticket.manifest.canonicalSha256())
            assertTrue(runCatching{x.output.validate(x.id,mode)}.isFailure)
            x.intake.prepare(x.intake.revalidateSaved(x.id,mode));x.build();assertFalse(x.lifecycle.state(x.id,mode).active);x.approve()
            val t=x.output.validate(x.id,mode);assertNotEquals(expected.getString("manifest"),t.review.manifest.canonicalSha256())
            val d=Phase56Destination();x.output.exportCreated(t,d,false);assertEquals(t.review.manifest.packetPdfSha256,BundleIntegrity.sha256(d.bytes.inputStream()))
        }
        File(root.parentFile,"phase56-restart-proof.json").writeText(JSONObject().put("producerPid",state.getInt("producerPid")).put("consumerPid",pid).put("modes",3).put("passed",true).toString())
        root.deleteRecursively()
    }
}

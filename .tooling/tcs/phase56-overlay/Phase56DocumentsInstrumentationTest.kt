package com.koenterprises.territorycardstudio

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class) class Phase56DocumentsInstrumentationTest {
    @Test fun actualDocumentProviderReadbackPreservesAllModesAndPairedAudit(){
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.context.startActivity(android.content.Intent().setClassName(instrumentation.context.packageName,Phase56GrantActivity::class.java.name).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        val resolver=instrumentation.targetContext.contentResolver
        val root=DocumentsContract.buildDocumentUri(Phase56SyntheticDocumentsProvider.AUTHORITY,"root")
        val deadline=android.os.SystemClock.elapsedRealtime()+10000
        while(runCatching{resolver.query(root,null,null,null,null)?.use{it.moveToFirst()}==true}.getOrDefault(false).not()) {
            check(android.os.SystemClock.elapsedRealtime()<deadline){"Synthetic provider grant unavailable"};android.os.SystemClock.sleep(100)
        }
        for(mode in WorkspaceMode.entries)Phase56Fixture(mode).use{x->
            // Incoming bytes cross the Android document provider boundary before validation.
            val input=requireNotNull(DocumentsContract.createDocument(resolver,root,"application/json","synthetic-project.json"))
            resolver.openOutputStream(input,"wt")!!.use{it.write(x.project)}
            val project=resolver.openInputStream(input)!!.use{it.readBytes()};x.intake.prepare(x.intake.validate(x.id,mode,project));x.build();x.approve()
            val t=x.output.validate(x.id,mode)
            for(audit in listOf(false,true)){
                val name=t.review.manifest.canonicalFilename.let{if(audit)it.removeSuffix(".pdf")+" - audit.json" else it}
                val uri=requireNotNull(DocumentsContract.createDocument(resolver,root,if(audit)"application/json" else "application/pdf",name))
                val receipt=x.output.exportCreated(t,AndroidCreatedExportDestination(resolver,uri),audit)
                val bytes=resolver.openInputStream(uri)!!.use{it.readBytes()};assertEquals(receipt.sha256,com.koenterprises.territorycardstudio.core.BundleIntegrity.sha256(bytes.inputStream()))
                File(x.f.app.filesDir,"phase56-provider-${mode.name.lowercase()}${if(audit)"-audit.json" else ".pdf"}").writeBytes(bytes)
                assertTrue(DocumentsContract.deleteDocument(resolver,uri))
            }
            assertTrue(DocumentsContract.deleteDocument(resolver,input))
        }
    }
}

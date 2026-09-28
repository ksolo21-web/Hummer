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

class Phase56GrantActivity:android.app.Activity() {
    override fun onCreate(saved:android.os.Bundle?) {super.onCreate(saved)
        grantUriPermission("com.koenterprises.territorycardstudio",DocumentsContract.buildDocumentUri(Phase56SyntheticDocumentsProvider.AUTHORITY,"root"),
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        finish()
    }
}
/** Installed only in the instrumentation APK. Contains synthetic fixture files, never user documents. */
class Phase56SyntheticDocumentsProvider:DocumentsProvider() {
    companion object{const val AUTHORITY="com.koenterprises.territorycardstudio.test.synthetic.documents"}
    private val root get()=File(requireNotNull(context).filesDir,"synthetic-documents").apply{mkdirs()}
    override fun onCreate()=true
    private fun file(id:String):File {require(id.matches(Regex("[a-f0-9-]{36}")));return File(root,id)}
    override fun queryRoots(projection:Array<out String>?):Cursor {
        val cols=projection ?: arrayOf(DocumentsContract.Root.COLUMN_ROOT_ID,DocumentsContract.Root.COLUMN_DOCUMENT_ID,DocumentsContract.Root.COLUMN_TITLE,DocumentsContract.Root.COLUMN_FLAGS,DocumentsContract.Root.COLUMN_MIME_TYPES)
        val values=mapOf<String,Any>(DocumentsContract.Root.COLUMN_ROOT_ID to "synthetic",DocumentsContract.Root.COLUMN_DOCUMENT_ID to "root",DocumentsContract.Root.COLUMN_TITLE to "Synthetic test files",DocumentsContract.Root.COLUMN_FLAGS to DocumentsContract.Root.FLAG_SUPPORTS_CREATE,DocumentsContract.Root.COLUMN_MIME_TYPES to "*/*")
        return MatrixCursor(cols).apply{addRow(cols.map{values[it]}.toTypedArray())}
    }
    private fun row(id:String,projection:Array<out String>?):MatrixCursor {
        val cols=projection ?: arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_FLAGS,DocumentsContract.Document.COLUMN_SIZE)
        val isRoot=id=="root";val f=if(isRoot)root else file(id)
        val name=if(isRoot)"Synthetic test files" else File(root,"$id.name").readText()
        val mime=if(isRoot)DocumentsContract.Document.MIME_TYPE_DIR else if(name.endsWith(".pdf"))"application/pdf" else "application/json"
        val flags=if(isRoot)DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE else DocumentsContract.Document.FLAG_SUPPORTS_WRITE or DocumentsContract.Document.FLAG_SUPPORTS_DELETE
        val values=mapOf<String,Any>(DocumentsContract.Document.COLUMN_DOCUMENT_ID to id,DocumentsContract.Document.COLUMN_DISPLAY_NAME to name,DocumentsContract.Document.COLUMN_MIME_TYPE to mime,DocumentsContract.Document.COLUMN_FLAGS to flags,DocumentsContract.Document.COLUMN_SIZE to if(isRoot)0L else f.length())
        return MatrixCursor(cols).apply{addRow(cols.map{values[it]}.toTypedArray())}
    }
    override fun queryDocument(documentId:String,projection:Array<out String>?)=row(documentId,projection)
    override fun queryChildDocuments(parentDocumentId:String,projection:Array<out String>?,sortOrder:String?):Cursor {
        require(parentDocumentId=="root");val result=row("root",projection);val columns=result.columnNames
        val out=MatrixCursor(columns)
        root.listFiles().orEmpty().filter{it.name.matches(Regex("[a-f0-9-]{36}"))}.forEach{f->row(f.name,projection).use{c->c.moveToFirst();out.addRow(columns.indices.map{if(c.getType(it)==Cursor.FIELD_TYPE_INTEGER)c.getLong(it) else c.getString(it)}.toTypedArray())}}
        result.close();return out
    }
    override fun createDocument(parentDocumentId:String,mimeType:String,displayName:String):String {
        require(parentDocumentId=="root" && displayName.length<=200 && '/' !in displayName)
        val id=UUID.randomUUID().toString();file(id).writeBytes(ByteArray(0));File(root,"$id.name").writeText(displayName);return id
    }
    override fun openDocument(documentId:String,mode:String,signal:CancellationSignal?)=ParcelFileDescriptor.open(file(documentId),ParcelFileDescriptor.parseMode(mode))
    override fun deleteDocument(documentId:String){check(file(documentId).delete());File(root,"$documentId.name").delete()}
}
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

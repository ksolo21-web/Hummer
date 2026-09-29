package com.koenterprises.territorycardstudio

import android.content.Context
import android.net.Uri
import com.koenterprises.territorycardstudio.core.BundleIntegrity
import java.io.File

/** Original bytes are retained separately; this never modifies the current map intake. */
class SupplementalBuildingReferenceStore(private val context:Context) {
    private val root=File(context.filesDir,"supplemental-building-references")
    fun verifiedFile(expected:String):File {
        require(expected.matches(Regex("[0-9a-f]{64}")))
        val file=File(root,"$expected.reference")
        require(file.isFile && file.inputStream().use(BundleIntegrity::sha256)==expected) { "Reimport the exact supplemental reference document" }
        return file
    }
    fun importReference(uri:Uri,expected:String,currentSource:String):File {
        require(expected.matches(Regex("[0-9a-f]{64}")) && expected!=currentSource)
        root.mkdirs();val temp=File.createTempFile("reference-",".tmp",root)
        try {
            requireNotNull(context.contentResolver.openInputStream(uri)).use { input -> temp.outputStream().use { output ->
                val buffer=ByteArray(8192);var total=0
                while(true){val count=input.read(buffer);if(count<0)break;total+=count;require(total<=32*1024*1024){"Reference exceeds 32 MB"};output.write(buffer,0,count)}
                require(total>0)
            } }
            require(temp.inputStream().use(BundleIntegrity::sha256)==expected) { "This is not the locked reference for this territory" }
            val target=File(root,"$expected.reference")
            require(temp.renameTo(target)) { "Could not retain the reference document" }
            return verifiedFile(expected)
        } finally {temp.delete()}
    }
}

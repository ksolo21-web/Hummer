package com.koenterprises.territorycardstudio

import com.koenterprises.territorycardstudio.core.BundleIntegrity
import java.io.File

/** A lock-free, read-only check of previously integrity-validated committed journal bytes. */
internal class EditingJournalWitness private constructor(private val file:File,private val limit:Int,private val expected:String?) {
    companion object {
        private fun digest(file:File,limit:Int):String? {
            require(!File(file.path+".bak").exists() && !File(file.path+".new").exists()) {"Journal write/recovery in progress"}
            if(!file.exists())return null
            require(file.isFile && file.length() in 1..limit.toLong()) {"Journal size changed"}
            val bytes=file.inputStream().use {it.readBytesBounded(limit)}
            require(!File(file.path+".bak").exists() && !File(file.path+".new").exists())
            return BundleIntegrity.sha256(bytes.inputStream())
        }
        fun capture(file:File,limit:Int)=EditingJournalWitness(file,limit,digest(file,limit))
    }
    fun current():Boolean=runCatching {digest(file,limit)==expected}.getOrDefault(false)
}
internal fun java.io.InputStream.readBytesBounded(limit:Int):ByteArray {
    val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
    while(true){val n=read(buffer);if(n<0)break;require(out.size()+n<=limit){"Input exceeds size limit"};out.write(buffer,0,n)}
    return out.toByteArray()
}

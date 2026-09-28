package com.koenterprises.territorycardstudio.core

/** Decimal final-file ceiling shared by assembly, packet checks and Android export. */
object GeneratedPdfSizeContract {
    const val MAX_BYTES=299999
    fun accepts(byteCount:Long)=byteCount in 1..MAX_BYTES.toLong()
}

package com.koenterprises.territorycardstudio

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.koenterprises.territorycardstudio.core.BundleIntegrity
import java.io.File
import kotlin.math.ceil

enum class PdfPreviewKind { FRONT, PACKET, APPROVED }

data class PdfPreviewDocument internal constructor(
    val displayId: String,
    val mode: WorkspaceMode,
    val kind: PdfPreviewKind,
    val canonicalFilename: String,
    val sha256: String,
    val pageCount: Int
)

internal data class CurrentPreviewArtifact(val document: PdfPreviewDocument, val file: File)

/** Renders the existing PDF bytes; never redraws territory geometry or creates a field approval. */
class AndroidPdfPreviewService(
    private val coordinator: AndroidBuildWorkflowCoordinator,
    private val cacheRoot: File,
    private val approvedArtifacts: AndroidPdfArtifactService? = null
) {
    @Synchronized
    fun open(id: String, mode: WorkspaceMode, kind: PdfPreviewKind): PdfPreviewDocument =
        resolve(id, mode, kind).document

    @Synchronized
    fun render(document: PdfPreviewDocument, pageIndex: Int, widthPx: Int): Bitmap {
        require(pageIndex in 0 until document.pageCount) { "PDF page is not available" }
        require(widthPx in 256..3072) { "Preview size is outside the supported range" }
        val current = requireCurrent(document)
        require(current.file.length() in 1..limit(document).toLong()) { "Candidate PDF size is not supported" }
        val bytes = current.file.inputStream().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= limit(document)) { "Candidate PDF size is not supported" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        require(bytes.size <= limit(document) && bytes.inputStream().use(BundleIntegrity::sha256) == document.sha256) {
            "Candidate PDF changed. Return to Build and open the current candidate."
        }
        require(cacheRoot.exists() || cacheRoot.mkdirs()) { "Preview storage is unavailable" }
        val snapshot = File.createTempFile("candidate-preview-", ".pdf", cacheRoot)
        var result: Bitmap? = null
        try {
            snapshot.writeBytes(bytes)
            require(snapshot.inputStream().use(BundleIntegrity::sha256) == document.sha256) { "Preview copy failed verification" }
            val descriptor = ParcelFileDescriptor.open(snapshot, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = try { PdfRenderer(descriptor) } catch (error: Throwable) { descriptor.close(); throw error }
            renderer.use { pdf ->
                require(pdf.pageCount == document.pageCount) { "Candidate PDF page count changed" }
                pdf.openPage(pageIndex).use { page ->
                    require(page.width > 0 && page.height > 0) { "Candidate PDF has invalid page dimensions" }
                    val scale = widthPx.toFloat() / page.width
                    val heightPx = ceil(page.height * scale.toDouble()).toInt()
                    require(heightPx > 0 && widthPx.toLong() * heightPx <= 6_500_000L) { "Preview dimensions are too large" }
                    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                    result = bitmap
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, Matrix().apply { setScale(scale, scale) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
            requireCurrent(document) // A changed source, input, inventory or artifact invalidates the rendered frame too.
            return requireNotNull(result)
        } catch (error: Throwable) {
            result?.recycle()
            throw error
        } finally { snapshot.delete() }
    }

    private fun limit(document: PdfPreviewDocument) = if (document.kind == PdfPreviewKind.APPROVED)
        AndroidApprovedExportService.MAX_APPROVED_BYTES else MAX_PDF_BYTES

    private fun resolve(id: String, mode: WorkspaceMode, kind: PdfPreviewKind): CurrentPreviewArtifact {
        if (kind != PdfPreviewKind.APPROVED) return coordinator.resolvePreview(id, mode, kind)
        val stored = requireNotNull(approvedArtifacts?.resolveExactApproved(id)) { "Attach the exact approved PDF from Export to preview it here." }
        require(stored.file.length() in 1..AndroidApprovedExportService.MAX_APPROVED_BYTES.toLong()) { "Approved PDF size is not supported" }
        val descriptor = ParcelFileDescriptor.open(stored.file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try { PdfRenderer(descriptor) } catch (failure: Throwable) { descriptor.close(); throw failure }
        val pages = renderer.use { it.pageCount }
        require(pages > 0) { "Approved PDF has no pages" }
        return CurrentPreviewArtifact(PdfPreviewDocument(id, mode, kind, stored.canonicalFilename, stored.sha256, pages), stored.file)
    }

    private fun requireCurrent(document: PdfPreviewDocument): CurrentPreviewArtifact {
        val current = resolve(document.displayId, document.mode, document.kind)
        require(current.document == document) { "Candidate PDF changed. Return to Build and open the current candidate." }
        return current
    }

    companion object { private const val MAX_PDF_BYTES = 2 * 1024 * 1024 }
}

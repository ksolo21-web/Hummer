package com.koenterprises.territorycardstudio

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.koenterprises.territorycardstudio.core.BundleIntegrity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class Phase2EPreviewInstrumentationTest {
    private fun pixels(bitmap: Bitmap): IntArray = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }
    private fun direct(file: File, index: Int): Bitmap {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return PdfRenderer(descriptor).use { pdf -> pdf.openPage(index).use { page ->
            Bitmap.createBitmap(768, page.height, Bitmap.Config.ARGB_8888).also { result ->
                result.eraseColor(Color.WHITE)
                page.render(result, null, Matrix().apply { setScale(1f, 1f) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        } }
    }
    private fun rejected(action: () -> Unit) { assertTrue("Expected closed preview gate", runCatching(action).isFailure) }

    @Test fun previewPixelsMatchExactStoredPdfAndFrontPageAcrossBothModes() {
        for (telephone in listOf(false, true)) Phase2DBFixture(telephone).use { f ->
            f.prepare(); f.build(); val packet = f.page2().packet!!
            val front = f.state().front!!
            val cache = File(f.root, "preview-test")
            val preview = AndroidPdfPreviewService(f.coordinator, cache)
            val single = preview.open(f.identity.displayId, f.mode, PdfPreviewKind.FRONT)
            val both = preview.open(f.identity.displayId, f.mode, PdfPreviewKind.PACKET)
            assertEquals(1, single.pageCount); assertEquals(2, both.pageCount)
            assertEquals(front.sha256, single.sha256); assertEquals(packet.sha256, both.sha256)
            val frontImage = preview.render(single, 0, 768)
            val packetFront = preview.render(both, 0, 768)
            val page2 = preview.render(both, 1, 768)
            val directFront = direct(front.file, 0)
            val directBack = direct(packet.file, 1)
            try {
                assertArrayEquals(pixels(directFront), pixels(frontImage))
                assertArrayEquals(pixels(frontImage), pixels(packetFront))
                assertArrayEquals(pixels(directBack), pixels(page2))
                assertFalse(pixels(frontImage).contentEquals(pixels(page2)))
                assertTrue(pixels(frontImage).count { it != Color.WHITE } > 10000)
                assertTrue(pixels(page2).count { it == Color.WHITE } > 10000)
                assertEquals(front.sha256, front.file.inputStream().use(BundleIntegrity::sha256))
                assertEquals(packet.sha256, packet.file.inputStream().use(BundleIntegrity::sha256))
                assertTrue(cache.listFiles().orEmpty().isEmpty())
            } finally { listOf(frontImage, packetFront, page2, directFront, directBack).forEach { it.recycle() } }
        }
    }

    @Test fun previewRejectsMissingArtifactsAndWrongIdentityHashModePageOrSize() {
        Phase2DBFixture(false).use { f ->
            val preview = AndroidPdfPreviewService(f.coordinator, File(f.root, "preview-test"))
            rejected { preview.open(f.identity.displayId, f.mode, PdfPreviewKind.FRONT) }
            f.prepare(); f.build()
            rejected { preview.open(f.identity.displayId, f.mode, PdfPreviewKind.PACKET) }
            val current = preview.open(f.identity.displayId, f.mode, PdfPreviewKind.FRONT)
            rejected { preview.render(current.copy(sha256 = "d".repeat(64)), 0, 768) }
            rejected { preview.render(current.copy(displayId = "1"), 0, 768) }
            rejected { preview.render(current.copy(mode = WorkspaceMode.TELEPHONE), 0, 768) }
            rejected { preview.render(current.copy(kind = PdfPreviewKind.PACKET), 0, 768) }
            rejected { preview.render(current, -1, 768) }
            rejected { preview.render(current, 1, 768) }
            rejected { preview.render(current, 0, 8192) }
            rejected { preview.render(current, 0, 0) }
            val restarted = AndroidBuildWorkflowCoordinator(f.kb, AndroidRenderModelService(f.kb), f.service, f.sources)
            rejected { AndroidPdfPreviewService(restarted, File(f.root, "restarted")).render(current, 0, 768) }
        }
        val app = ApplicationProvider.getApplicationContext<TerritoryCardStudioApplication>()
        rejected { app.services.pdfPreview.open("1", WorkspaceMode.REGULAR, PdfPreviewKind.FRONT) }
    }

    @Test fun staleSourceChangedInventoryAndDeletedOrTamperedPdfHidePreview() {
        Phase2DBFixture(false).use { f ->
            f.prepare(); f.build(); f.page2()
            val preview = AndroidPdfPreviewService(f.coordinator, File(f.root, "preview-test"))
            val packet = preview.open(f.identity.displayId, f.mode, PdfPreviewKind.PACKET)
            f.state().packet!!.file.appendText("tamper")
            rejected { preview.render(packet, 1, 768) }
            f.page2()
            val front = preview.open(f.identity.displayId, f.mode, PdfPreviewKind.FRONT)
            f.state().front!!.file.delete()
            rejected { preview.render(front, 0, 768) }
            rejected { preview.render(packet, 0, 768) }
            f.build(); f.page2()
            val restored = preview.open(f.identity.displayId, f.mode, PdfPreviewKind.PACKET)
            f.importSource("replacement")
            rejected { preview.render(restored, 1, 768) }
        }
        Phase2DBFixture(false).use { f ->
            val inv = (f.inventory as Page2Inventory.LetterWriting).inventory
            val mutable = inv.records.toMutableList()
            f.coordinator.prepare(f.identity.displayId, f.mode, f.source.sha256, f.input,
                Page2Inventory.LetterWriting(inv.copy(records = mutable)))
            f.build(); f.page2()
            val preview = AndroidPdfPreviewService(f.coordinator, File(f.root, "preview-test"))
            val document = preview.open(f.identity.displayId, f.mode, PdfPreviewKind.PACKET)
            mutable.clear()
            rejected { preview.render(document, 1, 768) }
        }
    }

    @Test fun zoomRendersRemainBoundedAndReleaseTemporarySnapshots() {
        Phase2DBFixture(true).use { f ->
            f.prepare(); f.build(); f.page2()
            val cache = File(f.root, "preview-test")
            val preview = AndroidPdfPreviewService(f.coordinator, cache)
            val document = preview.open(f.identity.displayId, f.mode, PdfPreviewKind.PACKET)
            for (width in listOf(768, 1536, 3072, 768)) for (page in 0..1) {
                val bitmap = preview.render(document, page, width)
                assertEquals(width, bitmap.width)
                assertTrue(bitmap.width.toLong() * bitmap.height <= 6_500_000L)
                bitmap.recycle()
                assertTrue(cache.listFiles().orEmpty().isEmpty())
            }
        }
    }
}

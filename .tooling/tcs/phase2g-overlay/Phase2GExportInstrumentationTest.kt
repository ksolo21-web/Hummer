package com.koenterprises.territorycardstudio

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.koenterprises.territorycardstudio.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.*

@RunWith(AndroidJUnit4::class)
class Phase2GExportInstrumentationTest {
    private class Destination(val writeFails: Boolean = false, val readFails: Boolean = false,
        val corruptRead: Boolean = false, val deleteWorks: Boolean = true) : CreatedExportDestination {
        val output = ByteArrayOutputStream()
        var opens = 0
        var deleted = false
        var closed = false
        override fun openOutput(): OutputStream {
            opens++
            return object : OutputStream() {
                override fun write(b: Int) { if (writeFails) throw IOException("write denied"); output.write(b) }
                override fun close() { closed = true }
            }
        }
        override fun openInput(): InputStream {
            check(closed)
            if (readFails) throw IOException("read denied")
            return if (corruptRead) "wrong bytes".byteInputStream() else output.toByteArray().inputStream()
        }
        override fun deleteCreated(): Boolean { deleted = deleteWorks; return deleteWorks }
    }
    private fun failure(block: () -> Unit): Throwable = runCatching(block).exceptionOrNull() ?: error("Invalid export accepted")

    @Test fun exactApprovedAttachmentExportAndRestartPreserveEveryByte() {
        Phase2GExportFixture().use { f ->
            assertNull(f.service.state(f.id).ticket)
            assertTrue(f.service.state(f.id).canAttach)
            val t = requireNotNull(f.attach().ticket)
            val restarted = AndroidApprovedExportService(f.kb, f.artifacts)
            assertEquals(t, restarted.state(f.id).ticket)
            val d = Destination(); val receipt = restarted.exportCreated(t, d)
            assertArrayEquals(f.bytes, d.output.toByteArray())
            assertEquals(t.sha256, receipt.sha256); assertEquals(t.byteCount, receipt.byteCount)
            assertTrue(d.closed); assertFalse(d.deleted)
        }
    }

    @Test fun reservedUnknownAndLocallyApprovedCandidatesCannotExport() {
        Phase2DBFixture(false).use { f ->
            val real = f.app.services.approvedExport
            val reserved = f.original.assignments.values.filter { it.needsNewCard }
            assertEquals(6, reserved.size)
            for (slot in reserved) { assertFalse(real.state(slot.displayId).canAttach); assertNull(real.state(slot.displayId).ticket) }
            assertFalse(real.state("unknown").canAttach)
            f.prepare(); f.build(); f.page2()
            val review = AndroidCandidateReviewService(f.coordinator, File(f.root, "reviews"))
            val t = requireNotNull(review.state(f.identity.displayId, f.mode).ticket)
            review.record(t, "Synthetic Reviewer", CandidateReviewChecks(true, true, true), true, true)
            val export = AndroidApprovedExportService(f.kb, f.service)
            assertFalse(export.state(f.identity.displayId).canAttach)
            assertNull(export.state(f.identity.displayId).ticket)
            failure { export.attach(f.identity.displayId, f.state().packet!!.file.inputStream()) }
        }
    }

    @Test fun wrongOversizedAndDeniedAttachmentsPreserveApprovedOriginal() {
        Phase2GExportFixture().use { f ->
            val original = requireNotNull(f.attach().ticket)
            failure { f.service.attach(f.id, "wrong.pdf".byteInputStream()) }
            val huge = object : InputStream() {
                override fun read(): Int = 0
                override fun read(b: ByteArray, off: Int, len: Int): Int { b.fill(0, off, off + len); return len }
            }
            failure { f.service.attach(f.id, huge) }
            failure { f.service.attach(f.id, object : InputStream() { override fun read(): Int = throw IOException("permission denied") }) }
            assertEquals(original, f.service.state(f.id).ticket)
            assertArrayEquals(f.bytes, f.artifacts.resolveExactApproved(f.id)!!.file.readBytes())
        }
    }

    @Test fun staleIdentityHashAuthorityMissingAndExpiredTicketsWriteNothing() {
        Phase2GExportFixture().use { f ->
            val t = requireNotNull(f.attach().ticket)
            for (wrong in listOf(t.copy(displayId = "unknown"), t.copy(canonicalFilename = "wrong.pdf"),
                t.copy(sha256 = "a".repeat(64)), t.copy(knowledgeBaseRevision = "stale"), t.copy(byteCount = 1), null)) {
                val d = Destination(); failure { f.service.exportCreated(wrong, d) }
                assertEquals(0, d.opens); assertTrue(d.deleted)
            }
            val changed = AndroidApprovedExportService(f.kb.copy(revision = "changed"), f.artifacts)
            val d = Destination(); failure { changed.exportCreated(t, d) }; assertEquals(0, d.opens)
            f.artifacts.resolveExactApproved(f.id)!!.file.appendText("tampered")
            val corrupt = Destination(); failure { f.service.exportCreated(t, corrupt) }; assertEquals(0, corrupt.opens)
            f.attach(); assertTrue(f.artifacts.resolveExactApproved(f.id)!!.file.delete())
            val missing = Destination(); failure { f.service.exportCreated(t, missing) }; assertEquals(0, missing.opens)
        }
    }

    @Test fun writeReadbackAndCleanupFailuresNeverReportSuccess() {
        Phase2GExportFixture().use { f ->
            val t = requireNotNull(f.attach().ticket)
            for (d in listOf(Destination(writeFails = true), Destination(readFails = true), Destination(corruptRead = true))) {
                val e = failure { f.service.exportCreated(t, d) }
                assertTrue(d.deleted); assertTrue(e.message!!.contains("new destination was removed"))
            }
            val incomplete = Destination(readFails = true, deleteWorks = false)
            val e = failure { f.service.exportCreated(t, incomplete) }
            assertTrue(e.message!!.contains("incomplete file may remain")); assertFalse(incomplete.deleted)
            assertEquals(t, f.service.state(f.id).ticket)
        }
    }
}

package com.koenterprises.territorycardstudio

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.koenterprises.territorycardstudio.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class Phase2FReviewInstrumentationTest {
    private val complete = CandidateReviewChecks(true, true, true)
    private fun service(f: Phase2DBFixture) = AndroidCandidateReviewService(f.coordinator, File(f.root, "reviews"))
    private fun ready(f: Phase2DBFixture) { f.prepare(); assertNull(f.build().error); assertNull(f.page2().error) }
    private fun rejected(action: () -> Unit) { assertTrue("Invalid decision was accepted", runCatching(action).isFailure) }

    @Test fun allModesBindExactManifestAndPersistApprovalThenRejection() {
        for (phone in listOf(false, true)) Phase2DBFixture(phone).use { f ->
            ready(f)
            val s = service(f)
            val ticket = requireNotNull(s.state(f.identity.displayId, f.mode).ticket)
            assertNull(s.state(f.identity.displayId, f.mode).decision)
            assertEquals(f.state().packet!!.sha256, ticket.manifest.packetPdfSha256)
            assertEquals(2, ticket.manifest.pageCount)
            val approval = s.record(ticket, "Runtime Reviewer", complete, true, true)
            assertTrue(CrossModePacketValidator.validateExactApproval(ticket.manifest, approval).isEmpty())
            assertEquals(approval, service(f).state(f.identity.displayId, f.mode).decision)
            val rejection = s.record(ticket, "Runtime Reviewer", CandidateReviewChecks(false, false, false), false, true)
            assertEquals(ExactApprovalState.REJECTED, rejection.approvalState)
            assertEquals(rejection, service(f).state(f.identity.displayId, f.mode).decision)
            assertFalse(f.kb.assignments.getValue(f.identity.displayId).fieldReleaseAllowedForExactArtifact)
            assertTrue(f.kb.assignments.getValue(f.identity.displayId).needsNewCard)
        }
        Phase2DBFixture(false).use { f ->
            f.coordinator.prepare(f.identity.displayId, WorkspaceMode.REGULAR, f.source.sha256, f.input)
            assertNull(f.coordinator.buildFront(f.identity.displayId, WorkspaceMode.REGULAR).error)
            val s = service(f); val t = requireNotNull(s.state(f.identity.displayId, WorkspaceMode.REGULAR).ticket)
            assertEquals(1, t.manifest.pageCount)
            assertEquals(CanonicalPacketMode.REGULAR, t.manifest.mode)
            assertEquals(listOf(TerritoryPdfPageRole.FRONT_MAP), t.manifest.pageRoles)
            assertTrue(CrossModePacketValidator.validateExactApproval(t.manifest, s.record(t, "Regular Reviewer", complete, true, true)).isEmpty())
        }
    }

    @Test fun missingInputsIncompletePacketActorChecklistAndConfirmationFailClosed() {
        Phase2DBFixture(false).use { f ->
            val s = service(f)
            assertNull(s.state(f.identity.displayId, f.mode).ticket)
            f.prepare(); f.build()
            assertNull(s.state(f.identity.displayId, f.mode).ticket)
            f.page2(); val t = requireNotNull(s.state(f.identity.displayId, f.mode).ticket)
            rejected { s.record(t, " ", complete, true, true) }
            rejected { s.record(t, "a\nb", complete, true, true) }
            rejected { s.record(t, "Reviewer", complete.copy(pages = false), true, true) }
            rejected { s.record(t, "Reviewer", complete.copy(boundaries = false), true, true) }
            rejected { s.record(t, "Reviewer", complete.copy(data = false), true, true) }
            rejected { s.record(t, "Reviewer", complete, true, false) }
            rejected { s.record(t, "Reviewer", complete, false, false) }
            assertNull(s.state(f.identity.displayId, f.mode).decision)
            val app = f.app.services
            assertNull(app.candidateReview.state("250T", WorkspaceMode.TELEPHONE).ticket)
            assertNull(app.candidateReview.state("1", WorkspaceMode.REGULAR).ticket)
        }
    }

    @Test fun wrongIdentityModeVersionHashAndSameBytesRebuildCannotReuseApproval() {
        Phase2DBFixture(false).use { f ->
            ready(f); val s = service(f); val t = requireNotNull(s.state(f.identity.displayId, f.mode).ticket)
            s.record(t, "Reviewer", complete, true, true)
            for (m in listOf(t.manifest.copy(displayId = "998Ta"), t.manifest.copy(mode = CanonicalPacketMode.TELEPHONE),
                t.manifest.copy(candidateVersion = "stale"), t.manifest.copy(packetPdfSha256 = "a".repeat(64)),
                t.manifest.copy(pageCount = 1), t.manifest.copy(canonicalFilename = "wrong.pdf"))) {
                rejected { s.record(t.copy(manifest = m), "Reviewer", complete, true, true) }
            }
            rejected { s.record(t.copy(sourceSha256 = "b".repeat(64)), "Reviewer", complete, true, true) }
            rejected { s.record(t.copy(preparedInputSha256 = "b".repeat(64)), "Reviewer", complete, true, true) }
            f.page2()
            val new = requireNotNull(s.state(f.identity.displayId, f.mode).ticket)
            assertEquals(t.manifest.packetPdfSha256, new.manifest.packetPdfSha256)
            assertNotEquals(t.manifest.candidateVersion, new.manifest.candidateVersion)
            assertNull(s.state(f.identity.displayId, f.mode).decision)
            rejected { s.record(t, "Reviewer", complete, true, true) }
            f.build(); f.page2()
            rejected { s.record(new, "Reviewer", complete, true, true) }
        }
    }

    @Test fun changedDeletedSourceAndInventoryInvalidateCurrentReview() {
        for (mutation in 0..3) Phase2DBFixture(false).use { f ->
            ready(f); val s = service(f); val t = requireNotNull(s.state(f.identity.displayId, f.mode).ticket)
            s.record(t, "Reviewer", complete, true, true)
            when (mutation) {
                0 -> f.state().packet!!.file.appendText("tampered")
                1 -> assertTrue(f.state().front!!.file.delete())
                2 -> f.importSource("replaced")
                3 -> { f.coordinator.prepare(f.identity.displayId, f.mode, f.source.sha256, f.input,
                    Page2Inventory.LetterWriting((f.inventory as Page2Inventory.LetterWriting).inventory.copy(
                        records = f.inventory.inventory.records.map { it.copy(streetAddress = "102 Example Way") }))) }
            }
            assertNull(s.state(f.identity.displayId, f.mode).ticket)
            rejected { s.record(t, "Reviewer", complete, true, true) }
        }
    }

    @Test fun diskFailureCorruptReceiptAndUnpreparedRestartNeverShowApproval() {
        Phase2DBFixture(false).use { f ->
            ready(f); val s = service(f); val t = requireNotNull(s.state(f.identity.displayId, f.mode).ticket)
            val blockedDirectory = File(f.root, "not-a-directory").apply { writeText("file") }
            rejected { AndroidCandidateReviewService(f.coordinator, blockedDirectory).record(t, "Reviewer", complete, true, true) }
            assertNull(s.state(f.identity.displayId, f.mode).decision)
            s.record(t, "Reviewer", complete, true, true)
            File(f.root, "reviews/${t.manifest.canonicalSha256()}.json").writeText("{partial")
            assertNull(service(f).state(f.identity.displayId, f.mode).ticket)
            assertNotNull(service(f).state(f.identity.displayId, f.mode).blocker)
            val restarted = AndroidBuildWorkflowCoordinator(f.kb, AndroidRenderModelService(f.kb), f.service, f.sources)
            assertNull(AndroidCandidateReviewService(restarted, File(f.root, "reviews")).state(f.identity.displayId, f.mode).decision)
        }
    }
}

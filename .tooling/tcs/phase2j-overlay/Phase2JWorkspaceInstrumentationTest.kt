package com.koenterprises.territorycardstudio

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.koenterprises.territorycardstudio.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class Phase2JWorkspaceInstrumentationTest {
    @Test fun snapshotsBindBothInventoriesAndVerificationToCurrentSource() {
        for (phone in listOf(false, true)) Phase2DBFixture(phone).use { f ->
            val id = f.identity.displayId
            assertNull(f.coordinator.workspaceSnapshot(id, f.mode))
            f.prepare()
            val snapshot = requireNotNull(f.coordinator.workspaceSnapshot(id, f.mode))
            assertEquals(f.source.sha256, snapshot.sourceHash)
            assertEquals(f.input.canonicalSha256(), snapshot.inputHash)
            assertTrue(snapshot.canBuild)
            val inventory = requireNotNull(snapshot.inventory)
            assertEquals(if (phone) 2 else 1, inventory.records.size)
            assertEquals(if (phone) 2 else 1, inventory.verified)
            assertEquals(0, inventory.review); assertEquals(0, inventory.conflicts)
            assertTrue(inventory.provenance.isNotEmpty())
            assertTrue(inventory.provenance.contains("Imported source SHA-256" to f.source.sha256))
            assertTrue(inventory.provenance.contains("Prepared input SHA-256" to f.input.canonicalSha256()))
            if (phone) { assertEquals(1, inventory.unavailableNumbers); assertTrue(inventory.records.any { it.phone == "UNAVAILABLE" }) }
            val item = TerritoryDashboardModel.from(f.kb).items.first { it.assignment.displayId == id }
            val model = VerificationWorkflowModel.from(item, f.mode, f.source, f.kb, f.app.services.activePolicy, snapshot)
            assertEquals(f.state().canBuild, model.buildMayProceed)
            assertEquals(VerificationUiState.VERIFIED, model.categories.first { it.key == "inventory" }.state)
            assertEquals(VerificationUiState.BLOCKED, model.categories.first { it.key == "field_release" }.state)
            f.importSource("changed")
            assertNull(f.coordinator.workspaceSnapshot(id, f.mode))
            val stale = VerificationWorkflowModel.from(item, f.mode, f.sources.verifiedRecord(id), f.kb, f.app.services.activePolicy, snapshot)
            assertFalse(stale.buildMayProceed)
            assertEquals(VerificationUiState.BLOCKED, stale.categories.first { it.key == "inventory" }.state)
        }
    }
    @Test fun mutableInventoryInvalidatesSnapshotAndTerritoryModeCannotLeak() {
        Phase2DBFixture(false).use { f ->
            val inventory = (f.inventory as Page2Inventory.LetterWriting).inventory
            val records = inventory.records.toMutableList()
            f.coordinator.prepare(f.identity.displayId, f.mode, f.source.sha256, f.input, Page2Inventory.LetterWriting(inventory.copy(records = records)))
            val original = requireNotNull(f.coordinator.workspaceSnapshot(f.identity.displayId, f.mode))
            assertNull(f.coordinator.workspaceSnapshot("273", f.mode))
            assertNull(f.coordinator.workspaceSnapshot(f.identity.displayId, WorkspaceMode.REGULAR))
            records.clear()
            assertEquals(1, original.inventory!!.records.size)
            assertNull(f.coordinator.workspaceSnapshot(f.identity.displayId, f.mode))
        }
    }
    @Test fun approvedPreviewIsExactAndIndependentOfCandidatePreparation() {
        Phase2GExportFixture().use { f ->
            val preview = AndroidPdfPreviewService(f.source.coordinator, File(f.source.root, "preview-j"), f.artifacts)
            assertTrue(runCatching { preview.open(f.id, WorkspaceMode.REGULAR, PdfPreviewKind.APPROVED) }.isFailure)
            f.attach()
            val document = preview.open(f.id, WorkspaceMode.REGULAR, PdfPreviewKind.APPROVED)
            assertEquals(BundleIntegrity.sha256(f.bytes.inputStream()), document.sha256)
            f.source.importSource("invalidate-candidate")
            preview.render(document, 0, 512).recycle()
            assertArrayEquals(f.bytes, f.artifacts.resolveExactApproved(f.id)!!.file.readBytes())
            f.artifacts.resolveExactApproved(f.id)!!.file.appendText("tamper")
            assertTrue(runCatching { preview.render(document, 0, 512) }.isFailure)
            assertTrue(runCatching { preview.open("T250", WorkspaceMode.TELEPHONE, PdfPreviewKind.APPROVED) }.isFailure)
        }
    }
}


package com.koenterprises.territorycardstudio

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.koenterprises.territorycardstudio.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class Phase2DBCoordinatorInstrumentationTest {
    @Test fun coordinatorBuildsBothModesThroughProductionAdapterAndPreservesApprovalGate() {
        for (telephone in listOf(false, true)) Phase2DBFixture(telephone).use { f ->
            assertFalse(f.state().canBuild)
            f.prepare()
            assertTrue(f.state().canBuild)
            assertFalse(f.state().canGeneratePage2)
            val front = f.build()
            assertNull(front.error)
            assertTrue(front.front!!.exactPdfValidationPassed)
            assertTrue(front.canGeneratePage2)
            val packet = f.page2()
            assertNull(packet.error)
            assertEquals(2, packet.packet!!.pageCount)
            assertEquals(front.front.sha256, packet.packet.frontPdfSha256)
            assertEquals(packet.packet.sha256, f.page2().packet!!.sha256)
            if (telephone) assertTrue(packet.packet.file.readText(Charsets.ISO_8859_1).contains("UNAVAILABLE"))
            val restart = AndroidBuildWorkflowCoordinator(f.kb, AndroidRenderModelService(f.kb), f.service, f.sources)
            assertFalse(restart.state(f.identity.displayId, f.mode).canBuild)
            assertNull(restart.state(f.identity.displayId, f.mode).packet)
        }
    }

    @Test fun changedSourceAndTamperedArtifactsInvalidateReadyState() {
        Phase2DBFixture(false).use { f ->
            f.prepare(); f.build(); val initial = f.page2()
            initial.packet!!.file.appendText("tamper")
            assertNull(f.state().packet)
            assertNotNull(f.page2().packet)
            f.state().front!!.file.appendText("tamper")
            assertNull(f.state().front)
            assertNull(f.state().packet)
            assertFalse(f.state().canGeneratePage2)
            assertNotNull(f.build().front)
            assertNotNull(f.page2().packet)
            f.importSource("replacement")
            assertFalse(f.state().canBuild)
            assertFalse(f.state().canGeneratePage2)
            assertNull(f.state().packet)
            assertNotNull(f.page2().error)
        }
    }

    @Test fun corruptedSourceCanBeRecoveredByReimportingOriginal() {
        Phase2DBFixture(false).use { f ->
            f.prepare()
            val sourceFile = File(f.app.noBackupFilesDir,
                "territory-card-studio/source-intake-v1/${f.identity.displayId}/${f.source.localFilename}")
            assertTrue(sourceFile.exists())
            sourceFile.appendText("tampered bytes")
            assertFalse(f.state().sourceReady)
            assertFalse(f.state().canBuild)
            val recovered = f.importSource("original")
            assertEquals(f.source.sha256, recovered.sha256)
            assertEquals(recovered, f.sources.verifiedRecord(f.identity.displayId))
            f.coordinator.prepare(f.identity.displayId, f.mode, recovered.sha256, f.input, f.inventory)
            assertTrue(f.state().canBuild)
            assertNotNull(f.build().front)
        }
    }

    @Test fun mutablePreparedDataAndWrongBindingsCannotGenerate() {
        Phase2DBFixture(false).use { f ->
            val labels = f.input.labels.toMutableList()
            f.coordinator.prepare(f.identity.displayId, f.mode, f.source.sha256, f.input.copy(labels = labels), f.inventory)
            labels.clear()
            assertFalse(f.state().canBuild)
            val inv = (f.inventory as Page2Inventory.LetterWriting).inventory
            val records = inv.records.toMutableList()
            f.coordinator.prepare(f.identity.displayId, f.mode, f.source.sha256, f.input,
                Page2Inventory.LetterWriting(inv.copy(records = records)))
            records.clear()
            assertFalse(f.state().canBuild)
            assertTrue(runCatching { f.coordinator.prepare(f.identity.displayId, WorkspaceMode.REGULAR,
                f.source.sha256, f.input, f.inventory) }.isFailure)
            assertTrue(runCatching { f.coordinator.prepare(f.identity.displayId, f.mode,
                "d".repeat(64), f.input, f.inventory) }.isFailure)
            assertTrue(runCatching { f.coordinator.prepare(f.identity.displayId, f.mode,
                f.source.sha256, f.input, Page2Inventory.LetterWriting(inv.copy(knowledgeBaseRevision = "stale"))) }.isFailure)
            f.coordinator.prepare(f.identity.displayId, WorkspaceMode.REGULAR, f.source.sha256, f.input)
            val regular = f.coordinator.buildFront(f.identity.displayId, WorkspaceMode.REGULAR)
            assertNotNull(regular.front)
            assertFalse(regular.canGeneratePage2)
        }
    }
}

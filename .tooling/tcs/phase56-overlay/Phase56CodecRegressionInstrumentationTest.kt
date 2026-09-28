package com.koenterprises.territorycardstudio

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.koenterprises.territorycardstudio.core.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class Phase56CodecRegressionInstrumentationTest {
    @Test fun malformedAndOversizedProviderMapsFailClosed() {
        Phase56Fixture(WorkspaceMode.REGULAR).use { x ->
            val decoded=VerifiedProjectCodec.decode(x.project)
            assertTrue(decoded.input.liveResult.decision.providerEvidenceDigests.isNotEmpty())
            for (invalid in listOf<Any>(JSONArray(), JSONObject().put("provider", 7),
                JSONObject().apply { repeat(4097) { put("provider-$it", "value") } })) {
                val root=JSONObject(x.project.toString(Charsets.UTF_8))
                root.getJSONArray("input").getJSONArray(3).getJSONArray(2).put(11,invalid)
                val bytes=ExtendedValues.canonical(root).toByteArray(Charsets.UTF_8)
                assertTrue("Malformed or oversized map must be rejected", runCatching { VerifiedProjectCodec.decode(bytes) }.isFailure)
            }
        }
    }

    @Test fun failedReplacementPreparationPreservesRecoveryAndCurrentApproval() {
        Phase56Fixture(WorkspaceMode.REGULAR).use { x ->
            x.ready()
            // Historical provider claims are ignored and independently recomputed during intake.
            val changed=x.f.input.copy(liveResult=x.f.input.liveResult.copy(decision=x.f.input.liveResult.decision.copy(
                providerDataVintages=mapOf("historical" to "ignored"))))
            val alternate=VerifiedProjectCodec.encode(x.id,x.mode,x.f.source.sha256,changed,x.inventory)
            val hash=BundleIntegrity.sha256(alternate.inputStream())
            assertNotEquals(x.projectHash,hash)
            val slot=x.kb.assignments.getValue(x.id)
            val kb=x.kb.copy(assignments=x.kb.assignments+(x.id to slot.copy(sourceHashes=slot.sourceHashes+hash)))
            val replacement=AndroidVerifiedProjectIntake(kb,x.f.app.services.activePolicy,x.coordinator,x.f.sources,
                File(x.f.root,"projects"),fetchEvidence={request->x.evidence(request)},clock={x.now})
            val pending=replacement.validate(x.id,x.mode,alternate)
            // A newer verified preparation makes the pending replacement stale.
            x.prepare();x.build();x.approve()
            val before=x.lifecycle.state(x.id,x.mode)
            assertTrue(before.active)
            val pointer=File(x.f.root,"projects").listFiles()!!.single{it.extension=="saved"}
            val previous=pointer.readBytes()
            val failure=runCatching{replacement.prepare(pending)}.exceptionOrNull()
            assertNotNull(failure)
            assertTrue(failure!!.message.orEmpty().contains("Prepared candidate changed"))
            assertArrayEquals(previous,pointer.readBytes())
            assertTrue(x.lifecycle.state(x.id,x.mode).active)
            assertEquals(before.revision,x.lifecycle.state(x.id,x.mode).revision)
            assertEquals(x.projectHash,replacement.revalidateSaved(x.id,x.mode).projectSha256)
        }
    }
    @Test fun invalidationAfterReceiptCreationRemovesSuccessRecordAndDestination() {
        Phase56Fixture(WorkspaceMode.REGULAR).use { x ->
            x.ready()
            val receipts=File(x.f.root,"race-receipts")
            var invalidated=false
            val output=AndroidFinalOutputService(x.kb,x.f.app.services.activePolicy,x.coordinator,x.lifecycle,receipts,
                fetchEvidence={request->x.evidence(request)},clock={
                    if(!invalidated && receipts.listFiles().orEmpty().any{it.extension=="json"}) {
                        invalidated=true;x.f.importSource("changed-after-receipt")
                    }
                    x.now
                })
            val ticket=output.validate(x.id,x.mode)
            val destination=Phase56Destination()
            assertTrue(runCatching{output.exportCreated(ticket,destination,false)}.isFailure)
            assertTrue("Race must occur after receipt was created",invalidated)
            assertTrue(destination.deleted)
            assertTrue(receipts.listFiles().isNullOrEmpty())
            assertFalse(x.lifecycle.state(x.id,x.mode).active)
        }
    }

}

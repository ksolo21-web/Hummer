package com.koenterprises.territorycardstudio

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.koenterprises.territorycardstudio.core.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.*

internal class Phase56Fixture(val mode:WorkspaceMode):AutoCloseable {
    val f=Phase2DBFixture(mode==WorkspaceMode.TELEPHONE);val id=f.identity.displayId
    val inventory=if(mode==WorkspaceMode.REGULAR)null else f.inventory
    val project=VerifiedProjectCodec.encode(id,mode,f.source.sha256,f.input,inventory)
    val projectHash=BundleIntegrity.sha256(project.inputStream())
    val kb=f.kb.copy(assignments=f.kb.assignments+(id to f.slot.copy(sourceHashes=f.slot.sourceHashes+projectHash)))
    val pdf=AndroidPdfArtifactService(kb,File(f.root,"output-pdf"),f.app.assets.open("territory/render-authority/Canonical-New-Designed-Template-R48.pdf").use{it.readBytes()})
    var now=1000L
    var evidenceAllowed=true
    val coordinator=AndroidBuildWorkflowCoordinator(kb,AndroidRenderModelService(kb),pdf,f.sources)
    val lifecycle=AndroidCandidateLifecycleService(kb,coordinator,f.sources,File(f.root,"output-lifecycle"))
    val intake=AndroidVerifiedProjectIntake(kb,f.app.services.activePolicy,coordinator,f.sources,File(f.root,"projects"),fetchEvidence={request->if(evidenceAllowed)evidence(request) else emptyList()},clock={now})
    val output=AndroidFinalOutputService(kb,f.app.services.activePolicy,coordinator,lifecycle,File(f.root,"receipts"),fetchEvidence={request->if(evidenceAllowed)evidence(request) else emptyList()},clock={now})
    fun evidence(request:LiveGeometryVerificationRequest)=f.input.liveResult.evidence.map{it.copy(requestFingerprint=request.requestFingerprint,responseSha256=BundleIntegrity.sha256((it.providerId+request.requestFingerprint).byteInputStream()))}
    fun prepare(){intake.prepare(intake.validate(id,mode,project))}
    fun build(){assertNull(lifecycle.build(id,mode,false).error);if(mode!=WorkspaceMode.REGULAR)assertNull(lifecycle.build(id,mode,true).error)}
    fun approve(){val s=lifecycle.state(id,mode);lifecycle.decide(requireNotNull(s.ticket),"Synthetic reviewer",CandidateReviewChecks(true,true,true),true,true,s.revision)}
    fun ready(){prepare();build();approve()}
    override fun close()=f.close()
}
internal class Phase56Destination:CreatedExportDestination {
    var bytes=ByteArray(0);var deleted=false;var corrupt=false;var outputFailure=false;var deleteFailure=false;var onRead:(()->Unit)?=null;var wrongName=false
    override fun verifyCanonicalName(expected:String){require(!wrongName){"Provider renamed document"}}
    override fun openOutput():OutputStream {if(outputFailure)throw IOException("Provider write denied");return object:ByteArrayOutputStream(){override fun close(){bytes=toByteArray();super.close()}}}
    override fun openInput():InputStream {onRead?.invoke();return (if(corrupt)bytes+byteArrayOf(0) else bytes).inputStream()}
    override fun deleteCreated():Boolean {if(deleteFailure)return false;deleted=true;bytes=ByteArray(0);return true}
}
@RunWith(AndroidJUnit4::class) class Phase56OutputInstrumentationTest {
    private fun denied(block:()->Unit){assertTrue("Must fail closed",runCatching(block).isFailure)}
    @Test fun allModesStartUnpreparedAndExportExactApprovedPacketAndAudit() {
        for(mode in WorkspaceMode.entries)Phase56Fixture(mode).use{x->
            assertFalse(x.coordinator.state(x.id,mode).inputReady);x.ready()
            val ticket=x.output.validate(x.id,mode);val pdf=Phase56Destination();val audit=Phase56Destination()
            val p=x.output.exportCreated(ticket,pdf,false);val a=x.output.exportCreated(ticket,audit,true)
            assertArrayEquals(x.lifecycle.readCurrentPdf(x.id,mode),pdf.bytes);assertEquals(x.f.identity.canonicalFilename,p.filename)
            assertEquals(if(mode==WorkspaceMode.REGULAR)1 else 2,ticket.review.manifest.pageCount)
            val j=JSONObject(audit.bytes.toString(Charsets.UTF_8));assertEquals(p.sha256,j.getString("pdfSha256"));assertEquals(ticket.lifecycleRevision,j.getString("lifecycleRevision"))
            assertEquals(ticket.review.manifest.candidateVersion,j.getString("candidateVersion"));assertEquals(2,j.getJSONArray("providers").length())
            assertEquals(BundleIntegrity.sha256(audit.bytes.inputStream()),a.sha256)
            val dir=x.f.app.filesDir;File(dir,"phase56-${mode.name.lowercase()}.pdf").writeBytes(pdf.bytes);File(dir,"phase56-${mode.name.lowercase()}-audit.json").writeBytes(audit.bytes)
            assertEquals(2,File(x.f.root,"receipts").listFiles()!!.size)
        }
    }
    @Test fun unapprovedRejectedAndPartialPacketsCannotExport(){for(mode in WorkspaceMode.entries)Phase56Fixture(mode).use{x->
        denied{x.output.validate(x.id,mode)};x.prepare();assertNull(x.lifecycle.build(x.id,mode,false).error);denied{x.output.validate(x.id,mode)}
        if(mode!=WorkspaceMode.REGULAR)assertNull(x.lifecycle.build(x.id,mode,true).error)
        denied{x.output.validate(x.id,mode)};val s=x.lifecycle.state(x.id,mode)
        x.lifecycle.decide(s.ticket!!,"Reject",CandidateReviewChecks(false,false,false),false,true,s.revision);denied{x.output.validate(x.id,mode)}
    }}
    @Test fun newVersionWithIdenticalBytesInvalidatesExport(){Phase56Fixture(WorkspaceMode.REGULAR).use{x->x.ready();val t=x.output.validate(x.id,x.mode);x.build();x.approve()
        val d=Phase56Destination();denied{x.output.exportCreated(t,d,false)};assertTrue(d.deleted)}}
    @Test fun changedSourceAndSameCandidateRejectionInvalidateExport(){for(source in listOf(false,true))Phase56Fixture(WorkspaceMode.LETTER_WRITING).use{x->x.ready();val t=x.output.validate(x.id,x.mode)
        if(source)x.f.importSource("changed-final") else {val s=x.lifecycle.state(x.id,x.mode);x.lifecycle.decide(s.ticket!!,"Reject",CandidateReviewChecks(false,false,false),false,true,s.revision)}
        val d=Phase56Destination();denied{x.output.exportCreated(t,d,false)};assertTrue(d.deleted)}}
    @Test fun offlineFinalVerificationCannotReuseEarlierApproval(){Phase56Fixture(WorkspaceMode.TELEPHONE).use{x->x.ready();x.output.validate(x.id,x.mode);x.evidenceAllowed=false
        denied{x.output.validate(x.id,x.mode)};assertTrue(x.lifecycle.state(x.id,x.mode).active)}}
    @Test fun expiredAndUnknownSessionTicketsFailClosed(){Phase56Fixture(WorkspaceMode.REGULAR).use{x->x.ready();val t=x.output.validate(x.id,x.mode);denied{x.output.verifyCurrent(t.copy(session="unknown"))};x.now+=300001;denied{x.output.verifyCurrent(t)}}}
    @Test fun failedWritesAndCorruptReadbackDeleteNewDocument(){for(failure in 0..2)Phase56Fixture(WorkspaceMode.REGULAR).use{x->x.ready();val t=x.output.validate(x.id,x.mode)
        val d=Phase56Destination().apply{corrupt=failure==0;outputFailure=failure==1;onRead=if(failure==2)({x.f.importSource("during-save");Unit}) else null}
        denied{x.output.exportCreated(t,d,false)};assertTrue(d.deleted);assertTrue(File(x.f.root,"receipts").listFiles().isNullOrEmpty())}}
    @Test fun cleanupFailureReportsResidualFile(){Phase56Fixture(WorkspaceMode.REGULAR).use{x->x.ready();val d=Phase56Destination().apply{corrupt=true;deleteFailure=true}
        val e=runCatching{x.output.exportCreated(x.output.validate(x.id,x.mode),d,false)}.exceptionOrNull();assertTrue(e!!.message!!.contains("A file may remain"))}}
    @Test fun auditWriteIsBoundToSameApprovalAndProviderEvidence(){Phase56Fixture(WorkspaceMode.TELEPHONE).use{x->x.ready();val t=x.output.validate(x.id,x.mode);val d=Phase56Destination().apply{corrupt=true}
        denied{x.output.exportCreated(t,d,true)};assertTrue(d.deleted)}}
    @Test fun exactDecimalPdfLimitIsEnforced(){assertEquals(299999,AndroidFinalOutputService.MAX_PDF_BYTES)
        assertEquals(299999,AndroidFinalOutputService.readBounded(ByteArray(299999).inputStream(),299999).size)
        denied{AndroidFinalOutputService.readBounded(ByteArray(300000).inputStream(),299999)}}
    @Test fun registeredProjectCodecRoundTripAcrossModes(){for(mode in WorkspaceMode.entries)Phase56Fixture(mode).use{x->val p=VerifiedProjectCodec.decode(x.project)
        assertEquals(x.f.input,p.input);assertEquals(x.inventory,p.inventory);assertEquals(x.f.source.sha256,p.source)}}
    @Test fun arbitraryOrWrongModeProjectCannotCreateBaseline(){Phase56Fixture(WorkspaceMode.REGULAR).use{x->denied{x.intake.validate(x.id,x.mode,x.project+byteArrayOf(32))}
        denied{x.intake.validate(x.id,WorkspaceMode.LETTER_WRITING,x.project)};assertFalse(x.coordinator.state(x.id,x.mode).inputReady)}}
    @Test fun expiredImportAndSourceChangedDuringValidationCannotPrepare(){for(expire in listOf(false,true))Phase56Fixture(WorkspaceMode.REGULAR).use{x->val t=x.intake.validate(x.id,x.mode,x.project)
        if(expire)x.now+=900001 else x.f.importSource("after-project-verify");denied{x.intake.prepare(t)};assertFalse(x.coordinator.state(x.id,x.mode).inputReady)}}
    @Test fun importedProviderClaimsDoNotReplaceFreshVerification(){Phase56Fixture(WorkspaceMode.REGULAR).use{x->x.evidenceAllowed=false;denied{x.intake.validate(x.id,x.mode,x.project)};assertFalse(x.coordinator.state(x.id,x.mode).inputReady)}}
    @Test fun projectTamperAfterPreparationInvalidatesApproval(){Phase56Fixture(WorkspaceMode.REGULAR).use{x->x.ready();val t=x.output.validate(x.id,x.mode)
        File(x.f.root,"projects").listFiles()!!.single{it.extension=="json"}.appendText(" ");denied{x.output.verifyCurrent(t)};assertFalse(x.lifecycle.state(x.id,x.mode).active)}}
    @Test fun freshServiceCannotRestoreExportAuthority(){Phase56Fixture(WorkspaceMode.REGULAR).use{x->x.ready();val t=x.output.validate(x.id,x.mode)
        val fresh=AndroidFinalOutputService(x.kb,x.f.app.services.activePolicy,x.coordinator,x.lifecycle,File(x.f.root,"fresh-receipts"),fetchEvidence={x.f.input.liveResult.evidence})
        denied{fresh.verifyCurrent(t)}}}
    @Test fun preparedBaselineSurvivesImportTicketExpiry(){Phase56Fixture(WorkspaceMode.REGULAR).use{x->x.prepare();x.now+=900001
        assertTrue(x.coordinator.state(x.id,x.mode).inputReady);x.build();x.approve();x.output.validate(x.id,x.mode)}}
    @Test fun repeatedProjectImportUsesOneDurableSlot(){Phase56Fixture(WorkspaceMode.REGULAR).use{x->repeat(65){x.intake.validate(x.id,x.mode,x.project)}
        assertEquals(1,File(x.f.root,"projects").listFiles()!!.size);x.prepare();assertTrue(x.coordinator.state(x.id,x.mode).inputReady)}}
    @Test fun renamedOutputFailsWithoutSaving(){Phase56Fixture(WorkspaceMode.REGULAR).use{x->x.ready();val d=Phase56Destination().apply{wrongName=true}
        denied{x.output.exportCreated(x.output.validate(x.id,x.mode),d,false)};assertTrue(d.deleted)}}
    @Test fun realReplacementCardsRemainUncommissioned(){Phase56Fixture(WorkspaceMode.REGULAR).use{x->x.ready();x.output.validate(x.id,x.mode)
        for(id in listOf("T250","A257","297","A298","299","TA347")){assertEquals(x.f.original.assignments[id],x.kb.assignments[id]);assertTrue(x.kb.assignments.getValue(id).needsNewCard)
            val mode=WorkspaceModePolicy.defaultMode(x.kb.assignments.getValue(id));assertFalse(x.lifecycle.state(id,mode).active);assertTrue(x.lifecycle.state(id,mode).candidates.isEmpty())}}}
}

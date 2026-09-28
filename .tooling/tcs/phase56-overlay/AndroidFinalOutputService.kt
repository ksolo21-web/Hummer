package com.koenterprises.territorycardstudio

import android.os.SystemClock
import android.util.AtomicFile
import com.koenterprises.territorycardstudio.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.util.UUID

data class FinalOutputTicket internal constructor(val session:String,val review:CandidateReviewTicket,val lifecycleRevision:String,
    val auditSha256:String,val byteCount:Int,val verifiedAt:String,val expiresAt:Long)
data class FinalOutputState(val approved:Boolean,val message:String,val ticket:FinalOutputTicket?=null)
data class FinalOutputReceipt(val filename:String,val sha256:String,val bytes:Int,val kind:String)

/** A short-lived export authorization binds the local approval to freshly revalidated provider and exact PDF evidence. */
class AndroidFinalOutputService internal constructor(private val kb:TerritoryKnowledgeBase,private val policy:OnlineSourcePolicy,
    private val coordinator:AndroidBuildWorkflowCoordinator,private val lifecycle:AndroidCandidateLifecycleService,
    private val directory:File,private val fetchEvidence:(LiveGeometryVerificationRequest)->List<ProviderVerificationEvidence>,
    private val clock:()->Long={SystemClock.elapsedRealtime()}) {
    companion object {const val MAX_PDF_BYTES=299999;const val MAX_AUDIT_BYTES=1024*1024
        internal fun readBounded(input:InputStream,max:Int):ByteArray {val out=java.io.ByteArrayOutputStream();val buf=ByteArray(8192)
            while(true){val n=input.read(buf);if(n<0)break;require(out.size().toLong()+n<=max){"Output exceeds permitted size"};out.write(buf,0,n)};return out.toByteArray()}}
    private data class Session(val ticket:FinalOutputTicket,val audit:ByteArray,val issued:Long)
    private val sessions=linkedMapOf<String,Session>()
    private fun hash(b:ByteArray)=BundleIntegrity.sha256(b.inputStream())
    private fun key(id:String,mode:WorkspaceMode)="$id:${mode.name}"
    fun state(id:String,mode:WorkspaceMode):FinalOutputState {
        val s=lifecycle.state(id,mode)
        return FinalOutputState(s.active,if(s.active)"Reviewed local reference. Run final validation before saving." else s.blocker ?: "Build and explicitly approve a complete candidate first.")
    }
    @Synchronized fun validate(id:String,mode:WorkspaceMode):FinalOutputTicket {
        sessions.remove(key(id,mode))
        val before=lifecycle.state(id,mode);require(before.active){before.blocker ?: "No current approved candidate"}
        val t=requireNotNull(before.promoted).ticket
        val baseline=coordinator.editingBaseline(id,mode).first
        require(baseline.canonicalSha256()==t.preparedInputSha256)
        val request=LiveGeometryVerificationRequestFactory.create(kb,policy,id,baseline.assignment.authoritySha256,
            TopologyOverlapDecisionEngine.topologySignature(baseline.assignment.roads),baseline.assignment.buildings.flatMap{it.sourceMembers}.distinct(),
            baseline.sourceTruth,baseline.liveRequest.jurisdiction)
        val evidence=EditingSnapshots.detach(fetchEvidence(EditingSnapshots.detach(request)))
        val decision=LiveGeometryVerificationReconciler.reconcile(request,policy,evidence)
        require(decision.renderable){"Current independent source verification failed. Resolve the findings before export."}
        val fresh=baseline.copy(liveRequest=request,liveResult=LiveVerificationExecutionResult(request.requestFingerprint,evidence,decision,emptyList()))
        val adapted=ProductionRenderModelAdapter.adaptProduction(kb,fresh)
        require(adapted is RenderModelAdaptationResult.Renderable){"Final source, topology, coverage, label or building validation failed"}
        val bytes=lifecycle.readCurrentPdf(id,mode)
        require(bytes.size in 1..MAX_PDF_BYTES){"Final PDF must be under 300,000 bytes"}
        require(hash(bytes)==t.manifest.packetPdfSha256 && t.manifest.printReady)
        val latest=lifecycle.state(id,mode)
        require(latest.active && latest.promoted?.ticket==t && latest.revision==before.revision){"Approval changed during final validation"}
        val approval=latest.history.last {it.action=="APPROVED" && it.candidateSha256==t.manifest.canonicalSha256()}
        val m=t.manifest
        val receipt=ExactPacketApprovalReceipt(m.canonicalSha256(),m.mode,m.displayId,m.canonicalFilename,m.candidateVersion,m.packetPdfSha256,
            ExactApprovalState.EXPLICITLY_APPROVED,approval.actor,approval.at)
        require(CrossModePacketValidator.validateExactApproval(m,receipt).isEmpty())
        val at=Instant.now().toString()
        val audit=JSONObject().put("schema","final-output-v1").put("territory",id).put("mode",mode.name).put("filename",m.canonicalFilename)
            .put("candidateVersion",m.candidateVersion).put("manifestSha256",m.canonicalSha256()).put("pdfSha256",m.packetPdfSha256)
            .put("frontSha256",m.frontPdfSha256).put("inventorySha256",m.sourceInventorySha256 ?: JSONObject.NULL)
            .put("sourceSha256",t.sourceSha256).put("inputSha256",t.preparedInputSha256).put("assignmentAuthoritySha256",m.assignmentAuthoritySha256)
            .put("knowledgeBaseRevision",kb.revision).put("policyRevision",policy.revision).put("pageCount",m.pageCount)
            .put("pageRoles",JSONArray(m.pageRoles.map{it.name})).put("pdfBytes",bytes.size).put("printReady",true)
            .put("printInstructions","Print at actual size (100%); do not fit or crop. Page 1 is the map; page 2, when present, is its working inventory.")
            .put("validatedAt",at).put("lifecycleRevision",latest.revision).put("reviewer",approval.actor).put("approvedAt",approval.at)
            .put("providers",JSONArray(evidence.map {e->JSONObject().put("provider",e.providerId).put("responseSha256",e.responseSha256)
                .put("observedAt",e.observedAtUtc).put("sourceDataVintage",e.sourceDataVintage).put("requestFingerprint",e.requestFingerprint)}))
            .put("history",JSONArray(latest.history.map{e->JSONObject().put("sequence",e.sequence).put("action",e.action).put("candidateSha256",e.candidateSha256)
                .put("actor",e.actor).put("at",e.at).put("reason",e.reason)}))
        val auditBytes=ExtendedValues.canonical(audit).toByteArray(Charsets.UTF_8);require(auditBytes.size<=MAX_AUDIT_BYTES)
        val now=clock();val ticket=FinalOutputTicket(UUID.randomUUID().toString(),t,latest.revision,hash(auditBytes),bytes.size,at,now+300000)
        sessions.entries.removeAll {clock()>it.value.ticket.expiresAt};require(sessions.size<16){"Close an unused export session"}
        sessions[key(id,mode)]=Session(ticket,auditBytes,now)
        verifyCurrent(ticket);return ticket
    }
    @Synchronized fun verifyCurrent(t:FinalOutputTicket) {
        val mode=WorkspaceMode.valueOf(t.review.manifest.mode.name);val p=requireNotNull(sessions[key(t.review.manifest.displayId,mode)]){"Run final validation again"}
        require(p.ticket==t && clock() in p.issued..t.expiresAt){"Final validation expired. Validate again before saving."}
        val s=lifecycle.state(t.review.manifest.displayId,mode)
        require(s.active && s.promoted?.ticket==t.review && s.revision==t.lifecycleRevision){"Candidate or approval changed. Validate again."}
        require(hash(p.audit)==t.auditSha256)
        val b=lifecycle.readCurrentPdf(t.review.manifest.displayId,mode)
        require(b.size==t.byteCount && b.size<=MAX_PDF_BYTES && hash(b)==t.review.manifest.packetPdfSha256){"Final PDF changed"}
    }
    @Synchronized internal fun exportCreated(t:FinalOutputTicket?,destination:CreatedExportDestination,audit:Boolean):FinalOutputReceipt {
        try {
            requireNotNull(t){"Export session expired. Validate again."};verifyCurrent(t)
            val m=t.review.manifest;val mode=WorkspaceMode.valueOf(m.mode.name)
            val bytes=if(audit)sessions.getValue(key(m.displayId,mode)).audit.copyOf() else lifecycle.readCurrentPdf(m.displayId,mode)
            val name=if(audit)m.canonicalFilename.removeSuffix(".pdf")+" - audit.json" else m.canonicalFilename
            destination.verifyCanonicalName(name)
            val expected=if(audit)t.auditSha256 else m.packetPdfSha256
            require(hash(bytes)==expected)
            destination.openOutput().use {it.write(bytes);it.flush()}
            val actual=destination.openInput().use{readBounded(it,bytes.size)}
            require(actual.contentEquals(bytes)){"Saved output differs from validated bytes"}
            verifyCurrent(t)
            destination.verifyCanonicalName(name)
            val receipt=FinalOutputReceipt(name,expected,bytes.size,if(audit)"AUDIT" else "PDF")
            val record=record(t,receipt)
            try {verifyCurrent(t)} catch(e:Exception) {record.delete();throw e}
            return receipt
        } catch(e:Exception) {
            val removed=runCatching{destination.deleteCreated()}.getOrDefault(false)
            throw IllegalStateException("Save failed: ${e.message}. "+if(removed)"The new destination was removed." else "A file may remain. Delete it before retrying.",e)
        }
    }
    private fun record(t:FinalOutputTicket,r:FinalOutputReceipt):AtomicFile {
        require(directory.isDirectory || directory.mkdirs()){"Cannot save output receipt"}
        require(directory.listFiles().orEmpty().size<512){"Output receipt archive is full"}
        val bytes=ExtendedValues.canonical(JSONObject().put("schema",1).put("session",t.session).put("manifestSha256",t.review.manifest.canonicalSha256())
            .put("lifecycleRevision",t.lifecycleRevision).put("filename",r.filename).put("sha256",r.sha256).put("bytes",r.bytes)
            .put("kind",r.kind).put("savedAt",Instant.now().toString()).put("readbackVerified",true)).toByteArray()
        val file=AtomicFile(File(directory,"${UUID.randomUUID()}.json"));val out=file.startWrite()
        try {out.write(bytes);out.fd.sync();file.finishWrite(out);require(file.openRead().use{readBounded(it,65536)}.contentEquals(bytes))}
        catch(e:Exception){file.failWrite(out);file.delete();throw e}
        return file
    }
}

package com.koenterprises.territorycardstudio

import android.util.AtomicFile
import com.koenterprises.territorycardstudio.core.*
import org.json.JSONObject
import java.io.File
import java.time.Instant

data class CandidateReviewTicket internal constructor(
    val manifest: CanonicalPacketManifest,
    val sourceSha256: String,
    val preparedInputSha256: String
)

data class CandidateReviewChecks(val pages: Boolean, val boundaries: Boolean, val data: Boolean) {
    val complete: Boolean get() = pages && boundaries && data
}

data class CandidateReviewState(
    val ticket: CandidateReviewTicket? = null,
    val decision: ExactPacketApprovalReceipt? = null,
    val blocker: String? = null
)

/** Local review evidence only: this class cannot promote authority or release a field PDF. */
class AndroidCandidateReviewService(
    private val coordinator: AndroidBuildWorkflowCoordinator,
    private val directory: File
) {
    @Synchronized
    fun state(id: String, mode: WorkspaceMode): CandidateReviewState = try {
        val ticket = coordinator.resolveReview(id, mode)
        coordinator.withCurrentReview(ticket) {
            CandidateReviewState(ticket, read(ticket))
        }
    } catch (failure: Exception) {
        CandidateReviewState(blocker = failure.message ?: "Review unavailable")
    }

    @Synchronized
    fun record(ticket: CandidateReviewTicket, actor: String, checks: CandidateReviewChecks,
        approved: Boolean, confirmed: Boolean): ExactPacketApprovalReceipt = coordinator.withCurrentReview(ticket) {
        require(confirmed) { "Explicit confirmation is required" }
        require(actor.trim().length in 1..120 && actor.none { it.isISOControl() }) { "Enter a reviewer name (1–120 characters)" }
        require(!approved || checks.complete) { "Complete all mandatory review checks" }
        val m = ticket.manifest
        val receipt = ExactPacketApprovalReceipt(m.canonicalSha256(), m.mode, m.displayId, m.canonicalFilename,
            m.candidateVersion, m.packetPdfSha256,
            if (approved) ExactApprovalState.EXPLICITLY_APPROVED else ExactApprovalState.REJECTED,
            actor.trim(), Instant.now().toString())
        require(CrossModePacketValidator.validateExactApproval(m,
            receipt.copy(approvalState = ExactApprovalState.EXPLICITLY_APPROVED)).isEmpty()) { "Invalid exact approval binding" }
        require(directory.isDirectory || directory.mkdirs()) { "Cannot create review store" }
        val json = JSONObject().put("schema", 1).put("manifest", receipt.manifestSha256)
            .put("mode", receipt.mode.name).put("id", receipt.displayId).put("filename", receipt.canonicalFilename)
            .put("version", receipt.candidateVersion).put("pdf", receipt.packetPdfSha256)
            .put("state", receipt.approvalState.name).put("actor", receipt.approvedBy).put("at", receipt.approvedAtUtc)
            .put("source", ticket.sourceSha256).put("input", ticket.preparedInputSha256)
            .put("pages", checks.pages).put("boundaries", checks.boundaries).put("data", checks.data)
        val file = store(ticket)
        val stream = file.startWrite()
        try {
            stream.write(json.toString().toByteArray(Charsets.UTF_8))
            stream.fd.sync()
            file.finishWrite(stream)
        } catch (failure: Exception) { file.failWrite(stream); throw failure }
        check(read(ticket) == receipt) { "Review record could not be verified after saving" }
        coordinator.withCurrentReview(ticket) { receipt }
    }

    private fun store(ticket: CandidateReviewTicket): AtomicFile = AtomicFile(File(directory,
        ticket.manifest.canonicalSha256() + ".json"))

    private fun read(ticket: CandidateReviewTicket): ExactPacketApprovalReceipt? {
        val file = store(ticket)
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
        val bytes = file.openRead().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= 16384) { "Review record is too large" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        val j = JSONObject(bytes.toString(Charsets.UTF_8))
        require(j.getInt("schema") == 1 && j.getString("source") == ticket.sourceSha256 &&
            j.getString("input") == ticket.preparedInputSha256) { "Review provenance changed" }
        val r = ExactPacketApprovalReceipt(j.getString("manifest"), CanonicalPacketMode.valueOf(j.getString("mode")),
            j.getString("id"), j.getString("filename"), j.getString("version"), j.getString("pdf"),
            ExactApprovalState.valueOf(j.getString("state")), j.getString("actor"), j.getString("at"))
        require(r.approvalState in setOf(ExactApprovalState.EXPLICITLY_APPROVED, ExactApprovalState.REJECTED)) { "Invalid review state" }
        require(CrossModePacketValidator.validateExactApproval(ticket.manifest,
            r.copy(approvalState = ExactApprovalState.EXPLICITLY_APPROVED)).isEmpty()) { "Review record does not match this candidate" }
        Instant.parse(r.approvedAtUtc)
        require(r.approvalState != ExactApprovalState.EXPLICITLY_APPROVED ||
            (j.getBoolean("pages") && j.getBoolean("boundaries") && j.getBoolean("data"))) { "Mandatory review is incomplete" }
        return r
    }
}

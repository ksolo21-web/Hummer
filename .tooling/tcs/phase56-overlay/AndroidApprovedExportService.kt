package com.koenterprises.territorycardstudio

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.koenterprises.territorycardstudio.core.*
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

data class ApprovedExportTicket internal constructor(val displayId: String, val canonicalFilename: String,
    val sha256: String, val knowledgeBaseRevision: String, val byteCount: Int)

data class ApprovedExportState(val displayId: String, val canonicalFilename: String?, val expectedSha256: String?,
    val canAttach: Boolean, val ticket: ApprovedExportTicket? = null, val blocker: String? = null)

data class ApprovedExportReceipt(val canonicalFilename: String, val sha256: String, val byteCount: Int)

/** Destination must come from this screen's fresh ACTION_CREATE_DOCUMENT result. */
internal interface CreatedExportDestination {
    fun openOutput(): OutputStream
    fun openInput(): InputStream
    fun deleteCreated(): Boolean
    fun verifyCanonicalName(expected: String) { }
}

internal class AndroidCreatedExportDestination(private val resolver: ContentResolver, private val uri: Uri) : CreatedExportDestination {
    init { require(uri.scheme == "content") { "Document provider URI required" } }
    override fun openOutput() = requireNotNull(resolver.openOutputStream(uri, "wt")) { "Cannot open the chosen destination" }
    override fun openInput() = requireNotNull(resolver.openInputStream(uri)) { "Cannot verify the saved destination" }
    override fun deleteCreated(): Boolean = DocumentsContract.deleteDocument(resolver, uri)
    override fun verifyCanonicalName(expected: String) {
        val actual = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
        require(actual == expected) { "Use the canonical filename $expected. The provider returned a different name." }
    }
}

class AndroidApprovedExportService(private val kb: TerritoryKnowledgeBase, private val artifacts: AndroidPdfArtifactService) {
    companion object { const val MAX_APPROVED_BYTES = 16 * 1024 * 1024 }

    private fun authorize(id: String): PdfArtifactAuthorization {
        val slot = kb.assignments[id] ?: error("Unknown territory assignment")
        val authorization = PdfArtifactBoundary.authorizeExactApprovedPassthrough(kb, id, slot.canonicalFilename, slot.referenceSha256)
        require(authorization.action == PdfArtifactAction.EXACT_APPROVED_PASSTHROUGH &&
            authorization.fieldReleaseState == PdfFieldReleaseState.ALLOWED) { authorization.reason }
        return authorization
    }

    @Synchronized
    fun state(id: String): ApprovedExportState {
        val slot = kb.assignments[id]
        val auth = runCatching { authorize(id) }
        if (auth.isFailure) return ApprovedExportState(id, slot?.canonicalFilename, slot?.referenceSha256,
            false, blocker = auth.exceptionOrNull()?.message)
        return try {
            val stored = artifacts.resolveExactApproved(id)
            if (stored == null) ApprovedExportState(id, slot!!.canonicalFilename, slot.referenceSha256, true,
                blocker = "Attach the exact approved PDF to save a copy.")
            else {
                require(stored.file.length() in 1..MAX_APPROVED_BYTES.toLong()) { "Approved PDF is empty or exceeds the 16 MiB export limit" }
                ApprovedExportState(id, stored.canonicalFilename, stored.sha256, true,
                    ApprovedExportTicket(id, stored.canonicalFilename, stored.sha256, kb.revision, stored.file.length().toInt()))
            }
        } catch (failure: Exception) {
            ApprovedExportState(id, slot!!.canonicalFilename, slot.referenceSha256, true,
                blocker = failure.message ?: "Approved copy is unavailable; attach it again.")
        }
    }

    @Synchronized
    fun attach(id: String, input: InputStream): ApprovedExportState {
        val auth = authorize(id)
        val bytes = readBounded(input, MAX_APPROVED_BYTES)
        require(bytes.isNotEmpty() && BundleIntegrity.sha256(bytes.inputStream()) == auth.expectedSha256) {
            "Selected PDF does not match the exact approved hash. The existing copy was preserved."
        }
        authorize(id)
        artifacts.importExactApproved(id, bytes.inputStream())
        return state(id)
    }

    @Synchronized
    fun verifyCurrent(ticket: ApprovedExportTicket) {
        require(state(ticket.displayId).ticket == ticket) { "Approved card changed or is unavailable. Recheck before saving." }
    }

    @Synchronized
    internal fun exportCreated(ticket: ApprovedExportTicket?, destination: CreatedExportDestination): ApprovedExportReceipt {
        try {
            requireNotNull(ticket) { "Export session expired. Choose Save a copy again." }
            verifyCurrent(ticket)
            val stored = requireNotNull(artifacts.resolveExactApproved(ticket.displayId))
            val bytes = stored.file.inputStream().use { readBounded(it, MAX_APPROVED_BYTES) }
            require(bytes.size == ticket.byteCount && BundleIntegrity.sha256(bytes.inputStream()) == ticket.sha256) { "Approved PDF bytes changed" }
            val auth = authorize(ticket.displayId)
            verifyCurrent(ticket)
            destination.openOutput().use { output ->
                ExactApprovedPdfPassthrough.copyVerified(auth, bytes.inputStream(), output)
                output.flush()
            }
            val readback = destination.openInput().use { readBounded(it, ticket.byteCount) }
            require(readback.size == ticket.byteCount && BundleIntegrity.sha256(readback.inputStream()) == ticket.sha256) {
                "Saved PDF did not match the approved original"
            }
            verifyCurrent(ticket)
            return ApprovedExportReceipt(ticket.canonicalFilename, ticket.sha256, ticket.byteCount)
        } catch (failure: Exception) {
            val deleted = runCatching { destination.deleteCreated() }.getOrDefault(false)
            val cleanup = if (deleted) "The new destination was removed." else "An incomplete file may remain at the chosen location. Delete it before retrying."
            throw IllegalStateException("Save failed: ${failure.message ?: "Document provider error"}. $cleanup", failure)
        }
    }

    private fun readBounded(input: InputStream, maximum: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size().toLong() + count <= maximum) { "PDF exceeds the permitted byte limit" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}

package com.koenterprises.territorycardstudio

import com.koenterprises.territorycardstudio.core.*
import java.io.File

/** Synthetic approval authority exists only inside androidTest. Real KB is never modified. */
internal class Phase2GExportFixture : AutoCloseable {
    val source = Phase2DBFixture(false)
    val id = source.identity.displayId
    val bytes: ByteArray
    val kb: TerritoryKnowledgeBase
    val artifacts: AndroidPdfArtifactService
    val service: AndroidApprovedExportService
    init {
        source.prepare(); check(source.build().error == null)
        bytes = source.state().front!!.file.readBytes()
        val sha = BundleIntegrity.sha256(bytes.inputStream())
        val slot = source.slot.copy(needsNewCard = false, newCardApproved = true, status = "approved",
            fieldReleaseAllowedForExactArtifact = true, referenceSha256 = sha)
        val role = source.kb.referenceRoles.getValue(slot.referenceFile).copy(role = "exact_assignment_reference",
            displayId = id, sha256 = sha, fieldReleaseAllowed = true)
        kb = source.kb.copy(assignments = source.kb.assignments + (id to slot),
            referenceRoles = source.kb.referenceRoles + (slot.referenceFile to role))
        artifacts = AndroidPdfArtifactService(kb, File(source.root, "approved-test"),
            source.app.assets.open("territory/render-authority/Canonical-New-Designed-Template-R48.pdf").use { it.readBytes() })
        service = AndroidApprovedExportService(kb, artifacts)
    }
    fun attach() = service.attach(id, bytes.inputStream())
    override fun close() = source.close()
}

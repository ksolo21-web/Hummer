package com.koenterprises.territorycardstudio.core

import java.security.MessageDigest
import java.util.Locale

enum class CanonicalPacketMode {
    REGULAR,
    LETTER_WRITING,
    TELEPHONE
}

enum class ExactApprovalState {
    EXPLICITLY_APPROVED,
    UNAPPROVED,
    REJECTED,
    INVALIDATED
}

data class CanonicalPacketManifest(
    val mode: CanonicalPacketMode,
    val displayId: String,
    val canonicalFilename: String,
    val candidateVersion: String,
    val knowledgeBaseRevision: String,
    val assignmentAuthoritySha256: String,
    val frontPdfSha256: String,
    val packetPdfSha256: String,
    val sourceInventorySha256: String?,
    val pageCount: Int,
    val pageRoles: List<TerritoryPdfPageRole>,
    val printReady: Boolean
) {
    fun canonicalSha256(): String = sha256CrossMode(buildString {
        append("mode=").append(mode.name).append('\n')
        append("display_id=").append(displayId).append('\n')
        append("canonical_filename=").append(canonicalFilename).append('\n')
        append("candidate_version=").append(candidateVersion).append('\n')
        append("knowledge_base_revision=").append(knowledgeBaseRevision).append('\n')
        append("assignment_authority_sha256=").append(assignmentAuthoritySha256).append('\n')
        append("front_pdf_sha256=").append(frontPdfSha256).append('\n')
        append("packet_pdf_sha256=").append(packetPdfSha256).append('\n')
        append("source_inventory_sha256=").append(sourceInventorySha256 ?: "").append('\n')
        append("page_count=").append(pageCount).append('\n')
        append("page_roles=").append(pageRoles.joinToString(",") { it.name }).append('\n')
        append("print_ready=").append(printReady).append('\n')
    }.toByteArray(Charsets.UTF_8))
}

data class ExactPacketApprovalReceipt(
    val manifestSha256: String,
    val mode: CanonicalPacketMode,
    val displayId: String,
    val canonicalFilename: String,
    val candidateVersion: String,
    val packetPdfSha256: String,
    val approvalState: ExactApprovalState,
    val approvedBy: String,
    val approvedAtUtc: String
)

data class CrossModePacketValidation(
    val passed: Boolean,
    val errors: List<String>,
    val manifest: CanonicalPacketManifest?
)

object CrossModePacketValidator {
    private const val PAGE_W = "768"
    private const val PAGE_H = "480.5"
    private const val MAX_SIDE_BYTES = 300 * 1024
    private const val MAX_PACKET_BYTES = 600 * 1024
    private val sha256Pattern = Regex("^[0-9a-f]{64}$")

    fun validateRegular(
        frontPdf: ByteArray,
        frontArtifact: CandidatePdfArtifactReceipt,
        candidateVersion: String
    ): CrossModePacketValidation {
        val errors = mutableListOf<String>()
        validateCommonFront(frontPdf, frontArtifact, errors)
        if (candidateVersion.isBlank()) errors += "Candidate version is required"
        val raw = frontPdf.toString(Charsets.ISO_8859_1)
        val pageCount = latestPageCount(raw)
        if (pageCount != 1) errors += "Regular packet must contain exactly one FRONT_MAP page"
        if (raw.contains("% TCS_CANONICAL_FRONT_BACK_V1")) errors += "Regular packet unexpectedly contains generated Page 2 semantics"
        if (frontPdf.size > MAX_SIDE_BYTES) errors += "Regular packet exceeds 300 KB front-side limit"
        validatePrintReadiness(raw, expectedPageCount = 1, errors = errors)

        val manifest = if (errors.isEmpty()) CanonicalPacketManifest(
            mode = CanonicalPacketMode.REGULAR,
            displayId = frontArtifact.displayId,
            canonicalFilename = frontArtifact.canonicalFilename,
            candidateVersion = candidateVersion,
            knowledgeBaseRevision = frontArtifact.renderValidation.knowledgeBaseRevision,
            assignmentAuthoritySha256 = frontArtifact.renderValidation.assignmentAuthoritySha256,
            frontPdfSha256 = frontArtifact.pdfSha256,
            packetPdfSha256 = sha256CrossMode(frontPdf),
            sourceInventorySha256 = null,
            pageCount = 1,
            pageRoles = listOf(TerritoryPdfPageRole.FRONT_MAP),
            printReady = true
        ) else null
        return CrossModePacketValidation(errors.isEmpty(), errors, manifest)
    }

    fun validateLetterWriting(
        frontPdf: ByteArray,
        frontArtifact: CandidatePdfArtifactReceipt,
        inventory: LetterWritingAddressInventory,
        locality: String,
        updated: String,
        candidateVersion: String
    ): CrossModePacketValidation {
        val errors = mutableListOf<String>()
        validateCommonFront(frontPdf, frontArtifact, errors)
        if (candidateVersion.isBlank()) errors += "Candidate version is required"
        val inventoryValidation = LetterWritingAddressInventoryValidator.validateForPage2(inventory)
        if (!inventoryValidation.passed) errors += inventoryValidation.errors.map { "Letter Writing: " + it }

        val result = if (errors.isEmpty()) runCatching {
            val spec = LetterWritingAddressInventoryValidator.toCanonicalBackSpec(inventory, locality, updated)
            val built = if (inventory.nonFieldFixture) {
                CanonicalFrontBackPdfAssembler.assembleNonFieldFixture(frontPdf, spec)
            } else {
                CanonicalFrontBackPdfAssembler.assembleCandidate(frontPdf, frontArtifact, spec)
            }
            spec to built
        }.getOrElse {
            errors += "Letter Writing packet assembly failed: " + (it.message ?: it::class.java.simpleName)
            null
        } else null

        val pair = result
        if (pair != null) {
            val spec = pair.first
            val built = pair.second
            if (built.validation.pageRoles != listOf(TerritoryPdfPageRole.FRONT_MAP, TerritoryPdfPageRole.BACK_ADDRESS_LIST)) {
                errors += "Letter Writing page order/roles drift"
            }
            validateTwoPagePacket(frontPdf, built.pdfBytes, errors)
            val manifest = if (errors.isEmpty()) CanonicalPacketManifest(
                mode = CanonicalPacketMode.LETTER_WRITING,
                displayId = frontArtifact.displayId,
                canonicalFilename = frontArtifact.canonicalFilename,
                candidateVersion = candidateVersion,
                knowledgeBaseRevision = frontArtifact.renderValidation.knowledgeBaseRevision,
                assignmentAuthoritySha256 = frontArtifact.renderValidation.assignmentAuthoritySha256,
                frontPdfSha256 = frontArtifact.pdfSha256,
                packetPdfSha256 = built.pdfSha256,
                sourceInventorySha256 = spec.sourceInventorySha256,
                pageCount = built.validation.pageCount,
                pageRoles = built.validation.pageRoles,
                printReady = true
            ) else null
            return CrossModePacketValidation(errors.isEmpty(), errors, manifest)
        }

        return CrossModePacketValidation(false, errors, null)
    }

    fun validateTelephone(
        frontPdf: ByteArray,
        frontArtifact: CandidatePdfArtifactReceipt,
        inventory: TelephoneTerritoryInventory,
        locality: String,
        updated: String,
        candidateVersion: String
    ): CrossModePacketValidation {
        val errors = mutableListOf<String>()
        validateCommonFront(frontPdf, frontArtifact, errors)
        if (candidateVersion.isBlank()) errors += "Candidate version is required"
        val inventoryValidation = TelephoneTerritoryInventoryValidator.validateForPage2(inventory)
        if (!inventoryValidation.passed) errors += inventoryValidation.errors.map { "Telephone: " + it }

        val result = if (errors.isEmpty()) runCatching {
            val spec = TelephoneTerritoryInventoryValidator.toCanonicalBackSpec(inventory, locality, updated)
            val built = if (inventory.nonFieldFixture) {
                CanonicalFrontBackPdfAssembler.assembleNonFieldFixture(frontPdf, spec)
            } else {
                CanonicalFrontBackPdfAssembler.assembleCandidate(frontPdf, frontArtifact, spec)
            }
            spec to built
        }.getOrElse {
            errors += "Telephone packet assembly failed: " + (it.message ?: it::class.java.simpleName)
            null
        } else null

        val pair = result
        if (pair != null) {
            val spec = pair.first
            val built = pair.second
            if (built.validation.pageRoles != listOf(TerritoryPdfPageRole.FRONT_MAP, TerritoryPdfPageRole.BACK_PHONE_LIST)) {
                errors += "Telephone page order/roles drift"
            }
            validateTwoPagePacket(frontPdf, built.pdfBytes, errors)
            val manifest = if (errors.isEmpty()) CanonicalPacketManifest(
                mode = CanonicalPacketMode.TELEPHONE,
                displayId = frontArtifact.displayId,
                canonicalFilename = frontArtifact.canonicalFilename,
                candidateVersion = candidateVersion,
                knowledgeBaseRevision = frontArtifact.renderValidation.knowledgeBaseRevision,
                assignmentAuthoritySha256 = frontArtifact.renderValidation.assignmentAuthoritySha256,
                frontPdfSha256 = frontArtifact.pdfSha256,
                packetPdfSha256 = built.pdfSha256,
                sourceInventorySha256 = spec.sourceInventorySha256,
                pageCount = built.validation.pageCount,
                pageRoles = built.validation.pageRoles,
                printReady = true
            ) else null
            return CrossModePacketValidation(errors.isEmpty(), errors, manifest)
        }

        return CrossModePacketValidation(false, errors, null)
    }

    fun validateExactApproval(
        manifest: CanonicalPacketManifest,
        approval: ExactPacketApprovalReceipt
    ): List<String> {
        val errors = mutableListOf<String>()
        if (approval.approvalState != ExactApprovalState.EXPLICITLY_APPROVED) errors += "Packet is not explicitly approved"
        if (approval.approvedBy.isBlank()) errors += "Approval actor is required"
        if (approval.approvedAtUtc.isBlank()) errors += "Approval timestamp is required"
        if (!sha256Pattern.matches(approval.manifestSha256)) errors += "Approval manifest SHA-256 is invalid"
        if (approval.manifestSha256 != manifest.canonicalSha256()) errors += "Approval is stale or bound to a different packet manifest"
        if (approval.mode != manifest.mode) errors += "Approval packet mode mismatch"
        if (approval.displayId != manifest.displayId) errors += "Approval territory identity mismatch"
        if (approval.canonicalFilename != manifest.canonicalFilename) errors += "Approval canonical filename mismatch"
        if (approval.candidateVersion != manifest.candidateVersion) errors += "Approval candidate version mismatch"
        if (approval.packetPdfSha256 != manifest.packetPdfSha256) errors += "Approval exact PDF hash mismatch"
        if (!manifest.printReady) errors += "Packet is not print ready"
        return errors
    }

    private fun validateCommonFront(
        frontPdf: ByteArray,
        frontArtifact: CandidatePdfArtifactReceipt,
        errors: MutableList<String>
    ) {
        val actualSha = sha256CrossMode(frontPdf)
        if (actualSha != frontArtifact.pdfSha256) errors += "Front PDF hash does not match artifact receipt"
        if (frontArtifact.displayId.isBlank()) errors += "Front display ID is blank"
        if (frontArtifact.canonicalFilename.isBlank()) errors += "Front canonical filename is blank"
        if (!sha256Pattern.matches(frontArtifact.renderValidation.assignmentAuthoritySha256)) errors += "Assignment authority SHA-256 is invalid"
        if (frontArtifact.renderValidation.knowledgeBaseRevision.isBlank()) errors += "Knowledge Base revision is blank"
        if (!frontArtifact.renderValidation.sourceTruthPassed) errors += "Source truth did not pass"
        if (!frontArtifact.renderValidation.liveGeometryVerificationPassed) errors += "Live geometry verification did not pass"
        if (!frontArtifact.renderValidation.topologyOverlapPassed) errors += "Duplicate/overlap validation did not pass"
        if (!frontArtifact.renderValidation.buildingValidationPassed) errors += "Building validation did not pass"
        if (!frontArtifact.renderValidation.labelValidationPassed) errors += "Label validation did not pass"
        val raw = frontPdf.toString(Charsets.ISO_8859_1)
        if (!raw.contains("card-display-id:" + frontArtifact.displayId) && !raw.contains("display_id=" + frontArtifact.displayId)) {
            errors += "Front PDF metadata identity mismatch"
        }
        if (!raw.contains("canonical-filename:" + frontArtifact.canonicalFilename) && !raw.contains("canonical_filename=" + frontArtifact.canonicalFilename)) {
            errors += "Front PDF metadata filename mismatch"
        }
    }

    private fun validateTwoPagePacket(frontPdf: ByteArray, packetPdf: ByteArray, errors: MutableList<String>) {
        if (frontPdf.size > MAX_SIDE_BYTES) errors += "Front side exceeds 300 KB"
        if (packetPdf.size > MAX_PACKET_BYTES) errors += "Combined packet exceeds 600 KB"
        val backContribution = packetPdf.size - frontPdf.size
        if (backContribution <= 0 || backContribution > MAX_SIDE_BYTES) errors += "Back side contribution exceeds canonical bounds"
        if (packetPdf.size < frontPdf.size || !packetPdf.copyOfRange(0, frontPdf.size).contentEquals(frontPdf)) {
            errors += "Exact front-byte prefix was not preserved"
        }
        val raw = packetPdf.toString(Charsets.ISO_8859_1)
        if (latestPageCount(raw) != 2) errors += "Two-page packet page count drift"
        validatePrintReadiness(raw, expectedPageCount = 2, errors = errors)
    }

    private fun validatePrintReadiness(raw: String, expectedPageCount: Int, errors: MutableList<String>) {
        if (!raw.startsWith("%PDF-")) errors += "Artifact is not a PDF"
        if (raw.contains("/Encrypt")) errors += "Print-ready packet must not be encrypted"
        val mediaBoxes = Regex("/MediaBox \\[ 0 0 " + PAGE_W + " " + Regex.escape(PAGE_H) + " \\]").findAll(raw).count()
        if (mediaBoxes < expectedPageCount) errors += "Canonical 768 x 480.5 pt page geometry is missing"
        if (!raw.contains("%%EOF")) errors += "PDF EOF marker missing"
    }

    private fun latestPageCount(raw: String): Int {
        val latest = Regex("19 0 obj\\s*<<\\s*/Count (\\d+) /Kids \\[ ([^]]+) ] /Type /Pages", RegexOption.DOT_MATCHES_ALL)
            .findAll(raw).lastOrNull()
        return latest?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }
}

private fun sha256CrossMode(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { String.format(Locale.US, "%02x", it) }

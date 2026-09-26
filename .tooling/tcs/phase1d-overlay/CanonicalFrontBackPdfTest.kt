package com.koenterprises.territorycardstudio.core

import java.io.File
import java.security.MessageDigest

object CanonicalFrontBackPdfTest {
    fun run(assets: File) {
        val template = File(assets, "render-authority/Canonical-New-Designed-Template-R48.pdf").readBytes()
        val frontSpec = CandidatePdfRendererTest.fullPlusDetailFixtureSpec()
        val front = CandidatePdfRenderer.renderNonFieldFixture(template, frontSpec)
        val addressPayload = (1..24).joinToString("\n") { "source-$it|${100 + it} Example Way Apt $it" }
        val sourceSha = sha256(addressPayload.toByteArray())
        val back = CanonicalAddressBackSpec(
            identity = frontSpec.identity,
            locality = frontSpec.locality,
            updated = frontSpec.updated,
            sourceInventorySha256 = sourceSha,
            sourceInventoryVerified = true,
            entries = (1..24).map { CanonicalAddressEntry("source-$it", "${100 + it} Example Way Apt $it") },
            nonFieldFixture = true
        )
        val first = CanonicalFrontBackPdfAssembler.assembleNonFieldFixture(front.pdfBytes, back)
        val second = CanonicalFrontBackPdfAssembler.assembleNonFieldFixture(front.pdfBytes, back)
        check(first.pdfBytes.contentEquals(second.pdfBytes)) { "Canonical front/back assembly is not byte deterministic" }
        check(first.validation.passed)
        check(first.validation.pageCount == 2)
        check(first.validation.pageRoles == listOf(TerritoryPdfPageRole.FRONT_MAP, TerritoryPdfPageRole.BACK_ADDRESS_LIST))
        check(first.pdfBytes.copyOfRange(0, front.pdfBytes.size).contentEquals(front.pdfBytes)) { "Front page bytes were not preserved as exact prefix" }
        val raw = first.pdfBytes.toString(Charsets.ISO_8859_1)
        check(raw.contains("% TCS_CANONICAL_FRONT_BACK_V1"))
        check(raw.contains("page_roles=FRONT_MAP,BACK_ADDRESS_LIST"))
        check(raw.contains("(101 Example Way Apt 1) Tj"))
        check(raw.contains("(124 Example Way Apt 24) Tj"))
        check(first.pdfBytes.size - front.pdfBytes.size <= 300 * 1024)

        val receipt = CandidatePdfArtifactReceipt(
            displayId = frontSpec.identity.displayId,
            canonicalFilename = frontSpec.identity.canonicalFilename,
            pdfSha256 = front.pdfSha256,
            renderValidation = CandidateRenderValidationReceipt(
                displayId = frontSpec.identity.displayId,
                canonicalFilename = frontSpec.identity.canonicalFilename,
                knowledgeBaseRevision = "fixture",
                assignmentAuthorityRole = "current_authoritative_assignment",
                assignmentAuthoritySha256 = "a".repeat(64),
                sourceTruthPassed = true,
                liveGeometryVerificationPassed = true,
                topologyOverlapPassed = true,
                buildingValidationPassed = true,
                labelValidationPassed = true,
                styleTokenSha256 = LockedPdfRendererAuthorityHashes.STYLE_TOKENS_SHA256,
                templatePdfSha256 = LockedPdfRendererAuthorityHashes.TEMPLATE_PDF_SHA256,
                rendererSha256 = LockedPdfRendererAuthorityHashes.RENDERER_SHA256,
                rendererContractSha256 = LockedPdfRendererAuthorityHashes.CONTRACT_SHA256,
                directionsBadgeSha256 = LockedPdfRendererAuthorityHashes.DIRECTIONS_BADGE_SHA256
            )
        )
        val productionBack = back.copy(nonFieldFixture = false)
        check(CanonicalFrontBackPdfAssembler.assembleCandidate(front.pdfBytes, receipt, productionBack).validation.passed)
        check(runCatching { CanonicalFrontBackPdfAssembler.assembleCandidate(front.pdfBytes, receipt, productionBack.copy(sourceInventoryVerified = false)) }.isFailure)
        check(runCatching { CanonicalFrontBackPdfAssembler.assembleNonFieldFixture(front.pdfBytes, back.copy(identity = TerritoryIdentity(991, TerritoryClass.Apartment, 'a'))) }.isFailure)
        check(runCatching { CanonicalFrontBackPdfAssembler.assembleNonFieldFixture(front.pdfBytes, back.copy(entries = back.entries + back.entries.first())) }.isFailure)
        check(runCatching { CanonicalFrontBackPdfAssembler.assembleNonFieldFixture(front.pdfBytes, back.copy(entries = emptyList())) }.isFailure)
        check(runCatching { CanonicalFrontBackPdfAssembler.assembleNonFieldFixture(first.pdfBytes, back) }.isFailure) { "Already-combined PDF unexpectedly accepted as front" }

        val tamperedOrder = first.pdfBytes.toString(Charsets.ISO_8859_1)
            .replace("/Count 2 /Kids [ 4 0 R 21 0 R ] /Type /Pages", "/Count 2 /Kids [ 21 0 R 4 0 R ] /Type /Pages")
            .toByteArray(Charsets.ISO_8859_1)
        val badOrder = CanonicalFrontBackPdfExactValidator.validate(tamperedOrder, back, front.pdfSha256, back.canonicalSha256())
        check(!badOrder.passed && badOrder.errors.any { "page count/order" in it }) { "Reordered front/back pages were not rejected" }

        val missingBack = CanonicalFrontBackPdfExactValidator.validate(front.pdfBytes, back, front.pdfSha256, back.canonicalSha256())
        check(!missingBack.passed) { "Missing back page unexpectedly passed canonical front/back validator" }

        println("canonical_front_back_pdf=PASS")
        println("canonical_front_back_fail_closed_mutations=7")
        println("canonical_front_back_pdf_sha256=${first.pdfSha256}")
        println("canonical_back_spec_sha256=${first.backSpecSha256}")
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

package com.koenterprises.territorycardstudio.core

import java.io.File
import java.security.MessageDigest

object CrossModePacketValidationRegression {
    private const val REGULAR_SHA = "d83dd01f11f5096f9b987417a86187061409bd5598fc73278db8a14bc54eb133"
    private const val LETTER_SHA = "c8678a6f66b1479a0808b07c0dab16383edbd8ba4ee8cdc7c417996101842232"
    private const val TELEPHONE_SHA = "01f704feb3e8253d443ceb3d373993b5dc57d41a7464a286e66e86fdacc9350e"
    private const val VERSION = "cross-mode-fixture-v1"

    fun run(assets: File) {
        val template = File(assets, "render-authority/Canonical-New-Designed-Template-R48.pdf").readBytes()

        val regularSpec = CandidatePdfRendererTest.fullPlusDetailFixtureSpec()
        val regularFirst = CandidatePdfRenderer.renderNonFieldFixture(template, regularSpec)
        val regularSecond = CandidatePdfRenderer.renderNonFieldFixture(template, regularSpec)
        check(regularFirst.pdfBytes.contentEquals(regularSecond.pdfBytes))
        check(regularFirst.pdfSha256 == REGULAR_SHA)
        val regularArtifact = artifactReceipt(regularSpec, regularFirst.pdfSha256)
        val regularValidation = CrossModePacketValidator.validateRegular(regularFirst.pdfBytes, regularArtifact, VERSION)
        check(regularValidation.passed) { regularValidation.errors.joinToString() }
        val regularManifest = requireNotNull(regularValidation.manifest)
        check(regularManifest.mode == CanonicalPacketMode.REGULAR)
        check(regularManifest.pageCount == 1)
        check(regularManifest.pageRoles == listOf(TerritoryPdfPageRole.FRONT_MAP))
        check(regularManifest.packetPdfSha256 == REGULAR_SHA)

        val letterInventory = letterInventory(regularSpec.identity)
        val letterValidationFirst = CrossModePacketValidator.validateLetterWriting(
            regularFirst.pdfBytes,
            regularArtifact,
            letterInventory,
            regularSpec.locality,
            regularSpec.updated,
            VERSION
        )
        val letterValidationSecond = CrossModePacketValidator.validateLetterWriting(
            regularFirst.pdfBytes,
            regularArtifact,
            letterInventory,
            regularSpec.locality,
            regularSpec.updated,
            VERSION
        )
        check(letterValidationFirst.passed) { letterValidationFirst.errors.joinToString() }
        check(letterValidationSecond.passed)
        val letterManifest = requireNotNull(letterValidationFirst.manifest)
        check(letterManifest == letterValidationSecond.manifest)
        check(letterManifest.packetPdfSha256 == LETTER_SHA)
        check(letterManifest.pageCount == 2)
        check(letterManifest.pageRoles == listOf(TerritoryPdfPageRole.FRONT_MAP, TerritoryPdfPageRole.BACK_ADDRESS_LIST))
        check(letterManifest.sourceInventorySha256 == LetterWritingAddressInventoryValidator.validateForPage2(letterInventory).inventorySha256)

        val telephoneSpec = regularSpec.copy(identity = TerritoryIdentity(996, TerritoryClass.Telephone, 'a'))
        val telephoneFrontFirst = CandidatePdfRenderer.renderNonFieldFixture(template, telephoneSpec)
        val telephoneFrontSecond = CandidatePdfRenderer.renderNonFieldFixture(template, telephoneSpec)
        check(telephoneFrontFirst.pdfBytes.contentEquals(telephoneFrontSecond.pdfBytes))
        val telephoneArtifact = artifactReceipt(telephoneSpec, telephoneFrontFirst.pdfSha256)
        val telephoneInventory = telephoneInventory(telephoneSpec.identity)
        val telephoneValidationFirst = CrossModePacketValidator.validateTelephone(
            telephoneFrontFirst.pdfBytes,
            telephoneArtifact,
            telephoneInventory,
            telephoneSpec.locality,
            telephoneSpec.updated,
            VERSION
        )
        val telephoneValidationSecond = CrossModePacketValidator.validateTelephone(
            telephoneFrontSecond.pdfBytes,
            telephoneArtifact,
            telephoneInventory,
            telephoneSpec.locality,
            telephoneSpec.updated,
            VERSION
        )
        check(telephoneValidationFirst.passed) { telephoneValidationFirst.errors.joinToString() }
        check(telephoneValidationSecond.passed)
        val telephoneManifest = requireNotNull(telephoneValidationFirst.manifest)
        check(telephoneManifest == telephoneValidationSecond.manifest)
        check(telephoneManifest.packetPdfSha256 == TELEPHONE_SHA)
        check(telephoneManifest.pageCount == 2)
        check(telephoneManifest.pageRoles == listOf(TerritoryPdfPageRole.FRONT_MAP, TerritoryPdfPageRole.BACK_PHONE_LIST))
        check(telephoneManifest.sourceInventorySha256 == TelephoneTerritoryInventoryValidator.validateForPage2(telephoneInventory).inventorySha256)

        listOf(regularManifest, letterManifest, telephoneManifest).forEach { manifest ->
            check(manifest.printReady)
            check(manifest.displayId.isNotBlank())
            check(manifest.canonicalFilename.startsWith("Territory - "))
            check(manifest.candidateVersion == VERSION)
            check(manifest.knowledgeBaseRevision == "fixture")
            check(manifest.assignmentAuthoritySha256 == "a".repeat(64))
            val approval = exactApproval(manifest)
            check(CrossModePacketValidator.validateExactApproval(manifest, approval).isEmpty())
        }

        val letterApproval = exactApproval(letterManifest)
        check(CrossModePacketValidator.validateExactApproval(
            letterManifest,
            letterApproval.copy(candidateVersion = "stale-version")
        ).any { "version mismatch" in it })
        check(CrossModePacketValidator.validateExactApproval(
            letterManifest,
            letterApproval.copy(packetPdfSha256 = "0".repeat(64))
        ).any { "PDF hash mismatch" in it })
        check(CrossModePacketValidator.validateExactApproval(
            letterManifest,
            letterApproval.copy(manifestSha256 = "0".repeat(64))
        ).any { "different packet manifest" in it })
        check(CrossModePacketValidator.validateExactApproval(
            letterManifest,
            letterApproval.copy(approvalState = ExactApprovalState.INVALIDATED)
        ).any { "not explicitly approved" in it })
        check(CrossModePacketValidator.validateExactApproval(
            telephoneManifest,
            letterApproval
        ).isNotEmpty())

        val overlapFailedArtifact = regularArtifact.copy(
            renderValidation = regularArtifact.renderValidation.copy(topologyOverlapPassed = false)
        )
        check(!CrossModePacketValidator.validateRegular(regularFirst.pdfBytes, overlapFailedArtifact, VERSION).passed)

        val duplicateLetter = letterInventory.copy(
            records = letterInventory.records + letterInventory.records.first().copy(recordId = "LW-DUP")
        )
        check(!CrossModePacketValidator.validateLetterWriting(
            regularFirst.pdfBytes,
            regularArtifact,
            duplicateLetter,
            regularSpec.locality,
            regularSpec.updated,
            VERSION
        ).passed)

        val duplicateTelephone = telephoneInventory.copy(
            records = telephoneInventory.records + telephoneInventory.records.first().copy(
                recordId = "TEL-DUP",
                streetAddress = "599 Example Way"
            )
        )
        check(!CrossModePacketValidator.validateTelephone(
            telephoneFrontFirst.pdfBytes,
            telephoneArtifact,
            duplicateTelephone,
            telephoneSpec.locality,
            telephoneSpec.updated,
            VERSION
        ).passed)

        val overflowLetter = letterInventory.copy(
            records = (1..241).map { n -> letterRecord(regularSpec.identity, n, "Overflow Way") }
        )
        check(!CrossModePacketValidator.validateLetterWriting(
            regularFirst.pdfBytes,
            regularArtifact,
            overflowLetter,
            regularSpec.locality,
            regularSpec.updated,
            VERSION
        ).passed)

        val overflowTelephone = telephoneInventory.copy(
            records = (1..241).map { n -> telephoneRecord(telephoneSpec.identity, n, "Overflow Way") }
        )
        check(!CrossModePacketValidator.validateTelephone(
            telephoneFrontFirst.pdfBytes,
            telephoneArtifact,
            overflowTelephone,
            telephoneSpec.locality,
            telephoneSpec.updated,
            VERSION
        ).passed)

        val tamperedRegular = regularFirst.pdfBytes.toString(Charsets.ISO_8859_1)
            .replace("/MediaBox [ 0 0 768 480.5 ]", "/MediaBox [ 0 0 767 480 ]")
            .toByteArray(Charsets.ISO_8859_1)
        val tamperedArtifact = regularArtifact.copy(pdfSha256 = sha256(tamperedRegular))
        val printFailure = CrossModePacketValidator.validateRegular(tamperedRegular, tamperedArtifact, VERSION)
        check(!printFailure.passed && printFailure.errors.any { "geometry" in it })

        val blankVersion = CrossModePacketValidator.validateRegular(regularFirst.pdfBytes, regularArtifact, "")
        check(!blankVersion.passed && blankVersion.errors.any { "Candidate version" in it })

        println("phase1d_d_cross_mode_validation=PASS")
        println("phase1d_d_modes=REGULAR,LETTER_WRITING,TELEPHONE")
        println("phase1d_d_regular_sha256=" + regularManifest.packetPdfSha256)
        println("phase1d_d_letter_sha256=" + letterManifest.packetPdfSha256)
        println("phase1d_d_telephone_sha256=" + telephoneManifest.packetPdfSha256)
        println("phase1d_d_exact_approval_mutations=5")
        println("phase1d_d_duplicate_overlap_mutations=3")
        println("phase1d_d_overflow_mutations=2")
        println("phase1d_d_print_version_mutations=2")
        println("phase1d_d_total_fail_closed_mutations=12")
    }

    private fun artifactReceipt(spec: CandidatePdfRenderSpec, pdfSha: String): CandidatePdfArtifactReceipt =
        CandidatePdfArtifactReceipt(
            displayId = spec.identity.displayId,
            canonicalFilename = spec.identity.canonicalFilename,
            pdfSha256 = pdfSha,
            renderValidation = CandidateRenderValidationReceipt(
                displayId = spec.identity.displayId,
                canonicalFilename = spec.identity.canonicalFilename,
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

    private fun exactApproval(manifest: CanonicalPacketManifest): ExactPacketApprovalReceipt =
        ExactPacketApprovalReceipt(
            manifestSha256 = manifest.canonicalSha256(),
            mode = manifest.mode,
            displayId = manifest.displayId,
            canonicalFilename = manifest.canonicalFilename,
            candidateVersion = manifest.candidateVersion,
            packetPdfSha256 = manifest.packetPdfSha256,
            approvalState = ExactApprovalState.EXPLICITLY_APPROVED,
            approvedBy = "synthetic-explicit-reviewer",
            approvedAtUtc = "2026-09-26T20:10:00Z"
        )

    private fun letterInventory(identity: TerritoryIdentity): LetterWritingAddressInventory {
        val sourceBytes = (1..18).joinToString("\n") { n ->
            "LW-" + n + "|" + (300 + (n - 1) / 6) + " Example Way|Apt " + n
        }.toByteArray()
        val sourceSha = sha256(sourceBytes)
        val provenance = LetterWritingAddressProvenance(
            provenanceId = "synthetic-authorized-source",
            sourceLabel = "Synthetic authorized Letter Writing inventory",
            sourceType = "USER_AUTHORIZED_FIXTURE",
            observedAtUtc = "2026-09-26T19:00:00Z",
            sourceSha256 = sourceSha,
            fieldUseEligible = true
        )
        return LetterWritingAddressInventory(
            identity = identity,
            assignmentAuthoritySha256 = "a".repeat(64),
            knowledgeBaseRevision = "fixture-kb-letter-writing",
            sourceTruthPassed = true,
            provenance = listOf(provenance),
            records = (1..18).map { n -> letterRecord(identity, n, "Example Way", provenance.provenanceId) },
            nonFieldFixture = true
        )
    }

    private fun letterRecord(
        identity: TerritoryIdentity,
        n: Int,
        streetName: String,
        provenanceId: String = "synthetic-authorized-source"
    ): LetterWritingAddressRecord =
        LetterWritingAddressRecord(
            recordId = "LW-" + n,
            territoryDisplayId = identity.displayId,
            streetAddress = (300 + (n - 1) / 6).toString() + " " + streetName,
            unit = "Apt " + n,
            city = "Rochester Hills",
            state = "MI",
            postalCode = "48309",
            buildingId = "fixture-building-" + (300 + (n - 1) / 6),
            verificationStatus = LetterWritingVerificationStatus.VERIFIED,
            verifiedAtUtc = "2026-09-26T19:00:00Z",
            boundaryStatus = LetterWritingBoundaryStatus.INSIDE_LOCKED_WORKING_AREA,
            boundaryEvidenceSha256 = "c".repeat(64),
            provenanceIds = listOf(provenanceId)
        )

    private fun telephoneInventory(identity: TerritoryIdentity): TelephoneTerritoryInventory {
        val sourceBytes = (1..15).joinToString("\n") { n ->
            val phone = if (n % 4 == 0) "UNAVAILABLE" else "248555" + (100 + n).toString().padStart(4, '0')
            "TEL-" + n + "|" + (500 + (n - 1) / 5) + " Example Way|Unit " + n + "|" + phone
        }.toByteArray()
        val sourceSha = sha256(sourceBytes)
        val provenance = TelephoneProvenance(
            provenanceId = "synthetic-authorized-telephone-source",
            sourceLabel = "Synthetic authorized Telephone inventory",
            sourceType = "USER_AUTHORIZED_FIXTURE",
            observedAtUtc = "2026-09-26T19:40:00Z",
            sourceSha256 = sourceSha,
            authorization = TelephoneSourceAuthorization.USER_PROVIDED,
            fieldUseEligible = true,
            telephoneUseAuthorized = true
        )
        return TelephoneTerritoryInventory(
            identity = identity,
            assignmentAuthoritySha256 = "a".repeat(64),
            knowledgeBaseRevision = "fixture-kb-telephone",
            sourceTruthPassed = true,
            provenance = listOf(provenance),
            records = (1..15).map { n -> telephoneRecord(identity, n, "Example Way", provenance.provenanceId) },
            nonFieldFixture = true
        )
    }

    private fun telephoneRecord(
        identity: TerritoryIdentity,
        n: Int,
        streetName: String,
        provenanceId: String = "synthetic-authorized-telephone-source"
    ): TelephoneTerritoryRecord {
        val unavailable = n % 4 == 0
        return TelephoneTerritoryRecord(
            recordId = "TEL-" + n,
            territoryDisplayId = identity.displayId,
            streetAddress = (500 + (n - 1) / 5).toString() + " " + streetName,
            unit = "Unit " + n,
            city = "Rochester Hills",
            state = "MI",
            postalCode = "48309",
            buildingId = "fixture-building-" + (500 + (n - 1) / 5),
            addressVerificationStatus = TelephoneRecordVerificationStatus.VERIFIED,
            addressVerifiedAtUtc = "2026-09-26T19:40:00Z",
            boundaryStatus = TelephoneBoundaryStatus.INSIDE_LOCKED_WORKING_AREA,
            boundaryEvidenceSha256 = "c".repeat(64),
            addressProvenanceIds = listOf(provenanceId),
            phoneState = if (unavailable) TelephoneNumberState.UNAVAILABLE else TelephoneNumberState.VERIFIED_NUMBER,
            phoneNumber = if (unavailable) null else "248555" + (100 + n).toString().padStart(4, '0'),
            phoneVerifiedAtUtc = "2026-09-26T19:40:00Z",
            phoneRecordBindingVerified = true,
            phoneProvenanceIds = listOf(provenanceId)
        )
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

fun main(args: Array<String>) {
    require(args.size == 1) { "usage: CrossModePacketValidationRegressionKt ASSETS_DIR" }
    CrossModePacketValidationRegression.run(File(args[0]))
}

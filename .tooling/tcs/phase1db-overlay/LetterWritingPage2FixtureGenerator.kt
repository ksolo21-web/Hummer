package com.koenterprises.territorycardstudio.core

import java.io.File
import java.security.MessageDigest

fun main(args: Array<String>) {
    require(args.size == 3) { "usage: LetterWritingPage2FixtureGenerator ASSETS_DIR FRONT.pdf COMBINED.pdf" }
    val assets = File(args[0])
    val frontOut = File(args[1])
    val combinedOut = File(args[2])
    val template = File(assets, "render-authority/Canonical-New-Designed-Template-R48.pdf").readBytes()
    val frontSpec = CandidatePdfRendererTest.fullPlusDetailFixtureSpec()
    val front = CandidatePdfRenderer.renderNonFieldFixture(template, frontSpec)

    val sourceBytes = (1..18).joinToString("\n") { n ->
        "LW-" + n + "|" + (300 + (n - 1) / 6) + " Example Way|Apt " + n
    }.toByteArray()
    val sourceSha = MessageDigest.getInstance("SHA-256").digest(sourceBytes).joinToString("") { "%02x".format(it) }
    val provenance = LetterWritingAddressProvenance(
        provenanceId = "synthetic-authorized-source",
        sourceLabel = "Synthetic authorized Letter Writing inventory",
        sourceType = "USER_AUTHORIZED_FIXTURE",
        observedAtUtc = "2026-09-26T19:00:00Z",
        sourceSha256 = sourceSha,
        fieldUseEligible = true
    )
    val inventory = LetterWritingAddressInventory(
        identity = frontSpec.identity,
        assignmentAuthoritySha256 = "a".repeat(64),
        knowledgeBaseRevision = "fixture-kb-letter-writing",
        sourceTruthPassed = true,
        provenance = listOf(provenance),
        records = (1..18).map { n ->
            LetterWritingAddressRecord(
                recordId = "LW-" + n,
                territoryDisplayId = frontSpec.identity.displayId,
                streetAddress = (300 + (n - 1) / 6).toString() + " Example Way",
                unit = "Apt " + n,
                city = "Rochester Hills",
                state = "MI",
                postalCode = "48309",
                buildingId = "fixture-building-" + (300 + (n - 1) / 6),
                verificationStatus = LetterWritingVerificationStatus.VERIFIED,
                verifiedAtUtc = "2026-09-26T19:00:00Z",
                boundaryStatus = LetterWritingBoundaryStatus.INSIDE_LOCKED_WORKING_AREA,
                provenanceIds = listOf(provenance.provenanceId)
            )
        },
        nonFieldFixture = true
    )
    val validation = LetterWritingAddressInventoryValidator.validateForPage2(inventory)
    check(validation.passed) { validation.errors.joinToString() }
    val back = LetterWritingAddressInventoryValidator.toCanonicalBackSpec(inventory, frontSpec.locality, frontSpec.updated)
    val combined = CanonicalFrontBackPdfAssembler.assembleNonFieldFixture(front.pdfBytes, back)
    frontOut.parentFile?.mkdirs()
    combinedOut.parentFile?.mkdirs()
    frontOut.writeBytes(front.pdfBytes)
    combinedOut.writeBytes(combined.pdfBytes)
    println("phase1d_b_letter_writing_fixture=PASS")
    println("inventory_count=" + inventory.records.size)
    println("inventory_sha256=" + validation.inventorySha256)
    println("combined_pdf_sha256=" + combined.pdfSha256)
    println("page_count=" + combined.validation.pageCount)
    println("page_roles=" + combined.validation.pageRoles.joinToString(","))
}

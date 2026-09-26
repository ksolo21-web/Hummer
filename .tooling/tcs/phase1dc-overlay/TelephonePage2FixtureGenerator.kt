package com.koenterprises.territorycardstudio.core

import java.io.File
import java.security.MessageDigest

fun main(args: Array<String>) {
    require(args.size == 3) { "usage: TelephonePage2FixtureGenerator ASSETS_DIR FRONT.pdf COMBINED.pdf" }
    val assets = File(args[0])
    val frontOut = File(args[1])
    val combinedOut = File(args[2])
    val template = File(assets, "render-authority/Canonical-New-Designed-Template-R48.pdf").readBytes()
    val frontSpec = CandidatePdfRendererTest.fullPlusDetailFixtureSpec().copy(
        identity = TerritoryIdentity(996, TerritoryClass.Telephone, 'a')
    )
    val front = CandidatePdfRenderer.renderNonFieldFixture(template, frontSpec)

    val sourceBytes = (1..15).joinToString("\n") { n ->
        val phone = if (n % 4 == 0) "UNAVAILABLE" else "248555" + (100 + n).toString().padStart(4, '0')
        "TEL-" + n + "|" + (500 + (n - 1) / 5) + " Example Way|Unit " + n + "|" + phone
    }.toByteArray()
    val sourceSha = MessageDigest.getInstance("SHA-256").digest(sourceBytes).joinToString("") { "%02x".format(it) }

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

    val inventory = TelephoneTerritoryInventory(
        identity = frontSpec.identity,
        assignmentAuthoritySha256 = "a".repeat(64),
        knowledgeBaseRevision = "fixture-kb-telephone",
        sourceTruthPassed = true,
        provenance = listOf(provenance),
        records = (1..15).map { n ->
            val unavailable = n % 4 == 0
            TelephoneTerritoryRecord(
                recordId = "TEL-" + n,
                territoryDisplayId = frontSpec.identity.displayId,
                streetAddress = (500 + (n - 1) / 5).toString() + " Example Way",
                unit = "Unit " + n,
                city = "Rochester Hills",
                state = "MI",
                postalCode = "48309",
                buildingId = "fixture-building-" + (500 + (n - 1) / 5),
                addressVerificationStatus = TelephoneRecordVerificationStatus.VERIFIED,
                addressVerifiedAtUtc = "2026-09-26T19:40:00Z",
                boundaryStatus = TelephoneBoundaryStatus.INSIDE_LOCKED_WORKING_AREA,
                boundaryEvidenceSha256 = "c".repeat(64),
                addressProvenanceIds = listOf(provenance.provenanceId),
                phoneState = if (unavailable) TelephoneNumberState.UNAVAILABLE else TelephoneNumberState.VERIFIED_NUMBER,
                phoneNumber = if (unavailable) null else "248555" + (100 + n).toString().padStart(4, '0'),
                phoneVerifiedAtUtc = "2026-09-26T19:40:00Z",
                phoneRecordBindingVerified = true,
                phoneProvenanceIds = listOf(provenance.provenanceId)
            )
        },
        nonFieldFixture = true
    )

    val validation = TelephoneTerritoryInventoryValidator.validateForPage2(inventory)
    check(validation.passed) { validation.errors.joinToString() }
    val back = TelephoneTerritoryInventoryValidator.toCanonicalBackSpec(inventory, frontSpec.locality, frontSpec.updated)
    val combined = CanonicalFrontBackPdfAssembler.assembleNonFieldFixture(front.pdfBytes, back)
    check(combined.validation.pageRoles == listOf(TerritoryPdfPageRole.FRONT_MAP, TerritoryPdfPageRole.BACK_PHONE_LIST))

    frontOut.parentFile?.mkdirs()
    combinedOut.parentFile?.mkdirs()
    frontOut.writeBytes(front.pdfBytes)
    combinedOut.writeBytes(combined.pdfBytes)

    println("phase1d_c_telephone_fixture=PASS")
    println("inventory_count=" + inventory.records.size)
    println("verified_phone_count=" + validation.verifiedPhoneCount)
    println("unavailable_phone_count=" + validation.unavailablePhoneCount)
    println("inventory_sha256=" + validation.inventorySha256)
    println("combined_pdf_sha256=" + combined.pdfSha256)
    println("page_count=" + combined.validation.pageCount)
    println("page_roles=" + combined.validation.pageRoles.joinToString(","))
}

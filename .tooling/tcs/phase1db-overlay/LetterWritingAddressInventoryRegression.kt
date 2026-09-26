package com.koenterprises.territorycardstudio.core

fun main() {
    val identity = TerritoryIdentity(993, TerritoryClass.Apartment, 'b')
    val provenance = listOf(
        LetterWritingAddressProvenance(
            provenanceId = "authorized-fixture",
            sourceLabel = "Synthetic authorized address inventory",
            sourceType = "USER_AUTHORIZED_FIXTURE",
            observedAtUtc = "2026-09-26T18:59:00Z",
            sourceSha256 = "1".repeat(64),
            fieldUseEligible = true
        ),
        LetterWritingAddressProvenance(
            provenanceId = "legacy-reference",
            sourceLabel = "Legacy reference only",
            sourceType = "LEGACY_REFERENCE",
            observedAtUtc = "2026-09-01T12:00:00Z",
            sourceSha256 = "2".repeat(64),
            fieldUseEligible = false
        )
    )

    fun record(
        id: String,
        street: String,
        unit: String? = null,
        status: LetterWritingVerificationStatus = LetterWritingVerificationStatus.VERIFIED,
        boundary: LetterWritingBoundaryStatus = LetterWritingBoundaryStatus.INSIDE_LOCKED_WORKING_AREA,
        territory: String = identity.displayId,
        conflicts: List<String> = emptyList(),
        provenanceIds: List<String> = listOf("authorized-fixture"),
        verifiedAt: String? = "2026-09-26T18:59:00Z"
    ) = LetterWritingAddressRecord(
        recordId = id,
        territoryDisplayId = territory,
        streetAddress = street,
        unit = unit,
        city = "Rochester Hills",
        state = "MI",
        postalCode = "48309",
        buildingId = "building-" + id,
        verificationStatus = status,
        verifiedAtUtc = verifiedAt,
        boundaryStatus = boundary,
        neighborTerritoryConflicts = conflicts,
        provenanceIds = provenanceIds
    )

    val base = LetterWritingAddressInventory(
        identity = identity,
        assignmentAuthoritySha256 = "a".repeat(64),
        knowledgeBaseRevision = "fixture-kb-r1",
        sourceTruthPassed = true,
        provenance = provenance,
        records = listOf(
            record("addr-1", "300 Example Way", "Apt 1"),
            record("addr-2", "300 Example Way", "Apt 2"),
            record("addr-3", "301 Example Way")
        ),
        nonFieldFixture = true
    )
    val first = LetterWritingAddressInventoryValidator.validateForPage2(base)
    val second = LetterWritingAddressInventoryValidator.validateForPage2(base)
    check(first.passed) { first.errors.joinToString() }
    check(first.inventorySha256 == second.inventorySha256)
    check(first.verifiedRecordCount == 3)

    val back = LetterWritingAddressInventoryValidator.toCanonicalBackSpec(base, "Rochester Hills", "9/26/2026")
    check(back.sourceInventorySha256 == first.inventorySha256)
    check(back.entries.size == 3)
    check(back.entries.first().addressLine.contains("300 Example Way"))
    check(back.nonFieldFixture)

    fun blocked(candidate: LetterWritingAddressInventory, expected: String) {
        val result = LetterWritingAddressInventoryValidator.validateForPage2(candidate)
        check(!result.passed) { "Expected blocked inventory: " + expected }
        check(result.errors.any { expected in it }) { "Missing expected failure " + expected + ": " + result.errors.joinToString() }
        check(runCatching {
            LetterWritingAddressInventoryValidator.toCanonicalBackSpec(candidate, "Rochester Hills", "9/26/2026")
        }.isFailure)
    }

    blocked(base.copy(sourceTruthPassed = false), "source-truth")
    blocked(base.copy(records = base.records.mapIndexed { i, r -> if (i == 0) r.copy(territoryDisplayId = "A992b") else r }), "territory assignment")
    blocked(base.copy(records = base.records.mapIndexed { i, r -> if (i == 0) r.copy(boundaryStatus = LetterWritingBoundaryStatus.AMBIGUOUS) else r }), "locked working area")
    blocked(base.copy(records = base.records.mapIndexed { i, r -> if (i == 0) r.copy(neighborTerritoryConflicts = listOf("A992b")) else r }), "neighboring-territory conflict")
    blocked(base.copy(records = base.records.mapIndexed { i, r -> if (i == 0) r.copy(verificationStatus = LetterWritingVerificationStatus.NEEDS_REVIEW) else r }), "not verified")
    blocked(base.copy(records = base.records.mapIndexed { i, r -> if (i == 0) r.copy(verifiedAtUtc = null) else r }), "verified timestamp")
    blocked(base.copy(records = base.records.mapIndexed { i, r -> if (i == 0) r.copy(provenanceIds = listOf("legacy-reference")) else r }), "field-use-eligible provenance")
    blocked(base.copy(records = base.records + base.records.first().copy(recordId = "addr-dup")), "Duplicate canonical mailing addresses")
    blocked(base.copy(records = base.records + base.records.first().copy(streetAddress = "302 Example Way")), "Duplicate address record IDs")

    val next = base.copy(
        previousInventorySha256 = base.canonicalSha256(),
        records = listOf(
            record("addr-1", "300 Example Way", "Apt 1"),
            record("addr-2", "300 Example Way", "Apt 22"),
            record("addr-4", "302 Example Way")
        )
    )
    val changes = LetterWritingInventoryDiff.between(base, next)
    check(changes.map { it.type to it.recordId }.toSet() == setOf(
        "REMOVED" to "addr-3",
        "ADDED" to "addr-4",
        "CHANGED" to "addr-2"
    ))
    val tracked = next.copy(changes = changes)
    val trackedValidation = LetterWritingAddressInventoryValidator.validateForPage2(tracked)
    check(trackedValidation.passed) { trackedValidation.errors.joinToString() }
    check(trackedValidation.inventorySha256 != first.inventorySha256)

    println("phase1d_b_letter_writing_inventory_contract=PASS")
    println("phase1d_b_verified_records=" + first.verifiedRecordCount)
    println("phase1d_b_fail_closed_mutations=9")
    println("phase1d_b_change_tracking=PASS")
    println("phase1d_b_inventory_sha256=" + first.inventorySha256)
}

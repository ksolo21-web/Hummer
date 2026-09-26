package com.koenterprises.territorycardstudio.core

object TelephoneTerritoryInventoryRegression {
    fun run() {
        val identity = TerritoryIdentity(996, TerritoryClass.Telephone, 'a')
        val provenance = listOf(
            TelephoneProvenance(
                provenanceId = "authorized-fixture",
                sourceLabel = "Synthetic authorized Telephone inventory",
                sourceType = "USER_AUTHORIZED_FIXTURE",
                observedAtUtc = "2026-09-26T19:30:00Z",
                sourceSha256 = "1".repeat(64),
                authorization = TelephoneSourceAuthorization.USER_PROVIDED,
                fieldUseEligible = true,
                telephoneUseAuthorized = true
            ),
            TelephoneProvenance(
                provenanceId = "reference-only",
                sourceLabel = "Synthetic reference-only record",
                sourceType = "REFERENCE_FIXTURE",
                observedAtUtc = "2026-09-01T12:00:00Z",
                sourceSha256 = "2".repeat(64),
                authorization = TelephoneSourceAuthorization.REFERENCE_ONLY,
                fieldUseEligible = false,
                telephoneUseAuthorized = false
            )
        )

        fun record(
            id: String,
            street: String,
            unit: String? = null,
            phoneState: TelephoneNumberState = TelephoneNumberState.VERIFIED_NUMBER,
            phone: String? = "2485550101",
            addressStatus: TelephoneRecordVerificationStatus = TelephoneRecordVerificationStatus.VERIFIED,
            boundary: TelephoneBoundaryStatus = TelephoneBoundaryStatus.INSIDE_LOCKED_WORKING_AREA,
            territory: String = identity.displayId,
            conflicts: List<String> = emptyList(),
            addressSources: List<String> = listOf("authorized-fixture"),
            phoneSources: List<String> = listOf("authorized-fixture"),
            addressVerifiedAt: String? = "2026-09-26T19:30:00Z",
            phoneVerifiedAt: String? = "2026-09-26T19:30:00Z",
            bindingVerified: Boolean = true,
            boundaryHash: String = "c".repeat(64)
        ) = TelephoneTerritoryRecord(
            recordId = id,
            territoryDisplayId = territory,
            streetAddress = street,
            unit = unit,
            city = "Rochester Hills",
            state = "MI",
            postalCode = "48309",
            buildingId = "building-" + id,
            addressVerificationStatus = addressStatus,
            addressVerifiedAtUtc = addressVerifiedAt,
            boundaryStatus = boundary,
            boundaryEvidenceSha256 = boundaryHash,
            neighborTerritoryConflicts = conflicts,
            addressProvenanceIds = addressSources,
            phoneState = phoneState,
            phoneNumber = phone,
            phoneVerifiedAtUtc = phoneVerifiedAt,
            phoneRecordBindingVerified = bindingVerified,
            phoneProvenanceIds = phoneSources
        )

        val base = TelephoneTerritoryInventory(
            identity = identity,
            assignmentAuthoritySha256 = "a".repeat(64),
            knowledgeBaseRevision = "fixture-kb-telephone-r1",
            sourceTruthPassed = true,
            provenance = provenance,
            records = listOf(
                record("tel-1", "400 Example Way", "Apt 1", phone = "2485550101"),
                record("tel-2", "400 Example Way", "Apt 2", phoneState = TelephoneNumberState.UNAVAILABLE, phone = null),
                record("tel-3", "401 Example Way", phone = "2485550102")
            ),
            nonFieldFixture = true
        )

        val first = TelephoneTerritoryInventoryValidator.validateForPage2(base)
        val second = TelephoneTerritoryInventoryValidator.validateForPage2(base)
        check(first.passed) { first.errors.joinToString() }
        check(first.inventorySha256 == second.inventorySha256)
        check(first.verifiedPhoneCount == 2)
        check(first.unavailablePhoneCount == 1)
        check(base.records[0].phoneDisplay() == "(248) 555-0101")
        check(base.records[1].phoneDisplay() == "UNAVAILABLE")

        val back = TelephoneTerritoryInventoryValidator.toCanonicalBackSpec(base, "Rochester Hills", "9/26/2026")
        check(back.backMode == CanonicalBackMode.PHONE_LIST)
        check(back.sourceInventorySha256 == first.inventorySha256)
        check(back.entries.size == 3)
        check(back.entries.first().addressLine.contains("(248) 555-0101"))
        check(back.entries[1].addressLine.contains("UNAVAILABLE"))

        fun blocked(candidate: TelephoneTerritoryInventory, expected: String) {
            val result = TelephoneTerritoryInventoryValidator.validateForPage2(candidate)
            check(!result.passed) { "Expected blocked inventory: " + expected }
            check(result.errors.any { expected in it }) { "Missing expected failure " + expected + ": " + result.errors.joinToString() }
            check(runCatching {
                TelephoneTerritoryInventoryValidator.toCanonicalBackSpec(candidate, "Rochester Hills", "9/26/2026")
            }.isFailure)
        }

        blocked(base.copy(sourceTruthPassed = false), "source-truth")
        blocked(base.copy(records = base.records.mapIndexed { i, r -> if (i == 0) r.copy(territoryDisplayId = "T995a") else r }), "territory assignment")
        blocked(base.copy(records = base.records.mapIndexed { i, r -> if (i == 0) r.copy(boundaryStatus = TelephoneBoundaryStatus.AMBIGUOUS) else r }), "locked working area")
        blocked(base.copy(records = base.records.mapIndexed { i, r -> if (i == 0) r.copy(boundaryEvidenceSha256 = "bad") else r }), "boundary/neighbor evidence SHA-256")
        blocked(base.copy(records = base.records.mapIndexed { i, r -> if (i == 0) r.copy(neighborTerritoryConflicts = listOf("T995a")) else r }), "neighboring-territory conflict")
        blocked(base.copy(records = base.records.mapIndexed { i, r -> if (i == 0) r.copy(addressVerificationStatus = TelephoneRecordVerificationStatus.NEEDS_REVIEW) else r }), "address is not verified")
        blocked(base.copy(records = base.records.mapIndexed { i, r -> if (i == 0) r.copy(addressVerifiedAtUtc = null) else r }), "address verification timestamp")
        blocked(base.copy(records = base.records.mapIndexed { i, r -> if (i == 0) r.copy(addressProvenanceIds = listOf("reference-only")) else r }), "field-use-eligible address provenance")
        blocked(base.copy(records = base.records.mapIndexed { i, r -> if (i == 0) r.copy(phoneNumber = null) else r }), "verified telephone number is missing")
        blocked(base.copy(records = base.records.mapIndexed { i, r -> if (i == 0) r.copy(phoneNumber = "55501") else r }), "telephone number format")
        blocked(base.copy(records = base.records.mapIndexed { i, r -> if (i == 0) r.copy(phoneVerifiedAtUtc = null) else r }), "telephone verification timestamp")
        blocked(base.copy(records = base.records.mapIndexed { i, r -> if (i == 1) r.copy(phoneNumber = "2485550199") else r }), "unavailable-number state")
        blocked(base.copy(records = base.records.mapIndexed { i, r -> if (i == 0) r.copy(phoneState = TelephoneNumberState.NEEDS_REVIEW) else r }), "still needs review")
        blocked(base.copy(records = base.records.mapIndexed { i, r -> if (i == 0) r.copy(phoneRecordBindingVerified = false) else r }), "not verified as belonging")
        blocked(base.copy(records = base.records.mapIndexed { i, r -> if (i == 0) r.copy(phoneProvenanceIds = listOf("reference-only")) else r }), "authorized/permitted telephone provenance")
        blocked(base.copy(records = base.records + record("tel-4", "402 Example Way", phone = "2485550101")), "Duplicate verified telephone numbers")
        blocked(base.copy(records = base.records + base.records.first().copy(recordId = "tel-dup")), "Duplicate canonical Telephone addresses")
        blocked(base.copy(records = base.records + base.records.first().copy(streetAddress = "403 Example Way")), "Duplicate telephone record IDs")

        val next = base.copy(
            previousInventorySha256 = base.canonicalSha256(),
            records = listOf(
                record("tel-1", "400 Example Way", "Apt 1", phone = "2485550103"),
                record("tel-2", "400 Example Way", "Apt 2", phoneState = TelephoneNumberState.UNAVAILABLE, phone = null),
                record("tel-4", "402 Example Way", phoneState = TelephoneNumberState.UNAVAILABLE, phone = null)
            )
        )
        val changes = TelephoneInventoryDiff.between(base, next)
        check(changes.map { it.type to it.recordId }.toSet() == setOf(
            "REMOVED" to "tel-3",
            "ADDED" to "tel-4",
            "CHANGED" to "tel-1"
        ))
        blocked(next.copy(previousInventorySha256 = null, changes = changes), "Change tracking requires previous inventory SHA-256")
        val tracked = next.copy(changes = changes)
        val trackedValidation = TelephoneTerritoryInventoryValidator.validateForPage2(tracked)
        check(trackedValidation.passed) { trackedValidation.errors.joinToString() }
        check(trackedValidation.inventorySha256 != first.inventorySha256)

        println("phase1d_c_telephone_inventory_contract=PASS")
        println("phase1d_c_verified_phone_count=" + first.verifiedPhoneCount)
        println("phase1d_c_unavailable_phone_count=" + first.unavailablePhoneCount)
        println("phase1d_c_fail_closed_mutations=19")
        println("phase1d_c_change_tracking=PASS")
        println("phase1d_c_inventory_sha256=" + first.inventorySha256)
    }
}

fun main() = TelephoneTerritoryInventoryRegression.run()

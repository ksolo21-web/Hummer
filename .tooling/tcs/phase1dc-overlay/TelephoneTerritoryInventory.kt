package com.koenterprises.territorycardstudio.core

import java.security.MessageDigest
import java.util.Locale

enum class TelephoneRecordVerificationStatus { VERIFIED, NEEDS_REVIEW, CONFLICT, UNVERIFIED }
enum class TelephoneBoundaryStatus { INSIDE_LOCKED_WORKING_AREA, OUTSIDE_LOCKED_WORKING_AREA, AMBIGUOUS }
enum class TelephoneNumberState { VERIFIED_NUMBER, UNAVAILABLE, NEEDS_REVIEW, CONFLICT }
enum class TelephoneSourceAuthorization { USER_PROVIDED, AUTHORIZED_RECORD, PERMITTED_SOURCE, REFERENCE_ONLY }

data class TelephoneProvenance(
    val provenanceId: String,
    val sourceLabel: String,
    val sourceType: String,
    val observedAtUtc: String,
    val sourceSha256: String,
    val authorization: TelephoneSourceAuthorization,
    val fieldUseEligible: Boolean,
    val telephoneUseAuthorized: Boolean
)

data class TelephoneTerritoryRecord(
    val recordId: String,
    val territoryDisplayId: String,
    val streetAddress: String,
    val unit: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postalCode: String? = null,
    val buildingId: String? = null,
    val addressVerificationStatus: TelephoneRecordVerificationStatus,
    val addressVerifiedAtUtc: String? = null,
    val boundaryStatus: TelephoneBoundaryStatus,
    val boundaryEvidenceSha256: String,
    val neighborTerritoryConflicts: List<String> = emptyList(),
    val addressProvenanceIds: List<String>,
    val phoneState: TelephoneNumberState,
    val phoneNumber: String? = null,
    val phoneVerifiedAtUtc: String? = null,
    val phoneRecordBindingVerified: Boolean,
    val phoneProvenanceIds: List<String>
) {
    fun canonicalAddressLine(): String = buildList {
        add(streetAddress.trim())
        unit?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
        val locality = buildString {
            city?.trim()?.takeIf { it.isNotEmpty() }?.let { append(it) }
            state?.trim()?.takeIf { it.isNotEmpty() }?.let {
                if (isNotEmpty()) append(", ")
                append(it)
            }
            postalCode?.trim()?.takeIf { it.isNotEmpty() }?.let {
                if (isNotEmpty()) append(' ')
                append(it)
            }
        }
        if (locality.isNotEmpty()) add(locality)
    }.joinToString(", ")

    fun normalizedAddressKey(): String = canonicalAddressLine()
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    fun normalizedPhoneDigits(): String? = phoneNumber?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() }

    fun phoneDisplay(): String = when (phoneState) {
        TelephoneNumberState.UNAVAILABLE -> "UNAVAILABLE"
        TelephoneNumberState.VERIFIED_NUMBER -> formatTelephoneNumber(requireNotNull(normalizedPhoneDigits()))
        TelephoneNumberState.NEEDS_REVIEW -> "NEEDS REVIEW"
        TelephoneNumberState.CONFLICT -> "CONFLICT"
    }

    fun canonicalPageLine(): String = canonicalAddressLine() + " | " + phoneDisplay()
    fun normalizedRecordKey(): String = normalizedAddressKey() + "|" + phoneState.name + "|" + (normalizedPhoneDigits() ?: "")
}

data class TelephoneInventoryChange(
    val type: String,
    val recordId: String,
    val beforeRecordKey: String? = null,
    val afterRecordKey: String? = null
)

data class TelephoneTerritoryInventory(
    val identity: TerritoryIdentity,
    val assignmentAuthoritySha256: String,
    val knowledgeBaseRevision: String,
    val sourceTruthPassed: Boolean,
    val provenance: List<TelephoneProvenance>,
    val records: List<TelephoneTerritoryRecord>,
    val previousInventorySha256: String? = null,
    val changes: List<TelephoneInventoryChange> = emptyList(),
    val nonFieldFixture: Boolean = false
) {
    fun canonicalSha256(): String = sha256Telephone(buildString {
        append("display_id=").append(identity.displayId).append('\n')
        append("canonical_filename=").append(identity.canonicalFilename).append('\n')
        append("assignment_authority_sha256=").append(assignmentAuthoritySha256).append('\n')
        append("knowledge_base_revision=").append(knowledgeBaseRevision).append('\n')
        append("source_truth_passed=").append(sourceTruthPassed).append('\n')
        append("previous_inventory_sha256=").append(previousInventorySha256 ?: "").append('\n')
        append("non_field_fixture=").append(nonFieldFixture).append('\n')
        provenance.sortedBy { it.provenanceId }.forEach { p ->
            append("provenance=").append(p.provenanceId).append('|').append(p.sourceLabel).append('|')
                .append(p.sourceType).append('|').append(p.observedAtUtc).append('|').append(p.sourceSha256).append('|')
                .append(p.authorization.name).append('|').append(p.fieldUseEligible).append('|').append(p.telephoneUseAuthorized).append('\n')
        }
        records.sortedBy { it.recordId }.forEach { r ->
            append("record=").append(r.recordId).append('|').append(r.territoryDisplayId).append('|')
                .append(r.streetAddress.trim()).append('|').append(r.unit?.trim() ?: "").append('|')
                .append(r.city?.trim() ?: "").append('|').append(r.state?.trim() ?: "").append('|')
                .append(r.postalCode?.trim() ?: "").append('|').append(r.buildingId?.trim() ?: "").append('|')
                .append(r.addressVerificationStatus.name).append('|').append(r.addressVerifiedAtUtc ?: "").append('|')
                .append(r.boundaryStatus.name).append('|').append(r.boundaryEvidenceSha256).append('|')
                .append(r.phoneState.name).append('|').append(r.normalizedPhoneDigits() ?: "").append('|')
                .append(r.phoneVerifiedAtUtc ?: "").append('|').append(r.phoneRecordBindingVerified).append('|')
            r.neighborTerritoryConflicts.sorted().forEach { append("neighbor:").append(it).append('|') }
            r.addressProvenanceIds.sorted().forEach { append("address_source:").append(it).append('|') }
            r.phoneProvenanceIds.sorted().forEach { append("phone_source:").append(it).append('|') }
            append('\n')
        }
        changes.sortedWith(compareBy<TelephoneInventoryChange>({ it.type }, { it.recordId })).forEach { c ->
            append("change=").append(c.type).append('|').append(c.recordId).append('|')
                .append(c.beforeRecordKey ?: "").append('|').append(c.afterRecordKey ?: "").append('\n')
        }
    }.toByteArray(Charsets.UTF_8))
}

data class TelephoneInventoryValidation(
    val passed: Boolean,
    val errors: List<String>,
    val verifiedPhoneCount: Int,
    val unavailablePhoneCount: Int,
    val duplicateAddressKeys: List<String>,
    val duplicatePhoneDigits: List<String>,
    val inventorySha256: String
)

object TelephoneTerritoryInventoryValidator {
    private val sha256Pattern = Regex("^[0-9a-f]{64}$")
    private val statePattern = Regex("^[A-Z]{2}$")
    private val zipPattern = Regex("^\\d{5}(-\\d{4})?$")
    private val allowedPhoneSources = setOf(
        TelephoneSourceAuthorization.USER_PROVIDED,
        TelephoneSourceAuthorization.AUTHORIZED_RECORD,
        TelephoneSourceAuthorization.PERMITTED_SOURCE
    )

    fun validateForPage2(inventory: TelephoneTerritoryInventory): TelephoneInventoryValidation {
        val errors = mutableListOf<String>()
        if (inventory.knowledgeBaseRevision.isBlank()) errors += "Knowledge Base revision is required"
        if (!sha256Pattern.matches(inventory.assignmentAuthoritySha256)) errors += "Assignment authority SHA-256 is invalid"
        if (!inventory.sourceTruthPassed) errors += "Locked source-truth reconciliation did not pass"
        if (inventory.records.isEmpty()) errors += "Telephone inventory is empty"
        if (inventory.previousInventorySha256 != null && !sha256Pattern.matches(inventory.previousInventorySha256)) errors += "Previous inventory SHA-256 is invalid"

        val provenanceById = inventory.provenance.associateBy { it.provenanceId }
        if (provenanceById.size != inventory.provenance.size) errors += "Duplicate provenance IDs are forbidden"
        inventory.provenance.forEach { p ->
            if (p.provenanceId.isBlank()) errors += "Provenance ID is blank"
            if (p.sourceLabel.isBlank()) errors += p.provenanceId + ": source label is blank"
            if (p.sourceType.isBlank()) errors += p.provenanceId + ": source type is blank"
            if (p.observedAtUtc.isBlank()) errors += p.provenanceId + ": observed timestamp is blank"
            if (!sha256Pattern.matches(p.sourceSha256)) errors += p.provenanceId + ": source SHA-256 is invalid"
        }

        val ids = inventory.records.map { it.recordId }
        if (ids.any { it.isBlank() }) errors += "Telephone record IDs must be nonblank"
        if (ids.distinct().size != ids.size) errors += "Duplicate telephone record IDs are forbidden"

        val addressKeys = inventory.records.map { it.normalizedAddressKey() }
        val duplicateAddressKeys = addressKeys.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted()
        if (duplicateAddressKeys.isNotEmpty()) errors += "Duplicate canonical Telephone addresses are forbidden: " + duplicateAddressKeys.joinToString()

        val phoneDigits = inventory.records.filter { it.phoneState == TelephoneNumberState.VERIFIED_NUMBER }.mapNotNull { it.normalizedPhoneDigits() }
        val duplicatePhoneDigits = phoneDigits.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted()
        if (duplicatePhoneDigits.isNotEmpty()) errors += "Duplicate verified telephone numbers are forbidden: " + duplicatePhoneDigits.joinToString()

        inventory.records.forEach { r ->
            if (r.territoryDisplayId != inventory.identity.displayId) errors += r.recordId + ": territory assignment does not match " + inventory.identity.displayId
            if (r.streetAddress.isBlank()) errors += r.recordId + ": street address is blank"
            if (r.addressVerificationStatus != TelephoneRecordVerificationStatus.VERIFIED) errors += r.recordId + ": address is not verified"
            if (r.addressVerifiedAtUtc.isNullOrBlank()) errors += r.recordId + ": address verification timestamp is required"
            if (r.boundaryStatus != TelephoneBoundaryStatus.INSIDE_LOCKED_WORKING_AREA) errors += r.recordId + ": address is not confirmed inside locked working area"
            if (!sha256Pattern.matches(r.boundaryEvidenceSha256)) errors += r.recordId + ": boundary/neighbor evidence SHA-256 is invalid"
            if (r.neighborTerritoryConflicts.isNotEmpty()) errors += r.recordId + ": unresolved neighboring-territory conflict"

            validateSources(r.recordId, "address", r.addressProvenanceIds, provenanceById, false, errors)
            validateSources(r.recordId, "telephone", r.phoneProvenanceIds, provenanceById, true, errors)

            r.state?.let { if (it.isNotBlank() && !statePattern.matches(it.trim().uppercase(Locale.US))) errors += r.recordId + ": invalid state code" }
            r.postalCode?.let { if (it.isNotBlank() && !zipPattern.matches(it.trim())) errors += r.recordId + ": invalid ZIP/postal code" }
            if (!r.phoneRecordBindingVerified) errors += r.recordId + ": telephone status is not verified as belonging to this address record"

            when (r.phoneState) {
                TelephoneNumberState.VERIFIED_NUMBER -> {
                    val digits = r.normalizedPhoneDigits()
                    if (digits == null) errors += r.recordId + ": verified telephone number is missing"
                    else if (!validPhoneDigits(digits)) errors += r.recordId + ": verified telephone number format is invalid"
                    if (r.phoneVerifiedAtUtc.isNullOrBlank()) errors += r.recordId + ": telephone verification timestamp is required"
                }
                TelephoneNumberState.UNAVAILABLE -> {
                    if (!r.phoneNumber.isNullOrBlank()) errors += r.recordId + ": unavailable-number state must not carry a telephone number"
                    if (r.phoneVerifiedAtUtc.isNullOrBlank()) errors += r.recordId + ": unavailable-number status requires verification timestamp"
                }
                TelephoneNumberState.NEEDS_REVIEW -> errors += r.recordId + ": telephone number/status still needs review"
                TelephoneNumberState.CONFLICT -> errors += r.recordId + ": telephone number/status has unresolved conflict"
            }
        }

        if (inventory.changes.isNotEmpty() && inventory.previousInventorySha256 == null) errors += "Change tracking requires previous inventory SHA-256"
        val currentIds = inventory.records.mapTo(linkedSetOf()) { it.recordId }
        inventory.changes.forEach { c ->
            if (c.type !in setOf("ADDED", "REMOVED", "CHANGED")) errors += c.recordId + ": invalid change type " + c.type
            if (c.type != "REMOVED" && c.recordId !in currentIds) errors += c.recordId + ": change record is not present in current inventory"
            if (c.type == "CHANGED" && (c.beforeRecordKey.isNullOrBlank() || c.afterRecordKey.isNullOrBlank())) errors += c.recordId + ": CHANGED entry requires before/after record keys"
        }

        val inventorySha = inventory.canonicalSha256()
        return TelephoneInventoryValidation(
            passed = errors.isEmpty(),
            errors = errors,
            verifiedPhoneCount = inventory.records.count { it.phoneState == TelephoneNumberState.VERIFIED_NUMBER },
            unavailablePhoneCount = inventory.records.count { it.phoneState == TelephoneNumberState.UNAVAILABLE },
            duplicateAddressKeys = duplicateAddressKeys,
            duplicatePhoneDigits = duplicatePhoneDigits,
            inventorySha256 = inventorySha
        )
    }

    fun toCanonicalBackSpec(inventory: TelephoneTerritoryInventory, locality: String, updated: String): CanonicalAddressBackSpec {
        val validation = validateForPage2(inventory)
        require(validation.passed) { "Telephone Page 2 inventory validation failed: " + validation.errors.joinToString() }
        return CanonicalAddressBackSpec(
            identity = inventory.identity,
            locality = locality,
            updated = updated,
            sourceInventorySha256 = validation.inventorySha256,
            sourceInventoryVerified = true,
            entries = inventory.records.sortedWith(
                compareBy<TelephoneTerritoryRecord>(
                    { naturalTelephoneSortKey(it.streetAddress) },
                    { naturalTelephoneSortKey(it.unit ?: "") },
                    { it.recordId }
                )
            ).map { CanonicalAddressEntry(it.recordId, it.canonicalPageLine()) },
            nonFieldFixture = inventory.nonFieldFixture,
            backMode = CanonicalBackMode.PHONE_LIST
        )
    }

    private fun validateSources(
        recordId: String,
        role: String,
        ids: List<String>,
        provenanceById: Map<String, TelephoneProvenance>,
        requireTelephoneAuthorization: Boolean,
        errors: MutableList<String>
    ) {
        if (ids.isEmpty()) {
            errors += recordId + ": " + role + " provenance is required"
            return
        }
        if (ids.distinct().size != ids.size) errors += recordId + ": duplicate " + role + " provenance bindings"
        val missing = ids.filter { it !in provenanceById }
        if (missing.isNotEmpty()) errors += recordId + ": unknown " + role + " provenance IDs " + missing.joinToString()
        val bound = ids.mapNotNull(provenanceById::get)
        if (bound.none { it.fieldUseEligible }) errors += recordId + ": no field-use-eligible " + role + " provenance"
        if (requireTelephoneAuthorization) {
            if (bound.none { it.telephoneUseAuthorized && it.authorization in allowedPhoneSources }) errors += recordId + ": no authorized/permitted telephone provenance"
            if (bound.any { !it.telephoneUseAuthorized || it.authorization !in allowedPhoneSources }) errors += recordId + ": telephone provenance includes non-authorized source"
        }
    }

    private fun validPhoneDigits(digits: String): Boolean = digits.length == 10 || (digits.length == 11 && digits.startsWith("1"))
}

object TelephoneInventoryDiff {
    fun between(previous: TelephoneTerritoryInventory, current: TelephoneTerritoryInventory): List<TelephoneInventoryChange> {
        require(previous.identity == current.identity) { "Cannot diff Telephone inventories for different territories" }
        val before = previous.records.associateBy { it.recordId }
        val after = current.records.associateBy { it.recordId }
        val out = mutableListOf<TelephoneInventoryChange>()
        (before.keys - after.keys).sorted().forEach { id -> out += TelephoneInventoryChange("REMOVED", id, before.getValue(id).normalizedRecordKey(), null) }
        (after.keys - before.keys).sorted().forEach { id -> out += TelephoneInventoryChange("ADDED", id, null, after.getValue(id).normalizedRecordKey()) }
        (before.keys intersect after.keys).sorted().forEach { id ->
            val a = before.getValue(id).normalizedRecordKey()
            val b = after.getValue(id).normalizedRecordKey()
            if (a != b) out += TelephoneInventoryChange("CHANGED", id, a, b)
        }
        return out
    }
}

private fun naturalTelephoneSortKey(value: String): String =
    Regex("\\d+|\\D+").findAll(value.trim().lowercase(Locale.US)).joinToString("") { part ->
        val token = part.value
        if (token.all { it.isDigit() }) token.padStart(12, '0') else token
    }

private fun formatTelephoneNumber(digits: String): String = when {
    digits.length == 10 -> "(" + digits.substring(0,3) + ") " + digits.substring(3,6) + "-" + digits.substring(6)
    digits.length == 11 && digits.startsWith("1") -> "+1 " + digits.substring(1,4) + "-" + digits.substring(4,7) + "-" + digits.substring(7)
    else -> digits
}

private fun sha256Telephone(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

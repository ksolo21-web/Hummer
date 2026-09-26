package com.koenterprises.territorycardstudio.core

import java.security.MessageDigest
import java.util.Locale

enum class LetterWritingVerificationStatus {
    VERIFIED,
    NEEDS_REVIEW,
    CONFLICT,
    UNVERIFIED
}

enum class LetterWritingBoundaryStatus {
    INSIDE_LOCKED_WORKING_AREA,
    OUTSIDE_LOCKED_WORKING_AREA,
    AMBIGUOUS
}

data class LetterWritingAddressProvenance(
    val provenanceId: String,
    val sourceLabel: String,
    val sourceType: String,
    val observedAtUtc: String,
    val sourceSha256: String,
    val fieldUseEligible: Boolean
)

data class LetterWritingAddressRecord(
    val recordId: String,
    val territoryDisplayId: String,
    val streetAddress: String,
    val unit: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postalCode: String? = null,
    val buildingId: String? = null,
    val verificationStatus: LetterWritingVerificationStatus,
    val verifiedAtUtc: String? = null,
    val boundaryStatus: LetterWritingBoundaryStatus,
    val boundaryEvidenceSha256: String,
    val neighborTerritoryConflicts: List<String> = emptyList(),
    val provenanceIds: List<String>
) {
    fun canonicalMailingLine(): String = buildList {
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

    fun normalizedMailingKey(): String = canonicalMailingLine()
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
}

data class LetterWritingInventoryChange(
    val type: String,
    val recordId: String,
    val beforeMailingKey: String? = null,
    val afterMailingKey: String? = null
)

data class LetterWritingAddressInventory(
    val identity: TerritoryIdentity,
    val assignmentAuthoritySha256: String,
    val knowledgeBaseRevision: String,
    val sourceTruthPassed: Boolean,
    val provenance: List<LetterWritingAddressProvenance>,
    val records: List<LetterWritingAddressRecord>,
    val previousInventorySha256: String? = null,
    val changes: List<LetterWritingInventoryChange> = emptyList(),
    val nonFieldFixture: Boolean = false
) {
    fun canonicalSha256(): String = sha256LetterWriting(buildString {
        append("display_id=").append(identity.displayId).append('\n')
        append("canonical_filename=").append(identity.canonicalFilename).append('\n')
        append("assignment_authority_sha256=").append(assignmentAuthoritySha256).append('\n')
        append("knowledge_base_revision=").append(knowledgeBaseRevision).append('\n')
        append("source_truth_passed=").append(sourceTruthPassed).append('\n')
        append("previous_inventory_sha256=").append(previousInventorySha256 ?: "").append('\n')
        append("non_field_fixture=").append(nonFieldFixture).append('\n')
        provenance.sortedBy { it.provenanceId }.forEach { p ->
            append("provenance=").append(p.provenanceId).append('|')
                .append(p.sourceLabel).append('|').append(p.sourceType).append('|')
                .append(p.observedAtUtc).append('|').append(p.sourceSha256).append('|')
                .append(p.fieldUseEligible).append('\n')
        }
        records.sortedBy { it.recordId }.forEach { r ->
            append("record=").append(r.recordId).append('|')
                .append(r.territoryDisplayId).append('|')
                .append(r.streetAddress.trim()).append('|')
                .append(r.unit?.trim() ?: "").append('|')
                .append(r.city?.trim() ?: "").append('|')
                .append(r.state?.trim() ?: "").append('|')
                .append(r.postalCode?.trim() ?: "").append('|')
                .append(r.buildingId?.trim() ?: "").append('|')
                .append(r.verificationStatus.name).append('|')
                .append(r.verifiedAtUtc ?: "").append('|')
                .append(r.boundaryStatus.name).append('|')
                .append(r.boundaryEvidenceSha256).append('|')
            r.neighborTerritoryConflicts.sorted().forEach { append("neighbor:").append(it).append('|') }
            r.provenanceIds.sorted().forEach { append("source:").append(it).append('|') }
            append('\n')
        }
        changes.sortedWith(compareBy<LetterWritingInventoryChange>({ it.type }, { it.recordId })).forEach { c ->
            append("change=").append(c.type).append('|').append(c.recordId).append('|')
                .append(c.beforeMailingKey ?: "").append('|').append(c.afterMailingKey ?: "").append('\n')
        }
    }.toByteArray(Charsets.UTF_8))
}

data class LetterWritingInventoryValidation(
    val passed: Boolean,
    val errors: List<String>,
    val verifiedRecordCount: Int,
    val duplicateKeys: List<String>,
    val inventorySha256: String
)

object LetterWritingAddressInventoryValidator {
    private val sha256Pattern = Regex("^[0-9a-f]{64}$")
    private val usStatePattern = Regex("^[A-Z]{2}$")
    private val zipPattern = Regex("^\\d{5}(-\\d{4})?$")

    fun validateForPage2(inventory: LetterWritingAddressInventory): LetterWritingInventoryValidation {
        val errors = mutableListOf<String>()
        if (inventory.knowledgeBaseRevision.isBlank()) errors += "Knowledge Base revision is required"
        if (!sha256Pattern.matches(inventory.assignmentAuthoritySha256)) errors += "Assignment authority SHA-256 is invalid"
        if (!inventory.sourceTruthPassed) errors += "Locked source-truth reconciliation did not pass"
        if (inventory.records.isEmpty()) errors += "Letter Writing address inventory is empty"
        if (inventory.previousInventorySha256 != null && !sha256Pattern.matches(inventory.previousInventorySha256)) {
            errors += "Previous inventory SHA-256 is invalid"
        }

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
        if (ids.any { it.isBlank() }) errors += "Address record IDs must be nonblank"
        if (ids.distinct().size != ids.size) errors += "Duplicate address record IDs are forbidden"

        val normalized = inventory.records.map { it.normalizedMailingKey() }
        val duplicateKeys = normalized.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted()
        if (duplicateKeys.isNotEmpty()) errors += "Duplicate canonical mailing addresses are forbidden: " + duplicateKeys.joinToString()

        inventory.records.forEach { r ->
            if (r.territoryDisplayId != inventory.identity.displayId) errors += r.recordId + ": territory assignment does not match " + inventory.identity.displayId
            if (r.streetAddress.isBlank()) errors += r.recordId + ": street address is blank"
            if (r.verificationStatus != LetterWritingVerificationStatus.VERIFIED) errors += r.recordId + ": address is not verified"
            if (r.verifiedAtUtc.isNullOrBlank()) errors += r.recordId + ": verified timestamp is required"
            if (r.boundaryStatus != LetterWritingBoundaryStatus.INSIDE_LOCKED_WORKING_AREA) errors += r.recordId + ": address is not confirmed inside locked working area"
            if (!sha256Pattern.matches(r.boundaryEvidenceSha256)) errors += r.recordId + ": boundary/neighbor evidence SHA-256 is invalid"
            if (r.neighborTerritoryConflicts.isNotEmpty()) errors += r.recordId + ": unresolved neighboring-territory conflict"
            if (r.provenanceIds.isEmpty()) errors += r.recordId + ": provenance is required"
            if (r.provenanceIds.distinct().size != r.provenanceIds.size) errors += r.recordId + ": duplicate provenance bindings"
            val missing = r.provenanceIds.filter { it !in provenanceById }
            if (missing.isNotEmpty()) errors += r.recordId + ": unknown provenance IDs " + missing.joinToString()
            val eligible = r.provenanceIds.mapNotNull(provenanceById::get).any { it.fieldUseEligible }
            if (!eligible) errors += r.recordId + ": no field-use-eligible provenance"
            r.state?.let {
                if (it.isNotBlank() && !usStatePattern.matches(it.trim().uppercase(Locale.US))) errors += r.recordId + ": invalid state code"
            }
            r.postalCode?.let {
                if (it.isNotBlank() && !zipPattern.matches(it.trim())) errors += r.recordId + ": invalid ZIP/postal code"
            }
            if (r.normalizedMailingKey().isBlank()) errors += r.recordId + ": canonical mailing key is blank"
        }

        val recordIds = inventory.records.mapTo(linkedSetOf()) { it.recordId }
        if (inventory.changes.isNotEmpty() && inventory.previousInventorySha256 == null) {
            errors += "Change tracking requires previous inventory SHA-256"
        }
        inventory.changes.forEach { c ->
            if (c.type !in setOf("ADDED", "REMOVED", "CHANGED")) errors += c.recordId + ": invalid change type " + c.type
            if (c.type != "REMOVED" && c.recordId !in recordIds) errors += c.recordId + ": change record is not present in current inventory"
            if (c.type == "CHANGED" && (c.beforeMailingKey.isNullOrBlank() || c.afterMailingKey.isNullOrBlank())) {
                errors += c.recordId + ": CHANGED entry requires before/after mailing keys"
            }
        }

        val inventorySha = inventory.canonicalSha256()
        return LetterWritingInventoryValidation(
            passed = errors.isEmpty(),
            errors = errors,
            verifiedRecordCount = inventory.records.count { it.verificationStatus == LetterWritingVerificationStatus.VERIFIED },
            duplicateKeys = duplicateKeys,
            inventorySha256 = inventorySha
        )
    }

    fun toCanonicalBackSpec(
        inventory: LetterWritingAddressInventory,
        locality: String,
        updated: String
    ): CanonicalAddressBackSpec {
        val validation = validateForPage2(inventory)
        require(validation.passed) { "Letter Writing Page 2 inventory validation failed: " + validation.errors.joinToString() }
        return CanonicalAddressBackSpec(
            identity = inventory.identity,
            locality = locality,
            updated = updated,
            sourceInventorySha256 = validation.inventorySha256,
            sourceInventoryVerified = true,
            entries = inventory.records.sortedWith(
                compareBy<LetterWritingAddressRecord>(
                    { it.streetAddress.lowercase(Locale.US) },
                    { it.unit?.lowercase(Locale.US) ?: "" },
                    { it.recordId }
                )
            ).map { CanonicalAddressEntry(it.recordId, it.canonicalMailingLine()) },
            nonFieldFixture = inventory.nonFieldFixture
        )
    }
}

object LetterWritingInventoryDiff {
    fun between(
        previous: LetterWritingAddressInventory,
        current: LetterWritingAddressInventory
    ): List<LetterWritingInventoryChange> {
        require(previous.identity == current.identity) { "Cannot diff Letter Writing inventories for different territories" }
        val before = previous.records.associateBy { it.recordId }
        val after = current.records.associateBy { it.recordId }
        val out = mutableListOf<LetterWritingInventoryChange>()
        (before.keys - after.keys).sorted().forEach { id ->
            out += LetterWritingInventoryChange("REMOVED", id, before.getValue(id).normalizedMailingKey(), null)
        }
        (after.keys - before.keys).sorted().forEach { id ->
            out += LetterWritingInventoryChange("ADDED", id, null, after.getValue(id).normalizedMailingKey())
        }
        (before.keys intersect after.keys).sorted().forEach { id ->
            val a = before.getValue(id).normalizedMailingKey()
            val b = after.getValue(id).normalizedMailingKey()
            if (a != b) out += LetterWritingInventoryChange("CHANGED", id, a, b)
        }
        return out
    }
}

private fun sha256LetterWriting(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

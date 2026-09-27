package com.koenterprises.territorycardstudio

import com.koenterprises.territorycardstudio.core.*

data class WorkspaceInventoryRow(val id: String, val address: String, val phone: String?, val details: List<Pair<String, String>>)
data class WorkspaceInventorySnapshot(val hash: String, val verified: Int, val review: Int, val conflicts: Int,
    val unavailableNumbers: Int, val records: List<WorkspaceInventoryRow>, val provenance: List<Pair<String, String>>)
data class WorkspacePreparedSnapshot internal constructor(val id: String, val mode: WorkspaceMode,
    val sourceHash: String, val inputHash: String, val canBuild: Boolean,
    val categories: List<VerificationCategoryUi>, val inventory: WorkspaceInventorySnapshot?) {
    companion object {
        internal fun from(id: String, mode: WorkspaceMode, sourceHash: String, inputHash: String,
            input: ProductionRenderModelInput, inventory: Page2Inventory?, canBuild: Boolean): WorkspacePreparedSnapshot {
            val inv = when (inventory) {
                is Page2Inventory.LetterWriting -> inventory.inventory.let { v ->
                    WorkspaceInventorySnapshot(v.canonicalSha256(), v.records.count { it.verificationStatus == LetterWritingVerificationStatus.VERIFIED },
                        v.records.count { it.verificationStatus in setOf(LetterWritingVerificationStatus.NEEDS_REVIEW, LetterWritingVerificationStatus.UNVERIFIED) },
                        v.records.count { it.verificationStatus == LetterWritingVerificationStatus.CONFLICT || it.neighborTerritoryConflicts.isNotEmpty() }, 0,
                        v.records.map { r -> WorkspaceInventoryRow(r.recordId, r.canonicalMailingLine(), null, listOf(
                            "Territory" to r.territoryDisplayId, "Status" to r.verificationStatus.name, "Verified" to (r.verifiedAtUtc ?: "Unavailable"),
                            "Building" to (r.buildingId ?: "Not applicable"), "Boundary" to r.boundaryStatus.name,
                            "Boundary SHA-256" to r.boundaryEvidenceSha256, "Neighbor conflicts" to r.neighborTerritoryConflicts.joinToString().ifEmpty { "None" },
                            "Address sources" to r.provenanceIds.joinToString())) },
                        listOf("Knowledge Base" to v.knowledgeBaseRevision, "Assignment SHA-256" to v.assignmentAuthoritySha256,
                            "Previous inventory SHA-256" to (v.previousInventorySha256 ?: "None"), "Changes" to v.changes.joinToString().ifEmpty { "None" }) +
                        v.provenance.map { p -> p.provenanceId to "${p.sourceLabel}\n${p.sourceType}\nObserved ${p.observedAtUtc}\nSHA-256 ${p.sourceSha256}\nField-use source eligible: ${p.fieldUseEligible}" })
                }
                is Page2Inventory.Telephone -> inventory.inventory.let { v ->
                    WorkspaceInventorySnapshot(v.canonicalSha256(), v.records.count { it.addressVerificationStatus == TelephoneRecordVerificationStatus.VERIFIED },
                        v.records.count { it.addressVerificationStatus in setOf(TelephoneRecordVerificationStatus.NEEDS_REVIEW, TelephoneRecordVerificationStatus.UNVERIFIED) || it.phoneState == TelephoneNumberState.NEEDS_REVIEW },
                        v.records.count { it.addressVerificationStatus == TelephoneRecordVerificationStatus.CONFLICT || it.phoneState == TelephoneNumberState.CONFLICT || it.neighborTerritoryConflicts.isNotEmpty() },
                        v.records.count { it.phoneState == TelephoneNumberState.UNAVAILABLE },
                        v.records.map { r -> WorkspaceInventoryRow(r.recordId, r.canonicalAddressLine(), r.phoneDisplay(), listOf(
                            "Territory" to r.territoryDisplayId, "Address status" to r.addressVerificationStatus.name,
                            "Address verified" to (r.addressVerifiedAtUtc ?: "Unavailable"), "Phone status" to r.phoneState.name,
                            "Phone verified" to (r.phoneVerifiedAtUtc ?: "Unavailable"), "Phone record binding" to r.phoneRecordBindingVerified.toString(),
                            "Building" to (r.buildingId ?: "Not applicable"), "Boundary" to r.boundaryStatus.name,
                            "Boundary SHA-256" to r.boundaryEvidenceSha256, "Neighbor conflicts" to r.neighborTerritoryConflicts.joinToString().ifEmpty { "None" },
                            "Address sources" to r.addressProvenanceIds.joinToString(), "Phone sources" to r.phoneProvenanceIds.joinToString().ifEmpty { "None — unavailable number" })) },
                        listOf("Knowledge Base" to v.knowledgeBaseRevision, "Assignment SHA-256" to v.assignmentAuthoritySha256,
                            "Previous inventory SHA-256" to (v.previousInventorySha256 ?: "None"), "Changes" to v.changes.joinToString().ifEmpty { "None" }) +
                        v.provenance.map { p -> p.provenanceId to "${p.sourceLabel}\n${p.sourceType}\nObserved ${p.observedAtUtc}\nSHA-256 ${p.sourceSha256}\nAuthorization: ${p.authorization}\nTelephone use authorized: ${p.telephoneUseAuthorized}\nField-use source eligible: ${p.fieldUseEligible}" })
                }
                null -> null
            }
            val categories = listOf(
                VerificationCategoryUi("source_truth", "Source truth", VerificationUiState.VERIFIED,
                    "Current prepared input reconciles to the locked assignment.", "Imported SHA-256 $sourceHash\nInput SHA-256 $inputHash"),
                VerificationCategoryUi("geometry", "Live geometry", VerificationUiState.VERIFIED,
                    "Current prepared geometry passed the authoritative provider reconciliation.", input.liveResult.evidence.joinToString("\n") { "${it.providerId} • ${it.observedAtUtc}\n${it.responseSha256}" }),
                VerificationCategoryUi("overlap", "Cross-territory overlap", VerificationUiState.VERIFIED,
                    "Current prepared topology passed cross-territory reconciliation.", "Input SHA-256 $inputHash"),
                VerificationCategoryUi("labels", "Street labels", VerificationUiState.VERIFIED,
                    "${input.labels.size} prepared labels passed the frozen render-model validation. PDF output is checked separately during Build.", "Input SHA-256 $inputHash"),
                VerificationCategoryUi("buildings", "Buildings / sites", if (input.buildingValidation.applicable) VerificationUiState.VERIFIED else VerificationUiState.NOT_APPLICABLE,
                    "Current prepared building/site result: ${input.buildingValidation.buildingCount} buildings.", "Input SHA-256 $inputHash"),
                VerificationCategoryUi("colors_template", "Colors & template", VerificationUiState.VERIFIED,
                    "Current prepared topology and template passed the frozen render-model validation.", "Input SHA-256 $inputHash")
            ) + if (inv != null) listOf(VerificationCategoryUi("inventory", if (mode == WorkspaceMode.TELEPHONE) "Telephone phone list" else "Letter Writing addresses",
                VerificationUiState.VERIFIED, "${inv.verified} verified records • ${inv.review} review • ${inv.conflicts} conflicts • ${inv.unavailableNumbers} unavailable numbers", "Inventory SHA-256 ${inv.hash}")) else emptyList()
            return WorkspacePreparedSnapshot(id, mode, sourceHash, inputHash, canBuild, categories.toList(), inv?.copy(provenance = listOf(
                "Territory" to id, "Mode" to mode.name, "Imported source SHA-256" to sourceHash,
                "Prepared input SHA-256" to inputHash) + inv.provenance))
        }
    }
}


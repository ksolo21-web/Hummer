package com.koenterprises.territorycardstudio.core

/** Application-owned lookup. Implementations must recheck the current receipt, source witness,
 * revocation and revision on EVERY lookup. A portable reconciliation file alone is not a lookup. */
fun interface NativeAssignmentEligibility {
    fun current(territory: String, authoritySha256: String): NativeAssignmentEvidence?
    companion object { val NONE = NativeAssignmentEligibility { _, _ -> null } }
}

class NativeAssignmentEvidence private constructor(
    val territory: String, val mode: String, val knowledgeBaseRevision: String,
    val authoritySha256: String, val importedSourceSha256: String, val lockedReferenceSha256: String,
    val assignmentContentSha256: String, val inventorySha256: String?,
    val topology: TopologySignature, val buildingMembers: List<String>, val truth: CandidateSourceTruthState, val reconciliationJson:String
) {
    fun matches(kb: TerritoryKnowledgeBase, a: CurrentAuthoritativeAssignmentState, sourceTruth: CandidateSourceTruthState): Boolean =
        territory == a.displayId && knowledgeBaseRevision == kb.revision && authoritySha256 == a.authoritySha256 &&
            lockedReferenceSha256 == kb.assignments[a.displayId]?.referenceSha256 &&
            assignmentContentSha256 == NativeSourceReconciliationContract.assignmentContentSha256(a) && truth == sourceTruth

    companion object {
        /** Called only after the owning ledger and exact source witness have been verified.
         * This binds the self-excluded authority hash back to the exact receipt bytes. */
        fun fromCurrentLedger(kb: TerritoryKnowledgeBase, receiptBytes: ByteArray,
            assignment: CurrentAuthoritativeAssignmentState, mode: String, sourceSha256: String,
            inventorySha256: String?): NativeAssignmentEvidence {
            val r = NativeSourceReconciliationContract.decode(receiptBytes)
            val hash = BundleIntegrity.sha256(receiptBytes.inputStream())
            require(assignment.authoritySha256 == hash) { "Assignment authority must identify exact reconciliation bytes" }
            val assessment = NativeSourceReconciliationContract.assess(kb,r,assignment,mode,sourceSha256,inventorySha256)
            require(assessment.passed) { assessment.failures.joinToString("; ") }
            return NativeAssignmentEvidence(r.territory,r.mode,kb.revision,hash,sourceSha256,r.lockedReferenceSha256,
                r.assignmentContentSha256,r.inventorySha256,TopologyOverlapDecisionEngine.topologySignature(assignment.roads),
                assignment.buildings.flatMap { it.sourceMembers }.distinct().sorted(),assessment.truth,receiptBytes.toString(Charsets.UTF_8))
        }
    }
}

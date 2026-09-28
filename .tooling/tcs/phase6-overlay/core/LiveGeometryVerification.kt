package com.koenterprises.territorycardstudio.core

import java.security.MessageDigest
import java.time.Instant

private val SHA256_HEX = Regex("^[0-9a-f]{64}$")

enum class CandidateBuildIntent {
    REBUILD_OR_NEW_CANDIDATE,
    EXACT_APPROVED_PASSTHROUGH
}

data class VerificationJurisdiction(
    val county: String?,
    val state: String,
    val country: String
) {
    init {
        require(state.isNotBlank()) { "Verification state is required" }
        require(country.isNotBlank()) { "Verification country is required" }
    }

    val oaklandCountyMichiganApplicable: Boolean
        get() {
            val normalizedCounty = county?.lowercase()?.replace(" county", "")?.trim()
            val normalizedState = state.lowercase().trim()
            val normalizedCountry = country.lowercase().trim()
            return normalizedCounty == "oakland" &&
                normalizedState in setOf("mi", "michigan") &&
                normalizedCountry in setOf("us", "usa", "united states", "united states of america")
        }
}

data class CandidateSourceTruthState(
    val sourceTerritoryDisplayId: String,
    val assignmentAuthoritySourceSha256: String,
    val lockedAssignmentReconciled: Boolean,
    val missingExpectedSegmentCount: Int,
    val unexpectedMeaningChangingSegmentCount: Int,
    val assignmentColorConflictCount: Int,
    val unresolvedPerimeterWorkedSideCount: Int,
    val illegalMidSegmentColorTransitionCount: Int,
    val unresolvedBuildingSiteCount: Int,
    val crossTerritoryInferenceUsed: Boolean,
    val styleOnlyGeographyUsed: Boolean
) {
    init {
        require(SHA256_HEX.matches(assignmentAuthoritySourceSha256)) { "Invalid assignment-authority source SHA-256" }
        listOf(
            missingExpectedSegmentCount,
            unexpectedMeaningChangingSegmentCount,
            assignmentColorConflictCount,
            unresolvedPerimeterWorkedSideCount,
            illegalMidSegmentColorTransitionCount,
            unresolvedBuildingSiteCount
        ).forEach { require(it >= 0) { "Source-truth counts cannot be negative" } }
    }

    fun hardFailures(expectedDisplayId: String): List<String> = buildList {
        if (sourceTerritoryDisplayId != expectedDisplayId) add("source belongs to $sourceTerritoryDisplayId, not $expectedDisplayId")
        if (!lockedAssignmentReconciled) add("candidate has not reconciled to the locked assignment")
        if (missingExpectedSegmentCount > 0) add("missing expected street/segment count=$missingExpectedSegmentCount")
        if (unexpectedMeaningChangingSegmentCount > 0) add("unexpected meaning-changing street/segment count=$unexpectedMeaningChangingSegmentCount")
        if (assignmentColorConflictCount > 0) add("assignment/color conflict count=$assignmentColorConflictCount")
        if (unresolvedPerimeterWorkedSideCount > 0) add("unresolved perimeter worked-side count=$unresolvedPerimeterWorkedSideCount")
        if (illegalMidSegmentColorTransitionCount > 0) add("illegal mid-segment transition count=$illegalMidSegmentColorTransitionCount")
        if (unresolvedBuildingSiteCount > 0) add("unresolved building/site assignment count=$unresolvedBuildingSiteCount")
        if (crossTerritoryInferenceUsed) add("cross-territory geography inference was used")
        if (styleOnlyGeographyUsed) add("style-only reference was used as geography")
    }
}

data class LiveRoadVerificationTarget(
    val targetId: String,
    val normalizedRoadName: String,
    val endpointAKind: String,
    val endpointBKind: String,
    val endpointANeighbors: List<String>,
    val endpointBNeighbors: List<String>
) {
    init {
        require(targetId.isNotBlank())
        require(normalizedRoadName.isNotBlank())
    }
}

data class LiveGeometryVerificationRequest(
    val territoryDisplayId: String,
    val candidateSourceSha256: String,
    val onlinePolicyRevision: String,
    val knowledgeBaseRevision: String,
    val lockedReferenceSha256: String,
    val lockedSourceHashes: List<String>,
    val assignmentSourceRole: String,
    val jurisdiction: VerificationJurisdiction,
    val sourceTruth: CandidateSourceTruthState,
    val roadTargets: List<LiveRoadVerificationTarget>,
    val candidateBuildingMemberIds: List<String>,
    val housingType: String,
    val needsNewCard: Boolean,
    val siteAddressCrosscheckRequired: Boolean,
    val buildingOutlineCrosscheckRequired: Boolean,
    val requestFingerprint: String
) {
    init {
        require(SHA256_HEX.matches(candidateSourceSha256)) { "Invalid candidate source SHA-256" }
        require(onlinePolicyRevision.isNotBlank()) { "Online policy revision is required" }
        require(SHA256_HEX.matches(lockedReferenceSha256)) { "Invalid locked-reference SHA-256" }
        require(lockedSourceHashes.isNotEmpty() && lockedSourceHashes.all(SHA256_HEX::matches)) { "Invalid locked source hashes" }
        require(lockedSourceHashes.distinct().size == lockedSourceHashes.size) { "Duplicate locked source hashes" }
        require(assignmentSourceRole == "exact_assignment_reference") {
            "Only exact_assignment_reference may authorize known-territory geography"
        }
        require(roadTargets.isNotEmpty()) { "Live verification requires candidate road topology" }
        require(roadTargets.map { it.targetId }.toSet().size == roadTargets.size) { "Duplicate live road target IDs" }
        require(candidateBuildingMemberIds.toSet().size == candidateBuildingMemberIds.size) { "Duplicate candidate building/site member IDs" }
        require(SHA256_HEX.matches(requestFingerprint)) { "Invalid request fingerprint" }
    }
}

object LiveGeometryVerificationRequestFactory {
    fun create(
        kb: TerritoryKnowledgeBase,
        policy: OnlineSourcePolicy,
        displayId: String,
        candidateSourceSha256: String,
        candidateTopology: TopologySignature,
        candidateBuildingMemberIds: List<String>,
        sourceTruth: CandidateSourceTruthState,
        jurisdiction: VerificationJurisdiction,
        intent: CandidateBuildIntent = CandidateBuildIntent.REBUILD_OR_NEW_CANDIDATE,
        nativeEligibility: NativeAssignmentEligibility = NativeAssignmentEligibility.NONE
    ): LiveGeometryVerificationRequest {
        require(intent == CandidateBuildIntent.REBUILD_OR_NEW_CANDIDATE) {
            "Exact approved passthrough must preserve bytes and must not enter the candidate GIS pipeline"
        }
        require(policy.releasePolicy.defaultAppMode == "required") { "Live source policy is not in required mode" }
        require(SHA256_HEX.matches(candidateSourceSha256)) { "Invalid candidate source SHA-256" }
        val assignment = kb.assignments[displayId] ?: error("Unknown territory $displayId; unknown assignments must BLOCK")
        val referenceRole = kb.referenceRoles[assignment.referenceFile]
            ?: error("${assignment.referenceFile} has no explicit source role")
        require(referenceRole.displayId == displayId) { "Reference/source territory mismatch for $displayId" }
        require(referenceRole.sha256 == assignment.referenceSha256) { "Reference role hash mismatch for $displayId" }
        require(referenceRole.role == "exact_assignment_reference") {
            "${referenceRole.role} cannot authorize $displayId geography; exact_assignment_reference required"
        }
        require(!referenceRole.crossTerritoryGeographyAllowed) { "Cross-territory geography must remain disabled" }
        val native = if (sourceTruth.assignmentAuthoritySourceSha256 in assignment.sourceHashes) null else
            nativeEligibility.current(displayId, sourceTruth.assignmentAuthoritySourceSha256)
        require(sourceTruth.assignmentAuthoritySourceSha256 in assignment.sourceHashes || native != null) {
            "Candidate assignment authority is not bound to locked sources or a current explicit local registration"
        }
        if (native != null) {
            require(native.territory == displayId && native.authoritySha256 == candidateSourceSha256 &&
                native.knowledgeBaseRevision == kb.revision && native.lockedReferenceSha256 == assignment.referenceSha256 &&
                native.truth == sourceTruth && native.topology == candidateTopology &&
                native.buildingMembers == candidateBuildingMemberIds.sorted()) { "Local registration facts do not match live verification input" }
        }

        val roadTargets = candidateTopology.junctionBoundedSegments.mapIndexed { index, segment ->
            val normalized = TopologyOverlapDecisionEngine.normalizeRoadName(segment.road)
            require(normalized.isNotBlank()) { "Blank candidate road target at index $index" }
            LiveRoadVerificationTarget(
                targetId = "road-${index.toString().padStart(4, '0')}",
                normalizedRoadName = normalized,
                endpointAKind = segment.endpointAKind,
                endpointBKind = segment.endpointBKind,
                endpointANeighbors = segment.endpointANeighbors.map(TopologyOverlapDecisionEngine::normalizeRoadName).filter(String::isNotBlank).sorted(),
                endpointBNeighbors = segment.endpointBNeighbors.map(TopologyOverlapDecisionEngine::normalizeRoadName).filter(String::isNotBlank).sorted()
            )
        }
        require(roadTargets.isNotEmpty()) { "Candidate topology has no verifiable road targets" }

        val multiUnit = assignment.housingType in setOf("apartment", "condo", "townhome", "mobile_home")
        if (multiUnit) {
            require(candidateBuildingMemberIds.isNotEmpty() || sourceTruth.unresolvedBuildingSiteCount > 0) {
                "Multi-unit candidate has no building/site member inventory"
            }
            if (!assignment.needsNewCard && sourceTruth.unresolvedBuildingSiteCount == 0) {
                val lockedMembers = assignment.assignmentSignature?.buildingMemberIds.orEmpty().sorted()
                require(lockedMembers.isNotEmpty()) { "Multi-unit locked assignment has no building/site member inventory" }
                require(candidateBuildingMemberIds.sorted() == lockedMembers) {
                    "Candidate building/site inventory does not exactly match the current locked assignment"
                }
            }
        } else {
            require(candidateBuildingMemberIds.isEmpty()) {
                "Ordinary residential candidate cannot carry apartment/mobile building-member inventory"
            }
        }
        val buildingCrosschecksRequired = multiUnit && candidateBuildingMemberIds.isNotEmpty() && jurisdiction.oaklandCountyMichiganApplicable

        val provisional = LiveGeometryVerificationRequest(
            territoryDisplayId = displayId,
            candidateSourceSha256 = candidateSourceSha256,
            onlinePolicyRevision = policy.revision,
            knowledgeBaseRevision = kb.revision,
            lockedReferenceSha256 = assignment.referenceSha256,
            lockedSourceHashes = assignment.sourceHashes.sorted(),
            assignmentSourceRole = referenceRole.role,
            jurisdiction = jurisdiction,
            sourceTruth = sourceTruth,
            roadTargets = roadTargets,
            candidateBuildingMemberIds = candidateBuildingMemberIds.sorted(),
            housingType = assignment.housingType,
            needsNewCard = assignment.needsNewCard,
            siteAddressCrosscheckRequired = buildingCrosschecksRequired,
            buildingOutlineCrosscheckRequired = buildingCrosschecksRequired,
            requestFingerprint = "0".repeat(64)
        )
        return provisional.copy(requestFingerprint = fingerprint(provisional))
    }

    fun fingerprintMatches(request: LiveGeometryVerificationRequest): Boolean =
        fingerprint(request) == request.requestFingerprint

    private fun fingerprint(request: LiveGeometryVerificationRequest): String {
        val canonical = buildString {
            append("territory=").append(request.territoryDisplayId).append('\n')
            append("candidate=").append(request.candidateSourceSha256).append('\n')
            append("onlinePolicy=").append(request.onlinePolicyRevision).append('\n')
            append("kb=").append(request.knowledgeBaseRevision).append('\n')
            append("locked=").append(request.lockedReferenceSha256).append('\n')
            append("sources=").append(request.lockedSourceHashes.joinToString(",")).append('\n')
            append("role=").append(request.assignmentSourceRole).append('\n')
            append("jurisdiction=").append(request.jurisdiction.county.orEmpty()).append('|')
                .append(request.jurisdiction.state).append('|').append(request.jurisdiction.country).append('\n')
            append("sourceTruth=")
                .append(request.sourceTruth.sourceTerritoryDisplayId).append('|')
                .append(request.sourceTruth.assignmentAuthoritySourceSha256).append('|')
                .append(request.sourceTruth.lockedAssignmentReconciled).append('|')
                .append(request.sourceTruth.missingExpectedSegmentCount).append('|')
                .append(request.sourceTruth.unexpectedMeaningChangingSegmentCount).append('|')
                .append(request.sourceTruth.assignmentColorConflictCount).append('|')
                .append(request.sourceTruth.unresolvedPerimeterWorkedSideCount).append('|')
                .append(request.sourceTruth.illegalMidSegmentColorTransitionCount).append('|')
                .append(request.sourceTruth.unresolvedBuildingSiteCount).append('|')
                .append(request.sourceTruth.crossTerritoryInferenceUsed).append('|')
                .append(request.sourceTruth.styleOnlyGeographyUsed).append('\n')
            request.roadTargets.forEach { target ->
                append(target.targetId).append('|').append(target.normalizedRoadName).append('|')
                    .append(target.endpointAKind).append('|').append(target.endpointBKind).append('|')
                    .append(target.endpointANeighbors.joinToString(",")).append('|')
                    .append(target.endpointBNeighbors.joinToString(",")).append('\n')
            }
            append("buildings=").append(request.candidateBuildingMemberIds.joinToString(",")).append('\n')
            append("housing=").append(request.housingType).append('\n')
            append("needsNew=").append(request.needsNewCard).append('\n')
            append("siteCrosscheck=").append(request.siteAddressCrosscheckRequired).append('\n')
            append("buildingCrosscheck=").append(request.buildingOutlineCrosscheckRequired).append('\n')
        }
        return sha256(canonical)
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

data class ProviderVerificationEvidence(
    val providerId: String,
    val requestFingerprint: String,
    val available: Boolean,
    val observedAtUtc: String?,
    val responseSha256: String?,
    val sourceDataVintage: String?,
    val queriedTargetIds: Set<String>,
    val confirmedTargetIds: Set<String>,
    val notFoundTargetIds: Set<String>,
    val conflictingTargetIds: Set<String>,
    val unknownMeaningChangingRoads: Set<String>,
    val siteAddressCrosscheckPassed: Boolean?,
    val buildingOutlineCrosscheckPassed: Boolean?,
    val errorCode: String?
) {
    init {
        require(providerId.isNotBlank())
        require(SHA256_HEX.matches(requestFingerprint)) { "Invalid evidence request fingerprint" }
        val outcomes = confirmedTargetIds + notFoundTargetIds + conflictingTargetIds
        require(outcomes.size == confirmedTargetIds.size + notFoundTargetIds.size + conflictingTargetIds.size) {
            "$providerId has a road target in more than one outcome set"
        }
        require(outcomes.all { it in queriedTargetIds }) { "$providerId reported an unqueried road target" }
        if (available) {
            require(!observedAtUtc.isNullOrBlank()) { "$providerId available evidence needs observed_at" }
            Instant.parse(observedAtUtc)
            require(responseSha256 != null && SHA256_HEX.matches(responseSha256)) { "$providerId available evidence needs a response SHA-256" }
            require(errorCode == null) { "$providerId cannot be available and carry an error code" }
        } else {
            require(!errorCode.isNullOrBlank()) { "$providerId unavailable evidence needs an error code" }
            observedAtUtc?.let { Instant.parse(it) }
            responseSha256?.let { require(SHA256_HEX.matches(it)) { "$providerId unavailable evidence has invalid response SHA-256" } }
            require(confirmedTargetIds.isEmpty() && notFoundTargetIds.isEmpty() && conflictingTargetIds.isEmpty()) {
                "$providerId unavailable evidence cannot assert road outcomes"
            }
            require(unknownMeaningChangingRoads.isEmpty()) { "$providerId unavailable evidence cannot assert unknown roads" }
            require(siteAddressCrosscheckPassed == null && buildingOutlineCrosscheckPassed == null) {
                "$providerId unavailable evidence cannot assert crosscheck results"
            }
        }
    }
}

enum class LiveVerificationDisposition {
    RENDERABLE_VERIFIED,
    BLOCKED
}

data class LiveGeometryVerificationDecision(
    val disposition: LiveVerificationDisposition,
    val renderable: Boolean,
    val independentGeometrySourceCount: Int,
    val qualifyingGeometryProviders: List<String>,
    val oaklandPrimarySatisfied: Boolean,
    val sourceTruthPassed: Boolean,
    val providerAgreementPassed: Boolean,
    val assignmentAuthorityPreserved: Boolean,
    val siteAddressCrosscheckPassed: Boolean?,
    val buildingOutlineCrosscheckPassed: Boolean?,
    val blockedReasons: List<String>,
    val providerEvidenceDigests: Map<String, String>,
    val providerObservedAtUtc: Map<String, String>,
    val providerDataVintages: Map<String, String>
)

object LiveGeometryVerificationReconciler {
    fun reconcile(
        request: LiveGeometryVerificationRequest,
        policy: OnlineSourcePolicy,
        evidence: List<ProviderVerificationEvidence>
    ): LiveGeometryVerificationDecision {
        val blocked = mutableListOf<String>()
        val requestFingerprintValid = LiveGeometryVerificationRequestFactory.fingerprintMatches(request)
        if (!requestFingerprintValid) blocked += "verification request fingerprint does not match request contents"
        val targetIds = request.roadTargets.mapTo(linkedSetOf()) { it.targetId }
        val sourceTruthFailures = request.sourceTruth.hardFailures(request.territoryDisplayId)
        blocked += sourceTruthFailures

        val duplicateProviders = evidence.groupingBy { it.providerId }.eachCount().filterValues { it > 1 }.keys.sorted()
        if (duplicateProviders.isNotEmpty()) blocked += "duplicate provider evidence: ${duplicateProviders.joinToString()}"

        evidence.forEach { item ->
            val provider = policy.providers[item.providerId]
            if (provider == null) {
                blocked += "unknown provider evidence: ${item.providerId}"
            } else {
                when (provider.capability) {
                    OnlineProviderCapability.ROAD_GEOMETRY_AUTHORITY -> {
                        if (item.siteAddressCrosscheckPassed != null || item.buildingOutlineCrosscheckPassed != null || item.sourceDataVintage != null) {
                            blocked += "${item.providerId} asserted evidence outside its road-geometry role"
                        }
                    }
                    OnlineProviderCapability.SITE_ADDRESS_CROSSCHECK -> {
                        if (item.queriedTargetIds.isNotEmpty() || item.buildingOutlineCrosscheckPassed != null || item.sourceDataVintage != null) {
                            blocked += "${item.providerId} asserted evidence outside its site-address role"
                        }
                    }
                    OnlineProviderCapability.BUILDING_OUTLINE_CROSSCHECK -> {
                        if (item.queriedTargetIds.isNotEmpty() || item.siteAddressCrosscheckPassed != null) {
                            blocked += "${item.providerId} asserted evidence outside its building-outline role"
                        }
                        if (item.available && item.sourceDataVintage.isNullOrBlank()) {
                            blocked += "${item.providerId} must retain source imagery/footprint vintage in the audit"
                        }
                    }
                    OnlineProviderCapability.OPTIONAL_COMMERCIAL_CROSSCHECK -> {
                        if (item.siteAddressCrosscheckPassed != null || item.buildingOutlineCrosscheckPassed != null || item.sourceDataVintage != null) {
                            blocked += "${item.providerId} cannot substitute for official site/building crosschecks"
                        }
                    }
                }
            }
            if (item.requestFingerprint != request.requestFingerprint) blocked += "${item.providerId} evidence is bound to a different verification request"
            if (!targetIds.containsAll(item.queriedTargetIds)) blocked += "${item.providerId} queried unknown target IDs"
            if (item.unknownMeaningChangingRoads.isNotEmpty()) {
                blocked += "${item.providerId} reports unknown meaning-changing roads: ${item.unknownMeaningChangingRoads.sorted().joinToString()}"
            }
            if (item.conflictingTargetIds.isNotEmpty()) {
                blocked += "${item.providerId} reports conflicting road topology for ${item.conflictingTargetIds.sorted().joinToString()}"
            }
        }

        val evidenceByProvider = evidence
            .asSequence()
            .filter { requestFingerprintValid }
            .filter { it.providerId in policy.providers }
            .filter { providerApplicable(request, it.providerId) }
            .filter { it.providerId !in duplicateProviders }
            .filter { it.requestFingerprint == request.requestFingerprint }
            .associateBy { it.providerId }
        val qualifying = mutableListOf<String>()
        policy.providers.values
            .filter { it.capability == OnlineProviderCapability.ROAD_GEOMETRY_AUTHORITY }
            .filter { providerApplicable(request, it.id) }
            .forEach { provider ->
                val item = evidenceByProvider[provider.id] ?: return@forEach
                if (!item.available) return@forEach
                val fullCoverage = item.queriedTargetIds == targetIds
                val allConfirmed = item.confirmedTargetIds == targetIds && item.notFoundTargetIds.isEmpty() && item.conflictingTargetIds.isEmpty()
                if (fullCoverage && allConfirmed && item.unknownMeaningChangingRoads.isEmpty()) {
                    qualifying += provider.id
                } else if (fullCoverage && item.notFoundTargetIds.isNotEmpty()) {
                    blocked += "${provider.id} cannot confirm expected road targets: ${item.notFoundTargetIds.sorted().joinToString()}"
                }
            }

        val oaklandPrimary = if (request.jurisdiction.oaklandCountyMichiganApplicable && policy.releasePolicy.oaklandCountyPrimaryWhenApplicable) {
            val item = evidenceByProvider["oakland_county_roads"]
            val passed = item != null && item.available && item.requestFingerprint == request.requestFingerprint && "oakland_county_roads" in qualifying
            if (!passed) blocked += "Oakland County road GIS is required and did not fully confirm candidate road topology"
            passed
        } else {
            true
        }

        if (qualifying.distinct().size < policy.releasePolicy.minimumIndependentGeometrySources) {
            blocked += "independent road-geometry confirmations=${qualifying.distinct().size}; required=${policy.releasePolicy.minimumIndependentGeometrySources}"
        }

        val siteCrosscheck = crosscheckResult(
            required = request.siteAddressCrosscheckRequired,
            providerId = "oakland_county_site_addresses",
            evidenceByProvider = evidenceByProvider,
            selector = { it.siteAddressCrosscheckPassed },
            blocked = blocked,
            label = "site-address"
        )
        val buildingCrosscheck = crosscheckResult(
            required = request.buildingOutlineCrosscheckRequired,
            providerId = "oakland_county_buildings",
            evidenceByProvider = evidenceByProvider,
            selector = { it.buildingOutlineCrosscheckPassed },
            blocked = blocked,
            label = "building-outline"
        )

        val providerAgreementPassed = evidence.none { it.conflictingTargetIds.isNotEmpty() || it.unknownMeaningChangingRoads.isNotEmpty() }
        val uniqueReasons = blocked.distinct()
        val renderable = uniqueReasons.isEmpty()
        return LiveGeometryVerificationDecision(
            disposition = if (renderable) LiveVerificationDisposition.RENDERABLE_VERIFIED else LiveVerificationDisposition.BLOCKED,
            renderable = renderable,
            independentGeometrySourceCount = qualifying.distinct().size,
            qualifyingGeometryProviders = qualifying.distinct().sorted(),
            oaklandPrimarySatisfied = oaklandPrimary,
            sourceTruthPassed = sourceTruthFailures.isEmpty(),
            providerAgreementPassed = providerAgreementPassed,
            assignmentAuthorityPreserved = sourceTruthFailures.isEmpty() && evidence.none { it.unknownMeaningChangingRoads.isNotEmpty() },
            siteAddressCrosscheckPassed = siteCrosscheck,
            buildingOutlineCrosscheckPassed = buildingCrosscheck,
            blockedReasons = uniqueReasons,
            providerEvidenceDigests = evidenceByProvider.values
                .filter { it.responseSha256 != null }
                .associate { it.providerId to requireNotNull(it.responseSha256) },
            providerObservedAtUtc = evidenceByProvider.values
                .filter { it.observedAtUtc != null }
                .associate { it.providerId to requireNotNull(it.observedAtUtc) },
            providerDataVintages = evidenceByProvider.values
                .filter { it.available && !it.sourceDataVintage.isNullOrBlank() }
                .associate { it.providerId to requireNotNull(it.sourceDataVintage) }
        )
    }

    private fun providerApplicable(request: LiveGeometryVerificationRequest, providerId: String): Boolean =
        if (providerId.startsWith("oakland_county_")) request.jurisdiction.oaklandCountyMichiganApplicable else true

    private fun crosscheckResult(
        required: Boolean,
        providerId: String,
        evidenceByProvider: Map<String, ProviderVerificationEvidence>,
        selector: (ProviderVerificationEvidence) -> Boolean?,
        blocked: MutableList<String>,
        label: String
    ): Boolean? {
        if (!required) return null
        val item = evidenceByProvider[providerId]
        if (item == null || !item.available) {
            blocked += "$providerId is required for $label verification and is unavailable"
            return false
        }
        val passed = selector(item) == true
        if (!passed) blocked += "$providerId did not pass the required $label crosscheck"
        return passed
    }
}

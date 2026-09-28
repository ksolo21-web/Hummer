package com.koenterprises.territorycardstudio.core

import java.math.BigDecimal
import java.security.MessageDigest

/** Coordinate space expected by the locked R48 renderer. */
enum class RenderCoordinateSpace {
    LOCKED_R48_PAGE_POINTS_TOP_ORIGIN
}

/**
 * Current authoritative assignment state used to build a render model.
 *
 * This is intentionally separate from KnowledgeBaseAssignment. For needs_new_card territories the KB record may
 * still contain legacy/reference geometry; that geometry must never be silently promoted into a new card. A caller
 * must provide newly authorized current assignment evidence here and bind it by SHA-256.
 */
data class CurrentAuthoritativeAssignmentState(
    val displayId: String,
    val identity: TerritoryIdentity,
    val canonicalFilename: String,
    val knowledgeBaseRevision: String,
    val authorityRole: String,
    val authoritySha256: String,
    val sourceMasterLabel: String,
    val locality: String,
    val updated: String,
    val directionsLines: List<String>,
    val layoutMode: String,
    val housingType: String,
    val coordinateSpace: RenderCoordinateSpace,
    val roads: List<RoadGeometry>,
    val buildings: List<BuildingGeometry>
) {
    init {
        require(displayId == identity.displayId) { "Current assignment display identity mismatch" }
        require(canonicalFilename == identity.canonicalFilename) { "Current assignment canonical filename mismatch" }
        require(authorityRole.isNotBlank()) { "Current assignment authority role is blank" }
        require(SHA256_PATTERN.matches(authoritySha256)) { "Current assignment authority SHA-256 is invalid" }
        require(sourceMasterLabel.isNotBlank()) { "Current assignment source-master label is blank" }
        require(locality.isNotBlank()) { "Current assignment locality is blank" }
        require(updated.isNotBlank()) { "Current assignment updated date is blank" }
        require(directionsLines.size in 1..3 && directionsLines.all { it.isNotBlank() }) { "Current assignment requires 1..3 directions lines" }
        require(roads.isNotEmpty()) { "Current assignment requires road geometry" }
        require(roads.map { it.segmentId }.distinct().size == roads.size) { "Current assignment contains duplicate road segment IDs" }
        require(buildings.map { it.buildingId }.distinct().size == buildings.size) { "Current assignment contains duplicate building IDs" }
    }

    fun canonicalSha256(): String = sha256(buildString {
        append("display=").append(displayId).append('\n')
        append("filename=").append(canonicalFilename).append('\n')
        append("kb=").append(knowledgeBaseRevision).append('\n')
        append("role=").append(authorityRole).append('\n')
        append("authority=").append(authoritySha256).append('\n')
        append("sourceLabel=").append(sourceMasterLabel).append('\n')
        append("locality=").append(locality).append('\n')
        append("updated=").append(updated).append('\n')
        append("layout=").append(layoutMode).append('\n')
        append("housing=").append(housingType).append('\n')
        append("coordinates=").append(coordinateSpace.name).append('\n')
        directionsLines.forEachIndexed { i, line -> append("direction[").append(i).append("]=").append(line).append('\n') }
        roads.sortedBy { it.segmentId }.forEach { road ->
            append("road=").append(road.segmentId).append('|').append(road.name).append('|').append(road.normalizedName).append('|').append(road.status)
                .append('|').append(road.role).append('|').append(road.insideSide).append('|').append(road.accessOnly)
                .append('|').append(road.endpointAKind).append('|').append(road.endpointBKind).append('|').append(number(road.widthPt))
            road.points.forEach { p -> append('|').append(number(p.x)).append(',').append(number(p.y)) }
            append('\n')
        }
        buildings.sortedBy { it.buildingId }.forEach { b ->
            append("building=").append(b.buildingId).append('|').append(b.label).append('|').append(b.housingType)
                .append('|').append(b.assigned).append('|').append(b.attachedGroup)
            b.sourceMembers.sorted().forEach { append("|member:").append(it) }
            b.labelItems.sortedBy { it.text }.forEach { item ->
                append("|label:").append(item.text).append('@').append(number(item.center.x)).append(',').append(number(item.center.y))
                    .append(',').append(number(item.angleDeg)).append(',').append(number(item.fontSizePt))
            }
            b.polygon.forEach { p -> append('|').append(number(p.x)).append(',').append(number(p.y)) }
            append('\n')
        }
    })
}

data class VerifiedLabelRenderOutput(
    val labelId: String,
    val segmentId: String,
    val text: String,
    val baselineX: Double,
    val baselineTopY: Double,
    val rotationDegrees: Double,
    val fontSizePt: Double,
    val assignedRoadGapPx: Double,
    val scale: LabelLayoutScale,
    val placement: LabelPlacement
) {
    init {
        require(labelId.isNotBlank()) { "Verified label ID is blank" }
        require(segmentId.isNotBlank()) { "Verified label segment ID is blank" }
        require(text.isNotBlank()) { "Verified label text is blank" }
        require(fontSizePt > 0.0) { "Verified label font size must be positive" }
    }
}

data class VerifiedDedicatedDetailGeometry(
    val detailId: String,
    val sourceBounds: AxisAlignedRect,
    val destinationBounds: AxisAlignedRect,
    val sourceSegmentIds: List<String>,
    val renderedRoads: List<RoadGeometry>,
    val labelIds: List<String>
) {
    init {
        require(detailId.isNotBlank()) { "Verified dedicated-detail ID is blank" }
        require(sourceSegmentIds.isNotEmpty()) { "Verified dedicated-detail source segments are empty" }
        require(labelIds.isNotEmpty()) { "Verified dedicated-detail label IDs are empty" }
    }
}

data class VerifiedSplitDetailPanelGeometry(
    val panelId: String,
    val sourceBounds: AxisAlignedRect,
    val destinationBounds: AxisAlignedRect,
    val sourceSegmentIds: List<String>,
    val sourceBuildingIds: List<String> = emptyList(),
    val renderedRoads: List<RoadGeometry>,
    val labelIds: List<String>,
    val renderedBuildings: List<BuildingGeometry> = emptyList()
) {
    init {
        require(panelId.isNotBlank()) { "Verified split-detail panel ID is blank" }
        require(sourceSegmentIds.isNotEmpty()) { "Verified split-detail source segments are empty" }
    }
}

data class VerifiedSplitDetailGeometry(
    val fullMapRect: AxisAlignedRect,
    val rightRect: AxisAlignedRect,
    val dividerX: Double,
    val detailDividerY: Double,
    val northDetailRect: AxisAlignedRect,
    val southDetailRect: AxisAlignedRect,
    val northPanel: VerifiedSplitDetailPanelGeometry,
    val southPanel: VerifiedSplitDetailPanelGeometry,
    val sharedSourceSegmentIds: List<String> = emptyList()
)

data class VerifiedFullPlusDetailGeometry(
    val mainContextRect: AxisAlignedRect,
    val detail: VerifiedFullPlusDetailPanelGeometry
)

data class VerifiedFullPlusDetailPanelGeometry(
    val panelId: String,
    val sourceBounds: AxisAlignedRect,
    val destinationBounds: AxisAlignedRect,
    val sourceSegmentIds: List<String>,
    val sourceBuildingIds: List<String> = emptyList(),
    val sourceLabelIds: List<String>,
    val detailLabelIds: List<String>,
    val renderedRoads: List<RoadGeometry>,
    val renderedBuildings: List<BuildingGeometry> = emptyList()
) {
    init {
        require(panelId.isNotBlank()) { "Verified full-plus-detail panel ID is blank" }
        require(sourceSegmentIds.isNotEmpty()) { "Verified full-plus-detail source segments are empty" }
        require(sourceLabelIds.isNotEmpty()) { "Verified full-plus-detail source labels are empty" }
        require(detailLabelIds.isNotEmpty()) { "Verified full-plus-detail detail labels are empty" }
    }
}

data class VerifiedSiteBuildingBinding(
    val buildingId: String,
    val sourceMemberIds: List<String>,
    val verifiedLabelTexts: List<String>
)

data class VerifiedSiteAccessInsetGeometry(
    val panelId: String,
    val sourceBounds: AxisAlignedRect,
    val destinationBounds: AxisAlignedRect,
    val sourceSegmentIds: List<String>,
    val sourceLabelIds: List<String>,
    val detailLabelIds: List<String>,
    val renderedRoads: List<RoadGeometry>
) {
    init {
        require(panelId.isNotBlank()) { "Verified site access inset panel ID is blank" }
        require(sourceSegmentIds.isNotEmpty()) { "Verified site access inset source segments are empty" }
        require(sourceLabelIds.isNotEmpty()) { "Verified site access inset source labels are empty" }
        require(detailLabelIds.isNotEmpty()) { "Verified site access inset detail labels are empty" }
    }
}

data class VerifiedSiteBuildingAssignmentGeometry(
    val diagramId: String,
    val diagramRect: AxisAlignedRect,
    val sourceSegmentIds: List<String>,
    val sourceBuildingIds: List<String>,
    val sourceLabelIds: List<String>,
    val buildingBindings: List<VerifiedSiteBuildingBinding>,
    val accessInset: VerifiedSiteAccessInsetGeometry? = null
) {
    init {
        require(diagramId.isNotBlank()) { "Verified site/building diagram ID is blank" }
        require(sourceSegmentIds.isNotEmpty()) { "Verified site/building source segments are empty" }
        require(sourceBuildingIds.isNotEmpty()) { "Verified site/building source buildings are empty" }
        require(sourceLabelIds.isNotEmpty()) { "Verified site/building source labels are empty" }
    }
}

data class ProductionRenderModelInput(
    val assignment: CurrentAuthoritativeAssignmentState,
    val sourceTruth: CandidateSourceTruthState,
    val liveRequest: LiveGeometryVerificationRequest,
    val liveResult: LiveVerificationExecutionResult,
    val topologySignature: TopologySignature,
    val topologyValidation: TopologyValidationReport,
    val overlapDecision: CandidateOverlapDecision,
    val buildingValidation: BuildingValidationReport,
    val labels: List<VerifiedLabelRenderOutput>,
    val dedicatedDetails: List<VerifiedDedicatedDetailGeometry> = emptyList(),
    val splitDetail: VerifiedSplitDetailGeometry? = null,
    val fullPlusDetail: VerifiedFullPlusDetailGeometry? = null,
    val siteBuildingAssignment: VerifiedSiteBuildingAssignmentGeometry? = null
) {
    fun canonicalSha256(): String = sha256(buildString {
        append("assignment=").append(assignment.canonicalSha256()).append('\n')
        append("sourceTruth=").append(sourceTruthCanonical(sourceTruth)).append('\n')
        append("liveRequest=").append(liveRequest.requestFingerprint).append('\n')
        append("liveDecision=").append(liveDecisionCanonical(liveResult.decision)).append('\n')
        liveResult.evidence.sortedBy { it.providerId }.forEach { evidence ->
            append("evidence=").append(evidence.providerId).append('|').append(evidence.requestFingerprint).append('|')
                .append(evidence.available).append('|').append(evidence.observedAtUtc.orEmpty()).append('|')
                .append(evidence.responseSha256.orEmpty()).append('|').append(evidence.sourceDataVintage.orEmpty()).append('|')
                .append(evidence.queriedTargetIds.sorted().joinToString(",")).append('|')
                .append(evidence.confirmedTargetIds.sorted().joinToString(",")).append('|')
                .append(evidence.notFoundTargetIds.sorted().joinToString(",")).append('|')
                .append(evidence.conflictingTargetIds.sorted().joinToString(",")).append('|')
                .append(evidence.unknownMeaningChangingRoads.sorted().joinToString(",")).append('|')
                .append(evidence.siteAddressCrosscheckPassed).append('|').append(evidence.buildingOutlineCrosscheckPassed).append('|')
                .append(evidence.errorCode.orEmpty()).append('\n')
        }
        append("topology=").append(topologyCanonical(topologySignature)).append('\n')
        append("topologyValidation=").append(topologyValidation.passed).append('|')
            .append(topologyValidation.segmentCount).append('|').append(topologyValidation.midSegmentColorTransitionCount).append('|')
            .append(topologyValidation.accessOnlyYellowCount).append('|').append(topologyValidation.failures.sorted().joinToString(";;")).append('\n')
        append("overlap=").append(overlapDecisionCanonical(overlapDecision)).append('\n')
        append("building=").append(buildingValidation.applicable).append('|').append(buildingValidation.passed).append('|')
            .append(buildingValidation.buildingCount).append('|').append(buildingValidation.assignedBuildingCount).append('|')
            .append(buildingValidation.memberLabelCount).append('|').append(buildingValidation.failures.sorted().joinToString(";;")).append('\n')
        labels.sortedBy { it.labelId }.forEach { label ->
            append("label=").append(labelCanonical(label)).append('\n')
        }
        dedicatedDetails.sortedBy { it.detailId }.forEach { detail ->
            append("detail=").append(detail.detailId).append('|')
                .append(number(detail.sourceBounds.left)).append(',').append(number(detail.sourceBounds.top)).append(',').append(number(detail.sourceBounds.right)).append(',').append(number(detail.sourceBounds.bottom)).append('|')
                .append(number(detail.destinationBounds.left)).append(',').append(number(detail.destinationBounds.top)).append(',').append(number(detail.destinationBounds.right)).append(',').append(number(detail.destinationBounds.bottom))
            detail.sourceSegmentIds.sorted().forEach { append("|source:").append(it) }
            detail.renderedRoads.sortedBy { it.segmentId }.forEach { road ->
                append("|road:").append(road.segmentId).append(',').append(road.name).append(',').append(road.status).append(',').append(number(road.widthPt))
                road.points.forEach { p -> append('@').append(number(p.x)).append(',').append(number(p.y)) }
            }
            detail.labelIds.sorted().forEach { append("|label:").append(it) }
            append('\n')
        }
        splitDetail?.let { split ->
            append("split=").append(rectCanonical(split.fullMapRect)).append('|').append(rectCanonical(split.rightRect)).append('|')
                .append(number(split.dividerX)).append('|').append(number(split.detailDividerY)).append('|')
                .append(rectCanonical(split.northDetailRect)).append('|').append(rectCanonical(split.southDetailRect))
            split.sharedSourceSegmentIds.sorted().forEach { append("|shared:").append(it) }
            append('\n')
            fun appendPanel(panel: VerifiedSplitDetailPanelGeometry) {
                append("splitPanel=").append(panel.panelId).append('|').append(rectCanonical(panel.sourceBounds)).append('|').append(rectCanonical(panel.destinationBounds))
                panel.sourceSegmentIds.sorted().forEach { append("|source:").append(it) }
                panel.sourceBuildingIds.sorted().forEach { append("|buildingSource:").append(it) }
                panel.renderedRoads.sortedBy { it.segmentId }.forEach { road ->
                    append("|road:").append(road.segmentId).append(',').append(road.name).append(',').append(road.status).append(',').append(number(road.widthPt))
                    road.points.forEach { p -> append('@').append(number(p.x)).append(',').append(number(p.y)) }
                }
                panel.labelIds.sorted().forEach { append("|label:").append(it) }
                panel.renderedBuildings.sortedBy { it.buildingId }.forEach { b ->
                    append("|building:").append(b.buildingId).append(',').append(b.label).append(',').append(b.housingType).append(',').append(b.assigned)
                    b.polygon.forEach { p -> append('@').append(number(p.x)).append(',').append(number(p.y)) }
                }
                append('\n')
            }
            appendPanel(split.northPanel)
            appendPanel(split.southPanel)
        }
        fullPlusDetail?.let { layout ->
            append("fullPlus=").append(rectCanonical(layout.mainContextRect)).append('\n')
            val panel = layout.detail
            append("fullPlusPanel=").append(panel.panelId).append('|').append(rectCanonical(panel.sourceBounds)).append('|').append(rectCanonical(panel.destinationBounds))
            panel.sourceSegmentIds.sorted().forEach { append("|source:").append(it) }
            panel.sourceBuildingIds.sorted().forEach { append("|buildingSource:").append(it) }
            panel.sourceLabelIds.sorted().forEach { append("|labelSource:").append(it) }
            panel.detailLabelIds.sorted().forEach { append("|detailLabel:").append(it) }
            panel.renderedRoads.sortedBy { it.segmentId }.forEach { road ->
                append("|road:").append(road.segmentId).append(',').append(road.name).append(',').append(road.status).append(',').append(number(road.widthPt))
                road.points.forEach { p -> append('@').append(number(p.x)).append(',').append(number(p.y)) }
            }
            panel.renderedBuildings.sortedBy { it.buildingId }.forEach { b ->
                append("|building:").append(b.buildingId).append(',').append(b.label).append(',').append(b.housingType).append(',').append(b.assigned)
                b.polygon.forEach { p -> append('@').append(number(p.x)).append(',').append(number(p.y)) }
            }
            append('\n')
        }
        siteBuildingAssignment?.let { layout ->
            append("siteBuilding=").append(layout.diagramId).append('|').append(rectCanonical(layout.diagramRect))
            layout.sourceSegmentIds.sorted().forEach { append("|source:").append(it) }
            layout.sourceBuildingIds.sorted().forEach { append("|buildingSource:").append(it) }
            layout.sourceLabelIds.sorted().forEach { append("|labelSource:").append(it) }
            append('\n')
            layout.buildingBindings.sortedBy { it.buildingId }.forEach { binding ->
                append("siteBuildingBinding=").append(binding.buildingId)
                binding.sourceMemberIds.sorted().forEach { append("|member:").append(it) }
                binding.verifiedLabelTexts.sorted().forEach { append("|label:").append(it) }
                append('\n')
            }
            layout.accessInset?.let { inset ->
                append("siteAccess=").append(inset.panelId).append('|').append(rectCanonical(inset.sourceBounds)).append('|').append(rectCanonical(inset.destinationBounds))
                inset.sourceSegmentIds.sorted().forEach { append("|source:").append(it) }
                inset.sourceLabelIds.sorted().forEach { append("|labelSource:").append(it) }
                inset.detailLabelIds.sorted().forEach { append("|detailLabel:").append(it) }
                inset.renderedRoads.sortedBy { it.segmentId }.forEach { road ->
                    append("|road:").append(road.segmentId).append(',').append(road.name).append(',').append(road.status).append(',').append(number(road.widthPt))
                    road.points.forEach { point -> append('@').append(number(point.x)).append(',').append(number(point.y)) }
                }
                append('\n')
            }
        }
    })
}

sealed class RenderModelAdaptationResult {
    abstract val inputSha256: String

    data class Renderable(
        override val inputSha256: String,
        val renderSpec: CandidatePdfRenderSpec,
        val renderSpecSha256: String,
        val validationReceipt: CandidateRenderValidationReceipt,
        val assignmentStateSha256: String
    ) : RenderModelAdaptationResult()

    data class Blocked(
        override val inputSha256: String,
        val reasons: List<String>
    ) : RenderModelAdaptationResult()
}

/**
 * Deterministic bridge from verified territory state into CandidatePdfRenderSpec.
 *
 * It never reads work meaning from GIS. Work status/inside-side/building assignment comes only from the supplied
 * current-authoritative assignment state. Live GIS is consumed only as verification evidence. Legacy KB geometry
 * is never copied into production candidate output.
 */
object ProductionRenderModelAdapter {
    private const val MAP_LEFT = 176.0
    private const val MAP_TOP = 20.0
    private const val MAP_RIGHT = 747.0
    private const val MAP_BOTTOM = 363.0
    private val SPLIT_FULL_MAP_RECT = AxisAlignedRect(176.0, 20.0, 350.0, 365.0)
    private val SPLIT_RIGHT_RECT = AxisAlignedRect(357.0, 20.0, 748.0, 365.0)
    private val SPLIT_NORTH_RECT = AxisAlignedRect(357.0, 20.0, 748.0, 228.0)
    private val SPLIT_SOUTH_RECT = AxisAlignedRect(357.0, 228.0, 748.0, 365.0)
    private val allowedLayouts = setOf("full_map", "split_detail", "full_plus_detail", "site_building_assignment")
    private val multiUnitHousing = setOf("apartment", "condo", "townhome", "mobile_home", "manufactured_home")

    fun adaptProduction(kb: TerritoryKnowledgeBase, input: ProductionRenderModelInput,
        nativeEligibility: NativeAssignmentEligibility = NativeAssignmentEligibility.NONE): RenderModelAdaptationResult =
        adapt(kb, input, nonFieldFixture = false, nativeEligibility = nativeEligibility)

    /** Test-only/non-field route. It still enforces every geometry, source, topology, building and label gate. */
    fun adaptNonFieldFixture(kb: TerritoryKnowledgeBase, input: ProductionRenderModelInput): RenderModelAdaptationResult =
        adapt(kb, input, nonFieldFixture = true)

    private fun adapt(
        kb: TerritoryKnowledgeBase,
        input: ProductionRenderModelInput,
        nonFieldFixture: Boolean,
        nativeEligibility: NativeAssignmentEligibility = NativeAssignmentEligibility.NONE
    ): RenderModelAdaptationResult {
        val inputSha = input.canonicalSha256()
        val failures = mutableListOf<String>()
        val state = input.assignment
        val kbAssignment = kb.assignments[state.displayId]

        if (kbAssignment == null) {
            failures += "unknown territory assignment"
        } else {
            if (state.identity != kbAssignment.identity) failures += "current assignment identity does not match Knowledge Base slot"
            if (state.canonicalFilename != kbAssignment.canonicalFilename) failures += "current assignment canonical filename does not match Knowledge Base"
            if (state.knowledgeBaseRevision != kb.revision) failures += "current assignment Knowledge Base revision mismatch"
            if (!nonFieldFixture && !kbAssignment.needsNewCard) {
                failures += "current approved territory requires exact-artifact passthrough and cannot enter production render-model adaptation"
            }
            if (nonFieldFixture && kbAssignment.needsNewCard) {
                failures += "non-field adapter regression may not use a reserved needs_new_card territory"
            }
        }

        if (kbAssignment != null && state.authoritySha256 !in kbAssignment.sourceHashes) {
            val native = runCatching { nativeEligibility.current(state.displayId, state.authoritySha256) }.getOrNull()
            if (native == null || !native.matches(kb, state, input.sourceTruth)) failures += "Current assignment has no matching active source registration"
        }
        if (state.authorityRole != "current_authoritative_assignment") failures += "current_authoritative_assignment evidence is required"
        if (state.coordinateSpace != RenderCoordinateSpace.LOCKED_R48_PAGE_POINTS_TOP_ORIGIN) failures += "unsupported render coordinate space"
        if (state.layoutMode !in allowedLayouts) failures += "unsupported R48 layout mode '${state.layoutMode}'"
        when (state.layoutMode) {
            "full_map" -> {
                if (input.splitDetail != null) failures += "full_map may not carry split_detail geometry"
                if (input.fullPlusDetail != null) failures += "full_map may not carry full_plus_detail geometry"
                if (input.siteBuildingAssignment != null) failures += "full_map may not carry site_building_assignment geometry"
            }
            "split_detail" -> if (input.splitDetail == null) failures += "split_detail requires mode-specific verified geometry"
            "full_plus_detail" -> if (input.fullPlusDetail == null) failures += "full_plus_detail requires mode-specific verified geometry"
            "site_building_assignment" -> if (input.siteBuildingAssignment == null) failures += "site_building_assignment requires mode-specific verified geometry"
        }
        if (input.sourceTruth.assignmentAuthoritySourceSha256 != state.authoritySha256) failures += "source-truth authority SHA does not match current assignment authority"
        failures += input.sourceTruth.hardFailures(state.displayId)

        if (!LiveGeometryVerificationRequestFactory.fingerprintMatches(input.liveRequest)) failures += "live verification request fingerprint mismatch"
        if (input.liveRequest.territoryDisplayId != state.displayId) failures += "live verification request territory mismatch"
        if (input.liveRequest.candidateSourceSha256 != state.authoritySha256) failures += "live verification candidate source SHA does not match current assignment authority"
        if (input.liveRequest.sourceTruth != input.sourceTruth) failures += "live verification source-truth state does not match adapter input"
        if (input.liveRequest.knowledgeBaseRevision != kb.revision) failures += "live verification Knowledge Base revision mismatch"
        if (input.liveRequest.housingType != state.housingType) failures += "live verification housing type mismatch"
        if (input.liveResult.requestFingerprint != input.liveRequest.requestFingerprint) failures += "live verification result/request fingerprint mismatch"
        if (input.liveResult.evidence.size < 2) failures += "live verification result has fewer than two provider evidence records"
        val liveDecision = input.liveResult.decision
        if (!liveDecision.renderable || liveDecision.disposition != LiveVerificationDisposition.RENDERABLE_VERIFIED) failures += "live geometry verification is not renderable"
        if (!liveDecision.sourceTruthPassed) failures += "live geometry source-truth gate failed"
        if (!liveDecision.providerAgreementPassed) failures += "live geometry provider agreement gate failed"
        if (!liveDecision.assignmentAuthorityPreserved) failures += "live GIS attempted or detected assignment-authority drift"
        if (liveDecision.independentGeometrySourceCount < 2) failures += "live geometry independent-source quorum is below two"
        if (input.liveRequest.jurisdiction.oaklandCountyMichiganApplicable && !liveDecision.oaklandPrimarySatisfied) failures += "Oakland primary GIS requirement not satisfied"

        val computedTopology = TopologyOverlapDecisionEngine.topologySignature(state.roads)
        if (computedTopology != input.topologySignature) failures += "topology output is not bound to current assignment roads"
        val computedTopologyValidation = TopologyOverlapDecisionEngine.validateIntersectionColorRoles(state.roads)
        if (computedTopologyValidation != input.topologyValidation) failures += "topology/color validation output drift"
        if (!computedTopologyValidation.passed) failures += computedTopologyValidation.failures.map { "topology/color: $it" }
        val computedOverlap = TopologyOverlapDecisionEngine.crossTerritoryOverlapCheck(state.identity, computedTopology, kb)
        if (computedOverlap != input.overlapDecision) failures += "cross-territory overlap output is not bound to current assignment topology"
        if (!computedOverlap.passed || computedOverlap.blockingCount > 0) failures += "cross-territory overlap decision contains blocking duplicate work"
        if (computedOverlap.reviewCandidateCount > 0) failures += "cross-territory overlap decision still requires master/live review"
        if (!computedOverlap.hardFailure.isNullOrBlank()) failures += "cross-territory overlap hard failure: ${computedOverlap.hardFailure}"

        validateHousingAndBuildings(state, input, failures)
        validateRoads(state.roads, failures)
        validateDedicatedDetails(state, input.dedicatedDetails, input.labels, failures)
        validateSplitDetail(state, input.splitDetail, input.labels, failures)
        validateFullPlusDetail(state, input.fullPlusDetail, input.labels, failures)
        validateSiteBuildingAssignment(state, input.siteBuildingAssignment, input.labels, failures)
        validateLabels(state, input.labels, input.dedicatedDetails, failures)
        validateLockedFontText(state, input.labels, failures)

        if (failures.isNotEmpty()) return RenderModelAdaptationResult.Blocked(inputSha, failures.distinct())

        val roads = state.roads.sortedBy { it.segmentId }.flatMap { road ->
            road.points.zipWithNext().map { (a, b) ->
                PdfRoadStroke(
                    segmentId = road.segmentId,
                    roadName = road.name,
                    x1 = a.x,
                    topY1 = a.y,
                    x2 = b.x,
                    topY2 = b.y,
                    status = roadStatus(road.status),
                    strokeWidthPt = road.widthPt
                )
            }
        }
        val roadsByIdForRender = state.roads.associateBy { it.segmentId }
        val splitOwnedLabelIds = input.splitDetail?.let { (it.northPanel.labelIds + it.southPanel.labelIds).toSet() }.orEmpty()
        val fullPlusOwnedLabelIds = input.fullPlusDetail?.detail?.detailLabelIds?.toSet().orEmpty()
        val siteAccessOwnedLabelIds = input.siteBuildingAssignment?.accessInset?.detailLabelIds?.toSet().orEmpty()
        val labels = input.labels.filter {
            it.placement.mode != LabelPlacementMode.DEDICATED_DETAIL &&
                it.labelId !in splitOwnedLabelIds && it.labelId !in fullPlusOwnedLabelIds && it.labelId !in siteAccessOwnedLabelIds
        }.sortedBy { it.labelId }.map { label ->
            val road = requireNotNull(roadsByIdForRender[label.segmentId]) { "Verified label targets unknown road ${label.segmentId}" }
            toPdfLabel(label, road)
        }
        val buildings = state.buildings.filter { it.assigned }.sortedBy { it.buildingId }.map(::toPdfBuildingShape)
        val labelByIdForDetail = input.labels.associateBy { it.labelId }
        val dedicatedDetails = input.dedicatedDetails.sortedBy { it.detailId }.map { detail ->
            PdfDedicatedDetail(
                detailId = detail.detailId,
                sourceBounds = PdfDetailRect(detail.sourceBounds.left, detail.sourceBounds.top, detail.sourceBounds.right, detail.sourceBounds.bottom),
                destinationBounds = PdfDetailRect(detail.destinationBounds.left, detail.destinationBounds.top, detail.destinationBounds.right, detail.destinationBounds.bottom),
                sourceSegmentIds = detail.sourceSegmentIds.sorted(),
                roads = detail.renderedRoads.sortedBy { it.segmentId }.flatMap { road ->
                    road.points.zipWithNext().map { (a, b) ->
                        PdfRoadStroke(road.segmentId, road.name, a.x, a.y, b.x, b.y, roadStatus(road.status), road.widthPt)
                    }
                },
                labels = detail.labelIds.sorted().map { id ->
                    val label = requireNotNull(labelByIdForDetail[id]) { "Dedicated detail references missing verified label $id" }
                    PdfStreetLabel(
                        labelId = label.labelId, segmentId = label.segmentId, text = label.text, x = label.baselineX, baselineTopY = label.baselineTopY,
                        rotationDegrees = label.rotationDegrees, fontSizePt = label.fontSizePt, assignedRoadGapPx = label.assignedRoadGapPx,
                        mode = LabelPlacementMode.DEDICATED_DETAIL
                    )
                }
            )
        }

        val labelByIdForSplit = input.labels.associateBy { it.labelId }
        val buildingByIdForSplit = state.buildings.associateBy { it.buildingId }
        val splitDetail = input.splitDetail?.let { split ->
            fun panel(panel: VerifiedSplitDetailPanelGeometry): PdfSplitDetailPanel = PdfSplitDetailPanel(
                panelId = panel.panelId,
                sourceBounds = PdfDetailRect(panel.sourceBounds.left, panel.sourceBounds.top, panel.sourceBounds.right, panel.sourceBounds.bottom),
                destinationBounds = PdfDetailRect(panel.destinationBounds.left, panel.destinationBounds.top, panel.destinationBounds.right, panel.destinationBounds.bottom),
                sourceSegmentIds = panel.sourceSegmentIds.sorted(),
                sourceBuildingIds = panel.sourceBuildingIds.sorted(),
                roads = panel.renderedRoads.sortedBy { it.segmentId }.flatMap { road ->
                    road.points.zipWithNext().map { (a, b) -> PdfRoadStroke(road.segmentId, road.name, a.x, a.y, b.x, b.y, roadStatus(road.status), road.widthPt) }
                },
                labels = panel.labelIds.sorted().map { id ->
                    val label = requireNotNull(labelByIdForSplit[id]) { "Split-detail panel references missing verified label $id" }
                    val baseRoad = requireNotNull(roadsByIdForRender[label.segmentId]) { "Split-detail label targets unknown road ${label.segmentId}" }
                    val renderedRoad = panel.renderedRoads.firstOrNull { it.segmentId == label.segmentId } ?: baseRoad
                    toPdfLabel(label, renderedRoad)
                },
                buildings = panel.renderedBuildings.sortedBy { it.buildingId }.map { b ->
                    requireNotNull(buildingByIdForSplit[b.buildingId]) { "Split-detail panel references unknown building ${b.buildingId}" }
                    toPdfBuildingShape(b)
                }
            )
            PdfSplitDetailLayout(
                fullMapRect = PdfDetailRect(split.fullMapRect.left, split.fullMapRect.top, split.fullMapRect.right, split.fullMapRect.bottom),
                rightRect = PdfDetailRect(split.rightRect.left, split.rightRect.top, split.rightRect.right, split.rightRect.bottom),
                dividerX = split.dividerX,
                detailDividerY = split.detailDividerY,
                northDetailRect = PdfDetailRect(split.northDetailRect.left, split.northDetailRect.top, split.northDetailRect.right, split.northDetailRect.bottom),
                southDetailRect = PdfDetailRect(split.southDetailRect.left, split.southDetailRect.top, split.southDetailRect.right, split.southDetailRect.bottom),
                northPanel = panel(split.northPanel),
                southPanel = panel(split.southPanel),
                sharedSourceSegmentIds = split.sharedSourceSegmentIds.sorted()
            )
        }

        val fullPlusDetail = input.fullPlusDetail?.let { layout ->
            val panel = layout.detail
            val labelById = input.labels.associateBy { it.labelId }
            val buildingById = state.buildings.associateBy { it.buildingId }
            PdfFullPlusDetailLayout(
                mainContextRect = PdfDetailRect(layout.mainContextRect.left, layout.mainContextRect.top, layout.mainContextRect.right, layout.mainContextRect.bottom),
                detail = PdfFullPlusDetailPanel(
                    panelId = panel.panelId,
                    sourceBounds = PdfDetailRect(panel.sourceBounds.left, panel.sourceBounds.top, panel.sourceBounds.right, panel.sourceBounds.bottom),
                    destinationBounds = PdfDetailRect(panel.destinationBounds.left, panel.destinationBounds.top, panel.destinationBounds.right, panel.destinationBounds.bottom),
                    sourceSegmentIds = panel.sourceSegmentIds.sorted(),
                    sourceBuildingIds = panel.sourceBuildingIds.sorted(),
                    sourceLabelIds = panel.sourceLabelIds.sorted(),
                    roads = panel.renderedRoads.sortedBy { it.segmentId }.flatMap { road ->
                        road.points.zipWithNext().map { (a, b) -> PdfRoadStroke(road.segmentId, road.name, a.x, a.y, b.x, b.y, roadStatus(road.status), road.widthPt) }
                    },
                    detailLabels = panel.detailLabelIds.sorted().map { id ->
                        val label = requireNotNull(labelById[id]) { "Full-plus-detail panel references missing verified label $id" }
                        val renderedRoad = panel.renderedRoads.firstOrNull { it.segmentId == label.segmentId }
                            ?: error("Full-plus-detail detail label $id has no rendered road for ${label.segmentId}")
                        toPdfLabel(label, renderedRoad)
                    },
                    buildings = panel.renderedBuildings.sortedBy { it.buildingId }.map { b ->
                        requireNotNull(buildingById[b.buildingId]) { "Full-plus-detail panel references unknown building ${b.buildingId}" }
                        toPdfBuildingShape(b)
                    }
                )
            )
        }

        val siteBuildingAssignment = input.siteBuildingAssignment?.let { layout ->
            val labelById = input.labels.associateBy { it.labelId }
            PdfSiteBuildingAssignmentLayout(
                diagramId = layout.diagramId,
                diagramRect = PdfDetailRect(layout.diagramRect.left, layout.diagramRect.top, layout.diagramRect.right, layout.diagramRect.bottom),
                sourceSegmentIds = layout.sourceSegmentIds.sorted(),
                sourceBuildingIds = layout.sourceBuildingIds.sorted(),
                sourceLabelIds = layout.sourceLabelIds.sorted(),
                buildingBindings = layout.buildingBindings.sortedBy { it.buildingId }.map { binding ->
                    PdfBuildingMemberLabelBinding(
                        buildingId = binding.buildingId,
                        sourceMemberIds = binding.sourceMemberIds.sorted(),
                        verifiedLabelTexts = binding.verifiedLabelTexts.sorted()
                    )
                },
                accessInset = layout.accessInset?.let { inset ->
                    PdfSiteAccessInsetPanel(
                        panelId = inset.panelId,
                        sourceBounds = PdfDetailRect(inset.sourceBounds.left, inset.sourceBounds.top, inset.sourceBounds.right, inset.sourceBounds.bottom),
                        destinationBounds = PdfDetailRect(inset.destinationBounds.left, inset.destinationBounds.top, inset.destinationBounds.right, inset.destinationBounds.bottom),
                        sourceSegmentIds = inset.sourceSegmentIds.sorted(),
                        sourceLabelIds = inset.sourceLabelIds.sorted(),
                        roads = inset.renderedRoads.sortedBy { it.segmentId }.flatMap { road ->
                            road.points.zipWithNext().map { (a, b) ->
                                PdfRoadStroke(road.segmentId, road.name, a.x, a.y, b.x, b.y, roadStatus(road.status), road.widthPt)
                            }
                        },
                        detailLabels = inset.detailLabelIds.sorted().map { id ->
                            val label = requireNotNull(labelById[id]) { "Site access inset references missing verified label $id" }
                            val renderedRoad = inset.renderedRoads.firstOrNull { it.segmentId == label.segmentId }
                                ?: error("Site access detail label $id has no rendered road for ${label.segmentId}")
                            toPdfLabel(label, renderedRoad)
                        }
                    )
                }
            )
        }

        val spec = CandidatePdfRenderSpec(
            identity = state.identity,
            locality = state.locality,
            updated = state.updated,
            directionsLines = state.directionsLines,
            layoutMode = state.layoutMode,
            sourceMasterLabel = state.sourceMasterLabel,
            roads = roads,
            labels = labels,
            buildings = buildings,
            dedicatedDetails = dedicatedDetails,
            splitDetail = splitDetail,
            fullPlusDetail = fullPlusDetail,
            siteBuildingAssignment = siteBuildingAssignment,
            nonFieldFixture = nonFieldFixture
        )
        val receipt = CandidateRenderValidationReceipt(
            displayId = state.displayId,
            canonicalFilename = state.canonicalFilename,
            knowledgeBaseRevision = kb.revision,
            assignmentAuthorityRole = state.authorityRole,
            assignmentAuthoritySha256 = state.authoritySha256,
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
        if (!nonFieldFixture) {
            val authorization = PdfArtifactBoundary.authorizeCandidateRender(kb, receipt)
            if (!authorization.renderAllowed || authorization.action != PdfArtifactAction.VALIDATED_CANDIDATE_RENDER) {
                return RenderModelAdaptationResult.Blocked(
                    inputSha,
                    listOf("PDF artifact boundary rejected adapted production model: ${authorization.reason}")
                )
            }
        }
        return RenderModelAdaptationResult.Renderable(
            inputSha256 = inputSha,
            renderSpec = spec,
            renderSpecSha256 = spec.canonicalSha256(),
            validationReceipt = receipt,
            assignmentStateSha256 = state.canonicalSha256()
        )
    }

    private fun toPdfBuildingShape(building: BuildingGeometry): PdfBuildingShape = PdfBuildingShape(
        buildingId = building.buildingId,
        label = building.label,
        points = building.polygon.map { it.x to it.y },
        labelItems = building.labelItems.map { item ->
            PdfBuildingLabelItem(
                text = item.text,
                centerX = item.center.x,
                centerTopY = item.center.y,
                originX = item.origin?.x,
                originTopY = item.origin?.y,
                rotationDegrees = item.angleDeg,
                fontSizePt = item.fontSizePt
            )
        }
    )

    private fun validateHousingAndBuildings(
        state: CurrentAuthoritativeAssignmentState,
        input: ProductionRenderModelInput,
        failures: MutableList<String>
    ) {
        val multiUnit = state.housingType in multiUnitHousing
        val computed = BuildingValidationEngine.validateBuildings(state.buildings, applicable = multiUnit)
        if (computed != input.buildingValidation) failures += "building-validation output is not bound to current assignment buildings"
        if (!computed.passed) failures += computed.failures.map { "building: $it" }

        if (!multiUnit && state.buildings.isNotEmpty()) {
            failures += "ordinary residential render model may not contain individual building footprints"
        }
        if (multiUnit && state.buildings.isEmpty()) failures += "multi-unit render model requires verified building footprints"
        state.buildings.forEach { building ->
            if (building.polygon.any { !mapPoint(it) }) failures += "${building.buildingId}: building footprint escapes the locked R48 map panel"
            if (building.labelItems.any { !mapPoint(it.center) }) failures += "${building.buildingId}: building label center escapes the locked R48 map panel"
        }
        if (multiUnit) {
            state.buildings.filter { it.assigned }.forEach { building ->
                if (building.housingType != state.housingType) {
                    failures += "${building.buildingId}: building housing type '${building.housingType}' does not match current assignment '${state.housingType}'"
                }
                val normalizedMembers = building.sourceMembers.map(BuildingValidationEngine::normalizeMemberId).sorted()
                val normalizedLabels = building.labelItems.map { BuildingValidationEngine.normalizeMemberId(it.text) }.sorted()
                if (normalizedMembers != normalizedLabels) {
                    failures += "${building.buildingId}: verified 300/301 source-member/label inventory drift"
                }
                if (building.labelItems.size == 1 && building.labelItems.single().text != building.label) {
                    failures += "${building.buildingId}: single-label building display label does not match verified interior label"
                }
                if (building.labelItems.size > 1 && building.labelItems.any { it.origin == null }) {
                    failures += "${building.buildingId}: multi-label single-footprint rendering requires exact verified text origins"
                }
            }
        }
        val memberIds = state.buildings.flatMap { it.sourceMembers }.map(BuildingValidationEngine::normalizeMemberId).distinct().sorted()
        val requestMembers = input.liveRequest.candidateBuildingMemberIds.map(BuildingValidationEngine::normalizeMemberId).distinct().sorted()
        if (memberIds != requestMembers) failures += "live verification building/site inventory does not match current assignment buildings"
        if (input.liveRequest.siteAddressCrosscheckRequired && input.liveResult.decision.siteAddressCrosscheckPassed != true) {
            failures += "required site-address crosscheck has not passed"
        }
        if (input.liveRequest.buildingOutlineCrosscheckRequired && input.liveResult.decision.buildingOutlineCrosscheckPassed != true) {
            failures += "required building-outline crosscheck has not passed"
        }
    }

    private fun validateRoads(roads: List<RoadGeometry>, failures: MutableList<String>) {
        roads.forEach { road ->
            if (road.status !in setOf("green", "yellow", "red")) failures += "${road.segmentId}: unsupported work status '${road.status}'"
            if (road.points.size < 2) failures += "${road.segmentId}: road has insufficient points"
            if (road.points.any { !mapPoint(it) }) failures += "${road.segmentId}: road geometry escapes the locked R48 map panel"
            if (road.widthPt !in 0.5..12.0) failures += "${road.segmentId}: road width is outside renderer-safe range"
        }
    }

    private fun validateDedicatedDetails(
        state: CurrentAuthoritativeAssignmentState,
        details: List<VerifiedDedicatedDetailGeometry>,
        labels: List<VerifiedLabelRenderOutput>,
        failures: MutableList<String>
    ) {
        if (details.map { it.detailId }.distinct().size != details.size) failures += "duplicate verified dedicated-detail IDs"
        val roadsById = state.roads.associateBy { it.segmentId }
        val labelIds = labels.map { it.labelId }.toSet()
        details.forEach { detail ->
            if (!mapRect(detail.sourceBounds)) failures += "${detail.detailId}: source bounds escape locked R48 map panel"
            if (!mapRect(detail.destinationBounds)) failures += "${detail.detailId}: destination bounds escape locked R48 map panel"
            if (detail.destinationBounds.width <= detail.sourceBounds.width || detail.destinationBounds.height <= detail.sourceBounds.height) {
                failures += "${detail.detailId}: dedicated detail must enlarge both dimensions"
            }
            if (detail.sourceSegmentIds.distinct().size != detail.sourceSegmentIds.size) failures += "${detail.detailId}: duplicate source segment IDs"
            detail.sourceSegmentIds.forEach { segmentId ->
                val base = roadsById[segmentId]
                if (base == null) failures += "${detail.detailId}: unknown source segment $segmentId"
                else if (base.points.none { rectContainsPoint(detail.sourceBounds, it) }) failures += "${detail.detailId}: source bounds do not cover $segmentId"
            }
            if (detail.renderedRoads.isEmpty()) failures += "${detail.detailId}: no enlarged road geometry"
            detail.renderedRoads.forEach { road ->
                val base = roadsById[road.segmentId]
                if (road.segmentId !in detail.sourceSegmentIds || base == null) failures += "${detail.detailId}: enlarged road targets undeclared segment ${road.segmentId}"
                else if (road.name != base.name || road.status != base.status) failures += "${detail.detailId}: enlarged road meaning drift for ${road.segmentId}"
                if (road.points.any { !rectContainsPoint(detail.destinationBounds, it) }) failures += "${detail.detailId}: enlarged road escapes destination bounds"
            }
            if (detail.labelIds.distinct().size != detail.labelIds.size) failures += "${detail.detailId}: duplicate detail label IDs"
            detail.labelIds.forEach { if (it !in labelIds) failures += "${detail.detailId}: unknown detail label $it" }
        }
        for (i in details.indices) for (j in i + 1 until details.size) {
            if (details[i].destinationBounds.touchesOrOverlaps(details[j].destinationBounds)) failures += "dedicated-detail destination regions overlap"
        }
    }

    private fun validateSplitDetail(
        state: CurrentAuthoritativeAssignmentState,
        split: VerifiedSplitDetailGeometry?,
        labels: List<VerifiedLabelRenderOutput>,
        failures: MutableList<String>
    ) {
        if (state.layoutMode != "split_detail") {
            if (split != null) failures += "split-detail geometry supplied for non-split layout"
            return
        }
        if (split == null) return
        if (split.fullMapRect != SPLIT_FULL_MAP_RECT) failures += "split_detail full-map rect does not match locked R48 tokens"
        if (split.rightRect != SPLIT_RIGHT_RECT) failures += "split_detail right rect does not match locked R48 tokens"
        if (kotlin.math.abs(split.dividerX - 350.0) > 1e-9) failures += "split_detail divider_x does not match locked 350 pt"
        if (kotlin.math.abs(split.detailDividerY - 228.0) > 1e-9) failures += "split_detail detail_divider_y does not match locked 228 pt"
        if (split.northDetailRect != SPLIT_NORTH_RECT || split.southDetailRect != SPLIT_SOUTH_RECT) failures += "split_detail north/south detail rects do not match locked R48 tokens"
        if (split.northPanel.destinationBounds != SPLIT_NORTH_RECT) failures += "north split panel destination rect drift"
        if (split.southPanel.destinationBounds != SPLIT_SOUTH_RECT) failures += "south split panel destination rect drift"
        if (split.northPanel.panelId == split.southPanel.panelId) failures += "split_detail panel IDs must be unique"
        if (split.sharedSourceSegmentIds.distinct().size != split.sharedSourceSegmentIds.size) failures += "split_detail shared segment IDs must be unique"

        state.roads.forEach { road -> if (road.points.any { !rectContainsPoint(SPLIT_FULL_MAP_RECT, it) }) failures += "${road.segmentId}: split base road escapes full-map rect" }
        state.buildings.filter { it.assigned }.forEach { building -> if (building.polygon.any { !rectContainsPoint(SPLIT_FULL_MAP_RECT, it) }) failures += "${building.buildingId}: split base building escapes full-map rect" }
        val labelById = labels.associateBy { it.labelId }
        val roadById = state.roads.associateBy { it.segmentId }
        val buildingById = state.buildings.associateBy { it.buildingId }
        val northIds = split.northPanel.sourceSegmentIds.toSet()
        val southIds = split.southPanel.sourceSegmentIds.toSet()
        val overlap = northIds intersect southIds
        if (overlap != split.sharedSourceSegmentIds.toSet()) failures += "split_detail ambiguous region ownership: shared segments must be explicitly declared"

        fun validatePanel(panel: VerifiedSplitDetailPanelGeometry, expectedDest: AxisAlignedRect) {
            if (panel.sourceSegmentIds.isEmpty() || panel.sourceSegmentIds.distinct().size != panel.sourceSegmentIds.size) failures += "${panel.panelId}: source segment IDs must be non-empty and unique"
            if (panel.sourceBuildingIds.distinct().size != panel.sourceBuildingIds.size) failures += "${panel.panelId}: source building IDs must be unique"
            if (panel.sourceBounds.left < SPLIT_FULL_MAP_RECT.left || panel.sourceBounds.right > SPLIT_FULL_MAP_RECT.right || panel.sourceBounds.top < SPLIT_FULL_MAP_RECT.top || panel.sourceBounds.bottom > SPLIT_FULL_MAP_RECT.bottom) failures += "${panel.panelId}: source bounds escape split full-map rect"
            if (panel.destinationBounds != expectedDest) failures += "${panel.panelId}: destination bounds do not match locked split region"
            panel.sourceSegmentIds.forEach { id ->
                val base = roadById[id]
                if (base == null) failures += "${panel.panelId}: unknown source segment $id"
                else if (base.points.none { rectContainsPoint(panel.sourceBounds, it) }) failures += "${panel.panelId}: source bounds do not cover $id"
            }
            panel.renderedRoads.forEach { road ->
                val base = roadById[road.segmentId]
                if (road.segmentId !in panel.sourceSegmentIds || base == null) failures += "${panel.panelId}: rendered road targets undeclared segment ${road.segmentId}"
                else if (road.name != base.name || road.status != base.status) failures += "${panel.panelId}: rendered road meaning drift for ${road.segmentId}"
                if (road.points.any { !rectContainsPoint(expectedDest, it) }) failures += "${panel.panelId}: rendered road escapes destination region"
            }
            panel.labelIds.forEach { id ->
                val label = labelById[id]
                if (label == null) failures += "${panel.panelId}: unknown panel label $id"
                else {
                    if (label.segmentId !in panel.sourceSegmentIds) failures += "${panel.panelId}: label $id targets segment outside panel ownership"
                    val points = buildList {
                        add(Point2D(label.baselineX, label.baselineTopY))
                        addAll(label.placement.curvePathPoints())
                        label.placement.callout?.let { c -> add(c.roadAnchor); add(c.tailStart); add(c.labelAttach) }
                    }
                    if (points.any { !rectContainsPoint(expectedDest, it) }) failures += "${panel.panelId}: label $id geometry escapes destination region"
                }
            }
            panel.sourceBuildingIds.forEach { id ->
                val base = buildingById[id]
                if (base == null) failures += "${panel.panelId}: unknown source building $id"
                else if (base.polygon.none { rectContainsPoint(panel.sourceBounds, it) }) failures += "${panel.panelId}: source bounds do not cover building $id"
            }
            panel.renderedBuildings.forEach { building ->
                val base = buildingById[building.buildingId]
                if (building.buildingId !in panel.sourceBuildingIds || base == null) failures += "${panel.panelId}: rendered building targets undeclared source ${building.buildingId}"
                else {
                    if (building.label != base.label || building.housingType != base.housingType || building.assigned != base.assigned) failures += "${panel.panelId}: rendered building meaning drift for ${building.buildingId}"
                    if (building.sourceMembers.sorted() != base.sourceMembers.sorted() || building.labelItems.map { it.text }.sorted() != base.labelItems.map { it.text }.sorted()) {
                        failures += "${panel.panelId}: rendered building 300/301 member-label binding drift for ${building.buildingId}"
                    }
                    val detailBuildingValidation = BuildingValidationEngine.validateBuildings(listOf(building), applicable = true)
                    if (!detailBuildingValidation.passed) failures += detailBuildingValidation.failures.map { "${panel.panelId}: $it" }
                }
                if (building.polygon.any { !rectContainsPoint(expectedDest, it) }) failures += "${panel.panelId}: rendered building escapes destination region"
                if (building.labelItems.any { !rectContainsPoint(expectedDest, it.center) }) failures += "${panel.panelId}: rendered building label escapes destination region"
            }
            if (panel.renderedBuildings.map { it.buildingId }.toSet() != panel.sourceBuildingIds.toSet()) failures += "${panel.panelId}: panel must preserve every declared source building exactly once"
        }
        validatePanel(split.northPanel, SPLIT_NORTH_RECT)
        validatePanel(split.southPanel, SPLIT_SOUTH_RECT)
    }

    private fun validateFullPlusDetail(
        state: CurrentAuthoritativeAssignmentState,
        layout: VerifiedFullPlusDetailGeometry?,
        labels: List<VerifiedLabelRenderOutput>,
        failures: MutableList<String>
    ) {
        if (state.layoutMode != "full_plus_detail") {
            if (layout != null) failures += "full_plus_detail geometry supplied for non-full-plus-detail layout"
            return
        }
        if (layout == null) return

        val main = layout.mainContextRect
        val panel = layout.detail
        fun insideLocked(rect: AxisAlignedRect): Boolean = rect.left >= MAP_LEFT && rect.right <= MAP_RIGHT && rect.top >= MAP_TOP && rect.bottom <= MAP_BOTTOM
        if (!insideLocked(main)) failures += "full_plus_detail main-context rect escapes locked R48 map panel"
        if (!insideLocked(panel.destinationBounds)) failures += "full_plus_detail detail destination escapes locked R48 map panel"
        if (main.touchesOrOverlaps(panel.destinationBounds)) failures += "full_plus_detail main-context/detail rectangles collide"
        if (panel.sourceBounds.left < main.left || panel.sourceBounds.right > main.right || panel.sourceBounds.top < main.top || panel.sourceBounds.bottom > main.bottom) failures += "full_plus_detail source bounds escape main-context rect"
        if (panel.destinationBounds.width <= panel.sourceBounds.width || panel.destinationBounds.height <= panel.sourceBounds.height) failures += "full_plus_detail destination must enlarge source bounds in both dimensions"
        if (panel.sourceSegmentIds.isEmpty() || panel.sourceSegmentIds.distinct().size != panel.sourceSegmentIds.size) failures += "full_plus_detail source segment IDs must be non-empty and unique"
        if (panel.sourceBuildingIds.distinct().size != panel.sourceBuildingIds.size) failures += "full_plus_detail source building IDs must be unique"
        if (panel.sourceLabelIds.isEmpty() || panel.sourceLabelIds.distinct().size != panel.sourceLabelIds.size) failures += "full_plus_detail source label IDs must be non-empty and unique"
        if (panel.detailLabelIds.isEmpty() || panel.detailLabelIds.distinct().size != panel.detailLabelIds.size) failures += "full_plus_detail detail label IDs must be non-empty and unique"
        if ((panel.sourceLabelIds.toSet() intersect panel.detailLabelIds.toSet()).isNotEmpty()) failures += "full_plus_detail source/detail label IDs must be distinct"

        state.roads.forEach { road -> if (road.points.any { !rectContainsPoint(main, it) }) failures += "${road.segmentId}: full_plus_detail base road escapes main-context rect" }
        state.buildings.filter { it.assigned }.forEach { building -> if (building.polygon.any { !rectContainsPoint(main, it) }) failures += "${building.buildingId}: full_plus_detail base building escapes main-context rect" }

        val roadById = state.roads.associateBy { it.segmentId }
        val buildingById = state.buildings.associateBy { it.buildingId }
        val labelById = labels.associateBy { it.labelId }

        fun mapPoint(p: Point2D): Point2D = Point2D(
            panel.destinationBounds.left + (p.x - panel.sourceBounds.left) * panel.destinationBounds.width / panel.sourceBounds.width,
            panel.destinationBounds.top + (p.y - panel.sourceBounds.top) * panel.destinationBounds.height / panel.sourceBounds.height
        )
        fun eq(a: Double, b: Double): Boolean = kotlin.math.abs(a - b) <= 1e-6

        panel.sourceSegmentIds.forEach { id ->
            val base = roadById[id]
            if (base == null) failures += "full_plus_detail unknown source segment $id"
            else if (base.points.any { !rectContainsPoint(panel.sourceBounds, it) }) failures += "full_plus_detail source bounds do not fully bind segment $id"
        }
        panel.sourceBuildingIds.forEach { id ->
            val base = buildingById[id]
            if (base == null) failures += "full_plus_detail unknown source building $id"
            else if (base.polygon.any { !rectContainsPoint(panel.sourceBounds, it) }) failures += "full_plus_detail source bounds do not fully bind building $id"
        }
        panel.sourceLabelIds.forEach { id ->
            val label = labelById[id]
            if (label == null) failures += "full_plus_detail unknown source label $id"
            else {
                if (label.segmentId !in panel.sourceSegmentIds) failures += "full_plus_detail source label $id targets undeclared segment"
                val points = buildList { add(Point2D(label.baselineX, label.baselineTopY)); label.placement.callout?.let { add(it.roadAnchor); add(it.tailStart); add(it.labelAttach) } }
                if (points.any { !rectContainsPoint(panel.sourceBounds, it) }) failures += "full_plus_detail source bounds do not bind label $id"
            }
        }

        val expectedRoads = panel.sourceSegmentIds.mapNotNull { roadById[it] }.sortedBy { it.segmentId }
        val actualRoads = panel.renderedRoads.sortedBy { it.segmentId }
        if (expectedRoads.size != actualRoads.size) failures += "full_plus_detail mapped road count drift"
        expectedRoads.zip(actualRoads).forEach { (base, detail) ->
            if (base.segmentId != detail.segmentId || base.name != detail.name || base.status != detail.status || base.widthPt != detail.widthPt) failures += "full_plus_detail road meaning/status drift for ${base.segmentId}"
            if (base.points.size != detail.points.size) failures += "full_plus_detail road point-count drift for ${base.segmentId}"
            else base.points.zip(detail.points).forEach { (src, dst) ->
                val mapped = mapPoint(src)
                if (!eq(mapped.x, dst.x) || !eq(mapped.y, dst.y)) failures += "full_plus_detail road geometry is not declared source-to-destination mapping for ${base.segmentId}"
            }
            if (detail.points.any { !rectContainsPoint(panel.destinationBounds, it) }) failures += "full_plus_detail rendered road escapes detail destination"
        }

        val expectedBuildings = panel.sourceBuildingIds.mapNotNull { buildingById[it] }.sortedBy { it.buildingId }
        val actualBuildings = panel.renderedBuildings.sortedBy { it.buildingId }
        if (expectedBuildings.size != actualBuildings.size) failures += "full_plus_detail must preserve every declared source building exactly once"
        expectedBuildings.zip(actualBuildings).forEach { (base, detail) ->
            if (base.buildingId != detail.buildingId || base.label != detail.label || base.housingType != detail.housingType || base.assigned != detail.assigned) failures += "full_plus_detail building meaning drift for ${base.buildingId}"
            if (base.sourceMembers.sorted() != detail.sourceMembers.sorted() || base.labelItems.map { it.text }.sorted() != detail.labelItems.map { it.text }.sorted()) {
                failures += "full_plus_detail building 300/301 member-label binding drift for ${base.buildingId}"
            }
            if (base.polygon.size != detail.polygon.size) failures += "full_plus_detail building vertex-count drift for ${base.buildingId}"
            else base.polygon.zip(detail.polygon).forEach { (src, dst) ->
                val mapped = mapPoint(src)
                if (!eq(mapped.x, dst.x) || !eq(mapped.y, dst.y)) failures += "full_plus_detail building geometry is not declared source-to-destination mapping for ${base.buildingId}"
            }
            val baseItems = base.labelItems.sortedBy { it.text }
            val detailItems = detail.labelItems.sortedBy { it.text }
            if (baseItems.size != detailItems.size) failures += "full_plus_detail building label-item count drift for ${base.buildingId}"
            else baseItems.zip(detailItems).forEach { (src, dst) ->
                val mappedCenter = mapPoint(src.center)
                if (src.text != dst.text || src.angleDeg != dst.angleDeg || src.fontSizePt != dst.fontSizePt || !eq(mappedCenter.x, dst.center.x) || !eq(mappedCenter.y, dst.center.y)) {
                    failures += "full_plus_detail building label-item mapping drift for ${base.buildingId}/${src.text}"
                }
                if ((src.origin == null) != (dst.origin == null)) failures += "full_plus_detail building label origin binding drift for ${base.buildingId}/${src.text}"
                if (src.origin != null && dst.origin != null) {
                    val mappedOrigin = mapPoint(src.origin)
                    if (!eq(mappedOrigin.x, dst.origin.x) || !eq(mappedOrigin.y, dst.origin.y)) failures += "full_plus_detail building label origin mapping drift for ${base.buildingId}/${src.text}"
                }
            }
            val detailBuildingValidation = BuildingValidationEngine.validateBuildings(listOf(detail), applicable = true)
            if (!detailBuildingValidation.passed) failures += detailBuildingValidation.failures.map { "full_plus_detail: $it" }
            if (detail.polygon.any { !rectContainsPoint(panel.destinationBounds, it) }) failures += "full_plus_detail rendered building escapes detail destination"
            if (detail.labelItems.any { !rectContainsPoint(panel.destinationBounds, it.center) }) failures += "full_plus_detail rendered building label escapes detail destination"
        }

        val sourceLabels = panel.sourceLabelIds.mapNotNull { labelById[it] }.sortedWith(compareBy({ it.segmentId }, { it.text }, { it.labelId }))
        val detailLabels = panel.detailLabelIds.mapNotNull { labelById[it] }.sortedWith(compareBy({ it.segmentId }, { it.text }, { it.labelId }))
        if (sourceLabels.size != panel.sourceLabelIds.size || detailLabels.size != panel.detailLabelIds.size) failures += "full_plus_detail declared label binding is incomplete"
        if (sourceLabels.size != detailLabels.size) failures += "full_plus_detail source/detail label cardinality mismatch"
        sourceLabels.zip(detailLabels).forEach { (src, dst) ->
            if (src.segmentId != dst.segmentId || src.text != dst.text) failures += "full_plus_detail detail label meaning drift"
            val mapped = mapPoint(Point2D(src.baselineX, src.baselineTopY))
            if (!eq(mapped.x, dst.baselineX) || !eq(mapped.y, dst.baselineTopY)) failures += "full_plus_detail detail label anchor is not declared source-to-destination mapping"
            if (dst.fontSizePt < 8.0) failures += "full_plus_detail detail label font below 8 pt"
            if (dst.placement.mode == LabelPlacementMode.NEARBY_ATTACHED_ARROW_CALLOUT) {
                if (dst.assignedRoadGapPx != 0.0) failures += "full_plus_detail callout label gap must be zero"
            } else if (dst.assignedRoadGapPx !in 2.0..4.0) failures += "full_plus_detail detail label gap outside 2..4 px"
            if (!rectContainsPoint(panel.destinationBounds, Point2D(dst.baselineX, dst.baselineTopY))) failures += "full_plus_detail detail label escapes destination"
        }
    }

    private fun validateSiteBuildingAssignment(
        state: CurrentAuthoritativeAssignmentState,
        layout: VerifiedSiteBuildingAssignmentGeometry?,
        labels: List<VerifiedLabelRenderOutput>,
        failures: MutableList<String>
    ) {
        if (state.layoutMode != "site_building_assignment") {
            if (layout != null) failures += "site_building_assignment geometry supplied for non-site layout"
            return
        }
        if (layout == null) return

        fun insideLocked(rect: AxisAlignedRect): Boolean =
            rect.left >= MAP_LEFT && rect.right <= MAP_RIGHT && rect.top >= MAP_TOP && rect.bottom <= MAP_BOTTOM
        fun eq(a: Double, b: Double): Boolean = kotlin.math.abs(a - b) <= 1e-6
        fun labelPoints(label: VerifiedLabelRenderOutput): List<Point2D> = buildList {
            add(Point2D(label.baselineX, label.baselineTopY))
            label.placement.callout?.let { add(it.roadAnchor); add(it.tailStart); add(it.labelAttach) }
        }

        if (state.housingType !in multiUnitHousing) failures += "site_building_assignment requires multi-unit/site housing"
        if (!insideLocked(layout.diagramRect)) failures += "site_building_assignment diagram rect escapes locked R48 map panel"
        if (layout.sourceSegmentIds.isEmpty() || layout.sourceSegmentIds.distinct().size != layout.sourceSegmentIds.size) {
            failures += "site_building_assignment source segment IDs must be non-empty and unique"
        }
        if (layout.sourceBuildingIds.isEmpty() || layout.sourceBuildingIds.distinct().size != layout.sourceBuildingIds.size) {
            failures += "site_building_assignment source building IDs must be non-empty and unique"
        }
        if (layout.sourceLabelIds.isEmpty() || layout.sourceLabelIds.distinct().size != layout.sourceLabelIds.size) {
            failures += "site_building_assignment source label IDs must be non-empty and unique"
        }

        val accessDetailIds = layout.accessInset?.detailLabelIds?.toSet().orEmpty()
        val baseLabels = labels.filter { it.labelId !in accessDetailIds }
        val roadById = state.roads.associateBy { it.segmentId }
        val assignedBuildings = state.buildings.filter { it.assigned }
        val buildingById = assignedBuildings.associateBy { it.buildingId }
        val baseLabelById = baseLabels.associateBy { it.labelId }
        val allLabelById = labels.associateBy { it.labelId }

        if (layout.sourceSegmentIds.toSet() != roadById.keys) failures += "site_building_assignment must explicitly own every current-assignment road segment"
        if (layout.sourceBuildingIds.toSet() != buildingById.keys) failures += "site_building_assignment must explicitly own every assigned building"
        if (layout.sourceLabelIds.toSet() != baseLabelById.keys) failures += "site_building_assignment must explicitly own every base street label"

        state.roads.forEach { road ->
            if (road.points.any { !rectContainsPoint(layout.diagramRect, it) }) failures += "${road.segmentId}: site_building_assignment base road escapes diagram"
        }
        assignedBuildings.forEach { building ->
            if (building.polygon.any { !rectContainsPoint(layout.diagramRect, it) }) failures += "${building.buildingId}: site_building_assignment building escapes diagram"
            if (building.labelItems.any { !rectContainsPoint(layout.diagramRect, it.center) }) failures += "${building.buildingId}: site_building_assignment building label escapes diagram"
        }
        baseLabels.forEach { label ->
            if (labelPoints(label).any { !rectContainsPoint(layout.diagramRect, it) }) failures += "${label.labelId}: site_building_assignment base label escapes diagram"
        }

        val bindingsById = layout.buildingBindings.associateBy { it.buildingId }
        if (bindingsById.size != layout.buildingBindings.size) failures += "site_building_assignment duplicate building binding IDs"
        if (bindingsById.keys != layout.sourceBuildingIds.toSet()) failures += "site_building_assignment building bindings must exactly cover source buildings"
        layout.sourceBuildingIds.forEach { id ->
            val building = buildingById[id]
            val binding = bindingsById[id]
            if (building == null) {
                failures += "site_building_assignment unknown source building $id"
            } else if (binding == null) {
                failures += "site_building_assignment missing building binding $id"
            } else {
                if (binding.sourceMemberIds.isEmpty() || binding.sourceMemberIds.distinct().size != binding.sourceMemberIds.size) failures += "$id: site source member IDs must be non-empty and unique"
                if (binding.verifiedLabelTexts.isEmpty() || binding.verifiedLabelTexts.distinct().size != binding.verifiedLabelTexts.size) failures += "$id: site verified label texts must be non-empty and unique"
                if (binding.sourceMemberIds.sorted() != building.sourceMembers.sorted()) failures += "$id: 300/301 source-member binding drift"
                val expectedLabels = building.labelItems.map { it.text }.sorted()
                if (binding.verifiedLabelTexts.sorted() != expectedLabels) failures += "$id: 300/301 member/label binding drift"
                if (expectedLabels.size > 1 && building.labelItems.any { it.origin == null }) failures += "$id: multi-label footprint has unverified text origin"
            }
        }

        val inset = layout.accessInset ?: return
        if (!insideLocked(inset.destinationBounds)) failures += "site_building_assignment access destination escapes locked R48 map panel"
        if (!layout.diagramRect.contains(inset.sourceBounds)) failures += "site_building_assignment access source bounds escape diagram"
        if (layout.diagramRect.touchesOrOverlaps(inset.destinationBounds)) failures += "site_building_assignment diagram/access inset rectangles collide"
        if (inset.destinationBounds.width <= inset.sourceBounds.width || inset.destinationBounds.height <= inset.sourceBounds.height) {
            failures += "site_building_assignment access destination must enlarge source bounds in both dimensions"
        }
        if (inset.sourceSegmentIds.isEmpty() || inset.sourceSegmentIds.distinct().size != inset.sourceSegmentIds.size) failures += "site_building_assignment access source segment IDs must be non-empty and unique"
        if (inset.sourceLabelIds.isEmpty() || inset.sourceLabelIds.distinct().size != inset.sourceLabelIds.size) failures += "site_building_assignment access source label IDs must be non-empty and unique"
        if (inset.detailLabelIds.isEmpty() || inset.detailLabelIds.distinct().size != inset.detailLabelIds.size) failures += "site_building_assignment access detail label IDs must be non-empty and unique"
        if ((inset.sourceLabelIds.toSet() intersect inset.detailLabelIds.toSet()).isNotEmpty()) failures += "site_building_assignment access source/detail label IDs must be distinct"
        if (inset.sourceSegmentIds.any { it !in layout.sourceSegmentIds }) failures += "site_building_assignment access inset targets undeclared site segment"

        inset.sourceSegmentIds.forEach { id ->
            val base = roadById[id]
            if (base == null) failures += "site_building_assignment access inset unknown source segment $id"
            else if (base.points.any { !rectContainsPoint(inset.sourceBounds, it) }) failures += "site_building_assignment access source bounds do not fully bind segment $id"
        }
        inset.sourceLabelIds.forEach { id ->
            val label = baseLabelById[id]
            if (label == null) failures += "site_building_assignment access inset unknown source label $id"
            else {
                if (label.segmentId !in inset.sourceSegmentIds) failures += "site_building_assignment access source label $id targets undeclared segment"
                if (labelPoints(label).any { !rectContainsPoint(inset.sourceBounds, it) }) failures += "site_building_assignment access source bounds do not bind label $id"
            }
        }

        fun mapPoint(point: Point2D): Point2D = Point2D(
            inset.destinationBounds.left + (point.x - inset.sourceBounds.left) * inset.destinationBounds.width / inset.sourceBounds.width,
            inset.destinationBounds.top + (point.y - inset.sourceBounds.top) * inset.destinationBounds.height / inset.sourceBounds.height
        )

        val expectedRoads = inset.sourceSegmentIds.mapNotNull { roadById[it] }.sortedBy { it.segmentId }
        val actualRoads = inset.renderedRoads.sortedBy { it.segmentId }
        if (expectedRoads.size != actualRoads.size || actualRoads.map { it.segmentId }.toSet() != inset.sourceSegmentIds.toSet()) failures += "site_building_assignment access mapped-road ownership drift"
        expectedRoads.zip(actualRoads).forEach { (base, detail) ->
            if (base.segmentId != detail.segmentId || base.name != detail.name || base.status != detail.status || base.widthPt != detail.widthPt) failures += "site_building_assignment access road meaning/status drift for ${base.segmentId}"
            if (base.points.size != detail.points.size) failures += "site_building_assignment access road point-count drift for ${base.segmentId}"
            else base.points.zip(detail.points).forEach { (src, dst) ->
                val mapped = mapPoint(src)
                if (!eq(mapped.x, dst.x) || !eq(mapped.y, dst.y)) failures += "site_building_assignment access road geometry is not declared source-to-destination mapping for ${base.segmentId}"
            }
            if (detail.points.any { !rectContainsPoint(inset.destinationBounds, it) }) failures += "site_building_assignment access rendered road escapes destination"
        }

        val sourceLabels = inset.sourceLabelIds.mapNotNull { baseLabelById[it] }.sortedWith(compareBy({ it.segmentId }, { it.text }, { it.labelId }))
        val detailLabels = inset.detailLabelIds.mapNotNull { allLabelById[it] }.sortedWith(compareBy({ it.segmentId }, { it.text }, { it.labelId }))
        if (sourceLabels.size != inset.sourceLabelIds.size || detailLabels.size != inset.detailLabelIds.size) failures += "site_building_assignment access declared label binding is incomplete"
        if (sourceLabels.size != detailLabels.size) failures += "site_building_assignment access source/detail label cardinality mismatch"
        sourceLabels.zip(detailLabels).forEach { (src, dst) ->
            if (src.segmentId != dst.segmentId || src.text != dst.text) failures += "site_building_assignment access detail label meaning drift"
            val mapped = mapPoint(Point2D(src.baselineX, src.baselineTopY))
            if (!eq(mapped.x, dst.baselineX) || !eq(mapped.y, dst.baselineTopY)) failures += "site_building_assignment access label anchor is not declared source-to-destination mapping"
            if (dst.fontSizePt < 8.0) failures += "site_building_assignment access detail label font below 8 pt"
            if (dst.placement.mode == LabelPlacementMode.NEARBY_ATTACHED_ARROW_CALLOUT) {
                if (dst.assignedRoadGapPx != 0.0) failures += "site_building_assignment access callout label gap must be zero"
            } else if (dst.assignedRoadGapPx !in 2.0..4.0) {
                failures += "site_building_assignment access detail label gap outside 2..4 px"
            }
            if (labelPoints(dst).any { !rectContainsPoint(inset.destinationBounds, it) }) failures += "site_building_assignment access detail label escapes destination"
        }
    }

    private fun LabelPlacement.curvePathPoints(): List<Point2D> = emptyList()

    private fun toPdfLabel(label: VerifiedLabelRenderOutput, road: RoadGeometry): PdfStreetLabel {
        val placement = label.placement
        val callout = placement.callout?.let { geometry ->
            PdfArrowCallout(
                roadAnchor = geometry.roadAnchor.x to geometry.roadAnchor.y,
                tailStart = geometry.tailStart.x to geometry.tailStart.y,
                labelAttach = geometry.labelAttach.x to geometry.labelAttach.y,
                lengthPt = geometry.length,
                tailGapPt = geometry.tailGapGeometryUnits
            )
        }
        val curvePath = if (placement.mode == LabelPlacementMode.CURVED_ROAD_FOLLOWING) road.points.map { it.x to it.y } else emptyList()
        val curveOffset = if (placement.mode == LabelPlacementMode.CURVED_ROAD_FOLLOWING) {
            val mid = pointAtPathFractionForAdapter(road.points, 0.5)
            kotlin.math.hypot(placement.center.x - mid.x, placement.center.y - mid.y)
        } else 0.0
        return PdfStreetLabel(
            labelId = label.labelId,
            segmentId = label.segmentId,
            text = label.text,
            x = label.baselineX,
            baselineTopY = label.baselineTopY,
            rotationDegrees = label.rotationDegrees,
            fontSizePt = label.fontSizePt,
            assignedRoadGapPx = if (placement.mode == LabelPlacementMode.NEARBY_ATTACHED_ARROW_CALLOUT) 0.0 else label.assignedRoadGapPx,
            mode = placement.mode,
            curvePath = curvePath,
            curveNormalSign = placement.sideEvidence?.usedSide?.normalSign ?: 0,
            curveBaselineOffsetPt = curveOffset,
            callout = callout
        )
    }

    private fun validateLabels(
        state: CurrentAuthoritativeAssignmentState,
        labels: List<VerifiedLabelRenderOutput>,
        dedicatedDetails: List<VerifiedDedicatedDetailGeometry>,
        failures: MutableList<String>
    ) {
        if (labels.map { it.labelId }.distinct().size != labels.size) failures += "duplicate verified label IDs"
        val roadsById = state.roads.associateBy { it.segmentId }
        labels.forEach { output ->
            val road = roadsById[output.segmentId]
            if (road == null) {
                failures += "${output.labelId}: label targets unknown current-assignment segment ${output.segmentId}"
                return@forEach
            }
            val placement = output.placement
            val lock = placement.navigationLock
            if (placement.requiresReview) failures += "${output.labelId}: label placement still requires review"
            val sourceSegmentIndex = placement.sourceSegmentIndex
            if (sourceSegmentIndex == null || sourceSegmentIndex !in 0 until (road.points.size - 1)) {
                failures += "${output.labelId}: verified placement is not bound to a valid local segment of ${road.segmentId}"
            }
            if (placement.mode !in setOf(
                    LabelPlacementMode.DIRECT_ROAD_FOLLOWING,
                    LabelPlacementMode.SAME_ROAD_RELOCATION,
                    LabelPlacementMode.CURVED_ROAD_FOLLOWING,
                    LabelPlacementMode.NEARBY_ATTACHED_ARROW_CALLOUT,
                    LabelPlacementMode.DEDICATED_DETAIL
                )
            ) failures += "${output.labelId}: renderer does not yet support ${placement.mode.styleToken} without loss of label semantics"
            if (placement.mode == LabelPlacementMode.CURVED_ROAD_FOLLOWING) {
                if (placement.sideEvidence == null) failures += "${output.labelId}: curved label lacks verified side evidence"
                if (road.points.size < 3) failures += "${output.labelId}: curved label target lacks curved path geometry"
            }
            if (placement.mode == LabelPlacementMode.NEARBY_ATTACHED_ARROW_CALLOUT) {
                val c = placement.callout
                if (c == null) failures += "${output.labelId}: attached callout lacks exact leader geometry"
                if (placement.calloutEvidence?.calloutJustified != true) failures += "${output.labelId}: callout is not justified by failed direct/same-road alternatives"
                if (placement.requiresReview) failures += "${output.labelId}: callout remains review-required and cannot render"
            }
            if (placement.mode == LabelPlacementMode.DEDICATED_DETAIL) {
                val owners = dedicatedDetails.filter { output.labelId in it.labelIds }
                if (owners.size != 1) failures += "${output.labelId}: dedicated-detail label must belong to exactly one verified detail region"
                if (placement.callout != null) failures += "${output.labelId}: dedicated-detail label may not carry callout geometry"
            }
            if (lock.labelId != output.labelId || lock.segmentId != output.segmentId) failures += "${output.labelId}: navigation lock/segment binding mismatch"
            if (TopologyOverlapDecisionEngine.normalizeRoadName(lock.streetName) != TopologyOverlapDecisionEngine.normalizeRoadName(road.name)) {
                failures += "${output.labelId}: navigation lock street name does not match target road"
            }
            if (TopologyOverlapDecisionEngine.normalizeRoadName(output.text) != TopologyOverlapDecisionEngine.normalizeRoadName(road.name)) {
                failures += "${output.labelId}: rendered text does not match target road name"
            }
            if (lock.sourceSha256 != state.authoritySha256) failures += "${output.labelId}: navigation lock is not bound to current assignment authority"
            if (placement.mode != LabelPlacementMode.NEARBY_ATTACHED_ARROW_CALLOUT &&
                kotlin.math.abs(output.rotationDegrees - placement.angleDeg) > 1e-6
            ) failures += "${output.labelId}: renderer rotation differs from verified placement"
            if (placement.mode == LabelPlacementMode.NEARBY_ATTACHED_ARROW_CALLOUT) {
                if (output.assignedRoadGapPx != 0.0) failures += "${output.labelId}: callout must be exempt from direct/curved road-gap metric"
            } else {
                val placementGapPx = output.scale.pixels(placement.roadGapGeometryUnits)
                if (kotlin.math.abs(placementGapPx - output.assignedRoadGapPx) > 0.05) failures += "${output.labelId}: renderer gap differs from verified label-engine gap"
                if (output.assignedRoadGapPx !in 2.0..4.0) failures += "${output.labelId}: road gap is outside locked 2..4 px range"
            }
            if (output.fontSizePt < 8.0) failures += "${output.labelId}: label font size is below 8 pt"
            if (!mapPoint(Point2D(output.baselineX, output.baselineTopY))) failures += "${output.labelId}: label baseline escapes locked map panel"
            if (placement.mode != LabelPlacementMode.CURVED_ROAD_FOLLOWING &&
                (output.baselineX < placement.bounds.left - 1e-6 || output.baselineX > placement.bounds.right + 1e-6 ||
                    output.baselineTopY < placement.bounds.top - 1e-6 || output.baselineTopY > placement.bounds.bottom + 1e-6)
            ) failures += "${output.labelId}: renderer baseline is not contained by verified placement bounds"
            if (road.role == "perimeter" && (lock.territoryFacingSide.isNullOrBlank() || lock.exteriorSide.isNullOrBlank())) {
                failures += "${output.labelId}: perimeter label lacks territory-facing/exterior-side lock"
            }
        }

        val namedRoads = state.roads.map { TopologyOverlapDecisionEngine.normalizeRoadName(it.name) }.filter { it.isNotBlank() }.toSet()
        val labeledRoads = labels.map { TopologyOverlapDecisionEngine.normalizeRoadName(it.text) }.filter { it.isNotBlank() }.toSet()
        val missing = namedRoads - labeledRoads
        if (missing.isNotEmpty()) failures += "missing labels for visible named roads: ${missing.sorted().joinToString()}"
    }

    private fun validateLockedFontText(
        state: CurrentAuthoritativeAssignmentState,
        labels: List<VerifiedLabelRenderOutput>,
        failures: MutableList<String>
    ) {
        val text = buildList {
            add(state.identity.displayId)
            add(state.canonicalFilename)
            add(state.locality)
            add(state.updated)
            add(state.sourceMasterLabel)
            addAll(state.directionsLines)
            state.roads.forEach { add(it.name) }
            state.buildings.filter { it.assigned }.forEach { add(it.label) }
            labels.forEach { add(it.text) }
        }
        text.filter { value -> value.any { it.code !in 32..126 } }.forEach { value ->
            failures += "locked embedded-font subset cannot render non-ASCII text: $value"
        }
    }

    private fun rectContainsPoint(rect: AxisAlignedRect, point: Point2D): Boolean = point.x >= rect.left && point.x <= rect.right && point.y >= rect.top && point.y <= rect.bottom
    private fun mapRect(rect: AxisAlignedRect): Boolean = rect.left >= MAP_LEFT && rect.right <= MAP_RIGHT && rect.top >= MAP_TOP && rect.bottom <= MAP_BOTTOM
    private fun mapPoint(point: Point2D): Boolean = point.x in MAP_LEFT..MAP_RIGHT && point.y in MAP_TOP..MAP_BOTTOM

    private fun roadStatus(status: String): PdfRoadStatus = when (status) {
        "yellow" -> PdfRoadStatus.WORK_INSIDE_ONLY
        "green" -> PdfRoadStatus.WORK_BOTH_SIDES
        "red" -> PdfRoadStatus.DO_NOT_WORK
        else -> error("Unsupported work status '$status'")
    }
}

private fun rectCanonical(rect: AxisAlignedRect): String = listOf(rect.left, rect.top, rect.right, rect.bottom).joinToString(",") { number(it) }

private val SHA256_PATTERN = Regex("^[0-9a-f]{64}$")

private fun sourceTruthCanonical(source: CandidateSourceTruthState): String = buildString {
    append(source.sourceTerritoryDisplayId).append('|').append(source.assignmentAuthoritySourceSha256).append('|')
        .append(source.lockedAssignmentReconciled).append('|').append(source.missingExpectedSegmentCount).append('|')
        .append(source.unexpectedMeaningChangingSegmentCount).append('|').append(source.assignmentColorConflictCount).append('|')
        .append(source.unresolvedPerimeterWorkedSideCount).append('|').append(source.illegalMidSegmentColorTransitionCount).append('|')
        .append(source.unresolvedBuildingSiteCount).append('|').append(source.crossTerritoryInferenceUsed).append('|')
        .append(source.styleOnlyGeographyUsed)
}

private fun liveDecisionCanonical(decision: LiveGeometryVerificationDecision): String = buildString {
    append(decision.disposition.name).append('|').append(decision.renderable).append('|')
        .append(decision.independentGeometrySourceCount).append('|').append(decision.qualifyingGeometryProviders.sorted().joinToString(",")).append('|')
        .append(decision.oaklandPrimarySatisfied).append('|').append(decision.sourceTruthPassed).append('|')
        .append(decision.providerAgreementPassed).append('|').append(decision.assignmentAuthorityPreserved).append('|')
        .append(decision.siteAddressCrosscheckPassed).append('|').append(decision.buildingOutlineCrosscheckPassed).append('|')
        .append(decision.blockedReasons.sorted().joinToString(";;"))
}


private fun overlapDecisionCanonical(decision: CandidateOverlapDecision): String = buildString {
    append(decision.status).append('|').append(decision.passed).append('|').append(decision.globalBlockingCount).append('|')
        .append(decision.hardFailure.orEmpty()).append('|').append(decision.note.orEmpty()).append('|').append(decision.permanentPatchCount)
    decision.blocking.sortedWith(compareBy({ it.road }, { it.otherTerritory }, { it.confidence })).forEach { finding ->
        append("|block:").append(overlapFindingCanonical(finding))
    }
    decision.reviewCandidates.sortedWith(compareBy({ it.road }, { it.otherTerritory }, { it.confidence })).forEach { finding ->
        append("|review:").append(overlapFindingCanonical(finding))
    }
    decision.allowed.sortedWith(compareBy({ it.road }, { it.otherTerritory }, { it.confidence })).forEach { finding ->
        append("|allow:").append(overlapFindingCanonical(finding))
    }
}

private fun overlapFindingCanonical(finding: CandidateOverlapFinding): String = buildString {
    append(finding.road).append(',').append(finding.otherTerritory).append(',').append(finding.confidence).append(',')
        .append(finding.candidateStatus.orEmpty()).append(',').append(finding.existingStatus.orEmpty()).append(',').append(finding.reason)
}

private fun labelCanonical(label: VerifiedLabelRenderOutput): String = buildString {
    val placement = label.placement
    val lock = placement.navigationLock
    append(label.labelId).append('|').append(label.segmentId).append('|').append(label.text).append('|')
        .append(number(label.baselineX)).append('|').append(number(label.baselineTopY)).append('|')
        .append(number(label.rotationDegrees)).append('|').append(number(label.fontSizePt)).append('|')
        .append(number(label.assignedRoadGapPx)).append('|').append(number(label.scale.pixelsPerGeometryUnit)).append('|')
        .append(placement.mode.name).append('|').append(number(placement.center.x)).append(',').append(number(placement.center.y)).append('|')
        .append(number(placement.angleDeg)).append('|').append(number(placement.bounds.left)).append(',').append(number(placement.bounds.top)).append(',')
        .append(number(placement.bounds.right)).append(',').append(number(placement.bounds.bottom)).append('|')
        .append(number(placement.roadGapGeometryUnits)).append('|').append(placement.sourceSegmentIndex).append('|')
        .append(placement.requiresReview).append('|').append(placement.reviewNote.orEmpty()).append('|')
        .append(placement.sideEvidence?.usedSide?.name.orEmpty()).append('|')
        .append(placement.callout?.let { c -> listOf(c.roadAnchor.x,c.roadAnchor.y,c.tailStart.x,c.tailStart.y,c.labelAttach.x,c.labelAttach.y,c.length,c.tailGapGeometryUnits).joinToString(",") }.orEmpty()).append('|')
        .append(lock.labelId).append('|').append(lock.streetName).append('|').append(lock.navigationRole).append('|')
        .append(lock.segmentId).append('|').append(lock.sourceEvidence).append('|').append(lock.sourceSha256).append('|')
        .append(lock.territoryFacingSide.orEmpty()).append('|').append(lock.exteriorSide.orEmpty())
}

private fun topologyCanonical(topology: TopologySignature): String = buildString {
    append("count=").append(topology.segmentCount).append('|')
    topology.junctionBoundedSegments.forEach { segment ->
        append(segment.road).append(',').append(segment.status).append(',').append(segment.role).append(',')
            .append(segment.insideSide).append(',').append(segment.accessOnly).append(',')
            .append(segment.endpointAKind).append(',').append(segment.endpointBKind).append(',')
            .append(segment.endpointANeighbors.sorted().joinToString("+")).append(',')
            .append(segment.endpointBNeighbors.sorted().joinToString("+")).append(';')
    }
}

private fun pointAtPathFractionForAdapter(points: List<Point2D>, fraction: Double): Point2D {
    val segments = points.zipWithNext().map { (a, b) -> a to b }
    val total = segments.sumOf { (a, b) -> kotlin.math.hypot(b.x - a.x, b.y - a.y) }
    require(total > 0.0)
    val target = total * fraction.coerceIn(0.0, 1.0)
    var walked = 0.0
    for ((a, b) in segments) {
        val len = kotlin.math.hypot(b.x - a.x, b.y - a.y)
        if (len <= 1e-12) continue
        if (walked + len >= target) {
            val t = (target - walked) / len
            return Point2D(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        }
        walked += len
    }
    return points.last()
}

private fun number(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

package com.koenterprises.territorycardstudio

import androidx.test.core.app.ApplicationProvider
import com.koenterprises.territorycardstudio.core.*
import java.io.File
import java.security.MessageDigest

/** Synthetic 998/999 fixtures live only in androidTest; not a production verification provider. */
internal class Phase2DBFixture(val telephoneMode: Boolean) : AutoCloseable {
    val app = ApplicationProvider.getApplicationContext<TerritoryCardStudioApplication>()
    val original = app.services.knowledgeBase
    val identity = TerritoryIdentity(if (telephoneMode) 998 else 999,
        if (telephoneMode) TerritoryClass.Telephone else TerritoryClass.Residential, 'a')
    val originalSlot = original.assignments.getValue("273")
    val slot = originalSlot.copy(status = "needs_new_card", displayId = identity.displayId, identity = identity,
        baseNumber = identity.baseNumber, cardClass = identity.territoryClass.token, suffix = "a", slot = identity.displayId,
        canonicalFilename = identity.canonicalFilename, needsNewCard = true, newCardApproved = false,
        fieldReleaseAllowedForExactArtifact = false, legacyReferenceFile = "SYNTHETIC-NOT-FOR-FIELD-USE")
    val kb = original.copy(
        assignments = original.assignments - "273" + (identity.displayId to slot),
        referenceRoles = original.referenceRoles + (slot.referenceFile to
            original.referenceRoles.getValue(slot.referenceFile).copy(displayId = identity.displayId, fieldReleaseAllowed = false)))
    val root = File(app.cacheDir, "phase2db-test-${identity.displayId}").apply { deleteRecursively(); check(mkdirs()) }
    val service = AndroidPdfArtifactService(kb, root, app.assets.open("territory/render-authority/Canonical-New-Designed-Template-R48.pdf").use { it.readBytes() })
    val sources = SourceMapIntakeStore(app)
    val coordinator = AndroidBuildWorkflowCoordinator(kb, AndroidRenderModelService(kb), service, sources)
    val mode = if (telephoneMode) WorkspaceMode.TELEPHONE else WorkspaceMode.LETTER_WRITING
    val stamp = "2026-09-27T01:00:00Z"
    val authority = originalSlot.sourceHashes.first()
    val revision = kb.revision
    val source = importSource("original")
    val input = makeInput()
    val inventory: Page2Inventory = if (telephoneMode) Page2Inventory.Telephone(telephone()) else Page2Inventory.LetterWriting(letter())
    fun importSource(suffix: String) = sources.importFromStream(slot, "synthetic-$suffix.pdf", "application/pdf",
        "%PDF-1.4\nSYNTHETIC NOT FOR FIELD USE $suffix".byteInputStream())
    fun prepare() = coordinator.prepare(identity.displayId, mode, source.sha256, input, inventory)
    fun state() = coordinator.state(identity.displayId, mode)
    fun build() = coordinator.buildFront(identity.displayId, mode)
    fun page2() = coordinator.generatePage2(identity.displayId, mode)
    override fun close() { sources.clear(identity.displayId); root.deleteRecursively() }
    private fun makeInput(): ProductionRenderModelInput {
        val policy = app.services.activePolicy
        val approvedRegression = slot
        val authoritySha = authority
        val roads = listOf(
            RoadGeometry(
                segmentId = "adapter-alpha",
                name = "Alpha Rd",
                normalizedName = "alpha rd",
                status = "yellow",
                role = "perimeter",
                insideSide = "left",
                accessOnly = false,
                endpointAKind = "junction",
                endpointBKind = "junction",
                widthPt = 4.0,
                points = listOf(Point2D(225.0, 115.0), Point2D(650.0, 115.0))
            ),
            RoadGeometry(
                segmentId = "adapter-beta",
                name = "Beta Dr",
                normalizedName = "beta dr",
                status = "green",
                role = "interior",
                insideSide = "",
                accessOnly = false,
                endpointAKind = "termination",
                endpointBKind = "termination",
                widthPt = 4.0,
                points = listOf(Point2D(225.0, 200.0), Point2D(650.0, 200.0))
            ),
            RoadGeometry(
                segmentId = "adapter-gamma",
                name = "Gamma Ct",
                normalizedName = "gamma ct",
                status = "red",
                role = "excluded",
                insideSide = "",
                accessOnly = false,
                endpointAKind = "termination",
                endpointBKind = "termination",
                widthPt = 4.0,
                points = listOf(Point2D(225.0, 285.0), Point2D(650.0, 285.0))
            )
        )
        val state = CurrentAuthoritativeAssignmentState(
            displayId = approvedRegression.displayId,
            identity = approvedRegression.identity,
            canonicalFilename = approvedRegression.canonicalFilename,
            knowledgeBaseRevision = kb.revision,
            authorityRole = "current_authoritative_assignment",
            authoritySha256 = authoritySha,
            sourceMasterLabel = "SYNTHETIC-RENDER-MODEL-ADAPTER",
            locality = "Oakland Township",
            updated = "9/25/2026",
            directionsLines = listOf(
                "Directions: Synthetic render-model adapter regression.",
                "No geography or assignment authority is certified.",
                "NOT FOR FIELD USE."
            ),
            layoutMode = "full_map",
            housingType = approvedRegression.housingType,
            coordinateSpace = RenderCoordinateSpace.LOCKED_R48_PAGE_POINTS_TOP_ORIGIN,
            roads = roads,
            buildings = emptyList()
        )
        val sourceTruth = CandidateSourceTruthState(
            sourceTerritoryDisplayId = state.displayId,
            assignmentAuthoritySourceSha256 = authoritySha,
            lockedAssignmentReconciled = true,
            missingExpectedSegmentCount = 0,
            unexpectedMeaningChangingSegmentCount = 0,
            assignmentColorConflictCount = 0,
            unresolvedPerimeterWorkedSideCount = 0,
            illegalMidSegmentColorTransitionCount = 0,
            unresolvedBuildingSiteCount = 0,
            crossTerritoryInferenceUsed = false,
            styleOnlyGeographyUsed = false
        )
        val topology = TopologyOverlapDecisionEngine.topologySignature(roads)
        val topologyValidation = TopologyOverlapDecisionEngine.validateIntersectionColorRoles(roads)
        check(topologyValidation.passed)
        val overlap = TopologyOverlapDecisionEngine.crossTerritoryOverlapCheck(state.identity, topology, kb)
        check(overlap.passed && overlap.reviewCandidateCount == 0)
        val request = LiveGeometryVerificationRequestFactory.create(
            kb = kb,
            policy = policy,
            displayId = state.displayId,
            candidateSourceSha256 = authoritySha,
            candidateTopology = topology,
            candidateBuildingMemberIds = emptyList(),
            sourceTruth = sourceTruth,
            jurisdiction = VerificationJurisdiction("Oakland County", "Michigan", "United States")
        )
        val targetIds = request.roadTargets.mapTo(linkedSetOf()) { it.targetId }
        val evidence = listOf(
            roadEvidence("oakland_county_roads", request, targetIds),
            roadEvidence("census_tigerweb_transportation", request, targetIds)
        )
        val decision = LiveGeometryVerificationReconciler.reconcile(request, policy, evidence)
        check(decision.renderable)
        val liveResult = LiveVerificationExecutionResult(
            requestFingerprint = request.requestFingerprint,
            evidence = evidence,
            decision = decision,
            events = emptyList()
        )
        val labels = listOf(
            label("adapter-label-alpha", roads[0], authoritySha, 330.0, 108.0, territoryFacing = "south", exterior = "north"),
            label("adapter-label-beta", roads[1], authoritySha, 330.0, 193.0),
            label("adapter-label-gamma", roads[2], authoritySha, 330.0, 278.0)
        )
        val buildingReport = BuildingValidationEngine.validateBuildings(emptyList(), applicable = false)
        return ProductionRenderModelInput(
            assignment = state,
            sourceTruth = sourceTruth,
            liveRequest = request,
            liveResult = liveResult,
            topologySignature = topology,
            topologyValidation = topologyValidation,
            overlapDecision = overlap,
            buildingValidation = buildingReport,
            labels = labels
        )

    }

    private fun letter() = LetterWritingAddressInventory(
        identity = identity, assignmentAuthoritySha256 = authority,
        knowledgeBaseRevision = revision, sourceTruthPassed = true,
        provenance = listOf(LetterWritingAddressProvenance("test", "Synthetic authorized source",
            "USER_AUTHORIZED_FIXTURE", stamp, "b".repeat(64), true)),
        records = listOf(LetterWritingAddressRecord("address-1", identity.displayId, "100 Example Way",
            verificationStatus = LetterWritingVerificationStatus.VERIFIED, verifiedAtUtc = stamp,
            boundaryStatus = LetterWritingBoundaryStatus.INSIDE_LOCKED_WORKING_AREA,
            boundaryEvidenceSha256 = "c".repeat(64), provenanceIds = listOf("test")))
    )

    private fun telephone(): TelephoneTerritoryInventory {
        val record = TelephoneTerritoryRecord("phone-1", identity.displayId, "100 Example Way",
            addressVerificationStatus = TelephoneRecordVerificationStatus.VERIFIED,
            addressVerifiedAtUtc = stamp, boundaryStatus = TelephoneBoundaryStatus.INSIDE_LOCKED_WORKING_AREA,
            boundaryEvidenceSha256 = "c".repeat(64), addressProvenanceIds = listOf("test"),
            phoneState = TelephoneNumberState.VERIFIED_NUMBER, phoneNumber = "2025550101",
            phoneVerifiedAtUtc = stamp, phoneRecordBindingVerified = true, phoneProvenanceIds = listOf("test"))
        return TelephoneTerritoryInventory(identity, authority, revision, true,
            listOf(TelephoneProvenance("test", "Synthetic authorized source", "USER_AUTHORIZED_FIXTURE",
                stamp, "b".repeat(64), TelephoneSourceAuthorization.USER_PROVIDED, true, true)),
            listOf(record, record.copy(recordId = "phone-2", streetAddress = "101 Example Way",
                phoneState = TelephoneNumberState.UNAVAILABLE, phoneNumber = null)))
    }

    private fun label(
        id: String,
        road: RoadGeometry,
        authoritySha: String,
        baselineX: Double,
        baselineTopY: Double,
        territoryFacing: String? = null,
        exterior: String? = null
    ): VerifiedLabelRenderOutput {
        val lock = LabelNavigationLock(
            labelId = id,
            streetName = road.name,
            navigationRole = if (road.role == "perimeter") "perimeter_run" else "road_run",
            segmentId = road.segmentId,
            sourceEvidence = "synthetic current-authority adapter fixture",
            sourceSha256 = authoritySha,
            territoryFacingSide = territoryFacing,
            exteriorSide = exterior
        )
        val bounds = AxisAlignedRect(baselineX, baselineTopY - 9.0, baselineX + 70.0, baselineTopY + 1.0)
        val placement = LabelPlacement(
            mode = LabelPlacementMode.DIRECT_ROAD_FOLLOWING,
            center = bounds.center,
            angleDeg = 0.0,
            bounds = bounds,
            roadGapGeometryUnits = 3.0,
            sourceSegmentIndex = 0,
            navigationLock = lock,
            requiresReview = false
        )
        return VerifiedLabelRenderOutput(
            labelId = id,
            segmentId = road.segmentId,
            text = road.name,
            baselineX = baselineX,
            baselineTopY = baselineTopY,
            rotationDegrees = 0.0,
            fontSizePt = 9.0,
            assignedRoadGapPx = 3.0,
            scale = LabelLayoutScale(1.0),
            placement = placement
        )
    }

    private fun roadEvidence(
        providerId: String,
        request: LiveGeometryVerificationRequest,
        targetIds: Set<String>
    ) = ProviderVerificationEvidence(
        providerId = providerId,
        requestFingerprint = request.requestFingerprint,
        available = true,
        observedAtUtc = "2026-09-25T16:00:00Z",
        responseSha256 = sha256("$providerId|${request.requestFingerprint}"),
        sourceDataVintage = null,
        queriedTargetIds = targetIds,
        confirmedTargetIds = targetIds,
        notFoundTargetIds = emptySet(),
        conflictingTargetIds = emptySet(),
        unknownMeaningChangingRoads = emptySet(),
        siteAddressCrosscheckPassed = null,
        buildingOutlineCrosscheckPassed = null,
        errorCode = null
    )

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

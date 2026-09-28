package com.koenterprises.territorycardstudio.core

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Deterministic R48 candidate PDF writer.
 *
 * The writer never reconstructs or substitutes the locked font/image resources. It starts from the
 * byte-locked R48 template PDF and appends an incremental PDF revision that replaces only the page
 * content stream and Info dictionary. The original page MediaBox (768 x 480.5 pt), embedded DejaVu
 * resources, and locked A33 directions badge therefore remain the rendering authority.
 */
object CandidatePdfRenderer {
    private const val PAGE_W = 768.0
    private const val PAGE_H = 480.5
    private const val INFO_OBJECT = 18
    private const val CONTENT_OBJECT = 20
    private const val ROOT_OBJECT = 17
    private const val PDF_SIZE = 21
    private const val REGULAR_FONT_OBJECT = 8
    private const val ID_FONT_OBJECT = 12
    private const val CAR_XOBJECT = "FormXob.dbb823e6e4d953b83cd8868ba2ab9525"
    private var currentTemplateMetrics: TemplateFontMetrics? = null

    fun renderCandidate(
        templatePdf: ByteArray,
        authorization: PdfArtifactAuthorization,
        spec: CandidatePdfRenderSpec
    ): CandidatePdfRenderResult {
        require(authorization.action == PdfArtifactAction.VALIDATED_CANDIDATE_RENDER) {
            "Candidate PDF render requires VALIDATED_CANDIDATE_RENDER authorization"
        }
        require(authorization.renderAllowed) { "Candidate renderer was not authorized" }
        require(authorization.displayId == spec.identity.displayId) { "Authorization/spec territory identity mismatch" }
        require(authorization.canonicalFilename == spec.identity.canonicalFilename) { "Authorization/spec canonical filename mismatch" }
        require(!spec.nonFieldFixture) { "Production candidate API cannot render fixture-only content" }
        return renderInternal(templatePdf, spec)
    }

    /** Regression-only API. Output is visibly marked NOT FOR FIELD USE and cannot enter release flow. */
    fun renderNonFieldFixture(templatePdf: ByteArray, spec: CandidatePdfRenderSpec): CandidatePdfRenderResult {
        require(spec.nonFieldFixture) { "Fixture renderer requires nonFieldFixture=true" }
        return renderInternal(templatePdf, spec)
    }

    private fun renderInternal(templatePdf: ByteArray, spec: CandidatePdfRenderSpec): CandidatePdfRenderResult {
        val templateHash = sha256(templatePdf)
        require(templateHash == LockedPdfRendererAuthorityHashes.TEMPLATE_PDF_SHA256) {
            "Locked template PDF SHA-256 mismatch: $templateHash"
        }
        validateSpec(spec)
        val metrics = TemplateFontMetrics.parse(templatePdf)
        val specSha = spec.canonicalSha256()
        currentTemplateMetrics = metrics
        val content = try { buildContent(spec, specSha, metrics).toByteArray(Charsets.ISO_8859_1) } finally { currentTemplateMetrics = null }
        val output = appendIncrementalRevision(templatePdf, content, spec, specSha)
        val pdfSha = sha256(output)
        val exactValidation = CandidatePdfExactValidator.validate(output, spec, specSha)
        require(exactValidation.passed) { "Generated candidate PDF failed internal exact-PDF validation: ${exactValidation.errors.joinToString()}" }
        return CandidatePdfRenderResult(
            pdfBytes = output,
            renderSpecSha256 = specSha,
            pdfSha256 = pdfSha,
            exactValidation = exactValidation
        )
    }

    private fun validateSpec(spec: CandidatePdfRenderSpec) {
        require(spec.locality.isNotBlank()) { "Locality is required" }
        require(spec.updated.isNotBlank()) { "Updated date is required" }
        require(spec.layoutMode in setOf("full_map", "split_detail", "full_plus_detail", "site_building_assignment")) {
            "Unsupported locked layout mode: ${spec.layoutMode}"
        }
        require(spec.directionsLines.isNotEmpty() && spec.directionsLines.size <= 3) { "1..3 directions lines required" }
        val strings = buildList {
            add(spec.identity.displayId)
            add(spec.identity.canonicalFilename)
            add(spec.locality)
            add(spec.updated)
            addAll(spec.directionsLines)
            spec.sourceMasterLabel?.let(::add)
            spec.roads.forEach { add(it.roadName) }
            spec.labels.forEach { add(it.text) }
            spec.buildings.forEach { building -> add(building.label); building.labelItems.forEach { add(it.text) } }
            spec.dedicatedDetails.forEach { detail ->
                detail.roads.forEach { add(it.roadName) }
                detail.labels.forEach { add(it.text) }
            }
            spec.fullPlusDetail?.detail?.let { detail ->
                detail.roads.forEach { add(it.roadName) }
                detail.detailLabels.forEach { add(it.text) }
                detail.buildings.forEach { building -> add(building.label); building.labelItems.forEach { add(it.text) } }
            }
            spec.siteBuildingAssignment?.let { layout ->
                layout.buildingBindings.forEach { binding ->
                    binding.sourceMemberIds.forEach(::add)
                    binding.verifiedLabelTexts.forEach(::add)
                }
                layout.accessInset?.let { inset ->
                    inset.roads.forEach { add(it.roadName) }
                    inset.detailLabels.forEach { add(it.text) }
                }
            }
        }
        strings.forEach { text -> require(text.all { it.code in 32..126 }) { "R48 embedded template font subset supports printable ASCII only: $text" } }
        spec.roads.forEach { r ->
            require(r.strokeWidthPt in 0.5..12.0) { "Road stroke width outside safe renderer range" }
            require(pagePoint(r.x1, r.topY1) && pagePoint(r.x2, r.topY2)) { "Road point outside locked page" }
        }
        spec.labels.forEach { l ->
            require(l.fontSizePt >= 8.0) { "Street label font must remain >=8 pt" }
            if (l.mode == LabelPlacementMode.NEARBY_ATTACHED_ARROW_CALLOUT) {
                require(l.assignedRoadGapPx == 0.0) { "Callout labels are exempt from direct/curved road-gap metric" }
                require(l.callout != null) { "Callout label requires exact leader geometry" }
            } else {
                require(l.assignedRoadGapPx in 2.0..4.0) { "Street label gap must remain in locked 2..4 px band" }
            }
            if (l.mode == LabelPlacementMode.CURVED_ROAD_FOLLOWING) {
                require(l.curvePath.size >= 2) { "Curved label requires path geometry" }
                require(l.curveNormalSign in setOf(-1, 1)) { "Curved label requires normal sign" }
                require(l.curveBaselineOffsetPt > 0.0) { "Curved label baseline offset must be positive" }
                require(l.curvePath.all { pagePoint(it.first, it.second) }) { "Curved label path escapes locked page" }
            } else require(pagePoint(l.x, l.baselineTopY)) { "Street label anchor outside locked page" }
        }
        spec.buildings.forEach { b ->
            require(b.points.size >= 3) { "Building polygon requires >=3 points" }
            require(b.points.all { pagePoint(it.first, it.second) }) { "Building polygon point outside locked page" }
            validateBuildingLabelItems(b, requireVerifiedItems = spec.layoutMode == "site_building_assignment")
        }
        val detailsById = spec.dedicatedDetails.associateBy { it.detailId }
        require(detailsById.size == spec.dedicatedDetails.size) { "Duplicate dedicated-detail IDs" }
        val baseRoadsById = spec.roads.groupBy { it.segmentId }
        val baseBuildingsById = spec.buildings.associateBy { it.buildingId }
        validateSplitDetailSpec(spec, baseRoadsById, baseBuildingsById)
        validateFullPlusDetailSpec(spec, baseRoadsById, baseBuildingsById)
        validateSiteBuildingAssignmentSpec(spec, baseRoadsById, baseBuildingsById)
        spec.dedicatedDetails.forEach { detail ->
            require(detail.detailId.isNotBlank()) { "Dedicated-detail ID is blank" }
            require(detail.sourceBounds.isInsideLockedMap()) { "${detail.detailId}: source bounds escape locked map panel" }
            require(detail.destinationBounds.isInsideLockedMap()) { "${detail.detailId}: destination bounds escape locked map panel" }
            require(detail.destinationBounds.width > detail.sourceBounds.width && detail.destinationBounds.height > detail.sourceBounds.height) {
                "${detail.detailId}: dedicated detail must enlarge both dimensions"
            }
            require(detail.sourceSegmentIds.isNotEmpty() && detail.sourceSegmentIds.distinct().size == detail.sourceSegmentIds.size) {
                "${detail.detailId}: source segment IDs must be non-empty and unique"
            }
            detail.sourceSegmentIds.forEach { segmentId ->
                val base = baseRoadsById[segmentId].orEmpty()
                require(base.isNotEmpty()) { "${detail.detailId}: source segment '$segmentId' is not present in the base map" }
                require(base.any { detail.sourceBounds.contains(it.x1, it.topY1) || detail.sourceBounds.contains(it.x2, it.topY2) }) {
                    "${detail.detailId}: source bounds do not cover '$segmentId'"
                }
            }
            require(detail.roads.isNotEmpty()) { "${detail.detailId}: dedicated detail contains no road geometry" }
            detail.roads.forEach { road ->
                require(road.segmentId in detail.sourceSegmentIds) { "${detail.detailId}: detail road targets undeclared source segment ${road.segmentId}" }
                val base = baseRoadsById.getValue(road.segmentId).first()
                require(base.roadName == road.roadName && base.status == road.status) { "${detail.detailId}: detail road meaning drift for ${road.segmentId}" }
                require(detail.destinationBounds.contains(road.x1, road.topY1) && detail.destinationBounds.contains(road.x2, road.topY2)) {
                    "${detail.detailId}: detail road escapes destination bounds"
                }
            }
            require(detail.labels.isNotEmpty()) { "${detail.detailId}: dedicated detail contains no labels" }
            detail.labels.forEach { label ->
                require(label.mode == LabelPlacementMode.DEDICATED_DETAIL) { "${detail.detailId}: detail label must use dedicated_detail mode" }
                require(label.segmentId in detail.sourceSegmentIds) { "${detail.detailId}: detail label targets undeclared segment ${label.segmentId}" }
                require(label.assignedRoadGapPx in 2.0..4.0) { "${detail.detailId}: detail label gap must remain in locked 2..4 px band" }
                require(label.callout == null && label.curvePath.isEmpty()) { "${detail.detailId}: dedicated-detail label must use explicit straight inset geometry" }
                require(detail.destinationBounds.contains(label.x, label.baselineTopY)) { "${detail.detailId}: detail label anchor escapes destination bounds" }
                val base = baseRoadsById.getValue(label.segmentId).first()
                require(label.text == base.roadName) { "${detail.detailId}: detail label text does not match source road" }
            }
        }
        spec.dedicatedDetails.zipWithNext().forEach { (a, c) ->
            require(!a.destinationBounds.overlaps(c.destinationBounds)) { "Dedicated-detail destination regions overlap" }
        }
    }

    private fun validateSplitDetailSpec(
        spec: CandidatePdfRenderSpec,
        baseRoadsById: Map<String, List<PdfRoadStroke>>,
        baseBuildingsById: Map<String, PdfBuildingShape>
    ) {
        val split = spec.splitDetail
        if (spec.layoutMode != "split_detail") {
            require(split == null) { "Split-detail geometry is only valid with layoutMode=split_detail" }
            return
        }
        requireNotNull(split) { "split_detail requires explicit verified split geometry" }
        require(spec.dedicatedDetails.isEmpty()) { "split_detail may not silently mix dedicated-detail geometry" }
        val expectedFull = PdfDetailRect(176.0, 20.0, 350.0, 365.0)
        val expectedRight = PdfDetailRect(357.0, 20.0, 748.0, 365.0)
        val expectedNorth = PdfDetailRect(357.0, 20.0, 748.0, 228.0)
        val expectedSouth = PdfDetailRect(357.0, 228.0, 748.0, 365.0)
        require(split.fullMapRect == expectedFull) { "split_detail full-map rect must match locked R48 tokens" }
        require(split.rightRect == expectedRight) { "split_detail right rect must match locked R48 tokens" }
        require(kotlin.math.abs(split.dividerX - 350.0) < 1e-9) { "split_detail divider_x must equal locked 350 pt" }
        require(kotlin.math.abs(split.detailDividerY - 228.0) < 1e-9) { "split_detail detail_divider_y must equal locked 228 pt" }
        require(split.northDetailRect == expectedNorth && split.southDetailRect == expectedSouth) {
            "split_detail north/south rects must match locked R48 tokens"
        }
        require(split.northPanel.destinationBounds == expectedNorth) { "north split panel destination must equal locked north detail rect" }
        require(split.southPanel.destinationBounds == expectedSouth) { "south split panel destination must equal locked south detail rect" }
        require(split.northPanel.panelId != split.southPanel.panelId) { "split_detail panel IDs must be unique" }
        require(split.sharedSourceSegmentIds.distinct().size == split.sharedSourceSegmentIds.size) { "split_detail shared source IDs must be unique" }

        spec.roads.forEach { road ->
            require(expectedFull.contains(road.x1, road.topY1) && expectedFull.contains(road.x2, road.topY2)) {
                "split_detail base road ${road.segmentId} escapes full-map rect"
            }
        }
        spec.labels.forEach { label ->
            require(labelPointSet(label).all { expectedFull.contains(it.first, it.second) }) {
                "split_detail base label ${label.labelId} escapes full-map rect"
            }
        }
        spec.buildings.forEach { building ->
            require(building.points.all { expectedFull.contains(it.first, it.second) }) {
                "split_detail base building ${building.buildingId} escapes full-map rect"
            }
        }

        val northIds = split.northPanel.sourceSegmentIds.toSet()
        val southIds = split.southPanel.sourceSegmentIds.toSet()
        val overlap = northIds intersect southIds
        require(overlap == split.sharedSourceSegmentIds.toSet()) {
            "split_detail ambiguous source ownership: shared segment IDs must exactly declare north/south overlap"
        }
        require(split.sharedSourceSegmentIds.all { it in baseRoadsById }) { "split_detail shared source segment is not present in base map" }
        validateSplitPanel(split.northPanel, expectedFull, baseRoadsById, baseBuildingsById)
        validateSplitPanel(split.southPanel, expectedFull, baseRoadsById, baseBuildingsById)
    }

    private fun validateFullPlusDetailSpec(
        spec: CandidatePdfRenderSpec,
        baseRoadsById: Map<String, List<PdfRoadStroke>>,
        baseBuildingsById: Map<String, PdfBuildingShape>
    ) {
        val layout = spec.fullPlusDetail
        if (spec.layoutMode != "full_plus_detail") {
            require(layout == null) { "full_plus_detail geometry is only valid with layoutMode=full_plus_detail" }
            return
        }
        requireNotNull(layout) { "full_plus_detail requires explicit verified geometry" }
        require(spec.splitDetail == null) { "full_plus_detail may not carry split_detail geometry" }
        require(spec.dedicatedDetails.isEmpty()) { "full_plus_detail may not silently mix generic dedicated-detail geometry" }
        require(layout.mainContextRect.isInsideLockedMap()) { "full_plus_detail main-context rect escapes locked R48 map panel" }
        require(layout.detail.destinationBounds.isInsideLockedMap()) { "full_plus_detail detail rect escapes locked R48 map panel" }
        require(!layout.mainContextRect.overlaps(layout.detail.destinationBounds)) { "full_plus_detail main-context/detail rectangles overlap" }
        require(layout.detail.sourceBounds.left >= layout.mainContextRect.left && layout.detail.sourceBounds.right <= layout.mainContextRect.right &&
            layout.detail.sourceBounds.top >= layout.mainContextRect.top && layout.detail.sourceBounds.bottom <= layout.mainContextRect.bottom) {
            "full_plus_detail source bounds escape main-context rect"
        }
        require(layout.detail.destinationBounds.width > layout.detail.sourceBounds.width && layout.detail.destinationBounds.height > layout.detail.sourceBounds.height) {
            "full_plus_detail destination must enlarge source bounds in both dimensions"
        }

        spec.roads.forEach { road ->
            require(layout.mainContextRect.contains(road.x1, road.topY1) && layout.mainContextRect.contains(road.x2, road.topY2)) {
                "full_plus_detail base road ${road.segmentId} escapes main-context rect"
            }
        }
        spec.labels.forEach { label ->
            require(labelPointSet(label).all { layout.mainContextRect.contains(it.first, it.second) }) {
                "full_plus_detail base label ${label.labelId} escapes main-context rect"
            }
        }
        spec.buildings.forEach { building ->
            require(building.points.all { layout.mainContextRect.contains(it.first, it.second) }) {
                "full_plus_detail base building ${building.buildingId} escapes main-context rect"
            }
        }

        val panel = layout.detail
        require(panel.panelId.isNotBlank()) { "full_plus_detail detail panel ID is blank" }
        require(panel.sourceSegmentIds.isNotEmpty() && panel.sourceSegmentIds.distinct().size == panel.sourceSegmentIds.size) {
            "full_plus_detail source segment IDs must be non-empty and unique"
        }
        require(panel.sourceBuildingIds.distinct().size == panel.sourceBuildingIds.size) { "full_plus_detail source building IDs must be unique" }
        require(panel.sourceLabelIds.isNotEmpty() && panel.sourceLabelIds.distinct().size == panel.sourceLabelIds.size) { "full_plus_detail source label IDs must be non-empty and unique" }
        require(panel.detailLabels.map { it.labelId }.distinct().size == panel.detailLabels.size) { "full_plus_detail detail label IDs must be unique" }

        val baseLabelsById = spec.labels.associateBy { it.labelId }
        panel.sourceSegmentIds.forEach { segmentId ->
            val base = baseRoadsById[segmentId].orEmpty()
            require(base.isNotEmpty()) { "full_plus_detail unknown source segment $segmentId" }
            require(base.all { panel.sourceBounds.contains(it.x1, it.topY1) && panel.sourceBounds.contains(it.x2, it.topY2) }) {
                "full_plus_detail source bounds do not fully bind segment $segmentId"
            }
        }
        panel.sourceBuildingIds.forEach { buildingId ->
            val base = baseBuildingsById[buildingId] ?: error("full_plus_detail unknown source building $buildingId")
            require(base.points.all { panel.sourceBounds.contains(it.first, it.second) }) {
                "full_plus_detail source bounds do not fully bind building $buildingId"
            }
        }
        panel.sourceLabelIds.forEach { labelId ->
            val base = baseLabelsById[labelId] ?: error("full_plus_detail unknown source label $labelId")
            require(base.segmentId in panel.sourceSegmentIds) { "full_plus_detail source label $labelId targets undeclared source segment" }
            require(labelPointSet(base).all { panel.sourceBounds.contains(it.first, it.second) }) { "full_plus_detail source bounds do not bind label $labelId" }
        }

        fun mapX(x: Double): Double = panel.destinationBounds.left + (x - panel.sourceBounds.left) * panel.destinationBounds.width / panel.sourceBounds.width
        fun mapY(y: Double): Double = panel.destinationBounds.top + (y - panel.sourceBounds.top) * panel.destinationBounds.height / panel.sourceBounds.height
        fun eq(a: Double, b: Double): Boolean = kotlin.math.abs(a - b) <= 1e-6

        val expectedRoads = panel.sourceSegmentIds.flatMap { baseRoadsById.getValue(it) }.sortedWith(compareBy<PdfRoadStroke>({ it.segmentId }, { it.x1 }, { it.topY1 }, { it.x2 }, { it.topY2 }))
        val actualRoads = panel.roads.sortedWith(compareBy<PdfRoadStroke>({ it.segmentId }, { it.x1 }, { it.topY1 }, { it.x2 }, { it.topY2 }))
        require(actualRoads.size == expectedRoads.size) { "full_plus_detail mapped road count drift" }
        expectedRoads.zip(actualRoads).forEach { (base, detail) ->
            require(base.segmentId == detail.segmentId && base.roadName == detail.roadName && base.status == detail.status) { "full_plus_detail road meaning/status drift for ${base.segmentId}" }
            require(eq(detail.x1, mapX(base.x1)) && eq(detail.topY1, mapY(base.topY1)) && eq(detail.x2, mapX(base.x2)) && eq(detail.topY2, mapY(base.topY2))) {
                "full_plus_detail road geometry is not the declared source-to-destination mapping for ${base.segmentId}"
            }
            require(panel.destinationBounds.contains(detail.x1, detail.topY1) && panel.destinationBounds.contains(detail.x2, detail.topY2)) { "full_plus_detail road escaped detail rect" }
        }

        val expectedBuildings = panel.sourceBuildingIds.map { baseBuildingsById.getValue(it) }.sortedBy { it.buildingId }
        val actualBuildings = panel.buildings.sortedBy { it.buildingId }
        require(actualBuildings.size == expectedBuildings.size) { "full_plus_detail must preserve every declared source building exactly once" }
        expectedBuildings.zip(actualBuildings).forEach { (base, detail) ->
            require(base.buildingId == detail.buildingId && base.label == detail.label && base.assigned == detail.assigned) { "full_plus_detail building meaning drift for ${base.buildingId}" }
            require(base.points.size == detail.points.size) { "full_plus_detail building vertex-count drift for ${base.buildingId}" }
            base.points.zip(detail.points).forEach { (src, dst) ->
                require(eq(dst.first, mapX(src.first)) && eq(dst.second, mapY(src.second))) { "full_plus_detail building geometry is not declared mapping for ${base.buildingId}" }
            }
        }

        require(panel.detailLabels.size == panel.sourceLabelIds.size) { "full_plus_detail source/detail label cardinality mismatch" }
        val sourceLabels = panel.sourceLabelIds.map { baseLabelsById.getValue(it) }.sortedWith(compareBy({ it.segmentId }, { it.text }, { it.labelId }))
        val detailLabels = panel.detailLabels.sortedWith(compareBy({ it.segmentId }, { it.text }, { it.labelId }))
        sourceLabels.zip(detailLabels).forEach { (base, detail) ->
            require(base.segmentId == detail.segmentId && base.text == detail.text) { "full_plus_detail detail label meaning drift" }
            require(eq(detail.x, mapX(base.x)) && eq(detail.baselineTopY, mapY(base.baselineTopY))) { "full_plus_detail label anchor is not declared source-to-destination mapping" }
            require(detail.fontSizePt >= 8.0) { "full_plus_detail label font below 8 pt" }
            if (detail.mode == LabelPlacementMode.NEARBY_ATTACHED_ARROW_CALLOUT) require(detail.assignedRoadGapPx == 0.0)
            else require(detail.assignedRoadGapPx in 2.0..4.0) { "full_plus_detail label gap outside 2..4 px" }
            require(labelPointSet(detail).all { panel.destinationBounds.contains(it.first, it.second) }) { "full_plus_detail label escaped detail rect" }
        }
    }

    private fun validateSiteBuildingAssignmentSpec(
        spec: CandidatePdfRenderSpec,
        baseRoadsById: Map<String, List<PdfRoadStroke>>,
        baseBuildingsById: Map<String, PdfBuildingShape>
    ) {
        val layout = spec.siteBuildingAssignment
        if (spec.layoutMode != "site_building_assignment") {
            require(layout == null) { "site_building_assignment geometry is only valid with layoutMode=site_building_assignment" }
            return
        }
        requireNotNull(layout) { "site_building_assignment requires explicit verified site/building geometry" }
        require(spec.dedicatedDetails.isEmpty()) { "site_building_assignment may not silently mix generic dedicated-detail geometry" }
        require(spec.splitDetail == null) { "site_building_assignment may not carry split_detail geometry" }
        require(spec.fullPlusDetail == null) { "site_building_assignment may not carry full_plus_detail geometry" }
        require(layout.diagramId.isNotBlank()) { "site_building_assignment diagram ID is blank" }
        require(layout.diagramRect.isInsideLockedMap()) { "site_building_assignment diagram rect escapes locked R48 map panel" }
        require(layout.sourceSegmentIds.isNotEmpty() && layout.sourceSegmentIds.distinct().size == layout.sourceSegmentIds.size) {
            "site_building_assignment source segment IDs must be non-empty and unique"
        }
        require(layout.sourceBuildingIds.isNotEmpty() && layout.sourceBuildingIds.distinct().size == layout.sourceBuildingIds.size) {
            "site_building_assignment source building IDs must be non-empty and unique"
        }
        require(layout.sourceLabelIds.isNotEmpty() && layout.sourceLabelIds.distinct().size == layout.sourceLabelIds.size) {
            "site_building_assignment source label IDs must be non-empty and unique"
        }
        require(layout.sourceSegmentIds.toSet() == baseRoadsById.keys) { "site_building_assignment must explicitly own every base road segment" }
        require(layout.sourceBuildingIds.toSet() == baseBuildingsById.keys) { "site_building_assignment must explicitly own every rendered building" }
        require(layout.sourceLabelIds.toSet() == spec.labels.map { it.labelId }.toSet()) { "site_building_assignment must explicitly own every base street label" }

        spec.roads.forEach { road ->
            require(layout.diagramRect.contains(road.x1, road.topY1) && layout.diagramRect.contains(road.x2, road.topY2)) {
                "site_building_assignment base road ${road.segmentId} escapes site diagram"
            }
        }
        spec.labels.forEach { label ->
            require(labelPointSet(label).all { layout.diagramRect.contains(it.first, it.second) }) {
                "site_building_assignment base label ${label.labelId} escapes site diagram"
            }
        }
        spec.buildings.forEach { building ->
            require(building.points.all { layout.diagramRect.contains(it.first, it.second) }) {
                "site_building_assignment building ${building.buildingId} escapes site diagram"
            }
        }

        val bindingsById = layout.buildingBindings.associateBy { it.buildingId }
        require(bindingsById.size == layout.buildingBindings.size) { "site_building_assignment duplicate building binding IDs" }
        require(bindingsById.keys == layout.sourceBuildingIds.toSet()) { "site_building_assignment building bindings must exactly cover source building IDs" }
        layout.sourceBuildingIds.forEach { buildingId ->
            val base = baseBuildingsById[buildingId] ?: error("site_building_assignment unknown source building $buildingId")
            val binding = bindingsById.getValue(buildingId)
            require(binding.sourceMemberIds.isNotEmpty() && binding.sourceMemberIds.distinct().size == binding.sourceMemberIds.size) {
                "site_building_assignment $buildingId source member IDs must be non-empty and unique"
            }
            require(binding.verifiedLabelTexts.isNotEmpty() && binding.verifiedLabelTexts.distinct().size == binding.verifiedLabelTexts.size) {
                "site_building_assignment $buildingId verified building labels must be non-empty and unique"
            }
            require(base.labelItems.isNotEmpty()) { "site_building_assignment $buildingId requires verified interior label items" }
            require(binding.verifiedLabelTexts.sorted() == base.labelItems.map { it.text }.sorted()) {
                "site_building_assignment $buildingId verified label binding drift"
            }
            if (base.labelItems.size > 1) {
                require(base.labelItems.all { it.originX != null && it.originTopY != null }) {
                    "site_building_assignment $buildingId multi-label footprint requires exact verified text origins"
                }
            }
        }

        val inset = layout.accessInset ?: return
        require(inset.panelId.isNotBlank()) { "site_building_assignment access inset panel ID is blank" }
        require(inset.sourceBounds.left >= layout.diagramRect.left && inset.sourceBounds.right <= layout.diagramRect.right &&
            inset.sourceBounds.top >= layout.diagramRect.top && inset.sourceBounds.bottom <= layout.diagramRect.bottom) {
            "site_building_assignment access source bounds escape site diagram"
        }
        require(inset.destinationBounds.isInsideLockedMap()) { "site_building_assignment access inset escapes locked R48 map panel" }
        require(!layout.diagramRect.overlaps(inset.destinationBounds)) { "site_building_assignment diagram/access inset rectangles overlap" }
        require(inset.destinationBounds.width > inset.sourceBounds.width && inset.destinationBounds.height > inset.sourceBounds.height) {
            "site_building_assignment access inset must enlarge source bounds in both dimensions"
        }
        require(inset.sourceSegmentIds.isNotEmpty() && inset.sourceSegmentIds.distinct().size == inset.sourceSegmentIds.size) {
            "site_building_assignment access source segment IDs must be non-empty and unique"
        }
        require(inset.sourceSegmentIds.all { it in layout.sourceSegmentIds }) { "site_building_assignment access inset targets undeclared site segment" }
        require(inset.sourceLabelIds.isNotEmpty() && inset.sourceLabelIds.distinct().size == inset.sourceLabelIds.size) {
            "site_building_assignment access source label IDs must be non-empty and unique"
        }
        require(inset.detailLabels.map { it.labelId }.distinct().size == inset.detailLabels.size) { "site_building_assignment access detail label IDs must be unique" }
        val baseLabelsById = spec.labels.associateBy { it.labelId }
        inset.sourceSegmentIds.forEach { segmentId ->
            val base = baseRoadsById[segmentId].orEmpty()
            require(base.isNotEmpty()) { "site_building_assignment access inset unknown source segment $segmentId" }
            require(base.all { inset.sourceBounds.contains(it.x1, it.topY1) && inset.sourceBounds.contains(it.x2, it.topY2) }) {
                "site_building_assignment access source bounds do not fully bind segment $segmentId"
            }
        }
        inset.sourceLabelIds.forEach { labelId ->
            val base = baseLabelsById[labelId] ?: error("site_building_assignment access inset unknown source label $labelId")
            require(base.segmentId in inset.sourceSegmentIds) { "site_building_assignment access source label $labelId targets undeclared access segment" }
            require(labelPointSet(base).all { inset.sourceBounds.contains(it.first, it.second) }) {
                "site_building_assignment access source bounds do not bind label $labelId"
            }
        }
        fun mapX(x: Double): Double = inset.destinationBounds.left + (x - inset.sourceBounds.left) * inset.destinationBounds.width / inset.sourceBounds.width
        fun mapY(y: Double): Double = inset.destinationBounds.top + (y - inset.sourceBounds.top) * inset.destinationBounds.height / inset.sourceBounds.height
        fun eq(a: Double, b: Double): Boolean = kotlin.math.abs(a - b) <= 1e-6
        val expectedRoads = inset.sourceSegmentIds.flatMap { baseRoadsById.getValue(it) }
            .sortedWith(compareBy<PdfRoadStroke>({ it.segmentId }, { it.x1 }, { it.topY1 }, { it.x2 }, { it.topY2 }))
        val actualRoads = inset.roads.sortedWith(compareBy<PdfRoadStroke>({ it.segmentId }, { it.x1 }, { it.topY1 }, { it.x2 }, { it.topY2 }))
        require(actualRoads.size == expectedRoads.size) { "site_building_assignment access mapped road count drift" }
        expectedRoads.zip(actualRoads).forEach { (base, detail) ->
            require(base.segmentId == detail.segmentId && base.roadName == detail.roadName && base.status == detail.status && base.strokeWidthPt == detail.strokeWidthPt) {
                "site_building_assignment access road meaning/status drift for ${base.segmentId}"
            }
            require(eq(detail.x1, mapX(base.x1)) && eq(detail.topY1, mapY(base.topY1)) && eq(detail.x2, mapX(base.x2)) && eq(detail.topY2, mapY(base.topY2))) {
                "site_building_assignment access road geometry is not declared source-to-destination mapping for ${base.segmentId}"
            }
            require(inset.destinationBounds.contains(detail.x1, detail.topY1) && inset.destinationBounds.contains(detail.x2, detail.topY2)) {
                "site_building_assignment access road escaped inset destination"
            }
        }
        require(inset.detailLabels.size == inset.sourceLabelIds.size) { "site_building_assignment access source/detail label cardinality mismatch" }
        val sourceLabels = inset.sourceLabelIds.map { baseLabelsById.getValue(it) }.sortedWith(compareBy({ it.segmentId }, { it.text }, { it.labelId }))
        val detailLabels = inset.detailLabels.sortedWith(compareBy({ it.segmentId }, { it.text }, { it.labelId }))
        sourceLabels.zip(detailLabels).forEach { (base, detail) ->
            require(base.segmentId == detail.segmentId && base.text == detail.text) { "site_building_assignment access detail label meaning drift" }
            require(eq(detail.x, mapX(base.x)) && eq(detail.baselineTopY, mapY(base.baselineTopY))) { "site_building_assignment access label anchor is not declared source-to-destination mapping" }
            require(detail.fontSizePt >= 8.0) { "site_building_assignment access label font below 8 pt" }
            if (detail.mode == LabelPlacementMode.NEARBY_ATTACHED_ARROW_CALLOUT) require(detail.assignedRoadGapPx == 0.0)
            else require(detail.assignedRoadGapPx in 2.0..4.0) { "site_building_assignment access label gap outside 2..4 px" }
            require(labelPointSet(detail).all { inset.destinationBounds.contains(it.first, it.second) }) { "site_building_assignment access label escaped inset destination" }
        }
    }

    private fun validateSplitPanel(
        panel: PdfSplitDetailPanel,
        fullMapRect: PdfDetailRect,
        baseRoadsById: Map<String, List<PdfRoadStroke>>,
        baseBuildingsById: Map<String, PdfBuildingShape>
    ) {
        require(panel.panelId.isNotBlank()) { "split_detail panel ID is blank" }
        require(panel.sourceBounds.left >= fullMapRect.left && panel.sourceBounds.right <= fullMapRect.right &&
            panel.sourceBounds.top >= fullMapRect.top && panel.sourceBounds.bottom <= fullMapRect.bottom) {
            "${panel.panelId}: source bounds escape split full-map rect"
        }
        require(panel.sourceSegmentIds.isNotEmpty() && panel.sourceSegmentIds.distinct().size == panel.sourceSegmentIds.size) {
            "${panel.panelId}: source segment IDs must be non-empty and unique"
        }
        require(panel.sourceBuildingIds.distinct().size == panel.sourceBuildingIds.size) { "${panel.panelId}: source building IDs must be unique" }
        panel.sourceSegmentIds.forEach { segmentId ->
            val base = baseRoadsById[segmentId].orEmpty()
            require(base.isNotEmpty()) { "${panel.panelId}: unknown source segment $segmentId" }
            require(base.any { panel.sourceBounds.contains(it.x1, it.topY1) || panel.sourceBounds.contains(it.x2, it.topY2) }) {
                "${panel.panelId}: source bounds do not cover $segmentId"
            }
        }
        panel.sourceBuildingIds.forEach { buildingId ->
            val base = baseBuildingsById[buildingId] ?: error("${panel.panelId}: unknown source building $buildingId")
            require(base.points.any { panel.sourceBounds.contains(it.first, it.second) }) {
                "${panel.panelId}: source bounds do not cover building $buildingId"
            }
        }
        require(panel.roads.isNotEmpty()) { "${panel.panelId}: split panel has no rendered road geometry" }
        panel.roads.forEach { road ->
            require(road.segmentId in panel.sourceSegmentIds) { "${panel.panelId}: rendered road targets undeclared source segment ${road.segmentId}" }
            val base = baseRoadsById.getValue(road.segmentId).first()
            require(base.roadName == road.roadName && base.status == road.status) { "${panel.panelId}: rendered road meaning drift for ${road.segmentId}" }
            require(panel.destinationBounds.contains(road.x1, road.topY1) && panel.destinationBounds.contains(road.x2, road.topY2)) {
                "${panel.panelId}: rendered road escapes destination panel"
            }
        }
        panel.labels.forEach { label ->
            require(label.segmentId in panel.sourceSegmentIds) { "${panel.panelId}: label targets undeclared source segment ${label.segmentId}" }
            val base = baseRoadsById.getValue(label.segmentId).first()
            require(label.text == base.roadName) { "${panel.panelId}: label text does not match source road" }
            require(label.fontSizePt >= 8.0) { "${panel.panelId}: label font below 8 pt" }
            if (label.mode == LabelPlacementMode.NEARBY_ATTACHED_ARROW_CALLOUT) require(label.assignedRoadGapPx == 0.0)
            else require(label.assignedRoadGapPx in 2.0..4.0) { "${panel.panelId}: label gap outside 2..4 px" }
            require(labelPointSet(label).all { panel.destinationBounds.contains(it.first, it.second) }) {
                "${panel.panelId}: label geometry escapes destination panel"
            }
        }
        panel.buildings.forEach { building ->
            require(building.buildingId in panel.sourceBuildingIds) { "${panel.panelId}: rendered building targets undeclared source building ${building.buildingId}" }
            val base = baseBuildingsById.getValue(building.buildingId)
            require(building.label == base.label && building.assigned == base.assigned) { "${panel.panelId}: building label meaning drift for ${building.buildingId}" }
            require(building.points.all { panel.destinationBounds.contains(it.first, it.second) }) {
                "${panel.panelId}: rendered building escapes destination panel"
            }
        }
        require(panel.buildings.map { it.buildingId }.toSet() == panel.sourceBuildingIds.toSet()) {
            "${panel.panelId}: split panel must preserve every declared source building exactly once"
        }
    }

    private fun labelPointSet(label: PdfStreetLabel): List<Pair<Double, Double>> = buildList {
        add(label.x to label.baselineTopY)
        addAll(label.curvePath)
        label.callout?.let { c -> add(c.roadAnchor); add(c.tailStart); add(c.labelAttach) }
    }

    private fun validateBuildingLabelItems(building: PdfBuildingShape, requireVerifiedItems: Boolean = false) {
        if (requireVerifiedItems) require(building.labelItems.isNotEmpty()) {
            "${building.buildingId}: verified building label items are required"
        }
        if (building.labelItems.isEmpty()) return
        require(building.labelItems.map { it.text }.distinct().size == building.labelItems.size) {
            "${building.buildingId}: duplicate verified building label text"
        }
        building.labelItems.forEach { item ->
            require(item.text.isNotBlank()) { "${building.buildingId}: blank verified building label text" }
            require(item.fontSizePt > 0.0) { "${building.buildingId}: invalid verified building label font size" }
            require(pagePoint(item.centerX, item.centerTopY)) { "${building.buildingId}: building label center outside locked page" }
            require(pointInPolygon(item.centerX, item.centerTopY, building.points)) {
                "${building.buildingId}: building label '${item.text}' center escapes physical footprint"
            }
            require((item.originX == null) == (item.originTopY == null)) {
                "${building.buildingId}: building label '${item.text}' has incomplete verified origin"
            }
            if (item.originX != null && item.originTopY != null) {
                require(pagePoint(item.originX, item.originTopY)) {
                    "${building.buildingId}: building label '${item.text}' origin outside locked page"
                }
            }
        }
        if (building.labelItems.size > 1) {
            require(building.labelItems.all { it.originX != null && it.originTopY != null }) {
                "${building.buildingId}: multi-label single-footprint rendering requires exact verified origins"
            }
        }
    }

    private fun pointInPolygon(x: Double, y: Double, polygon: List<Pair<Double, Double>>): Boolean {
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val (xi, yi) = polygon[i]
            val (xj, yj) = polygon[j]
            val cross = (x - xi) * (yj - yi) - (y - yi) * (xj - xi)
            val dot = (x - xi) * (x - xj) + (y - yi) * (y - yj)
            if (kotlin.math.abs(cross) <= 1e-7 && dot <= 1e-7) return true
            val denom = (yj - yi).takeIf { kotlin.math.abs(it) > 1e-12 } ?: 1e-12
            if (((yi > y) != (yj > y)) && x < (xj - xi) * (y - yi) / denom + xi) inside = !inside
            j = i
        }
        return inside
    }

    private fun pagePoint(x: Double, topY: Double): Boolean = x in 0.0..PAGE_W && topY in 0.0..PAGE_H

    private fun buildContent(spec: CandidatePdfRenderSpec, specSha: String, metrics: TemplateFontMetrics): String {
        val b = StringBuilder(16384)
        b.append("% TCS_RENDER_SPEC_SHA256 ").append(specSha).append('\n')
        b.append("% TCS_LOCKED_TEMPLATE_SHA256 ").append(LockedPdfRendererAuthorityHashes.TEMPLATE_PDF_SHA256).append('\n')
        // White page background, then locked shell.
        fillColor(b, "#FFFFFF"); rect(b, 0.0, 0.0, PAGE_W, PAGE_H, fill = true, stroke = false)
        roundedRect(b, 9.0, topToY(9.0, 463.0), 146.0, 463.0, 11.0, "#10212B", null, 0.0)
        roundedRect(b, 164.0, topToY(9.0, 366.0), 595.0, 366.0, 10.0, "#FFFFFF", "#D6E0E4", 0.6)
        roundedRect(b, 164.0, topToY(385.0, 87.0), 595.0, 87.0, 10.0, "#FFFFFF", "#D6E0E4", 0.6)

        val localityLines = wrapLocality(spec.locality, metrics)
        val twoLine = localityLines.size > 1
        strokeColor(b, "#70818A"); lineWidth(b, 0.6)
        line(b, 28.0, PAGE_H - (if (twoLine) 167.0 else 154.0), 136.0, PAGE_H - (if (twoLine) 167.0 else 154.0))
        line(b, 28.0, PAGE_H - 369.0, 136.0, PAGE_H - 369.0)

        fillColor(b, "#FFFFFF")
        text(b, "F2+0", 12.0, 28.0, PAGE_H - 55.0, "TERRITORY")
        var idSize = 40.0
        while (idSize > 28.0 && metrics.idWidth(spec.identity.displayId, idSize) > 117.0) idSize -= 0.5
        require(metrics.idWidth(spec.identity.displayId, idSize) <= 117.0) { "Territory ID cannot fit locked 117 pt box at >=28 pt" }
        text(b, "F3+0", idSize, 28.0, PAGE_H - 119.0, spec.identity.displayId)
        localityLines.take(2).forEachIndexed { i, line -> text(b, "F2+0", 12.5, 28.0, PAGE_H - (147.0 + i * 15.0), line) }
        text(b, "F4+0", 11.0, 28.0, PAGE_H - ((if (twoLine) 174.0 else 162.0) + 15.0), "LEGEND")

        swatch(b, 28.0, 198.0, "#FFDC18", "Work Inside Only", 201.0)
        swatch(b, 28.0, 242.0, "#51C72B", "Work Both Sides", 245.0)
        swatch(b, 28.0, 286.0, "#FF1435", "Do Not Work", 289.0)
        drawCalendar(b)
        fillColor(b, "#FFFFFF")
        text(b, "F4+0", 10.0, 28.0, PAGE_H - 439.0, "UPDATED")
        text(b, "F2+0", 11.0, 28.0, PAGE_H - 457.0, spec.updated)

        // Reuse locked A33 image XObject from the template resources.
        b.append("q 44 0 0 45 177 ").append(num(topToY(411.0, 45.0))).append(" cm /").append(CAR_XOBJECT).append(" Do Q\n")
        drawCompass(b)

        // Map layer: all coordinates are absolute page points with top-origin input.
        if (spec.layoutMode == "split_detail") {
            drawSplitDetailLayout(b, requireNotNull(spec.splitDetail), spec)
        } else if (spec.layoutMode == "full_plus_detail") {
            drawFullPlusDetailLayout(b, requireNotNull(spec.fullPlusDetail), spec)
        } else if (spec.layoutMode == "site_building_assignment") {
            drawSiteBuildingAssignmentLayout(b, requireNotNull(spec.siteBuildingAssignment), spec)
        } else {
            b.append("q 176 ").append(num(topToY(20.0, 343.0))).append(" 571 343 re W n\n")
            spec.buildings.forEach { drawBuilding(b, it) }
            spec.roads.forEach { drawRoad(b, it) }
            spec.labels.forEach { drawStreetLabel(b, it) }
            spec.dedicatedDetails.forEach { drawDedicatedDetail(b, it) }
            b.append("Q\n")
        }

        drawDirections(b, spec.directionsLines)
        if (spec.nonFieldFixture) drawFixtureWatermark(b)
        return b.toString()
    }

    private fun wrapLocality(locality: String, metrics: TemplateFontMetrics): List<String> {
        val out = mutableListOf<String>()
        var current = ""
        locality.trim().split(Regex("\\s+")).forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (metrics.regularWidth(candidate, 12.5) <= 108.0) current = candidate else {
                if (current.isNotEmpty()) out += current
                current = word
            }
        }
        if (current.isNotEmpty()) out += current
        require(out.size <= 2) { "Locality exceeds locked two-line variant" }
        return out
    }

    private fun drawRoad(b: StringBuilder, r: PdfRoadStroke) {
        strokeColor(b, r.status.hex)
        lineWidth(b, r.strokeWidthPt)
        b.append("1 J 1 j\n")
        line(b, r.x1, PAGE_H - r.topY1, r.x2, PAGE_H - r.topY2)
    }

    private fun drawStreetLabel(b: StringBuilder, l: PdfStreetLabel) {
        fillColor(b, "#27343C")
        when (l.mode) {
            LabelPlacementMode.CURVED_ROAD_FOLLOWING -> drawCurvedStreetLabel(b, l)
            LabelPlacementMode.NEARBY_ATTACHED_ARROW_CALLOUT -> drawCalloutStreetLabel(b, l)
            else -> drawStraightStreetLabel(b, l)
        }
    }

    private fun drawStraightStreetLabel(b: StringBuilder, l: PdfStreetLabel) {
        val angle = Math.toRadians(l.rotationDegrees)
        val c = cos(angle); val s = sin(angle)
        b.append("BT /F2+0 ").append(num(l.fontSizePt)).append(" Tf ")
            .append(num(c)).append(' ').append(num(s)).append(' ')
            .append(num(-s)).append(' ').append(num(c)).append(' ')
            .append(num(l.x)).append(' ').append(num(PAGE_H - l.baselineTopY)).append(" Tm (")
            .append(pdfEscape(l.text)).append(") Tj ET\n")
    }

    private fun drawCurvedStreetLabel(b: StringBuilder, l: PdfStreetLabel) {
        require(l.curvePath.size >= 2) { "Curved label requires >=2 path points" }
        require(l.curveNormalSign in setOf(-1, 1)) { "Curved label requires verified normal sign" }
        require(l.curveBaselineOffsetPt > 0.0) { "Curved label requires positive verified baseline offset" }
        var path = l.curvePath
        var sign = l.curveNormalSign
        val midpoint = pointAndTangentAtDistance(path, polylineLength(path) / 2.0)
        var pdfAngle = -atan2(midpoint.ty, midpoint.tx) * 180.0 / PI
        if (pdfAngle > 90.0 || pdfAngle < -90.0) {
            path = path.asReversed()
            sign = -sign
        }
        val metrics = currentTemplateMetrics ?: error("Curved renderer font metrics unavailable")
        val textWidth = metrics.regularWidth(l.text, l.fontSizePt)
        val pathLength = polylineLength(path)
        require(pathLength > textWidth) { "Curved path cannot hold complete label" }
        var distanceAlong = (pathLength - textWidth) / 2.0
        for (ch in l.text) {
            val pt = pointAndTangentAtDistance(path, distanceAlong)
            val nx = -pt.ty
            val ny = pt.tx
            val x = pt.x + nx * l.curveBaselineOffsetPt * sign
            val topY = pt.y + ny * l.curveBaselineOffsetPt * sign
            val angle = -atan2(pt.ty, pt.tx)
            val c = cos(angle); val s = sin(angle)
            b.append("BT /F2+0 ").append(num(l.fontSizePt)).append(" Tf ")
                .append(num(c)).append(' ').append(num(s)).append(' ')
                .append(num(-s)).append(' ').append(num(c)).append(' ')
                .append(num(x)).append(' ').append(num(PAGE_H - topY)).append(" Tm (")
                .append(pdfEscape(ch.toString())).append(") Tj ET\n")
            distanceAlong += metrics.regularAdvance(ch, l.fontSizePt)
        }
    }

    private fun drawCalloutStreetLabel(b: StringBuilder, l: PdfStreetLabel) {
        val callout = requireNotNull(l.callout) { "Callout label requires exact leader geometry" }
        strokeColor(b, "#27343C"); fillColor(b, "#27343C"); lineWidth(b, 0.9)
        val dx = callout.roadAnchor.first - callout.tailStart.first
        val dy = callout.roadAnchor.second - callout.tailStart.second
        val length = hypot(dx, dy)
        require(length > 5.0) { "Callout leader is too short for locked arrowhead" }
        val ux = dx / length; val uy = dy / length
        val arrowLength = 5.0
        val arrowHalfWidth = 2.2
        val shaftEndX = callout.roadAnchor.first - ux * arrowLength
        val shaftEndY = callout.roadAnchor.second - uy * arrowLength
        line(b, callout.tailStart.first, PAGE_H - callout.tailStart.second, shaftEndX, PAGE_H - shaftEndY)
        val px = -uy; val py = ux
        val base1X = shaftEndX + px * arrowHalfWidth
        val base1Y = shaftEndY + py * arrowHalfWidth
        val base2X = shaftEndX - px * arrowHalfWidth
        val base2Y = shaftEndY - py * arrowHalfWidth
        b.append(num(callout.roadAnchor.first)).append(' ').append(num(PAGE_H - callout.roadAnchor.second)).append(" m ")
            .append(num(base1X)).append(' ').append(num(PAGE_H - base1Y)).append(" l ")
            .append(num(base2X)).append(' ').append(num(PAGE_H - base2Y)).append(" l h f\n")
        drawStraightStreetLabel(b, l)
    }

    private data class PathPoint(val x: Double, val y: Double, val tx: Double, val ty: Double)

    private fun pointAndTangentAtDistance(points: List<Pair<Double, Double>>, requested: Double): PathPoint {
        val total = polylineLength(points)
        val target = requested.coerceIn(0.0, total)
        var walked = 0.0
        for ((a, c) in points.zipWithNext()) {
            val dx = c.first - a.first; val dy = c.second - a.second
            val len = hypot(dx, dy)
            if (len <= 1e-12) continue
            if (walked + len + 1e-9 >= target) {
                val t = ((target - walked) / len).coerceIn(0.0, 1.0)
                return PathPoint(a.first + dx * t, a.second + dy * t, dx / len, dy / len)
            }
            walked += len
        }
        val (a, c) = points.zipWithNext().last { hypot(it.second.first - it.first.first, it.second.second - it.first.second) > 1e-12 }
        val dx = c.first - a.first; val dy = c.second - a.second; val len = hypot(dx, dy)
        return PathPoint(c.first, c.second, dx / len, dy / len)
    }

    private fun polylineLength(points: List<Pair<Double, Double>>): Double =
        points.zipWithNext().sumOf { (a, c) -> hypot(c.first - a.first, c.second - a.second) }

    private fun drawSplitDetailLayout(b: StringBuilder, split: PdfSplitDetailLayout, spec: CandidatePdfRenderSpec) {
        b.append("% TCS_SPLIT_DETAIL_LOCKED 350 228\n")
        drawClippedRegion(b, split.fullMapRect, spec.roads, spec.labels, spec.buildings)
        drawSplitPanel(b, split.northPanel)
        drawSplitPanel(b, split.southPanel)
        strokeColor(b, "#D6E0E4"); lineWidth(b, 0.6)
        line(b, split.dividerX, PAGE_H - split.fullMapRect.top, split.dividerX, PAGE_H - split.fullMapRect.bottom)
        line(b, split.rightRect.left, PAGE_H - split.detailDividerY, split.rightRect.right, PAGE_H - split.detailDividerY)
        fillColor(b, "#536D7D")
        text(b, "F4+0", 7.0, split.fullMapRect.left + 4.0, PAGE_H - 30.0, "FULL MAP")
        text(b, "F4+0", 7.0, split.northDetailRect.left + 7.0, PAGE_H - 30.0, "NORTH DETAIL")
        text(b, "F4+0", 7.0, split.southDetailRect.left + 7.0, PAGE_H - 245.0, "SOUTH DETAIL")
    }

    private fun drawSplitPanel(b: StringBuilder, panel: PdfSplitDetailPanel) {
        drawClippedRegion(b, panel.destinationBounds, panel.roads, panel.labels, panel.buildings)
    }

    private fun drawFullPlusDetailLayout(b: StringBuilder, layout: PdfFullPlusDetailLayout, spec: CandidatePdfRenderSpec) {
        b.append("% TCS_FULL_PLUS_DETAIL_EXPLICIT_MAPPING\n")
        drawClippedRegion(b, layout.mainContextRect, spec.roads, spec.labels, spec.buildings)
        val panel = layout.detail
        fillColor(b, "#FFFFFF"); strokeColor(b, "#D6E0E4"); lineWidth(b, 0.8)
        rect(b, panel.destinationBounds.left, topToY(panel.destinationBounds.top, panel.destinationBounds.height), panel.destinationBounds.width, panel.destinationBounds.height, fill = true, stroke = true)
        drawClippedRegion(b, panel.destinationBounds, panel.roads, panel.detailLabels, panel.buildings)
        fillColor(b, "#536D7D")
        text(b, "F4+0", 7.0, layout.mainContextRect.left + 4.0, PAGE_H - (layout.mainContextRect.top + 10.0), "MAIN CONTEXT")
        text(b, "F4+0", 7.0, panel.destinationBounds.left + 7.0, PAGE_H - (panel.destinationBounds.top + 10.0), "VERIFIED DETAIL")
    }

    private fun drawSiteBuildingAssignmentLayout(b: StringBuilder, layout: PdfSiteBuildingAssignmentLayout, spec: CandidatePdfRenderSpec) {
        b.append("% TCS_SITE_BUILDING_ASSIGNMENT_EXPLICIT\n")
        fillColor(b, "#FFFFFF"); strokeColor(b, "#D6E0E4"); lineWidth(b, 0.8)
        rect(b, layout.diagramRect.left, topToY(layout.diagramRect.top, layout.diagramRect.height), layout.diagramRect.width, layout.diagramRect.height, fill = true, stroke = true)
        drawClippedRegion(b, layout.diagramRect, spec.roads, spec.labels, spec.buildings)
        fillColor(b, "#536D7D")
        text(b, "F4+0", 7.0, layout.diagramRect.left + 7.0, PAGE_H - (layout.diagramRect.top + 10.0), "SITE / BUILDINGS")
        layout.accessInset?.let { inset ->
            fillColor(b, "#FFFFFF"); strokeColor(b, "#D6E0E4"); lineWidth(b, 0.8)
            rect(b, inset.destinationBounds.left, topToY(inset.destinationBounds.top, inset.destinationBounds.height), inset.destinationBounds.width, inset.destinationBounds.height, fill = true, stroke = true)
            drawClippedRegion(b, inset.destinationBounds, inset.roads, inset.detailLabels, emptyList())
            fillColor(b, "#536D7D")
            text(b, "F4+0", 7.0, inset.destinationBounds.left + 7.0, PAGE_H - (inset.destinationBounds.top + 10.0), "VERIFIED ACCESS")
        }
    }

    private fun drawClippedRegion(
        b: StringBuilder,
        rect: PdfDetailRect,
        roads: List<PdfRoadStroke>,
        labels: List<PdfStreetLabel>,
        buildings: List<PdfBuildingShape>
    ) {
        b.append("q ").append(num(rect.left)).append(' ').append(num(PAGE_H - rect.bottom)).append(' ')
            .append(num(rect.width)).append(' ').append(num(rect.height)).append(" re W n\n")
        buildings.forEach { drawBuilding(b, it) }
        roads.forEach { drawRoad(b, it) }
        labels.forEach { drawStreetLabel(b, it) }
        b.append("Q\n")
    }

    private fun drawDedicatedDetail(b: StringBuilder, detail: PdfDedicatedDetail) {
        val r = detail.destinationBounds
        fillColor(b, "#FFFFFF"); strokeColor(b, "#D6E0E4"); lineWidth(b, 0.8)
        rect(b, r.left, topToY(r.top, r.height), r.width, r.height, fill = true, stroke = true)
        b.append("q ").append(num(r.left)).append(' ').append(num(PAGE_H - r.bottom)).append(' ')
            .append(num(r.width)).append(' ').append(num(r.height)).append(" re W n\n")
        detail.roads.forEach { drawRoad(b, it) }
        detail.labels.forEach { drawStreetLabel(b, it) }
        b.append("Q\n")
    }

    private fun drawBuilding(b: StringBuilder, building: PdfBuildingShape) {
        b.append("% TCS_BUILDING_WORK_STATUS ").append(building.buildingId).append(' ').append(if(building.assigned)"assigned" else "excluded").append('\n')
        fillColor(b, if(building.assigned)PdfRoadStatus.WORK_BOTH_SIDES.hex else "#FFFFFF")
        strokeColor(b, if(building.assigned)PdfRoadStatus.WORK_BOTH_SIDES.hex else PdfRoadStatus.DO_NOT_WORK.hex);lineWidth(b,0.8)
        val first = building.points.first()
        b.append(num(first.first)).append(' ').append(num(PAGE_H - first.second)).append(" m\n")
        building.points.drop(1).forEach { (x, y) -> b.append(num(x)).append(' ').append(num(PAGE_H - y)).append(" l\n") }
        b.append("h B\n")
        fillColor(b, "#27343C")
        if (building.labelItems.isNotEmpty()) {
            b.append("% TCS_BUILDING_LABEL_ITEMS_EXACT ").append(building.buildingId).append('\n')
            building.labelItems.forEach { drawBuildingLabelItem(b, it) }
        } else {
            val cx = building.points.map { it.first }.average()
            val cyTop = building.points.map { it.second }.average()
            text(b, "F2+0", 8.0, cx - (building.label.length * 2.0), PAGE_H - cyTop + 2.5, building.label)
        }
    }

    private fun drawBuildingLabelItem(b: StringBuilder, item: PdfBuildingLabelItem) {
        val metrics = currentTemplateMetrics ?: error("Building-label renderer font metrics unavailable")
        val angle = Math.toRadians(item.rotationDegrees)
        val c = cos(angle)
        val s = sin(angle)
        val width = metrics.regularWidth(item.text, item.fontSizePt)
        val baselineX: Double
        val baselineTopY: Double
        if (item.originX != null && item.originTopY != null) {
            baselineX = item.originX
            baselineTopY = item.originTopY
        } else {
            val halfW = width / 2.0
            val baselineRise = item.fontSizePt * 0.26
            baselineX = item.centerX - c * halfW - s * baselineRise
            baselineTopY = item.centerTopY - s * halfW + c * baselineRise
        }
        b.append("BT /F2+0 ").append(num(item.fontSizePt)).append(" Tf ")
            .append(num(c)).append(' ').append(num(s)).append(' ')
            .append(num(-s)).append(' ').append(num(c)).append(' ')
            .append(num(baselineX)).append(' ').append(num(PAGE_H - baselineTopY)).append(" Tm (")
            .append(pdfEscape(item.text)).append(") Tj ET\n")
    }

    private fun drawDirections(b: StringBuilder, lines: List<String>) {
        fillColor(b, "#27343C")
        val tops = listOf(400.0, 420.0, 442.0)
        lines.take(3).forEachIndexed { i, raw ->
            val y = PAGE_H - (tops[i] + 10.0)
            if (i == 0 && raw.startsWith("Directions:")) {
                text(b, "F4+0", 9.0, 227.0, y, "Directions:")
                text(b, "F2+0", 9.0, 288.0, y, raw.removePrefix("Directions:").trim())
            } else text(b, "F2+0", 9.0, 227.0, y, raw)
        }
    }

    private fun drawFixtureWatermark(b: StringBuilder) {
        // Keep the fixture warning in intentionally reserved whitespace so it never intersects
        // the regression roads, labels, or building. It is horizontal to make contact review clear.
        fillColor(b, "#536D7D")
        text(b, "F4+0", 10.0, 515.0, PAGE_H - 55.0, "NOT FOR FIELD USE")
    }

    private fun swatch(b: StringBuilder, x: Double, top: Double, color: String, label: String, labelTop: Double) {
        roundedRect(b, x, topToY(top, 20.0), 20.0, 20.0, 4.0, color, null, 0.0)
        fillColor(b, "#FFFFFF")
        text(b, "F2+0", 9.5, 56.0, PAGE_H - (labelTop + 10.0), label)
    }

    private fun drawCalendar(b: StringBuilder) {
        val x = 29.0; val y = topToY(390.5, 20.0)
        strokeColor(b, "#FFFFFF"); lineWidth(b, 1.0)
        roundedRectPath(b, x, y, 20.0, 20.0, 2.0); b.append("S\n")
        line(b, x, y + 15.0, x + 20.0, y + 15.0)
        line(b, x + 4.0, y + 24.0, x + 4.0, y + 18.0)
        line(b, x + 15.0, y + 24.0, x + 15.0, y + 18.0)
        fillColor(b, "#FFFFFF")
        for (dx in listOf(4.0, 9.0, 14.0)) for (dy in listOf(5.0, 10.0)) rect(b, x + dx - 0.6, y + dy - 0.6, 1.2, 1.2, fill = true, stroke = false)
    }

    private fun drawCompass(b: StringBuilder) {
        val cx = 732.0; val cy = PAGE_H - 350.0; val r = 13.0
        fillColor(b, "#4376BE")
        b.append(num(cx)).append(' ').append(num(cy + r - 2)).append(" m ")
            .append(num(cx - 5)).append(' ').append(num(cy - 5)).append(" l ")
            .append(num(cx)).append(' ').append(num(cy - 2)).append(" l ")
            .append(num(cx + 5)).append(' ').append(num(cy - 5)).append(" l h f\n")
        fillColor(b, "#27343C")
        text(b, "F2+0", 7.0, cx - 2.6, cy - r + 2.0, "N")
    }

    private fun appendIncrementalRevision(
        base: ByteArray,
        content: ByteArray,
        spec: CandidatePdfRenderSpec,
        specSha: String
    ): ByteArray {
        val baseText = base.toString(Charsets.ISO_8859_1)
        require(baseText.contains("/MediaBox [ 0 0 768 480.5 ]")) { "Locked template MediaBox drift" }
        require(baseText.contains("/Contents 20 0 R")) { "Locked template content object drift" }
        require(baseText.contains("/Root 17 0 R") && baseText.contains("/Info 18 0 R")) { "Locked template trailer structure drift" }
        val prev = Regex("startxref\\s+(\\d+)\\s+%%EOF", RegexOption.DOT_MATCHES_ALL)
            .findAll(baseText).lastOrNull()?.groupValues?.get(1)?.toLong()
            ?: error("Locked template startxref unavailable")

        val out = java.io.ByteArrayOutputStream(base.size + content.size + 4096)
        out.write(base)
        if (base.lastOrNull()?.toInt() != '\n'.code) out.write('\n'.code)

        val infoOffset = out.size().toLong()
        writeAscii(out, "$INFO_OBJECT 0 obj\n")
        val subject = "R48 locked canonical territory candidate; display_id=${spec.identity.displayId}; canonical_filename=${spec.identity.canonicalFilename}; source_master_label=${spec.sourceMasterLabel ?: ""}; token_sha256=${LockedPdfRendererAuthorityHashes.STYLE_TOKENS_SHA256}; render_spec_sha256=$specSha"
        val keywords = "R48;territory-card;locked-template;android-candidate;card-display-id:${spec.identity.displayId};canonical-filename:${spec.identity.canonicalFilename};token-sha256:${LockedPdfRendererAuthorityHashes.STYLE_TOKENS_SHA256};render-spec-sha256:$specSha"
        writeAscii(out, "<< /Creator (Territory Card Studio Android R48 Renderer) /Producer (R48 Locked Territory Renderer) /Title (${pdfEscape("Territory ${spec.identity.displayId} - R48 Locked Template")}) /Subject (${pdfEscape(subject)}) /Keywords (${pdfEscape(keywords)}) /Trapped /False >>\nendobj\n")

        val contentOffset = out.size().toLong()
        writeAscii(out, "$CONTENT_OBJECT 0 obj\n<< /Length ${content.size} >>\nstream\n")
        out.write(content)
        if (content.lastOrNull()?.toInt() != '\n'.code) out.write('\n'.code)
        writeAscii(out, "endstream\nendobj\n")

        val xrefOffset = out.size().toLong()
        writeAscii(out, "xref\n$INFO_OBJECT 1\n${xrefEntry(infoOffset)}\n$CONTENT_OBJECT 1\n${xrefEntry(contentOffset)}\n")
        val id = sha256((specSha + LockedPdfRendererAuthorityHashes.TEMPLATE_PDF_SHA256).toByteArray(Charsets.US_ASCII)).take(32)
        writeAscii(out, "trailer\n<< /Size $PDF_SIZE /Root $ROOT_OBJECT 0 R /Info $INFO_OBJECT 0 R /Prev $prev /ID [<$id><$id>] >>\nstartxref\n$xrefOffset\n%%EOF\n")
        return out.toByteArray()
    }

    private fun xrefEntry(offset: Long): String = String.format(Locale.US, "%010d 00000 n ", offset)
    private fun writeAscii(out: java.io.ByteArrayOutputStream, value: String) = out.write(value.toByteArray(Charsets.ISO_8859_1))
    private fun topToY(top: Double, height: Double = 0.0): Double = PAGE_H - top - height

    private fun text(b: StringBuilder, font: String, size: Double, x: Double, y: Double, value: String) {
        b.append("BT /").append(font).append(' ').append(num(size)).append(" Tf 1 0 0 1 ")
            .append(num(x)).append(' ').append(num(y)).append(" Tm (").append(pdfEscape(value)).append(") Tj ET\n")
    }

    private fun line(b: StringBuilder, x1: Double, y1: Double, x2: Double, y2: Double) = b.append(num(x1)).append(' ').append(num(y1)).append(" m ").append(num(x2)).append(' ').append(num(y2)).append(" l S\n")
    private fun lineWidth(b: StringBuilder, w: Double) { b.append(num(w)).append(" w\n") }
    private fun fillColor(b: StringBuilder, hex: String) { val c = rgb(hex); b.append(num(c[0])).append(' ').append(num(c[1])).append(' ').append(num(c[2])).append(" rg\n") }
    private fun strokeColor(b: StringBuilder, hex: String) { val c = rgb(hex); b.append(num(c[0])).append(' ').append(num(c[1])).append(' ').append(num(c[2])).append(" RG\n") }
    private fun rgb(hex: String): DoubleArray {
        val h = hex.removePrefix("#")
        return doubleArrayOf(h.substring(0,2).toInt(16)/255.0, h.substring(2,4).toInt(16)/255.0, h.substring(4,6).toInt(16)/255.0)
    }
    private fun rect(b: StringBuilder, x: Double, y: Double, w: Double, h: Double, fill: Boolean, stroke: Boolean) {
        b.append(num(x)).append(' ').append(num(y)).append(' ').append(num(w)).append(' ').append(num(h)).append(" re ")
            .append(if (fill && stroke) "B" else if (fill) "f" else "S").append('\n')
    }
    private fun roundedRect(b: StringBuilder, x: Double, y: Double, w: Double, h: Double, r: Double, fillHex: String?, strokeHex: String?, strokeWidth: Double) {
        fillHex?.let { fillColor(b, it) }; strokeHex?.let { strokeColor(b, it); lineWidth(b, strokeWidth) }
        roundedRectPath(b, x, y, w, h, r)
        b.append(if (fillHex != null && strokeHex != null) "B\n" else if (fillHex != null) "f\n" else "S\n")
    }
    private fun roundedRectPath(b: StringBuilder, x: Double, y: Double, w: Double, h: Double, r: Double) {
        val k = 0.5522847498307936
        val c = r * k
        b.append(num(x+r)).append(' ').append(num(y)).append(" m ")
        b.append(num(x+w-r)).append(' ').append(num(y)).append(" l ")
        b.append(num(x+w-r+c)).append(' ').append(num(y)).append(' ').append(num(x+w)).append(' ').append(num(y+r-c)).append(' ').append(num(x+w)).append(' ').append(num(y+r)).append(" c ")
        b.append(num(x+w)).append(' ').append(num(y+h-r)).append(" l ")
        b.append(num(x+w)).append(' ').append(num(y+h-r+c)).append(' ').append(num(x+w-r+c)).append(' ').append(num(y+h)).append(' ').append(num(x+w-r)).append(' ').append(num(y+h)).append(" c ")
        b.append(num(x+r)).append(' ').append(num(y+h)).append(" l ")
        b.append(num(x+r-c)).append(' ').append(num(y+h)).append(' ').append(num(x)).append(' ').append(num(y+h-r+c)).append(' ').append(num(x)).append(' ').append(num(y+h-r)).append(" c ")
        b.append(num(x)).append(' ').append(num(y+r)).append(" l ")
        b.append(num(x)).append(' ').append(num(y+r-c)).append(' ').append(num(x+r-c)).append(' ').append(num(y)).append(' ').append(num(x+r)).append(' ').append(num(y)).append(" c h\n")
    }
    private fun pdfEscape(s: String): String = s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
    private fun num(v: Double): String {
        val rounded = String.format(Locale.US, "%.4f", v).trimEnd('0').trimEnd('.')
        return if (rounded == "-0") "0" else rounded
    }
    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

enum class PdfRoadStatus(val hex: String) {
    WORK_INSIDE_ONLY("#FFDC18"),
    WORK_BOTH_SIDES("#51C72B"),
    DO_NOT_WORK("#FF1435")
}

data class PdfRoadStroke(
    val segmentId: String,
    val roadName: String,
    val x1: Double,
    val topY1: Double,
    val x2: Double,
    val topY2: Double,
    val status: PdfRoadStatus,
    val strokeWidthPt: Double = 4.0
)

data class PdfArrowCallout(
    val roadAnchor: Pair<Double, Double>,
    val tailStart: Pair<Double, Double>,
    val labelAttach: Pair<Double, Double>,
    val lengthPt: Double,
    val tailGapPt: Double
)

data class PdfStreetLabel(
    val labelId: String,
    val segmentId: String,
    val text: String,
    val x: Double,
    val baselineTopY: Double,
    val rotationDegrees: Double = 0.0,
    val fontSizePt: Double = 9.0,
    val assignedRoadGapPx: Double = 3.0,
    val mode: LabelPlacementMode = LabelPlacementMode.DIRECT_ROAD_FOLLOWING,
    val curvePath: List<Pair<Double, Double>> = emptyList(),
    val curveNormalSign: Int = 0,
    val curveBaselineOffsetPt: Double = 0.0,
    val callout: PdfArrowCallout? = null
)

data class PdfBuildingLabelItem(
    val text: String,
    val centerX: Double,
    val centerTopY: Double,
    val originX: Double? = null,
    val originTopY: Double? = null,
    val rotationDegrees: Double = 0.0,
    val fontSizePt: Double = 8.0
)

data class PdfBuildingShape(
    val buildingId: String,
    val label: String,
    val points: List<Pair<Double, Double>>,
    val labelItems: List<PdfBuildingLabelItem> = emptyList(),
    val assigned: Boolean = true
)

data class PdfDetailRect(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double
) {
    init { require(left < right && top < bottom) { "Invalid dedicated-detail rectangle" } }
    val width: Double get() = right - left
    val height: Double get() = bottom - top
    fun contains(x: Double, topY: Double): Boolean = x >= left && x <= right && topY >= top && topY <= bottom
    fun overlaps(other: PdfDetailRect): Boolean = left < other.right && right > other.left && top < other.bottom && bottom > other.top
    fun isInsideLockedMap(): Boolean = left >= 176.0 && right <= 747.0 && top >= 20.0 && bottom <= 363.0
}

data class PdfDedicatedDetail(
    val detailId: String,
    val sourceBounds: PdfDetailRect,
    val destinationBounds: PdfDetailRect,
    val sourceSegmentIds: List<String>,
    val roads: List<PdfRoadStroke>,
    val labels: List<PdfStreetLabel>
)

data class PdfSplitDetailPanel(
    val panelId: String,
    val sourceBounds: PdfDetailRect,
    val destinationBounds: PdfDetailRect,
    val sourceSegmentIds: List<String>,
    val sourceBuildingIds: List<String> = emptyList(),
    val roads: List<PdfRoadStroke>,
    val labels: List<PdfStreetLabel>,
    val buildings: List<PdfBuildingShape> = emptyList()
)

data class PdfSplitDetailLayout(
    val fullMapRect: PdfDetailRect,
    val rightRect: PdfDetailRect,
    val dividerX: Double,
    val detailDividerY: Double,
    val northDetailRect: PdfDetailRect,
    val southDetailRect: PdfDetailRect,
    val northPanel: PdfSplitDetailPanel,
    val southPanel: PdfSplitDetailPanel,
    val sharedSourceSegmentIds: List<String> = emptyList()
)

data class PdfFullPlusDetailPanel(
    val panelId: String,
    val sourceBounds: PdfDetailRect,
    val destinationBounds: PdfDetailRect,
    val sourceSegmentIds: List<String>,
    val sourceBuildingIds: List<String> = emptyList(),
    val sourceLabelIds: List<String>,
    val roads: List<PdfRoadStroke>,
    val detailLabels: List<PdfStreetLabel>,
    val buildings: List<PdfBuildingShape> = emptyList()
)

data class PdfFullPlusDetailLayout(
    val mainContextRect: PdfDetailRect,
    val detail: PdfFullPlusDetailPanel
)

data class PdfBuildingMemberLabelBinding(
    val buildingId: String,
    val sourceMemberIds: List<String>,
    val verifiedLabelTexts: List<String>
)

data class PdfSiteAccessInsetPanel(
    val panelId: String,
    val sourceBounds: PdfDetailRect,
    val destinationBounds: PdfDetailRect,
    val sourceSegmentIds: List<String>,
    val sourceLabelIds: List<String>,
    val roads: List<PdfRoadStroke>,
    val detailLabels: List<PdfStreetLabel>
)

data class PdfSiteBuildingAssignmentLayout(
    val diagramId: String,
    val diagramRect: PdfDetailRect,
    val sourceSegmentIds: List<String>,
    val sourceBuildingIds: List<String>,
    val sourceLabelIds: List<String>,
    val buildingBindings: List<PdfBuildingMemberLabelBinding>,
    val accessInset: PdfSiteAccessInsetPanel? = null
)

data class CandidatePdfRenderSpec(
    val identity: TerritoryIdentity,
    val locality: String,
    val updated: String,
    val directionsLines: List<String>,
    val layoutMode: String = "full_map",
    val sourceMasterLabel: String? = null,
    val roads: List<PdfRoadStroke> = emptyList(),
    val labels: List<PdfStreetLabel> = emptyList(),
    val buildings: List<PdfBuildingShape> = emptyList(),
    val dedicatedDetails: List<PdfDedicatedDetail> = emptyList(),
    val splitDetail: PdfSplitDetailLayout? = null,
    val fullPlusDetail: PdfFullPlusDetailLayout? = null,
    val siteBuildingAssignment: PdfSiteBuildingAssignmentLayout? = null,
    val nonFieldFixture: Boolean = false
) {
    fun canonicalSha256(): String {
        val canonical = buildString {
            append("identity=").append(identity.displayId).append('\n')
            append("filename=").append(identity.canonicalFilename).append('\n')
            append("locality=").append(locality).append('\n')
            append("updated=").append(updated).append('\n')
            append("layout=").append(layoutMode).append('\n')
            append("source=").append(sourceMasterLabel ?: "").append('\n')
            append("fixture=").append(nonFieldFixture).append('\n')
            directionsLines.forEachIndexed { i, s -> append("direction[").append(i).append("]=").append(s).append('\n') }
            roads.forEachIndexed { i, r -> append("road[").append(i).append("]=").append(r.segmentId).append('|').append(r.roadName).append('|').append(r.x1).append('|').append(r.topY1).append('|').append(r.x2).append('|').append(r.topY2).append('|').append(r.status.name).append('|').append(r.strokeWidthPt).append('\n') }
            labels.forEachIndexed { i, l ->
                append("label[").append(i).append("]=").append(l.labelId).append('|').append(l.segmentId).append('|').append(l.text).append('|')
                    .append(l.x).append('|').append(l.baselineTopY).append('|').append(l.rotationDegrees).append('|').append(l.fontSizePt).append('|')
                    .append(l.assignedRoadGapPx).append('|').append(l.mode.name).append('|').append(l.curveNormalSign).append('|').append(l.curveBaselineOffsetPt)
                l.curvePath.forEach { p -> append("|curve:").append(p.first).append(',').append(p.second) }
                l.callout?.let { c -> append("|callout:").append(c.roadAnchor.first).append(',').append(c.roadAnchor.second).append(';')
                    .append(c.tailStart.first).append(',').append(c.tailStart.second).append(';').append(c.labelAttach.first).append(',').append(c.labelAttach.second)
                    .append(';').append(c.lengthPt).append(';').append(c.tailGapPt) }
                append('\n')
            }
            buildings.forEachIndexed { i, x ->
                append("building[").append(i).append("]=").append(x.buildingId).append('|').append(x.label).append("|assigned:").append(x.assigned)
                x.points.forEach { p -> append('|').append(p.first).append(',').append(p.second) }
                x.labelItems.forEach { item -> append("|memberLabel:").append(item.text).append('@').append(item.centerX).append(',').append(item.centerTopY).append(',').append(item.originX ?: "").append(',').append(item.originTopY ?: "").append(',').append(item.rotationDegrees).append(',').append(item.fontSizePt) }
                append('\n')
            }
            dedicatedDetails.forEachIndexed { i, d ->
                append("detail[").append(i).append("]=").append(d.detailId).append('|')
                    .append(d.sourceBounds.left).append(',').append(d.sourceBounds.top).append(',').append(d.sourceBounds.right).append(',').append(d.sourceBounds.bottom).append('|')
                    .append(d.destinationBounds.left).append(',').append(d.destinationBounds.top).append(',').append(d.destinationBounds.right).append(',').append(d.destinationBounds.bottom)
                d.sourceSegmentIds.forEach { append("|source:").append(it) }
                d.roads.forEach { r -> append("|road:").append(r.segmentId).append(',').append(r.roadName).append(',').append(r.x1).append(',').append(r.topY1).append(',').append(r.x2).append(',').append(r.topY2).append(',').append(r.status.name).append(',').append(r.strokeWidthPt) }
                d.labels.forEach { l -> append("|label:").append(l.labelId).append(',').append(l.segmentId).append(',').append(l.text).append(',').append(l.x).append(',').append(l.baselineTopY).append(',').append(l.fontSizePt).append(',').append(l.assignedRoadGapPx).append(',').append(l.mode.name) }
                append('\n')
            }
            splitDetail?.let { split ->
                append("split=").append(split.fullMapRect.left).append(',').append(split.fullMapRect.top).append(',').append(split.fullMapRect.right).append(',').append(split.fullMapRect.bottom).append('|')
                    .append(split.rightRect.left).append(',').append(split.rightRect.top).append(',').append(split.rightRect.right).append(',').append(split.rightRect.bottom).append('|')
                    .append(split.dividerX).append('|').append(split.detailDividerY).append('|')
                    .append(split.northDetailRect.left).append(',').append(split.northDetailRect.top).append(',').append(split.northDetailRect.right).append(',').append(split.northDetailRect.bottom).append('|')
                    .append(split.southDetailRect.left).append(',').append(split.southDetailRect.top).append(',').append(split.southDetailRect.right).append(',').append(split.southDetailRect.bottom)
                split.sharedSourceSegmentIds.sorted().forEach { append("|shared:").append(it) }
                append('\n')
                fun appendPanel(panel: PdfSplitDetailPanel) {
                    append("splitPanel=").append(panel.panelId).append('|')
                        .append(panel.sourceBounds.left).append(',').append(panel.sourceBounds.top).append(',').append(panel.sourceBounds.right).append(',').append(panel.sourceBounds.bottom).append('|')
                        .append(panel.destinationBounds.left).append(',').append(panel.destinationBounds.top).append(',').append(panel.destinationBounds.right).append(',').append(panel.destinationBounds.bottom)
                    panel.sourceSegmentIds.sorted().forEach { append("|source:").append(it) }
                    panel.sourceBuildingIds.sorted().forEach { append("|buildingSource:").append(it) }
                    panel.roads.sortedWith(compareBy<PdfRoadStroke>({ it.segmentId }, { it.x1 }, { it.topY1 }, { it.x2 }, { it.topY2 })).forEach { r ->
                        append("|road:").append(r.segmentId).append(',').append(r.roadName).append(',').append(r.x1).append(',').append(r.topY1).append(',').append(r.x2).append(',').append(r.topY2).append(',').append(r.status.name).append(',').append(r.strokeWidthPt)
                    }
                    panel.labels.sortedBy { it.labelId }.forEach { l -> append("|label:").append(l.labelId).append(',').append(l.segmentId).append(',').append(l.text).append(',').append(l.x).append(',').append(l.baselineTopY).append(',').append(l.mode.name) }
                    panel.buildings.sortedBy { it.buildingId }.forEach { x -> append("|building:").append(x.buildingId).append(',').append(x.label).append("|assigned:").append(x.assigned); x.points.forEach { p -> append('@').append(p.first).append(',').append(p.second) }; x.labelItems.forEach { item -> append("|memberLabel:").append(item.text).append('@').append(item.centerX).append(',').append(item.centerTopY).append(',').append(item.originX ?: "").append(',').append(item.originTopY ?: "").append(',').append(item.rotationDegrees).append(',').append(item.fontSizePt) } }
                    append('\n')
                }
                appendPanel(split.northPanel)
                appendPanel(split.southPanel)
            }
            fullPlusDetail?.let { layout ->
                append("fullPlus=").append(layout.mainContextRect.left).append(',').append(layout.mainContextRect.top).append(',').append(layout.mainContextRect.right).append(',').append(layout.mainContextRect.bottom).append('\n')
                val p = layout.detail
                append("fullPlusPanel=").append(p.panelId).append('|')
                    .append(p.sourceBounds.left).append(',').append(p.sourceBounds.top).append(',').append(p.sourceBounds.right).append(',').append(p.sourceBounds.bottom).append('|')
                    .append(p.destinationBounds.left).append(',').append(p.destinationBounds.top).append(',').append(p.destinationBounds.right).append(',').append(p.destinationBounds.bottom)
                p.sourceSegmentIds.sorted().forEach { append("|source:").append(it) }
                p.sourceBuildingIds.sorted().forEach { append("|buildingSource:").append(it) }
                p.sourceLabelIds.sorted().forEach { append("|labelSource:").append(it) }
                p.roads.sortedWith(compareBy<PdfRoadStroke>({ it.segmentId }, { it.x1 }, { it.topY1 }, { it.x2 }, { it.topY2 })).forEach { r -> append("|road:").append(r.segmentId).append(',').append(r.roadName).append(',').append(r.x1).append(',').append(r.topY1).append(',').append(r.x2).append(',').append(r.topY2).append(',').append(r.status.name).append(',').append(r.strokeWidthPt) }
                p.detailLabels.sortedBy { it.labelId }.forEach { l -> append("|label:").append(l.labelId).append(',').append(l.segmentId).append(',').append(l.text).append(',').append(l.x).append(',').append(l.baselineTopY).append(',').append(l.mode.name) }
                p.buildings.sortedBy { it.buildingId }.forEach { x -> append("|building:").append(x.buildingId).append(',').append(x.label).append("|assigned:").append(x.assigned); x.points.forEach { pt -> append('@').append(pt.first).append(',').append(pt.second) }; x.labelItems.forEach { item -> append("|memberLabel:").append(item.text).append('@').append(item.centerX).append(',').append(item.centerTopY).append(',').append(item.originX ?: "").append(',').append(item.originTopY ?: "").append(',').append(item.rotationDegrees).append(',').append(item.fontSizePt) } }
                append('\n')
            }
            siteBuildingAssignment?.let { layout ->
                append("siteBuilding=").append(layout.diagramId).append('|')
                    .append(layout.diagramRect.left).append(',').append(layout.diagramRect.top).append(',').append(layout.diagramRect.right).append(',').append(layout.diagramRect.bottom)
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
                    append("siteAccess=").append(inset.panelId).append('|')
                        .append(inset.sourceBounds.left).append(',').append(inset.sourceBounds.top).append(',').append(inset.sourceBounds.right).append(',').append(inset.sourceBounds.bottom).append('|')
                        .append(inset.destinationBounds.left).append(',').append(inset.destinationBounds.top).append(',').append(inset.destinationBounds.right).append(',').append(inset.destinationBounds.bottom)
                    inset.sourceSegmentIds.sorted().forEach { append("|source:").append(it) }
                    inset.sourceLabelIds.sorted().forEach { append("|labelSource:").append(it) }
                    inset.roads.sortedWith(compareBy<PdfRoadStroke>({ it.segmentId }, { it.x1 }, { it.topY1 }, { it.x2 }, { it.topY2 })).forEach { r ->
                        append("|road:").append(r.segmentId).append(',').append(r.roadName).append(',').append(r.x1).append(',').append(r.topY1).append(',').append(r.x2).append(',').append(r.topY2).append(',').append(r.status.name).append(',').append(r.strokeWidthPt)
                    }
                    inset.detailLabels.sortedBy { it.labelId }.forEach { l -> append("|label:").append(l.labelId).append(',').append(l.segmentId).append(',').append(l.text).append(',').append(l.x).append(',').append(l.baselineTopY).append(',').append(l.mode.name) }
                    append('\n')
                }
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

data class CandidatePdfRenderResult(
    val pdfBytes: ByteArray,
    val renderSpecSha256: String,
    val pdfSha256: String,
    val exactValidation: CandidatePdfExactValidation
)

data class CandidatePdfExactValidation(
    val passed: Boolean,
    val errors: List<String>,
    val pageWidthPt: Double,
    val pageHeightPt: Double,
    val producer: String,
    val renderSpecSha256: String
)

object CandidatePdfExactValidator {
    fun validate(pdf: ByteArray, spec: CandidatePdfRenderSpec, specSha: String): CandidatePdfExactValidation {
        val errors = mutableListOf<String>()
        val raw = pdf.toString(Charsets.ISO_8859_1)
        if (!raw.startsWith("%PDF-")) errors += "PDF header missing"
        if (!raw.trimEnd().endsWith("%%EOF")) errors += "PDF EOF missing"
        if (!raw.contains("/MediaBox [ 0 0 768 480.5 ]")) errors += "Locked 768 x 480.5 MediaBox missing"
        val pageCount = Regex("/Type\\s*/Page(?!s)").findAll(raw).count()
        if (pageCount != 1) errors += "Expected exactly one page, got $pageCount"
        if (!raw.contains("/Producer (R48 Locked Territory Renderer)")) errors += "Locked producer metadata missing"
        if (!raw.contains("token_sha256=${LockedPdfRendererAuthorityHashes.STYLE_TOKENS_SHA256}")) errors += "Exact style-token provenance missing"
        if (!raw.contains("render_spec_sha256=$specSha") || !raw.contains("render-spec-sha256:$specSha")) errors += "Render-spec hash binding missing"
        if (!raw.contains("display_id=${spec.identity.displayId}")) errors += "Display-ID metadata binding missing"
        if (!raw.contains("canonical_filename=${spec.identity.canonicalFilename}")) errors += "Canonical filename metadata binding missing"
        if (!raw.contains("% TCS_RENDER_SPEC_SHA256 $specSha")) errors += "Content stream spec-hash marker missing"
        if (!raw.contains("/F2+0") || !raw.contains("/F3+0") || !raw.contains("/F4+0")) errors += "Locked DejaVu font resources missing"
        if (!raw.contains("/FormXob.dbb823e6e4d953b83cd8868ba2ab9525")) errors += "Locked A33 badge resource missing"
        if (spec.layoutMode == "split_detail" && !raw.contains("% TCS_SPLIT_DETAIL_LOCKED 350 228")) errors += "Locked split-detail marker/dividers missing"
        if (spec.layoutMode == "full_plus_detail" && !raw.contains("% TCS_FULL_PLUS_DETAIL_EXPLICIT_MAPPING")) errors += "Explicit full-plus-detail mapping marker missing"
        if (spec.layoutMode == "site_building_assignment" && !raw.contains("% TCS_SITE_BUILDING_ASSIGNMENT_EXPLICIT")) errors += "Explicit site/building assignment marker missing"
        if (spec.buildings.any { it.labelItems.size > 1 } && !raw.contains("% TCS_BUILDING_LABEL_ITEMS_EXACT")) errors += "Exact multi-label building rendering marker missing"
        return CandidatePdfExactValidation(
            passed = errors.isEmpty(),
            errors = errors,
            pageWidthPt = PAGE_W,
            pageHeightPt = PAGE_H,
            producer = "R48 Locked Territory Renderer",
            renderSpecSha256 = specSha
        )
    }

    private const val PAGE_W = 768.0
    private const val PAGE_H = 480.5
}

private data class FontWidthTable(val firstChar: Int, val widths: DoubleArray) {
    fun width(text: String, size: Double): Double = text.sumOf { ch ->
        val code = ch.code
        require(code >= firstChar && code < firstChar + widths.size) { "Glyph outside embedded ASCII font subset: $ch" }
        widths[code - firstChar] / 1000.0 * size
    }
}

private data class TemplateFontMetrics(val regular: FontWidthTable, val idBold: FontWidthTable) {
    fun regularWidth(text: String, size: Double): Double = regular.width(text, size)
    fun regularAdvance(ch: Char, size: Double): Double = regular.width(ch.toString(), size)
    fun idWidth(text: String, size: Double): Double = idBold.width(text, size)

    companion object {
        fun parse(template: ByteArray): TemplateFontMetrics {
            val raw = template.toString(Charsets.ISO_8859_1)
            return TemplateFontMetrics(parseObject(raw, 8), parseObject(raw, 12))
        }
        private fun parseObject(raw: String, number: Int): FontWidthTable {
            val objectText = Regex("$number 0 obj(.*?)endobj", setOf(RegexOption.DOT_MATCHES_ALL)).find(raw)?.groupValues?.get(1)
                ?: error("Locked template font object $number unavailable")
            val first = Regex("/FirstChar\\s+(\\d+)").find(objectText)?.groupValues?.get(1)?.toInt()
                ?: error("Locked template font object $number FirstChar unavailable")
            val widthBody = Regex("/Widths\\s*\\[(.*?)]", setOf(RegexOption.DOT_MATCHES_ALL)).find(objectText)?.groupValues?.get(1)
                ?: error("Locked template font object $number Widths unavailable")
            val widths = Regex("[-+]?[0-9]*\\.?[0-9]+").findAll(widthBody).map { it.value.toDouble() }.toList().toDoubleArray()
            require(widths.size >= 128) { "Locked template font object $number width table is incomplete" }
            return FontWidthTable(first, widths)
        }
    }
}

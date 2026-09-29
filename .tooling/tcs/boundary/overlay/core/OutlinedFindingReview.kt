package com.koenterprises.territorycardstudio.core

import kotlin.math.hypot

/** A decision about ONE immutable extraction finding, never a coverage or PDF approval. */
data class OutlinedFindingResolution(
    val findingId: String,
    val kind: String,
    val evidence: String,
    val outputIds: List<String>,
    val reviewedOutputSha256: String
)

/** Typed, source-local review. Resolving an image mark never removes a road candidate,
 * a building obligation, a source interval, or the separate current-inventory gate. */
object OutlinedFindingReviewContract {
    val nonRoadKinds: Set<String> = linkedSetOf("PARKING_MARKING", "BUILDING_EDGE", "MAP_SYMBOL", "LABEL_ARTIFACT")
    val kinds: Set<String> = nonRoadKinds + setOf("OUTSIDE_CONTEXT", "MATCHED_ROAD")
    private const val LABEL_PREFIX = "No aligned road geometry was recovered for "
    private const val LABEL_SUFFIX = "; check the source."

    fun allowed(finding: MapImageFinding): Set<String> = when {
        finding.sourceBounds == null -> emptySet()
        finding.id.startsWith("component-") -> nonRoadKinds + "OUTSIDE_CONTEXT"
        finding.id.startsWith("unmatched-label-") -> linkedSetOf("MATCHED_ROAD", "OUTSIDE_CONTEXT")
        else -> emptySet() // A missing road/name/boundary is not a dismissible mark.
    }

    /** The label's text and bounds come from the hashed extraction finding, not editable OCR. */
    private fun sourceLabel(finding: MapImageFinding): MapImageText? {
        if (!finding.message.startsWith(LABEL_PREFIX) || !finding.message.endsWith(LABEL_SUFFIX)) return null
        val text = finding.message.removePrefix(LABEL_PREFIX).removeSuffix(LABEL_SUFFIX)
        if (MapImageDraftExtractor.streetText(text) == null) return null
        return MapImageText(text, finding.sourceBounds ?: return null)
    }

    fun failures(extraction: OutlinedMapExtraction, transform: OutlinedMapTransform,
        resolution: OutlinedFindingResolution, roads: List<RoadGeometry>,
        spans: List<OutlinedSourceSpan>): List<String> {
        val errors = mutableListOf<String>()
        val finding = extraction.findings.singleOrNull { it.id == resolution.findingId }
            ?: return listOf("UNKNOWN_OR_DUPLICATE_FINDING")
        val box = finding.sourceBounds ?: return listOf("SOURCE_REGION_REQUIRED")
        if (listOf(box.left, box.top, box.right, box.bottom).any { !it.isFinite() } ||
            box.left < 0 || box.top < 0 || box.right > extraction.boundary.width ||
            box.bottom > extraction.boundary.height || box.right <= box.left || box.bottom <= box.top)
            errors += "INVALID_SOURCE_REGION"
        if (resolution.kind !in allowed(finding)) errors += "DISPOSITION_NOT_ALLOWED"
        if (resolution.evidence.trim().length !in 8..2000 || resolution.evidence.length > 2000)
            errors += "SPECIFIC_SOURCE_EVIDENCE_REQUIRED"
        if (resolution.outputIds.size > 512 || resolution.outputIds.any { it.isBlank() } ||
            resolution.outputIds.distinct().size != resolution.outputIds.size)
            errors += "INVALID_OUTPUT_IDS"
        if (roads.map { it.segmentId }.distinct().size != roads.size) errors += "DUPLICATE_OUTPUT_ID"
        val selected = resolution.outputIds.mapNotNull { id -> roads.singleOrNull { it.segmentId == id } }
        if (selected.size != resolution.outputIds.size) errors += "MISSING_OUTPUT"
        val digest = runCatching { OutlinedCoverageContract.outputSha256(selected) }.getOrNull()
        if (digest == null || resolution.reviewedOutputSha256 != digest) errors += "FINDING_OUTPUT_REVIEW_CHANGED"
        if (resolution.kind == "MATCHED_ROAD") {
            if (selected.isEmpty()) errors += "MATCH_REQUIRES_ROAD"
            val label = sourceLabel(finding)
            val expected = label?.let { MapImageDraftExtractor.streetText(it.text) }
                ?.let(TopologyOverlapDecisionEngine::normalizeRoadName)
            if (expected == null) errors += "SOURCE_LABEL_UNRESOLVED"
            for (road in selected) {
                val actual = TopologyOverlapDecisionEngine.normalizeRoadName(road.name)
                if (actual != expected || road.normalizedName != actual) errors += "ROAD_NAME_MISMATCH:${road.segmentId}"
                val source = road.points.map(transform::source)
                // Keep the existing geometric alignment tolerance unchanged. Proximity alone,
                // a perpendicular road, or a matching name on another block never suffices.
                val aligned = label?.let { OutlinedMapRoadExtractor.alignedStreetName(source, listOf(it)) }
                    ?.let(TopologyOverlapDecisionEngine::normalizeRoadName)
                if (aligned == null || aligned != expected) errors += "SOURCE_LABEL_NOT_ALIGNED:${road.segmentId}"
                if (!sourceBound(extraction, transform, road, spans)) errors += "MATCH_WITHOUT_REVIEWED_SOURCE_SPANS:${road.segmentId}"
            }
        } else {
            if (resolution.outputIds.isNotEmpty()) errors += "NON_ROAD_DECISION_HAS_OUTPUT"
            if (resolution.kind in nonRoadKinds && extraction.roads.any { proposal ->
                    proposal.road.points.zipWithNext().any { (a, b) -> intersectsRegion(a, b, box) }
                }) errors += "SOURCE_REGION_CONTAINS_ROAD_TRACE"
            if (resolution.kind == "OUTSIDE_CONTEXT" && !whollyOutside(box, extraction.boundary.polygon))
                errors += "SOURCE_REGION_NOT_WHOLLY_OUTSIDE"
        }
        return errors.distinct()
    }

    /** Independently reconstruct the selected output from its exact, individually reviewed
     * source intervals. Refreshing only the finding's digest cannot renew stale span evidence. */
    private fun sourceBound(extraction: OutlinedMapExtraction, transform: OutlinedMapTransform,
        road: RoadGeometry, allSpans: List<OutlinedSourceSpan>): Boolean = runCatching {
        val linked = allSpans.filter { it.outputId == road.segmentId }.sortedBy { it.outputOrder }
        require(linked.isNotEmpty() && linked.map { it.outputOrder } == linked.indices.toList())
        require(road.points.size >= 2)
        val hash = OutlinedCoverageContract.outputSha256(listOf(road))
        val path = mutableListOf<Point2D>()
        for (span in linked) {
            require(span.disposition == OutlinedSpanDisposition.ROAD && span.reviewedOutputSha256 == hash)
            require(span.evidence.trim().length in 8..2000)
            val candidate = requireNotNull(extraction.roads.singleOrNull { it.road.id == span.candidateId })
            val part = OutlinedCoverageContract.slice(candidate.road.points, span.from, span.to)
                .let { if (span.reversed) it.reversed() else it }
            if (path.isNotEmpty()) require(same(path.last(), part.first()))
            path += if (path.isEmpty()) part else part.drop(1)
        }
        val expected = path.map(transform::page)
        require(expected.size == road.points.size && expected.zip(road.points).all { (a, b) -> same(a, b) })
        true
    }.getOrDefault(false)

    private fun same(a: Point2D, b: Point2D): Boolean =
        a.x.isFinite() && a.y.isFinite() && b.x.isFinite() && b.y.isFinite() && hypot(a.x - b.x, a.y - b.y) <= 1e-7

    /** Conservative segment/rectangle clipping. A mixed region containing a retained source
     * trace cannot be dismissed as one symbol, even after its output has been removed. */
    private fun intersectsRegion(a: Point2D, b: Point2D, box: AxisAlignedRect): Boolean {
        var lower = 0.0; var upper = 1.0
        fun clip(p: Double, q: Double): Boolean {
            if (p == 0.0) return q >= 0.0
            val r = q / p
            if (p < 0.0) lower = maxOf(lower, r) else upper = minOf(upper, r)
            return lower <= upper
        }
        val dx = b.x-a.x; val dy = b.y-a.y
        return clip(-dx,a.x-box.left) && clip(dx,box.right-a.x) &&
            clip(-dy,a.y-box.top) && clip(dy,box.bottom-a.y)
    }

    fun whollyOutside(box: AxisAlignedRect, polygon: List<Point2D>): Boolean {
        if (polygon.any { it.x in box.left..box.right && it.y in box.top..box.bottom }) return false
        val ring = listOf(Point2D(box.left, box.top), Point2D(box.right, box.top),
            Point2D(box.right, box.bottom), Point2D(box.left, box.bottom), Point2D(box.left, box.top))
        return runCatching { OutlinedCoverageContract.exactRelations(ring, polygon) == setOf("outside") }.getOrDefault(false)
    }
}

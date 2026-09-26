#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
renderer = root / "core/src/main/kotlin/com/koenterprises/territorycardstudio/core/CandidatePdfRenderer.kt"
adapter = root / "core/src/main/kotlin/com/koenterprises/territorycardstudio/core/ProductionRenderModelAdapter.kt"
renderer_test = root / "core/src/test/kotlin/com/koenterprises/territorycardstudio/core/CandidatePdfRendererTest.kt"
adapter_test = root / "core/src/test/kotlin/com/koenterprises/territorycardstudio/core/ProductionRenderModelAdapterTest.kt"
targets = (renderer, adapter, renderer_test, adapter_test)
D = "$" + "{"

# Neutralize Kotlin interpolation while this transport script is applied.
for p in targets:
    p.write_text(p.read_text().replace(D, "@@{"))

def rep(path, old, new, label, count=1):
    s = path.read_text()
    n = s.count(old)
    if n != count:
        raise SystemExit(f"{label}: expected {count} matches in {path.name}, got {n}")
    path.write_text(s.replace(old, new, count))

# Candidate renderer: carry, validate, hash and draw independent building labels.
rep(renderer,
'''            spec.buildings.forEach { add(it.label) }''',
'''            spec.buildings.forEach { building -> add(building.label); building.labelItems.forEach { add(it.text) } }''',
"base building label preflight")

rep(renderer,
'''                detail.buildings.forEach { add(it.label) }''',
'''                detail.buildings.forEach { building -> add(building.label); building.labelItems.forEach { add(it.text) } }''',
"detail building label preflight")

rep(renderer,
'''        spec.buildings.forEach { b ->
            require(b.points.size >= 3) { "Building polygon requires >=3 points" }
            require(b.points.all { pagePoint(it.first, it.second) }) { "Building polygon point outside locked page" }
        }''',
'''        spec.buildings.forEach { b ->
            require(b.points.size >= 3) { "Building polygon requires >=3 points" }
            require(b.points.all { pagePoint(it.first, it.second) }) { "Building polygon point outside locked page" }
            validateBuildingLabelItems(b, requireVerifiedItems = spec.layoutMode == "site_building_assignment")
        }''',
"base building label validation")

rep(renderer,
'''            require(binding.verifiedLabelTexts.size == 1) {
                "site_building_assignment $buildingId multi-label single-footprint rendering remains blocked until Phase 1C"
            }
            require(binding.verifiedLabelTexts.single() == base.label) { "site_building_assignment $buildingId verified label binding drift" }''',
'''            require(base.labelItems.isNotEmpty()) { "site_building_assignment $buildingId requires verified interior label items" }
            require(binding.verifiedLabelTexts.sorted() == base.labelItems.map { it.text }.sorted()) {
                "site_building_assignment $buildingId verified label binding drift"
            }
            if (base.labelItems.size > 1) {
                require(base.labelItems.all { it.originX != null && it.originTopY != null }) {
                    "site_building_assignment $buildingId multi-label footprint requires exact verified text origins"
                }
            }''',
"site multi-label gate")

rep(renderer,
'''    private fun labelPointSet(label: PdfStreetLabel): List<Pair<Double, Double>> = buildList {
        add(label.x to label.baselineTopY)
        addAll(label.curvePath)
        label.callout?.let { c -> add(c.roadAnchor); add(c.tailStart); add(c.labelAttach) }
    }

    private fun pagePoint(x: Double, topY: Double): Boolean = x in 0.0..PAGE_W && topY in 0.0..PAGE_H
''',
'''    private fun labelPointSet(label: PdfStreetLabel): List<Pair<Double, Double>> = buildList {
        add(label.x to label.baselineTopY)
        addAll(label.curvePath)
        label.callout?.let { c -> add(c.roadAnchor); add(c.tailStart); add(c.labelAttach) }
    }

    private fun validateBuildingLabelItems(building: PdfBuildingShape, requireVerifiedItems: Boolean = false) {
        if (requireVerifiedItems) require(building.labelItems.isNotEmpty()) {
            "@@{building.buildingId}: verified building label items are required"
        }
        if (building.labelItems.isEmpty()) return
        require(building.labelItems.map { it.text }.distinct().size == building.labelItems.size) {
            "@@{building.buildingId}: duplicate verified building label text"
        }
        building.labelItems.forEach { item ->
            require(item.text.isNotBlank()) { "@@{building.buildingId}: blank verified building label text" }
            require(item.fontSizePt > 0.0) { "@@{building.buildingId}: invalid verified building label font size" }
            require(pagePoint(item.centerX, item.centerTopY)) { "@@{building.buildingId}: building label center outside locked page" }
            require(pointInPolygon(item.centerX, item.centerTopY, building.points)) {
                "@@{building.buildingId}: building label '@@{item.text}' center escapes physical footprint"
            }
            require((item.originX == null) == (item.originTopY == null)) {
                "@@{building.buildingId}: building label '@@{item.text}' has incomplete verified origin"
            }
            if (item.originX != null && item.originTopY != null) {
                require(pagePoint(item.originX, item.originTopY)) {
                    "@@{building.buildingId}: building label '@@{item.text}' origin outside locked page"
                }
            }
        }
        if (building.labelItems.size > 1) {
            require(building.labelItems.all { it.originX != null && it.originTopY != null }) {
                "@@{building.buildingId}: multi-label single-footprint rendering requires exact verified origins"
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
''',
"building label validator helpers")

rep(renderer,
'''    private fun drawBuilding(b: StringBuilder, building: PdfBuildingShape) {
        fillColor(b, "#EEF2F4"); strokeColor(b, "#536D7D"); lineWidth(b, 0.8)
        val first = building.points.first()
        b.append(num(first.first)).append(' ').append(num(PAGE_H - first.second)).append(" m\\n")
        building.points.drop(1).forEach { (x, y) -> b.append(num(x)).append(' ').append(num(PAGE_H - y)).append(" l\\n") }
        b.append("h B\\n")
        val cx = building.points.map { it.first }.average()
        val cyTop = building.points.map { it.second }.average()
        fillColor(b, "#27343C")
        text(b, "F2+0", 8.0, cx - (building.label.length * 2.0), PAGE_H - cyTop + 2.5, building.label)
    }''',
'''    private fun drawBuilding(b: StringBuilder, building: PdfBuildingShape) {
        fillColor(b, "#EEF2F4"); strokeColor(b, "#536D7D"); lineWidth(b, 0.8)
        val first = building.points.first()
        b.append(num(first.first)).append(' ').append(num(PAGE_H - first.second)).append(" m\\n")
        building.points.drop(1).forEach { (x, y) -> b.append(num(x)).append(' ').append(num(PAGE_H - y)).append(" l\\n") }
        b.append("h B\\n")
        fillColor(b, "#27343C")
        if (building.labelItems.isNotEmpty()) {
            b.append("% TCS_BUILDING_LABEL_ITEMS_EXACT ").append(building.buildingId).append('\\n')
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
            .append(pdfEscape(item.text)).append(") Tj ET\\n")
    }''',
"exact building label drawing")

rep(renderer,
'''data class PdfBuildingShape(
    val buildingId: String,
    val label: String,
    val points: List<Pair<Double, Double>>
)''',
'''data class PdfBuildingLabelItem(
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
    val labelItems: List<PdfBuildingLabelItem> = emptyList()
)''',
"building label render model")

rep(renderer,
'''            buildings.forEachIndexed { i, x -> append("building[").append(i).append("]=").append(x.buildingId).append('|').append(x.label); x.points.forEach { p -> append('|').append(p.first).append(',').append(p.second) }; append('\\n') }''',
'''            buildings.forEachIndexed { i, x ->
                append("building[").append(i).append("]=").append(x.buildingId).append('|').append(x.label)
                x.points.forEach { p -> append('|').append(p.first).append(',').append(p.second) }
                x.labelItems.forEach { item -> append("|memberLabel:").append(item.text).append('@').append(item.centerX).append(',').append(item.centerTopY).append(',').append(item.originX ?: "").append(',').append(item.originTopY ?: "").append(',').append(item.rotationDegrees).append(',').append(item.fontSizePt) }
                append('\\n')
            }''',
"canonical base building labels")

rep(renderer,
'''append("|building:").append(x.buildingId).append(',').append(x.label); x.points.forEach { p -> append('@').append(p.first).append(',').append(p.second) }''',
'''append("|building:").append(x.buildingId).append(',').append(x.label); x.points.forEach { p -> append('@').append(p.first).append(',').append(p.second) }; x.labelItems.forEach { item -> append("|memberLabel:").append(item.text).append('@').append(item.centerX).append(',').append(item.centerTopY).append(',').append(item.originX ?: "").append(',').append(item.originTopY ?: "").append(',').append(item.rotationDegrees).append(',').append(item.fontSizePt) }''',
"canonical split building labels")

rep(renderer,
'''append("|building:").append(x.buildingId).append(',').append(x.label); x.points.forEach { pt -> append('@').append(pt.first).append(',').append(pt.second) }''',
'''append("|building:").append(x.buildingId).append(',').append(x.label); x.points.forEach { pt -> append('@').append(pt.first).append(',').append(pt.second) }; x.labelItems.forEach { item -> append("|memberLabel:").append(item.text).append('@').append(item.centerX).append(',').append(item.centerTopY).append(',').append(item.originX ?: "").append(',').append(item.originTopY ?: "").append(',').append(item.rotationDegrees).append(',').append(item.fontSizePt) }''',
"canonical full-plus building labels")

rep(renderer,
'''        if (spec.layoutMode == "site_building_assignment" && !raw.contains("% TCS_SITE_BUILDING_ASSIGNMENT_EXPLICIT")) errors += "Explicit site/building assignment marker missing"''',
'''        if (spec.layoutMode == "site_building_assignment" && !raw.contains("% TCS_SITE_BUILDING_ASSIGNMENT_EXPLICIT")) errors += "Explicit site/building assignment marker missing"
        if (spec.buildings.any { it.labelItems.size > 1 } && !raw.contains("% TCS_BUILDING_LABEL_ITEMS_EXACT")) errors += "Exact multi-label building rendering marker missing"''',
"multi-label exact marker validation")

# Production adapter: preserve exact BuildingLabelItem geometry and remove the Phase-1C blocker.
rep(adapter,
'''        val buildings = state.buildings.filter { it.assigned }.sortedBy { it.buildingId }.map { building ->
            PdfBuildingShape(
                buildingId = building.buildingId,
                label = building.label,
                points = building.polygon.map { it.x to it.y }
            )
        }''',
'''        val buildings = state.buildings.filter { it.assigned }.sortedBy { it.buildingId }.map(::toPdfBuildingShape)''',
"adapter main building conversion")

rep(adapter,
'''                    val base = requireNotNull(buildingByIdForSplit[b.buildingId]) { "Split-detail panel references unknown building @@{b.buildingId}" }
                    PdfBuildingShape(base.buildingId, base.label, b.polygon.map { it.x to it.y })''',
'''                    requireNotNull(buildingByIdForSplit[b.buildingId]) { "Split-detail panel references unknown building @@{b.buildingId}" }
                    toPdfBuildingShape(b)''',
"adapter split building conversion")

rep(adapter,
'''                        val base = requireNotNull(buildingByIdForFullPlus[b.buildingId]) { "Full-plus-detail panel references unknown building @@{b.buildingId}" }
                        PdfBuildingShape(base.buildingId, base.label, b.polygon.map { it.x to it.y })''',
'''                        requireNotNull(buildingByIdForFullPlus[b.buildingId]) { "Full-plus-detail panel references unknown building @@{b.buildingId}" }
                        toPdfBuildingShape(b)''',
"adapter full-plus building conversion")

rep(adapter,
'''                if (building.labelItems.size > 1) {
                    failures += "@@{building.buildingId}: current renderer cannot preserve multiple verified 300/301 labels inside one footprint"
                }
                if (building.labelItems.singleOrNull()?.text != building.label) {
                    failures += "@@{building.buildingId}: building display label is not exactly the single verified interior label"
                }''',
'''                val normalizedMembers = building.sourceMembers.map(BuildingValidationEngine::normalizeMemberId).sorted()
                val normalizedLabels = building.labelItems.map { BuildingValidationEngine.normalizeMemberId(it.text) }.sorted()
                if (normalizedMembers != normalizedLabels) {
                    failures += "@@{building.buildingId}: verified 300/301 source-member/label inventory drift"
                }
                if (building.labelItems.size == 1 && building.labelItems.single().text != building.label) {
                    failures += "@@{building.buildingId}: single-label building display label does not match verified interior label"
                }
                if (building.labelItems.size > 1 && building.labelItems.any { it.origin == null }) {
                    failures += "@@{building.buildingId}: multi-label single-footprint rendering requires exact verified text origins"
                }''',
"adapter multi-label production gate")

rep(adapter,
'''                if (binding.sourceMemberIds.sorted() != building.sourceMembers.sorted()) failures += "$id: 300/301 source-member binding drift"
                val expectedLabels = building.labelItems.map { it.text }.sorted()
                if (binding.verifiedLabelTexts.sorted() != expectedLabels) failures += "$id: 300/301 member/label binding drift"
                if (expectedLabels.size != 1) failures += "$id: multi-label single-footprint rendering remains blocked until Phase 1C"
                if (expectedLabels.singleOrNull() != building.label) failures += "$id: rendered building label does not preserve verified label item"''',
'''                if (binding.sourceMemberIds.sorted() != building.sourceMembers.sorted()) failures += "$id: 300/301 source-member binding drift"
                val expectedLabels = building.labelItems.map { it.text }.sorted()
                if (binding.verifiedLabelTexts.sorted() != expectedLabels) failures += "$id: 300/301 member/label binding drift"
                if (expectedLabels.size > 1 && building.labelItems.any { it.origin == null }) failures += "$id: multi-label footprint has unverified text origin"''',
"adapter site multi-label production gate")

rep(adapter,
'''    private fun validateHousingAndBuildings(
        state: CurrentAuthoritativeAssignmentState,''',
'''    private fun toPdfBuildingShape(building: BuildingGeometry): PdfBuildingShape = PdfBuildingShape(
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
        state: CurrentAuthoritativeAssignmentState,''',
"adapter building render helper")

# Revalidate member/label semantics for detail buildings now that those labels become render-visible.
rep(adapter,
'''                else if (building.label != base.label || building.housingType != base.housingType || building.assigned != base.assigned) failures += "@@{panel.panelId}: rendered building meaning drift for @@{building.buildingId}"
                if (building.polygon.any { !rectContainsPoint(expectedDest, it) }) failures += "@@{panel.panelId}: rendered building escapes destination region"''',
'''                else {
                    if (building.label != base.label || building.housingType != base.housingType || building.assigned != base.assigned) failures += "@@{panel.panelId}: rendered building meaning drift for @@{building.buildingId}"
                    if (building.sourceMembers.sorted() != base.sourceMembers.sorted() || building.labelItems.map { it.text }.sorted() != base.labelItems.map { it.text }.sorted()) {
                        failures += "@@{panel.panelId}: rendered building 300/301 member-label binding drift for @@{building.buildingId}"
                    }
                    val detailBuildingValidation = BuildingValidationEngine.validateBuildings(listOf(building), applicable = true)
                    if (!detailBuildingValidation.passed) failures += detailBuildingValidation.failures.map { "@@{panel.panelId}: $it" }
                }
                if (building.polygon.any { !rectContainsPoint(expectedDest, it) }) failures += "@@{panel.panelId}: rendered building escapes destination region"
                if (building.labelItems.any { !rectContainsPoint(expectedDest, it.center) }) failures += "@@{panel.panelId}: rendered building label escapes destination region"''',
"split visible member-label validation")

rep(adapter,
'''            if (base.buildingId != detail.buildingId || base.label != detail.label || base.housingType != detail.housingType || base.assigned != detail.assigned) failures += "full_plus_detail building meaning drift for @@{base.buildingId}"
            if (base.polygon.size != detail.polygon.size) failures += "full_plus_detail building vertex-count drift for @@{base.buildingId}"
            else base.polygon.zip(detail.polygon).forEach { (src, dst) ->
                val mapped = mapPoint(src)
                if (!eq(mapped.x, dst.x) || !eq(mapped.y, dst.y)) failures += "full_plus_detail building geometry is not declared source-to-destination mapping for @@{base.buildingId}"
            }
            if (detail.polygon.any { !rectContainsPoint(panel.destinationBounds, it) }) failures += "full_plus_detail rendered building escapes detail destination"''',
'''            if (base.buildingId != detail.buildingId || base.label != detail.label || base.housingType != detail.housingType || base.assigned != detail.assigned) failures += "full_plus_detail building meaning drift for @@{base.buildingId}"
            if (base.sourceMembers.sorted() != detail.sourceMembers.sorted() || base.labelItems.map { it.text }.sorted() != detail.labelItems.map { it.text }.sorted()) {
                failures += "full_plus_detail building 300/301 member-label binding drift for @@{base.buildingId}"
            }
            if (base.polygon.size != detail.polygon.size) failures += "full_plus_detail building vertex-count drift for @@{base.buildingId}"
            else base.polygon.zip(detail.polygon).forEach { (src, dst) ->
                val mapped = mapPoint(src)
                if (!eq(mapped.x, dst.x) || !eq(mapped.y, dst.y)) failures += "full_plus_detail building geometry is not declared source-to-destination mapping for @@{base.buildingId}"
            }
            val baseItems = base.labelItems.sortedBy { it.text }
            val detailItems = detail.labelItems.sortedBy { it.text }
            if (baseItems.size != detailItems.size) failures += "full_plus_detail building label-item count drift for @@{base.buildingId}"
            else baseItems.zip(detailItems).forEach { (src, dst) ->
                val mappedCenter = mapPoint(src.center)
                if (src.text != dst.text || src.angleDeg != dst.angleDeg || src.fontSizePt != dst.fontSizePt || !eq(mappedCenter.x, dst.center.x) || !eq(mappedCenter.y, dst.center.y)) {
                    failures += "full_plus_detail building label-item mapping drift for @@{base.buildingId}/@@{src.text}"
                }
                if ((src.origin == null) != (dst.origin == null)) failures += "full_plus_detail building label origin binding drift for @@{base.buildingId}/@@{src.text}"
                if (src.origin != null && dst.origin != null) {
                    val mappedOrigin = mapPoint(src.origin)
                    if (!eq(mappedOrigin.x, dst.origin.x) || !eq(mappedOrigin.y, dst.origin.y)) failures += "full_plus_detail building label origin mapping drift for @@{base.buildingId}/@@{src.text}"
                }
            }
            val detailBuildingValidation = BuildingValidationEngine.validateBuildings(listOf(detail), applicable = true)
            if (!detailBuildingValidation.passed) failures += detailBuildingValidation.failures.map { "full_plus_detail: $it" }
            if (detail.polygon.any { !rectContainsPoint(panel.destinationBounds, it) }) failures += "full_plus_detail rendered building escapes detail destination"
            if (detail.labelItems.any { !rectContainsPoint(panel.destinationBounds, it.center) }) failures += "full_plus_detail rendered building label escapes detail destination"''',
"full-plus visible member-label validation")

# Candidate renderer regression fixture becomes one physical footprint carrying independent 300 and 301 labels.
rep(renderer_test,
'''        val buildings = listOf(
            PdfBuildingShape("site-building-300", "300", listOf(235.0 to 165.0, 300.0 to 165.0, 300.0 to 210.0, 235.0 to 210.0)),
            PdfBuildingShape("site-building-301", "301", listOf(335.0 to 165.0, 400.0 to 165.0, 400.0 to 210.0, 335.0 to 210.0))
        )''',
'''        val buildings = listOf(
            PdfBuildingShape(
                buildingId = "site-building-300-301",
                label = "300-301",
                points = listOf(235.0 to 165.0, 400.0 to 165.0, 400.0 to 225.0, 235.0 to 225.0),
                labelItems = listOf(
                    PdfBuildingLabelItem("300", 285.0, 190.0, 275.604, 192.338, 0.0, 9.0),
                    PdfBuildingLabelItem("301", 350.0, 200.0, 340.604, 202.338, 0.0, 9.0)
                )
            )
        )''',
"single-footprint renderer fixture")

rep(renderer_test,
'''                sourceBuildingIds = listOf("site-building-300", "site-building-301"),''',
'''                sourceBuildingIds = listOf("site-building-300-301"),''',
"renderer source building identity")

rep(renderer_test,
'''                buildingBindings = listOf(
                    PdfBuildingMemberLabelBinding("site-building-300", listOf("300"), listOf("300")),
                    PdfBuildingMemberLabelBinding("site-building-301", listOf("301"), listOf("301"))
                ),''',
'''                buildingBindings = listOf(
                    PdfBuildingMemberLabelBinding("site-building-300-301", listOf("300", "301"), listOf("300", "301"))
                ),''',
"renderer building member binding")

rep(renderer_test,
'''        check(siteRaw.contains("% TCS_SITE_BUILDING_ASSIGNMENT_EXPLICIT"))
        check(siteRaw.contains("SITE / BUILDINGS") && siteRaw.contains("VERIFIED ACCESS"))''',
'''        check(siteRaw.contains("% TCS_SITE_BUILDING_ASSIGNMENT_EXPLICIT"))
        check(siteRaw.contains("% TCS_BUILDING_LABEL_ITEMS_EXACT site-building-300-301"))
        check(siteRaw.contains("(300) Tj") && siteRaw.contains("(301) Tj")) { "single-footprint 300/301 labels were not both rendered" }
        check(siteRaw.contains("SITE / BUILDINGS") && siteRaw.contains("VERIFIED ACCESS"))''',
"renderer output assertion")

rep(renderer_test,
'''        check(runCatching {
            CandidatePdfRenderer.renderNonFieldFixture(
                template,
                site.copy(siteBuildingAssignment = siteLayout.copy(
                    accessInset = requireNotNull(siteLayout.accessInset).copy(
                        roads = siteLayout.accessInset.roads.map { it.copy(status = PdfRoadStatus.DO_NOT_WORK) }
                    )
                ))
            )
        }.isFailure) { "site_building_assignment access work-status drift unexpectedly rendered" }''',
'''        check(runCatching {
            CandidatePdfRenderer.renderNonFieldFixture(
                template,
                site.copy(buildings = site.buildings.map { it.copy(labelItems = it.labelItems.dropLast(1)) })
            )
        }.isFailure) { "site_building_assignment missing 301 label item unexpectedly rendered" }
        check(runCatching {
            CandidatePdfRenderer.renderNonFieldFixture(
                template,
                site.copy(buildings = site.buildings.map { it.copy(labelItems = it.labelItems.mapIndexed { index, item -> if (index == 1) item.copy(originX = null, originTopY = null) else item }) })
            )
        }.isFailure) { "site_building_assignment guessed multi-label origin unexpectedly rendered" }
        check(runCatching {
            CandidatePdfRenderer.renderNonFieldFixture(
                template,
                site.copy(siteBuildingAssignment = siteLayout.copy(
                    accessInset = requireNotNull(siteLayout.accessInset).copy(
                        roads = siteLayout.accessInset.roads.map { it.copy(status = PdfRoadStatus.DO_NOT_WORK) }
                    )
                ))
            )
        }.isFailure) { "site_building_assignment access work-status drift unexpectedly rendered" }''',
"renderer multi-label failure regressions")

# Adapter regression fixture: one footprint with two source members/labels and exact origins.
rep(adapter_test, '''            label = "435",''', '''            label = "435-437",''', "adapter aggregate label")
rep(adapter_test, '''            sourceMembers = listOf("435"),''', '''            sourceMembers = listOf("435", "437"),''', "adapter source members")
rep(adapter_test,
'''            labelItems = listOf(
                BuildingLabelItem(
                    text = "435",
                    center = Point2D(270.0, 187.0),
                    origin = Point2D(260.0, 190.0),
                    angleDeg = 0.0,
                    fontSizePt = 9.0
                )
            ),''',
'''            labelItems = listOf(
                BuildingLabelItem(
                    text = "435",
                    center = Point2D(265.0, 184.0),
                    origin = Point2D(255.0, 187.0),
                    angleDeg = 0.0,
                    fontSizePt = 9.0
                ),
                BuildingLabelItem(
                    text = "437",
                    center = Point2D(285.0, 196.0),
                    origin = Point2D(275.0, 199.0),
                    angleDeg = 0.0,
                    fontSizePt = 9.0
                )
            ),''',
"adapter multi-label items")

rep(adapter_test, '''            candidateBuildingMemberIds = listOf("435"),''', '''            candidateBuildingMemberIds = listOf("435", "437"),''', "adapter live member list")

rep(adapter_test,
'''                    sourceMemberIds = listOf("435"),
                    verifiedLabelTexts = listOf("435")''',
'''                    sourceMemberIds = listOf("435", "437"),
                    verifiedLabelTexts = listOf("435", "437")''',
"adapter verified binding")

rep(adapter_test,
'''        check(requireNotNull(siteAdapted.renderSpec.siteBuildingAssignment).buildingBindings.single().sourceMemberIds == listOf("435"))''',
'''        check(requireNotNull(siteAdapted.renderSpec.siteBuildingAssignment).buildingBindings.single().sourceMemberIds == listOf("435", "437"))
        check(siteAdapted.renderSpec.buildings.single().labelItems.map { it.text } == listOf("435", "437")) {
            "adapter flattened or dropped verified multi-label building items"
        }''',
"adapter positive multi-label assertion")

rep(adapter_test,
'''        expectBlocked(
            kb,
            siteInput.copy(siteBuildingAssignment = siteGeometry.copy(
                accessInset = requireNotNull(siteGeometry.accessInset).copy(
                    renderedRoads = siteGeometry.accessInset.renderedRoads.map { it.copy(status = "red") }
                )
            )),
            "road meaning/status drift"
        )''',
'''        expectBlocked(
            kb,
            siteInput.copy(siteBuildingAssignment = siteGeometry.copy(
                buildingBindings = listOf(
                    VerifiedSiteBuildingBinding("site-building-435", listOf("435", "437"), listOf("435"))
                )
            )),
            "member/label binding drift"
        )
        expectBlocked(
            kb,
            siteInput.copy(assignment = siteState.copy(
                buildings = listOf(siteBuilding.copy(labelItems = siteBuilding.labelItems.mapIndexed { index, item -> if (index == 1) item.copy(origin = null) else item }))
            )),
            "multi-label single-footprint rendering requires exact verified text origins"
        )
        expectBlocked(
            kb,
            siteInput.copy(siteBuildingAssignment = siteGeometry.copy(
                accessInset = requireNotNull(siteGeometry.accessInset).copy(
                    renderedRoads = siteGeometry.accessInset.renderedRoads.map { it.copy(status = "red") }
                )
            )),
            "road meaning/status drift"
        )''',
"adapter multi-label drift/origin vetoes")

rep(adapter_test,
'''        println("adapter_fail_closed_mutations=28")''',
'''        println("adapter_fail_closed_mutations=32")''',
"adapter mutation marker")

# Restore Kotlin interpolation.
for p in targets:
    p.write_text(p.read_text().replace("@@{", D))

print("PHASE1C_APPLY=PASS")
for p in targets:
    print(p.relative_to(root))

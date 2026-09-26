#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CAND = ROOT / "core/src/main/kotlin/com/koenterprises/territorycardstudio/core/CandidatePdfRenderer.kt"
ADAPTER = ROOT / "core/src/main/kotlin/com/koenterprises/territorycardstudio/core/ProductionRenderModelAdapter.kt"
CTEST = ROOT / "core/src/test/kotlin/com/koenterprises/territorycardstudio/core/CandidatePdfRendererTest.kt"
ATEST = ROOT / "core/src/test/kotlin/com/koenterprises/territorycardstudio/core/ProductionRenderModelAdapterTest.kt"
GEN = ROOT / "core/src/test/kotlin/com/koenterprises/territorycardstudio/core/MultiLabel300301FixtureGenerator.kt"

def replace_once(path: Path, old: str, new: str, label: str):
    s = path.read_text()
    n = s.count(old)
    if n != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {n}")
    path.write_text(s.replace(old, new, 1))

# Candidate renderer: exact BuildingLabelItem geometry becomes part of the render spec.
replace_once(
    CAND,
    """        validateSpec(spec)
        val metrics = TemplateFontMetrics.parse(templatePdf)
""",
    """        val metrics = TemplateFontMetrics.parse(templatePdf)
        validateSpec(spec, metrics)
""",
    "candidate metrics-before-validation",
)
replace_once(
    CAND,
    "    private fun validateSpec(spec: CandidatePdfRenderSpec) {\n",
    "    private fun validateSpec(spec: CandidatePdfRenderSpec, metrics: TemplateFontMetrics) {\n",
    "candidate validateSpec signature",
)
replace_once(
    CAND,
    "            spec.buildings.forEach { add(it.label) }\n",
    """            spec.buildings.forEach { building ->
                add(building.label)
                building.labelItems.forEach { add(it.text) }
            }
""",
    "candidate base building string preflight",
)
replace_once(
    CAND,
    "                detail.buildings.forEach { add(it.label) }\n",
    """                detail.buildings.forEach { building ->
                    add(building.label)
                    building.labelItems.forEach { add(it.text) }
                }
""",
    "candidate detail building string preflight",
)
replace_once(
    CAND,
    """        spec.buildings.forEach { b ->
            require(b.points.size >= 3) { "Building polygon requires >=3 points" }
            require(b.points.all { pagePoint(it.first, it.second) }) { "Building polygon point outside locked page" }
        }
""",
    """        spec.buildings.forEach { b ->
            require(b.points.size >= 3) { "Building polygon requires >=3 points" }
            require(b.points.all { pagePoint(it.first, it.second) }) { "Building polygon point outside locked page" }
            validateBuildingLabelItems(b, metrics, productionCandidate = !spec.nonFieldFixture)
        }
""",
    "candidate building validation",
)

# Remove the deliberate Phase 1C blocker while retaining exact binding parity.
replace_once(
    CAND,
    """            require(binding.verifiedLabelTexts.size == 1) {
                "site_building_assignment $buildingId multi-label single-footprint rendering remains blocked until Phase 1C"
            }
            require(binding.verifiedLabelTexts.single() == base.label) { "site_building_assignment $buildingId verified label binding drift" }
""",
    """            val renderedLabelTexts = if (base.labelItems.isEmpty()) listOf(base.label) else base.labelItems.map { it.text }
            require(binding.verifiedLabelTexts.sorted() == renderedLabelTexts.sorted()) {
                "site_building_assignment $buildingId verified multi-label binding drift"
            }
""",
    "candidate Phase1C site blocker",
)

# Preserve explicit label geometry in the PDF model while retaining legacy synthetic fallback.
replace_once(
    CAND,
    """data class PdfBuildingShape(
    val buildingId: String,
    val label: String,
    val points: List<Pair<Double, Double>>
)
""",
    """data class PdfBuildingLabelItem(
    val text: String,
    val centerX: Double,
    val centerTopY: Double,
    val originX: Double? = null,
    val originTopY: Double? = null,
    val angleDeg: Double = 0.0,
    val fontSizePt: Double = 9.0
)

data class PdfBuildingShape(
    val buildingId: String,
    val label: String,
    val points: List<Pair<Double, Double>>,
    val labelItems: List<PdfBuildingLabelItem> = emptyList()
)
""",
    "candidate building model",
)

replace_once(
    CAND,
    """    private fun drawBuilding(b: StringBuilder, building: PdfBuildingShape) {
        fillColor(b, "#EEF2F4"); strokeColor(b, "#536D7D"); lineWidth(b, 0.8)
        val first = building.points.first()
        b.append(num(first.first)).append(' ').append(num(PAGE_H - first.second)).append(" m\n")
        building.points.drop(1).forEach { (x, y) -> b.append(num(x)).append(' ').append(num(PAGE_H - y)).append(" l\n") }
        b.append("h B\n")
        val cx = building.points.map { it.first }.average()
        val cyTop = building.points.map { it.second }.average()
        fillColor(b, "#27343C")
        text(b, "F2+0", 8.0, cx - (building.label.length * 2.0), PAGE_H - cyTop + 2.5, building.label)
    }
""",
    """    private fun drawBuilding(b: StringBuilder, building: PdfBuildingShape) {
        fillColor(b, "#EEF2F4"); strokeColor(b, "#536D7D"); lineWidth(b, 0.8)
        val first = building.points.first()
        b.append(num(first.first)).append(' ').append(num(PAGE_H - first.second)).append(" m\n")
        building.points.drop(1).forEach { (x, y) -> b.append(num(x)).append(' ').append(num(PAGE_H - y)).append(" l\n") }
        b.append("h B\n")
        fillColor(b, "#27343C")
        if (building.labelItems.isEmpty()) {
            // Legacy synthetic regression only. Production candidates are rejected above unless exact label items are present.
            val cx = building.points.map { it.first }.average()
            val cyTop = building.points.map { it.second }.average()
            text(b, "F2+0", 8.0, cx - (building.label.length * 2.0), PAGE_H - cyTop + 2.5, building.label)
        } else {
            building.labelItems.forEach { drawBuildingLabelItem(b, it) }
        }
    }

    private fun drawBuildingLabelItem(b: StringBuilder, item: PdfBuildingLabelItem) {
        val metrics = currentTemplateMetrics ?: error("Building-label font metrics unavailable")
        val originX = item.originX ?: (item.centerX - metrics.regularWidth(item.text, item.fontSizePt) / 2.0)
        val originTopY = item.originTopY ?: (item.centerTopY + item.fontSizePt * 0.27)
        val angle = Math.toRadians(item.angleDeg)
        val c = cos(angle); val s = sin(angle)
        b.append("BT /F2+0 ").append(num(item.fontSizePt)).append(" Tf ")
            .append(num(c)).append(' ').append(num(s)).append(' ')
            .append(num(-s)).append(' ').append(num(c)).append(' ')
            .append(num(originX)).append(' ').append(num(PAGE_H - originTopY)).append(" Tm (")
            .append(pdfEscape(item.text)).append(") Tj ET\n")
    }

    private fun validateBuildingLabelItems(building: PdfBuildingShape, metrics: TemplateFontMetrics, productionCandidate: Boolean) {
        if (productionCandidate) require(building.labelItems.isNotEmpty()) {
            "${building.buildingId}: production building footprint requires explicit verified label-item geometry"
        }
        if (building.labelItems.isEmpty()) return
        require(building.labelItems.map { it.text }.distinct().size == building.labelItems.size) {
            "${building.buildingId}: duplicate verified building label text"
        }
        if (building.labelItems.size > 1) require(building.labelItems.all { it.originX != null && it.originTopY != null }) {
            "${building.buildingId}: multi-label single-footprint rendering requires explicit source-bound label origins"
        }
        building.labelItems.forEach { item ->
            require(item.text.isNotBlank()) { "${building.buildingId}: blank building label item" }
            require(item.fontSizePt > 0.0) { "${building.buildingId}: invalid building label font size" }
            require(pagePoint(item.centerX, item.centerTopY)) { "${building.buildingId}: building label center outside locked page" }
            if (item.originX != null || item.originTopY != null) {
                require(item.originX != null && item.originTopY != null && pagePoint(item.originX, item.originTopY)) {
                    "${building.buildingId}: building label origin is incomplete or outside locked page"
                }
            }
            require(pointInPolygon(item.centerX, item.centerTopY, building.points)) {
                "${building.buildingId}: building label '${item.text}' center escapes its single physical footprint"
            }
            // Exact font metrics bind the preserved center/origin data into the render path.
            require(metrics.regularWidth(item.text, item.fontSizePt) > 0.0) {
                "${building.buildingId}: building label '${item.text}' has no renderable width"
            }
        }
    }

    private fun pointInPolygon(x: Double, y: Double, polygon: List<Pair<Double, Double>>): Boolean {
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val xi = polygon[i].first; val yi = polygon[i].second
            val xj = polygon[j].first; val yj = polygon[j].second
            val crosses = ((yi > y) != (yj > y)) &&
                (x < (xj - xi) * (y - yi) / ((yj - yi).takeIf { kotlin.math.abs(it) > 1e-12 } ?: 1e-12) + xi)
            if (crosses) inside = !inside
            j = i
        }
        return inside
    }
""",
    "candidate explicit building label renderer",
)

# Canonical hash must bind every exact building label item. Existing empty-item fixtures remain byte-identical.
replace_once(
    CAND,
    """            buildings.forEachIndexed { i, x -> append("building[").append(i).append("]=").append(x.buildingId).append('|').append(x.label); x.points.forEach { p -> append('|').append(p.first).append(',').append(p.second) }; append('\n') }
""",
    """            buildings.forEachIndexed { i, x ->
                append("building[").append(i).append("]=").append(x.buildingId).append('|').append(x.label)
                x.points.forEach { p -> append('|').append(p.first).append(',').append(p.second) }
                x.labelItems.sortedWith(compareBy<PdfBuildingLabelItem>({ it.text }, { it.centerX }, { it.centerTopY })).forEach { item ->
                    append("|buildingLabel:").append(item.text).append('@').append(item.centerX).append(',').append(item.centerTopY)
                        .append(',').append(item.originX ?: "null").append(',').append(item.originTopY ?: "null")
                        .append(',').append(item.angleDeg).append(',').append(item.fontSizePt)
                }
                append('\n')
            }
""",
    "candidate base building canonical hash",
)
replace_once(
    CAND,
    """                    panel.buildings.sortedBy { it.buildingId }.forEach { x -> append("|building:").append(x.buildingId).append(',').append(x.label); x.points.forEach { p -> append('@').append(p.first).append(',').append(p.second) } }
""",
    """                    panel.buildings.sortedBy { it.buildingId }.forEach { x ->
                        append("|building:").append(x.buildingId).append(',').append(x.label)
                        x.points.forEach { p -> append('@').append(p.first).append(',').append(p.second) }
                        x.labelItems.sortedWith(compareBy<PdfBuildingLabelItem>({ it.text }, { it.centerX }, { it.centerTopY })).forEach { item ->
                            append("|buildingLabel:").append(item.text).append('@').append(item.centerX).append(',').append(item.centerTopY)
                                .append(',').append(item.originX ?: "null").append(',').append(item.originTopY ?: "null")
                                .append(',').append(item.angleDeg).append(',').append(item.fontSizePt)
                        }
                    }
""",
    "candidate split building canonical hash",
)
replace_once(
    CAND,
    """                p.buildings.sortedBy { it.buildingId }.forEach { x -> append("|building:").append(x.buildingId).append(',').append(x.label); x.points.forEach { pt -> append('@').append(pt.first).append(',').append(pt.second) } }
""",
    """                p.buildings.sortedBy { it.buildingId }.forEach { x ->
                    append("|building:").append(x.buildingId).append(',').append(x.label)
                    x.points.forEach { pt -> append('@').append(pt.first).append(',').append(pt.second) }
                    x.labelItems.sortedWith(compareBy<PdfBuildingLabelItem>({ it.text }, { it.centerX }, { it.centerTopY })).forEach { item ->
                        append("|buildingLabel:").append(item.text).append('@').append(item.centerX).append(',').append(item.centerTopY)
                            .append(',').append(item.originX ?: "null").append(',').append(item.originTopY ?: "null")
                            .append(',').append(item.angleDeg).append(',').append(item.fontSizePt)
                    }
                }
""",
    "candidate full-plus building canonical hash",
)

# Bind a durable marker into PDFs that exercise the new behavior.
replace_once(
    CAND,
    """        b.append("% TCS_LOCKED_TEMPLATE_SHA256 ").append(LockedPdfRendererAuthorityHashes.TEMPLATE_PDF_SHA256).append('\n')
""",
    """        b.append("% TCS_LOCKED_TEMPLATE_SHA256 ").append(LockedPdfRendererAuthorityHashes.TEMPLATE_PDF_SHA256).append('\n')
        if (spec.buildings.any { it.labelItems.size > 1 }) b.append("% TCS_MULTI_LABEL_SINGLE_FOOTPRINT_EXACT\n")
""",
    "candidate Phase1C marker",
)
replace_once(
    CAND,
    """        if (spec.layoutMode == "site_building_assignment" && !raw.contains("% TCS_SITE_BUILDING_ASSIGNMENT_EXPLICIT")) errors += "Explicit site/building assignment marker missing"
""",
    """        if (spec.layoutMode == "site_building_assignment" && !raw.contains("% TCS_SITE_BUILDING_ASSIGNMENT_EXPLICIT")) errors += "Explicit site/building assignment marker missing"
        if (spec.buildings.any { it.labelItems.size > 1 } && !raw.contains("% TCS_MULTI_LABEL_SINGLE_FOOTPRINT_EXACT")) errors += "Exact multi-label single-footprint marker missing"
""",
    "candidate exact validator Phase1C marker",
)

# Adapter: remove the global Phase1C blocker and require exact member/label preservation.
replace_once(
    ADAPTER,
    """                if (building.labelItems.size > 1) {
                    failures += "${building.buildingId}: current renderer cannot preserve multiple verified 300/301 labels inside one footprint"
                }
                if (building.labelItems.singleOrNull()?.text != building.label) {
                    failures += "${building.buildingId}: building display label is not exactly the single verified interior label"
                }
""",
    """                if (building.sourceMembers.isNotEmpty()) {
                    val memberIds = building.sourceMembers.map(BuildingValidationEngine::normalizeMemberId).sorted()
                    val labelIds = building.labelItems.map { BuildingValidationEngine.normalizeMemberId(it.text) }.sorted()
                    if (memberIds != labelIds) failures += "${building.buildingId}: 300/301 source-member and verified label-item sets differ"
                }
                if (building.labelItems.size > 1 && building.labelItems.any { it.origin == null }) {
                    failures += "${building.buildingId}: multi-label single-footprint requires explicit source-bound label origins"
                }
""",
    "adapter Phase1C blocker",
)

# Base production building conversion now carries exact authoritative label geometry.
replace_once(
    ADAPTER,
    """            PdfBuildingShape(
                buildingId = building.buildingId,
                label = building.label,
                points = building.polygon.map { it.x to it.y }
            )
""",
    """            PdfBuildingShape(
                buildingId = building.buildingId,
                label = building.label,
                points = building.polygon.map { it.x to it.y },
                labelItems = building.labelItems.map(::toPdfBuildingLabelItem)
            )
""",
    "adapter base building conversion",
)

# Split/full-plus rendered building objects already constitute verified destination geometry; preserve their label items too.
replace_once(
    ADAPTER,
    """                    PdfBuildingShape(base.buildingId, base.label, b.polygon.map { it.x to it.y })
""",
    """                    PdfBuildingShape(
                        base.buildingId,
                        base.label,
                        b.polygon.map { it.x to it.y },
                        b.labelItems.map(::toPdfBuildingLabelItem)
                    )
""",
    "adapter split building conversion",
)
replace_once(
    ADAPTER,
    """                        PdfBuildingShape(base.buildingId, base.label, b.polygon.map { it.x to it.y })
""",
    """                        PdfBuildingShape(
                            base.buildingId,
                            base.label,
                            b.polygon.map { it.x to it.y },
                            b.labelItems.map(::toPdfBuildingLabelItem)
                        )
""",
    "adapter full-plus building conversion",
)

# Site binding parity now allows multiple labels but never drops or invents one.
replace_once(
    ADAPTER,
    """                if (expectedLabels.size != 1) failures += "$id: multi-label single-footprint rendering remains blocked until Phase 1C"
                if (expectedLabels.singleOrNull() != building.label) failures += "$id: rendered building label does not preserve verified label item"
""",
    """                if (expectedLabels.isEmpty()) failures += "$id: assigned building has no verified label items"
""",
    "adapter site Phase1C blocker",
)

# Helper maps exact BuildingLabelItem geometry into renderer data.
anchor = "    private fun LabelPlacement.curvePathPoints(): List<Point2D> = emptyList()\n"
insert = """    private fun toPdfBuildingLabelItem(item: BuildingLabelItem): PdfBuildingLabelItem = PdfBuildingLabelItem(
        text = item.text,
        centerX = item.center.x,
        centerTopY = item.center.y,
        originX = item.origin?.x,
        originTopY = item.origin?.y,
        angleDeg = item.angleDeg,
        fontSizePt = item.fontSizePt
    )

"""
replace_once(ADAPTER, anchor, insert + anchor, "adapter building-label helper")

# Strengthen split-panel preservation when explicit label geometry is provided by the verified panel.
replace_once(
    ADAPTER,
    """                else if (building.label != base.label || building.housingType != base.housingType || building.assigned != base.assigned) failures += "${panel.panelId}: rendered building meaning drift for ${building.buildingId}"
                if (building.polygon.any { !rectContainsPoint(expectedDest, it) }) failures += "${panel.panelId}: rendered building escapes destination region"
""",
    """                else {
                    if (building.label != base.label || building.housingType != base.housingType || building.assigned != base.assigned) failures += "${panel.panelId}: rendered building meaning drift for ${building.buildingId}"
                    if (building.sourceMembers.map(BuildingValidationEngine::normalizeMemberId).sorted() != base.sourceMembers.map(BuildingValidationEngine::normalizeMemberId).sorted()) failures += "${panel.panelId}: rendered building source-member drift for ${building.buildingId}"
                    if (building.labelItems.map { BuildingValidationEngine.normalizeMemberId(it.text) }.sorted() != base.labelItems.map { BuildingValidationEngine.normalizeMemberId(it.text) }.sorted()) failures += "${panel.panelId}: rendered building label-item drift for ${building.buildingId}"
                }
                if (building.polygon.any { !rectContainsPoint(expectedDest, it) }) failures += "${panel.panelId}: rendered building escapes destination region"
                if (building.labelItems.any { !rectContainsPoint(expectedDest, it.center) }) failures += "${panel.panelId}: rendered building label item escapes destination region"
""",
    "adapter split building label preservation",
)

# Candidate renderer regression: new fixture is a single footprint carrying exact 300 and 301 labels.
run_marker = "        check(runCatching { CandidatePdfRenderer.renderNonFieldFixture(template, site.copy(siteBuildingAssignment = siteLayout.copy(sourceSegmentIds = listOf(\"seg-access\")))) }.isFailure) { \"site_building_assignment missing base road ownership unexpectedly rendered\" }\n"
phase1c_test = run_marker + """
        val multi = multiLabel300301FixtureSpec()
        val multiFirst = CandidatePdfRenderer.renderNonFieldFixture(template, multi)
        val multiSecond = CandidatePdfRenderer.renderNonFieldFixture(template, multi)
        check(multiFirst.pdfBytes.contentEquals(multiSecond.pdfBytes)) { "Phase 1C multi-label renderer is not byte-deterministic" }
        check(multiFirst.exactValidation.passed)
        val multiRaw = multiFirst.pdfBytes.toString(Charsets.ISO_8859_1)
        check(multiRaw.contains("% TCS_MULTI_LABEL_SINGLE_FOOTPRINT_EXACT"))
        check(multiRaw.contains("(300)") && multiRaw.contains("(301)"))
        check(multi.buildings.size == 1 && multi.buildings.single().labelItems.map { it.text } == listOf("300", "301"))
        val multiBuilding = multi.buildings.single()
        check(runCatching {
            CandidatePdfRenderer.renderNonFieldFixture(template, multi.copy(
                buildings = listOf(multiBuilding.copy(labelItems = multiBuilding.labelItems.dropLast(1)))
            ))
        }.isFailure) { "Phase 1C dropped member label unexpectedly rendered" }
        check(runCatching {
            CandidatePdfRenderer.renderNonFieldFixture(template, multi.copy(
                buildings = listOf(multiBuilding.copy(labelItems = multiBuilding.labelItems.mapIndexed { index, item ->
                    if (index == 1) item.copy(text = "300") else item
                }))
            ))
        }.isFailure) { "Phase 1C duplicate member label unexpectedly rendered" }
        check(runCatching {
            CandidatePdfRenderer.renderNonFieldFixture(template, multi.copy(
                buildings = listOf(multiBuilding.copy(labelItems = multiBuilding.labelItems.mapIndexed { index, item ->
                    if (index == 1) item.copy(centerX = 470.0, originX = 462.0) else item
                }))
            ))
        }.isFailure) { "Phase 1C escaped label unexpectedly rendered" }
"""
replace_once(CTEST, run_marker, phase1c_test, "candidate Phase1C run regression")

fixture_anchor = "    fun siteBuildingAssignmentFixtureSpec(): CandidatePdfRenderSpec {\n"
multi_fixture = """    fun multiLabel300301FixtureSpec(): CandidatePdfRenderSpec {
        val building = PdfBuildingShape(
            buildingId = "single-footprint-300-301",
            label = "300-301",
            points = listOf(245.0 to 150.0, 385.0 to 150.0, 385.0 to 225.0, 245.0 to 225.0),
            labelItems = listOf(
                PdfBuildingLabelItem("300", 280.0, 185.0, 270.0, 188.0, 0.0, 9.0),
                PdfBuildingLabelItem("301", 350.0, 185.0, 340.0, 188.0, 0.0, 9.0)
            )
        )
        return CandidatePdfRenderSpec(
            identity = TerritoryIdentity(993, TerritoryClass.Apartment, 'a'),
            locality = "Oakland Township",
            updated = "9/26/2026",
            directionsLines = listOf(
                "Directions: Multi-label 300/301 renderer regression.",
                "One verified physical footprint; two verified labels.",
                "NOT FOR FIELD USE."
            ),
            layoutMode = "site_building_assignment",
            sourceMasterLabel = "SYNTHETIC-ANDROID-MULTI-LABEL-300-301",
            roads = listOf(
                PdfRoadStroke("seg-site", "Site Loop", 205.0, 270.0, 455.0, 270.0, PdfRoadStatus.WORK_INSIDE_ONLY)
            ),
            labels = listOf(
                PdfStreetLabel("label-site", "seg-site", "Site Loop", 285.0, 263.0, assignedRoadGapPx = 3.0)
            ),
            buildings = listOf(building),
            siteBuildingAssignment = PdfSiteBuildingAssignmentLayout(
                diagramId = "multi-label-site-diagram",
                diagramRect = PdfDetailRect(176.0, 20.0, 500.0, 363.0),
                sourceSegmentIds = listOf("seg-site"),
                sourceBuildingIds = listOf("single-footprint-300-301"),
                sourceLabelIds = listOf("label-site"),
                buildingBindings = listOf(
                    PdfBuildingMemberLabelBinding(
                        "single-footprint-300-301",
                        listOf("300", "301"),
                        listOf("300", "301")
                    )
                )
            ),
            nonFieldFixture = true
        )
    }

"""
replace_once(CTEST, fixture_anchor, multi_fixture + fixture_anchor, "candidate Phase1C fixture")

# Adapter Phase 1B-3 synthetic state now exercises the exact Phase 1C multi-label path.
replace_once(
    ATEST,
    """        val siteBuilding = BuildingGeometry(
            buildingId = "site-building-435",
            label = "435",
            housingType = "apartment",
            assigned = true,
            attachedGroup = "site-building",
            sourceMembers = listOf("435"),
            labelItems = listOf(
                BuildingLabelItem(
                    text = "435",
                    center = Point2D(270.0, 187.0),
                    origin = Point2D(260.0, 190.0),
                    angleDeg = 0.0,
                    fontSizePt = 9.0
                )
            ),
""",
    """        val siteBuilding = BuildingGeometry(
            buildingId = "site-building-435",
            label = "435-437",
            housingType = "apartment",
            assigned = true,
            attachedGroup = "site-building",
            sourceMembers = listOf("435", "437"),
            labelItems = listOf(
                BuildingLabelItem(
                    text = "435",
                    center = Point2D(265.0, 187.0),
                    origin = Point2D(255.0, 190.0),
                    angleDeg = 0.0,
                    fontSizePt = 9.0
                ),
                BuildingLabelItem(
                    text = "437",
                    center = Point2D(290.0, 187.0),
                    origin = Point2D(280.0, 190.0),
                    angleDeg = 0.0,
                    fontSizePt = 9.0
                )
            ),
""",
    "adapter Phase1C synthetic building",
)
replace_once(
    ATEST,
    "            candidateBuildingMemberIds = listOf(\"435\"),\n",
    "            candidateBuildingMemberIds = listOf(\"435\", \"437\"),\n",
    "adapter Phase1C request members",
)
replace_once(
    ATEST,
    """                    sourceMemberIds = listOf("435"),
                    verifiedLabelTexts = listOf("435")
""",
    """                    sourceMemberIds = listOf("435", "437"),
                    verifiedLabelTexts = listOf("435", "437")
""",
    "adapter Phase1C site binding",
)
replace_once(
    ATEST,
    """        check(requireNotNull(siteAdapted.renderSpec.siteBuildingAssignment).buildingBindings.single().sourceMemberIds == listOf("435"))
""",
    """        check(requireNotNull(siteAdapted.renderSpec.siteBuildingAssignment).buildingBindings.single().sourceMemberIds == listOf("435", "437"))
        check(siteAdapted.renderSpec.buildings.single().labelItems.map { it.text } == listOf("435", "437"))
        check(siteAdapted.renderSpec.buildings.single().labelItems.map { it.centerX } == listOf(265.0, 290.0))
""",
    "adapter Phase1C exact assertions",
)
replace_once(
    ATEST,
    """                buildingBindings = listOf(VerifiedSiteBuildingBinding("site-building-435", listOf("WRONG"), listOf("435")))
""",
    """                buildingBindings = listOf(VerifiedSiteBuildingBinding("site-building-435", listOf("WRONG"), listOf("435", "437")))
""",
    "adapter Phase1C wrong-member mutation",
)
replace_once(
    ATEST,
    """        println("adapter_fail_closed_mutations=28")
""",
    """        println("adapter_fail_closed_mutations=30")
""",
    "adapter Phase1C mutation count",
)

GEN.write_text("""package com.koenterprises.territorycardstudio.core

import java.io.File

fun main(args: Array<String>) {
    require(args.size == 2) { "usage: MultiLabel300301FixtureGenerator ASSETS_DIR OUTPUT.pdf" }
    val assets = File(args[0])
    val output = File(args[1])
    val template = File(assets, "render-authority/Canonical-New-Designed-Template-R48.pdf").readBytes()
    val spec = CandidatePdfRendererTest.multiLabel300301FixtureSpec()
    val result = CandidatePdfRenderer.renderNonFieldFixture(template, spec)
    output.parentFile?.mkdirs()
    output.writeBytes(result.pdfBytes)
    println("pdf=${output.absolutePath}")
    println("bytes=${result.pdfBytes.size}")
    println("spec_sha256=${result.renderSpecSha256}")
    println("pdf_sha256=${result.pdfSha256}")
    println("validation_passed=${result.exactValidation.passed}")
}
""")

print("PHASE1C_TRANSFORM=PASS")

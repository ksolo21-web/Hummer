package com.koenterprises.territorycardstudio.core

/** Ancillary is an explicit source fact, never inferred from a missing number or an
 * unassigned checkbox. Residential exclusions remain red even without a member label.
 * These helpers create unapproved geometry only; no source, GIS or PDF approval is granted. */
object NativeBuildingWorkSemantics {
    const val ANCILLARY = "ancillary"
    fun isContextOnly(building: BuildingGeometry): Boolean = building.housingType == ANCILLARY

    fun failures(building: BuildingGeometry): List<String> = if (!isContextOnly(building)) emptyList() else buildList {
        if (building.assigned) add("${building.buildingId}: ancillary context cannot be assigned for work")
        if (building.sourceMembers.isNotEmpty() || building.labelItems.isNotEmpty() || building.label.isNotBlank())
            add("${building.buildingId}: ancillary context cannot carry residential member labels")
    }

    /** Shared by the native editor and executable core regression tests. Changing a kind
     * does not silently delete assignment/member facts; conflicting input is rejected. */
    fun createSourceItem(id: String, residentialHousingType: String, assigned: Boolean,
        ancillaryContext: Boolean, members: List<String>, polygon: List<Point2D>,
        labeler: (List<String>, List<Point2D>) -> List<BuildingLabelItem>): BuildingGeometry {
        require(id.isNotBlank() && id == id.trim() && id.length <= 120 && id.all { it.code in 32..126 }) {
            "Enter a printable source building identifier"
        }
        require(residentialHousingType in setOf("apartment", "condo", "townhome", "mobile_home", "manufactured_home")) {
            "Use the territory's supported residential housing type"
        }
        require(!ancillaryContext || (!assigned && members.isEmpty())) {
            "Ancillary context must be unassigned and have no residential member labels; review these fields explicitly"
        }
        require(!assigned || members.isNotEmpty()) { "A worked residence requires its source member labels" }
        val detachedPolygon = polygon.map { it.copy() }
        val labels = labeler(members.toList(), detachedPolygon)
        val building = BuildingGeometry(id, members.joinToString("/"),
            if (ancillaryContext) ANCILLARY else residentialHousingType, assigned, "", members.toList(), labels, detachedPolygon)
        val report = BuildingValidationEngine.validateBuildings(listOf(building), true)
        require(report.passed) { report.failures.joinToString("; ") }
        return building
    }

    /** Reflect only the text-coordinate convention here. Kind remains explicitly bound to
     * source housingType and to the render-spec digest, including every detail view. */
    fun toPdf(building: BuildingGeometry): PdfBuildingShape {
        require(failures(building).isEmpty()) { failures(building).joinToString("; ") }
        return PdfBuildingShape(building.buildingId, building.label,
            building.polygon.map { it.x to it.y },
            building.labelItems.map(BuildingLabelCoordinateTransform::toPdf),
            building.assigned, isContextOnly(building))
    }
}

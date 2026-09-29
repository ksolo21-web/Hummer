package com.koenterprises.territorycardstudio.core

/** A display-only typography change, retained for review against the immutable reference. */
data class ReferenceComparisonTextChange(val field: String, val sourceText: String, val proposedText: String)

data class NativeReferenceComparisonProposal(
    val referenceSha256: String,
    val roads: List<RoadGeometry>,
    val buildings: List<BuildingGeometry>,
    val segments: List<SourceSegmentObservation>,
    val buildingObservations: List<SourceBuildingObservation>,
    val textChanges: List<ReferenceComparisonTextChange>
)

/** Seeds an UNCONFIRMED comparison, never current-source geometry or assignment approval.
 * The locked renderer accepts printable ASCII. Known range punctuation can therefore be
 * displayed with a hyphen, but numbers, member ordering, geometry and work rules must stay
 * identical. Do this BEFORE draft/fact hashes are calculated; never rewrite a saved receipt.
 * Unknown characters are rejected with the exact field, not transliterated or discarded. */
object NativeReferenceComparison {
    fun propose(source: KnowledgeBaseAssignment): NativeReferenceComparisonProposal {
        require(source.referenceSha256.matches(Regex("[0-9a-f]{64}"))) { "Invalid locked reference digest" }
        val changes = mutableListOf<ReferenceComparisonTextChange>()
        fun display(text: String, field: String): String {
            require(text.length <= 4096) { "Reference $field exceeds the supported text limit" }
            val proposed = text.replace('\u2013', '-').replace('\u2014', '-').replace('\u2212', '-')
            require(proposed.all { it.code in 32..126 }) {
                "Reference $field contains text unsupported by the locked font. Review this field before using the comparison."
            }
            if (text != proposed) changes += ReferenceComparisonTextChange(field, text, proposed)
            return proposed
        }
        val roads = source.roads.map { road ->
            // A road-name change is not a range-typography change and must not be inferred.
            require(road.name.all { it.code in 32..126 }) { "Reference road ${road.segmentId} needs a supported, source-reviewed name" }
            road.copy(points = road.points.toList())
        }
        val buildings = source.buildings.map { building ->
            val id = building.buildingId
            building.copy(
                label = display(building.label, "$id.label"),
                sourceMembers = building.sourceMembers.mapIndexed { index, text -> display(text, "$id.member[$index]") },
                labelItems = building.labelItems.mapIndexed { index, label ->
                    label.copy(text = display(label.text, "$id.labelItem[$index]"))
                },
                polygon = building.polygon.toList()
            )
        }
        check(buildings.zip(source.buildings).all { (proposal, original) ->
            proposal.copy(label = original.label, sourceMembers = original.sourceMembers,
                labelItems = original.labelItems) == original
        }) { "Comparison typography must not change geometry, identity or work rules" }
        return NativeReferenceComparisonProposal(source.referenceSha256, roads, buildings,
            roads.map { road -> SourceSegmentObservation(road.segmentId, road.name, road.status, road.role,
                road.insideSide, road.accessOnly, road.endpointAKind, road.endpointBKind,
                "Locked reference comparison only; verify this exact segment against the current uploaded source before confirmation", false) },
            buildings.map { building -> SourceBuildingObservation(building.buildingId, building.sourceMembers, building.assigned,
                "Locked reference comparison only; trace/match this footprint against the current uploaded source before confirmation", false) },
            changes.toList())
    }
}

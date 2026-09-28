package com.koenterprises.territorycardstudio.core

import kotlin.math.*

enum class OutlinedProposalPriority { TERRITORY, CONNECTED_APPROACH, SURROUNDING_CONTEXT }
data class OutlinedReviewRegion(val id:String,val sourceBounds:AxisAlignedRect,
    val proposalIds:List<String>,val findingIds:List<String>)
data class OutlinedMapTriage(val priorities:Map<String,OutlinedProposalPriority>,
    val surroundingRegions:List<OutlinedReviewRegion>,val uncertaintyRegions:List<OutlinedReviewRegion>)

/** Presentation grouping only: every proposal and finding remains in the immutable extraction.
 * A region is not an omission decision and cannot clear a source-coverage gate. */
object OutlinedMapTriageBuilder {
    fun build(extraction:OutlinedMapExtraction):OutlinedMapTriage {
        val roads=extraction.roads
        require(roads.map {it.road.id}.distinct().size==roads.size)
        require(extraction.findings.map {it.id}.distinct().size==extraction.findings.size)
        val primary=roads.filter {it.relation!=BoundaryRoadRelation.EXTERIOR}
        val ends=primary.flatMap {listOf(it.road.points.first(),it.road.points.last())}.toSet()
        // Exact shared skeleton nodes only. Proximity cannot invent an approach across a gap.
        val priorities=roads.associate {p->p.road.id to when {
            p.relation!=BoundaryRoadRelation.EXTERIOR->OutlinedProposalPriority.TERRITORY
            p.road.points.first() in ends || p.road.points.last() in ends->OutlinedProposalPriority.CONNECTED_APPROACH
            else->OutlinedProposalPriority.SURROUNDING_CONTEXT
        }}
        fun bounds(p:OutlinedRoadProposal)=AxisAlignedRect(p.road.points.minOf {it.x},p.road.points.minOf {it.y},p.road.points.maxOf {it.x}+1,p.road.points.maxOf {it.y}+1)
        fun cell(b:AxisAlignedRect):Pair<Int,Int> = floor((b.left+b.right)/192.0).toInt() to floor((b.top+b.bottom)/192.0).toInt()
        fun union(rows:List<AxisAlignedRect>)=AxisAlignedRect(rows.minOf {it.left},rows.minOf {it.top},rows.maxOf {it.right},rows.maxOf {it.bottom})
        val surrounding=roads.filter {priorities[it.road.id]==OutlinedProposalPriority.SURROUNDING_CONTEXT}
            .groupBy {cell(bounds(it))}.toSortedMap(compareBy<Pair<Int,Int>> {it.second}.thenBy {it.first}).map {(key,items)->
                OutlinedReviewRegion("context-${key.first}-${key.second}",union(items.map(::bounds)),items.map {it.road.id}.sorted(),emptyList())
            }
        val full=AxisAlignedRect(0.0,0.0,extraction.boundary.width.toDouble(),extraction.boundary.height.toDouble())
        val uncertainties=extraction.findings.groupBy {it.sourceBounds?.let(::cell)}
            .entries.sortedWith(compareBy({it.key?.second ?: -1},{it.key?.first ?: -1})).map {(key,items)->
                OutlinedReviewRegion(if(key==null)"uncertainty-global" else "uncertainty-${key.first}-${key.second}",
                    union(items.map {it.sourceBounds ?: full}),emptyList(),items.map {it.id}.sorted())
            }
        check(surrounding.flatMap {it.proposalIds}.toSet()==priorities.filterValues {it==OutlinedProposalPriority.SURROUNDING_CONTEXT}.keys)
        check(uncertainties.flatMap {it.findingIds}.toSet()==extraction.findings.map {it.id}.toSet())
        return OutlinedMapTriage(priorities,surrounding,uncertainties)
    }
}

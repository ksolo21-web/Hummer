package com.koenterprises.territorycardstudio.core

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class OutlinedCoverageTest {
    private val source="a".repeat(64)
    private val polygon=listOf(Point2D(10.0,10.0),Point2D(90.0,10.0),Point2D(90.0,90.0),Point2D(10.0,90.0))
    private val boundary=OutlinedMapBoundary(100,100,polygon,6400,AxisAlignedRect(10.0,10.0,90.0,90.0))
    private val transform=OutlinedMapTransform(2.0,200.0,40.0)
    private fun proposal(id:String,points:List<Point2D>)=OutlinedRoadProposal(MapImageRoad(id,"Alpha Rd","context",points,false,false),OutlinedMapRoadExtractor.relationship(points,polygon))
    private val p=proposal("source-a",listOf(Point2D(20.0,50.0),Point2D(50.0,50.0),Point2D(80.0,50.0)))
    private fun extraction(vararg p:OutlinedRoadProposal)=OutlinedMapExtraction(boundary,p.toList(),emptyList())
    private fun road(id:String,points:List<Point2D>)=RoadGeometry(id,"Alpha Rd","alpha rd","green","interior","",false,"termination","termination",4.0,points.map(transform::page))
    private fun span(id:String,from:Double,to:Double,out:String?,order:Int=0,reverse:Boolean=false,kind:OutlinedSpanDisposition=OutlinedSpanDisposition.ROAD)=
        OutlinedSourceSpan(id,from,to,kind,out,order,reverse,"Source crop reviewed against the corresponding street trace")
    private fun review(e:OutlinedMapExtraction,roads:List<RoadGeometry>,spans:List<OutlinedSourceSpan>)=OutlinedCoverageDecision(
        OutlinedCoverageContract.analysisSha256(source,e,transform),OutlinedCoverageContract.outputSha256(roads),true,
        spans.map {s->s.copy(reviewedOutputSha256=roads.firstOrNull {it.segmentId==s.outputId}?.let {OutlinedCoverageContract.outputSha256(listOf(it))}.orEmpty())})
    private fun assess(e:OutlinedMapExtraction,roads:List<RoadGeometry>,spans:List<OutlinedSourceSpan>)=
        OutlinedCoverageContract.assess(source,e,transform,review(e,roads,spans),roads)
    @Test fun completeSplitAccountsForEverySourceInterval() {
        val e=extraction(p);val roads=listOf(road("a",OutlinedCoverageContract.slice(p.road.points,0.0,0.5)),road("b",OutlinedCoverageContract.slice(p.road.points,0.5,1.0)))
        assertTrue(assess(e,roads,listOf(span("source-a",0.0,0.5,"a"),span("source-a",0.5,1.0,"b"))).passed)
    }
    @Test fun gapsAndOverlapsCannotBeHiddenByCurrentOutputHashes() {
        val e=extraction(p)
        for(start in listOf(0.4,0.6)) {
            val roads=listOf(road("a",OutlinedCoverageContract.slice(p.road.points,0.0,0.5)),road("b",OutlinedCoverageContract.slice(p.road.points,start,1.0)))
            val result=assess(e,roads,listOf(span("source-a",0.0,0.5,"a"),span("source-a",start,1.0,"b")))
            assertTrue(result.failures.any {it.startsWith("SPAN_GAP_OR_OVERLAP")})
        }
    }
    @Test fun exactOrientedJoinPassesButProximityConnectorFails() {
        val first=proposal("a",listOf(Point2D(20.0,50.0),Point2D(50.0,50.0)))
        val second=proposal("b",listOf(Point2D(80.0,50.0),Point2D(50.0,50.0)))
        val r=road("joined",p.road.points)
        val spans=listOf(span("a",0.0,1.0,"joined"),span("b",0.0,1.0,"joined",1,true))
        assertTrue(assess(extraction(first,second),listOf(r),spans).passed)
        val gap=second.copy(road=second.road.copy(points=listOf(Point2D(80.0,50.0),Point2D(51.0,50.0))))
        assertTrue(assess(extraction(first,gap),listOf(r),spans).failures.any {it.startsWith("UNSUPPORTED_CONNECTOR")})
        assertFalse(assess(extraction(first,second),listOf(r),spans.map {it.copy(reversed=false)}).passed)
    }
    @Test fun sourceBoundaryTransformAndOutputEditsInvalidateReview() {
        val e=extraction(p);val roads=listOf(road("a",p.road.points));val d=review(e,roads,listOf(span("source-a",0.0,1.0,"a")))
        assertTrue(OutlinedCoverageContract.assess(source,e,transform,d,roads).passed)
        assertFalse(OutlinedCoverageContract.assess("b".repeat(64),e,transform,d,roads).passed)
        assertFalse(OutlinedCoverageContract.assess(source,e.copy(boundary=boundary.copy(polygon=polygon.map {it.copy(x=it.x+1)})),transform,d,roads).passed)
        assertFalse(OutlinedCoverageContract.assess(source,e,transform.copy(offsetX=201.0),d,roads).passed)
        listOf(roads[0].copy(name="Other Rd"),roads[0].copy(status="red",role="excluded"),roads[0].copy(points=roads[0].points.reversed())).forEach {
            assertTrue(OutlinedCoverageContract.assess(source,e,transform,d,listOf(it)).failures.contains("OUTPUT_CHANGED"),"Output edit was not invalidated")
        }
    }
    @Test fun interiorAndPartialRoadCannotDisappearAsOutsideContext() {
        val e=extraction(p)
        val result=assess(e,emptyList(),listOf(span("source-a",0.0,1.0,null,kind=OutlinedSpanDisposition.OUTSIDE_CONTEXT)))
        assertTrue(result.failures.any {it.startsWith("NONEXTERIOR_CONTEXT_OMISSION")})
        assertFalse(assess(e,emptyList(),listOf(span("source-a",0.0,0.5,null,kind=OutlinedSpanDisposition.NON_ROAD))).passed)
    }
    @Test fun removedOutputsAndUnboundAdditionsRemainBlocking() {
        val e=extraction(p);val r=road("a",p.road.points);val spans=listOf(span("source-a",0.0,1.0,"a"))
        assertFalse(assess(e,emptyList(),spans).passed)
        assertFalse(assess(e,listOf(r,r.copy(segmentId="unbound")),spans).passed)
    }
    @Test fun crossingCannotBeColoredAsOneWorkedSegment() {
        val cross=proposal("cross",listOf(Point2D(0.0,50.0),Point2D(50.0,50.0),Point2D(100.0,50.0)))
        assertTrue(assess(extraction(cross),listOf(road("r",cross.road.points)),listOf(span("cross",0.0,1.0,"r"))).failures.any {it.startsWith("UNSPLIT_WORK_CROSSING")})
    }
    @Test fun changedGeometryCannotPassWithRefreshedOutputDigest() {
        val e=extraction(p);val r=road("a",p.road.points.map {it.copy(y=it.y+2)})
        assertTrue(assess(e,listOf(r),listOf(span("source-a",0.0,1.0,"a"))).failures.any {it.startsWith("SOURCE_OUTPUT_GEOMETRY_MISMATCH")})
    }
}

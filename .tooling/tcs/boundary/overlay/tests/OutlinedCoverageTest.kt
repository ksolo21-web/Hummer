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
    @Test fun neutralContextIsRenderedWithoutAWorkColor() {
        val root=generateSequence(java.io.File(System.getProperty("user.dir")).absoluteFile){it.parentFile}.first {java.io.File(it,"app/src/main/assets/territory").isDirectory}
        val template=java.io.File(root,"app/src/main/assets/territory/render-authority/Canonical-New-Designed-Template-R48.pdf").readBytes()
        val original=CandidatePdfRendererTest.syntheticFixtureSpec()
        val spec=original.copy(roads=original.roads.map {it.copy(status=PdfRoadStatus.CONTEXT_ONLY)})
        val rendered=CandidatePdfRenderer.renderNonFieldFixture(template,spec)
        assertTrue(rendered.exactValidation.passed)
        assertNotEquals(original.canonicalSha256(),spec.canonicalSha256())
        assertEquals("#858B94",PdfRoadStatus.CONTEXT_ONLY.hex)
        java.io.File(root.parentFile,"evidence/outlined-neutral-context.pdf").apply {parentFile.mkdirs()}.writeBytes(rendered.pdfBytes)
    }
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
    @Test fun boundaryToleranceCannotAuthorizeExteriorWork() {
        for(x in listOf(8.0,9.999,10.0)) {
            val q=proposal("q",listOf(Point2D(x,50.0),Point2D(50.0,50.0)))
            val result=assess(extraction(q),listOf(road("r",q.road.points)),listOf(span("q",0.0,1.0,"r")))
            assertEquals(x==10.0,result.passed,result.failures.toString())
        }
    }
    @Test fun yellowSideMustFacePolygonAndReverseWithGeometry() {
        val q=proposal("q",listOf(Point2D(10.0,10.0),Point2D(90.0,10.0)))
        val e=extraction(q)
        for(reverse in listOf(false,true)) {
            val points=if(reverse)q.road.points.reversed() else q.road.points
            for(side in listOf("left","right")) {
                val r=road("r",points).copy(status="yellow",role="perimeter",insideSide=side)
                assertEquals(side==(if(reverse)"left" else "right"),assess(e,listOf(r),listOf(span("q",0.0,1.0,"r",reverse=reverse))).passed)
            }
        }
    }
    @Test fun aggregateRefreshCannotRenewAStaleIndividualRoadReview() {
        val e=extraction(p);val roads=listOf(road("r",p.road.points));val old=review(e,roads,listOf(span("source-a",0.0,1.0,"r")))
        val edited=roads.map {it.copy(name="Renamed Rd",normalizedName="renamed rd")}
        val refreshed=old.copy(outputSha256=OutlinedCoverageContract.outputSha256(edited))
        assertTrue(OutlinedCoverageContract.assess(source,e,transform,refreshed,edited).failures.any {it.startsWith("SPAN_OUTPUT_REVIEW_CHANGED")})
    }
    @Test fun degenerateOutsideGeometryReturnsBlockingFinding() {
        val q=proposal("q",listOf(Point2D(0.0,0.0),Point2D(0.0,0.0)))
        assertFalse(assess(extraction(q),emptyList(),listOf(span("q",0.0,1.0,null,kind=OutlinedSpanDisposition.OUTSIDE_CONTEXT))).passed)
    }
}

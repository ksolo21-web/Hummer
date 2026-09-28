package com.koenterprises.territorycardstudio.core

import java.io.File
import javax.imageio.ImageIO
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class OutlinedMapBoundaryTest {
    private val root=generateSequence(File(System.getProperty("user.dir")).absoluteFile){it.parentFile}.first {File(it,"app/src/main/assets/territory").isDirectory}
    private fun source()=ImageIO.read(File(root,"core/src/test/resources/boundary/source-26435.jpg"))
    @Test fun exactUserMapPreservesConcaveOutlineAndNarrowStem() {
        val image=source();val p=IntArray(image.width*image.height);image.getRGB(0,0,image.width,image.height,p,0,image.width)
        val b=OutlinedMapBoundaryDetector.detect(image.width,image.height,p)
        assertEquals(1079,b.width);assertEquals(547,b.height)
        assertTrue(b.enclosedPixels in 48000..51000)
        assertTrue(OutlinedMapBoundaryDetector.inside(Point2D(580.0,120.0),b.polygon))
        assertTrue(OutlinedMapBoundaryDetector.inside(Point2D(500.0,400.0),b.polygon))
        assertTrue(OutlinedMapBoundaryDetector.inside(Point2D(700.0,420.0),b.polygon))
        assertFalse(OutlinedMapBoundaryDetector.inside(Point2D(500.0,200.0),b.polygon))
        assertFalse(OutlinedMapBoundaryDetector.inside(Point2D(660.0,250.0),b.polygon))
        assertFalse(OutlinedMapBoundaryDetector.inside(Point2D(550.0,465.0),b.polygon))
        assertFalse(OutlinedMapBoundaryDetector.inside(Point2D(750.0,250.0),b.polygon))
        val out=File(root.parentFile,"evidence/boundary-analysis.json");out.parentFile.mkdirs()
        out.writeText("""{"width":${b.width},"height":${b.height},"enclosedPixels":${b.enclosedPixels},"polygon":[${b.polygon.joinToString(","){"[${it.x},${it.y}]"}}]}""")
    }
    @Test fun actualImageRoadProposalsRemainNeutralUntilReviewed() {
        val image=source();val p=IntArray(image.width*image.height);image.getRGB(0,0,image.width,image.height,p,0,image.width)
        val result=OutlinedMapRoadExtractor.extract(image.width,image.height,p,emptyList())
        assertTrue(result.roads.isNotEmpty())
        assertTrue(result.roads.all {it.road.status=="context"})
        assertTrue(result.roads.any {it.relation==BoundaryRoadRelation.INTERIOR})
        val triage=OutlinedMapTriageBuilder.build(result)
        assertEquals(result.roads.map {it.road.id}.toSet(),triage.priorities.keys)
        assertEquals(result.findings.map {it.id}.toSet(),triage.uncertaintyRegions.flatMap {it.findingIds}.toSet())
        assertTrue(result.roads.filter {it.relation!=BoundaryRoadRelation.EXTERIOR}.all {triage.priorities[it.road.id]==OutlinedProposalPriority.TERRITORY})
        val out=File(root.parentFile,"evidence/outlined-road-proposals.json");out.parentFile.mkdirs()
        out.writeText("""{"roads":[${result.roads.joinToString(","){r->"""{"id":"${r.road.id}","relation":"${r.relation}","priority":"${triage.priorities[r.road.id]}","points":[${r.road.points.joinToString(","){"[${it.x},${it.y}]"}}]}"""}}],"findingCount":${result.findings.size},"contextRegionCount":${triage.surroundingRegions.size},"uncertaintyRegionCount":${triage.uncertaintyRegions.size}}""")
    }
    @Test fun sourceSpaceRoadRelationsDistinguishCrossingsAndSides() {
        val polygon=listOf(Point2D(0.0,0.0),Point2D(100.0,0.0),Point2D(100.0,100.0),Point2D(0.0,100.0))
        fun relation(x1:Double,y1:Double,x2:Double,y2:Double)=OutlinedMapRoadExtractor.relationship(listOf(Point2D(x1,y1),Point2D(x2,y2)),polygon)
        assertEquals(BoundaryRoadRelation.INTERIOR,relation(20.0,20.0,80.0,80.0))
        assertEquals(BoundaryRoadRelation.EXTERIOR,relation(-20.0,20.0,-20.0,80.0))
        assertEquals(BoundaryRoadRelation.CROSSING,relation(-20.0,50.0,120.0,50.0))
        assertEquals(BoundaryRoadRelation.BOUNDARY_FOLLOWING,relation(0.0,20.0,0.0,80.0))
        assertEquals(BoundaryRoadRelation.BOUNDARY_ENDPOINT,relation(50.0,50.0,100.0,50.0))
        // Most of the trace follows the edge, but its two tails genuinely cross it.
        assertEquals(BoundaryRoadRelation.CROSSING,OutlinedMapRoadExtractor.relationship(listOf(
            Point2D(-8.0,10.0),Point2D(0.0,10.0),Point2D(0.0,90.0),Point2D(8.0,90.0)),polygon))
    }
    @Test fun brokenUserOutlineIsNotSilentlyClosed() {
        val image=source();val p=IntArray(image.width*image.height);image.getRGB(0,0,image.width,image.height,p,0,image.width)
        for(y in 82..92)for(x in 584..590)p[y*image.width+x]=0xFFFFFFFF.toInt()
        assertThrows(IllegalArgumentException::class.java){OutlinedMapBoundaryDetector.detect(image.width,image.height,p)}
    }
    @Test fun triageKeepsShortTerritoryBranchesAndNeverJoinsAcrossGaps() {
        val polygon=listOf(Point2D(20.0,20.0),Point2D(80.0,20.0),Point2D(80.0,80.0),Point2D(20.0,80.0))
        val boundary=OutlinedMapBoundary(100,100,polygon,3600,AxisAlignedRect(20.0,20.0,80.0,80.0))
        fun road(id:String,a:Point2D,b:Point2D,relation:BoundaryRoadRelation)=OutlinedRoadProposal(MapImageRoad(id,null,"context",listOf(a,b),false,false),relation)
        val roads=listOf(road("short",Point2D(30.0,30.0),Point2D(31.0,30.0),BoundaryRoadRelation.INTERIOR),
            road("edge",Point2D(20.0,40.0),Point2D(40.0,40.0),BoundaryRoadRelation.BOUNDARY_ENDPOINT),
            road("approach",Point2D(0.0,40.0),Point2D(20.0,40.0),BoundaryRoadRelation.EXTERIOR),
            road("gap",Point2D(0.0,39.0),Point2D(19.0,39.0),BoundaryRoadRelation.EXTERIOR))
        val finding=MapImageFinding("unknown","Unknown geometry",AxisAlignedRect(10.0,10.0,12.0,12.0))
        val triage=OutlinedMapTriageBuilder.build(OutlinedMapExtraction(boundary,roads,listOf(finding)))
        assertEquals(OutlinedProposalPriority.TERRITORY,triage.priorities["short"])
        assertEquals(OutlinedProposalPriority.CONNECTED_APPROACH,triage.priorities["approach"])
        assertEquals(OutlinedProposalPriority.SURROUNDING_CONTEXT,triage.priorities["gap"])
        assertEquals(listOf("gap"),triage.surroundingRegions.flatMap {it.proposalIds})
        assertEquals(listOf("unknown"),triage.uncertaintyRegions.flatMap {it.findingIds})
    }
    @Test fun competingAndClippedOutlinesAreRejected() {
        val w=400;val h=240;val p=IntArray(w*h){0xFFDDDDDD.toInt()}
        fun rect(l:Int,t:Int,r:Int,b:Int){for(x in l..r){p[t*w+x]=0xFF000000.toInt();p[b*w+x]=0xFF000000.toInt()};for(y in t..b){p[y*w+l]=0xFF000000.toInt();p[y*w+r]=0xFF000000.toInt()}}
        rect(20,20,170,200);rect(220,20,370,200)
        assertThrows(IllegalArgumentException::class.java){OutlinedMapBoundaryDetector.detect(w,h,p)}
        p.fill(0xFFDDDDDD.toInt());rect(0,20,170,200)
        assertThrows(IllegalArgumentException::class.java){OutlinedMapBoundaryDetector.detect(w,h,p)}
    }
    @Test fun connectedEnclosuresAndInteriorHolesCannotBeFilledSilently() {
        val w=400;val h=300;val p=IntArray(w*h){0xFFDDDDDD.toInt()}
        fun rect(l:Int,t:Int,r:Int,b:Int){for(x in l..r){p[t*w+x]=0xFF000000.toInt();p[b*w+x]=0xFF000000.toInt()};for(y in t..b){p[y*w+l]=0xFF000000.toInt();p[y*w+r]=0xFF000000.toInt()}}
        rect(20,20,300,260);rect(300,100,325,140)
        assertThrows(IllegalArgumentException::class.java){OutlinedMapBoundaryDetector.detect(w,h,p)}
        p.fill(0xFFDDDDDD.toInt());rect(20,20,300,260);rect(120,100,160,140)
        for(x in 20..120)p[120*w+x]=0xFF000000.toInt()
        assertThrows(IllegalArgumentException::class.java){OutlinedMapBoundaryDetector.detect(w,h,p)}
    }
    @Test fun neutralColoredAreasDoNotEstablishBoundaryOrWorkStatus() {
        val w=300;val h=200;val p=IntArray(w*h){when(it%w/75){0->0xFF51C72B.toInt();1->0xFFFF1435.toInt();2->0xFF22CCFF.toInt();else->0xFFFFDC18.toInt()}}
        assertThrows(IllegalArgumentException::class.java){OutlinedMapBoundaryDetector.detect(w,h,p)}
    }
}

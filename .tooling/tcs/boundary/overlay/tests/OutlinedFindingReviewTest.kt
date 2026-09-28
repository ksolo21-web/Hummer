package com.koenterprises.territorycardstudio.core

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class OutlinedFindingReviewTest {
    private val polygon=listOf(Point2D(10.0,10.0),Point2D(90.0,10.0),Point2D(90.0,90.0),Point2D(10.0,90.0))
    private val transform=OutlinedMapTransform(2.0,200.0,40.0)
    private val path=listOf(Point2D(20.0,50.0),Point2D(80.0,50.0))
    private val candidate=OutlinedRoadProposal(MapImageRoad("source-a","Alpha Rd","context",path,false,false),BoundaryRoadRelation.INTERIOR)
    private val mark=MapImageFinding("component-1","Review this small colored mark.",AxisAlignedRect(20.0,20.0,25.0,25.0))
    private val label=MapImageFinding("unmatched-label-1","No aligned road geometry was recovered for Alpha Rd; check the source.",AxisAlignedRect(25.0,43.0,75.0,49.0))
    private val road=RoadGeometry("output-a","Alpha Rd",TopologyOverlapDecisionEngine.normalizeRoadName("Alpha Rd"),"context","context","",false,"termination","termination",4.0,path.map(transform::page))
    private fun extraction(f:MapImageFinding=mark)=OutlinedMapExtraction(OutlinedMapBoundary(100,100,polygon,6400,AxisAlignedRect(10.0,10.0,90.0,90.0)),listOf(candidate),listOf(f))
    private fun span(r:RoadGeometry=road)=OutlinedSourceSpan("source-a",0.0,1.0,OutlinedSpanDisposition.ROAD,r.segmentId,evidence="Exact source interval inspected",reviewedOutputSha256=OutlinedCoverageContract.outputSha256(listOf(r)))
    private fun decision(f:MapImageFinding=mark,kind:String="MAP_SYMBOL",selected:List<RoadGeometry> = emptyList())=OutlinedFindingResolution(f.id,kind,"This exact source region was inspected",selected.map {it.segmentId},OutlinedCoverageContract.outputSha256(selected))
    private fun assess(f:MapImageFinding=mark,d:OutlinedFindingResolution=decision(f),roads:List<RoadGeometry> = listOf(road),spans:List<OutlinedSourceSpan> = listOf(span()))=
        OutlinedFindingReviewContract.failures(extraction(f),transform,d,roads,spans)

    @Test fun smallInteriorNonRoadMarkCanBeExplicitlyReviewed() {
        for(kind in OutlinedFindingReviewContract.nonRoadKinds)assertTrue(assess(d=decision(kind=kind)).isEmpty())
    }
    @Test fun mixedRegionCannotHideAnyRetainedRoadTrace() {
        val mixed=mark.copy(sourceBounds=AxisAlignedRect(20.0,40.0,80.0,60.0))
        for(kind in OutlinedFindingReviewContract.nonRoadKinds) {
            assertTrue(assess(mixed,decision(mixed,kind)).contains("SOURCE_REGION_CONTAINS_ROAD_TRACE"))
            assertTrue(assess(mixed,decision(mixed,kind),emptyList(),emptyList()).contains("SOURCE_REGION_CONTAINS_ROAD_TRACE"))
        }
    }
    @Test fun crossingRegionRejectsEvenWhenBothSegmentEndsAreOutside() {
        val crossing=mark.copy(sourceBounds=AxisAlignedRect(45.0,45.0,55.0,55.0))
        assertTrue(assess(crossing,decision(crossing)).contains("SOURCE_REGION_CONTAINS_ROAD_TRACE"))
    }
    @Test fun findingsDoNotWaiveRoadCandidateAccountingOrBoundaryReview() {
        val e=extraction()
        assertTrue(assess().isEmpty())
        val d=OutlinedCoverageDecision(OutlinedCoverageContract.analysisSha256("a".repeat(64),e,transform),OutlinedCoverageContract.outputSha256(listOf(road)),false,emptyList())
        val errors=OutlinedCoverageContract.assess("a".repeat(64),e,transform,d,listOf(road)).failures
        assertTrue(errors.contains("BOUNDARY_UNCONFIRMED"));assertTrue(errors.contains("UNACCOUNTED_CANDIDATE:source-a"))
    }
    @Test fun interiorCannotBeOmittedAsOutside() {
        assertTrue(assess(d=decision(kind="OUTSIDE_CONTEXT")).contains("SOURCE_REGION_NOT_WHOLLY_OUTSIDE"))
        val outside=mark.copy(sourceBounds=AxisAlignedRect(1.0,20.0,5.0,25.0))
        assertTrue(assess(outside,decision(outside,"OUTSIDE_CONTEXT")).isEmpty())
        val crossing=mark.copy(sourceBounds=AxisAlignedRect(5.0,20.0,15.0,25.0))
        assertTrue(assess(crossing,decision(crossing,"OUTSIDE_CONTEXT")).contains("SOURCE_REGION_NOT_WHOLLY_OUTSIDE"))
    }
    @Test fun enclosingOrBoundaryRegionCannotBeOmittedAsOutside() {
        for(box in listOf(AxisAlignedRect(0.0,0.0,99.0,99.0),AxisAlignedRect(5.0,20.0,10.0,25.0))) {
            val f=mark.copy(sourceBounds=box)
            assertTrue(assess(f,decision(f,"OUTSIDE_CONTEXT")).contains("SOURCE_REGION_NOT_WHOLLY_OUTSIDE"))
        }
    }
    @Test fun unresolvedRoadNamesAndRealStreetLabelsCannotBeDismissedAsSymbols() {
        val missing=mark.copy(id="name-0")
        assertTrue(OutlinedFindingReviewContract.allowed(missing).isEmpty())
        assertTrue(assess(missing,decision(missing)).contains("DISPOSITION_NOT_ALLOWED"))
        assertTrue(assess(label,decision(label)).contains("DISPOSITION_NOT_ALLOWED"))
    }
    @Test fun missingInvalidRegionsAndEmptyEvidenceFail() {
        assertTrue(assess(mark.copy(sourceBounds=null)).contains("SOURCE_REGION_REQUIRED"))
        val invalid=mark.copy(sourceBounds=AxisAlignedRect(20.0,20.0,101.0,25.0))
        assertTrue(assess(invalid,decision(invalid)).contains("INVALID_SOURCE_REGION"))
        for(evidence in listOf("", "       ", "x".repeat(2001)))
            assertTrue(assess(d=decision().copy(evidence=evidence)).contains("SPECIFIC_SOURCE_EVIDENCE_REQUIRED"))
    }
    @Test fun exactAlignedNamedSourceReviewedRoadCanResolveItsOwnLabel() {
        assertTrue(assess(label,decision(label,"MATCHED_ROAD",listOf(road))).isEmpty())
    }
    @Test fun labelMatchNeedsAnOutputAndCannotIntroduceAnUnboundRoad() {
        assertTrue(assess(label,decision(label,"MATCHED_ROAD")).contains("MATCH_REQUIRES_ROAD"))
        assertTrue(assess(label,decision(label,"MATCHED_ROAD",listOf(road)),spans=emptyList()).any {it.startsWith("MATCH_WITHOUT_REVIEWED_SOURCE_SPANS")})
    }
    @Test fun removedAndDuplicateOutputIdsFail() {
        val d=decision(label,"MATCHED_ROAD",listOf(road))
        assertTrue(assess(label,d,emptyList()).contains("MISSING_OUTPUT"))
        assertTrue(assess(label,d.copy(outputIds=listOf(road.segmentId,road.segmentId))).contains("INVALID_OUTPUT_IDS"))
        assertTrue(assess(label,d,listOf(road,road)).contains("DUPLICATE_OUTPUT_ID"))
    }
    @Test fun changedNameStatusOrGeometryCannotReuseFindingEvidence() {
        val d=decision(label,"MATCHED_ROAD",listOf(road))
        for(changed in listOf(road.copy(name="Other Rd"),road.copy(status="red",role="excluded"),road.copy(points=road.points.map {it.copy(y=it.y+1)}))) {
            val failures=assess(label,d,listOf(changed))
            assertTrue(failures.contains("FINDING_OUTPUT_REVIEW_CHANGED"))
            assertTrue(failures.any {it.startsWith("MATCH_WITHOUT_REVIEWED_SOURCE_SPANS")})
        }
    }
    @Test fun refreshingFindingHashCannotRenewChangedSourceSpanEvidence() {
        val changed=road.copy(status="red",role="excluded")
        val d=decision(label,"MATCHED_ROAD",listOf(changed))
        assertTrue(assess(label,d,listOf(changed)).any {it.startsWith("MATCH_WITHOUT_REVIEWED_SOURCE_SPANS")})
    }
    @Test fun wrongNameNearbyParallelOrPerpendicularRoadCannotMatch() {
        val wrong=road.copy(name="Beta Rd",normalizedName=TopologyOverlapDecisionEngine.normalizeRoadName("Beta Rd"))
        assertTrue(assess(label,decision(label,"MATCHED_ROAD",listOf(wrong)),listOf(wrong),listOf(span(wrong))).any {it.startsWith("ROAD_NAME_MISMATCH")})
        for(points in listOf(listOf(Point2D(20.0,70.0),Point2D(80.0,70.0)),listOf(Point2D(50.0,20.0),Point2D(50.0,80.0)))) {
            val changed=road.copy(points=points.map(transform::page))
            assertTrue(assess(label,decision(label,"MATCHED_ROAD",listOf(changed)),listOf(changed),listOf(span(changed))).any {it.startsWith("SOURCE_LABEL_NOT_ALIGNED")})
        }
    }
    @Test fun inventedOutputGeometryFailsEvenWithRefreshedDigests() {
        val changed=road.copy(points=road.points.map {it.copy(x=it.x+0.1)})
        assertTrue(assess(label,decision(label,"MATCHED_ROAD",listOf(changed)),listOf(changed),listOf(span(changed))).any {it.startsWith("MATCH_WITHOUT_REVIEWED_SOURCE_SPANS")})
    }
    @Test fun unknownFindingOrMutableOcrMessageCannotAuthorizeMatch() {
        assertTrue(assess(d=decision().copy(findingId="missing")).contains("UNKNOWN_OR_DUPLICATE_FINDING"))
        val corrupt=label.copy(message="User-entered road text")
        assertTrue(assess(corrupt,decision(corrupt,"MATCHED_ROAD",listOf(road))).contains("SOURCE_LABEL_UNRESOLVED"))
    }
    @Test fun omissionMustNotContainOutputOrAStaleDigest() {
        assertTrue(assess(d=decision(selected=listOf(road))).contains("NON_ROAD_DECISION_HAS_OUTPUT"))
        assertTrue(assess(d=decision().copy(reviewedOutputSha256="0".repeat(64))).contains("FINDING_OUTPUT_REVIEW_CHANGED"))
    }
}

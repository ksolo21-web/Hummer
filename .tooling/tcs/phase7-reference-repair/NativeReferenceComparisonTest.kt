package com.koenterprises.territorycardstudio.core

import java.io.File
import org.junit.jupiter.api.Test

class NativeReferenceComparisonTest {
    private val root=generateSequence(File(System.getProperty("user.dir")).absoluteFile){it.parentFile}
        .first {File(it,"app/src/main/assets/territory").isDirectory}
    private val kb=File(root,"app/src/main/assets/territory/Territory-Knowledge-Base.json").reader().use(TerritoryKnowledgeBaseLoader::load)
    private val original=kb.assignments.getValue("A265")
    private fun assignment(buildings:List<BuildingGeometry>)=CurrentAuthoritativeAssignmentState(original.displayId,original.identity,
        original.canonicalFilename,kb.revision,"current_authoritative_assignment","0".repeat(64),"Unconfirmed comparison regression",
        "Rochester Hills","9/28/2026",listOf("Directions: Test only, not for field use."),"full_map",original.housingType,
        RenderCoordinateSpace.LOCKED_R48_PAGE_POINTS_TOP_ORIGIN,original.roads,buildings)

    @Test fun actualSixBuildingReferenceReproducesOldSaveFailure() {
        check(original.buildings.size==6)
        check(runCatching {NativeAssignmentCodec.encode(assignment(original.buildings))}.isFailure)
    }
    @Test fun allSixComparisonBuildingsRoundTripThroughNativeCodec() {
        val value=assignment(NativeReferenceComparison.propose(original).buildings)
        val bytes=NativeAssignmentCodec.encode(value)
        check(NativeAssignmentCodec.decode(bytes)==value)
        check(NativeAssignmentCodec.encode(NativeAssignmentCodec.decode(bytes)).contentEquals(bytes))
    }
    @Test fun numbersWorkRulesIdentitiesAndGeometryAreUnchanged() {
        val proposal=NativeReferenceComparison.propose(original)
        check(proposal.roads==original.roads)
        check(proposal.referenceSha256==original.referenceSha256)
        proposal.buildings.zip(original.buildings).forEach {(p,b)->
            check(p.buildingId==b.buildingId && p.assigned==b.assigned && p.polygon==b.polygon)
            check(p.sourceMembers.map(BuildingValidationEngine::normalizeMemberId)==b.sourceMembers.map(BuildingValidationEngine::normalizeMemberId))
            check(p.labelItems.zip(b.labelItems).all {(a,c)->a.copy(text=c.text)==c})
        }
        check(original==kb.assignments.getValue("A265"))
    }
    @Test fun typographyChangesAreExplicitAndReproducible() {
        val p=NativeReferenceComparison.propose(original)
        check(p.textChanges.size==12)
        check(p.textChanges.map {it.field}.distinct().size==p.textChanges.size)
        check(p.textChanges.all {it.sourceText!=it.proposedText && '\u2013' in it.sourceText && '-' in it.proposedText})
        check(p==NativeReferenceComparison.propose(original))
    }
    @Test fun referenceSeedNeverConfirmsRoadsBuildingsOrSupplementalMatches() {
        val p=NativeReferenceComparison.propose(original)
        check(p.segments.all {!it.confirmed})
        check(p.buildingObservations.all {!it.confirmed && it.supplementalReference==null})
        check(p.buildingObservations.map {it.sourceMembers}==p.buildings.map {it.sourceMembers})
    }
    @Test fun normalizedReferenceLabelsStillPassExistingBuildingGate() {
        val p=NativeReferenceComparison.propose(original)
        val report=BuildingValidationEngine.validateBuildings(p.buildings,true)
        check(report.passed) {report.failures.toString()}
        check(report.buildingCount==6 && report.assignedBuildingCount==6 && report.memberLabelCount==9)
    }
    @Test fun asciiInputIsIdempotentAndKeepsCanonicalBytes() {
        val first=NativeReferenceComparison.propose(original)
        val second=NativeReferenceComparison.propose(original.copy(buildings=first.buildings))
        check(second.textChanges.isEmpty() && second.buildings==first.buildings)
        check(NativeAssignmentCodec.encode(assignment(second.buildings)).contentEquals(NativeAssignmentCodec.encode(assignment(first.buildings))))
    }
    @Test fun onlyKnownRangePunctuationIsTranslated() {
        for(dash in listOf('\u2013','\u2014','\u2212')) {
            val b=original.buildings.first().copy(label="510${dash}524",sourceMembers=listOf("510${dash}524"),
                labelItems=listOf(original.buildings.first().labelItems.first().copy(text="510${dash}524")))
            val p=NativeReferenceComparison.propose(original.copy(buildings=listOf(b)))
            check(p.buildings.single().label=="510-524")
            check(p.buildings.single().sourceMembers==listOf("510-524"))
            check(p.buildings.single().labelItems.single().text=="510-524")
        }
    }
    @Test fun unsupportedAndInvisibleCharactersCannotBeDiscardedOrHidden() {
        for(text in listOf("510\n524","510\t524","510\u202e524","510\u200b524","\u0000","\ud800","B\u00e9ta","\uD83D\uDE00")) {
            val failure=runCatching {NativeReferenceComparison.propose(original.copy(buildings=listOf(original.buildings.first().copy(label=text))))}.exceptionOrNull()
            check(failure is IllegalArgumentException && failure.message.orEmpty().contains("b25.label"))
        }
    }
    @Test fun unconfirmedComparisonCannotBecomeNativeAuthority() {
        val kb2=NativeCommissioningProjection.project(kb,"A265",original.referenceSha256,"1".repeat(64))
        val p=NativeReferenceComparison.propose(original)
        val a=assignment(p.buildings).copy(knowledgeBaseRevision=kb2.revision)
        val r=NativeSourceReconciliation("A265","REGULAR",kb2.revision,"2".repeat(64),original.referenceSha256,
            "current_assignment_map","Regression reviewer","2026-09-28T23:00:00Z",NativeSourceReconciliationContract.assignmentContentSha256(a),
            null,false,false,false,false,p.segments,p.buildingObservations,"00000000-0000-0000-0000-000000000081",null)
        check(NativeSourceReconciliationContract.decode(NativeSourceReconciliationContract.encode(r))==r)
        val result=NativeSourceReconciliationContract.assess(kb2,r,a,"REGULAR",r.importedSourceSha256,null)
        check(!result.passed)
        check(result.failures.containsAll(listOf("SOURCE_COVERAGE_UNCONFIRMED","EXPLICIT_CONFIRMATION_REQUIRED","SEGMENT_REVIEW_INCOMPLETE","BUILDING_REVIEW_INCOMPLETE")))
    }
}

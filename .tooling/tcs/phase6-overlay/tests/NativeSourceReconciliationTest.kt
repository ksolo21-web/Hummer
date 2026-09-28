package com.koenterprises.territorycardstudio.core

import java.io.File
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class NativeSourceReconciliationTest {
    private val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "app/src/main/assets/territory").isDirectory }
    private val kb0 = File(root, "app/src/main/assets/territory/Territory-Knowledge-Base.json").reader().use(TerritoryKnowledgeBaseLoader::load)
    private val identity = TerritoryIdentity(999, TerritoryClass.Residential, 'a')
    private val old = kb0.assignments.getValue("273")
    private val slot = old.copy(status = "needs_new_card", displayId = identity.displayId, identity = identity,
        baseNumber = identity.baseNumber, cardClass = identity.territoryClass.token, suffix = "a", slot = identity.displayId,
        canonicalFilename = identity.canonicalFilename, needsNewCard = true, newCardApproved = false,
        fieldReleaseAllowedForExactArtifact = false, legacyReferenceFile = "SYNTHETIC-NOT-FOR-FIELD-USE")
    private val kb = kb0.copy(assignments = kb0.assignments - "273" + (identity.displayId to slot))
    private val road = RoadGeometry("alpha", "Alpha Rd", "alpha rd", "yellow", "perimeter", "left", false,
        "junction", "junction", 4.0, listOf(Point2D(225.0,115.0),Point2D(650.0,115.0)))
    private val a = CurrentAuthoritativeAssignmentState(identity.displayId, identity, identity.canonicalFilename,
        kb.revision, "current_authoritative_assignment", "a".repeat(64), "SYNTHETIC NOT FOR FIELD USE", "Oakland Township", "9/28/2026",
        listOf("Directions: Synthetic regression only."), "full_map", slot.housingType,
        RenderCoordinateSpace.LOCKED_R48_PAGE_POINTS_TOP_ORIGIN, listOf(road), emptyList())
    private val r = NativeSourceReconciliation(identity.displayId, "REGULAR", kb.revision, "b".repeat(64),slot.referenceSha256,
        "current_assignment_map", "Synthetic reviewer", "2026-09-28T04:00:00Z", NativeSourceReconciliationContract.assignmentContentSha256(a), null,
        true,true,false,false,listOf(SourceSegmentObservation(road.segmentId,road.name,road.status,road.role,road.insideSide,
            road.accessOnly,road.endpointAKind,road.endpointBKind,"Page 1: Alpha Rd inside side indicated left",true)),emptyList(),"00000000-0000-0000-0000-000000000001",null)
    private fun assess(v:NativeSourceReconciliation=r, state:CurrentAuthoritativeAssignmentState=a, mode:String="REGULAR", source:String=r.importedSourceSha256, inventory:String?=null) =
        NativeSourceReconciliationContract.assess(kb,v,state,mode,source,inventory)
    private fun blocked(v:NativeSourceReconciliation, code:String) { val x=assess(v); assertFalse(x.passed);assertTrue(code in x.failures,x.failures.toString()) }

    @Test fun canonicalRoundTripAndPortableGolden() {
        val bytes=NativeSourceReconciliationContract.encode(r)
        assertEquals(r,NativeSourceReconciliationContract.decode(bytes));assertTrue(assess().passed,assess().failures.toString())
        val out=File(root,"../evidence/reconciliation-golden.json");out.parentFile.mkdirs();out.writeBytes(bytes)
    }
    @Test fun importsAndLegacyReferencesNeverGrantAuthority() {
        blocked(r.copy(explicitAssignmentConfirmation=false),"EXPLICIT_CONFIRMATION_REQUIRED")
        blocked(r.copy(sourceCoverageComplete=false),"SOURCE_COVERAGE_UNCONFIRMED")
        blocked(r.copy(sourceClass="legacy_reference"),"SOURCE_CLASS_NOT_CURRENT")
        blocked(r.copy(importedSourceSha256=slot.referenceSha256),"LEGACY_SOURCE_NOT_CURRENT")
        blocked(r.copy(sourceClass="style_only",styleOnlyGeographyUsed=true),"SOURCE_CLASS_NOT_CURRENT")
        assertFalse(assess(r.copy(crossTerritoryInferenceUsed=true)).passed)
    }
    @Test fun contextAndEveryCandidateFactRemainBound() {
        blocked(r.copy(knowledgeBaseRevision="other"),"REVISION_MISMATCH")
        blocked(r.copy(lockedReferenceSha256="f".repeat(64)),"REFERENCE_MISMATCH")
        assertFalse(assess(source="f".repeat(64)).passed)
        assertFalse(assess(mode="TELEPHONE").passed)
        assertFalse(assess(state=a.copy(locality="Different locality")).passed)
        assertFalse(assess(state=a.copy(roads=listOf(road.copy(points=listOf(Point2D(226.0,115.0),Point2D(650.0,115.0)))))).passed)
        // Authority digest is assigned only after canonical reconciliation bytes exist.
        assertEquals(NativeSourceReconciliationContract.assignmentContentSha256(a),NativeSourceReconciliationContract.assignmentContentSha256(a.copy(authoritySha256="c".repeat(64))))
    }
    @Test fun conflictCountsAreComputedFromSourceObservations() {
        val extra=r.segments.single().copy(segmentId="missing",name="Missing Rd")
        val x=assess(r.copy(segments=listOf(extra)))
        assertEquals(1,x.truth.missingExpectedSegmentCount);assertEquals(1,x.truth.unexpectedMeaningChangingSegmentCount)
        assertEquals(1,x.truth.unresolvedPerimeterWorkedSideCount);assertFalse(x.passed)
        val conflict=assess(r.copy(segments=listOf(r.segments.single().copy(status="red"))))
        assertEquals(1,conflict.truth.assignmentColorConflictCount);assertFalse(conflict.passed)
        blocked(r.copy(segments=listOf(r.segments.single().copy(confirmed=false))),"SEGMENT_REVIEW_INCOMPLETE")
        blocked(r.copy(segments=listOf(r.segments.single().copy(evidenceNote=""))),"SEGMENT_REVIEW_INCOMPLETE")
    }
    @Test fun buildingMembershipIsReconciledIndependently() {
        val v=r.copy(buildings=listOf(SourceBuildingObservation("building-1",listOf("300"),true,"Page 1 footprint",true)))
        val x=assess(v);assertFalse(x.passed);assertEquals(1,x.truth.unresolvedBuildingSiteCount)
    }
    @Test fun inventoryBindingDoesNotAuthorizePhoneUse() {
        blocked(r.copy(inventorySha256="e".repeat(64)),"INVENTORY_MISMATCH")
        assertFalse(assess(r.copy(mode="TELEPHONE"),mode="TELEPHONE").passed)
        // Matching hash is only content binding. Phone eligibility remains a separate inventory gate.
        val inv="e".repeat(64)
        assertTrue(assess(r.copy(mode="LETTER_WRITING",inventorySha256=inv),mode="LETTER_WRITING",inventory=inv).passed)
    }
    @Test fun malformedAndAmbiguousPortableBytesFailClosed() {
        val bytes=NativeSourceReconciliationContract.encode(r);val text=bytes.toString(Charsets.UTF_8)
        val bad=listOf(" "+text,text.replace("\"schema\":","\"unknown\":true,\"schema\":"),text.replace("\"author\":","\"author\":\"duplicate\",\"author\":"),"[".repeat(20)+"]".repeat(20))
        bad.forEach { assertThrows(Exception::class.java) { NativeSourceReconciliationContract.decode(it.toByteArray()) } }
        assertThrows(Exception::class.java) {NativeSourceReconciliationContract.decode(byteArrayOf(0xc3.toByte(),0x28))}
        assertThrows(Exception::class.java) {NativeSourceReconciliationContract.decode(ByteArray(NativeSourceReconciliationContract.MAX_BYTES+1))}
        assertThrows(Exception::class.java) {NativeSourceReconciliationContract.encode(r.copy(segments=r.segments+r.segments))}
        assertThrows(Exception::class.java) {NativeSourceReconciliationContract.encode(r.copy(author="Untrimmed "))}
    }
    @Test fun completeDigestBindsBuildingOriginsAndExactCoordinates() {
        val b=BuildingGeometry("b1","300", "apartment",true,"",listOf("300"),
            listOf(BuildingLabelItem("300",Point2D(300.0,300.0),Point2D(299.0,300.0),0.0,9.0)),
            listOf(Point2D(280.0,280.0),Point2D(320.0,280.0),Point2D(320.0,320.0)))
        val x=a.copy(buildings=listOf(b));val y=x.copy(buildings=listOf(b.copy(labelItems=listOf(b.labelItems.single().copy(origin=Point2D(298.0,300.0))))))
        assertNotEquals(NativeSourceReconciliationContract.assignmentContentSha256(x),NativeSourceReconciliationContract.assignmentContentSha256(y))
        assertNotEquals(NativeSourceReconciliationContract.assignmentContentSha256(a),NativeSourceReconciliationContract.assignmentContentSha256(a.copy(roads=listOf(road.copy(widthPt=4.00000001)))))
    }
    @Test fun sharedVocabularyAndClassModeRulesAreRespected() {
        val context=r.segments.single().copy(status="context",role="context",insideSide="")
        assertEquals(context,NativeSourceReconciliationContract.decode(NativeSourceReconciliationContract.encode(r.copy(segments=listOf(context)))).segments.single())
        assertThrows(Exception::class.java) { NativeSourceReconciliationContract.encode(r.copy(segments=listOf(context.copy(role="access_only")))) }
        val inv="e".repeat(64)
        val result=assess(r.copy(mode="TELEPHONE",inventorySha256=inv),mode="TELEPHONE",inventory=inv)
        assertTrue("CLASS_MODE_MISMATCH" in result.failures)
        val excluded=SourceBuildingObservation("excluded",emptyList(),false,"Not assigned",true)
        assertEquals(excluded,NativeSourceReconciliationContract.decode(NativeSourceReconciliationContract.encode(r.copy(buildings=listOf(excluded)))).buildings.single())
    }
    @Test fun namedAssignmentRoundTripAndPortableBinding() {
        val bytes=NativeAssignmentCodec.encode(a)
        assertEquals(a,NativeAssignmentCodec.decode(bytes))
        val out=File(root,"../evidence/assignment-golden.json");out.parentFile.mkdirs();out.writeBytes(bytes)
        File(root,"../evidence/assignment-content.sha256").writeText(NativeSourceReconciliationContract.assignmentContentSha256(a))
        assertThrows(Exception::class.java) {NativeAssignmentCodec.decode((" "+bytes.toString(Charsets.UTF_8)).toByteArray())}
        val extra=bytes.toString(Charsets.UTF_8).replace("\"schema\":","\"unknown\":true,\"schema\":")
        assertThrows(Exception::class.java) {NativeAssignmentCodec.decode(extra.toByteArray())}
    }
    @Test fun durableRegistrationRevocationAndFreshWitnessChecks() {
        val dir=java.nio.file.Files.createTempDirectory("native-ledger").toFile()
        var source:String?=r.importedSourceSha256
        fun ledger()=NativeRegistrationLedger(dir,kb,{source},{_,_->null},{_,_->NativeSourceReconciliationContract.assignmentContentSha256(a)},{_,_->NativeSourceReconciliationContract.draftFactsSha256(r)})
        try {
            val l=ledger();val saved=l.register(r,a,null)
            assertEquals(1,saved.head.sequence);assertEquals(saved,ledger().active(r.territory,r.mode))
            assertNotNull(l.current(r.territory,saved.assignment.authoritySha256))
            source="c".repeat(64);assertNull(l.current(r.territory,saved.assignment.authoritySha256))
            source=r.importedSourceSha256
            assertThrows(Exception::class.java) {l.register(r.copy(explicitAssignmentConfirmation=false),a,saved.head.eventSha256)}
            assertEquals(saved,ledger().active(r.territory,r.mode))
            assertThrows(Exception::class.java) {l.register(r,a,null)}
            val revoked=l.revoke(r.territory,r.mode,saved.head.eventSha256,"Synthetic reviewer","2026-09-28T04:01:00Z")
            assertEquals(2,revoked.sequence);assertNull(ledger().current(r.territory,saved.assignment.authoritySha256))
            assertNull(ledger().active(r.territory,r.mode))
            assertThrows(Exception::class.java) {l.register(r,a,saved.head.eventSha256)}
            // Deleting a committed revocation cannot reactivate its predecessor.
            val event=dir.walkTopDown().single {it.name==revoked.eventSha256+".event"};assertTrue(event.delete())
            assertNull(ledger().current(r.territory,saved.assignment.authoritySha256))
            assertThrows(Exception::class.java) {ledger().history(r.territory,r.mode)}
        } finally {dir.deleteRecursively()}
    }
    @Test fun tamperedArchivesAndUncommittedEventsCannotGrantAuthority() {
        val dir=java.nio.file.Files.createTempDirectory("native-tamper").toFile()
        try {
            val l=NativeRegistrationLedger(dir,kb,{r.importedSourceSha256},{_,_->null},{_,_->NativeSourceReconciliationContract.assignmentContentSha256(a)},{_,_->NativeSourceReconciliationContract.draftFactsSha256(r)})
            val saved=l.register(r,a,null)
            val archive=dir.walkTopDown().single {it.name==saved.head.assignmentSha256+".assignment"}
            File(archive.parentFile,"unselected.event").writeText("Not a committed registration")
            assertEquals(saved,l.active(r.territory,r.mode))
            archive.appendText(" ")
            assertNull(l.current(r.territory,saved.assignment.authoritySha256))
            assertThrows(Exception::class.java) {l.active(r.territory,r.mode)}
        } finally {dir.deleteRecursively()}
    }
    @Test fun nativeAuthorityCannotBypassRequestOrAssignmentBinding() {
        val dir=java.nio.file.Files.createTempDirectory("native-request").toFile()
        val boundKb=kb.copy(referenceRoles=kb.referenceRoles+(slot.referenceFile to kb.referenceRoles.getValue(slot.referenceFile).copy(displayId=identity.displayId,fieldReleaseAllowed=false)))
        try {
            val ledger=NativeRegistrationLedger(dir,boundKb,{r.importedSourceSha256},{_,_->null},{_,_->NativeSourceReconciliationContract.assignmentContentSha256(a)},{_,_->NativeSourceReconciliationContract.draftFactsSha256(r)});val saved=ledger.register(r,a,null)
            val evidence=requireNotNull(ledger.current(r.territory,saved.assignment.authoritySha256))
            val policy=File(root,"app/src/main/assets/territory/Online-Source-Policy.json").reader().use(OnlineSourcePolicyLoader::load)
            fun request(eligibility:NativeAssignmentEligibility)=LiveGeometryVerificationRequestFactory.create(boundKb,policy,r.territory,saved.assignment.authoritySha256,evidence.topology,emptyList(),evidence.truth,VerificationJurisdiction("Oakland County","Michigan","United States"),nativeEligibility=eligibility)
            assertThrows(Exception::class.java) {request(NativeAssignmentEligibility.NONE)}
            assertTrue(LiveGeometryVerificationRequestFactory.fingerprintMatches(request(ledger)))
            assertFalse(evidence.matches(boundKb,saved.assignment.copy(locality="Changed"),evidence.truth))
            assertThrows(Exception::class.java) {NativeAssignmentEvidence.fromCurrentLedger(boundKb,NativeSourceReconciliationContract.encode(r),a,r.mode,r.importedSourceSha256,null)}
            ledger.revoke(r.territory,r.mode,saved.head.eventSha256,"Synthetic reviewer","2026-09-28T04:02:00Z")
            assertThrows(Exception::class.java) {request(ledger)}
        } finally {dir.deleteRecursively()}
    }
    @Test fun failedPrecommitPreservesSelectionAndPostcommitSyncFailureIsExplicit() {
        val dir=java.nio.file.Files.createTempDirectory("native-commit").toFile()
        var failBefore=false;var failAfter=false
        val ledger=NativeRegistrationLedger(dir,kb,{r.importedSourceSha256},{_,_->null},{_,_->NativeSourceReconciliationContract.assignmentContentSha256(a)},{_,_->NativeSourceReconciliationContract.draftFactsSha256(r)},
            beforeCommit={if(failBefore)error("Injected before rename")},syncDirectory={if(failAfter)error("Injected directory sync failure")})
        try {
            val first=ledger.register(r,a,null)
            val second=r.copy(registrationId="00000000-0000-0000-0000-000000000002",predecessorEventSha256=first.head.eventSha256)
            failBefore=true
            assertThrows(Exception::class.java) {ledger.register(second,a,first.head.eventSha256)}
            assertEquals(first,ledger.active(r.territory,r.mode))
            failBefore=false;failAfter=true
            val committed=ledger.register(second,a,first.head.eventSha256)
            assertEquals(committed,ledger.active(r.territory,r.mode));assertNotNull(ledger.durabilityWarning)
            assertNull(ledger.current(r.territory,first.assignment.authoritySha256))
        } finally {dir.deleteRecursively()}
    }
    @Test fun finalCapacityIsReservedForRevocation() {
        val dir=java.nio.file.Files.createTempDirectory("native-capacity").toFile()
        val ledger=NativeRegistrationLedger(dir,kb,{r.importedSourceSha256},{_,_->null},{_,_->NativeSourceReconciliationContract.assignmentContentSha256(a)},{_,_->NativeSourceReconciliationContract.draftFactsSha256(r)},syncDirectory={})
        try {
            var head:String?=null
            repeat(NativeRegistrationLedger.MAX_EVENTS-1) {i->
                val row=r.copy(registrationId="00000000-0000-0000-0000-"+(i+1).toString().padStart(12,'0'),predecessorEventSha256=head)
                head=ledger.register(row,a,head).head.eventSha256
            }
            val next=r.copy(registrationId="00000000-0000-0000-0000-000000001000",predecessorEventSha256=head)
            assertThrows(Exception::class.java) {ledger.register(next,a,head)}
            val revoked=ledger.revoke(r.territory,r.mode,requireNotNull(head),"Synthetic reviewer","2026-09-28T04:03:00Z")
            assertEquals(NativeRegistrationLedger.MAX_EVENTS,revoked.sequence);assertNull(ledger.active(r.territory,r.mode))
        } finally {dir.deleteRecursively()}
    }
    @Test fun lookupAndConcurrentRevocationLinearizeWithoutReactivatingAuthority() {
        val dir=java.nio.file.Files.createTempDirectory("native-race").toFile()
        val ledger=NativeRegistrationLedger(dir,kb,{r.importedSourceSha256},{_,_->null},{_,_->NativeSourceReconciliationContract.assignmentContentSha256(a)},{_,_->NativeSourceReconciliationContract.draftFactsSha256(r)},syncDirectory={})
        val executor=java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val saved=ledger.register(r,a,null)
            val revoked=java.util.concurrent.CountDownLatch(1)
            val aTask=executor.submit {ledger.revoke(r.territory,r.mode,saved.head.eventSha256,"Synthetic reviewer","2026-09-28T04:04:00Z");revoked.countDown()}
            val bTask=executor.submit {revoked.await();repeat(20) {assertNull(ledger.current(r.territory,saved.assignment.authoritySha256))}}
            aTask.get(30,java.util.concurrent.TimeUnit.SECONDS);bTask.get(30,java.util.concurrent.TimeUnit.SECONDS)
        } finally {executor.shutdownNow();dir.deleteRecursively()}
    }
    @Test fun sixRealAssignmentsAreUnchanged() {
        assertEquals(kb0.needsNewCardQueue,kb.needsNewCardQueue)
        kb0.needsNewCardQueue.keys.forEach {assertEquals(kb0.assignments[it],kb.assignments[it])}
    }
}

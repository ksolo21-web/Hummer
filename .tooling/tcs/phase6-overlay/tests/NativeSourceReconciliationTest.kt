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

    @Test fun strictDecimalFinalFileBoundary() {
        assertTrue(GeneratedPdfSizeContract.accepts(299999));assertFalse(GeneratedPdfSizeContract.accepts(300000))
        assertFalse(GeneratedPdfSizeContract.accepts(0));assertFalse(GeneratedPdfSizeContract.accepts(307200))
    }
    @Test fun nativeLabelTransformPreservesGeometryAndPerimeterExterior() {
        val template=File(root,"app/src/main/assets/territory/render-authority/Canonical-New-Designed-Template-R48.pdf").readBytes()
        val style=File(root,"app/src/main/assets/territory/R48-Canonical-Style-Tokens.json").reader().use(LabelStyleContractLoader::load)
        val producer=NativeRoadLabelProducer(template,style)
        val paths=listOf(listOf(Point2D(250.0,180.0),Point2D(650.0,180.0)),
            listOf(Point2D(450.0,60.0),Point2D(450.0,320.0)),
            listOf(Point2D(250.0,80.0),Point2D(650.0,300.0)))
        paths.forEach {points->
            val out=producer.labels(a.copy(roads=listOf(road.copy(points=points)))).single()
            assertTrue(out.assignedRoadGapPx in 2.0..4.0)
            assertTrue(out.baselineX in out.placement.bounds.left..out.placement.bounds.right)
            assertTrue(out.baselineTopY in out.placement.bounds.top..out.placement.bounds.bottom)
            assertFalse(out.placement.requiresReview)
        }
        val callout=producer.labels(a.copy(roads=listOf(road.copy(name="Tiny Ct",normalizedName="tiny ct",status="green",role="interior",insideSide="",points=listOf(Point2D(390.0,180.0),Point2D(410.0,180.0)))))).single().placement
        assertEquals(LabelPlacementMode.NEARBY_ATTACHED_ARROW_CALLOUT,callout.mode)
        assertTrue(requireNotNull(callout.calloutEvidence).calloutJustified)
        assertEquals(LabelSide.NEGATIVE_NORMAL,callout.calloutEvidence?.directCandidateSide)
        assertTrue(requireNotNull(callout.callout).roadAnchor.y>0.0)
        val curve=producer.labels(a.copy(roads=listOf(road.copy(name="Curving Terrace",normalizedName="curving terrace",status="green",role="interior",insideSide="",points=listOf(Point2D(320.0,180.0),Point2D(360.0,182.0),Point2D(400.0,190.0),Point2D(440.0,205.0)))))).single().placement
        assertEquals(LabelPlacementMode.CURVED_ROAD_FOLLOWING,curve.mode)
        assertNotNull(curve.sideEvidence)
        val regular=producer.labels(a.copy(roads=listOf(road.copy(role="interior",status="green",insideSide="",points=listOf(Point2D(225.0,191.5),Point2D(650.0,191.5)))))) .single()
        assertTrue(regular.placement.center.y<191.5,"horizontal tie must place label north")
        val horizontal=producer.labels(a).single()
        assertTrue(horizontal.placement.center.y>road.points.first().y,"left inside requires right exterior in top-origin geometry")
    }

    @Test fun portableStructuralDifferenceMatrix() {
        val cases=listOf(r,r.copy(segments=listOf(r.segments.single().copy(segmentId="missing"))),
            r.copy(segments=listOf(r.segments.single().copy(status="red"))),
            r.copy(segments=listOf(r.segments.single().copy(confirmed=false))),
            r.copy(buildings=listOf(SourceBuildingObservation("missing",listOf("1"),true,"Synthetic source",true))))
        val rows=cases.map {v->
            val t=assess(v).truth
            val expected="""{"missingExpectedSegmentCount":${t.missingExpectedSegmentCount},"unexpectedMeaningChangingSegmentCount":${t.unexpectedMeaningChangingSegmentCount},"assignmentColorConflictCount":${t.assignmentColorConflictCount},"unresolvedPerimeterWorkedSideCount":${t.unresolvedPerimeterWorkedSideCount},"unresolvedBuildingSiteCount":${t.unresolvedBuildingSiteCount}}"""
            """{"reconciliation":${NativeSourceReconciliationContract.encode(v).toString(Charsets.UTF_8)},"assignment":${NativeAssignmentCodec.encode(a).toString(Charsets.UTF_8)},"expected":$expected}"""
        }
        File(root,"../evidence/structural-parity.json").also {it.parentFile.mkdirs()}.writeText(rows.joinToString(",","[","]"))
    }

    @Test fun automaticImageExtractionFindsLabeledColoredRoadsAndMissingText() {
        val w=500;val h=300;val pixels=IntArray(w*h){0xffffffff.toInt()}
        for(y in 98..102)for(x in 50..450)pixels[y*w+x]=0xff00bb30.toInt()
        for(y in 198..202)for(x in 50..450)pixels[y*w+x]=0xffdd2020.toInt()
        val names=listOf(MapImageText("Alpha Rd",AxisAlignedRect(190.0,75.0,270.0,92.0)),MapImageText("Missing Ct",AxisAlignedRect(20.0,250.0,90.0,270.0)))
        val result=MapImageDraftExtractor.extract(w,h,pixels,names)
        assertEquals(2,result.roads.size)
        assertTrue(result.roads.any {it.name=="Alpha Rd" && it.status=="green"})
        assertTrue(result.roads.any {it.name==null && it.status=="red"})
        assertTrue(result.findings.any {it.id.startsWith("unmatched-label-")})
        assertTrue(result.roads.all {it.points.size>=2})
    }
    @Test fun numberedConcaveFootprintRejectsBoundingBoxNeighbor() {
        val w=300;val h=220;val pixels=IntArray(w*h){0xffffffff.toInt()}
        for(y in 30..150)for(x in 30..60)pixels[y*w+x]=0xff00aa30.toInt()
        for(y in 120..150)for(x in 30..170)pixels[y*w+x]=0xff00aa30.toInt()
        val labels=listOf(MapImageText("101",AxisAlignedRect(35.0,60.0,55.0,76.0)),MapImageText("999",AxisAlignedRect(110.0,60.0,140.0,76.0)))
        val result=MapImageDraftExtractor.extract(w,h,pixels,labels)
        assertEquals(1,result.buildings.size)
        assertEquals(listOf("101"),result.buildings.single().labels.map {it.text})
        assertTrue(result.buildings.single().polygon.size>=6)
        assertTrue(result.findings.any {it.id.startsWith("unmatched-member-")})
    }
    @Test fun mapNarrativeIsNotMistakenForRoadLabel() {
        assertNull(MapImageDraftExtractor.streetText("E = Elizabeth St B = Baldwin Ave. Numbers identify buildings"))
        assertNull(MapImageDraftExtractor.streetText("Enter Miller Ave. Work only the three green buildings"))
        assertEquals("Alpha Rd",MapImageDraftExtractor.streetText("Alpha Rd: inside RIGHT"))
        assertNull(MapImageDraftExtractor.streetText("To Meadow Ln"))
        listOf("St Clair St","St John Dr","Parkway Dr","Court St","Trail Rd").forEach {assertEquals(it,MapImageDraftExtractor.streetText(it))}
    }
    @Test fun unicodeNumberRangesRemainBuildingEvidence() {
        val w=200;val h=120;val pixels=IntArray(w*h){0xffffffff.toInt()}
        for(y in 30..80)for(x in 30..150)pixels[y*w+x]=0xff00aa30.toInt()
        listOf("440–456","440—456","440 − 456").forEach {label->
            val result=MapImageDraftExtractor.extract(w,h,pixels,listOf(MapImageText(label,AxisAlignedRect(50.0,45.0,130.0,65.0))))
            assertEquals(listOf(label.replace(" ","")),result.buildings.single().labels.map {it.text})
        }
    }
    @Test fun numberedCaptionExposesMemberMissedByOcr() {
        val w=200;val h=150;val pixels=IntArray(w*h){0xffffffff.toInt()}
        for(y in 30..80)for(x in 30..150)pixels[y*w+x]=0xff00aa30.toInt()
        val text=listOf(MapImageText("490",AxisAlignedRect(70.0,45.0,105.0,65.0)),MapImageText("Units: 488 / 490",AxisAlignedRect(30.0,100.0,170.0,120.0)))
        val result=MapImageDraftExtractor.extract(w,h,pixels,text)
        assertTrue(result.findings.any {it.id.startsWith("caption-members-") && it.message.contains("488")})
    }

    @Test fun shortContiguousWorkColorChangeCannotDisappearInMajority() {
        val w=500;val h=150;val pixels=IntArray(w*h){0xffffffff.toInt()}
        for(y in 68..72)for(x in 30..470)pixels[y*w+x]=if(x in 245..251)0xffdd2020.toInt() else 0xff00bb30.toInt()
        val result=MapImageDraftExtractor.extract(w,h,pixels,listOf(MapImageText("Alpha Rd",AxisAlignedRect(160.0,43.0,250.0,62.0))))
        assertTrue(result.findings.any {it.id.startsWith("color-")},"A short real excluded segment must be reviewed")
    }

    @Test fun imageInterpretationReceiptIsBoundToRegistrationFacts() {
        val interpreted=r.copy(imageInterpretationSha256="d".repeat(64))
        assertNotEquals(NativeSourceReconciliationContract.draftFactsSha256(r),NativeSourceReconciliationContract.draftFactsSha256(interpreted))
        assertEquals(interpreted,NativeSourceReconciliationContract.decode(NativeSourceReconciliationContract.encode(interpreted)))
    }

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
            val archive=ledger.archiveRevokedHistory(r.territory,r.mode,revoked.eventSha256)
            assertEquals(revoked.eventSha256,archive);assertTrue(ledger.history(r.territory,r.mode).isEmpty())
            assertThrows(Exception::class.java){ledger.register(r,a,null)}
            val fresh=r.copy(registrationId="00000000-0000-0000-0000-000000002000",reviewedAtUtc="2026-09-28T04:04:00Z",predecessorEventSha256=null)
            assertEquals(1,ledger.register(fresh,a,null).head.sequence)
            assertTrue(dir.walkTopDown().any {it.name==archive && it.isDirectory})
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

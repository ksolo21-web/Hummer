package com.koenterprises.territorycardstudio

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.koenterprises.territorycardstudio.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase3CBStoreInstrumentationTest {
    private fun fails(run:()->Unit)=assertTrue("Must reject",runCatching(run).isFailure)
    @Test fun everyCategoryPersistsReopensAndRestoresWithoutPreparing() {
        val covered=mutableSetOf<ExtendedKind>()
        listOf(false,true).forEach {phone->Phase3CBFixture(phone).use {x->
            val state=x.f.state();val originalKb=x.kb.toString();x.create()
            x.catalog().items.map {it.kind}.distinct().forEach {kind->
                val p=x.proposal(kind);val d=x.draft();x.store.save(x.id,x.mode,d.latest.token,d.latest.proposals+p);covered+=kind
                val reopened=AndroidExtendedDraftStore(x.root,x.kb,x.f.sources,x.f.coordinator).read(x.id,x.mode)!!
                assertEquals(p,reopened.latest.proposals.last());assertFalse(reopened.grantsAuthority)
            }
            val all=x.draft();x.store.save(x.id,x.mode,all.latest.token,emptyList());val empty=x.draft()
            x.store.restore(x.id,x.mode,empty.latest.token,all.latest.number);assertEquals(all.latest.proposals,x.draft().latest.proposals)
            assertEquals(state,x.f.state());assertEquals(originalKb,x.kb.toString())
        }}
        assertEquals(ExtendedKind.entries.toSet(),covered)
    }
    @Test fun exactBeforeKnownIdsCategoryEvidenceAndModesAreEnforced() {
        Phase3CBFixture().use {x->val d=x.create();val p=x.proposal(ExtendedKind.ROAD_WORK)
            fails{x.store.save(x.id,x.mode,d.latest.token,listOf(p.copy(itemId="unknown")))}
            fails{x.store.save(x.id,x.mode,d.latest.token,listOf(p.copy(before=WorkValue("red","excluded","",false))))}
            fails{x.store.save(x.id,x.mode,d.latest.token,listOf(p.copy(evidenceRole="IMPORTED_MAP",evidenceSha256=x.f.source.sha256)))}
            fails{x.store.save(x.id,x.mode,d.latest.token,listOf(p,p))}
            fails{x.store.catalog(x.id,WorkspaceMode.TELEPHONE)}
            fails{x.store.create("unknown",x.mode,d.binding)}
            assertEquals(0,x.draft().latest.number)
        }
    }
    @Test fun geometryCompoundWorkAndBuildingMembersFailClosed() {
        Phase3CBFixture().use {x->val d=x.create();val road=x.proposal(ExtendedKind.ROAD_PATH)
            listOf(listOf(Point2D(Double.NaN,20.0),Point2D(250.0,100.0)),listOf(Point2D(100.0,100.0),Point2D(250.0,100.0)),listOf(Point2D(250.0,100.0))).forEach {ps->fails{x.store.save(x.id,x.mode,d.latest.token,listOf(road.copy(proposed=PathValue(ps))))}}
            val work=x.proposal(ExtendedKind.ROAD_WORK);fails{x.store.save(x.id,x.mode,d.latest.token,listOf(work.copy(proposed=WorkValue("green","perimeter","left",true))))}
            val building=x.proposal(ExtendedKind.BUILDING_MEMBERS);val b=building.proposed as BuildingValue
            fails{x.store.save(x.id,x.mode,d.latest.token,listOf(building.copy(proposed=b.copy(members=listOf("3")))))}
            fails{x.store.save(x.id,x.mode,d.latest.token,listOf(building.copy(proposed=b.copy(label="Wrong"))))}
            val path=x.proposal(ExtendedKind.BUILDING_PATH);fails{x.store.save(x.id,x.mode,d.latest.token,listOf(path.copy(proposed=PathValue(listOf(Point2D(300.0,140.0),Point2D(400.0,190.0),Point2D(300.0,190.0),Point2D(400.0,140.0))))))}
            assertEquals(0,x.draft().latest.number)
        }
    }
    @Test fun phoneAvailabilityAndProvenanceCannotManufactureVerification() {
        Phase3CBFixture(true).use {x->val d=x.create();val p=x.proposal(ExtendedKind.PHONE_NUMBER)
            fails{x.store.save(x.id,x.mode,d.latest.token,listOf(p.copy(proposed=PhoneValue(ProposedPhoneState.UNAVAILABLE,"2025550101"))))}
            fails{x.store.save(x.id,x.mode,d.latest.token,listOf(p.copy(proposed=PhoneValue(ProposedPhoneState.NUMBER,"123"))))}
            fails{x.store.save(x.id,x.mode,d.latest.token,listOf(p.copy(evidenceRole="ADDRESS_SOURCE")))}
            val valid=p.copy(proposed=PhoneValue(ProposedPhoneState.NUMBER,"2025550199"));x.store.save(x.id,x.mode,d.latest.token,listOf(valid))
            assertFalse(x.draft().grantsAuthority);assertEquals("2025550101",(x.f.inventory as Page2Inventory.Telephone).inventory.records.first().phoneNumber)
            val unavailable=x.catalog().items.single {it.kind==ExtendedKind.PHONE_NUMBER && it.id=="phone-2"}.before as PhoneValue
            assertEquals(ProposedPhoneState.UNAVAILABLE,unavailable.state);assertEquals("",unavailable.number)
        }
    }
    @Test fun inventoryRequiresVerifiedBaselineAndRejectsDuplicateAddresses() {
        Phase3CBFixture(inventory=false).use {x->assertFalse(x.catalog().inventoryAvailable);assertTrue(x.catalog().items.none {it.kind==ExtendedKind.LETTER_ADDRESS});x.create()
            val fake=ExtendedProposal(ExtendedKind.LETTER_ADDRESS,"new",AddressValue("A","","","","",""),AddressValue("B","","","","",""),"Reason","ADDRESS_SOURCE","b".repeat(64));fails{x.store.save(x.id,x.mode,x.draft().latest.token,listOf(fake))}}
        Phase3CBFixture(true).use {x->x.create();val item=x.catalog().items.single {it.kind==ExtendedKind.PHONE_ADDRESS && it.id=="phone-2"};val first=x.catalog().items.single {it.kind==ExtendedKind.PHONE_ADDRESS && it.id=="phone-1"};val e=item.evidence.first();fails{x.store.save(x.id,x.mode,x.draft().latest.token,listOf(ExtendedProposal(item.kind,item.id,item.before,first.before,"Duplicate","ADDRESS_SOURCE",e.sha256)))}}
    }
    @Test fun staleSourceInventoryAndOptimisticConflictsPreserveHistory() {
        Phase3CBFixture().use {x->x.seed();val d=x.draft();x.store.save(x.id,x.mode,d.latest.token,d.latest.proposals.dropLast(1));fails{x.store.restore(x.id,x.mode,d.latest.token,0)};fails{x.store.discard(x.id,x.mode,d.latest.token)}
            val inv=(x.f.inventory as Page2Inventory.LetterWriting).inventory
            x.f.coordinator.prepare(x.id,x.mode,x.f.source.sha256,x.f.input,Page2Inventory.LetterWriting(inv.copy(records=inv.records.map {it.copy(streetAddress="200 Changed Way")})))
            assertTrue(x.draft().stale);fails{x.store.save(x.id,x.mode,x.draft().latest.token,emptyList())}
            x.f.importSource("changed");assertTrue(x.draft().stale);x.store.discard(x.id,x.mode,x.draft().latest.token);assertNull(x.store.read(x.id,x.mode))
        }
    }
    @Test fun corruptJournalCannotBecomeAnEmptyDraftAndValuesAreDetached() {
        Phase3CBFixture().use {x->
            x.create();val p=x.proposal(ExtendedKind.ROAD_PATH);val points=(p.proposed as PathValue).points.toMutableList()
            x.store.save(x.id,x.mode,x.draft().latest.token,listOf(p.copy(proposed=PathValue(points)),x.proposal(ExtendedKind.BUILDING_MEMBERS)))
            points.clear();val draft=x.draft();val catalog=x.catalog()
            val path=draft.latest.proposals.first {it.kind==ExtendedKind.ROAD_PATH}
            assertTrue((path.proposed as PathValue).points.isNotEmpty())
            val encoded=ExtendedValues.canonical(ExtendedValues.json(path.proposed))
            fails{(draft.revisions as MutableList<*>).clear()};fails{(draft.latest.proposals as MutableList<*>).clear()}
            fails{((path.before as PathValue).points as MutableList<*>).clear()};fails{((path.proposed as PathValue).points as MutableList<*>).clear()}
            fails{(catalog.items as MutableList<*>).clear()};fails{(catalog.items.first().evidence as MutableList<*>).clear()}
            val building=draft.latest.proposals.first {it.kind==ExtendedKind.BUILDING_MEMBERS}
            listOf(building.before,building.proposed,catalog.items.first {it.kind==ExtendedKind.BUILDING_MEMBERS}.before).forEach {v->
                val value=v as BuildingValue;fails{(value.members as MutableList<*>).clear()};fails{(value.labels as MutableList<*>).clear()}
            }
            assertEquals(draft,x.draft());assertEquals(catalog,x.catalog());assertEquals(encoded,ExtendedValues.canonical(ExtendedValues.json(path.proposed)))
            val file=x.root.listFiles()!!.single {it.extension=="json"};file.appendText("garbage");fails{x.store.read(x.id,x.mode)};fails{x.store.create(x.id,x.mode,x.catalog().binding)}
        }
    }
    @Test fun limitsInterruptedWritesAndConcurrentEditorsPreserveJournal() {
        Phase3CBFixture().use {x->
            x.create();val initial=x.draft();val file=x.root.listFiles()!!.single {it.extension=="json"}
            val atomic=android.util.AtomicFile(file);val pending=atomic.startWrite();pending.write("interrupted".toByteArray());pending.close()
            assertEquals(initial,x.draft())
            val barrier=java.util.concurrent.CountDownLatch(1);val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
            try {
                val jobs=(1..2).map {n->pool.submit<Boolean> {barrier.await();runCatching {
                    val other=AndroidExtendedDraftStore(x.root,x.kb,x.f.sources,x.f.coordinator)
                    other.save(x.id,x.mode,initial.latest.token,listOf(x.proposal(ExtendedKind.ROAD_WORK).copy(reason="Writer $n")))
                }.isSuccess}}
                barrier.countDown();assertEquals(1,jobs.count {it.get(30,java.util.concurrent.TimeUnit.SECONDS)})
            }finally {pool.shutdownNow()}
            val current=x.draft();val p=x.proposal(ExtendedKind.ROAD_WORK)
            fails{x.store.save(x.id,x.mode,current.latest.token,(0..AndroidExtendedDraftStore.MAX_PROPOSALS).map {p.copy(itemId="item-$it")} )}
            while(x.draft().revisions.size<AndroidExtendedDraftStore.MAX_REVISIONS) {val d=x.draft();x.store.save(x.id,x.mode,d.latest.token,listOf(p.copy(reason="Revision ${d.latest.number+1}")))}
            val full=x.draft();fails{x.store.save(x.id,x.mode,full.latest.token,emptyList())};assertEquals(full,x.draft())
            file.writeBytes(ByteArray(AndroidExtendedDraftStore.MAX_BYTES+1){32});fails{x.store.read(x.id,x.mode)}
        }
    }
    @Test fun formRoundTripsEveryCategoryIncludingMultipleBuildingMembers() {
        listOf(false,true).forEach {phone->Phase3CBFixture(phone).use {x->x.catalog().items.forEach {item->assertEquals(item.before,ExtendedDraftForm.parse(item.kind,ExtendedDraftForm.fields(item.before).map {it.second}))}}}
        val b=BuildingValue(true,"1-2",listOf("1","2"),listOf(BuildingLabelItem("1",Point2D(330.0,165.0),Point2D(325.0,168.0),0.0,10.0),BuildingLabelItem("2",Point2D(370.0,165.0),Point2D(365.0,168.0),0.0,10.0)))
        assertEquals(b,ExtendedDraftForm.parse(ExtendedKind.BUILDING_MEMBERS,ExtendedDraftForm.fields(b).map {it.second}));ExtendedValues.validate(ExtendedKind.BUILDING_MEMBERS,b)
    }
}

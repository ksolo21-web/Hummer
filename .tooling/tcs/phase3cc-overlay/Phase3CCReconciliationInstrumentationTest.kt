package com.koenterprises.territorycardstudio

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.koenterprises.territorycardstudio.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class Phase3CCReconciliationInstrumentationTest {
    private fun fails(block:()->Unit) {assertTrue("Must fail closed",runCatching(block).isFailure)}
    @Test fun allLetterCategoriesAndLabelPrepareWithoutApproval() {
        Phase3CCFixture().use {x->
            x.importAll();assertNull(x.labels.read(x.id,x.mode));assertNull(x.extended.read(x.id,x.mode))
            x.seed();val previous=x.coordinator.state(x.id,x.mode);val t=x.validate()
            assertEquals(previous,x.coordinator.state(x.id,x.mode));assertEquals(1,x.calls);assertEquals(6,t.changeCount)
            x.preparation.prepare(t);assertTrue(x.coordinator.state(x.id,x.mode).canBuild)
            val base=x.coordinator.editingBaseline(x.id,x.mode)
            assertEquals("Alpha Road",base.first.assignment.roads.first().name);assertEquals("right",base.first.assignment.roads.first().insideSide)
            assertEquals(227.0,base.first.assignment.roads.first().points.first().x,0.0)
            assertEquals(listOf("2"),base.first.assignment.buildings.single().sourceMembers);assertEquals("2",base.first.assignment.buildings.single().labelItems.single().text)
            assertEquals(302.0,base.first.assignment.buildings.single().polygon.first().x,0.0)
            assertEquals("102 Verified Example Way",(base.third as Page2Inventory.LetterWriting).inventory.records.first().streetAddress)
            assertNotNull(x.coordinator.buildFront(x.id,x.mode).front);assertNotNull(x.coordinator.generatePage2(x.id,x.mode).packet)
            assertEquals("Alpha Rd",x.kb.assignments.getValue(x.id).roads.first().name);assertFalse(x.labels.read(x.id,x.mode)!!.grantsAuthority)
        }
    }
    @Test fun telephoneAuthorityUpdatesFieldsAndPreservesUnavailableNumber() {
        Phase3CCFixture(true).use {x->x.seed();x.importAll();val t=x.validate();x.preparation.prepare(t)
            val inv=(x.coordinator.editingBaseline(x.id,x.mode).third as Page2Inventory.Telephone).inventory
            assertEquals("2485550111",inv.records.first().phoneNumber);assertEquals("102 Verified Example Way",inv.records.first().streetAddress)
            assertEquals(TelephoneNumberState.UNAVAILABLE,inv.records.last().phoneState)
            assertNotNull(x.coordinator.buildFront(x.id,x.mode).front);assertNotNull(x.coordinator.generatePage2(x.id,x.mode).packet)
        }
    }
    @Test fun unrecognizedTamperedDuplicateAndMixedAuthorityFactsFailClosed() {
        Phase3CCFixture().use {x->
            fails{x.authority.importFacts(x.id,x.mode,x.assignmentBytes+32.toByte())}
            fails{x.authority.importFacts(x.id,x.mode,x.document(x.assignmentFacts+x.inventoryFacts))}
            fails{x.authority.importFacts(x.id,x.mode,ByteArray(AndroidEditingAuthorityStore.MAX_BYTES+1))}
            assertFalse(x.authority.status(x.id,x.mode).available)
            x.importAll();fails{x.authority.importFacts(x.id,x.mode,x.assignmentBytes)}
        }
        listOf<(ByteArray)->ByteArray>(
            {b->String(b).replace("\"schema\":1","\"schema\":1,\"schema\":1").toByteArray()},
            {b->String(b).replace("\"schema\":1","\"schema\":2").toByteArray()},
            {b->String(b).replace("\"ROAD_PATH\"","\"UNRECOGNIZED_CATEGORY\"").toByteArray()}
        ).forEach {transform->Phase3CCFixture(assignmentDocument=transform).use {x->fails{x.authority.importFacts(x.id,x.mode,x.assignmentBytes)};assertFalse(x.authority.status(x.id,x.mode).available)}}
    }
    @Test fun recordSpecificProvenanceAndUnknownTelephoneCannotPass() {
        Phase3CCFixture(true,inventoryTransform={old->val inv=(old as Page2Inventory.Telephone).inventory
            Page2Inventory.Telephone(inv.copy(provenance=inv.provenance+inv.provenance.first().copy(provenanceId="different",sourceSha256="d".repeat(64)),
                records=inv.records.mapIndexed {i,r->if(i==0)r.copy(addressProvenanceIds=listOf("different"),phoneProvenanceIds=listOf("different"))else r}))
        }).use {x->fails{x.authority.importFacts(x.id,x.mode,x.inventoryBytes)}}
        Phase3CCFixture(true,unknownPhone=true).use {x->x.seed();x.importAll();val previous=x.coordinator.currentCandidateVersion(x.id,x.mode);fails{x.validate()};assertEquals(previous,x.coordinator.currentCandidateVersion(x.id,x.mode))}
    }
    @Test fun exactBothJournalsRejectMissingExtraAndContradictoryChanges() {
        Phase3CCFixture().use {x->x.importAll();x.seedLabels();fails{x.validate()};x.seedExtended()
            val d=x.extended.read(x.id,x.mode)!!;x.extended.save(x.id,x.mode,d.latest.token,d.latest.proposals.dropLast(1));fails{x.validate()}
            val l=x.labels.read(x.id,x.mode)!!;x.labels.save(x.id,x.mode,l.latest.token,l.latest.edits+DraftLabelEdit(DraftLabelKind.BUILDING,"building-1","1","3","Contradictory member label",x.assignmentHash));fails{x.validate()}
            assertEquals(0,x.calls)
        }
    }
    @Test fun freshProvidersAndBuildingCrosschecksRemainMandatory() {
        Phase3CCFixture().use {x->x.seed();x.importAll();val previous=x.coordinator.currentCandidateVersion(x.id,x.mode)
            x.evidenceTransform={emptyList()};fails{x.validate()}
            x.evidenceTransform={it.filterNot {e->e.providerId=="oakland_county_buildings"}};fails{x.validate()}
            x.evidenceTransform={it.map {e->if(e.providerId=="oakland_county_site_addresses")e.copy(siteAddressCrosscheckPassed=false)else e}};fails{x.validate()}
            assertEquals(previous,x.coordinator.currentCandidateVersion(x.id,x.mode));assertEquals(3,x.calls)
        }
    }
    @Test fun changesDuringFetchRejectWithoutReplacingCandidate() {
        Phase3CCFixture().use {x->x.seed();x.importAll();val previous=x.coordinator.currentCandidateVersion(x.id,x.mode)
            x.duringFetch={val d=x.extended.read(x.id,x.mode)!!;x.extended.save(x.id,x.mode,d.latest.token,d.latest.proposals.dropLast(1))}
            fails{x.validate()};assertEquals(previous,x.coordinator.currentCandidateVersion(x.id,x.mode))
        }
        Phase3CCFixture().use {x->x.seed();x.importAll();val previous=x.coordinator.currentCandidateVersion(x.id,x.mode)
            x.duringFetch={x.authority.revoke(x.id,x.mode,x.authority.status(x.id,x.mode).receiptId!!)}
            fails{x.validate()};assertEquals(previous,x.coordinator.currentCandidateVersion(x.id,x.mode))
        }
    }
    @Test fun ticketsExpireCancelConsumeAndProtectChangedCandidate() {
        Phase3CCFixture().use {x->x.seed();x.importAll();val t=x.validate();x.preparation.cancel(t);fails{x.preparation.prepare(t)}
            val expired=x.validate();x.now+=300001;fails{x.preparation.prepare(expired)}
            val t2=x.validate();x.coordinator.prepare(x.id,x.mode,x.f.source.sha256,x.input,x.inventory);fails{x.preparation.prepare(t2)}
            x.authority.revoke(x.id,x.mode,x.authority.status(x.id,x.mode).receiptId!!);x.importAll();val t3=x.validate();x.preparation.prepare(t3);fails{x.preparation.prepare(t3)}
        }
    }
    @Test fun preparedGuardRejectsJournalCorruptionRevocationAndClockRollback() {
        Phase3CCFixture().use {x->x.seed();x.importAll();x.preparation.prepare(x.validate());assertTrue(x.coordinator.state(x.id,x.mode).canBuild)
            x.extendedRoot.listFiles()!!.single {it.extension=="json"}.appendText("bad");assertFalse(x.coordinator.state(x.id,x.mode).canBuild)
        }
        Phase3CCFixture().use {x->x.seed();x.importAll();x.preparation.prepare(x.validate());x.authority.revoke(x.id,x.mode,x.authority.status(x.id,x.mode).receiptId!!);assertFalse(x.coordinator.state(x.id,x.mode).canBuild)}
        Phase3CCFixture().use {x->x.seed();x.importAll();val t=x.validate();x.now=999;assertFalse(x.preparation.ticketCurrent(t));fails{x.preparation.prepare(t)}}
    }
    @Test fun witnessesHandleAbsenceAtomicWritesAndConcurrentReadWithoutDeadlock() {
        Phase3CCFixture().use {x->val absent=x.labels.revisionWitness(x.id,x.mode);assertTrue(absent.current());x.seedLabels();assertFalse(absent.current())
            val witness=x.labels.revisionWitness(x.id,x.mode);val file=x.labelsRoot.listFiles()!!.single {it.extension=="json"}
            File(file.path+".new").writeText("partial");assertFalse(witness.current());File(file.path+".new").delete();assertTrue(witness.current())
            x.seedExtended();x.importAll();x.preparation.prepare(x.validate())
            val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
            try {val jobs=listOf(pool.submit<Boolean>{repeat(20){x.coordinator.state(x.id,x.mode)};true},pool.submit<Boolean>{repeat(20){x.extended.read(x.id,x.mode)};true})
                jobs.forEach {assertTrue(it.get(20,java.util.concurrent.TimeUnit.SECONDS))}
            }finally{pool.shutdownNow()}
        }
    }
}

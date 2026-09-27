package com.koenterprises.territorycardstudio

import android.util.AtomicFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.koenterprises.territorycardstudio.core.TerritoryKnowledgeBase
import com.koenterprises.territorycardstudio.core.BuildingGeometry
import com.koenterprises.territorycardstudio.core.Point2D
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class Phase3ADraftInstrumentationTest {
    private class Fixture : AutoCloseable {
        val f=Phase2DBFixture(false)
        val root=File(f.root,"drafts")
        val slot=f.slot.copy(roads=f.input.assignment.roads,buildings=listOf(BuildingGeometry("building-1","1","apartment",true,"",listOf("1"),emptyList(),listOf(Point2D(0.0,0.0),Point2D(1.0,0.0),Point2D(0.0,1.0)))))
        val kb=f.kb.copy(assignments=f.kb.assignments+(f.identity.displayId to slot))
        val id=f.identity.displayId
        val mode=WorkspaceMode.LETTER_WRITING
        fun store(other: TerritoryKnowledgeBase=kb)=AndroidEditingDraftStore(root,other,f.sources)
        fun edit(label: String="Alpha Road")=DraftLabelEdit(DraftLabelKind.ROAD,"adapter-alpha","Alpha Rd",label,"User supplied source correction",f.source.sha256)
        fun building()=DraftLabelEdit(DraftLabelKind.BUILDING,"building-1","1","Building 1","Existing source building label",f.source.sha256)
        fun file()=root.listFiles()!!.single { it.extension=="json" }
        override fun close()=f.close()
    }
    private fun fails(block: () -> Unit) { assertTrue("Operation must reject invalid/stale draft",runCatching(block).isFailure) }

    @Test fun createSaveReopenPreservesAuthorityAndBuildState() {
        Fixture().use { x ->
            x.f.prepare();x.f.build();x.f.page2();val before=x.f.state();val assignment=x.kb.assignments.getValue(x.id)
            val created=x.store().create(x.id,x.mode)
            assertEquals(0,created.latest.number);assertFalse(created.stale)
            val saved=x.store().save(x.id,x.mode,created.latest.token,listOf(x.edit(),x.building()))
            assertEquals(saved,x.store().read(x.id,x.mode))
            assertEquals("UNVALIDATED_PROPOSAL",saved.status);assertFalse(saved.grantsAuthority)
            assertEquals(assignment,x.kb.assignments.getValue(x.id));assertEquals(before,x.f.state())
            assertEquals(x.f.source,x.f.sources.verifiedRecord(x.id))
            assertEquals("Alpha Rd",x.kb.assignments.getValue(x.id).roads.first().name)
            assertEquals("Alpha Road",saved.latest.edits.first { it.kind==DraftLabelKind.ROAD }.proposed)
            assertEquals(x.building(),saved.latest.edits.first { it.kind==DraftLabelKind.BUILDING })
            assertEquals("1",x.kb.assignments.getValue(x.id).buildings.single().label)
        }
    }
    @Test fun invalidContextAndUnknownOrMalformedEditsFailClosed() {
        Fixture().use { x ->
            fails { x.store().create("unknown",x.mode) }
            fails { x.store().create(x.id,WorkspaceMode.TELEPHONE) }
            val d=x.store().create(x.id,x.mode);val edit=x.edit()
            val invalid=listOf(edit.copy(itemId="nonexistent"),edit.copy(before="wrong"),edit.copy(proposed="Alpha Rd"),
                edit.copy(proposed=" bad "),edit.copy(proposed="bad\nlabel"),edit.copy(proposed="x".repeat(257)),
                edit.copy(rationale=""),edit.copy(evidenceSha256="f".repeat(64)),edit.copy(evidenceSha256="invalid"),
                edit.copy(kind=DraftLabelKind.BUILDING))
            invalid.forEach { e -> fails { x.store().save(x.id,x.mode,d.latest.token,listOf(e)) } }
            fails { x.store().save(x.id,x.mode,d.latest.token,listOf(edit,edit)) }
            assertEquals(d,x.store().read(x.id,x.mode))
        }
    }
    @Test fun modeIsolationAndCrossContextReplayRejected() {
        Fixture().use { x ->
            val regular=x.store().create(x.id,WorkspaceMode.REGULAR)
            val letter=x.store().create(x.id,x.mode)
            assertNotEquals(regular.baseBinding,letter.baseBinding)
            val files=x.root.listFiles()!!.filter { it.extension=="json" }
            val a=files.first { it.readText().contains("\"mode\":\"REGULAR\"") };val b=files.first { it!=a }
            b.writeBytes(a.readBytes())
            fails { x.store().read(x.id,x.mode) };fails { x.store().create(x.id,x.mode) }
            assertEquals(regular,x.store().read(x.id,WorkspaceMode.REGULAR))
        }
    }
    @Test fun allMutationsRejectOldRevisionTokens() {
        Fixture().use { x ->
            val a=x.store();val b=x.store();val first=a.create(x.id,x.mode)
            val second=a.save(x.id,x.mode,first.latest.token,listOf(x.edit()))
            fails { b.save(x.id,x.mode,first.latest.token,listOf(x.edit("Alpha Avenue"))) }
            fails { b.restore(x.id,x.mode,first.latest.token,0) }
            fails { b.discard(x.id,x.mode,first.latest.token) }
            assertEquals(second,b.read(x.id,x.mode))
        }
    }
    @Test fun sourceAndLockedBaseChangesMakeDraftStale() {
        Fixture().use { x ->
            val start=x.store().create(x.id,x.mode)
            val first=x.store().save(x.id,x.mode,start.latest.token,listOf(x.edit()))
            val d=x.store().save(x.id,x.mode,first.latest.token,listOf(x.edit("Alpha Avenue")))
            val changed=x.kb.copy(assignments=x.kb.assignments+(x.id to x.slot.copy(roads=x.slot.roads.map { it.copy(widthPt=it.widthPt+1) })))
            assertTrue(x.store(changed).read(x.id,x.mode)!!.stale)
            fails { x.store(changed).save(x.id,x.mode,d.latest.token,listOf(x.edit())) }
            assertTrue(x.store(x.kb.copy(revision=x.kb.revision+"-changed")).read(x.id,x.mode)!!.stale)
            x.f.importSource("source-replaced")
            assertTrue(x.store().read(x.id,x.mode)!!.stale)
            fails { x.store().save(x.id,x.mode,d.latest.token,listOf(x.edit())) }
            fails { x.store().restore(x.id,x.mode,d.latest.token,1) }
            x.store().discard(x.id,x.mode,d.latest.token);assertNull(x.store().read(x.id,x.mode))
        }
    }
    @Test fun restoreCreatesNewRevisionAndRetainsOriginalProvenance() {
        Fixture().use { x ->
            val a=x.store().create(x.id,x.mode)
            val b=x.store().save(x.id,x.mode,a.latest.token,listOf(x.edit(),x.building()))
            val c=x.store().save(x.id,x.mode,b.latest.token,listOf(x.edit("Alpha Avenue")))
            val d=x.store().restore(x.id,x.mode,c.latest.token,1)
            assertEquals(3,d.latest.number);assertEquals(1,d.latest.restoredFrom);assertEquals("RESTORE",d.latest.action)
            assertEquals(b.latest.edits,d.latest.edits);assertEquals(c.revisions,d.revisions.take(3))
            assertNotEquals(b.latest.token,d.latest.token);assertEquals(d,x.store().read(x.id,x.mode))
            x.store().discard(x.id,x.mode,d.latest.token);assertNull(x.store().read(x.id,x.mode))
        }
    }
    @Test fun corruptDraftIsNeverTreatedAsAbsentOrOverwritten() {
        Fixture().use { x ->
            val d=x.store().create(x.id,x.mode);val file=x.file();val valid=file.readBytes()
            file.writeText(String(valid).replace(d.latest.token,"0".repeat(64)))
            val corrupt=file.readBytes()
            fails { x.store().read(x.id,x.mode) };fails { x.store().create(x.id,x.mode) }
            fails { x.store().save(x.id,x.mode,d.latest.token,listOf(x.edit())) }
            assertArrayEquals(corrupt,file.readBytes())
            file.writeBytes(valid+"garbage".toByteArray());fails { x.store().read(x.id,x.mode) }
        }
    }
    @Test fun interruptedAtomicWriteRetainsLastCommittedRevision() {
        Fixture().use { x ->
            val d=x.store().create(x.id,x.mode);val atomic=AtomicFile(x.file())
            atomic.startWrite().use { it.write("interrupted partial revision".toByteArray());it.fd.sync() }
            assertEquals(d,x.store().read(x.id,x.mode))
            val next=x.store().save(x.id,x.mode,d.latest.token,listOf(x.edit()))
            assertEquals(1,next.latest.number);assertEquals(next,x.store().read(x.id,x.mode))
        }
    }
    @Test fun boundedHistoryPayloadAndMissingSourceAreExplicit() {
        Fixture().use { x ->
            var d=x.store().create(x.id,x.mode)
            fails { x.store().save(x.id,x.mode,d.latest.token,List(65) { x.edit() }) }
            repeat(63) { n -> d=x.store().save(x.id,x.mode,d.latest.token,listOf(x.edit("Alpha $n"))) }
            fails { x.store().save(x.id,x.mode,d.latest.token,listOf(x.edit("Overflow"))) }
            assertEquals(64,x.store().read(x.id,x.mode)!!.revisions.size)
            val valid=x.file().readBytes();x.file().writeBytes(ByteArray(AndroidEditingDraftStore.MAX_BYTES+1))
            fails { x.store().read(x.id,x.mode) };x.file().writeBytes(valid)
            x.f.sources.clear(x.id);assertTrue(x.store().read(x.id,x.mode)!!.stale)
            x.store().discard(x.id,x.mode,d.latest.token)
            fails { x.store().create(x.id,x.mode) };assertNull(x.store().read(x.id,x.mode))
        }
    }
    @Test fun competingInstancesAllowExactlyOneWriter() {
        Fixture().use { x ->
            val d=x.store().create(x.id,x.mode);val ready=CountDownLatch(2);val go=CountDownLatch(1)
            val pool=Executors.newFixedThreadPool(2)
            try {
                val tasks=(0..1).map { n -> pool.submit<Boolean> { ready.countDown();go.await(5,TimeUnit.SECONDS)
                    runCatching { x.store().save(x.id,x.mode,d.latest.token,listOf(x.edit("Alpha $n"))) }.isSuccess } }
                assertTrue(ready.await(5,TimeUnit.SECONDS));go.countDown()
                assertEquals(1,tasks.count { it.get(10,TimeUnit.SECONDS) })
                assertEquals(1,x.store().read(x.id,x.mode)!!.latest.number)
            } finally { pool.shutdownNow() }
        }
    }
}

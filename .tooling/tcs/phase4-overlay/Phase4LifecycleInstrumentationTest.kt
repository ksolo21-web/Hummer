package com.koenterprises.territorycardstudio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import com.koenterprises.territorycardstudio.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class Phase4LifecycleInstrumentationTest {
    private val checks=CandidateReviewChecks(true,true,true)
    private fun denied(action:()->Unit) { assertTrue("Operation must fail closed",runCatching(action).isFailure) }
    private fun hash(bytes:ByteArray)=BundleIntegrity.sha256(bytes.inputStream())
    private fun AndroidCandidateLifecycleService.decideLatest(ticket:CandidateReviewTicket,actor:String,checks:CandidateReviewChecks,approved:Boolean,confirmed:Boolean):CandidateLifecycleState =
        decide(ticket,actor,checks,approved,confirmed,state(ticket.manifest.displayId,WorkspaceMode.valueOf(ticket.manifest.mode.name)).revision)

    private class Fixture(val mode:WorkspaceMode):AutoCloseable {
        val f=Phase2DBFixture(mode==WorkspaceMode.TELEPHONE)
        val id=f.identity.displayId
        val dir=File(f.root,"lifecycle")
        val service=AndroidCandidateLifecycleService(f.kb,f.coordinator,f.sources,dir)
        init { f.coordinator.prepare(id,mode,f.source.sha256,f.input,if(mode==WorkspaceMode.REGULAR)null else f.inventory) }
        fun build() {assertNull(service.build(id,mode,false).error);if(mode!=WorkspaceMode.REGULAR)assertNull(service.build(id,mode,true).error)}
        fun state()=service.state(id,mode)
        fun ticket()=requireNotNull(state().ticket)
        fun journal()=File(dir,hashText("$id:${mode.name}")+"/journal.json")
        fun archive(t:CandidateReviewTicket)=File(journal().parentFile,"pdf/${t.manifest.canonicalSha256()}.pdf")
        override fun close()=f.close()
        companion object { fun hashText(s:String)=BundleIntegrity.sha256(s.byteInputStream()) }
    }
    private fun allModes(action:(Fixture)->Unit) {WorkspaceMode.values().forEach {Fixture(it).use(action)}}
    @Test fun allModesBuildArchivesExactUnapprovedCandidate()=allModes {x->
        x.build();val t=x.ticket();val s=x.state()
        assertEquals(1,s.candidates.size);assertEquals("CANDIDATE-UNAPPROVED",s.candidates.single().status)
        assertNull(s.promoted);assertFalse(s.active);assertEquals(t,x.service.capture(x.id,x.mode).ticket)
        assertEquals(t.manifest.packetPdfSha256,hash(x.archive(t).readBytes()))
        assertEquals(if(x.mode==WorkspaceMode.REGULAR)1 else 2,t.manifest.pageCount)
        assertEquals(s.history,x.state().history);denied{x.service.readCurrentPdf(x.id,x.mode)}
    }
    @Test fun approvalRequiresExplicitConfirmationReviewerAndEveryCheck()=allModes {x->
        x.build();val t=x.ticket();val bytes=x.journal().readBytes()
        denied{x.service.decideLatest(t,"Reviewer",checks,true,false)}
        denied{x.service.decideLatest(t,"",checks,true,true)}
        for(c in listOf(CandidateReviewChecks(false,true,true),CandidateReviewChecks(true,false,true),CandidateReviewChecks(true,true,false)))
            denied{x.service.decideLatest(t,"Reviewer",c,true,true)}
        assertArrayEquals(bytes,x.journal().readBytes());assertNull(x.state().promoted)
        val s=x.service.decideLatest(t,"Reviewer",checks,true,true)
        assertTrue(s.active);assertEquals(t,s.promoted!!.ticket)
        assertEquals(t.manifest.packetPdfSha256,hash(x.service.readCurrentPdf(x.id,x.mode)))
    }
    @Test fun rejectionIsDurableAndHistoryNeverOverwritesPriorDecision()=allModes {x->
        x.build();val first=x.ticket();val rejected=x.service.decideLatest(first,"Reject reviewer",CandidateReviewChecks(false,false,false),false,true)
        assertEquals("REJECTED",rejected.candidates.single().status);assertFalse(rejected.active);assertNull(rejected.promoted)
        val prior=rejected.history
        x.build();val next=x.ticket();assertNotEquals(first,next)
        val approved=x.service.decideLatest(next,"Approve reviewer",checks,true,true)
        assertEquals(prior,approved.history.take(prior.size));assertTrue(approved.history.size>prior.size)
        assertEquals((1..approved.history.size).toList(),approved.history.map {it.sequence})
        assertEquals("REJECTED",approved.candidates.single {it.ticket==first}.status)
        val reloaded=AndroidCandidateLifecycleService(x.f.kb,x.f.coordinator,x.f.sources,x.dir).state(x.id,x.mode)
        assertEquals(approved.history,reloaded.history);assertEquals(approved.promoted,reloaded.promoted)
    }
    @Test fun byteIdenticalRebuildStillRequiresNewExactVersionApproval()=allModes {x->
        x.build();val old=x.ticket();x.service.decideLatest(old,"Reviewer",checks,true,true)
        if(x.mode==WorkspaceMode.REGULAR)assertNull(x.service.build(x.id,x.mode,false).error)
        else assertNull(x.service.build(x.id,x.mode,true).error)
        val fresh=x.ticket();assertEquals(old.manifest.packetPdfSha256,fresh.manifest.packetPdfSha256)
        assertNotEquals(old.manifest.candidateVersion,fresh.manifest.candidateVersion)
        assertFalse(x.state().active);denied{x.service.readCurrentPdf(x.id,x.mode)}
        val before=x.journal().readBytes();denied{x.service.decideLatest(old,"Stale reviewer",checks,true,true)};assertArrayEquals(before,x.journal().readBytes())
        assertTrue(x.service.decideLatest(fresh,"Fresh reviewer",checks,true,true).active)
    }
    @Test fun replacedSourceSuspendsPromotionAndCannotApproveStaleTicket()=allModes {x->
        x.build();val t=x.ticket();x.service.decideLatest(t,"Reviewer",checks,true,true);val history=x.state().history
        x.f.importSource("replacement-phase4")
        val s=x.state();assertFalse(s.active);assertNull(s.ticket);assertNotNull(s.blocker)
        assertEquals(history,s.history.take(history.size));denied{x.service.decideLatest(t,"Reviewer",checks,true,true)};denied{x.service.readCurrentPdf(x.id,x.mode)}
    }
    @Test fun editedJournalAndAuthorityChangesSuspendApprovedCandidates() {
        for(phone in listOf(false,true))for(change in 0..3)Phase3CCFixture(phone).use {x->
            x.seed();x.importAll();x.preparation.prepare(x.validate())
            val s=AndroidCandidateLifecycleService(x.kb,x.coordinator,x.f.sources,File(x.f.root,"lifecycle"))
            assertNull(s.build(x.id,x.mode,false).error);assertNull(s.build(x.id,x.mode,true).error)
            val t=requireNotNull(s.state(x.id,x.mode).ticket);assertTrue(s.decideLatest(t,"Reviewer",checks,true,true).active)
            when(change){
                0->{val d=x.labels.read(x.id,x.mode)!!;x.labels.save(x.id,x.mode,d.latest.token,emptyList())}
                1->{val d=x.extended.read(x.id,x.mode)!!;x.extended.discard(x.id,x.mode,d.latest.token)}
                2->x.authority.revoke(x.id,x.mode,x.authority.status(x.id,x.mode).receiptId!!)
                3->x.now+=900001
            }
            val invalidated=s.state(x.id,x.mode)
            assertFalse(invalidated.active);assertNull(invalidated.ticket);assertNull(invalidated.promoted)
            assertEquals("INVALIDATED",invalidated.history.last().action)
            assertEquals(t.manifest.canonicalSha256(),invalidated.history.last().candidateSha256)
            assertEquals(invalidated.history,s.state(x.id,x.mode).history)
            denied{s.decideLatest(t,"Reviewer",checks,true,true)};denied{s.readCurrentPdf(x.id,x.mode)}
        }
    }
    @Test fun freshCoordinatorReloadPreservesHistoryButCannotRestoreAuthority()=allModes {x->
        x.build();val t=x.ticket();val old=x.service.decideLatest(t,"Reviewer",checks,true,true)
        val bytes=x.journal().readBytes();val pdf=x.archive(t).readBytes()
        val coordinator=AndroidBuildWorkflowCoordinator(x.f.kb,AndroidRenderModelService(x.f.kb),x.f.service,SourceMapIntakeStore(x.f.app))
        val fresh=AndroidCandidateLifecycleService(x.f.kb,coordinator,SourceMapIntakeStore(x.f.app),x.dir)
        val state=fresh.state(x.id,x.mode);assertFalse(state.active);assertNull(state.ticket)
        assertEquals(old.history,state.history.take(old.history.size));assertEquals(t,state.promoted!!.ticket)
        assertArrayEquals(pdf,x.archive(t).readBytes());assertTrue(bytes.isNotEmpty())
        denied{fresh.readCurrentPdf(x.id,x.mode)};denied{fresh.decideLatest(t,"Reviewer",checks,true,true)}
    }
    @Test fun malformedAndOversizeJournalsFailClosedWithoutReplacement() {
        for(change in 0..4)Fixture(WorkspaceMode.REGULAR).use {x->
            x.build();val t=x.ticket();x.service.decideLatest(t,"Reviewer",checks,true,true)
            val payload=when(change) {
                0->"{broken".toByteArray()
                1->ByteArray(1024*1024+1){32}
                2->x.journal().readText().replace("Reviewer","Tampered reviewer").toByteArray()
                3->x.journal().readText().replace("\"schema\":1","\"schema\":99").toByteArray()
                else->("[".repeat(4096)+"0"+"]".repeat(4096)).toByteArray()
            }
            x.journal().writeBytes(payload)
            val s=x.state();assertFalse(s.active);assertNotNull(s.blocker)
            denied{x.service.readCurrentPdf(x.id,x.mode)};denied{x.service.decideLatest(t,"Reviewer",checks,true,true)}
            assertArrayEquals(payload,x.journal().readBytes())
        }
        Fixture(WorkspaceMode.REGULAR).use {x->
            x.build();val t=x.ticket();x.service.decideLatest(t,"Reviewer",checks,true,true)
            assertTrue(x.journal().delete());assertTrue(x.archive(t).isFile)
            assertFalse(x.state().active);assertNotNull(x.state().blocker)
            denied{x.service.readCurrentPdf(x.id,x.mode)};denied{x.service.capture(x.id,x.mode)}
            assertFalse(x.journal().exists())
        }
        Fixture(WorkspaceMode.REGULAR).use {x->
            x.build();val t=x.ticket()
            repeat(127){x.service.decideLatest(t,"Capacity reviewer $it",checks,true,true)}
            assertEquals(128,x.state().history.size);val bytes=x.journal().readBytes()
            denied{x.service.decideLatest(t,"Overflow reviewer",checks,true,true)}
            assertArrayEquals(bytes,x.journal().readBytes());assertEquals(128,x.state().history.size)
            assertTrue(x.state().active);assertEquals(t.manifest.packetPdfSha256,hash(x.service.readCurrentPdf(x.id,x.mode)))
        }
    }
    @Test fun archivedPdfTamperMissingAndOversizeBlockCurrentReads() {
        for(change in 0..2)Fixture(WorkspaceMode.REGULAR).use {x->
            x.build();val t=x.ticket();x.service.decideLatest(t,"Reviewer",checks,true,true)
            val f=x.archive(t);when(change){0->{val b=f.readBytes();b[b.lastIndex]=(b.last().toInt() xor 1).toByte();f.writeBytes(b)};1->assertTrue(f.delete());2->f.writeBytes(ByteArray(300*1024+1))}
            assertFalse(x.state().active);denied{x.service.readCurrentPdf(x.id,x.mode)};denied{x.service.decideLatest(t,"Reviewer",checks,true,true)}
        }
    }
    @Test fun concurrentServiceInstancesSerializeDecisionsWithoutLostHistory() {
        Fixture(WorkspaceMode.REGULAR).use {x->
            x.build();val ticket=x.ticket();val initial=x.state().history.size;val head=x.state().revision
            val pool=Executors.newFixedThreadPool(2);val start=CountDownLatch(1)
            try {
                val results=(1..2).map {n->pool.submit<Boolean>{start.await();runCatching{AndroidCandidateLifecycleService(x.f.kb,x.f.coordinator,x.f.sources,x.dir).decide(ticket,"Concurrent $n",checks,true,true,head)}.isSuccess}}
                start.countDown();val accepted=results.count {it.get(30,TimeUnit.SECONDS)};assertEquals(1,accepted)
                val state=x.state();assertTrue(state.active);assertEquals(ticket,state.promoted!!.ticket)
                assertEquals(initial+accepted,state.history.size);assertEquals((1..state.history.size).toList(),state.history.map {it.sequence})
            } finally {pool.shutdownNow()}
        }
    }
    @Test fun staleInstanceTicketCannotReplaceNewCurrentPointer()=allModes {x->
        x.build();val old=x.ticket();val other=AndroidCandidateLifecycleService(x.f.kb,x.f.coordinator,x.f.sources,x.dir)
        other.decideLatest(old,"First reviewer",checks,true,true);x.build();val next=x.ticket()
        val current=x.service.decideLatest(next,"Next reviewer",checks,true,true);val before=x.journal().readBytes()
        denied{other.decideLatest(old,"Stale reviewer",checks,true,true)}
        assertArrayEquals(before,x.journal().readBytes());assertEquals(current.promoted,x.state().promoted);assertTrue(x.state().active)
    }
    @Test fun lifecycleApprovalNeverChangesReservedCardsOrFieldExportAuthority()=allModes {x->
        val kb=x.f.app.services.knowledgeBase;val ids=listOf("T250","A257","297","A298","299","TA347")
        val assignments=ids.associateWith {kb.assignments.getValue(it)};val roles=kb.referenceRoles
        x.build();x.service.decideLatest(x.ticket(),"Reviewer",checks,true,true)
        val export=AndroidApprovedExportService(x.f.kb,x.f.service)
        assertNull(export.state(x.id).ticket);assertFalse(export.state(x.id).canAttach)
        assertEquals(assignments,ids.associateWith {x.f.app.services.knowledgeBase.assignments.getValue(it)})
        assertEquals(roles,x.f.app.services.knowledgeBase.referenceRoles)
        ids.forEach {assertNull(x.f.app.services.approvedExport.state(it).ticket);assertFalse(x.f.app.services.approvedExport.state(it).canAttach)}
    }
    @Test fun staleSameCandidateDialogCannotOverrideNewerRejection()=allModes {x->
        x.build();val t=x.ticket();val oldRevision=x.state().revision
        val rejected=x.service.decide(t,"Newer rejection",CandidateReviewChecks(false,false,false),false,true,oldRevision)
        val bytes=x.journal().readBytes()
        denied{x.service.decide(t,"Stale approval dialog",checks,true,true,oldRevision)}
        assertArrayEquals(bytes,x.journal().readBytes());assertEquals(rejected.history,x.state().history)
        assertNull(x.state().promoted);assertFalse(x.state().active)
        assertTrue(x.service.decide(t,"Fresh explicit approval",checks,true,true,rejected.revision).active)
    }
    @Test fun failedAtomicWritesPreservePriorDecisionAndRemoveUnjournaledPdf() {
        Fixture(WorkspaceMode.REGULAR).use {x->
            x.build();val t=x.ticket();x.service.decideLatest(t,"Reviewer",checks,true,true)
            val before=x.journal().readBytes();val pointer=x.state().promoted;val revision=x.state().revision
            val obstruction=File(x.journal().path+".new");assertTrue(obstruction.mkdirs());File(obstruction,"block").writeText("prevent replacement")
            denied{x.service.decide(t,"Rejected during failed write",checks,false,true,revision)}
            obstruction.deleteRecursively()
            assertArrayEquals(before,x.journal().readBytes());assertEquals(pointer,x.state().promoted);assertTrue(x.state().active)
        }
        Fixture(WorkspaceMode.REGULAR).use {x->
            val obstruction=File(x.journal().path+".new");assertTrue(obstruction.mkdirs());File(obstruction,"block").writeText("prevent journal creation")
            assertNotNull(x.service.build(x.id,x.mode,false).error)
            assertFalse(x.journal().exists());assertTrue(File(x.journal().parentFile,"pdf").listFiles().isNullOrEmpty())
            obstruction.deleteRecursively()
            assertNotNull(x.service.capture(x.id,x.mode));assertEquals(1,x.state().candidates.size)
        }
    }
    @Test fun interruptedAtomicJournalRecoversLastCommittedApproval() {
        Fixture(WorkspaceMode.REGULAR).use {x->
            x.build();val t=x.ticket();val approved=x.service.decideLatest(t,"Reviewer",checks,true,true)
            val committed=x.journal().readBytes()
            File(x.journal().path+".bak").writeBytes(committed);x.journal().writeText("interrupted bad write")
            val restored=x.state();assertEquals(approved.history,restored.history);assertEquals(approved.promoted,restored.promoted);assertTrue(restored.active)
            assertArrayEquals(committed,x.journal().readBytes());assertFalse(File(x.journal().path+".bak").exists())
            File(x.journal().path+".new").writeText("uncommitted new write")
            assertEquals(approved.history,x.state().history);assertArrayEquals(committed,x.journal().readBytes())
            assertEquals(t.manifest.packetPdfSha256,hash(x.service.readCurrentPdf(x.id,x.mode)))
        }
    }
    private fun persistedHashes(root:File):Map<String,String> = root.walkTopDown().filter {it.isFile}.associate {it.relativeTo(root).path to hash(it.readBytes())}.toSortedMap()
    private fun restartRoot()=File(ApplicationProvider.getApplicationContext<TerritoryCardStudioApplication>().filesDir,"phase4-restart")
    @Test fun stageCompleteLifecycleBeforeProcessDeath() {
        val root=restartRoot();root.deleteRecursively();assertTrue(root.mkdirs())
        val meta=JSONObject().put("pid",android.os.Process.myPid())
        for(mode in WorkspaceMode.values())Fixture(mode).use {x->
            x.build()
            // Persistent lifecycle storage is separate from fixture caches and survives cleanup.
            val dir=File(root,mode.name);assertTrue(dir.mkdirs())
            val service=AndroidCandidateLifecycleService(x.f.kb,x.f.coordinator,x.f.sources,File(dir,"lifecycle"))
            val ticket=requireNotNull(service.state(x.id,mode).ticket)
            val approved=service.decideLatest(ticket,"Process restart reviewer",checks,true,true);assertTrue(approved.active)
            assertEquals(ticket.manifest.packetPdfSha256,hash(service.readCurrentPdf(x.id,mode)))
            assertTrue(x.f.root.copyRecursively(File(dir,"runtime")))
            val sourceDir=File(x.f.app.noBackupFilesDir,"territory-card-studio/source-intake-v1/${x.id}")
            assertTrue(sourceDir.copyRecursively(File(dir,"source")))
            val sourceRaw=requireNotNull(x.f.app.getSharedPreferences("territory-card-studio-source-intake-v1",0).getString("source_map_${x.id}",null))
            val entry=JSONObject().put("id",x.id).put("manifest",ticket.manifest.canonicalSha256())
                .put("sourceRaw",sourceRaw).put("runtime",JSONObject(persistedHashes(x.f.root)))
                .put("sourceFiles",JSONObject(persistedHashes(sourceDir))).put("lifecycle",JSONObject(persistedHashes(File(dir,"lifecycle"))))
                .put("historyCount",approved.history.size).put("pdfSha",ticket.manifest.packetPdfSha256)
            meta.put(mode.name,entry)
        }
        File(root,"state.json").writeText(meta.toString())
    }
    @Test fun newProcessKeepsPersistedApprovalSuspendedWithoutRestoringAuthority() {
        val root=restartRoot();val meta=JSONObject(File(root,"state.json").readText());val pid=android.os.Process.myPid()
        assertNotEquals("The consumer must run after a real app process death",meta.getInt("pid"),pid)
        for(mode in WorkspaceMode.values())Fixture(mode).use {x->
            val dir=File(root,mode.name);val expected=meta.getJSONObject(mode.name)
            fun matches(key:String,folder:File) {
                val j=expected.getJSONObject(key);assertEquals(j.keys().asSequence().associateWith {j.getString(it)},persistedHashes(folder))
            }
            // Restore every persisted byte after reconstructing only immutable fixture configuration.
            x.f.root.deleteRecursively();assertTrue(File(dir,"runtime").copyRecursively(x.f.root));matches("runtime",x.f.root)
            val sourceDir=File(x.f.app.noBackupFilesDir,"territory-card-studio/source-intake-v1/${x.id}")
            sourceDir.deleteRecursively();assertTrue(File(dir,"source").copyRecursively(sourceDir));matches("sourceFiles",sourceDir)
            val preferences=x.f.app.getSharedPreferences("territory-card-studio-source-intake-v1",0)
            assertTrue(preferences.edit().putString("source_map_${x.id}",expected.getString("sourceRaw")).commit())
            val sources=SourceMapIntakeStore(x.f.app)
            assertEquals(JSONObject(expected.getString("sourceRaw")).getString("sha256"),sources.verifiedRecord(x.id)!!.sha256)
            val lifecycleDir=File(dir,"lifecycle");matches("lifecycle",lifecycleDir)
            val pdf=AndroidPdfArtifactService(x.f.kb,File(x.f.root,"fresh-pdf"),x.f.app.assets.open("territory/render-authority/Canonical-New-Designed-Template-R48.pdf").use {it.readBytes()})
            val coordinator=AndroidBuildWorkflowCoordinator(x.f.kb,AndroidRenderModelService(x.f.kb),pdf,sources)
            val service=AndroidCandidateLifecycleService(x.f.kb,coordinator,sources,lifecycleDir)
            val state=service.state(x.id,mode);assertFalse(state.active);assertNull(state.ticket);assertNotNull(state.blocker)
            assertEquals(expected.getInt("historyCount"),state.history.size)
            val promoted=requireNotNull(state.promoted);assertEquals("APPROVED",promoted.status)
            assertEquals(expected.getString("manifest"),promoted.ticket.manifest.canonicalSha256())
            assertEquals(expected.getString("pdfSha"),promoted.ticket.manifest.packetPdfSha256)
            denied{service.readCurrentPdf(x.id,mode)};denied{service.decideLatest(promoted.ticket,"Restart reviewer",checks,true,true)}
            matches("lifecycle",lifecycleDir)
        }
        File(ApplicationProvider.getApplicationContext<TerritoryCardStudioApplication>().filesDir,"phase4-restart-proof.json")
            .writeText(JSONObject().put("producerPid",meta.getInt("pid")).put("consumerPid",pid).put("passed",true).put("modes",3).toString())
        root.deleteRecursively()
    }

}

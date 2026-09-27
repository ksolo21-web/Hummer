package com.koenterprises.territorycardstudio

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.koenterprises.territorycardstudio.core.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.*

@RunWith(AndroidJUnit4::class)
class Phase3DIntegrationInstrumentationTest {
    private val checks=CandidateReviewChecks(true,true,true)
    private fun denied(action:()->Unit) {assertTrue("Unsafe operation accepted",runCatching(action).isFailure)}
    private fun review(x:Phase3CCFixture,root:File=File(x.f.root,"3d-reviews"))=AndroidCandidateReviewService(x.coordinator,root)
    private fun build(x:Phase3CCFixture) {
        x.seed();x.importAll();x.preparation.prepare(x.validate())
        assertNull(x.coordinator.buildFront(x.id,x.mode).error)
        assertNull(x.coordinator.generatePage2(x.id,x.mode).error)
    }
    internal class Destination:CreatedExportDestination {
        val bytes=ByteArrayOutputStream();var opens=0;var removed=false
        override fun openOutput():OutputStream {opens++;return bytes}
        override fun openInput():InputStream=bytes.toByteArray().inputStream()
        override fun deleteCreated():Boolean {removed=true;return true}
    }
    @Test fun editedBothModesReachExactPreviewAndExplicitReview() {
        for(phone in listOf(false,true))Phase3CCFixture(phone).use {x->
            val original=x.kb;build(x);val s=review(x);val t=requireNotNull(s.state(x.id,x.mode).ticket)
            assertNull(s.state(x.id,x.mode).decision)
            assertEquals(x.coordinator.editingBaseline(x.id,x.mode).first.canonicalSha256(),t.preparedInputSha256)
            assertEquals(x.coordinator.state(x.id,x.mode).packet!!.sha256,t.manifest.packetPdfSha256)
            assertEquals(2,t.manifest.pageCount);assertEquals(x.coordinator.currentCandidateVersion(x.id,x.mode),t.manifest.candidateVersion)
            val preview=AndroidPdfPreviewService(x.coordinator,File(x.f.root,"preview"))
            val doc=preview.open(x.id,x.mode,PdfPreviewKind.PACKET);assertEquals(t.manifest.packetPdfSha256,doc.sha256)
            for(page in 0..1) {val b=preview.render(doc,page,1536);try {File(x.f.app.filesDir,"phase3d-${if(phone)"telephone" else "letter"}-page${page+1}.png").outputStream().use {assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))}}finally{b.recycle()}}
            denied{s.record(t,"Integration reviewer",checks,true,false)};assertNull(s.state(x.id,x.mode).decision)
            val receipt=s.record(t,"Integration reviewer",checks,true,true)
            assertEquals(ExactApprovalState.EXPLICITLY_APPROVED,receipt.approvalState)
            assertTrue(CrossModePacketValidator.validateExactApproval(t.manifest,receipt).isEmpty())
            assertEquals(receipt,review(x).state(x.id,x.mode).decision);assertEquals(original,x.kb)
        }
    }
    @Test fun everyPostDecisionInvalidationRejectsOldTicketAndArtifacts() {
        for(phone in listOf(false,true))for(mutation in 0..4)Phase3CCFixture(phone).use {x->
            build(x);val s=review(x);val t=requireNotNull(s.state(x.id,x.mode).ticket);s.record(t,"Reviewer",checks,true,true)
            val preview=AndroidPdfPreviewService(x.coordinator,File(x.f.root,"preview"));val doc=preview.open(x.id,x.mode,PdfPreviewKind.PACKET)
            when(mutation) {
                0->{val d=x.labels.read(x.id,x.mode)!!;x.labels.save(x.id,x.mode,d.latest.token,emptyList())}
                1->{val d=x.extended.read(x.id,x.mode)!!;x.extended.save(x.id,x.mode,d.latest.token,d.latest.proposals.dropLast(1))}
                2->x.authority.revoke(x.id,x.mode,x.authority.status(x.id,x.mode).receiptId!!)
                3->{x.now+=300001;assertTrue("Consumed ticket expiry must not alone expire authority",x.authority.status(x.id,x.mode).available);assertNotNull(s.state(x.id,x.mode).decision);x.now+=600000;assertFalse(x.authority.status(x.id,x.mode).available)}
                4->x.f.importSource("phase3d-replaced")
            }
            assertFalse(x.coordinator.state(x.id,x.mode).canBuild);assertNotNull(x.coordinator.buildFront(x.id,x.mode).error)
            assertNull(s.state(x.id,x.mode).ticket);assertNull(s.state(x.id,x.mode).decision)
            denied{s.record(t,"Reviewer",checks,true,true)}
            denied{preview.open(x.id,x.mode,PdfPreviewKind.PACKET)};denied{preview.render(doc,0,256).recycle()}
        }
    }
    @Test fun sameByteRebuildInvalidatesLocalDecisionInBothModes() {
        for(phone in listOf(false,true))Phase3CCFixture(phone).use {x->
            build(x);val s=review(x);val old=requireNotNull(s.state(x.id,x.mode).ticket);s.record(old,"Reviewer",checks,true,true)
            assertNull(x.coordinator.generatePage2(x.id,x.mode).error)
            val current=requireNotNull(s.state(x.id,x.mode).ticket)
            assertEquals(old.manifest.packetPdfSha256,current.manifest.packetPdfSha256)
            assertNotEquals(old.manifest.candidateVersion,current.manifest.candidateVersion);assertNull(s.state(x.id,x.mode).decision)
            denied{s.record(old,"Reviewer",checks,true,true)}
            assertNull(x.coordinator.buildFront(x.id,x.mode).error);assertNull(s.state(x.id,x.mode).ticket)
            assertNull(x.coordinator.generatePage2(x.id,x.mode).error);denied{s.record(current,"Reviewer",checks,true,true)}
        }
    }
    @Test fun editedLocalApprovalNeverCreatesExportAuthorityOrChangesReservedCards() {
        for(phone in listOf(false,true))Phase3CCFixture(phone).use {x->
            val before=x.f.app.services.knowledgeBase;val reserved=before.assignments.filterKeys {it in setOf("250T","257A","297","298A","299","347TA")};assertEquals(6,reserved.size)
            val roles=before.referenceRoles;build(x);val s=review(x);val t=requireNotNull(s.state(x.id,x.mode).ticket);s.record(t,"Reviewer",checks,true,true)
            val export=AndroidApprovedExportService(x.kb,x.pdf);assertFalse(export.state(x.id).canAttach);assertNull(export.state(x.id).ticket)
            denied{export.attach(x.id,x.coordinator.state(x.id,x.mode).packet!!.file.inputStream())}
            val forged=ApprovedExportTicket(x.id,t.manifest.canonicalFilename,t.manifest.packetPdfSha256,x.kb.revision,x.coordinator.state(x.id,x.mode).packet!!.file.length().toInt())
            val d=Destination();denied{export.exportCreated(forged,d)};assertEquals(0,d.opens);assertEquals(0,d.bytes.size());assertTrue(d.removed)
            assertEquals(reserved,x.f.app.services.knowledgeBase.assignments.filterKeys {it in reserved.keys});assertEquals(roles,x.f.app.services.knowledgeBase.referenceRoles)
            for(id in reserved.keys){assertNull(x.f.app.services.approvedExport.state(id).ticket);assertFalse(x.f.app.services.approvedExport.state(id).canAttach)}
        }
    }
    private fun hashes(root:File):Map<String,String> = root.walkTopDown().filter {it.isFile}.associate {it.relativeTo(root).path to BundleIntegrity.sha256(it.inputStream())}.toSortedMap()
    private fun restartRoot()=File(ApplicationProvider.getApplicationContext<TerritoryCardStudioApplication>().filesDir,"phase3d-restart")
    @Test fun stageEditedDecisionsAndApprovedReferenceBeforeProcessDeath() {
        val root=restartRoot();root.deleteRecursively();assertTrue(root.mkdirs());val meta=JSONObject().put("pid",android.os.Process.myPid())
        for(phone in listOf(false,true))Phase3CCFixture(phone).use {x->build(x);val dir=File(root,if(phone)"phone" else "letter");dir.mkdirs()
            val s=review(x,File(dir,"reviews"));val t=requireNotNull(s.state(x.id,x.mode).ticket);s.record(t,"Restart reviewer",checks,true,true)
            val packet=x.coordinator.state(x.id,x.mode).packet!!;packet.file.copyTo(File(dir,"candidate.pdf"));meta.put(x.id,packet.sha256)
            assertTrue(File(dir,"reviews").listFiles()!!.isNotEmpty())
            assertTrue(x.f.root.copyRecursively(File(dir,"runtime")))
            val sourceDir=File(x.f.app.noBackupFilesDir,"territory-card-studio/source-intake-v1/${x.id}")
            assertTrue(sourceDir.copyRecursively(File(dir,"source")))
            val sourceRaw=requireNotNull(x.f.app.getSharedPreferences("territory-card-studio-source-intake-v1",0).getString("source_map_${x.id}",null))
            meta.put("stores-${x.id}",JSONObject(hashes(x.f.root))).put("source-${x.id}",sourceRaw).put("sourceFiles-${x.id}",JSONObject(hashes(sourceDir)))
        }
        Phase2GExportFixture().use {f->
            val artifacts=AndroidPdfArtifactService(f.kb,File(root,"approved"),f.source.app.assets.open("territory/render-authority/Canonical-New-Designed-Template-R48.pdf").use {it.readBytes()})
            val service=AndroidApprovedExportService(f.kb,artifacts);val t=requireNotNull(service.attach(f.id,f.bytes.inputStream()).ticket)
            meta.put("approvedSha",t.sha256);meta.put("approvedBytes",t.byteCount)
        }
        File(root,"state.json").writeText(meta.toString())
    }
    @Test fun processRestartKeepsEditedSessionsBlockedAndApprovedReferenceExportWorking() {
        val root=restartRoot();val meta=JSONObject(File(root,"state.json").readText())
        assertNotEquals("Must be a real new Android process",meta.getInt("pid"),android.os.Process.myPid())
        for(phone in listOf(false,true))Phase3CCFixture(phone).use {x->
            val dir=File(root,if(phone)"phone" else "letter");assertEquals(meta.getString(x.id),BundleIntegrity.sha256(File(dir,"candidate.pdf").inputStream()))
            val decisions=File(dir,"reviews").listFiles()!!.associate {it.name to it.readText()};assertTrue(decisions.isNotEmpty())
            // Recreate immutable fixture configuration, then restore EVERY persisted runtime byte
            // before constructing any fresh services. This is not an empty-store restart check.
            x.f.root.deleteRecursively();assertTrue(File(dir,"runtime").copyRecursively(x.f.root))
            val expected=meta.getJSONObject("stores-${x.id}");assertEquals(expected.keys().asSequence().associateWith {expected.getString(it)},hashes(x.f.root))
            val sourceDir=File(x.f.app.noBackupFilesDir,"territory-card-studio/source-intake-v1/${x.id}");sourceDir.deleteRecursively();assertTrue(File(dir,"source").copyRecursively(sourceDir))
            val sourceExpected=meta.getJSONObject("sourceFiles-${x.id}");assertEquals(sourceExpected.keys().asSequence().associateWith {sourceExpected.getString(it)},hashes(sourceDir))
            val preferences=x.f.app.getSharedPreferences("territory-card-studio-source-intake-v1",0)
            assertTrue(preferences.edit().putString("source_map_${x.id}",meta.getString("source-${x.id}")).commit())
            val sources=SourceMapIntakeStore(x.f.app);assertEquals(JSONObject(meta.getString("source-${x.id}")).getString("sha256"),sources.verifiedRecord(x.id)!!.sha256)
            val artifacts=AndroidPdfArtifactService(x.kb,File(x.f.root,"pdf"),x.f.app.assets.open("territory/render-authority/Canonical-New-Designed-Template-R48.pdf").use {it.readBytes()})
            val restarted=AndroidBuildWorkflowCoordinator(x.kb,x.models,artifacts,sources)
            val labels=AndroidEditingDraftStore(x.labelsRoot,x.kb,sources);assertTrue(labels.read(x.id,x.mode)!!.latest.edits.isNotEmpty())
            val extended=AndroidExtendedDraftStore(x.extendedRoot,x.kb,sources,restarted);assertTrue(extended.read(x.id,x.mode)!!.latest.proposals.isNotEmpty())
            val authority=AndroidEditingAuthorityStore(x.authorityRoot,x.kb,sources,restarted,{x.now})
            assertFalse(authority.status(x.id,x.mode).available)
            val preparation=AndroidExtendedPreparationService(x.kb,x.f.app.services.activePolicy,labels,extended,authority,sources,restarted,x.models,{error("Restart must require explicit authority intake before provider access")},{x.now})
            denied{preparation.validate(x.id,x.mode)}
            val review=AndroidCandidateReviewService(restarted,File(dir,"reviews"));assertNull(review.state(x.id,x.mode).ticket);assertNull(review.state(x.id,x.mode).decision)
            assertFalse(restarted.state(x.id,x.mode).canBuild);assertNotNull(restarted.buildFront(x.id,x.mode).error)
            val preview=AndroidPdfPreviewService(restarted,File(dir,"preview"));denied{preview.open(x.id,x.mode,PdfPreviewKind.PACKET)}
            assertNull(AndroidApprovedExportService(x.kb,x.pdf).state(x.id).ticket)
            assertEquals(decisions,File(dir,"reviews").listFiles()!!.associate {it.name to it.readText()})
        }
        Phase2GExportFixture().use {f->
            val artifacts=AndroidPdfArtifactService(f.kb,File(root,"approved"),f.source.app.assets.open("territory/render-authority/Canonical-New-Designed-Template-R48.pdf").use {it.readBytes()})
            val export=AndroidApprovedExportService(f.kb,artifacts);val t=requireNotNull(export.state(f.id).ticket)
            assertEquals(meta.getString("approvedSha"),t.sha256);val d=Destination();val receipt=export.exportCreated(t,d)
            assertEquals(meta.getString("approvedSha"),receipt.sha256);assertEquals(meta.getInt("approvedBytes"),d.bytes.size());assertArrayEquals(f.bytes,d.bytes.toByteArray())
        }
        File(root.parentFile,"phase3d-restart-proof.json").writeText(meta.put("newPid",android.os.Process.myPid()).put("passed",true).toString());root.deleteRecursively()
    }
}

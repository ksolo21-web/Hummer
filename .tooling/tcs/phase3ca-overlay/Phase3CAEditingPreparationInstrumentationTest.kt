package com.koenterprises.territorycardstudio

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.koenterprises.territorycardstudio.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class Phase3CAEditingPreparationInstrumentationTest {
    private class Fixture(telephone:Boolean=false):AutoCloseable {
        val f=Phase2DBFixture(telephone)
        val roads=f.input.assignment.roads
        val slot=f.slot.copy(roads=roads,roadCount=roads.size,namedRoadCount=roads.map{it.name}.distinct().size,
            roadNameInventory=roads.map{it.name},buildings=emptyList(),buildingCount=0)
        val kb=f.kb.copy(assignments=f.kb.assignments+(f.identity.displayId to slot))
        val drafts=AndroidEditingDraftStore(File(f.root,"drafts"),kb,f.sources)
        val models=AndroidRenderModelService(kb)
        val co=AndroidBuildWorkflowCoordinator(kb,models,f.service,f.sources)
        val id=f.identity.displayId
        val mode=f.mode
        var now=1000L
        var calls=0
        var onFetch:()->Unit={}
        var evidenceTransform:(List<ProviderVerificationEvidence>)->List<ProviderVerificationEvidence> = {it}
        val bridge=AndroidEditingPreparationService(kb,f.app.services.activePolicy,drafts,f.sources,co,models,
            {request -> calls++;onFetch()
                val targets=request.roadTargets.mapTo(linkedSetOf()){it.targetId}
                evidenceTransform(f.input.liveResult.evidence.map {it.copy(requestFingerprint=request.requestFingerprint,
                    queriedTargetIds=targets,confirmedTargetIds=targets,responseSha256=BundleIntegrity.sha256((it.providerId+request.requestFingerprint).byteInputStream()))})
            },{now})
        val edit=DraftLabelEdit(DraftLabelKind.ROAD,"adapter-alpha","Alpha Rd","Alpha Road","Independently reconciled source label",f.source.sha256)
        init {val d=drafts.create(id,mode);drafts.save(id,mode,d.latest.token,listOf(edit));co.prepare(id,mode,f.source.sha256,f.input,f.inventory)}
        fun draft()=drafts.read(id,mode)!!
        fun input():ProductionRenderModelInput {
            val roads=roads.map {if(it.segmentId==edit.itemId)it.copy(name=edit.proposed,normalizedName=TopologyOverlapDecisionEngine.normalizeRoadName(edit.proposed))else it}
            val state=f.input.assignment.copy(roads=roads)
            val topology=TopologyOverlapDecisionEngine.topologySignature(roads)
            val request=LiveGeometryVerificationRequestFactory.create(kb,f.app.services.activePolicy,id,state.authoritySha256,
                topology,emptyList(),f.input.sourceTruth,f.input.liveRequest.jurisdiction)
            val labels=f.input.labels.map {label ->
                if(label.segmentId!=edit.itemId)label else label.copy(text=edit.proposed,
                    placement=label.placement.copy(navigationLock=label.placement.navigationLock.copy(streetName=edit.proposed)))
            }
            return f.input.copy(assignment=state,topologySignature=topology,
                topologyValidation=TopologyOverlapDecisionEngine.validateIntersectionColorRoles(roads),
                overlapDecision=TopologyOverlapDecisionEngine.crossTerritoryOverlapCheck(state.identity,topology,kb),
                liveRequest=request,labels=labels)
        }
        fun validate(input:ProductionRenderModelInput=input(),inventory:Page2Inventory?=f.inventory)=bridge.validate(id,mode,draft().latest.token,input,inventory)
        fun changeDraft() {val d=draft();drafts.save(id,mode,d.latest.token,listOf(edit.copy(proposed="Alpha Avenue")))}
        override fun close()=f.close()
    }
    private fun fails(block:()->Unit) {assertTrue("Must reject",runCatching(block).isFailure)}
    @Test fun buildingMemberChangesStayBlockedUntilAuthorityAwareReconciliation() {
        Fixture().use {x->
            val building=BuildingGeometry("building-1","1","apartment",true,"",listOf("1"),
                listOf(BuildingLabelItem("1",Point2D(350.0,165.0),null,0.0,10.0)),
                listOf(Point2D(300.0,140.0),Point2D(400.0,140.0),Point2D(400.0,190.0),Point2D(300.0,190.0)))
            val kb=x.kb.copy(assignments=x.kb.assignments+(x.id to x.slot.copy(housingType="apartment",buildings=listOf(building),buildingCount=1)))
            val drafts=AndroidEditingDraftStore(File(x.f.root,"building-drafts"),kb,x.f.sources)
            val d=drafts.create(x.id,x.mode)
            val saved=drafts.save(x.id,x.mode,d.latest.token,listOf(DraftLabelEdit(DraftLabelKind.BUILDING,
                "building-1","1","2","Member change requires independent authority",x.f.source.sha256)))
            val bridge=AndroidEditingPreparationService(kb,x.f.app.services.activePolicy,drafts,x.f.sources,x.co,AndroidRenderModelService(kb),
                fetchEvidence={error("Blocked building proposal must not reach providers")})
            val previous=x.co.currentCandidateVersion(x.id,x.mode)
            val failure=runCatching {bridge.validate(x.id,x.mode,saved.latest.token,x.input(),x.f.inventory)}.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertTrue(failure!!.message!!.contains("authority-aware building"))
            assertEquals(previous,x.co.currentCandidateVersion(x.id,x.mode))
        }
    }
    @Test fun letterPreparationBuildAndPacketStayUnapproved() {
        Fixture().use {x->
            val before=x.co.state(x.id,x.mode)
            val t=x.validate()
            assertEquals(before,x.co.state(x.id,x.mode));assertEquals(1,x.calls)
            x.bridge.prepare(t);assertTrue(x.co.state(x.id,x.mode).canBuild)
            assertNotNull(x.co.buildFront(x.id,x.mode).front)
            assertNotNull(x.co.generatePage2(x.id,x.mode).packet)
            val snapshot=x.co.workspaceSnapshot(x.id,x.mode)!!
            assertEquals(t.inputSha256,snapshot.inputHash);assertEquals(t.inventorySha256,snapshot.inventory!!.hash)
            assertEquals("Alpha Rd",x.kb.assignments.getValue(x.id).roads.first().name)
            assertFalse(x.draft().grantsAuthority)
        }
    }
    @Test fun telephoneInventoryAndUnavailableNumberPreserved() {
        Fixture(true).use {x->
            val t=x.validate();x.bridge.prepare(t)
            assertEquals(1,x.co.workspaceSnapshot(x.id,x.mode)!!.inventory!!.unavailableNumbers)
            assertNotNull(x.co.buildFront(x.id,x.mode).front)
            assertNotNull(x.co.generatePage2(x.id,x.mode).packet)
        }
    }
    @Test fun freshEvidenceMandatoryAndOldSuppliedResultIgnored() {
        Fixture().use {x->
            val original=x.input()
            x.evidenceTransform={emptyList()};fails{x.validate(original)}
            assertEquals(1,x.calls);assertEquals(x.f.input.canonicalSha256(),x.co.workspaceSnapshot(x.id,x.mode)!!.inputHash)
            x.evidenceTransform={it.map{e->e.copy(requestFingerprint="f".repeat(64))}}
            fails{x.validate(original)};assertEquals(x.f.input.canonicalSha256(),x.co.workspaceSnapshot(x.id,x.mode)!!.inputHash)
        }
    }
    @Test fun mismatchedProposalOrUnboundAuthorityCannotValidate() {
        Fixture().use {x->
            fails{x.validate(x.f.input)}
            fails{x.validate(x.input().let{it.copy(assignment=it.assignment.copy(authoritySha256="a".repeat(64)))})}
            assertEquals(0,x.calls)
            assertEquals(x.f.input.canonicalSha256(),x.co.workspaceSnapshot(x.id,x.mode)!!.inputHash)
        }
    }
    @Test fun geometryAndAssignmentChangesCannotHideInsideLabelProposal() {
        Fixture().use {x->
            val input=x.input()
            listOf(input.assignment.roads.first().copy(status="green"),
                input.assignment.roads.first().copy(points=listOf(Point2D(1.0,2.0),Point2D(3.0,4.0)))).forEach {road->
                fails{x.validate(input.copy(assignment=input.assignment.copy(roads=listOf(road)+input.assignment.roads.drop(1))))}
            }
            fails{x.validate(input.copy(assignment=input.assignment.copy(locality="Changed")))}
            val wrong=input.assignment.roads.first().copy(normalizedName="unrelated")
            fails{x.validate(input.copy(assignment=input.assignment.copy(roads=listOf(wrong)+input.assignment.roads.drop(1))))}
            val label=input.labels.last()
            fails{x.validate(input.copy(labels=input.labels.dropLast(1)+label.copy(text="Undeclared label")))}
            fails{x.validate(input.copy(buildingValidation=input.buildingValidation.copy(applicable=true)))}
            assertEquals(0,x.calls)
        }
    }
    @Test fun requestTargetsAndLabelsMustMatchEditedInput() {
        Fixture().use {x->
            fails{x.validate(x.input().copy(liveRequest=x.f.input.liveRequest))}
            val input=x.input()
            val foreign=LiveGeometryVerificationRequestFactory.create(x.kb,x.f.app.services.activePolicy,x.id,input.assignment.authoritySha256,
                input.topologySignature,emptyList(),input.sourceTruth,VerificationJurisdiction("Other County","Other","United States"))
            fails{x.validate(input.copy(liveRequest=foreign))}
            fails{x.validate(input.copy(labels=x.f.input.labels))}
            assertEquals(x.f.input.canonicalSha256(),x.co.workspaceSnapshot(x.id,x.mode)!!.inputHash)
        }
    }
    @Test fun sourceAndDraftChangesDuringVerificationRejectWithoutReplacement() {
        Fixture().use {x->
            x.co.prepare(x.id,x.mode,x.f.source.sha256,x.f.input,x.f.inventory)
            val previous=x.co.currentCandidateVersion(x.id,x.mode)
            x.onFetch={x.changeDraft()};fails{x.validate()}
            assertEquals(previous,x.co.currentCandidateVersion(x.id,x.mode))
            x.onFetch={x.f.importSource("changed")}
            val changed=x.draft();x.drafts.save(x.id,x.mode,changed.latest.token,listOf(x.edit))
            fails{x.validate()}
            assertFalse(x.co.state(x.id,x.mode).inputReady)
        }
    }
    @Test fun staleTicketAndCandidateReplacementRejectAtomically() {
        Fixture().use {x->
            val t=x.validate()
            x.co.prepare(x.id,x.mode,x.f.source.sha256,x.f.input,x.f.inventory)
            val previous=x.co.currentCandidateVersion(x.id,x.mode)
            fails{x.bridge.prepare(t)}
            assertEquals(previous,x.co.currentCandidateVersion(x.id,x.mode))
            val next=x.validate();x.changeDraft();fails{x.bridge.prepare(next)}
            assertEquals(previous,x.co.currentCandidateVersion(x.id,x.mode))
        }
    }
    @Test fun preparedCandidateBecomesUnavailableAfterDraftChanges() {
        Fixture().use {x->
            x.bridge.prepare(x.validate());assertNotNull(x.co.buildFront(x.id,x.mode).front)
            x.changeDraft()
            assertFalse(x.co.state(x.id,x.mode).canBuild);assertNull(x.co.workspaceSnapshot(x.id,x.mode))
            fails{x.co.resolvePreview(x.id,x.mode,PdfPreviewKind.FRONT)}
            fails{x.co.resolveReview(x.id,x.mode)}
        }
    }
    @Test fun ticketIsOneUseBoundedCancellableAndExpires() {
        Fixture().use {x->
            val t=x.validate();fails{x.bridge.prepare(t.copy(inputSha256="0".repeat(64)))}
            x.bridge.cancel(t);fails{x.bridge.prepare(t)}
            val expires=x.validate();x.now+=300001;fails{x.bridge.prepare(expires)}
            val once=x.validate();x.bridge.prepare(once);fails{x.bridge.prepare(once)}
        }
    }
    @Test fun inventoryFailuresAndCrossModeCannotReplaceCurrentPreparation() {
        Fixture().use {x->
            x.co.prepare(x.id,x.mode,x.f.source.sha256,x.f.input,x.f.inventory)
            val previous=x.co.currentCandidateVersion(x.id,x.mode)
            val inv=(x.f.inventory as Page2Inventory.LetterWriting).inventory
            fails{x.validate(inventory=Page2Inventory.LetterWriting(inv.copy(records=inv.records+inv.records)))}
            fails{x.validate(inventory=Page2Inventory.LetterWriting(inv.copy(records=inv.records.map {it.copy(streetAddress="999 Undeclared Way")})))}
            fails{x.bridge.validate(x.id,WorkspaceMode.TELEPHONE,x.draft().latest.token,x.input(),x.f.inventory)}
            assertEquals(previous,x.co.currentCandidateVersion(x.id,x.mode))
        }
    }
    @Test fun mutablePayloadIsDetachedAndNullInventoryOnlyPermitsFront() {
        Fixture().use {x->
            val labels=x.input().labels.toMutableList()
            val t=x.validate(x.input().copy(labels=labels))
            labels.clear();x.bridge.prepare(t)
            assertEquals(t.inputSha256,x.co.workspaceSnapshot(x.id,x.mode)!!.inputHash)
            x.bridge.prepare(x.validate(inventory=null));assertTrue(x.co.state(x.id,x.mode).canBuild)
            assertFalse(x.co.state(x.id,x.mode).canGeneratePage2)
        }
    }
}

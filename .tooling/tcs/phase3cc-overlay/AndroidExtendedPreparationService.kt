package com.koenterprises.territorycardstudio

import android.os.SystemClock
import com.koenterprises.territorycardstudio.core.*
import java.util.UUID

data class ExtendedPreparationTicket internal constructor(val id:String,val territory:String,val mode:WorkspaceMode,
    val inputSha256:String,val inventorySha256:String?,val authoritySourceCount:Int,val changeCount:Int,val providerIds:List<String>)

/** Reconciliation/validation never installs inputs; preparation is a separate explicit guarded transaction. */
class AndroidExtendedPreparationService internal constructor(private val kb:TerritoryKnowledgeBase,private val policy:OnlineSourcePolicy,
    private val labels:AndroidEditingDraftStore,private val extended:AndroidExtendedDraftStore,private val authority:AndroidEditingAuthorityStore,
    private val sources:SourceMapIntakeStore,private val coordinator:AndroidBuildWorkflowCoordinator,private val models:AndroidRenderModelService,
    private val fetchEvidence:(LiveGeometryVerificationRequest)->List<ProviderVerificationEvidence>,private val clock:()->Long={SystemClock.elapsedRealtime()}) {
    private data class Pending(val ticket:ExtendedPreparationTicket,val receipt:EditingAuthorityReceipt,val labelWitness:EditingJournalWitness,
        val extendedWitness:EditingJournalWitness,val input:ProductionRenderModelInput,val inventory:Page2Inventory?,val issued:Long,val expires:Long)
    private val pending=linkedMapOf<String,Pending>()
    private fun inventoryHash(v:Page2Inventory?):String?=when(v){is Page2Inventory.LetterWriting->v.inventory.canonicalSha256();is Page2Inventory.Telephone->v.inventory.canonicalSha256();null->null}
    private fun bindingCurrent(p:Pending)=authority.current(p.receipt) && p.labelWitness.current() && p.extendedWitness.current() &&
        sources.verifiedRecord(p.ticket.territory)==p.receipt.source
    fun ticketCurrent(t:ExtendedPreparationTicket):Boolean=synchronized(this) {
        val p=pending[t.id];p!=null && p.ticket==t && clock() in p.issued..p.expires && bindingCurrent(p) && coordinator.currentCandidateVersion(t.territory,t.mode)==p.receipt.baseVersion
    }
    @Synchronized fun validate(id:String,mode:WorkspaceMode):ExtendedPreparationTicket {
        pending.entries.removeAll {clock() !in it.value.issued..it.value.expires || !bindingCurrent(it.value)}
        require(pending.size<16) {"Cancel an unused validation first"}
        val receipt=authority.receipt(id,mode)
        val baseline=coordinator.editingBaseline(id,mode)
        require(baseline.second==receipt.baseVersion && baseline.first==receipt.baseline && baseline.third==receipt.inventory) {"Prepared baseline changed; import authority facts again"}
        val ld=labels.read(id,mode);val ed=extended.read(id,mode)
        val lw=labels.revisionWitness(id,mode);val ew=extended.revisionWitness(id,mode)
        require(labels.read(id,mode)==ld && extended.read(id,mode)==ed && lw.current() && ew.current()) {"Proposal journals changed while loading"}
        val (candidate,inventory)=EditingFactReconciliation.reconcile(receipt,ld,ed)
        val topology=TopologyOverlapDecisionEngine.topologySignature(candidate.assignment.roads)
        val color=TopologyOverlapDecisionEngine.validateIntersectionColorRoles(candidate.assignment.roads)
        val overlap=TopologyOverlapDecisionEngine.crossTerritoryOverlapCheck(candidate.assignment.identity,topology,kb)
        val multi=candidate.assignment.housingType in setOf("apartment","condo","townhome","mobile_home","manufactured_home")
        val buildings=BuildingValidationEngine.validateBuildings(candidate.assignment.buildings,applicable=multi)
        require(color.passed && overlap.passed && overlap.reviewCandidateCount==0 && buildings.passed) {"Edited topology, work sides, overlap or building validation blocked preparation"}
        val request=LiveGeometryVerificationRequestFactory.create(kb,policy,id,candidate.assignment.authoritySha256,topology,
            candidate.assignment.buildings.flatMap {it.sourceMembers}.distinct(),candidate.sourceTruth,receipt.baseline.liveRequest.jurisdiction)
        val evidence=EditingSnapshots.detach(fetchEvidence(EditingSnapshots.detach(request)))
        val decision=LiveGeometryVerificationReconciler.reconcile(request,policy,evidence)
        require(decision.renderable) {"Fresh independent geometry verification blocked preparation"}
        val fresh=EditingSnapshots.detach(candidate.copy(topologySignature=topology,topologyValidation=color,overlapDecision=overlap,buildingValidation=buildings,
            liveRequest=request,liveResult=LiveVerificationExecutionResult(request.requestFingerprint,evidence,decision,emptyList())))
        val adapted=models.adaptProduction(fresh)
        require(adapted is RenderModelAdaptationResult.Renderable) {(adapted as RenderModelAdaptationResult.Blocked).reasons.joinToString("; ")}
        val inventoryBuildings=when(inventory) {
            is Page2Inventory.LetterWriting->inventory.inventory.records.mapNotNull {it.buildingId?.takeIf(String::isNotBlank)}
            is Page2Inventory.Telephone->inventory.inventory.records.mapNotNull {it.buildingId?.takeIf(String::isNotBlank)}
            null->emptyList()
        }
        require(inventoryBuildings.all {id->fresh.assignment.buildings.any {it.buildingId==id && it.assigned}}) {"Inventory refers to an unknown or unassigned building"}
        coordinator.validateEditingInventory(id,mode,fresh,inventory)
        val ticket=ExtendedPreparationTicket(UUID.randomUUID().toString(),id,mode,fresh.canonicalSha256(),inventoryHash(inventory),receipt.hashes.size,
            ld?.latest?.edits.orEmpty().size+ed?.latest?.proposals.orEmpty().size,ExtendedValues.frozen(evidence.map {it.providerId}.distinct().sorted()))
        val now=clock();val p=Pending(ticket,receipt,lw,ew,fresh,EditingSnapshots.detach(inventory),now,now+300000)
        require(bindingCurrent(p) && coordinator.editingBaseline(id,mode).second==receipt.baseVersion) {"Source, authority, proposals or candidate changed during validation"}
        pending[ticket.id]=p;return ticket
    }
    @Synchronized fun prepare(ticket:ExtendedPreparationTicket) {
        val p=requireNotNull(pending[ticket.id]) {"Validation is unknown, consumed or cancelled"}
        require(p.ticket==ticket && clock() in p.issued..p.expires && bindingCurrent(p)) {"Validation changed or expired; validate again"}
        // The installed guard is lock-free with respect to both journal stores and this service.
        coordinator.prepareEditing(ticket.territory,ticket.mode,p.receipt.source.sha256,p.input,p.inventory,p.receipt.baseVersion) {bindingCurrent(p)}
        pending.remove(ticket.id)
    }
    @Synchronized fun cancel(ticket:ExtendedPreparationTicket) {require(pending[ticket.id]?.ticket==ticket);pending.remove(ticket.id)}
}

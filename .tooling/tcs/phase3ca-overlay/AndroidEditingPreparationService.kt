package com.koenterprises.territorycardstudio

import com.koenterprises.territorycardstudio.core.*
import java.util.UUID

/** Opaque session ticket. Validation permits explicit preparation, never field approval. */
data class EditingPreparationTicket internal constructor(
    val id:String,val territory:String,val mode:WorkspaceMode,val draftRevision:Int,
    val inputSha256:String,val inventorySha256:String?,val providerIds:List<String>
)

/**
 * Receives a complete independently reconciled input, not input manufactured from draft strings.
 * Fresh provider execution and the frozen production adapter remain mandatory.
 * This bounded gate supports existing label drafts. Extended edit journals are a separate dependency.
 */
class AndroidEditingPreparationService internal constructor(
    private val kb:TerritoryKnowledgeBase,private val policy:OnlineSourcePolicy,
    private val drafts:AndroidEditingDraftStore,private val sources:SourceMapIntakeStore,
    private val coordinator:AndroidBuildWorkflowCoordinator,private val models:AndroidRenderModelService,
    private val fetchEvidence:(LiveGeometryVerificationRequest)->List<ProviderVerificationEvidence>,
    private val clock:()->Long={System.currentTimeMillis()}
) {
    private data class Validation(val ticket:EditingPreparationTicket,val draftId:String,val token:String,
        val base:String,val source:SourceMapIntakeRecord,val input:ProductionRenderModelInput,
        val inventory:Page2Inventory?,val previousCandidate:String?,val expiresAt:Long)
    private val pending=linkedMapOf<String,Validation>()
    private fun inventoryHash(v:Page2Inventory?):String?=when(v) {
        is Page2Inventory.LetterWriting -> v.inventory.canonicalSha256()
        is Page2Inventory.Telephone -> v.inventory.canonicalSha256()
        null -> null
    }
    private fun current(v:Validation):Boolean=runCatching {
        val d=drafts.read(v.ticket.territory,v.ticket.mode)
        d!=null && !d.stale && d.draftId==v.draftId && d.latest.token==v.token && d.baseBinding==v.base &&
            sources.verifiedRecord(v.ticket.territory)==v.source &&
            v.input.canonicalSha256()==v.ticket.inputSha256 && inventoryHash(v.inventory)==v.ticket.inventorySha256
    }.getOrDefault(false)

    @Synchronized
    fun validate(id:String,mode:WorkspaceMode,expectedToken:String,
        reconciledInput:ProductionRenderModelInput,inventory:Page2Inventory?):EditingPreparationTicket {
        pending.entries.removeAll { it.value.expiresAt<clock() || !current(it.value) }
        require(pending.size<16) { "Too many pending validations; cancel an unused validation" }
        val d=requireNotNull(drafts.read(id,mode)) { "Save a draft first" }
        require(!d.stale && d.latest.token==expectedToken && d.latest.edits.isNotEmpty()) { "Draft changed, stale, or empty" }
        val slot=requireNotNull(kb.assignments[id])
        require(slot.needsNewCard) { "Approved artifacts remain locked" }
        val source=requireNotNull(sources.verifiedRecord(id))
        val inputHashBefore=reconciledInput.canonicalSha256();val inventoryHashBefore=inventoryHash(inventory)
        val input=EditingSnapshots.detach(reconciledInput)
        val detachedInventory=EditingSnapshots.detach(inventory)
        val baseline=coordinator.editingBaseline(id,mode)
        require(input.assignment.copy(roads=baseline.first.assignment.roads,buildings=baseline.first.assignment.buildings)==baseline.first.assignment &&
            input.sourceTruth==baseline.first.sourceTruth) { "Assignment metadata or authority differs from the independently verified baseline" }
        require(input.assignment.displayId==id && input.assignment.authoritySha256 in slot.sourceHashes) {
            "Independently reconciled input must retain this territory's locked assignment authority"
        }
        val roadEdits=d.latest.edits.filter {it.kind==DraftLabelKind.ROAD}.associateBy {it.itemId}
        val buildingEdits=d.latest.edits.filter {it.kind==DraftLabelKind.BUILDING}.associateBy {it.itemId}
        require(input.assignment.roads.map{it.segmentId}.toSet()==slot.roads.map{it.segmentId}.toSet()) { "Unexpected road additions/removals" }
        require(input.assignment.buildings.map{it.buildingId}.toSet()==slot.buildings.map{it.buildingId}.toSet()) { "Unexpected building additions/removals" }
        input.assignment.roads.forEach { road ->
            val base=slot.roads.single {it.segmentId==road.segmentId}
            require(road.normalizedName==TopologyOverlapDecisionEngine.normalizeRoadName(road.name)) { "Normalized road name does not match label" }
            require(road.name==(roadEdits[road.segmentId]?.proposed ?: base.name)) { "Road proposal does not match reconciled label" }
            require(road.copy(name=base.name,normalizedName=base.normalizedName)==base) { "Geometry/assignment edits need the extended editing gate" }
        }
        input.assignment.buildings.forEach { b ->
            val base=slot.buildings.single {it.buildingId==b.buildingId}
            require(b.label==(buildingEdits[b.buildingId]?.proposed ?: base.label) && b.copy(label=base.label)==base) {
                "Building proposal or geometry does not match this label draft"
            }
        }
        val request=LiveGeometryVerificationRequestFactory.create(kb,policy,id,input.assignment.authoritySha256,
            TopologyOverlapDecisionEngine.topologySignature(input.assignment.roads),
            input.assignment.buildings.flatMap {it.sourceMembers}.distinct(),input.sourceTruth,baseline.first.liveRequest.jurisdiction)
        require(request==input.liveRequest) { "Live request does not describe the exact edited assignment" }
        val previous=baseline.second
        // Ignore supplied decisions/results. Production dependency always executes with BYPASS.
        val evidence=EditingSnapshots.detach(fetchEvidence(EditingSnapshots.detach(request)))
        val decision=LiveGeometryVerificationReconciler.reconcile(request,policy,evidence)
        require(decision.renderable) { "Fresh independent provider verification blocked: $decision" }
        val fresh=input.copy(liveResult=LiveVerificationExecutionResult(request.requestFingerprint,evidence,decision,emptyList()))
        require(reconciledInput.canonicalSha256()==inputHashBefore && inventoryHash(inventory)==inventoryHashBefore) { "Input changed during validation" }
        val adapted=models.adaptProduction(fresh)
        require(adapted is RenderModelAdaptationResult.Renderable) {
            (adapted as RenderModelAdaptationResult.Blocked).reasons.joinToString("; ")
        }
        // Run the same coordinator inventory/identity gates without replacing the current session.
        coordinator.validateEditingInventory(id,mode,fresh,detachedInventory)
        val ticket=EditingPreparationTicket(UUID.randomUUID().toString(),id,mode,d.latest.number,
            fresh.canonicalSha256(),inventoryHashBefore,java.util.Collections.unmodifiableList(evidence.map {it.providerId}.distinct().sorted()))
        val value=Validation(ticket,d.draftId,d.latest.token,d.baseBinding,source,fresh,detachedInventory,previous,clock()+300000)
        require(current(value)) { "Draft/source/input changed during validation" }
        pending[ticket.id]=value
        return ticket
    }

    @Synchronized
    fun prepare(ticket:EditingPreparationTicket) {
        val value=requireNotNull(pending[ticket.id]) { "Validation is unknown, consumed, or cancelled" }
        require(value.ticket==ticket && clock()<=value.expiresAt && current(value)) { "Validation expired or changed; validate again" }
        coordinator.prepareEditing(ticket.territory,ticket.mode,value.source.sha256,value.input,value.inventory,value.previousCandidate) {current(value)}
        pending.remove(ticket.id)
    }
    @Synchronized
    fun cancel(ticket:EditingPreparationTicket) { require(pending[ticket.id]?.ticket==ticket) { "Validation ticket mismatch" };pending.remove(ticket.id) }
}

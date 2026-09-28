package com.koenterprises.territorycardstudio

import com.koenterprises.territorycardstudio.core.*

/** Builds the first input directly from the native draft; no preprepared baseline or external layout. */
class AndroidNativeAuthoringService internal constructor(private val kb:TerritoryKnowledgeBase,private val policy:OnlineSourcePolicy,
    private val drafts:AndroidNativeDraftStore,private val sources:SourceMapIntakeStore,private val coordinator:AndroidBuildWorkflowCoordinator,
    private val labels:NativeRoadLabelProducer,private val fetchEvidence:(LiveGeometryVerificationRequest)->List<ProviderVerificationEvidence>) {
    fun buildingLabel(text:String,center:Point2D)=labels.buildingLabel(text,center)
    fun buildingLabels(members:List<String>,polygon:List<Point2D>)=labels.buildingLabels(members,polygon)
    fun prepare(id:String,mode:WorkspaceMode,expectedDraftRevision:String):List<String> {
        val d=requireNotNull(drafts.read(id,mode)) {"Create and save a native draft first"}
        require(d.revisionSha256==expectedDraftRevision) {"Draft changed; review it again"}
        val registration=requireNotNull(drafts.ledger.active(id,mode.name)) {"Explicitly register the reconciled assignment first"}
        val a=registration.assignment
        val source=requireNotNull(sources.verifiedRecord(id));require(source.sha256==registration.reconciliation.importedSourceSha256)
        val inv=drafts.inventory(d,a.authoritySha256)
        val assessment=NativeSourceReconciliationContract.assess(kb,registration.reconciliation,a,mode.name,source.sha256,drafts.inventoryContentSha256(d))
        require(assessment.passed) {assessment.failures.joinToString("; ")}
        val prior=coordinator.currentCandidateVersion(id,mode)
        val topology=TopologyOverlapDecisionEngine.topologySignature(a.roads)
        val color=TopologyOverlapDecisionEngine.validateIntersectionColorRoles(a.roads)
        val overlap=TopologyOverlapDecisionEngine.crossTerritoryOverlapCheck(a.identity,topology,kb)
        val buildings=BuildingValidationEngine.validateBuildings(a.buildings,a.housingType in setOf("apartment","condo","townhome","mobile_home","manufactured_home"))
        require(color.passed && overlap.passed && overlap.reviewCandidateCount==0 && buildings.passed) {"Resolve topology, duplicate work or building findings before preparation"}
        val request=LiveGeometryVerificationRequestFactory.create(kb,policy,id,a.authoritySha256,topology,a.buildings.flatMap {it.sourceMembers}.distinct(),assessment.truth,d.jurisdiction,nativeEligibility=drafts.ledger)
        val evidence=EditingSnapshots.detach(fetchEvidence(EditingSnapshots.detach(request)))
        val decision=LiveGeometryVerificationReconciler.reconcile(request,policy,evidence)
        require(decision.renderable) {"Fresh independent geometry verification did not pass"}
        val input=ProductionRenderModelInput(a,assessment.truth,request,LiveVerificationExecutionResult(request.requestFingerprint,evidence,decision,emptyList()),topology,color,overlap,buildings,labels.labels(a))
        val adapted=ProductionRenderModelAdapter.adaptProduction(kb,input,drafts.ledger)
        require(adapted is RenderModelAdaptationResult.Renderable) {(adapted as RenderModelAdaptationResult.Blocked).reasons.joinToString("; ")}
        val inventoryBuildings=when(inv) {
            is Page2Inventory.LetterWriting->inv.inventory.records.mapNotNull {it.buildingId?.takeIf(String::isNotBlank)}
            is Page2Inventory.Telephone->inv.inventory.records.mapNotNull {it.buildingId?.takeIf(String::isNotBlank)}
            null->emptyList()
        }
        require(inventoryBuildings.all {building->a.buildings.any {it.buildingId==building && it.assigned}}) {"Inventory refers to an unknown or unassigned building"}
        coordinator.validateEditingInventory(id,mode,input,inv)
        coordinator.prepareEditing(id,mode,source.sha256,input,inv,prior) {
            runCatching {drafts.read(id,mode)?.revisionSha256==d.revisionSha256 && sources.verifiedRecord(id)==source && drafts.ledger.current(id,a.authoritySha256)!=null}.getOrDefault(false)
        }
        return evidence.map {it.providerId}.distinct().sorted()
    }
}

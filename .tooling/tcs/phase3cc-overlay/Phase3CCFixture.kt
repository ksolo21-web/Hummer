package com.koenterprises.territorycardstudio

import com.koenterprises.territorycardstudio.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal class Phase3CCFixture(val phone:Boolean=false,val unknownPhone:Boolean=false,val phoneChange:Boolean=true,val omitPhoneFact:Boolean=false,assignmentDocument:(ByteArray)->ByteArray={it},inventoryTransform:(Page2Inventory)->Page2Inventory={it}):AutoCloseable {
    val f=Phase2DBFixture(phone);val id=f.identity.displayId;val mode=f.mode;val stamp=f.stamp
    val building=BuildingGeometry("building-1","1","apartment",true,"",listOf("1"),listOf(BuildingLabelItem("1",Point2D(350.0,165.0),null,0.0,10.0)),listOf(Point2D(300.0,140.0),Point2D(400.0,140.0),Point2D(400.0,190.0),Point2D(300.0,190.0)))
    fun fact(kind:String,item:String,value:JSONObject,verification:JSONObject=JSONObject())=JSONObject().put("kind",kind).put("item",item).put("value",value).put("verification",verification)
    fun document(facts:List<JSONObject>)=ExtendedValues.canonical(JSONObject().put("schema",1).put("territory",id).put("mode",mode.name).put("kb",f.kb.revision).put("facts",JSONArray(facts))).toByteArray()
    val assignmentFacts=listOf(
        fact("ROAD_LABEL","adapter-alpha",JSONObject().put("text","Alpha Road")),
        fact("ROAD_PATH","adapter-alpha",ExtendedValues.json(PathValue(f.input.assignment.roads.first().points.mapIndexed {i,p->if(i==0)p.copy(x=227.0)else p}))),
        fact("ROAD_WORK","adapter-alpha",ExtendedValues.json(WorkValue("yellow","perimeter","right",false))),
        fact("BUILDING_PATH","building-1",ExtendedValues.json(PathValue(building.polygon.mapIndexed {i,p->if(i==0)p.copy(x=302.0)else p}))),
        fact("BUILDING_MEMBERS","building-1",ExtendedValues.json(BuildingValue(true,"2",listOf("2"),building.labelItems.map {it.copy(text="2")}))))
    val assignmentBytes=assignmentDocument(document(assignmentFacts));val assignmentHash=BundleIntegrity.sha256(assignmentBytes.inputStream())
    private val baseBoundary=when(val inv=f.inventory){is Page2Inventory.LetterWriting->inv.inventory.records.first().boundaryEvidenceSha256;is Page2Inventory.Telephone->inv.inventory.records.first().boundaryEvidenceSha256}
    val inventoryFacts=buildList {
        add(fact(if(phone)"PHONE_ADDRESS" else "LETTER_ADDRESS",if(phone)"phone-1" else "address-1",ExtendedValues.json(AddressValue("102 Verified Example Way","","","","","building-1")),
            JSONObject().put("status","VERIFIED").put("verifiedAt",stamp).put("boundary","INSIDE_LOCKED_WORKING_AREA").put("boundarySha",baseBoundary).put("conflicts",JSONArray())))
        if(phone && !omitPhoneFact)add(fact("PHONE_NUMBER","phone-1",ExtendedValues.json(PhoneValue(if(unknownPhone)ProposedPhoneState.UNKNOWN else ProposedPhoneState.NUMBER,if(unknownPhone)"" else if(phoneChange)"2485550111" else "2025550101")),
            JSONObject().put("state",if(unknownPhone)"NEEDS_REVIEW" else "VERIFIED_NUMBER").put("verifiedAt",if(unknownPhone)"" else stamp).put("bindingVerified",!unknownPhone)))
    }
    val inventoryBytes=document(inventoryFacts);val inventoryHash=BundleIntegrity.sha256(inventoryBytes.inputStream())
    val slot=f.slot.copy(sourceHashes=listOf(assignmentHash)+f.slot.sourceHashes,housingType="apartment",roads=f.input.assignment.roads,roadCount=3,namedRoadCount=3,roadNameInventory=f.input.assignment.roads.map {it.name},buildings=listOf(building),buildingCount=1)
    val kb=f.kb.copy(assignments=f.kb.assignments+(id to slot))
    val models=AndroidRenderModelService(kb)
    val pdf=AndroidPdfArtifactService(kb,File(f.root,"pdf"),f.app.assets.open("territory/render-authority/Canonical-New-Designed-Template-R48.pdf").use {it.readBytes()})
    val coordinator=AndroidBuildWorkflowCoordinator(kb,models,pdf,f.sources)
    private val state=f.input.assignment.copy(authoritySha256=assignmentHash,housingType="apartment",buildings=listOf(building))
    private val truth=f.input.sourceTruth.copy(assignmentAuthoritySourceSha256=assignmentHash)
    private val request=LiveGeometryVerificationRequestFactory.create(kb,f.app.services.activePolicy,id,assignmentHash,f.input.topologySignature,listOf("1"),truth,f.input.liveRequest.jurisdiction)
    fun evidence(request:LiveGeometryVerificationRequest):List<ProviderVerificationEvidence> {
        val targets=request.roadTargets.map {it.targetId}.toSet()
        return f.input.liveResult.evidence.map {it.copy(requestFingerprint=request.requestFingerprint,queriedTargetIds=targets,confirmedTargetIds=targets,responseSha256=BundleIntegrity.sha256((it.providerId+request.requestFingerprint).byteInputStream()))} + listOf("oakland_county_site_addresses","oakland_county_buildings").map {provider->
            f.input.liveResult.evidence.first().copy(providerId=provider,requestFingerprint=request.requestFingerprint,
                responseSha256=BundleIntegrity.sha256((provider+request.requestFingerprint).byteInputStream()),queriedTargetIds=emptySet(),confirmedTargetIds=emptySet(),
                sourceDataVintage=if(provider.endsWith("buildings"))"2026" else null,
                siteAddressCrosscheckPassed=if(provider.endsWith("site_addresses"))true else null,buildingOutlineCrosscheckPassed=if(provider.endsWith("buildings"))true else null)
        }
    }
    val input=f.input.copy(assignment=state,sourceTruth=truth,liveRequest=request,
        liveResult=LiveVerificationExecutionResult(request.requestFingerprint,evidence(request),LiveGeometryVerificationReconciler.reconcile(request,f.app.services.activePolicy,evidence(request)),emptyList()),
        buildingValidation=BuildingValidationEngine.validateBuildings(listOf(building),applicable=true),
        labels=f.input.labels.map {it.copy(placement=it.placement.copy(navigationLock=it.placement.navigationLock.copy(sourceSha256=assignmentHash)))})
    val inventory=inventoryTransform(when(val old=f.inventory){
        is Page2Inventory.LetterWriting->Page2Inventory.LetterWriting(old.inventory.copy(assignmentAuthoritySha256=assignmentHash,provenance=old.inventory.provenance.map {it.copy(sourceSha256=inventoryHash)}))
        is Page2Inventory.Telephone->Page2Inventory.Telephone(old.inventory.copy(assignmentAuthoritySha256=assignmentHash,provenance=old.inventory.provenance.map {it.copy(sourceSha256=inventoryHash)}))
    })
    val labelsRoot=File(f.root,"labels");val extendedRoot=File(f.root,"extended");val authorityRoot=File(f.root,"authority")
    val labels=AndroidEditingDraftStore(labelsRoot,kb,f.sources)
    val extended=AndroidExtendedDraftStore(extendedRoot,kb,f.sources,coordinator)
    var now=1000L;var calls=0;var duringFetch:()->Unit={};var evidenceTransform:(List<ProviderVerificationEvidence>)->List<ProviderVerificationEvidence>={it}
    val authority=AndroidEditingAuthorityStore(authorityRoot,kb,f.sources,coordinator,{now})
    val preparation=AndroidExtendedPreparationService(kb,f.app.services.activePolicy,labels,extended,authority,f.sources,coordinator,models,{q->calls++;duringFetch();evidenceTransform(evidence(q))},{now})
    init {coordinator.prepare(id,mode,f.source.sha256,input,inventory)}
    fun seedLabels() {val d=labels.create(id,mode);labels.save(id,mode,d.latest.token,listOf(DraftLabelEdit(DraftLabelKind.ROAD,"adapter-alpha","Alpha Rd","Alpha Road","Independent source correction",assignmentHash)))}
    fun seedExtended() {val c=extended.catalog(id,mode);val d=extended.create(id,mode,c.binding)
        val all=assignmentFacts.drop(1)+inventoryFacts.filterNot {it.getString("kind")=="PHONE_NUMBER" && !phoneChange}
        extended.save(id,mode,d.latest.token,all.map {f->val kind=ExtendedKind.valueOf(f.getString("kind"));val item=c.items.single {it.kind==kind && it.id==f.getString("item")};val e=item.evidence.first {it.sha256==if(kind in setOf(ExtendedKind.LETTER_ADDRESS,ExtendedKind.PHONE_ADDRESS,ExtendedKind.PHONE_NUMBER))inventoryHash else assignmentHash}
            ExtendedProposal(kind,item.id,item.before,ExtendedValues.parse(kind,f.getJSONObject("value")),"Compare independently authored source facts",e.role,e.sha256)})
    }
    fun seed() {seedLabels();seedExtended()}
    fun importAll() {authority.importFacts(id,mode,assignmentBytes);authority.importFacts(id,mode,inventoryBytes)}
    fun validate()=preparation.validate(id,mode)
    override fun close()=f.close()
}

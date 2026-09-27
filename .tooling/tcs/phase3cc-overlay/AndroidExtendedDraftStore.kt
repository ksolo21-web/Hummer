package com.koenterprises.territorycardstudio

import android.util.AtomicFile
import com.koenterprises.territorycardstudio.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID

data class ExtendedCatalog(val binding:String,val items:List<ExtendedItem>,val inventoryAvailable:Boolean)
data class ExtendedRevision(val number:Int,val token:String,val action:String,val atUtc:String,val restoredFrom:Int?,val proposals:List<ExtendedProposal>)
data class ExtendedDraft(val id:String,val territory:String,val mode:WorkspaceMode,val binding:String,val revisions:List<ExtendedRevision>,val stale:Boolean) {
    val latest get()=revisions.last()
    val grantsAuthority:Boolean get()=false
}
data class ExtendedEditorState(val catalog:ExtendedCatalog?,val draft:ExtendedDraft?,val catalogError:String?)

/** Durable proposals only. Evidence references never grant assignment, inventory or field authority. */
class AndroidExtendedDraftStore internal constructor(private val root:File,private val kb:TerritoryKnowledgeBase,
    private val sources:SourceMapIntakeStore,private val coordinator:AndroidBuildWorkflowCoordinator) {
    companion object {
        const val MAX_REVISIONS=64
        const val MAX_PROPOSALS=128
        const val MAX_BYTES=4*1024*1024
        private val LOCK=Any()
        private val SHA=Regex("[0-9a-f]{64}")
        private fun hash(s:String)=BundleIntegrity.sha256(s.byteInputStream())
    }
    private fun address(r:LetterWritingAddressRecord)=AddressValue(r.streetAddress,r.unit.orEmpty(),r.city.orEmpty(),r.state.orEmpty(),r.postalCode.orEmpty(),r.buildingId.orEmpty())
    private fun address(r:TelephoneTerritoryRecord)=AddressValue(r.streetAddress,r.unit.orEmpty(),r.city.orEmpty(),r.state.orEmpty(),r.postalCode.orEmpty(),r.buildingId.orEmpty())
    private fun verifiedInventory(id:String,mode:WorkspaceMode):Page2Inventory? {
        if(!coordinator.state(id,mode).inputReady)return null
        return coordinator.editingBaseline(id,mode).third
    }
    private fun capture(id:String,mode:WorkspaceMode):ExtendedCatalog {
        val slot=requireNotNull(kb.assignments[id]) {"Unknown territory"}
        require(mode in WorkspaceModePolicy.allowedModes(slot)) {"Territory mode mismatch"}
        val source=requireNotNull(sources.verifiedRecord(id)) {"Import a current source map first"}
        require(source.territoryDisplayId==id && source.canonicalFilename==slot.canonicalFilename)
        val inventory=verifiedInventory(id,mode)
        val locked=slot.sourceHashes.distinct().sorted().map {ExtendedEvidence("LOCKED_ASSIGNMENT",it,"Locked assignment reference")}
        val geometry=(listOf(ExtendedEvidence("IMPORTED_MAP",source.sha256,"Current imported map"))+locked).distinct()
        val items=mutableListOf<ExtendedItem>()
        slot.roads.forEach {r->
            items+=ExtendedItem(ExtendedKind.ROAD_PATH,r.segmentId,r.name,PathValue(r.points),geometry)
            items+=ExtendedItem(ExtendedKind.ROAD_WORK,r.segmentId,r.name,WorkValue(r.status,r.role,r.insideSide,r.accessOnly),locked)
        }
        slot.buildings.forEach {b->
            items+=ExtendedItem(ExtendedKind.BUILDING_PATH,b.buildingId,b.label,PathValue(b.polygon),geometry)
            items+=ExtendedItem(ExtendedKind.BUILDING_MEMBERS,b.buildingId,b.label,BuildingValue(b.assigned,b.label,b.sourceMembers,b.labelItems),locked)
        }
        when(inventory) {
            is Page2Inventory.LetterWriting -> {
                val v=inventory.inventory
                require(v.identity.displayId==id && v.knowledgeBaseRevision==kb.revision && v.assignmentAuthoritySha256 in slot.sourceHashes)
                require(LetterWritingAddressInventoryValidator.validateForPage2(v).passed)
                v.records.forEach {r-> val evidence=v.provenance.filter {it.provenanceId in r.provenanceIds && it.fieldUseEligible}.map {ExtendedEvidence("ADDRESS_SOURCE",it.sourceSha256,it.sourceLabel)}.distinct()
                    items+=ExtendedItem(ExtendedKind.LETTER_ADDRESS,r.recordId,r.canonicalMailingLine(),address(r),evidence)
                }
            }
            is Page2Inventory.Telephone -> {
                val v=inventory.inventory
                require(v.identity.displayId==id && v.knowledgeBaseRevision==kb.revision && v.assignmentAuthoritySha256 in slot.sourceHashes)
                require(TelephoneTerritoryInventoryValidator.validateForPage2(v).passed)
                v.records.forEach {r->
                    val ae=v.provenance.filter {it.provenanceId in r.addressProvenanceIds && it.fieldUseEligible && it.authorization!=TelephoneSourceAuthorization.REFERENCE_ONLY}.map {ExtendedEvidence("ADDRESS_SOURCE",it.sourceSha256,it.sourceLabel)}.distinct()
                    val pe=v.provenance.filter {it.fieldUseEligible && it.telephoneUseAuthorized && it.authorization!=TelephoneSourceAuthorization.REFERENCE_ONLY && (it.provenanceId in r.phoneProvenanceIds || r.phoneState==TelephoneNumberState.UNAVAILABLE && it.provenanceId in r.addressProvenanceIds)}.map {ExtendedEvidence("TELEPHONE_SOURCE",it.sourceSha256,it.sourceLabel)}.distinct()
                    items+=ExtendedItem(ExtendedKind.PHONE_ADDRESS,r.recordId,r.canonicalAddressLine(),address(r),ae)
                    items+=ExtendedItem(ExtendedKind.PHONE_NUMBER,r.recordId,r.canonicalAddressLine(),PhoneValue(when(r.phoneState) {TelephoneNumberState.VERIFIED_NUMBER->ProposedPhoneState.NUMBER;TelephoneNumberState.UNAVAILABLE->ProposedPhoneState.UNAVAILABLE;else->ProposedPhoneState.UNKNOWN},r.phoneNumber.orEmpty()),pe)
                }
            }
            null -> Unit
        }
        require(items.map {it.key}.toSet().size==items.size) {"Duplicate known items"}
        val canonicalItems=JSONArray(items.map {JSONObject().put("kind",it.kind.name).put("id",it.id).put("before",ExtendedValues.json(it.before)).put("evidence",JSONArray(it.evidence.map {e->JSONObject().put("role",e.role).put("sha",e.sha256).put("title",e.title)}))})
        val invHash=when(inventory) {is Page2Inventory.LetterWriting->inventory.inventory.canonicalSha256();is Page2Inventory.Telephone->inventory.inventory.canonicalSha256();null->""}
        val binding=hash(ExtendedValues.canonical(JSONObject().put("territory",id).put("mode",mode.name).put("kb",kb.revision).put("slot",slot.toString()).put("source",source.toString()).put("inventory",invHash).put("items",canonicalItems)))
        require(sources.verifiedRecord(id)==source && verifiedInventory(id,mode)==inventory) {"Source or inventory changed while loading items"}
        return ExtendedCatalog(binding,ExtendedValues.frozen(items.map {it.copy(before=ExtendedValues.detached(it.kind,it.before),evidence=ExtendedValues.frozen(it.evidence))}),inventory!=null)
    }
    fun catalog(id:String,mode:WorkspaceMode):ExtendedCatalog=synchronized(LOCK) {capture(id,mode)}
    private fun file(id:String,mode:WorkspaceMode):AtomicFile {ExtendedValues.text(id,80);require(root.exists() || root.mkdirs());return AtomicFile(File(root,hash(id+":"+mode.name)+".json"))}
    private fun proposal(p:ExtendedProposal)=JSONObject().put("kind",p.kind.name).put("item",p.itemId).put("before",ExtendedValues.json(p.before)).put("proposed",ExtendedValues.json(p.proposed)).put("reason",p.reason).put("role",p.evidenceRole).put("evidence",p.evidenceSha256)
    private fun proposals(a:JSONArray):List<ExtendedProposal> {require(a.length()<=MAX_PROPOSALS);return ExtendedValues.frozen((0 until a.length()).map {i->val p=a.getJSONObject(i);ExtendedValues.keys(p,"kind","item","before","proposed","reason","role","evidence");val k=ExtendedKind.valueOf(p.getString("kind"));ExtendedProposal(k,p.getString("item"),ExtendedValues.parse(k,p.getJSONObject("before")),ExtendedValues.parse(k,p.getJSONObject("proposed")),p.getString("reason"),p.getString("role"),p.getString("evidence"))})}
    private fun validate(ps:List<ExtendedProposal>,catalog:ExtendedCatalog?=null) {
        require(ps.size<=MAX_PROPOSALS && ps.map {it.key}.toSet().size==ps.size) {"Duplicate or excessive proposals"}
        ps.forEach {p->
            ExtendedValues.text(p.itemId);ExtendedValues.text(p.reason,1024);require(SHA.matches(p.evidenceSha256));require(p.before!=p.proposed) {"No change proposed"}
            ExtendedValues.validate(p.kind,p.proposed)
            if(catalog!=null) {
                val item=requireNotNull(catalog.items.singleOrNull {it.key==p.key}) {"Unknown item or unavailable inventory"}
                require(p.before==item.before) {"Before-value changed"}
                require(item.evidence.any {it.role==p.evidenceRole && it.sha256==p.evidenceSha256}) {"Evidence is not eligible for this category and item"}
                if(p.proposed is AddressValue)require(p.proposed.building.isEmpty() || catalog.items.any {it.kind==ExtendedKind.BUILDING_PATH && it.id==p.proposed.building}) {"Unknown building binding"}
            }
        }
        if(catalog!=null) listOf(ExtendedKind.LETTER_ADDRESS,ExtendedKind.PHONE_ADDRESS).forEach {kind->
            val lines=catalog.items.filter {it.kind==kind}.map {item->
                val value=(ps.firstOrNull {it.key==item.key}?.proposed ?: item.before) as AddressValue
                listOf(value.street,value.unit,value.city,value.state,value.postal).filter {it.isNotEmpty()}.joinToString(" ").lowercase().replace(Regex("[^a-z0-9]+")," ").trim()
            }
            require(lines.toSet().size==lines.size) {"Proposed addresses duplicate an existing inventory row"}
        }
    }
    private fun token(raw:JSONObject,row:JSONObject)=hash(ExtendedValues.canonical(JSONObject().put("id",raw.getString("id")).put("territory",raw.getString("territory")).put("mode",raw.getString("mode")).put("binding",raw.getString("binding")).put("revision",row)))
    private fun readRaw(id:String,mode:WorkspaceMode):JSONObject? {
        val f=file(id,mode);if(!f.baseFile.exists() && !File(f.baseFile.path+".bak").exists())return null
        val bytes=f.openRead().use {input->val out=java.io.ByteArrayOutputStream();val b=ByteArray(8192);while(true) {val n=input.read(b);if(n<0)break;require(out.size()+n<=MAX_BYTES);out.write(b,0,n)};out.toByteArray()}
        val raw=JSONObject(bytes.toString(Charsets.UTF_8));require(ExtendedValues.canonical(raw).toByteArray().contentEquals(bytes)) {"Draft encoding or trailing data changed"}
        ExtendedValues.keys(raw,"schema","id","territory","mode","binding","revisions")
        require(raw.get("schema")==1 && raw.getString("territory")==id && raw.getString("mode")==mode.name);UUID.fromString(raw.getString("id"));require(SHA.matches(raw.getString("binding")))
        val rows=raw.getJSONArray("revisions");require(rows.length() in 1..MAX_REVISIONS);var previous=""
        for(i in 0 until rows.length()) {
            val r=rows.getJSONObject(i);ExtendedValues.keys(r,"number","action","at","restoredFrom","proposals","previous","token")
            require(r.get("number")==i && r.getString("previous")==previous);Instant.parse(r.getString("at"));require(r.getString("action") in if(i==0)setOf("CREATE") else setOf("SAVE","RESTORE"))
            val ps=proposals(r.getJSONArray("proposals"));validate(ps);if(i==0)require(ps.isEmpty())
            if(r.getString("action")=="RESTORE") {val n=r.get("restoredFrom");require(n is Int && n in 0 until i);require(ExtendedValues.canonical(r.getJSONArray("proposals"))==ExtendedValues.canonical(rows.getJSONObject(n).getJSONArray("proposals")))} else require(r.isNull("restoredFrom"))
            val expected=r.getString("token");r.remove("token");require(expected==token(raw,r)) {"Draft integrity failed"};r.put("token",expected);previous=expected
        };return raw
    }
    private fun view(raw:JSONObject,catalog:ExtendedCatalog?):ExtendedDraft {
        val a=raw.getJSONArray("revisions")
        val revisions=(0 until a.length()).map {i->
            val r=a.getJSONObject(i)
            ExtendedRevision(i,r.getString("token"),r.getString("action"),r.getString("at"),
                if(r.isNull("restoredFrom"))null else r.getInt("restoredFrom"),proposals(r.getJSONArray("proposals")))
        }
        return ExtendedDraft(raw.getString("id"),raw.getString("territory"),WorkspaceMode.valueOf(raw.getString("mode")),
            raw.getString("binding"),ExtendedValues.frozen(revisions),catalog?.binding!=raw.getString("binding"))
    }
    fun load(id:String,mode:WorkspaceMode):ExtendedEditorState=synchronized(LOCK) {
        val raw=readRaw(id,mode);val c=runCatching {capture(id,mode)};ExtendedEditorState(c.getOrNull(),raw?.let {view(it,c.getOrNull())},c.exceptionOrNull()?.let {it.message ?: "Current editing catalog could not be loaded"})
    }
    fun read(id:String,mode:WorkspaceMode):ExtendedDraft?=load(id,mode).draft
    private fun append(raw:JSONObject,action:String,ps:List<ExtendedProposal>,restore:Int?=null) {
        val a=raw.getJSONArray("revisions");require(a.length()<MAX_REVISIONS) {"Revision limit reached"};val r=JSONObject().put("number",a.length()).put("action",action).put("at",Instant.now().toString()).put("restoredFrom",restore ?: JSONObject.NULL).put("proposals",JSONArray(ps.map(::proposal))).put("previous",if(a.length()==0)"" else a.getJSONObject(a.length()-1).getString("token"));r.put("token",token(raw,r));a.put(r)
    }
    private fun write(id:String,mode:WorkspaceMode,raw:JSONObject) {val bytes=ExtendedValues.canonical(raw).toByteArray();require(bytes.size<=MAX_BYTES);val f=file(id,mode);val o=f.startWrite();try {o.write(bytes);f.finishWrite(o)}catch(t:Throwable){f.failWrite(o);throw t}}
    fun create(id:String,mode:WorkspaceMode,expectedBinding:String):ExtendedDraft=synchronized(LOCK) {
        require(readRaw(id,mode)==null) {"Draft already exists"};val c=capture(id,mode);require(c.binding==expectedBinding) {"Items changed; reload"}
        val raw=JSONObject().put("schema",1).put("id",UUID.randomUUID().toString()).put("territory",id).put("mode",mode.name).put("binding",c.binding).put("revisions",JSONArray());append(raw,"CREATE",emptyList());require(capture(id,mode).binding==c.binding);write(id,mode,raw);view(raw,c)
    }
    private fun current(id:String,mode:WorkspaceMode,expectedToken:String):Pair<JSONObject,ExtendedCatalog> {val r=requireNotNull(readRaw(id,mode));val c=capture(id,mode);val d=view(r,c);require(d.latest.token==expectedToken) {"Draft changed; reload"};require(!d.stale) {"Source, assignment or inventory changed; draft is stale"};return r to c}
    fun save(id:String,mode:WorkspaceMode,expectedToken:String,ps:List<ExtendedProposal>):ExtendedDraft=synchronized(LOCK) {
        val (r,c)=current(id,mode,expectedToken);val copy=proposals(JSONArray(ps.map(::proposal)));validate(copy,c);require(copy!=view(r,c).latest.proposals) {"No changes"};append(r,"SAVE",copy);require(capture(id,mode).binding==c.binding);write(id,mode,r);view(r,c)
    }
    fun restore(id:String,mode:WorkspaceMode,expectedToken:String,revision:Int):ExtendedDraft=synchronized(LOCK) {
        val (r,c)=current(id,mode,expectedToken);val d=view(r,c);require(revision in 0 until d.latest.number);val ps=d.revisions[revision].proposals;validate(ps,c);append(r,"RESTORE",ps,revision);require(capture(id,mode).binding==c.binding);write(id,mode,r);view(r,c)
    }
    fun discard(id:String,mode:WorkspaceMode,expectedToken:String)=synchronized(LOCK) {val r=requireNotNull(readRaw(id,mode));require(r.getJSONArray("revisions").getJSONObject(r.getJSONArray("revisions").length()-1).getString("token")==expectedToken) {"Draft changed; reload"};file(id,mode).delete();require(readRaw(id,mode)==null)}
    /** Capture under the journal lock; checking the resulting witness never acquires that lock. */
    internal fun revisionWitness(id:String,mode:WorkspaceMode):EditingJournalWitness=synchronized(LOCK) {
        readRaw(id,mode) // Validate/recover only here, before entering coordinator locks.
        EditingJournalWitness.capture(file(id,mode).baseFile,MAX_BYTES)
    }

}

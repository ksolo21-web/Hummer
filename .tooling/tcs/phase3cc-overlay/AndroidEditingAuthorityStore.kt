package com.koenterprises.territorycardstudio

import android.os.SystemClock
import android.util.AtomicFile
import com.koenterprises.territorycardstudio.core.*
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal data class EditingAuthorityFact(val kind:String,val itemId:String,val value:ExtendedValue?,val text:String?,val verification:String,val sourceSha256:String) {
    val key get()="$kind:$itemId"
}
internal data class EditingAuthorityReceipt(val id:String,val territory:String,val mode:WorkspaceMode,val baseVersion:String,
    val source:SourceMapIntakeRecord,val baseline:ProductionRenderModelInput,val inventory:Page2Inventory?,
    val facts:List<EditingAuthorityFact>,val hashes:List<String>,val witnesses:List<EditingJournalWitness>,val issued:Long,val expires:Long)
data class EditingAuthorityStatus(val available:Boolean,val sourceCount:Int,val factCount:Int,val message:String,val receiptId:String?=null)

/** Imports facts only from bytes whose complete hash is already independently eligible for the exact record/category. */
class AndroidEditingAuthorityStore internal constructor(private val root:File,private val kb:TerritoryKnowledgeBase,
    private val sources:SourceMapIntakeStore,private val coordinator:AndroidBuildWorkflowCoordinator,
    private val clock:()->Long={SystemClock.elapsedRealtime()}) {
    companion object {const val MAX_BYTES=1024*1024;const val PARSER_REVISION="authority-facts-v1"}
    private val sessions=ConcurrentHashMap<String,EditingAuthorityReceipt>()
    private fun key(id:String,mode:WorkspaceMode)="$id:${mode.name}"
    internal fun current(r:EditingAuthorityReceipt):Boolean=runCatching {
        val now=clock();sessions[key(r.territory,r.mode)]===r && now>=r.issued && now<=r.expires &&
            sources.verifiedRecord(r.territory)==r.source && r.witnesses.all {it.current()}
    }.getOrDefault(false)
    internal fun receipt(id:String,mode:WorkspaceMode):EditingAuthorityReceipt {
        val r=requireNotNull(sessions[key(id,mode)]) {"Import independently authored authority facts first"}
        require(current(r)) {"Authority import expired, changed or was revoked; import again"};return r
    }
    fun status(id:String,mode:WorkspaceMode):EditingAuthorityStatus {
        val r=sessions[key(id,mode)]
        return if(r!=null && current(r))EditingAuthorityStatus(true,r.hashes.size,r.facts.size,"Independent source facts ready for reconciliation",r.id)
        else EditingAuthorityStatus(false,0,0,"Independent authority facts are unavailable. An imported map or draft reference alone cannot authorize these changes.")
    }
    @Synchronized fun revoke(id:String,mode:WorkspaceMode,expectedReceiptId:String) {require(sessions[key(id,mode)]?.id==expectedReceiptId){"Authority receipt changed; refresh first"};sessions.remove(key(id,mode))}
    private fun parse(bytes:ByteArray):JSONObject {
        require(bytes.size in 1..MAX_BYTES) {"Authority file exceeds size limit"}
        val text=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        var depth=0;var quoted=false;var escaped=false
        text.forEach {c->if(quoted){if(escaped)escaped=false else if(c=='\\')escaped=true else if(c=='"')quoted=false}else when(c){'"'->quoted=true;'[','{'->{depth++;require(depth<=24){"Authority nesting exceeds limit"}};']','}'->{depth--;require(depth>=0)}}}
        require(!quoted && depth==0)
        val o=JSONObject(text)
        require(ExtendedValues.canonical(o).toByteArray().contentEquals(bytes)) {"Use exact canonical JSON; duplicate keys, trailing data and ambiguous encodings are rejected"}
        return o
    }
    @Synchronized fun importFacts(id:String,mode:WorkspaceMode,submittedBytes:ByteArray):EditingAuthorityStatus {
        val bytes=submittedBytes.copyOf()
        val source=requireNotNull(sources.verifiedRecord(id)) {"Import the current source map first"}
        val baseline=coordinator.editingBaseline(id,mode);val slot=requireNotNull(kb.assignments[id])
        require(slot.needsNewCard && mode in WorkspaceModePolicy.allowedModes(slot)) {"Territory is locked or mode differs"}
        val hash=BundleIntegrity.sha256(bytes.inputStream());val o=parse(bytes)
        ExtendedValues.keys(o,"schema","territory","mode","kb","facts")
        require(o.get("schema")==1 && o.getString("territory")==id && o.getString("mode")==mode.name && o.getString("kb")==kb.revision) {"Authority file context differs"}
        val rows=o.getJSONArray("facts");require(rows.length() in 1..128)
        val facts=(0 until rows.length()).map {i->
            val f=rows.getJSONObject(i);ExtendedValues.keys(f,"kind","item","value","verification")
            val kind=f.getString("kind");val item=f.getString("item");ExtendedValues.text(item)
            val geometryKind=kind in setOf("ROAD_LABEL","ROAD_PATH","ROAD_WORK","BUILDING_PATH","BUILDING_MEMBERS")
            if(geometryKind) {
                require(baseline.first.assignment.authorityRole=="current_authoritative_assignment" && hash==baseline.first.assignment.authoritySha256 && hash in slot.sourceHashes) {"File is not this baseline's current assignment authority; reference hashes do not authorize changes"}
                require(if(kind.startsWith("ROAD"))baseline.first.assignment.roads.any {it.segmentId==item} else baseline.first.assignment.buildings.any {it.buildingId==item}) {"Unknown authoritative item"}
                ExtendedValues.keys(f.getJSONObject("verification"))
            } else when(val inventory=baseline.third) {
                is Page2Inventory.LetterWriting -> {
                    require(kind=="LETTER_ADDRESS");val record=inventory.inventory.records.single {it.recordId==item}
                    require(inventory.inventory.provenance.any {it.provenanceId in record.provenanceIds && it.sourceSha256==hash && it.fieldUseEligible}) {"Source is not eligible for this address record"}
                }
                is Page2Inventory.Telephone -> {
                    require(kind in setOf("PHONE_ADDRESS","PHONE_NUMBER"));val record=inventory.inventory.records.single {it.recordId==item}
                    val ids=if(kind=="PHONE_ADDRESS")record.addressProvenanceIds else record.phoneProvenanceIds+if(record.phoneState==TelephoneNumberState.UNAVAILABLE)record.addressProvenanceIds else emptyList()
                    require(inventory.inventory.provenance.any {it.provenanceId in ids && it.sourceSha256==hash && it.fieldUseEligible && it.authorization!=TelephoneSourceAuthorization.REFERENCE_ONLY && (kind!="PHONE_NUMBER" || it.telephoneUseAuthorized)}) {"Source is not authorized for this record and telephone field"}
                }
                null -> error("Verified inventory is required for address or telephone facts")
            }
            val value=f.getJSONObject("value");val parsed=if(kind=="ROAD_LABEL") {ExtendedValues.keys(value,"text");ExtendedValues.text(value.getString("text"));null} else ExtendedValues.parse(ExtendedKind.valueOf(kind),value).also {ExtendedValues.validate(ExtendedKind.valueOf(kind),it)}
            val v=f.getJSONObject("verification")
            if(kind in setOf("LETTER_ADDRESS","PHONE_ADDRESS")) {ExtendedValues.keys(v,"status","verifiedAt","boundary","boundarySha","conflicts");require(v.getJSONArray("conflicts").length()<=128)}
            if(kind=="PHONE_NUMBER")ExtendedValues.keys(v,"state","verifiedAt","bindingVerified")
            EditingAuthorityFact(kind,item,parsed,if(kind=="ROAD_LABEL")value.getString("text") else null,ExtendedValues.canonical(v),hash)
        }
        require(facts.map {it.key}.toSet().size==facts.size) {"Duplicate authority facts"}
        val old=sessions[key(id,mode)]?.takeIf {current(it) && it.baseVersion==baseline.second && it.source==source}
        require(old==null || hash !in old.hashes) {"This exact authority file is already imported"}
        require((old?.hashes?.size ?: 0)<16 && (old?.facts?.size ?: 0)+facts.size<=128)
        require(old==null || old.facts.none {a->facts.any {it.key==a.key}}) {"Overlapping authority sources require explicit replacement, not precedence"}
        require(root.exists() || root.mkdirs());val file=File(root,"${UUID.randomUUID()}.json");val atomic=AtomicFile(file);val stream=atomic.startWrite()
        try {stream.write(bytes);atomic.finishWrite(stream)}catch(t:Throwable){atomic.failWrite(stream);throw t}
        val witness=EditingJournalWitness.capture(file,MAX_BYTES)
        require(sources.verifiedRecord(id)==source && coordinator.editingBaseline(id,mode).second==baseline.second) {"Baseline changed during authority import"}
        val now=clock();val r=EditingAuthorityReceipt(UUID.randomUUID().toString(),id,mode,baseline.second,source,
            EditingSnapshots.detach(baseline.first),EditingSnapshots.detach(baseline.third),ExtendedValues.frozen(old?.facts.orEmpty()+facts),
            ExtendedValues.frozen(old?.hashes.orEmpty()+hash),ExtendedValues.frozen(old?.witnesses.orEmpty()+witness),now,now+900000)
        sessions[key(id,mode)]=r;return status(id,mode)
    }
}

package com.koenterprises.territorycardstudio

import android.os.SystemClock
import android.util.AtomicFile
import com.koenterprises.territorycardstudio.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import java.util.UUID

/** Versioned, closed typed exchange format. Files cannot choose classes or contain executable type names. */
internal object VerifiedProjectCodec {
    const val MAX_BYTES=2*1024*1024
    fun encode(id:String,mode:WorkspaceMode,source:String,input:ProductionRenderModelInput,inventory:Page2Inventory?):ByteArray {
        val inv=when(inventory){is Page2Inventory.LetterWriting->inventory.inventory;is Page2Inventory.Telephone->inventory.inventory;null->null}
        return ExtendedValues.canonical(JSONObject().put("schema","verified-project-v1").put("territory",id).put("mode",mode.name)
            .put("sourceSha256",source).put("input",pack(input)).put("inventory",pack(inv))).toByteArray(Charsets.UTF_8)
    }
    private fun components(c:Class<*>)=c.methods.filter{it.name.matches(Regex("component[0-9]+")) && it.parameterCount==0}.sortedBy{it.name.removePrefix("component").toInt()}
    private fun pack(v:Any?):Any=when(v){null->JSONObject.NULL;is String,is Boolean,is Int,is Long,is Double->v;is Char->v.toString();is Enum<*>->v.name
        is Map<*,*>->JSONObject().also{o->v.forEach{(k,value)->require(k is String);o.put(k,pack(value))}}
        is Collection<*>->JSONArray(v.map(::pack));else->{require(v.javaClass.name.startsWith("com.koenterprises.territorycardstudio.core."));val c=components(v.javaClass);require(c.isNotEmpty());JSONArray(c.map{pack(it.invoke(v))})}}
    data class Project(val id:String,val mode:WorkspaceMode,val source:String,val input:ProductionRenderModelInput,val inventory:Page2Inventory?)
    fun decode(bytes:ByteArray):Project {
        require(bytes.size in 1..MAX_BYTES){"Verified project exceeds size limit"}
        val text=Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        var depth=0;var quoted=false;var escaped=false
        for(c in text){if(quoted){if(escaped)escaped=false else if(c=='\\')escaped=true else if(c=='"')quoted=false}else when(c){'"'->quoted=true;'[','{'->{depth++;require(depth<=64)};']','}'->{depth--;require(depth>=0)}}};require(!quoted && depth==0)
        val root=JSONObject(text);require(ExtendedValues.canonical(root).toByteArray().contentEquals(bytes)){"Use exact canonical project encoding"}
        ExtendedValues.keys(root,"schema","territory","mode","sourceSha256","input","inventory");require(root.getString("schema")=="verified-project-v1")
        var nodes=0
        fun unpack(v:Any,t:Type,d:Int):Any? {
            require(++nodes<=100000 && d<=64)
            if(v===JSONObject.NULL)return null
            if(t is WildcardType)return unpack(v,t.upperBounds.single(),d+1)
            if(t is ParameterizedType){val raw=t.rawType as Class<*>
                if(raw==Map::class.java){require(t.actualTypeArguments[0]==String::class.java);val o=v as JSONObject;require(o.length()<=4096)
                    return o.keys().asSequence().toList().associateWith{unpack(o.get(it),t.actualTypeArguments[1],d+1)}}
                require(raw==List::class.java || raw==Set::class.java)
                val a=v as JSONArray;require(a.length()<=4096);val values=(0 until a.length()).map{unpack(a.get(it),t.actualTypeArguments.single(),d+1)}
                return if(raw==Set::class.java)values.toSet().also{require(it.size==values.size)} else values}
            val c=t as Class<*>
            return when(c){String::class.java->v as String;java.lang.Boolean.TYPE,java.lang.Boolean::class.java->v as Boolean
                java.lang.Integer.TYPE,java.lang.Integer::class.java->(v as Number).let{require(it.toDouble()==it.toInt().toDouble());it.toInt()}
                java.lang.Long.TYPE,java.lang.Long::class.java->(v as Number).let{require(it.toDouble()==it.toLong().toDouble());it.toLong()}
                java.lang.Double.TYPE,java.lang.Double::class.java->(v as Number).toDouble().also{require(it.isFinite())}
                java.lang.Character.TYPE,java.lang.Character::class.java->(v as String).single()
                else->if(c.isEnum)c.enumConstants.single{(it as Enum<*>).name==v} else {
                    require(c.name.startsWith("com.koenterprises.territorycardstudio.core."));val a=v as JSONArray;val n=components(c).size;require(n>0 && a.length()==n)
                    val ctor=c.constructors.single{it.parameterCount==n && it.parameterTypes.none{p->p.name.contains("DefaultConstructorMarker")}}
                    ctor.newInstance(*(0 until n).map{unpack(a.get(it),ctor.genericParameterTypes[it],d+1)}.toTypedArray())
                }}
        }
        val mode=WorkspaceMode.valueOf(root.getString("mode"));val input=unpack(root.get("input"),ProductionRenderModelInput::class.java,0) as ProductionRenderModelInput
        val inv=if(root.isNull("inventory"))null else when(mode){WorkspaceMode.REGULAR->error("Regular project must not contain Page 2")
            WorkspaceMode.LETTER_WRITING->Page2Inventory.LetterWriting(unpack(root.get("inventory"),LetterWritingAddressInventory::class.java,0) as LetterWritingAddressInventory)
            WorkspaceMode.TELEPHONE->Page2Inventory.Telephone(unpack(root.get("inventory"),TelephoneTerritoryInventory::class.java,0) as TelephoneTerritoryInventory)}
        return Project(root.getString("territory"),mode,root.getString("sourceSha256"),input,inv)
    }
}

data class InitialPreparationTicket internal constructor(val session:String,val territory:String,val mode:WorkspaceMode,val projectSha256:String,val sourceSha256:String,val providers:List<String>)
/** Project bytes must already be independently registered for this assignment. Import never confers authority. */
class AndroidVerifiedProjectIntake internal constructor(private val kb:TerritoryKnowledgeBase,private val policy:OnlineSourcePolicy,
    private val coordinator:AndroidBuildWorkflowCoordinator,private val sources:SourceMapIntakeStore,private val directory:File,
    private val fetchEvidence:(LiveGeometryVerificationRequest)->List<ProviderVerificationEvidence>,private val clock:()->Long={SystemClock.elapsedRealtime()}) {
    private data class Pending(val ticket:InitialPreparationTicket,val input:ProductionRenderModelInput,val inventory:Page2Inventory?,val source:SourceMapIntakeRecord,
        val prior:String?,val witness:EditingJournalWitness,val issued:Long,val expires:Long)
    private val pending=linkedMapOf<String,Pending>()
    private fun bindingCurrent(p:Pending)=runCatching{p.witness.current() && sources.verifiedRecord(p.ticket.territory)==p.source}.getOrDefault(false)
    private fun current(p:Pending)=clock() in p.issued..p.expires && bindingCurrent(p)
    @Synchronized fun validate(id:String,mode:WorkspaceMode,submitted:ByteArray):InitialPreparationTicket {
        val bytes=submitted.copyOf();require(bytes.size in 1..VerifiedProjectCodec.MAX_BYTES)
        val slot=requireNotNull(kb.assignments[id]);require(slot.needsNewCard && mode in WorkspaceModePolicy.allowedModes(slot))
        val hash=BundleIntegrity.sha256(bytes.inputStream())
        require(hash in slot.sourceHashes){"This project is not registered as an independent source for this territory. Import a registered current-assignment project."}
        val p=VerifiedProjectCodec.decode(bytes);val source=requireNotNull(sources.verifiedRecord(id)){"Import the source map first"}
        require(p.id==id && p.mode==mode && p.source==source.sha256 && p.input.assignment.displayId==id){"Project does not match this territory, mode or exact source map"}
        require(p.input.assignment.authoritySha256 in slot.sourceHashes && p.input.assignment.authorityRole=="current_authoritative_assignment"){"Current assignment authority is not registered"}
        if(mode!=WorkspaceMode.REGULAR)requireNotNull(p.inventory){"A complete working inventory is required"}
        val prior=coordinator.currentCandidateVersion(id,mode)
        val a=p.input.assignment;val topo=TopologyOverlapDecisionEngine.topologySignature(a.roads)
        val color=TopologyOverlapDecisionEngine.validateIntersectionColorRoles(a.roads);val overlap=TopologyOverlapDecisionEngine.crossTerritoryOverlapCheck(a.identity,topo,kb)
        val buildings=BuildingValidationEngine.validateBuildings(a.buildings,a.housingType in setOf("apartment","condo","townhome","mobile_home","manufactured_home"))
        val request=LiveGeometryVerificationRequestFactory.create(kb,policy,id,a.authoritySha256,topo,a.buildings.flatMap{it.sourceMembers}.distinct(),p.input.sourceTruth,p.input.liveRequest.jurisdiction)
        val evidence=EditingSnapshots.detach(fetchEvidence(EditingSnapshots.detach(request)));val decision=LiveGeometryVerificationReconciler.reconcile(request,policy,evidence)
        require(decision.renderable){"Independent source verification blocked this project"}
        val input=EditingSnapshots.detach(p.input.copy(topologySignature=topo,topologyValidation=color,overlapDecision=overlap,buildingValidation=buildings,
            liveRequest=request,liveResult=LiveVerificationExecutionResult(request.requestFingerprint,evidence,decision,emptyList())))
        val adapted=ProductionRenderModelAdapter.adaptProduction(kb,input);require(adapted is RenderModelAdaptationResult.Renderable){"Project failed the shared render-model validators"}
        coordinator.validateEditingInventory(id,mode,input,p.inventory)
        require(sources.verifiedRecord(id)==source && coordinator.currentCandidateVersion(id,mode)==prior){"Source or candidate changed during verification"}
        pending.entries.removeAll{!current(it.value)};require(pending.size<16){"Cancel an unused import first"}
        require(directory.isDirectory || directory.mkdirs())
        val file=AtomicFile(File(directory,"$hash.json"))
        require(file.baseFile.exists() || directory.listFiles().orEmpty().count{it.extension=="json"}<64){"Project archive is full; preserve prior evidence before importing another distinct project"}
        if(!file.baseFile.exists()) {
            // Only positively known never-prepared imports are eligible for explicit pruning.
            File(directory,"$hash.unused").writeText("never-prepared-v1")
            val stream=file.startWrite()
            try{stream.write(bytes);stream.fd.sync();file.finishWrite(stream)}catch(e:Exception){file.failWrite(stream);throw e}
        }
        require(file.openRead().use{AndroidFinalOutputService.readBounded(it,VerifiedProjectCodec.MAX_BYTES)}.contentEquals(bytes)){"Saved project bytes changed"}
        val witness=EditingJournalWitness.capture(file.baseFile,VerifiedProjectCodec.MAX_BYTES);val now=clock()
        val ticket=InitialPreparationTicket(UUID.randomUUID().toString(),id,mode,hash,source.sha256,evidence.map{it.providerId}.distinct().sorted())
        pending.entries.removeAll{it.value.ticket.territory==id && it.value.ticket.mode==mode}
        pending[ticket.session]=Pending(ticket,input,EditingSnapshots.detach(p.inventory),source,prior,witness,now,now+900000);return ticket
    }
    @Synchronized fun pruneUnusedImports():Int {
        pending.entries.removeAll{!current(it.value)}
        val selected=directory.listFiles().orEmpty().filter {it.extension=="saved"}.map {f->
            AtomicFile(f).openRead().use {AndroidFinalOutputService.readBounded(it,64)}.toString(Charsets.US_ASCII)
        }.toSet()
        val protected=selected+pending.values.map {it.ticket.projectSha256}
        var removed=0
        directory.listFiles().orEmpty().filter {it.extension=="unused" && it.nameWithoutExtension !in protected}.forEach {marker->
            val file=File(directory,"${marker.nameWithoutExtension}.json")
            if(!file.exists() || file.delete()){marker.delete();removed++}
        }
        return removed
    }
    @Synchronized fun revalidateSaved(id:String,mode:WorkspaceMode):InitialPreparationTicket {
        require(id in kb.assignments && mode in WorkspaceModePolicy.allowedModes(kb.assignments.getValue(id)))
        val pointer=BundleIntegrity.sha256("$id:${mode.name}".byteInputStream())+".saved"
        val hash=AtomicFile(File(directory,pointer)).openRead().use{AndroidFinalOutputService.readBounded(it,64)}.toString(Charsets.US_ASCII)
        require(hash.matches(Regex("[0-9a-f]{64}")))
        val bytes=AtomicFile(File(directory,"$hash.json")).openRead().use{AndroidFinalOutputService.readBounded(it,VerifiedProjectCodec.MAX_BYTES)}
        return validate(id,mode,bytes)
    }
    @Synchronized fun prepare(t:InitialPreparationTicket) {
        val p=requireNotNull(pending[t.session]);require(p.ticket==t && current(p)){"Verified project changed or expired; import again"}
        val pointer=AtomicFile(File(directory,BundleIntegrity.sha256("${t.territory}:${t.mode.name}".byteInputStream())+".saved"))
        // Recovery selection is not authority: every reuse must fetch evidence and rebuild/reapprove.
        val previousPointer=if(pointer.baseFile.exists())pointer.openRead().use{AndroidFinalOutputService.readBounded(it,64)} else null
        val out=pointer.startWrite()
        try{out.write(t.projectSha256.toByteArray(Charsets.US_ASCII));out.fd.sync();pointer.finishWrite(out)}catch(e:Exception){pointer.failWrite(out);throw e}
        // Preserve all attempts that reached preparation, including uncertain failures.
        File(directory,"${t.projectSha256}.unused").delete()
        try {
            coordinator.prepareEditing(t.territory,t.mode,t.sourceSha256,p.input,p.inventory,p.prior){bindingCurrent(p)}
        } catch(failure:Exception) {
            try {
                if(previousPointer==null)pointer.delete() else {
                    val restore=pointer.startWrite()
                    try{restore.write(previousPointer);restore.fd.sync();pointer.finishWrite(restore)}catch(e:Exception){pointer.failWrite(restore);throw e}
                }
            }catch(restoreFailure:Exception){failure.addSuppressed(restoreFailure)}
            throw failure
        }
        pending.remove(t.session)
    }
}

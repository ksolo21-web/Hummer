package com.koenterprises.territorycardstudio

import android.util.AtomicFile
import com.koenterprises.territorycardstudio.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

/** A local reference is not field-release authority. Every use rechecks current preparation. */
data class LifecycleCandidate(val ticket:CandidateReviewTicket,val status:String,val createdAt:String)
data class LifecycleEvent(val sequence:Int,val action:String,val candidateSha256:String,val actor:String,val at:String,val reason:String)
data class CandidateLifecycleState(val ticket:CandidateReviewTicket?=null,val candidates:List<LifecycleCandidate> = emptyList(),
    val history:List<LifecycleEvent> = emptyList(),val promoted:LifecycleCandidate?=null,val active:Boolean=false,val blocker:String?=null,val revision:String="")

class AndroidCandidateLifecycleService(private val kb:TerritoryKnowledgeBase,private val coordinator:AndroidBuildWorkflowCoordinator,
    private val sources:SourceMapIntakeStore,private val directory:File) {
    companion object {private val LOCK=Any();private const val MAX_EVENTS=128;private const val MAX_JOURNAL=1024*1024;private const val MAX_PDF=300*1024-1
        private val SHA=Regex("[0-9a-f]{64}")}
    private val observedActive=mutableSetOf<String>()
    private fun digest(bytes:ByteArray)=BundleIntegrity.sha256(bytes.inputStream())
    private fun canonical(j:JSONObject)=ExtendedValues.canonical(j).toByteArray(Charsets.UTF_8)
    private fun folder(id:String,mode:WorkspaceMode):File {
        val slot=requireNotNull(kb.assignments[id]) {"Unknown territory"}
        require(mode in WorkspaceModePolicy.allowedModes(slot)) {"Invalid workspace mode"}
        return File(directory,digest("$id:${mode.name}".toByteArray()))
    }
    private fun journal(id:String,mode:WorkspaceMode)=AtomicFile(File(folder(id,mode),"journal.json"))
    private fun pdf(id:String,mode:WorkspaceMode,key:String):File {require(SHA.matches(key));return File(folder(id,mode),"pdf/$key.pdf")}
    private fun bounded(file:AtomicFile,limit:Int)=file.openRead().use {input->
        val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
        while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=limit){"Stored lifecycle data exceeds its limit"};out.write(buffer,0,n)};out.toByteArray()}
    private fun write(file:AtomicFile,bytes:ByteArray) {
        require(file.baseFile.parentFile!!.isDirectory || file.baseFile.parentFile!!.mkdirs()) {"Cannot create lifecycle storage"}
        val stream=file.startWrite();try {stream.write(bytes);stream.fd.sync();file.finishWrite(stream)}catch(t:Throwable){file.failWrite(stream);throw t}
    }
    private fun encode(t:CandidateReviewTicket):JSONObject {val m=t.manifest
        return JSONObject().put("mode",m.mode.name).put("id",m.displayId).put("filename",m.canonicalFilename).put("version",m.candidateVersion)
            .put("kb",m.knowledgeBaseRevision).put("authority",m.assignmentAuthoritySha256).put("front",m.frontPdfSha256).put("packet",m.packetPdfSha256)
            .put("inventory",m.sourceInventorySha256 ?: JSONObject.NULL).put("pages",m.pageCount).put("roles",JSONArray(m.pageRoles.map {it.name}))
            .put("print",m.printReady).put("source",t.sourceSha256).put("input",t.preparedInputSha256)
    }
    private fun decode(j:JSONObject):CandidateReviewTicket {
        ExtendedValues.keys(j,"mode","id","filename","version","kb","authority","front","packet","inventory","pages","roles","print","source","input")
        val roles=j.getJSONArray("roles");require(roles.length() in 1..2)
        val m=CanonicalPacketManifest(CanonicalPacketMode.valueOf(j.getString("mode")),j.getString("id"),j.getString("filename"),j.getString("version"),j.getString("kb"),
            j.getString("authority"),j.getString("front"),j.getString("packet"),if(j.isNull("inventory"))null else j.getString("inventory"),j.getInt("pages"),
            (0 until roles.length()).map {TerritoryPdfPageRole.valueOf(roles.getString(it))},j.getBoolean("print"))
        require(m.printReady && m.pageCount==roles.length());java.util.UUID.fromString(m.candidateVersion)
        for(h in listOf(m.assignmentAuthoritySha256,m.frontPdfSha256,m.packetPdfSha256,j.getString("source"),j.getString("input")))require(SHA.matches(h))
        m.sourceInventorySha256?.let {require(SHA.matches(it))}
        return CandidateReviewTicket(m,j.getString("source"),j.getString("input"))
    }
    private data class Store(val raw:JSONObject,val candidates:LinkedHashMap<String,LifecycleCandidate>,val events:List<LifecycleEvent>,val current:String?)
    private fun empty(id:String,mode:WorkspaceMode)=JSONObject().put("schema",1).put("id",id).put("mode",mode.name).put("events",JSONArray())
    private fun eventHash(id:String,mode:WorkspaceMode,e:JSONObject):String=digest("$id\n${mode.name}\n".toByteArray()+canonical(e))
    private fun parse(raw:JSONObject,id:String,mode:WorkspaceMode):Store {
        ExtendedValues.keys(raw,"schema","id","mode","events");require(raw.getInt("schema")==1 && raw.getString("id")==id && raw.getString("mode")==mode.name)
        val a=raw.getJSONArray("events");require(a.length()<=MAX_EVENTS);val candidates=linkedMapOf<String,LifecycleCandidate>();val events=mutableListOf<LifecycleEvent>();var previous="";var current:String?=null
        for(i in 0 until a.length()) {val e=a.getJSONObject(i)
            ExtendedValues.keys(e,"seq","action","candidate","actor","at","reason","ticket","pages","boundaries","data","previous","hash")
            require(e.getInt("seq")==i+1 && e.getString("previous")==previous);val hash=e.getString("hash");val copy=JSONObject(e.toString());copy.remove("hash");require(hash==eventHash(id,mode,copy)) {"Lifecycle audit integrity failed"};previous=hash
            val key=e.getString("candidate");require(SHA.matches(key));val action=e.getString("action");val actor=e.getString("actor");val at=e.getString("at");Instant.parse(at)
            require(actor.length in 1..120 && actor.none {it.isISOControl()});require(e.getString("reason").length<=500)
            if(action=="CREATED") {
                require(key !in candidates && candidates.size<64);val t=decode(e.getJSONObject("ticket"));require(t.manifest.displayId==id && t.manifest.mode.name==mode.name && t.manifest.canonicalSha256()==key)
                require(t.manifest.canonicalFilename==kb.assignments.getValue(id).canonicalFilename)
                candidates[key]=LifecycleCandidate(t,"CANDIDATE-UNAPPROVED",at)
            } else {
                require(e.isNull("ticket"));val c=requireNotNull(candidates[key]);require(c.status!="INVALIDATED") {"Invalidated candidate cannot be reapproved"}
                when(action) {
                    "APPROVED"->{require(e.getBoolean("pages") && e.getBoolean("boundaries") && e.getBoolean("data"));require(current==null || current==key)
                        val m=c.ticket.manifest;val receipt=ExactPacketApprovalReceipt(key,m.mode,m.displayId,m.canonicalFilename,m.candidateVersion,m.packetPdfSha256,ExactApprovalState.EXPLICITLY_APPROVED,actor,at)
                        require(CrossModePacketValidator.validateExactApproval(m,receipt).isEmpty());current=key;candidates[key]=c.copy(status="APPROVED")}
                    "REJECTED"->{if(current==key)current=null;candidates[key]=c.copy(status="REJECTED")}
                    "INVALIDATED"->{if(current==key)current=null;candidates[key]=c.copy(status="INVALIDATED")}
                    else->error("Unknown lifecycle action")
                }
            }
            events+=LifecycleEvent(i+1,action,key,actor,at,e.getString("reason"))
        }
        return Store(raw,candidates,events,current)
    }
    private fun decodeBounded(bytes:ByteArray):JSONObject {
        val decoder=Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        val text=decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        var depth=0;var quoted=false;var escaped=false
        for(c in text) {if(quoted){if(escaped)escaped=false else if(c=='\\')escaped=true else if(c=='"')quoted=false}
            else when(c){'"'->quoted=true;'[','{'->{depth++;require(depth<=24){"Lifecycle nesting exceeds limit"}};']','}'->{depth--;require(depth>=0)}}}
        require(!quoted && depth==0){"Incomplete lifecycle data"};return JSONObject(text)
    }
    private fun load(id:String,mode:WorkspaceMode):Store {
        val f=journal(id,mode);val raw=if(f.baseFile.exists() || File(f.baseFile.path+".bak").exists()) {
            val bytes=bounded(f,MAX_JOURNAL);val j=decodeBounded(bytes);require(canonical(j).contentEquals(bytes)) {"Lifecycle encoding changed"};j
        } else {require(File(folder(id,mode),"pdf").listFiles().isNullOrEmpty()) {"Lifecycle journal missing; saved PDFs cannot be treated as approved"};empty(id,mode)}
        return parse(raw,id,mode)
    }
    private fun append(raw:JSONObject,id:String,mode:WorkspaceMode,action:String,key:String,actor:String,reason:String,ticket:CandidateReviewTicket?=null,checks:CandidateReviewChecks=CandidateReviewChecks(false,false,false)) {
        val a=raw.getJSONArray("events");require(a.length()<MAX_EVENTS) {"Audit capacity reached; preserve this history before creating more candidates"}
        val e=JSONObject().put("seq",a.length()+1).put("action",action).put("candidate",key).put("actor",actor).put("at",Instant.now().toString())
            .put("reason",reason).put("ticket",ticket?.let(::encode) ?: JSONObject.NULL).put("pages",checks.pages).put("boundaries",checks.boundaries).put("data",checks.data)
            .put("previous",if(a.length()==0)"" else a.getJSONObject(a.length()-1).getString("hash"))
        e.put("hash",eventHash(id,mode,e));a.put(e)
    }
    private fun save(raw:JSONObject,id:String,mode:WorkspaceMode):Store {
        val parsed=parse(raw,id,mode);val bytes=canonical(raw);require(bytes.size<=MAX_JOURNAL);write(journal(id,mode),bytes)
        require(bounded(journal(id,mode),MAX_JOURNAL).contentEquals(bytes)) {"Lifecycle write could not be verified"};return parsed
    }
    private fun bytes(c:LifecycleCandidate):ByteArray {val t=c.ticket;val b=bounded(AtomicFile(pdf(t.manifest.displayId,WorkspaceMode.valueOf(t.manifest.mode.name),t.manifest.canonicalSha256())),MAX_PDF)
        require(b.isNotEmpty() && digest(b)==t.manifest.packetPdfSha256) {"Archived candidate PDF changed"};return b}
    private fun refresh(id:String,mode:WorkspaceMode,capture:Boolean):CandidateLifecycleState {
        var store=load(id,mode)
        val current=runCatching {coordinator.resolveReview(id,mode)};val t=current.getOrNull();val version=coordinator.currentCandidateVersion(id,mode)
        val source=runCatching {sources.verifiedRecord(id)}.getOrNull()
        val promoted=store.current?.let {store.candidates.getValue(it)}
        if(promoted!=null && (promoted.ticket.manifest.knowledgeBaseRevision!=kb.revision || source?.sha256!=promoted.ticket.sourceSha256 ||
                    (version!=null && version!=promoted.ticket.manifest.candidateVersion) || (t!=null && t!=promoted.ticket) || (t==null && promoted.ticket.manifest.canonicalSha256() in observedActive))) {
            append(store.raw,id,mode,"INVALIDATED",promoted.ticket.manifest.canonicalSha256(),"System","Source, version or validated candidate binding changed")
            store=save(store.raw,id,mode);observedActive.remove(promoted.ticket.manifest.canonicalSha256())
        }
        if(capture && t!=null && t.manifest.canonicalSha256() !in store.candidates) {
            coordinator.withCurrentReview(t) {
                val key=t.manifest.canonicalSha256();val artifact=coordinator.resolvePreview(id,mode,if(mode==WorkspaceMode.REGULAR)PdfPreviewKind.FRONT else PdfPreviewKind.PACKET)
                val b=bounded(AtomicFile(artifact.file),MAX_PDF);require(digest(b)==t.manifest.packetPdfSha256)
                require(store.candidates.size<64 && store.events.size<MAX_EVENTS) {"Lifecycle archive capacity reached"}
                val staged=AtomicFile(pdf(id,mode,key))
                try {
                    write(staged,b)
                    coordinator.withCurrentReview(t) {append(store.raw,id,mode,"CREATED",key,"System","Saved exact validated candidate; no approval inferred",t);store=save(store.raw,id,mode)}
                } catch(failure:Exception) {
                    val committed=runCatching {load(id,mode).candidates.containsKey(key)}.getOrDefault(false)
                    if(!committed)staged.delete()
                    throw failure
                }
            }
        }
        val p=store.current?.let {store.candidates.getValue(it)}
        p?.let(::bytes)
        val active=p!=null && t==p.ticket
        if(active)observedActive.add(p!!.ticket.manifest.canonicalSha256())
        val blocker=if(p!=null && !active)"Saved reference suspended. Revalidate, rebuild and approve the new version before use." else if(t==null)current.exceptionOrNull()?.message else null
        return CandidateLifecycleState(t,store.candidates.values.toList(),store.events.toList(),p,active,blocker,store.raw.getJSONArray("events").let {if(it.length()==0)"" else it.getJSONObject(it.length()-1).getString("hash")})
    }
    fun state(id:String,mode:WorkspaceMode):CandidateLifecycleState=synchronized(LOCK) {
        try {refresh(id,mode,true)}catch(e:Exception){CandidateLifecycleState(blocker=e.message ?: "Lifecycle unavailable")}
    }
    fun capture(id:String,mode:WorkspaceMode):LifecycleCandidate=synchronized(LOCK) {
        val s=refresh(id,mode,true);val t=requireNotNull(s.ticket){s.blocker ?: "No complete candidate"};s.candidates.first {it.ticket==t}
    }
    fun build(id:String,mode:WorkspaceMode,page2:Boolean):BuildWorkflowState=synchronized(LOCK) {
        // Validate store before changing a candidate; corruption never silently creates a new lineage.
        try {load(id,mode)}catch(e:Exception){return@synchronized coordinator.state(id,mode).copy(error=e.message)}
        val result=if(page2)coordinator.generatePage2(id,mode) else coordinator.buildFront(id,mode)
        if(result.error!=null)result else try {refresh(id,mode,true);result}catch(e:Exception){result.copy(error="Candidate history could not be saved: ${e.message}")}
    }
    fun decide(ticket:CandidateReviewTicket,actor:String,checks:CandidateReviewChecks,approved:Boolean,confirmed:Boolean,expectedRevision:String):CandidateLifecycleState=synchronized(LOCK) {
        require(confirmed){"Explicit confirmation is required"};val name=actor.trim();require(name.length in 1..120 && actor.none {it.isISOControl()}){"Enter a reviewer name (1–120 characters)"}
        require(!approved || checks.complete){"Complete all mandatory review checks"}
        val id=ticket.manifest.displayId;val mode=WorkspaceMode.valueOf(ticket.manifest.mode.name)
        coordinator.withCurrentReview(ticket) {
            val previous=load(id,mode).raw.getJSONArray("events");val head=if(previous.length()==0)"" else previous.getJSONObject(previous.length()-1).getString("hash")
            require(head==expectedRevision){"Lifecycle decision changed. Reopen review before deciding."}
            refresh(id,mode,true);val store=load(id,mode);val key=ticket.manifest.canonicalSha256();val c=requireNotNull(store.candidates[key]);require(c.ticket==ticket && c.status!="INVALIDATED")
            bytes(c);val last=store.events.lastOrNull()
            val action=if(approved)"APPROVED" else "REJECTED"
            if(last?.action!=action || last.candidateSha256!=key || last.actor!=name) {
                append(store.raw,id,mode,action,key,name,if(approved)"Explicit human approval promoted this exact PDF to the local current reference; field export remains separately gated" else "Explicit human rejection removed this candidate from the local current reference",checks=checks)
                coordinator.withCurrentReview(ticket){save(store.raw,id,mode)}
            }
            refresh(id,mode,false)
        }
    }
    fun readCurrentPdf(id:String,mode:WorkspaceMode):ByteArray=synchronized(LOCK) {
        val s=refresh(id,mode,false);require(s.active){s.blocker ?: "No approved local reference"};val p=requireNotNull(s.promoted)
        coordinator.withCurrentReview(p.ticket){val b=bytes(p);coordinator.withCurrentReview(p.ticket){b}}
    }
}

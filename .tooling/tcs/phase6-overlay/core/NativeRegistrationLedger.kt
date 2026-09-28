package com.koenterprises.territorycardstudio.core

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant

/** Durable local authorization history. No registration is installed by mere source/draft import.
 * The single atomic commit selects a content-addressed event. Previous immutable files are retained.
 * Callers supply current source/inventory witnesses; the ledger never creates telephone authority. */
class NativeRegistrationLedger(
    private val root:File, private val kb:TerritoryKnowledgeBase,
    private val currentSourceHash:(String)->String?,
    private val currentInventoryContentHash:(String,String)->String?,
    private val beforeCommit:(File)->Unit = {},
    private val syncDirectory:(File)->Unit = { directory ->
        java.nio.channels.FileChannel.open(directory.toPath(),java.nio.file.StandardOpenOption.READ).use {it.force(true)}
    }
):NativeAssignmentEligibility {
    data class Head(val sequence:Int,val eventSha256:String,val action:String,val reconciliationSha256:String,
        val assignmentSha256:String,val actor:String,val at:String)
    data class Registered(val head:Head,val reconciliation:NativeSourceReconciliation,val assignment:CurrentAuthoritativeAssignmentState)
    companion object {private val LOCK=Any();const val MAX_EVENTS=256;private val HASH=Regex("[0-9a-f]{64}")}
    @Volatile var durabilityWarning:String? = null
        private set
    private fun hash(bytes:ByteArray)=BundleIntegrity.sha256(bytes.inputStream())
    private fun scope(id:String,mode:String):File {
        require(id in kb.assignments && mode in setOf("REGULAR","LETTER_WRITING","TELEPHONE"))
        require(root.isDirectory || root.mkdirs())
        return File(root,hash("$id:$mode".toByteArray())).also {require(it.isDirectory || it.mkdirs())}
    }
    private fun bounded(file:File,max:Int):ByteArray {
        require(file.isFile && file.length() in 1..max.toLong()) {"Registration witness missing or oversized"}
        return file.inputStream().use {input->val out=java.io.ByteArrayOutputStream();val buf=ByteArray(8192)
            while(true){val n=input.read(buf);if(n<0)break;require(out.size().toLong()+n<=max);out.write(buf,0,n)};out.toByteArray()}
    }
    private fun archive(dir:File,bytes:ByteArray,suffix:String):String {
        val sha=hash(bytes);val file=File(dir,"$sha.$suffix")
        if(file.exists())require(bounded(file,bytes.size).contentEquals(bytes)) else atomic(file,bytes)
        return sha
    }
    private fun atomic(file:File,bytes:ByteArray) {
        val temp=File(file.parentFile,".${file.name}.${java.util.UUID.randomUUID()}.tmp")
        try {java.io.FileOutputStream(temp).use {it.write(bytes);it.fd.sync()}
            if(file.name=="head")beforeCommit(file)
            Files.move(temp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
            // After rename the selection is COMMITTED. A directory-sync error must not report a
            // failed replacement while silently selecting the new registration.
            try { syncDirectory(file.parentFile); if(file.name=="head")durabilityWarning=null }
            catch(failure:Exception) {
                require(bounded(file,bytes.size).contentEquals(bytes)) {"Commit outcome is uncertain; reload registration history"}
                durabilityWarning="Saved locally; power-loss durability could not be confirmed."
            }
        } finally {temp.delete()}
    }
    private fun events(dir:File):List<Head> {
        val pointer=File(dir,"head")
        if(!pointer.exists())return emptyList()
        val selected=bounded(pointer,128).toString(Charsets.US_ASCII).split('\n')
        require(selected.size==3 && selected.last()=="" && HASH.matches(selected[1])) {"Registration head changed"}
        val count=selected[0].toInt();require(count in 1..MAX_EVENTS)
        var digest=selected[1]
        val result=mutableListOf<Head>()
        for(sequence in count downTo 1) {
            val bytes=bounded(File(dir,"$digest.event"),8192)
            require(hash(bytes)==digest) {"Registration event bytes changed"}
            val parts=bytes.toString(Charsets.UTF_8).split('\n')
            require(parts.size==10 && parts[0]=="native-registration-event-v1" && parts[1]==sequence.toString() && HASH.matches(parts[2]) && parts.last()=="") {"Registration history changed"}
            require(parts[3] in setOf("REGISTER","REVOKE") && HASH.matches(parts[4]) && HASH.matches(parts[5]))
            require(parts[6].isNotBlank() && parts[6].length<=120 && parts[6].all {it.code in 32..126})
            Instant.parse(parts[7]);require(parts[8]==kb.revision) {"Registration Knowledge Base revision changed"}
            result+=Head(sequence,digest,parts[3],parts[4],parts[5],parts[6],parts[7])
            digest=parts[2]
        }
        require(digest=="0".repeat(64)) {"Registration history does not end at its origin"}
        return result.reversed()
    }
    private fun <T> locked(id:String,mode:String,block:(File)->T):T=synchronized(LOCK) {
        val dir=scope(id,mode)
        RandomAccessFile(File(dir,"transaction.lock"),"rw").use {raf->raf.channel.lock().use {block(dir)}}
    }
    fun history(id:String,mode:String):List<Head> = locked(id,mode) {events(it).toList()}
    private fun registered(dir:File,head:Head):Registered? {
        if(head.action!="REGISTER")return null
        val rBytes=bounded(File(dir,"${head.reconciliationSha256}.reconciliation"),NativeSourceReconciliationContract.MAX_BYTES)
        val aBytes=bounded(File(dir,"${head.assignmentSha256}.assignment"),NativeAssignmentCodec.MAX_BYTES)
        require(hash(rBytes)==head.reconciliationSha256 && hash(aBytes)==head.assignmentSha256) {"Registration archive changed"}
        val r=NativeSourceReconciliationContract.decode(rBytes);val a=NativeAssignmentCodec.decode(aBytes)
        require(head.actor==r.author && head.at==r.reviewedAtUtc && a.authoritySha256==head.reconciliationSha256)
        return Registered(head,r,a)
    }
    fun active(id:String,mode:String):Registered?=locked(id,mode) {dir->
        val head=events(dir).lastOrNull() ?: return@locked null
        val item=registered(dir,head) ?: return@locked null
        require(item.reconciliation.territory==id && item.reconciliation.mode==mode)
        evidence(item) // Rechecks context/source/inventory every time; stale is never silently active.
        item
    }
    private fun evidence(item:Registered):NativeAssignmentEvidence {
        val r=item.reconciliation
        return NativeAssignmentEvidence.fromCurrentLedger(kb,NativeSourceReconciliationContract.encode(r),item.assignment,r.mode,
            requireNotNull(currentSourceHash(r.territory)) {"Current source witness missing"},currentInventoryContentHash(r.territory,r.mode))
    }
    override fun current(territory:String,authoritySha256:String):NativeAssignmentEvidence? = synchronized(LOCK) {
        if(!HASH.matches(authoritySha256) || territory !in kb.assignments)return@synchronized null
        for(mode in listOf("REGULAR","LETTER_WRITING","TELEPHONE")) {
            val found=runCatching {locked(territory,mode) {dir->
                val head=events(dir).lastOrNull() ?: return@locked null
                if(head.action!="REGISTER" || head.reconciliationSha256!=authoritySha256)return@locked null
                val item=requireNotNull(registered(dir,head))
                require(item.reconciliation.territory==territory && item.reconciliation.mode==mode)
                evidence(item) // Constructed while both the scope and process locks are held.
            }}.getOrNull()
            if(found!=null)return@synchronized found
        }
        null
    }
    /** Caller has displayed the exact source and collected explicit per-item reconciliation.
     * expectedHead is compare-and-set: failed replacement never selects an older/newer draft. */
    fun register(r:NativeSourceReconciliation,draft:CurrentAuthoritativeAssignmentState,expectedHead:String?):Registered = locked(r.territory,r.mode) {dir->
        val existing=events(dir);require(existing.lastOrNull()?.eventSha256==expectedHead) {"Registration changed; review again"}
        require(existing.size<MAX_EVENTS-1) {"Registration history is full; final capacity is reserved for revocation"}
        require(r.predecessorEventSha256==expectedHead) {"Reconciliation belongs to a different registration history"}
        val receipt=NativeSourceReconciliationContract.encode(r);val authority=hash(receipt)
        require(existing.none {it.reconciliationSha256==authority}) {"Reconciliation receipt was already used"}
        existing.lastOrNull()?.let {require(!Instant.parse(r.reviewedAtUtc).isBefore(Instant.parse(it.at))) {"Review timestamp predates registration history"}}
        val assignment=NativeAssignmentCodec.decode(NativeAssignmentCodec.encode(draft.copy(authoritySha256=authority)))
        val beforeSource=currentSourceHash(r.territory);val beforeInventory=currentInventoryContentHash(r.territory,r.mode)
        NativeAssignmentEvidence.fromCurrentLedger(kb,receipt,assignment,r.mode,requireNotNull(beforeSource),beforeInventory)
        require(dir.listFiles().orEmpty().count {it.extension in setOf("assignment","reconciliation")}<1024) {"Registration evidence archive is full"}
        val rHash=archive(dir,receipt,"reconciliation");val aHash=archive(dir,NativeAssignmentCodec.encode(assignment),"assignment")
        require(currentSourceHash(r.territory)==beforeSource && currentInventoryContentHash(r.territory,r.mode)==beforeInventory) {"Source or inventory changed during registration"}
        val head=commit(dir,existing,"REGISTER",rHash,aHash,r.author,r.reviewedAtUtc)
        Registered(head,r,assignment)
    }
    fun revoke(id:String,mode:String,expectedHead:String,actor:String,at:String):Head=locked(id,mode) {dir->
        val history=events(dir);val old=requireNotNull(history.lastOrNull())
        require(old.eventSha256==expectedHead && old.action=="REGISTER") {"Registration changed or already revoked"}
        commit(dir,history,"REVOKE",old.reconciliationSha256,old.assignmentSha256,actor,at)
    }
    private fun commit(dir:File,history:List<Head>,action:String,rHash:String,aHash:String,actor:String,at:String):Head {
        require(history.size<MAX_EVENTS && actor.isNotBlank() && actor==actor.trim() && actor.length<=120 && actor.all {it.code in 32..126})
        Instant.parse(at);require('\n' !in kb.revision && '\r' !in kb.revision)
        val seq=history.size+1
        val bytes=listOf("native-registration-event-v1",seq.toString(),history.lastOrNull()?.eventSha256 ?: "0".repeat(64),action,rHash,aHash,actor,at,kb.revision,"").joinToString("\n").toByteArray(Charsets.UTF_8)
        val eventHash=archive(dir,bytes,"event")
        // Orphan archives from a failed/crashed precommit are never selected or replayed.
        atomic(File(dir,"head"),"$seq\n$eventHash\n".toByteArray(Charsets.US_ASCII))
        return Head(seq,eventHash,action,rHash,aHash,actor,at)
    }
}

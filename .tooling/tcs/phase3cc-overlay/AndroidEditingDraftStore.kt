package com.koenterprises.territorycardstudio

import android.util.AtomicFile
import com.koenterprises.territorycardstudio.core.TerritoryKnowledgeBase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

enum class DraftLabelKind { ROAD, BUILDING }
data class DraftLabelEdit(val kind: DraftLabelKind, val itemId: String, val before: String,
    val proposed: String, val rationale: String, val evidenceSha256: String)
data class EditingDraftRevision(val number: Int, val token: String, val action: String,
    val atUtc: String, val restoredFrom: Int?, val edits: List<DraftLabelEdit>)
data class EditingDraft(val draftId: String, val territoryId: String, val mode: WorkspaceMode,
    val baseBinding: String, val revisions: List<EditingDraftRevision>, val stale: Boolean) {
    val latest: EditingDraftRevision get() = revisions.last()
    val status: String get() = if (stale) "STALE_UNVALIDATED_PROPOSAL" else "UNVALIDATED_PROPOSAL"
    val grantsAuthority: Boolean get() = false
}

/** Single-process, app-private proposal journal. Never consumed by the build or approval services. */
class AndroidEditingDraftStore internal constructor(private val root: File,
    private val kb: TerritoryKnowledgeBase, private val sources: SourceMapIntakeStore) {
    companion object {
        const val MAX_EDITS = 64
        const val MAX_REVISIONS = 64
        const val MAX_BYTES = 2 * 1024 * 1024
        private val LOCK = Any() // Serializes all instances in the application's single process.
        private val SHA = Regex("[0-9a-f]{64}")
        private fun hash(text: String) = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        private fun canonical(value: Any?): String = when (value) {
            null, JSONObject.NULL -> "null"
            is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") {
                JSONObject.quote(it) + ":" + canonical(value.get(it))
            }
            is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.get(it)) }
            is String -> JSONObject.quote(value)
            is Int, is Long, is Boolean -> value.toString()
            else -> error("Unsupported draft value")
        }
        private fun keys(value: JSONObject, expected: Set<String>) {
            require(value.keys().asSequence().toSet() == expected) { "Draft schema changed or corrupt" }
        }
        private fun text(value: String, limit: Int) {
            require(value.isNotBlank() && value.length <= limit && value == value.trim() &&
                value.none { it.isISOControl() }) { "Draft text must be trimmed, nonempty and within limits" }
        }
    }
    private data class Base(val binding: String, val labels: Map<Pair<DraftLabelKind,String>,String>, val evidence: Set<String>)
    private fun base(id: String, mode: WorkspaceMode): Base {
        val a = requireNotNull(kb.assignments[id]) { "Unknown territory" }
        require(a.displayId == id && mode in WorkspaceModePolicy.allowedModes(a)) { "Draft territory/mode mismatch" }
        val source = requireNotNull(sources.verifiedRecord(id)) { "Current verified source is required" }
        require(source.canonicalFilename == a.canonicalFilename && source.territoryDisplayId == id)
        val labels = linkedMapOf<Pair<DraftLabelKind,String>,String>()
        a.roads.sortedBy { it.segmentId }.forEach {
            text(it.segmentId,256); require(labels.put(DraftLabelKind.ROAD to it.segmentId,it.name)==null) { "Duplicate road ID" }
        }
        a.buildings.sortedBy { it.buildingId }.forEach {
            text(it.buildingId,256); require(labels.put(DraftLabelKind.BUILDING to it.buildingId,it.label)==null) { "Duplicate building ID" }
        }
        val data = JSONObject().put("schema",1).put("territory",id).put("mode",mode.name)
            .put("kb",kb.revision).put("reference",a.referenceSha256).put("filename",a.canonicalFilename)
            .put("source",source.sha256).put("importedAt",source.importedAtUtc)
            .put("assignmentSources",JSONArray(a.sourceHashes.sorted())).put("status",a.status)
            .put("roadAssignment",a.roadAssignmentStatus).put("buildingAssignment",a.buildingAssignmentStatus)
            .put("roads",JSONArray(a.roads.sortedBy { it.segmentId }.map { it.toString() }))
            .put("buildings",JSONArray(a.buildings.sortedBy { it.buildingId }.map { it.toString() }))
        return Base(hash(canonical(data)),labels,(a.sourceHashes + source.sha256).toSet())
    }
    private fun file(id: String, mode: WorkspaceMode): AtomicFile {
        text(id,80)
        require(root.exists() || root.mkdirs()) { "Cannot create private draft store" }
        return AtomicFile(File(root,hash(id+":"+mode.name)+".json"))
    }
    private fun editJson(e: DraftLabelEdit) = JSONObject().put("kind",e.kind.name).put("item",e.itemId)
        .put("before",e.before).put("proposed",e.proposed).put("rationale",e.rationale).put("evidence",e.evidenceSha256)
    private fun validate(edits: List<DraftLabelEdit>, current: Base? = null) {
        require(edits.size<=MAX_EDITS) { "Too many draft edits" }
        require(edits.map { it.kind to it.itemId }.toSet().size==edits.size) { "Duplicate draft item" }
        edits.forEach {
            text(it.itemId,256); text(it.proposed,256); text(it.rationale,1024)
            require(it.before.length<=256 && it.before.none { c -> c.isISOControl() })
            require(it.proposed!=it.before && SHA.matches(it.evidenceSha256)) { "No-op or invalid evidence" }
            if(current!=null) {
                require(current.labels[it.kind to it.itemId]==it.before) { "Unknown item or changed before-value" }
                require(it.evidenceSha256 in current.evidence) { "Evidence is not bound to this territory/source" }
            }
        }
    }
    private fun readRaw(id: String, mode: WorkspaceMode): JSONObject? {
        val f=file(id,mode)
        if(!f.baseFile.exists() && !File(f.baseFile.path+".bak").exists()) return null
        val raw=f.openRead().use { input ->
            val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true) { val n=input.read(buffer);if(n<0)break;require(out.size()+n<=MAX_BYTES) { "Draft file exceeds limit" };out.write(buffer,0,n) };out.toByteArray()
        }
        val value=JSONObject(String(raw,Charsets.UTF_8))
        require(canonical(value).toByteArray(Charsets.UTF_8).contentEquals(raw)) { "Draft encoding or trailing data changed" }
        keys(value,setOf("schema","id","territory","mode","binding","revisions"))
        require(value.get("schema")==1 && value.getString("territory")==id && value.getString("mode")==mode.name) { "Draft context/schema mismatch" }
        UUID.fromString(value.getString("id"));require(SHA.matches(value.getString("binding")))
        val rows=value.getJSONArray("revisions");require(rows.length() in 1..MAX_REVISIONS)
        var previous=""
        for(i in 0 until rows.length()) {
            val row=rows.getJSONObject(i)
            keys(row,setOf("number","action","at","restoredFrom","edits","previous","token"))
            require(row.get("number")==i && row.getString("previous")==previous) { "Draft revision chain changed" }
            require(row.getString("action") in if(i==0) setOf("CREATE") else setOf("SAVE","RESTORE"))
            Instant.parse(row.getString("at"))
            if(row.getString("action")=="RESTORE") {
                val n=row.get("restoredFrom");require(n is Int && n in 0 until i)
                require(canonical(row.getJSONArray("edits"))==canonical(rows.getJSONObject(n).getJSONArray("edits")))
            } else require(row.isNull("restoredFrom"))
            val edits=parseEdits(row.getJSONArray("edits"));validate(edits)
            if(i==0) require(edits.isEmpty())
            val token=row.getString("token");require(SHA.matches(token))
            row.remove("token")
            val actual=revisionHash(value,row)
            row.put("token",token);require(token==actual) { "Draft integrity check failed" };previous=token
        }
        return value
    }
    private fun parseEdits(rows: JSONArray): List<DraftLabelEdit> {
        require(rows.length()<=MAX_EDITS)
        return (0 until rows.length()).map { i -> val e=rows.getJSONObject(i)
            keys(e,setOf("kind","item","before","proposed","rationale","evidence"))
            DraftLabelEdit(DraftLabelKind.valueOf(e.getString("kind")),e.getString("item"),e.getString("before"),
                e.getString("proposed"),e.getString("rationale"),e.getString("evidence"))
        }
    }
    private fun revisionHash(root: JSONObject, row: JSONObject) = hash(canonical(JSONObject()
        .put("id",root.getString("id")).put("territory",root.getString("territory")).put("mode",root.getString("mode"))
        .put("binding",root.getString("binding")).put("revision",row)))
    private fun view(raw: JSONObject): EditingDraft {
        val id=raw.getString("territory");val mode=WorkspaceMode.valueOf(raw.getString("mode"))
        val binding=raw.getString("binding");val rows=raw.getJSONArray("revisions")
        return EditingDraft(raw.getString("id"),id,mode,binding,(0 until rows.length()).map { i ->
            val r=rows.getJSONObject(i);EditingDraftRevision(i,r.getString("token"),r.getString("action"),r.getString("at"),
                if(r.isNull("restoredFrom")) null else r.getInt("restoredFrom"),parseEdits(r.getJSONArray("edits")))
        },runCatching { base(id,mode).binding!=binding }.getOrDefault(true))
    }
    private fun append(raw: JSONObject, action: String, edits: List<DraftLabelEdit>, restored: Int? = null) {
        val rows=raw.getJSONArray("revisions");require(rows.length()<MAX_REVISIONS) { "Draft revision limit reached" }
        val row=JSONObject().put("number",rows.length()).put("action",action).put("at",Instant.now().toString())
            .put("restoredFrom",restored ?: JSONObject.NULL).put("edits",JSONArray(edits.map(::editJson)))
            .put("previous",if(rows.length()==0) "" else rows.getJSONObject(rows.length()-1).getString("token"))
        row.put("token",revisionHash(raw,row));rows.put(row)
    }
    private fun write(id: String, mode: WorkspaceMode, raw: JSONObject) {
        val bytes=canonical(raw).toByteArray(Charsets.UTF_8);require(bytes.size<=MAX_BYTES)
        val atomic=file(id,mode);val output=atomic.startWrite()
        try { output.write(bytes);atomic.finishWrite(output) } catch(t: Throwable) { atomic.failWrite(output);throw t }
    }
    fun read(id: String, mode: WorkspaceMode): EditingDraft? = synchronized(LOCK) { readRaw(id,mode)?.let(::view) }
    fun create(id: String, mode: WorkspaceMode): EditingDraft = synchronized(LOCK) {
        require(readRaw(id,mode)==null) { "Draft already exists" }
        val current=base(id,mode)
        val raw=JSONObject().put("schema",1).put("id",UUID.randomUUID().toString()).put("territory",id)
            .put("mode",mode.name).put("binding",current.binding).put("revisions",JSONArray())
        append(raw,"CREATE",emptyList())
        require(base(id,mode).binding==current.binding) { "Draft base changed during creation" }
        write(id,mode,raw);view(raw)
    }
    private fun current(id: String, mode: WorkspaceMode, token: String): Pair<JSONObject,Base> {
        val raw=requireNotNull(readRaw(id,mode)) { "No draft" };val draft=view(raw)
        require(draft.latest.token==token) { "Draft changed; reload before saving" }
        val b=base(id,mode);require(b.binding==draft.baseBinding) { "Draft base is stale; discard and start again" }
        return raw to b
    }
    fun save(id: String, mode: WorkspaceMode, token: String, edits: List<DraftLabelEdit>): EditingDraft = synchronized(LOCK) {
        val (raw,b)=current(id,mode,token);val copy=edits.toList();validate(copy,b)
        require(copy!=view(raw).latest.edits) { "No draft changes" }
        append(raw,"SAVE",copy);require(base(id,mode).binding==b.binding) { "Draft base changed during save" }
        write(id,mode,raw);view(raw)
    }
    fun restore(id: String, mode: WorkspaceMode, token: String, revision: Int): EditingDraft = synchronized(LOCK) {
        val (raw,b)=current(id,mode,token);val old=view(raw)
        require(revision in 0 until old.latest.number) { "Restore must select an earlier revision" }
        val edits=old.revisions[revision].edits;validate(edits,b);append(raw,"RESTORE",edits,revision)
        require(base(id,mode).binding==b.binding);write(id,mode,raw);view(raw)
    }
    fun discard(id: String, mode: WorkspaceMode, token: String) = synchronized(LOCK) {
        val raw=requireNotNull(readRaw(id,mode)) { "No draft" }
        require(view(raw).latest.token==token) { "Draft changed; reload before discarding" }
        file(id,mode).delete()
        require(readRaw(id,mode)==null) { "Draft could not be discarded" }
    }
    /** Capture under the journal lock; checking the resulting witness never acquires that lock. */
    internal fun revisionWitness(id:String,mode:WorkspaceMode):EditingJournalWitness=synchronized(LOCK) {
        readRaw(id,mode) // Validate/recover only here, before entering coordinator locks.
        EditingJournalWitness.capture(file(id,mode).baseFile,MAX_BYTES)
    }

}

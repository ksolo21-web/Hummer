package com.koenterprises.territorycardstudio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import com.koenterprises.territorycardstudio.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

data class NativeContactDraft(val id:String,val address:String,val unit:String,val city:String,val state:String,val postalCode:String,val buildingId:String,
    val sourceSha256:String,val sourceLabel:String,val verifiedAt:String,val addressConfirmed:Boolean,val boundaryConfirmed:Boolean,
    val addressUseAuthorized:Boolean,val telephoneUseAuthorized:Boolean,val phoneState:String,val phone:String,val phoneBindingConfirmed:Boolean)
data class NativeAuthoringDraft(val assignment:CurrentAuthoritativeAssignmentState,val reconciliation:NativeSourceReconciliation,
    val jurisdiction:VerificationJurisdiction,val contacts:List<NativeContactDraft>,val revisionSha256:String="",val imageReview:NativeImageReview?=null,val outlinedReview:OutlinedNativeReview?=null)
data class NativeInventoryDocument(val sha256:String,val label:String)

/** Initial drafts and separately imported contact evidence. Drafts grant no authority. */
class AndroidNativeDraftStore internal constructor(private val context:Context,private val kb:TerritoryKnowledgeBase,
    private val sources:SourceMapIntakeStore,private val root:File) {
    companion object {const val MAX_DRAFT_BYTES=4*1024*1024;const val MAX_CONTACT_SOURCE_BYTES=4*1024*1024}
    private val documents=File(root,"contact-sources")
    private val history=File(root,"draft-history")
    private val mapEvidence=File(root,"map-evidence")
    private fun archive(d:NativeAuthoringDraft) {
        val bytes=encode(d);val sha=hash(bytes)
        require(history.isDirectory || history.mkdirs())
        val f=AtomicFile(File(history,"$sha.draft"))
        require(f.baseFile.exists() || history.listFiles().orEmpty().count {it.extension=="draft"}<512) {"Draft history is full. Remove unused draft history before saving."}
        if(!f.baseFile.exists())write(f,bytes)
        require(f.openRead().use {hash(it.readBytes())}==sha) {"Archived draft changed"}
    }
    private fun preserveMap(id:String,expected:String) {
        val source=requireNotNull(sources.verifiedFile(id));require(mapEvidence.isDirectory || mapEvidence.mkdirs())
        val target=AtomicFile(File(mapEvidence,"$expected.source"))
        val bytes=source.readBytes();require(hash(bytes)==expected)
        if(!target.baseFile.exists())write(target,bytes)
        require(target.openRead().use {hash(it.readBytes())}==expected) {"Archived map changed"}
    }
    /** Explicit cleanup preserves every selected draft and every registration attempt's evidence. */
    @Synchronized fun pruneUnusedDraftHistory(pendingContactSources:Set<String> = emptySet()):Int {
        val protected=root.listFiles().orEmpty().filter {it.extension=="draft"}.map {hash(AtomicFile(it).openRead().use {s->s.readBytes()})}.toSet()+
            history.listFiles().orEmpty().filter {it.extension=="registered"}.map {it.nameWithoutExtension}
        var removed=0
        history.listFiles().orEmpty().filter {it.extension=="draft" && it.nameWithoutExtension !in protected}.forEach {if(it.delete())removed++}
        val referenced=(history.listFiles().orEmpty().filter {it.extension=="draft"}+root.listFiles().orEmpty().filter {it.extension=="draft"}).flatMap {f->
            val rows=JSONObject(AtomicFile(f).openRead().use {it.readBytes().toString(Charsets.UTF_8)}).getJSONArray("contacts")
            (0 until rows.length()).map {rows.getJSONObject(it).getString("sourceSha256")}
        }.toSet()+pendingContactSources
        documents.listFiles().orEmpty().filter {it.extension=="source" && it.nameWithoutExtension !in referenced}.forEach {if(it.delete())removed++}
        return removed
    }
    /** Evidence-only ZIP: importing it cannot activate registration or approve any PDF. */
    @Synchronized internal fun exportHistory(destination:CreatedExportDestination):String {
        val temp=File.createTempFile("native-evidence-",".zip",context.cacheDir)
        try {
            fun files()=root.walkTopDown().filter {it.isFile && it.extension in setOf("draft","registered","source","assignment","reconciliation","event") || it.isFile && it.name=="head"}.sortedBy {it.relativeTo(root).path}.toList()
            val selected=files();require(selected.isNotEmpty()) {"No native evidence to export"}
            val manifest=selected.associate {it.relativeTo(root).invariantSeparatorsPath to it.inputStream().use(BundleIntegrity::sha256)}
            java.util.zip.ZipOutputStream(temp.outputStream()).use {zip->
                for(f in selected){val name=f.relativeTo(root).invariantSeparatorsPath;zip.putNextEntry(java.util.zip.ZipEntry(name));f.inputStream().use {it.copyTo(zip)};zip.closeEntry()}
                zip.putNextEntry(java.util.zip.ZipEntry("manifest.json"));zip.write(ExtendedValues.canonical(JSONObject().put("schema","native-evidence-archive-v1").put("purpose","Evidence only; no assignment activation or PDF approval").put("sha256",JSONObject(manifest))).toByteArray());zip.closeEntry()
            }
            require(files()==selected && selected.all {it.inputStream().use(BundleIntegrity::sha256)==manifest[it.relativeTo(root).invariantSeparatorsPath]}) {"Evidence changed during export; retry"}
            val expected=temp.inputStream().use(BundleIntegrity::sha256)
            destination.openOutput().use {out->temp.inputStream().use {it.copyTo(out)}}
            val received=destination.openInput().use {input->
                val digest=java.security.MessageDigest.getInstance("SHA-256");val buffer=ByteArray(8192);var count=0L
                while(true){val n=input.read(buffer);if(n<0)break;count+=n;require(count<=temp.length()) {"Saved evidence length changed"};digest.update(buffer,0,n)}
                require(count==temp.length());digest.digest().joinToString(""){"%02x".format(it)}
            }
            require(received==expected) {"Saved evidence did not match; export failed"}
            return expected
        } catch(e:Exception){runCatching {destination.deleteCreated()};throw e} finally {temp.delete()}
    }
    fun contactSource(document:NativeInventoryDocument):File {
        require(contactWitness(document.sha256)) {"Contact source bytes are missing or changed"}
        return File(documents,"${document.sha256}.source")
    }
    val ledger=NativeRegistrationLedger(File(root,"registrations"),kb,
        {id->sources.verifiedRecord(id)?.sha256},
        {id,mode->read(id,WorkspaceMode.valueOf(mode))?.let {inventoryContentSha256(it)}},
        {id,mode->read(id,WorkspaceMode.valueOf(mode))?.let {NativeSourceReconciliationContract.assignmentContentSha256(it.assignment)}},
        {id,mode->read(id,WorkspaceMode.valueOf(mode))?.let {d->require(d.imageReview?.complete(d.assignment.roads)!=false && d.outlinedReview?.complete(d.assignment.roads)!=false) {"Review the automatic image findings first"};NativeSourceReconciliationContract.draftFactsSha256(d.reconciliation)}})
    private fun hash(b:ByteArray)=BundleIntegrity.sha256(b.inputStream())
    private fun file(id:String,mode:WorkspaceMode):AtomicFile {
        val slot=requireNotNull(kb.assignments[id]);require(mode in WorkspaceModePolicy.allowedModes(slot))
        require(root.isDirectory || root.mkdirs())
        return AtomicFile(File(root,hash("$id:${mode.name}".toByteArray())+".draft"))
    }
    private fun write(f:AtomicFile,b:ByteArray) {val out=f.startWrite();try {out.write(b);out.fd.sync();f.finishWrite(out)}catch(e:Exception){f.failWrite(out);throw e}}
    private fun contact(c:NativeContactDraft)=JSONObject().put("id",c.id).put("address",c.address).put("unit",c.unit).put("city",c.city).put("state",c.state).put("postalCode",c.postalCode).put("buildingId",c.buildingId)
        .put("sourceSha256",c.sourceSha256).put("sourceLabel",c.sourceLabel).put("verifiedAt",c.verifiedAt).put("addressConfirmed",c.addressConfirmed).put("boundaryConfirmed",c.boundaryConfirmed)
        .put("addressUseAuthorized",c.addressUseAuthorized).put("telephoneUseAuthorized",c.telephoneUseAuthorized).put("phoneState",c.phoneState).put("phone",c.phone).put("phoneBindingConfirmed",c.phoneBindingConfirmed)
    private fun contact(o:JSONObject):NativeContactDraft {
        ExtendedValues.keys(o,"id","address","unit","city","state","postalCode","buildingId","sourceSha256","sourceLabel","verifiedAt","addressConfirmed","boundaryConfirmed","addressUseAuthorized","telephoneUseAuthorized","phoneState","phone","phoneBindingConfirmed")
        return NativeContactDraft(o.getString("id"),o.getString("address"),o.getString("unit"),o.getString("city"),o.getString("state"),o.getString("postalCode"),o.getString("buildingId"),o.getString("sourceSha256"),o.getString("sourceLabel"),o.getString("verifiedAt"),o.getBoolean("addressConfirmed"),o.getBoolean("boundaryConfirmed"),o.getBoolean("addressUseAuthorized"),o.getBoolean("telephoneUseAuthorized"),o.getString("phoneState"),o.getString("phone"),o.getBoolean("phoneBindingConfirmed"))
    }
    private fun encode(d:NativeAuthoringDraft):ByteArray {
        require(d.contacts.size<=240 && d.contacts.map {it.id}.distinct().size==d.contacts.size)
        val o=JSONObject().put("schema",if(d.outlinedReview==null)"native-draft-v2" else "native-draft-v3").put("assignment",NativeAssignmentCodec.encode(d.assignment).toString(Charsets.UTF_8))
            .put("reconciliation",NativeSourceReconciliationContract.encode(d.reconciliation).toString(Charsets.UTF_8))
            .put("jurisdiction",JSONObject().put("county",d.jurisdiction.county ?: JSONObject.NULL).put("state",d.jurisdiction.state).put("country",d.jurisdiction.country))
            .put("contacts",JSONArray(d.contacts.map(::contact))).put("imageReview",d.imageReview?.let {org.json.JSONObject().put("analysis",it.analysisJson).put("acknowledged",org.json.JSONArray(it.acknowledged.sorted()))} ?: org.json.JSONObject.NULL)
        if(d.outlinedReview!=null)o.put("outlinedReview",d.outlinedReview.document)
        return ExtendedValues.canonical(o).toByteArray().also {require(it.size<=MAX_DRAFT_BYTES)}
    }
    @Synchronized fun read(id:String,mode:WorkspaceMode):NativeAuthoringDraft? {
        val f=file(id,mode);if(!f.baseFile.exists())return null
        val bytes=f.openRead().use {AndroidFinalOutputService.readBounded(it,MAX_DRAFT_BYTES)}
        val text=Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        var depth=0;var quoted=false;var escaped=false
        for(c in text){if(quoted){if(escaped)escaped=false else if(c=='\\')escaped=true else if(c=='"')quoted=false}else when(c){'"'->quoted=true;'[','{'->{depth++;require(depth<=16)};']','}'->{depth--;require(depth>=0)}}};require(!quoted && depth==0)
        val o=JSONObject(text)
        val schema=o.getString("schema");require(schema in setOf("native-draft-v2","native-draft-v3"))
        if(schema=="native-draft-v2")ExtendedValues.keys(o,"schema","assignment","reconciliation","jurisdiction","contacts","imageReview")
        else ExtendedValues.keys(o,"schema","assignment","reconciliation","jurisdiction","contacts","imageReview","outlinedReview")
        val a=NativeAssignmentCodec.decode(o.getString("assignment").toByteArray(Charsets.UTF_8))
        val r=NativeSourceReconciliationContract.decode(o.getString("reconciliation").toByteArray(Charsets.UTF_8))
        val j=o.getJSONObject("jurisdiction");ExtendedValues.keys(j,"county","state","country")
        val contacts=o.getJSONArray("contacts");require(contacts.length()<=240)
        val image=if(o.isNull("imageReview"))null else o.getJSONObject("imageReview").let {v->
            ExtendedValues.keys(v,"analysis","acknowledged");val ack=v.getJSONArray("acknowledged");require(ack.length()<=4096)
            NativeImageReview(v.getString("analysis"),(0 until ack.length()).map {ack.getString(it)}.toSet())
        }
        val outlined=if(schema=="native-draft-v3")OutlinedNativeReview(o.getString("outlinedReview")) else null
        require(image==null || outlined==null)
        require((outlined?.sha256 ?: image?.sha256)==r.imageInterpretationSha256)
        outlined?.let {require(it.sourceSha256==r.importedSourceSha256)}
        image?.let {require(JSONObject(it.analysisJson).getString("sourceSha256")==r.importedSourceSha256)}
        val d=NativeAuthoringDraft(a,r,VerificationJurisdiction(if(j.isNull("county"))null else j.getString("county"),j.getString("state"),j.getString("country")),(0 until contacts.length()).map {contact(contacts.getJSONObject(it))},hash(bytes),image,outlined)
        require(a.displayId==id && r.territory==id && r.mode==mode.name && a.knowledgeBaseRevision==kb.revision && r.knowledgeBaseRevision==kb.revision)
        require(encode(d).contentEquals(bytes)) {"Native draft encoding changed"};return d
    }
    @Synchronized fun save(d:NativeAuthoringDraft,expectedRevision:String?):NativeAuthoringDraft {
        val id=d.assignment.displayId;val mode=WorkspaceMode.valueOf(d.reconciliation.mode)
        val slot=requireNotNull(kb.assignments[id]);require(slot.needsNewCard)
        val previous=read(id,mode)
        require(previous?.revisionSha256==expectedRevision) {"Draft changed; reload before saving"}
        val source=requireNotNull(sources.verifiedRecord(id));require(source.sha256==d.reconciliation.importedSourceSha256)
        require(d.reconciliation.territory==id && d.assignment.identity==slot.identity && d.assignment.canonicalFilename==slot.canonicalFilename && d.assignment.knowledgeBaseRevision==kb.revision && d.reconciliation.knowledgeBaseRevision==kb.revision && d.reconciliation.lockedReferenceSha256==slot.referenceSha256 && d.assignment.housingType==slot.housingType)
        if(previous!=null && previous.reconciliation.importedSourceSha256!=source.sha256) {
            require(!d.reconciliation.sourceCoverageComplete && d.reconciliation.segments.none {it.confirmed} && d.reconciliation.buildings.none {it.confirmed} && d.contacts.none {it.boundaryConfirmed}) {"Source changed; clear prior item and boundary confirmations before reconciling the replacement"}
        }
        val outlinedWitness=AtomicFile(File(root,"outlined-source-${source.sha256}.mode"))
        val previouslyOutlined=previous?.reconciliation?.importedSourceSha256==source.sha256 && previous.outlinedReview!=null
        require((!outlinedWitness.baseFile.exists() && !previouslyOutlined) || d.outlinedReview!=null) {"This source requires its outlined-map review; it cannot be saved as a manual draft"}
        require(d.imageReview==null || d.outlinedReview==null)
        require((d.outlinedReview?.sha256 ?: d.imageReview?.sha256)==d.reconciliation.imageInterpretationSha256)
        d.outlinedReview?.let {require(it.sourceSha256==source.sha256)}
        d.imageReview?.let {require(JSONObject(it.analysisJson).getString("sourceSha256")==source.sha256)}
        val reset=d.copy(assignment=d.assignment.copy(authoritySha256="0".repeat(64)),reconciliation=d.reconciliation.copy(
            assignmentContentSha256=NativeSourceReconciliationContract.assignmentContentSha256(d.assignment),explicitAssignmentConfirmation=false))
        previous?.let(::archive);archive(reset);preserveMap(id,source.sha256)
        if(d.outlinedReview!=null)write(outlinedWitness,source.sha256.toByteArray(Charsets.UTF_8))
        write(file(id,mode),encode(reset));return requireNotNull(read(id,mode))
    }
    fun importContactSource(uri:Uri):NativeInventoryDocument {
        val resolver=context.contentResolver
        val mime=resolver.getType(uri);require(mime in setOf("application/pdf","text/plain","text/csv")) {"Choose a PDF, text or CSV contact source"}
        val label=resolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {if(it.moveToFirst())it.getString(0) else null} ?: "User provided list"
        ExtendedValues.text(label)
        val bytes=requireNotNull(resolver.openInputStream(uri)).use {AndroidFinalOutputService.readBounded(it,MAX_CONTACT_SOURCE_BYTES)};require(bytes.isNotEmpty())
        if(mime=="application/pdf")require(bytes.take(5).toByteArray().toString(Charsets.US_ASCII)=="%PDF-")
        require(documents.isDirectory || documents.mkdirs());val sha=hash(bytes);val f=AtomicFile(File(documents,"$sha.source"))
        require(f.baseFile.exists() || documents.listFiles().orEmpty().count {it.extension=="source"}<128) {"Contact evidence archive is full; preserve evidence before adding more"}
        if(!f.baseFile.exists())write(f,bytes)
        require(contactWitness(sha));return NativeInventoryDocument(sha,label)
    }
    private fun contactWitness(sha:String):Boolean=runCatching {
        require(sha.matches(Regex("[0-9a-f]{64}")))
        val bytes=AtomicFile(File(documents,"$sha.source")).openRead().use {AndroidFinalOutputService.readBounded(it,MAX_CONTACT_SOURCE_BYTES)}
        hash(bytes)==sha
    }.getOrDefault(false)
    fun inventoryContentSha256(d:NativeAuthoringDraft):String? = when(val v=inventory(d,"0".repeat(64))) {
        is Page2Inventory.LetterWriting->v.inventory.canonicalSha256();is Page2Inventory.Telephone->v.inventory.canonicalSha256();null->null
    }
    fun inventory(d:NativeAuthoringDraft,authority:String):Page2Inventory? {
        val mode=WorkspaceMode.valueOf(d.reconciliation.mode)
        if(mode==WorkspaceMode.REGULAR){require(d.contacts.isEmpty());return null}
        require(d.contacts.isNotEmpty()) {"Add independently sourced working addresses"}
        d.contacts.forEach {c->
            require(c.addressConfirmed && c.boundaryConfirmed && c.addressUseAuthorized) {"Address and boundary reconciliation is incomplete"}
            require(contactWitness(c.sourceSha256)) {"Contact source bytes are missing or changed"}
            listOf(c.id,c.address,c.sourceLabel,c.verifiedAt).forEach {ExtendedValues.text(it)}
            Instant.parse(c.verifiedAt)
            if(mode==WorkspaceMode.TELEPHONE) {
                require(c.phoneState in setOf("UNAVAILABLE","VERIFIED_NUMBER")) {"Choose a verified number or explicitly mark unavailable"}
                require(c.telephoneUseAuthorized && c.phoneBindingConfirmed) {"Verify and authorize the number availability in the contact source"}
                if(c.phoneState=="VERIFIED_NUMBER")require(c.phone.isNotBlank()) {"The number needs independent source authorization and address binding"}
                if(c.phoneState=="UNAVAILABLE")require(c.phone.isEmpty()) {"Unavailable must not contain a number"}
            }
        }
        val id=d.assignment.identity;val source=d.reconciliation.importedSourceSha256
        if(mode==WorkspaceMode.LETTER_WRITING) {
            val v=LetterWritingAddressInventory(id,authority,kb.revision,true,d.contacts.map {c->LetterWritingAddressProvenance(c.id,c.sourceLabel,"USER_PROVIDED_LIST",c.verifiedAt,c.sourceSha256,c.addressUseAuthorized)},
                d.contacts.map {c->LetterWritingAddressRecord(c.id,id.displayId,c.address,c.unit.ifBlank{null},c.city.ifBlank{null},c.state.ifBlank{null},c.postalCode.ifBlank{null},c.buildingId.ifBlank{null},LetterWritingVerificationStatus.VERIFIED,c.verifiedAt,LetterWritingBoundaryStatus.INSIDE_LOCKED_WORKING_AREA,source,provenanceIds=listOf(c.id))})
            val report=LetterWritingAddressInventoryValidator.validateForPage2(v);require(report.passed){report.errors.joinToString("; ")};return Page2Inventory.LetterWriting(v)
        }
        val v=TelephoneTerritoryInventory(id,authority,kb.revision,true,d.contacts.map {c->TelephoneProvenance(c.id,c.sourceLabel,"USER_PROVIDED_LIST",c.verifiedAt,c.sourceSha256,TelephoneSourceAuthorization.USER_PROVIDED,c.addressUseAuthorized,c.telephoneUseAuthorized)},
            d.contacts.map {c->TelephoneTerritoryRecord(c.id,id.displayId,c.address,c.unit.ifBlank{null},c.city.ifBlank{null},c.state.ifBlank{null},c.postalCode.ifBlank{null},c.buildingId.ifBlank{null},TelephoneRecordVerificationStatus.VERIFIED,c.verifiedAt,TelephoneBoundaryStatus.INSIDE_LOCKED_WORKING_AREA,source,addressProvenanceIds=listOf(c.id),phoneState=TelephoneNumberState.valueOf(c.phoneState),phoneNumber=c.phone.ifBlank{null},phoneVerifiedAtUtc=c.verifiedAt,phoneRecordBindingVerified=c.phoneBindingConfirmed,phoneProvenanceIds=listOf(c.id))})
        val report=TelephoneTerritoryInventoryValidator.validateForPage2(v);require(report.passed){report.errors.joinToString("; ")};return Page2Inventory.Telephone(v)
    }
    fun register(id:String,mode:WorkspaceMode,expectedRevision:String):NativeRegistrationLedger.Registered {
        val d=requireNotNull(read(id,mode));require(d.revisionSha256==expectedRevision) {"Draft changed; review again"}
        val prior=ledger.history(id,mode.name).lastOrNull()?.eventSha256
        val r=d.reconciliation.copy(author=d.reconciliation.author,reviewedAtUtc=Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
            registrationId=UUID.randomUUID().toString(),predecessorEventSha256=prior,explicitAssignmentConfirmation=true,
            inventorySha256=inventoryContentSha256(d),assignmentContentSha256=NativeSourceReconciliationContract.assignmentContentSha256(d.assignment))
        synchronized(this) {
            archive(d);preserveMap(id,d.reconciliation.importedSourceSha256)
            // Write the preservation marker first. A failed registration safely over-retains evidence.
            write(AtomicFile(File(history,"${d.revisionSha256}.registered")),NativeSourceReconciliationContract.encode(r))
        }
        return ledger.register(r,d.assignment,prior)
    }
}

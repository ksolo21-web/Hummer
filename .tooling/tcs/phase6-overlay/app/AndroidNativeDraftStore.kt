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
    val jurisdiction:VerificationJurisdiction,val contacts:List<NativeContactDraft>,val revisionSha256:String="")
data class NativeInventoryDocument(val sha256:String,val label:String)

/** Initial drafts and separately imported contact evidence. Drafts grant no authority. */
class AndroidNativeDraftStore internal constructor(private val context:Context,private val kb:TerritoryKnowledgeBase,
    private val sources:SourceMapIntakeStore,private val root:File) {
    companion object {const val MAX_DRAFT_BYTES=4*1024*1024;const val MAX_CONTACT_SOURCE_BYTES=4*1024*1024}
    private val documents=File(root,"contact-sources")
    val ledger=NativeRegistrationLedger(File(root,"registrations"),kb,
        {id->sources.verifiedRecord(id)?.sha256},
        {id,mode->read(id,WorkspaceMode.valueOf(mode))?.let {inventoryContentSha256(it)}},
        {id,mode->read(id,WorkspaceMode.valueOf(mode))?.let {NativeSourceReconciliationContract.assignmentContentSha256(it.assignment)}},
        {id,mode->read(id,WorkspaceMode.valueOf(mode))?.let {NativeSourceReconciliationContract.draftFactsSha256(it.reconciliation)}})
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
        val o=JSONObject().put("schema","native-draft-v1").put("assignment",JSONObject(NativeAssignmentCodec.encode(d.assignment).toString(Charsets.UTF_8)))
            .put("reconciliation",JSONObject(NativeSourceReconciliationContract.encode(d.reconciliation).toString(Charsets.UTF_8)))
            .put("jurisdiction",JSONObject().put("county",d.jurisdiction.county ?: JSONObject.NULL).put("state",d.jurisdiction.state).put("country",d.jurisdiction.country))
            .put("contacts",JSONArray(d.contacts.map(::contact)))
        return ExtendedValues.canonical(o).toByteArray().also {require(it.size<=MAX_DRAFT_BYTES)}
    }
    @Synchronized fun read(id:String,mode:WorkspaceMode):NativeAuthoringDraft? {
        val f=file(id,mode);if(!f.baseFile.exists())return null
        val bytes=f.openRead().use {AndroidFinalOutputService.readBounded(it,MAX_DRAFT_BYTES)}
        val o=JSONObject(Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString())
        ExtendedValues.keys(o,"schema","assignment","reconciliation","jurisdiction","contacts");require(o.getString("schema")=="native-draft-v1")
        val a=NativeAssignmentCodec.decode(ExtendedValues.canonical(o.getJSONObject("assignment")).toByteArray())
        val r=NativeSourceReconciliationContract.decode(ExtendedValues.canonical(o.getJSONObject("reconciliation")).toByteArray())
        val j=o.getJSONObject("jurisdiction");ExtendedValues.keys(j,"county","state","country")
        val contacts=o.getJSONArray("contacts");require(contacts.length()<=240)
        val d=NativeAuthoringDraft(a,r,VerificationJurisdiction(if(j.isNull("county"))null else j.getString("county"),j.getString("state"),j.getString("country")),(0 until contacts.length()).map {contact(contacts.getJSONObject(it))},hash(bytes))
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
        val reset=d.copy(assignment=d.assignment.copy(authoritySha256="0".repeat(64)),reconciliation=d.reconciliation.copy(
            assignmentContentSha256=NativeSourceReconciliationContract.assignmentContentSha256(d.assignment),explicitAssignmentConfirmation=false))
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
                if(c.phoneState=="VERIFIED_NUMBER")require(c.telephoneUseAuthorized && c.phoneBindingConfirmed && c.phone.isNotBlank()) {"The number needs independent source authorization and address binding"}
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
            d.contacts.map {c->TelephoneTerritoryRecord(c.id,id.displayId,c.address,c.unit.ifBlank{null},c.city.ifBlank{null},c.state.ifBlank{null},c.postalCode.ifBlank{null},c.buildingId.ifBlank{null},TelephoneRecordVerificationStatus.VERIFIED,c.verifiedAt,TelephoneBoundaryStatus.INSIDE_LOCKED_WORKING_AREA,source,addressProvenanceIds=listOf(c.id),phoneState=TelephoneNumberState.valueOf(c.phoneState),phoneNumber=c.phone.ifBlank{null},phoneVerifiedAtUtc=if(c.phoneState=="VERIFIED_NUMBER")c.verifiedAt else null,phoneRecordBindingVerified=c.phoneBindingConfirmed,phoneProvenanceIds=if(c.phoneState=="VERIFIED_NUMBER")listOf(c.id) else emptyList())})
        val report=TelephoneTerritoryInventoryValidator.validateForPage2(v);require(report.passed){report.errors.joinToString("; ")};return Page2Inventory.Telephone(v)
    }
    fun register(id:String,mode:WorkspaceMode,expectedRevision:String):NativeRegistrationLedger.Registered {
        val d=requireNotNull(read(id,mode));require(d.revisionSha256==expectedRevision) {"Draft changed; review again"}
        val prior=ledger.history(id,mode.name).lastOrNull()?.eventSha256
        val r=d.reconciliation.copy(author=d.reconciliation.author,reviewedAtUtc=Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
            registrationId=UUID.randomUUID().toString(),predecessorEventSha256=prior,explicitAssignmentConfirmation=true,
            inventorySha256=inventoryContentSha256(d),assignmentContentSha256=NativeSourceReconciliationContract.assignmentContentSha256(d.assignment))
        return ledger.register(r,d.assignment,prior)
    }
}

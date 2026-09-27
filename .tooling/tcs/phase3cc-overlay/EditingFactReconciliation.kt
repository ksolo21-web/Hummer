package com.koenterprises.territorycardstudio

import com.koenterprises.territorycardstudio.core.*
import org.json.JSONObject

internal object EditingFactReconciliation {
    private fun address(r:LetterWritingAddressRecord)=AddressValue(r.streetAddress,r.unit.orEmpty(),r.city.orEmpty(),r.state.orEmpty(),r.postalCode.orEmpty(),r.buildingId.orEmpty())
    private fun address(r:TelephoneTerritoryRecord)=AddressValue(r.streetAddress,r.unit.orEmpty(),r.city.orEmpty(),r.state.orEmpty(),r.postalCode.orEmpty(),r.buildingId.orEmpty())
    private fun phone(r:TelephoneTerritoryRecord)=PhoneValue(when(r.phoneState){TelephoneNumberState.VERIFIED_NUMBER->ProposedPhoneState.NUMBER;TelephoneNumberState.UNAVAILABLE->ProposedPhoneState.UNAVAILABLE;else->ProposedPhoneState.UNKNOWN},r.phoneNumber.orEmpty())
    private fun values(input:ProductionRenderModelInput,inventory:Page2Inventory?):Map<String,Any> = buildMap {
        input.assignment.roads.forEach {r->put("ROAD_LABEL:${r.segmentId}",r.name);put("ROAD_PATH:${r.segmentId}",PathValue(r.points));put("ROAD_WORK:${r.segmentId}",WorkValue(r.status,r.role,r.insideSide,r.accessOnly))}
        input.assignment.buildings.forEach {b->put("BUILDING_PATH:${b.buildingId}",PathValue(b.polygon));put("BUILDING_MEMBERS:${b.buildingId}",BuildingValue(b.assigned,b.label,b.sourceMembers,b.labelItems))}
        when(inventory){
            is Page2Inventory.LetterWriting->inventory.inventory.records.forEach {put("LETTER_ADDRESS:${it.recordId}",address(it))}
            is Page2Inventory.Telephone->inventory.inventory.records.forEach {put("PHONE_ADDRESS:${it.recordId}",address(it));put("PHONE_NUMBER:${it.recordId}",phone(it))}
            null->Unit
        }
    }
    /** Compare independently acquired complete facts to exact saved deltas; acquisition never receives drafts. */
    fun reconcile(r:EditingAuthorityReceipt,labels:EditingDraft?,extended:ExtendedDraft?):Pair<ProductionRenderModelInput,Page2Inventory?> {
        val before=values(r.baseline,r.inventory);val expected=before.toMutableMap()
        val edits=labels?.latest?.edits.orEmpty();val proposals=extended?.latest?.proposals.orEmpty()
        require(edits.isNotEmpty() || proposals.isNotEmpty()) {"Save at least one proposal first"}
        require(labels?.stale!=true && extended?.stale!=true) {"A proposal journal is stale"}
        proposals.forEach {p->require(before[p.key]==p.before) {"Proposal before-value differs from verified baseline: ${p.key}"};expected[p.key]=p.proposed}
        edits.forEach {e->
            if(e.kind==DraftLabelKind.ROAD) {
                val k="ROAD_LABEL:${e.itemId}";require(before[k]==e.before) {"Road label baseline changed"};expected[k]=e.proposed
            } else {
                val k="BUILDING_MEMBERS:${e.itemId}";val b=requireNotNull(before[k] as? BuildingValue) {"Unknown building label"}
                require(b.label==e.before) {"Building label baseline changed"}
                val compound=proposals.firstOrNull {it.key==k}?.proposed as? BuildingValue
                require(compound==null || compound.label==e.proposed) {"Building label and compound member proposals contradict each other"}
                expected[k]=(compound ?: b).copy(label=e.proposed)
            }
        }
        val changed=expected.filter {(k,v)->before[k]!=v}.keys
        require(r.facts.none {it.kind in setOf("LETTER_ADDRESS","PHONE_ADDRESS","PHONE_NUMBER") && it.key !in changed}) {"Unrequested inventory verification changes are not allowed"}
        val actual=before.toMutableMap()
        r.facts.forEach {f->require(f.key in before) {"Unknown independent fact"};actual[f.key]=f.value ?: requireNotNull(f.text)}
        require(actual==expected) {"Independent source facts do not exactly match all saved changes; missing or undeclared changes remain"}
        return assemble(r)
    }
    private fun String.optional()=takeIf {isNotEmpty()}
    private fun strings(o:JSONObject,key:String):List<String> {val a=o.getJSONArray(key);return (0 until a.length()).map {a.getString(it)}}
    /** This path only sees independent facts and the verified baseline, never proposal text or flags. */
    private fun assemble(r:EditingAuthorityReceipt):Pair<ProductionRenderModelInput,Page2Inventory?> {
        val f=r.facts.associateBy {it.key};val base=r.baseline
        val roads=base.assignment.roads.map {road->
            val name=f["ROAD_LABEL:${road.segmentId}"]?.text ?: road.name
            val path=f["ROAD_PATH:${road.segmentId}"]?.value as? PathValue
            val work=f["ROAD_WORK:${road.segmentId}"]?.value as? WorkValue
            road.copy(name=name,normalizedName=TopologyOverlapDecisionEngine.normalizeRoadName(name),points=path?.points ?: road.points,
                status=work?.status ?: road.status,role=work?.role ?: road.role,insideSide=work?.insideSide ?: road.insideSide,accessOnly=work?.accessOnly ?: road.accessOnly)
        }
        val buildings=base.assignment.buildings.map {b->
            val path=f["BUILDING_PATH:${b.buildingId}"]?.value as? PathValue;val members=f["BUILDING_MEMBERS:${b.buildingId}"]?.value as? BuildingValue
            b.copy(polygon=path?.points ?: b.polygon,assigned=members?.assigned ?: b.assigned,label=members?.label ?: b.label,sourceMembers=members?.members ?: b.sourceMembers,labelItems=members?.labels ?: b.labelItems)
        }
        val labels=base.labels.map {l->val name=f["ROAD_LABEL:${l.segmentId}"]?.text
            if(name==null)l else l.copy(text=name,placement=l.placement.copy(navigationLock=l.placement.navigationLock.copy(streetName=name)))}
        val inventory=when(val old=r.inventory) {
            is Page2Inventory.LetterWriting -> Page2Inventory.LetterWriting(old.inventory.copy(records=old.inventory.records.map {record->
                val fact=f["LETTER_ADDRESS:${record.recordId}"] ?: return@map record;val a=fact.value as AddressValue;val v=JSONObject(fact.verification)
                require(v.getString("boundarySha") in setOf(record.boundaryEvidenceSha256,base.assignment.authoritySha256)) {"Unrecognized boundary authority"}
                record.copy(streetAddress=a.street,unit=a.unit.optional(),city=a.city.optional(),state=a.state.optional(),postalCode=a.postal.optional(),buildingId=a.building.optional(),
                    verificationStatus=LetterWritingVerificationStatus.valueOf(v.getString("status")),verifiedAtUtc=v.getString("verifiedAt").optional(),
                    boundaryStatus=LetterWritingBoundaryStatus.valueOf(v.getString("boundary")),boundaryEvidenceSha256=v.getString("boundarySha"),neighborTerritoryConflicts=strings(v,"conflicts"),
                    provenanceIds=old.inventory.provenance.filter {it.provenanceId in record.provenanceIds && it.sourceSha256==fact.sourceSha256}.map {it.provenanceId})
            }))
            is Page2Inventory.Telephone -> Page2Inventory.Telephone(old.inventory.copy(records=old.inventory.records.map {record->
                val af=f["PHONE_ADDRESS:${record.recordId}"];val pf=f["PHONE_NUMBER:${record.recordId}"]
                var n=record
                if(af!=null) {val a=af.value as AddressValue;val v=JSONObject(af.verification)
                    require(v.getString("boundarySha") in setOf(record.boundaryEvidenceSha256,base.assignment.authoritySha256)) {"Unrecognized boundary authority"}
                    n=n.copy(streetAddress=a.street,unit=a.unit.optional(),city=a.city.optional(),state=a.state.optional(),postalCode=a.postal.optional(),buildingId=a.building.optional(),
                        addressVerificationStatus=TelephoneRecordVerificationStatus.valueOf(v.getString("status")),addressVerifiedAtUtc=v.getString("verifiedAt").optional(),
                        boundaryStatus=TelephoneBoundaryStatus.valueOf(v.getString("boundary")),boundaryEvidenceSha256=v.getString("boundarySha"),neighborTerritoryConflicts=strings(v,"conflicts"),
                        addressProvenanceIds=old.inventory.provenance.filter {it.provenanceId in record.addressProvenanceIds && it.sourceSha256==af.sourceSha256}.map {it.provenanceId})
                }
                if(pf!=null) {val p=pf.value as PhoneValue;val v=JSONObject(pf.verification);val state=TelephoneNumberState.valueOf(v.getString("state"))
                    require(when(p.state){ProposedPhoneState.NUMBER->state==TelephoneNumberState.VERIFIED_NUMBER;ProposedPhoneState.UNAVAILABLE->state==TelephoneNumberState.UNAVAILABLE;ProposedPhoneState.UNKNOWN->state in setOf(TelephoneNumberState.NEEDS_REVIEW,TelephoneNumberState.CONFLICT)}) {"Independent telephone state disagrees with value"}
                    val ids=record.phoneProvenanceIds+if(record.phoneState==TelephoneNumberState.UNAVAILABLE)record.addressProvenanceIds else emptyList()
                    n=n.copy(phoneState=state,phoneNumber=p.number.optional(),phoneVerifiedAtUtc=v.getString("verifiedAt").optional(),phoneRecordBindingVerified=v.getBoolean("bindingVerified"),
                        phoneProvenanceIds=old.inventory.provenance.filter {it.provenanceId in ids && it.sourceSha256==pf.sourceSha256 && it.telephoneUseAuthorized}.map {it.provenanceId})
                }
                n
            }))
            null->null
        }
        return EditingSnapshots.detach(base.copy(assignment=base.assignment.copy(roads=roads,buildings=buildings),labels=labels)) to EditingSnapshots.detach(inventory)
    }
}

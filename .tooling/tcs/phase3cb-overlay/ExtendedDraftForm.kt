package com.koenterprises.territorycardstudio

import com.koenterprises.territorycardstudio.core.*

internal object ExtendedDraftForm {
    fun fields(v:ExtendedValue):List<Pair<String,String>> = when(v) {
        is PathValue -> v.points.flatMapIndexed {i,p->listOf("Vertex ${i+1} • X" to p.x.toString(),"Vertex ${i+1} • Y" to p.y.toString())}
        is WorkValue -> listOf("Work color" to v.status,"Role" to v.role,"Inside side" to v.insideSide,"Access only" to v.accessOnly.toString())
        is BuildingValue -> listOf("Assigned" to v.assigned.toString(),"Building display label" to v.label)+v.labels.flatMapIndexed {i,l->listOf(
            "Member ${i+1} • label" to l.text,"Member ${i+1} • center X" to l.center.x.toString(),"Member ${i+1} • center Y" to l.center.y.toString(),
            "Member ${i+1} • origin X (optional)" to l.origin?.x?.toString().orEmpty(),"Member ${i+1} • origin Y (optional)" to l.origin?.y?.toString().orEmpty(),
            "Member ${i+1} • angle" to l.angleDeg.toString(),"Member ${i+1} • text size" to l.fontSizePt.toString()))}
        is AddressValue -> listOf("Street address" to v.street,"Unit" to v.unit,"City" to v.city,"State" to v.state,"Postal code" to v.postal,"Building ID (optional)" to v.building)
        is PhoneValue -> listOf("Availability" to v.state.name,"Proposed number" to v.number)
    }
    fun names(kind:ExtendedKind,count:Int):List<String> = when(kind) {
        ExtendedKind.ROAD_PATH,ExtendedKind.BUILDING_PATH -> (0 until count).map {"Vertex ${it/2+1} • ${if(it%2==0)"X" else "Y"}"}
        ExtendedKind.BUILDING_MEMBERS -> listOf("Assigned","Building display label")+(0 until (count-2)/7).flatMap {i->listOf("label","center X","center Y","origin X (optional)","origin Y (optional)","angle","text size").map {"Member ${i+1} • $it"}}
        ExtendedKind.ROAD_WORK -> listOf("Work color","Role","Inside side","Access only")
        ExtendedKind.LETTER_ADDRESS,ExtendedKind.PHONE_ADDRESS -> listOf("Street address","Unit","City","State","Postal code","Building ID (optional)")
        ExtendedKind.PHONE_NUMBER -> listOf("Availability","Proposed number")
    }
    fun parse(kind:ExtendedKind,v:List<String>):ExtendedValue=when(kind) {
        ExtendedKind.ROAD_PATH,ExtendedKind.BUILDING_PATH -> {require(v.size%2==0);PathValue(v.chunked(2).map {Point2D(it[0].toDouble(),it[1].toDouble())})}
        ExtendedKind.ROAD_WORK -> {require(v.size==4);WorkValue(v[0],v[1],v[2],v[3].toBooleanStrict())}
        ExtendedKind.BUILDING_MEMBERS -> {require(v.size>=2 && (v.size-2)%7==0);val labels=v.drop(2).chunked(7).map {a->
            require((a[3].isEmpty())==(a[4].isEmpty())) {"Both origin coordinates are required together"}
            BuildingLabelItem(a[0],Point2D(a[1].toDouble(),a[2].toDouble()),if(a[3].isEmpty())null else Point2D(a[3].toDouble(),a[4].toDouble()),a[5].toDouble(),a[6].toDouble())}
            BuildingValue(v[0].toBooleanStrict(),v[1],labels.map {it.text},labels)}
        ExtendedKind.LETTER_ADDRESS,ExtendedKind.PHONE_ADDRESS -> {require(v.size==6);AddressValue(v[0],v[1],v[2],v[3],v[4],v[5])}
        ExtendedKind.PHONE_NUMBER -> {require(v.size==2);PhoneValue(ProposedPhoneState.valueOf(v[0]),v[1])}
    }
}

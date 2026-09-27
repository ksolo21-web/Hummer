package com.koenterprises.territorycardstudio

import com.koenterprises.territorycardstudio.core.*
import org.json.JSONArray
import org.json.JSONObject

/** Proposal values contain no verification/approval flags. Known record identities live outside these values. */
enum class ExtendedKind(val title:String) {
    ROAD_PATH("Road geometry"), ROAD_WORK("Road working sides"), BUILDING_PATH("Building footprint"),
    BUILDING_MEMBERS("Building assignment and labels"), LETTER_ADDRESS("Letter address"),
    PHONE_ADDRESS("Telephone address"), PHONE_NUMBER("Telephone availability")
}
sealed interface ExtendedValue
 data class PathValue(val points:List<Point2D>):ExtendedValue
 data class WorkValue(val status:String,val role:String,val insideSide:String,val accessOnly:Boolean):ExtendedValue
 data class BuildingValue(val assigned:Boolean,val label:String,val members:List<String>,val labels:List<BuildingLabelItem>):ExtendedValue
 data class AddressValue(val street:String,val unit:String,val city:String,val state:String,val postal:String,val building:String):ExtendedValue
 enum class ProposedPhoneState { NUMBER, UNAVAILABLE, UNKNOWN }
 data class PhoneValue(val state:ProposedPhoneState,val number:String):ExtendedValue
 data class ExtendedEvidence(val role:String,val sha256:String,val title:String)
 data class ExtendedItem(val kind:ExtendedKind,val id:String,val title:String,val before:ExtendedValue,val evidence:List<ExtendedEvidence>) {
    val key:String get()=kind.name+":"+id
 }
 data class ExtendedProposal(val kind:ExtendedKind,val itemId:String,val before:ExtendedValue,val proposed:ExtendedValue,
    val reason:String,val evidenceRole:String,val evidenceSha256:String) { val key:String get()=kind.name+":"+itemId }

internal object ExtendedValues {
    fun text(v:String,max:Int=256,blank:Boolean=false) {require((blank || v.isNotBlank()) && v==v.trim() && v.length<=max && v.none {it.isISOControl()}) {"Use trimmed text within $max characters"}}
    fun keys(o:JSONObject,vararg names:String) {require(o.keys().asSequence().toSet()==names.toSet()) {"Unknown or missing proposal fields"}}
    fun canonical(v:Any?):String=when(v) {
        null,JSONObject.NULL -> "null"
        is JSONObject -> v.keys().asSequence().toList().sorted().joinToString(",","{","}"){JSONObject.quote(it)+":"+canonical(v.get(it))}
        is JSONArray -> (0 until v.length()).joinToString(",","[","]"){canonical(v.get(it))}
        is String -> JSONObject.quote(v)
        is Boolean,is Int,is Long -> v.toString()
        is Double -> {require(v.isFinite());JSONObject.numberToString(v)}
        else -> error("Unsupported proposal field")
    }
    private fun point(p:Point2D)=JSONArray(listOf(p.x,p.y))
    private fun point(a:JSONArray):Point2D {require(a.length()==2);return Point2D(a.getDouble(0),a.getDouble(1))}
    fun json(v:ExtendedValue):JSONObject=when(v) {
        is PathValue -> JSONObject().put("points",JSONArray(v.points.map(::point)))
        is WorkValue -> JSONObject().put("status",v.status).put("role",v.role).put("inside",v.insideSide).put("access",v.accessOnly)
        is BuildingValue -> JSONObject().put("assigned",v.assigned).put("label",v.label).put("members",JSONArray(v.members)).put("labels",JSONArray(v.labels.map {
            JSONObject().put("text",it.text).put("center",point(it.center)).put("origin",it.origin?.let(::point) ?: JSONObject.NULL).put("rotation",it.angleDeg).put("font",it.fontSizePt)
        }))
        is AddressValue -> JSONObject().put("street",v.street).put("unit",v.unit).put("city",v.city).put("state",v.state).put("postal",v.postal).put("building",v.building)
        is PhoneValue -> JSONObject().put("state",v.state.name).put("number",v.number)
    }
    fun parse(kind:ExtendedKind,o:JSONObject):ExtendedValue=when(kind) {
        ExtendedKind.ROAD_PATH,ExtendedKind.BUILDING_PATH -> {keys(o,"points");val a=o.getJSONArray("points");require(a.length()<=512);PathValue((0 until a.length()).map {point(a.getJSONArray(it))})}
        ExtendedKind.ROAD_WORK -> {keys(o,"status","role","inside","access");WorkValue(o.getString("status"),o.getString("role"),o.getString("inside"),o.getBoolean("access"))}
        ExtendedKind.BUILDING_MEMBERS -> {keys(o,"assigned","label","members","labels");val m=o.getJSONArray("members");val a=o.getJSONArray("labels");require(m.length()<=128 && a.length()<=128)
            BuildingValue(o.getBoolean("assigned"),o.getString("label"),(0 until m.length()).map {m.getString(it)},(0 until a.length()).map {i-> val l=a.getJSONObject(i);keys(l,"text","center","origin","rotation","font")
                BuildingLabelItem(l.getString("text"),point(l.getJSONArray("center")),if(l.isNull("origin"))null else point(l.getJSONArray("origin")),l.getDouble("rotation"),l.getDouble("font"))})}
        ExtendedKind.LETTER_ADDRESS,ExtendedKind.PHONE_ADDRESS -> {keys(o,"street","unit","city","state","postal","building");AddressValue(o.getString("street"),o.getString("unit"),o.getString("city"),o.getString("state"),o.getString("postal"),o.getString("building"))}
        ExtendedKind.PHONE_NUMBER -> {keys(o,"state","number");PhoneValue(ProposedPhoneState.valueOf(o.getString("state")),o.getString("number"))}
    }
    fun detached(kind:ExtendedKind,v:ExtendedValue)=parse(kind,JSONObject(canonical(json(v))))
    fun validate(kind:ExtendedKind,v:ExtendedValue) {
        require(detached(kind,v)==v) {"Proposal type does not match category"}
        when(v) {
            is PathValue -> {
                require(v.points.size in (if(kind==ExtendedKind.ROAD_PATH)2 else 3)..512) {"Wrong vertex count"}
                require(v.points.all {it.x.isFinite() && it.y.isFinite() && it.x in 176.0..747.0 && it.y in 20.0..363.0}) {"Vertices must stay within the map panel"}
                require(v.points.zipWithNext().all {(a,b)->a!=b}) {"Consecutive vertices must differ"}
                if(kind==ExtendedKind.BUILDING_PATH) {
                    val b=BuildingGeometry("proposal","", "ancillary",false,"",emptyList(),emptyList(),v.points)
                    require(BuildingValidationEngine.validateBuildings(listOf(b)).passed) {"Building polygon is invalid"}
                }
            }
            is WorkValue -> require(when(v.status) {
                "yellow" -> v.role=="perimeter" && v.insideSide in setOf("left","right") && !v.accessOnly
                "green" -> v.role=="interior" && v.insideSide=="" && !v.accessOnly
                "red" -> v.role=="excluded" && v.insideSide=="" && !v.accessOnly
                "context" -> v.role=="context" && v.insideSide==""
                else -> false
            }) {"Work color, role and inside side must agree for the whole segment"}
            is BuildingValue -> {
                text(v.label,blank=!v.assigned);require(v.members.size<=128 && v.labels.size<=128)
                v.members.forEach {text(it)};v.labels.forEach {text(it.text);require(it.center.x in 176.0..747.0 && it.center.y in 20.0..363.0 && it.angleDeg in -180.0..180.0 && it.fontSizePt in 6.0..32.0);it.origin?.let {p->require(p.x in 176.0..747.0 && p.y in 20.0..363.0)}}
                require(v.members.toSet().size==v.members.size && v.labels.map {it.text}.toSet().size==v.labels.size) {"Duplicate building member or label"}
                require(v.members.sorted()==v.labels.map {it.text}.sorted()) {"Every member requires one matching interior label"}
                if(v.assigned)require(v.members.isNotEmpty()) {"Assigned buildings require member labels"}
                if(v.labels.size==1)require(v.label==v.labels.single().text) {"Building label must match its single member"}
                if(v.labels.size>1)require(v.labels.all {it.origin!=null}) {"Multiple labels require exact origins"}
            }
            is AddressValue -> {text(v.street);listOf(v.unit,v.city,v.state,v.postal,v.building).forEach {text(it,blank=true)};require(v.state.isEmpty() || Regex("[A-Z]{2}").matches(v.state));require(v.postal.isEmpty() || Regex("[0-9]{5}(-[0-9]{4})?").matches(v.postal))}
            is PhoneValue -> {text(v.number,32,true);if(v.state==ProposedPhoneState.NUMBER)require(Regex("[0-9]{10}|1[0-9]{10}").matches(v.number)) {"Use a 10-digit number or 1 plus 10 digits"} else require(v.number.isEmpty()) {"Unavailable/unknown must not carry a number"}}
        }
    }
    fun summary(v:ExtendedValue):String=when(v) {
        is PathValue -> v.points.joinToString(" → "){"${it.x}, ${it.y}"}
        is WorkValue -> "${v.status} • ${v.role}"+(if(v.insideSide.isNotEmpty())" • work ${v.insideSide} side" else "")+(if(v.accessOnly)" • access only" else "")
        is BuildingValue -> "${if(v.assigned)"Assigned" else "Excluded"} • ${v.label}\nMembers: ${v.members.joinToString()}"+v.labels.joinToString("",prefix="\n") {"${it.text}: center ${it.center.x}, ${it.center.y}; origin ${it.origin?.let {p->"${p.x}, ${p.y}"} ?: "centered"}; angle ${it.angleDeg}°; size ${it.fontSizePt} pt\n"}
        is AddressValue -> listOf(v.street,v.unit,v.city,v.state,v.postal,v.building).filter{it.isNotBlank()}.joinToString(", ")
        is PhoneValue -> if(v.state==ProposedPhoneState.NUMBER)v.number else v.state.name
    }
}

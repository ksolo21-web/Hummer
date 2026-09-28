package com.koenterprises.territorycardstudio.core

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Named, bounded geometry format for the native authoring draft. Coordinates are decimal strings
 * so Java/Python JSON number formatting cannot change evidence bytes. No class names/reflection. */
object NativeAssignmentCodec {
    const val MAX_BYTES = 2 * 1024 * 1024
    private fun s(v: String): JsonValue { require(v.length<=4096 && v.all { it.code in 32..126 });return JsonValue.Str(v) }
    private fun n(v: Double): JsonValue {require(v.isFinite());return s(v.toString())}
    private fun b(v: Boolean)=JsonValue.Bool(v)
    private fun o(vararg v:Pair<String,JsonValue>)=JsonValue.Obj(linkedMapOf(*v))
    private fun <T> a(v:List<T>,f:(T)->JsonValue)=JsonValue.Arr(v.map(f))
    private fun point(v:Point2D)=o("x" to n(v.x),"y" to n(v.y))
    private fun label(v:BuildingLabelItem)=o("text" to s(v.text),"center" to point(v.center),"origin" to (v.origin?.let(::point)?:JsonValue.Null),"angleDeg" to n(v.angleDeg),"fontSizePt" to n(v.fontSizePt))
    private fun road(v:RoadGeometry)=o("segmentId" to s(v.segmentId),"name" to s(v.name),"normalizedName" to s(v.normalizedName),"status" to s(v.status),"role" to s(v.role),"insideSide" to s(v.insideSide),"accessOnly" to b(v.accessOnly),"endpointAKind" to s(v.endpointAKind),"endpointBKind" to s(v.endpointBKind),"widthPt" to n(v.widthPt),"points" to a(v.points,::point))
    private fun building(v:BuildingGeometry)=o("buildingId" to s(v.buildingId),"label" to s(v.label),"housingType" to s(v.housingType),"assigned" to b(v.assigned),"attachedGroup" to s(v.attachedGroup),"sourceMembers" to a(v.sourceMembers,::s),"labelItems" to a(v.labelItems,::label),"polygon" to a(v.polygon,::point))
    fun encode(v:CurrentAuthoritativeAssignmentState):ByteArray {
        require(v.identity.displayId==v.displayId && v.roads.size<=512 && v.buildings.size<=512)
        require(v.roads.all {it.points.size in 2..2048} && v.buildings.all {it.polygon.size in 3..2048 && it.labelItems.size<=512 && it.sourceMembers.size<=512})
        val root=o("schema" to s("native-assignment-v1"),"displayId" to s(v.displayId),"canonicalFilename" to s(v.canonicalFilename),"knowledgeBaseRevision" to s(v.knowledgeBaseRevision),"authorityRole" to s(v.authorityRole),"authoritySha256" to s(v.authoritySha256),"sourceMasterLabel" to s(v.sourceMasterLabel),"locality" to s(v.locality),"updated" to s(v.updated),"directionsLines" to a(v.directionsLines,::s),"layoutMode" to s(v.layoutMode),"housingType" to s(v.housingType),"coordinateSpace" to s(v.coordinateSpace.name),"roads" to a(v.roads,::road),"buildings" to a(v.buildings,::building))
        return canonical(root).toByteArray(Charsets.UTF_8).also {require(it.size<=MAX_BYTES)}
    }
    private fun obj(v:JsonValue,vararg keys:String):Map<String,JsonValue> = v.obj("native assignment").also {require(it.keys==keys.toSet()) {"Native assignment schema drift"}}
    private fun Map<String,JsonValue>.s(k:String)=getValue(k).string(k)
    private fun Map<String,JsonValue>.n(k:String)=s(k).toDouble().also {require(it.isFinite())}
    private fun Map<String,JsonValue>.b(k:String)=getValue(k).bool(k)
    private fun <T> Map<String,JsonValue>.a(k:String,f:(JsonValue)->T)=getValue(k).arr(k).also {require(it.size<=2048)}.map(f)
    private fun point(v:JsonValue):Point2D {val o=obj(v,"x","y");return Point2D(o.n("x"),o.n("y"))}
    private fun label(v:JsonValue):BuildingLabelItem {val o=obj(v,"text","center","origin","angleDeg","fontSizePt");return BuildingLabelItem(o.s("text"),point(o.getValue("center")),if(o["origin"]==JsonValue.Null)null else point(o.getValue("origin")),o.n("angleDeg"),o.n("fontSizePt"))}
    private fun road(v:JsonValue):RoadGeometry {val o=obj(v,"segmentId","name","normalizedName","status","role","insideSide","accessOnly","endpointAKind","endpointBKind","widthPt","points");return RoadGeometry(o.s("segmentId"),o.s("name"),o.s("normalizedName"),o.s("status"),o.s("role"),o.s("insideSide"),o.b("accessOnly"),o.s("endpointAKind"),o.s("endpointBKind"),o.n("widthPt"),o.a("points",::point))}
    private fun building(v:JsonValue):BuildingGeometry {val o=obj(v,"buildingId","label","housingType","assigned","attachedGroup","sourceMembers","labelItems","polygon");return BuildingGeometry(o.s("buildingId"),o.s("label"),o.s("housingType"),o.b("assigned"),o.s("attachedGroup"),o.a("sourceMembers"){it.string("member")},o.a("labelItems",::label),o.a("polygon",::point))}
    fun decode(bytes:ByteArray):CurrentAuthoritativeAssignmentState {
        require(bytes.size in 1..MAX_BYTES)
        val text=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        var depth=0;var quote=false;var escape=false
        text.forEach {c->if(quote){if(escape)escape=false else if(c=='\\')escape=true else if(c=='"')quote=false}else when(c){'"'->quote=true;'[','{'->{depth++;require(depth<=12)};']','}'->{depth--;require(depth>=0)}}};require(depth==0 && !quote)
        val o=obj(StrictJson.parse(text.reader()),"schema","displayId","canonicalFilename","knowledgeBaseRevision","authorityRole","authoritySha256","sourceMasterLabel","locality","updated","directionsLines","layoutMode","housingType","coordinateSpace","roads","buildings")
        require(o.s("schema")=="native-assignment-v1")
        val v=CurrentAuthoritativeAssignmentState(o.s("displayId"),TerritoryIdentity.parse(o.s("displayId")),o.s("canonicalFilename"),o.s("knowledgeBaseRevision"),o.s("authorityRole"),o.s("authoritySha256"),o.s("sourceMasterLabel"),o.s("locality"),o.s("updated"),o.a("directionsLines"){it.string("direction")},o.s("layoutMode"),o.s("housingType"),RenderCoordinateSpace.valueOf(o.s("coordinateSpace")),o.a("roads",::road),o.a("buildings",::building))
        require(encode(v).contentEquals(bytes)) {"Use canonical native assignment encoding"};return v
    }
    private fun canonical(v:JsonValue):String=when(v){is JsonValue.Obj->v.values.toSortedMap().entries.joinToString(",","{","}"){canonical(s(it.key))+":"+canonical(it.value)};is JsonValue.Arr->v.values.joinToString(",","[","]",transform=::canonical);is JsonValue.Str->"\""+v.value.replace("\\","\\\\").replace("\"","\\\"")+"\"";is JsonValue.Bool->v.value.toString();JsonValue.Null->"null";is JsonValue.Num->error("Unexpected JSON number")}
}

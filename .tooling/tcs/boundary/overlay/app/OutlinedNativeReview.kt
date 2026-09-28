package com.koenterprises.territorycardstudio

import com.koenterprises.territorycardstudio.core.*
import org.json.JSONArray
import org.json.JSONObject

/** Canonical source-space analysis and typed local review. It grants no PDF approval. */
data class OutlinedNativeReview(val document:String) {
    private val root:JSONObject
    val extraction:OutlinedMapExtraction
    val transform:OutlinedMapTransform
    val sourceSha256:String
    val spans:List<OutlinedSourceSpan>
    val boundaryConfirmed:Boolean
    init {
        require(document.toByteArray().size<=2*1024*1024)
        var depth=0;var quoted=false;var escaped=false
        for(c in document){if(quoted){if(escaped)escaped=false else if(c=='\\')escaped=true else if(c=='"')quoted=false}else when(c){'"'->quoted=true;'[','{'->{depth++;require(depth<=12)};']','}'->{depth--;require(depth>=0)}}};require(!quoted && depth==0)
        root=JSONObject(document)
        ExtendedValues.keys(root,"schema","sourceSha256","analysis","transform","analysisSha256","boundaryConfirmed","spans","resolutions")
        require(root.getString("schema")=="outlined-native-review-v1")
        sourceSha256=root.getString("sourceSha256");require(sourceSha256.matches(Regex("[0-9a-f]{64}")))
        val a=root.getJSONObject("analysis");ExtendedValues.keys(a,"width","height","polygon","enclosedPixels","bounds","roads","findings","recognizedText")
        val width=a.getInt("width");val height=a.getInt("height");require(width in 16..1400 && height in 16..1400)
        val polygon=points(a.getJSONArray("polygon"));require(polygon.size in 3..2048)
        val boundary=OutlinedMapBoundary(width,height,polygon,a.getInt("enclosedPixels"),bounds(a.getJSONArray("bounds")))
        require(boundary.enclosedPixels in 1..width*height)
        val raw=a.getJSONArray("roads");require(raw.length()<=4096)
        val roads=(0 until raw.length()).map {i->val r=raw.getJSONObject(i);ExtendedValues.keys(r,"id","name","points","junctionA","junctionB","relation")
            val path=points(r.getJSONArray("points"));require(path.size>=2)
            OutlinedRoadProposal(MapImageRoad(r.getString("id"),if(r.isNull("name"))null else r.getString("name"),"context",path,r.getBoolean("junctionA"),r.getBoolean("junctionB")),BoundaryRoadRelation.valueOf(r.getString("relation")))}
        val rows=a.getJSONArray("findings");require(rows.length()<=8192)
        val findings=(0 until rows.length()).map {i->val f=rows.getJSONObject(i);ExtendedValues.keys(f,"id","message","bounds")
            MapImageFinding(f.getString("id"),f.getString("message"),if(f.isNull("bounds"))null else bounds(f.getJSONArray("bounds")))}
        require(a.getJSONArray("recognizedText").length()<=10000)
        extraction=OutlinedMapExtraction(boundary,roads,findings)
        require((polygon+roads.flatMap {it.road.points}).all {it.x in 0.0..width.toDouble() && it.y in 0.0..height.toDouble()})
        val t=root.getJSONArray("transform");require(t.length()==3);transform=OutlinedMapTransform(t.getDouble(0),t.getDouble(1),t.getDouble(2))
        require(root.getString("analysisSha256")==OutlinedCoverageContract.analysisSha256(sourceSha256,extraction,transform))
        boundaryConfirmed=root.getBoolean("boundaryConfirmed")
        val s=root.getJSONArray("spans");require(s.length()<=16384)
        spans=(0 until s.length()).map {i->val v=s.getJSONObject(i);ExtendedValues.keys(v,"candidateId","from","to","disposition","outputId","order","reversed","evidence","outputSha256")
            OutlinedSourceSpan(v.getString("candidateId"),v.getDouble("from"),v.getDouble("to"),OutlinedSpanDisposition.valueOf(v.getString("disposition")),if(v.isNull("outputId"))null else v.getString("outputId"),v.getInt("order"),v.getBoolean("reversed"),v.getString("evidence"),v.getString("outputSha256"))}
        require(spans.all {it.from.isFinite() && it.to.isFinite() && it.evidence.length<=2000})
        val resolved=root.getJSONArray("resolutions");require(resolved.length()<=8192)
        val ids=mutableSetOf<String>()
        for(i in 0 until resolved.length()){val v=resolved.getJSONObject(i);ExtendedValues.keys(v,"id","kind","evidence","outputIds","outputSha256")
            require(ids.add(v.getString("id")) && findings.any {it.id==v.getString("id")})
            require(v.getString("kind") in RESOLUTIONS && v.getString("evidence").length in 8..2000)
            require(v.getJSONArray("outputIds").length()<=512)}
        require(ExtendedValues.canonical(root)==document)
    }
    val sha256 get()=BundleIntegrity.sha256(document.byteInputStream())
    private fun changed(block:(JSONObject)->Unit):OutlinedNativeReview {
        val value=JSONObject(document);block(value);return OutlinedNativeReview(ExtendedValues.canonical(value))
    }
    fun confirmBoundary(confirmed:Boolean)=changed {it.put("boundaryConfirmed",confirmed)}
    fun reviewCandidate(id:String,disposition:OutlinedSpanDisposition,evidence:String,roads:List<RoadGeometry>):OutlinedNativeReview {
        return reviewSpan(id,0.0,1.0,disposition,evidence,roads,if(disposition==OutlinedSpanDisposition.ROAD)id else null)
    }
    fun reviewSpan(id:String,from:Double,to:Double,disposition:OutlinedSpanDisposition,evidence:String,roads:List<RoadGeometry>,outputId:String?):OutlinedNativeReview {
        require(extraction.roads.any {it.road.id==id});require(evidence.trim().length in 8..2000)
        require(from.isFinite() && to.isFinite() && from>=0 && to<=1 && to>from)
        val previous=spans.filterNot {it.candidateId==id && it.from==from && it.to==to}
        require(previous.none {it.candidateId==id && maxOf(from,it.from)<minOf(to,it.to)}){"Clear the overlapping decision before replacing its source interval"}
        val r=roads.firstOrNull {it.segmentId==outputId}
        require(disposition!=OutlinedSpanDisposition.ROAD || r!=null)
        val span=OutlinedSourceSpan(id,from,to,disposition,if(disposition==OutlinedSpanDisposition.ROAD)outputId else null,
            evidence=evidence.trim(),reviewedOutputSha256=if(disposition==OutlinedSpanDisposition.ROAD)OutlinedCoverageContract.outputSha256(listOf(requireNotNull(r))) else "")
        return changed {it.put("spans",JSONArray((previous+span).map(::spanJson)))}
    }
    fun clearCandidate(id:String,roads:List<RoadGeometry>):Pair<OutlinedNativeReview,List<RoadGeometry>> {
        val outputs=spans.filter {it.candidateId==id}.mapNotNull {it.outputId}.toSet()
        require(spans.none {it.candidateId!=id && it.outputId in outputs}){"This trace is part of a joined road; undo that join first"}
        return changed {it.put("spans",JSONArray(spans.filterNot {s->s.candidateId==id}.map(::spanJson)))} to roads.filterNot {it.segmentId in outputs}
    }
    fun join(firstId:String,nextId:String,roads:List<RoadGeometry>):Pair<OutlinedNativeReview,List<RoadGeometry>> {
        require(firstId!=nextId)
        val a=requireNotNull(roads.firstOrNull {it.segmentId==firstId});val b=requireNotNull(roads.firstOrNull {it.segmentId==nextId})
        fun same(x:Point2D,y:Point2D)=kotlin.math.hypot(x.x-y.x,x.y-y.y)<1e-7
        val reverse=when {same(a.points.last(),b.points.first())->false;same(a.points.last(),b.points.last())->true;else->error("These roads do not share an exact endpoint. A gap needs separate source-backed geometry.")}
        val bSide=if(reverse)when(b.insideSide){"left"->"right";"right"->"left";else->b.insideSide} else b.insideSide
        require(a.name==b.name && a.normalizedName==b.normalizedName && a.status==b.status && a.role==b.role && a.insideSide==bSide && a.accessOnly==b.accessOnly && a.widthPt==b.widthPt){"Names and work instructions must agree before joining"}
        val aSpans=spans.filter {it.outputId==firstId}.sortedBy {it.outputOrder}
        var bSpans=spans.filter {it.outputId==nextId}.sortedBy {it.outputOrder}
        require(aSpans.isNotEmpty()&&bSpans.isNotEmpty()){"Review both source traces first"}
        require(aSpans.all {it.reviewedOutputSha256==OutlinedCoverageContract.outputSha256(listOf(a))} && bSpans.all {it.reviewedOutputSha256==OutlinedCoverageContract.outputSha256(listOf(b))}){"A road changed since its source review"}
        if(reverse)bSpans=bSpans.reversed().map {it.copy(reversed=!it.reversed)}
        val path=a.points+(if(reverse)b.points.reversed() else b.points).drop(1)
        val id="joined-"+BundleIntegrity.sha256((firstId+"|"+nextId).byteInputStream()).take(16)
        require(roads.none {it.segmentId==id})
        val joined=a.copy(segmentId=id,points=path,endpointBKind=if(reverse)b.endpointAKind else b.endpointBKind)
        val digest=OutlinedCoverageContract.outputSha256(listOf(joined))
        val updated=(aSpans+bSpans).mapIndexed {i,s->s.copy(outputId=id,outputOrder=i,reviewedOutputSha256=digest,evidence=(s.evidence+"; exact continuation reviewed").take(2000))}
        val review=changed {it.put("spans",JSONArray((spans.filterNot {s->s.outputId in setOf(firstId,nextId)}+updated).map(::spanJson)))}
        val output=roads.filterNot {it.segmentId in setOf(firstId,nextId)}+joined
        require(review.coverageFailures(output).none {it.endsWith(":$id")}){"Joined source coverage or work rules did not validate"}
        return review to output
    }
    fun undoJoin(outputId:String,roads:List<RoadGeometry>):Pair<OutlinedNativeReview,List<RoadGeometry>> {
        val linked=spans.filter {it.outputId==outputId};require(linked.size>1){"Choose a joined road"}
        // Removes approval, not source evidence. The original source spans remain available to review.
        return changed {it.put("spans",JSONArray(spans.filterNot {s->s.outputId==outputId}.map(::spanJson)))} to roads.filterNot {it.segmentId==outputId}
    }
    fun reviewFinding(id:String,kind:String,evidence:String,outputIds:List<String>,roads:List<RoadGeometry>):OutlinedNativeReview {
        require(kind in allowedResolutions(id) && evidence.trim().length in 8..2000)
        require(extraction.findings.any {it.id==id && it.sourceBounds!=null})
        val selected=outputIds.map {out->requireNotNull(roads.firstOrNull {it.segmentId==out})}
        require(if(kind=="MATCHED_ROAD")selected.isNotEmpty() else selected.isEmpty())
        val value=JSONObject().put("id",id).put("kind",kind).put("evidence",evidence.trim()).put("outputIds",JSONArray(outputIds))
            .put("outputSha256",OutlinedCoverageContract.outputSha256(selected))
        return changed {v->val a=v.getJSONArray("resolutions");v.put("resolutions",JSONArray((0 until a.length()).map {a.getJSONObject(it)}.filterNot {it.getString("id")==id}+value))}
    }
    fun allowedResolutions(id:String):Set<String> = when {
        id.startsWith("component-")->setOf("OUTSIDE_CONTEXT")
        id.startsWith("unmatched-label-")->setOf("OUTSIDE_CONTEXT")
        else->emptySet()
    }
    fun coverageFailures(roads:List<RoadGeometry>):List<String> = OutlinedCoverageContract.assess(sourceSha256,extraction,transform,
        OutlinedCoverageDecision(root.getString("analysisSha256"),OutlinedCoverageContract.outputSha256(roads),boundaryConfirmed,spans),roads).failures
    fun unresolvedFindings(roads:List<RoadGeometry>):List<MapImageFinding> {
        val a=root.getJSONArray("resolutions");val resolutions=(0 until a.length()).map {a.getJSONObject(it)}.associateBy {it.getString("id")}
        return extraction.findings.filter {f->
            val candidate=f.id.removePrefix("name-").toIntOrNull()?.takeIf {f.id.startsWith("name-")}?.let {"image-road-${it+1}"}
            val sourceSpans=spans.filter {it.candidateId==candidate}
            if(candidate!=null && sourceSpans.isNotEmpty() && sourceSpans.all {s->s.disposition!=OutlinedSpanDisposition.ROAD || roads.any {it.segmentId==s.outputId && it.name.isNotBlank() && !it.name.startsWith("Unresolved road")}})false
            else {
                val v=resolutions[f.id];val box=f.sourceBounds
                if(v==null || box==null || v.getString("kind") !in allowedResolutions(f.id))true else {
                    val ids=v.getJSONArray("outputIds");val selected=(0 until ids.length()).mapNotNull {i->roads.firstOrNull {it.segmentId==ids.getString(i)}}
                    when(v.getString("kind")) {
                        "OUTSIDE_CONTEXT"->!outside(box,extraction.boundary.polygon)
                        else->ids.length()!=0 || !f.id.startsWith("component-")
                    }
                }
            }
        }
    }
    fun complete(roads:List<RoadGeometry>)=roads.isNotEmpty() && coverageFailures(roads).isEmpty() && unresolvedFindings(roads).isEmpty()
    companion object {
        val RESOLUTIONS=setOf("PARKING_MARKING","BUILDING_EDGE","MAP_SYMBOL","LABEL_ARTIFACT","OUTSIDE_CONTEXT","MATCHED_ROAD")
        private fun points(a:JSONArray):List<Point2D>{require(a.length()<=4096);return (0 until a.length()).map {i->val p=a.getJSONArray(i);require(p.length()==2);Point2D(p.getDouble(0),p.getDouble(1)).also {require(it.x.isFinite()&&it.y.isFinite())}}}
        private fun bounds(a:JSONArray):AxisAlignedRect {require(a.length()==4);val b=AxisAlignedRect(a.getDouble(0),a.getDouble(1),a.getDouble(2),a.getDouble(3));require(listOf(b.left,b.top,b.right,b.bottom).all {it.isFinite()}&&b.right>=b.left&&b.bottom>=b.top);return b}
        private fun rect(b:AxisAlignedRect)=JSONArray(listOf(b.left,b.top,b.right,b.bottom))
        private fun path(p:List<Point2D>)=JSONArray(p.map {JSONArray(listOf(it.x,it.y))})
        private fun outside(b:AxisAlignedRect,polygon:List<Point2D>):Boolean {
            if(polygon.any {it.x in b.left..b.right && it.y in b.top..b.bottom})return false
            val p=listOf(Point2D(b.left,b.top),Point2D(b.right,b.top),Point2D(b.right,b.bottom),Point2D(b.left,b.bottom),Point2D(b.left,b.top))
            return runCatching {OutlinedCoverageContract.exactRelations(p,polygon)==setOf("outside")}.getOrDefault(false)
        }
        private fun spanJson(s:OutlinedSourceSpan)=JSONObject().put("candidateId",s.candidateId).put("from",s.from).put("to",s.to).put("disposition",s.disposition.name)
            .put("outputId",s.outputId ?: JSONObject.NULL).put("order",s.outputOrder).put("reversed",s.reversed).put("evidence",s.evidence).put("outputSha256",s.reviewedOutputSha256)
        fun from(result:InterpretedMapDraft):OutlinedNativeReview {
            val e=requireNotNull(result.outlined);val t=requireNotNull(result.sourceToPage);val b=e.boundary
            val analysis=JSONObject().put("width",b.width).put("height",b.height).put("enclosedPixels",b.enclosedPixels).put("bounds",rect(b.sourceBounds)).put("polygon",path(b.polygon))
                .put("roads",JSONArray(e.roads.map {p->val r=p.road;JSONObject().put("id",r.id).put("name",r.name ?: JSONObject.NULL).put("points",path(r.points)).put("junctionA",r.junctionA).put("junctionB",r.junctionB).put("relation",p.relation.name)}))
                .put("findings",JSONArray(e.findings.map {f->JSONObject().put("id",f.id).put("message",f.message).put("bounds",f.sourceBounds?.let(::rect) ?: JSONObject.NULL)}))
                .put("recognizedText",JSONArray(result.recognizedText.map {JSONObject().put("text",it.text).put("bounds",rect(it.bounds))}))
            val root=JSONObject().put("schema","outlined-native-review-v1").put("sourceSha256",result.sourceSha256).put("analysis",analysis)
                .put("transform",JSONArray(listOf(t.scale,t.offsetX,t.offsetY))).put("analysisSha256",OutlinedCoverageContract.analysisSha256(result.sourceSha256,e,t))
                .put("boundaryConfirmed",false).put("spans",JSONArray()).put("resolutions",JSONArray())
            return OutlinedNativeReview(ExtendedValues.canonical(root))
        }
    }
}

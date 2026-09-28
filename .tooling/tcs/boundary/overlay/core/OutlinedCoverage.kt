package com.koenterprises.territorycardstudio.core

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.math.*

/** Coordinates remain in the decoded source until this explicit uniform page placement. */
data class OutlinedMapTransform(val scale:Double,val offsetX:Double,val offsetY:Double) {
    init {require(scale.isFinite() && scale>0 && offsetX.isFinite() && offsetY.isFinite())}
    fun page(p:Point2D)=Point2D(offsetX+p.x*scale,offsetY+p.y*scale)
    fun source(p:Point2D)=Point2D((p.x-offsetX)/scale,(p.y-offsetY)/scale)
}
enum class OutlinedSpanDisposition { ROAD, NON_ROAD, OUTSIDE_CONTEXT }
data class OutlinedSourceSpan(val candidateId:String,val from:Double,val to:Double,
    val disposition:OutlinedSpanDisposition,val outputId:String?,val outputOrder:Int=0,
    val reversed:Boolean=false,val evidence:String="",val reviewedOutputSha256:String="")
data class OutlinedCoverageDecision(val analysisSha256:String,val outputSha256:String,
    val boundaryConfirmed:Boolean,val spans:List<OutlinedSourceSpan>,val boundaryRepairEvidence:Map<String,String> = emptyMap())
data class OutlinedCoverageResult(val failures:List<String>) {val passed get()=failures.isEmpty()}

/** This gate proves exact candidate accounting and source-to-output geometry, not geographic
 * completeness. Unresolved findings and current-source inventory remain separate mandatory gates. */
object OutlinedCoverageContract {
    private const val EPS=1e-7
    private fun digest(write:(DataOutputStream)->Unit):String {
        val bytes=ByteArrayOutputStream();DataOutputStream(bytes).use(write)
        return BundleIntegrity.sha256(bytes.toByteArray().inputStream())
    }
    private fun DataOutputStream.text(s:String){val b=s.toByteArray(Charsets.UTF_8);require(b.size<=16384);writeInt(b.size);write(b)}
    private fun DataOutputStream.point(p:Point2D){require(p.x.isFinite()&&p.y.isFinite());writeDouble(p.x);writeDouble(p.y)}
    private fun DataOutputStream.points(p:List<Point2D>){require(p.size<=4096);writeInt(p.size);p.forEach {point(it)}}
    private fun DataOutputStream.bounds(b:AxisAlignedRect){writeDouble(b.left);writeDouble(b.top);writeDouble(b.right);writeDouble(b.bottom)}
    fun analysisSha256(sourceSha256:String,e:OutlinedMapExtraction,t:OutlinedMapTransform):String=digest {o->
        require(sourceSha256.matches(Regex("[0-9a-f]{64}")))
        require(e.roads.size<=4096 && e.findings.size<=8192)
        require(e.roads.map {it.road.id}.distinct().size==e.roads.size)
        require(e.findings.map {it.id}.distinct().size==e.findings.size)
        o.text("outlined-coverage-analysis-v1");o.text(sourceSha256);o.writeInt(e.boundary.width);o.writeInt(e.boundary.height)
        o.writeInt(e.boundary.enclosedPixels);o.bounds(e.boundary.sourceBounds);o.points(e.boundary.polygon)
        o.writeDouble(t.scale);o.writeDouble(t.offsetX);o.writeDouble(t.offsetY)
        o.writeInt(e.roads.size);e.roads.forEach {p->val r=p.road;o.text(r.id);o.text(r.name ?: "");o.text(r.status)
            o.text(p.relation.name);o.writeBoolean(r.junctionA);o.writeBoolean(r.junctionB);o.points(r.points)}
        o.writeInt(e.findings.size);e.findings.forEach {f->o.text(f.id);o.text(f.message);o.writeBoolean(f.sourceBounds!=null);f.sourceBounds?.let {o.bounds(it)}}
        if(e.paintMode!=OutlinedRoadPaintMode.LIGHT_NEUTRAL){o.text("outlined-road-paint-v1");o.text(e.paintMode.name)}
        // Preserve existing no-repair hashes; repaired analyses carry a versioned extension.
        if(e.boundary.gapRepairs.isNotEmpty()) {
            o.text("boundary-repair-proposals-v1");o.writeInt(e.boundary.gapRepairs.size)
            e.boundary.gapRepairs.forEach {r->o.text(r.id);o.text(r.algorithm);o.point(r.start);o.point(r.end);o.points(r.addedPixels)}
        }
    }
    fun outputSha256(roads:List<RoadGeometry>):String=digest {o->
        o.text("outlined-coverage-output-v1");o.writeInt(roads.size)
        roads.forEach {r->o.text(r.segmentId);o.text(r.name);o.text(r.normalizedName);o.text(r.status);o.text(r.role);o.text(r.insideSide)
            o.writeBoolean(r.accessOnly);o.text(r.endpointAKind);o.text(r.endpointBKind);o.writeDouble(r.widthPt);o.points(r.points)}
    }
    /** Arc-length fractions, retaining all intervening source vertices. */
    fun slice(points:List<Point2D>,from:Double,to:Double):List<Point2D> {
        require(points.size>=2 && from.isFinite() && to.isFinite() && from>=0 && to<=1 && to>from)
        val lengths=points.zipWithNext().map {(a,b)->hypot(b.x-a.x,b.y-a.y)}
        require(lengths.all {it.isFinite() && it>0})
        val total=lengths.sum();val start=from*total;val end=to*total;var position=0.0
        val result=mutableListOf<Point2D>()
        for(i in lengths.indices){val length=lengths[i];val a=points[i];val b=points[i+1]
            val lo=max(start,position);val hi=min(end,position+length)
            if(hi>lo+EPS){fun at(v:Double)=Point2D(a.x+(b.x-a.x)*(v-position)/length,a.y+(b.y-a.y)*(v-position)/length)
                val p=at(lo);if(result.isEmpty()||!same(result.last(),p))result+=p;result+=at(hi)}
            position+=length
        }
        require(result.size>=2);return result
    }
    private fun same(a:Point2D,b:Point2D)=hypot(a.x-b.x,a.y-b.y)<=EPS
    /** Exact positive-length interval classification. Pixel tolerance is only for proposals. */
    fun exactRelations(path:List<Point2D>,polygon:List<Point2D>):Set<String> {
        val result=mutableSetOf<String>()
        fun cross(a:Point2D,b:Point2D)=a.x*b.y-a.y*b.x
        fun minus(a:Point2D,b:Point2D)=Point2D(a.x-b.x,a.y-b.y)
        fun boundary(p:Point2D)= (polygon+polygon.first()).zipWithNext().any {(a,b)->
            val d=minus(b,a);val q=minus(p,a);val length=hypot(d.x,d.y)
            length>EPS && abs(cross(d,q))/length<=EPS && q.x*d.x+q.y*d.y>=-EPS && q.x*d.x+q.y*d.y<=length*length+EPS
        }
        for((a,b) in path.zipWithNext()) {
            val d=minus(b,a);val length2=d.x*d.x+d.y*d.y
            require(length2>EPS*EPS && length2.isFinite())
            val cuts=mutableListOf(0.0,1.0)
            for((u,v) in (polygon+polygon.first()).zipWithNext()) {
                val edge=minus(v,u);val q=minus(u,a);val denominator=cross(d,edge)
                if(abs(denominator)>EPS) {
                    val t=cross(q,edge)/denominator;val s=cross(q,d)/denominator
                    if(t in 0.0..1.0 && s in 0.0..1.0)cuts+=t
                } else if(abs(cross(q,d))<=EPS) {
                    for(p in listOf(u,v)){val x=minus(p,a);val t=(x.x*d.x+x.y*d.y)/length2;if(t in 0.0..1.0)cuts+=t}
                }
            }
            for((lo,hi) in cuts.sorted().zipWithNext())if(hi-lo>EPS) {
                val t=(lo+hi)/2;val p=Point2D(a.x+d.x*t,a.y+d.y*t)
                result+=if(boundary(p))"boundary" else if(OutlinedMapBoundaryDetector.inside(p,polygon))"inside" else "outside"
            }
        }
        return result
    }
    /** Screen-coordinate left is north when traversing an eastbound line. */
    fun inwardSide(path:List<Point2D>,polygon:List<Point2D>):String? {
        val sides=path.zipWithNext().map {(a,b)->
            val dx=b.x-a.x;val dy=b.y-a.y;val length=hypot(dx,dy);if(length<=EPS)return null
            val m=Point2D((a.x+b.x)/2,(a.y+b.y)/2)
            val left=Point2D(m.x+8*dy/length,m.y-8*dx/length)
            val right=Point2D(m.x-8*dy/length,m.y+8*dx/length)
            val l=OutlinedMapBoundaryDetector.inside(left,polygon);val r=OutlinedMapBoundaryDetector.inside(right,polygon)
            if(l==r)return null
            if(l)"left" else "right"
        }.toSet()
        return sides.singleOrNull()
    }
    fun assess(sourceSha256:String,e:OutlinedMapExtraction,t:OutlinedMapTransform,
        decision:OutlinedCoverageDecision,roads:List<RoadGeometry>):OutlinedCoverageResult {
        val errors=mutableListOf<String>()
        if(decision.analysisSha256!=analysisSha256(sourceSha256,e,t))errors+="SOURCE_ANALYSIS_CHANGED"
        if(decision.outputSha256!=outputSha256(roads))errors+="OUTPUT_CHANGED"
        if(!decision.boundaryConfirmed)errors+="BOUNDARY_UNCONFIRMED"
        for(repair in e.boundary.gapRepairs)if(decision.boundaryRepairEvidence[repair.id]?.trim()?.length !in 8..2000)errors+="BOUNDARY_REPAIR_UNREVIEWED:${repair.id}"
        if(decision.boundaryRepairEvidence.keys.any {id->e.boundary.gapRepairs.none {it.id==id}})errors+="UNKNOWN_BOUNDARY_REPAIR"
        val candidates=e.roads.associateBy {it.road.id};val output=roads.associateBy {it.segmentId}
        if(output.size!=roads.size)errors+="DUPLICATE_OUTPUT_ID"
        if(decision.spans.size>16384)return OutlinedCoverageResult(errors+"TOO_MANY_SPANS")
        if(decision.spans.any {it.candidateId !in candidates})errors+="UNKNOWN_CANDIDATE"
        for((id,proposal) in candidates) {
            val spans=decision.spans.filter {it.candidateId==id}.sortedBy {it.from}
            if(spans.isEmpty()){errors+="UNACCOUNTED_CANDIDATE:$id";continue}
            var end=0.0
            for(s in spans){
                if(!s.from.isFinite()||!s.to.isFinite()||s.from<0||s.to>1||s.to<=s.from){errors+="INVALID_SPAN:$id";continue}
                if(abs(s.from-end)>EPS)errors+="SPAN_GAP_OR_OVERLAP:$id"
                end=s.to
                if(s.evidence.trim().length !in 8..2000)errors+="SOURCE_EVIDENCE_REQUIRED:$id"
                if(s.disposition==OutlinedSpanDisposition.ROAD){
                    val r=output[s.outputId]
                    if(r==null)errors+="MISSING_OUTPUT:$id"
                    else if(s.reviewedOutputSha256!=outputSha256(listOf(r)))errors+="SPAN_OUTPUT_REVIEW_CHANGED:$id"
                }
                else {
                    if(s.outputId!=null || s.reversed || s.outputOrder!=0 || s.reviewedOutputSha256.isNotEmpty())errors+="OMISSION_HAS_OUTPUT:$id"
                    if(s.disposition==OutlinedSpanDisposition.OUTSIDE_CONTEXT) {
                        val relations=runCatching {exactRelations(slice(proposal.road.points,s.from,s.to),e.boundary.polygon)}.getOrNull()
                        if(relations!=setOf("outside"))errors+="NONEXTERIOR_CONTEXT_OMISSION:$id"
                    }
                }
            }
            if(abs(end-1.0)>EPS)errors+="SPAN_GAP_OR_OVERLAP:$id"
        }
        val bindings=decision.spans.filter {it.disposition==OutlinedSpanDisposition.ROAD}.groupBy {it.outputId}
        if(bindings.keys.filterNotNull().toSet()!=output.keys)errors+="UNBOUND_OUTPUT"
        for((id,road) in output) {
            val spans=bindings[id].orEmpty().sortedBy {it.outputOrder}
            if(spans.map {it.outputOrder}!=spans.indices.toList()){errors+="OUTPUT_ORDER_INVALID:$id";continue}
            val path=mutableListOf<Point2D>()
            for(s in spans){val candidate=candidates[s.candidateId] ?: continue
                val part=runCatching {slice(candidate.road.points,s.from,s.to).let {if(s.reversed)it.reversed() else it}}.getOrNull()
                if(part==null){errors+="INVALID_GEOMETRY_SPAN:$id";continue}
                if(path.isNotEmpty()&&!same(path.last(),part.first()))errors+="UNSUPPORTED_CONNECTOR:$id"
                path+=if(path.isEmpty())part else part.drop(1)
            }
            val expected=path.map(t::page)
            if(expected.size!=road.points.size || expected.zip(road.points).any {(a,b)->!same(a,b)})errors+="SOURCE_OUTPUT_GEOMETRY_MISMATCH:$id"
            if(road.points.any {it.x !in 176.0..747.0 || it.y !in 20.0..363.0})errors+="OUTPUT_OUTSIDE_MAP:$id"
            if(path.size>=2) {
                val relation=OutlinedMapRoadExtractor.relationship(path,e.boundary.polygon)
                val exact=runCatching {exactRelations(path,e.boundary.polygon)}.getOrNull()
                if(exact==null)errors+="INVALID_SOURCE_PATH:$id"
                if(road.status=="green" && exact!=setOf("inside"))errors+="WORKED_PATH_NOT_INTERIOR:$id"
                if(road.status=="yellow" && (relation!=BoundaryRoadRelation.BOUNDARY_FOLLOWING || exact!=setOf("boundary") || inwardSide(path,e.boundary.polygon)!=road.insideSide))errors+="WORKED_SIDE_NOT_VERIFIED:$id"
                if(relation==BoundaryRoadRelation.CROSSING && road.status in setOf("green","yellow"))errors+="UNSPLIT_WORK_CROSSING:$id"
                if(relation==BoundaryRoadRelation.EXTERIOR && road.status in setOf("green","yellow"))errors+="EXTERIOR_WORK_CONFLICT:$id"
                if(relation==BoundaryRoadRelation.BOUNDARY_FOLLOWING && road.status=="green")errors+="BOUNDARY_BOTH_SIDES_CONFLICT:$id"
            }
            val valid=when(road.status){"green"->road.role=="interior"&&road.insideSide.isBlank();"yellow"->road.role=="perimeter"&&road.insideSide in setOf("left","right");"red"->road.role=="excluded"&&road.insideSide.isBlank();"context"->road.role=="context"&&road.insideSide.isBlank();else->false}
            if(!valid)errors+="INVALID_WORK_RULE:$id"
        }
        return OutlinedCoverageResult(errors.distinct())
    }
}

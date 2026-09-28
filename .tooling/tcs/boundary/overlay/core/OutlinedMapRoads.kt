package com.koenterprises.territorycardstudio.core

import kotlin.math.*

enum class BoundaryRoadRelation { INTERIOR, EXTERIOR, BOUNDARY_FOLLOWING, CROSSING, BOUNDARY_ENDPOINT }
data class OutlinedRoadProposal(val road:MapImageRoad,val relation:BoundaryRoadRelation)
data class OutlinedMapExtraction(val boundary:OutlinedMapBoundary,val roads:List<OutlinedRoadProposal>,val findings:List<MapImageFinding>)

/** Only proposes visible neutral road paint. Source colors never enter the work-status classifier. */
object OutlinedMapRoadExtractor {
    fun extract(width:Int,height:Int,pixels:IntArray,text:List<MapImageText>):OutlinedMapExtraction {
        val boundary=OutlinedMapBoundaryDetector.detect(width,height,pixels)
        val pad=max(32.0,(boundary.sourceBounds.bottom-boundary.sourceBounds.top)*0.4)
        val roi=AxisAlignedRect(max(0.0,boundary.sourceBounds.left-pad),max(0.0,boundary.sourceBounds.top-pad),min(width.toDouble(),boundary.sourceBounds.right+pad),min(height.toDouble(),boundary.sourceBounds.bottom+pad))
        val interior=BooleanArray(pixels.size);val luminance=IntArray(pixels.size);val neutral=BooleanArray(pixels.size)
        val insideHistogram=IntArray(256);val outsideHistogram=IntArray(256)
        for(y in roi.top.toInt() until roi.bottom.toInt())for(x in roi.left.toInt() until roi.right.toInt()) {
            val i=y*width+x;val p=pixels[i];val r=(p ushr 16)and 255;val g=(p ushr 8)and 255;val b=p and 255
            luminance[i]=(r+g+b)/3;neutral[i]=max(r,max(g,b))-min(r,min(g,b))<18
            interior[i]=OutlinedMapBoundaryDetector.inside(Point2D(x+0.5,y+0.5),boundary.polygon)
            if(neutral[i]&&luminance[i]>=100)(if(interior[i])insideHistogram else outsideHistogram)[luminance[i]]++
        }
        fun threshold(h:IntArray):Int {
            val background=(100..245).maxByOrNull {h[it]} ?: error("Missing neutral basemap background")
            require(h[background]>=32){"Insufficient neutral road contrast; use a clearer map."}
            return min(250,background+12)
        }
        val insideThreshold=threshold(insideHistogram);val outsideThreshold=threshold(outsideHistogram)
        val proposed=IntArray(pixels.size){0xFFFFFFFF.toInt()}
        for(y in roi.top.toInt() until roi.bottom.toInt())for(x in roi.left.toInt() until roi.right.toInt()) {
            val i=y*width+x
            if(neutral[i]&&luminance[i]>=(if(interior[i])insideThreshold else outsideThreshold))proposed[i]=0xFF51C72B.toInt()
        }
        // Reuse only the neutral mask's geometric skeleton; its temporary color is not a work decision.
        val geometry=MapImageDraftExtractor.extract(width,height,proposed,deduplicatedStreetText(text))
        val roads=geometry.roads.map {r->OutlinedRoadProposal(r.copy(status="context"),relationship(r.points,boundary.polygon))}
        val findings=geometry.findings.toMutableList()
        if(roads.isEmpty())findings+=MapImageFinding("boundary-no-roads","No reliable visible roads were recovered inside the outlined area.",boundary.sourceBounds)
        return OutlinedMapExtraction(boundary,roads,findings)
    }
    /** Repeated OCR passes at the same source label are one observation, not competing names.
     * Different readings remain separate and therefore ambiguous. */
    fun deduplicatedStreetText(text:List<MapImageText>):List<MapImageText> {
        val result=mutableListOf<MapImageText>()
        for(t in text.filter {MapImageDraftExtractor.streetText(it.text)!=null}) {
            fun key(s:String)=s.lowercase().filterNot(Char::isWhitespace)
            val b=t.bounds;val cx=(b.left+b.right)/2;val cy=(b.top+b.bottom)/2
            val duplicate=result.any {r->val a=r.bounds;key(r.text)==key(t.text) && hypot((a.left+a.right)/2-cx,(a.top+a.bottom)/2-cy)<=15.0}
            if(!duplicate)result+=t
        }
        return result
    }
    fun relationship(points:List<Point2D>,polygon:List<Point2D>):BoundaryRoadRelation {
        require(points.size>=2&&polygon.size>=3)
        val samples=points.zipWithNext().flatMap {(a,b)->val n=max(1,ceil(hypot(b.x-a.x,b.y-a.y)/2).toInt());(0..n).map {i->Point2D(a.x+(b.x-a.x)*i/n,a.y+(b.y-a.y)*i/n)}}
        val near=samples.map {distance(it,polygon)<=4.0}
        val definite=samples.filterIndexed {i,_->!near[i]}.map {OutlinedMapBoundaryDetector.inside(it,polygon)}
        return when {
            definite.any {it}&&definite.any {!it}->BoundaryRoadRelation.CROSSING
            near.count {it}>=samples.size*0.7->BoundaryRoadRelation.BOUNDARY_FOLLOWING
            distance(points.first(),polygon)<=5.0||distance(points.last(),polygon)<=5.0->BoundaryRoadRelation.BOUNDARY_ENDPOINT
            definite.any {it}->BoundaryRoadRelation.INTERIOR
            else->BoundaryRoadRelation.EXTERIOR
        }
    }
    private fun distance(p:Point2D,poly:List<Point2D>):Double=(poly+poly.first()).zipWithNext().minOf {(a,b)->val dx=b.x-a.x;val dy=b.y-a.y;val t=(((p.x-a.x)*dx+(p.y-a.y)*dy)/max(1e-12,dx*dx+dy*dy)).coerceIn(0.0,1.0);hypot(p.x-a.x-t*dx,p.y-a.y-t*dy)}
}

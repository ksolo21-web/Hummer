package com.koenterprises.territorycardstudio.core

import kotlin.math.*

/** Source members and physical footprints are immutable inputs. This only places text.
 * It never shrinks the 9 pt font, changes a number, moves a footprint or grants approval.
 * Unsupported/too-small geometry produces an actionable error rather than clipped text. */
object NativeBuildingLabelPlacement {
    private const val SIZE = 9.0
    private data class Frame(val ux:Double,val uy:Double,val length:Double,val width:Double,val area:Double)

    fun place(members:List<String>,polygon:List<Point2D>,measure:(String,Double)->Double):List<BuildingLabelItem> {
        require(members.size in 0..64 && members.distinct().size==members.size &&
            members.all {it.isNotBlank() && it.length<=128 && it.all {c->c.code in 32..126}}) { "Enter distinct supported building member labels" }
        require(polygon.size in 3..4096 && polygon.all {it.x.isFinite() && it.y.isFinite()}) { "Trace a finite physical footprint" }
        // An explicitly excluded/unassigned site may intentionally have no member label.
        // Its assignment gate, not this layout helper, prevents unlabeled worked buildings.
        if(members.isEmpty())return emptyList()
        val widths=members.associateWith {measure(it,SIZE).also {v->require(v.isFinite() && v>0)}}
        fun item(text:String,center:Point2D,angle:Double=0.0):BuildingLabelItem {
            var degrees=angle
            while(degrees < -90.0)degrees+=180.0
            while(degrees > 90.0)degrees-=180.0
            val r=Math.toRadians(degrees);val w=widths.getValue(text)
            // Baseline origin in the same top-origin geometry as the footprint.
            // PDF conversion changes only the angle sign, never this origin.
            val origin=Point2D(center.x-cos(r)*w/2-sin(r)*SIZE*0.26,
                center.y-sin(r)*w/2+cos(r)*SIZE*0.26)
            return BuildingLabelItem(text,center,origin,degrees,SIZE)
        }
        fun envelopesOverlap(a:BuildingLabelItem,b:BuildingLabelItem):Boolean {
            val aa=BuildingValidationEngine.labelRectangle(a,widths.getValue(a.text))
            val bb=BuildingValidationEngine.labelRectangle(b,widths.getValue(b.text))
            // Separating-axis test, with 0.5 pt clearance between member labels.
            for(shape in listOf(aa,bb))for(i in 0..1) {
                val dx=shape[i+1].x-shape[i].x;val dy=shape[i+1].y-shape[i].y;val len=hypot(dx,dy)
                val x=-dy/len;val y=dx/len
                val ap=aa.map {it.x*x+it.y*y};val bp=bb.map {it.x*x+it.y*y}
                if(ap.maxOrNull()!!+0.5<=bp.minOrNull()!! || bp.maxOrNull()!!+0.5<=ap.minOrNull()!!)return false
            }
            return true
        }
        var evaluations=0
        fun fits(i:BuildingLabelItem,placed:List<BuildingLabelItem>):Boolean {
            require(++evaluations<=4096) { "Building label search reached its bounded limit. Enlarge the diagram or review label placement; no geometry or member was changed." }
            return BuildingValidationEngine.labelFits(polygon,i,widths.getValue(i.text)) && placed.none {envelopesOverlap(i,it)}
        }
        fun allFit(items:List<BuildingLabelItem>)=items.indices.all {fits(items[it],items.take(it))}
        // Preserve the former byte-for-byte placement whenever its actual text fits.
        val oldCenter=Point2D(polygon.map {it.x}.average(),polygon.map {it.y}.average())
        val old=members.mapIndexed {i,text->item(text,Point2D(oldCenter.x,oldCenter.y+(i-(members.size-1)/2.0)*12.0))}
        if(allFit(old))return old
        val edges=(polygon+polygon.first()).zipWithNext()
        val cross=edges.sumOf {(a,b)->a.x*b.y-b.x*a.y}
        require(cross.isFinite() && abs(cross)>1e-7) { "Footprint has no usable area" }
        val center=Point2D(edges.sumOf {(a,b)->(a.x+b.x)*(a.x*b.y-b.x*a.y)}/(3*cross),
            edges.sumOf {(a,b)->(a.y+b.y)*(a.x*b.y-b.x*a.y)}/(3*cross))
        val candidates=(listOf(0.0)+edges.filter {(a,b)->hypot(b.x-a.x,b.y-a.y)>1e-7}
            .sortedByDescending {(a,b)->hypot(b.x-a.x,b.y-a.y)}.take(32).map {(a,b)->atan2(b.y-a.y,b.x-a.x)})
        val frames=candidates.map {angle->
            var ux=cos(angle);var uy=sin(angle)
            var along=polygon.map {it.x*ux+it.y*uy};var across=polygon.map {-it.x*uy+it.y*ux}
            var length=along.maxOrNull()!!-along.minOrNull()!!;var width=across.maxOrNull()!!-across.minOrNull()!!
            if(width>length){val t=ux;ux=-uy;uy=t;val swap=length;length=width;width=swap}
            // The first member goes toward the northern/left end; text remains upright.
            if(uy < -1e-9 || abs(uy)<=1e-9 && ux<0){ux=-ux;uy=-uy}
            Frame(ux,uy,length,width,length*width)
        }.distinctBy {round(atan2(it.uy,it.ux)*10000).toInt()}.sortedBy {it.area}.take(16)
        for(frame in frames) {
            val angle=Math.toDegrees(atan2(frame.uy,frame.ux))
            for(spacing in listOf(0.45,0.40,0.50,0.35,0.55,0.30,0.60)) {
                val placed=mutableListOf<BuildingLabelItem>()
                for((index,text) in members.withIndex()) {
                    val base=(index-(members.size-1)/2.0)*frame.length*spacing/max(1,members.size-1)
                    val offsets=listOf(0.0,-0.04,0.04,-0.08,0.08,-0.12,0.12)
                    var found:BuildingLabelItem?=null
                    for(rotation in listOf(0.0,angle).distinct()) {
                        search@ for(longOffset in offsets)for(crossOffset in offsets) {
                            val along=base+longOffset*frame.length;val across=crossOffset*frame.width
                            val at=Point2D(center.x+frame.ux*along-frame.uy*across,center.y+frame.uy*along+frame.ux*across)
                            val value=item(text,at,rotation)
                            if(fits(value,placed)){found=value;break@search}
                        }
                        if(found!=null)break
                    }
                    if(found==null)break else placed+=found
                }
                if(placed.size==members.size && allFit(placed))return placed
            }
        }
        error("Building member labels do not fit at the required 9 pt size. Enlarge the site diagram or review their source-bound placement; no numbers or footprint were changed.")
    }
}

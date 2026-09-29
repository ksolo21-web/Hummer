package com.koenterprises.territorycardstudio.core

import kotlin.math.*

/** Neutral basemap colors carry no assignment meaning. A boundary is a source proposal only. */
data class OutlinedMapBoundary(val width:Int,val height:Int,val polygon:List<Point2D>,val enclosedPixels:Int,
    val sourceBounds:AxisAlignedRect,val gapRepairs:List<BoundaryGapRepair> = emptyList()) {
    init {
        require(gapRepairs.size<=1)
        require(gapRepairs.flatMap {listOf(it.start,it.end)+it.addedPixels}.all {it.x in 1.0..(width-2).toDouble() && it.y in 1.0..(height-2).toDouble()})
    }
}

/** A review-only bridge. Original image pixels are never changed. */
data class BoundaryGapRepair(val id:String,val start:Point2D,val end:Point2D,val addedPixels:List<Point2D>,val algorithm:String="thin-endpoints-v1") {
    init {
        require(id=="gap-1" && algorithm=="thin-endpoints-v1" && addedPixels.size in 1..33)
        require((listOf(start,end)+addedPixels).all {it.x.isFinite() && it.y.isFinite() && it.x==round(it.x) && it.y==round(it.y)})
        require(addedPixels.distinct().size==addedPixels.size && hypot(start.x-end.x,start.y-end.y)<=32.0)
    }
}

object OutlinedMapBoundaryDetector {
    fun detect(width:Int,height:Int,pixels:IntArray,proposeShortGaps:Boolean=false):OutlinedMapBoundary {
        require(width in 16..1400 && height in 16..1400 && pixels.size==width*height)
        val dark=BooleanArray(pixels.size){val p=pixels[it];max((p ushr 16)and 255,max((p ushr 8)and 255,p and 255))<80}
        val seen=BooleanArray(dark.size);val queue=IntArray(dark.size);val candidates=mutableListOf<OutlinedMapBoundary>()
        for(start in dark.indices)if(dark[start]&&!seen[start]) {
            var head=0;var tail=0;queue[tail++]=start;seen[start]=true
            var left=width;var top=height;var right=0;var bottom=0
            while(head<tail){val p=queue[head++];val x=p%width;val y=p/width;left=min(left,x);right=max(right,x);top=min(top,y);bottom=max(bottom,y)
                for(dy in -1..1)for(dx in -1..1){val nx=x+dx;val ny=y+dy
                    if(nx in 0 until width&&ny in 0 until height){val n=ny*width+nx;if(dark[n]&&!seen[n]){seen[n]=true;queue[tail++]=n}}}}
            if(tail<max(80,(width+height)/3)||right-left<width/20||bottom-top<height/20)continue
            if(left==0||top==0||right==width-1||bottom==height-1){
                val boxArea=(right-left+1).toLong()*(bottom-top+1).toLong()
                // A screenshot's solid full-width toolbar is not map linework. A thin
                // clipped outline remains a hard failure, even beside valid shapes.
                if(right-left+1 >= width*0.95 && tail.toDouble()/boxArea >= 0.75)continue
                throw IllegalArgumentException("Black outline touches the image edge. Supply the complete closed boundary.")
            }
            val edge=BooleanArray(pixels.size);for(i in 0 until tail)edge[queue[i]]=true
            fun enclosed():BooleanArray {
                val exterior=BooleanArray(pixels.size);head=0;tail=0;queue[tail++]=0;exterior[0]=true
                while(head<tail){val p=queue[head++];val x=p%width;val y=p/width
                    for(n in intArrayOf(if(x>0)p-1 else -1,if(x<width-1)p+1 else -1,if(y>0)p-width else -1,if(y<height-1)p+width else -1))
                        if(n>=0&&!edge[n]&&!exterior[n]){exterior[n]=true;queue[tail++]=n}}
                return BooleanArray(pixels.size){!exterior[it]&&!edge[it]}
            }
            var interior=enclosed();var count=interior.count{it}
            var repair:BoundaryGapRepair?=null
            if(count==0 && proposeShortGaps && right-left>=width/5 && bottom-top>=height/5) {
                repair=shortGap(width,height,edge,dark,left,top,right,bottom)
                if(repair!=null){repair.addedPixels.forEach {edge[it.y.toInt()*width+it.x.toInt()]=true};interior=enclosed();count=interior.count{it}}
            }
            if(count<max(64,pixels.size/100))continue
            // Trace source pixels plus only the separately recorded pending bridge, when present.
            val edges=HashMap<Int,MutableList<Int>>()
            fun add(x:Int,y:Int,a:Int,b:Int){edges.getOrPut(y*(width+1)+x){mutableListOf()}.add(b*(width+1)+a)}
            for(p in interior.indices)if(interior[p]){val x=p%width;val y=p/width
                if(y==0||!interior[p-width])add(x,y,x+1,y)
                if(x==width-1||!interior[p+1])add(x+1,y,x+1,y+1)
                if(y==height-1||!interior[p+width])add(x+1,y+1,x,y+1)
                if(x==0||!interior[p-1])add(x,y+1,x,y)}
            val loops=mutableListOf<List<Point2D>>()
            while(edges.isNotEmpty()){val first=edges.keys.minOrNull()!!;var v=first;val loop=mutableListOf<Point2D>()
                do {loop+=Point2D((v%(width+1)).toDouble(),(v/(width+1)).toDouble());val outgoing=edges[v] ?: break
                    val next=outgoing.removeAt(0);if(outgoing.isEmpty())edges.remove(v);v=next
                }while(v!=first&&loop.size<=pixels.size*4)
                if(v==first&&loop.size>=3)loops+=loop}
            require(loops.size==1){"Connected multiple enclosures or interior holes need explicit boundary review."}
            val outer=loops.single()
            val polygon=simplify(outer+outer.first(),1.0).dropLast(1)
            require(polygon.size in 3..2048){"Boundary complexity needs a clearer source."}
            require(abs(area(polygon)-count)<=max(8.0,count*0.005)){"Boundary simplification changed source coverage."}
            candidates+=OutlinedMapBoundary(width,height,polygon,count,AxisAlignedRect(left.toDouble(),top.toDouble(),right+1.0,bottom+1.0),listOfNotNull(repair))
        }
        require(candidates.size==1){if(candidates.isEmpty())"No single closed black boundary was recovered. Use a clearer, complete outlined-area map." else "Several closed black boundaries were found. Select a map containing one territory."}
        return candidates.single()
    }
    private fun shortGap(width:Int,height:Int,edge:BooleanArray,dark:BooleanArray,left:Int,top:Int,right:Int,bottom:Int):BoundaryGapRepair? {
        // Thin only this component to locate two unique open ends. Complex branches,
        // large missing sections and clipped outlines cannot acquire a bridge proposal.
        val active=edge.indices.filter {edge[it]}
        if(active.size.toDouble()/((right-left+1)*(bottom-top+1))>0.15)return null
        val thin=edge.copyOf();val offsets=intArrayOf(-width,-width+1,1,width+1,width,width-1,-1,-width-1)
        var converged=false
        for(iteration in 0 until 64) {
            var changed=false
            for(step in 0..1) {
                val remove=mutableListOf<Int>()
                for(p in active)if(thin[p]) {
                    val n=offsets.map {if(thin[p+it])1 else 0};val total=n.sum()
                    val turns=(0..7).count {n[it]==0 && n[(it+1)%8]==1}
                    val shape=if(step==0)n[0]*n[2]*n[4]==0 && n[2]*n[4]*n[6]==0 else n[0]*n[2]*n[6]==0 && n[0]*n[4]*n[6]==0
                    if(total in 2..6 && turns==1 && shape)remove+=p
                }
                if(remove.isNotEmpty()){changed=true;remove.forEach {thin[it]=false}}
            }
            if(!changed){converged=true;break}
        }
        if(!converged)return null
        val ends=active.filter {thin[it] && offsets.count {d->thin[it+d]}==1}
        if(ends.size!=2)return null
        val a=Point2D((ends[0]%width).toDouble(),(ends[0]/width).toDouble())
        val b=Point2D((ends[1]%width).toDouble(),(ends[1]/width).toDouble())
        if(hypot(a.x-b.x,a.y-b.y)>min(32.0,max(width,height)*0.025))return null
        val steps=max(abs(b.x-a.x),abs(b.y-a.y)).toInt();if(steps<2)return null
        val path=(0..steps).map {i->Point2D(round(a.x+(b.x-a.x)*i/steps),round(a.y+(b.y-a.y)*i/steps))}.distinct()
        if(path.any {val p=it.y.toInt()*width+it.x.toInt();dark[p]&&!edge[p]})return null
        val added=path.filter {!edge[it.y.toInt()*width+it.x.toInt()]}
        if(added.isEmpty())return null
        return BoundaryGapRepair("gap-1",a,b,added)
    }
    fun inside(p:Point2D,polygon:List<Point2D>):Boolean {
        var hit=false;var j=polygon.lastIndex
        for(i in polygon.indices){val a=polygon[i];val b=polygon[j];if((a.y>p.y)!=(b.y>p.y)&&p.x<(b.x-a.x)*(p.y-a.y)/(b.y-a.y)+a.x)hit=!hit;j=i};return hit
    }
    fun area(p:List<Point2D>):Double=abs((p+p.first()).zipWithNext().sumOf{(a,b)->a.x*b.y-b.x*a.y})/2
    private fun simplify(p:List<Point2D>,epsilon:Double):List<Point2D>{
        if(p.size<=2)return p
        val keep=BooleanArray(p.size);keep[0]=true;keep[p.lastIndex]=true
        val stack=java.util.ArrayDeque<Pair<Int,Int>>();stack.add(0 to p.lastIndex)
        while(stack.isNotEmpty()) {val (first,last)=stack.removeLast();val a=p[first];val b=p[last];val dx=b.x-a.x;val dy=b.y-a.y;val length=hypot(dx,dy)
            var distance=0.0;var index=first
            for(i in first+1 until last){val x=p[i];val d=if(length==0.0)hypot(x.x-a.x,x.y-a.y)else abs(dy*x.x-dx*x.y+b.x*a.y-b.y*a.x)/length;if(d>distance){distance=d;index=i}}
            if(distance>epsilon){keep[index]=true;stack.add(first to index);stack.add(index to last)}
        }
        return p.filterIndexed {i,_->keep[i]}
    }
}

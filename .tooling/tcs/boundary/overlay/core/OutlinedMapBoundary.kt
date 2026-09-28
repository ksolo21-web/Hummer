package com.koenterprises.territorycardstudio.core

import kotlin.math.*

/** Neutral basemap colors carry no assignment meaning. A boundary is a source proposal only. */
data class OutlinedMapBoundary(val width:Int,val height:Int,val polygon:List<Point2D>,val enclosedPixels:Int,
    val sourceBounds:AxisAlignedRect)

object OutlinedMapBoundaryDetector {
    fun detect(width:Int,height:Int,pixels:IntArray):OutlinedMapBoundary {
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
            val exterior=BooleanArray(pixels.size);head=0;tail=0;queue[tail++]=0;exterior[0]=true
            while(head<tail){val p=queue[head++];val x=p%width;val y=p/width
                for(n in intArrayOf(if(x>0)p-1 else -1,if(x<width-1)p+1 else -1,if(y>0)p-width else -1,if(y<height-1)p+width else -1))
                    if(n>=0&&!edge[n]&&!exterior[n]){exterior[n]=true;queue[tail++]=n}}
            val interior=BooleanArray(pixels.size){!exterior[it]&&!edge[it]};val count=interior.count{it}
            if(count<max(64,pixels.size/100))continue
            // Trace exact pixel edges of the enclosed region; never close or bridge a broken outline.
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
            candidates+=OutlinedMapBoundary(width,height,polygon,count,AxisAlignedRect(left.toDouble(),top.toDouble(),right+1.0,bottom+1.0))
        }
        require(candidates.size==1){if(candidates.isEmpty())"No single closed black boundary was recovered. Use a clearer, complete outlined-area map." else "Several closed black boundaries were found. Select a map containing one territory."}
        return candidates.single()
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

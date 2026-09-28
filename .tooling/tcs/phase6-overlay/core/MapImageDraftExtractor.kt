package com.koenterprises.territorycardstudio.core

import kotlin.math.*

/** Image interpretation yields proposals only. It never creates source authorization or PDF approval. */
data class MapImageText(val text:String,val bounds:AxisAlignedRect)
data class MapImageFinding(val id:String,val message:String,val sourceBounds:AxisAlignedRect?)
data class MapImageRoad(val id:String,val name:String?,val status:String,val points:List<Point2D>,val junctionA:Boolean,val junctionB:Boolean)
data class MapImageExtraction(val roads:List<MapImageRoad>,val findings:List<MapImageFinding>,val width:Int,val height:Int)

object MapImageDraftExtractor {
    private val suffix=Regex("(?i)\\b(?:road|rd|street|st|drive|dr|court|ct|lane|ln|way|avenue|ave|boulevard|blvd|circle|cir|trail|trl|place|pl|parkway|pkwy|terrace|ter)\\b")
    fun streetText(text:String):String? {
        val clean=text.trim().replace(Regex("\\s+")," ")
        if(clean.length !in 3..100 || clean.any {it.code !in 32..126})return null
        val match=suffix.findAll(clean).lastOrNull() ?: return null
        return clean.substring(0,match.range.last+1).takeIf {it.any(Char::isLetter)}
    }
    private fun color(p:Int):Int {
        val r=(p ushr 16) and 255;val g=(p ushr 8) and 255;val b=p and 255
        if(r>145 && g>125 && b<min(r,g)*0.68)return 1
        if(g>65 && g>r*1.22 && g>b*1.12)return 2
        if(r>100 && r>g*1.4 && r>b*1.4)return 3
        return 0
    }
    fun extract(width:Int,height:Int,pixels:IntArray,text:List<MapImageText>):MapImageExtraction {
        require(width in 16..1400 && height in 16..1400 && pixels.size==width*height)
        val colors=IntArray(pixels.size){color(pixels[it])};val findings=mutableListOf<MapImageFinding>()
        // Explicit legend text is evidence for excluding its nearby swatch, not an inferred road.
        text.filter {streetText(it.text)==null && (it.text.contains("work inside",true)||it.text.contains("work both",true)||it.text.contains("do not work",true))}.forEach {label->
            val b=label.bounds
            for(y in max(0,b.top.toInt()-4)..min(height-1,b.bottom.toInt()+4))
                for(x in max(0,b.left.toInt()-80)..min(width-1,b.right.toInt()+5)) colors[y*width+x]=0
        }
        val mask=BooleanArray(colors.size){colors[it]!=0}
        val seen=BooleanArray(mask.size);val queue=IntArray(mask.size)
        fun near(i:Int):List<Int> {
            val x=i%width;val y=i/width;val out=ArrayList<Int>(8)
            for(dy in -1..1)for(dx in -1..1)if(dx!=0||dy!=0){val nx=x+dx;val ny=y+dy;if(nx in 0 until width && ny in 0 until height)out+=ny*width+nx}
            return out
        }
        // Retain substantial colored components; report every rejected mark rather than erasing it silently.
        for(start in mask.indices)if(mask[start]&&!seen[start]) {
            var head=0;var tail=0;queue[tail++]=start;seen[start]=true
            var left=width;var right=0;var top=height;var bottom=0
            while(head<tail){val p=queue[head++];val x=p%width;val y=p/width;left=min(left,x);right=max(right,x);top=min(top,y);bottom=max(bottom,y)
                for(n in near(p))if(mask[n]&&!seen[n]){seen[n]=true;queue[tail++]=n}}
            val bounds=AxisAlignedRect(left.toDouble(),top.toDouble(),(right+1).toDouble(),(bottom+1).toDouble())
            val solid=tail.toDouble()/((right-left+1)*(bottom-top+1))
            val mark=tail<12 || max(right-left,bottom-top)<12
            val area=solid>0.65 && min(right-left,bottom-top)>20 && max(right-left,bottom-top)<min(right-left,bottom-top)*3
            if(mark||area){for(i in 0 until tail)mask[queue[i]]=false
                if(tail>=5)findings+=MapImageFinding("component-$start",if(area)"Review this filled area or building footprint; it was not converted into a road." else "Review this small colored mark.",bounds)}
        }
        // Zhang–Suen thinning preserves the topology of the combined color mask.
        val remove=IntArray(mask.size)
        var changed=true;var iterations=0
        while(changed && iterations++<256){changed=false
            for(pass in 0..1){var count=0
                for(y in 1 until height-1)for(x in 1 until width-1){val p=y*width+x;if(!mask[p])continue
                    val ns=intArrayOf(p-width,p-width+1,p+1,p+width+1,p+width,p+width-1,p-1,p-width-1)
                    val n=ns.map {if(mask[it])1 else 0};val total=n.sum();if(total !in 2..6)continue
                    if((0..7).count {n[it]==0&&n[(it+1)%8]==1}!=1)continue
                    val allowed=if(pass==0)n[0]*n[2]*n[4]==0&&n[2]*n[4]*n[6]==0 else n[0]*n[2]*n[6]==0&&n[0]*n[4]*n[6]==0
                    if(allowed)remove[count++]=p
                }
                if(count>0){changed=true;for(i in 0 until count)mask[remove[i]]=false}
            }
        }
        if(changed)findings+=MapImageFinding("thinning-limit","Image geometry is too dense to interpret reliably.",null)
        fun neighbors(p:Int)=near(p).filter {n->mask[n] && run {
            val dx=n%width-p%width;val dy=n/width-p/width
            dx==0 || dy==0 || (!mask[p+dx]&&!mask[p+dy*width])
        }}
        val adjacency=HashMap<Int,List<Int>>()
        mask.indices.filter {mask[it]}.forEach {adjacency[it]=neighbors(it)}
        val visited=HashSet<Long>();fun edge(a:Int,b:Int)=(min(a,b).toLong() shl 32) or max(a,b).toLong()
        val paths=mutableListOf<List<Int>>()
        fun walk(start:Int,next:Int) {val path=mutableListOf(start);var previous=start;var current=next
            while(true){if(!visited.add(edge(previous,current)))break;path+=current;val ns=adjacency.getValue(current)
                if(ns.size!=2 || current==start)break
                val n=ns.first {it!=previous};previous=current;current=n
            };if(path.size>=2)paths+=path}
        adjacency.filterValues {it.size!=2}.keys.sorted().forEach {p->adjacency.getValue(p).forEach {n->if(edge(p,n) !in visited)walk(p,n)}}
        adjacency.keys.sorted().forEach {p->adjacency.getValue(p).forEach {n->if(edge(p,n) !in visited)walk(p,n)}}
        require(paths.size<=4096){"Too many image components; use a clearer map crop"}
        val labels=text.mapNotNull {t->streetText(t.text)?.let {it to t.bounds}}
        val usedLabels=HashSet<Int>();val roads=mutableListOf<MapImageRoad>()
        paths.forEachIndexed {index,path->
            if(path.size<8){findings+=MapImageFinding("short-$index","Review a short or broken road trace.",null);return@forEachIndexed}
            val points=path.map {Point2D((it%width).toDouble(),(it/width).toDouble())}
            val votes=path.groupingBy {colors[it]}.eachCount().filterKeys {it!=0};val dominant=votes.maxByOrNull {it.value}?.key ?: 0
            if(votes.filterKeys {it!=dominant}.values.sum()>max(3,path.size/20))findings+=MapImageFinding("color-$index","A work color changes away from a detected junction; verify the split.",null)
            fun distance(b:AxisAlignedRect):Double {val cx=(b.left+b.right)/2;val cy=(b.top+b.bottom)/2;return points.minOf {hypot(it.x-cx,it.y-cy)}}
            val nearest=labels.mapIndexed {i,l->i to distance(l.second)}.sortedBy {it.second}
            val match=nearest.firstOrNull()?.takeIf {it.second<=max(28.0,min(width,height)*0.07)}
            val ambiguous=match!=null && nearest.getOrNull(1)?.second?.let {it-match.second<6.0}==true
            val name=if(ambiguous)null else match?.let {usedLabels+=it.first;labels[it.first].first}
            if(name==null)findings+=MapImageFinding("name-$index","Confirm the name of road ${index+1}; image text was missing or ambiguous.",null)
            roads+=MapImageRoad("image-road-${index+1}",name,when(dominant){1->"yellow";2->"green";3->"red";else->"context"},simplify(points,1.4),adjacency.getValue(path.first()).size>2,adjacency.getValue(path.last()).size>2)
        }
        labels.forEachIndexed {i,l->if(i !in usedLabels)findings+=MapImageFinding("unmatched-label-$i","No reliable colored road was found for ${l.first}. Verify missing or uncolored geometry.",l.second)}
        if(roads.isEmpty())findings+=MapImageFinding("no-roads","No reliable colored road network was detected. Work boundaries cannot be inferred from an unmarked map.",null)
        return MapImageExtraction(roads,findings,width,height)
    }
    private fun simplify(points:List<Point2D>,epsilon:Double):List<Point2D> {
        if(points.size<=2)return points
        val keep=BooleanArray(points.size);keep[0]=true;keep[points.lastIndex]=true
        val stack=java.util.ArrayDeque<Pair<Int,Int>>();stack.add(0 to points.lastIndex)
        while(stack.isNotEmpty()) {
            val (first,last)=stack.removeLast();val a=points[first];val b=points[last];val dx=b.x-a.x;val dy=b.y-a.y;val length=hypot(dx,dy)
            var distance=0.0;var index=first
            for(i in first+1 until last){val p=points[i];val d=if(length==0.0)hypot(p.x-a.x,p.y-a.y) else abs(dy*p.x-dx*p.y+b.x*a.y-b.y*a.x)/length
                if(d>distance){distance=d;index=i}}
            if(distance>epsilon){keep[index]=true;stack.add(first to index);stack.add(index to last)}
        }
        return points.filterIndexed {i,_->keep[i]}
    }
}

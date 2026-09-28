package com.koenterprises.territorycardstudio

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.os.ParcelFileDescriptor
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.koenterprises.territorycardstudio.core.*
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.*

data class InterpretedMapDraft(val sourceSha256:String,val roads:List<RoadGeometry>,val observations:List<SourceSegmentObservation>,val findings:List<MapImageFinding>,val recognizedText:List<MapImageText>,val buildings:List<BuildingGeometry> = emptyList(),val buildingObservations:List<SourceBuildingObservation> = emptyList())

/** Bundled OCR and local image geometry. Source images are never uploaded by this interpreter. */
data class NativeImageReview(val analysisJson:String,val acknowledged:Set<String> = emptySet()) {
    init {
        require(analysisJson.toByteArray().size<=1024*1024)
        var depth=0;var quoted=false;var escaped=false
        for(c in analysisJson){if(quoted){if(escaped)escaped=false else if(c=='\\')escaped=true else if(c=='"')quoted=false}else when(c){'"'->quoted=true;'[','{'->{depth++;require(depth<=8)};']','}'->{depth--;require(depth>=0)}}};require(!quoted && depth==0)
        val value=org.json.JSONObject(analysisJson);ExtendedValues.keys(value,"schema","sourceSha256","findings","recognizedText")
        require(value.getString("schema")=="map-image-analysis-v1" && value.getString("sourceSha256").matches(Regex("[0-9a-f]{64}")))
        require(value.getJSONArray("findings").length()<=4096 && value.getJSONArray("recognizedText").length()<=5000)
        fun rectangle(a:org.json.JSONArray){require(a.length()==4);val v=(0..3).map {a.getDouble(it)};require(v.all {it.isFinite()} && v[2]>=v[0] && v[3]>=v[1])}
        val findingRows=value.getJSONArray("findings")
        for(i in 0 until findingRows.length()) {
            val row=findingRows.getJSONObject(i);val keys=row.keys().asSequence().toSet()
            require(keys==setOf("id","message") || keys==setOf("id","message","sourceBounds"))
            require(row.get("id") is String && row.get("message") is String)
            if(!row.isNull("sourceBounds"))rectangle(row.getJSONArray("sourceBounds"))
        }
        val recognized=value.getJSONArray("recognizedText")
        for(i in 0 until recognized.length()) {val row=recognized.getJSONObject(i);ExtendedValues.keys(row,"text","bounds");require(row.get("text") is String && row.getString("text").length<=2000);rectangle(row.getJSONArray("bounds"))}
        val rows=findings();require(rows.map {it.first}.distinct().size==rows.size && acknowledged.all {id->rows.any {it.first==id}})
        require(rows.all {it.first.length<=120 && it.second.length<=1000})
        require(ExtendedValues.canonical(value)==analysisJson)
    }
    val sha256 get()=BundleIntegrity.sha256(analysisJson.byteInputStream())
    fun findings():List<Pair<String,String>> {val a=org.json.JSONObject(analysisJson).getJSONArray("findings");return (0 until a.length()).map {a.getJSONObject(it).let {v->v.getString("id") to v.getString("message")}}}
    fun bounds(id:String):AxisAlignedRect? {
        val rows=org.json.JSONObject(analysisJson).getJSONArray("findings")
        val row=(0 until rows.length()).map {rows.getJSONObject(it)}.firstOrNull {it.getString("id")==id} ?: return null
        if(row.isNull("sourceBounds"))return null
        val a=row.getJSONArray("sourceBounds");return AxisAlignedRect(a.getDouble(0),a.getDouble(1),a.getDouble(2),a.getDouble(3))
    }
    fun complete(roads:List<RoadGeometry> = emptyList())=findings().all {(id,_)->id in acknowledged && when {
        id.startsWith("side-")->roads.any {it.segmentId==id.removePrefix("side-") && it.insideSide in setOf("left","right")}
        id.startsWith("name-")->id.removePrefix("name-").toIntOrNull()?.takeIf {it in 0..4095}?.let {index->roads.any {it.segmentId=="image-road-${index+1}" && !it.name.startsWith("Unresolved road")}}==true
        else->false
    }}
    fun correctable(id:String)=id.matches(Regex("side-image-road-[1-9][0-9]{0,3}")) || id.removePrefix("name-").toIntOrNull()?.let {id.startsWith("name-") && it in 0..4095}==true
    companion object {
        fun from(result:InterpretedMapDraft):NativeImageReview {
            val findings=org.json.JSONArray(result.findings.map {f->org.json.JSONObject().put("id",f.id).put("message",f.message).put("sourceBounds",f.sourceBounds?.let {org.json.JSONArray(listOf(it.left,it.top,it.right,it.bottom))} ?: org.json.JSONObject.NULL)})
            val text=org.json.JSONArray(result.recognizedText.map {t->org.json.JSONObject().put("text",t.text).put("bounds",org.json.JSONArray(listOf(t.bounds.left,t.bounds.top,t.bounds.right,t.bounds.bottom)))})
            return NativeImageReview(ExtendedValues.canonical(org.json.JSONObject().put("schema","map-image-analysis-v1").put("sourceSha256",result.sourceSha256).put("findings",findings).put("recognizedText",text)))
        }
    }
}

class AndroidMapImageInterpreter {
    fun interpret(file:File,expectedSha256:String,housingType:String="",buildingLabel:((String,Point2D)->BuildingLabelItem)?=null):InterpretedMapDraft {
        require(file.inputStream().use(BundleIntegrity::sha256)==expectedSha256) {"Source changed before image interpretation"}
        val source=decode(file)
        val pixels=IntArray(source.width*source.height);source.getPixels(pixels,0,source.width,0,0,source.width,source.height)
        var inFlight:com.google.android.gms.tasks.Task<com.google.mlkit.vision.text.Text>?=null
        try {
            val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val text=try {
                fun recognized(rotation:Int,region:AxisAlignedRect?=null):List<MapImageText> {
                    val left=region?.left?.toInt() ?: 0;val top=region?.top?.toInt() ?: 0
                    val width=region?.let {(it.right-it.left).toInt()} ?: source.width
                    val height=region?.let {(it.bottom-it.top).toInt()} ?: source.height
                    val scale=if(region==null && rotation==0)1.0 else 3.0
                    val bitmap=if(scale==1.0)source else Bitmap.createBitmap(source,left,top,width,height,Matrix().apply {postScale(scale.toFloat(),scale.toFloat());postRotate(rotation.toFloat())},true)
                    val task=recognizer.process(InputImage.fromBitmap(bitmap,0))
                    inFlight=task
                    // Timeout does not cancel ML Kit. Release its bitmap only after completion.
                    if(bitmap!==source)task.addOnCompleteListener(java.util.concurrent.Executor {it.run()}) {bitmap.recycle()}
                    val result=Tasks.await(task,60,TimeUnit.SECONDS)
                    val numeric=Regex("^[| ]*[0-9]+(?:[ \t]*[-/–—−][ \t]*[0-9]+)*(?:[ \t]*[A-Z])?[| ]*$")
                    fun box(b:android.graphics.Rect):AxisAlignedRect=when(rotation) {
                        90->AxisAlignedRect(left+b.top/scale,top+height-b.right/scale,left+b.bottom/scale,top+height-b.left/scale)
                        270->AxisAlignedRect(left+width-b.bottom/scale,top+b.left/scale,left+width-b.top/scale,top+b.right/scale)
                        else->AxisAlignedRect(left+b.left/scale,top+b.top/scale,left+b.right/scale,top+b.bottom/scale)
                    }
                    return result.textBlocks.flatMap {block->block.lines.flatMap {line->
                        val pieces=if(line.elements.size>1 && line.elements.all {numeric.matches(it.text)})line.elements.mapNotNull {e->e.boundingBox?.let {MapImageText(e.text,box(it))}}
                            else listOfNotNull(line.boundingBox?.let {MapImageText(line.text,box(it))})
                        pieces.filter {(rotation==0 && region==null) || numeric.matches(it.text)}
                    }}
                }
                val normal=recognized(0)
                val rotated=if(housingType in setOf("apartment","condo","townhome","mobile_home","manufactured_home"))MapImageDraftExtractor.numericOcrRegions(source.width,source.height,pixels).flatMap {region->listOf(0,90,270).flatMap {rotation->recognized(rotation,region)}} else emptyList()
                normal+rotated
            } finally {
                val pending=inFlight
                if(pending!=null && !pending.isComplete)pending.addOnCompleteListener(java.util.concurrent.Executor {it.run()}) {recognizer.close()}
                else recognizer.close()
            }
            val extraction=MapImageDraftExtractor.extract(source.width,source.height,pixels,text)
            val all=extraction.roads.flatMap {it.points}+extraction.buildings.flatMap {it.polygon}
            val findings=extraction.findings.toMutableList()
            if(all.isEmpty())return InterpretedMapDraft(expectedSha256,emptyList(),emptyList(),findings,text)
            val left=all.minOf {it.x};val right=all.maxOf {it.x};val top=all.minOf {it.y};val bottom=all.maxOf {it.y}
            val scale=min(511.0/max(1.0,right-left),283.0/max(1.0,bottom-top))
            val offsetX=176.0+(571.0-(right-left)*scale)/2
            val offsetY=20.0+(343.0-(bottom-top)*scale)/2
            fun mapped(p:Point2D)=Point2D(offsetX+(p.x-left)*scale,offsetY+(p.y-top)*scale)
            val multiUnit=housingType in setOf("apartment","condo","townhome","mobile_home","manufactured_home")
            require(!multiUnit || extraction.buildings.isEmpty() || buildingLabel!=null) {"Building label layout is required for extracted footprints"}
            val buildings=if(multiUnit && buildingLabel!=null)extraction.buildings.map {b->
                val labels=b.labels.map {label->buildingLabel(label.text.replace('–','-').replace('—','-').replace('−','-'),mapped(Point2D((label.bounds.left+label.bounds.right)/2,(label.bounds.top+label.bounds.bottom)/2)))}
                if(b.status=="yellow")findings+=MapImageFinding("building-color-${b.id}","A yellow footprint requires assignment review.",null)
                BuildingGeometry(b.id,labels.joinToString("/"){it.text},housingType,b.status=="green","",labels.map {it.text},labels,b.polygon.map(::mapped))
            } else emptyList()
            if(!multiUnit && extraction.buildings.isNotEmpty())findings+=MapImageFinding("building-type","Numbered buildings were found; verify the territory housing type before generation.",null)
            val buildingObservations=buildings.map {b->SourceBuildingObservation(b.buildingId,b.sourceMembers,b.assigned,"Footprint and member labels extracted from source image; requires source review",false)}
            val roads=extraction.roads.map {r->
                val name=r.name ?: "Unresolved road ${r.id.removePrefix("image-road-")}"
                val role=when(r.status){"yellow"->"perimeter";"green"->"interior";"red"->"excluded";else->"context"}
                if(r.status=="yellow")findings+=MapImageFinding("side-${r.id}","Confirm the worked side of $name from the source boundary.",AxisAlignedRect(r.points.minOf {it.x},r.points.minOf {it.y},r.points.maxOf {it.x}+1,r.points.maxOf {it.y}+1))
                RoadGeometry(r.id,name,TopologyOverlapDecisionEngine.normalizeRoadName(name),r.status,role,"",false,
                    if(r.junctionA)"junction" else "termination",if(r.junctionB)"junction" else "termination",4.0,
                    r.points.map(::mapped))
            }
            val observations=roads.map {r->SourceSegmentObservation(r.segmentId,r.name,r.status,r.role,r.insideSide,r.accessOnly,r.endpointAKind,r.endpointBKind,
                "Automatically extracted from source image $expectedSha256; requires source review",false)}
            require(file.inputStream().use(BundleIntegrity::sha256)==expectedSha256) {"Source changed during image interpretation"}
            return InterpretedMapDraft(expectedSha256,roads,observations,findings,text,buildings,buildingObservations)
        } finally {
            val pending=inFlight
            if(pending!=null && !pending.isComplete)pending.addOnCompleteListener(java.util.concurrent.Executor {it.run()}) {source.recycle()}
            else source.recycle()
        }
    }
    internal fun decode(file:File):Bitmap {
        val pdf=file.inputStream().use {input->ByteArray(5).also {input.read(it)}}.toString(Charsets.US_ASCII)=="%PDF-"
        if(pdf)return PdfRenderer(ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)).use {renderer->
            require(renderer.pageCount==1) {"Choose a single map picture or a one-page map PDF for automatic interpretation"}
            renderer.openPage(0).use {page->val scale=min(1400.0/page.width,1400.0/page.height)
                Bitmap.createBitmap(max(16,(page.width*scale).toInt()),max(16,(page.height*scale).toInt()),Bitmap.Config.ARGB_8888).also {b->b.eraseColor(android.graphics.Color.WHITE);page.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)}}
        }
        val options=BitmapFactory.Options().apply {inJustDecodeBounds=true};BitmapFactory.decodeFile(file.path,options)
        require(options.outWidth in 16..30000 && options.outHeight in 16..30000) {"Map image dimensions are unsupported"}
        options.inJustDecodeBounds=false;options.inSampleSize=1
        while(max(options.outWidth,options.outHeight)/options.inSampleSize>2800)options.inSampleSize*=2
        var bitmap=requireNotNull(BitmapFactory.decodeFile(file.path,options)) {"Cannot decode map picture"}
        val orientation=runCatching {ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)}.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix=Matrix()
        when(orientation){ExifInterface.ORIENTATION_ROTATE_90->matrix.postRotate(90f);ExifInterface.ORIENTATION_ROTATE_180->matrix.postRotate(180f);ExifInterface.ORIENTATION_ROTATE_270->matrix.postRotate(270f);ExifInterface.ORIENTATION_FLIP_HORIZONTAL->matrix.postScale(-1f,1f);ExifInterface.ORIENTATION_FLIP_VERTICAL->matrix.postScale(1f,-1f);ExifInterface.ORIENTATION_TRANSPOSE->{matrix.postRotate(90f);matrix.postScale(-1f,1f)};ExifInterface.ORIENTATION_TRANSVERSE->{matrix.postRotate(270f);matrix.postScale(-1f,1f)}}
        if(!matrix.isIdentity){val rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true);if(rotated!==bitmap)bitmap.recycle();bitmap=rotated}
        val scale=min(1.0,1400.0/max(bitmap.width,bitmap.height))
        if(scale<1){val resized=Bitmap.createScaledBitmap(bitmap,max(16,(bitmap.width*scale).toInt()),max(16,(bitmap.height*scale).toInt()),true);if(resized!==bitmap)bitmap.recycle();bitmap=resized}
        return bitmap
    }
}

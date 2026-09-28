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

data class InterpretedMapDraft(val sourceSha256:String,val roads:List<RoadGeometry>,val observations:List<SourceSegmentObservation>,val findings:List<MapImageFinding>,val recognizedText:List<MapImageText>)

/** Bundled OCR and local image geometry. Source images are never uploaded by this interpreter. */
data class NativeImageReview(val analysisJson:String,val acknowledged:Set<String> = emptySet()) {
    init {
        require(analysisJson.toByteArray().size<=1024*1024)
        var depth=0;var quoted=false;var escaped=false
        for(c in analysisJson){if(quoted){if(escaped)escaped=false else if(c=='\\')escaped=true else if(c=='"')quoted=false}else when(c){'"'->quoted=true;'[','{'->{depth++;require(depth<=8)};']','}'->{depth--;require(depth>=0)}}};require(!quoted && depth==0)
        val value=org.json.JSONObject(analysisJson);ExtendedValues.keys(value,"schema","sourceSha256","findings","recognizedText")
        require(value.getString("schema")=="map-image-analysis-v1" && value.getString("sourceSha256").matches(Regex("[0-9a-f]{64}")))
        require(value.getJSONArray("findings").length()<=4096 && value.getJSONArray("recognizedText").length()<=5000)
        val rows=findings();require(rows.map {it.first}.distinct().size==rows.size && acknowledged.all {id->rows.any {it.first==id}})
        require(rows.all {it.first.length<=120 && it.second.length<=1000})
        require(ExtendedValues.canonical(value)==analysisJson)
    }
    val sha256 get()=BundleIntegrity.sha256(analysisJson.byteInputStream())
    fun findings():List<Pair<String,String>> {val a=org.json.JSONObject(analysisJson).getJSONArray("findings");return (0 until a.length()).map {a.getJSONObject(it).let {v->v.getString("id") to v.getString("message")}}}
    fun complete()=findings().all {it.first in acknowledged}
    companion object {
        fun from(result:InterpretedMapDraft):NativeImageReview {
            val findings=org.json.JSONArray(result.findings.map {f->org.json.JSONObject().put("id",f.id).put("message",f.message)})
            val text=org.json.JSONArray(result.recognizedText.map {t->org.json.JSONObject().put("text",t.text).put("bounds",org.json.JSONArray(listOf(t.bounds.left,t.bounds.top,t.bounds.right,t.bounds.bottom)))})
            return NativeImageReview(ExtendedValues.canonical(org.json.JSONObject().put("schema","map-image-analysis-v1").put("sourceSha256",result.sourceSha256).put("findings",findings).put("recognizedText",text)))
        }
    }
}

class AndroidMapImageInterpreter {
    fun interpret(file:File,expectedSha256:String):InterpretedMapDraft {
        require(file.inputStream().use(BundleIntegrity::sha256)==expectedSha256) {"Source changed before image interpretation"}
        val source=decode(file)
        try {
            val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val text=try {
                Tasks.await(recognizer.process(InputImage.fromBitmap(source,0)),60,TimeUnit.SECONDS).textBlocks.flatMap {block->block.lines.mapNotNull {line->
                    line.boundingBox?.let {b->MapImageText(line.text,AxisAlignedRect(b.left.toDouble(),b.top.toDouble(),b.right.toDouble(),b.bottom.toDouble()))}
                }}
            } finally {recognizer.close()}
            val pixels=IntArray(source.width*source.height);source.getPixels(pixels,0,source.width,0,0,source.width,source.height)
            val extraction=MapImageDraftExtractor.extract(source.width,source.height,pixels,text)
            val all=extraction.roads.flatMap {it.points}
            val findings=extraction.findings.toMutableList()
            if(all.isEmpty())return InterpretedMapDraft(expectedSha256,emptyList(),emptyList(),findings,text)
            val left=all.minOf {it.x};val right=all.maxOf {it.x};val top=all.minOf {it.y};val bottom=all.maxOf {it.y}
            val scale=min(511.0/max(1.0,right-left),283.0/max(1.0,bottom-top))
            val offsetX=176.0+(571.0-(right-left)*scale)/2
            val offsetY=20.0+(343.0-(bottom-top)*scale)/2
            val roads=extraction.roads.map {r->
                val name=r.name ?: "Unresolved road ${r.id.removePrefix("image-road-")}"
                val role=when(r.status){"yellow"->"perimeter";"green"->"interior";"red"->"excluded";else->"context"}
                if(r.status=="yellow")findings+=MapImageFinding("side-${r.id}","Confirm the worked side of $name from the source boundary.",null)
                RoadGeometry(r.id,name,TopologyOverlapDecisionEngine.normalizeRoadName(name),r.status,role,"",false,
                    if(r.junctionA)"junction" else "termination",if(r.junctionB)"junction" else "termination",4.0,
                    r.points.map {Point2D(offsetX+(it.x-left)*scale,offsetY+(it.y-top)*scale)})
            }
            val observations=roads.map {r->SourceSegmentObservation(r.segmentId,r.name,r.status,r.role,r.insideSide,r.accessOnly,r.endpointAKind,r.endpointBKind,
                "Automatically extracted from source image $expectedSha256; requires source review",false)}
            require(file.inputStream().use(BundleIntegrity::sha256)==expectedSha256) {"Source changed during image interpretation"}
            return InterpretedMapDraft(expectedSha256,roads,observations,findings,text)
        } finally {source.recycle()}
    }
    private fun decode(file:File):Bitmap {
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

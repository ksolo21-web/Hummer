package com.koenterprises.territorycardstudio

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.koenterprises.territorycardstudio.core.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

/** An original-byte closeup of exactly the selected finding. The context map remains above it.
 * Readability resets for every finding, source, and source hash. A previous crop cannot authorize
 * a new finding, and source integrity is checked again when the user records the decision. */
@Composable internal fun OutlinedFindingCrop(file:File,review:OutlinedNativeReview,finding:MapImageFinding,
    roads:List<RoadGeometry>,onReadable:(Boolean)->Unit) {
    val box=finding.sourceBounds ?: return
    var bitmap by remember(file.path,review.sourceSha256,finding){mutableStateOf<Bitmap?>(null)}
    var origin by remember(file.path,review.sourceSha256,finding){mutableStateOf(Point2D(0.0,0.0))}
    var error by remember(file.path,review.sourceSha256,finding){mutableStateOf<String?>(null)}
    val callback by rememberUpdatedState(onReadable)
    LaunchedEffect(file.path,review.sourceSha256,finding) {
        callback(false)
        runCatching {withContext(Dispatchers.IO) {
            require(file.inputStream().use(BundleIntegrity::sha256)==review.sourceSha256){"The source changed. Import it again before reviewing this finding."}
            val full=AndroidMapImageInterpreter().decode(file)
            try {
                require(full.width==review.extraction.boundary.width && full.height==review.extraction.boundary.height){"Source coordinates no longer match this analysis"}
                require(box.left>=0 && box.top>=0 && box.right<=full.width && box.bottom<=full.height && box.right>box.left && box.bottom>box.top)
                val left=max(0,floor(box.left).toInt()-28);val top=max(0,floor(box.top).toInt()-28)
                val right=min(full.width,ceil(box.right).toInt()+28);val bottom=min(full.height,ceil(box.bottom).toInt()+28)
                val crop=Bitmap.createBitmap(full,left,top,right-left,bottom-top)
                val retained=if(crop===full)requireNotNull(crop.copy(Bitmap.Config.ARGB_8888,false)) else crop
                retained to Point2D(left.toDouble(),top.toDouble())
            } finally {full.recycle()}
        }}.onSuccess {(image,point)->bitmap=image;origin=point;callback(true)}.onFailure {error=it.message ?: "The selected source region could not be displayed"}
    }
    DisposableEffect(bitmap){val old=bitmap;onDispose {old?.recycle()}}
    var overlay by remember(finding.id,review.sourceSha256){mutableStateOf(false)}
    Row {Switch(overlay,{overlay=it},modifier=Modifier.testTag("outlined-finding-overlay"));Text(if(overlay)"Finding bounds and matching source traces" else "Original source without overlays",Modifier.padding(top=12.dp))}
    Box(Modifier.fillMaxWidth().height(300.dp).testTag("outlined-finding-crop")) {
        bitmap?.let {image->
            Image(image.asImageBitmap(),"Exact source closeup for ${finding.id}",Modifier.fillMaxSize())
            if(overlay)Canvas(Modifier.fillMaxSize().clipToBounds()) {
                val scale=min(size.width/image.width,size.height/image.height)
                val dx=(size.width-image.width*scale)/2;val dy=(size.height-image.height*scale)/2
                fun point(p:Point2D)=Offset(dx+((p.x-origin.x)*scale).toFloat(),dy+((p.y-origin.y)*scale).toFloat())
                val a=point(Point2D(box.left,box.top));val b=point(Point2D(box.right,box.bottom))
                drawRect(Color(0xFF0077FF),a,androidx.compose.ui.geometry.Size(b.x-a.x,b.y-a.y),style=Stroke(1.dp.toPx()))
                roads.forEach {road->road.points.map(review.transform::source).zipWithNext().forEach {(first,last)->
                    drawLine(Color(0xFFFF8800),point(first),point(last),1.dp.toPx())
                }}
            }
        }
        if(bitmap==null && error==null)CircularProgressIndicator()
    }
    error?.let {Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("outlined-finding-source-error"))}
}

@Composable internal fun OutlinedFindingReviewPanel(review:OutlinedNativeReview,roads:List<RoadGeometry>,source:File?,
    onChanged:(OutlinedNativeReview,List<RoadGeometry>)->Unit) {
    val unresolved=remember(review.document,roads){review.unresolvedFindings(roads)}
    val decisions=remember(review.document){review.findingResolutions().associateBy {it.findingId}}
    var showReviewed by remember(review.sourceSha256){mutableStateOf(false)}
    var index by remember(review.sourceSha256,showReviewed){mutableStateOf(0)}
    var message by remember(review.sourceSha256){mutableStateOf("")}
    val unresolvedIds=unresolved.map {it.id}.toSet()
    val actionable=if(showReviewed)review.extraction.findings.filter {it.id in decisions}
        else unresolved.filter {review.allowedResolutions(it.id).isNotEmpty()}
    Text("${unresolved.size} unresolved image findings",style=MaterialTheme.typography.titleMedium,modifier=Modifier.testTag("outlined-finding-count"))
    Text("${unresolved.count {it.id.startsWith("name-")}} road-name findings remain linked to individual source-trace review. A mark decision never removes a road trace or verifies a building.")
    Row {FilterChip(!showReviewed,{showReviewed=false},label={Text("Pending marks / labels")},modifier=Modifier.testTag("outlined-findings-pending"))
        FilterChip(showReviewed,{showReviewed=true},label={Text("Recorded (${decisions.size})")},modifier=Modifier.testTag("outlined-findings-recorded"))}
    val finding=actionable.getOrNull(index.coerceIn(0,maxOf(0,actionable.lastIndex)))
    if(finding==null)Text(if(showReviewed)"No finding decisions recorded." else "No individually reviewable marks remain. Missing names, roads and boundary findings require source-backed correction.")
    else key(review.sourceSha256,finding.id,source?.path,showReviewed) {
        var kind by remember {mutableStateOf(decisions[finding.id]?.kind.orEmpty())}
        var evidence by remember {mutableStateOf(decisions[finding.id]?.evidence.orEmpty())}
        var ids by remember {mutableStateOf(decisions[finding.id]?.outputIds?.joinToString(", ").orEmpty())}
        var readable by remember {mutableStateOf(false)}
        var inspected by remember(kind,evidence,ids,review.document,roads){mutableStateOf(false)}
        Row {TextButton(onClick={index=max(0,index-1)},enabled=index>0,modifier=Modifier.testTag("outlined-finding-previous")){Text("Previous finding")}
            Text("${index.coerceAtMost(actionable.lastIndex)+1} / ${actionable.size}")
            TextButton(onClick={index++},enabled=index<actionable.lastIndex,modifier=Modifier.testTag("outlined-finding-next")){Text("Next finding")}}
        Text("${finding.id}: ${finding.message}",modifier=Modifier.testTag("outlined-finding-identity"))
        if(finding.id in decisions)Text(if(finding.id in unresolvedIds)"This recorded decision is stale or invalid. Review it again." else "Recorded source decision. This is not a card approval.")
        val selectedIds=ids.split(',').map(String::trim).filter(String::isNotEmpty)
        val selected=if(kind=="MATCHED_ROAD")roads.filter {it.segmentId in selectedIds} else emptyList()
        if(source!=null)OutlinedFindingCrop(source,review,finding,selected){readable=it}
        else Text("The original source is unavailable. Recording is blocked.",color=MaterialTheme.colorScheme.error)
        review.allowedResolutions(finding.id).forEach {value->FilterChip(kind==value,{kind=value},label={Text(value.replace('_',' '))},modifier=Modifier.testTag("outlined-finding-kind-$value"))}
        if(kind=="MATCHED_ROAD") {
            Text("Select only an individually source-reviewed road aligned with this exact street label. A nearby road or an unreviewed trace cannot resolve it.")
            OutlinedTextField(ids,{ids=it},label={Text("Matching output road IDs, separated by commas")},modifier=Modifier.fillMaxWidth().testTag("outlined-finding-road-ids"))
            selected.forEach {Text("${it.segmentId}: ${it.name}")}
        }
        OutlinedTextField(evidence,{evidence=it},label={Text("Specific evidence visible in this source closeup")},modifier=Modifier.fillMaxWidth().testTag("outlined-finding-evidence"))
        Row {Checkbox(inspected,{inspected=it},enabled=readable && kind.isNotEmpty(),modifier=Modifier.testTag("outlined-finding-inspected"));Text("I inspected this exact finding in the original source closeup",Modifier.weight(1f))}
        Button(onClick={runCatching {
            require(source!=null && source.inputStream().use(BundleIntegrity::sha256)==review.sourceSha256){"The source changed. Import it again before recording a finding."}
            val output=if(kind=="MATCHED_ROAD")selectedIds else emptyList()
            val next=review.reviewFinding(finding.id,kind,evidence,output,roads)
            require(next.unresolvedFindings(roads).none {it.id==finding.id}){"The source finding remains unresolved"}
            onChanged(next,roads);message="One source finding recorded. Coverage and card approval remain separate."
        }.onFailure {message=it.message.orEmpty()}},enabled=readable && inspected && kind in review.allowedResolutions(finding.id) && evidence.trim().length in 8..2000,modifier=Modifier.testTag("outlined-finding-record")){Text("Record this finding")}
        if(finding.id in decisions)TextButton(onClick={onChanged(review.clearFinding(finding.id),roads);message="Decision removed; the finding needs review again."},modifier=Modifier.testTag("outlined-finding-clear")){Text("Reopen this finding")}
    }
    Text(message,modifier=Modifier.testTag("outlined-finding-status"))
}

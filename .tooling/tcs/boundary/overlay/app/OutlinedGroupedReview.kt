package com.koenterprises.territorycardstudio

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

/** Small, explicit omission groups. Existing decisions cannot be overwritten in bulk. */
internal fun reviewOutlinedGroup(review:OutlinedNativeReview,roads:List<RoadGeometry>,ids:Set<String>,
    cellX:Int,cellY:Int,kind:OutlinedSpanDisposition,reason:String,evidence:String):Pair<OutlinedNativeReview,List<RoadGeometry>> {
    require(ids.size in 1..12 && kind!=OutlinedSpanDisposition.ROAD)
    require(evidence.trim().length in 8..1600)
    require(kind!=OutlinedSpanDisposition.NON_ROAD || reason in listOf("PARKING_MARKING","BUILDING_EDGE","MAP_SYMBOL","LABEL_ARTIFACT"))
    val selected=review.extraction.roads.filter {it.road.id in ids}
    require(selected.size==ids.size && review.spans.none {it.candidateId in ids}){"Select only unreviewed traces"}
    require(selected.all {p->p.road.points.all {it.x>=cellX*96 && it.x<(cellX+1)*96 && it.y>=cellY*96 && it.y<(cellY+1)*96}}){"Every selected trace must fit completely inside this source region"}
    val output=roads.filterNot {it.segmentId in ids}
    var result=review
    val note="Region [$cellX,$cellY], explicit IDs ${ids.sorted().joinToString()}; ${if(kind==OutlinedSpanDisposition.NON_ROAD)reason else kind.name}: ${evidence.trim()}"
    require(note.length<=2000)
    ids.sorted().forEach {id->result=result.reviewSpan(id,0.0,1.0,kind,note,output,null)}
    val failures=result.coverageFailures(output).filter {failure->ids.any {failure.endsWith(":$it")} && !failure.startsWith("UNACCOUNTED_CANDIDATE")}
    require(failures.isEmpty()){failures.joinToString("; ")}
    return result to output
}

@Composable internal fun OutlinedGroupedReview(review:OutlinedNativeReview,roads:List<RoadGeometry>,source:File?,onChanged:(OutlinedNativeReview,List<RoadGeometry>)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    TextButton(onClick={expanded=!expanded},modifier=Modifier.testTag("outlined-group-open")){Text(if(expanded)"Close region review" else "Review small source regions")}
    if(!expanded)return
    val regions=review.extraction.roads.filter {p->review.spans.none {it.candidateId==p.road.id}}
        .groupBy {p->floor(p.road.points.first().x/96).toInt() to floor(p.road.points.first().y/96).toInt()}
        .mapValues {(cell,items)->items.filter {p->p.road.points.all {floor(it.x/96).toInt()==cell.first && floor(it.y/96).toInt()==cell.second}}}
        .filterValues {it.isNotEmpty()}.toSortedMap(compareBy<Pair<Int,Int>> {it.second}.thenBy {it.first})
    var index by remember {mutableStateOf(0)}
    val cell=regions.keys.toList().getOrNull(index.coerceIn(0,maxOf(0,regions.size-1))) ?: run {Text("No unreviewed traces fit these regions. Use individual span review.");return}
    val proposals=regions.getValue(cell)
    var ids by remember(review.document,cell){mutableStateOf(emptySet<String>())}
    var kind by remember(cell){mutableStateOf(OutlinedSpanDisposition.NON_ROAD)}
    var reason by remember(cell){mutableStateOf("BUILDING_EDGE")}
    var evidence by remember(cell){mutableStateOf("")}
    var confirmed by remember(ids,cell,kind,reason){mutableStateOf(false)}
    var readable by remember(cell,source,review.sourceSha256){mutableStateOf(false)}
    var message by remember {mutableStateOf("")}
    Text("Region ${cell.first}, ${cell.second} • ${proposals.size} unreviewed traces. Select at most 12 after inspecting the crop. Traces crossing region edges remain in individual review.")
    Row {TextButton(onClick={index=maxOf(0,index-1)},enabled=index>0){Text("Previous region")};TextButton(onClick={index++},enabled=index<regions.size-1){Text("Next region")}}
    if(source!=null)OutlinedGroupCrop(source,review.sourceSha256,cell,proposals.filter {it.road.id in ids}){readable=it}
    proposals.forEach {p->Row {Checkbox(p.road.id in ids,{checked->ids=if(checked)ids+p.road.id else ids-p.road.id},enabled=p.road.id in ids || ids.size<12,modifier=Modifier.testTag("outlined-group-select-${p.road.id}"));Text(p.road.id,Modifier.padding(top=12.dp))}}
    listOf(OutlinedSpanDisposition.NON_ROAD,OutlinedSpanDisposition.OUTSIDE_CONTEXT).forEach {value->FilterChip(kind==value,{kind=value},label={Text(value.name.replace('_',' '))})}
    if(kind==OutlinedSpanDisposition.NON_ROAD)listOf("PARKING_MARKING","BUILDING_EDGE","MAP_SYMBOL","LABEL_ARTIFACT").forEach {value->FilterChip(reason==value,{reason=value},label={Text(value.replace('_',' '))})}
    Text("Selected traces must all have this same disposition. Mixed street/parking traces need individual span correction. Outside context requires every point and segment to be outside the boundary.")
    OutlinedTextField(evidence,{evidence=it;confirmed=false},label={Text("Evidence visible for all selected traces")},modifier=Modifier.fillMaxWidth())
    Row {Checkbox(confirmed,{confirmed=it},enabled=readable && ids.isNotEmpty());Text("I inspected every selected orange trace in this source crop",Modifier.weight(1f))}
    Button(onClick={runCatching {val (r,out)=reviewOutlinedGroup(review,roads,ids,cell.first,cell.second,kind,reason,evidence);onChanged(r,out);message="Recorded ${ids.size} explicit source dispositions."}.onFailure {message=it.message.orEmpty()}},enabled=confirmed && readable && ids.isNotEmpty() && evidence.trim().length in 8..1600,modifier=Modifier.testTag("outlined-group-record")){Text("Record ${ids.size} selected traces")}
    Text(message)
}

@Composable private fun OutlinedGroupCrop(file:File,sha:String,cell:Pair<Int,Int>,selected:List<OutlinedRoadProposal>,onReadable:(Boolean)->Unit) {
    var bitmap by remember(file.path,sha,cell){mutableStateOf<Bitmap?>(null)}
    var error by remember(file.path,sha,cell){mutableStateOf<String?>(null)}
    LaunchedEffect(file.path,sha,cell){onReadable(false);runCatching {withContext(Dispatchers.IO){
        require(file.inputStream().use(BundleIntegrity::sha256)==sha){"Source changed; import it again"}
        val b=AndroidMapImageInterpreter().decode(file)
        try {val left=cell.first*96;val top=cell.second*96;val crop=Bitmap.createBitmap(b,left,top,min(96,b.width-left),min(96,b.height-top));if(crop===b)requireNotNull(crop.copy(Bitmap.Config.ARGB_8888,false))else crop}finally {b.recycle()}
    }}.onSuccess {bitmap=it;onReadable(true)}.onFailure {error=it.message}}
    DisposableEffect(bitmap){val old=bitmap;onDispose {old?.recycle()}}
    var overlay by remember(cell){mutableStateOf(true)}
    Row {Switch(overlay,{overlay=it});Text(if(overlay)"Selected exact traces" else "Original source without overlays",Modifier.padding(top=12.dp))}
    Box(Modifier.fillMaxWidth().height(320.dp).testTag("outlined-group-crop")){bitmap?.let {b->
        Image(b.asImageBitmap(),"Exact source region with selected traces",Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()){
            val scale=min(size.width/b.width,size.height/b.height);val dx=(size.width-b.width*scale)/2;val dy=(size.height-b.height*scale)/2
            if(overlay)selected.forEach {p->
                val path=androidx.compose.ui.graphics.Path()
                p.road.points.forEachIndexed {i,v->val x=dx+((v.x-cell.first*96)*scale).toFloat();val y=dy+((v.y-cell.second*96)*scale).toFloat();if(i==0)path.moveTo(x,y)else path.lineTo(x,y)}
                drawPath(path,Color(0xFFFF8800),style=Stroke(1.5.dp.toPx()))
                p.road.points.firstOrNull()?.let {v->drawCircle(Color(0xFF0077FF),2.dp.toPx(),Offset(dx+((v.x-cell.first*96)*scale).toFloat(),dy+((v.y-cell.second*96)*scale).toFloat()))}
            }
        }
    }}
    error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
}

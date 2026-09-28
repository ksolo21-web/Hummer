package com.koenterprises.territorycardstudio

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.koenterprises.territorycardstudio.core.*
import java.io.File

@Composable internal fun OutlinedNativeReviewPanel(review:OutlinedNativeReview,roads:List<RoadGeometry>,source:File?,
    onChanged:(OutlinedNativeReview,List<RoadGeometry>)->Unit) {
    var group by remember {mutableStateOf("TERRITORY")}
    var index by remember(group) {mutableStateOf(0)}
    var message by remember {mutableStateOf("")}
    val triage=remember(review.document){OutlinedMapTriageBuilder.build(review.extraction)}
    val candidates=review.extraction.roads.filter {triage.priorities[it.road.id]?.name==group}
    val current=candidates.getOrNull(index.coerceIn(0,maxOf(0,candidates.lastIndex)))
    val reviewed=review.spans.map {it.candidateId}.toSet()
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Outlined-map source review",style=MaterialTheme.typography.titleLarge)
        Text("${reviewed.size} of ${review.extraction.roads.size} traces have a recorded disposition. Uncertain marks remain separate findings.")
        if(source!=null)NativeSourcePreview(source,outline=review.extraction.boundary.polygon,sourceRoad=current?.road?.points.orEmpty()){}
        Row {Checkbox(review.boundaryConfirmed,{onChanged(review.confirmBoundary(it),roads)},modifier=Modifier.testTag("outlined-boundary-confirm"));Text("The blue outline matches the complete territory boundary",Modifier.padding(top=12.dp))}
        listOf("TERRITORY","CONNECTED_APPROACH","SURROUNDING_CONTEXT").forEach {value->FilterChip(group==value,{group=value},label={Text(value.replace('_',' '))})}
        Row {TextButton(onClick={index--},enabled=index>0){Text("Previous trace")};Text("${if(candidates.isEmpty())0 else index.coerceAtMost(candidates.lastIndex)+1} / ${candidates.size}");TextButton(onClick={index++},enabled=index<candidates.lastIndex){Text("Next trace")}}
        current?.let {p->
            val id=p.road.id;val old=roads.firstOrNull {it.segmentId==id}
            var kind by remember(id){mutableStateOf("ROAD")}
            var name by remember(id){mutableStateOf(old?.name ?: p.road.name.orEmpty())}
            var status by remember(id){mutableStateOf(old?.status ?: "context")}
            var side by remember(id){mutableStateOf(old?.insideSide ?: "")}
            var reason by remember(id){mutableStateOf("PARKING_MARKING")}
            var evidence by remember(id){mutableStateOf("")}
            Text("$id • ${p.relation.name.replace('_',' ')}"+(if(id in reviewed)" • review recorded" else ""))
            Text("Orange shows this exact source trace. Its name and work instruction are proposals until you check them.")
            listOf("ROAD","NON_ROAD","OUTSIDE_CONTEXT").forEach {value->FilterChip(kind==value,{kind=value},label={Text(value.replace('_',' '))})}
            if(kind=="ROAD") {
                OutlinedTextField(name,{name=it},label={Text("Verified street name")},modifier=Modifier.fillMaxWidth().testTag("outlined-road-name"))
                listOf("context" to "Navigation context only","green" to "Work both sides","yellow" to "Work inside only","red" to "Explicit do not work").forEach {(value,label)->FilterChip(status==value,{status=value},label={Text(label)})}
                if(status=="yellow")listOf("left","right").forEach {value->FilterChip(side==value,{side=value},label={Text("Work $value, from the first point toward the last")})}
            } else if(kind=="NON_ROAD") {
                listOf("PARKING_MARKING","BUILDING_EDGE","MAP_SYMBOL","LABEL_ARTIFACT").forEach {value->FilterChip(reason==value,{reason=value},label={Text(value.replace('_',' '))})}
                Text("Do not classify a mixed street/parking trace as entirely non-road. It needs source-span correction first.")
            } else Text("This omission is allowed only when the whole trace is outside the boundary. It does not assign red work instructions.")
            OutlinedTextField(evidence,{evidence=it},label={Text("What source evidence supports this decision?")},modifier=Modifier.fillMaxWidth().testTag("outlined-source-evidence"))
            Button(onClick={runCatching {
                val disposition=OutlinedSpanDisposition.valueOf(kind)
                val updated=if(disposition==OutlinedSpanDisposition.ROAD) {
                    require(name.isNotBlank() && !name.startsWith("Unresolved road")){"Verify the street name first"}
                    val role=when(status){"green"->"interior";"yellow"->"perimeter";"red"->"excluded";else->"context"}
                    val r=RoadGeometry(id,name.trim(),TopologyOverlapDecisionEngine.normalizeRoadName(name),status,role,if(status=="yellow")side else "",false,
                        if(p.road.junctionA)"junction" else "termination",if(p.road.junctionB)"junction" else "termination",4.0,p.road.points.map(review.transform::page))
                    roads.filterNot {it.segmentId==id}+r
                } else roads.filterNot {it.segmentId==id}
                val note=if(kind=="NON_ROAD")"$reason: ${evidence.trim()}" else evidence.trim()
                require(evidence.trim().length>=8){"Describe the source evidence for this trace"}
                val changed=review.reviewCandidate(id,disposition,note,updated)
                val failures=changed.coverageFailures(updated).filter {it.endsWith(":$id") && !it.startsWith("UNACCOUNTED_CANDIDATE")}
                require(failures.isEmpty()){failures.joinToString("; ")}
                onChanged(changed,updated);message="Source disposition recorded for $id."
            }.onFailure {message=it.message.orEmpty()}},modifier=Modifier.testTag("outlined-review-trace")){Text("Record this source decision")}
        }
        Text(message,modifier=Modifier.testTag("outlined-review-status"))
        val findings=review.unresolvedFindings(roads)
        Text("${findings.size} unresolved image findings",style=MaterialTheme.typography.titleMedium)
        if(findings.isNotEmpty()) {
            var findingIndex by remember {mutableStateOf(0)}
            val f=findings[findingIndex.coerceIn(0,findings.lastIndex)]
            var kind by remember(f.id){mutableStateOf("MAP_SYMBOL")}
            var evidence by remember(f.id){mutableStateOf("")}
            var ids by remember(f.id){mutableStateOf("")}
            Row {TextButton(onClick={findingIndex--},enabled=findingIndex>0){Text("Previous finding")};TextButton(onClick={findingIndex++},enabled=findingIndex<findings.lastIndex){Text("Next finding")}}
            Text("${f.id}: ${f.message}")
            if(source!=null && f.sourceBounds!=null)NativeSourcePreview(source,f.sourceBounds,review.extraction.boundary.polygon){}
            if(f.sourceBounds==null)Text("This global analysis failure needs a clearer or corrected source. It cannot be dismissed.")
            else {
                OutlinedNativeReview.RESOLUTIONS.forEach {value->FilterChip(kind==value,{kind=value},label={Text(value.replace('_',' '))})}
                if(kind=="MATCHED_ROAD")OutlinedTextField(ids,{ids=it},label={Text("Matching output road IDs, separated by commas")},modifier=Modifier.fillMaxWidth())
                OutlinedTextField(evidence,{evidence=it},label={Text("Specific evidence visible in this source region")},modifier=Modifier.fillMaxWidth())
                Button(onClick={runCatching {
                    val output=if(kind=="MATCHED_ROAD")ids.split(',').map {it.trim()}.filter {it.isNotEmpty()} else emptyList()
                    val updated=review.reviewFinding(f.id,kind,evidence,output,roads)
                    require(updated.unresolvedFindings(roads).none {it.id==f.id}){"This decision does not resolve the source finding"}
                    onChanged(updated,roads);message="Typed finding resolution recorded."
                }.onFailure {message=it.message.orEmpty()}}){Text("Record finding resolution")}
            }
        }
        val failures=review.coverageFailures(roads)
        if(failures.isNotEmpty())Text("Coverage review pending: "+failures.take(5).joinToString("; "))
        Text("Missing or obscured roads need source-backed geometry. Confirming visible traces alone does not establish complete coverage.")
    }
}

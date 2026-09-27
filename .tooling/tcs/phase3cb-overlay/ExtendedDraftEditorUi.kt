package com.koenterprises.territorycardstudio

import android.graphics.Paint
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.koenterprises.territorycardstudio.core.Point2D
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

@Composable
fun ExtendedDraftEditorScreen(modifier:Modifier,id:String,mode:WorkspaceMode,store:AndroidExtendedDraftStore,onBack:()->Unit) {
    val scope=rememberCoroutineScope();val activity=LocalContext.current.previewActivity();val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
    var state by remember(id,mode) {mutableStateOf<ExtendedEditorState?>(null)}
    var error by remember {mutableStateOf<String?>(null)};var loaded by remember {mutableStateOf(false)};var busy by remember {mutableStateOf(false)}
    var active by remember {mutableStateOf(true)};var refresh by remember {mutableIntStateOf(0)}
    var selectedKey by rememberSaveable(id,mode) {mutableStateOf("")};var fields by rememberSaveable(id,mode) {mutableStateOf("[]")}
    var reason by rememberSaveable(id,mode) {mutableStateOf("")};var evidence by rememberSaveable(id,mode) {mutableStateOf("")}
    var dirty by rememberSaveable(id,mode) {mutableStateOf(false)};var token by rememberSaveable(id,mode) {mutableStateOf<String?>(null)}
    var section by rememberSaveable(id,mode) {mutableStateOf("Items")};var query by rememberSaveable(id,mode) {mutableStateOf("")}
    var message by rememberSaveable(id,mode) {mutableStateOf<String?>(null)}
    var confirmation by remember {mutableStateOf<String?>(null)};var action by remember {mutableStateOf<(() -> Unit)?>(null)}
    val catalog=state?.catalog;val draft=state?.draft;val selected=catalog?.items?.firstOrNull {it.key==selectedKey}
    val conflict=loaded && error==null && token!=null && draft?.latest?.token!=token
    val writable=loaded && active && !busy && error==null && catalog!=null && draft!=null && !draft.stale && !conflict
    fun ask(text:String,run:()->Unit) {confirmation=text;action=run}
    fun clear() {selectedKey="";fields="[]";reason="";evidence="";dirty=false}
    fun reload() {clear();token=null;loaded=false;refresh++}
    fun leave() {if(!busy) {if(dirty)ask("Discard unsaved proposal fields and return? Saved revisions will remain.",onBack) else onBack()}}
    fun populate(item:ExtendedItem,d:ExtendedDraft?) {val p=d?.latest?.proposals?.firstOrNull {it.key==item.key};selectedKey=item.key;fields=JSONArray(ExtendedDraftForm.fields(p?.proposed ?: item.before).map {it.second}).toString();reason=p?.reason.orEmpty();evidence=p?.let {it.evidenceRole+":"+it.evidenceSha256}.orEmpty();dirty=false;token=d?.latest?.token}
    fun choose(item:ExtendedItem) {val run={populate(item,draft);section="Edit"};if(dirty)ask("Discard unsaved fields before selecting ${item.title}?",run) else run()}
    fun perform(success:String,run:()->Unit) {if(busy)return;busy=true;focus.clearFocus();keyboard?.hide();scope.launch {try {
        val result=withContext(Dispatchers.IO) {runCatching {run();store.load(id,mode)}}
        result.onSuccess {state=it;token=it.draft?.latest?.token;clear();section="Review";message=success}.onFailure {message=it.message ?: "Operation failed; input retained"};refresh++
    }finally{busy=false}}}
    DisposableEffect(activity) {
        val window=activity?.window;val previous=window?.attributes?.softInputMode
        previous?.let {window?.setSoftInputMode((it and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)}
        val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_PAUSE)active=false;if(event==Lifecycle.Event.ON_RESUME){active=true;refresh++}}
        activity?.lifecycle?.addObserver(observer)
        onDispose {activity?.lifecycle?.removeObserver(observer);previous?.let {window?.setSoftInputMode(it)}}
    }
    LaunchedEffect(id,mode,refresh,active) {if(active) {loaded=false;val result=withContext(Dispatchers.IO) {runCatching {store.load(id,mode)}};state=result.getOrNull();error=result.exceptionOrNull()?.let {it.message ?: "Draft read failed; existing data was not replaced"}
        if(!dirty && result.isSuccess) {token=state?.draft?.latest?.token;state?.catalog?.items?.firstOrNull {it.key==selectedKey}?.let {populate(it,state?.draft)}};loaded=true}}
    BackHandler {leave()}
    confirmation?.let {text->AlertDialog(onDismissRequest={confirmation=null;action=null},title={Text("Confirm proposal change")},text={Text(text)},confirmButton={TextButton(onClick={val run=action;confirmation=null;action=null;run?.invoke()},modifier=Modifier.testTag("extended-confirm")){Text("Continue")}},dismissButton={TextButton(onClick={confirmation=null;action=null},modifier=Modifier.testTag("extended-cancel")){Text("Cancel")}})}
    val values=runCatching {val a=JSONArray(fields);(0 until a.length()).map {a.getString(it)}}.getOrDefault(emptyList())
    val parsed=selected?.let {runCatching {ExtendedDraftForm.parse(it.kind,values)}}
    val problem=selected?.let {runCatching {val v=parsed!!.getOrThrow();ExtendedValues.validate(it.kind,v)}.exceptionOrNull()?.message}
    fun change(index:Int,value:String) {if(value.length<=4096){val a=JSONArray(fields);a.put(index,value);fields=a.toString();dirty=true}}
    Box(modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter) {
        LazyColumn(Modifier.fillMaxHeight().widthIn(max=960.dp).fillMaxWidth().imePadding().testTag("extended-editor"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {TextButton(onClick={leave()},enabled=!busy,modifier=Modifier.testTag("extended-back")){Text("← Back to workspace")};Text("Territory changes",style=MaterialTheme.typography.headlineLarge);Text("Territory $id • ${mode.label}");Text("Draft proposals • not verified or approved. Saving does not change a map or field PDF.")}
            item {Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(when{!loaded->"Loading current items…";error!=null->"Draft cannot be read";draft==null->"No saved change draft";draft.stale->"Stale draft • saving blocked";else->"Unvalidated proposals • revision ${draft.latest.number}"},style=MaterialTheme.typography.titleMedium,modifier=Modifier.testTag("extended-status"))
                if(dirty)Text("Unsaved fields",modifier=Modifier.testTag("extended-dirty"))
                if(conflict)Text("Saved revision changed. Your fields are retained; reload explicitly before saving.",color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("extended-conflict"))
                error?.let {Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("extended-error"))};state?.catalogError?.let {Text(it,color=MaterialTheme.colorScheme.error)}
                if(catalog?.inventoryAvailable==false)Text("Address and phone items require a current verified inventory. None is available.",modifier=Modifier.testTag("extended-no-inventory"))
                if(draft?.stale==true)Text("Source, assignment or inventory changed. Review history, then discard this draft to start from the current base.")
            }}}
            item {Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={if(dirty)ask("Discard unsaved fields and reload the latest revision?"){reload()} else reload()},enabled=!busy,modifier=Modifier.weight(1f).testTag("extended-reload")){Text("Reload")}
                if(draft!=null)OutlinedButton(onClick={val t=token ?: return@OutlinedButton;ask("Discard the entire saved draft, its history, and all unsaved fields?"){perform("Draft discarded"){store.discard(id,mode,t)}}},enabled=loaded && active && !busy && !conflict && error==null,modifier=Modifier.weight(1f).testTag("extended-discard")){Text("Discard draft")}
            }}
            if(busy)item {LinearProgressIndicator(Modifier.fillMaxWidth().testTag("extended-busy"))}
            message?.let {item {Text(it,modifier=Modifier.testTag("extended-message"))}}
            if(loaded && error==null && draft==null && catalog!=null)item {Button(onClick={val b=catalog.binding;perform("Change draft started"){store.create(id,mode,b)}},enabled=active && !busy && !conflict && catalog.items.isNotEmpty(),modifier=Modifier.fillMaxWidth().testTag("extended-create")){Text("Start change draft")}}
            if(draft!=null) {
                item {Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){(listOf("Items","Review","History")+if(selected!=null)listOf("Edit") else emptyList()).forEach {tab->FilterChip(selected=section==tab,onClick={section=tab;focus.clearFocus();keyboard?.hide()},enabled=!busy,label={Text(tab)},modifier=Modifier.testTag("extended-tab-$tab"))}}}
                if(section=="Items") {
                    item {OutlinedTextField(query,{query=it},label={Text("Find a road, building or address")},modifier=Modifier.fillMaxWidth().testTag("extended-search"))}
                    val found=catalog?.items.orEmpty().filter {query.isBlank() || it.title.contains(query,true) || it.kind.title.contains(query,true) || it.id.contains(query,true)}
                    if(found.isEmpty())item {Text("No matching known items")}
                    items(found,key={it.key}) {item->OutlinedButton(onClick={choose(item)},enabled=writable,modifier=Modifier.fillMaxWidth().testTag("extended-item-${item.key}")){Column(Modifier.fillMaxWidth()){Text(item.title.ifBlank {item.id});Text(item.kind.title,style=MaterialTheme.typography.bodySmall)}}}
                }
                if(section=="Edit" && selected!=null) {
                    item {Text(selected.kind.title,style=MaterialTheme.typography.headlineSmall);Text(selected.title);Text("Current: ${ExtendedValues.summary(selected.before)}",modifier=Modifier.testTag("extended-before"))}
                    if(selected.kind in setOf(ExtendedKind.ROAD_PATH,ExtendedKind.ROAD_WORK,ExtendedKind.BUILDING_PATH,ExtendedKind.BUILDING_MEMBERS))item {
                        ExtendedMapPreview(selected,catalog!!.items,if(problem==null)parsed?.getOrNull() else null,draft.latest.proposals)
                    }
                    if(selected.kind==ExtendedKind.ROAD_WORK)item {
                        Text("Apply to the whole existing segment. Left/right follows the vertex order.")
                        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            listOf("Inside left" to WorkValue("yellow","perimeter","left",false),"Inside right" to WorkValue("yellow","perimeter","right",false),"Both sides" to WorkValue("green","interior","",false),"Do not work" to WorkValue("red","excluded","",false),"Access only" to WorkValue("context","context","",true)).forEach {(label,v)->FilterChip(selected=parsed?.getOrNull()==v,onClick={fields=JSONArray(ExtendedDraftForm.fields(v).map {it.second}).toString();dirty=true},enabled=writable,label={Text(label)},modifier=Modifier.testTag("extended-work-${v.status}-${v.insideSide}"))}
                        }
                    } else if(selected.kind==ExtendedKind.PHONE_NUMBER)item {
                        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {ProposedPhoneState.entries.forEach {s->FilterChip(selected=values.firstOrNull()==s.name,onClick={change(0,s.name);if(s!=ProposedPhoneState.NUMBER)change(1,"")},enabled=writable,label={Text(when(s){ProposedPhoneState.NUMBER->"Number proposed";ProposedPhoneState.UNAVAILABLE->"Unavailable";ProposedPhoneState.UNKNOWN->"Unknown"})},modifier=Modifier.testTag("extended-phone-${s.name}"))}}
                        Text("A proposed number stays unverified until independently checked against its authorized source.")
                    }
                    val names=ExtendedDraftForm.names(selected.kind,values.size)
                    items(values.indices.toList(),key={"field-$it"}) {index->
                        if(selected.kind==ExtendedKind.ROAD_WORK || selected.kind==ExtendedKind.PHONE_NUMBER && index==0)Unit
                        else if(selected.kind==ExtendedKind.BUILDING_MEMBERS && index==0)Row(verticalAlignment=Alignment.CenterVertically) {Switch(values[0]=="true",{change(0,it.toString())},enabled=writable,modifier=Modifier.testTag("extended-assigned"));Text("Assigned building")}
                        else OutlinedTextField(values[index],{change(index,it)},label={Text(names[index])},enabled=writable && (selected.kind!=ExtendedKind.PHONE_NUMBER || values[0]=="NUMBER"),modifier=Modifier.fillMaxWidth().testTag("extended-field-$index"))
                    }
                    if(selected.kind==ExtendedKind.ROAD_PATH || selected.kind==ExtendedKind.BUILDING_PATH)item {Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick={fields=JSONArray(values+listOf("350.0","165.0")).toString();dirty=true},enabled=writable && values.size<1024,modifier=Modifier.testTag("extended-add-vertex")){Text("Add vertex")}
                        OutlinedButton(onClick={fields=JSONArray(values.dropLast(2)).toString();dirty=true},enabled=writable && values.size>if(selected.kind==ExtendedKind.ROAD_PATH)4 else 6){Text("Remove last vertex")}
                    };Text("Map coordinates are in points. Edit vertices without creating a new work-color segment.")}
                    if(selected.kind==ExtendedKind.BUILDING_MEMBERS)item {Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick={fields=JSONArray(values+listOf("","350.0","165.0","","","0.0","10.0")).toString();dirty=true},enabled=writable && values.size<898,modifier=Modifier.testTag("extended-add-member")){Text("Add member")}
                        OutlinedButton(onClick={fields=JSONArray(values.dropLast(7)).toString();dirty=true},enabled=writable && values.size>2){Text("Remove last member")}
                    };Text("Member IDs are saved together with their interior labels. Single-member display labels must match.")}
                    problem?.let {item {Text("Check proposal: $it",color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("extended-input-error"))}}
                    item {OutlinedTextField(reason,{if(it.length<=1024){reason=it;dirty=true}},label={Text("Reason for this change")},modifier=Modifier.fillMaxWidth().testTag("extended-reason"),enabled=writable)}
                    item {Text("Supporting evidence reference",style=MaterialTheme.typography.titleMedium);Text("This reference does not authorize or verify the change.")}
                    if(selected.evidence.isEmpty())item {Text("No eligible evidence for this item. Saving is blocked.",modifier=Modifier.testTag("extended-no-evidence"))}
                    items(selected.evidence,key={it.role+it.sha256}) {e->FilterChip(selected=evidence==e.role+":"+e.sha256,onClick={evidence=e.role+":"+e.sha256;dirty=true},enabled=writable,label={Column {Text(e.title);Text(e.sha256.take(16)+"…",style=MaterialTheme.typography.bodySmall)}},modifier=Modifier.fillMaxWidth().testTag("extended-evidence-${e.role}-${e.sha256}"))}
                    item {Button(onClick={val t=token ?: return@Button;val value=parsed?.getOrNull() ?: return@Button;val e=selected.evidence.firstOrNull {evidence==it.role+":"+it.sha256} ?: return@Button
                        val p=ExtendedProposal(selected.kind,selected.id,selected.before,value,reason,e.role,e.sha256);val ps=draft.latest.proposals.filterNot {it.key==p.key}+p
                        perform("Proposal saved • still unvalidated"){store.save(id,mode,t,ps)}
                    },enabled=writable && problem==null && parsed?.getOrNull()!=selected.before && reason.isNotBlank() && selected.evidence.any {evidence==it.role+":"+it.sha256},modifier=Modifier.fillMaxWidth().testTag("extended-save")){Text("Save proposal")}}
                }
                if(section=="Review") {
                    item {Text("Saved change review",style=MaterialTheme.typography.headlineSmall);Text("${draft.latest.proposals.size} proposals • all require independent validation",modifier=Modifier.testTag("extended-review-count"))}
                    items(draft.latest.proposals,key={it.key}) {p->Card(Modifier.fillMaxWidth().testTag("extended-review-${p.key}")){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(p.kind.title+" • "+p.itemId,style=MaterialTheme.typography.titleMedium);Text("Before: ${ExtendedValues.summary(p.before)}");Text("Proposed: ${ExtendedValues.summary(p.proposed)}");Text(p.reason);Text("Reference: ${p.evidenceRole} • ${p.evidenceSha256}",style=MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick={val t=token ?: return@OutlinedButton;val ps=draft.latest.proposals.filterNot {it.key==p.key};ask("Remove this saved proposal and discard any unsaved fields?"){perform("Proposal removed"){store.save(id,mode,t,ps)}}},enabled=writable,modifier=Modifier.testTag("extended-remove-${p.key}")){Text("Remove proposal")}
                    }}}
                }
                if(section=="History") {
                    item {Text("Revision history",style=MaterialTheme.typography.headlineSmall)}
                    items(draft.revisions.asReversed(),key={it.number}) {r->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("Revision ${r.number} • ${r.action}",style=MaterialTheme.typography.titleMedium);Text(r.atUtc);Text("${r.proposals.size} saved proposals")
                        if(r.number<draft.latest.number)OutlinedButton(onClick={val t=token ?: return@OutlinedButton;ask("Restore revision ${r.number} as a new revision and discard any unsaved fields?"){perform("Revision restored"){store.restore(id,mode,t,r.number)}}},enabled=writable,modifier=Modifier.testTag("extended-restore-${r.number}")){Text("Restore revision")}
                    }}}
                }
            }
        }
    }
}

@Composable
private fun ExtendedMapPreview(item:ExtendedItem,items:List<ExtendedItem>,proposed:ExtendedValue?,saved:List<ExtendedProposal>) {
    val pathKind=if(item.kind in setOf(ExtendedKind.ROAD_PATH,ExtendedKind.ROAD_WORK))ExtendedKind.ROAD_PATH else ExtendedKind.BUILDING_PATH
    fun bounded(p:Point2D)=p.x in 176.0..747.0 && p.y in 20.0..363.0
    val base=(items.firstOrNull {it.kind==pathKind && it.id==item.id}?.before as? PathValue)?.points.orEmpty().filter(::bounded)
    val currentPath=if(proposed is PathValue)proposed.points else (saved.firstOrNull {it.kind==pathKind && it.itemId==item.id}?.proposed as? PathValue)?.points ?: base
    val shown=currentPath.takeIf {it.all(::bounded)} ?: base
    val work=proposed as? WorkValue ?: (saved.firstOrNull {it.kind==ExtendedKind.ROAD_WORK && it.itemId==item.id}?.proposed as? WorkValue) ?: (items.firstOrNull {it.kind==ExtendedKind.ROAD_WORK && it.id==item.id}?.before as? WorkValue)
    val building=proposed as? BuildingValue ?: (saved.firstOrNull {it.kind==ExtendedKind.BUILDING_MEMBERS && it.itemId==item.id}?.proposed as? BuildingValue) ?: (items.firstOrNull {it.kind==ExtendedKind.BUILDING_MEMBERS && it.id==item.id}?.before as? BuildingValue)
    val color=when {work?.status=="yellow"->Color(0xFFBC8A00);work?.status=="red" || building?.assigned==false->Color(0xFFD94A53);work?.status=="context"->Color(0xFF6A7587);else->Color(0xFF1C9763)}
    val ink=MaterialTheme.colorScheme.onSurface;val surface=MaterialTheme.colorScheme.surfaceVariant;val gray=MaterialTheme.colorScheme.outline
    Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Proposed map detail • not verified",style=MaterialTheme.typography.titleSmall);Text("Current: grey outline  •  Proposed: working color",style=MaterialTheme.typography.bodySmall)
        Canvas(Modifier.fillMaxWidth().height(220.dp).testTag("extended-map-preview")) {
            drawRect(surface)
            val all=(base+shown+building?.labels.orEmpty().map {it.center}).filter(::bounded)
            if(all.isEmpty())return@Canvas
            val minX=all.minOf{it.x}-15;val minY=all.minOf{it.y}-15;val maxX=all.maxOf{it.x}+15;val maxY=all.maxOf{it.y}+15
            val scale=minOf(size.width/(maxX-minX).toFloat(),size.height/(maxY-minY).toFloat())
            val ox=(size.width-(maxX-minX).toFloat()*scale)/2;val oy=(size.height-(maxY-minY).toFloat()*scale)/2
            fun pos(p:Point2D)=Offset(ox+(p.x-minX).toFloat()*scale,oy+(p.y-minY).toFloat()*scale)
            fun path(points:List<Point2D>):Path=Path().apply {points.forEachIndexed {i,p->val q=pos(p);if(i==0)moveTo(q.x,q.y) else lineTo(q.x,q.y)};if(pathKind==ExtendedKind.BUILDING_PATH)close()}
            drawPath(path(base),gray,style=Stroke(3.dp.toPx()));drawPath(path(shown),color,style=Stroke(1.5.dp.toPx()));shown.forEachIndexed {i,p->val q=pos(p);drawCircle(color,4.dp.toPx(),q);drawContext.canvas.nativeCanvas.drawText((i+1).toString(),q.x+6.dp.toPx(),q.y-6.dp.toPx(),Paint(Paint.ANTI_ALIAS_FLAG).apply {this.color=ink.toArgb();textSize=12.sp.toPx()})}
            if(work?.status=="yellow" && shown.size>=2) {
                val a=pos(shown[0]);val b=pos(shown[1]);val dx=b.x-a.x;val dy=b.y-a.y;val len=kotlin.math.sqrt(dx*dx+dy*dy)
                if(len>1f) {val middle=Offset((a.x+b.x)/2,(a.y+b.y)/2);val sign=if(work.insideSide=="left")1f else -1f
                    val n=Offset(sign*dy/len,-sign*dx/len);val end=middle+n*28.dp.toPx();drawLine(color,middle,end,2.dp.toPx())
                    drawLine(color,end,end-n*8.dp.toPx()+Offset(dx/len,dy/len)*5.dp.toPx(),2.dp.toPx());drawLine(color,end,end-n*8.dp.toPx()-Offset(dx/len,dy/len)*5.dp.toPx(),2.dp.toPx())
                }
            }
            building?.labels?.forEach {l->if(bounded(l.center)){val p=pos(l.center);drawContext.canvas.nativeCanvas.drawText(l.text,p.x,p.y,Paint(Paint.ANTI_ALIAS_FLAG).apply {this.color=ink.toArgb();textSize=16.sp.toPx();textAlign=Paint.Align.CENTER})}}
        }
    }}
}

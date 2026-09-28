package com.koenterprises.territorycardstudio

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.koenterprises.territorycardstudio.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Composable private fun NativeField(label:String,value:String,tag:String,onChange:(String)->Unit) {
    OutlinedTextField(value,onChange,label={Text(label)},modifier=Modifier.fillMaxWidth().testTag(tag))
}
@Composable private fun NativeCheck(label:String,value:Boolean,tag:String,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth()) {Checkbox(value,onChange,modifier=Modifier.testTag(tag));Text(label,Modifier.padding(top=12.dp))}
}
@Composable private fun NativeChoice(label:String,value:String,choices:List<String>,tag:String,onChange:(String)->Unit) {
    Text(label,style=MaterialTheme.typography.titleSmall)
    Column {choices.forEach {choice->FilterChip(selected=value==choice,onClick={onChange(choice)},label={Text(choice.replace('_',' '))},modifier=Modifier.testTag("$tag-$choice"))}}
}

@Composable private fun NativeSourcePreview(file:File,onReadable:(Boolean)->Unit) {
    var page by remember(file.path) {mutableStateOf(0)}
    var pages by remember(file.path) {mutableStateOf(1)}
    var bitmap by remember(file.path,page) {mutableStateOf<Bitmap?>(null)}
    var error by remember(file.path,page) {mutableStateOf<String?>(null)}
    LaunchedEffect(file.path,page) {
        onReadable(false)
        val result=withContext(Dispatchers.IO) {runCatching {
            if(file.inputStream().use {input->ByteArray(5).also {input.read(it)}}.toString(Charsets.US_ASCII)=="%PDF-") {
                PdfRenderer(ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)).use {pdf->
                    val total=pdf.pageCount;require(total in 1..200)
                    pdf.openPage(page.coerceIn(0,total-1)).use {p->
                        val scale=minOf(900.0/p.width,1800.0/p.height)
                        val b=Bitmap.createBitmap(maxOf(1,(p.width*scale).toInt()),maxOf(1,(p.height*scale).toInt()),Bitmap.Config.ARGB_8888)
                        b.eraseColor(android.graphics.Color.WHITE);p.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);b to total
                    }
                }
            } else {
                val options=BitmapFactory.Options().apply {inJustDecodeBounds=true};BitmapFactory.decodeFile(file.path,options)
                require(options.outWidth in 1..30000 && options.outHeight in 1..30000)
                options.inJustDecodeBounds=false;options.inSampleSize=1
                while(maxOf(options.outWidth,options.outHeight)/options.inSampleSize>1800)options.inSampleSize*=2
                requireNotNull(BitmapFactory.decodeFile(file.path,options)) to 1
            }
        }}
        result.onSuccess {(b,count)->bitmap=b;pages=count;onReadable(true)}.onFailure {error=it.message ?: "Source could not be displayed"}
    }
    Text("Exact imported source",style=MaterialTheme.typography.titleMedium)
    bitmap?.let {Image(it.asImageBitmap(),"Imported territory source page ${page+1}",Modifier.fillMaxWidth().heightIn(max=480.dp).testTag("native-source-preview"))}
    error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    if(pages>1)Row {TextButton(onClick={page--},enabled=page>0){Text("Previous source page")};Text("${page+1} / $pages");TextButton(onClick={page++},enabled=page+1<pages){Text("Next source page")}}
}

@Composable private fun NativeContactSourcePreview(store:AndroidNativeDraftStore,document:NativeInventoryDocument) {
    var file by remember(document.sha256) {mutableStateOf<File?>(null)}
    var text by remember(document.sha256) {mutableStateOf<String?>(null)}
    var error by remember(document.sha256) {mutableStateOf<String?>(null)}
    LaunchedEffect(document.sha256) {
        runCatching {withContext(Dispatchers.IO) {
            val f=store.contactSource(document)
            if(f.inputStream().use {input->ByteArray(5).also {input.read(it)}}.toString(Charsets.US_ASCII)=="%PDF-") f to null
            else null to Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(f.readBytes())).toString()
        }}.onSuccess {(f,t)->file=f;text=t}.onFailure {error=it.message}
    }
    file?.let {NativeSourcePreview(it){}}
    text?.let {value->
        var page by remember(document.sha256){mutableStateOf(0)}
        val pages=maxOf(1,(value.length+3999)/4000)
        Text("Exact contact source • page ${page+1} / $pages")
        Text(value.substring(page*4000,minOf(value.length,(page+1)*4000)),modifier=Modifier.testTag("native-contact-source-text"))
        Row {TextButton(onClick={page--},enabled=page>0){Text("Previous source text")};TextButton(onClick={page++},enabled=page+1<pages){Text("Next source text")}}
    }
    error?.let {Text("Cannot read source: $it",color=MaterialTheme.colorScheme.error)}
}

@Composable private fun NativeTrace(roads:List<RoadGeometry>,buildings:List<BuildingGeometry>,points:List<Point2D>,onPoint:(Point2D)->Unit) {
    Text("Tap the map to trace points in order. Use source junctions and real road ends.")
    val border=MaterialTheme.colorScheme.outline
    Canvas(Modifier.fillMaxWidth().height(240.dp).testTag("native-trace").pointerInput(points) {
        detectTapGestures {p->onPoint(Point2D(176.0+p.x/size.width*571.0,20.0+p.y/size.height*343.0))}
    }) {
        fun point(p:Point2D)=Offset(((p.x-176.0)/571.0*size.width).toFloat(),((p.y-20.0)/343.0*size.height).toFloat())
        drawRect(Color.White);drawRect(border,style=androidx.compose.ui.graphics.drawscope.Stroke(2f))
        buildings.forEach {b->(b.polygon+b.polygon.take(1)).zipWithNext().forEach {(a,c)->drawLine(Color.Gray,point(a),point(c),3f)}}
        roads.forEach {r->r.points.zipWithNext().forEach {(a,b)->drawLine(when(r.status){"yellow"->Color(0xFFD0AA00);"green"->Color(0xFF07883B);"red"->Color(0xFFD32222);else->Color.Gray},point(a),point(b),4f)}}
        points.zipWithNext().forEach {(a,b)->drawLine(Color.Blue,point(a),point(b),3f)}
        points.forEach {drawCircle(Color.Blue,5f,point(it))}
    }
}

/** Native initial authoring. All source confirmations start false and every save invalidates registration facts. */
@Composable
fun NativeAuthoringScreen(modifier:Modifier,assignment:KnowledgeBaseAssignment,mode:WorkspaceMode,
    kb:TerritoryKnowledgeBase,store:AndroidNativeDraftStore,service:AndroidNativeAuthoringService,sources:SourceMapIntakeStore,
    onRegisteredImport:()->Unit,onBack:()->Unit) {
    val id=assignment.displayId
    var draft by remember(id,mode) {mutableStateOf<NativeAuthoringDraft?>(null)}
    var loaded by remember(id,mode) {mutableStateOf(false)}
    var message by remember(id,mode) {mutableStateOf("")}
    var busy by remember {mutableStateOf(false)}
    var section by rememberSaveable(id,mode.name) {mutableStateOf("Map")}
    var sourceReadable by remember {mutableStateOf(false)}
    var locality by rememberSaveable(id,mode.name) {mutableStateOf("")}
    var updated by rememberSaveable(id,mode.name) {mutableStateOf("")}
    var directions by rememberSaveable(id,mode.name) {mutableStateOf("")}
    var author by rememberSaveable(id,mode.name) {mutableStateOf("")}
    var county by rememberSaveable(id,mode.name) {mutableStateOf("")}
    var state by rememberSaveable(id,mode.name) {mutableStateOf("")}
    var country by rememberSaveable(id,mode.name) {mutableStateOf("")}
    var roads by remember(id,mode) {mutableStateOf<List<RoadGeometry>>(emptyList())}
    var buildings by remember(id,mode) {mutableStateOf<List<BuildingGeometry>>(emptyList())}
    var roadFacts by remember(id,mode) {mutableStateOf<List<SourceSegmentObservation>>(emptyList())}
    var buildingFacts by remember(id,mode) {mutableStateOf<List<SourceBuildingObservation>>(emptyList())}
    var contacts by remember(id,mode) {mutableStateOf<List<NativeContactDraft>>(emptyList())}
    var coverage by remember(id,mode) {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    val source=runCatching {sources.verifiedRecord(id)}.getOrNull()
    val sourceFile=source?.let {runCatching {sources.verifiedFile(id)}.getOrNull()}
    LaunchedEffect(source?.sha256) {
        sourceReadable=false
        sourceReadable=withContext(Dispatchers.IO) {runCatching {
            val f=requireNotNull(sourceFile)
            if(f.inputStream().use {input->ByteArray(5).also {input.read(it)}}.toString(Charsets.US_ASCII)=="%PDF-") {
                PdfRenderer(ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY)).use {require(it.pageCount in 1..200)}
            } else {
                val options=BitmapFactory.Options().apply {inJustDecodeBounds=true};BitmapFactory.decodeFile(f.path,options)
                require(options.outWidth in 1..30000 && options.outHeight in 1..30000)
            }
            true
        }.getOrDefault(false)}
    }
    LaunchedEffect(id,mode) {
        runCatching {withContext(Dispatchers.IO) {store.read(id,mode)}}.onSuccess {d->
            draft=d
            if(d!=null) {locality=d.assignment.locality;updated=d.assignment.updated;directions=d.assignment.directionsLines.joinToString("\n");author=d.reconciliation.author
                county=d.jurisdiction.county.orEmpty();state=d.jurisdiction.state;country=d.jurisdiction.country;roads=d.assignment.roads;buildings=d.assignment.buildings
                roadFacts=d.reconciliation.segments;buildingFacts=d.reconciliation.buildings;contacts=d.contacts;coverage=d.reconciliation.sourceCoverageComplete}
        }.onFailure {message=it.message.orEmpty()};loaded=true
    }
    LaunchedEffect(loaded,source?.sha256) {
        if(loaded && draft!=null && source!=null && draft!!.reconciliation.importedSourceSha256!=source.sha256) {
            roadFacts=roadFacts.map {it.copy(confirmed=false)};buildingFacts=buildingFacts.map {it.copy(confirmed=false)}
            contacts=contacts.map {it.copy(boundaryConfirmed=false)};coverage=false
            message="Source changed. Save the reset draft, then review every source item and address boundary again."
        }
    }
    fun proposal():NativeAuthoringDraft {
        val src=requireNotNull(source) {"Import this territory's current source map first"}
        val a=CurrentAuthoritativeAssignmentState(id,assignment.identity,assignment.canonicalFilename,kb.revision,"current_authoritative_assignment","0".repeat(64),src.sourceFilename,
            locality.trim(),updated.trim(),directions.lines().filter {it.isNotBlank()},"full_map",assignment.housingType,RenderCoordinateSpace.LOCKED_R48_PAGE_POINTS_TOP_ORIGIN,roads,buildings)
        val r=NativeSourceReconciliation(id,mode.name,kb.revision,src.sha256,assignment.referenceSha256,"current_assignment_map",author.trim(),
            draft?.reconciliation?.reviewedAtUtc ?: Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),NativeSourceReconciliationContract.assignmentContentSha256(a),null,
            coverage,false,false,false,roadFacts,buildingFacts,draft?.reconciliation?.registrationId ?: UUID.randomUUID().toString(),null)
        return NativeAuthoringDraft(a,r,VerificationJurisdiction(county.trim().ifBlank {null},state.trim(),country.trim()),contacts)
    }
    fun save() {
        runCatching {store.save(proposal(),draft?.revisionSha256)}.onSuccess {draft=it;message="Draft saved. Review source facts before registering."}.onFailure {message=it.message.orEmpty()}
    }
    var itemId by rememberSaveable {mutableStateOf("")}
    var name by rememberSaveable {mutableStateOf("")}
    var status by rememberSaveable {mutableStateOf("")}
    var role by rememberSaveable {mutableStateOf("")}
    var side by rememberSaveable {mutableStateOf("")}
    var endA by rememberSaveable {mutableStateOf("")}
    var endB by rememberSaveable {mutableStateOf("")}
    var note by rememberSaveable {mutableStateOf("")}
    var confirmed by rememberSaveable {mutableStateOf(false)}
    var accessOnly by rememberSaveable {mutableStateOf(false)}
    var points by remember {mutableStateOf<List<Point2D>>(emptyList())}
    var pointText by rememberSaveable {mutableStateOf("")}
    var members by rememberSaveable {mutableStateOf("")}
    var assigned by rememberSaveable {mutableStateOf(false)}
    var address by rememberSaveable {mutableStateOf("")}
    var unit by rememberSaveable {mutableStateOf("")}
    var city by rememberSaveable {mutableStateOf("")}
    var postal by rememberSaveable {mutableStateOf("")}
    var addressState by rememberSaveable {mutableStateOf("")}
    var buildingId by rememberSaveable {mutableStateOf("")}
    var phone by rememberSaveable {mutableStateOf("")}
    var phoneState by rememberSaveable {mutableStateOf("")}
    var addressCheck by rememberSaveable {mutableStateOf(false)}
    var boundaryCheck by rememberSaveable {mutableStateOf(false)}
    var addressUse by rememberSaveable {mutableStateOf(false)}
    var phoneUse by rememberSaveable {mutableStateOf(false)}
    var bindingCheck by rememberSaveable {mutableStateOf(false)}
    var document by remember {mutableStateOf<NativeInventoryDocument?>(null)}
    val context=LocalContext.current
    var archivePickerOwned by rememberSaveable {mutableStateOf(false)}
    val archivePicker=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {uri->
        val owned=archivePickerOwned;archivePickerOwned=false
        if(owned && uri!=null)scope.launch {busy=true
            runCatching {withContext(Dispatchers.IO){store.exportHistory(AndroidCreatedExportDestination(context.contentResolver,uri))}}
                .onSuccess {message="Evidence archive saved and read back successfully. SHA-256 $it"}.onFailure {message=it.message.orEmpty()};busy=false
        }
    }
    val contactPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->if(uri!=null)scope.launch {
        busy=true;runCatching {withContext(Dispatchers.IO){store.importContactSource(uri)}}.onSuccess {document=it;addressCheck=false;boundaryCheck=false;addressUse=false;phoneUse=false;bindingCheck=false;message="Contact source imported; authorization and record checks are still required."}.onFailure {message=it.message.orEmpty()};busy=false
    }}
    LazyColumn(modifier.fillMaxSize().testTag("native-authoring-screen"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {TextButton(onClick=onBack){Text("Back to workspace")};Text("Create territory $id",style=MaterialTheme.typography.headlineMedium);Text(mode.label+" • "+assignment.canonicalFilename)}
        item {Text(message,modifier=Modifier.testTag("native-status"));if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())}
        item {Row {(listOf("Map","Roads")+(if(assignment.housingType in setOf("apartment","condo","townhome","mobile_home","manufactured_home"))listOf("Buildings") else emptyList())+listOf(if(mode==WorkspaceMode.REGULAR)"Review" else "Addresses")).forEach {label->TextButton(onClick={section=label},modifier=Modifier.testTag("native-tab-$label")){Text(label)}}}
            if(mode!=WorkspaceMode.REGULAR)TextButton(onClick={section="Review"},modifier=Modifier.testTag("native-tab-Review")){Text("Review")}}
        if(!loaded) item {Text("Loading draft…")}
        if(sourceFile==null) item {Text("Import a current source map from the workspace before authoring.")}
        else item {NativeSourcePreview(sourceFile){}}
        if(section=="Map") {
            item {Text("Describe the new card",style=MaterialTheme.typography.titleLarge);Text("Enter current source facts. Legacy geometry is not copied into this draft.")}
            item {NativeField("Locality",locality,"native-locality"){locality=it}}
            item {NativeField("Updated date",updated,"native-updated"){updated=it}}
            item {NativeField("Directions (one to three lines)",directions,"native-directions"){directions=it}}
            item {NativeField("Your name for the reconciliation record",author,"native-author"){author=it}}
            item {NativeField("County",county,"native-county"){county=it};NativeField("State or province",state,"native-state"){state=it};NativeField("Country",country,"native-country"){country=it}}
            item {TextButton(onClick=onRegisteredImport){Text("Import an independently registered project")}}
        }
        if(section=="Roads" || section=="Buildings") {
            item {NativeTrace(roads,buildings,points){confirmed=false;points=points+it;pointText=points.joinToString("; "){p->"${p.x},${p.y}"}}}
            item {OutlinedButton(onClick={points=emptyList();pointText="";confirmed=false}){Text("Clear trace")};NativeField("Trace points (x,y; x,y)",pointText,"native-points"){pointText=it;confirmed=false}
                TextButton(onClick={runCatching {pointText.split(';').map {s->val xy=s.trim().split(',');require(xy.size==2);Point2D(xy[0].trim().toDouble(),xy[1].trim().toDouble())}}.onSuccess{points=it}.onFailure{message="Use a pair of coordinates for each point"}}){Text("Apply points")}}
            item {NativeField("Source item identifier",itemId,"native-item-id"){itemId=it;confirmed=false}}
            if(section=="Roads") {
                item {NativeField("Street name exactly as sourced",name,"native-road-name"){name=it;confirmed=false}}
                item {NativeChoice("Work instruction",status,listOf("yellow","green","red","context"),"native-road-status"){status=it;confirmed=false};Text("Yellow: inside only. Green: both sides. Red: do not work.")}
                item {NativeChoice("Segment role",role,listOf("perimeter","interior","excluded","context"),"native-road-role"){role=it;confirmed=false}}
                if(role=="perimeter")item {NativeChoice("Worked side from the first point toward the last",side,listOf("left","right"),"native-road-side"){side=it;confirmed=false}}
                item {NativeChoice("First endpoint",endA,listOf("junction","termination"),"native-end-a"){endA=it;confirmed=false};NativeChoice("Last endpoint",endB,listOf("junction","termination"),"native-end-b"){endB=it;confirmed=false}}
                item {NativeCheck("This segment is access only",accessOnly,"native-access-only"){accessOnly=it;confirmed=false}}
            } else {
                item {NativeField("Assigned building/member identifiers, separated by commas",members,"native-building-members"){members=it;confirmed=false};NativeCheck("This footprint is assigned",assigned,"native-building-assigned"){assigned=it;confirmed=false}}
            }
            item {NativeField("Where these facts appear in the source",note,"native-evidence-note"){note=it;confirmed=false};NativeCheck("I checked this item, its assignment and geometry against the exact source",confirmed,"native-item-confirmed"){confirmed=it}}
            item {Button(enabled=sourceReadable && !busy,onClick={runCatching {
                require(confirmed && note.isNotBlank() && itemId.isNotBlank()) {"Confirm this source item and describe its evidence"}
                if(section=="Roads") {
                    require(points.size>=2 && name.isNotBlank() && status.isNotBlank() && role.isNotBlank() && endA.isNotBlank() && endB.isNotBlank()) {"Complete the road trace and source facts"}
                    val r=RoadGeometry(itemId.trim(),name.trim(),TopologyOverlapDecisionEngine.normalizeRoadName(name),status,role,if(role=="perimeter")side else "",accessOnly,endA,endB,4.0,points)
                    roads=roads.filter {it.segmentId!=r.segmentId}+r
                    roadFacts=roadFacts.filter {it.segmentId!=r.segmentId}+SourceSegmentObservation(r.segmentId,r.name,r.status,r.role,r.insideSide,r.accessOnly,r.endpointAKind,r.endpointBKind,note.trim(),confirmed)
                } else {
                    require(points.size>=3);val ms=members.split(',').map {it.trim()}.filter {it.isNotBlank()};require(!assigned || ms.isNotEmpty())
                    val center=Point2D(points.map {it.x}.average(),points.map {it.y}.average())
                    val labels=service.buildingLabels(ms,points)
                    val b=BuildingGeometry(itemId.trim(),ms.joinToString("/"),assignment.housingType,assigned,"",ms,labels,points)
                    buildings=buildings.filter {it.buildingId!=b.buildingId}+b
                    buildingFacts=buildingFacts.filter {it.buildingId!=b.buildingId}+SourceBuildingObservation(b.buildingId,ms,assigned,note.trim(),confirmed)
                }
                coverage=false;save();confirmed=false
            }.onFailure {message=it.message.orEmpty()}},modifier=Modifier.testTag("native-save-item")){Text("Save source item and trace")}}
            item {Text("Draft: ${roads.size} road segments • ${buildings.size} footprints")}
            items(roads.size) {i->val r=roads[i];Text("${r.segmentId}: ${r.name} • ${r.status} • ${r.role}")
                TextButton(onClick={itemId=r.segmentId;name=r.name;status=r.status;role=r.role;side=r.insideSide;endA=r.endpointAKind;endB=r.endpointBKind;accessOnly=r.accessOnly;points=r.points;pointText=r.points.joinToString("; "){"${it.x},${it.y}"};note=roadFacts.firstOrNull{it.segmentId==r.segmentId}?.evidenceNote.orEmpty();confirmed=false;section="Roads"}){Text("Edit ${r.segmentId}")}
                TextButton(enabled=roads.size>1,onClick={roads=roads.filter {it.segmentId!=r.segmentId};roadFacts=roadFacts.filter {it.segmentId!=r.segmentId};coverage=false;save()}){Text("Remove source item ${r.segmentId}")}}
            items(buildings.size) {i->val b=buildings[i];Text("${b.buildingId}: ${b.sourceMembers.joinToString()}")
                TextButton(onClick={itemId=b.buildingId;members=b.sourceMembers.joinToString(",");assigned=b.assigned;points=b.polygon;pointText=b.polygon.joinToString("; "){"${it.x},${it.y}"};note=buildingFacts.firstOrNull{it.buildingId==b.buildingId}?.evidenceNote.orEmpty();confirmed=false;section="Buildings"}){Text("Edit ${b.buildingId}")}
                TextButton(onClick={buildings=buildings.filter {it.buildingId!=b.buildingId};buildingFacts=buildingFacts.filter {it.buildingId!=b.buildingId};coverage=false;save()}){Text("Remove source item ${b.buildingId}")}}
        }
        if(section=="Addresses" && mode!=WorkspaceMode.REGULAR) {
            item {Button(onClick={contactPicker.launch(arrayOf("application/pdf","text/plain","text/csv"))},modifier=Modifier.testTag("native-import-contacts")){Text("Import contact source")};Text(document?.label ?: "Import the source containing these records. A map import grants no telephone-use permission.")}
            document?.let {doc->item {NativeContactSourcePreview(store,doc)}}
            item {NativeField("Record identifier",itemId,"native-contact-id"){itemId=it};NativeField("Street address",address,"native-address"){address=it;addressCheck=false;boundaryCheck=false;bindingCheck=false};NativeField("Unit",unit,"native-unit"){unit=it;addressCheck=false;boundaryCheck=false;bindingCheck=false};NativeField("City",city,"native-city"){city=it;addressCheck=false;boundaryCheck=false;bindingCheck=false};NativeField("State (two letters)",addressState,"native-address-state"){addressState=it;addressCheck=false;boundaryCheck=false;bindingCheck=false};NativeField("Postal code",postal,"native-postal"){postal=it;addressCheck=false;boundaryCheck=false;bindingCheck=false};NativeField("Building identifier, if applicable",buildingId,"native-address-building"){buildingId=it;boundaryCheck=false;bindingCheck=false}}
            item {NativeCheck("I verified this address in the imported contact source",addressCheck,"native-address-confirmed"){addressCheck=it};NativeCheck("I verified this address is inside this territory's worked area",boundaryCheck,"native-boundary-confirmed"){boundaryCheck=it};NativeCheck("I provided this source and authorize its address records for this card",addressUse,"native-address-authorized"){addressUse=it}}
            if(mode==WorkspaceMode.TELEPHONE) {
                item {NativeChoice("Number availability",phoneState,listOf("VERIFIED_NUMBER","UNAVAILABLE"),"native-phone-state"){phoneState=it;if(it=="UNAVAILABLE")phone="";bindingCheck=false}}
                if(phoneState=="VERIFIED_NUMBER")item {NativeField("Number from the authorized source",phone,"native-phone"){phone=it;bindingCheck=false};NativeCheck("I authorize this source for telephone use",phoneUse,"native-phone-authorized"){phoneUse=it};NativeCheck("I checked that this exact number belongs to this address in the source",bindingCheck,"native-phone-binding"){bindingCheck=it}}
            }
            item {Button(onClick={runCatching {val doc=requireNotNull(document){"Import the contact source first"};require(itemId.isNotBlank() && address.isNotBlank())
                val c=NativeContactDraft(itemId,address,unit,city,addressState,postal,buildingId,doc.sha256,doc.label,Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),addressCheck,boundaryCheck,addressUse,phoneUse,phoneState,phone,bindingCheck)
                contacts=contacts.filter {it.id!=c.id}+c;save();addressCheck=false;boundaryCheck=false;bindingCheck=false
            }.onFailure {message=it.message.orEmpty()}},modifier=Modifier.testTag("native-save-contact")){Text("Save sourced record")};Text("${contacts.size} records in draft")}
            items(contacts.size) {i->val c=contacts[i];Text("${c.id}: ${c.address} • ${c.phoneState}")
                TextButton(onClick={itemId=c.id;address=c.address;unit=c.unit;city=c.city;addressState=c.state;postal=c.postalCode;buildingId=c.buildingId;phone=c.phone;phoneState=c.phoneState;document=NativeInventoryDocument(c.sourceSha256,c.sourceLabel);addressCheck=false;boundaryCheck=false;bindingCheck=false;addressUse=false;phoneUse=false}){Text("Edit record ${c.id}")}
                TextButton(onClick={contacts=contacts.filter {it.id!=c.id};save()}){Text("Remove record ${c.id}")}}

        }
        if(section=="Review") {
            item {Text("Reconcile the complete source",style=MaterialTheme.typography.titleLarge);Text("${roads.size} roads, ${buildings.size} footprints, ${contacts.size} working records. This records your local assignment authorization; it does not approve a PDF.")}
            item {NativeTrace(roads,buildings,emptyList()) {}}
            item {NativeCheck("I reviewed all source pages and accounted for every road, worked side, exclusion and building",coverage,"native-source-complete"){coverage=it}}
            item {Button(enabled=sourceReadable && !busy,onClick={save()},modifier=Modifier.testTag("native-save-draft")){Text("Save reconciliation draft")}}
            item {Button(enabled=sourceReadable && !busy && draft!=null,onClick={scope.launch {busy=true
                runCatching {val proposed=proposal();val current=requireNotNull(draft);require(proposed.assignment==current.assignment && proposed.reconciliation==current.reconciliation && proposed.contacts==current.contacts && proposed.jurisdiction==current.jurisdiction){"Save all changes before registering"}
                    withContext(Dispatchers.IO) {store.register(id,mode,current.revisionSha256)}
                }.onSuccess {message="Local assignment registered. Verify fresh sources to prepare the card."+(store.ledger.durabilityWarning?.let {" Storage warning: $it"} ?: "")}.onFailure {message=it.message.orEmpty()};busy=false
            }},modifier=Modifier.testTag("native-register")){Text("Register this reconciled assignment")}}
            item {Button(enabled=!busy && draft!=null,onClick={scope.launch {busy=true
                runCatching {val current=requireNotNull(draft);val proposed=proposal();require(proposed.assignment==current.assignment && proposed.reconciliation==current.reconciliation && proposed.contacts==current.contacts && proposed.jurisdiction==current.jurisdiction){"Save all changes before preparation"};withContext(Dispatchers.IO) {service.prepare(id,mode,current.revisionSha256)}}.onSuccess {message="Prepared using fresh independent sources. Build the candidate, inspect its PDF and explicitly approve it in the workspace."}.onFailure {message=it.message.orEmpty()};busy=false
            }},modifier=Modifier.testTag("native-prepare")){Text("Verify sources and prepare")}}
            item {OutlinedButton(enabled=!busy && !archivePickerOwned,onClick={archivePickerOwned=true;archivePicker.launch("Territory-native-evidence.zip")},modifier=Modifier.testTag("native-export-history")){Text("Export all native source evidence")}}
            item {OutlinedButton(onClick={runCatching {val head=store.ledger.history(id,mode.name).lastOrNull() ?: error("No registration history");store.ledger.archiveRevokedHistory(id,mode.name,head.eventSha256)}.onSuccess {message="Revoked history preserved. Review and register again to begin a new cycle."}.onFailure {message=it.message.orEmpty()}},modifier=Modifier.testTag("native-archive-registration")){Text("Archive revoked registration history")}}
            item {OutlinedButton(onClick={runCatching {store.pruneUnusedDraftHistory(setOfNotNull(document?.sha256))}.onSuccess {message="Removed $it unused draft versions. Registered evidence and current drafts were retained."}.onFailure {message=it.message.orEmpty()}},modifier=Modifier.testTag("native-prune-history")){Text("Remove unused draft history")}}
            item {OutlinedButton(onClick={runCatching {val head=store.ledger.history(id,mode.name).lastOrNull() ?: error("No registration exists");store.ledger.revoke(id,mode.name,head.eventSha256,author,Instant.now().truncatedTo(ChronoUnit.SECONDS).toString())}.onSuccess {message="Registration revoked. Prepared candidates and approvals are invalid."}.onFailure {message=it.message.orEmpty()}},modifier=Modifier.testTag("native-revoke")){Text("Revoke local registration")}}
        }
    }
}

package com.koenterprises.territorycardstudio

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class PreparationConfirmation(val kind:String,val ticket:ExtendedPreparationTicket?,val receiptId:String?)

@Composable
fun EditingPreparationScreen(modifier:Modifier,id:String,mode:WorkspaceMode,authority:AndroidEditingAuthorityStore,
    preparation:AndroidExtendedPreparationService,onBack:()->Unit) {
    val context=LocalContext.current;val activity=context.previewActivity();val scope=rememberCoroutineScope()
    var status by remember(id,mode) {mutableStateOf(EditingAuthorityStatus(false,0,0,"Loading source status…"))}
    var ticket by remember(id,mode) {mutableStateOf<ExtendedPreparationTicket?>(null)}
    var busy by remember {mutableStateOf(false)};var active by remember {mutableStateOf(true)}
    var message by remember {mutableStateOf<String?>(null)};var confirm by remember {mutableStateOf<PreparationConfirmation?>(null)}
    var prepared by remember {mutableStateOf(false)}
    fun refresh() {scope.launch {val s=withContext(Dispatchers.IO){authority.status(id,mode)};status=s
        val t=ticket;if(t!=null && !withContext(Dispatchers.IO){preparation.ticketCurrent(t)} && ticket==t){ticket=null;message="Validation expired or its source/proposals changed. Validate again."}}}
    fun action(success:String,returnedDocument:Boolean=false,operation:()->ExtendedPreparationTicket?) {
        if(busy || (!active && !returnedDocument))return
        val old=ticket;ticket=null;busy=true;message=null;prepared=false
        scope.launch {try {val result=withContext(Dispatchers.IO){runCatching {old?.let {runCatching{preparation.cancel(it)}};operation()}}
            result.onSuccess {ticket=it;message=success;prepared=success.startsWith("Prepared")}.onFailure {message=it.message ?: "Preparation blocked; existing candidate was preserved"}
        }finally{busy=false;refresh()}}
    }
    fun leave() {if(!busy){if(ticket!=null)confirm=PreparationConfirmation("back",ticket,status.receiptId) else onBack()}}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->if(uri!=null)action("Independent source facts imported. Review and validate before preparing.",returnedDocument=true) {
        val bytes=requireNotNull(context.contentResolver.openInputStream(uri)).use {it.readBytesBounded(AndroidEditingAuthorityStore.MAX_BYTES)}
        authority.importFacts(id,mode,bytes);null
    }}
    DisposableEffect(activity,id,mode) {
        val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_PAUSE)active=false;if(event==Lifecycle.Event.ON_RESUME){active=true;refresh()}}
        activity?.lifecycle?.addObserver(observer);onDispose {activity?.lifecycle?.removeObserver(observer)}
    }
    LaunchedEffect(id,mode){refresh()}
    LaunchedEffect(ticket?.id,active){while(ticket!=null && active){delay(1000);if(!busy)refresh()}}
    BackHandler {leave()}
    confirm?.let {confirmation->val kind=confirmation.kind;val captured=confirmation.ticket
        AlertDialog(onDismissRequest={confirm=null},title={Text(if(kind=="prepare")"Prepare this verified change?" else if(kind=="revoke")"Remove imported authority facts?" else "Leave validation?")},
            text={Text(when(kind){"prepare"->"Install only this exact validated input for building. This does not build, approve, or export a card.";"revoke"->"Existing validation and prepared inputs bound to these facts will become unavailable.";else->"Cancel this validation ticket and return to the workspace. Saved proposals remain."})},
            confirmButton={TextButton(modifier=Modifier.testTag("preparation-confirm"),onClick={confirm=null
                when(kind){
                    "prepare"->if(captured!=null){ticket=null;action("Prepared for build • not approved") {preparation.prepare(captured);null}}
                    "revoke"->action("Authority facts removed; import independently authorized facts to continue") {authority.revoke(id,mode,requireNotNull(confirmation.receiptId));null}
                    else->{captured?.let {runCatching{preparation.cancel(it)}};ticket=null;onBack()}
                }
            }){Text("Continue")}},dismissButton={TextButton(modifier=Modifier.testTag("preparation-cancel"),onClick={confirm=null}){Text("Cancel")}})
    }
    Box(modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter) {
        LazyColumn(Modifier.fillMaxHeight().widthIn(max=960.dp).fillMaxWidth().testTag("editing-preparation"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item {TextButton(onClick={leave()},enabled=!busy,modifier=Modifier.testTag("preparation-back")){Text("← Back to workspace")};Text("Validate and prepare changes",style=MaterialTheme.typography.headlineLarge);Text("Territory $id • ${mode.label}")}
            item {Text("Saved proposals must match independently authored source facts. Source references and draft text never grant authority. Validation uses fresh geometry providers and the existing frozen checks.")}
            item {Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(if(status.available)"Independent facts available" else "Independent facts required",style=MaterialTheme.typography.titleLarge,modifier=Modifier.testTag("preparation-status"));Text(status.message);if(status.available)Text("${status.sourceCount} sources • ${status.factCount} facts")}}}
            item {OutlinedButton(onClick={picker.launch(arrayOf("application/json","text/plain","application/octet-stream"))},enabled=!busy && active,modifier=Modifier.fillMaxWidth().testTag("preparation-import")){Text("Import independent authority facts")};Text("Only exact files already recognized by the current assignment authority or eligible record-specific sources are accepted. A map PDF by itself is not a structured fact file.",style=MaterialTheme.typography.bodySmall)}
            if(busy)item {LinearProgressIndicator(Modifier.fillMaxWidth().testTag("preparation-busy"));Text("Checking exact sources, proposals and validation results…")}
            message?.let {item {Text(it,modifier=Modifier.testTag("preparation-message"),color=if(prepared)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)}}
            item {Button(onClick={action("Validation passed. Review the bound input below, then explicitly prepare it."){preparation.validate(id,mode)}},enabled=status.available && !busy && active,modifier=Modifier.fillMaxWidth().testTag("preparation-validate")){Text("Validate saved changes")}}
            ticket?.let {t->item {Card(Modifier.fillMaxWidth().testTag("preparation-ticket")){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Validation passed • not prepared",style=MaterialTheme.typography.titleLarge)
                Text("${t.changeCount} proposals • ${t.authoritySourceCount} independent authority files")
                Text("Fresh geometry providers: ${t.providerIds.joinToString()}")
                Text("Input: ${t.inputSha256}",style=MaterialTheme.typography.bodySmall)
                Text("Inventory: ${t.inventorySha256 ?: "None — Page 2 remains blocked"}",style=MaterialTheme.typography.bodySmall)
                Text("Changes to either journal, source, authority import or current candidate require a new validation.")
            }}}
            item {Button(onClick={confirm=PreparationConfirmation("prepare",ticket,status.receiptId)},enabled=!busy && active,modifier=Modifier.fillMaxWidth().testTag("preparation-prepare")){Text("Prepare validated input")}}}
            if(status.available)item {OutlinedButton(onClick={confirm=PreparationConfirmation("revoke",ticket,status.receiptId)},enabled=!busy && active,modifier=Modifier.fillMaxWidth().testTag("preparation-revoke")){Text("Remove imported authority facts")}}
            item {OutlinedButton(onClick={refresh()},enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("preparation-refresh")){Text("Refresh status")};Text("Preparing does not approve field use. Build, candidate review, approval and export remain separate guarded steps.")}
        }
    }
}

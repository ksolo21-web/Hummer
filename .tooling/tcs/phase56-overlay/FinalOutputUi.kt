package com.koenterprises.territorycardstudio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable fun FinalOutputScreen(modifier:Modifier,id:String,mode:WorkspaceMode,service:AndroidFinalOutputService,onBack:()->Unit) {
    key(id,mode,service){FinalOutputContent(modifier,id,mode,service,onBack)}
}
@Composable private fun FinalOutputContent(modifier:Modifier,id:String,mode:WorkspaceMode,service:AndroidFinalOutputService,onBack:()->Unit) {
    val context=LocalContext.current;val activity=context.exportActivity();val scope=rememberCoroutineScope()
    var state by remember{mutableStateOf<FinalOutputState?>(null)};var ticket by remember{mutableStateOf<FinalOutputTicket?>(null)}
    var pending by remember{mutableStateOf<FinalOutputTicket?>(null)};var pendingAudit by remember{mutableStateOf(false)}
    var picker by rememberSaveable{mutableStateOf(false)};var busy by remember{mutableStateOf(false)}
    var resumed by remember{mutableStateOf(activity?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED)!=false)}
    var revision by remember{mutableIntStateOf(0)};var message by remember{mutableStateOf<String?>(null)}
    var generation by remember{mutableIntStateOf(0)}
    DisposableEffect(activity){val observer=LifecycleEventObserver{_,e->
        if(e==Lifecycle.Event.ON_PAUSE){resumed=false;generation++;ticket=null}
        if(e==Lifecycle.Event.ON_RESUME){resumed=true;revision++}
    };activity?.lifecycle?.addObserver(observer);onDispose{activity?.lifecycle?.removeObserver(observer)}}
    LaunchedEffect(revision,resumed){if(resumed)state=withContext(Dispatchers.IO){service.state(id,mode)}}
    fun saved(uri:android.net.Uri?) {
        val owned=picker;val selected=pending;val audit=pendingAudit;picker=false;pending=null;ticket=null
        if(!owned){message="Expired save result ignored.";return}
        if(uri==null){message="Save cancelled. No output was written.";return}
        busy=true
        scope.launch{val result=withContext(Dispatchers.IO){runCatching{service.exportCreated(selected,AndroidCreatedExportDestination(context.contentResolver,uri),audit)}}
            message=result.fold({"${it.kind} saved and read back successfully. SHA-256 ${it.sha256}"},{it.message ?: "Save failed"});busy=false;revision++}
    }
    val pdfPicker=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")){saved(it)}
    val auditPicker=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){saved(it)}
    Surface(modifier.fillMaxSize().testTag("final-output-screen")) {
        LazyColumn(Modifier.fillMaxSize().testTag("final-output-list"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item{TextButton(onClick=onBack,enabled=!busy && !picker){Text("← Back to workspace")};Text("Validate and export",style=MaterialTheme.typography.headlineLarge);Text("Territory $id • ${mode.label}")}
            item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(state?.message ?: "Checking current approval…",Modifier.testTag("final-output-status"))
                Text("Final validation checks the exact reviewed packet and current independent source evidence. Changes or expired verification block saving.")
            }}}
            item{Button(onClick={busy=true;ticket=null;message=null;val gen=generation
                scope.launch{val result=withContext(Dispatchers.IO){runCatching{service.validate(id,mode)}}
                    if(gen==generation && resumed){ticket=result.getOrNull();message=result.exceptionOrNull()?.message};busy=false}
            },enabled=state?.approved==true && resumed && !busy && !picker,modifier=Modifier.fillMaxWidth().testTag("final-output-validate")){Text(if(busy)"Checking…" else "Run final validation")}}
            ticket?.let{t->
                item{Card(Modifier.fillMaxWidth().testTag("final-output-ready")){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text("Ready to save",style=MaterialTheme.typography.titleLarge);Text(t.review.manifest.canonicalFilename)
                    Text("${t.review.manifest.pageCount} page(s) • ${t.byteCount} bytes");Text("PDF SHA-256");Text(t.review.manifest.packetPdfSha256)
                    Text("Print at actual size (100%). Do not fit or crop. Keep the pages together.")
                }}}
                item{Button(onClick={pending=t;pendingAudit=false;picker=true;pdfPicker.launch(t.review.manifest.canonicalFilename)},enabled=resumed && !busy && !picker,modifier=Modifier.fillMaxWidth().testTag("final-output-pdf")){Text("Save PDF…")}}
                item{OutlinedButton(onClick={pending=t;pendingAudit=true;picker=true;auditPicker.launch(t.review.manifest.canonicalFilename.removeSuffix(".pdf")+" - audit.json")},enabled=resumed && !busy && !picker,modifier=Modifier.fillMaxWidth().testTag("final-output-audit")){Text("Save validation audit…")}}
            }
            message?.let{item{Text(it,Modifier.testTag("final-output-message"))}}
        }
    }
}

package com.koenterprises.territorycardstudio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable fun VerifiedProjectScreen(modifier:Modifier,id:String,mode:WorkspaceMode,service:AndroidVerifiedProjectIntake,onBack:()->Unit) {
    key(id,mode,service){
        val context=LocalContext.current;val scope=rememberCoroutineScope();val activity=context.exportActivity()
        var busy by remember{mutableStateOf(false)};var ticket by remember{mutableStateOf<InitialPreparationTicket?>(null)}
        var message by remember{mutableStateOf("Import the source map, then its independently registered current-assignment project.")}
        var epoch by remember{mutableIntStateOf(0)};var resumed by remember{mutableStateOf(true)}
        DisposableEffect(activity){val observer=LifecycleEventObserver{_,e->if(e==Lifecycle.Event.ON_PAUSE){epoch++;resumed=false;ticket=null};if(e==Lifecycle.Event.ON_RESUME)resumed=true}
            activity?.lifecycle?.addObserver(observer);onDispose{activity?.lifecycle?.removeObserver(observer)}}
        val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
            ticket=null
            if(uri==null)message="Import cancelled." else {busy=true;val generation=epoch
                scope.launch{val result=withContext(Dispatchers.IO){runCatching{val bytes=requireNotNull(context.contentResolver.openInputStream(uri)).use{AndroidFinalOutputService.readBounded(it,VerifiedProjectCodec.MAX_BYTES)};service.validate(id,mode,bytes)}}
                    if(generation==epoch){ticket=result.getOrNull();message=result.fold({"Independent verification passed. Review the source binding, then prepare for Build."},{it.message ?: "Project could not be verified"})};busy=false}
            }
        }
        Surface(modifier.fillMaxSize().testTag("verified-project-screen")){
            LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
                item{TextButton(onClick=onBack,enabled=!busy){Text("← Back to workspace")};Text("Prepare a new card",style=MaterialTheme.typography.headlineLarge);Text("Territory $id • ${mode.label}")}
                item{Text("The project supplies source-backed roads, labels, building assignments and the working inventory. Imported approval flags and old provider results cannot authorize a build.")}
                item{Text(message,Modifier.testTag("verified-project-status"))}
                item{Button(onClick={picker.launch(arrayOf("application/json"))},enabled=!busy && resumed,modifier=Modifier.fillMaxWidth().testTag("verified-project-import")){Text(if(busy)"Verifying…" else "Import verified project…")}}
                ticket?.let{t->
                    item{Text("Project SHA-256\n${t.projectSha256}\n\nSource map SHA-256\n${t.sourceSha256}\n\nVerified sources: ${t.providers.joinToString()}")}
                    item{Button(onClick={busy=true;ticket=null;scope.launch{val result=withContext(Dispatchers.IO){runCatching{service.prepare(t)}};message=result.fold({"Prepared. Return to the workspace to build and review your card."},{it.message ?: "Preparation failed"});busy=false}},enabled=!busy && resumed,modifier=Modifier.fillMaxWidth().testTag("verified-project-prepare")){Text("Prepare for Build")}}
                }
            }
        }
    }
}

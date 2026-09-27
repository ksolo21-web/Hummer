package com.koenterprises.territorycardstudio

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ApprovedExportScreen(modifier: Modifier, territoryId: String, service: AndroidApprovedExportService, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context.exportActivity()
    val scope = rememberCoroutineScope()
    var state by remember(territoryId, service) { mutableStateOf<ApprovedExportState?>(null) }
    var busy by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var receipt by remember { mutableStateOf<ApprovedExportReceipt?>(null) }
    // Deliberately not saveable: a recreated screen cannot reuse an old export ticket.
    var pending by remember { mutableStateOf<ApprovedExportTicket?>(null) }
    var pickerActive by rememberSaveable { mutableStateOf(false) }
    var resumed by remember { mutableStateOf(activity?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != false) }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) { resumed = false; state = null; receipt = null }
            if (event == Lifecycle.Event.ON_RESUME) { resumed = true; revision++ }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }
    LaunchedEffect(territoryId, service, revision, resumed) {
        if (resumed) state = withContext(Dispatchers.IO) { service.state(territoryId) }
    }
    val savePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val ownResult = pickerActive
        val ticket = pending
        pickerActive = false; pending = null; receipt = null; error = null
        if (!ownResult) {
            message = "Expired document result ignored. Choose Save a copy again."
        } else if (uri == null) {
            message = "Save cancelled. No PDF was exported."
        } else {
            busy = true; message = null
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { service.exportCreated(ticket, AndroidCreatedExportDestination(context.contentResolver, uri)) }
                }
                receipt = result.getOrNull(); error = result.exceptionOrNull()?.message
                state = withContext(Dispatchers.IO) { service.state(territoryId) }
                busy = false
            }
        }
    }
    val attachPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) message = "Attachment cancelled. The existing copy was preserved."
        else {
            busy = true; message = null; error = null; receipt = null
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching {
                    requireNotNull(context.contentResolver.openInputStream(uri)) { "Cannot open selected PDF" }.use {
                        service.attach(territoryId, it)
                    }
                } }
                error = result.exceptionOrNull()?.message
                if (result.isSuccess) message = "Exact approved PDF attached and verified."
                state = withContext(Dispatchers.IO) { service.state(territoryId) }
                busy = false
            }
        }
    }
    val current = state
    Surface(modifier.fillMaxSize().testTag("approved-export-screen").semantics {
        stateDescription = if (busy || state == null) "busy" else "ready"
    }, color = MaterialTheme.colorScheme.background) {
        LazyColumn(Modifier.fillMaxSize().testTag("export-list"), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                TextButton(onClick = onBack, enabled = !busy && !pickerActive, modifier = Modifier.testTag("export-back")) { Text("← Back to workspace") }
                Text("Export approved card", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Territory $territoryId", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Existing approved card only", fontWeight = FontWeight.SemiBold)
                        Text("Save an unchanged copy of the card authorized in the Knowledge Base. This does not export a generated candidate or a different workspace-mode packet.")
                        Text("Local candidate approval does not unlock field export.")
                    }
                }
            }
            if (busy || current == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (current != null) {
                item {
                    Card(Modifier.fillMaxWidth().testTag("export-identity")) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(current.canonicalFilename ?: "Unknown card", style = MaterialTheme.typography.titleLarge)
                            Text("Authority SHA-256", style = MaterialTheme.typography.labelMedium)
                            Text(current.expectedSha256 ?: "Unavailable", style = MaterialTheme.typography.bodySmall)
                            if (current.ticket != null) {
                                Text("Exact approved copy verified", color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("export-ready"))
                                Text("${current.ticket.byteCount} bytes • PDF")
                            }
                        }
                    }
                }
                current.blocker?.let { reason -> item {
                    Text(if (current.canAttach) "Approved copy unavailable" else "Export blocked", style = MaterialTheme.typography.titleMedium)
                    Text(reason, modifier = Modifier.testTag("export-blocker"))
                } }
                if (current.canAttach) item {
                    OutlinedButton(onClick = {
                        message = null; error = null
                        try { attachPicker.launch(arrayOf("application/pdf")) }
                        catch (e: Exception) { error = e.message ?: "Document picker unavailable" }
                    },
                        enabled = !busy && resumed && !pickerActive, modifier = Modifier.fillMaxWidth().testTag("export-attach")) { Text("Attach exact approved PDF") }
                }
                item {
                    Button(onClick = {
                        val ticket = current.ticket ?: return@Button
                        busy = true; message = null; error = null; receipt = null
                        scope.launch {
                            val failure = withContext(Dispatchers.IO) { runCatching { service.verifyCurrent(ticket) }.exceptionOrNull() }
                            busy = false
                            if (failure == null) {
                                pending = ticket; pickerActive = true
                                try { savePicker.launch(ticket.canonicalFilename) }
                                catch (e: Exception) { pending = null; pickerActive = false; error = e.message ?: "Document picker unavailable" }
                            } else { error = failure.message; revision++ }
                        }
                    }, enabled = current.ticket != null && !busy && resumed && !pickerActive,
                        modifier = Modifier.fillMaxWidth().testTag("export-save")) { Text("Save a copy…") }
                }
            }
            receipt?.let { saved -> item {
                Card(Modifier.fillMaxWidth().testTag("export-success"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Saved and verified", style = MaterialTheme.typography.titleLarge)
                        Text(saved.canonicalFilename)
                        Text("${saved.byteCount} bytes • Exact original preserved")
                        Text(saved.sha256, style = MaterialTheme.typography.bodySmall)
                        Text("The saved copy was read back and its hash matches the approved original.")
                    }
                }
            } }
            message?.let { text -> item { Text(text, modifier = Modifier.testTag("export-message")) } }
            error?.let { text -> item { Text(text, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("export-error")) } }
            item { TextButton(onClick = { revision++; receipt = null; message = null; error = null },
                enabled = !busy && resumed && !pickerActive, modifier = Modifier.testTag("export-refresh")) { Text("Recheck approved copy") } }
        }
    }
}

private tailrec fun Context.exportActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.exportActivity()
    else -> null
}

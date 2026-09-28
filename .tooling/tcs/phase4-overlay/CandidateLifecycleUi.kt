package com.koenterprises.territorycardstudio

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class LifecycleConfirmation(
    val ticket: CandidateReviewTicket,
    val actor: String,
    val checks: CandidateReviewChecks,
    val approved: Boolean,
    val revision: String
)

@Composable
fun CandidateLifecycleScreen(modifier: Modifier, territoryId: String, mode: WorkspaceMode,
    service: AndroidCandidateLifecycleService, onPreview: () -> Unit, onBack: () -> Unit) {
    var state by remember(territoryId, mode, service) { mutableStateOf<CandidateLifecycleState?>(null) }
    var actor by remember { mutableStateOf("") }
    var pages by remember { mutableStateOf(false) }
    var boundaries by remember { mutableStateOf(false) }
    var data by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<LifecycleConfirmation?>(null) }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    val activity = LocalContext.current.lifecycleActivity()
    var resumed by remember { mutableStateOf(activity?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != false) }
    val scope = rememberCoroutineScope()
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) { resumed = false; state = null; confirmation = null }
            if (event == Lifecycle.Event.ON_RESUME) { resumed = true; revision++ }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }
    LaunchedEffect(territoryId, mode, service, revision, resumed) {
        state = null; actor = ""; pages = false; boundaries = false; data = false; confirmation = null
        if (!resumed) { busy = false; return@LaunchedEffect }
        busy = true
        val loaded = withContext(Dispatchers.IO) { runCatching { service.state(territoryId, mode) } }
        state = loaded.getOrNull()
        error = loaded.exceptionOrNull()?.message
        busy = false
    }
    val ticket = state?.ticket
    Surface(modifier.fillMaxSize().testTag("lifecycle-screen").semantics { stateDescription = if (busy) "busy" else "ready" }, color = MaterialTheme.colorScheme.background) {
        LazyColumn(Modifier.fillMaxSize().testTag("lifecycle-list"), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                TextButton(onClick = onBack, enabled = !busy, modifier = Modifier.testTag("lifecycle-back")) { Text("← Back to Build") }
                Text("Candidate lifecycle", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Territory $territoryId • ${mode.label}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Local card reference", fontWeight = FontWeight.SemiBold)
                        Text("Approve a reviewed candidate to make it the current local reference. This does not authorize field export.")
                    }
                }
            }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state?.blocker?.let { reason -> item {
                Card(Modifier.fillMaxWidth().testTag("lifecycle-blocked")) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Review blocked", style = MaterialTheme.typography.titleLarge)
                        Text(reason)
                        Text("Return to Build and prepare a current, complete candidate.")
                    }
                }
            } }
            if (ticket != null && resumed) {
                val m = ticket.manifest
                item {
                    Card(Modifier.fillMaxWidth().testTag("lifecycle-identity")) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(m.canonicalFilename, style = MaterialTheme.typography.titleMedium)
                            Text("${m.pageCount} page" + if (m.pageCount == 1) " • Front map" else "s • Front map + " + if (mode == WorkspaceMode.TELEPHONE) "phone list" else "address list")
                            Text("Candidate version", style = MaterialTheme.typography.labelMedium)
                            Text(m.candidateVersion, style = MaterialTheme.typography.bodySmall)
                            Text("Exact PDF SHA-256", style = MaterialTheme.typography.labelMedium)
                            Text(m.packetPdfSha256, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("lifecycle-hash"))
                            Text("Validation passed • Current source and inventory", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                item {
                    val candidate = state?.candidates?.firstOrNull { it.ticket == ticket }
                    val status = candidate?.status ?: "CANDIDATE-UNAPPROVED"
                    Card(Modifier.fillMaxWidth().testTag("lifecycle-status"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(status, style = MaterialTheme.typography.titleLarge)
                            Text(if (state?.active == true && state?.promoted?.ticket == ticket) "Current local reference" else "Not the active local reference")
                            Text("Field export requires separate authority.")
                        }
                    }
                }
                item { OutlinedButton(onClick = onPreview, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("lifecycle-preview")) { Text("Open exact PDF Preview") } }
                item {
                    Text("Mandatory review", style = MaterialTheme.typography.titleLarge)
                    Text("Check the actual PDF, then confirm each statement. Validation alone is not approval.")
                }
                item { LifecycleCheck("I reviewed every page of this candidate.", "lifecycle-pages", pages, !busy) { pages = it } }
                item { LifecycleCheck("The map, boundaries and work instructions are correct.", "lifecycle-boundaries", boundaries, !busy) { boundaries = it } }
                item { LifecycleCheck(if (mode == WorkspaceMode.REGULAR) "Labels and directions are readable and complete." else "Addresses, sources and any phone availability are correct.", "lifecycle-data", data, !busy) { data = it } }
                item { OutlinedTextField(actor, { actor = it.take(120) }, label = { Text("Reviewer name") }, singleLine = true,
                    enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("lifecycle-actor")) }
                item {
                    Button(onClick = { confirmation = LifecycleConfirmation(ticket, actor, CandidateReviewChecks(pages, boundaries, data), true, state!!.revision) }, enabled = !busy && actor.isNotBlank() && pages && boundaries && data,
                        modifier = Modifier.fillMaxWidth().testTag("lifecycle-approve")) { Text("Approve and make current…") }
                    OutlinedButton(onClick = { confirmation = LifecycleConfirmation(ticket, actor, CandidateReviewChecks(pages, boundaries, data), false, state!!.revision) }, enabled = !busy && actor.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().testTag("lifecycle-reject")) { Text("Reject candidate…") }
                }
            }
            state?.let { snapshot ->
                if (snapshot.promoted != null && !snapshot.active) item {
                    Card(Modifier.fillMaxWidth().testTag("lifecycle-suspended")) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("SUSPENDED", style = MaterialTheme.typography.titleLarge)
                            Text("Saved local reference is inactive. Revalidate, build and explicitly approve a current candidate.")
                            Text("Version ${snapshot.promoted.ticket.manifest.candidateVersion}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth().testTag("lifecycle-history"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Version and decision history", style = MaterialTheme.typography.titleLarge)
                        if (snapshot.candidates.isEmpty()) Text("No saved candidates yet.")
                        snapshot.candidates.asReversed().forEach { candidate ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(candidate.status, fontWeight = FontWeight.SemiBold)
                                    Text("Version ${candidate.ticket.manifest.candidateVersion}", style = MaterialTheme.typography.bodySmall)
                                    Text(candidate.createdAt, style = MaterialTheme.typography.bodySmall)
                                    Text("PDF ${candidate.ticket.manifest.packetPdfSha256}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        snapshot.history.asReversed().forEach { event ->
                            Text("${event.sequence}. ${event.action} • ${event.actor}\n${event.at}\n${event.reason}\nCandidate ${event.candidateSha256}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("lifecycle-error")) } }
            item { TextButton(onClick = { revision++; error = null }, enabled = !busy && resumed,
                modifier = Modifier.testTag("lifecycle-refresh")) { Text("Recheck current candidate") } }
        }
    }
    val confirm = confirmation
    if (confirm != null && ticket != null && resumed) AlertDialog(
        modifier = Modifier.testTag("lifecycle-confirmation"),
        onDismissRequest = { confirmation = null },
        title = { Text(if (confirm.approved) "Approve and make current?" else "Reject this candidate?") },
        text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(confirm.ticket.manifest.canonicalFilename)
            Text("Version ${confirm.ticket.manifest.candidateVersion}", style = MaterialTheme.typography.bodySmall)
            Text("SHA-256 ${confirm.ticket.manifest.packetPdfSha256}", style = MaterialTheme.typography.bodySmall)
            Text(if (confirm.approved) "This exact version becomes the current local reference and replaces the previous local reference. Field export remains separately controlled." else "This records rejection of this exact version. It stays rejected unless you explicitly review and approve it again.")
        } },
        dismissButton = { TextButton(onClick = { confirmation = null }, modifier = Modifier.testTag("lifecycle-cancel")) { Text("Cancel") } },
        confirmButton = { TextButton(onClick = {
            val decisionRevision = revision
            confirmation = null; busy = true; error = null
            scope.launch {
                val failure = withContext(Dispatchers.IO) {
                    runCatching { service.decide(confirm.ticket, confirm.actor, confirm.checks, confirm.approved, true, confirm.revision) }.exceptionOrNull()
                }
                if (!resumed || revision != decisionRevision) return@launch
                error = failure?.message
                val loaded = withContext(Dispatchers.IO) { runCatching { service.state(territoryId, mode) } }
                if (!resumed || revision != decisionRevision) return@launch
                state = loaded.getOrNull()
                if (loaded.isFailure) error = loaded.exceptionOrNull()?.message
                actor = ""; pages = false; boundaries = false; data = false; busy = false
            }
        }, modifier = Modifier.testTag("lifecycle-confirm")) { Text(if (confirm.approved) "Approve and make current" else "Confirm rejection") } }
    )
}

@Composable
private fun LifecycleCheck(text: String, tag: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, change, enabled = enabled, modifier = Modifier.testTag(tag).semantics { contentDescription = text })
        Text(text, Modifier.weight(1f))
    }
}

private tailrec fun Context.lifecycleActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.lifecycleActivity()
    else -> null
}

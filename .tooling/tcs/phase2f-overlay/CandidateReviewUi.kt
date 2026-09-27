package com.koenterprises.territorycardstudio

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.koenterprises.territorycardstudio.core.ExactApprovalState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CandidateReviewScreen(modifier: Modifier, territoryId: String, mode: WorkspaceMode,
    service: AndroidCandidateReviewService, onPreview: () -> Unit, onBack: () -> Unit) {
    var state by remember(territoryId, mode, service) { mutableStateOf<CandidateReviewState?>(null) }
    var actor by remember { mutableStateOf("") }
    var pages by remember { mutableStateOf(false) }
    var boundaries by remember { mutableStateOf(false) }
    var data by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    val activity = LocalContext.current.reviewActivity()
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
        state = withContext(Dispatchers.IO) { service.state(territoryId, mode) }
        busy = false
    }
    val ticket = state?.ticket
    Surface(modifier.fillMaxSize().testTag("candidate-review-screen").semantics { stateDescription = if (busy) "busy" else "ready" }, color = MaterialTheme.colorScheme.background) {
        LazyColumn(Modifier.fillMaxSize().testTag("review-list"), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                TextButton(onClick = onBack, enabled = !busy, modifier = Modifier.testTag("review-back")) { Text("← Back to Build") }
                Text("Review candidate", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Territory $territoryId • ${mode.label}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Not approved for field use", fontWeight = FontWeight.SemiBold)
                        Text("Your decision is saved for this exact candidate. Authority promotion is still required before it can replace a field card.")
                    }
                }
            }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state?.blocker?.let { reason -> item {
                Card(Modifier.fillMaxWidth().testTag("review-blocked")) {
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
                    Card(Modifier.fillMaxWidth().testTag("review-identity")) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(m.canonicalFilename, style = MaterialTheme.typography.titleMedium)
                            Text("${m.pageCount} page" + if (m.pageCount == 1) " • Front map" else "s • Front map + " + if (mode == WorkspaceMode.TELEPHONE) "phone list" else "address list")
                            Text("Candidate version", style = MaterialTheme.typography.labelMedium)
                            Text(m.candidateVersion, style = MaterialTheme.typography.bodySmall)
                            Text("Exact PDF SHA-256", style = MaterialTheme.typography.labelMedium)
                            Text(m.packetPdfSha256, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("review-hash"))
                            Text("Validation passed • Current source and inventory", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                state?.decision?.let { decision -> item {
                    Card(Modifier.fillMaxWidth().testTag("review-decision"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (decision.approvalState == ExactApprovalState.EXPLICITLY_APPROVED)
                                "Local approval recorded" else "Candidate rejected", style = MaterialTheme.typography.titleLarge)
                            Text("${decision.approvedBy} • ${decision.approvedAtUtc}")
                            Text("Field release remains blocked.")
                        }
                    }
                } }
                item { OutlinedButton(onClick = onPreview, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("review-preview")) { Text("Open exact PDF Preview") } }
                item {
                    Text("Mandatory review", style = MaterialTheme.typography.titleLarge)
                    Text("Check the actual PDF, then confirm each statement. Validation alone is not approval.")
                }
                item { ReviewCheck("I reviewed every page of this candidate.", "review-pages", pages, !busy) { pages = it } }
                item { ReviewCheck("The map, boundaries and work instructions are correct.", "review-boundaries", boundaries, !busy) { boundaries = it } }
                item { ReviewCheck(if (mode == WorkspaceMode.REGULAR) "Labels and directions are readable and complete." else "Addresses, sources and any phone availability are correct.", "review-data", data, !busy) { data = it } }
                item { OutlinedTextField(actor, { actor = it.take(120) }, label = { Text("Reviewer name") }, singleLine = true,
                    enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("review-actor")) }
                item {
                    Button(onClick = { confirmation = true }, enabled = !busy && actor.isNotBlank() && pages && boundaries && data,
                        modifier = Modifier.fillMaxWidth().testTag("review-approve")) { Text("Record local approval…") }
                    OutlinedButton(onClick = { confirmation = false }, enabled = !busy && actor.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().testTag("review-reject")) { Text("Reject candidate…") }
                }
            }
            error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("review-error")) } }
            item { TextButton(onClick = { revision++; error = null }, enabled = !busy && resumed,
                modifier = Modifier.testTag("review-refresh")) { Text("Recheck current candidate") } }
        }
    }
    val confirm = confirmation
    if (confirm != null && ticket != null && resumed) AlertDialog(
        modifier = Modifier.testTag("review-confirmation"),
        onDismissRequest = { confirmation = null },
        title = { Text(if (confirm) "Record local approval?" else "Reject this candidate?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(ticket.manifest.canonicalFilename)
            Text("Version ${ticket.manifest.candidateVersion}", style = MaterialTheme.typography.bodySmall)
            Text("SHA-256 ${ticket.manifest.packetPdfSha256}", style = MaterialTheme.typography.bodySmall)
            Text(if (confirm) "This records your review decision only. It does not authorize field use." else "This replaces any local approval for this exact candidate.")
        } },
        dismissButton = { TextButton(onClick = { confirmation = null }, modifier = Modifier.testTag("review-cancel")) { Text("Cancel") } },
        confirmButton = { TextButton(onClick = {
            confirmation = null; busy = true; error = null
            scope.launch {
                val failure = withContext(Dispatchers.IO) {
                    runCatching { service.record(ticket, actor, CandidateReviewChecks(pages, boundaries, data), confirm, true) }.exceptionOrNull()
                }
                error = failure?.message
                state = withContext(Dispatchers.IO) { service.state(territoryId, mode) }
                actor = ""; pages = false; boundaries = false; data = false; busy = false
            }
        }, modifier = Modifier.testTag("review-confirm")) { Text(if (confirm) "Confirm approval" else "Confirm rejection") } }
    )
}

@Composable
private fun ReviewCheck(text: String, tag: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, change, enabled = enabled, modifier = Modifier.testTag(tag))
        Text(text, Modifier.weight(1f))
    }
}

private tailrec fun Context.reviewActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.reviewActivity()
    else -> null
}

package com.koenterprises.territorycardstudio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BuildWorkflowScreen(
    modifier: Modifier,
    territoryId: String,
    mode: WorkspaceMode,
    coordinator: AndroidBuildWorkflowCoordinator,
    previewService: AndroidPdfPreviewService? = null,
    reviewService: AndroidCandidateReviewService? = null,
    onBack: () -> Unit
) {
    var reviewOpen by remember(territoryId, mode, coordinator) { mutableStateOf(false) }
    var previewKind by remember(territoryId, mode, coordinator) { mutableStateOf<PdfPreviewKind?>(null) }
    if (previewKind != null && previewService != null) {
        PdfPreviewScreen(modifier, territoryId, mode, previewKind!!, previewService, if (reviewOpen) "← Back to review" else "← Back to Build") { previewKind = null }
        return
    }
    if (reviewOpen && reviewService != null) {
        CandidateReviewScreen(modifier, territoryId, mode, reviewService,
            onPreview = { previewKind = if (mode == WorkspaceMode.REGULAR) PdfPreviewKind.FRONT else PdfPreviewKind.PACKET },
            onBack = { reviewOpen = false })
        return
    }
    var state by remember(territoryId, mode, coordinator) { mutableStateOf<BuildWorkflowState?>(null) }
    var busy by remember(territoryId, mode, coordinator) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(territoryId, mode, coordinator) {
        state = withContext(Dispatchers.IO) { coordinator.state(territoryId, mode) }
    }
    fun perform(page2: Boolean) {
        busy = true
        scope.launch {
            try {
                state = withContext(Dispatchers.IO) {
                    if (page2) coordinator.generatePage2(territoryId, mode) else coordinator.buildFront(territoryId, mode)
                }
            } finally { busy = false }
        }
    }
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("build-screen"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                TextButton(onClick = onBack, enabled = !busy) { Text("← Back to workspace") }
                Text("Build", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Territory $territoryId • ${mode.label}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Candidate only", fontWeight = FontWeight.SemiBold)
                        Text("Building does not approve or replace a field card. Review and explicit approval are still required.")
                    }
                }
            }
            val current = state
            if (current == null) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Checking build inputs…") }
            } else {
                item {
                    Card(Modifier.fillMaxWidth().testTag("build-readiness"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Build readiness", style = MaterialTheme.typography.titleLarge)
                            Text("Source map: " + if (current.sourceReady) "verified local copy" else "not ready")
                            Text("Build inputs: " + if (current.inputReady) "verified" else "not prepared")
                            Text("Inventory: ${current.inventorySummary}")
                            Text("Front candidate: " + if (current.front != null) "built" else "not built")
                            if (mode != WorkspaceMode.REGULAR) Text("Two-page packet: " + if (current.packet != null) "generated" else "not generated")
                        }
                    }
                }
                if (current.buildBlockers.isNotEmpty()) {
                    item { Text("Before you can build", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                    items(current.buildBlockers) { reason -> Text(reason, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                item {
                    Button(onClick = { perform(false) }, enabled = current.canBuild && !busy,
                        modifier = Modifier.fillMaxWidth().testTag("build-front")) {
                        Text(if (busy) "Working…" else "Build front candidate")
                    }
                }
                if (mode != WorkspaceMode.REGULAR) {
                    val additionalPageBlockers = current.page2Blockers - current.buildBlockers.toSet()
                    if (additionalPageBlockers.isNotEmpty()) item {
                        Text(additionalPageBlockers.joinToString("\n"), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("page2-blockers"))
                    }
                    item {
                        Button(onClick = { perform(true) }, enabled = current.canGeneratePage2 && !busy,
                            modifier = Modifier.fillMaxWidth().testTag("generate-page2")) {
                            Text("Generate Page 2")
                        }
                    }
                }
                if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                current.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("build-error")) } }
                if (reviewService != null) item {
                    OutlinedButton(onClick = { reviewOpen = true }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth().testTag("review-candidate")) { Text("Review candidate") }
                }
                if (current.front != null) item {
                    Card(Modifier.fillMaxWidth().testTag("build-result"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (current.packet != null) "Two-page candidate generated" else "Front candidate built", style = MaterialTheme.typography.titleLarge)
                            Text(if (reviewService == null) "Awaiting review and explicit approval" else "Field release blocked • See candidate review", color = MaterialTheme.colorScheme.primary)
                            Text(current.front.canonicalFilename)
                            if (previewService != null) {
                                OutlinedButton(onClick = { previewKind = PdfPreviewKind.FRONT }, enabled = !busy,
                                    modifier = Modifier.fillMaxWidth().testTag("preview-front")) { Text("Preview front PDF") }
                                if (current.packet != null) Button(onClick = { previewKind = PdfPreviewKind.PACKET }, enabled = !busy,
                                    modifier = Modifier.fillMaxWidth().testTag("preview-packet")) { Text("Preview two-page PDF") }
                            }
                            Text("SHA-256", style = MaterialTheme.typography.labelMedium)
                            Text(current.packet?.sha256 ?: current.front.sha256, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

package com.koenterprises.territorycardstudio

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

@Composable
internal fun WorkspaceExactPreview(id: String, mode: WorkspaceMode, kind: PdfPreviewKind,
    service: AndroidPdfPreviewService, revision: Int, active: Boolean, onOpen: () -> Unit) {
    var frame by remember(id, mode, kind, revision, active) { mutableStateOf<Bitmap?>(null) }
    var document by remember(id, mode, kind, revision, active) { mutableStateOf<PdfPreviewDocument?>(null) }
    var error by remember(id, mode, kind, revision, active) { mutableStateOf<String?>(null) }
    DisposableEffect(frame) { val owned = frame; onDispose { owned?.recycle() } }
    LaunchedEffect(id, mode, kind, service, revision, active) {
        if (!active) return@LaunchedEffect
        var pending: Bitmap? = null
        try {
            val current = withContext(Dispatchers.IO) { service.open(id, mode, kind) }
            val rendered = withContext(Dispatchers.IO) { service.render(current, 0, 1024).also { pending = it } }
            document = current; frame = rendered; pending = null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { frame = null; document = null; error = failure.message ?: "Current PDF is unavailable" }
        finally { pending?.recycle() }
    }
    Text(if (kind == PdfPreviewKind.APPROVED) "Exact approved reference" else "Current candidate • not approved for field use", style = MaterialTheme.typography.titleMedium)
    Text(if (kind == PdfPreviewKind.APPROVED) "Original approved document; this does not certify a new address or telephone packet." else "Preview uses the current validated PDF bytes.", style = MaterialTheme.typography.bodySmall)
    frame?.let { image ->
        Image(image.asImageBitmap(), "Exact PDF front page for territory $id", Modifier.fillMaxWidth().aspectRatio(image.width.toFloat()/image.height).testTag("workspace-pdf-page"), contentScale = ContentScale.Fit)
        document?.let { Text("SHA-256 ${it.sha256}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("workspace-pdf-hash")) }
        OutlinedButton(onClick = onOpen, modifier = Modifier.testTag("workspace-open-preview")) { Text("Open PDF • pages and zoom") }
    }
    if (frame == null) Text(error ?: if (active) "Checking current PDF…" else "Preview paused", Modifier.testTag("workspace-preview-unavailable"))
}

@Composable
internal fun WorkspaceInventoryContent(snapshot: WorkspaceInventorySnapshot) {
    Text("${snapshot.verified} verified • ${snapshot.review} needs review • ${snapshot.conflicts} conflicts", Modifier.testTag("workspace-inventory-counts"))
    if (snapshot.records.any { it.phone != null }) Text("${snapshot.unavailableNumbers} unavailable numbers")
    Text("Read-only prepared inventory • candidate field release remains separate", style = MaterialTheme.typography.bodySmall)
    Text("Inventory SHA-256 ${snapshot.hash}", style = MaterialTheme.typography.labelSmall)
    snapshot.records.forEach { record ->
        HorizontalDivider()
        Column(Modifier.fillMaxWidth().testTag("workspace-inventory-record-${record.id}"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(record.address, style = MaterialTheme.typography.titleMedium)
            record.phone?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            Text("Record ${record.id}", style = MaterialTheme.typography.labelSmall)
            record.details.forEach { (label, value) -> Text("$label: $value", style = MaterialTheme.typography.bodySmall) }
        }
    }
    HorizontalDivider()
    Text("Inventory provenance", style = MaterialTheme.typography.titleMedium)
    snapshot.provenance.forEach { (label, value) ->
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

package com.koenterprises.territorycardstudio

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun PdfPreviewScreen(
    modifier: Modifier,
    territoryId: String,
    mode: WorkspaceMode,
    kind: PdfPreviewKind,
    service: AndroidPdfPreviewService,
    backLabel: String = "← Back to Build",
    onBack: () -> Unit
) {
    var document by remember(territoryId, mode, kind, service) { mutableStateOf<PdfPreviewDocument?>(null) }
    var page by remember(territoryId, mode, kind) { mutableIntStateOf(0) }
    var zoom by remember(territoryId, mode, kind) { mutableIntStateOf(1) }
    var revision by remember { mutableIntStateOf(0) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val activity = remember(context) { context.previewActivity() }
    var resumed by remember(activity) { mutableStateOf(activity?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != false) }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> { resumed = true; revision++ }
                Lifecycle.Event.ON_PAUSE -> { resumed = false; bitmap = null }
                else -> Unit
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }
    Surface(modifier.fillMaxSize().testTag("pdf-preview-screen"), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = onBack, modifier = Modifier.testTag("preview-back")) { Text(backLabel) }
            Text("PDF Preview", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Territory $territoryId • ${mode.label}", style = MaterialTheme.typography.bodyMedium)
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
                Text(if (kind == PdfPreviewKind.APPROVED) "Exact approved reference • original PDF" else "Candidate • Not approved for field use", Modifier.fillMaxWidth().padding(12.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
            }
            document?.let { current ->
                Text(current.canonicalFilename, style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { page-- }, enabled = !busy && error == null && page > 0,
                        modifier = Modifier.testTag("preview-previous")) { Text("Previous") }
                    Text("${page + 1} / ${current.pageCount}", Modifier.testTag("preview-page-number"))
                    OutlinedButton(onClick = { page++ }, enabled = !busy && error == null && page + 1 < current.pageCount,
                        modifier = Modifier.testTag("preview-next")) { Text("Next") }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { zoom = 1 }, enabled = !busy && error == null && document != null,
                    modifier = Modifier.testTag("preview-fit")) { Text("Fit page") }
                TextButton(onClick = { zoom = (zoom + 1).coerceAtMost(3) }, enabled = !busy && error == null && document != null && zoom < 3,
                    modifier = Modifier.testTag("preview-zoom")) { Text("Zoom ${zoom}× +") }
                TextButton(onClick = { revision++ }, enabled = !busy,
                    modifier = Modifier.testTag("preview-refresh")) { Text("Recheck") }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
                val density = LocalDensity.current.density
                val fitWidthPx = (maxWidth.value * density).roundToInt().coerceIn(256, 1024)
                val fitHeightPx = (maxHeight.value * density).roundToInt().coerceAtLeast(1)
                // The canonical PDF is landscape. Fit both axes, then rerender from the same PDF at each zoom level.
                val fittedWidth = minOf(fitWidthPx, (fitHeightPx * 768f / 481f).roundToInt()).coerceIn(256, 1024)
                val renderWidth = (fittedWidth * zoom).coerceAtMost(3072)
                LaunchedEffect(territoryId, mode, kind, service, page, zoom, revision, renderWidth, resumed) {
                    bitmap = null
                    if (!resumed) { busy = false; return@LaunchedEffect }
                    busy = true; error = null
                    try {
                        val current = document ?: withContext(Dispatchers.IO) { service.open(territoryId, mode, kind) }
                        document = current
                        bitmap = withContext(Dispatchers.IO) { service.render(current, page, renderWidth) }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { error = failure.message ?: "This PDF is unavailable. Return to Build." }
                    finally { busy = false }
                }
                val horizontal = rememberScrollState()
                val vertical = rememberScrollState()
                LaunchedEffect(page, zoom) { horizontal.scrollTo(0); vertical.scrollTo(0) }
                if (busy) CircularProgressIndicator(Modifier.align(Alignment.Center).testTag("preview-loading"))
                error?.let { reason ->
                    Column(Modifier.fillMaxWidth().padding(20.dp).verticalScroll(rememberScrollState()).testTag("preview-error"),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Preview unavailable", style = MaterialTheme.typography.titleLarge)
                        Text(reason)
                        Text(if (kind == PdfPreviewKind.APPROVED) "Return to the workspace and attach the exact approved PDF from Export." else "Return to Build to verify and open the current candidate.")
                    }
                }
                bitmap?.let { rendered ->
                    Box(Modifier.fillMaxSize().horizontalScroll(horizontal).verticalScroll(vertical).testTag("preview-pan")) {
                        Image(rendered.asImageBitmap(), "PDF page ${page + 1} of ${document?.pageCount}",
                            modifier = Modifier.size((rendered.width / density).dp, (rendered.height / density).dp).testTag("preview-page"),
                            contentScale = ContentScale.FillBounds)
                    }
                }
            }
            Text(if (kind == PdfPreviewKind.APPROVED) "Original approved document • page ${page + 1}" else if (page == 0) "Front map" else if (mode == WorkspaceMode.TELEPHONE) "Address and telephone list" else "Address list",
                style = MaterialTheme.typography.labelLarge)
            document?.let { Text("SHA-256 ${it.sha256}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("preview-hash")) }
        }
    }
}

internal tailrec fun Context.previewActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.previewActivity()
    else -> null
}

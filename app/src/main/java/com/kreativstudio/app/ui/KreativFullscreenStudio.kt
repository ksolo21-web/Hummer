@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kreativstudio.app.ui

import android.content.Context
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kreativstudio.app.model.AttachmentKind
import com.kreativstudio.app.model.CanvasLayer
import com.kreativstudio.app.model.KreativProject
import com.kreativstudio.app.model.StrokePoint
import com.kreativstudio.app.model.ToolType
import com.kreativstudio.app.ui.canvas.KreativCanvasView
import com.kreativstudio.app.ui.theme.KreativTheme
import com.kreativstudio.app.ui.theme.LocalKreativTokens
import kotlin.math.ceil
import kotlin.math.max

@Composable
fun KreativFullscreenStudioHost(viewModel: KreativViewModel) {
    val settings by viewModel.settings.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.message) {
        val message = viewModel.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.dismissMessage()
    }

    KreativTheme(settings) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LocalKreativTokens.current.canvasChrome),
        ) {
            FullscreenStudioContent(viewModel)
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 154.dp),
            )
        }
    }
}

@Composable
private fun FullscreenStudioContent(viewModel: KreativViewModel) {
    val project = viewModel.currentProject
    if (project == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ElevatedCard(Modifier.padding(24.dp)) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Default.Brush, null, modifier = Modifier.size(54.dp))
                    Text("No canvas is open", style = MaterialTheme.typography.headlineMedium)
                    Button(onClick = { viewModel.createProject("New Artwork") }) { Text("Create canvas") }
                    TextButton(onClick = { viewModel.navigate(StudioScreen.HOME) }) { Text("Back to Atelier") }
                }
            }
        }
        return
    }

    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    var frame by remember { mutableStateOf<FullscreenCanvasFrame?>(null) }
    var controlsOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var chromeVisible by remember { mutableStateOf(true) }
    var fillViewport by remember(project.id) { mutableStateOf(true) }
    var textPoint by remember { mutableStateOf<StrokePoint?>(null) }

    val attachLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.addAttachments(context, uris, AttachmentKind.REFERENCE)
    }
    val textureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.addAttachments(context, uris, AttachmentKind.TEXTURE)
    }
    val exportProjectLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportProject(uri)
    }
    val exportPngLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) viewModel.exportPng(uri)
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { canvasContext ->
                FullscreenCanvasFrame(canvasContext).also { safeFrame ->
                    frame = safeFrame
                    safeFrame.onCanvasFailure = viewModel::showMessage
                    safeFrame.canvasView.onElementsFinished = viewModel::addElements
                    safeFrame.canvasView.onEraseGesture = viewModel::erase
                    safeFrame.canvasView.onFillRequested = viewModel::fillBackground
                    safeFrame.canvasView.onTextPlacementRequested = { textPoint = it }
                    safeFrame.canvasView.onElementTransformed = viewModel::transformElement
                    safeFrame.canvasView.onInputStatus = { viewModel.inputStatus = it }
                }
            },
            update = { safeFrame ->
                runCatching {
                    safeFrame.fillViewport = fillViewport
                    safeFrame.updateProject(project)
                    safeFrame.canvasView.activeTool = viewModel.activeTool
                    safeFrame.canvasView.activeColorArgb = viewModel.activeColorArgb
                    safeFrame.canvasView.brushWidth = viewModel.brushWidth
                    safeFrame.canvasView.brushOpacity = viewModel.brushOpacity
                    safeFrame.canvasView.stabilization = viewModel.stabilization
                    safeFrame.canvasView.symmetryEnabled = settings.symmetryEnabled
                    safeFrame.canvasView.perspectiveGridEnabled = settings.perspectiveGridEnabled
                    safeFrame.canvasView.palmRejectionEnabled = settings.palmRejectionEnabled
                    safeFrame.canvasView.shapeSnapEnabled = settings.shapeSnapEnabled
                    safeFrame.canvasView.replayProgress = viewModel.replayProgress
                    safeFrame.canvasView.invalidate()
                }.onFailure(safeFrame::reportFailure)
            },
        )

        if (chromeVisible) {
            StudioTopOverlay(
                project = project,
                fillViewport = fillViewport,
                onBack = { viewModel.navigate(StudioScreen.HOME) },
                onRename = { renameOpen = true },
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onSave = viewModel::saveNow,
                onSync = viewModel::syncCurrentProject,
                onReset = { frame?.canvasView?.resetView() },
                onRotate = { frame?.canvasView?.rotateCanvas(15f) },
                onToggleViewport = { fillViewport = !fillViewport },
                onAttach = { attachLauncher.launch(arrayOf("image/*", "application/pdf", "video/*", "audio/*")) },
                onExportProject = { exportProjectLauncher.launch("${project.title.fullscreenFileName()}.kreativ.json") },
                onExportPng = { exportPngLauncher.launch("${project.title.fullscreenFileName()}.png") },
                onHideChrome = { chromeVisible = false },
            )

            StudioBottomOverlay(
                viewModel = viewModel,
                onControls = { controlsOpen = true },
            )
        } else {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(10.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = .88f),
                tonalElevation = 6.dp,
            ) {
                IconButton(onClick = { chromeVisible = true }) {
                    Icon(Icons.Default.Visibility, "Show studio controls")
                }
            }
        }
    }

    if (controlsOpen) {
        ModalBottomSheet(onDismissRequest = { controlsOpen = false }) {
            FullscreenStudioControls(
                viewModel = viewModel,
                project = project,
                onAttach = { attachLauncher.launch(arrayOf("image/*", "application/pdf", "video/*", "audio/*")) },
                onTexture = { textureLauncher.launch(arrayOf("image/*")) },
            )
        }
    }

    textPoint?.let { point ->
        var text by remember(point) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { textPoint = null },
            title = { Text("Add text") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Artwork text") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addText(text, point)
                        textPoint = null
                    },
                    enabled = text.isNotBlank(),
                ) { Text("Place text") }
            },
            dismissButton = { TextButton(onClick = { textPoint = null }) { Text("Cancel") } },
        )
    }

    if (renameOpen) {
        var title by remember(project.id) { mutableStateOf(project.title) }
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("Rename project") },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Project title") },
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.renameProject(title); renameOpen = false }) { Text("Save title") }
            },
            dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StudioTopOverlay(
    project: KreativProject,
    fillViewport: Boolean,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
    onSync: () -> Unit,
    onReset: () -> Unit,
    onRotate: () -> Unit,
    onToggleViewport: () -> Unit,
    onAttach: () -> Unit,
    onExportProject: () -> Unit,
    onExportPng: () -> Unit,
    onHideChrome: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .statusBarsPadding(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .9f),
        tonalElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Atelier")
            }
            TextButton(onClick = onRename) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(project.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(
                        "${project.widthPx} × ${project.heightPx}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                item { IconButton(onClick = onUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") } }
                item { IconButton(onClick = onRedo) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo") } }
                item { IconButton(onClick = onSave) { Icon(Icons.Default.Save, "Save locally") } }
                item { IconButton(onClick = onSync) { Icon(Icons.Default.CloudSync, "Sync") } }
                item { IconButton(onClick = onReset) { Icon(Icons.Default.Refresh, "Reset canvas view") } }
                item { IconButton(onClick = onRotate) { Icon(Icons.Default.RotateRight, "Rotate canvas") } }
                item {
                    TextButton(onClick = onToggleViewport) {
                        Text(if (fillViewport) "Fit" else "Fill")
                    }
                }
                item { IconButton(onClick = onAttach) { Icon(Icons.Default.PhotoLibrary, "Add reference") } }
                item {
                    OutlinedButton(onClick = onExportProject) {
                        Icon(Icons.Default.Download, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Project")
                    }
                }
                item {
                    OutlinedButton(onClick = onExportPng) {
                        Icon(Icons.Default.Download, null)
                        Spacer(Modifier.width(4.dp))
                        Text("PNG")
                    }
                }
                item { IconButton(onClick = onHideChrome) { Icon(Icons.Default.VisibilityOff, "Hide controls") } }
            }
        }
    }
}

@Composable
private fun BoxScopeStudioBottomSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .9f),
        tonalElevation = 8.dp,
        content = content,
    )
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.StudioBottomOverlay(
    viewModel: KreativViewModel,
    onControls: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .9f),
        tonalElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(fullscreenTools, key = { it }) { tool ->
                    FilterChip(
                        selected = viewModel.activeTool == tool,
                        onClick = { viewModel.activeTool = tool },
                        label = { Text(tool.fullscreenLabel()) },
                        leadingIcon = {
                            Icon(
                                if (tool == ToolType.TEXT) Icons.Default.TextFields else Icons.Default.Edit,
                                null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(shape = CircleShape, color = Color(viewModel.activeColorArgb)) {
                    Spacer(Modifier.size(28.dp))
                }
                Text("${viewModel.brushWidth.toInt()} px", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = viewModel.brushWidth,
                    onValueChange = { viewModel.brushWidth = it },
                    valueRange = 1f..180f,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onControls) {
                    Icon(Icons.Default.MoreHoriz, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Controls")
                }
            }
        }
    }
}

@Composable
private fun FullscreenStudioControls(
    viewModel: KreativViewModel,
    project: KreativProject,
    onAttach: () -> Unit,
    onTexture: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState()
    var journalText by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(.9f)
            .imePadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("Studio controls", style = MaterialTheme.typography.headlineMedium) }
        item {
            Text("Color", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                items(fullscreenColors) { color ->
                    val selected = viewModel.activeColorArgb == color
                    Surface(
                        modifier = Modifier
                            .size(if (selected) 43.dp else 37.dp)
                            .clickable { viewModel.activeColorArgb = color },
                        shape = CircleShape,
                        color = Color(color),
                        border = BorderStroke(
                            if (selected) 3.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        ),
                    ) {}
                }
            }
        }
        item {
            Text("Brush", style = MaterialTheme.typography.titleLarge)
            Text("Opacity ${(viewModel.brushOpacity * 100).toInt()}%")
            Slider(value = viewModel.brushOpacity, onValueChange = { viewModel.brushOpacity = it }, valueRange = .05f..1f)
            Text("Stabilization ${(viewModel.stabilization * 100).toInt()}%")
            Slider(value = viewModel.stabilization, onValueChange = { viewModel.stabilization = it }, valueRange = 0f...95f)
        }
        item {
            Text("Brush library", style = MaterialTheme.typography.titleLarge)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                project.brushPresets.forEach { preset ->
                    OutlinedButton(onClick = { viewModel.applyBrush(preset) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Brush, null)
                        Spacer(Modifier.width(8.dp))
                        Text("${preset.name} • ${preset.width.toInt()} px")
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Layers", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = viewModel::addLayer) { Icon(Icons.Default.Add, "Add layer") }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                project.layers.asReversed().forEach { layer -> FullscreenLayerRow(viewModel, project, layer) }
            }
        }
        item {
            Text("Precision", style = MaterialTheme.typography.titleLarge)
            FullscreenToggle("Perfect shape snapping", settings.shapeSnapEnabled) { value ->
                viewModel.updateSettings { it.copy(shapeSnapEnabled = value) }
            }
            FullscreenToggle("Mirror symmetry", settings.symmetryEnabled) { value ->
                viewModel.updateSettings { it.copy(symmetryEnabled = value) }
            }
            FullscreenToggle("Perspective guide", settings.perspectiveGridEnabled) { value ->
                viewModel.updateSettings { it.copy(perspectiveGridEnabled = value) }
            }
            FullscreenToggle("Palm rejection", settings.palmRejectionEnabled) { value ->
                viewModel.updateSettings { it.copy(palmRejectionEnabled = value) }
            }
        }
        item {
            Text("References and textures", style = MaterialTheme.typography.titleLarge)
            Button(onClick = onAttach, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PhotoLibrary, null)
                Spacer(Modifier.width(6.dp))
                Text("Add references")
            }
            OutlinedButton(onClick = onTexture, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Palette, null)
                Spacer(Modifier.width(6.dp))
                Text("Add material texture")
            }
            Text("${project.attachments.size} attachment${if (project.attachments.size == 1) "" else "s"}")
        }
        item {
            Text("Journal", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = journalText,
                onValueChange = { journalText = it },
                label = { Text("What did you learn?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            Button(
                onClick = { viewModel.addJournalEntry(journalText); journalText = "" },
                enabled = journalText.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save journal note") }
        }
        item {
            Text("Replay", style = MaterialTheme.typography.titleLarge)
            Slider(value = viewModel.replayProgress, onValueChange = { viewModel.replayProgress = it }, valueRange = 0f..1f)
        }
        item { Spacer(Modifier.height(26.dp)) }
    }
}

@Composable
private fun FullscreenLayerRow(viewModel: KreativViewModel, project: KreativProject, layer: CanvasLayer) {
    val active = layer.id == project.activeLayerId
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { viewModel.selectLayer(layer.id) },
        shape = RoundedCornerShape(14.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.toggleLayerVisibility(layer.id) }) {
                Icon(if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null)
            }
            Text(layer.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { viewModel.toggleLayerLock(layer.id) }) {
                Icon(if (layer.isLocked) Icons.Default.Lock else Icons.Default.LockOpen, null)
            }
            IconButton(onClick = { viewModel.deleteLayer(layer.id) }, enabled = project.layers.size > 1) {
                Icon(Icons.Default.Delete, null)
            }
        }
    }
}

@Composable
private fun FullscreenToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private class FullscreenCanvasFrame(context: Context) : FrameLayout(context) {
    val canvasView = KreativCanvasView(context)
    var onCanvasFailure: (String) -> Unit = {}
    var fillViewport: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    private var failure: String? = null
    private var reported = false
    private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = 36f
    }

    init {
        clipChildren = true
        clipToPadding = true
        addView(canvasView)
        setWillNotDraw(false)
    }

    fun updateProject(project: KreativProject) {
        val old = canvasView.project
        val sizeChanged = old?.widthPx != project.widthPx || old.heightPx != project.heightPx
        canvasView.project = project
        if (sizeChanged) requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        val height = MeasureSpec.getSize(heightMeasureSpec).coerceAtLeast(1)
        setMeasuredDimension(width, height)

        val project = canvasView.project
        val childSize = if (fillViewport && project != null) {
            val scale = max(
                width.toFloat() / project.widthPx.coerceAtLeast(1),
                height.toFloat() / project.heightPx.coerceAtLeast(1),
            )
            val childWidth = ceil(project.widthPx * scale / .9f).toInt().coerceAtLeast(width)
            val childHeight = ceil(project.heightPx * scale / .9f).toInt().coerceAtLeast(height)
            childWidth to childHeight
        } else {
            width to height
        }

        canvasView.measure(
            MeasureSpec.makeMeasureSpec(childSize.first, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(childSize.second, MeasureSpec.EXACTLY),
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val childWidth = canvasView.measuredWidth
        val childHeight = canvasView.measuredHeight
        val childLeft = (measuredWidth - childWidth) / 2
        val childTop = (measuredHeight - childHeight) / 2
        canvasView.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight)
    }

    fun reportFailure(error: Throwable) {
        if (failure != null) return
        failure = error.message ?: error.javaClass.simpleName
        canvasView.visibility = View.INVISIBLE
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        invalidate()
        reportOnce()
    }

    override fun dispatchDraw(canvas: AndroidCanvas) {
        if (failure != null) {
            drawFallback(canvas)
            return
        }
        try {
            super.dispatchDraw(canvas)
        } catch (error: Throwable) {
            reportFailure(error)
            drawFallback(canvas)
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (failure != null) return true
        return try {
            super.dispatchTouchEvent(event)
        } catch (error: Throwable) {
            reportFailure(error)
            true
        }
    }

    private fun drawFallback(canvas: AndroidCanvas) {
        canvas.drawColor(AndroidColor.rgb(24, 18, 32))
        fallbackPaint.textSize = 38f
        canvas.drawText("Canvas safe mode", 42f, 72f, fallbackPaint)
        fallbackPaint.textSize = 25f
        canvas.drawText("The studio stayed open after a device render error.", 42f, 118f, fallbackPaint)
        canvas.drawText("Return to Atelier and reopen this project after updating.", 42f, 156f, fallbackPaint)
    }

    private fun reportOnce() {
        if (reported) return
        reported = true
        post { onCanvasFailure("Canvas entered safe mode instead of closing: ${failure ?: "unknown render error"}") }
    }
}

private val fullscreenTools = listOf(
    ToolType.PEN,
    ToolType.PENCIL,
    ToolType.WATERCOLOR,
    ToolType.CHARCOAL,
    ToolType.MARKER,
    ToolType.ERASER,
    ToolType.LINE,
    ToolType.RECTANGLE,
    ToolType.ELLIPSE,
    ToolType.TRIANGLE,
    ToolType.POLYGON,
    ToolType.STAR,
    ToolType.ARC,
    ToolType.ARROW,
    ToolType.FILL,
    ToolType.SELECT,
    ToolType.TEXT,
)

private val fullscreenColors = listOf(
    0xFF17121FL,
    0xFFFFFFFFL,
    0xFF6E3BC9L,
    0xFFB86CFFL,
    0xFFD7A25FL,
    0xFFB44F64L,
    0xFF4B75B8L,
    0xFF3F8B72L,
    0xFFDB7F45L,
)

private fun ToolType.fullscreenLabel(): String = name.lowercase().replaceFirstChar(Char::uppercase)

private fun String.fullscreenFileName(): String =
    replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifBlank { "KREATIV_Artwork" }

package com.kreativstudio.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Grid4x4
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Rectangle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kreativstudio.app.model.AttachmentKind
import com.kreativstudio.app.model.BrushPreset
import com.kreativstudio.app.model.CanvasLayer
import com.kreativstudio.app.model.KreativProject
import com.kreativstudio.app.model.StrokePoint
import com.kreativstudio.app.model.SyncState
import com.kreativstudio.app.model.ToolType
import com.kreativstudio.app.ui.canvas.KreativCanvasView
import com.kreativstudio.app.ui.theme.LocalKreativTokens
import kotlinx.coroutines.delay

private enum class InspectorPanel { BRUSHES, LAYERS, PRECISION, REFERENCES, JOURNAL, LESSON }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioCanvasScreen(viewModel: KreativViewModel) {
    val project = viewModel.currentProject
    if (project == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ElevatedCard(Modifier.padding(24.dp)) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Brush, null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("No canvas is open", style = MaterialTheme.typography.headlineMedium)
                    Text("Create a project or choose one from the gallery.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { viewModel.createProject("New Artwork") }) { Text("Create canvas") }
                    TextButton(onClick = { viewModel.navigate(StudioScreen.GALLERY) }) { Text("Open gallery") }
                }
            }
        }
        return
    }

    val studioSettings by viewModel.settings.collectAsState()
    LaunchedEffect(project.id, studioSettings.handHealthReminders) {
        if (!studioSettings.handHealthReminders) return@LaunchedEffect
        while (true) {
            delay(30L * 60L * 1000L)
            viewModel.showHandHealthReminder()
        }
    }

    val context = LocalContext.current
    var activePanel by remember { mutableStateOf(InspectorPanel.BRUSHES) }
    var compactSheet by remember { mutableStateOf(false) }
    var renameDialog by remember { mutableStateOf(false) }
    var pendingTextPoint by remember { mutableStateOf<StrokePoint?>(null) }
    var canvasRef by remember { mutableStateOf<KreativCanvasView?>(null) }

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

    Column(Modifier.fillMaxSize()) {
        StudioTopBar(
            project = project,
            viewModel = viewModel,
            onRename = { renameDialog = true },
            onResetView = { canvasRef?.resetView() },
            onRotate = { canvasRef?.rotateCanvas(15f) },
            onAttach = { attachLauncher.launch(arrayOf("image/*", "application/pdf", "video/*", "audio/*")) },
            onExportProject = { exportProjectLauncher.launch("${project.title.safeFileName()}.kreativ.json") },
            onExportPng = { exportPngLauncher.launch("${project.title.safeFileName()}.png") },
        )
        HorizontalDivider()
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 980.dp
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    if (studioSettings.leftHanded) {
                        StudioInspector(
                            viewModel = viewModel,
                            project = project,
                            activePanel = activePanel,
                            onPanel = { activePanel = it },
                            onAttach = { attachLauncher.launch(arrayOf("image/*", "application/pdf", "video/*", "audio/*")) },
                            onTexture = { textureLauncher.launch(arrayOf("image/*")) },
                            modifier = Modifier.width(340.dp).fillMaxHeight(),
                        )
                        VerticalDivider(Modifier.fillMaxHeight())
                    } else {
                        ToolRail(
                            active = viewModel.activeTool,
                            onSelect = { viewModel.activeTool = it },
                            modifier = Modifier.width(92.dp).fillMaxHeight(),
                        )
                        VerticalDivider(Modifier.fillMaxHeight())
                    }
                    CanvasHost(
                        viewModel = viewModel,
                        project = project,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onView = { canvasRef = it },
                        onTextPlacement = { pendingTextPoint = it },
                    )
                    VerticalDivider(Modifier.fillMaxHeight())
                    if (studioSettings.leftHanded) {
                        ToolRail(
                            active = viewModel.activeTool,
                            onSelect = { viewModel.activeTool = it },
                            modifier = Modifier.width(92.dp).fillMaxHeight(),
                        )
                    } else {
                        StudioInspector(
                            viewModel = viewModel,
                            project = project,
                            activePanel = activePanel,
                            onPanel = { activePanel = it },
                            onAttach = { attachLauncher.launch(arrayOf("image/*", "application/pdf", "video/*", "audio/*")) },
                            onTexture = { textureLauncher.launch(arrayOf("image/*")) },
                            modifier = Modifier.width(340.dp).fillMaxHeight(),
                        )
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    ToolStrip(
                        active = viewModel.activeTool,
                        onSelect = { viewModel.activeTool = it },
                        onOpenPanel = { panel -> activePanel = panel; compactSheet = true },
                    )
                    HorizontalDivider()
                    CanvasHost(
                        viewModel = viewModel,
                        project = project,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        onView = { canvasRef = it },
                        onTextPlacement = { pendingTextPoint = it },
                    )
                    CompactBrushControls(viewModel, onMore = { compactSheet = true })
                }
            }
        }
    }

    if (compactSheet) {
        ModalBottomSheet(onDismissRequest = { compactSheet = false }) {
            StudioInspector(
                viewModel = viewModel,
                project = project,
                activePanel = activePanel,
                onPanel = { activePanel = it },
                onAttach = { attachLauncher.launch(arrayOf("image/*", "application/pdf", "video/*", "audio/*")) },
                onTexture = { textureLauncher.launch(arrayOf("image/*")) },
                modifier = Modifier.fillMaxWidth().fillMaxHeight(.82f),
            )
        }
    }

    pendingTextPoint?.let { point ->
        var text by remember(point) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pendingTextPoint = null },
            title = { Text("Add text") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Text will be placed where you tapped. Use the Select tool afterward to move it.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Artwork text") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Size ${viewModel.brushWidth.toInt()} px", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = viewModel.brushWidth.coerceIn(14f, 360f),
                        onValueChange = { viewModel.brushWidth = it },
                        valueRange = 14f..360f,
                    )
                    ColorPalette(viewModel)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addText(text, point)
                        pendingTextPoint = null
                    },
                    enabled = text.isNotBlank(),
                ) { Text("Place text") }
            },
            dismissButton = { TextButton(onClick = { pendingTextPoint = null }) { Text("Cancel") } },
        )
    }

    if (renameDialog) {
        var title by remember(project.id) { mutableStateOf(project.title) }
        AlertDialog(
            onDismissRequest = { renameDialog = false },
            title = { Text("Rename project") },
            text = { OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Project title") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { Button(onClick = { viewModel.renameProject(title); renameDialog = false }) { Text("Save title") } },
            dismissButton = { TextButton(onClick = { renameDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StudioTopBar(
    project: KreativProject,
    viewModel: KreativViewModel,
    onRename: () -> Unit,
    onResetView: () -> Unit,
    onRotate: () -> Unit,
    onAttach: () -> Unit,
    onExportProject: () -> Unit,
    onExportPng: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = { viewModel.navigate(StudioScreen.HOME) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Atelier") }
        TextButton(onClick = onRename) {
            Column(horizontalAlignment = Alignment.Start) {
                Text(project.title, style = MaterialTheme.typography.titleMedium)
                Text("${project.widthPx} × ${project.heightPx}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = viewModel::undo) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }
        IconButton(onClick = viewModel::redo) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo") }
        IconButton(onClick = viewModel::saveNow) { Icon(Icons.Default.Save, "Save locally") }
        IconButton(onClick = viewModel::syncCurrentProject) { Icon(Icons.Default.CloudSync, "Sync") }
        IconButton(onClick = onResetView) { Icon(Icons.Default.Refresh, "Reset canvas view") }
        IconButton(onClick = onRotate) { Icon(Icons.Default.RotateRight, "Rotate canvas") }
        IconButton(onClick = onAttach) { Icon(Icons.Default.PhotoLibrary, "Add reference") }
        OutlinedButton(onClick = onExportProject) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(5.dp)); Text("Project") }
        OutlinedButton(onClick = onExportPng) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(5.dp)); Text("PNG") }
        CloudStateBadge(project.syncState == SyncState.SYNCED)
        AssistChip(onClick = {}, label = { Text(viewModel.inputStatus) })
    }
}

@Composable
private fun CanvasHost(
    viewModel: KreativViewModel,
    project: KreativProject,
    modifier: Modifier,
    onView: (KreativCanvasView) -> Unit,
    onTextPlacement: (StrokePoint) -> Unit,
) {
    val settings by viewModel.settings.collectAsState()
    AndroidView(
        modifier = modifier.background(LocalKreativTokens.current.canvasChrome),
        factory = { context ->
            KreativCanvasView(context).also { view ->
                onView(view)
                view.onElementsFinished = viewModel::addElements
                view.onEraseGesture = viewModel::erase
                view.onFillRequested = viewModel::fillBackground
                view.onTextPlacementRequested = onTextPlacement
                view.onElementTransformed = viewModel::transformElement
                view.onInputStatus = { viewModel.inputStatus = it }
            }
        },
        update = { view ->
            view.project = project
            view.activeTool = viewModel.activeTool
            view.activeColorArgb = viewModel.activeColorArgb
            view.brushWidth = viewModel.brushWidth
            view.brushOpacity = viewModel.brushOpacity
            view.stabilization = viewModel.stabilization
            view.symmetryEnabled = settings.symmetryEnabled
            view.perspectiveGridEnabled = settings.perspectiveGridEnabled
            view.palmRejectionEnabled = settings.palmRejectionEnabled
            view.shapeSnapEnabled = settings.shapeSnapEnabled
            view.replayProgress = viewModel.replayProgress
            view.invalidate()
        },
    )
}

@Composable
private fun ToolRail(active: ToolType, onSelect: (ToolType) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        toolItems.forEach { item -> ToolButton(item, active == item.tool, onSelect) }
    }
}

@Composable
private fun ToolStrip(
    active: ToolType,
    onSelect: (ToolType) -> Unit,
    onOpenPanel: (InspectorPanel) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        toolItems.forEach { item -> ToolButton(item, active == item.tool, onSelect) }
        TextButton(onClick = { onOpenPanel(InspectorPanel.LAYERS) }) { Icon(Icons.Default.Layers, null); Spacer(Modifier.width(4.dp)); Text("Layers") }
        TextButton(onClick = { onOpenPanel(InspectorPanel.PRECISION) }) { Icon(Icons.Default.Grid4x4, null); Spacer(Modifier.width(4.dp)); Text("Precision") }
    }
}

private data class ToolItem(val tool: ToolType, val label: String, val icon: ImageVector)

private val toolItems = listOf(
    ToolItem(ToolType.PEN, "Pen", Icons.Default.Edit),
    ToolItem(ToolType.PENCIL, "Pencil", Icons.Default.Edit),
    ToolItem(ToolType.WATERCOLOR, "Watercolor", Icons.Default.Colorize),
    ToolItem(ToolType.CHARCOAL, "Charcoal", Icons.Default.Brush),
    ToolItem(ToolType.MARKER, "Marker", Icons.Default.Edit),
    ToolItem(ToolType.ERASER, "Eraser", Icons.Default.Remove),
    ToolItem(ToolType.SMUDGE, "Smudge", Icons.Default.MoreHoriz),
    ToolItem(ToolType.LINE, "Line", Icons.Default.Timeline),
    ToolItem(ToolType.RECTANGLE, "Rectangle", Icons.Default.Rectangle),
    ToolItem(ToolType.ELLIPSE, "Ellipse", Icons.Default.Circle),
    ToolItem(ToolType.TRIANGLE, "Triangle", Icons.Default.Grid4x4),
    ToolItem(ToolType.POLYGON, "Polygon", Icons.Default.Grid4x4),
    ToolItem(ToolType.STAR, "Star", Icons.Default.AutoAwesome),
    ToolItem(ToolType.ARC, "Arc", Icons.Default.Timeline),
    ToolItem(ToolType.ARROW, "Arrow", Icons.Default.ArrowForward),
    ToolItem(ToolType.FILL, "Fill", Icons.Default.FormatColorFill),
    ToolItem(ToolType.SELECT, "Select", Icons.Default.OpenWith),
    ToolItem(ToolType.TEXT, "Text", Icons.Default.TextFields),
)


@Composable
private fun ToolButton(item: ToolItem, selected: Boolean, onSelect: (ToolType) -> Unit) {
    Surface(
        modifier = Modifier.width(80.dp).clickable { onSelect(item.tool) },
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(item.icon, null, modifier = Modifier.size(22.dp))
            Text(item.label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun CompactBrushControls(viewModel: KreativViewModel, onMore: () -> Unit) {
    Surface(tonalElevation = 4.dp) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ColorPalette(viewModel, scrollable = false)
            Text("Size ${viewModel.brushWidth.toInt()}", style = MaterialTheme.typography.labelMedium)
            Slider(value = viewModel.brushWidth, onValueChange = { viewModel.brushWidth = it }, valueRange = 1f..140f, modifier = Modifier.width(180.dp))
            TextButton(onClick = onMore) { Icon(Icons.Default.MoreHoriz, null); Spacer(Modifier.width(4.dp)); Text("Studio controls") }
        }
    }
}

@Composable
private fun StudioInspector(
    viewModel: KreativViewModel,
    project: KreativProject,
    activePanel: InspectorPanel,
    onPanel: (InspectorPanel) -> Unit,
    onAttach: () -> Unit,
    onTexture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(MaterialTheme.colorScheme.surface)) {
        val tabs = InspectorPanel.entries
        ScrollableTabRow(selectedTabIndex = tabs.indexOf(activePanel), edgePadding = 6.dp) {
            tabs.forEach { tab ->
                Tab(
                    selected = tab == activePanel,
                    onClick = { onPanel(tab) },
                    text = { Text(tab.name.lowercase().replaceFirstChar(Char::uppercase)) },
                )
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (activePanel) {
                InspectorPanel.BRUSHES -> BrushInspector(viewModel, project, onTexture)
                InspectorPanel.LAYERS -> LayerInspector(viewModel, project)
                InspectorPanel.PRECISION -> PrecisionInspector(viewModel)
                InspectorPanel.REFERENCES -> ReferenceInspector(viewModel, project, onAttach, onTexture)
                InspectorPanel.JOURNAL -> JournalInspector(viewModel, project)
                InspectorPanel.LESSON -> LessonInspector(viewModel, project)
            }
        }
    }
}

@Composable
private fun BrushInspector(viewModel: KreativViewModel, project: KreativProject, onTexture: () -> Unit) {
    Text("Brush library", style = MaterialTheme.typography.headlineMedium)
    project.brushPresets.forEach { preset ->
        BrushPresetRow(preset, selected = viewModel.activeTool == preset.tool && viewModel.brushWidth == preset.width) { viewModel.applyBrush(preset) }
    }
    HorizontalDivider()
    Text("Size ${viewModel.brushWidth.toInt()} px", style = MaterialTheme.typography.titleMedium)
    Slider(value = viewModel.brushWidth, onValueChange = { viewModel.brushWidth = it }, valueRange = 1f..180f)
    Text("Opacity ${"%.0f".format(viewModel.brushOpacity * 100)}%", style = MaterialTheme.typography.titleMedium)
    Slider(value = viewModel.brushOpacity, onValueChange = { viewModel.brushOpacity = it }, valueRange = .05f..1f)
    Text("Stabilization ${"%.0f".format(viewModel.stabilization * 100)}%", style = MaterialTheme.typography.titleMedium)
    Slider(value = viewModel.stabilization, onValueChange = { viewModel.stabilization = it }, valueRange = 0f..0.95f)
    Text("Color", style = MaterialTheme.typography.titleMedium)
    ColorPalette(viewModel)
    var brushName by remember { mutableStateOf("") }
    OutlinedTextField(value = brushName, onValueChange = { brushName = it }, label = { Text("Custom brush name") }, modifier = Modifier.fillMaxWidth())
    Button(onClick = { viewModel.saveBrushPreset(brushName); brushName = "" }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text("Save current brush") }
    OutlinedButton(onClick = onTexture, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(5.dp)); Text("Capture/import material texture") }
}

@Composable
private fun BrushPresetRow(preset: BrushPreset, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Brush, null)
            Column(Modifier.weight(1f)) {
                Text(preset.name, style = MaterialTheme.typography.titleMedium)
                Text("${preset.tool.name.lowercase()} • ${preset.width.toInt()} px", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ColorPalette(viewModel: KreativViewModel, scrollable: Boolean = true) {
    val colors = listOf(
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
    val rowModifier = if (scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier
    Row(rowModifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.forEach { color ->
            val selected = viewModel.activeColorArgb == color
            Surface(
                modifier = Modifier.size(if (selected) 38.dp else 32.dp).clickable { viewModel.activeColorArgb = color },
                shape = CircleShape,
                color = Color(color),
                border = androidx.compose.foundation.BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
            ) {}
        }
    }
}

@Composable
private fun LayerInspector(viewModel: KreativViewModel, project: KreativProject) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Layers", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = viewModel::addLayer) { Icon(Icons.Default.Add, "Add layer") }
    }
    project.layers.asReversed().forEach { layer ->
        LayerRow(layer, active = layer.id == project.activeLayerId, onSelect = { viewModel.selectLayer(layer.id) }, onVisibility = { viewModel.toggleLayerVisibility(layer.id) }, onLock = { viewModel.toggleLayerLock(layer.id) }, onDelete = { viewModel.deleteLayer(layer.id) })
    }
}

@Composable
private fun LayerRow(
    layer: CanvasLayer,
    active: Boolean,
    onSelect: () -> Unit,
    onVisibility: () -> Unit,
    onLock: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        shape = RoundedCornerShape(14.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onVisibility) { Icon(if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null) }
            Text(layer.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onLock) { Icon(if (layer.isLocked) Icons.Default.Lock else Icons.Default.LockOpen, null) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null) }
        }
    }
}

@Composable
private fun PrecisionInspector(viewModel: KreativViewModel) {
    val settings by viewModel.settings.collectAsState()
    Text("Precision Studio", style = MaterialTheme.typography.headlineMedium)
    ToggleRow("Perfect shape snapping", "Lines snap to 15° increments; rectangles, ellipses, triangles, and arrows remain clean.", settings.shapeSnapEnabled) { value -> viewModel.updateSettings { it.copy(shapeSnapEnabled = value) } }
    ToggleRow("Mirror symmetry", "Every completed mark is mirrored across the canvas center line.", settings.symmetryEnabled) { value -> viewModel.updateSettings { it.copy(symmetryEnabled = value) } }
    ToggleRow("Perspective guide", "Two-point horizon and depth grid remain visible without being exported.", settings.perspectiveGridEnabled) { value -> viewModel.updateSettings { it.copy(perspectiveGridEnabled = value) } }
    ToggleRow("Palm rejection", "Detected palm contacts are ignored while the pen is active.", settings.palmRejectionEnabled) { value -> viewModel.updateSettings { it.copy(palmRejectionEnabled = value) } }
    HorizontalDivider()
    Text("Shape tools", style = MaterialTheme.typography.titleLarge)
    listOf(ToolType.LINE, ToolType.RECTANGLE, ToolType.ELLIPSE, ToolType.TRIANGLE, ToolType.POLYGON, ToolType.STAR, ToolType.ARC, ToolType.ARROW).forEach { tool ->
        OutlinedButton(onClick = { viewModel.activeTool = tool }, modifier = Modifier.fillMaxWidth()) { Text(tool.name.lowercase().replaceFirstChar(Char::uppercase)) }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ReferenceInspector(viewModel: KreativViewModel, project: KreativProject, onAttach: () -> Unit, onTexture: () -> Unit) {
    val context = LocalContext.current
    Text("Reference board", style = MaterialTheme.typography.headlineMedium)
    Button(onClick = onAttach, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("Upload or attach references") }
    OutlinedButton(onClick = onTexture, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Palette, null); Spacer(Modifier.width(6.dp)); Text("Add material texture") }
    if (project.attachments.isEmpty()) {
        Text("Images, PDFs, video, audio, textures, and documents can stay attached to this project.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        project.attachments.forEach { attachment ->
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PhotoLibrary, null)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(attachment.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(attachment.kind.name.lowercase(), style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(
                        onClick = {
                            runCatching {
                                val uri = Uri.parse(attachment.uri)
                                val intent = Intent(Intent.ACTION_VIEW)
                                    .setDataAndType(uri, attachment.mimeType ?: "*/*")
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                context.startActivity(Intent.createChooser(intent, "Open attachment"))
                            }.onFailure {
                                viewModel.showMessage("This attachment is saved, but no compatible viewer is available on this device.")
                            }
                        },
                    ) { Icon(Icons.Default.OpenInNew, "Open attachment") }
                    IconButton(onClick = { viewModel.removeAttachment(attachment.id) }) { Icon(Icons.Default.Delete, "Remove attachment") }
                }
            }
        }
    }
}

@Composable
private fun JournalInspector(viewModel: KreativViewModel, project: KreativProject) {
    Text("Art journal", style = MaterialTheme.typography.headlineMedium)
    var note by remember(project.id) { mutableStateOf("") }
    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Thoughts, materials, decisions, or next steps") }, minLines = 3, modifier = Modifier.fillMaxWidth())
    Button(onClick = { viewModel.addJournalEntry(note); note = "" }, enabled = note.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Add journal entry") }
    project.journal.asReversed().forEach { entry ->
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(entry.text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
    HorizontalDivider()
    Text("Studio replay", style = MaterialTheme.typography.titleLarge)
    Slider(value = viewModel.replayProgress, onValueChange = { viewModel.replayProgress = it }, valueRange = 0f..1f)
    Text("Replay ${"%.0f".format(viewModel.replayProgress * 100)}% of marks", style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun LessonInspector(viewModel: KreativViewModel, project: KreativProject) {
    val lesson = viewModel.lessons.firstOrNull { it.id == (viewModel.selectedLessonId ?: project.lessonId) }
    if (lesson == null) {
        Text("No lesson attached", style = MaterialTheme.typography.headlineMedium)
        Text("Choose a course from Learn to open the guided workspace beside the canvas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { viewModel.navigate(StudioScreen.LESSONS) }) { Text("Browse lessons") }
        return
    }
    val step = lesson.steps[viewModel.lessonStepIndex.coerceIn(0, lesson.steps.lastIndex)]
    Text(lesson.title, style = MaterialTheme.typography.headlineMedium)
    AssistChip(onClick = {}, label = { Text("Step ${viewModel.lessonStepIndex + 1} of ${lesson.steps.size}") })
    Text(step.title, style = MaterialTheme.typography.titleLarge)
    Text(step.instruction, style = MaterialTheme.typography.bodyLarge)
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(12.dp)) {
            Text("Checkpoint", style = MaterialTheme.typography.labelLarge)
            Text(step.checkpoint, style = MaterialTheme.typography.bodyLarge)
        }
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = viewModel::previousLessonStep, enabled = viewModel.lessonStepIndex > 0) { Text("Previous") }
        Button(onClick = viewModel::nextLessonStep, enabled = viewModel.lessonStepIndex < lesson.steps.lastIndex) { Text("Complete & next") }
    }
    OutlinedButton(onClick = { viewModel.aiPrompt = "Explain ${step.title} and help me check ${step.checkpoint}"; viewModel.navigate(StudioScreen.MENTOR) }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.AutoAwesome, null)
        Spacer(Modifier.width(6.dp))
        Text("Show me why")
    }
}

private fun String.safeFileName(): String = replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifBlank { "KREATIV_Artwork" }

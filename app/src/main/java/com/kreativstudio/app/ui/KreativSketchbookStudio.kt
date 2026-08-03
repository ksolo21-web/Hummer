@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kreativstudio.app.ui

import android.app.Activity
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kreativstudio.app.model.AppSettings
import com.kreativstudio.app.model.AttachmentKind
import com.kreativstudio.app.model.CanvasLayer
import com.kreativstudio.app.model.KreativProject
import com.kreativstudio.app.model.StrokePoint
import com.kreativstudio.app.model.ToolType
import com.kreativstudio.app.ui.theme.KreativTheme
import kotlinx.coroutines.delay

/**
 * Canvas-first, professional mobile studio. The drawing view is measured to the
 * exact rectangle between two fixed bars; it is never enlarged behind them.
 */
@Composable
fun KreativSketchbookStudioHost(viewModel: KreativViewModel, activity: Activity) {
    val settings by viewModel.settings.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.message) {
        val message = viewModel.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.dismissMessage()
    }

    KreativTheme(settings) {
        Box(Modifier.fillMaxSize().background(Color(0xFF101014))) {
            ProfessionalStudio(viewModel, activity, settings)
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 82.dp),
            )
        }
    }
}

@Composable
private fun ProfessionalStudio(
    viewModel: KreativViewModel,
    activity: Activity,
    settings: AppSettings,
) {
    val project = viewModel.currentProject ?: return
    val context = LocalContext.current
    val window = rememberKreativWindowState(activity)
    val controller = rememberAdaptiveCanvasController()
    var cleanCanvas by remember { mutableStateOf(false) }
    var controlsOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var textPoint by remember { mutableStateOf<StrokePoint?>(null) }

    val addReference = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> viewModel.addAttachments(context, uris, AttachmentKind.REFERENCE) }
    val exportProject = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> if (uri != null) viewModel.exportProject(uri) }
    val exportPng = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { uri -> if (uri != null) viewModel.exportPng(uri) }

    LaunchedEffect(window.signature, cleanCanvas) {
        // Let WindowManager and Compose finish the Fold posture remeasurement first.
        delay(100)
        controller.fit()
    }

    if (cleanCanvas) {
        Box(Modifier.fillMaxSize().background(Color(0xFF101014))) {
            AdaptiveCanvasArea(
                viewModel = viewModel,
                project = project,
                controller = controller,
                modifier = Modifier.fillMaxSize(),
                onTextPlacement = { textPoint = it },
            )
            Surface(
                modifier = Modifier
                    .align(if (settings.leftHanded) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(10.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = .92f),
                tonalElevation = 8.dp,
            ) {
                IconButton(onClick = { cleanCanvas = false }) {
                    Icon(Icons.Default.Visibility, "Show studio controls")
                }
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            StudioTopBar(
                project = project,
                busy = viewModel.isBusy,
                onBack = { viewModel.navigate(StudioScreen.HOME) },
                onRename = { renameOpen = true },
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onSave = viewModel::saveNow,
                onSync = viewModel::syncCurrentProject,
                onFit = controller::fit,
                onRotate = { controller.rotate() },
                onReference = {
                    addReference.launch(arrayOf("image/*", "application/pdf", "video/*", "audio/*"))
                },
                onCleanCanvas = { cleanCanvas = true },
                onMore = { controlsOpen = true },
            )

            // This weighted child receives only the real space between both bars.
            AdaptiveCanvasArea(
                viewModel = viewModel,
                project = project,
                controller = controller,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF101014)),
                onTextPlacement = { textPoint = it },
            )

            BrushDock(
                viewModel = viewModel,
                singleRow = window.widthDp >= 600,
                onMore = { controlsOpen = true },
            )
        }
    }

    if (controlsOpen) {
        ModalBottomSheet(onDismissRequest = { controlsOpen = false }) {
            StudioControlsSheet(
                viewModel = viewModel,
                project = project,
                settings = settings,
                onReference = {
                    addReference.launch(arrayOf("image/*", "application/pdf", "video/*", "audio/*"))
                },
                onExportProject = {
                    exportProject.launch("${project.title.fileSafe()}.kreativ.json")
                },
                onExportPng = { exportPng.launch("${project.title.fileSafe()}.png") },
                onCleanCanvas = {
                    controlsOpen = false
                    cleanCanvas = true
                },
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
            dismissButton = {
                TextButton(onClick = { textPoint = null }) { Text("Cancel") }
            },
        )
    }

    if (renameOpen) {
        var title by remember(project.id) { mutableStateOf(project.title) }
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("Rename project") },
            text = {
                OutlinedTextField(title, { title = it }, label = { Text("Project title") })
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.renameProject(title)
                    renameOpen = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameOpen = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun StudioTopBar(
    project: KreativProject,
    busy: Boolean,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
    onSync: () -> Unit,
    onFit: () -> Unit,
    onRotate: () -> Unit,
    onReference: () -> Unit,
    onCleanCanvas: () -> Unit,
    onMore: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Atelier")
            }
            Column(
                modifier = Modifier
                    .widthIn(min = 128.dp, max = 220.dp)
                    .clickable(onClick = onRename)
                    .padding(horizontal = 8.dp),
            ) {
                Text(
                    project.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${project.widthPx} × ${project.heightPx}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyRow(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item { ActionIcon(Icons.AutoMirrored.Filled.Undo, "Undo", onUndo) }
                item { ActionIcon(Icons.AutoMirrored.Filled.Redo, "Redo", onRedo) }
                item { ActionIcon(Icons.Default.Save, "Save", onSave) }
                item {
                    IconButton(onClick = onSync, enabled = !busy) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.CloudSync, "Sync")
                    }
                }
                item { ActionIcon(Icons.Default.Refresh, "Frame canvas", onFit) }
                item { ActionIcon(Icons.AutoMirrored.Filled.RotateRight, "Rotate canvas", onRotate) }
                item { ActionIcon(Icons.Default.PhotoLibrary, "Add reference", onReference) }
                item { ActionIcon(Icons.Default.VisibilityOff, "Clean canvas", onCleanCanvas) }
                item { ActionIcon(Icons.Default.MoreHoriz, "More controls", onMore) }
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun ActionIcon(
    image: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    action: () -> Unit,
) {
    IconButton(onClick = action) { Icon(image, description) }
}

@Composable
private fun BrushDock(
    viewModel: KreativViewModel,
    singleRow: Boolean,
    onMore: () -> Unit,
) {
    HorizontalDivider()
    Surface(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
    ) {
        if (singleRow) {
            Row(
                modifier = Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ColorDot(viewModel.activeColorArgb)
                ToolStrip(viewModel, Modifier.weight(1f))
                Text("${viewModel.brushWidth.toInt()} px")
                Slider(
                    value = viewModel.brushWidth,
                    onValueChange = { viewModel.brushWidth = it },
                    valueRange = 1f..180f,
                    modifier = Modifier.width(180.dp),
                )
                ActionIcon(Icons.Default.MoreHoriz, "Controls", onMore)
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                ToolStrip(viewModel, Modifier.fillMaxWidth())
                Row(
                    modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ColorDot(viewModel.activeColorArgb)
                    Text("${viewModel.brushWidth.toInt()} px")
                    Slider(
                        value = viewModel.brushWidth,
                        onValueChange = { viewModel.brushWidth = it },
                        valueRange = 1f..180f,
                        modifier = Modifier.weight(1f),
                    )
                    ActionIcon(Icons.Default.MoreHoriz, "Controls", onMore)
                }
            }
        }
    }
}

@Composable
private fun ToolStrip(viewModel: KreativViewModel, modifier: Modifier) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(quickTools, key = { it.name }) { tool ->
            FilterChip(
                selected = viewModel.activeTool == tool,
                onClick = { viewModel.activeTool = tool },
                label = { Text(tool.name.lowercase().replaceFirstChar(Char::uppercase)) },
                leadingIcon = {
                    Icon(
                        if (tool == ToolType.TEXT) Icons.Default.TextFields else Icons.Default.Edit,
                        null,
                        Modifier.size(17.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun ColorDot(argb: Long) {
    Surface(
        modifier = Modifier.size(31.dp),
        shape = CircleShape,
        color = Color(argb),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {}
}

@Composable
private fun StudioControlsSheet(
    viewModel: KreativViewModel,
    project: KreativProject,
    settings: AppSettings,
    onReference: () -> Unit,
    onExportProject: () -> Unit,
    onExportPng: () -> Unit,
    onCleanCanvas: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(.9f)
            .imePadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text("Studio controls", style = MaterialTheme.typography.headlineMedium) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCleanCanvas, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.VisibilityOff, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Clean canvas")
                }
                OutlinedButton(onClick = onReference, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PhotoLibrary, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Reference")
                }
            }
        }
        item {
            Text("Color", style = MaterialTheme.typography.titleLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(palette) { value ->
                    val selected = viewModel.activeColorArgb == value
                    Surface(
                        modifier = Modifier
                            .size(if (selected) 46.dp else 40.dp)
                            .clickable { viewModel.activeColorArgb = value },
                        shape = CircleShape,
                        color = Color(value),
                        border = BorderStroke(
                            if (selected) 3.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                        ),
                    ) {}
                }
            }
        }
        item {
            Text("Brush response", style = MaterialTheme.typography.titleLarge)
            Text("Opacity ${(viewModel.brushOpacity * 100).toInt()}%")
            Slider(
                viewModel.brushOpacity,
                { viewModel.brushOpacity = it },
                valueRange = .05f..1f,
            )
            Text("Stabilization ${(viewModel.stabilization * 100).toInt()}%")
            Slider(
                viewModel.stabilization,
                { viewModel.stabilization = it },
                valueRange = 0f..0.95f,
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Layers", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                ActionIcon(Icons.Default.Add, "Add layer", viewModel::addLayer)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                project.layers.asReversed().forEach { layer ->
                    LayerRow(viewModel, project, layer)
                }
            }
        }
        item {
            Text("Precision", style = MaterialTheme.typography.titleLarge)
            SettingToggle("Perfect shape snapping", settings.shapeSnapEnabled) { enabled ->
                viewModel.updateSettings { it.copy(shapeSnapEnabled = enabled) }
            }
            SettingToggle("Mirror symmetry", settings.symmetryEnabled) { enabled ->
                viewModel.updateSettings { it.copy(symmetryEnabled = enabled) }
            }
            SettingToggle("Perspective guide", settings.perspectiveGridEnabled) { enabled ->
                viewModel.updateSettings { it.copy(perspectiveGridEnabled = enabled) }
            }
            SettingToggle("Palm rejection", settings.palmRejectionEnabled) { enabled ->
                viewModel.updateSettings { it.copy(palmRejectionEnabled = enabled) }
            }
        }
        item {
            Text("Export", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onExportProject, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Download, null)
                    Spacer(Modifier.width(5.dp))
                    Text("Project")
                }
                OutlinedButton(onClick = onExportPng, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Download, null)
                    Spacer(Modifier.width(5.dp))
                    Text("PNG")
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun LayerRow(
    viewModel: KreativViewModel,
    project: KreativProject,
    layer: CanvasLayer,
) {
    val active = layer.id == project.activeLayerId
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { viewModel.selectLayer(layer.id) },
        shape = RoundedCornerShape(14.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.toggleLayerVisibility(layer.id) }) {
                Icon(if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null)
            }
            Text(layer.name, modifier = Modifier.weight(1f))
            IconButton(onClick = { viewModel.toggleLayerLock(layer.id) }) {
                Icon(if (layer.isLocked) Icons.Default.Lock else Icons.Default.LockOpen, null)
            }
            IconButton(
                onClick = { viewModel.deleteLayer(layer.id) },
                enabled = project.layers.size > 1,
            ) { Icon(Icons.Default.Delete, null) }
        }
    }
}

@Composable
private fun SettingToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private val quickTools = listOf(
    ToolType.PEN,
    ToolType.PENCIL,
    ToolType.WATERCOLOR,
    ToolType.CHARCOAL,
    ToolType.MARKER,
    ToolType.ERASER,
    ToolType.LINE,
    ToolType.RECTANGLE,
    ToolType.ELLIPSE,
    ToolType.SELECT,
    ToolType.TEXT,
)

private val palette = listOf(
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

private fun String.fileSafe(): String =
    replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_')
        .ifBlank { "KREATIV_Artwork" }

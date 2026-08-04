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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pentagon
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Rectangle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kreativstudio.app.model.AppSettings
import com.kreativstudio.app.model.AttachmentKind
import com.kreativstudio.app.model.CanvasLayer
import com.kreativstudio.app.model.KreativProject
import com.kreativstudio.app.model.StrokePoint
import com.kreativstudio.app.model.ToolType
import com.kreativstudio.app.ui.theme.KreativTheme
import kotlinx.coroutines.delay

private val HudSurface = Color(0xFF17131E)
private val HudSurfaceVariant = Color(0xFF30273A)
private val HudOnSurface = Color.White
private val HudMuted = Color(0xFFF3ECF7)
private val HudSelected = Color(0xFF7654D8)
private val HudOutline = Color(0xFFF0E6FF)
private const val HudZIndex = 100f

/**
 * A canvas-first studio inspired by the speed and restraint of a dedicated
 * sketchbook: full-screen artwork, compact floating controls, and no permanent
 * panels stealing the drawing area.
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
            CanvasFirstStudio(viewModel, activity, settings)
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 104.dp),
            )
        }
    }
}

@Composable
private fun CanvasFirstStudio(
    viewModel: KreativViewModel,
    activity: Activity,
    settings: AppSettings,
) {
    val project = viewModel.currentProject ?: return
    val context = LocalContext.current
    val window = rememberKreativWindowState(activity)
    val expanded = window.widthDp >= 600
    val railOnLeft = !settings.leftHanded
    val controller = rememberAdaptiveCanvasController()

    var hudVisible by remember { mutableStateOf(true) }
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

    val viewport = when {
        !hudVisible -> CanvasViewportInsets(8.dp, 8.dp, 8.dp, 8.dp)
        expanded && railOnLeft -> CanvasViewportInsets(86.dp, 76.dp, 24.dp, 88.dp)
        expanded -> CanvasViewportInsets(24.dp, 76.dp, 86.dp, 88.dp)
        else -> CanvasViewportInsets(16.dp, 72.dp, 16.dp, 126.dp)
    }

    LaunchedEffect(window.signature, hudVisible, railOnLeft, expanded) {
        delay(120)
        controller.fit()
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF101014))) {
        AdaptiveCanvasArea(
            viewModel = viewModel,
            project = project,
            controller = controller,
            modifier = Modifier.fillMaxSize().zIndex(0f),
            viewportInsets = viewport,
            onTextPlacement = { textPoint = it },
        )

        if (hudVisible) {
            FloatingTopBar(
                project = project,
                busy = viewModel.isBusy,
                compact = !expanded,
                onBack = { viewModel.navigate(StudioScreen.HOME) },
                onRename = { renameOpen = true },
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onFit = controller::fit,
                onRotate = { controller.rotate() },
                onMore = { controlsOpen = true },
            )

            if (expanded) {
                VerticalToolRail(
                    viewModel = viewModel,
                    modifier = Modifier
                        .align(if (railOnLeft) Alignment.CenterStart else Alignment.CenterEnd)
                        .padding(horizontal = 10.dp),
                )
                BrushPuck(
                    viewModel = viewModel,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 10.dp),
                    onMore = { controlsOpen = true },
                    onHide = { hudVisible = false },
                )
            } else {
                CompactToolDock(
                    viewModel = viewModel,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(8.dp),
                    onMore = { controlsOpen = true },
                    onHide = { hudVisible = false },
                )
            }
        } else {
            Surface(
                modifier = Modifier
                    .zIndex(HudZIndex)
                    .align(if (railOnLeft) Alignment.TopStart else Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(10.dp),
                shape = CircleShape,
                color = HudSurface,
                contentColor = HudOnSurface,
                border = BorderStroke(2.dp, HudOutline.copy(alpha = .78f)),
                tonalElevation = 12.dp,
                shadowElevation = 12.dp,
            ) {
                IconButton(onClick = { hudVisible = true }) {
                    Icon(Icons.Default.Visibility, "Show canvas controls", tint = HudOnSurface)
                }
            }
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
                onSave = viewModel::saveNow,
                onSync = viewModel::syncCurrentProject,
                onExportProject = {
                    exportProject.launch("${project.title.fileSafe()}.kreativ.json")
                },
                onExportPng = { exportPng.launch("${project.title.fileSafe()}.png") },
                onCleanCanvas = {
                    controlsOpen = false
                    hudVisible = false
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
            title = { Text("Rename artwork") },
            text = {
                OutlinedTextField(title, { title = it }, label = { Text("Artwork title") })
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
private fun FloatingTopBar(
    project: KreativProject,
    busy: Boolean,
    compact: Boolean,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onFit: () -> Unit,
    onRotate: () -> Unit,
    onMore: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .zIndex(HudZIndex)
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        color = HudSurface,
        contentColor = HudOnSurface,
        border = BorderStroke(2.dp, HudOutline.copy(alpha = .72f)),
        tonalElevation = 14.dp,
        shadowElevation = 14.dp,
    ) {
        Row(
            modifier = Modifier.height(54.dp).padding(horizontal = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Atelier", onBack)
            Column(
                modifier = Modifier
                    .widthIn(min = if (compact) 78.dp else 140.dp, max = if (compact) 126.dp else 250.dp)
                    .clickable(onClick = onRename)
                    .padding(horizontal = 6.dp),
            ) {
                Text(
                    project.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = HudOnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!compact) {
                    Text(
                        "${project.widthPx} × ${project.heightPx}",
                        style = MaterialTheme.typography.labelSmall,
                        color = HudMuted,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = HudOnSurface,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(4.dp))
            }
            ActionIcon(Icons.AutoMirrored.Filled.Undo, "Undo", onUndo)
            ActionIcon(Icons.AutoMirrored.Filled.Redo, "Redo", onRedo)
            ActionIcon(Icons.Default.Refresh, "Fit artwork", onFit)
            if (!compact) ActionIcon(Icons.AutoMirrored.Filled.RotateRight, "Rotate canvas", onRotate)
            ActionIcon(Icons.Default.MoreHoriz, "Studio controls", onMore)
        }
    }
}

@Composable
private fun VerticalToolRail(viewModel: KreativViewModel, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.zIndex(HudZIndex),
        shape = RoundedCornerShape(26.dp),
        color = HudSurface,
        contentColor = HudOnSurface,
        border = BorderStroke(2.dp, HudOutline.copy(alpha = .72f)),
        tonalElevation = 14.dp,
        shadowElevation = 14.dp,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            railTools.forEach { tool -> ToolIconButton(viewModel, tool) }
        }
    }
}

@Composable
private fun CompactToolDock(
    viewModel: KreativViewModel,
    modifier: Modifier = Modifier,
    onMore: () -> Unit,
    onHide: () -> Unit,
) {
    Surface(
        modifier = modifier.zIndex(HudZIndex).fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = HudSurface,
        contentColor = HudOnSurface,
        border = BorderStroke(2.dp, HudOutline.copy(alpha = .72f)),
        tonalElevation = 14.dp,
        shadowElevation = 14.dp,
    ) {
        Column(Modifier.padding(vertical = 5.dp)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(railTools, key = { it.name }) { tool ->
                    ToolIconButton(viewModel, tool)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ColorDot(viewModel.activeColorArgb, onMore)
                Text(
                    viewModel.activeTool.displayName(),
                    style = MaterialTheme.typography.labelLarge,
                    color = HudOnSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                SizeStepper(viewModel)
                ActionIcon(Icons.Default.MoreHoriz, "More controls", onMore)
                ActionIcon(Icons.Default.VisibilityOff, "Hide controls", onHide)
            }
        }
    }
}

@Composable
private fun BrushPuck(
    viewModel: KreativViewModel,
    modifier: Modifier = Modifier,
    onMore: () -> Unit,
    onHide: () -> Unit,
) {
    Surface(
        modifier = modifier.zIndex(HudZIndex),
        shape = RoundedCornerShape(28.dp),
        color = HudSurface,
        contentColor = HudOnSurface,
        border = BorderStroke(2.dp, HudOutline.copy(alpha = .72f)),
        tonalElevation = 14.dp,
        shadowElevation = 14.dp,
    ) {
        Row(
            modifier = Modifier.height(58.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ColorDot(viewModel.activeColorArgb, onMore)
            Column(Modifier.widthIn(min = 96.dp, max = 190.dp)) {
                Text(
                    viewModel.activeTool.displayName(),
                    style = MaterialTheme.typography.labelLarge,
                    color = HudOnSurface,
                )
                Text(
                    viewModel.activeTool.hudHint(viewModel.brushOpacity),
                    style = MaterialTheme.typography.labelSmall,
                    color = HudMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SizeStepper(viewModel)
            ActionIcon(Icons.Default.MoreHoriz, "More controls", onMore)
            ActionIcon(Icons.Default.VisibilityOff, "Hide controls", onHide)
        }
    }
}

@Composable
private fun SizeStepper(viewModel: KreativViewModel) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = HudSurfaceVariant,
        contentColor = HudOnSurface,
        border = BorderStroke(1.dp, HudOutline.copy(alpha = .6f)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { viewModel.brushWidth = (viewModel.brushWidth - 2f).coerceAtLeast(1f) },
                contentPadding = PaddingValues(horizontal = 9.dp),
            ) { Text("−", color = HudOnSurface) }
            Text(
                "${viewModel.brushWidth.toInt()} px",
                style = MaterialTheme.typography.labelMedium,
                color = HudOnSurface,
                modifier = Modifier.widthIn(min = 46.dp),
                maxLines = 1,
            )
            TextButton(
                onClick = { viewModel.brushWidth = (viewModel.brushWidth + 2f).coerceAtMost(180f) },
                contentPadding = PaddingValues(horizontal = 9.dp),
            ) { Text("+", color = HudOnSurface) }
        }
    }
}

@Composable
private fun ToolIconButton(viewModel: KreativViewModel, tool: ToolType) {
    val selected = viewModel.activeTool == tool
    Surface(
        modifier = Modifier.size(46.dp),
        shape = CircleShape,
        color = if (selected) HudSelected else HudSurfaceVariant,
        contentColor = HudOnSurface,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            HudOutline.copy(alpha = if (selected) .82f else .46f),
        ),
    ) {
        IconButton(
            onClick = {
                viewModel.activeTool = tool
                if (tool == ToolType.SELECT) {
                    viewModel.showMessage("Select / Move: tap a visible stroke, shape, or text, then drag it.")
                }
            },
        ) {
            Icon(
                imageVector = tool.icon(),
                contentDescription = tool.displayName(),
                tint = HudOnSurface,
            )
        }
    }
}

@Composable
private fun ActionIcon(
    image: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    action: () -> Unit,
) {
    Surface(
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        color = HudSurfaceVariant,
        contentColor = HudOnSurface,
        border = BorderStroke(1.dp, HudOutline.copy(alpha = .46f)),
    ) {
        IconButton(onClick = action) {
            Icon(image, description, tint = HudOnSurface)
        }
    }
}

@Composable
private fun ColorDot(argb: Long, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(34.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = Color(argb),
        border = BorderStroke(2.dp, HudOutline),
    ) {}
}

@Composable
private fun StudioControlsSheet(
    viewModel: KreativViewModel,
    project: KreativProject,
    settings: AppSettings,
    onReference: () -> Unit,
    onSave: () -> Unit,
    onSync: () -> Unit,
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
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Text("Studio controls", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Keep the canvas clear. Open only the controls you need.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCleanCanvas, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.VisibilityOff, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Canvas only")
                }
                OutlinedButton(onClick = onReference, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PhotoLibrary, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Reference")
                }
            }
        }
        item {
            Text("Tools", style = MaterialTheme.typography.titleLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(allTools, key = { it.name }) { tool ->
                    FilterChip(
                        selected = viewModel.activeTool == tool,
                        onClick = { viewModel.activeTool = tool },
                        label = { Text(tool.displayName()) },
                        leadingIcon = { Icon(tool.icon(), null, Modifier.size(18.dp)) },
                    )
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
                            .size(if (selected) 48.dp else 42.dp)
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
            Text("Size ${viewModel.brushWidth.toInt()} px")
            Slider(
                value = viewModel.brushWidth,
                onValueChange = { viewModel.brushWidth = it },
                valueRange = 1f..180f,
            )
            Text("Opacity ${(viewModel.brushOpacity * 100).toInt()}%")
            Slider(
                value = viewModel.brushOpacity,
                onValueChange = { viewModel.brushOpacity = it },
                valueRange = .05f..1f,
            )
            Text("Stabilization ${(viewModel.stabilization * 100).toInt()}%")
            Slider(
                value = viewModel.stabilization,
                onValueChange = { viewModel.stabilization = it },
                valueRange = 0f..0.95f,
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Layers", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                ActionIcon(Icons.Default.Add, "Add layer", viewModel::addLayer)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                project.layers.asReversed().forEach { layer -> LayerRow(viewModel, project, layer) }
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
            Text("Project", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.width(5.dp))
                    Text("Save")
                }
                OutlinedButton(onClick = onSync, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.CloudSync, null)
                    Spacer(Modifier.width(5.dp))
                    Text("Sync")
                }
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
            IconButton(onClick = { viewModel.deleteLayer(layer.id) }, enabled = project.layers.size > 1) {
                Icon(Icons.Default.Delete, null)
            }
        }
    }
}

@Composable
private fun SettingToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private val railTools = listOf(
    ToolType.PENCIL,
    ToolType.PEN,
    ToolType.ERASER,
    ToolType.SELECT,
    ToolType.LINE,
    ToolType.RECTANGLE,
    ToolType.ELLIPSE,
    ToolType.TEXT,
)

private val allTools = listOf(
    ToolType.PENCIL,
    ToolType.PEN,
    ToolType.WATERCOLOR,
    ToolType.CHARCOAL,
    ToolType.MARKER,
    ToolType.ERASER,
    ToolType.SMUDGE,
    ToolType.FILL,
    ToolType.SELECT,
    ToolType.LINE,
    ToolType.RECTANGLE,
    ToolType.ELLIPSE,
    ToolType.TRIANGLE,
    ToolType.POLYGON,
    ToolType.STAR,
    ToolType.ARC,
    ToolType.ARROW,
    ToolType.TEXT,
)

private fun ToolType.displayName(): String = when (this) {
    ToolType.SELECT -> "Select / Move"
    else -> name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
}

private fun ToolType.hudHint(opacity: Float): String = when (this) {
    ToolType.SELECT -> "Tap an object • drag to move"
    ToolType.ERASER -> "Drag across marks to erase"
    ToolType.TEXT -> "Tap canvas to place text"
    else -> "Opacity ${(opacity * 100).toInt()}%"
}

private fun ToolType.icon() = when (this) {
    ToolType.PENCIL -> Icons.Default.Edit
    ToolType.PEN -> Icons.Default.BorderColor
    ToolType.WATERCOLOR -> Icons.Default.WaterDrop
    ToolType.CHARCOAL -> Icons.Default.Gesture
    ToolType.MARKER -> Icons.Default.Brush
    ToolType.ERASER -> Icons.Default.CleaningServices
    ToolType.SMUDGE -> Icons.Default.BlurOn
    ToolType.FILL -> Icons.Default.FormatColorFill
    ToolType.SELECT -> Icons.Default.SelectAll
    ToolType.LINE -> Icons.Default.HorizontalRule
    ToolType.RECTANGLE -> Icons.Default.Rectangle
    ToolType.ELLIPSE -> Icons.Default.Circle
    ToolType.TRIANGLE -> Icons.Default.ChangeHistory
    ToolType.POLYGON -> Icons.Default.Pentagon
    ToolType.STAR -> Icons.Default.Star
    ToolType.ARC -> Icons.Default.Timeline
    ToolType.ARROW -> Icons.Default.ArrowForward
    ToolType.TEXT -> Icons.Default.TextFields
}

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
    replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifBlank { "KREATIV_Artwork" }

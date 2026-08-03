package com.kreativstudio.app.ui

import android.app.Activity
import android.content.Context
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Grid4x4
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush as GradientBrush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kreativstudio.app.R
import com.kreativstudio.app.ai.OnDeviceMentorPhase
import com.kreativstudio.app.model.AiProcessingMode
import com.kreativstudio.app.model.AppUser
import com.kreativstudio.app.model.AttachmentKind
import com.kreativstudio.app.model.CanvasLayer
import com.kreativstudio.app.model.KreativProject
import com.kreativstudio.app.model.StrokePoint
import com.kreativstudio.app.model.SyncState
import com.kreativstudio.app.model.ToolType
import com.kreativstudio.app.ui.canvas.KreativCanvasView
import com.kreativstudio.app.ui.theme.KreativTheme
import com.kreativstudio.app.ui.theme.LocalKreativTokens

@Composable
fun KreativAppStable(viewModel: KreativViewModel, activity: Activity) {
    val settings by viewModel.settings.collectAsState()
    val user by viewModel.user.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.message) {
        val message = viewModel.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.dismissMessage()
    }

    KreativTheme(settings) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                if (user == null) {
                    StableWelcomeScreen(
                        busy = viewModel.isBusy,
                        googleConfigured = viewModel.isGoogleConfigured,
                        onGoogle = { viewModel.signInWithGoogle(activity) },
                        onOliviaPreview = viewModel::useOliviaPreview,
                        onGuest = viewModel::useGuestStudio,
                    )
                } else {
                    StableAppShell(viewModel, requireNotNull(user))
                }
            }
        }
    }
}

@Composable
private fun StableWelcomeScreen(
    busy: Boolean,
    googleConfigured: Boolean,
    onGoogle: () -> Unit,
    onOliviaPreview: () -> Unit,
    onGuest: () -> Unit,
) {
    val tokens = LocalKreativTokens.current
    Box(
        Modifier
            .fillMaxSize()
            .background(
                GradientBrush.radialGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = .24f),
                        MaterialTheme.colorScheme.background,
                        Color.Black,
                    ),
                ),
            )
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth().heightIn(max = 760.dp),
            shape = RoundedCornerShape(30.dp),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= 760.dp
                if (wide) {
                    Row(
                        Modifier.fillMaxSize().padding(26.dp),
                        horizontalArrangement = Arrangement.spacedBy(26.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.kreativ_icon_source),
                            contentDescription = "KREATIV Studio owl atelier",
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp)),
                            contentScale = ContentScale.Fit,
                        )
                        StableWelcomeCopy(
                            modifier = Modifier.weight(1f),
                            busy = busy,
                            googleConfigured = googleConfigured,
                            onGoogle = onGoogle,
                            onOliviaPreview = onOliviaPreview,
                            onGuest = onGuest,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        item {
                            Image(
                                painter = painterResource(R.drawable.kreativ_icon_source),
                                contentDescription = "KREATIV Studio owl atelier",
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)),
                                contentScale = ContentScale.Fit,
                            )
                        }
                        item {
                            StableWelcomeCopy(
                                modifier = Modifier.fillMaxWidth(),
                                busy = busy,
                                googleConfigured = googleConfigured,
                                onGoogle = onGoogle,
                                onOliviaPreview = onOliviaPreview,
                                onGuest = onGuest,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StableWelcomeCopy(
    modifier: Modifier,
    busy: Boolean,
    googleConfigured: Boolean,
    onGoogle: () -> Unit,
    onOliviaPreview: () -> Unit,
    onGuest: () -> Unit,
) {
    val tokens = LocalKreativTokens.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("KREATIV Studio", style = MaterialTheme.typography.displayMedium)
        Text("Draw. Paint. Learn. Master.", style = MaterialTheme.typography.titleLarge, color = tokens.gold)
        Text(
            "A private adaptive art studio with precision tools, guided teaching, offline creativity, and an AI mentor that protects the artist's own voice.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider()
        Button(onClick = onGoogle, enabled = !busy && googleConfigured, modifier = Modifier.fillMaxWidth()) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (googleConfigured) "Continue with Google" else "Google sign-in awaits private Firebase keys")
        }
        Button(
            onClick = onOliviaPreview,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = tokens.gold, contentColor = Color(0xFF1C1004)),
        ) {
            Icon(Icons.Default.Lock, null)
            Spacer(Modifier.width(8.dp))
            Text("Open Olivia's private preview")
        }
        TextButton(onClick = onGuest, modifier = Modifier.fillMaxWidth()) { Text("Explore as guest artist") }
    }
}

private data class StableNavItem(val screen: StudioScreen, val label: String, val icon: ImageVector)

private val stableNavItems = listOf(
    StableNavItem(StudioScreen.HOME, "Atelier", Icons.Default.Home),
    StableNavItem(StudioScreen.STUDIO, "Studio", Icons.Default.Brush),
    StableNavItem(StudioScreen.LESSONS, "Learn", Icons.Default.MenuBook),
    StableNavItem(StudioScreen.GALLERY, "Gallery", Icons.Default.Collections),
    StableNavItem(StudioScreen.MENTOR, "Mentor", Icons.Default.AutoAwesome),
    StableNavItem(StudioScreen.SETTINGS, "Settings", Icons.Default.Settings),
)

@Composable
private fun StableAppShell(viewModel: KreativViewModel, user: AppUser) {
    val settings by viewModel.settings.collectAsState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 1200.dp && !settings.focusMode
        if (useRail) {
            Row(Modifier.fillMaxSize()) {
                StableNavigationRail(viewModel)
                VerticalDivider(Modifier.fillMaxHeight())
                StableScreenHost(viewModel, user, Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                StableScreenHost(viewModel, user, Modifier.weight(1f))
                if (!settings.focusMode) StableBottomNavigation(viewModel)
            }
        }
    }
}

@Composable
private fun StableScreenHost(viewModel: KreativViewModel, user: AppUser, modifier: Modifier) {
    Box(modifier) {
        key(viewModel.screen) {
            when (viewModel.screen) {
                StudioScreen.HOME -> HomeScreen(viewModel, user)
                StudioScreen.STUDIO -> StableStudioScreen(viewModel)
                StudioScreen.LESSONS -> LessonsScreen(viewModel)
                StudioScreen.GALLERY -> GalleryScreen(viewModel)
                StudioScreen.MENTOR -> StableMentorScreen(viewModel)
                StudioScreen.SETTINGS -> SettingsScreen(viewModel, user)
            }
        }
    }
}

@Composable
private fun StableNavigationRail(viewModel: KreativViewModel) {
    NavigationRail(
        modifier = Modifier.fillMaxHeight(),
        header = {
            Image(
                painter = painterResource(R.drawable.kreativ_icon_source),
                contentDescription = null,
                modifier = Modifier.padding(10.dp).size(58.dp).clip(CircleShape),
            )
        },
    ) {
        stableNavItems.forEach { item ->
            NavigationRailItem(
                selected = viewModel.screen == item.screen,
                onClick = { viewModel.navigate(item.screen) },
                icon = { Icon(item.icon, null) },
                label = { Text(item.label) },
                alwaysShowLabel = true,
            )
        }
    }
}

@Composable
private fun StableBottomNavigation(viewModel: KreativViewModel) {
    Surface(tonalElevation = 5.dp) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(stableNavItems, key = { it.screen }) { item ->
                val selected = viewModel.screen == item.screen
                TextButton(
                    onClick = { viewModel.navigate(item.screen) },
                    modifier = Modifier.width(104.dp),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(item.icon, null, modifier = Modifier.size(21.dp))
                        Text(item.label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun StableMentorScreen(viewModel: KreativViewModel) {
    val settings by viewModel.settings.collectAsState()
    val onDeviceMentor by viewModel.onDeviceMentorState.collectAsState()
    val advice = viewModel.aiAdvice

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            StableHeader(
                title = "KREATIV Mentor",
                subtitle = "Private, honest coaching that explains why and never changes artwork without approval.",
                icon = Icons.Default.AutoAwesome,
            )
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                            Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.padding(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Ask about your art", style = MaterialTheme.typography.titleLarge)
                            Text(
                                if (settings.aiLocalFirst) "Local-first coaching is active." else "Cloud coaching is active when Firebase AI is configured.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Online + offline teaching engine", fontWeight = FontWeight.SemiBold)
                            Text(onDeviceMentor.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            AssistChip(
                                onClick = viewModel::refreshOnDeviceMentorStatus,
                                label = { Text(onDeviceMentor.phase.stableLabel()) },
                            )
                            if (onDeviceMentor.phase == OnDeviceMentorPhase.DOWNLOADING) {
                                val total = onDeviceMentor.bytesToDownload
                                if (total != null && total > 0L) {
                                    LinearProgressIndicator(
                                        progress = { (onDeviceMentor.bytesDownloaded.toFloat() / total).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                            }
                            if (onDeviceMentor.phase == OnDeviceMentorPhase.DOWNLOADABLE) {
                                OutlinedButton(onClick = viewModel::prepareOnDeviceMentor, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Download, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Download Gemini Nano for offline coaching")
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = viewModel.aiPrompt,
                        onValueChange = { viewModel.aiPrompt = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 7,
                        label = { Text("What would you like help with?") },
                        placeholder = { Text("How can I improve the lighting, anatomy, composition, or watercolor edges?") },
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("Portrait proportions", "Watercolor control", "Perspective check", "Color and light")) { prompt ->
                            AssistChip(onClick = { viewModel.aiPrompt = prompt }, label = { Text(prompt) })
                        }
                    }
                    Button(
                        onClick = viewModel::requestAiAdvice,
                        enabled = !viewModel.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (viewModel.isBusy) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Ask KREATIV Mentor")
                    }
                }
            }
        }
        if (advice != null) {
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(advice.title, style = MaterialTheme.typography.headlineMedium)
                        AssistChip(onClick = {}, label = { Text(advice.processingMode.stableLabel()) })
                        Text(advice.explanation, style = MaterialTheme.typography.bodyLarge)
                        HorizontalDivider()
                        advice.actions.forEachIndexed { index, action ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                    Text("${index + 1}", modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), fontWeight = FontWeight.Bold)
                                }
                                Text(action, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        Text(
                            "AI suggestions are guidance only. Apply changes on a duplicate layer and keep the artist in control.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StableStudioScreen(viewModel: KreativViewModel) {
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

    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    var canvasFrame by remember { mutableStateOf<SafeCanvasFrame?>(null) }
    var controlsOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
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

    Column(Modifier.fillMaxSize()) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item {
                IconButton(onClick = { viewModel.navigate(StudioScreen.HOME) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Atelier")
                }
            }
            item {
                TextButton(onClick = { renameOpen = true }) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(project.title, style = MaterialTheme.typography.titleMedium)
                        Text("${project.widthPx} × ${project.heightPx}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { IconButton(onClick = viewModel::undo) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") } }
            item { IconButton(onClick = viewModel::redo) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo") } }
            item { IconButton(onClick = viewModel::saveNow) { Icon(Icons.Default.Save, "Save locally") } }
            item { IconButton(onClick = viewModel::syncCurrentProject) { Icon(Icons.Default.CloudSync, "Sync") } }
            item { IconButton(onClick = { canvasFrame?.canvasView?.resetView() }) { Icon(Icons.Default.Refresh, "Reset canvas view") } }
            item { IconButton(onClick = { canvasFrame?.canvasView?.rotateCanvas(15f) }) { Icon(Icons.Default.RotateRight, "Rotate canvas") } }
            item {
                IconButton(onClick = { attachLauncher.launch(arrayOf("image/*", "application/pdf", "video/*", "audio/*")) }) {
                    Icon(Icons.Default.PhotoLibrary, "Add reference")
                }
            }
            item {
                OutlinedButton(onClick = { exportProjectLauncher.launch("${project.title.stableFileName()}.kreativ.json") }) {
                    Icon(Icons.Default.Download, null)
                    Spacer(Modifier.width(5.dp))
                    Text("Project")
                }
            }
            item {
                OutlinedButton(onClick = { exportPngLauncher.launch("${project.title.stableFileName()}.png") }) {
                    Icon(Icons.Default.Download, null)
                    Spacer(Modifier.width(5.dp))
                    Text("PNG")
                }
            }
            item { CloudStateBadge(project.syncState == SyncState.SYNCED) }
            item { AssistChip(onClick = {}, label = { Text(viewModel.inputStatus) }) }
        }
        HorizontalDivider()
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth().background(LocalKreativTokens.current.canvasChrome),
            factory = { canvasContext ->
                SafeCanvasFrame(canvasContext).also { frame ->
                    canvasFrame = frame
                    frame.onCanvasFailure = { message -> viewModel.showMessage(message) }
                    frame.canvasView.onElementsFinished = viewModel::addElements
                    frame.canvasView.onEraseGesture = viewModel::erase
                    frame.canvasView.onFillRequested = viewModel::fillBackground
                    frame.canvasView.onTextPlacementRequested = { textPoint = it }
                    frame.canvasView.onElementTransformed = viewModel::transformElement
                    frame.canvasView.onInputStatus = { viewModel.inputStatus = it }
                }
            },
            update = { frame ->
                runCatching {
                    frame.canvasView.project = project
                    frame.canvasView.activeTool = viewModel.activeTool
                    frame.canvasView.activeColorArgb = viewModel.activeColorArgb
                    frame.canvasView.brushWidth = viewModel.brushWidth
                    frame.canvasView.brushOpacity = viewModel.brushOpacity
                    frame.canvasView.stabilization = viewModel.stabilization
                    frame.canvasView.symmetryEnabled = settings.symmetryEnabled
                    frame.canvasView.perspectiveGridEnabled = settings.perspectiveGridEnabled
                    frame.canvasView.palmRejectionEnabled = settings.palmRejectionEnabled
                    frame.canvasView.shapeSnapEnabled = settings.shapeSnapEnabled
                    frame.canvasView.replayProgress = viewModel.replayProgress
                    frame.canvasView.invalidate()
                }.onFailure(frame::reportFailure)
            },
        )
        HorizontalDivider()
        Surface(tonalElevation = 5.dp) {
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(stableTools, key = { it }) { tool ->
                        FilterChip(
                            selected = viewModel.activeTool == tool,
                            onClick = { viewModel.activeTool = tool },
                            label = { Text(tool.stableLabel()) },
                            leadingIcon = { Icon(if (tool == ToolType.TEXT) Icons.Default.TextFields else Icons.Default.Edit, null, modifier = Modifier.size(18.dp)) },
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("${viewModel.brushWidth.toInt()} px", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = viewModel.brushWidth,
                        onValueChange = { viewModel.brushWidth = it },
                        valueRange = 1f..180f,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { controlsOpen = true }) {
                        Icon(Icons.Default.MoreHoriz, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Controls")
                    }
                }
            }
        }
    }

    if (controlsOpen) {
        ModalBottomSheet(onDismissRequest = { controlsOpen = false }) {
            StableStudioControls(
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
            text = { OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Project title") }) },
            confirmButton = {
                Button(onClick = { viewModel.renameProject(title); renameOpen = false }) { Text("Save title") }
            },
            dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StableStudioControls(
    viewModel: KreativViewModel,
    project: KreativProject,
    onAttach: () -> Unit,
    onTexture: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState()
    var journalText by remember { mutableStateOf("") }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(.88f).navigationBarsPadding(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("Studio controls", style = MaterialTheme.typography.headlineMedium) }
        item {
            Text("Color", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                items(stableColors) { color ->
                    val selected = viewModel.activeColorArgb == color
                    Surface(
                        modifier = Modifier.size(if (selected) 42.dp else 36.dp).clickable { viewModel.activeColorArgb = color },
                        shape = CircleShape,
                        color = Color(color),
                        border = androidx.compose.foundation.BorderStroke(
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
                project.layers.asReversed().forEach { layer ->
                    StableLayerRow(viewModel, project, layer)
                }
            }
        }
        item {
            Text("Precision", style = MaterialTheme.typography.titleLarge)
            StableToggle("Perfect shape snapping", settings.shapeSnapEnabled) { value -> viewModel.updateSettings { it.copy(shapeSnapEnabled = value) } }
            StableToggle("Mirror symmetry", settings.symmetryEnabled) { value -> viewModel.updateSettings { it.copy(symmetryEnabled = value) } }
            StableToggle("Perspective guide", settings.perspectiveGridEnabled) { value -> viewModel.updateSettings { it.copy(perspectiveGridEnabled = value) } }
            StableToggle("Palm rejection", settings.palmRejectionEnabled) { value -> viewModel.updateSettings { it.copy(palmRejectionEnabled = value) } }
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
            Text("${project.attachments.size} attachment${if (project.attachments.size == 1) "" else "s"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun StableLayerRow(viewModel: KreativViewModel, project: KreativProject, layer: CanvasLayer) {
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
private fun StableToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StableHeader(title: String, subtitle: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(icon, null, modifier = Modifier.padding(14.dp).size(30.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private class SafeCanvasFrame(context: Context) : FrameLayout(context) {
    val canvasView = KreativCanvasView(context)
    var onCanvasFailure: (String) -> Unit = {}
    private var failure: String? = null
    private var reported = false
    private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = 36f
    }

    init {
        addView(canvasView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        setWillNotDraw(false)
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
        post {
            onCanvasFailure("Canvas entered safe mode instead of closing: ${failure ?: "unknown render error"}")
        }
    }
}

private val stableTools = listOf(
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

private val stableColors = listOf(
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

private fun ToolType.stableLabel(): String = name.lowercase().replaceFirstChar(Char::uppercase)

private fun OnDeviceMentorPhase.stableLabel(): String = when (this) {
    OnDeviceMentorPhase.CHECKING -> "Checking device"
    OnDeviceMentorPhase.AVAILABLE -> "Gemini Nano ready"
    OnDeviceMentorPhase.DOWNLOADABLE -> "Download available"
    OnDeviceMentorPhase.DOWNLOADING -> "Downloading"
    OnDeviceMentorPhase.UNSUPPORTED -> "Built-in offline coach"
    OnDeviceMentorPhase.LOCAL_FALLBACK -> "Built-in offline coach"
    OnDeviceMentorPhase.ERROR -> "Offline coach ready"
}

private fun AiProcessingMode.stableLabel(): String = when (this) {
    AiProcessingMode.ON_DEVICE -> "Processed locally"
    AiProcessingMode.CLOUD -> "Processed online"
    AiProcessingMode.HYBRID -> "Hybrid processing"
}

private fun String.stableFileName(): String =
    replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifBlank { "KREATIV_Artwork" }

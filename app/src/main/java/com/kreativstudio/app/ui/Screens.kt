package com.kreativstudio.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.kreativstudio.app.R
import com.kreativstudio.app.ai.OnDeviceMentorPhase
import com.kreativstudio.app.model.AiProcessingMode
import com.kreativstudio.app.model.AppUser
import com.kreativstudio.app.model.KreativProject
import com.kreativstudio.app.model.Lesson
import com.kreativstudio.app.model.StudioThemeId
import com.kreativstudio.app.model.SyncState
import com.kreativstudio.app.ui.theme.LocalKreativTokens
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(viewModel: KreativViewModel, user: AppUser) {
    val projects by viewModel.projects.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val progress by viewModel.lessonProgress.collectAsState()
    var newProject by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importProject(uri)
    }
    val tokens = LocalKreativTokens.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            BoxWithConstraints {
                val wide = maxWidth >= 720.dp
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        GreetingCard(user, settings.fromKalebMessage, viewModel.cloudAccessAvailable, Modifier.weight(1.35f))
                        Image(
                            painter = painterResource(R.drawable.kreativ_icon_source),
                            contentDescription = "KREATIV Studio",
                            modifier = Modifier
                                .weight(.65f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(26.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                } else {
                    GreetingCard(user, settings.fromKalebMessage, viewModel.cloudAccessAvailable, Modifier.fillMaxWidth())
                }
            }
        }
        item {
            SectionHeading("Quick start", "Everything begins in the same continuous studio.")
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                QuickAction("New canvas", Icons.Default.Add, tokens.glow) { newProject = true }
                QuickAction("Import project", Icons.Default.FolderOpen, tokens.gold) {
                    importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
                }
                QuickAction("Start lesson", Icons.Default.School, MaterialTheme.colorScheme.tertiary) {
                    viewModel.navigate(StudioScreen.LESSONS)
                }
                QuickAction("Ask Mentor", Icons.Default.AutoAwesome, MaterialTheme.colorScheme.primary) {
                    viewModel.navigate(StudioScreen.MENTOR)
                }
            }
        }
        item {
            SectionHeading("Continue creating", if (projects.isEmpty()) "Your first canvas will appear here." else "Local autosave protects every mark.")
        }
        if (projects.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Default.Brush,
                    title = "Your studio is waiting",
                    body = "Create a canvas, choose a lesson, or import a .kreativ project. The same project can hold layers, references, journal notes, custom brushes, and lesson progress.",
                    action = "Create first canvas",
                    onAction = { newProject = true },
                )
            }
        } else {
            items(projects.take(6), key = { it.id }) { project ->
                ProjectRow(project, onOpen = { viewModel.openProject(project.id) })
            }
        }
        item {
            SectionHeading("Learning path", "Portraits, people, watercolor, perspective, color, and more.")
        }
        items(viewModel.lessons.take(3), key = { it.id }) { lesson ->
            val lessonProgress = progress.firstOrNull { it.lessonId == lesson.id }
            LessonCard(
                lesson = lesson,
                completed = lessonProgress?.completedSteps ?: 0,
                onStart = { viewModel.startLesson(lesson.id) },
            )
        }
        item {
            FromKalebCard(settings.fromKalebMessage)
        }
    }

    if (newProject) {
        NewProjectDialog(
            onDismiss = { newProject = false },
            onCreate = { title, width, height, bg ->
                newProject = false
                viewModel.createProject(title, width, height, bg)
            },
        )
    }
}

@Composable
private fun GreetingCard(user: AppUser, fromKaleb: String, cloudAvailable: Boolean, modifier: Modifier) {
    val tokens = LocalKreativTokens.current
    ElevatedCard(modifier, shape = RoundedCornerShape(28.dp)) {
        Column(
            modifier = Modifier
                .background(
                    GradientBrush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = .92f),
                            MaterialTheme.colorScheme.surface,
                        )
                    )
                )
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = CircleShape, color = tokens.gold.copy(alpha = .18f)) {
                    Icon(
                        if (user.isOliviaOwner) Icons.Default.Favorite else Icons.Default.Palette,
                        contentDescription = null,
                        tint = tokens.gold,
                        modifier = Modifier.padding(12.dp).size(28.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (user.isOliviaOwner) "Welcome home, Olivia." else "Welcome, ${user.displayName}.",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        if (user.isOliviaOwner) "Your Royal Owl atelier is ready." else "Your creative studio is ready.",
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.gold,
                    )
                }
            }
            CloudStateBadge(synced = !user.isLocalPreview && cloudAvailable)
            Text(
                if (user.isOliviaOwner) "Take your time, trust your hand, and create something only you could make."
                else "Create freely, learn deliberately, and keep every step of the journey.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            Text("From Kaleb", style = MaterialTheme.typography.labelLarge, color = tokens.gold)
            Text(fromKaleb, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.width(160.dp).clickable(onClick = onClick),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = CircleShape, color = color.copy(alpha = .18f)) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(10.dp).size(24.dp))
            }
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ProjectRow(project: KreativProject, onOpen: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color(project.backgroundArgb),
                tonalElevation = 3.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Brush, contentDescription = null, tint = Color(project.backgroundArgb).readableForeground())
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(project.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    "${project.widthPx} × ${project.heightPx} • ${project.layers.size} layers • ${project.elements.size} marks",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Updated ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(project.updatedAt))}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                if (project.syncState == SyncState.SYNCED) Icons.Outlined.CloudDone else Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = if (project.syncState == SyncState.SYNCED) LocalKreativTokens.current.success else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (String, Int, Int, Long) -> Unit,
) {
    var title by remember { mutableStateOf("Olivia's New Artwork") }
    var width by remember { mutableStateOf("2048") }
    var height by remember { mutableStateOf("2048") }
    var background by remember { mutableStateOf(0xFFFFFFFFL) }
    val presets = listOf(
        "Square" to (2048 to 2048),
        "Portrait" to (1800 to 2400),
        "Landscape" to (2560 to 1600),
        "Print" to (3000 to 4000),
    )
    val backgrounds = listOf(0xFFFFFFFFL, 0xFFF7F0E4L, 0xFF17131EL, 0xFF0C1119L, 0xFFE8E5D9L)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New canvas") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Project title") }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { (label, size) ->
                        AssistChip(onClick = { width = size.first.toString(); height = size.second.toString() }, label = { Text(label) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = width, onValueChange = { width = it.filter(Char::isDigit) }, label = { Text("Width px") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = height, onValueChange = { height = it.filter(Char::isDigit) }, label = { Text("Height px") }, modifier = Modifier.weight(1f))
                }
                Text("Paper/background", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    backgrounds.forEach { value ->
                        Surface(
                            modifier = Modifier.size(52.dp).clickable { background = value },
                            shape = CircleShape,
                            color = Color(value),
                            border = if (background == value) androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        ) {}
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onCreate(
                    title,
                    width.toIntOrNull()?.coerceIn(256, 8192) ?: 2048,
                    height.toIntOrNull()?.coerceIn(256, 8192) ?: 2048,
                    background,
                )
            }) { Text("Create canvas") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun LessonsScreen(viewModel: KreativViewModel) {
    val progress by viewModel.lessonProgress.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScreenHeader(
                title = "KREATIV Mentor Academy",
                subtitle = "Guided courses move from demonstration to independent practice without taking over the artist's hand.",
                icon = Icons.Default.MenuBook,
            )
        }
        items(viewModel.lessons, key = { it.id }) { lesson ->
            LessonCard(
                lesson = lesson,
                completed = progress.firstOrNull { it.lessonId == lesson.id }?.completedSteps ?: 0,
                onStart = { viewModel.startLesson(lesson.id) },
            )
        }
    }
}

@Composable
private fun LessonCard(lesson: Lesson, completed: Int, onStart: () -> Unit) {
    val fraction = if (lesson.steps.isEmpty()) 0f else completed.toFloat() / lesson.steps.size
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.padding(11.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(Modifier.weight(1f)) {
                    Text(lesson.title, style = MaterialTheme.typography.titleLarge)
                    Text(lesson.subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text("${lesson.minutes} min") })
                if (lesson.offlineAvailable) AssistChip(onClick = {}, label = { Text("Available offline") })
            }
            LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Text("$completed of ${lesson.steps.size} checkpoints • Difficulty ${lesson.difficulty}/5", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (completed > 0) "Continue lesson" else "Begin lesson")
            }
        }
    }
}

@Composable
fun GalleryScreen(viewModel: KreativViewModel) {
    val projects by viewModel.projects.collectAsState()
    var deleteId by remember { mutableStateOf<String?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) viewModel.importProject(uri) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScreenHeader("Gallery & portfolio", "Every project stays local-first, versionable, exportable, and ready to sync.", Icons.Default.GridView)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { viewModel.createProject("New Artwork") }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("New") }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) { Icon(Icons.Default.Upload, null); Spacer(Modifier.width(6.dp)); Text("Import .kreativ") }
            }
        }
        if (projects.isEmpty()) {
            item { EmptyStateCard(Icons.Default.Collections, "No projects yet", "Create or import a project and it will appear here.", "Create artwork") { viewModel.createProject("New Artwork") } }
        } else {
            items(projects, key = { it.id }) { project ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProjectRow(project) { viewModel.openProject(project.id) }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.openProject(project.id) }) { Text("Open") }
                            OutlinedButton(onClick = { viewModel.duplicateProject(project.id) }) { Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(4.dp)); Text("Duplicate") }
                            OutlinedButton(onClick = { deleteId = project.id }) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(4.dp)); Text("Delete") }
                        }
                    }
                }
            }
        }
    }
    if (deleteId != null) {
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Delete project?") },
            text = { Text("This removes the local project. Export a copy first when you need to preserve it.") },
            confirmButton = { Button(onClick = { viewModel.deleteProject(requireNotNull(deleteId)); deleteId = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun MentorScreen(viewModel: KreativViewModel) {
    val settings by viewModel.settings.collectAsState()
    val onDeviceMentor by viewModel.onDeviceMentorState.collectAsState()
    val advice = viewModel.aiAdvice
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader("KREATIV Mentor", "Private, honest coaching that explains why and never changes artwork without approval.", Icons.Default.AutoAwesome)
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Online + offline teaching engine", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        onDeviceMentor.detail,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                AssistChip(
                                    onClick = viewModel::refreshOnDeviceMentorStatus,
                                    label = { Text(onDeviceMentor.phase.label()) },
                                )
                            }
                            if (onDeviceMentor.phase == OnDeviceMentorPhase.DOWNLOADING) {
                                val total = onDeviceMentor.bytesToDownload
                                if (total != null && total > 0L) {
                                    LinearProgressIndicator(
                                        progress = { (onDeviceMentor.bytesDownloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                            }
                            if (onDeviceMentor.phase == OnDeviceMentorPhase.DOWNLOADABLE) {
                                OutlinedButton(
                                    onClick = viewModel::prepareOnDeviceMentor,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null)
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
                        minLines = 4,
                        label = { Text("What would you like help with?") },
                        placeholder = { Text("How can I improve the lighting, anatomy, composition, or watercolor edges?") },
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Portrait proportions", "Watercolor control", "Perspective check", "Color and light").forEach { prompt ->
                            AssistChip(onClick = { viewModel.aiPrompt = prompt }, label = { Text(prompt) })
                        }
                    }
                    Button(onClick = { viewModel.requestAiAdvice() }, enabled = !viewModel.isBusy, modifier = Modifier.fillMaxWidth()) {
                        if (viewModel.isBusy) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Ask KREATIV Mentor")
                    }
            }
        }
        if (advice != null) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(advice.title, style = MaterialTheme.typography.headlineMedium)
                        AssistChip(onClick = {}, label = { Text(advice.processingMode.label()) })
                        Text(advice.explanation, style = MaterialTheme.typography.bodyLarge)
                        HorizontalDivider()
                        advice.actions.forEachIndexed { index, action ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                    Text("${index + 1}", modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), fontWeight = FontWeight.Bold)
                                }
                                Text(action, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            }
                        }
                        Text(
                            "AI suggestions are guidance only. Apply changes on a duplicate layer and keep the artist in control.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: KreativViewModel, user: AppUser) {
    val settings by viewModel.settings.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { ScreenHeader("Studio settings", "Themes, accessibility, pen behavior, privacy, AI routing, and Olivia's owner experience.", Icons.Default.Settings) }
        item {
            SettingsSection("Dark owl themes") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioThemeId.entries.forEach { theme ->
                        val selected = settings.themeId == theme
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.setTheme(theme) },
                            label = { Text(theme.displayName()) },
                            leadingIcon = { Icon(Icons.Default.Palette, null) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        item {
            SettingsSection("Readability and adaptive layout") {
                SettingSwitch("High-contrast text protection", "Ensures readable foreground colors and protected surfaces over artwork.", settings.highContrastText) {
                    viewModel.updateSettings { s -> s.copy(highContrastText = it) }
                }
                Text("Text scale ${"%.0f".format(settings.textScale * 100)}%", style = MaterialTheme.typography.titleMedium)
                Slider(value = settings.textScale, onValueChange = { value -> viewModel.updateSettings { it.copy(textScale = value) } }, valueRange = .85f..1.5f)
                Text("All long screens scroll vertically. Tool strips and tabs scroll horizontally. Critical labels are never forced into fixed-height containers.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SettingsSection("Pen and precision") {
                SettingSwitch("Palm rejection", "Reject detected palm contacts while an active pen is drawing.", settings.palmRejectionEnabled) { value -> viewModel.updateSettings { it.copy(palmRejectionEnabled = value) } }
                SettingSwitch("Perfect shape snapping", "Snap lines to clean angles and preserve editable geometric intent.", settings.shapeSnapEnabled) { value -> viewModel.updateSettings { it.copy(shapeSnapEnabled = value) } }
                SettingSwitch("Left-handed studio", "Moves docked controls away from the drawing hand.", settings.leftHanded) { value -> viewModel.updateSettings { it.copy(leftHanded = value) } }
                SettingSwitch("Hand-health reminders", "Offers optional stretch and pacing reminders during long sessions.", settings.handHealthReminders) { value -> viewModel.updateSettings { it.copy(handHealthReminders = value) } }
            }
        }
        item {
            SettingsSection("AI and privacy") {
                SettingSwitch("Local AI first", "Use offline coaching before requesting cloud intelligence.", settings.aiLocalFirst) { value -> viewModel.updateSettings { it.copy(aiLocalFirst = value) } }
                Text("Online AI changes are preview-only and must never overwrite artwork silently.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SettingsSection("Account and cloud") {
                Text(user.displayName, style = MaterialTheme.typography.titleLarge)
                user.email?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                AssistChip(onClick = {}, label = { Text(if (user.isOliviaOwner) "Olivia owner profile" else if (user.isLocalPreview) "Local preview" else "Google account") }, leadingIcon = { Icon(Icons.Default.Lock, null) })
                Text(
                    if (viewModel.isGoogleConfigured) "Google Credential Manager and Firebase Authentication are configured."
                    else "The sign-in implementation is present; private Firebase/OAuth values are intentionally not embedded in source control.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (user.isLocalPreview) "Device-only preview."
                    else if (viewModel.cloudAccessAvailable) "Cloud backup is connected."
                    else "Google sign-in succeeded. Device autosave is active. ${viewModel.cloudFailureDetail ?: "Cloud access has not been verified yet."}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = viewModel::syncAllUserData,
                    enabled = viewModel.cloudAccessAvailable && !viewModel.isBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.CloudSync, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Back up the full studio")
                }
                if (!user.isLocalPreview && !viewModel.cloudAccessAvailable) {
                    OutlinedButton(
                        onClick = viewModel::retryCloudConnection,
                        enabled = !viewModel.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Retry cloud connection")
                    }
                }
                OutlinedButton(onClick = viewModel::signOut) { Text("Sign out") }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String, icon: ImageVector) {
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

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyStateCard(icon: ImageVector, title: String, body: String, action: String, onAction: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(46.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun FromKalebCard(message: String) {
    val tokens = LocalKreativTokens.current
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .background(GradientBrush.linearGradient(listOf(tokens.gold.copy(alpha = .16f), MaterialTheme.colorScheme.surface)))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("A private note from Kaleb", style = MaterialTheme.typography.titleLarge, color = tokens.gold)
            Text(message, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun StudioThemeId.displayName(): String = name.lowercase().split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
private fun AiProcessingMode.label(): String = when (this) {
    AiProcessingMode.ON_DEVICE -> "Processed locally"
    AiProcessingMode.CLOUD -> "Processed online"
    AiProcessingMode.HYBRID -> "Hybrid processing"
}

private fun OnDeviceMentorPhase.label(): String = when (this) {
    OnDeviceMentorPhase.CHECKING -> "Checking"
    OnDeviceMentorPhase.AVAILABLE -> "Nano ready"
    OnDeviceMentorPhase.DOWNLOADABLE -> "Nano available"
    OnDeviceMentorPhase.DOWNLOADING -> "Downloading"
    OnDeviceMentorPhase.UNSUPPORTED -> "Local coach"
    OnDeviceMentorPhase.LOCAL_FALLBACK -> "Local coach"
    OnDeviceMentorPhase.ERROR -> "Local fallback"
}

private fun Color.readableForeground(): Color {
    val luminance = .299f * red + .587f * green + .114f * blue
    return if (luminance > .54f) Color.Black else Color.White
}

package com.kreativstudio.app.ui

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kreativstudio.app.ai.CanvasAwareMentorEngine
import com.kreativstudio.app.ai.CanvasMentorResult
import com.kreativstudio.app.ai.ProjectBitmapRenderer
import com.kreativstudio.app.ui.theme.KreativTheme
import kotlinx.coroutines.launch

private enum class MentorV2Mode(val title: String, val instruction: String) {
    CRITIQUE("Critique", "Give an honest professional critique and rank the three highest-impact corrections."),
    TEACH("Teach me", "Teach the underlying skill step by step and include one focused drill."),
    FIX("Fix plan", "Give a correction sequence from large structure to small detail."),
    IDEAS("Ideas", "Offer three directions that preserve the artist's voice and existing intent."),
}

private val mentorV2Focuses = listOf(
    "Whole artwork",
    "Portrait & anatomy",
    "Composition",
    "Values & light",
    "Color",
    "Watercolor",
    "Perspective",
    "Line quality",
)

@Composable
fun KreativMentorV2Host(viewModel: KreativViewModel, activity: Activity) {
    val settings by viewModel.settings.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.message) {
        val message = viewModel.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.dismissMessage()
    }

    KreativTheme(settings) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            MentorV2Screen(viewModel, activity)
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
    }
}

@Composable
private fun MentorV2Screen(viewModel: KreativViewModel, activity: Activity) {
    val project = viewModel.currentProject
    val settings by viewModel.settings.collectAsState()
    val deviceState by viewModel.onDeviceMentorState.collectAsState()
    val windowState = rememberKreativWindowState(activity)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var mode by remember { mutableStateOf(MentorV2Mode.CRITIQUE) }
    var focus by remember { mutableStateOf(mentorV2Focuses.first()) }
    var question by remember { mutableStateOf("Analyze my current artwork and tell me the most important correction to make next.") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CanvasMentorResult?>(null) }

    val previewBitmap = remember(project?.id, project?.updatedAt) {
        project?.let { ProjectBitmapRenderer.render(it, 560) }
    }
    DisposableEffect(previewBitmap) {
        onDispose { previewBitmap?.recycle() }
    }

    val analyze = {
        scope.launch {
            busy = true
            result = null
            val request = buildString {
                appendLine(mode.instruction)
                appendLine("Primary focus: $focus.")
                appendLine("Artist question: ${question.trim()}")
            }
            result = CanvasAwareMentorEngine.analyze(
                project = project,
                artistRequest = request,
                preferOnDevice = settings.aiLocalFirst,
            )
            busy = false
            listState.animateScrollToItem(if (windowState.isExpanded) 4 else 6)
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Surface(Modifier.fillMaxWidth().statusBarsPadding(), tonalElevation = 8.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.navigate(StudioScreen.HOME) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Atelier")
                }
                Column(Modifier.weight(1f)) {
                    Text("KREATIV Mentor", style = MaterialTheme.typography.titleLarge)
                    Text("Canvas-aware teacher and critic", style = MaterialTheme.typography.labelMedium)
                }
                if (project != null) {
                    TextButton(onClick = { viewModel.navigate(StudioScreen.STUDIO) }) {
                        Icon(Icons.Default.Brush, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Studio")
                    }
                }
            }
        }
        HorizontalDivider()

        if (windowState.isExpanded) {
            Row(Modifier.fillMaxSize().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item { MentorV2Hero(project?.title) }
                    item { MentorV2Controls(mode, { mode = it }, focus, { focus = it }) }
                    item { MentorV2Question(question, { question = it }, busy, analyze) }
                    if (busy) item { MentorBusyCard() }
                    result?.let { response -> item { MentorV2Response(response) } }
                }
                LazyColumn(
                    modifier = Modifier.widthIn(min = 320.dp, max = 410.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item { MentorCanvasPreview(project?.title, previewBitmap?.asImageBitmap()) }
                    item {
                        MentorDeviceStatus(
                            detail = deviceState.detail,
                            localFirst = settings.aiLocalFirst,
                            onRefresh = viewModel::refreshOnDeviceMentorStatus,
                            onDownload = viewModel::prepareOnDeviceMentor,
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(14.dp, 14.dp, 14.dp, 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { MentorV2Hero(project?.title) }
                item { MentorCanvasPreview(project?.title, previewBitmap?.asImageBitmap()) }
                item { MentorV2Controls(mode, { mode = it }, focus, { focus = it }) }
                item { MentorV2Question(question, { question = it }, busy, analyze) }
                if (busy) item { MentorBusyCard() }
                result?.let { response -> item { MentorV2Response(response) } }
                item {
                    MentorDeviceStatus(
                        detail = deviceState.detail,
                        localFirst = settings.aiLocalFirst,
                        onRefresh = viewModel::refreshOnDeviceMentorStatus,
                        onDownload = viewModel::prepareOnDeviceMentor,
                    )
                }
            }
        }
    }
}

@Composable
private fun MentorV2Hero(projectTitle: String?) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Your artwork is the lesson", style = MaterialTheme.typography.headlineSmall)
                    Text(projectTitle ?: "Open a canvas for visual analysis", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                "The Mentor now renders the actual project and sends the artwork image with your question. It identifies visible evidence, explains why it matters, and gives a practical next move.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun MentorV2Controls(
    selectedMode: MentorV2Mode,
    onMode: (MentorV2Mode) -> Unit,
    selectedFocus: String,
    onFocus: (String) -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Coaching mode", style = MaterialTheme.typography.titleLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MentorV2Mode.entries) { item ->
                    FilterChip(selectedMode == item, { onMode(item) }, label = { Text(item.title) })
                }
            }
            Text("Focus", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(mentorV2Focuses) { item ->
                    FilterChip(selectedFocus == item, { onFocus(item) }, label = { Text(item) })
                }
            }
        }
    }
}

@Composable
private fun MentorV2Question(
    question: String,
    onQuestion: (String) -> Unit,
    busy: Boolean,
    onAnalyze: () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("What should the Mentor examine?", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = question,
                onValueChange = onQuestion,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 7,
                label = { Text("Question or goal") },
            )
            Button(onClick = onAnalyze, enabled = !busy && question.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.CheckCircle, null)
                Spacer(Modifier.width(7.dp))
                Text(if (busy) "Analyzing real canvas…" else "Analyze current canvas")
            }
        }
    }
}

@Composable
private fun MentorBusyCard() {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Rendering the artwork and asking the local-first multimodal Mentor. The answer will appear directly below this card.")
        }
    }
}

@Composable
private fun MentorV2Response(result: CanvasMentorResult) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(result.title, style = MaterialTheme.typography.headlineSmall)
            AssistChip(
                onClick = {},
                label = { Text(result.sourceLabel) },
                leadingIcon = { Icon(if (result.sawCanvas) Icons.Default.CheckCircle else Icons.Default.Cloud, null, Modifier.size(18.dp)) },
            )
            Text(result.explanation, style = MaterialTheme.typography.bodyLarge)
            HorizontalDivider()
            Text("Next actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            result.actions.forEachIndexed { index, action -> Text("${index + 1}. $action") }
        }
    }
}

@Composable
private fun MentorCanvasPreview(title: String?, image: androidx.compose.ui.graphics.ImageBitmap?) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Canvas being analyzed", style = MaterialTheme.typography.titleLarge)
            if (image == null) {
                Surface(
                    Modifier.fillMaxWidth().height(180.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) { Text("No canvas open") }
                }
            } else {
                Image(
                    bitmap = image,
                    contentDescription = title ?: "Current artwork",
                    modifier = Modifier.fillMaxWidth().height(260.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Fit,
                )
                Text(title ?: "Current artwork", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MentorDeviceStatus(
    detail: String,
    localFirst: Boolean,
    onRefresh: () -> Unit,
    onDownload: () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Mentor engine", style = MaterialTheme.typography.titleLarge)
            AssistChip(onClick = {}, label = { Text(if (localFirst) "Local-first" else "Cloud-first") })
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRefresh, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Refresh")
                }
                OutlinedButton(onClick = onDownload, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Download, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Prepare local")
                }
            }
        }
    }
}

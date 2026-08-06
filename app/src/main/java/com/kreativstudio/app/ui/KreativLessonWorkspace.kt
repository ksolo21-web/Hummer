package com.kreativstudio.app.ui

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.kreativstudio.app.ai.CanvasAwareMentorEngine
import com.kreativstudio.app.ai.CanvasMentorResult
import com.kreativstudio.app.model.KreativProject
import com.kreativstudio.app.model.Lesson
import com.kreativstudio.app.model.LessonCategory
import com.kreativstudio.app.model.LessonMastery
import com.kreativstudio.app.model.LessonStep
import com.kreativstudio.app.model.StrokePoint
import com.kreativstudio.app.model.ToolType
import com.kreativstudio.app.ui.theme.KreativTheme
import kotlinx.coroutines.launch
import kotlin.math.min

@Composable
fun KreativLessonWorkspaceHost(viewModel: KreativViewModel, activity: Activity) {
    val settings by viewModel.settings.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.message) {
        val message = viewModel.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.dismissMessage()
    }

    KreativTheme(settings) {
        Box(Modifier.fillMaxSize().background(Color(0xFF101014))) {
            LessonWorkspace(viewModel, activity)
            SnackbarHost(
                snackbar,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 132.dp),
            )
        }
    }
}

@Composable
private fun LessonWorkspace(viewModel: KreativViewModel, activity: Activity) {
    val project = viewModel.currentProject ?: return
    val lesson = viewModel.lessons.firstOrNull { it.id == project.lessonId }
    if (lesson == null) {
        KreativAdaptiveStudioHost(viewModel, activity)
        return
    }

    val stepIndex = viewModel.lessonStepIndex.coerceIn(0, lesson.steps.lastIndex)
    val step = lesson.steps[stepIndex]
    val windowState = rememberKreativWindowState(activity)
    val controller = rememberAdaptiveCanvasController()
    val scope = rememberCoroutineScope()
    var showGuide by remember { mutableStateOf(false) }
    var compactPanelOpen by remember { mutableStateOf(true) }
    var textPoint by remember { mutableStateOf<StrokePoint?>(null) }
    var checking by remember { mutableStateOf(false) }
    var assessment by remember { mutableStateOf<CanvasMentorResult?>(null) }

    LaunchedEffect(stepIndex) {
        assessment = null
        showGuide = false
        step.recommendedTool?.let { viewModel.activeTool = it }
        step.recommendedBrushWidth?.let { viewModel.brushWidth = it }
    }

    val checkWork: () -> Unit = {
        scope.launch {
            checking = true
            val evaluated = CanvasAwareMentorEngine.analyze(
                project = viewModel.currentProject,
                artistRequest = "Evaluate my current canvas for this lesson step. Identify visible evidence of what is working, what is missing, and the single correction I should make next.",
                preferOnDevice = viewModel.settings.value.aiLocalFirst,
                lessonContext = buildString {
                    append("${lesson.title}, step ${stepIndex + 1}: ${step.title}. ")
                    append("Instruction: ${step.instruction}. Checkpoint: ${step.checkpoint}. ")
                    if (step.whyItMatters.isNotBlank()) append("Why it matters: ${step.whyItMatters}. ")
                    if (step.practice.isNotBlank()) append("Practice assignment: ${step.practice}. ")
                    if (step.commonMistakes.isNotEmpty()) append("Common mistakes: ${step.commonMistakes.joinToString()}")
                },
            )
            assessment = evaluated
            viewModel.recordLessonAssessment(evaluated.mastery)
            checking = false
            compactPanelOpen = true
        }
        Unit
    }

    Column(Modifier.fillMaxSize()) {
        LessonTopBar(
            lesson = lesson,
            stepIndex = stepIndex,
            controller = controller,
            showGuide = showGuide,
            checking = checking,
            onBack = { viewModel.navigate(StudioScreen.LESSONS) },
            onPrevious = viewModel::previousLessonStep,
            onNext = viewModel::nextLessonStep,
            onToggleGuide = { showGuide = !showGuide },
            onCheck = checkWork,
            onOpenMentor = { viewModel.navigate(StudioScreen.MENTOR) },
        )

        if (windowState.isExpanded && !windowState.isTabletop) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                AdaptiveCanvasArea(
                    viewModel = viewModel,
                    project = project,
                    controller = controller,
                    modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF101014)).padding(2.dp),
                    onTextPlacement = { textPoint = it },
                    overlay = {
                        if (showGuide) LessonGuideOverlay(project, lesson.category, stepIndex)
                    },
                )
                LessonInstructionPanel(
                    lesson = lesson,
                    step = step,
                    stepIndex = stepIndex,
                    assessment = assessment,
                    checking = checking,
                    showGuide = showGuide,
                    onToggleGuide = { showGuide = !showGuide },
                    onCheck = checkWork,
                    modifier = Modifier.widthIn(min = 350.dp, max = 430.dp).fillMaxHeight(),
                )
            }
        } else {
            if (compactPanelOpen) {
                LessonInstructionPanel(
                    lesson = lesson,
                    step = step,
                    stepIndex = stepIndex,
                    assessment = assessment,
                    checking = checking,
                    showGuide = showGuide,
                    onToggleGuide = { showGuide = !showGuide },
                    onCheck = checkWork,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    onCollapse = { compactPanelOpen = false },
                )
            } else {
                Surface(
                    Modifier.fillMaxWidth().clickable { compactPanelOpen = true },
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.School, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Step ${stepIndex + 1}: ${step.title}", modifier = Modifier.weight(1f))
                        Text("Show instruction")
                    }
                }
            }
            AdaptiveCanvasArea(
                viewModel = viewModel,
                project = project,
                controller = controller,
                modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFF101014)).padding(2.dp),
                onTextPlacement = { textPoint = it },
                overlay = {
                    if (showGuide) LessonGuideOverlay(project, lesson.category, stepIndex)
                },
            )
        }

        LessonToolBar(viewModel)
    }

    textPoint?.let { point ->
        var text by remember(point) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { textPoint = null },
            title = { Text("Add text") },
            text = { OutlinedTextField(text, { text = it }, label = { Text("Artwork text") }, minLines = 3) },
            confirmButton = {
                Button(onClick = { viewModel.addText(text, point); textPoint = null }, enabled = text.isNotBlank()) {
                    Text("Place text")
                }
            },
            dismissButton = { TextButton(onClick = { textPoint = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LessonTopBar(
    lesson: Lesson,
    stepIndex: Int,
    controller: AdaptiveCanvasController,
    showGuide: Boolean,
    checking: Boolean,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleGuide: () -> Unit,
    onCheck: () -> Unit,
    onOpenMentor: () -> Unit,
) {
    Surface(Modifier.fillMaxWidth().statusBarsPadding(), tonalElevation = 8.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to lessons") }
            Column(Modifier.widthIn(min = 170.dp, max = 300.dp)) {
                Text(lesson.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text("Step ${stepIndex + 1} of ${lesson.steps.size}", style = MaterialTheme.typography.labelSmall)
            }
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item { IconButton(onClick = onPrevious, enabled = stepIndex > 0) { Icon(Icons.Default.ChevronLeft, "Previous step") } }
                item { IconButton(onClick = onNext, enabled = stepIndex < lesson.steps.lastIndex) { Icon(Icons.Default.ChevronRight, "Next step") } }
                item {
                    TextButton(onClick = controller::fit) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.width(3.dp))
                        Text("Fit")
                    }
                }
                item {
                    TextButton(onClick = onToggleGuide) {
                        Icon(if (showGuide) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                        Spacer(Modifier.width(3.dp))
                        Text(if (showGuide) "Hide demo" else "Show me")
                    }
                }
                item {
                    Button(onClick = onCheck, enabled = !checking) {
                        if (checking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.CheckCircle, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Check my work")
                    }
                }
                item { IconButton(onClick = onOpenMentor) { Icon(Icons.Default.AutoAwesome, "Open Mentor") } }
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun LessonInstructionPanel(
    lesson: Lesson,
    step: LessonStep,
    stepIndex: Int,
    assessment: CanvasMentorResult?,
    checking: Boolean,
    showGuide: Boolean,
    onToggleGuide: () -> Unit,
    onCheck: () -> Unit,
    modifier: Modifier,
    onCollapse: (() -> Unit)? = null,
) {
    Surface(modifier, color = MaterialTheme.colorScheme.surface, tonalElevation = 5.dp) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Icon(Icons.Default.School, null, Modifier.padding(9.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Step ${stepIndex + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(step.title, style = MaterialTheme.typography.headlineSmall)
                    }
                    onCollapse?.let { TextButton(onClick = it) { Text("Hide") } }
                }
            }
            if (step.recommendedTool != null || step.recommendedBrushWidth != null) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        step.recommendedTool?.let { tool ->
                            item { AssistChip(onClick = {}, label = { Text("Tool: ${tool.name.lowercase().replaceFirstChar(Char::uppercase)}") }) }
                        }
                        step.recommendedBrushWidth?.let { width ->
                            item { AssistChip(onClick = {}, label = { Text("Brush: ${width.toInt()} px") }) }
                        }
                    }
                }
            }
            item {
                Text("Objective", style = MaterialTheme.typography.titleMedium)
                Text(step.instruction, style = MaterialTheme.typography.bodyLarge)
            }
            if (step.whyItMatters.isNotBlank()) {
                item {
                    LessonTeachingCard("Why this matters", step.whyItMatters, MaterialTheme.colorScheme.primaryContainer)
                }
            }
            if (step.demonstration.isNotBlank()) {
                item {
                    LessonTeachingCard("What Show Me demonstrates", step.demonstration, MaterialTheme.colorScheme.tertiaryContainer)
                }
            }
            if (step.practice.isNotBlank()) {
                item {
                    LessonTeachingCard("Guided practice", step.practice, MaterialTheme.colorScheme.surfaceVariant)
                }
            }
            if (step.commonMistakes.isNotEmpty()) {
                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Watch for these mistakes", style = MaterialTheme.typography.titleMedium)
                            step.commonMistakes.forEach { mistake -> Text("• $mistake") }
                        }
                    }
                }
            }
            item {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Mastery checkpoint", style = MaterialTheme.typography.titleMedium)
                        Text(step.checkpoint)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onToggleGuide, modifier = Modifier.weight(1f)) {
                        Icon(if (showGuide) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (showGuide) "Hide guide" else "Show me")
                    }
                    Button(onClick = onCheck, enabled = !checking, modifier = Modifier.weight(1f)) {
                        Text("Check work")
                    }
                }
            }
            if (checking) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Rendering the real canvas and checking it against this step…")
                    }
                }
            }
            assessment?.let { result ->
                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(result.title, style = MaterialTheme.typography.titleLarge)
                            AssistChip(onClick = {}, label = { Text(result.sourceLabel) })
                            Surface(
                                color = if (result.mastery == LessonMastery.READY_TO_ADVANCE) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.errorContainer
                                },
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(
                                    if (result.mastery == LessonMastery.READY_TO_ADVANCE) {
                                        "Mastery: Ready to advance"
                                    } else {
                                        "Mastery: Needs practice"
                                    },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            Text(result.explanation)
                            result.actions.forEachIndexed { index, action -> Text("${index + 1}. $action") }
                        }
                    }
                }
            }
            item {
                Text("Course: ${lesson.title}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LessonTeachingCard(title: String, body: String, color: Color) {
    Surface(Modifier.fillMaxWidth(), color = color, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body)
        }
    }
}

@Composable
private fun LessonToolBar(viewModel: KreativViewModel) {
    HorizontalDivider()
    Surface(Modifier.fillMaxWidth().navigationBarsPadding(), tonalElevation = 8.dp) {
        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(lessonTools, key = { it }) { tool ->
                    FilterChip(
                        selected = viewModel.activeTool == tool,
                        onClick = { viewModel.activeTool = tool },
                        label = { Text(tool.name.lowercase().replaceFirstChar(Char::uppercase)) },
                        leadingIcon = { Icon(if (tool == ToolType.TEXT) Icons.Default.TextFields else Icons.Default.Edit, null, Modifier.size(17.dp)) },
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${viewModel.brushWidth.toInt()} px", modifier = Modifier.width(58.dp))
                Slider(viewModel.brushWidth, { viewModel.brushWidth = it }, valueRange = 1f..180f, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BoxScope.LessonGuideOverlay(project: KreativProject, category: LessonCategory, stepIndex: Int) {
    Canvas(Modifier.fillMaxSize()) {
        val scale = min(size.width / project.widthPx.coerceAtLeast(1), size.height / project.heightPx.coerceAtLeast(1))
        val pageWidth = project.widthPx * scale
        val pageHeight = project.heightPx * scale
        val page = Rect(
            left = (size.width - pageWidth) / 2f,
            top = (size.height - pageHeight) / 2f,
            right = (size.width + pageWidth) / 2f,
            bottom = (size.height + pageHeight) / 2f,
        )
        val guide = Color(0xFF9F7AEA)
        val bright = Color(0xFFE9DFFF)
        val stroke = (size.minDimension / 360f).coerceIn(2f, 6f)

        if (category == LessonCategory.PORTRAIT) {
            val head = Rect(
                page.left + page.width * .27f,
                page.top + page.height * .13f,
                page.right - page.width * .27f,
                page.top + page.height * .67f,
            )
            drawOval(guide.copy(alpha = .85f), topLeft = head.topLeft, size = head.size, style = Stroke(stroke))
            drawLine(bright, Offset(head.center.x, head.top), Offset(head.center.x, head.bottom), strokeWidth = stroke)
            val browY = head.top + head.height * .43f
            drawLine(bright, Offset(head.left, browY), Offset(head.right, browY), strokeWidth = stroke)

            if (stepIndex >= 2) {
                val jaw = Path().apply {
                    moveTo(head.left + head.width * .08f, head.top + head.height * .46f)
                    lineTo(head.left + head.width * .18f, head.bottom - head.height * .08f)
                    lineTo(head.center.x, head.bottom + head.height * .12f)
                    lineTo(head.right - head.width * .18f, head.bottom - head.height * .08f)
                    lineTo(head.right - head.width * .08f, head.top + head.height * .46f)
                }
                drawPath(jaw, guide, style = Stroke(stroke))
            }
            if (stepIndex >= 3) {
                val eyeY = browY + head.height * .08f
                val noseY = head.top + head.height * .68f
                val mouthY = head.top + head.height * .82f
                drawLine(guide, Offset(head.left + head.width * .16f, eyeY), Offset(head.right - head.width * .16f, eyeY), strokeWidth = stroke)
                drawLine(guide.copy(alpha = .75f), Offset(head.left + head.width * .3f, noseY), Offset(head.right - head.width * .3f, noseY), strokeWidth = stroke)
                drawLine(guide.copy(alpha = .75f), Offset(head.left + head.width * .25f, mouthY), Offset(head.right - head.width * .25f, mouthY), strokeWidth = stroke)
            }
            if (stepIndex >= 4) {
                drawCircle(bright, radius = head.width * .08f, center = Offset(head.left + head.width * .36f, browY + head.height * .08f), style = Stroke(stroke))
                drawCircle(bright, radius = head.width * .08f, center = Offset(head.right - head.width * .36f, browY + head.height * .08f), style = Stroke(stroke))
            }
            if (stepIndex >= 5) {
                drawRect(
                    Color(0x553E2A5C),
                    topLeft = Offset(head.center.x, head.top),
                    size = Size(head.width / 2f, head.height),
                )
                drawLine(bright, Offset(head.center.x, head.top), Offset(head.center.x, head.bottom), strokeWidth = stroke)
            }
            if (stepIndex >= 6) {
                val focusCenter = Offset(head.left + head.width * .36f, browY + head.height * .08f)
                drawCircle(Color(0x99FFD37A), radius = head.width * .13f, center = focusCenter, style = Stroke(stroke * 1.3f))
            }
        } else {
            drawLine(guide, Offset(page.left, page.center.y), Offset(page.right, page.center.y), strokeWidth = stroke)
            drawLine(guide, Offset(page.center.x, page.top), Offset(page.center.x, page.bottom), strokeWidth = stroke)
            drawRect(guide, topLeft = Offset(page.left + page.width * .15f, page.top + page.height * .15f), size = Size(page.width * .7f, page.height * .7f), style = Stroke(stroke))
        }
    }
}

private val lessonTools = listOf(
    ToolType.PENCIL,
    ToolType.PEN,
    ToolType.WATERCOLOR,
    ToolType.CHARCOAL,
    ToolType.ERASER,
    ToolType.LINE,
    ToolType.ELLIPSE,
    ToolType.SELECT,
    ToolType.TEXT,
)

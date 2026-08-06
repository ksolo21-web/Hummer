package com.kreativstudio.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush as GradientBrush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kreativstudio.app.ai.OnDeviceMentorPhase
import com.kreativstudio.app.model.AiAdvice
import com.kreativstudio.app.model.AiProcessingMode
import com.kreativstudio.app.model.AttachmentKind
import com.kreativstudio.app.model.KreativProject
import com.kreativstudio.app.ui.theme.LocalKreativTokens

private enum class MentorMode(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val instruction: String,
) {
    CRITIQUE(
        "Critique",
        "Honest priorities",
        Icons.Default.Visibility,
        "Give an honest professional critique. Prioritize the three changes with the greatest visual impact.",
    ),
    TEACH(
        "Teach me",
        "Explain the skill",
        Icons.Default.MenuBook,
        "Teach the skill step by step, explain why each step matters, and include one small practice drill.",
    ),
    FIX_PLAN(
        "Fix plan",
        "Correct in order",
        Icons.Default.Edit,
        "Create a correction plan from large structural decisions to small details. Protect what is already working.",
    ),
    INSPIRE(
        "Inspire",
        "Explore, do not replace",
        Icons.Default.AutoAwesome,
        "Offer three creative directions that preserve the artist's voice. Do not redesign or replace the artwork.",
    ),
}

private data class MentorFocus(
    val title: String,
    val description: String,
    val starter: String,
    val drills: List<String>,
)

private val mentorFocuses = listOf(
    MentorFocus(
        "Composition",
        "Focal point, balance, rhythm",
        "Help me strengthen the composition and make the focal point clearer.",
        listOf(
            "Give me a three-value thumbnail exercise for this composition.",
            "Help me simplify the background without losing atmosphere.",
            "Show me how to improve visual flow toward the focal point.",
        ),
    ),
    MentorFocus(
        "Portrait & anatomy",
        "Structure before detail",
        "Help me check the portrait proportions and underlying anatomy before I add detail.",
        listOf(
            "Give me a five-minute head construction drill.",
            "Explain how to compare facial angles and negative spaces.",
            "Help me diagnose why a portrait likeness feels off.",
        ),
    ),
    MentorFocus(
        "Color & light",
        "Values, temperature, mood",
        "Help me clarify the light direction, value groups, and color temperature.",
        listOf(
            "Create a three-value light study for my scene.",
            "Explain warm-versus-cool color relationships in plain language.",
            "Help me reserve the strongest contrast for the focal area.",
        ),
    ),
    MentorFocus(
        "Watercolor",
        "Water, pigment, edges",
        "Help me control watercolor moisture, glazing, blooms, and edge variety.",
        listOf(
            "Give me a wetness-control swatch exercise.",
            "Explain when to use hard, soft, and lost edges.",
            "Help me plan watercolor layers from light to dark.",
        ),
    ),
    MentorFocus(
        "Perspective",
        "Space that agrees",
        "Help me correct the horizon, vanishing directions, scale, and depth.",
        listOf(
            "Give me a one-point perspective warm-up.",
            "Help me identify inconsistent vanishing directions.",
            "Explain how repeated spacing compresses with distance.",
        ),
    ),
    MentorFocus(
        "Line & shape",
        "Confidence and clarity",
        "Help me improve line confidence, shape design, and edge control.",
        listOf(
            "Give me a ten-line confidence warm-up.",
            "Help me simplify the subject into clear shape families.",
            "Explain when a perfect line helps and when it makes art feel stiff.",
        ),
    ),
)

@Composable
fun KreativMentorExperience(viewModel: KreativViewModel) {
    val settings by viewModel.settings.collectAsState()
    val onDeviceMentor by viewModel.onDeviceMentorState.collectAsState()
    val project = viewModel.currentProject
    val advice = viewModel.aiAdvice
    val context = LocalContext.current
    val recentQuestions = remember { mutableStateListOf<String>() }
    var selectedMode by remember { mutableStateOf(MentorMode.CRITIQUE) }
    var selectedFocus by remember { mutableStateOf(mentorFocuses.first()) }

    val referenceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<Uri> ->
        if (project == null) {
            viewModel.showMessage("Open or create a canvas before adding project references.")
        } else {
            viewModel.addAttachments(context, uris, AttachmentKind.REFERENCE)
        }
    }

    val submit: (String?) -> Unit = { overridePrompt ->
        val artistQuestion = (overridePrompt ?: viewModel.aiPrompt).trim().ifBlank { selectedFocus.starter }
        val request = buildString {
            appendLine(selectedMode.instruction)
            appendLine("Primary focus: ${selectedFocus.title}.")
            appendLine("Artist request: $artistQuestion")
            append("Be warm, specific, honest, and concise. Explain why. Never claim to see pixels or details that were not supplied.")
        }
        viewModel.aiPrompt = artistQuestion
        recentQuestions.remove(artistQuestion)
        recentQuestions.add(0, artistQuestion)
        while (recentQuestions.size > 5) recentQuestions.removeAt(recentQuestions.lastIndex)
        viewModel.requestAiAdvice(request)
    }

    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        if (maxWidth >= 980.dp) {
            Row(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item { MentorHero(project, settings.aiLocalFirst) }
                    item { ProjectContextCard(project) }
                    item { MentorModePicker(selectedMode) { selectedMode = it } }
                    item { MentorFocusPicker(selectedFocus) { selectedFocus = it } }
                    item {
                        MentorComposer(
                            viewModel = viewModel,
                            project = project,
                            mode = selectedMode,
                            focus = selectedFocus,
                            onSubmit = { submit(null) },
                            onCoachProject = { submit(selectedFocus.starter) },
                            onAddReference = { referenceLauncher.launch(arrayOf("image/*", "application/pdf")) },
                        )
                    }
                    if (advice != null) {
                        item {
                            MentorResponseCard(
                                advice = advice,
                                onPractice = { viewModel.navigate(StudioScreen.STUDIO) },
                                onFollowUp = {
                                    viewModel.aiPrompt = "Explain the first action in more detail and give me a short practice drill."
                                },
                            )
                        }
                    }
                }
                LazyColumn(
                    modifier = Modifier.widthIn(min = 310.dp, max = 370.dp).fillMaxHeight(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        MentorStatusCard(
                            localFirst = settings.aiLocalFirst,
                            phase = onDeviceMentor.phase,
                            detail = onDeviceMentor.detail,
                            downloaded = onDeviceMentor.bytesDownloaded,
                            total = onDeviceMentor.bytesToDownload,
                            onRefresh = viewModel::refreshOnDeviceMentorStatus,
                            onDownload = viewModel::prepareOnDeviceMentor,
                        )
                    }
                    item { MentorDrillsCard(selectedFocus) { viewModel.aiPrompt = it } }
                    if (recentQuestions.isNotEmpty()) {
                        item { RecentMentorQuestions(recentQuestions) { viewModel.aiPrompt = it } }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { MentorHero(project, settings.aiLocalFirst) }
                item { ProjectContextCard(project) }
                item { MentorModePicker(selectedMode) { selectedMode = it } }
                item { MentorFocusPicker(selectedFocus) { selectedFocus = it } }
                item {
                    MentorComposer(
                        viewModel = viewModel,
                        project = project,
                        mode = selectedMode,
                        focus = selectedFocus,
                        onSubmit = { submit(null) },
                        onCoachProject = { submit(selectedFocus.starter) },
                        onAddReference = { referenceLauncher.launch(arrayOf("image/*", "application/pdf")) },
                    )
                }
                item {
                    MentorStatusCard(
                        localFirst = settings.aiLocalFirst,
                        phase = onDeviceMentor.phase,
                        detail = onDeviceMentor.detail,
                        downloaded = onDeviceMentor.bytesDownloaded,
                        total = onDeviceMentor.bytesToDownload,
                        onRefresh = viewModel::refreshOnDeviceMentorStatus,
                        onDownload = viewModel::prepareOnDeviceMentor,
                    )
                }
                item { MentorDrillsCard(selectedFocus) { viewModel.aiPrompt = it } }
                if (advice != null) {
                    item {
                        MentorResponseCard(
                            advice = advice,
                            onPractice = { viewModel.navigate(StudioScreen.STUDIO) },
                            onFollowUp = {
                                viewModel.aiPrompt = "Explain the first action in more detail and give me a short practice drill."
                            },
                        )
                    }
                }
                if (recentQuestions.isNotEmpty()) {
                    item { RecentMentorQuestions(recentQuestions) { viewModel.aiPrompt = it } }
                }
            }
        }
    }
}

@Composable
private fun MentorHero(project: KreativProject?, localFirst: Boolean) {
    val tokens = LocalKreativTokens.current
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp), color = Color.Transparent) {
        Box(
            Modifier
                .background(
                    GradientBrush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            tokens.owlSurface,
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .88f),
                        ),
                    ),
                )
                .padding(22.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = CircleShape, color = tokens.gold.copy(alpha = .18f)) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            null,
                            tint = tokens.gold,
                            modifier = Modifier.padding(13.dp).size(28.dp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text("KREATIV Mentor", style = MaterialTheme.typography.headlineLarge)
                        Text(
                            "Your private art teacher, critic, and practice coach",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    "Ask for a critique, a lesson, a correction plan, or fresh directions. The mentor explains why, protects your style, and never edits your work without approval.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        AssistChip(
                            onClick = {},
                            label = { Text(if (localFirst) "Local-first" else "Cloud-first") },
                            leadingIcon = { Icon(Icons.Default.CloudSync, null, Modifier.size(18.dp)) },
                        )
                    }
                    item {
                        AssistChip(
                            onClick = {},
                            label = { Text(project?.title ?: "No canvas open") },
                            leadingIcon = { Icon(Icons.Default.Brush, null, Modifier.size(18.dp)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectContextCard(project: KreativProject?) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Brush, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text("Project context", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (project == null) "Open a canvas for project-aware coaching."
                        else "The mentor receives project structure and your written description.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (project == null) {
                Text("You can still ask technique questions, build a lesson, or plan a new artwork.")
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { ContextMetric("${project.widthPx} × ${project.heightPx}", "canvas") }
                    item { ContextMetric(project.layers.size.toString(), "layers") }
                    item { ContextMetric(project.elements.size.toString(), "marks") }
                    item { ContextMetric(project.attachments.size.toString(), "references") }
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    "Honesty rule: the mentor will not pretend to see visual details that were not supplied. Describe the problem clearly for the strongest answer.",
                    modifier = Modifier.padding(13.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ContextMetric(value: String, label: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f)) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun MentorModePicker(selected: MentorMode, onSelected: (MentorMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Choose how the mentor should help", "Change the teaching behavior, not just the wording.")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(MentorMode.values().toList(), key = { it.name }) { mode ->
                FilterChip(
                    selected = selected == mode,
                    onClick = { onSelected(mode) },
                    label = {
                        Column {
                            Text(mode.title, fontWeight = FontWeight.SemiBold)
                            Text(mode.subtitle, style = MaterialTheme.typography.labelMedium)
                        }
                    },
                    leadingIcon = { Icon(mode.icon, null, Modifier.size(19.dp)) },
                )
            }
        }
    }
}

@Composable
private fun MentorFocusPicker(selected: MentorFocus, onSelected: (MentorFocus) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Select the skill focus", selected.description)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(mentorFocuses, key = { it.title }) { focus ->
                FilterChip(
                    selected = selected == focus,
                    onClick = { onSelected(focus) },
                    label = { Text(focus.title) },
                    leadingIcon = {
                        Icon(
                            when (focus.title) {
                                "Color & light", "Watercolor" -> Icons.Default.Palette
                                "Portrait & anatomy" -> Icons.Default.Visibility
                                "Perspective" -> Icons.Default.Settings
                                else -> Icons.Default.Brush
                            },
                            null,
                            Modifier.size(18.dp),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun MentorComposer(
    viewModel: KreativViewModel,
    project: KreativProject?,
    mode: MentorMode,
    focus: MentorFocus,
    onSubmit: () -> Unit,
    onCoachProject: () -> Unit,
    onAddReference: () -> Unit,
) {
    val tokens = LocalKreativTokens.current
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(mode.icon, null, modifier = Modifier.padding(11.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("${mode.title} • ${focus.title}", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Tell the mentor what feels wrong, what you intended, and what medium you are using.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedTextField(
                value = viewModel.aiPrompt,
                onValueChange = { viewModel.aiPrompt = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 132.dp, max = 260.dp),
                minLines = 4,
                maxLines = 9,
                label = { Text("Describe the artwork or ask a question") },
                placeholder = { Text(focus.starter) },
                supportingText = { Text("Specific intent + specific problem = stronger coaching") },
            )
            Button(
                onClick = onSubmit,
                enabled = !viewModel.isBusy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = tokens.gold, contentColor = Color(0xFF1C1004)),
            ) {
                if (viewModel.isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(19.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF1C1004),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Thinking…")
                } else {
                    Icon(Icons.Default.AutoAwesome, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Ask KREATIV Mentor")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onCoachProject,
                    enabled = project != null && !viewModel.isBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Brush, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Coach project")
                }
                OutlinedButton(
                    onClick = onAddReference,
                    enabled = project != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.PhotoLibrary, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("References")
                }
            }
        }
    }
}

@Composable
private fun MentorStatusCard(
    localFirst: Boolean,
    phase: OnDeviceMentorPhase,
    detail: String,
    downloaded: Long,
    total: Long?,
    onRefresh: () -> Unit,
    onDownload: () -> Unit,
) {
    val tokens = LocalKreativTokens.current
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = CircleShape, color = tokens.success.copy(alpha = .16f)) {
                    Icon(Icons.Default.CloudSync, null, tint = tokens.success, modifier = Modifier.padding(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("AI privacy & availability", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (localFirst) "Local processing is preferred." else "Cloud processing is preferred.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AssistChip(
                onClick = onRefresh,
                label = { Text(phase.mentorLabel()) },
                leadingIcon = { Icon(Icons.Default.Refresh, null, Modifier.size(17.dp)) },
            )
            Text(detail, style = MaterialTheme.typography.bodyMedium)
            if (phase == OnDeviceMentorPhase.DOWNLOADING) {
                val progress = total?.takeIf { it > 0L }?.let {
                    (downloaded.toFloat() / it.toFloat()).coerceIn(0f, 1f)
                }
                if (progress == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text("${(progress * 100).toInt()}% downloaded", style = MaterialTheme.typography.labelMedium)
                }
            }
            if (phase == OnDeviceMentorPhase.DOWNLOADABLE) {
                Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Download, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Prepare offline AI")
                }
            }
            Text(
                "The built-in studio coach remains available when Gemini Nano or cloud AI is unavailable.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MentorDrillsCard(focus: MentorFocus, onChoose: (String) -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("${focus.title} practice", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Use a focused drill instead of asking a broad question.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            focus.drills.forEach { drill ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    TextButton(onClick = { onChoose(drill) }, modifier = Modifier.fillMaxWidth()) {
                        Text(drill, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                    }
                }
            }
        }
    }
}

@Composable
private fun MentorResponseCard(advice: AiAdvice, onPractice: () -> Unit, onFollowUp: () -> Unit) {
    val tokens = LocalKreativTokens.current
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = CircleShape, color = tokens.glow.copy(alpha = .18f)) {
                    Icon(Icons.Default.AutoAwesome, null, tint = tokens.glow, modifier = Modifier.padding(11.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Mentor response", style = MaterialTheme.typography.labelLarge, color = tokens.gold)
                    Text(advice.title, style = MaterialTheme.typography.headlineMedium)
                }
                AssistChip(onClick = {}, label = { Text(advice.processingMode.mentorLabel()) })
            }
            Text(advice.explanation, style = MaterialTheme.typography.bodyLarge)
            HorizontalDivider()
            Text("Your next three moves", style = MaterialTheme.typography.titleLarge)
            advice.actions.forEachIndexed { index, action ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        modifier = Modifier.padding(13.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(
                                "${index + 1}",
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(action, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onPractice, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Brush, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Practice")
                }
                OutlinedButton(onClick = onFollowUp, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Follow-up")
                }
            }
        }
    }
}

@Composable
private fun RecentMentorQuestions(questions: List<String>, onChoose: (String) -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("This session", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tap a previous question to refine it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            questions.forEach { question ->
                TextButton(onClick = { onChoose(question) }, modifier = Modifier.fillMaxWidth()) {
                    Text(question, modifier = Modifier.fillMaxWidth(), maxLines = 2, textAlign = TextAlign.Start)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun OnDeviceMentorPhase.mentorLabel(): String = when (this) {
    OnDeviceMentorPhase.CHECKING -> "Checking device"
    OnDeviceMentorPhase.AVAILABLE -> "Offline AI ready"
    OnDeviceMentorPhase.DOWNLOADABLE -> "Offline AI available"
    OnDeviceMentorPhase.DOWNLOADING -> "Downloading offline AI"
    OnDeviceMentorPhase.UNSUPPORTED -> "Built-in coach"
    OnDeviceMentorPhase.LOCAL_FALLBACK -> "Offline studio coach"
    OnDeviceMentorPhase.ERROR -> "Fallback active"
}

private fun AiProcessingMode.mentorLabel(): String = when (this) {
    AiProcessingMode.ON_DEVICE -> "On device"
    AiProcessingMode.CLOUD -> "Cloud"
    AiProcessingMode.HYBRID -> "Hybrid"
}

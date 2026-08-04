package com.mystudycompanion.app.ui

import com.mystudycompanion.app.companion.ExactJwLinkPolicy
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Ballot
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HowToVote
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mystudycompanion.app.auth.UserAccount
import com.mystudycompanion.app.companion.AgeGroup
import com.mystudycompanion.app.companion.BibleJourneyCatalog
import com.mystudycompanion.app.companion.BibleJourneyCategory
import com.mystudycompanion.app.companion.BiblePlanMode
import com.mystudycompanion.app.companion.CompanionHubRepository
import com.mystudycompanion.app.companion.CompanionHubState
import com.mystudycompanion.app.companion.CompanionProfile
import com.mystudycompanion.app.companion.FamilyBoardRole
import com.mystudycompanion.app.companion.FamilyWorshipIdea
import com.mystudycompanion.app.companion.JwLibraryLinkResolver
import com.mystudycompanion.app.companion.PersonalStudyPlan
import com.mystudycompanion.app.companion.YouthActivity
import com.mystudycompanion.app.companion.YouthActivityType
import com.mystudycompanion.app.companion.YouthStudyPlanner
import com.mystudycompanion.app.ui.adaptive.AdaptiveLayoutSpec
import com.mystudycompanion.app.ui.adaptive.AdaptiveWidthClass
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

private enum class CompanionSection(val label: String) {
    TODAY("Today"),
    BIBLE("Bible"),
    EVENTS("Events"),
    MINISTRY("Ministry"),
    RESEARCH("Research"),
}

@Composable
fun CompanionHubScreen(
    account: UserAccount,
    repository: CompanionHubRepository,
    layoutSpec: AdaptiveLayoutSpec,
    onOpenAi: (String) -> Unit,
    onOpenNotes: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by repository.state.collectAsStateWithLifecycle()
    var sectionIndex by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(account.uid) { repository.bindAccount(account) }

    Column(modifier.fillMaxSize()) {
        val tabs: @Composable () -> Unit = {
            CompanionSection.entries.forEachIndexed { index, section ->
                Tab(
                    selected = sectionIndex == index,
                    onClick = { sectionIndex = index },
                    text = { Text(section.label, maxLines = 1) },
                )
            }
        }
        if (layoutSpec.widthClass == AdaptiveWidthClass.COMPACT) {
            ScrollableTabRow(
                selectedTabIndex = sectionIndex,
                edgePadding = 12.dp,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            ) { tabs() }
        } else {
            TabRow(
                selectedTabIndex = sectionIndex,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            ) { tabs() }
        }
        when (CompanionSection.entries[sectionIndex]) {
            CompanionSection.TODAY -> TodaySection(state, repository, layoutSpec, onOpenAi, onOpenNotes)
            CompanionSection.BIBLE -> BibleSection(state, repository, layoutSpec, onOpenNotes)
            CompanionSection.EVENTS -> EventNotebooksSection(state, repository, layoutSpec)
            CompanionSection.MINISTRY -> MinistrySection(state, repository, layoutSpec, onOpenNotes)
            CompanionSection.RESEARCH -> ResearchSection(state, layoutSpec, onOpenAi, onOpenNotes)
        }
    }
}

@Composable
private fun TodaySection(
    state: CompanionHubState,
    repository: CompanionHubRepository,
    layoutSpec: AdaptiveLayoutSpec,
    onOpenAi: (String) -> Unit,
    onOpenNotes: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(layoutSpec.outerPaddingDp.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            HeaderCard(
                icon = Icons.Outlined.AutoStories,
                title = "Personal Study Companion",
                body = "Every daily and weekly assignment arrives with its questions and age-appropriate activities already prepared.",
            )
        }
        state.dailyPlan?.let { plan ->
            item {
                StudyPlanCard(
                    plan = plan,
                    profile = state.profile,
                    completed = plan.id in state.completedStudyPlanIds,
                    completedActivities = state.completedActivityIds,
                    onTogglePlan = { repository.togglePlanComplete(plan.id) },
                    onToggleActivity = repository::toggleActivity,
                    onOpenAi = onOpenAi,
                    onOpenNotes = onOpenNotes,
                )
            }
        }
        state.weeklyPlan?.let { plan ->
            item {
                StudyPlanCard(
                    plan = plan,
                    profile = state.profile,
                    completed = plan.id in state.completedStudyPlanIds,
                    completedActivities = state.completedActivityIds,
                    onTogglePlan = { repository.togglePlanComplete(plan.id) },
                    onToggleActivity = repository::toggleActivity,
                    onOpenAi = onOpenAi,
                    onOpenNotes = onOpenNotes,
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
internal fun ProfileSwitcher(
    state: CompanionHubState,
    onSelected: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Studying as ${state.profile.displayName}", fontWeight = FontWeight.SemiBold)
                    Text(
                        "${state.profile.ageGroup.label} • ${if (state.profile.googleConnected) "Google account connected" else "Private device profile"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                    )
                }
                IconButton(onClick = onAdd) { Icon(Icons.Outlined.Add, contentDescription = "Add family profile") }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.localProfiles, key = { it.uid }) { profile ->
                    FilterChip(
                        selected = profile.uid == state.profile.uid,
                        onClick = { onSelected(profile.uid) },
                        label = { Text(profile.displayName) },
                        leadingIcon = {
                            Icon(
                                if (profile.ageGroup == AgeGroup.ADULT) Icons.Outlined.Person else Icons.Outlined.ChildCare,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun AddProfileDialog(onDismiss: () -> Unit, onAdd: (String, AgeGroup) -> Unit) {
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf(AgeGroup.CHILD) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a family profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Children and teens may use a Google account when cloud sign-in is configured, or a private profile on this device during testing.")
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AgeGroup.entries.forEach { option ->
                        FilterChip(selected = age == option, onClick = { age = option }, label = { Text(option.label) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(name, age) }, enabled = name.trim().length >= 2) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun StudyPlanCard(
    plan: PersonalStudyPlan,
    profile: CompanionProfile,
    completed: Boolean,
    completedActivities: Set<String>,
    onTogglePlan: () -> Unit,
    onToggleActivity: (String) -> Unit,
    onOpenAi: (String) -> Unit,
    onOpenNotes: (String) -> Unit,
) {
    val context = LocalContext.current
    val exactPlanPassages = ExactJwLinkPolicy.splitBiblePassages(plan.readingReference)
    Card(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(plan.periodLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(plan.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onTogglePlan) {
                    Icon(
                        if (completed) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = if (completed) "Mark incomplete" else "Mark complete",
                        tint = if (completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(plan.readingReference, fontWeight = FontWeight.SemiBold)
            Text(plan.focus, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f))
            plan.questions.forEachIndexed { index, question ->
                Text("${index + 1}. $question")
            }
            exactPlanPassages.forEachIndexed { index, passage ->
                Button(
                    onClick = { JwLibraryLinkResolver.openBibleReference(context, passage, true) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.LibraryBooks, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (exactPlanPassages.size == 1) "Open in JW Library" else "Open passage ${index + 1}: $passage")
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onOpenAi("Help me study ${plan.readingReference}. Use only verified JW sources and focus on: ${plan.focus}") }) {
                    Icon(Icons.Outlined.Psychology, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Study with AI")
                }
                OutlinedButton(onClick = { onOpenNotes(plan.title) }) {
                    Icon(Icons.Outlined.Send, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add notes")
                }
            }
            if (plan.youthOfficialUrl.isNotBlank()) {
                OutlinedButton(
                    onClick = { JwLibraryLinkResolver.openOfficial(context, plan.youthOfficialUrl, profile.preferJwLibrary) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(plan.youthOfficialLabel)
                }
            }
            if (plan.activities.isNotEmpty()) {
                Text("Activities already prepared", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                plan.activities.forEach { activity ->
                    ActivityCard(
                        activity = activity,
                        completed = activity.id in completedActivities,
                        onToggle = { onToggleActivity(activity.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityCard(activity: YouthActivity, completed: Boolean, onToggle: () -> Unit) {
    OutlinedCard(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (activity.type) {
                        YouthActivityType.COLORING, YouthActivityType.COLOR_BY_NUMBER -> Icons.Outlined.Palette
                        YouthActivityType.WORD_SEARCH, YouthActivityType.CROSSWORD -> Icons.Outlined.Search
                        YouthActivityType.SCENARIO, YouthActivityType.REFLECTION -> Icons.Outlined.Psychology
                        YouthActivityType.MEMORY_CARD -> Icons.Outlined.AutoStories
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(activity.title, fontWeight = FontWeight.SemiBold)
                    Text(activity.instructions, style = MaterialTheme.typography.bodySmall)
                }
                Checkbox(checked = completed, onCheckedChange = { onToggle() })
            }
            when (activity.type) {
                YouthActivityType.COLORING, YouthActivityType.COLOR_BY_NUMBER -> YouthActivityArtwork(activity)
                YouthActivityType.WORD_SEARCH -> WordSearch(activity)
                YouthActivityType.CROSSWORD -> CrosswordPreview(activity)
                YouthActivityType.SCENARIO, YouthActivityType.REFLECTION -> activity.questions.forEachIndexed { i, q -> Text("${i + 1}. $q") }
                YouthActivityType.MEMORY_CARD -> MemoryCard(activity)
            }
        }
    }
}

@Composable
private fun YouthActivityArtwork(activity: YouthActivity) {
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val fill = MaterialTheme.colorScheme.primaryContainer
    Box(
        Modifier.fillMaxWidth().aspectRatio(1.55f).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(18.dp)),
    ) {
        Canvas(Modifier.fillMaxSize().padding(14.dp)) {
            val stroke = Stroke(width = 4f)
            drawRoundRect(color = ink, style = stroke, cornerRadius = androidx.compose.ui.geometry.CornerRadius(24f, 24f))
            val cx = size.width / 2f
            val cy = size.height / 2f
            when (activity.artTheme) {
                "noah" -> {
                    val hull = Path().apply {
                        moveTo(size.width * .18f, size.height * .63f)
                        lineTo(size.width * .82f, size.height * .63f)
                        lineTo(size.width * .70f, size.height * .80f)
                        lineTo(size.width * .30f, size.height * .80f)
                        close()
                    }
                    drawPath(hull, if (activity.type == YouthActivityType.COLOR_BY_NUMBER) fill.copy(alpha = .45f) else Color.Transparent)
                    drawPath(hull, ink, style = stroke)
                    drawRect(ink, topLeft = Offset(size.width * .36f, size.height * .34f), size = Size(size.width * .28f, size.height * .29f), style = stroke)
                    drawArc(accent, 195f, 150f, false, topLeft = Offset(size.width * .24f, size.height * .05f), size = Size(size.width * .52f, size.height * .50f), style = stroke)
                }
                "david" -> {
                    drawCircle(if (activity.type == YouthActivityType.COLOR_BY_NUMBER) fill.copy(alpha = .45f) else Color.Transparent, radius = size.minDimension * .18f, center = Offset(cx, cy))
                    drawCircle(ink, radius = size.minDimension * .18f, center = Offset(cx, cy), style = stroke)
                    drawLine(ink, Offset(cx, cy - size.minDimension * .18f), Offset(cx + size.width * .25f, cy - size.height * .28f), strokeWidth = 4f)
                    drawLine(ink, Offset(cx + size.width * .25f, cy - size.height * .28f), Offset(cx + size.width * .32f, cy - size.height * .18f), strokeWidth = 4f)
                    drawCircle(accent, radius = 10f, center = Offset(cx + size.width * .32f, cy - size.height * .18f), style = stroke)
                }
                "job" -> {
                    drawCircle(ink, radius = size.minDimension * .12f, center = Offset(cx, size.height * .30f), style = stroke)
                    drawLine(ink, Offset(cx, size.height * .42f), Offset(cx, size.height * .70f), strokeWidth = 4f)
                    drawLine(ink, Offset(cx, size.height * .50f), Offset(cx - size.width * .18f, size.height * .60f), strokeWidth = 4f)
                    drawLine(ink, Offset(cx, size.height * .50f), Offset(cx + size.width * .18f, size.height * .60f), strokeWidth = 4f)
                    repeat(5) { i ->
                        drawCircle(accent.copy(alpha = .85f), radius = 7f, center = Offset(size.width * (.18f + i * .16f), size.height * .18f), style = stroke)
                    }
                }
                "solomon" -> {
                    val crown = Path().apply {
                        moveTo(size.width * .25f, size.height * .68f)
                        lineTo(size.width * .20f, size.height * .32f)
                        lineTo(size.width * .38f, size.height * .48f)
                        lineTo(size.width * .50f, size.height * .25f)
                        lineTo(size.width * .62f, size.height * .48f)
                        lineTo(size.width * .80f, size.height * .32f)
                        lineTo(size.width * .75f, size.height * .68f)
                        close()
                    }
                    drawPath(crown, if (activity.type == YouthActivityType.COLOR_BY_NUMBER) fill.copy(alpha = .45f) else Color.Transparent)
                    drawPath(crown, ink, style = stroke)
                }
                else -> {
                    val book = Path().apply {
                        moveTo(size.width * .18f, size.height * .28f)
                        quadraticBezierTo(size.width * .38f, size.height * .22f, cx, size.height * .36f)
                        quadraticBezierTo(size.width * .62f, size.height * .22f, size.width * .82f, size.height * .28f)
                        lineTo(size.width * .82f, size.height * .72f)
                        quadraticBezierTo(size.width * .62f, size.height * .66f, cx, size.height * .78f)
                        quadraticBezierTo(size.width * .38f, size.height * .66f, size.width * .18f, size.height * .72f)
                        close()
                    }
                    drawPath(book, if (activity.type == YouthActivityType.COLOR_BY_NUMBER) fill.copy(alpha = .45f) else Color.Transparent)
                    drawPath(book, ink, style = stroke)
                    drawLine(ink, Offset(cx, size.height * .36f), Offset(cx, size.height * .78f), strokeWidth = 4f)
                }
            }
            if (activity.type == YouthActivityType.COLOR_BY_NUMBER) {
                val paint = android.graphics.Paint().apply {
                    color = ink.toArgb()
                    textSize = 28f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true
                }
                drawContext.canvas.nativeCanvas.drawText("1", size.width * .35f, size.height * .55f, paint)
                drawContext.canvas.nativeCanvas.drawText("2", size.width * .65f, size.height * .55f, paint)
                drawContext.canvas.nativeCanvas.drawText("3", size.width * .50f, size.height * .25f, paint)
            }
        }
        Text(
            "Original activity artwork • Seed ${activity.seed}",
            modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WordSearch(activity: YouthActivity) {
    val grid = remember(activity.id) { wordGrid(activity.words, activity.seed) }
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f), RoundedCornerShape(16.dp)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        grid.forEach { row ->
            Text(row.joinToString("  "), fontFamily = FontFamily.Monospace, fontSize = 15.sp, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(activity.words.joinToString(" • "), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
    }
}

private fun wordGrid(words: List<String>, seed: Int, size: Int = 10): List<List<Char>> {
    val random = java.util.Random(seed.toLong())
    val grid = Array(size) { CharArray(size) { ('A'.code + random.nextInt(26)).toChar() } }
    words.map { it.uppercase().filter(Char::isLetter).take(size) }.filter { it.length >= 3 }.take(6).forEachIndexed { index, word ->
        val horizontal = index % 2 == 0
        if (horizontal) {
            val row = (index * 2 + 1) % size
            val start = random.nextInt((size - word.length + 1).coerceAtLeast(1))
            word.forEachIndexed { offset, char -> grid[row][start + offset] = char }
        } else {
            val col = (index * 2 + 2) % size
            val start = random.nextInt((size - word.length + 1).coerceAtLeast(1))
            word.forEachIndexed { offset, char -> grid[start + offset][col] = char }
        }
    }
    return grid.map(CharArray::toList)
}

@Composable
private fun CrosswordPreview(activity: YouthActivity) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        activity.clues.forEachIndexed { index, clue ->
            Text("${index + 1}. $clue")
            Text("□ □ □ □ □ □ □", fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun MemoryCard(activity: YouthActivity) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f))) {
        Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(activity.words.firstOrNull().orEmpty(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(28.dp))
            Text("My key verse: __________________________________________")
            Spacer(Modifier.height(14.dp))
            Text("Why it matters: ________________________________________")
        }
    }
}

@Composable
private fun BibleSection(
    state: CompanionHubState,
    repository: CompanionHubRepository,
    layoutSpec: AdaptiveLayoutSpec,
    onOpenNotes: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val progress = state.bibleProgress
    val current = YouthStudyPlanner.currentReading(progress)
    val activeJourney = BibleJourneyCatalog.journeys.firstOrNull { it.id == progress.activeJourneyId }
    var journeyCategory by rememberSaveable { mutableStateOf(activeJourney?.category ?: BibleJourneyCategory.STORY) }
    val filteredJourneys = BibleJourneyCatalog.journeys.filter { it.category == journeyCategory }
    val currentPositionLabel = when (progress.mode) {
        BiblePlanMode.STORY_JOURNEYS -> "Day ${progress.activeJourneyDayIndex + 1} of ${activeJourney?.days?.size ?: 1}"
        BiblePlanMode.GENESIS_TO_REVELATION -> {
            val total = BibleJourneyCatalog.canonicalPlan(progress.canonicalPaceDays).size
            "Day ${progress.canonicalDayIndex + 1} of $total"
        }
    }
    val passageButtons = ExactJwLinkPolicy.splitBiblePassages(current.reference)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = layoutSpec.outerPaddingDp.dp,
            end = layoutSpec.outerPaddingDp.dp,
            top = layoutSpec.outerPaddingDp.dp,
            bottom = (layoutSpec.outerPaddingDp + 32).dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            HeaderCard(Icons.Outlined.AutoStories, "Daily Bible Reading", "Choose Story, Theme, or Timeline Journeys—or a balanced Genesis-to-Revelation plan. Progress is saved for each active profile.")
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BiblePlanMode.entries.forEach { mode ->
                    FilterChip(
                        selected = progress.mode == mode,
                        onClick = {
                            repository.setBibleMode(mode)
                            scope.launch { listState.animateScrollToItem(2) }
                        },
                        label = { Text(mode.label) },
                    )
                }
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .98f)),
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(currentPositionLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(
                        if (progress.mode == BiblePlanMode.STORY_JOURNEYS) activeJourney?.title ?: current.title else "Genesis to Revelation",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(current.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(current.reference, fontWeight = FontWeight.SemiBold)
                    Text(current.focus)
                    current.questions.forEachIndexed { index, question -> Text("${index + 1}. $question") }
                    passageButtons.forEachIndexed { index, reference ->
                        Button(
                            onClick = { JwLibraryLinkResolver.openBibleReference(context, reference, state.profile.preferJwLibrary) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.LibraryBooks, contentDescription = null)
                            Spacer(Modifier.width(7.dp))
                            Text(if (passageButtons.size == 1) "Read now in JW Library" else "Read passage ${index + 1}: $reference")
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { JwLibraryLinkResolver.openResearchGuide(context, state.profile.preferJwLibrary) }) {
                            Text("Research Guide")
                        }
                        OutlinedButton(onClick = { onOpenNotes("Bible reading — ${current.title}") }) { Text("Add notes") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { repository.moveReading(-1) }) { Text("Previous") }
                        Button(onClick = repository::completeCurrentReading, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Complete and continue")
                        }
                        OutlinedButton(onClick = { repository.moveReading(1) }) { Text("Next") }
                    }
                }
            }
        }
        if (progress.mode == BiblePlanMode.STORY_JOURNEYS) {
            item { Text("Choose a Bible journey", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BibleJourneyCategory.entries.forEach { category ->
                        val count = BibleJourneyCatalog.journeys.count { it.category == category }
                        FilterChip(
                            selected = journeyCategory == category,
                            onClick = { journeyCategory = category },
                            label = { Text("${category.label} ($count)") },
                        )
                    }
                }
            }
            items(filteredJourneys, key = { it.id }) { journey ->
                val selected = journey.id == progress.activeJourneyId
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(journey.category.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(journey.title, fontWeight = FontWeight.Bold)
                        Text(journey.subtitle, color = MaterialTheme.colorScheme.primary)
                        Text(journey.description, style = MaterialTheme.typography.bodySmall)
                        Text("${journey.days.size} reading days", style = MaterialTheme.typography.labelMedium)
                        Button(
                            onClick = {
                                repository.selectJourney(journey.id)
                                scope.launch { listState.animateScrollToItem(2) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (selected) "Restart at Day 1" else "Start journey at Day 1")
                        }
                    }
                }
            }
        } else {
            item {
                Card(shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Choose your pace", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(365 to "1 year", 548 to "18 months", 730 to "2 years", 1095 to "3 years").forEach { (days, label) ->
                                FilterChip(
                                    selected = progress.canonicalPaceDays == days,
                                    onClick = {
                                        repository.setCanonicalPace(days)
                                        scope.launch { listState.animateScrollToItem(2) }
                                    },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
internal fun FamilyBoardSection(
    state: CompanionHubState,
    repository: CompanionHubRepository,
    layoutSpec: AdaptiveLayoutSpec,
    modifier: Modifier = Modifier,
) {
    var ideaTopic by rememberSaveable { mutableStateOf("") }
    var ideaReason by rememberSaveable { mutableStateOf("") }
    var ideaScripture by rememberSaveable { mutableStateOf("") }
    var customTopic by rememberSaveable { mutableStateOf(state.familyBoard.selectedCustomTopic) }
    var date by rememberSaveable { mutableStateOf(state.familyBoard.scheduledDateIso.ifBlank { LocalDate.now().plusDays(7).toString() }) }
    var time by rememberSaveable { mutableStateOf(state.familyBoard.scheduledTime24h) }
    var duration by rememberSaveable { mutableStateOf(state.familyBoard.durationMinutes.toString()) }
    var recurring by rememberSaveable { mutableStateOf(state.familyBoard.recurringWeekly) }
    val canOrganize = repository.canOrganize()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(layoutSpec.outerPaddingDp.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            HeaderCard(Icons.Outlined.FamilyRestroom, "${state.familyBoard.familyName} Worship Board", "Everyone can submit ideas and vote. The creator or co-organizer always chooses the final topic, date, and time.")
        }
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Family connection", fontWeight = FontWeight.Bold)
                    Text("Invite code: ${state.familyBoard.inviteCode.ifBlank { "Created when the organizer profile is active" }}")
                    Text("${state.familyBoard.members.size} profile(s) on this device • Cross-device family sync requires Firebase, Google OAuth, the private HTTPS backend, and household invitation/join support.", style = MaterialTheme.typography.bodySmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.familyBoard.members, key = { it.uid }) { member ->
                            AssistChip(onClick = {}, label = { Text("${member.displayName} • ${member.ageGroup.label}") })
                        }
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Suggest a family worship idea", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(ideaTopic, { ideaTopic = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Topic") })
                    OutlinedTextField(ideaReason, { ideaReason = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Why this may help our family") }, minLines = 2)
                    OutlinedTextField(ideaScripture, { ideaScripture = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Optional scripture") })
                    Button(
                        onClick = {
                            repository.submitIdea(ideaTopic, ideaReason, ideaScripture, ideaScripture.takeIf(String::isNotBlank)?.let(JwLibraryLinkResolver::bibleUrl).orEmpty())
                            ideaTopic = ""
                            ideaReason = ""
                            ideaScripture = ""
                        },
                        enabled = ideaTopic.trim().length >= 3,
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Submit idea")
                    }
                }
            }
        }
        item { Text("Ideas and family votes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (state.familyBoard.ideas.isEmpty()) {
            item { OutlinedCard { Text("No ideas have been submitted yet.", modifier = Modifier.fillMaxWidth().padding(20.dp)) } }
        }
        items(state.familyBoard.ideas.sortedByDescending { it.voterUids.size }, key = { it.id }) { idea ->
            FamilyIdeaCard(
                idea = idea,
                activeUid = state.profile.uid,
                selected = state.familyBoard.selectedIdeaId == idea.id,
                canOrganize = canOrganize,
                onVote = { repository.toggleIdeaVote(idea.id) },
                onSelect = { repository.selectFamilyWorshipIdea(idea.id) },
            )
        }
        item {
            Card(shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Organizer's final choice and schedule", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (!canOrganize) {
                        Text("Only the creator or a co-organizer can make the final selection and schedule it.")
                    }
                    OutlinedTextField(
                        value = customTopic,
                        onValueChange = { customTopic = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Or choose a different final topic") },
                        enabled = canOrganize,
                    )
                    OutlinedButton(onClick = { repository.selectCustomFamilyWorshipTopic(customTopic) }, enabled = canOrganize && customTopic.trim().length >= 3) {
                        Text("Use this final topic")
                    }
                    Text("Selected topic: ${repository.selectedFamilyWorshipTopic().ifBlank { "Not selected yet" }}", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(date, { date = it }, modifier = Modifier.weight(1.2f), label = { Text("Date YYYY-MM-DD") }, enabled = canOrganize)
                        OutlinedTextField(time, { time = it }, modifier = Modifier.weight(.8f), label = { Text("Time") }, enabled = canOrganize)
                    }
                    OutlinedTextField(duration, { duration = it.filter(Char::isDigit).take(3) }, label = { Text("Expected minutes") }, enabled = canOrganize)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = recurring, onCheckedChange = { recurring = it }, enabled = canOrganize)
                        Text("Repeat weekly")
                    }
                    Button(
                        onClick = { repository.scheduleFamilyWorship(date, time, duration.toIntOrNull() ?: 60, recurring) },
                        enabled = canOrganize,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Save family worship date and time")
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun FamilyIdeaCard(
    idea: FamilyWorshipIdea,
    activeUid: String,
    selected: Boolean,
    canOrganize: Boolean,
    onVote: () -> Unit,
    onSelect: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(idea.topic, fontWeight = FontWeight.Bold)
                    Text("Suggested by ${idea.authorName}", style = MaterialTheme.typography.labelMedium)
                }
                AssistChip(onClick = onVote, label = { Text("${idea.voterUids.size} vote${if (idea.voterUids.size == 1) "" else "s"}") }, leadingIcon = { Icon(Icons.Outlined.HowToVote, null, Modifier.size(18.dp)) })
            }
            if (idea.reason.isNotBlank()) Text(idea.reason)
            ExactJwLinkPolicy.splitBiblePassages(idea.scripture).forEach { passage ->
                TextButton(onClick = { JwLibraryLinkResolver.openBibleReference(context, passage, true) }) { Text(passage) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (activeUid in idea.voterUids) "You voted for this idea" else "Tap vote to support this idea", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                if (canOrganize) {
                    OutlinedButton(onClick = onSelect) { Text(if (selected) "Selected" else "Choose") }
                }
            }
        }
    }
}

@Composable
private fun MinistrySection(
    state: CompanionHubState,
    repository: CompanionHubRepository,
    layoutSpec: AdaptiveLayoutSpec,
    onOpenNotes: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var territory by rememberSaveable(state.profile.uid) { mutableStateOf(state.profile.territory) }
    var conducts by rememberSaveable(state.profile.uid) { mutableStateOf(state.profile.conductsFieldService) }
    val ministry = state.ministry
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(layoutSpec.outerPaddingDp.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { DailyFieldServicePointerCard(LocalDate.now()) }
        item { HeaderCard(Icons.Outlined.VolunteerActivism, "Field Service Conductor", "Prepare a scripture, two or three questions, a practical pointer, and a realistic scenario for meetings you conduct several times each week.") }
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(territory, { territory = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Territory city, county, or region") })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = conducts, onCheckedChange = { conducts = it })
                        Text("I conduct field service meetings")
                    }
                    Button(onClick = {
                        repository.updateProfile(
                            displayName = state.profile.displayName,
                            ageGroup = state.profile.ageGroup,
                            territory = territory,
                            conductsFieldService = conducts,
                            conductorDays = state.profile.conductorDays,
                            preferJwLibrary = state.profile.preferJwLibrary,
                        )
                    }) { Text("Save ministry profile") }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(26.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Newspaper, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Current territory awareness", fontWeight = FontWeight.Bold)
                            Text("News is used only as situational context. Scriptures and spiritual guidance stay JW-only.", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { scope.launch { repository.refreshAwareness() } }, enabled = !ministry.refreshingAwareness) {
                            if (ministry.refreshingAwareness) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Outlined.Refresh, contentDescription = "Refresh current events")
                        }
                    }
                    ministry.awarenessError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    ministry.headlines.forEachIndexed { index, headline ->
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth().clickable { repository.generateMinistryOutline(index) },
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(headline.title, fontWeight = FontWeight.SemiBold)
                                Text(headline.domain, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text("Tap to build a ministry outline around the possible concern—not to use the headline as doctrine.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Button(onClick = { repository.generateMinistryOutline(null) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (ministry.headlines.isEmpty()) "Generate a ministry outline" else "Generate next rotating outline")
                    }
                }
            }
        }
        ministry.currentOutline?.let { outline ->
            item {
                Card(
                    shape = RoundedCornerShape(30.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .98f)),
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("FIELD SERVICE MEETING", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(outline.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        if (outline.currentEventContext.isNotBlank()) {
                            Text("Current-event context for conductor review", fontWeight = FontWeight.SemiBold)
                            Text(outline.currentEventContext, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Possible territory concern", fontWeight = FontWeight.SemiBold)
                        Text(outline.territoryConcern)
                        Text("Scripture: ${outline.scriptureReference}", fontWeight = FontWeight.Bold)
                        outline.questions.forEachIndexed { i, question -> Text("${i + 1}. $question") }
                        Text("Try this today", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Text(outline.tryToday)
                        Text("Optional brief scenario", fontWeight = FontWeight.SemiBold)
                        Text(outline.scenario)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { JwLibraryLinkResolver.openBibleReference(context, outline.scriptureReference, state.profile.preferJwLibrary) }) { Text("Open scripture") }
                            OutlinedButton(onClick = { onOpenNotes("Field service — ${outline.title}") }) { Text("Save notes") }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ResearchSection(
    state: CompanionHubState,
    layoutSpec: AdaptiveLayoutSpec,
    onOpenAi: (String) -> Unit,
    onOpenNotes: (String) -> Unit,
) {
    val context = LocalContext.current
    var scripture by rememberSaveable { mutableStateOf("") }
    var topic by rememberSaveable { mutableStateOf("") }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(layoutSpec.outerPaddingDp.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { HeaderCard(Icons.Outlined.LibraryBooks, "JW Library and Research Guide", "JW Library is the primary destination. My Study Companion organizes the assignment, research trail, questions, and private notes.") }
        item {
            Card(shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (JwLibraryLinkResolver.isInstalled(context)) Icons.Outlined.CloudDone else Icons.Outlined.LibraryBooks, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(if (JwLibraryLinkResolver.isInstalled(context)) "JW Library detected on this device" else "JW Library was not detected", fontWeight = FontWeight.Bold)
                    }
                    Button(onClick = { JwLibraryLinkResolver.openResearchGuide(context, true) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Open the Research Guide in JW Library")
                    }
                    OutlinedTextField(scripture, { scripture = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Scripture, for example Jeremiah 20:11") })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { JwLibraryLinkResolver.openBibleReference(context, scripture, true) }, enabled = scripture.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Open scripture in JW Library") }
                        OutlinedButton(onClick = { JwLibraryLinkResolver.openResearchGuide(context, true) }, modifier = Modifier.weight(1f)) { Text("Research Guide") }
                    }
                    OutlinedTextField(topic, { topic = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Topic or meeting-part question") })
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { JwLibraryLinkResolver.openResearchGuide(context, true) }) { Text("Open Research Guide") }
                        OutlinedButton(onClick = { onOpenAi("Research this subject using only JW.org, Watchtower Online Library, the NWT Study Edition, and Research Guide references: $topic") }, enabled = topic.isNotBlank()) { Text("Ask grounded AI") }
                        OutlinedButton(onClick = { onOpenNotes("Research — $topic") }, enabled = topic.isNotBlank()) { Text("Create notes") }
                    }
                }
            }
        }
        item {
            HeaderCard(
                Icons.Outlined.Ballot,
                "Meeting Notes and Preparation",
                "Use the same private note workspace for Treasures, Apply Yourself, Living as Christians, Congregation Bible Study, Watchtower Study, public talks, assemblies, conventions, and custom parts.",
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun HeaderCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .78f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .13f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(body, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .78f))
            }
        }
    }
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)

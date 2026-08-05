#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

REPOSITORY="MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/companion/CompanionHubRepository.kt"
UI="MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/CompanionHubScreen.kt"
WIDGET="MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/widget/DailyStudyWidget.kt"
XML_DIR="MyStudyCompanion/app/src/main/res/xml"

python3 - "$REPOSITORY" "$UI" "$WIDGET" "$XML_DIR" <<'PY'
from pathlib import Path
import sys

repository_path = Path(sys.argv[1])
ui_path = Path(sys.argv[2])
widget_path = Path(sys.argv[3])
xml_dir = Path(sys.argv[4])


def replace_exact(text: str, old: str, new: str, label: str, expected: int = 1) -> str:
    found = text.count(old)
    if found != expected:
        raise SystemExit(f"{label}: expected {expected} match(es), found {found}")
    return text.replace(old, new, expected)

repository = repository_path.read_text(encoding="utf-8")
repository = replace_exact(
    repository,
    "import java.time.LocalDate\n",
    "import java.time.LocalDate\nimport java.time.LocalDateTime\nimport java.time.LocalTime\n",
    "family schedule time imports",
)
repository = replace_exact(
    repository,
    "class CompanionHubRepository(\n",
    """data class FamilyWorshipScheduleResult(
    val saved: Boolean,
    val message: String,
    val scheduledDateIso: String? = null,
)

class CompanionHubRepository(
""",
    "family schedule result type",
)
old_schedule = """    fun scheduleFamilyWorship(dateIso: String, time24h: String, durationMinutes: Int, recurringWeekly: Boolean) {
        if (!canOrganize()) return
        val date = runCatching { LocalDate.parse(dateIso) }.getOrNull() ?: return
        val time = time24h.takeIf { it.matches(Regex("([01]\\\\d|2[0-3]):[0-5]\\\\d")) } ?: return
        val current = mutableState.value
        commit(current.copy(familyBoard = current.familyBoard.copy(
            scheduledDateIso = date.toString(),
            scheduledTime24h = time,
            durationMinutes = durationMinutes.coerceIn(15, 180),
            recurringWeekly = recurringWeekly,
        )))
        FamilyWorshipReminderScheduler.schedule(appContext, date.toString(), time, recurringWeekly)
    }
"""
new_schedule = """    fun scheduleFamilyWorship(
        dateIso: String,
        time24h: String,
        durationMinutes: Int,
        recurringWeekly: Boolean,
    ): FamilyWorshipScheduleResult {
        if (!canOrganize()) {
            return FamilyWorshipScheduleResult(false, "Only the household creator or a co-organizer can save this schedule.")
        }
        if (selectedFamilyWorshipTopic().isBlank()) {
            return FamilyWorshipScheduleResult(false, "Choose a final family worship topic before saving the date and time.")
        }

        val requestedDate = runCatching { LocalDate.parse(dateIso.trim()) }.getOrNull()
            ?: return FamilyWorshipScheduleResult(false, "Enter the date as YYYY-MM-DD.")
        val timeParts = time24h.trim().split(':')
        val hour = timeParts.getOrNull(0)?.toIntOrNull()
        val minute = timeParts.getOrNull(1)?.toIntOrNull()
        val requestedTime = if (timeParts.size == 2 && hour != null && minute != null) {
            runCatching { LocalTime.of(hour, minute) }.getOrNull()
        } else null
        if (requestedTime == null) {
            return FamilyWorshipScheduleResult(false, "Enter a valid time such as 17:00.")
        }
        if (durationMinutes !in 15..180) {
            return FamilyWorshipScheduleResult(false, "Expected minutes must be between 15 and 180.")
        }

        val now = LocalDateTime.now()
        var scheduledAt = LocalDateTime.of(requestedDate, requestedTime)
        if (!scheduledAt.isAfter(now)) {
            if (!recurringWeekly) {
                return FamilyWorshipScheduleResult(false, "Choose a date and time that has not already passed.")
            }
            while (!scheduledAt.isAfter(now)) scheduledAt = scheduledAt.plusWeeks(1)
        }

        val normalizedDate = scheduledAt.toLocalDate().toString()
        val normalizedTime = String.format(Locale.US, "%02d:%02d", scheduledAt.hour, scheduledAt.minute)
        val current = mutableState.value
        commit(current.copy(familyBoard = current.familyBoard.copy(
            scheduledDateIso = normalizedDate,
            scheduledTime24h = normalizedTime,
            durationMinutes = durationMinutes,
            recurringWeekly = recurringWeekly,
        )))

        val reminderFailure = runCatching {
            FamilyWorshipReminderScheduler.schedule(appContext, normalizedDate, normalizedTime, recurringWeekly)
        }.exceptionOrNull()
        val dateAdjustment = if (normalizedDate != requestedDate.toString()) {
            " The first weekly occurrence was moved to $normalizedDate because the entered date had already passed."
        } else ""
        return if (reminderFailure == null) {
            FamilyWorshipScheduleResult(
                saved = true,
                message = "Family worship is scheduled for $normalizedDate at $normalizedTime.$dateAdjustment",
                scheduledDateIso = normalizedDate,
            )
        } else {
            FamilyWorshipScheduleResult(
                saved = true,
                message = "The family schedule was saved, but Android could not set the reminder. Check notification and alarm permissions.$dateAdjustment",
                scheduledDateIso = normalizedDate,
            )
        }
    }
"""
repository = replace_exact(repository, old_schedule, new_schedule, "silent family schedule implementation")
repository_path.write_text(repository, encoding="utf-8")

ui = ui_path.read_text(encoding="utf-8")
old_state = """    var customTopic by rememberSaveable { mutableStateOf(state.familyBoard.selectedCustomTopic) }
    var date by rememberSaveable { mutableStateOf(state.familyBoard.scheduledDateIso.ifBlank { LocalDate.now().plusDays(7).toString() }) }
    var time by rememberSaveable { mutableStateOf(state.familyBoard.scheduledTime24h) }
    var duration by rememberSaveable { mutableStateOf(state.familyBoard.durationMinutes.toString()) }
    var recurring by rememberSaveable { mutableStateOf(state.familyBoard.recurringWeekly) }
    val canOrganize = repository.canOrganize()
"""
new_state = """    var customTopic by rememberSaveable { mutableStateOf(state.familyBoard.selectedCustomTopic) }
    var date by rememberSaveable { mutableStateOf(state.familyBoard.scheduledDateIso.ifBlank { LocalDate.now().plusDays(7).toString() }) }
    var time by rememberSaveable { mutableStateOf(state.familyBoard.scheduledTime24h) }
    var duration by rememberSaveable { mutableStateOf(state.familyBoard.durationMinutes.toString()) }
    var recurring by rememberSaveable { mutableStateOf(state.familyBoard.recurringWeekly) }
    var scheduleMessage by rememberSaveable { mutableStateOf("") }
    var scheduleSaved by rememberSaveable { mutableStateOf(false) }
    val canOrganize = repository.canOrganize()

    LaunchedEffect(state.familyBoard.selectedCustomTopic) {
        customTopic = state.familyBoard.selectedCustomTopic
    }
    LaunchedEffect(
        state.familyBoard.scheduledDateIso,
        state.familyBoard.scheduledTime24h,
        state.familyBoard.durationMinutes,
        state.familyBoard.recurringWeekly,
    ) {
        date = state.familyBoard.scheduledDateIso.ifBlank { LocalDate.now().plusDays(7).toString() }
        time = state.familyBoard.scheduledTime24h
        duration = state.familyBoard.durationMinutes.toString()
        recurring = state.familyBoard.recurringWeekly
    }
"""
ui = replace_exact(ui, old_state, new_state, "family schedule UI state")
old_fields = """                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
"""
new_fields = """                    if (layoutSpec.widthClass == AdaptiveWidthClass.COMPACT) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                date,
                                { date = it; scheduleMessage = "" },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Date YYYY-MM-DD") },
                                enabled = canOrganize,
                                singleLine = true,
                            )
                            OutlinedTextField(
                                time,
                                { time = it; scheduleMessage = "" },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Time HH:MM") },
                                enabled = canOrganize,
                                singleLine = true,
                            )
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                date,
                                { date = it; scheduleMessage = "" },
                                modifier = Modifier.weight(1.2f),
                                label = { Text("Date YYYY-MM-DD") },
                                enabled = canOrganize,
                                singleLine = true,
                            )
                            OutlinedTextField(
                                time,
                                { time = it; scheduleMessage = "" },
                                modifier = Modifier.weight(.8f),
                                label = { Text("Time HH:MM") },
                                enabled = canOrganize,
                                singleLine = true,
                            )
                        }
                    }
                    OutlinedTextField(
                        duration,
                        { duration = it.filter(Char::isDigit).take(3); scheduleMessage = "" },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Expected minutes") },
                        enabled = canOrganize,
                        singleLine = true,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = recurring,
                            onCheckedChange = { recurring = it; scheduleMessage = "" },
                            enabled = canOrganize,
                        )
                        Text("Repeat weekly")
                    }
                    Button(
                        onClick = {
                            val enteredDuration = duration.toIntOrNull()
                            if (enteredDuration == null) {
                                scheduleSaved = false
                                scheduleMessage = "Enter the expected number of minutes."
                            } else {
                                val result = repository.scheduleFamilyWorship(date, time, enteredDuration, recurring)
                                scheduleSaved = result.saved
                                scheduleMessage = result.message
                                result.scheduledDateIso?.let { date = it }
                            }
                        },
                        enabled = canOrganize && date.isNotBlank() && time.isNotBlank() && duration.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Save family worship date and time")
                    }
                    if (scheduleMessage.isNotBlank()) {
                        Text(
                            scheduleMessage,
                            color = if (scheduleSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
"""
ui = replace_exact(ui, old_fields, new_fields, "family schedule controls")
ui_path.write_text(ui, encoding="utf-8")

widget = widget_path.read_text(encoding="utf-8")
old_load = """    val watchtower = OfficialWatchtowerStudyRepository(context).contentFor(snapshot.weekly.weekId)
    val official = if (fetchOfficialText) {
        OfficialDailyTextRepository(context).contentFor(snapshot.daily.date)
    } else {
        OfficialDailyTextRepository(context).cached(snapshot.daily.date)
    }
    return StudyWidgetData(
        snapshot = snapshot.copy(
            weekly = snapshot.weekly.withOfficialWatchtowerStudy(watchtower),
        ),
"""
new_load = """    val watchtower = runCatching {
        OfficialWatchtowerStudyRepository(context).contentFor(snapshot.weekly.weekId)
    }.getOrNull()
    val official = runCatching {
        if (fetchOfficialText) {
            OfficialDailyTextRepository(context).contentFor(snapshot.daily.date)
        } else {
            OfficialDailyTextRepository(context).cached(snapshot.daily.date)
        }
    }.getOrNull()
    return StudyWidgetData(
        snapshot = snapshot.copy(
            weekly = watchtower?.let { snapshot.weekly.withOfficialWatchtowerStudy(it) } ?: snapshot.weekly,
        ),
"""
widget = replace_exact(widget, old_load, new_load, "widget official-source fallback")
old_refresh = """        manager.getGlanceIds(DailyStudyWidget::class.java).forEach { id ->
            DailyStudyWidget().update(appContext, id)
        }
        manager.getGlanceIds(WeeklyStudyWidget::class.java).forEach { id ->
            WeeklyStudyWidget().update(appContext, id)
        }
        manager.getGlanceIds(FamilyWorshipWidget::class.java).forEach { id ->
            FamilyWorshipWidget().update(appContext, id)
        }
        manager.getGlanceIds(CoverCompanionWidget::class.java).forEach { id ->
            CoverCompanionWidget().update(appContext, id)
        }
"""
new_refresh = """        runCatching {
            manager.getGlanceIds(DailyStudyWidget::class.java).forEach { id ->
                DailyStudyWidget().update(appContext, id)
            }
        }
        runCatching {
            manager.getGlanceIds(WeeklyStudyWidget::class.java).forEach { id ->
                WeeklyStudyWidget().update(appContext, id)
            }
        }
        runCatching {
            manager.getGlanceIds(FamilyWorshipWidget::class.java).forEach { id ->
                FamilyWorshipWidget().update(appContext, id)
            }
        }
        runCatching {
            manager.getGlanceIds(CoverCompanionWidget::class.java).forEach { id ->
                CoverCompanionWidget().update(appContext, id)
            }
        }
"""
widget = replace_exact(widget, old_refresh, new_refresh, "independent widget refresh")
widget_path.write_text(widget, encoding="utf-8")

for name in (
    "daily_study_widget_info.xml",
    "weekly_study_widget_info.xml",
    "family_worship_widget_info.xml",
    "cover_companion_widget_info.xml",
    "samsung_cover_companion_widget_info.xml",
):
    path = xml_dir / name
    if not path.exists():
        continue
    xml = path.read_text(encoding="utf-8")
    if 'android:updatePeriodMillis="0"' not in xml:
        continue
    xml = replace_exact(xml, 'android:updatePeriodMillis="0"', 'android:updatePeriodMillis="1800000"', f"{name} periodic refresh")
    path.write_text(xml, encoding="utf-8")
PY

grep -Fq 'data class FamilyWorshipScheduleResult' "$REPOSITORY"
grep -Fq 'Choose a final family worship topic before saving' "$REPOSITORY"
grep -Fq 'FamilyWorshipReminderScheduler.schedule(appContext, normalizedDate, normalizedTime, recurringWeekly)' "$REPOSITORY"
grep -Fq 'exceptionOrNull()' "$REPOSITORY"
grep -Fq 'scheduleMessage by rememberSaveable' "$UI"
grep -Fq 'Time HH:MM' "$UI"
grep -Fq 'layoutSpec.widthClass == AdaptiveWidthClass.COMPACT' "$UI"
grep -Fq 'OfficialWatchtowerStudyRepository(context).contentFor' "$WIDGET"
grep -Fq 'watchtower?.let { snapshot.weekly.withOfficialWatchtowerStudy(it) }' "$WIDGET"
grep -Fq 'runCatching {' "$WIDGET"
! grep -Fq 'fun scheduleFamilyWorship(dateIso: String, time24h: String, durationMinutes: Int, recurringWeekly: Boolean) {' "$REPOSITORY"
! grep -Fq 'onClick = { repository.scheduleFamilyWorship(date, time, duration.toIntOrNull() ?: 60, recurring) }' "$UI"

for file in \
  "$XML_DIR/daily_study_widget_info.xml" \
  "$XML_DIR/weekly_study_widget_info.xml" \
  "$XML_DIR/family_worship_widget_info.xml" \
  "$XML_DIR/cover_companion_widget_info.xml"; do
  grep -Fq 'android:updatePeriodMillis="1800000"' "$file"
done

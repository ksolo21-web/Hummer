package com.kreativstudio.app.ui

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kreativstudio.app.KreativContainer
import com.kreativstudio.app.model.AiAdvice
import com.kreativstudio.app.model.AppSettings
import com.kreativstudio.app.model.AttachmentKind
import com.kreativstudio.app.model.BrushPreset
import com.kreativstudio.app.model.CanvasElement
import com.kreativstudio.app.model.CanvasLayer
import com.kreativstudio.app.model.JournalEntry
import com.kreativstudio.app.model.KreativProject
import com.kreativstudio.app.model.LessonMastery
import com.kreativstudio.app.model.LessonProgress
import com.kreativstudio.app.model.ProjectAttachment
import com.kreativstudio.app.model.StrokePoint
import com.kreativstudio.app.model.StudioThemeId
import com.kreativstudio.app.model.SyncState
import com.kreativstudio.app.model.ToolType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import java.util.UUID
import kotlin.math.hypot

enum class StudioScreen { HOME, STUDIO, LESSONS, GALLERY, MENTOR, SETTINGS }

class KreativViewModel(private val container: KreativContainer) : ViewModel() {
    val settings = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val projects = container.projectRepository.projects
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val user = container.authRepository.user
    val lessonProgress = container.lessonProgressRepository.progress
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val lessons = container.lessonRepository.lessons
    val onDeviceMentorState = container.aiMentorRepository.onDeviceState
    val isGoogleConfigured: Boolean get() = container.authRepository.isGoogleConfigured
    val isCloudConfigured: Boolean get() = container.cloudSyncRepository.isConfigured

    var screen by mutableStateOf(StudioScreen.HOME)
        private set
    var currentProject by mutableStateOf<KreativProject?>(null)
        private set
    var activeTool by mutableStateOf(ToolType.PENCIL)
    var activeColorArgb by mutableStateOf(0xFF2A2037L)
    var brushWidth by mutableFloatStateOf(10f)
    var brushOpacity by mutableFloatStateOf(1f)
    var stabilization by mutableFloatStateOf(.25f)
    var selectedLessonId by mutableStateOf<String?>(null)
        private set
    var lessonStepIndex by mutableStateOf(0)
        private set
    var aiAdvice by mutableStateOf<AiAdvice?>(null)
        private set
    var aiPrompt by mutableStateOf("")
    var isBusy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var inputStatus by mutableStateOf("Touch precision mode")
    var replayProgress by mutableFloatStateOf(1f)

    private val undoStack = ArrayDeque<KreativProject>()
    private val redoStack = ArrayDeque<KreativProject>()
    private var autosaveJob: Job? = null

    init {
        viewModelScope.launch { container.aiMentorRepository.refreshOnDeviceStatus() }
    }

    fun navigate(target: StudioScreen) {
        screen = target
    }

    fun dismissMessage() {
        message = null
    }

    fun showMessage(text: String) {
        message = text
    }

    fun showHandHealthReminder() {
        message = "Creative pause: relax your grip, roll your shoulders, and look across the room before the next stroke."
    }

    fun useOliviaPreview() {
        container.authRepository.useOliviaPreview()
        message = "Olivia's Royal Owl studio is ready."
    }

    fun useGuestStudio() {
        container.authRepository.useGuestStudio()
    }

    fun signInWithGoogle(activity: Activity) {
        viewModelScope.launch {
            isBusy = true
            container.authRepository.signInWithGoogle(activity)
                .onSuccess { signedIn ->
                    message = "Welcome home, ${signedIn.displayName}."
                    restoreFromCloud()
                }
                .onFailure { message = it.message ?: "Google sign-in failed." }
            isBusy = false
        }
    }

    fun signOut() {
        viewModelScope.launch {
            container.authRepository.signOut()
            currentProject = null
            screen = StudioScreen.HOME
        }
    }

    fun createProject(
        title: String,
        width: Int = 2048,
        height: Int = 2048,
        background: Long = 0xFFFFFFFF,
        lessonId: String? = null,
    ) {
        viewModelScope.launch {
            isBusy = true
            runCatching {
                container.projectRepository.create(title, width, height, background, lessonId)
            }.onSuccess {
                openProjectInternal(it)
                screen = StudioScreen.STUDIO
            }.onFailure { message = it.message ?: "Could not create the project." }
            isBusy = false
        }
    }

    fun openProject(projectId: String) {
        viewModelScope.launch {
            isBusy = true
            runCatching { container.projectRepository.get(projectId) }
                .onSuccess { project ->
                    if (project != null) {
                        openProjectInternal(project)
                        screen = StudioScreen.STUDIO
                    }
                }
                .onFailure { message = it.message ?: "Could not open the project." }
            isBusy = false
        }
    }

    fun duplicateProject(projectId: String) {
        viewModelScope.launch {
            container.projectRepository.duplicate(projectId)
                ?.let { message = "Created ${it.title}." }
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            container.projectRepository.delete(projectId)
            if (currentProject?.id == projectId) currentProject = null
            message = "Project deleted."
        }
    }

    fun renameProject(title: String) = changeProject { it.copy(title = title.ifBlank { "Untitled Artwork" }) }

    fun addElements(elements: List<CanvasElement>) {
        val project = currentProject ?: return
        if (project.layers.firstOrNull { it.id == project.activeLayerId }?.isLocked == true) {
            message = "That layer is locked."
            return
        }
        changeProject { it.copy(elements = it.elements + elements) }
    }

    fun addText(text: String, at: StrokePoint) {
        val project = currentProject ?: return
        if (text.isBlank()) return
        if (project.layers.firstOrNull { it.id == project.activeLayerId }?.isLocked == true) {
            message = "That layer is locked."
            return
        }
        val element = CanvasElement(
            layerId = project.activeLayerId,
            tool = ToolType.TEXT,
            points = listOf(at),
            colorArgb = activeColorArgb,
            width = brushWidth.coerceIn(14f, 360f),
            opacity = brushOpacity,
            stabilization = 0f,
            text = text.trim(),
        )
        changeProject { it.copy(elements = it.elements + element) }
    }

    fun transformElement(updated: CanvasElement) {
        val project = currentProject ?: return
        val layer = project.layers.firstOrNull { it.id == updated.layerId } ?: return
        if (layer.isLocked) {
            message = "That layer is locked."
            return
        }
        changeProject { current ->
            current.copy(elements = current.elements.map { if (it.id == updated.id) updated else it })
        }
    }

    fun erase(points: List<StrokePoint>, width: Float) {
        val project = currentProject ?: return
        if (project.layers.firstOrNull { it.id == project.activeLayerId }?.isLocked == true) {
            message = "That layer is locked."
            return
        }
        changeProject { project ->
            if (points.isEmpty()) return@changeProject project
            val radius = width * 1.25f
            val kept = project.elements.filterNot { element ->
                element.layerId == project.activeLayerId && element.points.any { ep ->
                    points.any { eraser ->
                        hypot((ep.x - eraser.x).toDouble(), (ep.y - eraser.y).toDouble()) <= radius
                    }
                }
            }
            project.copy(elements = kept)
        }
    }

    fun fillBackground(argb: Long) = changeProject { it.copy(backgroundArgb = argb) }

    fun undo() {
        if (undoStack.isEmpty()) return
        currentProject?.let { redoStack.addLast(it) }
        currentProject = undoStack.removeLast()
        scheduleSave()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        currentProject?.let { undoStack.addLast(it) }
        currentProject = redoStack.removeLast()
        scheduleSave()
    }

    fun addLayer() = changeProject { project ->
        val layer = CanvasLayer(name = "Layer ${project.layers.size + 1}")
        project.copy(layers = project.layers + layer, activeLayerId = layer.id)
    }

    fun selectLayer(layerId: String) {
        currentProject = currentProject?.copy(activeLayerId = layerId)
    }

    fun toggleLayerVisibility(layerId: String) = changeProject { project ->
        project.copy(layers = project.layers.map { if (it.id == layerId) it.copy(isVisible = !it.isVisible) else it })
    }

    fun toggleLayerLock(layerId: String) = changeProject { project ->
        project.copy(layers = project.layers.map { if (it.id == layerId) it.copy(isLocked = !it.isLocked) else it })
    }

    fun deleteLayer(layerId: String) = changeProject { project ->
        if (project.layers.size <= 1) return@changeProject project
        val layers = project.layers.filterNot { it.id == layerId }
        project.copy(
            layers = layers,
            activeLayerId = if (project.activeLayerId == layerId) layers.first().id else project.activeLayerId,
            elements = project.elements.filterNot { it.layerId == layerId },
        )
    }

    fun addAttachments(context: Context, uris: List<Uri>, kind: AttachmentKind = AttachmentKind.REFERENCE) {
        if (uris.isEmpty()) return
        val attachments = uris.map { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            ProjectAttachment(
                uri = uri.toString(),
                displayName = displayName(context, uri),
                mimeType = context.contentResolver.getType(uri),
                kind = kind,
            )
        }
        changeProject { it.copy(attachments = it.attachments + attachments) }
    }

    fun removeAttachment(id: String) = changeProject { project ->
        project.copy(attachments = project.attachments.filterNot { it.id == id })
    }

    fun addJournalEntry(text: String) {
        if (text.isBlank()) return
        changeProject { it.copy(journal = it.journal + JournalEntry(text = text.trim())) }
    }

    fun saveBrushPreset(name: String) {
        val p = currentProject ?: return
        val preset = BrushPreset(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Custom Brush" },
            tool = activeTool,
            width = brushWidth,
            opacity = brushOpacity,
            stabilization = stabilization,
            textureUri = p.attachments.lastOrNull { it.kind == AttachmentKind.TEXTURE }?.uri,
        )
        changeProject { it.copy(brushPresets = it.brushPresets + preset) }
    }

    fun applyBrush(preset: BrushPreset) {
        activeTool = preset.tool
        brushWidth = preset.width
        brushOpacity = preset.opacity
        stabilization = preset.stabilization
    }

    fun startLesson(lessonId: String) {
        selectedLessonId = lessonId
        val progress = lessonProgress.value.firstOrNull { it.lessonId == lessonId }
        val lesson = lessons.firstOrNull { it.id == lessonId }
        lessonStepIndex = lesson?.steps?.indices
            ?.firstOrNull { it !in progress?.masteredStepIndices.orEmpty() }
            ?: 0
        val project = currentProject
        if (project == null || project.lessonId != lessonId) {
            createProject(
                title = lesson?.title ?: "Lesson Practice",
                width = 1800,
                height = 2400,
                background = 0xFFF8F3EAL,
                lessonId = lessonId,
            )
        } else {
            screen = StudioScreen.STUDIO
        }
        viewModelScope.launch {
            container.lessonProgressRepository.markStep(lessonId, progress?.completedSteps ?: 0, attempted = true)
            backupUserState()
        }
    }

    fun nextLessonStep() {
        val lesson = lessons.firstOrNull { it.id == selectedLessonId } ?: return
        lessonStepIndex = (lessonStepIndex + 1).coerceAtMost(lesson.steps.lastIndex)
    }

    fun previousLessonStep() {
        lessonStepIndex = (lessonStepIndex - 1).coerceAtLeast(0)
    }

    fun recordLessonAssessment(mastery: LessonMastery) {
        val lessonId = selectedLessonId ?: currentProject?.lessonId ?: return
        val step = lessonStepIndex
        viewModelScope.launch {
            container.lessonProgressRepository.recordAssessment(lessonId, step, mastery)
            backupUserState()
            message = when (mastery) {
                LessonMastery.READY_TO_ADVANCE -> "Step mastered. You are ready to advance."
                LessonMastery.NEEDS_PRACTICE -> "This step is saved as Needs Practice. Use the correction, revise, and check again."
                LessonMastery.NOT_ASSESSED -> "The step was not assessed."
            }
        }
    }

    fun requestAiAdvice(prompt: String = aiPrompt) {
        viewModelScope.launch {
            isBusy = true
            container.aiMentorRepository.advise(prompt, currentProject, settings.value.aiLocalFirst)
                .onSuccess { aiAdvice = it }
                .onFailure { message = it.message ?: "KREATIV Mentor could not respond." }
            isBusy = false
        }
    }

    fun prepareOnDeviceMentor() {
        viewModelScope.launch {
            container.aiMentorRepository.downloadOnDeviceModel()
        }
    }

    fun refreshOnDeviceMentorStatus() {
        viewModelScope.launch {
            container.aiMentorRepository.refreshOnDeviceStatus()
        }
    }

    fun syncCurrentProject() {
        val project = currentProject ?: return
        viewModelScope.launch {
            isBusy = true
            container.cloudSyncRepository.upload(project)
                .onSuccess { synced ->
                    currentProject = container.projectRepository.save(synced)
                    backupUserState()
                    message = "Project safely synced."
                }
                .onFailure { message = it.message ?: "Cloud sync failed." }
            isBusy = false
        }
    }

    fun syncAllUserData() {
        if (!isCloudConfigured) {
            message = "Sign in with the configured Google account before syncing the full studio."
            return
        }
        viewModelScope.launch {
            isBusy = true
            runCatching {
                projects.value.forEach { project ->
                    val synced = container.cloudSyncRepository.upload(project).getOrThrow()
                    val saved = container.projectRepository.save(synced)
                    if (currentProject?.id == saved.id) currentProject = saved
                }
                backupUserState()
            }.onSuccess {
                message = "Every local project, lesson milestone, and studio setting is backed up."
            }.onFailure {
                message = it.message ?: "Full studio sync failed."
            }
            isBusy = false
        }
    }

    fun saveNow() {
        val project = currentProject ?: return
        viewModelScope.launch {
            currentProject = container.projectRepository.save(project)
            message = "Saved locally."
        }
    }

    fun exportProject(uri: Uri) {
        val project = currentProject ?: return
        viewModelScope.launch {
            isBusy = true
            runCatching { container.projectExporter.exportProject(project, uri) }
                .onSuccess { message = "KREATIV project exported." }
                .onFailure { message = it.message ?: "Project export failed." }
            isBusy = false
        }
    }

    fun exportPng(uri: Uri) {
        val project = currentProject ?: return
        viewModelScope.launch {
            isBusy = true
            runCatching { container.projectExporter.exportPng(project, uri) }
                .onSuccess { message = "PNG exported." }
                .onFailure { message = it.message ?: "PNG export failed." }
            isBusy = false
        }
    }

    fun importProject(uri: Uri) {
        viewModelScope.launch {
            isBusy = true
            runCatching {
                val imported = container.projectExporter.importProject(uri)
                container.projectRepository.importJson(container.projectRepository.encode(imported))
            }.onSuccess {
                openProjectInternal(it)
                screen = StudioScreen.STUDIO
                message = "Project imported."
            }.onFailure { message = it.message ?: "Project import failed." }
            isBusy = false
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            val updated = transform(settings.value)
            container.settingsRepository.update { updated }
            if (isCloudConfigured) {
                container.cloudSyncRepository.backupUserState(updated, lessonProgress.value)
            }
        }
    }

    fun setTheme(theme: StudioThemeId) = updateSettings { it.copy(themeId = theme) }

    private suspend fun restoreFromCloud() {
        if (!isCloudConfigured) return
        val restoredProjects = container.cloudSyncRepository.restoreProjects().getOrElse {
            message = "Signed in, but cloud artwork could not be restored yet: ${it.message ?: "unknown error"}"
            emptyList()
        }
        restoredProjects.forEach { remote ->
            val local = container.projectRepository.get(remote.id)
            if (local == null || remote.updatedAt > local.updatedAt) {
                container.projectRepository.save(remote.copy(syncState = SyncState.SYNCED))
            }
        }
        container.cloudSyncRepository.restoreUserState().getOrNull()?.let { cloudState ->
            cloudState.settings?.let { restored -> container.settingsRepository.update { restored } }
            if (cloudState.progress.isNotEmpty()) {
                val merged = (lessonProgress.value + cloudState.progress)
                    .groupBy { it.lessonId }
                    .map { (lessonId, entries) ->
                        val mastered = entries.flatMap { it.masteredStepIndices }.toSet()
                        val needsPractice = entries.flatMap { it.needsPracticeStepIndices }.toSet() - mastered
                        LessonProgress(
                            lessonId = lessonId,
                            completedSteps = mastered.size,
                            attempts = entries.maxOfOrNull { it.attempts } ?: 0,
                            masteredStepIndices = mastered,
                            needsPracticeStepIndices = needsPractice,
                            lastOpenedAt = entries.maxOfOrNull { it.lastOpenedAt } ?: System.currentTimeMillis(),
                        )
                    }
                container.lessonProgressRepository.replaceAll(merged)
            }
        }
        if (restoredProjects.isNotEmpty()) {
            message = "Welcome home. ${restoredProjects.size} cloud project${if (restoredProjects.size == 1) "" else "s"} checked and restored."
        }
    }

    private suspend fun backupUserState() {
        if (!isCloudConfigured) return
        container.cloudSyncRepository.backupUserState(settings.value, lessonProgress.value)
    }

    private fun openProjectInternal(project: KreativProject) {
        currentProject = project
        selectedLessonId = project.lessonId
        lessonStepIndex = project.lessonId?.let { lessonId ->
            val lesson = lessons.firstOrNull { it.id == lessonId }
            val mastered = lessonProgress.value.firstOrNull { it.lessonId == lessonId }?.masteredStepIndices.orEmpty()
            lesson?.steps?.indices?.firstOrNull { it !in mastered } ?: 0
        } ?: 0
        undoStack.clear()
        redoStack.clear()
        replayProgress = 1f
    }

    private fun changeProject(transform: (KreativProject) -> KreativProject) {
        val before = currentProject ?: return
        val after = transform(before)
        if (after == before) return
        undoStack.addLast(before)
        while (undoStack.size > 80) undoStack.removeFirst()
        redoStack.clear()
        currentProject = after.copy(syncState = SyncState.PENDING, updatedAt = System.currentTimeMillis())
        scheduleSave()
    }

    private fun scheduleSave() {
        autosaveJob?.cancel()
        val project = currentProject ?: return
        autosaveJob = viewModelScope.launch {
            delay(settings.value.autosaveSeconds.coerceIn(2, 60) * 1000L)
            currentProject = container.projectRepository.save(currentProject ?: project)
        }
    }

    private fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment ?: "Attachment"
    }
}

class KreativViewModelFactory(private val container: KreativContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(KreativViewModel::class.java)) {
            return KreativViewModel(container) as T
        }
        error("Unknown ViewModel class: ${modelClass.name}")
    }
}

package com.kreativstudio.app.data

import android.content.Context
import com.kreativstudio.app.model.LessonProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

class LessonProgressRepository(context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val file = File(context.filesDir, "lesson_progress.json")
    private val state = MutableStateFlow(load())

    val progress: Flow<List<LessonProgress>> = state.asStateFlow()

    suspend fun replaceAll(progress: List<LessonProgress>) = withContext(Dispatchers.IO) {
        state.value = progress
            .groupBy { it.lessonId }
            .map { (_, entries) -> entries.maxBy { it.lastOpenedAt } }
            .sortedByDescending { it.lastOpenedAt }
        persist()
    }

    suspend fun markStep(lessonId: String, completedSteps: Int, attempted: Boolean = false) = withContext(Dispatchers.IO) {
        val current = state.value.firstOrNull { it.lessonId == lessonId }
        val next = LessonProgress(
            lessonId = lessonId,
            completedSteps = maxOf(current?.completedSteps ?: 0, completedSteps),
            attempts = (current?.attempts ?: 0) + if (attempted) 1 else 0,
            lastOpenedAt = System.currentTimeMillis(),
        )
        state.value = state.value.filterNot { it.lessonId == lessonId } + next
        persist()
    }

    private fun load(): List<LessonProgress> = runCatching {
        if (!file.exists()) emptyList()
        else json.decodeFromString(ListSerializer(LessonProgress.serializer()), file.readText())
    }.getOrDefault(emptyList())

    private fun persist() {
        file.writeText(json.encodeToString(ListSerializer(LessonProgress.serializer()), state.value))
    }
}

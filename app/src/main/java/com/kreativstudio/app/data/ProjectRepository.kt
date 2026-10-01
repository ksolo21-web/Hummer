package com.kreativstudio.app.data

import android.content.Context
import com.kreativstudio.app.model.KreativProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

interface ProjectRepository {
    val projects: Flow<List<KreativProject>>
    suspend fun create(
        title: String = "Untitled Artwork",
        widthPx: Int = 2048,
        heightPx: Int = 2048,
        backgroundArgb: Long = 0xFFFFFFFF,
        lessonId: String? = null,
    ): KreativProject
    suspend fun save(project: KreativProject): KreativProject
    suspend fun delete(projectId: String)
    suspend fun get(projectId: String): KreativProject?
    suspend fun duplicate(projectId: String): KreativProject?
    suspend fun importJson(raw: String): KreativProject
    fun encode(project: KreativProject): String
}

class FileProjectRepository(context: Context) : ProjectRepository {
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true; encodeDefaults = true }
    private val root = File(context.filesDir, "projects").apply { mkdirs() }
    private val state = MutableStateFlow(loadAll())

    override val projects: Flow<List<KreativProject>> = state.asStateFlow()

    override suspend fun create(
        title: String,
        widthPx: Int,
        heightPx: Int,
        backgroundArgb: Long,
        lessonId: String?,
    ): KreativProject = withContext(Dispatchers.IO) {
        val project = KreativProject(
            title = title.ifBlank { "Untitled Artwork" },
            widthPx = widthPx.coerceIn(256, 8192),
            heightPx = heightPx.coerceIn(256, 8192),
            backgroundArgb = backgroundArgb,
            lessonId = lessonId,
        )
        write(project)
        refresh()
        project
    }

    override suspend fun save(project: KreativProject): KreativProject = withContext(Dispatchers.IO) {
        val saved = project.copy(updatedAt = System.currentTimeMillis())
        write(saved)
        refresh()
        saved
    }

    override suspend fun delete(projectId: String) = withContext(Dispatchers.IO) {
        File(root, "$projectId.kreativ.json").delete()
        refresh()
    }

    override suspend fun get(projectId: String): KreativProject? = withContext(Dispatchers.IO) {
        val file = File(root, "$projectId.kreativ.json")
        if (!file.exists()) null else runCatching { json.decodeFromString<KreativProject>(file.readText()) }.getOrNull()
    }

    override suspend fun duplicate(projectId: String): KreativProject? = withContext(Dispatchers.IO) {
        val original = get(projectId) ?: return@withContext null
        val copy = original.copy(
            id = java.util.UUID.randomUUID().toString(),
            title = "${original.title} Copy",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        write(copy)
        refresh()
        copy
    }

    override suspend fun importJson(raw: String): KreativProject = withContext(Dispatchers.IO) {
        val parsed = json.decodeFromString<KreativProject>(raw)
        val imported = parsed.copy(
            id = java.util.UUID.randomUUID().toString(),
            title = parsed.title.ifBlank { "Imported Artwork" },
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        write(imported)
        refresh()
        imported
    }

    override fun encode(project: KreativProject): String = json.encodeToString(project)

    private fun write(project: KreativProject) {
        val target = File(root, "${project.id}.kreativ.json")
        val temp = File(root, "${project.id}.tmp")
        temp.writeText(json.encodeToString(project))
        if (!temp.renameTo(target)) {
            target.writeText(temp.readText())
            temp.delete()
        }
    }

    private fun loadAll(): List<KreativProject> = root.listFiles()
        ?.filter { it.name.endsWith(".kreativ.json") }
        ?.mapNotNull { runCatching { json.decodeFromString<KreativProject>(it.readText()) }.getOrNull() }
        ?.sortedByDescending { it.updatedAt }
        .orEmpty()

    private fun refresh() {
        state.value = loadAll()
    }
}

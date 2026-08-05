package com.kreativstudio.app.cloud

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.kreativstudio.app.model.AppSettings
import com.kreativstudio.app.model.KreativProject
import com.kreativstudio.app.model.LessonProgress
import com.kreativstudio.app.model.ProjectAttachment
import com.kreativstudio.app.model.SyncState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Cloud backup that keeps the local studio authoritative while making signed-in recovery possible. */
class CloudSyncRepository(
    private val context: Context,
    private val firebaseReady: Boolean,
) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val appCheckMutex = Mutex()

    @Volatile
    private var appCheckTokenReady = false

    val isConfigured: Boolean
        get() = firebaseReady && runCatching { FirebaseAuth.getInstance().currentUser != null }.getOrDefault(false)

    suspend fun upload(project: KreativProject): Result<KreativProject> = runCatching {
        val userId = requireCloudSession()
        val storage = FirebaseStorage.getInstance()
        val cloudAttachments = project.attachments.map { attachment ->
            backupAttachment(storage, userId, project.id, attachment)
        }
        val cloudCopy = project.copy(
            attachments = cloudAttachments,
            syncState = SyncState.SYNCED,
            updatedAt = System.currentTimeMillis(),
        )
        val bytes = json.encodeToString(cloudCopy).encodeToByteArray()
        val storagePath = "users/$userId/projects/${project.id}.kreativ.json"
        storage.reference.child(storagePath).putBytes(bytes).await()
        FirebaseFirestore.getInstance()
            .collection("users").document(userId)
            .collection("projects").document(project.id)
            .set(
                mapOf(
                    "title" to project.title,
                    "updatedAt" to cloudCopy.updatedAt,
                    "widthPx" to project.widthPx,
                    "heightPx" to project.heightPx,
                    "storagePath" to storagePath,
                    "lessonId" to project.lessonId,
                    "attachmentCount" to project.attachments.size,
                    "schemaVersion" to 1,
                )
            ).await()
        project.copy(syncState = SyncState.SYNCED, updatedAt = cloudCopy.updatedAt)
    }

    suspend fun restoreProjects(): Result<List<KreativProject>> = runCatching {
        val userId = requireCloudSession()
        val snapshot = FirebaseFirestore.getInstance()
            .collection("users").document(userId)
            .collection("projects")
            .get().await()
        val storage = FirebaseStorage.getInstance()
        snapshot.documents.mapNotNull { document ->
            val storagePath = document.getString("storagePath") ?: return@mapNotNull null
            runCatching {
                val bytes = storage.reference.child(storagePath).getBytes(MAX_PROJECT_BYTES).await()
                val cloudProject = json.decodeFromString<KreativProject>(bytes.decodeToString())
                val restoredAttachments = cloudProject.attachments.map { attachment ->
                    restoreAttachment(storage, cloudProject.id, attachment)
                }
                cloudProject.copy(
                    attachments = restoredAttachments,
                    syncState = SyncState.SYNCED,
                )
            }.getOrNull()
        }.sortedByDescending { it.updatedAt }
    }

    suspend fun backupUserState(
        settings: AppSettings,
        progress: List<LessonProgress>,
    ): Result<Unit> = runCatching {
        val userId = requireCloudSession()
        FirebaseFirestore.getInstance()
            .collection("users").document(userId)
            .collection("private").document("studioState")
            .set(
                mapOf(
                    "settingsJson" to json.encodeToString(settings),
                    "progressJson" to json.encodeToString(ListSerializer(LessonProgress.serializer()), progress),
                    "updatedAt" to System.currentTimeMillis(),
                    "schemaVersion" to 1,
                )
            ).await()
        Unit
    }

    suspend fun restoreUserState(): Result<CloudUserState?> = runCatching {
        val userId = requireCloudSession()
        val document = FirebaseFirestore.getInstance()
            .collection("users").document(userId)
            .collection("private").document("studioState")
            .get().await()
        if (!document.exists()) return@runCatching null
        val settings = document.getString("settingsJson")
            ?.let { runCatching { json.decodeFromString<AppSettings>(it) }.getOrNull() }
        val progress = document.getString("progressJson")
            ?.let {
                runCatching {
                    json.decodeFromString(ListSerializer(LessonProgress.serializer()), it)
                }.getOrDefault(emptyList())
            }.orEmpty()
        CloudUserState(settings = settings, progress = progress)
    }

    private suspend fun backupAttachment(
        storage: FirebaseStorage,
        userId: String,
        projectId: String,
        attachment: ProjectAttachment,
    ): ProjectAttachment {
        if (attachment.uri.startsWith("firebase-storage://")) return attachment
        val localUri = runCatching { Uri.parse(attachment.uri) }.getOrNull() ?: return attachment
        val safeName = attachment.displayName
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .take(120)
            .ifBlank { "attachment" }
        val storagePath = "users/$userId/attachments/$projectId/${attachment.id}_$safeName"
        return runCatching {
            context.contentResolver.openInputStream(localUri)?.use { input ->
                storage.reference.child(storagePath).putStream(input).await()
            } ?: error("The attachment could not be read.")
            attachment.copy(uri = "firebase-storage://$storagePath")
        }.getOrElse { attachment }
    }

    private suspend fun restoreAttachment(
        storage: FirebaseStorage,
        projectId: String,
        attachment: ProjectAttachment,
    ): ProjectAttachment {
        if (!attachment.uri.startsWith("firebase-storage://")) return attachment
        val storagePath = attachment.uri.removePrefix("firebase-storage://")
        if (storagePath.isBlank()) return attachment
        val safeName = attachment.displayName
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .take(120)
            .ifBlank { "attachment" }
        val directory = File(context.filesDir, "cloud_attachments/$projectId").apply { mkdirs() }
        val target = File(directory, "${attachment.id}_$safeName")
        return runCatching {
            storage.reference.child(storagePath).getFile(target).await()
            val localUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.files",
                target,
            )
            attachment.copy(uri = localUri.toString())
        }.getOrElse { attachment }
    }

    private suspend fun requireCloudSession(): String {
        val userId = requireUserId()
        ensureFreshAppCheckToken()
        return userId
    }

    private suspend fun ensureFreshAppCheckToken() {
        if (appCheckTokenReady) return
        appCheckMutex.withLock {
            if (appCheckTokenReady) return@withLock
            FirebaseAppCheck.getInstance().getAppCheckToken(true).await()
            appCheckTokenReady = true
        }
    }

    private fun requireUserId(): String {
        check(firebaseReady) { "Cloud sync needs Firebase configuration." }
        return requireNotNull(FirebaseAuth.getInstance().currentUser) { "Sign in before syncing." }.uid
    }

    companion object {
        private const val MAX_PROJECT_BYTES = 100L * 1024L * 1024L
    }
}

data class CloudUserState(
    val settings: AppSettings?,
    val progress: List<LessonProgress>,
)

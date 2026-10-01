package com.kreativstudio.app.cloud

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.kreativstudio.app.model.AppSettings
import com.kreativstudio.app.model.KreativProject
import com.kreativstudio.app.model.LessonProgress
import com.kreativstudio.app.model.ProjectAttachment
import com.kreativstudio.app.model.SyncState
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

    @Volatile
    var lastFailureMessage: String? = null
        private set

    val isConfigured: Boolean
        get() = firebaseReady && runCatching { FirebaseAuth.getInstance().currentUser != null }.getOrDefault(false)

    suspend fun upload(project: KreativProject): Result<KreativProject> = cloudOperation {
        val userId = requireUserId()
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
        val storagePath = "users/$userId/kreativStudio/projects/${project.id}.kreativ.json"
        storage.reference.child(storagePath).putBytes(bytes).await()
        FirebaseFirestore.getInstance()
            .collection("users").document(userId)
            .collection(KREATIV_PROJECTS_COLLECTION).document(project.id)
            .set(
                mapOf(
                    "title" to project.title,
                    "updatedAt" to cloudCopy.updatedAt,
                    "widthPx" to project.widthPx,
                    "heightPx" to project.heightPx,
                    "storagePath" to storagePath,
                    "lessonId" to project.lessonId,
                    "attachmentCount" to project.attachments.size,
                    "schemaVersion" to 2,
                )
            ).await()
        project.copy(syncState = SyncState.SYNCED, updatedAt = cloudCopy.updatedAt)
    }

    suspend fun restoreProjects(): Result<List<KreativProject>> = cloudOperation {
        val userId = requireUserId()
        val snapshot = FirebaseFirestore.getInstance()
            .collection("users").document(userId)
            .collection(KREATIV_PROJECTS_COLLECTION)
            .get().await()
        val storage = FirebaseStorage.getInstance()

        // Verify Storage upload/read/delete before the UI enables cloud backup.
        verifyStorageAccess(storage, userId)

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
    ): Result<Unit> = cloudOperation {
        val userId = requireUserId()
        FirebaseFirestore.getInstance()
            .collection("users").document(userId)
            .collection(KREATIV_PRIVATE_COLLECTION).document("studioState")
            .set(
                mapOf(
                    "settingsJson" to json.encodeToString(settings),
                    "progressJson" to json.encodeToString(ListSerializer(LessonProgress.serializer()), progress),
                    "updatedAt" to System.currentTimeMillis(),
                    "schemaVersion" to 2,
                )
            ).await()
        Unit
    }

    suspend fun restoreUserState(): Result<CloudUserState?> = cloudOperation {
        val userId = requireUserId()
        val reference = FirebaseFirestore.getInstance()
            .collection("users").document(userId)
            .collection(KREATIV_PRIVATE_COLLECTION).document("studioState")

        // Merge a harmless field so connection success proves Firestore write and read access.
        reference.set(
            mapOf(
                "lastConnectionCheckAt" to System.currentTimeMillis(),
                "schemaVersion" to 2,
            ),
            SetOptions.merge(),
        ).await()

        val document = reference.get().await()
        if (!document.exists()) return@cloudOperation null
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

    private suspend fun verifyStorageAccess(storage: FirebaseStorage, userId: String) {
        val probe = storage.reference.child("users/$userId/kreativStudio/private/connectivity.probe")
        probe.putBytes("KREATIV_CLOUD_CHECK".encodeToByteArray()).await()
        try {
            probe.getMetadata().await()
        } finally {
            try {
                probe.delete().await()
            } catch (_: Throwable) {
                // Upload plus metadata read already proved access; a stale probe is harmless.
            }
        }
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
        val storagePath = "users/$userId/kreativStudio/attachments/$projectId/${attachment.id}_$safeName"
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

    private suspend fun <T> cloudOperation(block: suspend () -> T): Result<T> {
        return try {
            val value = block()
            lastFailureMessage = null
            Result.success(value)
        } catch (error: Throwable) {
            lastFailureMessage = describeFailure(error)
            Result.failure(error)
        }
    }

    private fun describeFailure(error: Throwable): String {
        val causes = generateSequence(error as Throwable?) { it.cause }.toList()
        causes.filterIsInstance<FirebaseFirestoreException>().firstOrNull()?.let { firestore ->
            return when (firestore.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    "Firestore rejected this signed-in account (PERMISSION_DENIED)."
                FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                    "Firestore did not receive a valid Firebase sign-in session (UNAUTHENTICATED)."
                FirebaseFirestoreException.Code.UNAVAILABLE ->
                    "Firestore is temporarily unavailable. Check the internet connection and retry."
                else -> "Firestore connection failed (${firestore.code.name})."
            }
        }
        causes.filterIsInstance<StorageException>().firstOrNull()?.let { storage ->
            return when (storage.errorCode) {
                StorageException.ERROR_NOT_AUTHENTICATED ->
                    "Cloud Storage did not receive a valid Firebase sign-in session."
                StorageException.ERROR_NOT_AUTHORIZED ->
                    "Cloud Storage rejected this signed-in account."
                StorageException.ERROR_RETRY_LIMIT_EXCEEDED ->
                    "Cloud Storage could not connect after several retries."
                else -> "Cloud Storage connection failed (code ${storage.errorCode})."
            }
        }
        val detail = causes.firstNotNullOfOrNull { it.message?.trim()?.takeIf(String::isNotEmpty) }
        return if (detail != null) {
            "Firebase connection failed: ${detail.take(180)}"
        } else {
            "Firebase connection failed (${error::class.java.simpleName})."
        }
    }

    private fun requireUserId(): String {
        check(firebaseReady) { "Cloud sync needs Firebase configuration." }
        return requireNotNull(FirebaseAuth.getInstance().currentUser) { "Sign in before syncing." }.uid
    }

    companion object {
        private const val MAX_PROJECT_BYTES = 100L * 1024L * 1024L
        private const val KREATIV_PROJECTS_COLLECTION = "kreativProjects"
        private const val KREATIV_PRIVATE_COLLECTION = "kreativPrivate"
    }
}

data class CloudUserState(
    val settings: AppSettings?,
    val progress: List<LessonProgress>,
)

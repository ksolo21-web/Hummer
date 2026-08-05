package com.kreativstudio.app.cloud

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.kreativstudio.app.model.AppSettings
import com.kreativstudio.app.model.KreativProject
import com.kreativstudio.app.model.LessonProgress
import com.kreativstudio.app.model.ProjectAttachment
import com.kreativstudio.app.model.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * User-owned cloud backup through Android's document provider.
 * Choosing Google Drive once gives KREATIV durable access to one backup file.
 * This avoids dependence on the shared Firebase project's broken rules.
 */
class DocumentCloudBackupRepository(private val context: Context) {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    @Volatile
    var lastFailureMessage: String? = null
        private set

    val isConnected: Boolean
        get() = connectedUri() != null

    suspend fun connectAndBackup(
        uri: Uri,
        projects: List<KreativProject>,
        settings: AppSettings,
        progress: List<LessonProgress>,
    ): Result<Unit> = operation {
        persistPermission(uri, write = true)
        writeArchive(uri, projects, settings, progress)
        preferences.edit().putString(KEY_BACKUP_URI, uri.toString()).apply()
    }

    suspend fun backup(
        projects: List<KreativProject>,
        settings: AppSettings,
        progress: List<LessonProgress>,
    ): Result<Unit> = operation {
        val uri = connectedUri()
  ?: error("Choose a Google Drive backup file before backing up the studio.")
        writeArchive(uri, projects, settings, progress)
    }

    suspend fun restoreConnected(): Result<DocumentStudioBackup> = operation {
        val uri = connectedUri()
  ?: error("No Google Drive backup file is connected.")
        readArchive(uri)
    }

    suspend fun restoreFrom(uri: Uri): Result<DocumentStudioBackup> = operation {
        persistPermission(uri, write = false)
        readArchive(uri)
    }

    private fun connectedUri(): Uri? = preferences
        .getString(KEY_BACKUP_URI, null)
        ?.takeIf { it.isNotBlank() }
        ?.let(Uri::parse)

    private fun persistPermission(uri: Uri, write: Boolean) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
  (if (write) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        try {
  context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (error: SecurityException) {
  throw IllegalStateException(
      "The cloud provider did not grant lasting access. Choose the backup file again.",
      error,
  )
        }
    }

    private suspend fun writeArchive(
        uri: Uri,
        projects: List<KreativProject>,
        settings: AppSettings,
        progress: List<LessonProgress>,
    ) = withContext(Dispatchers.IO) {
        val output = context.contentResolver.openOutputStream(uri, "wt")
  ?: error("The selected cloud backup file could not be opened for writing.")
        output.use { raw ->
  ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
      val portableProjects = projects.map { project ->
          project.copy(
              attachments = project.attachments.map { attachment ->
                  embedAttachment(zip, project.id, attachment)
              },
              syncState = SyncState.SYNCED,
          )
      }
      val manifest = DocumentStudioBackup(
          createdAt = System.currentTimeMillis(),
          settings = settings,
          progress = progress,
          projects = portableProjects,
      )
      zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
      zip.write(json.encodeToString(manifest).encodeToByteArray())
      zip.closeEntry()
  }
        }
    }

    private fun embedAttachment(
        zip: ZipOutputStream,
        projectId: String,
        attachment: ProjectAttachment,
    ): ProjectAttachment {
        val source = runCatching { Uri.parse(attachment.uri) }.getOrNull() ?: return attachment
        if (source.scheme != "content" && source.scheme != "file") return attachment
        val safeName = safeSegment(attachment.displayName).take(100).ifBlank { "attachment" }
        val entryName = "attachments/${safeSegment(projectId)}/${safeSegment(attachment.id)}_$safeName"
        return try {
  val input = context.contentResolver.openInputStream(source) ?: return attachment
  input.use {
      zip.putNextEntry(ZipEntry(entryName))
      it.copyTo(zip)
      zip.closeEntry()
  }
  attachment.copy(uri = BACKUP_URI_PREFIX + entryName)
        } catch (_: Throwable) {
  runCatching { zip.closeEntry() }
  attachment
        }
    }

    private suspend fun readArchive(uri: Uri): DocumentStudioBackup = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
  ?: error("The selected cloud backup file could not be opened for reading.")
        val restoreRoot = File(context.filesDir, RESTORE_DIRECTORY).apply {
  deleteRecursively()
  mkdirs()
        }
        var manifestText: String? = null
        input.use { raw ->
  ZipInputStream(BufferedInputStream(raw)).use { zip ->
      while (true) {
          val entry = zip.nextEntry ?: break
          when {
              entry.isDirectory -> Unit
              entry.name == MANIFEST_ENTRY -> manifestText = zip.readBytes().decodeToString()
              entry.name.startsWith("attachments/") -> {
                  safeRestoreTarget(restoreRoot, entry.name)?.let { target ->
                      target.parentFile?.mkdirs()
                      target.outputStream().buffered().use { zip.copyTo(it) }
                  }
              }
          }
          zip.closeEntry()
      }
  }
        }
        val decoded = json.decodeFromString<DocumentStudioBackup>(
  manifestText ?: error("This is not a valid KREATIV Studio backup."),
        )
        decoded.copy(
  projects = decoded.projects.map { project ->
      project.copy(
          attachments = project.attachments.map { attachment ->
              restoreAttachmentUri(restoreRoot, attachment)
          },
          syncState = SyncState.SYNCED,
      )
  },
        )
    }

    private fun restoreAttachmentUri(
        restoreRoot: File,
        attachment: ProjectAttachment,
    ): ProjectAttachment {
        if (!attachment.uri.startsWith(BACKUP_URI_PREFIX)) return attachment
        val entryName = attachment.uri.removePrefix(BACKUP_URI_PREFIX)
        val target = safeRestoreTarget(restoreRoot, entryName) ?: return attachment
        if (!target.isFile) return attachment
        val localUri = FileProvider.getUriForFile(
  context,
  "${context.packageName}.files",
  target,
        )
        return attachment.copy(uri = localUri.toString())
    }

    private fun safeRestoreTarget(root: File, entryName: String): File? {
        val canonicalRoot = root.canonicalFile
        val target = File(canonicalRoot, entryName).canonicalFile
        val prefix = canonicalRoot.path + File.separator
        return target.takeIf { it.path.startsWith(prefix) }
    }

    private fun safeSegment(value: String): String = value
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .take(120)
        .ifBlank { "item" }

    private suspend fun <T> operation(block: suspend () -> T): Result<T> = try {
        val value = block()
        lastFailureMessage = null
        Result.success(value)
    } catch (error: Throwable) {
        lastFailureMessage = when (error) {
  is SecurityException -> "Google Drive access expired. Choose the backup file again."
  else -> error.message?.takeIf { it.isNotBlank() }
      ?: "The cloud backup file could not be accessed."
        }
        Result.failure(error)
    }

    companion object {
        private const val PREFERENCES = "kreativ_document_cloud_backup"
        private const val KEY_BACKUP_URI = "backup_uri"
        private const val MANIFEST_ENTRY = "studio.json"
        private const val RESTORE_DIRECTORY = "restored_cloud_backup"
        private const val BACKUP_URI_PREFIX = "kreativ-backup:"
    }
}

@Serializable
data class DocumentStudioBackup(
    val schemaVersion: Int = 1,
    val createdAt: Long,
    val settings: AppSettings,
    val progress: List<LessonProgress>,
    val projects: List<KreativProject>,
)

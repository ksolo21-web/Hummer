package com.koenterprises.territorycardstudio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.koenterprises.territorycardstudio.core.KnowledgeBaseAssignment
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant

data class SourceMapIntakeRecord(
    val territoryDisplayId: String,
    val canonicalFilename: String,
    val sourceFilename: String,
    val mimeType: String,
    val byteCount: Long,
    val sha256: String,
    val importedAtUtc: String,
    val localFilename: String,
    val provenanceType: String,
    val assignmentAuthority: Boolean
) {
    init {
        require(territoryDisplayId.isNotBlank())
        require(canonicalFilename.isNotBlank())
        require(sourceFilename.isNotBlank())
        require(mimeType in SourceMapIntakeStore.ALLOWED_MIME_TYPES)
        require(byteCount in 1..SourceMapIntakeStore.MAX_SOURCE_BYTES)
        require(Regex("^[0-9a-f]{64}$").matches(sha256))
        Instant.parse(importedAtUtc)
        require(localFilename.isNotBlank())
        require(provenanceType == "user_provided_source_map")
        require(!assignmentAuthority) {
            "An imported source map cannot silently become assignment authority"
        }
    }
}

class SourceMapIntakeStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val root = File(appContext.noBackupFilesDir, "territory-card-studio/source-intake-v1").apply {
        require(exists() || mkdirs()) { "Unable to create source-map intake directory" }
    }

    fun get(displayId: String): SourceMapIntakeRecord? {
        val raw = preferences.getString(key(displayId), null) ?: return null
        return parse(raw)
    }

    fun verifiedRecord(displayId: String): SourceMapIntakeRecord? {
        val record = get(displayId) ?: return null
        require(record.territoryDisplayId == displayId) { "Source map territory changed" }
        val directory = File(root, safeTerritoryDirectory(displayId)).canonicalFile
        val file = File(directory, record.localFilename).canonicalFile
        require(file.parentFile == directory && file.isFile && file.length() == record.byteCount) {
            "Source map is missing or changed; import it again"
        }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        require(digest.digest().joinToString("") { "%02x".format(it) } == record.sha256) {
            "Source map bytes changed; import it again"
        }
        return record
    }

    fun verifiedFile(displayId:String):File {
        val record=requireNotNull(verifiedRecord(displayId)) {"Import a source map first"}
        return File(File(root,safeTerritoryDirectory(displayId)),record.localFilename)
    }

    fun importFromUri(
        assignment: KnowledgeBaseAssignment,
        uri: Uri
    ): SourceMapIntakeRecord {
        val resolver = appContext.contentResolver
        val mime = resolver.getType(uri)?.lowercase()
            ?: error("Source map MIME type is unavailable")
        require(mime in ALLOWED_MIME_TYPES) {
            "Unsupported source map type: $mime"
        }
        val displayName = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }?.takeIf { it.isNotBlank() } ?: "source-map" + extensionFor(mime)

        val input = resolver.openInputStream(uri)
            ?: error("Unable to open selected source map")
        return input.use {
            importFromStream(
                assignment = assignment,
                sourceFilename = displayName,
                mimeType = mime,
                input = it
            )
        }
    }

    fun importFromStream(
        assignment: KnowledgeBaseAssignment,
        sourceFilename: String,
        mimeType: String,
        input: InputStream,
        importedAtUtc: String = Instant.now().toString()
    ): SourceMapIntakeRecord {
        require(mimeType in ALLOWED_MIME_TYPES) { "Unsupported source map type: $mimeType" }
        require(sourceFilename.isNotBlank()) { "Source filename is required" }

        val territoryDir = File(root, safeTerritoryDirectory(assignment.displayId)).apply {
            require(exists() || mkdirs()) { "Unable to create territory source-map directory" }
        }
        val temp = File(territoryDir, "incoming.tmp")
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        try {
            temp.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    require(total <= MAX_SOURCE_BYTES) {
                        "Source map exceeds maximum intake size"
                    }
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
            }
            require(total > 0L) { "Source map is empty" }
            validateMagic(temp, mimeType)

            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            val localName = sha + extensionFor(mimeType)
            val destination = File(territoryDir, localName)
            // Replace even an existing hash-named file: it may have been corrupted after intake.
            java.nio.file.Files.move(temp.toPath(), destination.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE)

            val record = SourceMapIntakeRecord(
                territoryDisplayId = assignment.displayId,
                canonicalFilename = assignment.canonicalFilename,
                sourceFilename = sourceFilename,
                mimeType = mimeType,
                byteCount = total,
                sha256 = sha,
                importedAtUtc = importedAtUtc,
                localFilename = localName,
                provenanceType = "user_provided_source_map",
                assignmentAuthority = false
            )
            // Historical registrations may reference earlier sources; preserve their exact bytes.
            require(preferences.edit().putString(key(assignment.displayId), encode(record)).commit()) {
                "Unable to persist source-map intake metadata"
            }
            return record
        } finally {
            temp.delete()
        }

    }

    fun clear(displayId: String) {
        require(preferences.edit().remove(key(displayId)).commit()) {
            "Unable to clear source-map selection"
        }
    }

    private fun validateMagic(file: File, mimeType: String) {
        val header = ByteArray(8)
        val read = file.inputStream().use { it.read(header) }
        require(read > 0) { "Source map is empty" }
        val valid = when (mimeType) {
            "application/pdf" -> read >= 5 &&
                header[0] == '%'.code.toByte() &&
                header[1] == 'P'.code.toByte() &&
                header[2] == 'D'.code.toByte() &&
                header[3] == 'F'.code.toByte() &&
                header[4] == '-'.code.toByte()
            "image/png" -> read >= 8 &&
                header.sliceArray(0 until 8).contentEquals(
                    byteArrayOf(
                        0x89.toByte(), 0x50, 0x4E, 0x47,
                        0x0D, 0x0A, 0x1A, 0x0A
                    )
                )
            "image/jpeg" -> read >= 3 &&
                header[0] == 0xFF.toByte() &&
                header[1] == 0xD8.toByte() &&
                header[2] == 0xFF.toByte()
            else -> false
        }
        require(valid) { "Source map file signature does not match $mimeType" }
    }

    private fun encode(record: SourceMapIntakeRecord): String = JSONObject()
        .put("territory_display_id", record.territoryDisplayId)
        .put("canonical_filename", record.canonicalFilename)
        .put("source_filename", record.sourceFilename)
        .put("mime_type", record.mimeType)
        .put("byte_count", record.byteCount)
        .put("sha256", record.sha256)
        .put("imported_at_utc", record.importedAtUtc)
        .put("local_filename", record.localFilename)
        .put("provenance_type", record.provenanceType)
        .put("assignment_authority", record.assignmentAuthority)
        .toString()

    private fun parse(raw: String): SourceMapIntakeRecord {
        val o = JSONObject(raw)
        return SourceMapIntakeRecord(
            territoryDisplayId = o.getString("territory_display_id"),
            canonicalFilename = o.getString("canonical_filename"),
            sourceFilename = o.getString("source_filename"),
            mimeType = o.getString("mime_type"),
            byteCount = o.getLong("byte_count"),
            sha256 = o.getString("sha256"),
            importedAtUtc = o.getString("imported_at_utc"),
            localFilename = o.getString("local_filename"),
            provenanceType = o.getString("provenance_type"),
            assignmentAuthority = o.getBoolean("assignment_authority")
        )
    }

    private fun key(displayId: String): String = "source_map_" + safeTerritoryDirectory(displayId)

    private fun safeTerritoryDirectory(displayId: String): String =
        displayId.replace(Regex("[^A-Za-z0-9_-]"), "_")

    companion object {
        const val MAX_SOURCE_BYTES: Long = 50L * 1024L * 1024L
        val ALLOWED_MIME_TYPES = setOf("application/pdf", "image/jpeg", "image/png")
        private const val PREFERENCES_NAME = "territory-card-studio-source-intake-v1"

        private fun extensionFor(mimeType: String): String = when (mimeType) {
            "application/pdf" -> ".pdf"
            "image/jpeg" -> ".jpg"
            "image/png" -> ".png"
            else -> error("Unsupported source map MIME type: $mimeType")
        }
    }
}

package com.bits.facultyai.data.attachments

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.bits.facultyai.data.local.FacultyDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Accepted extensions for note attachments (images are validated by MIME). */
val NOTE_FILE_EXTENSIONS = setOf("pdf", "docx", "doc", "pptx", "ppt", "txt", "md", "csv", "png", "jpg", "jpeg", "webp", "heic")

/**
 * Attachment pipeline: pick -> copy into app-private storage -> PROCESSING ->
 * SAVED -> (SYNCED | FAILED). The note is fully usable offline at SAVED; SYNCED
 * is cloud bookkeeping reserved for a future backend (Firebase Storage hook).
 *
 * All mutations land on Room first so the UI observes a single source of truth.
 */
class NoteAttachments(
    context: Context,
    private val dao: FacultyDao,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.IO)

    /** App-private attachments dir. Files survive app updates, not uninstalls. */
    private val attachmentsDir: File
        get() = File(appContext.filesDir, "note_attachments").apply { mkdirs() }

    fun observeAttachments(noteId: Long): Flow<List<com.bits.facultyai.data.local.NoteAttachmentEntity>> =
        dao.observeAttachments(noteId)

    // ------------------------------------------------------------------
    // Attach flow
    // ------------------------------------------------------------------

    /**
     * Attaches the given SAF uris to [noteId]. Each file is copied into
     * private storage; the DB row walks UPLOADING -> PROCESSING -> SAVED.
     * Failures leave a retryable FAILED row with the reason.
     */
    fun attach(noteId: Long, uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            uris.forEach { uri -> attachOne(noteId, uri) }
        }
    }

    private suspend fun attachOne(noteId: Long, uri: Uri) {
        val id = dao.insertAttachment(
            com.bits.facultyai.data.local.NoteAttachmentEntity(
                noteId = noteId,
                displayName = queryDisplayName(uri) ?: "attachment",
                ext = "",
                mimeType = guessMimeType(uri),
                sizeBytes = 0L,
                localFileName = "",
                sourceUri = uri.toString(),
                state = "UPLOADING",
                createdAt = System.currentTimeMillis(),
            )
        )
        try {
            // ---- UPLOADING: stream the content into private storage ----
            val resolver = appContext.contentResolver
            val (name, ext, size) = withContext(Dispatchers.IO) {
                copyIntoPrivateStorage(id, uri, resolver)
            }
            if (size == 0L) throw IOException("File is empty or unreadable")
            // The row can be removed mid-upload; bail out quietly if so.
            val staged = dao.getAttachment(id) ?: return
            dao.updateAttachment(
                staged.copy(
                    displayName = name,
                    ext = ext,
                    sizeBytes = size,
                    localFileName = attachmentFileName(id),
                    state = "PROCESSING",
                )
            )

            // ---- PROCESSING: type check + metadata finalize ----
            val finalExt = ext.ifBlank { extOf(name) }
            if (finalExt !in NOTE_FILE_EXTENSIONS) {
                throw IOException("Unsupported file type: .$finalExt")
            }
            dao.getAttachment(id)?.let {
                dao.updateAttachment(it.copy(state = "SAVED", errorMessage = ""))
                dao.setNoteSyncState(noteId, "PENDING_SYNC")
            }
        } catch (t: Throwable) {
            // ---- FAILED: keep the row so the user can retry ----
            dao.getAttachment(id)?.let { stale ->
                dao.updateAttachment(
                    stale.copy(state = "FAILED", errorMessage = t.message ?: "Upload failed")
                )
            }
        }
    }

    /** Streams the source uri into private storage; returns (displayName, ext, size). */
    private suspend fun copyIntoPrivateStorage(
        attachmentId: Long,
        uri: Uri,
        resolver: ContentResolver,
    ): Triple<String, String, Long> {
        val displayName = queryDisplayName(uri) ?: "attachment-${System.currentTimeMillis()}"
        val ext = extOf(displayName)
        val target = File(attachmentsDir, attachmentFileName(attachmentId))
        var bytes = 0L
        withContext(Dispatchers.IO) {
            resolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n == -1) break
                        bytes += n
                        output.write(buf, 0, n)
                    }
                }
            } ?: throw IOException("Cannot open the selected file")
        }
        return Triple(displayName, ext, bytes)
    }

    private fun attachmentFileName(attachmentId: Long) = "att_$attachmentId"

    // ------------------------------------------------------------------
    // Lifecycle: rename, remove, retry, open
    // ------------------------------------------------------------------

    /** Renames the attachment display name (extension preserved). */
    fun renameAttachment(attachmentId: Long, newName: String) {
        scope.launch {
            val a = dao.getAttachment(attachmentId) ?: return@launch
            val ext = a.ext.ifBlank { extOf(newName) }
            val cleaned = newName.trim().ifBlank { a.displayName }
            dao.updateAttachment(a.copy(displayName = "$cleaned.$ext"))
            dao.setNoteSyncState(a.noteId, "PENDING_SYNC")
        }
    }

    /** Removes the attachment row and its private file. */
    fun removeAttachment(attachmentId: Long) {
        scope.launch {
            val a = dao.getAttachment(attachmentId) ?: return@launch
            File(attachmentsDir, a.localFileName).delete()
            dao.deleteAttachment(attachmentId)
            dao.setNoteSyncState(a.noteId, "PENDING_SYNC")
        }
    }

    /** Re-runs the copy pipeline for a FAILED attachment. */
    fun retryAttachment(attachmentId: Long) {
        scope.launch {
            val a = dao.getAttachment(attachmentId) ?: return@launch
            if (a.state != "FAILED") return@launch
            dao.updateAttachment(a.copy(state = "UPLOADING", errorMessage = ""))
            try {
                val resolver = appContext.contentResolver
                val source = a.sourceUri.toUriOrNull()
                if (source == null) {
                    // Source picker grant is gone — but a previous partial copy
                    // may exist. If the private file is already there, promote it.
                    val local = File(attachmentsDir, attachmentFileName(a.id))
                    if (local.exists() && local.length() > 0) {
                        dao.updateAttachment(a.copy(state = "SAVED", errorMessage = ""))
                    } else {
                        throw IOException("Original file is no longer accessible — re-attach it")
                    }
                } else {
                    val (name, ext, size) = copyIntoPrivateStorage(a.id, source, resolver)
                    dao.updateAttachment(
                        a.copy(
                            displayName = if (name.isBlank()) a.displayName else name,
                            ext = ext.ifBlank { a.ext },
                            sizeBytes = size,
                            localFileName = attachmentFileName(a.id),
                            state = "PROCESSING",
                        )
                    )
                    dao.getAttachment(a.id)?.let {
                        dao.updateAttachment(it.copy(state = "SAVED", errorMessage = ""))
                    }
                }
                dao.setNoteSyncState(a.noteId, "PENDING_SYNC")
            } catch (t: Throwable) {
                dao.getAttachment(a.id)?.let {
                    dao.updateAttachment(it.copy(state = "FAILED", errorMessage = t.message ?: "Retry failed"))
                }
            }
        }
    }

    /** Retries every FAILED attachment (offline catch-up). */
    fun retryAllFailed() {
        scope.launch { dao.getFailedAttachments().forEach { retryAttachment(it.id) } }
    }

    /**
     * Opens the attachment with an external viewer. Works entirely from the
     * private copy (no storage permission, no SAF re-read).
     */
    fun openAttachment(attachmentId: Long, onMissing: () -> Unit) {
        scope.launch {
            val a = dao.getAttachment(attachmentId) ?: return@launch
            val file = File(attachmentsDir, a.localFileName)
            if (!file.exists() || file.length() == 0L) {
                onMissing()
            } else {
                withContext(Dispatchers.Main) {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        appContext,
                        "${appContext.packageName}.fileprovider",
                        file,
                    )
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, a.mimeType.ifBlank { "application/octet-stream" })
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { appContext.startActivity(intent) }
                        .onFailure { onMissing() }
                }
            }
        }
    }

    /** True when the private copy exists and is non-empty (preview gating). */
    suspend fun localFileReady(attachmentId: Long): Boolean {
        val a = dao.getAttachment(attachmentId) ?: return false
        val f = File(attachmentsDir, a.localFileName)
        return f.exists() && f.length() > 0
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun queryDisplayName(uri: Uri): String? {
        // Content URIs: query OpenableColumns. Document URIs: fall back to name.
        if (uri.scheme == "content") {
            appContext.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val nameCol = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameCol >= 0 && c.moveToFirst()) {
                    c.getString(nameCol)?.let { return it }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun guessMimeType(uri: Uri): String =
        appContext.contentResolver.getType(uri) ?: when (extOf(uri.lastPathSegment ?: "")) {
            "pdf" -> "application/pdf"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "doc" -> "application/msword"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "ppt" -> "application/vnd.ms-powerpoint"
            "txt" -> "text/plain"
            "md" -> "text/markdown"
            "csv" -> "text/csv"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "heic" -> "image/heic"
            else -> "application/octet-stream"
        }

    private fun String.toUriOrNull(): Uri? = runCatching { Uri.parse(this) }.getOrNull()

    /** Lowercase extension without the dot; "" when there is none. */
    fun extOf(name: String): String {
        val clean = name.substringBefore('?').substringBefore('#')
        val base = clean.substringAfterLast('/')
        return if (base.contains('.')) base.substringAfterLast('.').lowercase() else ""
    }
}

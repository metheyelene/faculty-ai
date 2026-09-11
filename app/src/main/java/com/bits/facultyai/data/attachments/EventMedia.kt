package com.bits.facultyai.data.attachments

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import com.bits.facultyai.data.local.EventExpenseEntity
import com.bits.facultyai.data.local.EventPhotoEntity
import com.bits.facultyai.data.local.FacultyDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Event media pipeline — the event-photo analogue of [NoteAttachments].
 *
 * Photos live in app-private storage (filesDir/event_photos); DB rows walk
 * UPLOADING -> PROCESSING -> UPLOADED (or FAILED, retryable). Receipts for
 * expenses ride the same store; removing a receipt never deletes the expense.
 */
class EventMedia(
    context: Context,
    private val dao: FacultyDao,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.IO)

    private val photosDir: File
        get() = File(appContext.filesDir, "event_photos").apply { mkdirs() }

    fun observePhotos(eventId: Long): Flow<List<EventPhotoEntity>> = dao.observeEventPhotos(eventId)

    fun photoFile(entity: EventPhotoEntity): File = File(photosDir, entity.fileName)

    // ------------------------------------------------------------------
    // Gallery
    // ------------------------------------------------------------------

    fun attachPhotos(eventId: Long, uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch { uris.forEach { uri -> attachPhoto(eventId, uri, fromCamera = false) } }
    }

    fun attachCameraPhoto(eventId: Long, uri: Uri) {
        scope.launch { attachPhoto(eventId, uri, fromCamera = true) }
    }

    private suspend fun attachPhoto(eventId: Long, uri: Uri, fromCamera: Boolean) {
        val baseName = "ev_${eventId}_${System.currentTimeMillis()}"
        val id = dao.insertEventPhoto(
            EventPhotoEntity(
                eventId = eventId,
                fileName = "$baseName.tmp",
                mimeType = "image/jpeg",
                sourceUri = uri.toString(),
                state = "UPLOADING",
                createdAt = System.currentTimeMillis(),
            )
        )
        try {
            val fileName = "$baseName.jpg"
            val target = File(photosDir, fileName)
            // PROCESSING: decode → downsample → EXIF-correct rotation → JPEG.
            // Bounded long edge keeps storage, loading and fullscreen viewing
            // memory-friendly on every device.
            val size = compressTo(uri, target)
            val row = dao.getEventPhoto(id) ?: return
            dao.updateEventPhoto(
                row.copy(
                    fileName = fileName,
                    sizeBytes = size,
                    mimeType = "image/jpeg",
                    state = "PROCESSING",
                )
            )
            // PROCESSING: an event's first photo becomes its cover automatically.
            val current = dao.getEventPhoto(id) ?: return
            val all = dao.getEventPhotos(eventId)
            val becomeCover = all.none { it.isCover } || all.size == 1
            dao.updateEventPhoto(
                current.copy(state = "UPLOADED", errorMessage = "", isCover = becomeCover)
            )
            if (becomeCover) {
                dao.getEvent(eventId)?.let { dao.updateEvent(it.copy(coverPhotoFileName = fileName)) }
            }
        } catch (t: Throwable) {
            dao.getEventPhoto(id)?.let {
                dao.updateEventPhoto(it.copy(state = "FAILED", errorMessage = t.message ?: "Upload failed"))
            }
        }
    }

    fun retryPhoto(photoId: Long) {
        scope.launch {
            val row = dao.getEventPhoto(photoId) ?: return@launch
            if (row.state != "FAILED") return@launch
            dao.updateEventPhoto(row.copy(state = "UPLOADING", errorMessage = ""))
            try {
                val source = row.sourceUri.toUriOrNull()
                    ?: throw IOException("Original photo no longer accessible")
                val fileName = row.fileName.ifBlank { "ev_${row.eventId}_${System.currentTimeMillis()}.jpg" }
                val target = File(photosDir, fileName)
                val size = compressTo(source, target)
                dao.getEventPhoto(photoId)?.let {
                    dao.updateEventPhoto(it.copy(fileName = fileName, sizeBytes = size, state = "UPLOADED", errorMessage = ""))
                }
            } catch (t: Throwable) {
                dao.getEventPhoto(photoId)?.let {
                    dao.updateEventPhoto(it.copy(state = "FAILED", errorMessage = t.message ?: "Retry failed"))
                }
            }
        }
    }

    fun removePhoto(photoId: Long) {
        scope.launch {
            val row = dao.getEventPhoto(photoId) ?: return@launch
            File(photosDir, row.fileName).delete()
            val wasCover = row.isCover
            dao.deleteEventPhoto(photoId)
            if (wasCover) {
                // Hand the cover to the most recent remaining photo, if any.
                val remaining = dao.getEventPhotos(row.eventId)
                val next = remaining.firstOrNull()
                if (next != null) {
                    dao.updateEventPhoto(next.copy(isCover = true))
                    dao.getEvent(row.eventId)?.let { dao.updateEvent(it.copy(coverPhotoFileName = next.fileName)) }
                } else {
                    dao.getEvent(row.eventId)?.let { dao.updateEvent(it.copy(coverPhotoFileName = null)) }
                }
            }
        }
    }

    fun setCover(photoId: Long) {
        scope.launch {
            val row = dao.getEventPhoto(photoId) ?: return@launch
            dao.clearEventCoverFlags(row.eventId)
            dao.updateEventPhoto(row.copy(isCover = true))
            dao.getEvent(row.eventId)?.let { dao.updateEvent(it.copy(coverPhotoFileName = row.fileName)) }
        }
    }

    fun setCaption(photoId: Long, caption: String) {
        scope.launch {
            dao.getEventPhoto(photoId)?.let { dao.updateEventPhoto(it.copy(caption = caption.trim())) }
        }
    }

    fun openPhoto(photoId: Long, onMissing: () -> Unit) {
        scope.launch {
            val row = dao.getEventPhoto(photoId) ?: return@launch
            openFile(File(photosDir, row.fileName), row.mimeType.ifBlank { "image/jpeg" }, onMissing)
        }
    }

    // ------------------------------------------------------------------
    // Expense receipts
    // ------------------------------------------------------------------

    /** Attaches a receipt to an expense; the expense record itself is never touched otherwise. */
    fun attachReceipt(expenseId: Long, uri: Uri) {
        scope.launch {
            val expense = dao.getEventExpense(expenseId) ?: return@launch
            try {
                val resolver = appContext.contentResolver
                val displayName = queryDisplayName(uri) ?: "receipt_${System.currentTimeMillis()}"
                val ext = if (displayName.contains('.')) displayName.substringAfterLast('.').lowercase() else "jpg"
                val fileName = "receipt_${expenseId}_${System.currentTimeMillis()}.$ext"
                val target = File(photosDir, fileName)
                withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { out -> input.copyTo(out) }
                    } ?: throw IOException("Cannot open the selected document")
                }
                val mime = resolver.getType(uri) ?: when (ext) {
                    "pdf" -> "application/pdf"
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    "heic" -> "image/heic"
                    else -> "image/jpeg"
                }
                dao.updateEventExpense(
                    expense.copy(receiptFileName = fileName, receiptName = displayName, updatedAt = System.currentTimeMillis())
                )
                dao.setEventSyncState(expense.eventId, "PENDING_SYNC")
                dao.updateEventExpense(dao.getEventExpense(expenseId) ?: expense)
            } catch (_: Throwable) {
                // Receipt attach is best-effort; the expense record is untouched.
            }
        }
    }

    fun removeReceipt(expenseId: Long) {
        scope.launch {
            val expense = dao.getEventExpense(expenseId) ?: return@launch
            expense.receiptFileName?.let { File(photosDir, it).delete() }
            dao.updateEventExpense(
                expense.copy(receiptFileName = null, receiptName = "", updatedAt = System.currentTimeMillis())
            )
        }
    }

    fun openReceipt(expenseId: Long, onMissing: () -> Unit) {
        scope.launch {
            val expense = dao.getEventExpense(expenseId) ?: return@launch
            val name = expense.receiptFileName
            if (name == null) onMissing() else openFile(File(photosDir, name), receiptMime(name), onMissing)
        }
    }

    private fun receiptMime(fileName: String): String = when (fileName.substringAfterLast('.').lowercase()) {
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "heic" -> "image/heic"
        else -> "image/jpeg"
    }

    // ------------------------------------------------------------------
    // Event cleanup
    // ------------------------------------------------------------------

    /** Deletes every stored file belonging to an event (photos + receipts). */
    fun deleteEventFiles(eventId: Long) {
        scope.launch {
            dao.getEventPhotos(eventId).forEach { File(photosDir, it.fileName).delete() }
            dao.getEventExpenses(eventId).forEach { e -> e.receiptFileName?.let { File(photosDir, it).delete() } }
        }
    }

    /**
     * Copies [uri] into [target] as re-compressed JPEG: downsampled so the
     * long edge stays ≤1600px, EXIF rotation baked in, quality 85. Returns
     * the written size in bytes. Throws on any failure so callers mark
     * FAILED and can retry.
     */
    private suspend fun compressTo(uri: Uri, target: File, maxEdge: Int = 1600, quality: Int = 85): Long =
        withContext(Dispatchers.IO) {
            val resolver = appContext.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                ?: throw IOException("Cannot read the selected photo")
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not a valid image")

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: throw IOException("Cannot read the selected photo")

            val rotation = readExifRotationDegrees(resolver, uri).toFloat()
            val oriented = if (rotation != 0f) {
                Bitmap.createBitmap(
                    decoded, 0, 0, decoded.width, decoded.height,
                    Matrix().apply { postRotate(rotation) }, true,
                )
            } else {
                decoded
            }
            target.outputStream().use { oriented.compress(Bitmap.CompressFormat.JPEG, quality, it) }
            if (oriented !== decoded) decoded.recycle()
            target.length().also { if (it == 0L) throw IOException("Compressed image is empty") }
        }

    private fun readExifRotationDegrees(resolver: ContentResolver, uri: Uri): Int = runCatching {
        resolver.openInputStream(uri)?.use { stream ->
            androidx.exifinterface.media.ExifInterface(stream).rotationDegrees
        } ?: 0
    }.getOrDefault(0)

    private suspend fun openFile(file: File, mime: String, onMissing: () -> Unit) {
        if (!file.exists() || file.length() == 0L) {
            withContext(Dispatchers.Main) { onMissing() }
            return
        }
        withContext(Dispatchers.Main) {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                file,
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { appContext.startActivity(intent) }.onFailure { onMissing() }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme == "content") {
            appContext.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val col = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (col >= 0 && c.moveToFirst()) c.getString(col)?.let { return it }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun String.toUriOrNull(): Uri? = runCatching { Uri.parse(this) }.getOrNull()
}

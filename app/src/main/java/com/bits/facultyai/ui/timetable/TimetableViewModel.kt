package com.bits.facultyai.ui.timetable

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bits.facultyai.data.local.ClassSlotEntity
import com.bits.facultyai.data.local.FacultyDatabase
import com.bits.facultyai.data.local.TimetableVersionEntity
import com.bits.facultyai.domain.TimeUtils
import com.bits.facultyai.domain.TimetableExtractor
import com.bits.facultyai.domain.TimetableOcr
import com.bits.facultyai.notifications.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class TimetableViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = FacultyDatabase.get(application).facultyDao()

    val timetable = dao.observeTimetable()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val versions = dao.observeTimetableVersions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedDay = MutableStateFlow(java.time.LocalDate.now().dayOfWeek.value)
    val selectedDay: StateFlow<Int> = _selectedDay

    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog

    private val _attendanceEvent = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val attendanceEvent = _attendanceEvent.asSharedFlow()

    // ---- Photo import flow ----

    enum class ImportStep { IDLE, PROCESSING, REVIEW }

    data class ImportState(
        val step: ImportStep = ImportStep.IDLE,
        val imageUri: Uri? = null,
        val draft: List<TimetableExtractor.ExtractedSlot> = emptyList(),
        val skippedLines: List<String> = emptyList(),
        val error: String? = null,
    )

    private val _import = MutableStateFlow(ImportState())
    val import: StateFlow<ImportState> = _import

    private val _showHistory = MutableStateFlow(false)
    val showHistory: StateFlow<Boolean> = _showHistory

    fun selectDay(day: Int) {
        _selectedDay.value = day
    }

    fun openAddDialog() {
        _showAddDialog.value = true
    }

    fun closeAddDialog() {
        _showAddDialog.value = false
    }

    fun onAttendanceRequested(slot: ClassSlotEntity) {
        _attendanceEvent.tryEmit(slot.id)
    }

    /** Step 1: user picked an image → copy it locally, run extraction. */
    fun onImagePicked(uri: Uri) {
        viewModelScope.launch {
            _import.value = ImportState(step = ImportStep.PROCESSING, imageUri = uri)
            try {
                val localUri = withContext(Dispatchers.IO) { copyToPrivateStorage(uri) }
                val lines = withContext(Dispatchers.IO) { ocrLines(localUri) }
                val result = TimetableExtractor.extract(lines)
                if (result.slots.isEmpty()) {
                    // Nothing machine-readable: start a manual draft over the photo.
                    _import.value = ImportState(
                        step = ImportStep.REVIEW,
                        imageUri = localUri,
                        draft = listOf(TimetableExtractor.ExtractedSlot()),
                        skippedLines = result.skippedLines,
                    )
                } else {
                    _import.value = ImportState(
                        step = ImportStep.REVIEW,
                        imageUri = localUri,
                        draft = result.slots,
                        skippedLines = result.skippedLines,
                    )
                }
            } catch (t: Throwable) {
                _import.value = ImportState(
                    step = ImportStep.REVIEW,
                    imageUri = uri,
                    draft = listOf(TimetableExtractor.ExtractedSlot()),
                    error = "Couldn't read the photo automatically — please review and complete the rows below.",
                )
            }
        }
    }

    /**
     * On-device OCR via ML Kit (bundled model — offline, private). Returns
     * reading-order text lines; an empty list means nothing was recognized
     * and the review editor starts with a manual draft row.
     */
    private suspend fun ocrLines(uri: Uri): List<String> =
        TimetableOcr.recognizeLines(getApplication(), uri)

    /** Copies the picked image into app-private storage so it survives gallery cleanup. */
    private suspend fun copyToPrivateStorage(uri: Uri): Uri = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val dir = File(context.filesDir, "timetable_photos").apply { mkdirs() }
        val file = File(dir, "tt_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Cannot open image")
        Uri.fromFile(file)
    }

    fun updateDraft(index: Int, slot: TimetableExtractor.ExtractedSlot) {
        val draft = _import.value.draft.toMutableList()
        if (index in draft.indices) {
            draft[index] = slot
            _import.value = _import.value.copy(draft = draft)
        }
    }

    fun addDraftRow() {
        _import.value = _import.value.copy(
            draft = _import.value.draft + TimetableExtractor.ExtractedSlot(
                dayOfWeek = _selectedDay.value,
            )
        )
    }

    fun removeDraftRow(index: Int) {
        val draft = _import.value.draft.toMutableList()
        if (index in draft.indices) draft.removeAt(index)
        _import.value = _import.value.copy(draft = draft)
    }

    /** Step 2: faculty confirmed the reviewed draft → archive + replace + resync. */
    fun confirmImport() {
        val state = _import.value
        val draft = state.draft.filter { it.subject.isNotBlank() }
        if (draft.isEmpty()) return
        viewModelScope.launch {
            // Archive current timetable as a version before replacing it.
            val current = dao.getTimetable()
            if (current.isNotEmpty()) {
                dao.insertTimetableVersion(
                    TimetableVersionEntity(
                        versionNumber = (dao.maxTimetableVersion() ?: 0) + 1,
                        createdAt = System.currentTimeMillis(),
                        sourceLabel = "Replaced by import",
                        sourceImageUri = state.imageUri?.toString() ?: "",
                        slotCount = current.size,
                        slotsJson = com.bits.facultyai.domain.TimetableExtractor.serialize(
                            current.map {
                                TimetableExtractor.ExtractedSlot(
                                    it.dayOfWeek, it.startTimeMinutes, it.endTimeMinutes, it.subject, it.section, it.room
                                )
                            }
                        ),
                    )
                )
            }

            dao.clearTimetable()
            dao.insertClassSlots(
                draft.map {
                    ClassSlotEntity(
                        dayOfWeek = it.dayOfWeek,
                        startTimeMinutes = it.startMinutes,
                        endTimeMinutes = it.endMinutes,
                        subject = it.subject.trim(),
                        section = it.section.ifBlank { "—" },
                        room = it.room.ifBlank { "—" },
                    )
                }
            )
            SyncScheduler.syncAll(getApplication())
            _import.value = ImportState()
        }
    }

    fun cancelImport() {
        _import.value = ImportState()
    }

    // ---- Manual editing ----

    fun addSlot(slot: ClassSlotEntity) = viewModelScope.launch {
        if (dao.countOverlapping(slot.dayOfWeek, slot.startTimeMinutes, slot.endTimeMinutes) == 0) {
            dao.insertClassSlot(slot)
            SyncScheduler.syncTimetableReminders(getApplication())
        }
    }

    fun updateSlot(slot: ClassSlotEntity) = viewModelScope.launch {
        if (dao.countOverlapping(slot.dayOfWeek, slot.startTimeMinutes, slot.endTimeMinutes, excludeId = slot.id) == 0) {
            dao.updateClassSlot(slot)
            SyncScheduler.syncTimetableReminders(getApplication())
        }
    }

    fun deleteSlot(id: Long) = viewModelScope.launch {
        dao.deleteClassSlot(id)
        SyncScheduler.syncTimetableReminders(getApplication())
    }

    /** Wipes the timetable without archiving (used by settings reset). */
    fun clearAllSlots() = viewModelScope.launch {
        dao.clearTimetable()
        SyncScheduler.syncTimetableReminders(getApplication())
    }

    fun showHistory() {
        _showHistory.value = true
    }

    fun dismissHistory() {
        _showHistory.value = false
    }

    /** Restores an archived version as the current timetable (archiving the current one first). */
    fun restoreVersion(version: TimetableVersionEntity) {
        viewModelScope.launch {
            val restored = TimetableExtractor.deserialize(version.slotsJson)
            if (restored.isEmpty()) return@launch
            val current = dao.getTimetable()
            if (current.isNotEmpty()) {
                dao.insertTimetableVersion(
                    TimetableVersionEntity(
                        versionNumber = (dao.maxTimetableVersion() ?: 0) + 1,
                        createdAt = System.currentTimeMillis(),
                        sourceLabel = "Replaced by restore of v${version.versionNumber}",
                        slotCount = current.size,
                        slotsJson = TimetableExtractor.serialize(
                            current.map {
                                TimetableExtractor.ExtractedSlot(
                                    it.dayOfWeek, it.startTimeMinutes, it.endTimeMinutes, it.subject, it.section, it.room
                                )
                            }
                        ),
                    )
                )
            }
            dao.clearTimetable()
            dao.insertClassSlots(restored)
            SyncScheduler.syncTimetableReminders(getApplication())
            _showHistory.value = false
        }
    }
}

package com.bits.facultyai.ui.attendance.monthly

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bits.facultyai.data.local.AttendanceRecordEntity
import com.bits.facultyai.data.local.FacultyDatabase
import com.bits.facultyai.domain.AttendanceExcelParser
import com.bits.facultyai.domain.MonthlyAttendance
import com.bits.facultyai.domain.XlsxReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth

/** Conflict policy for marks that already exist for student+date+subject. */
enum class ConflictPolicy { UPDATE, SKIP }

sealed interface ImportStep {
    data object Idle : ImportStep
    data class NeedSubject(val fileName: String, val table: List<List<String?>>) : ImportStep
    data class NeedMapping(
        val fileName: String,
        val table: List<List<String?>>,
        val headers: List<String?>,
    ) : ImportStep

    data class Previewing(
        val fileName: String,
        val format: AttendanceExcelParser.Format?,
        val month: YearMonth,
        val subject: String,
        val preview: AttendanceExcelParser.Preview,
        val conflicts: List<AttendanceExcelParser.Conflict>,
    ) : ImportStep

    data class Done(val imported: Int, val updated: Int, val skipped: Int) : ImportStep
    data class Failed(val message: String) : ImportStep
}

/**
 * Drives the Excel monthly-attendance import for one class/month:
 * read -> pick subject -> (map columns if detection fails) -> preview with
 * conflicts -> policy choice -> guarded commit. Nothing writes to the
 * database until [commitImport] is explicitly called.
 */
class MonthlyImportViewModel(
    private val application: Application,
    private val dao: com.bits.facultyai.data.local.FacultyDao,
    payload: String,
) : ViewModel() {

    val year: Int
    val section: String
    val subject: String
    val month: YearMonth

    init {
        val parts = payload.split("|")
        year = parts.getOrNull(0)?.toIntOrNull() ?: 0
        section = parts.getOrNull(1).orEmpty()
        subject = parts.getOrNull(2).orEmpty().ifEmpty { "ALL" }
        val monthParts = parts.getOrNull(3)?.split("-").orEmpty()
        month = runCatching {
            YearMonth.of(
                monthParts.getOrNull(0)?.trim()?.toIntOrNull() ?: YearMonth.now().year,
                monthParts.getOrNull(1)?.trim()?.toIntOrNull() ?: YearMonth.now().monthValue,
            )
        }.getOrDefault(YearMonth.now())
    }

    private val _step = MutableStateFlow<ImportStep>(ImportStep.Idle)
    val step: StateFlow<ImportStep> = _step

    /** Guards against double-taps racing two commit passes. */
    var commitInFlight = false
        private set

    /** Subjects the faculty can stamp imported sessions with: real subjects of the class. */
    fun subjectOptions(onLoaded: (List<String>) -> Unit) {
        viewModelScope.launch {
            onLoaded(
                if (year == 0 || section.isEmpty()) emptyList()
                else withContext(Dispatchers.IO) { dao.getRecordedSubjectsFor(year, section) }
                    .ifEmpty {
                        // No sessions yet: fall back to the timetable's subjects for this class.
                        withContext(Dispatchers.IO) { dao.getTimetable().map { it.subject }.distinct() }
                    },
            )
        }
    }

    private fun readTable(uri: Uri): List<List<String?>> =
        application.contentResolver.openInputStream(uri)?.use { XlsxReader.readFirstSheet(it) }
            ?: throw XlsxReader.ImportException("Couldn't read the selected file.")

    /** Reads the sheet; if no subject filter is chosen, asks for one before parsing. */
    fun loadPreview(uri: Uri, fileName: String) {
        if (year == 0 || section.isEmpty()) {
            _step.value = ImportStep.Failed("Pick the class (year + section) first — the import matches against that roster.")
            return
        }
        viewModelScope.launch {
            try {
                val table = withContext(Dispatchers.IO) { readTable(uri) }
                if (subject == "ALL") {
                    _step.value = ImportStep.NeedSubject(fileName, table)
                } else {
                    parseAndPreview(fileName, table, subject)
                }
            } catch (e: XlsxReader.ImportException) {
                _step.value = ImportStep.Failed(e.message ?: "Couldn't read the spreadsheet.")
            } catch (e: Exception) {
                _step.value = ImportStep.Failed("Couldn't read the file: ${e.message ?: "unknown error"}")
            }
        }
    }

    /** Faculty picked the subject to stamp imported sessions with; continue parsing. */
    fun setImportSubject(fileName: String, table: List<List<String?>>, subject: String) {
        viewModelScope.launch { parseAndPreview(fileName, table, subject) }
    }

    private suspend fun parseAndPreview(fileName: String, table: List<List<String?>>, importSubject: String) {
        try {
            val roster = withContext(Dispatchers.IO) { dao.getStudentsFor(year, section) }
            val mapping = AttendanceExcelParser.detectMapping(table, month.year, month.monthValue)
            if (mapping == null) {
                _step.value = ImportStep.NeedMapping(fileName, table, table.first())
                return
            }
            val preview = withContext(Dispatchers.IO) {
                AttendanceExcelParser.parse(table, roster, mapping, month.year, month.monthValue)
            }
            val conflicts = withContext(Dispatchers.IO) { findConflicts(preview, importSubject) }
            _step.value = ImportStep.Previewing(fileName, mapping.format, month, importSubject, preview, conflicts)
        } catch (e: XlsxReader.ImportException) {
            _step.value = ImportStep.Failed(e.message ?: "Couldn't read the spreadsheet.")
        } catch (e: Exception) {
            _step.value = ImportStep.Failed("Couldn't read the file: ${e.message ?: "unknown error"}")
        }
    }

    /** Manual mapping: the UI resolved the columns by hand; re-run the parse. */
    fun applyMapping(fileName: String, table: List<List<String?>>, mapping: AttendanceExcelParser.Mapping, importSubject: String) {
        viewModelScope.launch {
            try {
                val roster = withContext(Dispatchers.IO) { dao.getStudentsFor(year, section) }
                val preview = withContext(Dispatchers.IO) {
                    AttendanceExcelParser.parse(table, roster, mapping, month.year, month.monthValue)
                }
                val conflicts = withContext(Dispatchers.IO) { findConflicts(preview, importSubject) }
                _step.value = ImportStep.Previewing(fileName, mapping.format, month, importSubject, preview, conflicts)
            } catch (e: Exception) {
                _step.value = ImportStep.Failed(e.message ?: "Couldn't parse with the chosen columns.")
            }
        }
    }

    private suspend fun findConflicts(
        preview: AttendanceExcelParser.Preview,
        importSubject: String,
    ): List<AttendanceExcelParser.Conflict> = withContext(Dispatchers.IO) {
        // A conflict is an existing mark for the SAME student, date and subject —
        // marks for other subjects on the same day are separate sessions, not conflicts.
        val (startIso, endIso) = MonthlyAttendance.monthRange(month.year, month.monthValue)
        val existing = dao.getStudentMonthEntriesBetween(startIso, endIso)
            .filter { it.recordSubject == importSubject }
            .associateBy { it.studentId to it.recordDate }
        preview.marks.mapNotNull { mark ->
            existing[mark.studentId to mark.dateIso]?.let { entry ->
                AttendanceExcelParser.Conflict(mark, entry.status, entry.recordId)
            }
        }
    }

    fun cancel() {
        _step.value = ImportStep.Idle
    }

    /**
     * Commits the previewed marks. Already-marked (conflicting) rows follow
     * [policy]: UPDATE rewrites those entries' statuses, SKIP leaves the
     * stored attendance untouched. New marks insert one session record per
     * date under the chosen [subject] — the same shape the daily flow writes,
     * so imported sessions appear in every per-subject view.
     */
    fun commitImport(policy: ConflictPolicy, onDone: (ImportStep) -> Unit = {}) {
        val current = _step.value
        if (current !is ImportStep.Previewing || commitInFlight) return
        commitInFlight = true
        val importSubject = current.subject
        viewModelScope.launch {
            try {
                var imported = 0; var updated = 0; var skipped = 0
                withContext(Dispatchers.IO) {
                    val conflictKeys = current.conflicts.map { it.mark.studentId to it.mark.dateIso }.toHashSet()
                    if (policy == ConflictPolicy.UPDATE) {
                        current.conflicts.forEach { c ->
                            dao.updateAttendanceEntryStatus(c.existingRecordId, c.mark.studentId, c.mark.status)
                            updated++
                        }
                    } else {
                        skipped = current.conflicts.size
                    }
                    val applicable = current.preview.marks.filter { (it.studentId to it.dateIso) !in conflictKeys }
                    val byDate = applicable.groupBy { it.dateIso }.toSortedMap()
                    for ((dateIso, marksOfDay) in byDate) {
                        var present = 0; var absent = 0; var late = 0; var excused = 0
                        for (m in marksOfDay) when (m.status) {
                            AttendanceExcelParser.Status.PRESENT -> present++
                            AttendanceExcelParser.Status.ABSENT -> absent++
                            AttendanceExcelParser.Status.LATE -> late++
                            AttendanceExcelParser.Status.EXCUSED -> excused++
                        }
                        val recordId = dao.insertAttendanceRecord(
                            AttendanceRecordEntity(
                                classSlotId = 0L, // import sessions are not tied to a timetable slot
                                subject = importSubject,
                                section = section,
                                date = dateIso,
                                year = year,
                                markedAt = System.currentTimeMillis(),
                                presentCount = present,
                                absentCount = absent,
                                lateCount = late,
                                excusedCount = excused,
                            ),
                        )
                        dao.insertAttendanceEntries(
                            marksOfDay.map { m ->
                                com.bits.facultyai.data.local.AttendanceEntryEntity(
                                    recordId = recordId,
                                    studentId = m.studentId,
                                    status = m.status,
                                )
                            },
                        )
                        imported += marksOfDay.size
                    }
                }
                _step.value = ImportStep.Done(imported, updated, skipped)
            } catch (e: Exception) {
                _step.value = ImportStep.Failed(e.message ?: "Import failed.")
            } finally {
                commitInFlight = false
            }
            onDone(_step.value)
        }
    }

    companion object {
        fun factory(
            application: Application,
            payload: String,
        ) = viewModelFactory {
            initializer {
                MonthlyImportViewModel(
                    application,
                    FacultyDatabase.get(application).facultyDao(),
                    payload,
                )
            }
        }
    }
}

package com.bits.facultyai.ui.students

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bits.facultyai.data.local.FacultyDatabase
import com.bits.facultyai.data.local.PendingDeletionEntity
import com.bits.facultyai.data.local.StudentEntity
import com.bits.facultyai.domain.StudentExcelParser
import com.bits.facultyai.domain.XlsxReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Years 1..4; sections are free-form data ("A", "B", …) — extensible without code change. */
val ACADEMIC_YEARS = listOf(1, 2, 3, 4)

fun Int.ordinalYear(): String = when (this) {
    1 -> "1ST"
    2 -> "2ND"
    3 -> "3RD"
    else -> "4TH"
}

class StudentsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = FacultyDatabase.get(application)
    private val dao = db.facultyDao()
    private val syncDao = db.syncDao()

    // ---- navigation + search ----

    val selectedYear = MutableStateFlow(ACADEMIC_YEARS.first())
    val selectedSection = MutableStateFlow<String?>(null)

    /** Sections that actually exist for the selected year (from imported students). */
    val availableSections: StateFlow<Set<String>> = combine(dao.observeStudents(), selectedYear) { all, year ->
        all.filter { it.year == year }.map { it.section }.toSortedSet()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val query = MutableStateFlow("")
    val searchQuery: StateFlow<String> = query

    @OptIn(FlowPreview::class)
    val students: StateFlow<List<StudentEntity>> = combine(
        dao.observeStudents(),
        query.debounce(200),
        selectedYear,
        selectedSection,
    ) { all, q, year, section ->
        val effectiveSection = section?.takeIf { s -> all.any { it.year == year && it.section == s } }
            ?: all.filter { it.year == year }.map { it.section }.toSortedSet().firstOrNull()
        all.filter { it.year == year && (effectiveSection == null || it.section == effectiveSection) }
            .filter {
                q.isBlank() || it.name.contains(q, true) ||
                    it.rollNumber.contains(q, true) ||
                    it.section.contains(q, true) ||
                    it.registrationNumber.contains(q, true)
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val attendanceRecords = dao.observeAttendanceRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- import state machine ----

    data class ImportState(
        val phase: Phase = Phase.IDLE,
        val fileName: String? = null,
        val preview: StudentExcelParser.Preview? = null,
        val chosenYear: Int = ACADEMIC_YEARS.first(),
        val chosenSection: String = "A",
        // rollNumber -> existing student, for duplicates against the roster
        val rosterDuplicates: Map<String, StudentEntity> = emptyMap(),
        val duplicateChoice: DuplicateChoice = DuplicateChoice.UNDECIDED,
        val errorMessage: String? = null,
        val importedCount: Int = 0,
    ) {
        enum class Phase { IDLE, PARSING, PREVIEW, IMPORTING, DONE, ERROR }
        enum class DuplicateChoice { UNDECIDED, SKIP, UPDATE }
    }

    val import = MutableStateFlow(ImportState())

    fun setSearch(q: String) { query.value = q }
    fun selectYear(year: Int) {
        selectedYear.value = year
        selectedSection.value = null
    }
    fun selectSection(section: String?) { selectedSection.value = section }

    fun startImport(uri: Uri, displayName: String?) {
        // Reject files that aren't .xlsx up front — .xls (legacy binary) isn't supported.
        val lowerName = displayName?.lowercase().orEmpty()
        if (lowerName.isNotEmpty() && !lowerName.endsWith(".xlsx")) {
            import.value = ImportState(
                phase = ImportState.Phase.ERROR,
                errorMessage = "\"${displayName}\" is not an .xlsx file. Please save the sheet as .xlsx and try again.",
            )
            return
        }
        import.value = ImportState(phase = ImportState.Phase.PARSING, fileName = displayName)
        viewModelScope.launch {
            try {
                val preview = withContext(Dispatchers.IO) { parseWorkbook(uri) }
                if (preview.valid.isEmpty() && preview.duplicatesInFile.isEmpty() && preview.invalid.isEmpty()) {
                    import.value = import.value.copy(
                        phase = ImportState.Phase.ERROR,
                        errorMessage = "No student rows found. Make sure row 1 has column headers like \"Roll Number\" and \"Name\".",
                    )
                    return@launch
                }
                // Pre-select the first (year, section) found in the sheet, else current selection.
                val sheetYear = preview.valid.firstNotNullOfOrNull { it.year }
                val sheetSection = preview.valid.firstNotNullOfOrNull { it.section }
                import.value = import.value.copy(
                    phase = ImportState.Phase.PREVIEW,
                    preview = preview,
                    chosenYear = sheetYear ?: selectedYear.value,
                    chosenSection = sheetSection ?: selectedSection.value ?: "A",
                )
                refreshRosterDuplicates()
            } catch (e: XlsxReader.ImportException) {
                import.value = import.value.copy(phase = ImportState.Phase.ERROR, errorMessage = e.message)
            } catch (e: Exception) {
                import.value = import.value.copy(
                    phase = ImportState.Phase.ERROR,
                    errorMessage = "Couldn't read this file. Supported format: .xlsx",
                )
            }
        }
    }

    private suspend fun parseWorkbook(uri: Uri): StudentExcelParser.Preview {
        val context = getApplication<Application>()
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw XlsxReader.ImportException("Couldn't open the selected file.")
        if (bytes.size > 10 * 1024 * 1024) {
            throw XlsxReader.ImportException("File is too large (over 10 MB).")
        }
        val table = withContext(Dispatchers.IO) {
            bytes.inputStream().use { XlsxReader.readFirstSheet(it) }
        }
        return StudentExcelParser.parse(table)
    }

    fun chooseImportYear(year: Int) {
        import.value = import.value.copy(chosenYear = year)
        viewModelScope.launch { refreshRosterDuplicates() }
    }

    fun chooseImportSection(section: String) {
        import.value = import.value.copy(chosenSection = section.trim().uppercase().take(4))
        viewModelScope.launch { refreshRosterDuplicates() }
    }

    fun setDuplicateChoice(choice: ImportState.DuplicateChoice) {
        import.value = import.value.copy(duplicateChoice = choice)
    }

    private suspend fun refreshRosterDuplicates() {
        val s = import.value
        val preview = s.preview ?: return
        val map = mutableMapOf<String, StudentEntity>()
        preview.valid.forEach { p ->
            val existing = dao.findExistingStudents(
                year = s.chosenYear,
                section = s.chosenSection,
                roll = p.rollNumber,
                reg = p.registrationNumber,
            ).firstOrNull()
            if (existing != null) map[p.rollNumber] = existing
        }
        import.value = import.value.copy(rosterDuplicates = map)
    }

    fun confirmImport(onDone: (Int) -> Unit = {}) {
        val s = import.value
        val preview = s.preview ?: return
        import.value = import.value.copy(phase = ImportState.Phase.IMPORTING)
        viewModelScope.launch {
            val duplicates = s.rosterDuplicates
            val now = System.currentTimeMillis()
            val rows = mutableListOf<StudentEntity>()
            var updated = 0
            preview.valid.forEach { p ->
                val existing = duplicates[p.rollNumber]
                when {
                    existing == null -> rows += StudentEntity(
                        rollNumber = p.rollNumber,
                        name = p.name,
                        section = s.chosenSection,
                        year = s.chosenYear,
                        registrationNumber = p.registrationNumber,
                        email = p.email,
                        phone = p.phone,
                        updatedAt = now,
                    )
                    s.duplicateChoice == ImportState.DuplicateChoice.UPDATE -> {
                        dao.updateStudent(
                            existing.copy(
                                name = p.name.ifBlank { existing.name },
                                registrationNumber = p.registrationNumber.ifBlank { existing.registrationNumber },
                                email = p.email.ifBlank { existing.email },
                                phone = p.phone.ifBlank { existing.phone },
                                updatedAt = now,
                            )
                        )
                        updated++
                    }
                    // SKIP: leave existing untouched
                }
            }
            if (rows.isNotEmpty()) dao.insertStudents(rows)
            // Imported students land under (chosenYear, chosenSection): navigate there.
            selectedYear.value = s.chosenYear
            selectedSection.value = s.chosenSection
            import.value = import.value.copy(phase = ImportState.Phase.DONE, importedCount = rows.count() + updated)
            onDone(rows.count() + updated)
        }
    }

    fun dismissImport() { import.value = ImportState() }
    fun dismissError() { import.value = import.value.copy(phase = ImportState.Phase.IDLE, errorMessage = null) }

    fun deleteStudentsFor(year: Int, section: String) {
        viewModelScope.launch {
            // Enqueue cloud tombstones for already-synced students so other
            // devices learn of the removal; local delete happens immediately.
            dao.getAllStudentsForSync()
                .filter { it.year == year && it.section == section && it.uuid.isNotBlank() }
                .forEach {
                    syncDao.insertDeletion(
                        PendingDeletionEntity(
                            entityType = "student",
                            uuid = it.uuid,
                            requestedAt = System.currentTimeMillis(),
                        )
                    )
                }
            dao.deleteStudentsFor(year, section)
        }
    }

    /** Manual single-student entry — complements the Excel importer. */
    fun addStudent(
        name: String,
        rollNumber: String,
        registrationNumber: String,
        year: Int,
        section: String,
    ) {
        val n = name.trim()
        val r = rollNumber.trim()
        if (n.isEmpty() || r.isEmpty()) return
        val sec = section.trim().uppercase().ifBlank { "A" }
        viewModelScope.launch {
            dao.insertStudents(
                listOf(
                    StudentEntity(
                        rollNumber = r,
                        name = n,
                        section = sec,
                        year = year,
                        registrationNumber = registrationNumber.trim(),
                        updatedAt = System.currentTimeMillis(),
                    )
                )
            )
            selectedYear.value = year
            selectedSection.value = sec
        }
    }
}

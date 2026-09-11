package com.bits.facultyai.ui.attendance

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bits.facultyai.data.local.AttendanceEntryEntity
import com.bits.facultyai.data.local.AttendanceRecordEntity
import com.bits.facultyai.data.local.ClassSlotEntity
import com.bits.facultyai.data.local.FacultyDatabase
import com.bits.facultyai.data.local.StudentEntity
import com.bits.facultyai.domain.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Status values: PRESENT, ABSENT, LATE, EXCUSED. Null = not yet marked. */
typealias Mark = String?

class AttendanceViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = FacultyDatabase.get(application).facultyDao()

    val timetable: StateFlow<List<ClassSlotEntity>> = dao.observeTimetable()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val students: StateFlow<List<StudentEntity>> = dao.observeStudents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val attendanceRecords: StateFlow<List<AttendanceRecordEntity>> = dao.observeAttendanceRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var selectedSlot = MutableStateFlow<ClassSlotEntity?>(null)
        private set

    val marks = MutableStateFlow<Map<Long, String>>(emptyMap())

    fun loadStudentsForSlot(slot: ClassSlotEntity) {
        selectedSlot.value = slot
        // Pre-mark everyone present by default for speed; faculty can flip individuals to ABSENT/LATE.
        marks.value = students.value
            .filter { it.year == slot.year && it.section == slot.section }
            .associate { it.id to "PRESENT" }
    }

    fun setMark(studentId: Long, status: String) {
        marks.value = marks.value + (studentId to status)
    }

    fun markAll(status: String) {
        val slot = selectedSlot.value ?: return
        marks.value = students.value
            .filter { it.year == slot.year && it.section == slot.section }
            .associate { it.id to status }
    }

    fun saveAttendance(onDone: () -> Unit) {
        val slot = selectedSlot.value ?: return
        viewModelScope.launch {
            val recordId = dao.insertAttendanceRecord(
                AttendanceRecordEntity(
                    classSlotId = slot.id,
                    subject = slot.subject,
                    section = slot.section,
                    date = TimeUtils.isoDate(TimeUtils.today()),
                    year = slot.year,
                    markedAt = System.currentTimeMillis(),
                    presentCount = marks.value.values.count { it == "PRESENT" },
                    absentCount = marks.value.values.count { it == "ABSENT" },
                    lateCount = marks.value.values.count { it == "LATE" },
                    excusedCount = marks.value.values.count { it == "EXCUSED" },
                )
            )
            dao.insertAttendanceEntries(
                marks.value.map { (studentId, status) ->
                    AttendanceEntryEntity(recordId = recordId, studentId = studentId, status = status)
                }
            )
            onDone()
        }
    }
}

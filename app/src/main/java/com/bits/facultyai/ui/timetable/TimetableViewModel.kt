package com.bits.facultyai.ui.timetable

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bits.facultyai.data.local.ClassSlotEntity
import com.bits.facultyai.data.local.FacultyDatabase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TimetableViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = FacultyDatabase.get(application).facultyDao()

    val timetable = dao.observeTimetable()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedDay = MutableStateFlow(java.time.LocalDate.now().dayOfWeek.value)
    val selectedDay: StateFlow<Int> = _selectedDay

    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog

    private val _attendanceEvent = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val attendanceEvent = _attendanceEvent.asSharedFlow()

    fun selectDay(day: Int) { _selectedDay.value = day }
    fun openAddDialog() { _showAddDialog.value = true }
    fun closeAddDialog() { _showAddDialog.value = false }
    fun onAttendanceRequested(slot: ClassSlotEntity) { _attendanceEvent.tryEmit(slot.id) }
    fun addSlot(slot: ClassSlotEntity) = viewModelScope.launch {
        if (dao.countOverlapping(slot.dayOfWeek, slot.startTimeMinutes, slot.endTimeMinutes) == 0) {
            dao.insertClassSlot(slot)
        }
    }

    fun deleteSlot(id: Long) = viewModelScope.launch { dao.deleteClassSlot(id) }
}

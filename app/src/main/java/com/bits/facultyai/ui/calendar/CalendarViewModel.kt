package com.bits.facultyai.ui.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bits.facultyai.data.local.AcademicEventEntity
import com.bits.facultyai.data.local.FacultyDatabase
import com.bits.facultyai.notifications.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

class CalendarViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = FacultyDatabase.get(application).facultyDao()

    val events: StateFlow<List<AcademicEventEntity>> = dao.observeAcademicEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val visibleMonth = MutableStateFlow(YearMonth.now())
    val selectedDate = MutableStateFlow<LocalDate?>(LocalDate.now())

    fun addEvent(title: String, date: LocalDate, category: String, notes: String = "") {
        if (title.isBlank()) return
        viewModelScope.launch {
            dao.insertAcademicEvent(
                AcademicEventEntity(title = title.trim(), date = date.toString(), category = category, notes = notes)
            )
        }
    }

    fun deleteEvent(id: Long) = viewModelScope.launch { dao.deleteAcademicEvent(id) }

    fun previousMonth() {
        visibleMonth.value = visibleMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        visibleMonth.value = visibleMonth.value.plusMonths(1)
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }
}

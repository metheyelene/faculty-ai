package com.bits.facultyai.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bits.facultyai.data.local.AcademicEventEntity
import com.bits.facultyai.data.local.ClassSlotEntity
import com.bits.facultyai.data.local.FacultyDatabase
import com.bits.facultyai.data.local.NoteEntity
import com.bits.facultyai.data.local.TaskEntity
import com.bits.facultyai.data.prefs.AppSettings
import com.bits.facultyai.domain.TimeUtils
import com.bits.facultyai.data.prefs.SettingsRepository
import com.bits.facultyai.data.local.FacultyProfileEntity
import com.bits.facultyai.notifications.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = FacultyDatabase.get(application).facultyDao()

    init {
        // Idempotent reminder sync on app open (no duplicates).
        viewModelScope.launch { SyncScheduler.syncAll(application) }
    }

    val profile: StateFlow<FacultyProfileEntity?> = dao.observeProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val settings: StateFlow<AppSettings?> = SettingsRepository(application).settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val timetable: StateFlow<List<ClassSlotEntity>> = dao.observeTimetable()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tasks: StateFlow<List<TaskEntity>> = dao.observeTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<NoteEntity>> = dao.observeNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val upcomingEvents: StateFlow<List<AcademicEventEntity>> = flow {
        emit(dao.getUpcomingEvents(LocalDate.now().toString(), 5))
        emitAll(dao.observeAcademicEvents().map { all ->
            all.filter { it.date >= LocalDate.now().toString() }.take(5)
        })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Ticking minute-of-day so the countdown stays live. */
    val nowMinuteOfDay: StateFlow<Int> = flow {
        while (true) {
            emit(com.bits.facultyai.domain.TimeUtils.nowMinutes())
            delay(30_000L)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimeUtils.nowMinutes())
}

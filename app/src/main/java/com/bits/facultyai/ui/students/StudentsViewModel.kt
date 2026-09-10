package com.bits.facultyai.ui.students

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bits.facultyai.data.local.FacultyDatabase
import com.bits.facultyai.data.local.StudentEntity
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn

class StudentsViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = FacultyDatabase.get(application).facultyDao()

    private val query = MutableStateFlow("")
    val searchQuery: StateFlow<String> = query

    @OptIn(FlowPreview::class)
    val students: StateFlow<List<StudentEntity>> = combine(
        dao.observeStudents(),
        query.debounce(200),
    ) { all, q ->
        if (q.isBlank()) all
        else all.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.rollNumber.contains(q, ignoreCase = true) ||
                it.section.contains(q, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val attendanceRecords = dao.observeAttendanceRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearch(q: String) { query.value = q }
}

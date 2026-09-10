package com.bits.facultyai.ui.tasks

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bits.facultyai.data.local.FacultyDatabase
import com.bits.facultyai.data.local.TaskEntity
import com.bits.facultyai.domain.NaturalDateParser
import com.bits.facultyai.domain.TimeUtils
import com.bits.facultyai.reminders.ReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TasksViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = FacultyDatabase.get(application).facultyDao()

    val tasks: StateFlow<List<TaskEntity>> = dao.observeTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Natural language quick add. Understands phrases like:
     * "tomorrow 9am submit internal marks" or "friday review reports".
     */
    fun quickAdd(text: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val parsed = NaturalDateParser.parse(text)
            val id = dao.insertTask(
                TaskEntity(
                    title = parsed.remainingText.ifBlank { text },
                    dueAt = parsed.dueAtMillis,
                    priority = "MEDIUM",
                    createdAt = System.currentTimeMillis(),
                )
            )
            parsed.dueAtMillis?.let {
                ReminderScheduler.scheduleTaskReminder(getApplication(), id, parsed.remainingText, it)
            }
            onCreated(parsed.remainingText.ifBlank { text })
        }
    }

    fun toggleComplete(task: TaskEntity) {
        viewModelScope.launch {
            dao.updateTask(task.copy(completed = !task.completed))
            if (!task.completed) {
                ReminderScheduler.cancelTaskReminder(getApplication(), task.id)
            }
        }
    }

    fun delete(task: TaskEntity) = viewModelScope.launch {
        ReminderScheduler.cancelTaskReminder(getApplication(), task.id)
        dao.deleteTask(task.id)
    }
}

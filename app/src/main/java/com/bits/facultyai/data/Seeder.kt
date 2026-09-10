package com.bits.facultyai.data

import com.bits.facultyai.data.local.FacultyDao
import com.bits.facultyai.data.local.SampleData
import com.bits.facultyai.data.local.StudentEntity

object Seeder {
    /** Populates first-run sample data. Never runs again after the first launch. */
    suspend fun seedIfFirstRun(dao: FacultyDao) {
        if (dao.countStudents() > 0) return
        dao.insertStudents(SampleData.students.map { StudentEntity(rollNumber = it.rollNumber, name = it.name, section = it.section, year = it.year) })
        SampleData.timetable.forEach { dao.insertClassSlot(it) }
        SampleData.notes.forEach {
            dao.insertNote(
                com.bits.facultyai.data.local.NoteEntity(
                    title = it.title,
                    body = it.body,
                    folder = it.folder,
                    subject = it.subject,
                    updatedAt = System.currentTimeMillis(),
                    createdAt = System.currentTimeMillis(),
                )
            )
        }
        SampleData.tasks.forEach {
            dao.insertTask(
                com.bits.facultyai.data.local.TaskEntity(
                    title = it.title,
                    dueAt = it.dueAt,
                    priority = it.priority,
                    createdAt = System.currentTimeMillis(),
                )
            )
        }
        SampleData.memories.forEach { dao.insertMemory(it) }
    }
}

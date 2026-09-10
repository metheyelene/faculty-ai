package com.bits.facultyai.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Faculty profile. Personal identity data, intentionally provided during
 * onboarding or profile editing. Single row (id = 1) for the local user.
 */
@Entity(tableName = "faculty_profile")
data class FacultyProfileEntity(
    @PrimaryKey val id: Long = 1L,
    val fullName: String,
    val preferredName: String,
    val designation: String,
    val department: String,
    val employeeId: String,
    val email: String,
    val phone: String,
    val qualification: String,
    val specialization: String,
    val cabin: String,
    val academicYear: String,
    val semester: String,
    val onboardingComplete: Boolean,
    val profileLocked: Boolean,
    val updatedAt: Long,
)

/**
 * A weekly class slot. Personal timetable — belongs to the logged-in faculty.
 */
@Entity(
    tableName = "class_slot",
    indices = [Index("dayOfWeek", "startTimeMinutes")],
)
data class ClassSlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayOfWeek: Int, // 1=Mon .. 7=Sun (java.time DayOfWeek value)
    val startTimeMinutes: Int, // minutes from midnight
    val endTimeMinutes: Int,
    val subject: String,
    val section: String,
    val room: String,
)

/**
 * One attendance session for a class slot on a specific date.
 */
@Entity(
    tableName = "attendance_record",
    indices = [Index("date", "classSlotId")],
)
data class AttendanceRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val classSlotId: Long,
    val subject: String,
    val section: String,
    val date: String, // ISO yyyy-MM-dd
    val markedAt: Long,
    val presentCount: Int,
    val absentCount: Int,
    val lateCount: Int,
)

/**
 * Per-student attendance entry within a session.
 */
@Entity(
    tableName = "attendance_entry",
    indices = [Index("recordId")],
)
data class AttendanceEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordId: Long,
    val studentId: Long,
    val status: String, // PRESENT | ABSENT | LATE
)

/**
 * Student roster (sample data seeded locally; intended to sync with the department backend).
 */
@Entity(tableName = "student", indices = [Index("section"), Index("rollNumber")])
data class StudentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rollNumber: String,
    val name: String,
    val section: String,
    val year: Int,
    val degree: String = "B.Tech ECE",
)

/**
 * A note in the faculty member's personal knowledge space.
 */
@Entity(tableName = "note", indices = [Index("folder"), Index("updatedAt")])
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val folder: String, // LECTURES, MEETINGS, RESEARCH, PERSONAL, LESSON PLANS, IDEAS
    val subject: String?,
    val updatedAt: Long = 0L,
    val createdAt: Long = 0L,
)

/**
 * Task / personal reminder with optional due date.
 */
@Entity(tableName = "task", indices = [Index("dueAt"), Index("completed")])
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val dueAt: Long?, // epoch millis, nullable = no due date
    val priority: String, // HIGH | MEDIUM | LOW
    val completed: Boolean = false,
    val createdAt: Long = 0L,
)

/**
 * A saved assistant memory — only created with user consent or from explicit user actions.
 */
@Entity(tableName = "memory", indices = [Index("createdAt")])
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String, // PROFILE | TEACHING | STUDENTS | WORK | NOTES | RESEARCH | PREFERENCES
    val text: String,
    val source: String, // what generated it, e.g. "Saved from Assistant", "Onboarding"
    val createdAt: Long = 0L,
)

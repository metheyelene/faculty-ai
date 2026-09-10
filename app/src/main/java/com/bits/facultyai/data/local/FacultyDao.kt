package com.bits.facultyai.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FacultyDao {

    // ---- Profile ----
    @Query("SELECT * FROM faculty_profile WHERE id = 1")
    fun observeProfile(): Flow<FacultyProfileEntity?>

    @Query("SELECT * FROM faculty_profile WHERE id = 1")
    suspend fun getProfile(): FacultyProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: FacultyProfileEntity)

    // ---- Timetable ----
    @Query("SELECT * FROM class_slot ORDER BY dayOfWeek, startTimeMinutes")
    fun observeTimetable(): Flow<List<ClassSlotEntity>>

    @Query("SELECT * FROM class_slot ORDER BY dayOfWeek, startTimeMinutes")
    suspend fun getTimetable(): List<ClassSlotEntity>

    @Insert
    suspend fun insertClassSlot(slot: ClassSlotEntity): Long

    @Insert
    suspend fun insertClassSlots(slots: List<ClassSlotEntity>): List<Long>

    @Update
    suspend fun updateClassSlot(slot: ClassSlotEntity)

    @Query("DELETE FROM class_slot WHERE id = :id")
    suspend fun deleteClassSlot(id: Long)

    @Query("SELECT COUNT(*) FROM class_slot WHERE dayOfWeek = :day AND startTimeMinutes < :end AND endTimeMinutes > :start AND (:excludeId IS NULL OR id != :excludeId)")
    suspend fun countOverlapping(day: Int, start: Int, end: Int, excludeId: Long? = null): Int

    // ---- Timetable versions ----
    @Query("SELECT * FROM timetable_version ORDER BY versionNumber DESC")
    fun observeTimetableVersions(): Flow<List<TimetableVersionEntity>>

    @Query("SELECT * FROM timetable_version ORDER BY versionNumber DESC")
    suspend fun getTimetableVersions(): List<TimetableVersionEntity>

    @Query("SELECT MAX(versionNumber) FROM timetable_version")
    suspend fun maxTimetableVersion(): Int?

    @Query("DELETE FROM timetable_version")
    suspend fun clearTimetableVersions()

    @Insert
    suspend fun insertTimetableVersion(version: TimetableVersionEntity): Long

    // ---- Academic events ----
    @Query("SELECT * FROM academic_event ORDER BY date")
    fun observeAcademicEvents(): Flow<List<AcademicEventEntity>>

    @Query("SELECT * FROM academic_event WHERE date >= :todayIso ORDER BY date LIMIT :limit")
    suspend fun getUpcomingEvents(todayIso: String, limit: Int): List<AcademicEventEntity>

    @Query("SELECT * FROM academic_event WHERE date = :date")
    suspend fun getEventsForDate(date: String): List<AcademicEventEntity>

    @Insert
    suspend fun insertAcademicEvent(event: AcademicEventEntity): Long

    @Update
    suspend fun updateAcademicEvent(event: AcademicEventEntity)

    @Query("DELETE FROM academic_event WHERE id = :id")
    suspend fun deleteAcademicEvent(id: Long)

    @Query("SELECT COUNT(*) FROM academic_event")
    suspend fun countAcademicEvents(): Int

    // ---- Attendance ----
    @Query("SELECT * FROM attendance_record ORDER BY markedAt DESC")
    fun observeAttendanceRecords(): Flow<List<AttendanceRecordEntity>>
    @Query("SELECT * FROM attendance_record WHERE date = :date")
    suspend fun getAttendanceForDate(date: String): List<AttendanceRecordEntity>

    @Query("SELECT * FROM attendance_entry WHERE recordId = :recordId")
    suspend fun getEntriesForRecord(recordId: Long): List<AttendanceEntryEntity>

    @Query("SELECT attendance_entry.* FROM attendance_entry INNER JOIN attendance_record ON attendance_entry.recordId = attendance_record.id WHERE attendance_entry.studentId = :studentId")
    fun observeAttendanceEntriesForStudent(studentId: Long): Flow<List<AttendanceEntryEntity>>

    @Insert
    suspend fun insertAttendanceRecord(record: AttendanceRecordEntity): Long

    @Insert
    suspend fun insertAttendanceEntries(entries: List<AttendanceEntryEntity>): List<Long>
    @Query("SELECT * FROM student ORDER BY year, section, rollNumber")
    fun observeStudents(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM student WHERE year = :year AND section = :section ORDER BY rollNumber")
    fun observeStudentsFor(year: Int, section: String): Flow<List<StudentEntity>>

    @Query("SELECT * FROM student WHERE year = :year AND section = :section ORDER BY rollNumber")
    suspend fun getStudentsFor(year: Int, section: String): List<StudentEntity>

    /** Distinct sections imported for a given year — drives the section chips (data, not hardcode). */
    @Query("SELECT DISTINCT section FROM student WHERE year = :year ORDER BY section")
    fun observeDistinctSectionsFor(year: Int): Flow<List<String>>

    @Query("SELECT * FROM student WHERE id = :id LIMIT 1")
    suspend fun getStudent(id: Long): StudentEntity?

    @Insert
    suspend fun insertStudents(students: List<StudentEntity>): List<Long>

    @Query("SELECT * FROM student WHERE year = :year AND section = :section AND (LOWER(rollNumber) = LOWER(:roll) OR (registrationNumber != '' AND LOWER(registrationNumber) = LOWER(:reg)))")
    suspend fun findExistingStudents(year: Int, section: String, roll: String, reg: String): List<StudentEntity>

    @Update
    suspend fun updateStudent(student: StudentEntity)

    @Query("DELETE FROM student WHERE year = :year AND section = :section")
    suspend fun deleteStudentsFor(year: Int, section: String)

    @Query("SELECT COUNT(*) FROM student")
    suspend fun countStudents(): Int

    @Query("SELECT COUNT(*) FROM student WHERE year = :year AND section = :section")
    suspend fun countStudentsFor(year: Int, section: String): Int

    // ---- Notes ----
    @Query("SELECT * FROM note ORDER BY updatedAt DESC")
    fun observeNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM note WHERE id = :id LIMIT 1")
    suspend fun getNote(id: Long): NoteEntity?

    @Insert
    suspend fun insertNote(note: NoteEntity): Long

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Query("DELETE FROM note WHERE id = :id")
    suspend fun deleteNote(id: Long)

    // ---- Tasks ----
    @Query("SELECT * FROM task ORDER BY completed, dueAt IS NULL, dueAt, createdAt DESC")
    fun observeTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM task WHERE id = :id LIMIT 1")
    suspend fun getTask(id: Long): TaskEntity?

    @Insert
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("DELETE FROM task WHERE id = :id")
    suspend fun deleteTask(id: Long)

    @Query("SELECT COUNT(*) FROM task WHERE completed = 0")
    suspend fun countOpenTasks(): Int

    @Query("SELECT * FROM task")
    suspend fun getTasks(): List<TaskEntity>

    @Query("SELECT * FROM note")
    suspend fun getNotes(): List<NoteEntity>

    @Query("SELECT * FROM student")
    suspend fun getStudents(): List<StudentEntity>

    @Query("SELECT * FROM academic_event")
    suspend fun getAcademicEvents(): List<AcademicEventEntity>

    // ---- Memory ----
    @Query("SELECT * FROM memory ORDER BY createdAt DESC")
    fun observeMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memory ORDER BY createdAt DESC")
    suspend fun getMemories(): List<MemoryEntity>

    @Insert
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Update
    suspend fun updateMemory(memory: MemoryEntity)

    @Query("DELETE FROM memory WHERE id = :id")
    suspend fun deleteMemory(id: Long)

    @Query("DELETE FROM memory")
    suspend fun clearMemories()

    @Query("DELETE FROM class_slot")
    suspend fun clearTimetable()

    @Query("DELETE FROM task")
    suspend fun clearTasks()

    @Query("DELETE FROM note")
    suspend fun clearNotes()

    @Query("DELETE FROM attendance_record")
    suspend fun clearAttendance()

    @Query("DELETE FROM attendance_entry")
    suspend fun clearAttendanceEntries()

    @Query("DELETE FROM student")
    suspend fun clearStudents()

    @Query("DELETE FROM academic_event")
    suspend fun clearAcademicEvents()
}

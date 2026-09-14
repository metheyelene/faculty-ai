package com.bits.facultyai.data.demo

import android.content.Context
import androidx.room.withTransaction
import com.bits.facultyai.data.local.FacultyDatabase

/**
 * Loads/wipes the demo dataset. Insert is idempotent (a second tap is a
 * no-op) and demo rows are fully removable — [wipe] deletes exactly the
 * marker-scoped rows it created, never the user's real data.
 */
object DemoData {

    sealed interface Result {
        data object Loaded : Result
        data object AlreadyLoaded : Result
        data class Failed(val message: String) : Result
    }

    /**
     * Idempotent. If any demo slot already exists, nothing is inserted (the
     * dataset is deterministic, so a partial load can be safely re-run).
     */
    suspend fun load(context: Context): Result = try {
        val db = FacultyDatabase.get(context)
        val dao = db.facultyDao()
        db.withTransaction {
            if (dao.countDemoSlots() > 0) {
                Result.AlreadyLoaded
            } else {
                // 1. Timetable (subjects carry the marker so the dashboard labels itself).
                val slots = DemoDataFactory.timetable()
                val slotIds = dao.insertClassSlots(slots)
                val markedSlots = slots.zip(slotIds) { s, id -> s.copy(id = id) }

                // 2. Rosters for both demo sections.
                val rosterA = dao.insertStudents(DemoDataFactory.studentsFor(3, DemoDataFactory.SECTION_A))
                    .zip(DemoDataFactory.studentsFor(3, DemoDataFactory.SECTION_A)) { id, s -> s.copy(id = id) }
                val rosterB = dao.insertStudents(DemoDataFactory.studentsFor(3, DemoDataFactory.SECTION_B))
                    .zip(DemoDataFactory.studentsFor(3, DemoDataFactory.SECTION_B)) { id, s -> s.copy(id = id) }

                // 3. Two weeks of attendance sessions per slot.
                val sessions = DemoDataFactory.buildAttendance(
                    markedSlots,
                    mapOf(
                        DemoDataFactory.SECTION_A to rosterA.map { it.id to it.rollNumber },
                        DemoDataFactory.SECTION_B to rosterB.map { it.id to it.rollNumber },
                    ),
                )
                for ((record, entries) in sessions) {
                    val recordId = dao.insertAttendanceRecord(record)
                    dao.insertAttendanceEntries(entries.map { it.copy(recordId = recordId) })
                }

                // 4. Tasks and notes.
                val today = java.time.LocalDate.now()
                for (t in DemoDataFactory.tasks(today)) dao.insertTask(t)
                val now = System.currentTimeMillis()
                for (n in DemoDataFactory.notes(now)) dao.insertNote(n)

                // 5. The flagship event + finances.
                val eventId = dao.insertEvent(DemoDataFactory.event())
                for (e in DemoDataFactory.expenses(eventId)) dao.insertEventExpense(e)
                for (c in DemoDataFactory.collections(eventId)) dao.insertEventCollection(c)
            }
        }
        Result.Loaded
    } catch (e: Exception) {
        Result.Failed(e.message ?: "Demo load failed")
    }

    /**
     * Removes every demo row by marker/section scope. Timetable slots are
     * matched on the demo subjects (which carry the marker); attendance rows
     * go with their slots (a slot cascade the user could not have created —
     * the demo owns the only sessions pointing at these slot ids).
     */
    suspend fun wipe(context: Context) {
        val db = FacultyDatabase.get(context)
        val dao = db.facultyDao()
        db.withTransaction {
            val slotIds = dao.demoSlotIds()
            if (slotIds.isNotEmpty()) {
                dao.deleteEntriesForSlots(slotIds)
                dao.deleteRecordsForSlots(slotIds)
                dao.deleteSlotsByIds(slotIds)
            }
            dao.deleteDemoStudents()
            dao.deleteDemoNotes()
            dao.deleteDemoTasks()
            val eventIds = dao.demoEventIds()
            if (eventIds.isNotEmpty()) {
                dao.deleteCollectionsForEvents(eventIds)
                dao.deleteExpensesForEvents(eventIds)
                dao.deletePhotosForEvents(eventIds)
                dao.deleteEventsByIds(eventIds)
            }
        }
    }
}

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
     * Idempotent. The timetable/roster/finance block is all-or-nothing (any
     * demo slot present ⇒ already loaded); the calendar seeds independently
     * so a demo dataset created before calendar seeding gains it on re-tap.
     */
    suspend fun load(context: Context): Result = try {
        val db = FacultyDatabase.get(context)
        val dao = db.facultyDao()
        var changed = false
        db.withTransaction {
            if (dao.countDemoSlots() == 0) {
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

                // 5. Three events — upcoming fest, workshop (own budget) and a
                //    past seminar — so UPCOMING/PAST filters and per-event
                //    finances are all demonstrable.
                val eventId = dao.insertEvent(DemoDataFactory.event())
                for (e in DemoDataFactory.expenses(eventId)) dao.insertEventExpense(e)
                for (c in DemoDataFactory.collections(eventId)) dao.insertEventCollection(c)

                val workshopId = dao.insertEvent(DemoDataFactory.workshopEvent())
                for (e in DemoDataFactory.workshopExpenses(workshopId)) dao.insertEventExpense(e)
                for (c in DemoDataFactory.workshopCollections(workshopId)) dao.insertEventCollection(c)

                val seminarId = dao.insertEvent(DemoDataFactory.seminarEvent())
                for (c in DemoDataFactory.seminarCollection(seminarId)) dao.insertEventCollection(c)
                changed = true
            }
            // 6. Calendar (exams + holidays) — seeded even for older demo sets.
            if (dao.countDemoAcademicEvents() == 0) {
                for (e in DemoDataFactory.calendarEvents(java.time.LocalDate.now())) dao.insertAcademicEvent(e)
                changed = true
            }
            // 7. Memories — same top-up semantics for pre-existing demo sets.
            if (dao.countDemoMemories() == 0) {
                for (m in DemoDataFactory.memories(System.currentTimeMillis())) dao.insertMemory(m)
                changed = true
            }
        }
        if (changed) Result.Loaded else Result.AlreadyLoaded
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
            dao.deleteDemoAcademicEvents()
            dao.deleteDemoMemories()
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

package com.bits.facultyai.data.demo

import com.bits.facultyai.data.local.AttendanceEntryEntity
import com.bits.facultyai.data.local.AttendanceRecordEntity
import com.bits.facultyai.data.local.ClassSlotEntity
import com.bits.facultyai.data.local.EventCollectionEntity
import com.bits.facultyai.data.local.EventEntity
import com.bits.facultyai.data.local.EventExpenseEntity
import com.bits.facultyai.data.local.NoteEntity
import com.bits.facultyai.data.local.StudentEntity
import com.bits.facultyai.data.local.TaskEntity
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The demo dataset shown to presentation audiences. Everything it inserts is
 * synthetic (generated roll numbers and amounts), carries the visible
 * "[DEMO]" marker, and is removable in one tap — [DemoData.wipe] — so demo
 * mode never mixes with the user's real records.
 *
 * Deterministic: the same table every time (fixed names, seeded attendance
 * pattern), so repeated demos look identical. Dates are computed relative to
 * "today" so the timetable, dashboard and event screens all have live content.
 */
object DemoDataFactory {

    /** Marker prefix on every demo row's primary label. */
    const val MARKER = "[DEMO]"

    /** Used demo sections/subjects — wipe targets match these exactly. */
    const val SECTION_A = "ECE-A"
    const val SECTION_B = "ECE-B"
    val DEMO_SECTIONS = listOf(SECTION_A, SECTION_B)
    val DEMO_SUBJECTS =
        listOf("Digital Signal Processing", "VLSI Design", "Antennas & Wave Propagation", "Microcontrollers")

    // 24 students per section — enough to look real, few enough to scroll fast.
    private val FIRST =
        listOf("Aarav", "Diya", "Ishaan", "Kavya", "Arjun", "Meera", "Rohan", "Ananya", "Vikram", "Sneha",
            "Karthik", "Pooja", "Aditya", "Nikhil", "Srivalli", "Tejas", "Harsha", "Likhitha", "Rahul", "Charan")
    private val LAST = listOf("Sharma", "Reddy", "Iyer", "Das", "Patel", "Rao", "Menon", "Verma", "Nair", "Kulkarni")

    fun studentsFor(year: Int, section: String): List<StudentEntity> {
        val now = System.currentTimeMillis()
        return (1..24).map { n ->
            val name = "${FIRST[n % FIRST.size]} ${LAST[(n * 3) % LAST.size]}"
            StudentEntity(
                rollNumber = "${22 + year - 1}B81A${if (section == SECTION_A) "04" else "05"}%02d".format(n),
                name = "$MARKER $name",
                section = section,
                year = year,
                registrationNumber = "${22 + year - 1}B81A${if (section == SECTION_A) "04" else "05"}%02d".format(n),
                email = "student%02d@bitsvizag-demo.edu".format(n),
                degree = "B.Tech ECE",
                updatedAt = now,
            )
        }
    }

    fun timetable(): List<ClassSlotEntity> {
        val now = System.currentTimeMillis()
        // Mon–Fri, four teaching periods, shared across sections/years.
        val periods = listOf(540 to 630, 630 to 720, 750 to 840, 840 to 930) // 9:00…15:30
        val grid = listOf(
            Triple(DayOfWeek.MONDAY, DEMO_SUBJECTS[0], SECTION_A),
            Triple(DayOfWeek.MONDAY, DEMO_SUBJECTS[1], SECTION_B),
            Triple(DayOfWeek.TUESDAY, DEMO_SUBJECTS[2], SECTION_A),
            Triple(DayOfWeek.TUESDAY, DEMO_SUBJECTS[3], SECTION_B),
            Triple(DayOfWeek.WEDNESDAY, DEMO_SUBJECTS[1], SECTION_A),
            Triple(DayOfWeek.WEDNESDAY, DEMO_SUBJECTS[0], SECTION_B),
            Triple(DayOfWeek.THURSDAY, DEMO_SUBJECTS[3], SECTION_A),
            Triple(DayOfWeek.THURSDAY, DEMO_SUBJECTS[2], SECTION_B),
            Triple(DayOfWeek.FRIDAY, DEMO_SUBJECTS[0], SECTION_A),
            Triple(DayOfWeek.FRIDAY, DEMO_SUBJECTS[1], SECTION_B),
        )
        return grid.mapIndexed { i, (day, subject, section) ->
            val (start, end) = periods[i % periods.size]
            ClassSlotEntity(
                dayOfWeek = day.value,
                startTimeMinutes = start,
                endTimeMinutes = end,
                subject = subject,
                section = section,
                room = "LH-${'A' + (i % 4)}",
                year = 3,
                updatedAt = now,
            )
        }
    }

    fun tasks(today: LocalDate): List<TaskEntity> {
        val now = System.currentTimeMillis()
        fun at(day: LocalDate, hour: Int) =
            day.atTime(hour, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        return listOf(
            TaskEntity(
                title = "$MARKER Submit internal marks — DSP",
                description = "Enter CIA-1 marks for III ECE-A before 5 PM.",
                dueAt = at(today, 17),
                priority = "HIGH",
                category = "EXAM",
                createdAt = now,
            ),
            TaskEntity(
                title = "$MARKER Prep VLSI lab manual",
                description = "Update experiment 4 (FIFO depth) handout.",
                dueAt = at(today.plusDays(2), 10),
                priority = "MEDIUM",
                category = "CLASS",
                createdAt = now,
            ),
            TaskEntity(
                title = "$MARKER Department meeting notes",
                description = "Circulate minutes from the curriculum review.",
                dueAt = at(today.plusDays(5), 14),
                priority = "LOW",
                category = "MEETING",
                createdAt = now,
            ),
        )
    }

    fun notes(now: Long): List<NoteEntity> = listOf(
        NoteEntity(
            title = "$MARKER DSP — Lecture 12 notes",
            body = "Z-transform: region of convergence rules; cascade vs parallel " +
                "realization; worked example on pole-zero cancellation.",
            folder = "LECTURES",
            subject = "Digital Signal Processing",
            favorite = true,
            updatedAt = now,
            createdAt = now,
        ),
        NoteEntity(
            title = "$MARKER VLSI — setup & hold margin",
            body = "Setup: data before clock edge. Hold: data after edge. " +
                "Skew trade-off summary with the two timing diagrams.",
            folder = "LECTURES",
            subject = "VLSI Design",
            updatedAt = now,
            createdAt = now,
        ),
        NoteEntity(
            title = "$MARKER Project review questions",
            body = "Antenna array factor derivation; grating lobe condition; " +
                "questions the panel asked last year.",
            folder = "LESSON PLANS",
            subject = "Antennas & Wave Propagation",
            updatedAt = now,
            createdAt = now,
        ),
    )

    /**
     * Attendance for [slotsCount] past teaching days: for each slot, a session
     * with per-student statuses from a fixed pattern (~82% present) so class
     * averages are stable between demos.
     */
    fun buildAttendance(
        slots: List<ClassSlotEntity>,
        rosterBySection: Map<String, List<Pair<Long, String>>>, // section -> (studentId, roll)
    ): List<Pair<AttendanceRecordEntity, List<AttendanceEntryEntity>>> {
        val out = mutableListOf<Pair<AttendanceRecordEntity, List<AttendanceEntryEntity>>>()
        val today = LocalDate.now()
        var day = today.minusDays(1)
        var sessions = 0
        while (sessions < 12 && day.isAfter(today.minusDays(21))) {
            val dow = day.dayOfWeek.value
            if (dow <= 5) {
                val daySlots = slots.filter { it.dayOfWeek == dow }
                for (slot in daySlots) {
                    val roster = rosterBySection[slot.section] ?: continue
                    val now = System.currentTimeMillis()
                    val entries = roster.mapIndexed { idx, (studentId, _) ->
                        // Deterministic ~82% present, ~9% absent, ~6% late, ~3% excused.
                        val v = (idx * 7 + day.dayOfMonth * 3 + sessions) % 100
                        val status = when {
                            v < 82 -> "PRESENT"
                            v < 91 -> "ABSENT"
                            v < 97 -> "LATE"
                            else -> "EXCUSED"
                        }
                        AttendanceEntryEntity(
                            recordId = 0, // patched after record insert
                            studentId = studentId,
                            status = status,
                            updatedAt = now,
                        )
                    }
                    val record = AttendanceRecordEntity(
                        classSlotId = slot.id,
                        subject = slot.subject,
                        section = slot.section,
                        date = day.toString(),
                        year = slot.year,
                        markedAt = day.atTime(9, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        presentCount = entries.count { it.status == "PRESENT" },
                        absentCount = entries.count { it.status == "ABSENT" },
                        lateCount = entries.count { it.status == "LATE" },
                        excusedCount = entries.count { it.status == "EXCUSED" },
                        updatedAt = System.currentTimeMillis(),
                    )
                    out += record to entries
                }
                sessions++
            }
            day = day.minusDays(1)
        }
        return out
    }

    /** The flagship event + its finances — the balances shown in the request examples. */
    fun event(): EventEntity {
        val now = System.currentTimeMillis()
        val date = LocalDate.now().plusDays(4) // upcoming during a demo
        return EventEntity(
            name = "$MARKER ECE Technical Fest",
            date = date.toString(),
            startTimeMinutes = 600, // 10:00 AM
            endTimeMinutes = 960,  // 4:00 PM
            venue = "Seminar Hall",
            description = "Project expo, circuit debugging contest and a technical quiz. " +
                "Demo record with sample finances for presentation.",
            organizer = "ECE Department",
            department = "ECE",
            participants = "III ECE-A, III ECE-B",
            category = "FEST",
            notes = "$MARKER — sample data; delete from Settings → Demo data.",
            createdAt = now,
            updatedAt = now,
        )
    }

    fun expenses(eventId: Long): List<EventExpenseEntity> {
        val now = System.currentTimeMillis()
        val d = LocalDate.now().toString()
        fun rupees(r: Long) = r * 100
        return listOf(
            EventExpenseEntity(eventId = eventId, title = "$MARKER Hall decoration", category = "DECORATION",
                amountPaisa = rupees(6200), date = d,
                paidBy = "Faculty coordinator", paymentMethod = "UPI", vendor = "Sri Balaji Decorators",
                description = "Stage + entrance", createdAt = now, updatedAt = now),
            EventExpenseEntity(eventId = eventId, title = "$MARKER Lunch — volunteers", category = "FOOD",
                amountPaisa = rupees(5400), date = d,
                paidBy = "Student coordinator", paymentMethod = "CASH", vendor = "Annapurna Caterers",
                createdAt = now, updatedAt = now),
            EventExpenseEntity(eventId = eventId, title = "$MARKER Banner & certificates", category = "PRINTING",
                amountPaisa = rupees(3850), date = d,
                paidBy = "Faculty coordinator", paymentMethod = "CARD", vendor = "Sai Printers",
                createdAt = now, updatedAt = now),
            EventExpenseEntity(eventId = eventId, title = "$MARKER Quiz buzzer rental", category = "EQUIPMENT",
                amountPaisa = rupees(3000), date = d,
                paidBy = "Tech club", paymentMethod = "UPI", vendor = "AV Rentals Vizag",
                createdAt = now, updatedAt = now),
        )
    }

    fun collections(eventId: Long): List<EventCollectionEntity> {
        val now = System.currentTimeMillis()
        val d = LocalDate.now().toString()
        fun rupees(r: Long) = r * 100
        return listOf(
            EventCollectionEntity(eventId = eventId, source = "Department fund", amountPaisa = rupees(10000), date = d,
                paymentMethod = "BANK", purpose = "Fest budget sanction", reference = "DF-2026-114", createdAt = now, updatedAt = now),
            EventCollectionEntity(eventId = eventId, source = "Registration fees", amountPaisa = rupees(8000), date = d,
                paymentMethod = "UPI", purpose = "24 teams × ₹333", reference = "REG-8891", createdAt = now, updatedAt = now),
            EventCollectionEntity(eventId = eventId, source = "Sponsorship — local firm", amountPaisa = rupees(7000), date = d,
                paymentMethod = "CHEQUE", purpose = "Banner sponsorship", reference = "SPN-221", createdAt = now, updatedAt = now),
        )
    }
}

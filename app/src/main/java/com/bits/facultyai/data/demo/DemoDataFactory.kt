package com.bits.facultyai.data.demo

import com.bits.facultyai.data.local.AcademicEventEntity
import com.bits.facultyai.data.local.AttendanceEntryEntity
import com.bits.facultyai.data.local.AttendanceRecordEntity
import com.bits.facultyai.data.local.ClassSlotEntity
import com.bits.facultyai.data.local.EventCollectionEntity
import com.bits.facultyai.data.local.EventEntity
import com.bits.facultyai.data.local.EventExpenseEntity
import com.bits.facultyai.data.local.MemoryEntity
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
        // Mon–Sat, three teaching periods a day, both sections — every day of
        // the week has live content so a demo never lands on an empty day.
        val periods = listOf(540 to 630, 630 to 720, 750 to 840) // 9:00…14:00
        data class Entry(val day: DayOfWeek, val subject: String, val section: String, val period: Int)
        val grid = listOf(
            Entry(DayOfWeek.MONDAY, DEMO_SUBJECTS[0], SECTION_A, 0),
            Entry(DayOfWeek.MONDAY, DEMO_SUBJECTS[1], SECTION_B, 0),
            Entry(DayOfWeek.MONDAY, DEMO_SUBJECTS[2], SECTION_A, 1),
            Entry(DayOfWeek.MONDAY, DEMO_SUBJECTS[3], SECTION_B, 2),
            Entry(DayOfWeek.TUESDAY, DEMO_SUBJECTS[1], SECTION_A, 0),
            Entry(DayOfWeek.TUESDAY, DEMO_SUBJECTS[2], SECTION_B, 0),
            Entry(DayOfWeek.TUESDAY, DEMO_SUBJECTS[3], SECTION_A, 1),
            Entry(DayOfWeek.TUESDAY, DEMO_SUBJECTS[0], SECTION_B, 2),
            Entry(DayOfWeek.WEDNESDAY, DEMO_SUBJECTS[2], SECTION_A, 0),
            Entry(DayOfWeek.WEDNESDAY, DEMO_SUBJECTS[3], SECTION_B, 0),
            Entry(DayOfWeek.WEDNESDAY, DEMO_SUBJECTS[0], SECTION_A, 1),
            Entry(DayOfWeek.WEDNESDAY, DEMO_SUBJECTS[1], SECTION_B, 2),
            Entry(DayOfWeek.THURSDAY, DEMO_SUBJECTS[3], SECTION_A, 0),
            Entry(DayOfWeek.THURSDAY, DEMO_SUBJECTS[0], SECTION_B, 0),
            Entry(DayOfWeek.THURSDAY, DEMO_SUBJECTS[1], SECTION_A, 1),
            Entry(DayOfWeek.THURSDAY, DEMO_SUBJECTS[2], SECTION_B, 2),
            Entry(DayOfWeek.FRIDAY, DEMO_SUBJECTS[0], SECTION_A, 0),
            Entry(DayOfWeek.FRIDAY, DEMO_SUBJECTS[2], SECTION_B, 0),
            Entry(DayOfWeek.FRIDAY, DEMO_SUBJECTS[1], SECTION_A, 1),
            Entry(DayOfWeek.FRIDAY, DEMO_SUBJECTS[3], SECTION_B, 2),
            Entry(DayOfWeek.SATURDAY, DEMO_SUBJECTS[1], SECTION_A, 0),
            Entry(DayOfWeek.SATURDAY, DEMO_SUBJECTS[3], SECTION_B, 0),
            Entry(DayOfWeek.SATURDAY, DEMO_SUBJECTS[2], SECTION_A, 1),
        )
        return grid.mapIndexed { i, e ->
            val (start, end) = periods[e.period]
            ClassSlotEntity(
                dayOfWeek = e.day.value,
                startTimeMinutes = start,
                endTimeMinutes = end,
                subject = e.subject,
                section = e.section,
                room = "LH-${'A' + (i % 6)}",
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
            TaskEntity(
                title = "$MARKER Weekly quiz — DSP",
                description = "Set 10 questions on sampling and quantization; upload to the class group.",
                dueAt = at(today.plusDays(3), 9),
                priority = "MEDIUM",
                category = "CLASS",
                recurrence = "WEEKLY",
                createdAt = now,
            ),
            TaskEntity(
                title = "$MARKER Submit lab budget request",
                description = "Equipment list for the microcontrollers lab — forward to the HOD.",
                dueAt = at(today.plusDays(7), 12),
                priority = "HIGH",
                category = "ADMIN",
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
        NoteEntity(
            title = "$MARKER Lesson plan — Microcontrollers Lab 4",
            body = "Objective: timer interrupts in C. Equipment: 8 kits, oscilloscopes. " +
                "Demo circuit: PWM-driven LED brightness. Wrap-up quiz (5 min).",
            folder = "LESSON PLANS",
            subject = "Microcontrollers",
            updatedAt = now,
            createdAt = now,
        ),
        NoteEntity(
            title = "$MARKER Faculty meeting — curriculum minutes",
            body = "Attendees: HOD, 6 faculty. Decisions: CIA-2 moved to week 8; " +
                "lab manuals due before mid-sem; new elective proposed for VI sem.",
            folder = "MEETINGS",
            subject = null,
            updatedAt = now,
            createdAt = now,
        ),
        NoteEntity(
            title = "$MARKER Research — phased-array calibration paper",
            body = "Reading: mutual-coupling compensation in adaptive arrays. " +
                "Gap: calibration drift under thermal load — possible review topic.",
            folder = "RESEARCH",
            subject = "Antennas & Wave Propagation",
            favorite = true,
            updatedAt = now,
            createdAt = now,
        ),
        NoteEntity(
            title = "$MARKER Open-house demo ideas",
            body = "1. Live FFT of microphone input on the classroom display. " +
                "2. Student-built line follower track. 3. QR-linked project videos.",
            folder = "IDEAS",
            subject = null,
            updatedAt = now,
            createdAt = now,
        ),
        NoteEntity(
            title = "$MARKER Personal — committee travel",
            body = "Boarding pass printed; carry the sanction letter for the " +
                "TA/DA claim. Remind the office about the substitute for Monday.",
            folder = "PERSONAL",
            subject = null,
            updatedAt = now,
            createdAt = now,
        ),
    )

    /**
     * What the assistant "knows" about this faculty member — gives MY MEMORY
     * and the AI screen real, personalized content during a demo.
     */
    fun memories(now: Long): List<MemoryEntity> = listOf(
        MemoryEntity(
            category = "TEACHING",
            text = "This semester I teach Digital Signal Processing, VLSI Design, " +
                "Antennas & Wave Propagation and Microcontrollers to III ECE.",
            source = "$MARKER Presentation",
            createdAt = now,
        ),
        MemoryEntity(
            category = "STUDENTS",
            text = "III ECE-A has 24 students; Aarav Sharma is the class representative.",
            source = "$MARKER Presentation",
            createdAt = now,
        ),
        MemoryEntity(
            category = "WORK",
            text = "I coordinate the department technical fest budget and the lab equipment requests.",
            source = "$MARKER Presentation",
            createdAt = now,
        ),
        MemoryEntity(
            category = "PREFERENCES",
            text = "Schedule my class reminders 30 minutes before the period starts.",
            source = "$MARKER Presentation",
            createdAt = now,
        ),
        MemoryEntity(
            category = "NOTES",
            text = "My DSP lecture notes are numbered by lecture; quizzes reference the latest two lectures.",
            source = "$MARKER Presentation",
            createdAt = now,
        ),
        MemoryEntity(
            category = "RESEARCH",
            text = "Currently reviewing papers on phased-array calibration for the ECE seminar series.",
            source = "$MARKER Presentation",
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
        while (sessions < 18 && day.isAfter(today.minusDays(21))) {
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
                sessions += daySlots.size
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
            notes = "$MARKER — sample data; remove from More → Presentation mode.",
            createdAt = now,
            updatedAt = now,
        )
    }

    /** A second upcoming event so filtering between events is demonstrable. */
    fun workshopEvent(): EventEntity {
        val now = System.currentTimeMillis()
        val date = LocalDate.now().plusDays(10)
        return EventEntity(
            name = "$MARKER PCB Design Workshop",
            date = date.toString(),
            startTimeMinutes = 600,
            endTimeMinutes = 780, // 10:00 AM – 1:00 PM
            venue = "Electronics Lab 2",
            description = "Hands-on schematic capture and layout with industry mentors; " +
                "each team fabricates a small board.",
            organizer = "Tech Club",
            department = "ECE",
            participants = "III ECE-A (12 teams)",
            category = "WORKSHOP",
            notes = "$MARKER — sample data; remove from More → Presentation mode.",
            createdAt = now,
            updatedAt = now,
        )
    }

    /** A past event so the PAST filter has content. */
    fun seminarEvent(): EventEntity {
        val now = System.currentTimeMillis()
        val date = LocalDate.now().minusDays(6)
        return EventEntity(
            name = "$MARKER Alumni Industry Talk",
            date = date.toString(),
            startTimeMinutes = 630, // 10:30 AM
            endTimeMinutes = 750,   // 12:30 PM
            venue = "Auditorium",
            description = "Alumni from semiconductor and telecom firms on career paths " +
                "and what they wish they had learned in college.",
            organizer = "Alumni Cell",
            department = "ECE",
            participants = "II & III ECE",
            category = "SEMINAR",
            notes = "$MARKER — sample data; remove from More → Presentation mode.",
            createdAt = now,
            updatedAt = now,
        )
    }

    /** Modest finances for the workshop — shows totals are per-event. */
    fun workshopExpenses(eventId: Long): List<EventExpenseEntity> {
        val now = System.currentTimeMillis()
        val d = LocalDate.now().toString()
        fun rupees(r: Long) = r * 100
        return listOf(
            EventExpenseEntity(eventId = eventId, title = "$MARKER PCB blanks & components", category = "EQUIPMENT",
                amountPaisa = rupees(4200), date = d,
                paidBy = "Tech club", paymentMethod = "UPI", vendor = "Vizag Component House",
                createdAt = now, updatedAt = now),
            EventExpenseEntity(eventId = eventId, title = "$MARKER Mentor travel & refreshments", category = "FOOD",
                amountPaisa = rupees(2500), date = d,
                paidBy = "Faculty coordinator", paymentMethod = "CASH",
                createdAt = now, updatedAt = now),
        )
    }

    fun workshopCollections(eventId: Long): List<EventCollectionEntity> {
        val now = System.currentTimeMillis()
        val d = LocalDate.now().toString()
        fun rupees(r: Long) = r * 100
        return listOf(
            EventCollectionEntity(eventId = eventId, source = "Registration fees (12 teams)",
                amountPaisa = rupees(6000), date = d, paymentMethod = "UPI",
                purpose = "₹500 per team", reference = "REG-W12", createdAt = now, updatedAt = now),
            EventCollectionEntity(eventId = eventId, source = "Sponsorship — local PCB firm",
                amountPaisa = rupees(3000), date = d, paymentMethod = "BANK",
                purpose = "Kit sponsorship", reference = "SPN-W3", createdAt = now, updatedAt = now),
        )
    }

    fun seminarCollection(eventId: Long): List<EventCollectionEntity> {
        val now = System.currentTimeMillis()
        val d = LocalDate.now().minusDays(6).toString()
        return listOf(
            EventCollectionEntity(eventId = eventId, source = "Alumni Cell grant",
                amountPaisa = 500000L, date = d, paymentMethod = "BANK",
                purpose = "Speaker honorarium and logistics", reference = "AC-2026-42",
                createdAt = now, updatedAt = now),
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

    /**
     * Academic-calendar seeding: an exam block, a mid-semester break and a
     * deadline, all relative to today so the calendar always has upcoming
     * content during a demo. Marked and removable like every demo row.
     */
    fun calendarEvents(today: LocalDate): List<AcademicEventEntity> {
        fun d(days: Long) = today.plusDays(days).toString()
        return listOf(
            AcademicEventEntity(
                title = "$MARKER CIA-2 Examinations",
                date = d(7),
                endDate = d(9),
                category = "EXAM",
                notes = "All III ECE sections · Forenoon 9:30 AM, Afternoon 2:00 PM.",
            ),
            AcademicEventEntity(
                title = "$MARKER Mid-semester Break",
                date = d(16),
                endDate = d(20),
                category = "HOLIDAY",
                notes = "Institute closed · Classes resume Monday.",
            ),
            AcademicEventEntity(
                title = "$MARKER Project review — Phase 1",
                date = d(25),
                category = "DEADLINE",
                notes = "Phase-1 report submission by 4 PM.",
            ),
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

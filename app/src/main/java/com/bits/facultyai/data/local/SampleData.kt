package com.bits.facultyai.data.local

/** First-run demo content so every screen shows real, useful information immediately. */
object SampleData {

    val timetable = listOf(
        ClassSlotEntity(dayOfWeek = 1, startTimeMinutes = 9 * 60, endTimeMinutes = 10 * 60, subject = "Digital Signal Processing", section = "III ECE-A", room = "204"),
        ClassSlotEntity(dayOfWeek = 1, startTimeMinutes = 11 * 60, endTimeMinutes = 12 * 60, subject = "Communication Systems", section = "II ECE-B", room = "105"),
        ClassSlotEntity(dayOfWeek = 1, startTimeMinutes = 2 * 60 + 30, endTimeMinutes = 3 * 60 + 30, subject = "DSP Lab", section = "III ECE-A", room = "Lab 2"),
        ClassSlotEntity(dayOfWeek = 2, startTimeMinutes = 10 * 60, endTimeMinutes = 11 * 60, subject = "Communication Systems", section = "II ECE-B", room = "105"),
        ClassSlotEntity(dayOfWeek = 2, startTimeMinutes = 2 * 60, endTimeMinutes = 3 * 60, subject = "Digital Signal Processing", section = "III ECE-A", room = "204"),
        ClassSlotEntity(dayOfWeek = 3, startTimeMinutes = 9 * 60, endTimeMinutes = 10 * 60, subject = "VLSI Design", section = "IV ECE-B", room = "301"),
        ClassSlotEntity(dayOfWeek = 3, startTimeMinutes = 11 * 60, endTimeMinutes = 12 * 60, subject = "Communication Systems", section = "II ECE-B", room = "105"),
        ClassSlotEntity(dayOfWeek = 4, startTimeMinutes = 9 * 60, endTimeMinutes = 10 * 60, subject = "Digital Signal Processing", section = "III ECE-A", room = "204"),
        ClassSlotEntity(dayOfWeek = 4, startTimeMinutes = 10 * 60, endTimeMinutes = 11 * 60, subject = "VLSI Design", section = "IV ECE-B", room = "301"),
        ClassSlotEntity(dayOfWeek = 5, startTimeMinutes = 10 * 60, endTimeMinutes = 11 * 60, subject = "Digital Signal Processing", section = "III ECE-A", room = "204"),
        ClassSlotEntity(dayOfWeek = 5, startTimeMinutes = 11 * 60, endTimeMinutes = 12 * 60, subject = "DSP Lab", section = "III ECE-A", room = "Lab 2"),
    )

    val students = listOf(
        StudentEntity(rollNumber = "225L1A0401", name = "Aarav K", section = "III ECE-A", year = 3),
        StudentEntity(rollNumber = "225L1A0402", name = "Bhavana S", section = "III ECE-A", year = 3),
        StudentEntity(rollNumber = "225L1A0403", name = "Chiranth V", section = "III ECE-A", year = 3),
        StudentEntity(rollNumber = "225L1A0404", name = "Deepika N", section = "III ECE-A", year = 3),
        StudentEntity(rollNumber = "225L1A0405", name = "Eshwar T", section = "III ECE-A", year = 3),
        StudentEntity(rollNumber = "235L5A0406", name = "Farhan A", section = "II ECE-B", year = 2),
        StudentEntity(rollNumber = "235L5A0407", name = "Gayatri M", section = "II ECE-B", year = 2),
        StudentEntity(rollNumber = "235L5A0408", name = "Harsha P", section = "II ECE-B", year = 2),
        StudentEntity(rollNumber = "235L5A0409", name = "Indira R", section = "II ECE-B", year = 2),
        StudentEntity(rollNumber = "245L5A0410", name = "Jagan K", section = "IV ECE-B", year = 4),
        StudentEntity(rollNumber = "245L5A0411", name = "Keerthi D", section = "IV ECE-B", year = 4),
        StudentEntity(rollNumber = "245L5A0412", name = "Lokesh B", section = "IV ECE-B", year = 4),
    )

    val notes = listOf(
        NoteEntity(
            title = "DSP — Unit 3: FFT",
            body = "Covered DFT properties, radix-2 FFT derivation. Students found twiddle factor rotation confusing — use the circle diagram next time. Assignment 3 due Friday covers problems 3.4–3.9.",
            folder = "LECTURES",
            subject = "Digital Signal Processing",
        ),
        NoteEntity(
            title = "Communication Systems — Amplitude Modulation recap",
            body = "Revise: modulation index, sidebands, power calculations. Numerical on power distribution went well; theory answers were weak. Give a quiz next class.",
            folder = "LECTURES",
            subject = "Communication Systems",
        ),
        NoteEntity(
            title = "Faculty meeting — July minutes",
            body = "Discussed internal assessment schedule, lab manual updates and the upcoming project review. Action item: submit question bank for DSP by next Friday.",
            folder = "MEETINGS",
            subject = null,
        ),
        NoteEntity(
            title = "Antenna project idea",
            body = "III ECE students are currently working on antenna design — microstrip patch array at 2.4 GHz. Explore FR-4 vs Rogers substrate comparison for the mini project report.",
            folder = "RESEARCH",
            subject = "Digital Signal Processing",
        ),
        NoteEntity(
            title = "Lesson plan — FFT numericals",
            body = "Objectives: compute 8-point FFT manually, relate to DFT. Activity: pair work on twiddle factor tables. Outcome: students compute X(k) for N=8 without tables.",
            folder = "LESSON PLANS",
            subject = "Digital Signal Processing",
        ),
    )

    val tasks = listOf(
        TaskEntity(
            title = "Submit internal marks for DSP",
            dueAt = System.currentTimeMillis() + 1000L * 60 * 60 * 26,
            priority = "HIGH",
        ),
        TaskEntity(
            title = "Prepare FFT quiz questions",
            dueAt = System.currentTimeMillis() + 1000L * 60 * 60 * 52,
            priority = "MEDIUM",
        ),
        TaskEntity(
            title = "Review antenna design reports",
            dueAt = System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 4,
            priority = "MEDIUM",
        ),
        TaskEntity(
            title = "Upload lab manual revision",
            dueAt = null,
            priority = "LOW",
        ),
    )

    val memories = listOf(
        MemoryEntity(
            category = "TEACHING",
            text = "III ECE students are currently working on antenna design (microstrip patch array).",
            source = "Saved from a note",
        ),
    )

}

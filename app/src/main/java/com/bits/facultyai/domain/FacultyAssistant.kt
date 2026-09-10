package com.bits.facultyai.domain

import com.bits.facultyai.data.local.ClassSlotEntity
import com.bits.facultyai.data.local.FacultyProfileEntity
import com.bits.facultyai.data.local.MemoryEntity
import com.bits.facultyai.data.local.NoteEntity
import com.bits.facultyai.data.local.StudentEntity
import com.bits.facultyai.data.local.TaskEntity
import java.time.LocalDate
import java.time.ZoneId

/** Where an answer's information came from — shown to build trust. */
data class AnswerSource(val kind: String, val detail: String)

data class AssistantAnswer(
    val lines: List<String>,
    val sources: List<AnswerSource>,
)

/**
 * Deterministic, data-grounded assistant. Understands a set of natural
 * intents and answers ONLY from the faculty's own stored information.
 * If nothing is found it says so instead of inventing content.
 */
object FacultyAssistant {

    fun respond(
        query: String,
        profile: FacultyProfileEntity?,
        timetable: List<ClassSlotEntity>,
        tasks: List<TaskEntity>,
        notes: List<NoteEntity>,
        memories: List<MemoryEntity>,
        students: List<StudentEntity>,
        aiNotesAllowed: Boolean,
    ): AssistantAnswer {
        val q = query.trim().lowercase()
        val today = LocalDate.now()
        val targetDay = when {
            q.contains("tomorrow") -> today.plusDays(1)
            q.contains("yesterday") -> today.minusDays(1)
            else -> today
        }

        // --- Intent: timetable / next class / day schedule ---
        if (isTimetableQuery(q)) {
            val day = if (q.contains("tomorrow")) targetDay else today
            val dayOfWeek = day.dayOfWeek.value
            val slots = timetable.filter { it.dayOfWeek == dayOfWeek }.sortedBy { it.startTimeMinutes }
            val dayLabel = if (day == today) "today" else TimeUtils.dayName(dayOfWeek).lowercase()
            if (q.contains("next")) {
                val nowMin = TimeUtils.nowMinutes()
                val next = slots.firstOrNull { it.endTimeMinutes > nowMin }
                    ?: if (day == today) timetableForNextDayWithClasses(timetable, today) else null
                return if (next != null) {
                    AssistantAnswer(
                        lines = listOf(
                            "Your next class is ${next.subject}.",
                            "${TimeUtils.formatTime(next.startTimeMinutes)} – ${TimeUtils.formatTime(next.endTimeMinutes)}",
                            "${next.section} · Room ${next.room}",
                        ),
                        sources = listOf(AnswerSource("TIMETABLE", "${next.subject} — ${next.section}")),
                    )
                } else {
                    AssistantAnswer(
                        lines = listOf("You have no more classes scheduled for $dayLabel."),
                        sources = listOf(AnswerSource("TIMETABLE", "Weekly schedule")),
                    )
                }
            }
            return if (slots.isEmpty()) {
                AssistantAnswer(
                    lines = listOf("Nothing scheduled for $dayLabel."),
                    sources = listOf(AnswerSource("TIMETABLE", "Weekly schedule")),
                )
            } else {
                AssistantAnswer(
                    lines = listOf("You have ${slots.size} class${if (slots.size > 1) "es" else ""} $dayLabel:") +
                        slots.map {
                            "${TimeUtils.formatTime(it.startTimeMinutes)} — ${it.subject} (${it.section}, Room ${it.room})"
                        },
                    sources = listOf(AnswerSource("TIMETABLE", TimeUtils.dayName(dayOfWeek))),
                )
            }
        }

        // --- Intent: tasks / reminders ---
        if (isTaskQuery(q)) {
            val open = tasks.filter { !it.completed }.sortedBy { it.dueAt ?: Long.MAX_VALUE }
            if (open.isEmpty()) {
                return AssistantAnswer(
                    lines = listOf("You have no open tasks. Everything is done."),
                    sources = listOf(AnswerSource("TASKS", "Open tasks")),
                )
            }
            val soon = open.take(4).map { t ->
                val due = t.dueAt?.let {
                    val d = java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                    " — ${TimeUtils.relativeDayLabel(d)}"
                } ?: ""
                "${t.title}$due"
            }
            return AssistantAnswer(
                lines = listOf("You have ${open.size} open task${if (open.size > 1) "s" else ""}. Top of your list:") + soon,
                sources = listOf(AnswerSource("TASKS", "${open.size} open tasks")),
            )
        }

        // --- Intent: notes lookup ("find my notes about X") ---
        if (isNoteQuery(q)) {
            if (!aiNotesAllowed) {
                return AssistantAnswer(
                    lines = listOf("I don't have access to your notes. You can allow it in Settings → Privacy."),
                    sources = emptyList(),
                )
            }
            val topic = extractTopic(q)
            val matches = if (topic == null) notes.take(3) else notes.filter {
                it.title.lowercase().contains(topic) || it.body.lowercase().contains(topic)
            }
            return if (matches.isEmpty()) {
                AssistantAnswer(
                    lines = listOf(
                        if (topic == null) "You haven't saved any notes yet."
                        else "I couldn't find anything saved about \"$topic\".",
                    ),
                    sources = emptyList(),
                )
            } else {
                AssistantAnswer(
                    lines = listOf("Here's what I found in your notes:") +
                        matches.take(3).map { "• ${it.title} — ${it.body.take(90)}${if (it.body.length > 90) "…" else ""}" },
                    sources = matches.take(3).map { AnswerSource("MY NOTES", it.title) },
                )
            }
        }

        // --- Intent: memory recall ("what did I save about X") ---
        if (isMemoryQuery(q)) {
            val topic = extractTopic(q)
            val matches = if (topic == null) memories.take(3) else memories.filter { it.text.lowercase().contains(topic) }
            return if (matches.isEmpty()) {
                AssistantAnswer(
                    lines = listOf(
                        if (topic == null) "I don't have any saved memories yet."
                        else "I couldn't find anything saved about \"$topic\".",
                    ),
                    sources = emptyList(),
                )
            } else {
                AssistantAnswer(
                    lines = listOf("From what you've asked me to remember:") +
                        matches.take(3).map { "• ${it.text}" },
                    sources = matches.take(3).map { AnswerSource("MEMORY", it.category.lowercase()) },
                )
            }
        }

        // --- Intent: students / section lookup ---
        if (q.contains("student") || q.contains("section") || q.contains("roll")) {
            val secMatch = Regex("([ivx]+)\\s*ece\\s*-?\\s*([abcd])").find(q)
            val matches = if (secMatch != null) {
                val sec = secMatch.value.replace(" ", "").uppercase().let { s ->
                    students.filter { it.section.replace(" ", "").uppercase() == s }
                }
                sec
            } else students.take(3)
            return if (matches.isEmpty()) {
                AssistantAnswer(
                    lines = listOf("I couldn't find students matching that section."),
                    sources = listOf(AnswerSource("STUDENTS", "Roster")),
                )
            } else {
                AssistantAnswer(
                    lines = listOf("${matches.size} matching student${if (matches.size > 1) "s" else ""}:") +
                        matches.take(5).map { "• ${it.rollNumber} — ${it.name} (${it.section})" },
                    sources = listOf(AnswerSource("STUDENTS", "Roster")),
                )
            }
        }

        // --- Intent: lesson plan draft ---
        if (q.contains("lesson plan") || (q.contains("prepare") && q.contains("class"))) {
            val next = timetableForNextDayWithClasses(timetable, today)
            val subject = next?.subject ?: extractTopic(q) ?: "your subject"
            return AssistantAnswer(
                lines = listOf(
                    "DRAFT LESSON PLAN — ${subject.uppercase()}",
                    "1. Recap previous topic (5 min)",
                    "2. Introduce today's concept with a real-world example (15 min)",
                    "3. Worked example on the board (15 min)",
                    "4. Student activity / numerical practice (10 min)",
                    "5. Summary, outcomes check and preview of next class (5 min)",
                ),
                sources = if (next != null) listOf(AnswerSource("TIMETABLE", "Next class: ${next.subject}")) else emptyList(),
            )
        }

        // --- Intent: question generation ---
        if (q.contains("question")) {
            val subject = nextUpSubject(timetable) ?: extractTopic(q) ?: "the current topic"
            return AssistantAnswer(
                lines = listOf(
                    "DRAFT QUESTIONS — ${subject.uppercase()}",
                    "1. Define the key concept and state its physical significance.",
                    "2. Derive the governing expression and explain each term.",
                    "3. Compare two approaches and give one engineering application.",
                    "4. A short numerical problem at exam level.",
                    "5. One higher-order question linking this topic to a prior unit.",
                ),
                sources = emptyList(),
            )
        }

        // --- Fallback: honest, brief, useful ---
        return AssistantAnswer(
            lines = listOf(
                "I can help with your timetable, tasks, notes, students and saved memories. Try:",
                "• \"What is my next class?\"",
                "• \"What do I have tomorrow?\"",
                "• \"Find my notes about DSP\"",
                "• \"Create a lesson plan\"",
            ),
            sources = emptyList(),
        )
    }

    private fun isTimetableQuery(q: String) =
        q.contains("class") || q.contains("timetable") || q.contains("schedule") ||
            (q.contains("next") && !q.contains("task")) || q.contains("today") || q.contains("tomorrow")

    private fun isTaskQuery(q: String) =
        q.contains("task") || q.contains("reminder") || q.contains("due") || q.contains("todo")

    private fun isNoteQuery(q: String) =
        q.contains("note") || (q.contains("lecture") && q.contains("find"))

    private fun isMemoryQuery(q: String) =
        q.contains("remember") || q.contains("saved") || q.contains("memory") ||
            q.contains("what did i")

    /** Crude topic extraction after "about". */
    private fun extractTopic(q: String): String? {
        val marker = "about "
        val idx = q.indexOf(marker)
        return if (idx >= 0) {
            q.substring(idx + marker.length).trim().trimEnd('?', '.').take(40).ifEmpty { null }
        } else null
    }

    fun nextUpSubject(timetable: List<ClassSlotEntity>): String? {
        val nowMin = TimeUtils.nowMinutes()
        val today = TimeUtils.today().dayOfWeek.value
        val todaySlots = timetable.filter { it.dayOfWeek == today }.sortedBy { it.startTimeMinutes }
        return todaySlots.firstOrNull { it.endTimeMinutes > nowMin }?.subject
            ?: timetableForNextDayWithClasses(timetable, TimeUtils.today())?.subject
    }

    /** Finds the next day (from tomorrow) that has classes, returning its first slot. */
    fun timetableForNextDayWithClasses(timetable: List<ClassSlotEntity>, today: LocalDate): ClassSlotEntity? {
        for (offset in 1..7) {
            val day = today.plusDays(offset.toLong()).dayOfWeek.value
            val slot = timetable.filter { it.dayOfWeek == day }.minByOrNull { it.startTimeMinutes }
            if (slot != null) return slot
        }
        return null
    }
}


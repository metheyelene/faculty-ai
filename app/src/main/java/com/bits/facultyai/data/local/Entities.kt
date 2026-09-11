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
    val subjects: String = "",
    val photoUri: String = "",
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
    val year: Int = 1, // academic year (1..4) this class is taught to
)

/**
 * A versioned snapshot of the whole timetable, created whenever the user
 * confirms a new (e.g. photo-imported) timetable. History is never destroyed
 * by a replacement — only archived.
 */
@Entity(tableName = "timetable_version")
data class TimetableVersionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val versionNumber: Int,
    val createdAt: Long,
    val sourceLabel: String, // "Photo import" | "Manual" | ...
    val sourceImageUri: String = "",
    val slotCount: Int,
    val slotsJson: String, // serialized slots, for archival/review
)

/**
 * Academic calendar event — semester dates, exams, holidays, meetings, etc.
 */
@Entity(tableName = "academic_event", indices = [Index("date")])
data class AcademicEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val date: String, // ISO yyyy-MM-dd
    val endDate: String? = null, // inclusive; for multi-day events
    val category: String, // EXAM | HOLIDAY | MEETING | DEADLINE | ACADEMIC | EVENT
    val notes: String = "",
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
    val year: Int = 0, // academic year of the roster; 0 = legacy rows
    val markedAt: Long,
    val presentCount: Int,
    val absentCount: Int,
    val lateCount: Int,
    val excusedCount: Int = 0, // added in v8 for monthly attendance (E status)
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
    val status: String, // PRESENT | ABSENT | LATE | EXCUSED
)

/** Flat entry + its session's date/subject — the monthly day-by-day detail rows. Room projection POJO. */
data class StudentMonthEntry(
    val id: Long,
    val recordId: Long,
    val studentId: Long,
    val status: String,
    val recordDate: String,
    val recordSubject: String,
)

/**
 * Student roster, imported by the faculty (Excel/manual). Scope: this faculty
 * member's classes only. Organized by academic year + section.
 */
@Entity(
    tableName = "student",
    indices = [Index("section"), Index("rollNumber"), Index(value = ["year", "section"]), Index(value = ["registrationNumber"], unique = false)],
)
data class StudentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rollNumber: String,
    val name: String,
    val section: String,
    val year: Int,
    val registrationNumber: String = "",
    val email: String = "",
    val phone: String = "",
    val degree: String = "",
)

/**
 * A note in the faculty member's personal knowledge space.
 *
 * Sync bookkeeping: when all attachments are SYNCED the note flips to SYNCED;
 * any local change flips it back to PENDING_SYNC. Real content lives in the
 * local store first (offline-safe); sync is bookkeeping, not a dependency.
 */
@Entity(tableName = "note", indices = [Index("folder"), Index("updatedAt")])
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val folder: String, // LECTURES, MEETINGS, RESEARCH, PERSONAL, LESSON PLANS, IDEAS
    val subject: String?,
    val favorite: Boolean = false,
    val archived: Boolean = false,
    val updatedAt: Long = 0L,
    val createdAt: Long = 0L,
    /** PENDING_SYNC | SYNCED */
    val syncState: String = "PENDING_SYNC",
)

/**
 * Task / personal reminder with optional due date.
 */
@Entity(tableName = "task", indices = [Index("dueAt"), Index("completed")])
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val dueAt: Long?, // epoch millis, nullable = no due date
    val priority: String, // HIGH | MEDIUM | LOW
    val category: String = "GENERAL", // GENERAL | CLASS | MEETING | EXAM | ADMIN
    val recurrence: String = "NONE", // NONE | DAILY | WEEKLY
    val completed: Boolean = false,
    val createdAt: Long = 0L,
)

/**
 * A file attached to a note (PDF, DOCX, PPTX, TXT, images). The original file
 * is copied into app-private storage at attach time — the note remains fully
 * readable offline forever, and the SAF source URI is not relied upon.
 *
 * Upload lifecycle mirrors the UI states:
 *   UPLOADING -> PROCESSING -> SAVED -> (SYNCED | FAILED)
 * "SAVED" means: local copy + metadata row are durable (the note is safe);
 * "SYNCED" additionally means the cloud copy exists (when a backend is
 * configured); "FAILED" is retryable via [NoteAttachments.retryAttachment].
 */
@Entity(
    tableName = "note_attachment",
    indices = [Index("noteId"), Index(value = ["noteId", "state"])],
)
data class NoteAttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    /** Display name, user-renameable, keeps its extension. */
    val displayName: String,
    /** Lowercase extension without dot, e.g. "pdf", "pptx", "png". */
    val ext: String,
    val mimeType: String,
    val sizeBytes: Long,
    /** File name inside the app-private attachments dir (== attachments dir on disk). */
    val localFileName: String,
    /** URI the file came from; informational — never required after attach. */
    val sourceUri: String = "",
    /** UPLOADING | PROCESSING | SAVED | SYNCED | FAILED */
    val state: String = "UPLOADING",
    val errorMessage: String = "",
    val createdAt: Long = 0L,
)

/** Projection row: attachment counts per note (list badges without N+1 queries). */
data class AttachmentCountRow(
    val noteId: Long,
    val cnt: Int,
    val failed: Int,
    val inProgress: Int,
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

// =====================================================================
// Events + event finance. Local-first like everything else: records are
// usable offline immediately; syncState is cloud bookkeeping (PENDING_SYNC
// / SYNCED) for the future backend. Money is stored in paise (Long) so
// totals never drift through float arithmetic.
// =====================================================================

/** A faculty event — fest, workshop, seminar, meet. */
@Entity(tableName = "event", indices = [Index("date")])
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val date: String, // ISO yyyy-MM-dd — day-of-week is derived for display
    val startTimeMinutes: Int, // minutes from midnight (same convention as class_slot)
    val endTimeMinutes: Int,
    val venue: String = "",
    val description: String = "",
    val organizer: String = "",
    val department: String = "",
    /** Free-text participant groups/classes, e.g. "III ECE-A, III ECE-B". */
    val participants: String = "",
    val category: String = "OTHER", // FEST | WORKSHOP | SEMINAR | MEETING | CULTURAL | SPORTS | OTHER | custom
    val notes: String = "",
    /** Local file name of the cover photo inside the event_photos dir. */
    val coverPhotoFileName: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val syncState: String = "PENDING_SYNC",
)

/** Money in paise for an event — Room POJO for aggregate queries. */
data class EventMoneyRow(
    val eventId: Long,
    val totalPaisa: Long,
)

/** A photo attached to an event (gallery or camera capture). */
@Entity(tableName = "event_photo", indices = [Index("eventId"), Index(value = ["eventId", "state"])])
data class EventPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: Long,
    /** File name inside the event_photos dir. */
    val fileName: String,
    val caption: String = "",
    val isCover: Boolean = false,
    val mimeType: String = "image/jpeg",
    val sizeBytes: Long = 0L,
    /** UPLOADING | PROCESSING | UPLOADED | FAILED */
    val state: String = "UPLOADING",
    val errorMessage: String = "",
    val sourceUri: String = "",
    val createdAt: Long = 0L,
)

/** Money spent on an event. Receipt stays optional; removing it never deletes the record. */
@Entity(tableName = "event_expense", indices = [Index("eventId"), Index("date")])
data class EventExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: Long,
    val title: String,
    val category: String = "OTHER", // VENUE | FOOD | TRANSPORT | DECORATION | PRINTING | EQUIPMENT | REFRESHMENTS | CERTIFICATES | MARKETING | OTHER | custom
    val amountPaisa: Long,
    val date: String, // ISO yyyy-MM-dd
    val paidBy: String = "",
    val paymentMethod: String = "CASH", // CASH | UPI | CARD | BANK | CHEQUE | OTHER
    val vendor: String = "",
    val description: String = "",
    /** Local receipt file name (image or PDF) inside the event_photos dir. */
    val receiptFileName: String? = null,
    val receiptName: String = "",
    val notes: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val syncState: String = "PENDING_SYNC",
)

/** Money collected for an event (funds, registration fees, sponsorship). */
@Entity(tableName = "event_collection", indices = [Index("eventId"), Index("date")])
data class EventCollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: Long,
    val source: String, // person / fund / sponsor
    val amountPaisa: Long,
    val date: String, // ISO yyyy-MM-dd
    val paymentMethod: String = "CASH",
    val purpose: String = "",
    val reference: String = "",
    val notes: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val syncState: String = "PENDING_SYNC",
)

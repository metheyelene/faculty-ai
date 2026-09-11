package com.bits.facultyai.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FacultyProfileEntity::class,
        ClassSlotEntity::class,
        TimetableVersionEntity::class,
        AcademicEventEntity::class,
        AttendanceRecordEntity::class,
        AttendanceEntryEntity::class,
        StudentEntity::class,
        NoteEntity::class,
        NoteAttachmentEntity::class,
        TaskEntity::class,
        MemoryEntity::class,
        EventEntity::class,
        EventPhotoEntity::class,
        EventExpenseEntity::class,
        EventCollectionEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class FacultyDatabase : RoomDatabase() {
    abstract fun facultyDao(): FacultyDao

    companion object {
        @Volatile
        private var instance: FacultyDatabase? = null

        /** v1 -> v2: new tables (timetable_version, academic_event) + new columns. Local cache only. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `timetable_version` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `versionNumber` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `sourceLabel` TEXT NOT NULL, `sourceImageUri` TEXT NOT NULL, `slotCount` INTEGER NOT NULL, `slotsJson` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `academic_event` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `date` TEXT NOT NULL, `endDate` TEXT, `category` TEXT NOT NULL, `notes` TEXT NOT NULL)"
                )
                db.execSQL("ALTER TABLE `faculty_profile` ADD COLUMN `subjects` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `faculty_profile` ADD COLUMN `photoUri` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `note` ADD COLUMN `favorite` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `note` ADD COLUMN `archived` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `task` ADD COLUMN `description` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `task` ADD COLUMN `category` TEXT NOT NULL DEFAULT 'GENERAL'")
                db.execSQL("ALTER TABLE `task` ADD COLUMN `recurrence` TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_academic_event_date` ON `academic_event` (`date`)")
            }
        }

        /** v6 -> v7: events gain a participants field. Guarded, additive. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(
                    db,
                    "event",
                    "participants",
                    "ALTER TABLE `event` ADD COLUMN `participants` TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        /** v5 -> v6: events + event photos + event finance. All-new tables. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `event` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `date` TEXT NOT NULL, `startTimeMinutes` INTEGER NOT NULL, `endTimeMinutes` INTEGER NOT NULL, `venue` TEXT NOT NULL DEFAULT '', `description` TEXT NOT NULL DEFAULT '', `organizer` TEXT NOT NULL DEFAULT '', `department` TEXT NOT NULL DEFAULT '', `category` TEXT NOT NULL DEFAULT 'OTHER', `notes` TEXT NOT NULL DEFAULT '', `coverPhotoFileName` TEXT, `createdAt` INTEGER NOT NULL DEFAULT 0, `updatedAt` INTEGER NOT NULL DEFAULT 0, `syncState` TEXT NOT NULL DEFAULT 'PENDING_SYNC')"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_date` ON `event` (`date`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `event_photo` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eventId` INTEGER NOT NULL, `fileName` TEXT NOT NULL, `caption` TEXT NOT NULL DEFAULT '', `isCover` INTEGER NOT NULL DEFAULT 0, `mimeType` TEXT NOT NULL DEFAULT 'image/jpeg', `sizeBytes` INTEGER NOT NULL DEFAULT 0, `state` TEXT NOT NULL DEFAULT 'UPLOADING', `errorMessage` TEXT NOT NULL DEFAULT '', `sourceUri` TEXT NOT NULL DEFAULT '', `createdAt` INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_photo_eventId` ON `event_photo` (`eventId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_photo_eventId_state` ON `event_photo` (`eventId`, `state`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `event_expense` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eventId` INTEGER NOT NULL, `title` TEXT NOT NULL, `category` TEXT NOT NULL DEFAULT 'OTHER', `amountPaisa` INTEGER NOT NULL, `date` TEXT NOT NULL, `paidBy` TEXT NOT NULL DEFAULT '', `paymentMethod` TEXT NOT NULL DEFAULT 'CASH', `vendor` TEXT NOT NULL DEFAULT '', `description` TEXT NOT NULL DEFAULT '', `receiptFileName` TEXT, `receiptName` TEXT NOT NULL DEFAULT '', `notes` TEXT NOT NULL DEFAULT '', `createdAt` INTEGER NOT NULL DEFAULT 0, `updatedAt` INTEGER NOT NULL DEFAULT 0, `syncState` TEXT NOT NULL DEFAULT 'PENDING_SYNC')"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_expense_eventId` ON `event_expense` (`eventId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_expense_date` ON `event_expense` (`date`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `event_collection` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eventId` INTEGER NOT NULL, `source` TEXT NOT NULL, `amountPaisa` INTEGER NOT NULL, `date` TEXT NOT NULL, `paymentMethod` TEXT NOT NULL DEFAULT 'CASH', `purpose` TEXT NOT NULL DEFAULT '', `reference` TEXT NOT NULL DEFAULT '', `notes` TEXT NOT NULL DEFAULT '', `createdAt` INTEGER NOT NULL DEFAULT 0, `updatedAt` INTEGER NOT NULL DEFAULT 0, `syncState` TEXT NOT NULL DEFAULT 'PENDING_SYNC')"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_collection_eventId` ON `event_collection` (`eventId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_collection_date` ON `event_collection` (`date`)")
            }
        }

        /** v4 -> v5: note attachments + note sync bookkeeping. New table, additive columns. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `note_attachment` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `noteId` INTEGER NOT NULL, `displayName` TEXT NOT NULL, `ext` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `localFileName` TEXT NOT NULL, `sourceUri` TEXT NOT NULL DEFAULT '', `state` TEXT NOT NULL DEFAULT 'UPLOADING', `errorMessage` TEXT NOT NULL DEFAULT '', `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_attachment_noteId` ON `note_attachment` (`noteId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_attachment_noteId_state` ON `note_attachment` (`noteId`, `state`)")
                db.execSQL("ALTER TABLE `note` ADD COLUMN `syncState` TEXT NOT NULL DEFAULT 'PENDING_SYNC'")
            }
        }

        /** v3 -> v4: class slots carry their academic year so attendance rosters match exactly. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Guarded: some v3 installs already carry class_slot.year, and
                // re-adding an existing column is a hard SQLite error.
                addColumnIfMissing(
                    db,
                    "class_slot",
                    "year",
                    "ALTER TABLE `class_slot` ADD COLUMN `year` INTEGER NOT NULL DEFAULT 1",
                )
            }
        }

        /** v2 -> v3: student import support — new roster fields + attendance year scoping. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // The shipped v2 schema was NOT identical across installs:
                // early v2 builds (v1.1.0) already carried `student.degree`
                // (default "B.Tech ECE"), later v2 builds did not. SQLite has
                // no ADD COLUMN IF NOT EXISTS, so every ALTER is guarded —
                // re-adding an existing column crashes the app on launch.
                // Guarding (instead of dropping/re-adding) also preserves the
                // degree values early installs already hold.
                addColumnIfMissing(
                    db,
                    "student",
                    "registrationNumber",
                    "ALTER TABLE `student` ADD COLUMN `registrationNumber` TEXT NOT NULL DEFAULT ''",
                )
                addColumnIfMissing(
                    db,
                    "student",
                    "email",
                    "ALTER TABLE `student` ADD COLUMN `email` TEXT NOT NULL DEFAULT ''",
                )
                addColumnIfMissing(
                    db,
                    "student",
                    "phone",
                    "ALTER TABLE `student` ADD COLUMN `phone` TEXT NOT NULL DEFAULT ''",
                )
                addColumnIfMissing(
                    db,
                    "student",
                    "degree",
                    "ALTER TABLE `student` ADD COLUMN `degree` TEXT NOT NULL DEFAULT ''",
                )
                addColumnIfMissing(
                    db,
                    "attendance_record",
                    "year",
                    "ALTER TABLE `attendance_record` ADD COLUMN `year` INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_student_year_section` ON `student` (`year`, `section`)" )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_student_registrationNumber` ON `student` (`registrationNumber`)")
            }
        }

        /** True when [table] already has a column named [column]. */
        private fun hasColumn(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
            var exists = false
            db.query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == column) exists = true
                }
            }
            return exists
        }

        /**
         * ALTER TABLE ADD COLUMN that silently skips when the column already
         * exists — makes migrations safe to run against any real-world
         * variant of the source schema.
         */
        private fun addColumnIfMissing(
            db: SupportSQLiteDatabase,
            table: String,
            column: String,
            ddl: String,
        ) {
            if (!hasColumn(db, table, column)) db.execSQL(ddl)
        }

        fun get(context: Context): FacultyDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FacultyDatabase::class.java,
                    "faculty_ai.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .build()
                    .also { instance = it }
            }
    }
}

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
        TaskEntity::class,
        MemoryEntity::class,
    ],
    version = 4,
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

        /** v3 -> v4: class slots carry their academic year so attendance rosters match exactly. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `class_slot` ADD COLUMN `year` INTEGER NOT NULL DEFAULT 1")
            }
        }

        /** v2 -> v3: student import support — new roster fields + attendance year scoping. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `student` ADD COLUMN `registrationNumber` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `student` ADD COLUMN `email` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `student` ADD COLUMN `phone` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `student` ADD COLUMN `degree` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `attendance_record` ADD COLUMN `year` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_student_year_section` ON `student` (`year`, `section`)" )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_student_registrationNumber` ON `student` (`registrationNumber`)")
            }
        }

        fun get(context: Context): FacultyDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FacultyDatabase::class.java,
                    "faculty_ai.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
    }
}

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
    version = 2,
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

        fun get(context: Context): FacultyDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FacultyDatabase::class.java,
                    "faculty_ai.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}

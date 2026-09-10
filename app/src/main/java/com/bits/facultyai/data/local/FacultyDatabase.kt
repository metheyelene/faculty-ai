package com.bits.facultyai.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FacultyProfileEntity::class,
        ClassSlotEntity::class,
        AttendanceRecordEntity::class,
        AttendanceEntryEntity::class,
        StudentEntity::class,
        NoteEntity::class,
        TaskEntity::class,
        MemoryEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class FacultyDatabase : RoomDatabase() {
    abstract fun facultyDao(): FacultyDao

    companion object {
        @Volatile
        private var instance: FacultyDatabase? = null

        fun get(context: Context): FacultyDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FacultyDatabase::class.java,
                    "faculty_ai.db",
                ).build().also { instance = it }
            }
    }
}

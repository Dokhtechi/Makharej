package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.EntertainmentDao
import com.example.data.model.EntertainmentPackMetaEntity
import com.example.data.model.EntertainmentPuzzleEntity

@Database(
    entities = [
        EntertainmentPuzzleEntity::class,
        EntertainmentPackMetaEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class EntertainmentDatabase : RoomDatabase() {

    abstract fun entertainmentDao(): EntertainmentDao

    companion object {
        @Volatile
        private var INSTANCE: EntertainmentDatabase? = null

        fun getDatabase(context: Context): EntertainmentDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EntertainmentDatabase::class.java,
                    "entertainment_puzzles.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

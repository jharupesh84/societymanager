package com.society.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.society.app.data.model.CollectionEntity
import com.society.app.data.model.ExpenseEntity

@Database(
    entities = [CollectionEntity::class, ExpenseEntity::class],
    version = 3,
    exportSchema = false
)
abstract class SocietyDatabase : RoomDatabase() {

    abstract fun societyDao(): SocietyDao

    companion object {
        @Volatile
        private var INSTANCE: SocietyDatabase? = null

        fun getDatabase(context: Context): SocietyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SocietyDatabase::class.java,
                    "society_database.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

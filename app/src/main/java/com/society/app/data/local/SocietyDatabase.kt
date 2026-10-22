package com.society.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.society.app.data.model.CollectionEntity
import com.society.app.data.model.ExpenseEntity
import com.society.app.data.model.FlatOwnerEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [CollectionEntity::class, ExpenseEntity::class, FlatOwnerEntity::class],
    version = 4,
    exportSchema = false
)
abstract class SocietyDatabase : RoomDatabase() {

    abstract fun societyDao(): SocietyDao

    companion object {
        @Volatile
        private var INSTANCE: SocietyDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `flat_owners` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `block` TEXT NOT NULL,
                        `flatNo` TEXT NOT NULL,
                        `flatKey` TEXT NOT NULL,
                        `ownerName` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_flat_owners_block_flatNo` ON `flat_owners` (`block`, `flatNo`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_flat_owners_flatKey` ON `flat_owners` (`flatKey`)")
            }
        }

        fun getDatabase(context: Context): SocietyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SocietyDatabase::class.java,
                    "society_database.db"
                )
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                INSTANCE?.societyDao()?.insertFlatOwners(DefaultFlatOwners.LIST)
                            }
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                val dao = INSTANCE?.societyDao()
                                if (dao != null && dao.getFlatOwnerCount() == 0) {
                                    dao.insertFlatOwners(DefaultFlatOwners.LIST)
                                }
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

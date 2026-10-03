package com.pixel.intelligentsearch.core.data
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [HistoryEntity::class], version = 2, exportSchema = false)
abstract class IntelligentSearchDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var INSTANCE: IntelligentSearchDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_search_history_timestamp` ON `search_history` (`timestamp`)")
            }
        }

        fun getDatabase(context: Context): IntelligentSearchDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    IntelligentSearchDatabase::class.java,
                    "intelligent_search_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .addCallback(object : Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            try {
                                db.execSQL("PRAGMA synchronous = NORMAL")
                                db.execSQL("PRAGMA temp_store = MEMORY")
                                db.query("PRAGMA busy_timeout = 3000").close()
                            } catch (_: Throwable) {}
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

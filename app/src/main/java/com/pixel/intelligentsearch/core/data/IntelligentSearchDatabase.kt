package com.pixel.intelligentsearch.core.data

import android.content.Context
import android.os.Build
import android.os.UserManager
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [HistoryEntity::class], version = 2, exportSchema = false)
abstract class IntelligentSearchDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        private const val TAG = "IntelligentSearchDB"
        const val DATABASE_NAME = "intelligent_search_database"

        @Volatile
        private var INSTANCE: IntelligentSearchDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_search_history_timestamp` ON `search_history` (`timestamp`)")
            }
        }

        /**
         * Resolves the Credential Encrypted (CE) context for personal search history storage.
         * Search queries are strictly personal and must remain protected behind user authentication (PIN/Pattern/Biometrics).
         * If a legacy database exists in Device Protected (DE) storage from a previous version,
         * it is migrated to CE storage upon user unlock and deleted from DE storage.
         */
        fun getSafeContext(context: Context): Context {
            val appContext = context.applicationContext ?: context

            val ceContext = if (appContext.isDeviceProtectedStorage) {
                appContext
            } else {
                appContext
            }

            val userManager = ceContext.getSystemService(Context.USER_SERVICE) as? UserManager
            val isUnlocked = userManager?.isUserUnlocked ?: true

            if (isUnlocked && !ceContext.isDeviceProtectedStorage) {
                try {
                    val deContext = ceContext.createDeviceProtectedStorageContext()
                    val deDbFile = deContext.getDatabasePath(DATABASE_NAME)
                    val ceDbFile = ceContext.getDatabasePath(DATABASE_NAME)

                    if (deDbFile.exists() && !ceDbFile.exists()) {
                        val moved = ceContext.moveDatabaseFrom(deContext, DATABASE_NAME)
                        Log.i(TAG, "Search history database migrated from DE to CE storage: $moved")
                    } else if (deDbFile.exists() && ceDbFile.exists()) {
                        deContext.deleteDatabase(DATABASE_NAME)
                        Log.i(TAG, "Redundant pre-unlock DE search history database deleted")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed migrating database from DE to CE storage: ${e.message}")
                }
            }

            return ceContext
        }

        fun getDatabase(context: Context): IntelligentSearchDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(getSafeContext(context))
                    .also { INSTANCE = it }
            }
        }

        private fun buildDatabase(safeContext: Context): IntelligentSearchDatabase {
            return Room.databaseBuilder(
                safeContext,
                IntelligentSearchDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration(true)
                .fallbackToDestructiveMigrationOnDowngrade(true)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        try {
                            db.execSQL("PRAGMA synchronous = NORMAL")
                            db.execSQL("PRAGMA temp_store = MEMORY")
                            db.execSQL("PRAGMA busy_timeout = 3000")
                            db.execSQL("PRAGMA foreign_keys = ON")
                        } catch (t: Throwable) {
                            Log.w(TAG, "Database pragma initialization warning", t)
                        }
                    }
                })
                .build()
        }

        /**
         * Clears instance cache for test teardown or database reset.
         */
        @Synchronized
        fun resetInstance() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}

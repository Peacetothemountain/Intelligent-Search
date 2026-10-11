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
         * Resolves a Direct Boot safe context (Device Protected Storage) to ensure database
         * operations are safe even before user unlock. Automatically migrates any legacy CE database
         * to DE storage upon user unlock.
         */
        fun getSafeContext(context: Context): Context {
            val appContext = context.applicationContext ?: context

            val deContext = if (!appContext.isDeviceProtectedStorage) {
                appContext.createDeviceProtectedStorageContext()
            } else {
                appContext
            }

            val userManager = appContext.getSystemService(Context.USER_SERVICE) as? UserManager
            val isUnlocked = userManager?.isUserUnlocked ?: true

            // When user is unlocked, migrate any existing legacy database from Credential Encrypted
            // storage to Device Protected Storage.
            if (isUnlocked && !appContext.isDeviceProtectedStorage) {
                try {
                    val ceDbFile = appContext.getDatabasePath(DATABASE_NAME)
                    val deDbFile = deContext.getDatabasePath(DATABASE_NAME)
                    if (ceDbFile.exists() && !deDbFile.exists()) {
                        val moved = deContext.moveDatabaseFrom(appContext, DATABASE_NAME)
                        Log.i(TAG, "Legacy CE database migrated to Device Protected Storage: $moved")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed migrating CE database to DE storage: ${e.message}")
                }
            }

            return deContext
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

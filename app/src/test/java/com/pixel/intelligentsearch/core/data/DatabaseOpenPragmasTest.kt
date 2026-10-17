package com.pixel.intelligentsearch.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Opening the history database must apply every connection setting. On devices, execSQL throws for
 * busy_timeout because it answers with a row: 9.1.1 crashed on open this way, and later builds caught the
 * error but silently skipped the settings after it. Robolectric's SQLite doesn't throw there, so this
 * guards the settings themselves rather than reproducing the device-only failure.
 */
@RunWith(RobolectricTestRunner::class)
class DatabaseOpenPragmasTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = IntelligentSearchDatabase.create(context)

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(IntelligentSearchDatabase.DATABASE_NAME)
    }

    private fun pragma(name: String): Long =
        database.openHelper.writableDatabase.query("PRAGMA $name").use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }

    @Test
    fun openingTheDatabaseAppliesEverySetting() {
        assertEquals("busy_timeout", 3000L, pragma("busy_timeout"))
        assertEquals("foreign_keys", 1L, pragma("foreign_keys"))
        assertEquals("synchronous NORMAL", 1L, pragma("synchronous"))
        assertEquals("temp_store MEMORY", 2L, pragma("temp_store"))
    }
}

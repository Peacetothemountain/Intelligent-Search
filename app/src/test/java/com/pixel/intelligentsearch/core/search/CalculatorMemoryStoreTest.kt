package com.pixel.intelligentsearch.core.search

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Earlier results are still there after the app is closed, and clearing them clears what was kept. */
@RunWith(RobolectricTestRunner::class)
class CalculatorMemoryStoreTest {

    private val prefs get() = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(CalculatorMemory.FILE, Context.MODE_PRIVATE)

    @Before
    fun clean() {
        prefs.edit().clear().commit()
    }

    @Test
    fun resultsOutliveTheApp() {
        CalculatorMemory(prefs).apply {
            keep("55", 55.0)
            keep("1,234.5", 1234.5)
        }
        val reopened = CalculatorMemory(prefs)
        assertEquals(listOf("1,234.5", "55"), reopened.earlier.map { it.shown })
        assertEquals("(1234.5)/5", reopened.withAns("ans/5"))
    }

    @Test
    fun clearingForgetsWhatWasKept() {
        CalculatorMemory(prefs).apply {
            keep("55", 55.0)
            clear()
        }
        assertTrue(CalculatorMemory(prefs).earlier.isEmpty())
    }

    @Test
    fun aDamagedFileIsReadAsWhatItCanBe() {
        prefs.edit().putString("earlier", "55\t55.0\nbroken\n\tNaN\nx\tnot a number\n7\t7.0").commit()
        assertEquals(listOf("55", "7"), CalculatorMemory(prefs).earlier.map { it.shown })
    }
}

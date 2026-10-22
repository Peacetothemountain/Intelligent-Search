package com.pixel.intelligentsearch.core.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A structured value kept as one text setting: decoded once per change, written back only when it changes. */
@RunWith(RobolectricTestRunner::class)
class ParsedSettingTest {

    private val prefs get() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
    private var decodes = 0
    private val words = ParsedSetting(
        { prefs }, SettingsManager.SAVED_LINKS,
        decode = { text -> decodes++; text.removeSurrounding("[", "]").split(",").filter { it.isNotBlank() } },
        encode = { it.joinToString(",") }
    )

    @Before fun clear() {
        prefs.edit().clear().commit()
    }

    @Test
    fun aValueIsDecodedOncePerChange() {
        prefs.edit().put(SettingsManager.SAVED_LINKS, "a,b").commit()
        val first = words.current
        assertSame(first, words.current)
        assertEquals(1, decodes)
        prefs.edit().put(SettingsManager.SAVED_LINKS, "a,b,c").commit()
        assertEquals(listOf("a", "b", "c"), words.current)
        assertEquals(2, decodes)
    }

    @Test
    fun anUpdateIsWrittenOnlyWhenItChangesSomething() {
        assertEquals(listOf("x"), words.update { it + "x" })
        assertEquals("x", prefs.getString(SettingsManager.SAVED_LINKS.name, null))
        prefs.edit().putString(SettingsManager.SAVED_LINKS.name, "x").commit()
        words.update { it }
        assertEquals("x", prefs.getString(SettingsManager.SAVED_LINKS.name, null))
    }
}

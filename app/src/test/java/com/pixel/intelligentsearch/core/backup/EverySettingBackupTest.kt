package com.pixel.intelligentsearch.core.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.IntelligentSearchDatabase
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.BooleanSetting
import com.pixel.intelligentsearch.core.data.settings.IntSetting
import com.pixel.intelligentsearch.core.data.settings.SettingKey
import com.pixel.intelligentsearch.core.data.settings.StringSetSetting
import com.pixel.intelligentsearch.core.data.settings.StringSetting
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.security.StrongBoxSecurityManager
import com.pixel.intelligentsearch.core.system.WidgetUpdater
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Every declared setting through a real backup and restore, so a new setting is covered the day it joins
 * [SettingsManager.ALL] and needs no backup test of its own. It comes back as saved, unless it is one of the few a
 * restore holds back, and those are named here.
 */
@RunWith(RobolectricTestRunner::class)
class EverySettingBackupTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, IntelligentSearchDatabase::class.java).allowMainThreadQueries().build()
    private lateinit var settings: SettingsManager
    private lateinit var backup: BackupManager
    private val uri = Uri.fromFile(File(context.cacheDir, "every_setting.json"))

    /** A file may switch these off, never on: they share data, reach the network or need a permission. */
    private val onlyTheUserSwitchesOn = setOf<SettingKey<*>>(
        SettingsManager.APP_FUNCTIONS_SHARE_CONTACTS,
        SettingsManager.SEARCH_WEB,
        SettingsManager.SMART_CLIPBOARD_SUGGESTIONS,
        SettingsManager.SEARCH_WEB_SUGGESTIONS,
        SettingsManager.LIVE_CURRENCY_RATES,
        SettingsManager.AI_ANSWERS_ENABLED,
        SettingsManager.NOTIFICATION_SHORTCUT,
        SettingsManager.FLOATING_SEARCH_BUTTON
    )

    /**
     * These mean something only on this phone, so a restore keeps this phone's. Finish Setting Up's "I'm Done for Now"
     * is one: a new phone needs its own permissions and folders, so its checklist shows again.
     */
    private val staysOnThisPhone = setOf<SettingKey<*>>(
        SettingsManager.COUNTDOWN_EVENTS, SettingsManager.FILES_EXCLUDED_FOLDERS, SettingsManager.SETUP_CHECKLIST_DISMISSED
    )

    /**
     * The file's older settings snapshot also holds these, read through the setting's range when the file is made. The
     * bad value is rejected, then the snapshot restores the value it was clamped to.
     */
    private val clampedBySnapshot = setOf<SettingKey<*>>(
        SettingsManager.BACKGROUND_BLUR,
        SettingsManager.BACKGROUND_TRANSPARENCY,
        SettingsManager.PILL_OPACITY,
        SettingsManager.WEB_RESULTS_COUNT,
        SettingsManager.CONTACT_RESULTS_COUNT,
        SettingsManager.FILE_RESULTS_COUNT,
        SettingsManager.SHORTCUT_RESULTS_COUNT
    )

    /** A backup must not turn on developer behavior. */
    private val neverImported = SettingsManager.ALL.filter { it.name.startsWith("debug") }.toSet()

    @Before fun setUp() = runBlocking {
        settings = SettingsManager(context)
        backup = BackupManager(context, settings, db.historyDao(), StrongBoxSecurityManager(context), WidgetUpdater { })
    }

    @After fun tearDown() = db.close()

    private suspend fun reset() {
        settings.prefs.edit().clear().commit()
        settings.migrateToSingleStore()
    }

    /** A value for [key] the settings screens could store: two different ones, so each can overwrite the other. */
    private fun sample(key: SettingKey<*>, first: Boolean): Any = when (key) {
        is BooleanSetting -> first
        is IntSetting -> key.range?.let { if (first) it.last else it.first } ?: if (first) 7 else 9
        is StringSetSetting -> setOf(if (first) "com.example.first" else "com.example.second")
        is StringSetting -> when (key) {
            SettingsManager.CUSTOM_SEARCH_ENGINE_URL -> if (first) "https://first.example/search?q=%s" else "https://second.example/search?q=%s"
            SettingsManager.CUSTOM_BANGS_JSON -> if (first) "[]" else "[ ]"
            else -> if (first) "first" else "second"
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun store(first: Boolean) {
        val editor = settings.prefs.edit()
        for (key in SettingsManager.ALL) editor.put(key as SettingKey<Any>, sample(key, first))
        editor.commit()
    }

    private fun expected(key: SettingKey<*>, inFile: Any, onPhone: Any): Any = when {
        key in staysOnThisPhone || key in neverImported -> onPhone
        key in onlyTheUserSwitchesOn && inFile == true -> onPhone
        else -> inFile
    }

    private fun assertRestored(fileHoldsFirst: Boolean) = runBlocking {
        reset()
        store(first = fileHoldsFirst)
        val inTheFile = JSONObject(backup.createBackupPayload().sharedPreferencesJson)
        for (key in staysOnThisPhone) assertFalse("${key.name} is left out of the file", inTheFile.has(key.name))
        backup.exportToFile(uri, PASSPHRASE).getOrThrow()
        store(first = !fileHoldsFirst)
        backup.importFromFile(uri, PASSPHRASE).getOrThrow()

        val wrong = SettingsManager.ALL.mapNotNull { key ->
            val want = expected(key, sample(key, fileHoldsFirst), sample(key, !fileHoldsFirst))
            val got: Any = settings.prefs[key]
            "${key.name}: wanted $want, got $got".takeIf { got != want }
        }
        assertEquals(wrong.joinToString("\n"), 0, wrong.size)
    }

    /** The first sample has every switch on, so this is also the test that a file can't switch the opt-ins on. */
    @Test fun aFileWithEverySwitchOnRestores() = assertRestored(fileHoldsFirst = true)

    @Test fun aFileWithEverySwitchOffRestores() = assertRestored(fileHoldsFirst = false)

    /** An older file holds a setting under the name it had then: it comes back under today's, with the old one gone. */
    @Test
    fun anOldSpellingRestoresUnderTodaysName() = runBlocking {
        val spelled = SettingsManager.ALL.filter { it.legacyNames.isNotEmpty() && it !in neverImported && it !in staysOnThisPhone }
        assertTrue("found settings with old spellings", spelled.isNotEmpty())
        fun storeUnderOldNames(first: Boolean) {
            val editor = settings.prefs.edit()
            for (key in spelled) {
                val old = key.legacyNames.last()
                when (val value = sample(key, first)) {
                    is Boolean -> editor.putBoolean(old, value)
                    is Int -> editor.putInt(old, value)
                    is String -> editor.putString(old, value)
                    is Set<*> -> editor.putStringSet(old, value.map(Any?::toString).toSet())
                }
            }
            editor.commit()
        }
        for (fileHoldsFirst in listOf(true, false)) {
            reset()
            storeUnderOldNames(fileHoldsFirst)
            backup.exportToFile(uri, PASSPHRASE).getOrThrow()
            // This phone still holds the other value under the old name, as a phone not yet migrated would.
            reset()
            storeUnderOldNames(!fileHoldsFirst)
            backup.importFromFile(uri, PASSPHRASE).getOrThrow()

            val wrong = spelled.mapNotNull { key ->
                val inFile = sample(key, fileHoldsFirst)
                val want = expected(key, inFile, sample(key, !fileHoldsFirst))
                val got: Any = settings.prefs[key]
                // A value the restore held back stays where it is; one it restored replaces the old spelling.
                val leftover = if (want == inFile) key.legacyNames.filter(settings.prefs::contains) else emptyList()
                "${key.name}: wanted $want, got $got, old names left $leftover".takeIf { got != want || leftover.isNotEmpty() }
            }
            assertEquals(wrong.joinToString("\n"), 0, wrong.size)
        }
    }

    /** Values written straight to storage, as a crafted file would carry them; no slider can set them. */
    @Test
    fun aNumberOutOfRangeIsNeverRestored() = runBlocking {
        val ranged = SettingsManager.ALL.filterIsInstance<IntSetting>().filter { it.range != null }
        assertTrue("found ranged settings", ranged.isNotEmpty())
        for (outside in listOf<(IntRange) -> Int>({ it.first - 1 }, { it.last + 1 })) {
            reset()
            val editor = settings.prefs.edit()
            for (key in ranged) editor.putInt(key.name, outside(key.range!!))
            editor.commit()
            backup.exportToFile(uri, PASSPHRASE).getOrThrow()
            reset()
            backup.importFromFile(uri, PASSPHRASE).getOrThrow()
            for (key in ranged) {
                if (key in clampedBySnapshot) {
                    val raw = settings.prefs.getInt(key.name, key.default)
                    assertTrue("${key.name} holds $raw", raw in key.range!!)
                } else {
                    assertFalse("${key.name} was restored", settings.prefs.contains(key.name))
                }
            }
        }
    }

    /** A removed setting never comes back, even from a file an older build wrote. */
    @Test
    fun aRemovedSettingIsNeverRestored() = runBlocking {
        reset()
        val editor = settings.prefs.edit()
        for (name in SettingsManager.REMOVED_NAMES) {
            assertNull("$name is removed, so it isn't declared", SettingsManager.keyFor(name))
            editor.putBoolean(name, true)
        }
        editor.commit()
        backup.exportToFile(uri, PASSPHRASE).getOrThrow()
        reset()
        backup.importFromFile(uri, PASSPHRASE).getOrThrow()
        for (name in SettingsManager.REMOVED_NAMES) assertFalse(name, settings.prefs.contains(name))
    }

    private companion object {
        const val PASSPHRASE = "correct horse battery"
    }
}

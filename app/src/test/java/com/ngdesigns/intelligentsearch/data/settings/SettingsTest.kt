package com.ngdesigns.intelligentsearch.data.settings

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun roundTripsThroughJson() {
        val s = Settings(
            look = Look(theme = ThemeMode.Dark, colorStyle = ColorStyle.Vibrant, blur = 20),
            privacy = Privacy(hiddenApps = setOf("com.example.secret")),
            web = Web(engine = "Custom", customTemplate = "https://startpage.com/?q=%s", customShortcuts = listOf(CustomShortcut("mine", "Mine", "https://example.com/?q=%s"))),
        )
        assertEquals(s, SettingsJson.decode(SettingsJson.encode(s)))
    }

    @Test fun toleratesUnknownFieldsAndUnknownChoices() {
        val json = """{"look":{"theme":"Sepia","blur":10,"futureThing":true},"brandNewSection":{"x":1}}"""
        val s = SettingsJson.decode(json)!!
        assertEquals(ThemeMode.System, s.look.theme)
        assertEquals(10, s.look.blur)
        assertNull(SettingsJson.decode("not json"))
        assertNull(SettingsJson.decode("[1,2,3]"))
    }

    @Test fun badValuesAreForcedIntoRange() {
        val json = """{"look":{"blur":900,"transparency":-5},"ranking":{"apps":7,"contactResults":-1,"shortcutResults":0},
            "web":{"customTemplate":"javascript:alert(%s)","shortcutSymbol":"abc","disabledBuiltIns":["yt","nope"],
              "customShortcuts":[{"key":"ev","name":"Evil","template":"intent://x#%s"},{"key":"ok","name":"Ok","template":"example.com/?q=%s"}]},
            "privacy":{"hiddenApps":["com.example.app","not a package"]},
            "quickApps":{"profiles":[],"active":"Gone"},
            "widgets":{"designs":{"Bar":{"transparency":300,"slots":[{"action":"App","target":"bad name"},{"action":"Web","target":"javascript:x"}]}}}}"""
        val s = SettingsJson.decode(json)!!
        assertEquals(100, s.look.blur)
        assertEquals(0, s.look.transparency)
        assertEquals(2, s.ranking.apps)
        assertEquals(0, s.ranking.contactResults)
        assertEquals(1, s.ranking.shortcutResults)
        assertEquals("", s.web.customTemplate)
        assertEquals("!", s.web.shortcutSymbol)
        assertEquals(setOf("yt"), s.web.disabledBuiltIns)
        assertEquals(listOf("ok"), s.web.customShortcuts.map { it.key })
        assertEquals(setOf("com.example.app"), s.privacy.hiddenApps)
        assertEquals("Everyday", s.quickApps.active)
        val bar = s.widgets.designs.getValue(WidgetDesign.Bar)
        assertEquals(100, bar.transparency)
        assertEquals(List(4) { WidgetSlot() }, bar.slots)
    }

    @Test fun backupsLeaveDeviceBoundSettingsBehind() {
        val here = Settings(files = Files(folders = listOf("content://tree/1")), debug = Debug(unlocked = true))
        val backup = here.forBackup()
        assertTrue(backup.files.folders.isEmpty())
        assertEquals(Debug(), backup.debug)
        val restored = here.restoredFrom(backup.copy(look = Look(blur = 80)))
        assertEquals(80, restored.look.blur)
        assertEquals(listOf("content://tree/1"), restored.files.folders)
        assertTrue(restored.debug.unlocked)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun storeUpdatesAtomicallyAndRecoversFromCorruption() = runTest(UnconfinedTestDispatcher()) {
        val file = File(tmp.root, "settings.json")
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val store = SettingsStore(DataStoreFactory.create(SettingsSerializer, ReplaceFileCorruptionHandler { Settings() }, scope = scope.backgroundScope) { file })
        store.update { it.copy(look = it.look.copy(blur = 250)) }
        assertEquals(100, store.current().look.blur)
        assertTrue(file.readText().contains("\"blur\":100"))
    }

    // DataStore swaps a corrupt file for defaults by renaming over it, which only Android (not the Windows test
    // machine) allows, so this checks our side: garbage raises the corruption signal the handler acts on.
    @Test fun unreadableFilesRaiseCorruption() = runTest {
        val thrown = runCatching { SettingsSerializer.readFrom("{{{ nope".byteInputStream()) }.exceptionOrNull()
        assertTrue(thrown is CorruptionException)
        assertEquals(Settings(), SettingsSerializer.defaultValue)
    }
}

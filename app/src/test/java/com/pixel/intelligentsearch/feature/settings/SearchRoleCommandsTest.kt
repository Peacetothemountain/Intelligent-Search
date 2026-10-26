package com.pixel.intelligentsearch.feature.settings

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.system.SearchRoleManager
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.screenshots.confinedEffects
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.KeyStoreSpi
import java.security.Provider
import java.security.Security
import java.security.cert.Certificate
import java.util.Collections
import java.util.Date
import java.util.Enumeration

/** Both ADB commands are needed to list and select the app, so every "copy" copies both. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class SearchRoleCommandsTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    /** An empty Android Keystore, so the dialog's hardware checks open (and find nothing) off a device. */
    class EmptyKeyStore : KeyStoreSpi() {
        override fun engineGetKey(alias: String?, password: CharArray?): Key? = null
        override fun engineGetCertificateChain(alias: String?): Array<Certificate>? = null
        override fun engineGetCertificate(alias: String?): Certificate? = null
        override fun engineGetCreationDate(alias: String?): Date? = null
        override fun engineSetKeyEntry(alias: String?, key: Key?, password: CharArray?, chain: Array<out Certificate>?) = Unit
        override fun engineSetKeyEntry(alias: String?, key: ByteArray?, chain: Array<out Certificate>?) = Unit
        override fun engineSetCertificateEntry(alias: String?, cert: Certificate?) = Unit
        override fun engineDeleteEntry(alias: String?) = Unit
        override fun engineAliases(): Enumeration<String> = Collections.emptyEnumeration()
        override fun engineContainsAlias(alias: String?) = false
        override fun engineSize() = 0
        override fun engineIsKeyEntry(alias: String?) = false
        override fun engineIsCertificateEntry(alias: String?) = false
        override fun engineGetCertificateAlias(cert: Certificate?): String? = null
        override fun engineStore(stream: OutputStream?, password: CharArray?) = Unit
        override fun engineLoad(stream: InputStream?, password: CharArray?) = Unit
    }

    private val provider = object : Provider("AndroidKeyStore", 1.0, "Empty Android Keystore for tests") {
        init { put("KeyStore.AndroidKeyStore", EmptyKeyStore::class.java.name) }
    }

    @Before fun setUp() {
        hilt.inject()
        Security.addProvider(provider)
    }

    @After fun tearDown() {
        Security.removeProvider(provider.name)
    }

    @Test
    fun theSharedCommandsSwitchTheRoleOnListSelectGrantAndHoldIt() {
        assertEquals(
            listOf(
                "adb shell settings put secure search_engine_role_available 1",
                "adb shell settings put secure user_setup_search_engine com.pixel.intelligentsearch",
                "adb shell settings put secure selected_search_engine com.pixel.intelligentsearch",
                "adb shell pm grant com.pixel.intelligentsearch android.permission.WRITE_SECURE_SETTINGS",
                "adb shell cmd role add-role-holder android.app.role.SEARCH_ENGINE com.pixel.intelligentsearch"
            ),
            SearchRoleManager.ADB_ACTIVATION_COMMANDS.lines()
        )
    }

    @Test
    fun theDeveloperNoteShowsAndCopiesEveryCommand() {
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) { SearchRoleInfoDialog(LocalContext.current) {} }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Copy Command").performClick()
        compose.waitForIdle()

        val clipboard = ApplicationProvider.getApplicationContext<Context>().getSystemService(ClipboardManager::class.java)
        val copied = clipboard.primaryClip?.getItemAt(0)?.text.toString()
        assertEquals(SearchRoleManager.ADB_ACTIVATION_COMMANDS, copied)
        for (command in SearchRoleManager.ADB_ACTIVATION_COMMANDS.lines()) compose.onNodeWithText(command).assertExists()
    }
}

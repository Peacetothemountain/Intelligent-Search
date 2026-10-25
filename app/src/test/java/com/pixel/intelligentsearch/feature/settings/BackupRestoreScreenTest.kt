package com.pixel.intelligentsearch.feature.settings

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.view.View
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.backup.BackupManager
import com.pixel.intelligentsearch.core.backup.CipherMetadata
import com.pixel.intelligentsearch.core.backup.EncryptedBackupEnvelope
import com.pixel.intelligentsearch.core.backup.KdfMetadata
import com.pixel.intelligentsearch.core.bangs.SearchBangManager
import com.pixel.intelligentsearch.core.data.HistoryDao
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.haptics.PixelHapticEngine
import com.pixel.intelligentsearch.core.search.CorpusIndexManager
import com.pixel.intelligentsearch.core.security.BiometricSearchGate
import com.pixel.intelligentsearch.core.security.StrongBoxSecurityManager
import com.pixel.intelligentsearch.core.security.StrongBoxSecurityManager.PassphraseSave
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.screenshots.confinedEffects
import com.pixel.intelligentsearch.screenshots.disableHandRolledAnimations
import com.pixel.intelligentsearch.screenshots.settle
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.util.ReflectionHelpers

/**
 * The Encrypted Backup page: what it says about the saved passphrase, how restore gets its passphrase, and how
 * its cards are laid out. The security chip, the system prompt, backup files and haptics need a real phone, so
 * Robolectric shadows stand in for them (see the fakes below this class).
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(
    application = HiltTestApplication::class,
    qualifiers = "w412dp-h2600dp-xhdpi",
    instrumentedPackages = [
        "com.pixel.intelligentsearch.core.security",
        "com.pixel.intelligentsearch.core.backup",
        "com.pixel.intelligentsearch.core.haptics",
    ],
    shadows = [FakeSecurityChip::class, FakeVerifyPrompt::class, FakeBackupFiles::class, CountingHaptics::class]
)
class BackupRestoreScreenTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    @Inject lateinit var settingsManager: SettingsManager
    @Inject lateinit var historyDao: HistoryDao
    @Inject lateinit var bangManager: SearchBangManager
    @Inject lateinit var backupManager: BackupManager
    @Inject lateinit var strongBox: StrongBoxSecurityManager
    @Inject lateinit var corpusIndexManager: CorpusIndexManager

    private var backs = 0

    @Before fun setUp() {
        hilt.inject()
        disableHandRolledAnimations()
        FakeSecurityChip.reset()
        FakeVerifyPrompt.reset()
        FakeBackupFiles.reset()
        CountingHaptics.reset()
    }

    /** Opens the page; the file picker always returns one backup file, whose header [FakeBackupFiles] supplies. */
    private fun showPage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
        val viewModel = SettingsViewModel(settingsManager, historyDao, bangManager, backupManager, strongBox, corpusIndexManager)
        val picker = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(
                    requestCode: Int,
                    contract: ActivityResultContract<I, O>,
                    input: I,
                    options: ActivityOptionsCompat?
                ) {
                    dispatchResult(requestCode, PICKED_FILE)
                }
            }
        }
        // The page has never-ending animations, so the clock is driven by hand instead of waiting for idle.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(
                LocalSettingsViewModel provides viewModel,
                LocalActivityResultRegistryOwner provides picker
            ) {
                IntelligentSearchTheme(darkTheme = false) { BackupRestoreScreen(prefs) { backs++ } }
            }
        }
        compose.settle(steps = 5)
    }

    private fun typePassphrase(text: String) {
        compose.onNode(hasSetTextAction()).performTextInput(text)
        compose.settle(steps = 2)
    }

    /**
     * Moves the page on while a dialog may open. A dialog's window attaches on its first frame, which Robolectric
     * runs only once the looper's clock moves; [settle] checks for idle before moving it, and would wait forever
     * on a dialog composed in its last step. So here the Compose clock moves first and the looper follows.
     */
    private fun advance(steps: Int = 10) = repeat(steps) {
        // Background work (reading the file header) runs in real time.
        Thread.sleep(50)
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(100)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
    }

    @Test
    fun aCancelledPromptForAReSaveStopsSayingAPassphraseIsSaved() {
        FakeSecurityChip.saved = "an older passphrase"
        // Saving again needs the user verified, and the older passphrase didn't survive the attempt.
        FakeSecurityChip.onSave = {
            FakeSecurityChip.saved = null
            PassphraseSave.NEEDS_AUTHENTICATION
        }
        FakeVerifyPrompt.approve = false
        showPage()
        compose.onNodeWithText("Saved to Hardware Keystore · type to replace", useUnmergedTree = true).assertExists()

        typePassphrase("a newer passphrase")
        compose.onNodeWithText("Save to Hardware Keystore").performClick()
        compose.settle(steps = 5)
        assertEquals(1, FakeVerifyPrompt.prompts)

        compose.onNodeWithContentDescription("Clear").performClick()
        compose.settle(steps = 3)
        compose.onNodeWithText("Saved to Hardware Keystore · type to replace", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Enter passphrase (optional)", useUnmergedTree = true).assertExists()
    }

    @Test
    fun aFailedReSaveChecksAgainWhetherAPassphraseIsSaved() {
        FakeSecurityChip.saved = "an older passphrase"
        FakeSecurityChip.onSave = {
            FakeSecurityChip.saved = null
            PassphraseSave.FAILED
        }
        showPage()
        compose.onNodeWithText("Secured with", substring = true, useUnmergedTree = true).assertExists()

        typePassphrase("a newer passphrase")
        compose.onNodeWithText("Save to Hardware Keystore").performClick()
        compose.settle(steps = 5)

        compose.onNodeWithText("Secured with", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun thePageAsksToUnlockEvenWithNothingHiddenOrSaved() {
        // A fresh install: no hidden apps and no saved passphrase, but the backup still holds every setting.
        FakeVerifyPrompt.lockSet = true
        FakeVerifyPrompt.approve = true
        showPage()

        assertEquals(1, FakeVerifyPrompt.prompts)
        compose.onNodeWithText("Select Backup to Restore").assertExists()
    }

    @Test
    fun cancellingTheUnlockLeavesThePage() {
        FakeVerifyPrompt.lockSet = true
        FakeVerifyPrompt.approve = false
        showPage()
        advance()

        assertEquals(1, FakeVerifyPrompt.prompts)
        assertEquals(1, backs)
    }

    @Test
    fun aPassphraseShorterThanTheBackupMinimumCantBeSaved() {
        showPage()
        typePassphrase("short")
        compose.onNodeWithText("Save to Hardware Keystore").assertIsNotEnabled()

        typePassphrase(" but long enough")
        compose.onNodeWithText("Save to Hardware Keystore").assertIsEnabled()
    }

    @Test
    fun onlyABackupMadeWithAPassphraseAsksForOne() {
        // A file from before passphrase backups: typing a passphrase can't help, so asking again would loop.
        assertFalse(
            shouldAskForPassphrase(
                legacyBackup,
                "This backup was made by an older version of Intelligent Search and can't be restored. Make a new backup with a passphrase."
            )
        )
        assertTrue(shouldAskForPassphrase(passphraseBackup, "Incorrect passphrase. Authentication tag mismatch."))
        assertTrue(shouldAskForPassphrase(passphraseBackup, "Passphrase required to decrypt this backup"))
        assertFalse(shouldAskForPassphrase(passphraseBackup, "Could not read backup file"))
    }

    @Test
    fun restoreUsesThePassphraseSavedOnTheChipOnceTheUserIsVerified() {
        // Saved, but too long since the user last verified: the chip only hands it over after the prompt.
        FakeSecurityChip.saved = "correct horse battery"
        FakeVerifyPrompt.approve = true
        FakeBackupFiles.envelope = passphraseBackup
        showPage()

        compose.onNodeWithText("Select Backup to Restore").performClick()
        advance()

        assertEquals(1, FakeVerifyPrompt.prompts)
        assertEquals(listOf<String?>("correct horse battery"), FakeBackupFiles.restoredWith)
        compose.onNodeWithText("Enter Backup Passphrase").assertDoesNotExist()
    }

    @Test
    fun aRestoreInProgressCantBeStartedAgain() {
        FakeBackupFiles.envelope = passphraseBackup
        val finish = CompletableDeferred<Result<Int>>()
        FakeBackupFiles.finish = finish
        showPage()
        compose.onNodeWithText("Select Backup to Restore").performClick()
        advance()
        compose.onNodeWithText("Enter Backup Passphrase").assertExists()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput("correct horse battery")
        advance(steps = 2)

        // The dialog's Restore button, whatever it reads while the key is derived.
        val restoreButton = hasText("Restor", substring = true) and hasClickAction() and hasAnyAncestor(isDialog())
        compose.onNode(restoreButton).performClick()
        advance(steps = 2)
        compose.onNode(restoreButton).assertIsNotEnabled()
        compose.onNode(restoreButton).performClick()
        advance(steps = 2)
        assertEquals(1, FakeBackupFiles.restoredWith.size)
        compose.onAllNodesWithText("Restoring…").assertAll(isNotEnabled())

        finish.complete(Result.success(3))
        advance(steps = 3)
        compose.onNodeWithText("Enter Backup Passphrase").assertDoesNotExist()
        compose.onNodeWithText("Select Backup to Restore").assertIsEnabled()
    }

    @Test
    fun theBugdroidStandsOnItsShadow() {
        showPage()
        compose.onAllNodesWithTag("backup_bugdroid_shadow").assertCountEquals(1)
    }

    @Test
    fun onAndroid12WithoutTheBugdroidVideoNoShadowIsDrawn() {
        // Android 12 is reported by the SDK level rather than run in its own Robolectric runtime: a second
        // runtime doesn't fit the test JVM's heap next to Android 14's.
        val sdk = Build.VERSION.SDK_INT
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", Build.VERSION_CODES.S_V2)
        try {
            showPage()
            compose.onAllNodesWithTag("backup_bugdroid_shadow").assertCountEquals(0)
        } finally {
            ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", sdk)
        }
    }

    @Test
    fun theBackupCardsLineUp() {
        showPage()
        val bounds = listOf("backup_card_passphrase", "backup_card_export", "backup_card_restore", "backup_card_auto")
            .map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
        assertEquals(1, bounds.map { it.left }.distinct().size)
        assertEquals(1, bounds.map { it.right }.distinct().size)
    }

    @Test
    fun theBackArrowGivesTheBackHaptic() {
        showPage()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.settle(steps = 1)
        assertEquals(1, CountingHaptics.predictiveBackHaptics)
        assertEquals(1, backs)
    }

    /** How far the page can scroll, in pixels: 0 when everything fits on the screen. */
    private fun scrollRange(): Float =
        compose.onNode(hasScrollAction()).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue()

    /** The bugdroid's height in dp (the window is xhdpi), whether or not it is scrolled into view. */
    private fun bugdroidHeightDp(): Float = compose.onNodeWithTag("backup_bugdroid").fetchSemanticsNode().size.height / 2f

    /** The page doesn't scroll, and the bugdroid shows whole below the cards, legs included, at a size it can be made out. */
    private fun assertTheWholePageFits() {
        assertEquals(0f, scrollRange())
        val page = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val bugdroid = compose.onNodeWithTag("backup_bugdroid").fetchSemanticsNode().boundsInRoot
        val lastCard = compose.onNodeWithTag("backup_card_auto").fetchSemanticsNode().boundsInRoot
        // The video keeps the whole character (key, head, arms and legs) inside its view, so the view on screen is enough.
        assertTrue("bugdroid $bugdroid below the cards $lastCard", bugdroid.top >= lastCard.bottom)
        assertTrue("bugdroid $bugdroid on the page $page", bugdroid.bottom <= page.bottom)
        assertTrue("bugdroid ${bugdroidHeightDp()} dp", bugdroidHeightDp() >= BUGDROID_MIN_HEIGHT.value)
    }

    // Text has its real size only with native graphics; the default mode measures every line the same.
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun atTheDefaultSizesOfAPixelProXlThePageFitsWithoutScrolling() {
        showPage()
        assertTheWholePageFits()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "w448dp-h921dp-xhdpi")
    fun onAWiderPhoneThePageFitsWithoutScrolling() {
        // A 1344 px wide screen at 3x, less the status and navigation bars.
        showPage()
        assertTheWholePageFits()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "w412dp-h915dp-xhdpi", fontScale = 2f)
    fun withLargeTextThePageStillScrolls() {
        showPage()
        assertTrue(scrollRange() > 0f)
        // The bugdroid keeps its smallest size below the cards rather than being squeezed out.
        assertEquals(BUGDROID_MIN_HEIGHT.value, bugdroidHeightDp(), 0.5f)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "w412dp-h640dp-xhdpi")
    fun onAShortScreenThePageStillScrolls() {
        showPage()
        assertTrue(scrollRange() > 0f)
        assertEquals(BUGDROID_MIN_HEIGHT.value, bugdroidHeightDp(), 0.5f)
    }

    @Test
    fun withRoomToSpareTheBugdroidStopsAtItsFullSize() {
        showPage()
        assertEquals(BUGDROID_MAX_HEIGHT.value, bugdroidHeightDp(), 0.5f)
        assertEquals(0f, scrollRange())
    }

    private companion object {
        val PICKED_FILE: Uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3Abackup.json")

        val legacyBackup = EncryptedBackupEnvelope(
            cipher = CipherMetadata(ivBase64 = "AAAAAAAAAAAAAAAA"),
            encryptedPayloadBase64 = "AAAA",
            payloadSha256 = "0"
        )
        val passphraseBackup = legacyBackup.copy(kdf = KdfMetadata(iterations = 600_000, saltBase64 = "AAAAAAAAAAAAAAAAAAAAAA=="))
    }
}

/** The saved passphrase, readable only once [verified] (the Keystore's window after the user verifies). */
@Implements(StrongBoxSecurityManager::class)
class FakeSecurityChip {
    @Implementation fun hasSavedPassphrase(): Boolean = saved != null
    @Implementation fun getSavedPassphrase(): String? = saved.takeIf { verified }
    @Implementation fun savePassphrase(passphrase: String): PassphraseSave = onSave(passphrase)
    @Implementation fun clearSavedPassphrase(): Boolean {
        saved = null
        return true
    }

    companion object {
        var saved: String? = null
        var verified = false
        var onSave: (String) -> PassphraseSave = { saved = it; PassphraseSave.SAVED }

        fun reset() {
            saved = null
            verified = false
            onSave = { saved = it; PassphraseSave.SAVED }
        }
    }
}

/**
 * The system prompt: by default the phone has no screen lock, so opening the page needs none ([lockSet] gives it
 * one); when shown, the user [approve]s it or cancels.
 */
@Implements(BiometricSearchGate::class)
class FakeVerifyPrompt {
    @Implementation fun canPrompt(): Boolean = lockSet

    @Implementation
    fun authenticate(activity: Activity, title: String, subtitle: String?, onSuccess: () -> Unit, onFailure: () -> Unit) {
        prompts++
        if (approve) {
            FakeSecurityChip.verified = true
            onSuccess()
        } else {
            onFailure()
        }
    }

    companion object {
        var approve = false
        var prompts = 0
        var lockSet = false

        fun reset() {
            approve = false
            prompts = 0
            lockSet = false
        }
    }
}

/** A picked backup with header [envelope]; a restore records its passphrase and waits for [finish], if set. */
@Implements(BackupManager::class)
class FakeBackupFiles {
    @Implementation fun inspectBackupEnvelope(uri: Uri): Result<EncryptedBackupEnvelope> =
        envelope?.let { Result.success(it) } ?: Result.failure(IllegalArgumentException("Could not read backup file"))

    @Implementation suspend fun importFromFile(uri: Uri, passphrase: String?): Result<Int> {
        restoredWith += passphrase
        return finish?.await() ?: Result.success(1)
    }

    companion object {
        var envelope: EncryptedBackupEnvelope? = null
        var finish: CompletableDeferred<Result<Int>>? = null
        val restoredWith = mutableListOf<String?>()

        fun reset() {
            envelope = null
            finish = null
            restoredWith.clear()
        }
    }
}

/** Counts the haptic the app's back and dismiss buttons give. */
@Implements(PixelHapticEngine::class)
class CountingHaptics {
    @Implementation fun performPredictiveBackHaptic(view: View?) {
        predictiveBackHaptics++
    }

    companion object {
        var predictiveBackHaptics = 0

        fun reset() {
            predictiveBackHaptics = 0
        }
    }
}

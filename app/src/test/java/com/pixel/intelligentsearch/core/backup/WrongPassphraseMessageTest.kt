package com.pixel.intelligentsearch.core.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.IntelligentSearchDatabase
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.security.StrongBoxSecurityManager
import com.pixel.intelligentsearch.core.system.WidgetUpdater
import com.pixel.intelligentsearch.feature.settings.shouldAskForPassphrase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Restoring with the wrong passphrase says so in the user's words ("Incorrect passphrase. It isn't the one this
 * backup was made with."), not the cipher's ("Authentication tag mismatch."), and the page still asks for it again.
 */
@RunWith(RobolectricTestRunner::class)
class WrongPassphraseMessageTest {

    @Test
    fun aWrongPassphraseIsReportedPlainly() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, IntelligentSearchDatabase::class.java).allowMainThreadQueries().build()
        val backup = BackupManager(context, SettingsManager(context), db.historyDao(), StrongBoxSecurityManager(context), WidgetUpdater { })
        val uri = Uri.fromFile(File(context.cacheDir, "wrong_passphrase.json"))
        backup.exportToFile(uri, "correct horse battery").getOrThrow()

        val message = backup.importFromFile(uri, "wrong passphrase").exceptionOrNull()?.message.orEmpty()
        assertEquals("Incorrect passphrase. It isn't the one this backup was made with.", message)
        assertFalse(message, message.contains("tag", ignoreCase = true))
        assertTrue("the page asks for it again", shouldAskForPassphrase(backup.inspectBackupEnvelope(uri).getOrThrow(), message))

        db.close()
    }
}

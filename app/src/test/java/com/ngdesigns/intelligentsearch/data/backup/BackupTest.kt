package com.ngdesigns.intelligentsearch.data.backup

import com.ngdesigns.intelligentsearch.data.settings.Files
import com.ngdesigns.intelligentsearch.data.settings.Look
import com.ngdesigns.intelligentsearch.data.settings.Privacy
import com.ngdesigns.intelligentsearch.data.settings.Settings
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {
    private fun key(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val thisPhone = key()
    private val codec = BackupCodec({ thisPhone })
    private val settings = Settings(
        look = Look(blur = 30),
        privacy = Privacy(hiddenApps = setOf("com.example.secret")),
        files = Files(folders = listOf("content://tree/1")),
    )
    private val doc = BackupDocument.of(settings, listOf("weather", "timer 5"), createdAtMillis = 1L, appVersion = "2.0")

    private inline fun <reified E : BackupError> fails(block: () -> Unit) {
        val e = runCatching(block).exceptionOrNull()
        assertTrue("expected ${E::class.simpleName}, got $e", e is E)
    }

    @Test fun passphraseBackupsRestoreAnywhere() {
        val bytes = codec.seal(doc, "correct horse".toCharArray())
        assertEquals(true, codec.needsPassphrase(bytes))
        val otherPhone = BackupCodec({ key() })
        val opened = otherPhone.open(bytes, "correct horse".toCharArray())
        assertEquals(listOf("weather", "timer 5"), opened.history)
        val restored = opened.readSettings()!!
        assertEquals(30, restored.look.blur)
        assertEquals(setOf("com.example.secret"), restored.privacy.hiddenApps)
        assertTrue("folder grants stay on the phone", restored.files.folders.isEmpty())
    }

    @Test fun wrongPassphraseTamperingAndMissingPassphraseFailClearly() {
        val bytes = codec.seal(doc, "correct horse".toCharArray())
        fails<BackupError.WrongPassphraseOrDamaged> { codec.open(bytes, "wrong".toCharArray()) }
        fails<BackupError.NeedsPassphrase> { codec.open(bytes, null) }
        val tampered = bytes.copyOf().also { it[it.size - 5] = (it[it.size - 5] + 1).toByte() }
        fails<BackupError.WrongPassphraseOrDamaged> { codec.open(tampered, "correct horse".toCharArray()) }
    }

    @Test fun deviceBackupsOpenOnlyOnThisPhone() {
        val bytes = codec.seal(doc, null)
        assertEquals(false, codec.needsPassphrase(bytes))
        assertEquals(doc, codec.open(bytes, null))
        fails<BackupError.OtherDevice> { BackupCodec({ key() }).open(bytes, null) }
        fails<BackupError.OtherDevice> { BackupCodec({ null }).open(bytes, null) }
    }

    @Test fun otherFilesAreNotBackups() {
        assertNull(codec.needsPassphrase("hello".encodeToByteArray()))
        fails<BackupError.NotABackup> { codec.open(ByteArray(0), null) }
        fails<BackupError.NotABackup> { codec.open("ISB2".encodeToByteArray() + byteArrayOf(1, 2, 3), "x".toCharArray()) }
    }

    @Test fun historyIsCappedAndSealingIsRandomized() {
        val big = BackupDocument.of(Settings(), List(900) { "q$it" }, 1L, "2.0")
        assertEquals(BackupDocument.MAX_HISTORY, big.history.size)
        assertFalse(codec.seal(doc, "p".toCharArray()).contentEquals(codec.seal(doc, "p".toCharArray())))
    }
}

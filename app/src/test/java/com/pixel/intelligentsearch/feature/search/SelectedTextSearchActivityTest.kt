package com.pixel.intelligentsearch.feature.search

import android.app.Activity
import android.app.SearchManager
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.text.SpannableString
import android.text.style.StyleSpan
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.feature.widget.WidgetActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The text selection menu and share sheet entry: which intents reach it, how their text is cleaned, and that only
 * that text is passed on to the search overlay.
 */
@RunWith(RobolectricTestRunner::class)
class SelectedTextSearchActivityTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val entry = ComponentName(context, SelectedTextSearchActivity::class.java)

    private fun resolves(intent: Intent): Boolean =
        context.packageManager.queryIntentActivities(intent.setPackage(context.packageName), 0)
            .any { it.activityInfo.name == SelectedTextSearchActivity::class.java.name }

    /** Text from code points, so the source holds no invisible characters. */
    private fun text(vararg codePoints: Int) = String(codePoints, 0, codePoints.size)

    private fun processText(text: CharSequence?, type: String? = "text/plain") = Intent(Intent.ACTION_PROCESS_TEXT).apply {
        this.type = type
        if (text != null) putExtra(Intent.EXTRA_PROCESS_TEXT, text)
    }

    private fun share(text: CharSequence?, type: String? = "text/plain") = Intent(Intent.ACTION_SEND).apply {
        this.type = type
        if (text != null) putExtra(Intent.EXTRA_TEXT, text)
    }

    @Test
    fun manifestOffersPlainTextSelectionAndSharingOnly() {
        assertTrue(resolves(Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")))
        assertTrue(resolves(Intent(Intent.ACTION_SEND).setType("text/plain")))

        assertFalse(resolves(Intent(Intent.ACTION_PROCESS_TEXT).setType("text/html")))
        assertFalse(resolves(Intent(Intent.ACTION_SEND).setType("image/png")))
        assertFalse(resolves(Intent(Intent.ACTION_SEND).setType("text/html")))
        assertFalse(resolves(Intent(Intent.ACTION_SEND_MULTIPLE).setType("text/plain")))
        assertFalse(resolves(Intent(Intent.ACTION_VIEW).setType("text/plain")))
        assertFalse(resolves(Intent(Intent.ACTION_SEND)))

        val info = context.packageManager.getActivityInfo(entry, 0)
        assertTrue(info.exported)
        assertEquals("Intelligent Search", info.loadLabel(context.packageManager).toString())
        // The overlay itself stays private: this entry is the only way in for other apps' text.
        assertFalse(context.packageManager.getActivityInfo(ComponentName(context, WidgetActivity::class.java), 0).exported)
    }

    @Test
    fun sanitizeMakesOneCleanLineOfText() {
        assertEquals("hello world", SelectedTextSearchActivity.sanitize("  hello\n\n\tworld \r\n"))
        assertEquals("ab", SelectedTextSearchActivity.sanitize("a\u0000\u0007\u001B\u007F\u0085\u009Fb"))
        // Line and paragraph separators and no-break spaces are spacing too.
        assertEquals("a b", SelectedTextSearchActivity.sanitize("a" + text(0x2028, 0xA0) + "b"))
        // Bidirectional overrides could make the query read as something else; they are dropped.
        assertEquals("evil.txt", SelectedTextSearchActivity.sanitize("evil" + text(0x202E) + ".txt" + text(0x2066, 0x200F, 0xFEFF)))
        // Emoji sequences (joined with a zero-width joiner) survive.
        val coder = text(0x1F469, 0x200D, 0x1F4BB)
        assertEquals(coder, SelectedTextSearchActivity.sanitize(coder))
        assertEquals("", SelectedTextSearchActivity.sanitize(null))
        assertEquals("", SelectedTextSearchActivity.sanitize(" \n\t\u0000 "))
    }

    @Test
    fun sanitizeCapsTheLengthWithoutSplittingACharacter() {
        val long = "x".repeat(600)
        assertEquals(SelectedTextSearchActivity.MAX_QUERY_LENGTH, SelectedTextSearchActivity.sanitize(long).length)
        assertEquals("x".repeat(512), SelectedTextSearchActivity.sanitize("  \n" + long))

        // An emoji straddling the cap is left out rather than cut in half.
        val straddling = "x".repeat(511) + text(0x1F600) + "tail"
        val cut = SelectedTextSearchActivity.sanitize(straddling)
        assertEquals("x".repeat(511), cut)
        assertFalse(Character.isHighSurrogate(cut.last()))

        // A space falling on the cap is trimmed, not kept as the last character.
        assertEquals("x".repeat(511), SelectedTextSearchActivity.sanitize("x".repeat(511) + "\n\nmore"))
    }

    @Test
    fun sanitizeDropsStylingSpans() {
        val styled = SpannableString("bold text").apply { setSpan(StyleSpan(1), 0, 4, 0) }
        val query = SelectedTextSearchActivity.sanitize(styled)
        assertEquals("bold text", query)
        assertEquals(String::class.java, query.javaClass)
    }

    @Test
    fun readsOnlyTheActionsOwnPlainTextExtra() {
        assertEquals("selected", SelectedTextSearchActivity.queryFrom(processText("selected")))
        assertEquals("shared", SelectedTextSearchActivity.queryFrom(share("shared")))
        assertEquals("charset", SelectedTextSearchActivity.queryFrom(share("charset", type = "text/plain; charset=utf-8")))

        // Each action reads its own extra only.
        assertEquals("", SelectedTextSearchActivity.queryFrom(processText(null).putExtra(Intent.EXTRA_TEXT, "not this")))
        assertEquals("", SelectedTextSearchActivity.queryFrom(share(null).putExtra(Intent.EXTRA_PROCESS_TEXT, "not this")))

        // Missing or non-text extras read as empty.
        assertEquals("", SelectedTextSearchActivity.queryFrom(processText(null)))
        assertEquals("", SelectedTextSearchActivity.queryFrom(processText(null).putExtra(Intent.EXTRA_PROCESS_TEXT, 42)))
        assertEquals("", SelectedTextSearchActivity.queryFrom(share(null).putExtra(Intent.EXTRA_TEXT, Uri.parse("https://example.com"))))
        assertEquals("", SelectedTextSearchActivity.queryFrom(share(null).putExtra(Intent.EXTRA_TEXT, arrayOf("a", "b"))))

        // Anything that is not a plain-text selection or share is refused.
        assertNull(SelectedTextSearchActivity.queryFrom(processText("x", type = "text/html")))
        assertNull(SelectedTextSearchActivity.queryFrom(share("x", type = "image/png")))
        assertNull(SelectedTextSearchActivity.queryFrom(share("x", type = null)))
        assertNull(SelectedTextSearchActivity.queryFrom(Intent(Intent.ACTION_VIEW, Uri.parse("tel:123")).putExtra(Intent.EXTRA_TEXT, "x")))
        assertNull(SelectedTextSearchActivity.queryFrom(Intent(Intent.ACTION_SEND_MULTIPLE).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "x")))
        assertNull(SelectedTextSearchActivity.queryFrom(null))
    }

    @Test
    fun selectedTextOpensTheOverlayWithOnlyTheCleanedQuery() {
        val intent = processText("  https://example.com\n call 555 0100 ").apply {
            putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
            putExtra(Intent.EXTRA_STREAM, Uri.parse("content://evil/file"))
            putExtra("launch_gemini_overlay", true)
            putExtra("auto_voice", true)
            clipData = ClipData.newRawUri("x", Uri.parse("content://evil/file"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val activity = Robolectric.buildActivity(SelectedTextSearchActivity::class.java, intent).create().get()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(ComponentName(context, WidgetActivity::class.java), started.component)
        assertEquals(setOf(SearchManager.QUERY), started.extras!!.keySet())
        assertEquals("https://example.com call 555 0100", started.getStringExtra(SearchManager.QUERY))
        assertNull(started.action)
        assertNull(started.data)
        assertNull(started.clipData)
        assertEquals(0, started.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)

        // Nothing else is opened, no replacement text goes back to the calling app, and the entry closes at once.
        assertNull(shadowOf(activity).nextStartedActivity)
        assertTrue(activity.isFinishing)
        assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
        assertNull(shadowOf(activity).resultIntent)
    }

    @Test
    fun sharedTextOpensTheOverlayWithTheQuery() {
        val activity = Robolectric.buildActivity(SelectedTextSearchActivity::class.java, share("pasta\trecipes")).create().get()
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(ComponentName(context, WidgetActivity::class.java), started.component)
        assertEquals("pasta recipes", started.getStringExtra(SearchManager.QUERY))
        assertTrue(activity.isFinishing)
    }

    @Test
    fun emptyTextOpensTheEmptyOverlay() {
        val activity = Robolectric.buildActivity(SelectedTextSearchActivity::class.java, processText(" \n ")).create().get()
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(ComponentName(context, WidgetActivity::class.java), started.component)
        assertFalse(started.hasExtra(SearchManager.QUERY))
        assertTrue(activity.isFinishing)
    }

    @Test
    fun otherIntentsOpenNothing() {
        for (intent in listOf(share("x", type = "image/png"), Intent(Intent.ACTION_MAIN), Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")))) {
            val activity = Robolectric.buildActivity(SelectedTextSearchActivity::class.java, intent).create().get()
            assertNull(shadowOf(activity).nextStartedActivity)
            assertTrue(activity.isFinishing)
        }
    }

    @Test
    fun theSettingShowsAndHidesTheEntry() {
        val pm = context.packageManager
        SelectedTextSearchActivity.setEnabled(context, false)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, pm.getComponentEnabledSetting(entry))
        assertFalse(resolves(Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")))

        SelectedTextSearchActivity.setEnabled(context, true)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, pm.getComponentEnabledSetting(entry))
        assertTrue(resolves(Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")))
    }
}

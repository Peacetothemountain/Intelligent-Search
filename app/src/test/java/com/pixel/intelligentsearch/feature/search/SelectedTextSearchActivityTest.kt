package com.pixel.intelligentsearch.feature.search

import android.app.Activity
import android.app.SearchManager
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.text.SpannableString
import android.text.style.StyleSpan
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
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
import org.robolectric.shadows.ShadowToast

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

    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    private fun start(intent: Intent) = Robolectric.buildActivity(SelectedTextSearchActivity::class.java, intent).create().get()

    /** Installs a Google app that takes GLOBAL_SEARCH, which only types the query in. */
    private fun installGoogleSearch() {
        val google = ComponentName("com.google.android.googlequicksearchbox", "com.google.android.googlequicksearchbox.SearchActivity")
        val pm = shadowOf(context.packageManager)
        pm.addActivityIfNotPresent(google)
        pm.addIntentFilterForActivity(google, IntentFilter("android.search.action.GLOBAL_SEARCH").apply { addCategory(Intent.CATEGORY_DEFAULT) })
    }

    @Test
    fun withTheOverlayOffSelectedTextIsOnlyTypedIntoGoogleSearch() {
        prefs.edit().put(SettingsManager.SEARCH_OVERLAY_ENABLED, false).commit()
        installGoogleSearch()

        val activity = start(processText("Eiffel\nTower"))
        val started = shadowOf(activity).nextStartedActivity
        assertEquals("android.search.action.GLOBAL_SEARCH", started.action)
        assertEquals("com.google.android.googlequicksearchbox", started.`package`)
        assertEquals("Eiffel Tower", started.getStringExtra(SearchManager.QUERY))
        assertNull(shadowOf(activity).nextStartedActivity)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun withTheOverlayOffAndNoGoogleAppSelectedTextIsNeverSearched() {
        prefs.edit().put(SettingsManager.SEARCH_OVERLAY_ENABLED, false).commit()
        // A web search is available (the overlay's own fallback), but it would run the search straight away.
        assertTrue(context.packageManager.resolveActivity(Intent(Intent.ACTION_WEB_SEARCH), 0) != null)

        for (intent in listOf(processText("private note"), share("private note"))) {
            val activity = start(intent)
            assertNull(shadowOf(activity).nextStartedActivity)
            assertTrue(activity.isFinishing)
            assertEquals(SelectedTextSearchActivity.OVERLAY_OFF_MESSAGE, ShadowToast.getTextOfLatestToast())
        }
    }

    @Test
    fun sharingAFileOpensNothingAndSaysOnlyTextCanBeSearched() {
        val file = Uri.parse("content://files/notes.txt")
        for (type in listOf("text/plain", "text/*")) {
            ShadowToast.reset()
            val activity = start(share(null, type = type).putExtra(Intent.EXTRA_STREAM, file))
            assertNull(shadowOf(activity).nextStartedActivity)
            assertTrue(activity.isFinishing)
            assertEquals(SelectedTextSearchActivity.NO_TEXT_MESSAGE, ShadowToast.getTextOfLatestToast())
        }

        // A share whose text is only spacing has nothing to search either.
        ShadowToast.reset()
        assertNull(shadowOf(start(share(" \n "))).nextStartedActivity)
        assertEquals(SelectedTextSearchActivity.NO_TEXT_MESSAGE, ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun wildcardTextSharesAreSearched() {
        assertEquals("shared", SelectedTextSearchActivity.queryFrom(share("shared", type = "text/*")))
        assertEquals("selected", SelectedTextSearchActivity.queryFrom(processText("selected", type = "TEXT/*")))
        assertEquals("any", SelectedTextSearchActivity.queryFrom(share("any", type = "*/*")))
        // The text extra is still read only as text: a link or a stream is not.
        assertEquals("", SelectedTextSearchActivity.queryFrom(share(null, type = "text/*").putExtra(Intent.EXTRA_TEXT, Uri.parse("content://x"))))
        assertEquals("", SelectedTextSearchActivity.queryFrom(share(null, type = "text/*").putExtra(Intent.EXTRA_STREAM, Uri.parse("content://x"))))
        assertNull(SelectedTextSearchActivity.queryFrom(share("x", type = "image/*")))

        assertTrue(resolves(Intent(Intent.ACTION_SEND).setType("text/*")))
        val started = shadowOf(start(share("pasta", type = "text/*"))).nextStartedActivity
        assertEquals(ComponentName(context, WidgetActivity::class.java), started.component)
        assertEquals("pasta", started.getStringExtra(SearchManager.QUERY))
    }

    @Test
    fun sanitizeDropsInvisibleFormattingAndBrokenSurrogates() {
        // Zero-width space, word joiner, soft hyphen and an invisible tag letter hide nothing in the query.
        assertEquals("abcde", SelectedTextSearchActivity.sanitize("a" + text(0x200B) + "b" + text(0x2060) + "c" + text(0xAD) + "d" + text(0xE0041, 0xE007F) + "e"))
        // Halves of a surrogate pair on their own are dropped; whole pairs are kept.
        assertEquals("abc", SelectedTextSearchActivity.sanitize("a\uD83Db\uDE00c"))
        assertEquals("x", SelectedTextSearchActivity.sanitize("x\uD83D"))
        assertEquals(text(0x1F600), SelectedTextSearchActivity.sanitize(text(0x1F600)))
        // Joiners and variation selectors shape emoji and words, so they stay.
        val heart = text(0x2764, 0xFE0F)
        assertEquals(heart, SelectedTextSearchActivity.sanitize(heart))
        val family = text(0x1F468, 0x200D, 0x1F469, 0x200D, 0x1F467)
        assertEquals(family, SelectedTextSearchActivity.sanitize(family))
        val persian = text(0x0645, 0x06CC, 0x200C, 0x062E, 0x0648, 0x0627, 0x0645)
        assertEquals(persian, SelectedTextSearchActivity.sanitize(persian))
        // Invisible characters between words don't leave a double space.
        assertEquals("a b", SelectedTextSearchActivity.sanitize("a " + text(0x200B) + " b"))
    }

    @Test
    fun joinersWithNothingToJoinAreTrimmed() {
        val zwnj = 0x200C
        val zwj = 0x200D
        // Only joiners and spacing is no text at all.
        assertEquals("", SelectedTextSearchActivity.sanitize(" " + text(zwnj) + " "))
        assertEquals("", SelectedTextSearchActivity.sanitize(text(zwj, zwnj, 0x20, zwj)))
        assertEquals("", SelectedTextSearchActivity.sanitize(text(0x200B, zwj) + "\n"))
        // Joiners at either end are trimmed; inside the text they stay.
        assertEquals("ab", SelectedTextSearchActivity.sanitize(text(zwj, zwnj) + "ab" + text(zwnj, zwj)))
        assertEquals("a b", SelectedTextSearchActivity.sanitize(" " + text(zwnj) + " a b " + text(zwj) + " "))
        val family = text(0x1F468, zwj, 0x1F469, zwj, 0x1F467)
        assertEquals(family, SelectedTextSearchActivity.sanitize(text(zwj) + family + text(zwj)))
        val persian = text(0x0645, 0x06CC, zwnj, 0x062E, 0x0648, 0x0627, 0x0645)
        assertEquals(persian, SelectedTextSearchActivity.sanitize(text(zwnj) + persian + " " + text(zwnj)))
    }

    @Test
    fun aShareOfOnlyInvisibleJoinersSaysOnlyTextCanBeSearched() {
        ShadowToast.reset()
        val shared = start(share(" " + text(0x200C) + " "))
        assertNull(shadowOf(shared).nextStartedActivity)
        assertEquals(SelectedTextSearchActivity.NO_TEXT_MESSAGE, ShadowToast.getTextOfLatestToast())

        // A selection of only a joiner opens the overlay empty rather than with an invisible query.
        val selected = shadowOf(start(processText(text(0x200D)))).nextStartedActivity
        assertEquals(ComponentName(context, WidgetActivity::class.java), selected.component)
        assertFalse(selected.hasExtra(SearchManager.QUERY))
    }

    @Test
    fun syncFollowsTheSetting() {
        val pm = context.packageManager
        prefs.edit().put(SettingsManager.SEARCH_FROM_SELECTED_TEXT, false).commit()
        SelectedTextSearchActivity.syncWithSetting(context)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, pm.getComponentEnabledSetting(entry))

        prefs.edit().put(SettingsManager.SEARCH_FROM_SELECTED_TEXT, true).commit()
        SelectedTextSearchActivity.syncWithSetting(context)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, pm.getComponentEnabledSetting(entry))
    }

    @Test
    fun aSlowSyncAppliesTheSettingAsItIsWhenItRuns() {
        prefs.edit().put(SettingsManager.SEARCH_FROM_SELECTED_TEXT, true).commit()
        SelectedTextSearchActivity.setEnabled(context, true)

        // The start-up sync starts while the switch is being turned off, and waits for it.
        val worker: Thread
        synchronized(SelectedTextSearchActivity.componentLock) {
            worker = Thread { SelectedTextSearchActivity.syncWithSetting(context) }
            worker.start()
            val deadline = System.currentTimeMillis() + 5_000
            while (worker.state != Thread.State.BLOCKED && System.currentTimeMillis() < deadline) Thread.yield()
            assertEquals(Thread.State.BLOCKED, worker.state)
            prefs.edit().put(SettingsManager.SEARCH_FROM_SELECTED_TEXT, false).commit()
            SelectedTextSearchActivity.setEnabled(context, false)
        }
        worker.join(5_000)

        // It applies the new value, not the one it would have read on starting.
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, context.packageManager.getComponentEnabledSetting(entry))
    }
}

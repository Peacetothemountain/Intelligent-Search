package com.pixel.intelligentsearch.core.bangs

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The AI assistants' built-in web shortcuts open each assistant with the query: in the browser, or in the
 * assistant's app when it is installed and takes its address (Gemini's app takes the query shared to it).
 */
@RunWith(RobolectricTestRunner::class)
class AiAssistantBangsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val bangs = SearchBangManager(context, SettingsManager(context))

    private fun open(query: String): Intent = bangs.dispatchBangSearch(bangs.parseBangQuery(query)!!)

    @Test
    fun eachAssistantsShortcutOpensItsSiteWithTheQuery() {
        val expected = mapOf(
            "!gpt what is rust" to "https://chatgpt.com/?q=what%20is%20rust",
            "!pplx what is rust" to "https://www.perplexity.ai/search?q=what%20is%20rust",
            "!gemini what is rust" to "https://gemini.google.com/app?q=what%20is%20rust",
            "!claude what is rust" to "https://claude.ai/new?q=what%20is%20rust",
            "!copilot what is rust" to "https://copilot.microsoft.com/?q=what%20is%20rust",
            // At the end, as every shortcut can be.
            "what is rust !claude" to "https://claude.ai/new?q=what%20is%20rust"
        )
        for ((query, url) in expected) {
            val intent = open(query)
            assertEquals(query, Intent.ACTION_VIEW, intent.action)
            assertEquals(query, url, intent.dataString)
            assertEquals(query, null, intent.`package`)
        }
    }

    @Test
    fun theExistingShortcutsStillWinTheirOwnTriggers() {
        assertEquals("!g", bangs.parseBangQuery("!g maps")!!.bang.prefix)
        assertEquals("!gpt", bangs.parseBangQuery("!gpt maps")!!.bang.prefix)
        assertEquals("!gh", bangs.parseBangQuery("!gh maps")!!.bang.prefix)
        assertEquals("!gemini", bangs.parseBangQuery("!gemini maps")!!.bang.prefix)
    }

    @Test
    fun anotherTriggerSymbolStillOpensTheAssistant() {
        SettingsManager(context).prefs.edit().put(SettingsManager.WEB_SHORTCUT_TRIGGER_SYMBOL, "/").commit()
        val manager = SearchBangManager(context, SettingsManager(context))
        val intent = manager.dispatchBangSearch(manager.parseBangQuery("/claude hello")!!)
        assertEquals("https://claude.ai/new?q=hello", intent.dataString)
    }

    @Test
    fun anInstalledAppThatTakesTheAddressOpensIt() {
        installActivity("com.openai.chatgpt", Intent.ACTION_VIEW, "https", "chatgpt.com")
        val intent = open("!gpt what is rust")
        assertEquals("com.openai.chatgpt", intent.`package`)
        assertEquals("https://chatgpt.com/?q=what%20is%20rust", intent.dataString)
    }

    @Test
    fun geminiGetsTheQuerySharedToItsApp() {
        installActivity("com.google.android.apps.bard", Intent.ACTION_SEND, mimeType = "text/plain")
        val intent = open("!gemini what is rust")
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("com.google.android.apps.bard", intent.`package`)
        assertEquals("what is rust", intent.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun aCustomShortcutUnderTheSameTriggerIsNotAnAssistant() {
        val custom = SearchBang(prefix = "!claude", name = "Mine", urlTemplate = "https://example.com/?q=%s")
        assertNull(AiAssistants.forBang(custom))
        val intent = bangs.dispatchBangSearch(bangs.parseBangQuery("!claude hi", listOf(custom))!!)
        assertEquals("https://example.com/?q=hi", intent.dataString)
    }

    @Test
    fun anAssistantsQuickLaunchPillAsksItTheSearch() {
        installActivity("ai.perplexity.app.android", Intent.ACTION_VIEW, "https", "www.perplexity.ai")
        val intent = com.pixel.intelligentsearch.feature.search.quickAppSearchIntent(context, "ai.perplexity.app.android", "best hikes")!!
        assertEquals("ai.perplexity.app.android", intent.`package`)
        assertEquals("https://www.perplexity.ai/search?q=best%20hikes", intent.dataString)
    }

    @Test
    fun theShortcutEditorSuggestsAnAssistantsAddressForItsApp() {
        assertEquals(
            "claude" to "https://claude.ai/new?q=%s",
            com.pixel.intelligentsearch.feature.settings.suggestedWebShortcut("com.anthropic.claude")
        )
    }

    private fun installActivity(pkg: String, action: String, scheme: String? = null, host: String? = null, mimeType: String? = null) {
        val pm = shadowOf(context.packageManager)
        val component = ComponentName(pkg, "$pkg.Main")
        pm.addActivityIfNotPresent(component)
        pm.addIntentFilterForActivity(component, IntentFilter(action).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addCategory(Intent.CATEGORY_BROWSABLE)
            scheme?.let(::addDataScheme)
            host?.let { addDataAuthority(it, null) }
            mimeType?.let(::addDataType)
        })
        pm.addOrUpdateActivity(context.packageManager.getActivityInfo(component, 0).apply { exported = true })
    }
}

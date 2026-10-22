package com.pixel.intelligentsearch.core.bangs

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.net.toUri
import com.pixel.intelligentsearch.core.security.CustomSearchUrl
import java.util.Locale

/**
 * AI assistants as search destinations: each has a built-in web shortcut ("!gpt", "!pplx", "!gemini", "!claude",
 * "!copilot"), searches with the query from its Quick Launch pill, and is where an AI answer continues. The installed
 * app is preferred when it opens the assistant's own address (an App Link), otherwise the browser does; Gemini, whose
 * site takes no query in the address, gets the query shared to its app instead when the app is there.
 */
object AiAssistants {

    data class Assistant(
        /** The web shortcut's trigger, without its symbol. */
        val trigger: String,
        val name: String,
        /** The assistant's own address for a new conversation, %s standing for the query. */
        val urlTemplate: String,
        val packageName: String,
        /** Its app takes the query as shared text (ACTION_SEND) rather than through its address. */
        val sharesQueryToApp: Boolean = false,
        val description: String
    )

    val CHATGPT = Assistant("gpt", "ChatGPT", "https://chatgpt.com/?q=%s", "com.openai.chatgpt", description = "Ask ChatGPT.")
    val PERPLEXITY = Assistant("pplx", "Perplexity", "https://www.perplexity.ai/search?q=%s", "ai.perplexity.app.android", description = "Ask Perplexity.")
    val GEMINI = Assistant("gemini", "Gemini", "https://gemini.google.com/app?q=%s", "com.google.android.apps.bard", sharesQueryToApp = true, description = "Ask Gemini.")
    val CLAUDE = Assistant("claude", "Claude", "https://claude.ai/new?q=%s", "com.anthropic.claude", description = "Ask Claude.")
    val COPILOT = Assistant("copilot", "Copilot", "https://copilot.microsoft.com/?q=%s", "com.microsoft.copilot", description = "Ask Microsoft Copilot.")

    val ALL: List<Assistant> = listOf(CHATGPT, PERPLEXITY, GEMINI, CLAUDE, COPILOT)

    /** The built-in web shortcut for [assistant]. */
    fun bang(assistant: Assistant): SearchBang = SearchBang(
        prefix = "!${assistant.trigger}",
        name = assistant.name,
        urlTemplate = assistant.urlTemplate,
        targetPackage = assistant.packageName,
        isBuiltIn = true,
        description = assistant.description
    )

    /** The assistant a built-in [bang] opens, whatever trigger symbol it is written with; null for any other bang. */
    fun forBang(bang: SearchBang): Assistant? {
        if (!bang.isBuiltIn) return null
        val trigger = bang.prefix.lowercase(Locale.ROOT).trimStart { !it.isLetterOrDigit() }
        return ALL.firstOrNull { it.trigger == trigger && it.urlTemplate == bang.urlTemplate }
    }

    fun forPackage(packageName: String): Assistant? = ALL.firstOrNull { it.packageName == packageName }

    /** The assistant's address for [query]: a new conversation with the query typed in. */
    fun webUri(assistant: Assistant, query: String): Uri =
        CustomSearchUrl.build(assistant.urlTemplate, query.trim())
            ?: assistant.urlTemplate.substringBefore('?').toUri()

    /**
     * How to ask [assistant] [query]: in its app when the app is installed and handles it (its App Link, or for
     * Gemini the shared query), otherwise its address in a browser.
     */
    fun launchIntent(context: Context, assistant: Assistant, query: String): Intent {
        val pm = context.packageManager
        val web = webUri(assistant, query)
        if (assistant.sharesQueryToApp && query.isNotBlank()) {
            val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, query.trim()).setPackage(assistant.packageName)
            if (resolvesExported(pm, share)) return share.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val appLink = CustomSearchUrl.browsableViewIntent(web).setPackage(assistant.packageName)
        if (resolvesExported(pm, appLink)) return appLink.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return CustomSearchUrl.browsableViewIntent(web).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun resolvesExported(pm: PackageManager, intent: Intent): Boolean = try {
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(intent, 0).any { it.activityInfo?.exported == true }
    } catch (_: Exception) {
        false
    }
}

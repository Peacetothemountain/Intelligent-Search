package com.pixel.intelligentsearch.core.answers

import android.content.Intent
import androidx.core.net.toUri
import java.net.URLEncoder

/**
 * Hands translations to Google Translate, as the widget's Translate shortcut does: the app when the phone has it,
 * else its web page. Nothing is translated or sent by Intelligent Search itself; building these intents and
 * addresses touches no network.
 */
object GoogleTranslate {
    /** Google Translate's app, which the widget's Translate (text) shortcut opens as well. */
    const val PACKAGE = com.pixel.intelligentsearch.core.system.GoogleApps.TRANSLATE_APP

    /** Google Translate on the web, for phones without the app. */
    const val WEB_HOME = "https://translate.google.com"

    /**
     * The code Google Translate uses for [code], one of [TranslationLanguages.CODES]: it still names Hebrew "iw",
     * and wants Chinese as simplified or traditional rather than just "zh".
     */
    internal fun googleCode(code: String): String = when (code) {
        "he" -> "iw"
        "zh" -> "zh-CN"
        else -> code
    }

    /** Google Translate's page for [text], [source] to [target]; "auto" lets Google tell the source when it's null. */
    fun webAddress(text: String, source: String?, target: String): String {
        val encoded = URLEncoder.encode(text, Charsets.UTF_8.name()).replace("+", "%20")
        return "$WEB_HOME/?sl=${source?.let(::googleCode) ?: "auto"}&tl=${googleCode(target)}&text=$encoded&op=translate"
    }

    /**
     * Ways to open [text] in the Google Translate app, best first: its translate page, which keeps the languages,
     * then sharing the text to it, which opens the app with it in the box.
     */
    fun appIntents(text: String, source: String?, target: String): List<Intent> = listOf(
        Intent(Intent.ACTION_VIEW, webAddress(text, source, target).toUri()).setPackage(PACKAGE),
        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).setPackage(PACKAGE)
    ).onEach { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    /** The first of [appIntents] that [resolves] (the app is installed and takes it), or null to use [webAddress]. */
    fun appIntent(text: String, source: String?, target: String, resolves: (Intent) -> Boolean): Intent? =
        appIntents(text, source, target).firstOrNull(resolves)
}

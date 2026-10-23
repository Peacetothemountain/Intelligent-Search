package com.pixel.intelligentsearch.core.security

import android.content.SharedPreferences
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.BooleanSetting
import com.pixel.intelligentsearch.core.data.settings.get

/**
 * The one place that decides when the app may reach the network on its own, so every feature, its settings subtitle
 * and the system search provider agree. Nothing here sends anything; callers ask before they do.
 *
 * - While Tor is the search engine nothing goes out except through Tor Browser: no suggestions, answers, rates, AI
 *   questions or icon fetches.
 * - Web (the Search Sources master switch) off means no web traffic at all.
 * - Live web suggestions and the instant answers that send what was typed (definitions, forecasts) also need Web
 *   Suggestions on; each answer has its own switch as well.
 */
object NetworkPolicy {

    /** The stored search-engine names that route searches through Tor Browser. */
    val TOR_ENGINES = listOf("Tor Project", "Tor Browser")

    /** The stored search-engine name of a user-entered search address, which has no suggestion service. */
    const val CUSTOM_ENGINE = "Custom"

    /** Why a feature can't reach the network right now, apart from its own switch. */
    enum class Block {
        /** Tor is the search engine. */
        TOR,
        /** Web is off in Search Sources. */
        WEB_OFF,
        /** Web Suggestions is off. */
        WEB_SUGGESTIONS_OFF
    }

    /** Whether [engine] (the stored search-engine choice) routes searches through Tor Browser. Exact names only. */
    fun isTorEngine(engine: String): Boolean = TOR_ENGINES.any { engine.trim().equals(it, ignoreCase = true) }

    fun torSelected(prefs: SharedPreferences): Boolean = isTorEngine(prefs[SettingsManager.SEARCH_ENGINE])

    /** Whether [engine] has a suggestion service to ask: not Tor, and not a custom search address. */
    fun engineSuggests(engine: String): Boolean = !isTorEngine(engine) && !engine.equals(CUSTOM_ENGINE, ignoreCase = true)

    /** Live web suggestions: Web and Web Suggestions on, and an engine that has suggestions (not Tor, not Custom). */
    fun webSuggestionsAllowed(prefs: SharedPreferences): Boolean =
        prefs[SettingsManager.SEARCH_WEB] && prefs[SettingsManager.SEARCH_WEB_SUGGESTIONS] &&
            engineSuggests(prefs[SettingsManager.SEARCH_ENGINE])

    /**
     * What keeps the instant answers that send what was typed (a word, a place) to a third party from running, or
     * null when only their own switch decides. They follow the live suggestions' switches: Web and Web Suggestions on,
     * and never while Tor is the engine.
     */
    fun webAnswersBlock(prefs: SharedPreferences): Block? = webAnswersBlock(
        web = prefs[SettingsManager.SEARCH_WEB],
        webSuggestions = prefs[SettingsManager.SEARCH_WEB_SUGGESTIONS],
        engine = prefs[SettingsManager.SEARCH_ENGINE]
    )

    /** [webAnswersBlock] from the settings' values, for a settings page that observes them. */
    fun webAnswersBlock(web: Boolean, webSuggestions: Boolean, engine: String): Block? = when {
        isTorEngine(engine) -> Block.TOR
        !web -> Block.WEB_OFF
        !webSuggestions -> Block.WEB_SUGGESTIONS_OFF
        else -> null
    }

    /** Whether the instant answer behind [feature] (Dictionary or Weather Answers) may reach the network. */
    fun webAnswersAllowed(prefs: SharedPreferences, feature: BooleanSetting): Boolean =
        prefs[feature] && webAnswersBlock(prefs) == null

    /**
     * What keeps AI Answers from asking, apart from its own switch: Tor as the engine, or Web off. A question is only
     * ever sent from the user's tap, so Web Suggestions doesn't matter.
     */
    fun aiBlock(prefs: SharedPreferences): Block? =
        aiBlock(web = prefs[SettingsManager.SEARCH_WEB], engine = prefs[SettingsManager.SEARCH_ENGINE])

    /** [aiBlock] from the settings' values, for a settings page that observes them. */
    fun aiBlock(web: Boolean, engine: String): Block? = when {
        isTorEngine(engine) -> Block.TOR
        !web -> Block.WEB_OFF
        else -> null
    }

    /** What a settings subtitle says about [block] in place of (or ahead of) the feature's own description. */
    fun blockSubtitle(block: Block): String = when (block) {
        Block.TOR -> "Off While Tor Is Your Search Engine."
        Block.WEB_OFF -> "Needs Web On in Search Sources."
        Block.WEB_SUGGESTIONS_OFF -> "Needs Web Suggestions On."
    }

    /**
     * Whether currency rates may be fetched from the ECB: the Calculator and Live Currency Rates on and Tor not the
     * engine. With the Calculator off there are no currency answers, so nothing to fetch rates for.
     */
    fun liveRatesAllowed(prefs: SharedPreferences): Boolean =
        prefs[SettingsManager.SEARCH_CALCULATOR] && prefs[SettingsManager.LIVE_CURRENCY_RATES] && !torSelected(prefs)
}

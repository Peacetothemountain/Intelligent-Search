package com.pixel.intelligentsearch.core.bangs

import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.data.settings.get
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.pixel.intelligentsearch.core.data.SettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchBangManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager
) {

    companion object {
        /** The only app-link schemes dispatched, all from built-in bangs (YouTube, Maps, Play Store, Spotify). */
        private val BUILT_IN_APP_URI_SCHEMES = setOf("vnd.youtube", "geo", "market", "spotify")

        private val WHITESPACE = Regex("\\s+")

        val BUILT_IN_BANGS = listOf(
            SearchBang(
                prefix = "!g",
                name = "Google",
                urlTemplate = "https://www.google.com/search?q=%s",
                targetPackage = com.pixel.intelligentsearch.core.system.GoogleApps.GOOGLE_APP,
                isBuiltIn = true,
                description = "Search Google Directly."
            ),
            SearchBang(
                prefix = "!yt",
                name = "YouTube",
                urlTemplate = "https://www.youtube.com/results?search_query=%s",
                targetPackage = "com.google.android.youtube",
                appIntentUriTemplate = "vnd.youtube://results?q=%s",
                isBuiltIn = true,
                description = "Search Videos on YouTube."
            ),
            SearchBang(
                prefix = "!w",
                name = "Wikipedia",
                urlTemplate = "https://en.wikipedia.org/wiki/Special:Search?search=%s",
                targetPackage = "org.wikipedia",
                isBuiltIn = true,
                description = "Look up Articles on Wikipedia."
            ),
            SearchBang(
                prefix = "!r",
                name = "Reddit",
                urlTemplate = "https://www.reddit.com/search/?q=%s",
                targetPackage = "com.reddit.frontpage",
                isBuiltIn = true,
                description = "Search Discussions on Reddit."
            ),
            SearchBang(
                prefix = "!m",
                name = "Google Maps",
                urlTemplate = "https://www.google.com/maps/search/%s",
                targetPackage = "com.google.android.apps.maps",
                appIntentUriTemplate = "geo:0,0?q=%s",
                isBuiltIn = true,
                description = "Find Locations and Directions on Google Maps."
            ),
            SearchBang(
                prefix = "!a",
                name = "Amazon",
                urlTemplate = "https://www.amazon.com/s?k=%s",
                targetPackage = "com.amazon.mShop.android.shopping",
                isBuiltIn = true,
                description = "Search Products on Amazon."
            ),
            SearchBang(
                prefix = "!gh",
                name = "GitHub",
                urlTemplate = "https://github.com/search?q=%s",
                targetPackage = "com.github.android",
                isBuiltIn = true,
                description = "Search Repositories and Code on GitHub."
            ),
            SearchBang(
                prefix = "!d",
                name = "Google Drive",
                urlTemplate = "https://drive.google.com/drive/search?q=%s",
                targetPackage = "com.google.android.apps.docs",
                isBuiltIn = true,
                description = "Search Files on Google Drive."
            ),
            SearchBang(
                prefix = "!ddg",
                name = "DuckDuckGo",
                urlTemplate = "https://duckduckgo.com/?q=%s",
                targetPackage = "com.duckduckgo.mobile.android",
                isBuiltIn = true,
                description = "Search Private Web with DuckDuckGo."
            ),
            SearchBang(
                prefix = "!x",
                name = "X / Twitter",
                urlTemplate = "https://x.com/search?q=%s",
                targetPackage = "com.twitter.android",
                isBuiltIn = true,
                description = "Search Posts on X."
            ),
            SearchBang(
                prefix = "!so",
                name = "Stack Overflow",
                urlTemplate = "https://stackoverflow.com/search?q=%s",
                isBuiltIn = true,
                description = "Search Programming Q&A on Stack Overflow."
            ),
            SearchBang(
                prefix = "!play",
                name = "Play Store",
                urlTemplate = "https://play.google.com/store/search?q=%s&c=apps",
                targetPackage = "com.android.vending",
                appIntentUriTemplate = "market://search?q=%s",
                isBuiltIn = true,
                description = "Search Apps and Games on Play Store."
            ),
            SearchBang(
                prefix = "!spot",
                name = "Spotify",
                urlTemplate = "https://open.spotify.com/search/%s",
                targetPackage = "com.spotify.music",
                appIntentUriTemplate = "spotify:search:%s",
                isBuiltIn = true,
                description = "Search Songs and Artists on Spotify."
            )
        ) + AiAssistants.ALL.map(AiAssistants::bang)

        /** A built-in's prefix with the "!" trigger, whatever trigger it was written with. */
        fun canonicalBuiltInPrefix(prefix: String): String =
            "!" + prefix.lowercase(Locale.ROOT).trim().trimStart { !it.isLetterOrDigit() }

        /**
         * The built-ins the user has removed, for the editor to offer back. Like [buildBangs] it compares canonical
         * prefixes, so one removed under an older trigger symbol is still listed.
         */
        fun availableBuiltIns(disabled: Set<String>): List<SearchBang> {
            val disabledCanonical = disabled.map(::canonicalBuiltInPrefix).toSet()
            return BUILT_IN_BANGS.filter { canonicalBuiltInPrefix(it.displayPrefix) in disabledCanonical }
        }
    }

    private fun getPrefs(): SharedPreferences = settingsManager.prefs

    fun getTriggerSymbol(): String = getPrefs()[SettingsManager.WEB_SHORTCUT_TRIGGER_SYMBOL].ifBlank { "!" }

    /**
     * The "Enable Quick Web Shortcuts" switch. While it's off no query is read as a shortcut and none are
     * suggested, so "!yt cats" is searched as typed; the editor still lists the shortcuts through [bangsFlow].
     */
    private fun quickShortcutsEnabled(): Boolean = getPrefs()[SettingsManager.SEARCH_QUICK_SHORTCUTS]

    val bangsFlow: Flow<List<SearchBang>> = settingsManager.settingsFlow.map { settings ->
        bangsFor(settings.webShortcutTriggerSymbol.ifBlank { "!" }, settings.customBangsJson, settings.disabledWebShortcuts)
    }

    /** The shortcuts last built, with the stored values they were built from. */
    private class BuiltBangs(val trigger: String, val customBangsJson: String, val disabled: Set<String>, val bangs: List<SearchBang>)

    /**
     * Kept with what it was built from and checked against the stored values at each use, not cleared by a change
     * listener: SharedPreferences keeps its listeners weakly, and the release build's shrinker can drop the field that
     * holds one, which left the trigger symbol and the shortcuts as they were when the app started.
     */
    @Volatile
    private var builtBangs: BuiltBangs? = null
    private val mutex = Mutex()

    fun getAllBangsSync(): List<SearchBang> = getPrefs().let { prefs ->
        bangsFor(getTriggerSymbol(), prefs[SettingsManager.CUSTOM_BANGS_JSON], prefs[SettingsManager.DISABLED_WEB_SHORTCUTS])
    }

    private fun bangsFor(trigger: String, customBangsJson: String, disabled: Set<String>): List<SearchBang> {
        builtBangs?.takeIf { it.trigger == trigger && it.customBangsJson == customBangsJson && it.disabled == disabled }
            ?.let { return it.bangs }
        return buildBangs(trigger, customBangsJson, disabled)
            .also { builtBangs = BuiltBangs(trigger, customBangsJson, disabled, it) }
    }

    /** Custom bangs first, then the built-ins that aren't disabled, re-prefixed when the trigger isn't "!". */
    private fun buildBangs(trigger: String, customBangsJson: String, disabled: Set<String>): List<SearchBang> {
        val builtIns = BUILT_IN_BANGS.map { bang ->
            if (trigger != "!" && bang.prefix.startsWith("!")) bang.copy(prefix = trigger + bang.prefix.removePrefix("!")) else bang
        }
        // Disabled built-ins are compared by their canonical "!" prefix, so changing the trigger symbol doesn't
        // bring them back (older entries were stored with whatever trigger was current at the time).
        val disabledCanonical = disabled.map(::canonicalBuiltInPrefix).toSet()
        return (parseCustomBangs(customBangsJson) + builtIns.filter { canonicalBuiltInPrefix(it.displayPrefix) !in disabledCanonical })
            .distinctBy { it.displayPrefix }
    }

    fun parseBangQuery(query: String, availableBangs: List<SearchBang> = getAllBangsSync()): ParsedBangQuery? {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || !quickShortcutsEnabled()) return null
        val triggerSymbol = getTriggerSymbol()

        // Fast-path: Bangs strictly require the active trigger symbol or universal fallback '!'
        if (!trimmed.contains(triggerSymbol) && !trimmed.contains("!")) {
            return null
        }

        // A bang is a word of its own, ended by any space a keyboard types (a tab after "!g" too). The one typed first
        // wins over one at the end, whatever order the bangs are listed in: "!yt cats !g" searches YouTube, and the
        // second bang is dropped rather than searched for.
        val firstEnd = trimmed.indexOfFirst { it.isWhitespace() }.let { if (it < 0) trimmed.length else it }
        bangNamed(trimmed.substring(0, firstEnd), availableBangs, triggerSymbol)?.let { bang ->
            return ParsedBangQuery(
                bang = bang,
                rawQuery = trimmed,
                extractedQuery = withoutBangs(trimmed.substring(firstEnd), availableBangs, triggerSymbol),
                isPrefix = true
            )
        }
        val lastStart = trimmed.indexOfLast { it.isWhitespace() } + 1
        if (lastStart > 0) {
            bangNamed(trimmed.substring(lastStart), availableBangs, triggerSymbol)?.let { bang ->
                return ParsedBangQuery(
                    bang = bang,
                    rawQuery = trimmed,
                    extractedQuery = withoutBangs(trimmed.substring(0, lastStart), availableBangs, triggerSymbol),
                    isPrefix = false
                )
            }
        }

        return null
    }

    /** The bang [word] calls, by any of the ways it can be typed, or null when it calls none. */
    private fun bangNamed(word: String, availableBangs: List<SearchBang>, triggerSymbol: String): SearchBang? {
        if (word.isEmpty() || word.first().isLetterOrDigit()) return null
        for (bang in availableBangs) {
            val bare = bang.prefix.trimStart { !it.isLetterOrDigit() }
            val p0 = bang.displayPrefix
            val p1 = bang.prefix.lowercase()
            val p2 = if (bare.isNotBlank()) "$triggerSymbol$bare".lowercase() else null
            val p3 = if (bare.isNotBlank()) "!$bare".lowercase() else null

            for (p in arrayOf(p0, p1, p2, p3)) {
                // A bang always starts with its trigger: a bare prefix ("wiki", from an older custom bang) would
                // otherwise fire on ordinary words whenever the query happens to contain "!".
                if (p.isNullOrBlank() || p.first().isLetterOrDigit()) continue
                if (word.equals(p, ignoreCase = true)) return bang
            }
        }
        return null
    }

    /** [text] with any further bang words taken out, trimmed: what the chosen bang searches for. */
    private fun withoutBangs(text: String, availableBangs: List<SearchBang>, triggerSymbol: String): String {
        val trimmed = text.trim()
        if (!trimmed.contains(triggerSymbol) && !trimmed.contains("!")) return trimmed
        val words = trimmed.split(WHITESPACE)
        if (words.none { bangNamed(it, availableBangs, triggerSymbol) != null }) return trimmed
        return words.filter { bangNamed(it, availableBangs, triggerSymbol) == null }.joinToString(" ")
    }

    fun getBangSuggestions(partialToken: String, availableBangs: List<SearchBang> = getAllBangsSync()): List<SearchBang> {
        val token = partialToken.trim().lowercase()
        if (token.isEmpty() || !quickShortcutsEnabled()) return emptyList()
        val triggerSymbol = getTriggerSymbol().lowercase()

        val startsWithTrigger = token.startsWith(triggerSymbol) || token.startsWith("!")
        val bareToken = token.trimStart { !it.isLetterOrDigit() }

        if (!startsWithTrigger && bareToken.isEmpty()) return emptyList()

        return availableBangs.filter { bang ->
            val bare = bang.prefix.trimStart { !it.isLetterOrDigit() }.lowercase()
            val customPrefixWithTrigger = "$triggerSymbol$bare"
            bang.displayPrefix.startsWith(token) ||
            customPrefixWithTrigger.startsWith(token) ||
            (bareToken.isNotEmpty() && (bare.startsWith(bareToken) || bang.name.lowercase().startsWith(bareToken)))
        }.take(8)
    }

    fun dispatchBangSearch(parsed: ParsedBangQuery): Intent {
        val bang = parsed.bang
        val query = parsed.extractedQuery
        val pm = context.packageManager

        // AI assistants open in their app when it takes their address (or, for Gemini, the shared query), else the web.
        AiAssistants.forBang(bang)?.let { return AiAssistants.launchIntent(context, it, query) }

        // Priority 1: Native Application Intent Deep Link (if installed)
        if (!bang.targetPackage.isNullOrBlank() && !bang.targetPackage.equals(context.packageName, ignoreCase = true)) {
            val isInstalled = isPackageInstalled(pm, bang.targetPackage)
            if (isInstalled) {
                val appUri = if (bang.isBuiltIn) bang.buildAppUri(query) else null
                val appScheme = appUri?.scheme?.lowercase(Locale.ROOT)
                if (appUri != null && appScheme in BUILT_IN_APP_URI_SCHEMES) {
                    val nativeIntent = Intent(Intent.ACTION_VIEW, appUri).apply {
                        addCategory(Intent.CATEGORY_BROWSABLE)
                        setPackage(bang.targetPackage)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val resolvedBrowsable = nativeIntent.resolveActivity(pm)
                    if (resolvedBrowsable != null && isActivityExported(pm, resolvedBrowsable)) {
                        return nativeIntent
                    }
                    val fallbackNativeIntent = Intent(Intent.ACTION_VIEW, appUri).apply {
                        setPackage(bang.targetPackage)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val resolvedNative = fallbackNativeIntent.resolveActivity(pm)
                    if (resolvedNative != null && isActivityExported(pm, resolvedNative)) {
                        return fallbackNativeIntent
                    }
                }

                // If native scheme failed, try standard search intent targeting the package
                val pkgSearchIntent = Intent(Intent.ACTION_SEARCH).apply {
                    setPackage(bang.targetPackage)
                    putExtra("query", query)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val resolved = pkgSearchIntent.resolveActivity(pm)
                if (resolved != null && isActivityExported(pm, resolved)) {
                    return pkgSearchIntent
                }
            }
        }

        // Priority 2: the web URL.
        return com.pixel.intelligentsearch.core.security.CustomSearchUrl.browsableViewIntent(bangWebUri(parsed)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * The bang's web address for its query. Custom bangs can arrive from imported backups, so the template goes
     * through the same validation as a typed custom engine; one that fails is never opened and the query goes to Google.
     */
    fun bangWebUri(parsed: ParsedBangQuery): Uri =
        com.pixel.intelligentsearch.core.security.CustomSearchUrl.build(parsed.bang.urlTemplate, parsed.extractedQuery)
            ?: Uri.parse("https://www.google.com/search?q=${Uri.encode(parsed.extractedQuery)}")

    private fun isActivityExported(pm: PackageManager, component: android.content.ComponentName): Boolean {
        return try {
            val activityInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getActivityInfo(component, 0)
            }
            activityInfo.exported
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Adds or edits a custom bang in one atomic write. When editing, the bang it replaces is removed first: a
     * built-in is disabled (so it can be restored), a custom bang under a different prefix is deleted.
     */
    suspend fun saveBang(bang: SearchBang, replacing: SearchBang? = null) = editBangs { custom, disabled ->
        replacing?.let { removeFrom(custom, disabled, it) }
        custom.removeAll { it.displayPrefix == bang.displayPrefix }
        custom.add(bang.copy(isBuiltIn = false))
        disabled.remove(bang.displayPrefix)
    }

    /** Removes bangs in one write: built-ins are disabled, custom bangs are deleted. */
    suspend fun removeBangs(bangs: List<SearchBang>) = editBangs { custom, disabled ->
        bangs.forEach { removeFrom(custom, disabled, it) }
    }

    suspend fun restoreBuiltInBang(prefix: String) = editBangs { _, disabled ->
        val canonical = canonicalBuiltInPrefix(prefix)
        disabled.removeAll { canonicalBuiltInPrefix(it) == canonical }
    }

    private fun removeFrom(custom: MutableList<SearchBang>, disabled: MutableSet<String>, bang: SearchBang) {
        if (bang.isBuiltIn) disabled.add(canonicalBuiltInPrefix(bang.displayPrefix)) else custom.removeAll { it.displayPrefix == bang.displayPrefix }
    }

    /** Read-modify-write of both bang settings under one lock, committed together so edits can't interleave. */
    private suspend fun editBangs(edit: (MutableList<SearchBang>, MutableSet<String>) -> Unit) {
        mutex.withLock {
            val prefs = getPrefs()
            val custom = parseCustomBangs(prefs[SettingsManager.CUSTOM_BANGS_JSON]).toMutableList()
            val disabled = prefs[SettingsManager.DISABLED_WEB_SHORTCUTS].toMutableSet()
            edit(custom, disabled)
            prefs.edit()
                .put(SettingsManager.CUSTOM_BANGS_JSON, serializeCustomBangs(custom))
                .put(SettingsManager.DISABLED_WEB_SHORTCUTS, disabled.toSet())
                .commit()
        }
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun parseCustomBangs(jsonString: String): List<SearchBang> {
        if (jsonString.isBlank()) return emptyList()
        val list = mutableListOf<SearchBang>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                try {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        SearchBang(
                            prefix = obj.getString("prefix"),
                            name = obj.getString("name"),
                            urlTemplate = obj.getString("urlTemplate"),
                            targetPackage = obj.optString("targetPackage").ifBlank { null },
                            // Custom bangs never carry an app link: one restored from a crafted backup could
                            // otherwise open sms:, tel: or another app scheme. Only built-in bangs have them.
                            appIntentUriTemplate = null,
                            isBuiltIn = false,
                            description = obj.optString("description")
                        )
                    )
                } catch (_: Throwable) {}
            }
        } catch (e: Exception) {
            android.util.Log.w("IntelligentSearch", "Unexpected error", e)
        }
        return list
    }

    fun serializeCustomBangs(bangs: List<SearchBang>): String {
        val jsonArray = JSONArray()
        for (bang in bangs) {
            val obj = JSONObject().apply {
                put("prefix", bang.prefix)
                put("name", bang.name)
                put("urlTemplate", bang.urlTemplate)
                put("targetPackage", bang.targetPackage ?: "")
                put("isBuiltIn", false)
                put("description", bang.description)
            }
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }
}

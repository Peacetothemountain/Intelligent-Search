package com.pixel.intelligentsearch.core.bangs

import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.data.settings.get
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.UserManager
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
        private val BLOCKED_APP_URI_SCHEMES = setOf(
            "file", "content", "javascript", "data", "intent", "vbscript"
        )

        val BUILT_IN_BANGS = listOf(
            SearchBang(
                prefix = "!g",
                name = "Google",
                urlTemplate = "https://www.google.com/search?q=%s",
                targetPackage = "com.google.android.googlequicksearchbox",
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
        )
    }

    private fun getPrefs(): SharedPreferences = settingsManager.prefs

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == SettingsManager.WEB_SHORTCUT_TRIGGER_SYMBOL.name ||
            key == SettingsManager.CUSTOM_BANGS_JSON.name || key == SettingsManager.DISABLED_WEB_SHORTCUTS.name
        ) {
            cachedTriggerSymbol = null
            cachedSyncBangs = null
        }
    }

    init {
        try {
            getPrefs().registerOnSharedPreferenceChangeListener(prefChangeListener)
        } catch (_: Exception) {}
    }

    @Volatile
    private var cachedTriggerSymbol: String? = null

    fun getTriggerSymbol(): String =
        cachedTriggerSymbol ?: getPrefs()[SettingsManager.WEB_SHORTCUT_TRIGGER_SYMBOL].ifBlank { "!" }
            .also { cachedTriggerSymbol = it }

    val bangsFlow: Flow<List<SearchBang>> = settingsManager.settingsFlow.map { settings ->
        val trigger = settings.webShortcutTriggerSymbol.ifBlank { "!" }
        cachedTriggerSymbol = trigger
        buildBangs(trigger, settings.customBangsJson, settings.disabledWebShortcuts).also { cachedSyncBangs = it }
    }

    @Volatile
    private var cachedSyncBangs: List<SearchBang>? = null
    private val mutex = Mutex()

    fun getAllBangsSync(): List<SearchBang> = cachedSyncBangs ?: getPrefs().let { prefs ->
        buildBangs(
            getTriggerSymbol(),
            prefs[SettingsManager.CUSTOM_BANGS_JSON],
            prefs[SettingsManager.DISABLED_WEB_SHORTCUTS]
        ).also { cachedSyncBangs = it }
    }

    /** Custom bangs first, then the built-ins that aren't disabled, re-prefixed when the trigger isn't "!". */
    private fun buildBangs(trigger: String, customBangsJson: String, disabled: Set<String>): List<SearchBang> {
        val builtIns = BUILT_IN_BANGS.map { bang ->
            if (trigger != "!" && bang.prefix.startsWith("!")) bang.copy(prefix = trigger + bang.prefix.removePrefix("!")) else bang
        }
        return (parseCustomBangs(customBangsJson) + builtIns.filter { it.displayPrefix !in disabled })
            .distinctBy { it.displayPrefix }
    }

    fun parseBangQuery(query: String, availableBangs: List<SearchBang> = getAllBangsSync()): ParsedBangQuery? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null
        val triggerSymbol = getTriggerSymbol()

        // Fast-path: Bangs strictly require the active trigger symbol or universal fallback '!'
        if (!trimmed.contains(triggerSymbol) && !trimmed.contains("!")) {
            return null
        }

        // Match against all available bangs without collection allocation
        for (bang in availableBangs) {
            val bare = bang.prefix.trimStart { !it.isLetterOrDigit() }
            val p0 = bang.displayPrefix
            val p1 = bang.prefix.lowercase()
            val p2 = if (bare.isNotBlank()) "$triggerSymbol$bare".lowercase() else null
            val p3 = if (bare.isNotBlank()) "!$bare".lowercase() else null

            val candidates = arrayOf(p0, p1, p2, p3)
            for (p in candidates) {
                if (p.isNullOrBlank()) continue
                // Prefix check: query starts with "$p " or equals "$p"
                if (trimmed.equals(p, ignoreCase = true)) {
                    return ParsedBangQuery(
                        bang = bang,
                        rawQuery = trimmed,
                        extractedQuery = "",
                        isPrefix = true
                    )
                }
                if (trimmed.startsWith("$p ", ignoreCase = true)) {
                    val rawRemaining = trimmed.substring(p.length).trim()
                    return ParsedBangQuery(
                        bang = bang,
                        rawQuery = trimmed,
                        extractedQuery = rawRemaining,
                        isPrefix = true
                    )
                }
                // Suffix check: query ends with " $p"
                if (trimmed.endsWith(" $p", ignoreCase = true)) {
                    val rawLeading = trimmed.substring(0, trimmed.length - p.length).trim()
                    return ParsedBangQuery(
                        bang = bang,
                        rawQuery = trimmed,
                        extractedQuery = rawLeading,
                        isPrefix = false
                    )
                }
            }
        }

        return null
    }

    fun getBangSuggestions(partialToken: String, availableBangs: List<SearchBang> = getAllBangsSync()): List<SearchBang> {
        val token = partialToken.trim().lowercase()
        if (token.isEmpty()) return emptyList()
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

        // Priority 1: Native Application Intent Deep Link (if installed)
        if (!bang.targetPackage.isNullOrBlank() && !bang.targetPackage.equals(context.packageName, ignoreCase = true)) {
            val isInstalled = isPackageInstalled(pm, bang.targetPackage)
            if (isInstalled) {
                val appUri = bang.buildAppUri(query)
                val appScheme = appUri?.scheme?.lowercase(Locale.ROOT)
                if (appUri != null && !appScheme.isNullOrBlank() && appScheme !in BLOCKED_APP_URI_SCHEMES) {
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

        // Priority 2: Universal Web URL Dispatch. Custom bangs can arrive from imported backups, so only
        // web URLs are dispatched; any other scheme (intent:, file:, javascript:...) is forced onto https.
        val webUrl = bang.buildWebUrl(query).let { url ->
            val scheme = Uri.parse(url).scheme?.lowercase(Locale.ROOT)
            if (scheme == "http" || scheme == "https") url else "https://${url.substringAfter("://")}"
        }
        return Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

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
        disabled.remove(prefix.lowercase(Locale.ROOT).trim())
    }

    private fun removeFrom(custom: MutableList<SearchBang>, disabled: MutableSet<String>, bang: SearchBang) {
        if (bang.isBuiltIn) disabled.add(bang.displayPrefix) else custom.removeAll { it.displayPrefix == bang.displayPrefix }
    }

    /** Read-modify-write of both bang settings under one lock, committed together so edits can't interleave. */
    private suspend fun editBangs(edit: (MutableList<SearchBang>, MutableSet<String>) -> Unit) {
        mutex.withLock {
            val prefs = getPrefs()
            val custom = parseCustomBangs(prefs[SettingsManager.CUSTOM_BANGS_JSON]).toMutableList()
            val disabled = prefs[SettingsManager.DISABLED_WEB_SHORTCUTS].toMutableSet()
            edit(custom, disabled)
            cachedSyncBangs = null
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
                            appIntentUriTemplate = obj.optString("appIntentUriTemplate").ifBlank { null },
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
                put("appIntentUriTemplate", bang.appIntentUriTemplate ?: "")
                put("isBuiltIn", false)
                put("description", bang.description)
            }
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }
}

package com.pixel.intelligentsearch.core.bangs

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.pixel.intelligentsearch.core.data.SettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchBangManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager
) {

    companion object {
        val BUILT_IN_BANGS = listOf(
            SearchBang(
                prefix = "!g",
                name = "Google",
                urlTemplate = "https://www.google.com/search?q=%s",
                targetPackage = "com.google.android.googlequicksearchbox",
                isBuiltIn = true,
                description = "Search Google directly"
            ),
            SearchBang(
                prefix = "!yt",
                name = "YouTube",
                urlTemplate = "https://www.youtube.com/results?search_query=%s",
                targetPackage = "com.google.android.youtube",
                appIntentUriTemplate = "vnd.youtube://results?q=%s",
                isBuiltIn = true,
                description = "Search videos on YouTube"
            ),
            SearchBang(
                prefix = "!w",
                name = "Wikipedia",
                urlTemplate = "https://en.wikipedia.org/wiki/Special:Search?search=%s",
                targetPackage = "org.wikipedia",
                isBuiltIn = true,
                description = "Look up articles on Wikipedia"
            ),
            SearchBang(
                prefix = "!r",
                name = "Reddit",
                urlTemplate = "https://www.reddit.com/search/?q=%s",
                targetPackage = "com.reddit.frontpage",
                isBuiltIn = true,
                description = "Search discussions on Reddit"
            ),
            SearchBang(
                prefix = "!m",
                name = "Google Maps",
                urlTemplate = "https://www.google.com/maps/search/%s",
                targetPackage = "com.google.android.apps.maps",
                appIntentUriTemplate = "geo:0,0?q=%s",
                isBuiltIn = true,
                description = "Find locations and directions on Maps"
            ),
            SearchBang(
                prefix = "!a",
                name = "Amazon",
                urlTemplate = "https://www.amazon.com/s?k=%s",
                targetPackage = "com.amazon.mShop.android.shopping",
                isBuiltIn = true,
                description = "Search products on Amazon"
            ),
            SearchBang(
                prefix = "!gh",
                name = "GitHub",
                urlTemplate = "https://github.com/search?q=%s",
                targetPackage = "com.github.android",
                isBuiltIn = true,
                description = "Search repositories and code on GitHub"
            ),
            SearchBang(
                prefix = "!d",
                name = "Google Drive",
                urlTemplate = "https://drive.google.com/drive/search?q=%s",
                targetPackage = "com.google.android.apps.docs",
                isBuiltIn = true,
                description = "Search files on Google Drive"
            ),
            SearchBang(
                prefix = "!ddg",
                name = "DuckDuckGo",
                urlTemplate = "https://duckduckgo.com/?q=%s",
                targetPackage = "com.duckduckgo.mobile.android",
                isBuiltIn = true,
                description = "Search private web with DuckDuckGo"
            ),
            SearchBang(
                prefix = "!x",
                name = "X / Twitter",
                urlTemplate = "https://x.com/search?q=%s",
                targetPackage = "com.twitter.android",
                isBuiltIn = true,
                description = "Search posts on X"
            ),
            SearchBang(
                prefix = "!so",
                name = "Stack Overflow",
                urlTemplate = "https://stackoverflow.com/search?q=%s",
                isBuiltIn = true,
                description = "Search programming Q&A on Stack Overflow"
            ),
            SearchBang(
                prefix = "!play",
                name = "Play Store",
                urlTemplate = "https://play.google.com/store/search?q=%s&c=apps",
                targetPackage = "com.android.vending",
                appIntentUriTemplate = "market://search?q=%s",
                isBuiltIn = true,
                description = "Search apps and games on Play Store"
            ),
            SearchBang(
                prefix = "!spot",
                name = "Spotify",
                urlTemplate = "https://open.spotify.com/search/%s",
                targetPackage = "com.spotify.music",
                appIntentUriTemplate = "spotify:search:%s",
                isBuiltIn = true,
                description = "Search songs and artists on Spotify"
            )
        )

        private val PREFIX_BANG_PATTERN = Pattern.compile("^(![a-zA-Z0-9_-]+)\\s*(.*)$")
        private val SUFFIX_BANG_PATTERN = Pattern.compile("^(.*?)\\s+(![a-zA-Z0-9_-]+)$")
    }

    val bangsFlow: Flow<List<SearchBang>> = settingsManager.settingsFlow.map { settings ->
        val customBangs = parseCustomBangs(settings.customBangsJson)
        val disabled = settings.disabledWebShortcuts
        (BUILT_IN_BANGS.filter { it.displayPrefix !in disabled } + customBangs).distinctBy { it.displayPrefix }
    }

    fun getAllBangsSync(): List<SearchBang> {
        val prefs = context.getSharedPreferences("PREFERENCES_CUSTOMISATIONS", Context.MODE_PRIVATE)
        val customBangsJson = prefs.getString("custom_bangs_json", null)
            ?: settingsManager.getInitialSettings().customBangsJson
        val customBangs = parseCustomBangs(customBangsJson)
        val disabled = prefs.getStringSet("disabled_web_shortcuts", null)?.toSet()
            ?: settingsManager.getInitialSettings().disabledWebShortcuts
        return (BUILT_IN_BANGS.filter { it.displayPrefix !in disabled } + customBangs).distinctBy { it.displayPrefix }
    }

    fun parseBangQuery(query: String, availableBangs: List<SearchBang> = getAllBangsSync()): ParsedBangQuery? {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || !trimmed.contains("!")) return null

        // 1. Check Prefix Bang (!yt query)
        val prefixMatcher = PREFIX_BANG_PATTERN.matcher(trimmed)
        if (prefixMatcher.matches()) {
            val prefixToken = prefixMatcher.group(1)?.lowercase() ?: ""
            val rawRemaining = prefixMatcher.group(2)?.trim() ?: ""
            val matchedBang = availableBangs.firstOrNull { it.displayPrefix == prefixToken }
            if (matchedBang != null) {
                return ParsedBangQuery(
                    bang = matchedBang,
                    rawQuery = trimmed,
                    extractedQuery = rawRemaining,
                    isPrefix = true
                )
            }
        }

        // 2. Check Suffix Bang (query !yt)
        val suffixMatcher = SUFFIX_BANG_PATTERN.matcher(trimmed)
        if (suffixMatcher.matches()) {
            val rawLeading = suffixMatcher.group(1)?.trim() ?: ""
            val suffixToken = suffixMatcher.group(2)?.lowercase() ?: ""
            val matchedBang = availableBangs.firstOrNull { it.displayPrefix == suffixToken }
            if (matchedBang != null) {
                return ParsedBangQuery(
                    bang = matchedBang,
                    rawQuery = trimmed,
                    extractedQuery = rawLeading,
                    isPrefix = false
                )
            }
        }

        return null
    }

    fun getBangSuggestions(partialToken: String, availableBangs: List<SearchBang> = getAllBangsSync()): List<SearchBang> {
        val token = partialToken.trim().lowercase()
        if (!token.startsWith("!")) return emptyList()
        return availableBangs.filter { bang ->
            bang.displayPrefix.startsWith(token) || bang.name.lowercase().startsWith(token.removePrefix("!"))
        }.take(8)
    }

    fun dispatchBangSearch(parsed: ParsedBangQuery): Intent {
        val bang = parsed.bang
        val query = parsed.extractedQuery
        val pm = context.packageManager

        // Priority 1: Native Application Intent Deep Link (if installed)
        if (!bang.targetPackage.isNullOrBlank()) {
            val isInstalled = isPackageInstalled(pm, bang.targetPackage)
            if (isInstalled) {
                val appUri = bang.buildAppUri(query)
                if (appUri != null) {
                    val nativeIntent = Intent(Intent.ACTION_VIEW, appUri).apply {
                        setPackage(bang.targetPackage)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (nativeIntent.resolveActivity(pm) != null) {
                        return nativeIntent
                    }
                }

                // If native scheme failed, try standard search intent targeting the package
                val pkgSearchIntent = Intent(Intent.ACTION_SEARCH).apply {
                    setPackage(bang.targetPackage)
                    putExtra("query", query)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (pkgSearchIntent.resolveActivity(pm) != null) {
                    return pkgSearchIntent
                }
            }
        }

        // Priority 2: Universal Web URL Dispatch
        val webUrl = bang.buildWebUrl(query)
        return Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun getPrefs() = context.getSharedPreferences("PREFERENCES_CUSTOMISATIONS", Context.MODE_PRIVATE)

    suspend fun saveCustomBang(bang: SearchBang) {
        val prefs = getPrefs()
        val currentJson = prefs.getString("custom_bangs_json", null)
            ?: settingsManager.getInitialSettings().customBangsJson
        val currentCustom = parseCustomBangs(currentJson).toMutableList()
        currentCustom.removeAll { it.displayPrefix == bang.displayPrefix || it.prefix.equals(bang.prefix, ignoreCase = true) }
        currentCustom.add(bang.copy(isBuiltIn = false))
        val jsonStr = serializeCustomBangs(currentCustom)
        prefs.edit().putString("custom_bangs_json", jsonStr).commit()
        settingsManager.updateSetting(SettingsManager.CUSTOM_BANGS_JSON, jsonStr)
    }

    suspend fun deleteCustomBang(prefix: String) {
        val normPrefix = prefix.lowercase().trim()
        val prefs = getPrefs()
        val currentJson = prefs.getString("custom_bangs_json", null)
            ?: settingsManager.getInitialSettings().customBangsJson
        val currentCustom = parseCustomBangs(currentJson).toMutableList()
        currentCustom.removeAll { it.displayPrefix == normPrefix || it.prefix.equals(normPrefix, ignoreCase = true) }
        val jsonStr = serializeCustomBangs(currentCustom)
        prefs.edit().putString("custom_bangs_json", jsonStr).commit()
        settingsManager.updateSetting(SettingsManager.CUSTOM_BANGS_JSON, jsonStr)
    }

    suspend fun disableBuiltInBang(prefix: String) {
        val normPrefix = prefix.lowercase().trim()
        val prefs = getPrefs()
        val currentDisabled = (prefs.getStringSet("disabled_web_shortcuts", null)?.toSet()
            ?: settingsManager.getInitialSettings().disabledWebShortcuts).toMutableSet()
        currentDisabled.add(normPrefix)
        prefs.edit().putStringSet("disabled_web_shortcuts", HashSet(currentDisabled)).commit()
        settingsManager.updateSetting(SettingsManager.DISABLED_WEB_SHORTCUTS, currentDisabled)
    }

    suspend fun enableBuiltInBang(prefix: String) {
        val normPrefix = prefix.lowercase().trim()
        val prefs = getPrefs()
        val currentDisabled = (prefs.getStringSet("disabled_web_shortcuts", null)?.toSet()
            ?: settingsManager.getInitialSettings().disabledWebShortcuts).toMutableSet()
        currentDisabled.remove(normPrefix)
        prefs.edit().putStringSet("disabled_web_shortcuts", HashSet(currentDisabled)).commit()
        settingsManager.updateSetting(SettingsManager.DISABLED_WEB_SHORTCUTS, currentDisabled)
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
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
            }
        } catch (e: Exception) {
            e.printStackTrace()
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

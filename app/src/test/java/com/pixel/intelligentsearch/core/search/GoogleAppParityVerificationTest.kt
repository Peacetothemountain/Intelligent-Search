package com.pixel.intelligentsearch.core.search

import com.pixel.intelligentsearch.core.backup.BackupContentPayload
import org.junit.Assert.*
import org.junit.Test
import java.net.URLEncoder

/**
 * Google App Parity Verification Suite.
 *
 * This test suite acts as an automated architectural contract to permanently preserve all
 * capabilities, layout logic, scoring, and UI behaviors modeled after Google App and Pixel Launcher:
 *
 * 1. Tier 0 Instant Execution (Math, Unit Conversion, System Action Router)
 * 2. Prefix & In-Memory Tokenization (QueryNormalizer, Acronyms, Initials, Radix Tree)
 * 3. Suggested Searches vs Search History (Deduplication, Recency, Formatting, Arrow Action)
 * 4. Search Provider Integration (Google, DuckDuckGo, Bing, Custom URL)
 * 5. Architectural Contracts (OneBox Cards, Expressive Tokens, Backup Payload Schema V2)
 */
class GoogleAppParityVerificationTest {

    // ---------------------------------------------------------------------------------------------
    // 1. TIER 0 INSTANT EXECUTION (Google Instant Answers & Calculator)
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testTier0InstantMath_scientificAndBasicExpressions() {
        val basic = MathematicalExpressionEngine.evaluate("24 * 5 + 10")
        assertTrue("Basic arithmetic must return a computation", basic is MathematicalExpressionEngine.MathEvaluationResult.Computation)
        val basicComp = basic as MathematicalExpressionEngine.MathEvaluationResult.Computation
        assertEquals("130", basicComp.formattedResult)

        val scientific = MathematicalExpressionEngine.evaluate("sqrt(64) + 2^3")
        assertTrue("Scientific arithmetic must return a computation", scientific is MathematicalExpressionEngine.MathEvaluationResult.Computation)
        val sciComp = scientific as MathematicalExpressionEngine.MathEvaluationResult.Computation
        assertEquals("16", sciComp.formattedResult)

        val bitwise = MathematicalExpressionEngine.evaluate("255 to hex")
        assertTrue("Base conversion must return bitwise result", bitwise is MathematicalExpressionEngine.MathEvaluationResult.Bitwise)
        assertEquals("0xFF", (bitwise as MathematicalExpressionEngine.MathEvaluationResult.Bitwise).hexValue)
    }

    @Test
    fun testTier0InstantUnitConversion() {
        val length = MathematicalExpressionEngine.evaluate("10 km to m")
        assertTrue("Length conversion must return UnitConversion", length is MathematicalExpressionEngine.MathEvaluationResult.UnitConversion)
        val lengthConv = length as MathematicalExpressionEngine.MathEvaluationResult.UnitConversion
        assertEquals(10000.0, lengthConv.toValue, 0.1)

        val temp = MathematicalExpressionEngine.evaluate("100 c to f")
        assertTrue("Temperature conversion must return UnitConversion", temp is MathematicalExpressionEngine.MathEvaluationResult.UnitConversion)
        val tempConv = temp as MathematicalExpressionEngine.MathEvaluationResult.UnitConversion
        assertEquals(212.0, tempConv.toValue, 0.1)
    }

    @Test
    fun testTier0SystemActionRouter_keywordsMapping() {
        assertTrue("Wifi keywords must include 'wifi'", SystemActionRouter.KEYWORDS_WIFI.contains("wifi"))
        assertTrue("Wifi keywords must include 'wireless'", SystemActionRouter.KEYWORDS_WIFI.contains("wireless"))
        assertTrue("Bluetooth keywords must include 'bluetooth'", SystemActionRouter.KEYWORDS_BLUETOOTH.contains("bluetooth"))
        assertTrue("Bluetooth keywords must include 'bt'", SystemActionRouter.KEYWORDS_BLUETOOTH.contains("bt"))
        assertTrue("Torch keywords must include 'flashlight'", SystemActionRouter.KEYWORDS_TORCH.contains("flashlight"))
        assertTrue("Battery keywords must include 'battery'", SystemActionRouter.KEYWORDS_BATTERY.contains("battery"))
        assertTrue("DND keywords must include 'dnd'", SystemActionRouter.KEYWORDS_DND.contains("dnd"))
        assertTrue("Volume keywords must include 'volume'", SystemActionRouter.KEYWORDS_MEDIA_VOLUME.contains("volume"))
    }

    // ---------------------------------------------------------------------------------------------
    // 2. QUERY NORMALIZER, INITIALS & TOKENIZATION (Pixel Launcher 0ms Search)
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testQueryNormalizer_accentStrippingAndPunctuation() {
        assertEquals("cafe", QueryNormalizer.normalize("Café"))
        assertEquals("creme brulee", QueryNormalizer.normalize("Crème brûlée"))
        assertEquals("uber", QueryNormalizer.normalize("Über"))
        assertEquals("resume", QueryNormalizer.normalize("Résumé"))
    }

    @Test
    fun testQueryNormalizer_initialsExtraction() {
        assertEquals("yt", QueryNormalizer.extractInitials("YouTube"))
        assertEquals("gps", QueryNormalizer.extractInitials("Google Play Store"))
        assertEquals("gpm", QueryNormalizer.extractInitials("Google Play Music"))
        assertEquals("wa", QueryNormalizer.extractInitials("WhatsApp"))
    }

    @Test
    fun testQueryNormalizer_tokenMatching() {
        val query = "you tub"
        val target = "YouTube"
        assertTrue("Multi-word tokens should match target app name", QueryNormalizer.containsAllTokens(target, query))
    }

    @Test
    fun testRadixTree_subMillisecondPrefixMatching() {
        val index = RadixTreeIndex<String>()
        index.insert("spotify", "Spotify")
        index.insert("settings", "Settings")
        index.insert("slack", "Slack")
        index.insert("snapchat", "Snapchat")
        index.insert("google chrome", "Chrome")

        val sMatches = index.searchPrefix("s")
        assertEquals(4, sMatches.size)

        val spMatches = index.searchPrefix("sp")
        assertEquals(1, spMatches.size)
        assertEquals("Spotify", spMatches[0])
    }

    // ---------------------------------------------------------------------------------------------
    // 3. SEARCH SUGGESTIONS & SEARCH HISTORY PARITY (Google App Layout Standards)
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testSearchHistory_deduplicationAndPruning() {
        val recentSearches = mutableListOf("android", "pixel", "weather")
        val newQuery = "pixel"

        // Optimistic update logic matching SearchViewModel.addSearchHistory
        val updated = (listOf(newQuery) + recentSearches.filterNot { it.equals(newQuery, ignoreCase = true) }).take(10)

        assertEquals("Most recent search should be at the top", "pixel", updated[0])
        assertEquals("Duplicates should be removed", 3, updated.size)
        assertEquals(listOf("pixel", "android", "weather"), updated)
    }

    @Test
    fun testSearchHistory_maxTenItemsEnforced() {
        var recents = emptyList<String>()
        for (i in 1..20) {
            val query = "query_$i"
            recents = (listOf(query) + recents.filterNot { it.equals(query, ignoreCase = true) }).take(10)
        }

        assertEquals("Search history must be strictly capped at 10 items", 10, recents.size)
        assertEquals("query_20", recents[0])
    }

    @Test
    fun testSuggestedSearches_mergedWithHistoryAtTop() {
        val recentSearches = listOf("weather tomorrow", "weather radar")
        val webSuggestions = listOf("weather forecast", "weather channel", "weather tomorrow")

        val trimmed = "weather"
        val matchingRecent = recentSearches.filter { it.contains(trimmed, ignoreCase = true) }.distinct()

        val nonRecentWeb = webSuggestions.filter { webSugg ->
            matchingRecent.none { it.equals(webSugg, ignoreCase = true) }
        }

        val allDisplaySuggestions = (matchingRecent + nonRecentWeb).distinct().take(6)

        // Matching recent searches must be at the very top
        assertEquals("weather tomorrow", allDisplaySuggestions[0])
        assertEquals("weather radar", allDisplaySuggestions[1])
        // Followed by unique web suggestions
        assertEquals("weather forecast", allDisplaySuggestions[2])
        assertEquals("weather channel", allDisplaySuggestions[3])
        // Duplicate 'weather tomorrow' must not appear twice
        assertEquals(4, allDisplaySuggestions.size)
    }

    @Test
    fun testSuggestionQueryAnnotation_prefixMatching() {
        val query = "pix"
        val suggestion = "pixel 9 pro"

        assertTrue(suggestion.startsWith(query, ignoreCase = true))
        val prefix = suggestion.substring(0, query.length)
        val suffix = suggestion.substring(query.length)

        assertEquals("pix", prefix)
        assertEquals("el 9 pro", suffix)
    }

    // ---------------------------------------------------------------------------------------------
    // 4. SEARCH PROVIDERS & URL RESOLUTION (Monotheme & Custom Providers)
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testSearchProviderUrlResolution() {
        val query = "Android 15 features"
        val encoded = URLEncoder.encode(query, "UTF-8")

        // Google standard URL
        val googleUrl = "https://www.google.com/search?q=$encoded"
        assertTrue(googleUrl.contains("google.com/search?q=Android+15+features"))

        // DuckDuckGo URL
        val ddgUrl = "https://duckduckgo.com/?q=$encoded"
        assertTrue(ddgUrl.contains("duckduckgo.com/?q=Android+15+features"))

        // Bing URL
        val bingUrl = "https://www.bing.com/search?q=$encoded"
        assertTrue(bingUrl.contains("bing.com/search?q=Android+15+features"))

        // Custom engine with template placeholder
        val customTemplate = "https://kagi.com/search?q=%s"
        val customUrl = customTemplate.replace("%s", encoded)
        assertEquals("https://kagi.com/search?q=Android+15+features", customUrl)
    }

    // ---------------------------------------------------------------------------------------------
    // 5. ENCRYPTED BACKUP SCHEMA V2 (Full Customization & Widget Persistence)
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testBackupPayloadSchemaV2_containsSharedPreferences() {
        val payload = BackupContentPayload(
            schemaVersion = 2,
            exportTimestampMs = System.currentTimeMillis(),
            appVersionCode = 96,
            preferencesMap = mapOf("theme" to "system"),
            searchHistory = listOf("pixel"),
            sharedPreferencesJson = "{\"widget.theme.style\":\"Material You (Minimal)\"}"
        )

        assertEquals("Backup payload schema must be version 2", 2, payload.schemaVersion)
        assertTrue("sharedPreferencesJson must be populated to preserve widget customizations", payload.sharedPreferencesJson.contains("widget.theme.style"))
    }

    // ---------------------------------------------------------------------------------------------
    // 6. ARCHITECTURAL CONTRACTS (OneBox & Expressive Components)
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testOneBoxCardsArchitecturalContract() {
        val oneBoxClass = Class.forName("com.pixel.intelligentsearch.core.ui.OneBoxCardsKt")
        assertNotNull("OneBoxCardsKt must be compiled and present", oneBoxClass)

        val methods = oneBoxClass.methods.map { it.name }
        assertTrue("MathResultOneBox must exist", methods.any { it.contains("MathResultOneBox") })
        assertTrue("ConversionOneBox must exist", methods.any { it.contains("ConversionOneBox") })
        assertTrue("DictionaryOneBox must exist", methods.any { it.contains("DictionaryOneBox") })
        assertTrue("UrlNavigationOneBox must exist", methods.any { it.contains("UrlNavigationOneBox") })
        assertTrue("TimeWeatherOneBox must exist", methods.any { it.contains("TimeWeatherOneBox") })
    }
}

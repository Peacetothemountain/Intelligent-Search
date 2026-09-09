package com.pixel.intelligentsearch.core.weighting

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import org.json.JSONArray
import org.json.JSONObject

@Immutable
@Serializable
enum class SearchSectionType(
    val title: String,
    val subtitle: String,
    val defaultOrder: Int,
    val defaultWeight: Float,
    val defaultMaxResults: Int
) {
    SYSTEM_TOGGLES("System Toggles", "Hardware switches (Torch, Wi-Fi, BT)", 0, 1.5f, 1),
    CALCULATIONS("Calculations & Conversions", "Inline math evaluation and currency/units", 1, 1.4f, 2),
    APPS("Applications", "Installed device applications and system tools", 2, 1.3f, 8),
    SHORTCUTS("App Shortcuts", "Deep shortcuts and actions provided by apps", 3, 1.1f, 6),
    CONTACTS("Contacts", "Phone book and communication contacts", 4, 1.0f, 5),
    FILES("Files & Documents", "On-device documents, images, audio, and downloads", 5, 0.9f, 5),
    WEB("Web Suggestions", "Real-time web search queries and suggestions", 6, 0.8f, 5),
    CALENDAR("Calendar Events", "Upcoming schedule events and agenda items", 7, 0.7f, 4);

    companion object {
        fun fromString(value: String): SearchSectionType? {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        }
    }
}

@Immutable
@Serializable
data class SearchSectionConfig(
    val sectionType: SearchSectionType,
    val isEnabled: Boolean = true,
    val weight: Float = 1.0f,
    val maxResults: Int = 5,
    val orderIndex: Int = 0
) {
    fun computeScore(baseMatchScore: Float): Float {
        return (baseMatchScore * weight).coerceAtLeast(0f)
    }
}

object SearchWeightingDefaults {
    fun createDefaultConfigs(): List<SearchSectionConfig> {
        return SearchSectionType.entries.map { type ->
            SearchSectionConfig(
                sectionType = type,
                isEnabled = true,
                weight = type.defaultWeight,
                maxResults = type.defaultMaxResults,
                orderIndex = type.defaultOrder
            )
        }.sortedBy { it.orderIndex }
    }

    fun parseConfigs(jsonString: String): List<SearchSectionConfig> {
        if (jsonString.isBlank()) return createDefaultConfigs()
        return try {
            val array = JSONArray(jsonString)
            val parsedMap = mutableMapOf<SearchSectionType, SearchSectionConfig>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val typeStr = obj.optString("type")
                val type = SearchSectionType.fromString(typeStr) ?: continue
                val config = SearchSectionConfig(
                    sectionType = type,
                    isEnabled = obj.optBoolean("isEnabled", true),
                    weight = obj.optDouble("weight", type.defaultWeight.toDouble()).toFloat(),
                    maxResults = obj.optInt("maxResults", type.defaultMaxResults),
                    orderIndex = obj.optInt("orderIndex", type.defaultOrder)
                )
                parsedMap[type] = config
            }

            // Fill missing types
            val completeList = SearchSectionType.entries.map { type ->
                parsedMap[type] ?: SearchSectionConfig(
                    sectionType = type,
                    isEnabled = true,
                    weight = type.defaultWeight,
                    maxResults = type.defaultMaxResults,
                    orderIndex = type.defaultOrder
                )
            }
            completeList.sortedBy { it.orderIndex }
        } catch (e: Exception) {
            createDefaultConfigs()
        }
    }

    fun serializeConfigs(configs: List<SearchSectionConfig>): String {
        val array = JSONArray()
        for (cfg in configs.sortedBy { it.orderIndex }) {
            val obj = JSONObject().apply {
                put("type", cfg.sectionType.name)
                put("isEnabled", cfg.isEnabled)
                put("weight", cfg.weight.toDouble())
                put("maxResults", cfg.maxResults)
                put("orderIndex", cfg.orderIndex)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun reorderConfigs(configs: List<SearchSectionConfig>, fromIndex: Int, toIndex: Int): List<SearchSectionConfig> {
        if (fromIndex == toIndex || fromIndex !in configs.indices || toIndex !in configs.indices) {
            return configs
        }
        val mutable = configs.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        return mutable.mapIndexed { index, cfg -> cfg.copy(orderIndex = index) }
    }
}

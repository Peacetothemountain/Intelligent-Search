package com.pixel.intelligentsearch.core.data
import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

class RecentSearchManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("recent_searches_prefs", Context.MODE_PRIVATE)
    private val MAX_HISTORY = 10

    fun addSearch(query: String) {
        if (query.isBlank()) return
        val current = getRecentSearches().toMutableList()
        current.remove(query)
        current.add(0, query)
        if (current.size > MAX_HISTORY) {
            current.removeAt(current.size - 1)
        }
        val jsonArray = JSONArray()
        current.forEach { jsonArray.put(it) }
        prefs.edit().putString("history", jsonArray.toString()).apply()
    }

    fun getRecentSearches(): List<String> {
        val historyString = prefs.getString("history", "[]") ?: "[]"
        val jsonArray = JSONArray(historyString)
        val list = mutableListOf<String>()
        for (i in 0 until jsonArray.length()) {
            list.add(jsonArray.getString(i))
        }
        return list
    }

    fun removeSearch(query: String) {
        if (query.isBlank()) return
        val current = getRecentSearches().toMutableList()
        if (current.remove(query)) {
            val jsonArray = JSONArray()
            current.forEach { jsonArray.put(it) }
            prefs.edit().putString("history", jsonArray.toString()).apply()
        }
    }

    fun clearHistory() {
        prefs.edit().remove("history").apply()
    }
}

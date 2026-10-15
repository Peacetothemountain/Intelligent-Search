package com.pixel.intelligentsearch.core.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class HistoryDao {
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC")
    abstract fun getSearchHistoryFlow(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT :limit")
    abstract suspend fun getSearchHistory(limit: Int = 25): List<HistoryEntity>

    @Upsert
    abstract suspend fun insertSearch(search: HistoryEntity)

    @Upsert
    abstract suspend fun insertSearches(searches: List<HistoryEntity>)

    /** Deletes [query] in any letter case, matching how the history list de-duplicates. */
    @Query("DELETE FROM search_history WHERE query = :query COLLATE NOCASE")
    abstract suspend fun deleteByQuery(query: String)

    @Query("DELETE FROM search_history")
    abstract suspend fun clearHistory()

    @Query("""
        DELETE FROM search_history 
        WHERE query NOT IN (
            SELECT query FROM search_history 
            ORDER BY timestamp DESC, query ASC 
            LIMIT :limit
        )
    """)
    abstract suspend fun pruneHistory(limit: Int)

    @Transaction
    open suspend fun recordAndPrune(query: String, timestamp: Long, limit: Int = 10) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val safeLimit = limit.coerceIn(1, 1000)
        // "Cats" after "cats" replaces it instead of keeping both spellings.
        deleteByQuery(trimmed)
        insertSearch(HistoryEntity(trimmed, timestamp))
        pruneHistory(safeLimit)
    }
}

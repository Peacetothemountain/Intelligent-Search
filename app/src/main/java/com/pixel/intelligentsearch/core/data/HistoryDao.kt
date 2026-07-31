package com.pixel.intelligentsearch.core.data
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC")
    fun getSearchHistoryFlow(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM search_history ORDER BY timestamp DESC")
    suspend fun getSearchHistory(): List<HistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(search: HistoryEntity)

    @Delete
    suspend fun deleteSearch(search: HistoryEntity)

    @Query("DELETE FROM search_history")
    suspend fun clearHistory()

    @Query("SELECT COUNT(*) FROM search_history")
    suspend fun getCount(): Int

    @Query("DELETE FROM search_history WHERE query NOT IN (SELECT query FROM search_history ORDER BY timestamp DESC LIMIT :limit)")
    suspend fun pruneHistory(limit: Int)
}

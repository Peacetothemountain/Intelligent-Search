package com.pixel.intelligentsearch.core.data
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "search_history",
    indices = [Index(value = ["timestamp"])]
)
data class HistoryEntity(
    @PrimaryKey val query: String,
    val timestamp: Long
)

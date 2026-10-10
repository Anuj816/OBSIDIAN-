package com.anuj.obsidian.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_history")
data class SearchHistoryItem(
    @PrimaryKey
    val query: String,
    val timestamp: Long
)

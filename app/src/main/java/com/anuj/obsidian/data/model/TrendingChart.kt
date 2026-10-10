package com.anuj.obsidian.data.model

data class TrendingChart(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconEmoji: String,
    val badgeColorHex: Long = 0xFF1DB954,
    val tracks: List<SearchResultItem>
)

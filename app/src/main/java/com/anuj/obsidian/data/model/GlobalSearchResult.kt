package com.anuj.obsidian.data.model

import androidx.compose.runtime.Immutable

enum class GlobalSearchFilter(val label: String) {
    ALL("All"),
    TRACKS("Tracks"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    GENRES("Genres")
}

@Immutable
data class GlobalSearchResultsState(
    val query: String = "",
    val selectedFilter: GlobalSearchFilter = GlobalSearchFilter.ALL,
    val matchingTracks: List<DownloadedTrack> = emptyList(),
    val matchingAlbums: List<AlbumInfo> = emptyList(),
    val matchingArtists: List<ArtistInfo> = emptyList(),
    val matchingGenres: List<GenreInfo> = emptyList(),
    val totalCount: Int = 0,
    val isLoading: Boolean = false
)

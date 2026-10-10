package com.anuj.obsidian.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.anuj.obsidian.ui.animation.bounceClickable
import com.anuj.obsidian.ui.animation.circularBounceClickable
import com.anuj.obsidian.ui.components.HomeChartSkeleton
import com.anuj.obsidian.ui.components.TrackArtworkPlaceholder
import com.anuj.obsidian.ui.components.TrackListSkeleton
import com.anuj.obsidian.ui.components.ObsidianEmptyState
import com.anuj.obsidian.ui.components.ObsidianLoadingSpinner
import com.anuj.obsidian.ui.components.ObsidianPullToRefreshIndicator
import com.anuj.obsidian.ui.components.hasRealArtwork
import com.anuj.obsidian.data.model.DownloadedTrack
import com.anuj.obsidian.ui.theme.AppThemePalette
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.anuj.obsidian.data.model.DownloadStatus
import com.anuj.obsidian.data.model.DownloadTask
import com.anuj.obsidian.data.model.SearchResultItem
import com.anuj.obsidian.data.model.TrendingChart
import com.anuj.obsidian.ui.ObsidianViewModel
import com.anuj.obsidian.ui.animation.pressSpring
import com.anuj.obsidian.ui.components.FormatChip
import com.anuj.obsidian.ui.components.QualityBadge
import com.anuj.obsidian.ui.theme.FlacHiResGold
import com.anuj.obsidian.ui.theme.ObsidianTheme
import java.util.Locale

enum class HomeSearchScope(val label: String) {
    ALL("All"),
    DOWNLOADED("Downloaded"),
    ARTIST("Artist"),
    ALBUM("Album")
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ObsidianViewModel,
    modifier: Modifier = Modifier
) {
    val appColors = ObsidianTheme.colors
    val query by viewModel.urlOrSearchQuery.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val downloadTasks by viewModel.downloadTasks.collectAsStateWithLifecycle()
    val activeTasks by viewModel.activeTasks.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val selectedChartId by viewModel.selectedChartId.collectAsStateWithLifecycle()
    val trendingCharts by viewModel.trendingCharts.collectAsStateWithLifecycle()
    val isChartsLoading by viewModel.isChartsLoading.collectAsStateWithLifecycle()
    val isRefreshingHome by viewModel.isRefreshingHome.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val downloadedTracks by viewModel.tracks.collectAsStateWithLifecycle()
    val dailyMixTracks by viewModel.dailyMixSmartPlaylist.collectAsStateWithLifecycle()
    val onRepeatTracks by viewModel.onRepeatSmartPlaylist.collectAsStateWithLifecycle()
    val forgottenFavoritesTracks by viewModel.forgottenFavoritesSmartPlaylist.collectAsStateWithLifecycle()
    val featuredThisWeek by viewModel.featuredThisWeek.collectAsStateWithLifecycle()
    val stayIndie by viewModel.stayIndie.collectAsStateWithLifecycle()
    val onlineSearchSuggestions by viewModel.onlineSearchSuggestions.collectAsStateWithLifecycle()
    var selectedSearchScope by remember { mutableStateOf(HomeSearchScope.ALL) }

    val downloadedTitlesAndArtists = remember(downloadedTracks) {
        downloadedTracks.map { "${it.title.lowercase()}###${it.artist.lowercase()}" }.toSet()
    }

    val scopedSearchResults = remember(searchResults, selectedSearchScope, query, downloadedTitlesAndArtists) {
        when (selectedSearchScope) {
            HomeSearchScope.ALL -> searchResults
            HomeSearchScope.DOWNLOADED -> searchResults.filter { item ->
                downloadedTitlesAndArtists.contains("${item.title.lowercase()}###${item.artist.lowercase()}") ||
                downloadedTracks.any { dt -> dt.title.equals(item.title, ignoreCase = true) }
            }
            HomeSearchScope.ARTIST -> searchResults.filter { item ->
                if (query.isBlank()) true else item.artist.contains(query.trim(), ignoreCase = true)
            }
            HomeSearchScope.ALBUM -> searchResults.filter { item ->
                if (query.isBlank()) true else item.album.contains(query.trim(), ignoreCase = true)
            }
        }
    }

    val context = LocalContext.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val currentChart = trendingCharts.find { it.id == selectedChartId } ?: trendingCharts.firstOrNull()

    val pullToRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        state = pullToRefreshState,
        isRefreshing = isRefreshingHome,
        onRefresh = { viewModel.refreshHomeData() },
        indicator = {
            ObsidianPullToRefreshIndicator(
                state = pullToRefreshState,
                isRefreshing = isRefreshingHome
            )
        },
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 4.dp, bottom = 180.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (settings.offlineMode) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = appColors.primary.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, appColors.primary.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = "Offline Mode",
                                tint = appColors.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Offline Mode Active",
                                    color = appColors.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Playing & searching local Room DB and device storage only",
                                    color = appColors.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Button(
                            onClick = { viewModel.setOfflineMode(false) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = appColors.primary,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Disable", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Search / Paste URL Input Field
        item {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.onQueryChange(it) },
                    placeholder = {
                        Text(
                            text = "Search track, artist or paste Spotify URL...",
                            color = appColors.textMuted,
                            fontSize = 12.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = appColors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (query.isNotBlank()) {
                                IconButton(
                                    onClick = { viewModel.clearSearch() },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear input",
                                        tint = appColors.textSecondary,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    viewModel.showSongRecognition(true)
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("home_song_recognition_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = "Recognize Playing Music",
                                    tint = FlacHiResGold,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clip = try {
                                        if (clipboard?.hasPrimaryClip() == true && (clipboard.primaryClip?.itemCount ?: 0) > 0) {
                                            clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                                        } else null
                                    } catch (e: Exception) {
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "HomeScreen.HomeScreen - attempt")
             null }
                                    if (!clip.isNullOrBlank()) {
                                        viewModel.onQueryChange(clip)
                                        viewModel.executeSearch()
                                    }
                                },
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .size(34.dp)
                                    .testTag("paste_clipboard_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste from clipboard",
                                    tint = appColors.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { 
                        keyboardController?.hide()
                        focusManager.clearFocus(force = true)
                        viewModel.executeSearch() 
                    }),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = appColors.primary,
                        unfocusedBorderColor = appColors.surfaceBorder,
                        focusedTextColor = appColors.textPrimary,
                        unfocusedTextColor = appColors.textPrimary,
                        focusedContainerColor = appColors.surfaceCard,
                        unfocusedContainerColor = appColors.surfaceCard
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("url_search_input")
                )

                // Search Scoping Filter Chips
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(HomeSearchScope.entries, key = { it.name }) { scope ->
                        val isSelected = selectedSearchScope == scope
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedSearchScope = scope },
                            leadingIcon = {
                                val icon = when (scope) {
                                    HomeSearchScope.ALL -> Icons.Default.Search
                                    HomeSearchScope.DOWNLOADED -> Icons.Default.Download
                                    HomeSearchScope.ARTIST -> Icons.Default.Person
                                    HomeSearchScope.ALBUM -> Icons.Default.Album
                                }
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.Black else appColors.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            label = {
                                val countSuffix = if (scope == HomeSearchScope.DOWNLOADED && downloadedTracks.isNotEmpty()) " (${downloadedTracks.size})" else ""
                                Text(
                                    text = "${scope.label}$countSuffix",
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = appColors.primary,
                                selectedLabelColor = Color.Black,
                                containerColor = appColors.surfaceCard,
                                labelColor = appColors.textSecondary
                            ),
                            border = BorderStroke(1.dp, if (isSelected) appColors.primary else appColors.surfaceBorder)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { 
                            keyboardController?.hide()
                            focusManager.clearFocus(force = true)
                            viewModel.executeSearch() 
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = appColors.primary,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("scrape_search_btn")
                    ) {
                        if (isSearching) {
                            ObsidianLoadingSpinner(
                                size = 16.dp,
                                color = Color.Black,
                                strokeWidth = 2.dp,
                                trackColor = Color.Black.copy(alpha = 0.2f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Extracting Master...", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(imageVector = Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Search / Parse", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (searchResults.isNotEmpty()) {
                        Button(
                            onClick = {
                                viewModel.queueAlbum(scopedSearchResults)
                                android.widget.Toast.makeText(context, "Added ${scopedSearchResults.size} track(s) to Queue", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = appColors.surfaceCard,
                                contentColor = appColors.primary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(42.dp)
                                .testTag("queue_all_btn")
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Queue (${scopedSearchResults.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                scopedSearchResults.forEach { viewModel.downloadSingle(it) }
                                android.widget.Toast.makeText(context, "Started ${scopedSearchResults.size} download(s)", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = appColors.surfaceElevated,
                                contentColor = appColors.textPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(42.dp)
                                .testTag("download_all_btn")
                        ) {
                            Icon(imageVector = Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Download (${scopedSearchResults.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Search Results Section
        if (isSearching) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "SEARCHING REAL-TIME...",
                        color = appColors.primary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    TrackListSkeleton(count = 5)
                }
            }
        } else if (query.isNotBlank() && scopedSearchResults.isEmpty()) {
            item {
                ObsidianEmptyState(
                    icon = Icons.Default.SearchOff,
                    title = "No Tracks Found",
                    subtitle = "No results found for \"$query\". Try checking the spelling, artist name, or paste a Spotify link.",
                    actionLabel = "Clear Search",
                    actionIcon = Icons.Default.Clear,
                    onActionClick = { viewModel.clearSearch() },
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        } else if (scopedSearchResults.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedSearchScope == HomeSearchScope.ALL) "SEARCH RESULTS (${scopedSearchResults.size})" else "${selectedSearchScope.label.uppercase()} RESULTS (${scopedSearchResults.size})",
                        color = appColors.textMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )

                    Text(
                        text = "Clear",
                        color = appColors.primary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable { viewModel.clearSearch() }
                            .padding(4.dp)
                    )
                }
            }

            items(
                items = scopedSearchResults,
                key = { it.id },
                contentType = { "search_result" }
            ) { item ->
                val isDownloaded = remember(item, downloadedTracks) {
                    viewModel.findDownloadedTrack(item) != null
                }
                SearchResultCard(
                    item = item,
                    isDownloaded = isDownloaded,
                    modifier = Modifier.animateItem(),
                    onPlay = { 
                        keyboardController?.hide()
                        focusManager.clearFocus(force = true)
                        viewModel.playSearchResult(item, openFullPlayer = true) 
                    },
                    onAddToQueue = {
                        viewModel.addSearchResultToQueue(item)
                        android.widget.Toast.makeText(context, "Added '${item.title}' to Queue", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onStartArtistRadio = {
                        viewModel.startArtistRadio(item.artist, item.title)
                        android.widget.Toast.makeText(context, "Starting Artist Radio for ${item.artist}...", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onDownload = { viewModel.downloadSingle(item) }
                )
            }
        }

        
        // Recent Searches
        if (searchResults.isEmpty() && !isSearching && searchHistory.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = appColors.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "RECENT SEARCHES",
                            color = appColors.textMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                    Text(
                        text = "Clear All",
                        color = appColors.textSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable { viewModel.clearSearchHistory() }
                            .padding(4.dp)
                    )
                }
            }
            
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    items(searchHistory, key = { it.query }) { historyItem ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = appColors.surfaceCard,
                            border = androidx.compose.foundation.BorderStroke(1.dp, appColors.surfaceBorder),
                            modifier = Modifier.clickable {
                                viewModel.onQueryChange(historyItem.query)
                                viewModel.executeSearch()
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = historyItem.query,
                                    color = appColors.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = appColors.textMuted,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { viewModel.removeSearchHistory(historyItem.query) }
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Discovery content (when not searching)
        if (searchResults.isEmpty() && !isSearching) {
            // Featured This Week (Picked dynamically from online lists)
            item {
                CategoryArtworkCarousel(
                    title = "Featured This Week",
                    subtitle = "Studio Master 24-bit releases & global highlights",
                    badgeText = "24-BIT STUDIO",
                    items = featuredThisWeek,
                    appColors = appColors,
                    onPlayItem = { item ->
                        keyboardController?.hide()
                        focusManager.clearFocus(force = true)
                        viewModel.playSearchResult(item, openFullPlayer = true)
                    },
                    onDownloadItem = { item ->
                        viewModel.downloadSingle(item)
                    }
                )
            }

            // Stay Indie (Picked dynamically from online lists)
            item {
                CategoryArtworkCarousel(
                    title = "Stay Indie",
                    subtitle = "Curated indie, alternative & ethereal sounds",
                    badgeText = "LOSSLESS",
                    items = stayIndie,
                    appColors = appColors,
                    onPlayItem = { item ->
                        keyboardController?.hide()
                        focusManager.clearFocus(force = true)
                        viewModel.playSearchResult(item, openFullPlayer = true)
                    },
                    onDownloadItem = { item ->
                        viewModel.downloadSingle(item)
                    }
                )
            }
        }
        
        // If search results are empty & not currently searching, display FOR YOU section
        if (searchResults.isEmpty() && !isSearching) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = null,
                                tint = appColors.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Made For You",
                                color = appColors.textPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Daily Mix
                    ForYouSubsectionRow(
                        title = "Daily Mix",
                        subtitle = "Updated daily based on your listening history",
                        tracks = dailyMixTracks,
                        emptyText = "Keep listening to build your Daily Mix!",
                        appColors = appColors,
                        onTrackClick = { track, list ->
                            viewModel.playTrackFromList(track, list)
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // On Repeat
                    ForYouSubsectionRow(
                        title = "On Repeat",
                        subtitle = "Most played tracks in the last 7 days",
                        tracks = onRepeatTracks,
                        emptyText = "Tracks you play often will appear here.",
                        appColors = appColors,
                        onTrackClick = { track, list ->
                            viewModel.playTrackFromList(track, list)
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Forgotten Favorites
                    ForYouSubsectionRow(
                        title = "Forgotten Favorites",
                        subtitle = "High play count, unplayed in 60+ days",
                        tracks = forgottenFavoritesTracks,
                        emptyText = "Rediscover old favorites as your history grows.",
                        appColors = appColors,
                        onTrackClick = { track, list ->
                            viewModel.playTrackFromList(track, list)
                        }
                    )
                }
            }
        }

        // If search results are empty & not currently searching, display TRENDING CHARTS section
        if (searchResults.isEmpty() && !isSearching) {
            if (isChartsLoading && trendingCharts.isEmpty()) {
                item {
                    HomeChartSkeleton()
                }
            } else {
                // Trending Charts Header & Category Chips
                item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = appColors.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "TRENDING CHARTS & TOP HITS",
                                color = appColors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }

                        FormatChip(
                            text = "24-BIT STUDIO",
                            color = appColors.secondary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Chart Categories Horizontal Scroll
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(
                            items = trendingCharts,
                            key = { it.id },
                            contentType = { "trending_chart" }
                        ) { chart ->
                            val isSelected = chart.id == selectedChartId
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) appColors.primary.copy(alpha = 0.2f) else appColors.surfaceCard,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) appColors.primary else appColors.surfaceBorder
                                ),
                                modifier = Modifier.bounceClickable(scaleDown = 0.94f) { viewModel.selectChart(chart.id) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = chart.iconEmoji, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = chart.title,
                                        color = if (isSelected) appColors.textPrimary else appColors.textSecondary,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Selected Chart Header & Action Row
                    if (currentChart != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = appColors.surfaceCard.copy(alpha = 0.6f),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, appColors.surfaceBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentChart.title,
                                        color = appColors.textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = currentChart.subtitle,
                                        color = appColors.textMuted,
                                        fontSize = 10.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Button(
                                    onClick = { viewModel.downloadTrendingChart(currentChart) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(currentChart.badgeColorHex).copy(alpha = 0.25f),
                                        contentColor = Color(currentChart.badgeColorHex)
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Get Chart (${currentChart.tracks.size})",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Trending Chart Tracks
            if (currentChart != null) {
                itemsIndexed(
                    items = currentChart.tracks,
                    key = { _, item -> item.id },
                    contentType = { _, _ -> "chart_track" }
                ) { index, item ->
                    val isDownloaded = remember(item, downloadedTracks) {
                        viewModel.findDownloadedTrack(item) != null
                    }
                    SearchResultCard(
                        item = item,
                        rank = index + 1,
                        isDownloaded = isDownloaded,
                        modifier = Modifier.animateItem(
                            placementSpec = com.anuj.obsidian.ui.animation.MotionSpec.SpringSmoothIntOffset
                        ),
                        onPlay = { 
                            keyboardController?.hide()
                            focusManager.clearFocus(force = true)
                            viewModel.playSearchResult(item, openFullPlayer = true) 
                        },
                        onAddToQueue = {
                            viewModel.addSearchResultToQueue(item)
                            android.widget.Toast.makeText(context, "Added '${item.title}' to Queue", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        onStartArtistRadio = {
                            viewModel.startArtistRadio(item.artist, item.title)
                            android.widget.Toast.makeText(context, "Starting Artist Radio for ${item.artist}...", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        onDownload = { viewModel.downloadSingle(item) }
                    )
                }
            }

            // Quick Search Suggestions below
            item {
                Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                    Text(
                        text = "POPULAR SEARCH QUERIES",
                        color = appColors.textMuted,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        onlineSearchSuggestions.forEach { suggestion ->
                            Surface(
                                color = appColors.surfaceCard,
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, appColors.surfaceBorder),
                                modifier = Modifier.clickable {
                                    viewModel.onQueryChange(suggestion)
                                    viewModel.executeSearch()
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = appColors.primary,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = suggestion,
                                        color = appColors.textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

        // Active Downloads Queue Section (Side-by-Side Parallel Loaders)
        if (downloadTasks.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "DOWNLOAD QUEUE (${activeTasks.size} active)",
                            color = appColors.textMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        if (activeTasks.size > 1) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = appColors.secondary.copy(alpha = 0.18f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, appColors.secondary.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "PARALLEL",
                                    color = appColors.secondary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    if (downloadTasks.any { it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.COMPLETED_WITHOUT_TAGS }) {
                        Text(
                            text = "Clear Done",
                            color = appColors.primary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clickable { viewModel.clearCompletedDownloads() }
                                .testTag("clear_completed_btn")
                        )
                    }
                }
            }

            if (downloadTasks.size >= 2) {
                // Render downloads in side-by-side pairs
                val taskPairs = downloadTasks.chunked(2)
                items(
                    items = taskPairs,
                    key = { pair -> pair.joinToString("_") { it.taskId.toString() } }
                ) { pair ->
                    Row(
                        modifier = Modifier.animateItem().fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            DownloadTaskCard(
                                task = pair[0],
                                onCancel = { viewModel.cancelDownload(pair[0].taskId) },
                                onRetryTag = { viewModel.retryTagging(it) }
                            )
                        }
                        if (pair.size > 1) {
                            Box(modifier = Modifier.weight(1f)) {
                                DownloadTaskCard(
                                    task = pair[1],
                                    onCancel = { viewModel.cancelDownload(pair[1].taskId) },
                                    onRetryTag = { viewModel.retryTagging(it) }
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                items(
                    items = downloadTasks,
                    key = { it.taskId }
                ) { task ->
                    Box(modifier = Modifier.animateItem().fillMaxWidth()) {
                        DownloadTaskCard(
                            task = task,
                            onCancel = { viewModel.cancelDownload(task.taskId) },
                            onRetryTag = { viewModel.retryTagging(it) }
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(160.dp))
        }
    }
    }
}

@Composable
fun SearchResultCard(
    item: SearchResultItem,
    rank: Int? = null,
    isDownloaded: Boolean = false,
    modifier: Modifier = Modifier,
    onPlay: () -> Unit,
    onAddToQueue: (() -> Unit)? = null,
    onStartArtistRadio: (() -> Unit)? = null,
    onDownload: () -> Unit
) {
    val appColors = ObsidianTheme.colors
    Surface(
        onClick = onPlay,
        shape = RoundedCornerShape(12.dp),
        color = appColors.surfaceCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, appColors.surfaceBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("track_card_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Optional Rank Badge
            if (rank != null) {
                val rankColor = when (rank) {
                    1 -> appColors.secondary
                    2 -> Color(0xFFC0C0C0)
                    3 -> Color(0xFFCD7F32)
                    else -> appColors.textMuted
                }
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .padding(end = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$rank",
                        color = rankColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            // Cover Art Box
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(appColors.surfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                val context = LocalContext.current
                if (hasRealArtwork(item.coverUrl)) {
                    AsyncImage(
                        model = remember(item.coverUrl) {
                            ImageRequest.Builder(context)
                                .data(item.coverUrl)
                                .crossfade(180)
                                .size(96, 96)
                                .build()
                        },
                        contentDescription = "Cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    TrackArtworkPlaceholder(
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 8.dp,
                        iconSize = 20.dp
                    )
                }
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = appColors.primary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Metadata Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.title,
                    color = appColors.textPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.artist} • ${item.album}",
                    color = appColors.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    QualityBadge(
                        bitDepth = item.bitDepth,
                        sampleRate = item.sampleRate,
                        compact = true
                    )
                    if (isDownloaded) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(appColors.secondary.copy(alpha = 0.18f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "Downloaded",
                                color = appColors.secondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action Buttons (Collision-Free, Exact Bounds)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onAddToQueue != null) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .circularBounceClickable(scaleDown = 0.88f) { onAddToQueue() }
                            .background(appColors.surfaceBorder, CircleShape)
                            .testTag("queue_track_${item.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Add to Queue",
                            tint = appColors.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (onStartArtistRadio != null) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .circularBounceClickable(scaleDown = 0.88f) { onStartArtistRadio() }
                            .background(appColors.surfaceBorder, CircleShape)
                            .testTag("radio_track_${item.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radio,
                            contentDescription = "Artist Radio",
                            tint = appColors.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .circularBounceClickable(scaleDown = 0.88f) { onPlay() }
                        .background(appColors.primary.copy(alpha = 0.18f), CircleShape)
                        .testTag("play_track_${item.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Now",
                        tint = appColors.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .circularBounceClickable(scaleDown = 0.88f) { onDownload() }
                        .background(if (isDownloaded) appColors.secondary.copy(alpha = 0.22f) else appColors.surfaceBorder, CircleShape)
                        .testTag("download_track_${item.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                        contentDescription = if (isDownloaded) "Downloaded FLAC (Play)" else "Download FLAC",
                        tint = appColors.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadTaskCard(
    task: DownloadTask,
    onCancel: () -> Unit,
    onRetryTag: ((Long) -> Unit)? = null
) {
    val appColors = ObsidianTheme.colors
    val isDone = task.status == DownloadStatus.COMPLETED || task.status == DownloadStatus.COMPLETED_WITHOUT_TAGS
    val isTagged = task.status == DownloadStatus.COMPLETED
    val isWithoutTags = task.status == DownloadStatus.COMPLETED_WITHOUT_TAGS

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = appColors.surfaceCard,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when {
                isTagged -> appColors.primary.copy(alpha = 0.4f)
                isWithoutTags -> Color(0xFFFFA726).copy(alpha = 0.5f)
                else -> appColors.surfaceBorder
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasRealArtwork(task.albumArtUrl)) {
                        val context = LocalContext.current
                        AsyncImage(
                            model = remember(task.albumArtUrl) {
                                ImageRequest.Builder(context)
                                    .data(task.albumArtUrl)
                                    .crossfade(true)
                                    .size(96, 96)
                                    .build()
                            },
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                    } else {
                        TrackArtworkPlaceholder(
                            modifier = Modifier.size(40.dp),
                            cornerRadius = 6.dp,
                            iconSize = 16.dp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = task.title,
                            color = appColors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${task.artist} • ${task.quality}",
                            color = appColors.textSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (isTagged) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Done (Tagged)",
                        tint = appColors.primary,
                        modifier = Modifier.size(22.dp)
                    )
                } else if (isWithoutTags) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Saved Without Tags",
                        tint = Color(0xFFFFA726),
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = appColors.textMuted,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { task.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = when {
                    isTagged -> appColors.primary
                    isWithoutTags -> Color(0xFFFFA726)
                    else -> appColors.secondary
                },
                trackColor = appColors.surfaceBorder,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusText = when (task.status) {
                    DownloadStatus.QUEUED -> "Queued..."
                    DownloadStatus.FETCHING_METADATA -> "Resolving Album Art & Lyrics..."
                    DownloadStatus.CONNECTING_PROVIDER -> "Connecting to ${task.provider}..."
                    DownloadStatus.DOWNLOADING_STREAM -> "Downloading FLAC (${(task.progress * 100).toInt()}%)"
                    DownloadStatus.EMBEDDING_TAGS -> "Embedding artwork & lyrics..."
                    DownloadStatus.COMPLETED -> "Saved to Music/ObsidianObsidian/ (Tagged)"
                    DownloadStatus.COMPLETED_WITHOUT_TAGS -> task.errorMessage ?: "Audio saved, but metadata/artwork embedding failed"
                    DownloadStatus.FAILED -> task.errorMessage ?: "Download failed"
                    DownloadStatus.CANCELLED -> "Cancelled"
                }

                val statusColor = when {
                    task.status == DownloadStatus.COMPLETED -> appColors.primary
                    task.status == DownloadStatus.COMPLETED_WITHOUT_TAGS -> Color(0xFFFFA726)
                    task.status == DownloadStatus.FAILED -> Color(0xFFFF5252)
                    task.status == DownloadStatus.EMBEDDING_TAGS -> appColors.secondary
                    else -> appColors.textSecondary
                }

                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = if (isWithoutTags || task.status == DownloadStatus.FAILED) 2 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isWithoutTags && onRetryTag != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFFA726).copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFA726).copy(alpha = 0.5f)),
                            modifier = Modifier
                                .clickable { onRetryTag(task.taskId) }
                                .testTag("retry_tag_${task.taskId}")
                        ) {
                            Text(
                                text = "Retry Tag",
                                color = Color(0xFFFFA726),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (!isDone && task.speedKbps > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = String.format(Locale.US, "%.1f MB/s", task.speedKbps / 1024f),
                        color = appColors.secondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ForYouSubsectionRow(
    title: String,
    subtitle: String,
    tracks: List<DownloadedTrack>,
    emptyText: String,
    appColors: AppThemePalette,
    onTrackClick: (DownloadedTrack, List<DownloadedTrack>) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title,
                    color = appColors.textPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = appColors.textMuted,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (tracks.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = appColors.surfaceCard,
                border = BorderStroke(1.dp, appColors.surfaceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = appColors.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = emptyText,
                        color = appColors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(items = tracks, key = { it.id }) { track ->
                    Surface(
                        onClick = { onTrackClick(track, tracks) },
                        shape = RoundedCornerShape(12.dp),
                        color = appColors.surfaceCard,
                        border = BorderStroke(1.dp, appColors.surfaceBorder),
                        modifier = Modifier.width(130.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(114.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                if (hasRealArtwork(track)) {
                                    val context = LocalContext.current
                                    AsyncImage(
                                        model = remember(track.id, track.albumArtUrl, track.localFilePath) {
                                            ImageRequest.Builder(context)
                                                .data(track)
                                                .crossfade(180)
                                                .size(228, 228)
                                                .build()
                                        },
                                        contentDescription = track.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    TrackArtworkPlaceholder(modifier = Modifier.fillMaxSize())
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = track.title,
                                color = appColors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = track.artist,
                                color = appColors.textSecondary,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryArtworkCarousel(
    title: String,
    subtitle: String,
    badgeText: String? = null,
    items: List<SearchResultItem>,
    appColors: AppThemePalette,
    onPlayItem: (SearchResultItem) -> Unit,
    onDownloadItem: (SearchResultItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = appColors.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = subtitle,
                    color = appColors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                )
            }
            if (!badgeText.isNullOrBlank()) {
                FormatChip(
                    text = badgeText,
                    color = appColors.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(items = items, key = { it.id }) { item ->
                CategoryArtworkCard(
                    item = item,
                    appColors = appColors,
                    onPlay = { onPlayItem(item) },
                    onDownload = { onDownloadItem(item) }
                )
            }
        }
    }
}

@Composable
fun CategoryArtworkCard(
    item: SearchResultItem,
    appColors: AppThemePalette,
    onPlay: () -> Unit,
    onDownload: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        onClick = onPlay,
        shape = RoundedCornerShape(16.dp),
        color = appColors.surfaceCard,
        border = BorderStroke(1.dp, appColors.surfaceBorder.copy(alpha = 0.7f)),
        modifier = Modifier
            .width(160.dp)
            .bounceClickable(scaleDown = 0.95f) { onPlay() }
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
            ) {
                if (hasRealArtwork(item.coverUrl)) {
                    AsyncImage(
                        model = remember(item.coverUrl) {
                            ImageRequest.Builder(context)
                                .data(item.coverUrl)
                                .crossfade(200)
                                .size(320, 320)
                                .build()
                        },
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    TrackArtworkPlaceholder(
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 14.dp,
                        iconSize = 36.dp
                    )
                }

                // Quick Play Button on Card
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(36.dp)
                        .background(appColors.primary, CircleShape)
                        .circularBounceClickable(scaleDown = 0.88f) { onPlay() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Quality Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Text(
                            text = "${item.bitDepth} • ${item.sampleRate}",
                            color = appColors.primary,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.title,
                color = appColors.textPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = item.artist,
                color = appColors.textSecondary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


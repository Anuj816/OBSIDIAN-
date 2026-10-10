package com.anuj.obsidian.data.repository

import com.anuj.obsidian.ObsidianCrashLogger
import com.anuj.obsidian.data.local.SearchHistoryDao
import com.anuj.obsidian.data.model.SearchHistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * SearchHistoryRepository: Manages search queries history flow, adding queries with timestamps,
 * deletion and clearing of user search records.
 */
class SearchHistoryRepository(
    private val searchHistoryDao: SearchHistoryDao
) {
    val searchHistory: Flow<List<SearchHistoryItem>> = searchHistoryDao.getSearchHistory()

    suspend fun addSearchHistory(query: String) = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext
        try {
            searchHistoryDao.insertSearchHistory(SearchHistoryItem(query = query.trim(), timestamp = System.currentTimeMillis()))
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            ObsidianCrashLogger.logException(e, "SearchHistoryRepository.addSearchHistory")
        }
    }

    suspend fun removeSearchHistory(query: String) = withContext(Dispatchers.IO) {
        try {
            searchHistoryDao.deleteSearchHistory(query.trim())
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            ObsidianCrashLogger.logException(e, "SearchHistoryRepository.removeSearchHistory")
        }
    }

    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        try {
            searchHistoryDao.clearHistory()
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            ObsidianCrashLogger.logException(e, "SearchHistoryRepository.clearSearchHistory")
        }
    }
}

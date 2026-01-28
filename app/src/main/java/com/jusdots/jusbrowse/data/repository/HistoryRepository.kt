package com.jusdots.jusbrowse.data.repository

import com.jusdots.jusbrowse.data.database.HistoryDao
import com.jusdots.jusbrowse.data.models.HistoryItem
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val historyDao: HistoryDao) {
    
    fun getAllHistory(): Flow<List<HistoryItem>> = historyDao.getAllHistory()

    fun searchHistory(query: String): Flow<List<HistoryItem>> =
        historyDao.searchHistory(query)

    fun getRecentHistory(limit: Int = 10): Flow<List<HistoryItem>> =
        historyDao.getRecentHistory(limit)

    suspend fun addToHistory(title: String, url: String) {
        // Check if URL already exists
        val existing = historyDao.getHistoryByUrl(url)
        if (existing != null) {
            // Update visit count and time
            val updated = existing.copy(
                visitCount = existing.visitCount + 1,
                visitedAt = System.currentTimeMillis()
            )
            historyDao.insertHistory(updated)
        } else {
            // Create new history entry
            val historyItem = HistoryItem(
                title = title,
                url = url
            )
            historyDao.insertHistory(historyItem)
        }
    }

    suspend fun deleteHistory(historyItem: HistoryItem) {
        historyDao.deleteHistory(historyItem)
    }

    suspend fun clearAllHistory() {
        historyDao.deleteAllHistory()
    }
}

package com.jusdots.jusbrowse.data.repository

import com.jusdots.jusbrowse.data.database.DownloadDao
import com.jusdots.jusbrowse.data.models.DownloadItem
import kotlinx.coroutines.flow.Flow

class DownloadRepository(private val downloadDao: DownloadDao) {
    val allDownloads: Flow<List<DownloadItem>> = downloadDao.getAllDownloads()

    suspend fun addDownload(item: DownloadItem) {
        downloadDao.insertDownload(item)
    }

    suspend fun deleteDownload(item: DownloadItem) {
        downloadDao.deleteDownload(item)
    }

    suspend fun clearAll() {
        downloadDao.clearAll()
    }
}

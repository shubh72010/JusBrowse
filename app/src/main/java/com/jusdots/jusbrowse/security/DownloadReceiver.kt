package com.jusdots.jusbrowse.security

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import com.jusdots.jusbrowse.data.repository.DownloadRepository
import com.jusdots.jusbrowse.data.repository.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Layer 12: Automated Security Guard
 * Listens for download completion and triggers Layer 11 Security Scanner
 */
class DownloadReceiver(
    private val downloadRepository: DownloadRepository,
    private val preferencesRepository: PreferencesRepository
) : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (downloadId == -1L) return

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor: Cursor = downloadManager.query(query)

            if (cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                if (statusIndex != -1 && cursor.getInt(statusIndex) == DownloadManager.STATUS_SUCCESSFUL) {
                    val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    val filenameIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                    
                    if (uriIndex != -1 && filenameIndex != -1) {
                        val fileUri = cursor.getString(uriIndex)
                        val fileName = cursor.getString(filenameIndex)
                        
                        // Convert URI to File Path
                        val filePath = fileUri.removePrefix("file://")
                        
                        // Trigger Scan
                        performSecurityScan(fileName, filePath)
                    }
                }
            }
            cursor.close()
        }
    }

    private fun performSecurityScan(fileName: String, filePath: String) {
        scope.launch {
            try {
                // Get API Keys from Preferences
                val vtKey = preferencesRepository.virusTotalApiKey.first()
                val koodousKey = preferencesRepository.koodousApiKey.first()

                // Update status to Scanning
                updateDownloadStatus(fileName, "Scanning", "Scan in progress...")

                // Run Scanner
                val result = SecurityScanner.scanFile(filePath, vtKey, koodousKey)

                // Update Database with Result
                updateDownloadStatus(fileName, result.status, result.detail)
            } catch (e: Exception) {
                updateDownloadStatus(fileName, "Error", "Scan failed: ${e.message}")
            }
        }
    }

    private suspend fun updateDownloadStatus(fileName: String, status: String, result: String) {
        val allDownloads = downloadRepository.allDownloads.first()
        val item = allDownloads.find { it.fileName == fileName }
        if (item != null) {
            downloadRepository.addDownload(
                item.copy(securityStatus = status, scanResult = result)
            )
        }
    }
}

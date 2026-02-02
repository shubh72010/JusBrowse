package com.jusdots.jusbrowse.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val url: String,
    val filePath: String,
    val fileSize: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "Completed", // Downloading, Completed, Failed
    val securityStatus: String = "Not Scanned", // Not Scanned, Scanning, Clean, Malicious, Error
    val scanResult: String? = null, // Detailed message or hash
    val systemDownloadId: Long = -1 // ID from Android DownloadManager
)

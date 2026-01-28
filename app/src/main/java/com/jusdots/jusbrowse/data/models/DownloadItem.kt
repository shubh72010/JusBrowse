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
    val status: String = "Completed" // Simplified for now: Downloading, Completed, Failed
)

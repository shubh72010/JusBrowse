package com.jusdots.jusbrowse.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jusdots.jusbrowse.data.models.Bookmark
import com.jusdots.jusbrowse.data.models.HistoryItem

@Database(
    entities = [
        Bookmark::class, 
        HistoryItem::class, 
        com.jusdots.jusbrowse.data.models.DownloadItem::class,
        com.jusdots.jusbrowse.data.models.SiteSettings::class
    ],
    version = 7, // Incremented for schema changes
    exportSchema = false
)
abstract class BrowserDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun siteSettingsDao(): SiteSettingsDao
}

package com.jusdots.jusbrowse

import android.app.Application
import androidx.room.Room
import com.jusdots.jusbrowse.data.database.BrowserDatabase

class BrowserApplication : Application() {
    
    companion object {
        @Volatile
        private var instance: BrowserApplication? = null
        
        fun getInstance(): BrowserApplication {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
        
        val database: BrowserDatabase by lazy {
            val app = getInstance()
            val context = app.applicationContext
            val personaId = com.jusdots.jusbrowse.security.FakeModeManager.getSavedPersonaId(context)
            val dbName = if (personaId != null) "browser_database_$personaId" else "browser_database"
            
            Room.databaseBuilder(
                context,
                BrowserDatabase::class.java,
                dbName
            ).fallbackToDestructiveMigration().build()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Phase 2: OS-Level Isolation
        // Must be called BEFORE any WebView is created
        val savedPersonaId = com.jusdots.jusbrowse.security.FakeModeManager.getSavedPersonaId(this)
        if (savedPersonaId != null) {
            try {
                // This creates a separate directory "app_webview_<id>" for cookies/cache
                android.webkit.WebView.setDataDirectorySuffix(savedPersonaId)
            } catch (e: Exception) {
                // Log but don't crash - suffixing might fail if process already locked
            }
        }
    }
}

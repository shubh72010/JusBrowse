package com.jusdots.jusbrowse

import android.app.Application
import androidx.room.Room
import com.jusdots.jusbrowse.data.database.BrowserDatabase
import org.chromium.net.CronetEngine
import org.chromium.net.ExperimentalCronetEngine
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import com.jusdots.jusbrowse.data.repository.PreferencesRepository

class BrowserApplication : Application() {
    
    companion object {
        @Volatile
        private var instance: BrowserApplication? = null

        @Volatile
        var cronetEngine: CronetEngine? = null
            private set
        
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
        val savedPersonaId = com.jusdots.jusbrowse.security.FakeModeManager.getSavedPersonaId(this)
        if (savedPersonaId != null) {
            try {
                android.webkit.WebView.setDataDirectorySuffix(savedPersonaId)
            } catch (e: Exception) {}
        }

        // Phase 4: QUIC & HTTP/3 Support
        initCronet(savedPersonaId)
    }

    private fun initCronet(savedPersonaId: String?) {
        try {
            val builder = ExperimentalCronetEngine.Builder(this)
            builder.enableQuic(true)
            builder.enableHttp2(true)
            builder.enableBrotli(true)
            builder.setExperimentalOptions("""
                {
                    "QUIC": {
                        "enable_quic": true,
                        "race_cert_verification": true,
                        "connection_options": "CHLO,ACKL"
                    },
                    "AsyncDNS": {
                        "enable": true
                    },
                    "StaleDNS": {
                        "enable": false
                    }
                }
            """.trimIndent())
            
            val preferencesRepository = PreferencesRepository(this)
            val maxCacheMB: Int = runBlocking<Int> {
                preferencesRepository.maxCacheSizeMB.first()
            }
            
            // Use persona-specific storage path for TLS session resumption isolation
            val storageDirName = if (savedPersonaId != null) "cronet_storage_$savedPersonaId" else "cronet_storage"
            val cronetStorageDir = java.io.File(cacheDir, storageDirName)
            if (!cronetStorageDir.exists()) cronetStorageDir.mkdirs()
            
            builder.setStoragePath(cronetStorageDir.absolutePath)
            builder.enableHttpCache(CronetEngine.Builder.HTTP_CACHE_DISK, maxCacheMB.toLong() * 1024 * 1024)
            
            cronetEngine = builder.build()
        } catch (e: Exception) {
            // Fallback handled by NetworkSurgeon
        }
    }
}

package com.jusdots.jusbrowse.security

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import java.io.InputStreamReader

object PersonaRepository {
    private val profiles = mutableListOf<FakePersona>()
    private val gson = Gson()

    private val initializationLock = Any()
    @Volatile private var isInitializing = false

    fun init(context: Context) {
        if (profiles.isNotEmpty() || isInitializing) return
        
        synchronized(initializationLock) {
            if (profiles.isNotEmpty() || isInitializing) return
            isInitializing = true
        }

        // Run heavy asset parsing in a background thread to avoid ANR
        java.lang.Thread {
            try {
                val assetManager = context.assets
                val files = assetManager.list("golden_profiles") ?: return@Thread
                
                val loadedProfiles = mutableListOf<FakePersona>()
                for (fileName in files) {
                    if (fileName.endsWith(".json")) {
                        try {
                            assetManager.open("golden_profiles/$fileName").use { inputStream ->
                                val reader = InputStreamReader(inputStream)
                                val rawProfile = gson.fromJson(reader, RawPersona::class.java)
                                rawProfile?.toFakePersona()?.let { loadedProfiles.add(it) }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("PersonaRepository", "Failed to load $fileName", e)
                        }
                    }
                }
                
                synchronized(initializationLock) {
                    profiles.clear()
                    profiles.addAll(loadedProfiles)
                    isInitializing = false
                }
                android.util.Log.d("PersonaRepository", "Loaded ${profiles.size} profiles")
            } catch (e: Exception) {
                android.util.Log.e("PersonaRepository", "Critical init error", e)
                synchronized(initializationLock) { isInitializing = false }
            }
        }.start()
    }

    val GOLDEN_PROFILES: List<FakePersona> get() = profiles

    fun getPersonaById(id: String): FakePersona? {
        return profiles.find { it.id == id }
    }

    fun getRandomPersona(): FakePersona {
        return if (profiles.isNotEmpty()) profiles.random() else createFallbackPersona()
    }

    fun getPersonaInGroup(groupId: String, flagship: Boolean): FakePersona {
        return profiles.find { it.groupId == groupId && it.isFlagship == flagship }
            ?: profiles.find { it.groupId == groupId }
            ?: getRandomPersona()
    }

    private fun createFallbackPersona(): FakePersona {
        return FakePersona(
            id = "fallback",
            displayName = "Generic Android",
            userAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            platform = "Android",
            platformString = "Linux aarch64",
            platformVersion = "10",
            model = "SM-G973F",
            mobile = true,
            headers = emptyMap(),
            brands = listOf(
                FakePersona.BrandVersion("Android", "10"),
                FakePersona.BrandVersion("Google Chrome", "120"),
                FakePersona.BrandVersion("Not_A Brand", "8")
            ),
            screenWidth = 1080,
            screenHeight = 2280,
            pixelRatio = 3.0,
            cpuCores = 8,
            ramGB = 8,
            videoCardRenderer = "Mali-G76",
            videoCardVendor = "ARM",
            locale = "en-US",
            languages = listOf("en-US"),
            timezone = "UTC",
            groupId = "generic",
            manufacturer = "Samsung"
        )
    }
}

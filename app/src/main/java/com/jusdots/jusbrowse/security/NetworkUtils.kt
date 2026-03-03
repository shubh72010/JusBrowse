package com.jusdots.jusbrowse.security

import java.net.HttpURLConnection
import java.net.URL
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NetworkUtils {

    private val LOCAL_IP_PRESETS = (1..100).map { i ->
        when {
            i <= 40 -> "192.168.1.$i"
            i <= 70 -> "192.168.0.${i-40}"
            i <= 85 -> "10.0.0.${i-70}"
            i <= 95 -> "192.168.1.${254-(i-85)}"
            else -> "172.16.0.${i-95}"
        }
    }

    /**
     * Returns a random local IP from the expanded preset list.
     */
    fun getWeightedRandomLocalIp(): String {
        return LOCAL_IP_PRESETS.random()
    }

    /**
     * Returns the system timezone as a fallback (leak prevented).
     */
    suspend fun fetchCurrentTimezone(): String? = withContext(Dispatchers.IO) {
        try {
            java.util.TimeZone.getDefault().id
        } catch (e: Exception) {
            null
        }
    }
}

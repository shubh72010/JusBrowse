package com.jusdots.jusbrowse.security

import java.net.HttpURLConnection
import java.net.URL
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NetworkUtils {

    /**
     * Fetches the timezone of the current IP address using a public API.
     * Returns null if the fetch fails.
     */
    suspend fun fetchCurrentTimezone(): String? = withContext(Dispatchers.IO) {
        try {
            // Using ipapi.co/timezone/ which returns a simple string like "America/New_York"
            val url = URL("https://ipapi.co/timezone/")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val timezone = reader.readLine()?.trim()
                reader.close()
                timezone
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

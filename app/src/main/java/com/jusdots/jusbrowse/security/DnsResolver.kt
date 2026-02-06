package com.jusdots.jusbrowse.security

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object DnsResolver {
    private val cnameCache = ConcurrentHashMap<String, String>()

    /**
     * Resolves the CNAME for a given host using Google DNS-over-HTTPS.
     * Returns the target domain if a CNAME exists, or null otherwise.
     */
    suspend fun resolveCname(host: String): String? = withContext(Dispatchers.IO) {
        // Return from cache if available
        cnameCache[host]?.let { if (it == "NONE") return@withContext null else return@withContext it }

        try {
            val url = URL("https://dns.google/resolve?name=$host&type=CNAME")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 1500 // Reduced from 3000
            connection.readTimeout = 1500    // Reduced from 3000
            connection.setRequestProperty("Accept", "application/json")

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readLine()
                reader.close()

                val json = JSONObject(response)
                val answer = json.optJSONArray("Answer")
                if (answer != null && answer.length() > 0) {
                    val cname = answer.getJSONObject(0).optString("data")?.trimEnd('.')
                    if (!cname.isNullOrEmpty()) {
                        cnameCache[host] = cname
                        return@withContext cname
                    }
                }
            }
        } catch (e: Exception) {
            // Silently fail, we don't want to break browsing
        }

        // Cache negative result to avoid repeated lookups
        cnameCache[host] = "NONE"
        null
    }
}

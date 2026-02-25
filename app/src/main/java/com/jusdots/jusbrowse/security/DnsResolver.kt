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
    private val blockCache = ConcurrentHashMap<String, Boolean>()

    private val BYPASS_DOMAINS = setOf(
        "google.com", "google.co.in", "google.de", "google.jp", "google.co.uk",
        "gstatic.com", "googleapis.com", "googleusercontent.com",
        "bing.com", "bing.net", "duckduckgo.com", "duckduckgo.co",
        "cloudflare.com", "cloudflare-dns.com", "dns.google", "dns.adguard-dns.com",
        "cleanbrowsing.org"
    )

    private fun formatDohUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        var formatted = url.trim()
        if (!formatted.startsWith("http://") && !formatted.startsWith("https://")) {
            formatted = "https://$formatted"
            if (!formatted.contains("/dns-query") && !formatted.contains("/resolve")) {
                formatted = "$formatted/dns-query"
            }
        }
        return formatted
    }

    /**
     * Checks if the host is blocked by the custom DoH resolver (returns 0.0.0.0 or NXDOMAIN).
     * Synchronous because it's called from `shouldInterceptRequest` on a background thread.
     */
    fun isBlockedByCustomDns(host: String, customDohUrl: String?): Boolean {
        val dohUrl = formatDohUrl(customDohUrl) ?: return false
        val lowHost = host.lowercase()
        
        // 1. Bypass trusted domains for performance
        if (BYPASS_DOMAINS.any { lowHost == it || lowHost.endsWith(".$it") }) {
            return false
        }
        
        // 2. Cache check
        blockCache[lowHost]?.let { return it }

        try {
            val urlObj = try { URL(dohUrl) } catch(e: Exception) { null }
            val sep = if (urlObj?.query != null) "&" else "?"
            val endpoint = "$dohUrl${sep}name=$lowHost&type=A"
            
            val url = URL(endpoint)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 1000 // 1s timeout
            connection.readTimeout = 1000
            connection.setRequestProperty("Accept", "application/dns-json")

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readLine()
                reader.close()

                val json = JSONObject(response)
                
                // NXDOMAIN (Status 3) or ServFail (Status 2) usually means blocked or unavailable
                val status = json.optInt("Status")
                if (status == 3 || status == 2) {
                    blockCache[lowHost] = true
                    return true
                }
                
                val answer = json.optJSONArray("Answer")
                // NOERROR (Status 0) but no Answer array -> treat as block if it's a family DNS?
                // Actually, let's stick to explicit Status 3 or blocked IPs.
                if (status == 0 && (answer == null || answer.length() == 0)) {
                    blockCache[lowHost] = false
                    return false
                }
                
                if (answer != null) {
                    for (i in 0 until answer.length()) {
                        val data = answer.getJSONObject(i).optString("data")
                        // Many DNS providers block by returning 0.0.0.0 or 127.0.0.1
                        // Others (like AdGuard/OpenDNS) return a specific "Block Page" IP address
                        val isBlockedIp = data == "0.0.0.0" || 
                                          data == "127.0.0.1" || 
                                          data == "::1" || 
                                          data == "0.0.0.0." || 
                                          data == "::" ||
                                          data == "94.140.14.35" || // AdGuard Block Page
                                          data == "94.140.15.35" || // AdGuard Block Page
                                          data == "146.112.61.106" || // OpenDNS Block Page
                                          data == "::ffff:146.112.61.106"

                        if (isBlockedIp) {
                            blockCache[lowHost] = true
                            return true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Silently fail open if DoH is unreachable
        }
        
        blockCache[lowHost] = false
        return false
    }

    /**
     * Resolves the CNAME for a given host using Google DNS-over-HTTPS.
     * Returns the target domain if a CNAME exists, or null otherwise.
     */
    suspend fun resolveCname(host: String, customDohUrl: String? = null): String? = withContext(Dispatchers.IO) {
        // Return from cache if available
        cnameCache[host]?.let { if (it == "NONE") return@withContext null else return@withContext it }

        try {
            val formattedDoh = formatDohUrl(customDohUrl)
            val endpoint = if (formattedDoh != null) {
                val urlObj = try { URL(formattedDoh) } catch(e: Exception) { null }
                val sep = if (urlObj?.query != null) "&" else "?"
                "$formattedDoh${sep}name=$host&type=CNAME"
            } else {
                "https://dns.google/resolve?name=$host&type=CNAME"
            }
            val url = URL(endpoint)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 1500 // Reduced from 3000
            connection.readTimeout = 1500    // Reduced from 3000
            connection.setRequestProperty("Accept", "application/dns-json")

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

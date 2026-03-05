package com.jusdots.jusbrowse.security

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap

/**
 * DNS over HTTPS (DoH) Client Implementation
 * Uses a common provider (Cloudflare) to maintain low fingerprint entropy.
 * Integrated with OkHttp for seamless network surgery.
 */
class DnsOverHttps(private val client: OkHttpClient) : Dns {
    private val dohUrl = "https://cloudflare-dns.com/dns-query".toHttpUrl()
    private val cache = ConcurrentHashMap<String, List<InetAddress>>()

    override fun lookup(hostname: String): List<InetAddress> {
        // 1. Fast cache check
        cache[hostname]?.let { return it }

        // 2. Perform DoH resolution
        try {
            val request = okhttp3.Request.Builder()
                .url(dohUrl.newBuilder().addQueryParameter("name", hostname).addQueryParameter("type", "A").build())
                .header("Accept", "application/dns-json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw UnknownHostException("DoH Failed: ${response.code}")
                
                val body = response.body?.string() ?: throw UnknownHostException("Empty DoH response")
                val json = org.json.JSONObject(body)
                val answer = json.optJSONArray("Answer")
                
                val results = mutableListOf<InetAddress>()
                if (answer != null) {
                    for (i in 0 until answer.length()) {
                        val obj = answer.getJSONObject(i)
                        if (obj.optInt("type") == 1) { // Type A (IPv4)
                            val ip = obj.getString("data")
                            results.add(InetAddress.getByName(ip))
                        }
                    }
                }

                if (results.isNotEmpty()) {
                    cache[hostname] = results
                    return results
                }
            }
        } catch (e: Exception) {
            // Fallback to system DNS if DoH fails to avoid breaking connectivity
            try {
                return Dns.SYSTEM.lookup(hostname)
            } catch (se: UnknownHostException) {
                throw se
            }
        }

        throw UnknownHostException("Could not resolve $hostname via DoH or System")
    }
}

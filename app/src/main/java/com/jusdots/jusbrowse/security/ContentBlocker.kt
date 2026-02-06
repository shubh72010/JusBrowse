package com.jusdots.jusbrowse.security

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Content blocker for ad/tracker blocking
 * Layer 1 Security Component
 */
class ContentBlocker(context: Context) {
    private val blockedDomains = HashSet<String>()
    private val blockCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    init {
        try {
            val inputStream = context.assets.open("adblock_list.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))
            var line: String? = reader.readLine()
            while (line != null) {
                val domain = line.trim().lowercase()
                if (domain.isNotEmpty() && !domain.startsWith("#")) {
                    blockedDomains.add(domain)
                }
                line = reader.readLine()
            }
            reader.close()
        } catch (e: Exception) {
            // Layer 12: No debug logging in release - silently handle
            // ProGuard strips all Log calls anyway
        }
    }

    /**
     * Suspend version for full check including CNAME uncloaking.
     */
    suspend fun shouldBlock(url: String, onTrackerBlocked: (String) -> Unit = {}): Boolean {
        val uri = try {
            Uri.parse(url)
        } catch (e: Exception) {
            return false
        }
        
        val host = uri.host?.lowercase() ?: return false
        
        // 0. Fast local cache check
        blockCache[host]?.let { if (it) onTrackerBlocked(host); return it }

        // 1. Check direct host/domain matches
        if (isDomainBlocked(host)) {
            blockCache[host] = true
            onTrackerBlocked(host)
            return true
        }
        
        // 2. CNAME Uncloaking: Only if host seems like a tracker or is a subdomain
        if (host.count { it == '.' } > 1) { 
            val cnameTarget = DnsResolver.resolveCname(host)
            if (cnameTarget != null && isDomainBlocked(cnameTarget)) {
                blockCache[host] = true
                onTrackerBlocked(cnameTarget)
                return true
            }
        }
        
        blockCache[host] = false
        return false
    }

    /**
     * Synchronous version for WebView threads. Skips CNAME uncloaking if not cached.
     */
    fun shouldBlockFast(url: String, onTrackerBlocked: (String) -> Unit = {}): Boolean {
        val uri = try {
            Uri.parse(url)
        } catch (e: Exception) {
            return false
        }
        
        val host = uri.host?.lowercase() ?: return false
        
        // 1. Check local cache first
        blockCache[host]?.let { if (it) onTrackerBlocked(host); return it }

        // 2. Check direct host/domain matches (very fast HashSet lookup)
        if (isDomainBlocked(host)) {
            blockCache[host] = true
            onTrackerBlocked(host)
            return true
        }

        return false
    }

    private fun isDomainBlocked(host: String): Boolean {
        if (blockedDomains.contains(host)) return true
        
        var parts = host.split(".")
        while (parts.size >= 2) {
            val parentDomain = parts.joinToString(".")
            if (blockedDomains.contains(parentDomain)) return true
            parts = parts.drop(1)
        }
        return false
    }
}

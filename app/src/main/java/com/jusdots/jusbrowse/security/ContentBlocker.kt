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

    fun shouldBlock(url: String): Boolean {
        val uri = try {
            Uri.parse(url)
        } catch (e: Exception) {
            return false
        }
        
        val host = uri.host?.lowercase() ?: return false
        
        // Check exact match
        if (blockedDomains.contains(host)) return true
        
        // Check parent domains (e.g., if doubleclick.net is blocked, ads.doubleclick.net should also be blocked)
        var parts = host.split(".")
        while (parts.size >= 2) {
            val parentDomain = parts.joinToString(".")
            if (blockedDomains.contains(parentDomain)) return true
            parts = parts.drop(1)
        }
        
        return false
    }
}

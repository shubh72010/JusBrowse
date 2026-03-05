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
    private val blockedPaths = mutableListOf<String>()
    private val blockCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    var customDohUrl: String = ""

    init {
        loadRules(context, "adblock_list.txt")
        loadRules(context, "easyprivacy.txt")
    }

    private fun loadRules(context: Context, fileName: String) {
        try {
            val inputStream = context.assets.open(fileName)
            val reader = BufferedReader(InputStreamReader(inputStream))
            var line: String? = reader.readLine()
            while (line != null) {
                val trimmedLine = line.trim()
                if (trimmedLine.isNotEmpty() && !trimmedLine.startsWith("!") && !trimmedLine.startsWith("#")) {
                    parseRule(trimmedLine)
                }
                line = reader.readLine()
            }
            reader.close()
        } catch (_: Exception) {}
    }

    private fun parseRule(rule: String) {
        // Very basic ABP-style parser for JusBrowse
        // Supports: ||domain^, /path/to/block, and raw domains
        when {
            rule.startsWith("||") -> {
                val domain = rule.substring(2).substringBefore("^").lowercase()
                if (domain.isNotEmpty()) blockedDomains.add(domain)
            }
            rule.startsWith("/") -> {
                val path = rule.substringBefore("$").lowercase()
                if (path.isNotEmpty()) blockedPaths.add(path)
            }
            else -> {
                val clean = rule.substringBefore("$").lowercase()
                if (clean.isNotEmpty() && clean.contains(".")) {
                    blockedDomains.add(clean)
                }
            }
        }
    }

    /**
     * Suspend version for full check including CNAME uncloaking.
     */
    suspend fun shouldBlock(url: String, onTrackerBlocked: (String) -> Unit = {}): Boolean {
        if (shouldBlockFast(url, onTrackerBlocked)) return true
        
        val uri = try { Uri.parse(url) } catch (e: Exception) { return false }
        val host = uri.host?.lowercase() ?: return false

        // 2. CNAME Uncloaking: Only if host seems like a tracker or is a subdomain
        if (host.count { it == '.' } > 1) { 
            val cnameTarget = DnsResolver.resolveCname(host, customDohUrl)
            if (cnameTarget != null && isDomainBlocked(cnameTarget)) {
                blockCache[host] = true
                onTrackerBlocked(cnameTarget)
                return true
            }
        }
        
        return false
    }

    /**
     * Synchronous version for WebView threads. Skips CNAME uncloaking if not cached.
     */
    fun shouldBlockFast(url: String, onTrackerBlocked: (String) -> Unit = {}): Boolean {
        val uri = try { Uri.parse(url) } catch (e: Exception) { return false }
        val host = uri.host?.lowercase() ?: return false
        val path = uri.path?.lowercase() ?: ""
        
        // 1. Check local cache first
        blockCache["$host$path"]?.let { if (it) onTrackerBlocked(host); return it }

        // 2. Check path-based rules (EasyPrivacy)
        for (blockedPath in blockedPaths) {
            if (path.contains(blockedPath)) {
                blockCache["$host$path"] = true
                onTrackerBlocked("Path: $blockedPath")
                return true
            }
        }

        // 3. Check direct host/domain matches
        if (isDomainBlocked(host)) {
            blockCache["$host$path"] = true
            onTrackerBlocked(host)
            return true
        }

        blockCache["$host$path"] = false
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

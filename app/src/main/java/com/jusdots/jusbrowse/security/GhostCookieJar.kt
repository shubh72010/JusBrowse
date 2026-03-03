package com.jusdots.jusbrowse.security

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * Ghost Cookie Jar: A volatile, in-memory cookie store that isolates 
 * cookies per container. It periodically pulls from WebView's CookieManager
 * but maintains a separate "Ghost" state for native interception traffic.
 */
object GhostCookieJar : CookieJar {

    private val containerCookies = ConcurrentHashMap<String, MutableList<Cookie>>()
    
    // Tracks current container ID for thread-local access if necessary, 
    // but we prefer explicit passing.
    private val activeContainerId = ThreadLocal<String>()

    fun setThreadContainerId(id: String) {
        activeContainerId.set(id)
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val containerId = activeContainerId.get() ?: "default"
        val store = containerCookies.getOrPut(containerId) { mutableListOf() }
        
        // Update existing or add new
        cookies.forEach { newCookie ->
            store.removeAll { it.name == newCookie.name && it.domain == newCookie.domain && it.path == newCookie.path }
            store.add(newCookie)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val containerId = activeContainerId.get() ?: "default"
        val ghostCookies = containerCookies[containerId]?.filter { it.matches(url) } ?: emptyList()
        
        // Sync Strategy: If ghost is empty, pull session cookies from WebView's CookieManager
        if (ghostCookies.isEmpty()) {
            val urlStr = url.toString()
            val webViewCookies = CookieManager.getInstance().getCookie(urlStr)
            if (!webViewCookies.isNullOrBlank()) {
                val pulled = webViewCookies.split(";").mapNotNull {
                    try {
                        Cookie.parse(url, it.trim())
                    } catch (e: Exception) {
                        null
                    }
                }
                return pulled
            }
        }
        
        return ghostCookies
    }

    fun clearContainer(containerId: String) {
        containerCookies.remove(containerId)
    }

    fun clearAll() {
        containerCookies.clear()
    }
}

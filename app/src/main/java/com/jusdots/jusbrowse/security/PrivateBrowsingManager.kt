package com.jusdots.jusbrowse.security

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView

/**
 * Layer 5 & 6: Cookie & Storage Control + Private/Incognito Mode
 * Manages isolated storage and cleanup for private browsing sessions
 */
object PrivateBrowsingManager {

    /**
     * Configure a WebView for private browsing mode
     */
    fun configurePrivateWebView(webView: WebView) {
        webView.apply {
            // Non-persistent cache
            settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            
            // Disable form data saving
            settings.saveFormData = false
            
            // Clear any existing data from this view
            clearCache(true)
            clearHistory()
            clearFormData()
        }
        
        // Configure cookies for this private session
        // Note: CookieManager is app-global, but we clear on close
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false) // Block third-party cookies
        }
    }

    /**
     * Complete cleanup when closing a private tab
     * Clears: cookies, cache, storage, service workers
     */
    fun cleanupPrivateSession(webView: WebView) {
        webView.apply {
            // Stop any ongoing loads
            stopLoading()
            
            // Clear all WebView data
            clearCache(true)
            clearHistory()
            clearFormData()
            clearSslPreferences()
            
            // Clear cookies
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            
            // Clear web storage (localStorage, sessionStorage, IndexedDB)
            WebStorage.getInstance().deleteAllData()
            
            // Destroy the WebView
            destroy()
        }
    }

    /**
     * Clear all browsing data (for "Clear Data" feature)
     */
    fun clearAllBrowsingData() {
        // Clear cookies
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        
        // Clear web storage
        WebStorage.getInstance().deleteAllData()
    }

    /**
     * Clear cookies only
     */
    fun clearCookies() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }

    /**
     * Clear session cookies only (keeps persistent cookies)
     */
    fun clearSessionCookies() {
        CookieManager.getInstance().removeSessionCookies(null)
        CookieManager.getInstance().flush()
    }

    /**
     * Block third-party cookies for a specific WebView
     */
    fun blockThirdPartyCookies(webView: WebView, block: Boolean) {
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, !block)
    }
}

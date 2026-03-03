package com.jusdots.jusbrowse.security

import android.webkit.WebView

/**
 * Follian Mode - Hard JavaScript Kill
 * 
 * Disables ALL JavaScript execution at the WebView level.
 * This WILL break most modern websites. That's the point.
 * 
 * Designed for:
 * - Maximum privacy (no tracking scripts)
 * - Reading-focused content consumption
 * - Hostile network environments
 */
object FollianBlocker {
    
    /**
     * Apply Follian Mode to a WebView.
     * This completely disables JavaScript execution.
     */
    fun applyToWebView(webView: WebView) {
        webView.settings.apply {
            // Core JS kill
            javaScriptEnabled = false
            javaScriptCanOpenWindowsAutomatically = false
            
            // Block related technologies
            // Note: These are already mostly blocked when JS is off,
            // but we're explicit about intent
            allowFileAccess = false
            allowContentAccess = false
        }
    }
    
    /**
     * Disable Follian Mode - restore JS execution
     */
    fun removeFromWebView(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = false // Still keep this off
        }
    }
    
    /**
     * CSS injection to hide "JavaScript required" warnings
     * and improve display of noscript content
     */
    fun getNoScriptStylesheet(): String = """
        <style>
            /* Hide JS-required warnings */
            .js-required, .enable-javascript, [data-js-required] {
                display: none !important;
            }
            
            /* Show noscript content properly */
            noscript {
                display: block !important;
            }
            
            /* Improve readability for static content */
            body {
                max-width: 800px;
                margin: 0 auto;
                padding: 16px;
                font-size: 18px;
                line-height: 1.6;
            }
        </style>
    """.trimIndent()
    
    /**
     * Check if a URL might work reasonably without JS
     */
    fun isLikelyUsable(url: String): Boolean {
        val staticFriendly = listOf(
            "wikipedia.org",
            ".gov",
            ".edu",
            "news",
            "article",
            "blog"
        )
        return staticFriendly.any { url.contains(it, ignoreCase = true) }
    }
}

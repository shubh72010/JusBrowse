package com.jusdots.jusbrowse.security

import android.content.Context
import androidx.webkit.WebViewCompat
import androidx.webkit.ProfileStore
import androidx.webkit.Profile
import androidx.webkit.WebViewFeature

object ContainerManager {
    val AVAILABLE_CONTAINERS = listOf("default", "work", "personal", "banking", "sandbox")

    /**
     * Set the profile for a WebView if supported.
     * Profile isolation ensures separate cookies, cache, and storage.
     */
    fun applyContainer(webView: android.webkit.WebView, containerId: String?) {
        if (containerId == null || containerId == "default") return

        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                val profileStore = ProfileStore.getInstance()
                val profile: Profile = profileStore.getOrCreateProfile(containerId)
                WebViewCompat.setProfile(webView, profile.name)
            }
        } catch (e: Exception) {
            // Fallback or ignore if not supported by current WebView version
        }
    }

    /**
     * Get display name for a container
     */
    fun getContainerName(containerId: String): String {
        return when (containerId) {
            "work" -> "Work"
            "personal" -> "Personal"
            "banking" -> "Banking"
            "sandbox" -> "Sandbox"
            else -> "Default"
        }
    }
    
    /**
     * Deletes all profiles to reset state (Nuclear Reset)
     */
    fun clearAllProfiles(context: Context) {
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                val profileStore = ProfileStore.getInstance()
                profileStore.getAllProfileNames().forEach { name ->
                    if (name != "Default") {
                        profileStore.deleteProfile(name)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
}

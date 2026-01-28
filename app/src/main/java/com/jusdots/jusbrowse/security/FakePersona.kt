package com.jusdots.jusbrowse.security

/**
 * Represents a complete fake digital identity/persona.
 *
 * This version uses "Golden Profiles" - verified combinations of UA/Headers/Screen
 * to ensure consistency across the stack.
 */
data class FakePersona(
    val id: String,
    val displayName: String,
    val flagEmoji: String,

    // Core Identity
    val userAgent: String,
    // Client Hints (Sec-CH-UA-*)
    val brands: List<BrandVersion>,
    val platform: String,
    val platformVersion: String,
    val model: String,
    val mobile: Boolean,
    
    // HTTP Headers (Accept-Language, etc)
    val headers: Map<String, String>,

    // Screen Metrics (Viewport)
    val screenWidth: Int,
    val screenHeight: Int,
    val pixelRatio: Double, // devicePixelRatio

    // Hardware
    val cpuCores: Int,
    val ramGB: Int,
    val videoCardRenderer: String, // WebGL UNMASKED_RENDERER
    val videoCardVendor: String,   // WebGL UNMASKED_VENDOR

    // Fingerprinting Noise
    val noiseSeed: Long, // Persistent seed for canvas/audio noise

    // Locale & Time
    val locale: String,    // navigator.language (e.g., "en-US")
    val languages: List<String>, // navigator.languages
    val timezone: String,  // Intl.DateTimeFormat (e.g., "America/New_York")
) {
    // Helper properties for UI compatibility
    val deviceModel: String get() = model
    val deviceManufacturer: String get() = model.split(" ").firstOrNull() ?: "Generic"
    val androidVersionName: String get() = platformVersion
    val dpi: Int get() = (pixelRatio * 160).toInt()
    
    val browserName: String get() = brands.find { !it.brand.contains("Not") }?.brand ?: "Chrome"
    val browserVersion: String get() = brands.find { !it.brand.contains("Not") }?.version ?: "0.0"
    
    val countryCode: String get() = locale.split("-").lastOrNull() ?: "US"

    data class BrandVersion(val brand: String, val version: String)

    /**
     * Helper to generate the Sec-CH-UA header value
     * Example: "Chromium";v="120", "Google Chrome";v="120", "Not-A.Brand";v="99"
     */
    fun getSecChUaHeader(): String {
        return brands.joinToString(", ") {
            "\"${it.brand}\";v=\"${it.version}\""
        }
    }
}

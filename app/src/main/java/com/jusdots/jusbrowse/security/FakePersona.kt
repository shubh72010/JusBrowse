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
    val platformString: String, // e.g. "Linux aarch64" or "iPhone"
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
    val doNotTrack: String = "1", // navigator.doNotTrack
    // Phase 3: Network & Timing
    val clockSkewMs: Long = 0, // Offset for Date/Time
    val networkType: String = "4g", // navigator.connection.effectiveType
    val networkDownlink: Double = 10.0, // navigator.connection.downlink
    val networkRtt: Int = 50, // navigator.connection.rtt

    // Phase 4: Extreme Believability
    val webglMaxTextureSize: Int = 8192,
    val webglMaxRenderBufferSize: Int = 8192,
    val webglExtensions: List<String> = listOf(
        "EXT_blend_minmax", "EXT_sRGB", "OES_texture_float", "OES_standard_derivatives",
        "WEBGL_debug_renderer_info", "WEBGL_lose_context"
    ),
    val mediaDeviceLabels: Map<String, String> = mapOf(
        "audioinput" to "Internal Microphone",
        "audiooutput" to "Speaker",
        "videoinput_front" to "Front Camera",
        "videoinput_back" to "Rear Camera"
    ),

    // Phase 5: Hardware-In-The-Loop Simulation
    val webglParams: Map<Int, Any> = emptyMap(),
    val audioBaseLatency: Double = 0.01, // 10ms typical for flagship
    val fontJitterSeed: Long = 42L,

    // Inner Profile Support
    val groupId: String = "generic",
    val isFlagship: Boolean = true
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

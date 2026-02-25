package com.jusdots.jusbrowse.security

/**
 * Represents a complete fake digital identity/persona.
 *
 * This version uses "Golden Profiles" - verified combinations of UA/Headers/Screen
 * to ensure consistency across the stack.
 */
import com.google.gson.annotations.SerializedName

/**
 * Represents a complete fake digital identity/persona.
 * 
 * Version 2: Golden Profiles
 * Verified combinations of UA/Headers/Screen/Hardware to ensure 
 * cross-signal consistency.
 */
data class FakePersona(
    @SerializedName("id") val id: String,
    @SerializedName("displayName") val displayName: String,
    @SerializedName("flagEmoji") val flagEmoji: String = "👤",
    @SerializedName("version") val version: Int = 1,

    // Core Identity
    @SerializedName("userAgent") val userAgent: String,
    
    // Client Hints (Sec-CH-UA-*)
    @SerializedName("brands") val brands: List<BrandVersion>,
    @SerializedName("platform") val platform: String,
    @SerializedName("platformString") val platformString: String, // e.g. "Linux aarch64"
    @SerializedName("platformVersion") val platformVersion: String,
    @SerializedName("model") val model: String,
    @SerializedName("mobile") val mobile: Boolean,
    
    // HTTP Headers
    @SerializedName("headers") val headers: Map<String, String>,

    // Screen Metrics
    @SerializedName("screenWidth") val screenWidth: Int,
    @SerializedName("screenHeight") val screenHeight: Int,
    @SerializedName("pixelRatio") val pixelRatio: Double,

    // Hardware Specs
    @SerializedName("cpuCores") val cpuCores: Int,
    @SerializedName("ramGB") val ramGB: Int,
    @SerializedName("videoCardRenderer") val videoCardRenderer: String,
    @SerializedName("videoCardVendor") val videoCardVendor: String,
    @SerializedName("hasWebGpu") val hasWebGpu: Boolean = false,
    @SerializedName("shaderPrecision") val shaderPrecision: Int = 23, // Default for most ARM
    @SerializedName("maxDescriptorSampledImages") val maxDescriptorSampledImages: Int = 16, // Default for Mali
    @SerializedName("ja4PartC") val ja4PartC: String? = null,
    @SerializedName("quicParameters") val quicParameters: Map<String, String>? = null,

    // Fingerprinting Noise Bases
    @SerializedName("noiseSeed") val noiseSeed: Long = System.currentTimeMillis(),

    // Locale & Time
    @SerializedName("locale") val locale: String,
    @SerializedName("languages") val languages: List<String>,
    @SerializedName("timezone") val timezone: String,
    @SerializedName("doNotTrack") val doNotTrack: String = "1",

    // Organization
    @SerializedName("groupId") val groupId: String = "generic",
    @SerializedName("isFlagship") val isFlagship: Boolean = true,
    @SerializedName("manufacturer") val manufacturer: String = "Generic"
) {
    // Helper properties for UI compatibility
    val deviceModel: String get() = model
    val deviceManufacturer: String get() = if (manufacturer != "Generic") manufacturer else (model.split(" ").firstOrNull() ?: "Generic")
    val androidVersionName: String get() = platformVersion
    val dpi: Int get() = (pixelRatio * 160).toInt()
    
    val browserName: String get() = brands.find { !it.brand.contains("Not") }?.brand ?: "Chrome"
    val browserVersion: String get() = brands.find { !it.brand.contains("Not") }?.version ?: "120.0"
    
    val countryCode: String get() = locale.split("-").lastOrNull() ?: "US"

    data class BrandVersion(
        @SerializedName("brand") val brand: String,
        @SerializedName("version") val version: String
    )

    fun getSecChUaHeader(): String {
        return brands
            .filter { !it.brand.contains("Android WebView", ignoreCase = true) }
            .joinToString(", ") {
                "\"${it.brand}\";v=\"${it.version.split(".").firstOrNull() ?: it.version}\""
            }
    }
}

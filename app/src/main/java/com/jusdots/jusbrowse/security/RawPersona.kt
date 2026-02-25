package com.jusdots.jusbrowse.security

import com.google.gson.annotations.SerializedName

/**
 * Data Transfer Object (DTO) for Persona JSON files.
 * Uses nullable types for all fields to safely handle missing data during Gson deserialization.
 */
data class RawPersona(
    @SerializedName("id") val id: String?,
    @SerializedName("displayName") val displayName: String?,
    @SerializedName("flagEmoji") val flagEmoji: String?,
    @SerializedName("version") val version: Int?,

    // Core Identity
    @SerializedName("userAgent") val userAgent: String?,
    
    // Client Hints (Sec-CH-UA-*)
    @SerializedName("brands") val brands: List<FakePersona.BrandVersion>?,
    @SerializedName("platform") val platform: String?,
    @SerializedName("platformString") val platformString: String?,
    @SerializedName("platformVersion") val platformVersion: String?,
    @SerializedName("model") val model: String?,
    @SerializedName("mobile") val mobile: Boolean?,
    
    // HTTP Headers
    @SerializedName("headers") val headers: Map<String, String>?,

    // Screen Metrics
    @SerializedName("screenWidth") val screenWidth: Int?,
    @SerializedName("screenHeight") val screenHeight: Int?,
    @SerializedName("pixelRatio") val pixelRatio: Double?,

    // Hardware Specs
    @SerializedName("cpuCores") val cpuCores: Int?,
    @SerializedName("ramGB") val ramGB: Int?,
    @SerializedName("videoCardRenderer") val videoCardRenderer: String?,
    @SerializedName("videoCardVendor") val videoCardVendor: String?,
    @SerializedName("hasWebGpu") val hasWebGpu: Boolean?,
    @SerializedName("shaderPrecision") val shaderPrecision: Int?,
    @SerializedName("maxDescriptorSampledImages") val maxDescriptorSampledImages: Int?,
    @SerializedName("ja4PartC") val ja4PartC: String?,
    @SerializedName("quicParameters") val quicParameters: Map<String, String>?,

    // Fingerprinting Noise Bases
    @SerializedName("noiseSeed") val noiseSeed: Long?,

    // Locale & Time
    @SerializedName("locale") val locale: String?,
    @SerializedName("languages") val languages: List<String>?,
    @SerializedName("timezone") val timezone: String?,
    @SerializedName("doNotTrack") val doNotTrack: String?,

    // Organization
    @SerializedName("groupId") val groupId: String?,
    @SerializedName("isFlagship") val isFlagship: Boolean?,
    @SerializedName("manufacturer") val manufacturer: String?
) {
    /**
     * Maps the RawPersona DTO to a non-nullable FakePersona domain model.
     * Provides defaults for any fields missing in the source JSON.
     */
    fun toFakePersona(): FakePersona {
        val nonNullId = id ?: "unknown"
        val nonNullGroupId = groupId ?: "generic"
        
        return FakePersona(
            id = nonNullId,
            displayName = displayName ?: "Generic Device",
            flagEmoji = flagEmoji ?: "👤",
            version = version ?: 1,
            userAgent = userAgent ?: "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            brands = brands ?: listOf(
                FakePersona.BrandVersion("Not_A Brand", "8"),
                FakePersona.BrandVersion("Chromium", "120"),
                FakePersona.BrandVersion("Google Chrome", "120")
            ),
            platform = platform ?: "Android",
            platformString = platformString ?: "Linux aarch64",
            platformVersion = platformVersion ?: "10",
            model = model ?: "SM-G973F",
            mobile = mobile ?: true,
            headers = headers ?: emptyMap(),
            screenWidth = screenWidth ?: 1080,
            screenHeight = screenHeight ?: 2280,
            pixelRatio = pixelRatio ?: 3.0,
            cpuCores = cpuCores ?: 8,
            ramGB = ramGB ?: 8,
            videoCardRenderer = videoCardRenderer ?: "Mali-G76",
            videoCardVendor = videoCardVendor ?: "ARM",
            hasWebGpu = hasWebGpu ?: false,
            shaderPrecision = shaderPrecision ?: 23,
            maxDescriptorSampledImages = maxDescriptorSampledImages ?: 16,
            ja4PartC = ja4PartC,
            quicParameters = quicParameters,
            noiseSeed = noiseSeed ?: System.currentTimeMillis(),
            locale = locale ?: "en-US",
            languages = languages ?: listOf("en-US"),
            timezone = timezone ?: "UTC",
            doNotTrack = doNotTrack ?: "1",
            groupId = nonNullGroupId,
            isFlagship = isFlagship ?: true,
            manufacturer = manufacturer ?: (nonNullGroupId.replaceFirstChar { it.uppercase() })
        )
    }
}

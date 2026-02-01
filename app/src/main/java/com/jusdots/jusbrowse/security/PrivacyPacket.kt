package com.jusdots.jusbrowse.security

/**
 * The state of a PrivacyPacket as it moves through the engines.
 */
enum class PrivacyState {
    RAW,        // Original OS data (should never leave the engine)
    FLATTENED,  // Identity removed by Priv8
    GLOWED,     // Personality added by RLE
    ADM,        // Automatic Defense Mode (protective profile)
    VOID        // Minimal/Null values (failsafe)
}

/**
 * A packet of sensitive data being processed for privacy.
 */
data class PrivacyPacket(
    val state: PrivacyState,
    val data: Map<String, Any>,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        // Standard keys used in the data map
        const val KEY_SCREEN_WIDTH = "screen_width"
        const val KEY_SCREEN_HEIGHT = "screen_height"
        const val KEY_PIXEL_RATIO = "pixel_ratio"
        const val KEY_BATTERY_LEVEL = "battery_level"
        const val KEY_BATTERY_CHARGING = "battery_charging"
        const val KEY_TIMEZONE = "timezone"
        const val KEY_USER_AGENT = "user_agent"
        const val KEY_HARDWARE_CONCURRENCY = "hardware_concurrency"
        const val KEY_DEVICE_MEMORY = "device_memory"
        const val KEY_PLATFORM = "platform"
        const val KEY_PLATFORM_STRING = "platform_string"
        const val KEY_LANGUAGE = "language"
        const val KEY_TIME_PRECISION_MS = "time_precision_ms"
    }
}

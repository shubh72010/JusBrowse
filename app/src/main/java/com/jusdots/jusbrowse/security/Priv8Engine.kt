package com.jusdots.jusbrowse.security

import kotlin.math.roundToInt

/**
 * Priv8 Engine: The Bouncer.
 * Removes identity by flattening data into generic buckets and static values.
 */
object Priv8Engine {

    fun flatten(packet: PrivacyPacket): PrivacyPacket {
        val rawData = packet.data
        val flattenedData = mutableMapOf<String, Any>()

        // Screen size: Flatten to generic buckets (e.g., 360x800)
        val width = rawData[PrivacyPacket.KEY_SCREEN_WIDTH] as? Int ?: 1080
        val height = rawData[PrivacyPacket.KEY_SCREEN_HEIGHT] as? Int ?: 2412
        
        // Simple bucket logic: standard mobile size
        flattenedData[PrivacyPacket.KEY_SCREEN_WIDTH] = 360
        flattenedData[PrivacyPacket.KEY_SCREEN_HEIGHT] = 800
        flattenedData[PrivacyPacket.KEY_PIXEL_RATIO] = 2.0

        // Battery: Round to 5% buckets to preserve drift signature but hide exact percentage
        val rawBattery = packet.data[PrivacyPacket.KEY_BATTERY_LEVEL] as? Double ?: 0.50
        flattenedData[PrivacyPacket.KEY_BATTERY_LEVEL] = Math.round(rawBattery * 20) / 20.0
        flattenedData[PrivacyPacket.KEY_BATTERY_CHARGING] = false

        // Time: Round to nearest hour or similar, and add precision rounding (100ms)
        flattenedData[PrivacyPacket.KEY_TIMEZONE] = "UTC"
        flattenedData[PrivacyPacket.KEY_TIME_PRECISION_MS] = 100 

        // Hardware: Baseline values (RLE will override these with Persona-specific values)
        flattenedData[PrivacyPacket.KEY_HARDWARE_CONCURRENCY] = 4
        flattenedData[PrivacyPacket.KEY_DEVICE_MEMORY] = 4
        flattenedData[PrivacyPacket.KEY_PLATFORM] = "Linux"
        flattenedData[PrivacyPacket.KEY_PLATFORM_STRING] = "Linux aarch64"

        // Identity: Remove UA and Locale
        flattenedData[PrivacyPacket.KEY_USER_AGENT] = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.0.0 Mobile Safari/537.36"
        flattenedData[PrivacyPacket.KEY_LANGUAGE] = "en-US"

        return PrivacyPacket(
            state = PrivacyState.FLATTENED,
            data = flattenedData
        )
    }
}

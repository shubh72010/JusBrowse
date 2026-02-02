package com.jusdots.jusbrowse.security

import kotlin.math.roundToInt

/**
 * Represents a screen dimensional bucket for privacy flattening
 */
data class ScreenBucket(val width: Int, val height: Int, val ratio: Double)

/**
 * Priv8 Engine: The Bouncer.
 * Removes identity by flattening data into generic buckets and static values.
 */
object Priv8Engine {

    // Realistic screen buckets based on common Android device classes
    private val REALISTIC_BUCKETS = listOf(
        ScreenBucket(360, 800, 2.0),   // Budget: Galaxy A54, Redmi Note 13
        ScreenBucket(393, 852, 4.0),   // Flagship: Xiaomi 14 Pro
        ScreenBucket(411, 914, 2.625), // Mid-range: Pixel 7a, Moto G54
        ScreenBucket(412, 915, 3.0)    // Flagship: Pixel 8 Pro
    )

    fun flatten(packet: PrivacyPacket): PrivacyPacket {
        val rawData = packet.data
        val flattenedData = mutableMapOf<String, Any>()

        // Screen size: Flatten to realistic buckets based on device class
        val width = rawData[PrivacyPacket.KEY_SCREEN_WIDTH] as? Int ?: 1080
        val height = rawData[PrivacyPacket.KEY_SCREEN_HEIGHT] as? Int ?: 2412
        val ratio = rawData[PrivacyPacket.KEY_PIXEL_RATIO] as? Double ?: 3.0
        
        // Calculate logical dimensions and select nearest realistic bucket
        val logicWidth = (width / ratio).toInt()
        val bucket = REALISTIC_BUCKETS.minByOrNull { 
            kotlin.math.abs(it.width - logicWidth)
        } ?: REALISTIC_BUCKETS[0]
        
        flattenedData[PrivacyPacket.KEY_SCREEN_WIDTH] = bucket.width
        flattenedData[PrivacyPacket.KEY_SCREEN_HEIGHT] = bucket.height
        flattenedData[PrivacyPacket.KEY_PIXEL_RATIO] = bucket.ratio

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

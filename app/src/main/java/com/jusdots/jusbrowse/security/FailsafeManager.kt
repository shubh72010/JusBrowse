package com.jusdots.jusbrowse.security

/**
 * Failsafe Manager: Handles ADM (Automatic Defense Mode) and VOID Mode.
 */
object FailsafeManager {

    /**
     * VOID Mode: Absolute minimum values.
     */
    fun getVoidPacket(): PrivacyPacket {
        return PrivacyPacket(
            state = PrivacyState.VOID,
            data = mapOf(
                PrivacyPacket.KEY_HARDWARE_CONCURRENCY to 4,
                PrivacyPacket.KEY_DEVICE_MEMORY to 4,
                PrivacyPacket.KEY_SCREEN_WIDTH to 360,
                PrivacyPacket.KEY_SCREEN_HEIGHT to 800,
                PrivacyPacket.KEY_PIXEL_RATIO to 2.0
            )
        )
    }

    /**
     * ADM (Automatic Defense Mode): A pre-baked, safe profile.
     */
    fun getAdmPacket(): PrivacyPacket {
        val admData = mutableMapOf<String, Any>()
        
        admData[PrivacyPacket.KEY_SCREEN_WIDTH] = 360
        admData[PrivacyPacket.KEY_SCREEN_HEIGHT] = 640
        admData[PrivacyPacket.KEY_PIXEL_RATIO] = 1.0
        admData[PrivacyPacket.KEY_USER_AGENT] = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.0.0 Mobile Safari/537.36"
        admData[PrivacyPacket.KEY_PLATFORM] = "Linux armv8l"
        admData[PrivacyPacket.KEY_LANGUAGE] = "en-US"
        admData[PrivacyPacket.KEY_TIMEZONE] = "UTC"
        admData[PrivacyPacket.KEY_BATTERY_LEVEL] = 0.50
        admData[PrivacyPacket.KEY_BATTERY_CHARGING] = false
        admData[PrivacyPacket.KEY_HARDWARE_CONCURRENCY] = 8
        admData[PrivacyPacket.KEY_DEVICE_MEMORY] = 8
        admData[PrivacyPacket.KEY_PLATFORM_STRING] = "Linux aarch64"
        
        return PrivacyPacket(
            state = PrivacyState.ADM,
            data = admData
        )
    }
}

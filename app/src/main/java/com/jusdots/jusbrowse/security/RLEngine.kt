package com.jusdots.jusbrowse.security

/**
 * RL Engine (RLE): The Stylist.
 * Restores usability by mapping flattened data to a specific persona "mannequin".
 */
object RLEngine {

    fun glow(packet: PrivacyPacket, persona: FakePersona): PrivacyPacket {
        if (packet.state != PrivacyState.FLATTENED) {
            // RLE should never see raw data or un-flattened data
            return PrivacyPacket(PrivacyState.VOID, emptyMap())
        }

        val glowedData = mutableMapOf<String, Any>()
        
        // Map to Persona Identity
        glowedData[PrivacyPacket.KEY_USER_AGENT] = persona.userAgent
        glowedData[PrivacyPacket.KEY_PLATFORM] = persona.platform
        glowedData[PrivacyPacket.KEY_PLATFORM_STRING] = persona.platformString
        glowedData[PrivacyPacket.KEY_LANGUAGE] = persona.locale
        
        // Map to Persona Screen (Logical)
        val logicWidth = (persona.screenWidth / persona.pixelRatio).toInt()
        val logicHeight = (persona.screenHeight / persona.pixelRatio).toInt()
        glowedData[PrivacyPacket.KEY_SCREEN_WIDTH] = logicWidth
        glowedData[PrivacyPacket.KEY_SCREEN_HEIGHT] = logicHeight
        glowedData[PrivacyPacket.KEY_PIXEL_RATIO] = persona.pixelRatio

        // Map to Persona Hardware
        glowedData[PrivacyPacket.KEY_HARDWARE_CONCURRENCY] = persona.cpuCores
        glowedData[PrivacyPacket.KEY_DEVICE_MEMORY] = persona.ramGB

        // Add persona-specific timezone
        glowedData[PrivacyPacket.KEY_TIMEZONE] = persona.timezone
        glowedData[PrivacyPacket.KEY_TIME_PRECISION_MS] = packet.data[PrivacyPacket.KEY_TIME_PRECISION_MS] ?: 100

        // Battery: Take from flattened packet (which preserves drift rounded by Priv8)
        glowedData[PrivacyPacket.KEY_BATTERY_LEVEL] = packet.data[PrivacyPacket.KEY_BATTERY_LEVEL] ?: 0.85
        glowedData[PrivacyPacket.KEY_BATTERY_CHARGING] = packet.data[PrivacyPacket.KEY_BATTERY_CHARGING] ?: true

        return PrivacyPacket(
            state = PrivacyState.GLOWED,
            data = glowedData
        )
    }
}

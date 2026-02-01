package com.jusdots.jusbrowse.security

/**
 * Privacy Bus: The internal coordinator.
 * Manages the flow from raw data to a glowed persona response.
 */
object PrivacyBus {

    /**
     * Process a request for private data.
     * JS Request -> Interceptor -> PrivacyBus -> Priv8 -> RLE -> WebView
     */
    fun process(rawPacket: PrivacyPacket, persona: FakePersona?): PrivacyPacket {
        // Step 0: Check Heuristic State
        val recommendedState = SuspicionScorer.getRecommendedState()
        if (recommendedState == PrivacyState.VOID) return FailsafeManager.getVoidPacket()
        if (recommendedState == PrivacyState.ADM) return FailsafeManager.getAdmPacket()

        // Step 1: Priv8 Flattens Everything
        val flattened = Priv8Engine.flatten(rawPacket)
        
        if (flattened.state != PrivacyState.FLATTENED) {
            return FailsafeManager.getVoidPacket()
        }

        // Step 2: RLE applies persona if available (with coherence validation)
        return if (persona != null) {
            // Validate persona coherence before applying
            val validationResult = CoherenceValidator.validate(persona)
            when (validationResult) {
                is CoherenceValidator.ValidationResult.Void -> {
                    // Hard rule failure - return null values
                    android.util.Log.e("PrivacyBus", "Persona VOID: ${validationResult.errors}")
                    FailsafeManager.getVoidPacket()
                }
                is CoherenceValidator.ValidationResult.Adm -> {
                    // Soft rule failure - return generic profile
                    android.util.Log.w("PrivacyBus", "Persona ADM: ${validationResult.warnings}")
                    FailsafeManager.getAdmPacket()
                }
                is CoherenceValidator.ValidationResult.Pass -> {
                    val glowed = RLEngine.glow(flattened, persona)
                    if (glowed.state == PrivacyState.GLOWED) {
                        glowed
                    } else {
                        FailsafeManager.getAdmPacket()
                    }
                }
            }
        } else {
            // No persona selected, stay in FLATTENED or use ADM
            FailsafeManager.getAdmPacket()
        }
    }
}

package com.jusdots.jusbrowse.security

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Zero-Trust Coherence Validator
 * 
 * Validates persona configurations before they reach the JS layer.
 * Any violation of HARD rules triggers VOID state (null values).
 * Any violation of SOFT rules triggers ADM state (generic defense profile).
 */
object CoherenceValidator {

    sealed class ValidationResult {
        object Pass : ValidationResult()
        data class Void(val errors: List<String>) : ValidationResult()
        data class Adm(val warnings: List<String>) : ValidationResult()
    }

    // Valid GPU vendors (must match what real devices report)
    private val VALID_GPU_VENDORS = setOf(
        "ARM",
        "Qualcomm", 
        "Apple Inc.",
        "Imagination Technologies",
        "NVIDIA",
        "Intel Inc.",
        "Google Inc." // For SwiftShader software rendering
    )

    // GPU renderer patterns per vendor
    private val GPU_VENDOR_RENDERER_PATTERNS = mapOf(
        "ARM" to listOf("Mali-"),
        "Qualcomm" to listOf("Adreno"),
        "Apple Inc." to listOf("Apple GPU", "Apple M"),
        "Imagination Technologies" to listOf("IMG", "PowerVR"),
        "NVIDIA" to listOf("NVIDIA", "Tegra"),
        "Intel Inc." to listOf("Intel"),
        "Google Inc." to listOf("SwiftShader")
    )

    /**
     * HARD RULES - Any violation = VOID (complete null response)
     * These are incoherent states that would be immediately detected.
     */
    fun validateHard(persona: FakePersona): ValidationResult {
        val errors = mutableListOf<String>()

        // Rule 1: Platform/GPU coherence
        if (persona.platform == "iOS" && !persona.videoCardRenderer.contains("Apple")) {
            errors.add("CRITICAL: iOS platform requires Apple GPU, got: ${persona.videoCardRenderer}")
        }
        if (persona.platform == "Android" && persona.videoCardRenderer.contains("Apple")) {
            errors.add("CRITICAL: Android platform cannot have Apple GPU")
        }

        // Rule 2: GPU Vendor must be valid
        if (persona.videoCardVendor !in VALID_GPU_VENDORS) {
            errors.add("CRITICAL: Invalid GPU vendor '${persona.videoCardVendor}' - not a known vendor")
        }

        // Rule 3: GPU Renderer must match Vendor pattern
        val expectedPatterns = GPU_VENDOR_RENDERER_PATTERNS[persona.videoCardVendor] ?: emptyList()
        if (expectedPatterns.isNotEmpty()) {
            val matchesPattern = expectedPatterns.any { persona.videoCardRenderer.startsWith(it) }
            if (!matchesPattern) {
                errors.add("CRITICAL: GPU renderer '${persona.videoCardRenderer}' doesn't match vendor '${persona.videoCardVendor}'")
            }
        }

        // Rule 4: Screen DPR math must be coherent
        val expectedLogicWidth = (persona.screenWidth / persona.pixelRatio).roundToInt()
        val actualCalculation = persona.screenWidth / persona.pixelRatio
        if (abs(expectedLogicWidth - actualCalculation) > 0.5) {
            // Check that we can get close to a whole number
            val nearestWhole = actualCalculation.roundToInt()
            val error = abs(actualCalculation - nearestWhole)
            if (error > 0.01) {
                errors.add("WARNING: DPR math produces fractional logical pixels: ${persona.screenWidth}/${persona.pixelRatio} = $actualCalculation")
            }
        }

        // Rule 5: iOS-specific requirements
        if (persona.platform == "iOS") {
            if (persona.brands.isNotEmpty()) {
                errors.add("CRITICAL: iOS Safari does not support Client Hints (brands must be empty)")
            }
            if (persona.platformString != "iPhone" && persona.platformString != "iPad") {
                errors.add("CRITICAL: iOS platformString must be 'iPhone' or 'iPad', got: ${persona.platformString}")
            }
        }

        // Rule 6: Android-specific requirements
        if (persona.platform == "Android") {
            if (persona.platformString != "Linux aarch64" && persona.platformString != "Linux armv7l") {
                errors.add("CRITICAL: Android platformString must be 'Linux aarch64' or 'Linux armv7l', got: ${persona.platformString}")
            }
        }

        return if (errors.isEmpty()) ValidationResult.Pass else ValidationResult.Void(errors)
    }

    /**
     * SOFT RULES - Violation = ADM (automatic defense mode)
     * These are suspicious combinations that may indicate spoofing.
     */
    fun validateSoft(persona: FakePersona): ValidationResult {
        val warnings = mutableListOf<String>()

        // Rule 1: RAM/CPU correlation
        if (persona.ramGB > 8 && persona.cpuCores < 6) {
            warnings.add("Suspicious: High RAM (${persona.ramGB}GB) with low cores (${persona.cpuCores})")
        }
        if (persona.ramGB < 4 && persona.cpuCores > 6) {
            warnings.add("Suspicious: Low RAM (${persona.ramGB}GB) with high cores (${persona.cpuCores})")
        }

        // Rule 2: Flagship tier consistency
        if (persona.isFlagship) {
            if (persona.ramGB < 6) {
                warnings.add("Suspicious: Flagship device with less than 6GB RAM")
            }
            if (persona.audioBaseLatency > 0.015) {
                warnings.add("Suspicious: Flagship device with high audio latency")
            }
            if (persona.webglMaxTextureSize < 8192) {
                warnings.add("Suspicious: Flagship device with low WebGL texture size")
            }
        } else {
            // Budget tier
            if (persona.ramGB > 8) {
                warnings.add("Suspicious: Budget device with more than 8GB RAM")
            }
            if (persona.audioBaseLatency < 0.010) {
                warnings.add("Suspicious: Budget device with flagship-tier audio latency")
            }
        }

        // Rule 3: Locale/Timezone fuzzy correlation
        val tzContinent = persona.timezone.split("/").firstOrNull() ?: ""
        val localeCountry = persona.locale.split("-").lastOrNull()?.uppercase() ?: ""
        
        val suspiciousCombinations = listOf(
            // Asian timezone with US locale is suspicious
            Pair("Asia", "US"),
            // European timezone with Asian locale is suspicious
            Pair("Europe", "CN"),
            Pair("Europe", "JP"),
            Pair("Europe", "KR")
        )
        
        if (suspiciousCombinations.any { it.first == tzContinent && it.second == localeCountry }) {
            warnings.add("Note: Timezone '$tzContinent' with locale '$localeCountry' is unusual")
        }

        // Rule 4: Network correlation
        if (persona.networkType == "5g" && persona.networkDownlink < 10.0) {
            warnings.add("Suspicious: 5G connection with low downlink speed")
        }
        if (persona.networkType == "3g" && persona.networkDownlink > 5.0) {
            warnings.add("Suspicious: 3G connection with high downlink speed")
        }

        return if (warnings.isEmpty()) ValidationResult.Pass else ValidationResult.Adm(warnings)
    }

    /**
     * Full validation - runs both hard and soft rules
     */
    fun validate(persona: FakePersona): ValidationResult {
        // Hard rules first - if any fail, return VOID immediately
        val hardResult = validateHard(persona)
        if (hardResult is ValidationResult.Void) {
            return hardResult
        }

        // Then soft rules - if any fail, return ADM
        val softResult = validateSoft(persona)
        if (softResult is ValidationResult.Adm) {
            return softResult
        }

        return ValidationResult.Pass
    }

    /**
     * Validate a persona and return the appropriate PrivacyState
     */
    fun getValidatedState(persona: FakePersona): PrivacyState {
        return when (validate(persona)) {
            is ValidationResult.Void -> PrivacyState.VOID
            is ValidationResult.Adm -> PrivacyState.ADM
            is ValidationResult.Pass -> PrivacyState.GLOWED
        }
    }
}

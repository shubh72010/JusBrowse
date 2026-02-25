package com.jusdots.jusbrowse.security

/**
 * Normalized hardware state used as input for the AI simulation.
 */
data class HardwareVector(
    val cpuCores: Int,
    val ramGB: Int,
    val batteryLevel: Double,
    val isCharging: Boolean,
    val thermalState: Int, // 0: Normal, 1: Fair, 2: Serious, 3: Critical
    val networkType: String, // "wifi", "cellular", "none"
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * AI-generated simulation data to be injected into the browser.
 */
data class SimulationPacket(
    val driftCurve: List<Double>,   // Numerical drift for timing/sensors
    val noiseProfile: List<Double>,  // High-frequency noise for fingerprinting
    val entropyWindow: Double,       // Variance bounds
    val varianceBounds: Pair<Double, Double>
)

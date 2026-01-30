package com.jusdots.jusbrowse.security

/**
 * Layer 7: Fingerprinting Resistance
 * JavaScript injection to block/spoof common fingerprinting vectors
 */
object FingerprintingProtection {

    /**
     * JavaScript code to inject for fingerprinting protection
     * Should be injected on every page load via evaluateJavascript()
     */
    fun getProtectionScript(seed: Int): String = """
        (function() {
            'use strict';
            const NOISE_SEED = $seed;

            // Mulberry32 PRNG for stable, seeded noise
            const prng = (function(seed) {
                return function() {
                    let t = seed += 0x6D2B79F5;
                    t = Math.imul(t ^ t >>> 15, t | 1);
                    t ^= t + Math.imul(t ^ t >>> 7, t | 61);
                    return ((t ^ t >>> 14) >>> 0) / 4294967296;
                };
            })(NOISE_SEED);

            // Helper to define properties that look real
            const defineSafeProp = (obj, prop, getter) => {
                Object.defineProperty(obj, prop, {
                    get: getter,
                    enumerable: true,
                    configurable: true
                });
            };

            // Block navigator.deviceMemory
            try {
                if (navigator.deviceMemory !== undefined) {
                    defineSafeProp(navigator, 'deviceMemory', () => 8);
                }
            } catch(e) { }
            
            // Block navigator.hardwareConcurrency
            try {
                defineSafeProp(navigator, 'hardwareConcurrency', () => 8);
            } catch(e) { }
            
            // Block Battery API
            try {
                if (navigator.getBattery) {
                    navigator.getBattery = function() {
                        return Promise.reject(new Error('Battery API is disabled'));
                    };
                }
            } catch(e) { }
            
            // WebGL debug info
            try {
                const webglTargets = [WebGLRenderingContext, (typeof WebGL2RenderingContext !== 'undefined' ? WebGL2RenderingContext : null)];
                webglTargets.forEach(t => {
                    if (!t) return;
                    const originalGetParameter = t.prototype.getParameter;
                    t.prototype.getParameter = function(param) {
                        if (param === 37445 || param === 37446) return 'Generic GPU';
                        return originalGetParameter.apply(this, arguments);
                    };
                });
            } catch(e) { }
            
            // Screen resolution
            try {
                // Common mobile-ish bucket for default protection
                defineSafeProp(screen, 'width', () => 412);
                defineSafeProp(screen, 'height', () => 915);
                defineSafeProp(screen, 'availWidth', () => 412);
                defineSafeProp(screen, 'availHeight', () => 915);
            } catch(e) { }
            
            // Timezone
            try {
                Date.prototype.getTimezoneOffset = function() { return 0; };
            } catch(e) { }
            
            // Canvas Seeded Noise
            try {
                const originalGetImageData = CanvasRenderingContext2D.prototype.getImageData;
                CanvasRenderingContext2D.prototype.getImageData = function() {
                    const imageData = originalGetImageData.apply(this, arguments);
                    const buffer = imageData.data;
                    const callPrng = (function(s) { 
                        return function() {
                            let t = s += 0x6D2B79F5;
                            t = Math.imul(t ^ t >>> 15, t | 1);
                            return ((t ^ t >>> 14) >>> 0) / 4294967296;
                        };
                    })(NOISE_SEED);

                    for (let i = 0; i < buffer.length; i += 160) {
                        if (callPrng() > 0.5) buffer[i] = buffer[i] ^ 1;
                    }
                    return imageData;
                };
            } catch(e) { }
            
            // AudioContext noise
            try {
                if (typeof AudioContext !== 'undefined') {
                    const originalCreateAnalyser = AudioContext.prototype.createAnalyser;
                    AudioContext.prototype.createAnalyser = function() {
                        const analyser = originalCreateAnalyser.call(this);
                        const originalGetFloatFrequencyData = analyser.getFloatFrequencyData;
                        analyser.getFloatFrequencyData = function(array) {
                            originalGetFloatFrequencyData.call(this, array);
                            const noiseVal = (prng() * 0.001);
                            for (let i = 0; i < array.length; i++) array[i] += noiseVal;
                        };
                        return analyser;
                    };
                }
            } catch(e) { }
            
            console.log('[JusBrowse] Seeded protection enabled');
        })();
    """.trimIndent()

    /**
     * Minimal protection script for performance-sensitive scenarios
     */
    val minimalProtectionScript: String = """
        (function() {
            'use strict';
            // Block Battery API
            if (navigator.getBattery) {
                navigator.getBattery = function() {
                    return Promise.reject(new Error('Battery API is disabled'));
                };
            }
            // Block Vibration API
            if (navigator.vibrate) {
                navigator.vibrate = function() { return false; };
            }
        })();
    """.trimIndent()
}

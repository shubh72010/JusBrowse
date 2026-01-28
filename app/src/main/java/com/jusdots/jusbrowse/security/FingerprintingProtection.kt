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
    val protectionScript: String = """
        (function() {
            'use strict';
            
            // Block navigator.deviceMemory
            try {
                if (navigator.deviceMemory !== undefined) {
                    Object.defineProperty(navigator, 'deviceMemory', {
                        get: function() { return 8; }, // Report standard value
                        configurable: false
                    });
                }
            } catch(e) { }
            
            // Block navigator.hardwareConcurrency spoofing
            try {
                Object.defineProperty(navigator, 'hardwareConcurrency', {
                    get: function() { return 4; }, // Report standard value
                    configurable: false
                });
            } catch(e) { }
            
            // Block Battery API
            try {
                if (navigator.getBattery) {
                    navigator.getBattery = function() {
                        return Promise.reject(new Error('Battery API is disabled'));
                    };
                }
            } catch(e) { }
            
            // Block Vibration API
            try {
                if (navigator.vibrate) {
                    navigator.vibrate = function() { return false; };
                }
            } catch(e) { }
            
            // Block WebGL debug info (WEBGL_debug_renderer_info)
            try {
                const getParameterOriginal = WebGLRenderingContext.prototype.getParameter;
                WebGLRenderingContext.prototype.getParameter = function(parameter) {
                    // UNMASKED_VENDOR_WEBGL = 37445, UNMASKED_RENDERER_WEBGL = 37446
                    if (parameter === 37445 || parameter === 37446) {
                        return 'Generic GPU';
                    }
                    return getParameterOriginal.call(this, parameter);
                };
            } catch(e) { }
            
            // Also block for WebGL2
            try {
                if (typeof WebGL2RenderingContext !== 'undefined') {
                    const getParameter2Original = WebGL2RenderingContext.prototype.getParameter;
                    WebGL2RenderingContext.prototype.getParameter = function(parameter) {
                        if (parameter === 37445 || parameter === 37446) {
                            return 'Generic GPU';
                        }
                        return getParameter2Original.call(this, parameter);
                    };
                }
            } catch(e) { }
            
            // Block screen resolution fingerprinting (report common values)
            try {
                Object.defineProperty(screen, 'width', { get: function() { return 1920; } });
                Object.defineProperty(screen, 'height', { get: function() { return 1080; } });
                Object.defineProperty(screen, 'availWidth', { get: function() { return 1920; } });
                Object.defineProperty(screen, 'availHeight', { get: function() { return 1040; } });
                Object.defineProperty(screen, 'colorDepth', { get: function() { return 24; } });
                Object.defineProperty(screen, 'pixelDepth', { get: function() { return 24; } });
            } catch(e) { }
            
            // Block timezone fingerprinting (report UTC)
            try {
                const originalGetTimezoneOffset = Date.prototype.getTimezoneOffset;
                Date.prototype.getTimezoneOffset = function() {
                    return 0; // UTC
                };
            } catch(e) { }
            
            // Block canvas fingerprinting (add subtle noise)
            try {
                const originalToDataURL = HTMLCanvasElement.prototype.toDataURL;
                HTMLCanvasElement.prototype.toDataURL = function(type, quality) {
                    // Add subtle noise to canvas output
                    const ctx = this.getContext('2d');
                    if (ctx) {
                        const imageData = ctx.getImageData(0, 0, this.width, this.height);
                        for (let i = 0; i < imageData.data.length; i += 4) {
                            // Add subtle random noise to RGB values
                            imageData.data[i] = Math.max(0, Math.min(255, imageData.data[i] + (Math.random() * 2 - 1)));
                        }
                        ctx.putImageData(imageData, 0, 0);
                    }
                    return originalToDataURL.call(this, type, quality);
                };
            } catch(e) { }
            
            // Block AudioContext fingerprinting
            try {
                if (typeof AudioContext !== 'undefined') {
                    const originalCreateAnalyser = AudioContext.prototype.createAnalyser;
                    AudioContext.prototype.createAnalyser = function() {
                        const analyser = originalCreateAnalyser.call(this);
                        const originalGetFloatFrequencyData = analyser.getFloatFrequencyData;
                        analyser.getFloatFrequencyData = function(array) {
                            originalGetFloatFrequencyData.call(this, array);
                            // Add noise
                            for (let i = 0; i < array.length; i++) {
                                array[i] += Math.random() * 0.001;
                            }
                        };
                        return analyser;
                    };
                }
            } catch(e) { }
            
            // Block connection type fingerprinting
            try {
                if (navigator.connection) {
                    Object.defineProperty(navigator.connection, 'effectiveType', {
                        get: function() { return '4g'; }
                    });
                    Object.defineProperty(navigator.connection, 'downlink', {
                        get: function() { return 10; }
                    });
                }
            } catch(e) { }
            
            console.log('[JusBrowse] Fingerprinting protection enabled');
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

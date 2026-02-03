package com.jusdots.jusbrowse.security

/**
 * Randomiser Engine: The Chameleon.
 * Inspired by Brave's fingerprinting protection (Farbling).
 * Adds seeded noise to APIs to break fingerprint stability and prevent cross-domain tracking.
 */
object RandomiserEngine {

    /**
     * Generate a script that adds noise to various fingerprinting vectors.
     * Use a per-session seed combined with domain-specific entropy.
     */
    fun generateScript(sessionSeed: Int): String = """
        (function() {
            'use strict';
            
            // 1. Core PRNG & Domain-Aware Seeding
            const sessionSeed = $sessionSeed;
            const hostname = window.location.hostname || 'unknown';
            
            // Simple hash for domain
            let hash = 0;
            for (let i = 0; i < hostname.length; i++) {
                hash = ((hash << 5) - hash) + hostname.charCodeAt(i);
                hash |= 0;
            }
            
            const combinedSeed = Math.abs(sessionSeed ^ hash);
            
            // Mulberry32 PRNG
            const prng = (function(s) {
                return function() {
                    let t = s += 0x6D2B79F5;
                    t = Math.imul(t ^ t >>> 15, t | 1);
                    t ^= t + Math.imul(t ^ t >>> 7, t | 61);
                    return ((t ^ t >>> 14) >>> 0) / 4294967296;
                };
            })(combinedSeed);

            const defineSafeProp = (obj, prop, getter) => {
                try {
                    Object.defineProperty(obj, prop, {
                        get: getter,
                        enumerable: true,
                        configurable: true
                    });
                } catch (e) {}
            };

            // 2. Navigator Property Farbling
            try {
                // Device Memory & Hardware Concurrency: Standardizing and slight randomization
                const coreChoices = [2, 4, 8];
                const pseudoCores = coreChoices[Math.floor(prng() * coreChoices.length)];
                defineSafeProp(navigator, 'hardwareConcurrency', () => pseudoCores);
                if (navigator.deviceMemory) {
                    defineSafeProp(navigator, 'deviceMemory', () => 8);
                }
                
                // User Agent: Append random spaces (Brave trick)
                const originalUA = navigator.userAgent;
                const spacesCount = Math.floor(prng() * 5);
                const tweakedUA = originalUA + ' '.repeat(spacesCount);
                defineSafeProp(navigator, 'userAgent', () => tweakedUA);
                
                // Media Devices: Shuffle the order
                if (navigator.mediaDevices && navigator.mediaDevices.enumerateDevices) {
                    const originalEnumerate = navigator.mediaDevices.enumerateDevices.bind(navigator.mediaDevices);
                    navigator.mediaDevices.enumerateDevices = async function() {
                        const devices = await originalEnumerate();
                        return devices.sort(() => prng() - 0.5);
                    };
                }
                
                defineSafeProp(navigator, 'webdriver', () => false);
            } catch(e) {}

            // 3. Canvas Farbling (Noise Injection)
            try {
                const addNoise = (buffer) => {
                    for (let i = 0; i < buffer.length; i += 128) {
                        const noise = (prng() * 2) - 1;
                        buffer[i] = Math.max(0, Math.min(255, buffer[i] + Math.round(noise)));
                    }
                };

                // Hook getImageData
                const originalGetImageData = CanvasRenderingContext2D.prototype.getImageData;
                CanvasRenderingContext2D.prototype.getImageData = function() {
                    const imageData = originalGetImageData.apply(this, arguments);
                    addNoise(imageData.data);
                    return imageData;
                };

                // Brave-style noise for toDataURL and toBlob
                const injectSubtleNoise = (canvas) => {
                    const ctx = canvas.getContext('2d');
                    if (ctx) {
                        const originalAlpha = ctx.globalAlpha;
                        ctx.globalAlpha = 0.01;
                        ctx.fillStyle = 'rgba(255,255,255,0.01)';
                        ctx.fillRect(Math.floor(prng() * canvas.width), Math.floor(prng() * canvas.height), 1, 1);
                        ctx.globalAlpha = originalAlpha;
                    }
                };

                const originalToDataURL = HTMLCanvasElement.prototype.toDataURL;
                HTMLCanvasElement.prototype.toDataURL = function() {
                    injectSubtleNoise(this);
                    return originalToDataURL.apply(this, arguments);
                };

                const originalToBlob = HTMLCanvasElement.prototype.toBlob;
                HTMLCanvasElement.prototype.toBlob = function() {
                    injectSubtleNoise(this);
                    return originalToBlob.apply(this, arguments);
                };
            } catch(e) {}

            // 4. WebGL Farbling
            try {
                const webglTargets = [WebGLRenderingContext, (typeof WebGL2RenderingContext !== 'undefined' ? WebGL2RenderingContext : null)];
                webglTargets.forEach(t => {
                    if (!t) return;
                    
                    // Parameter Spoofing (Unmasked Vendor/Renderer)
                    const originalGetParameter = t.prototype.getParameter;
                    t.prototype.getParameter = function(param) {
                        if (param === 37445) return 'Google Inc. (Intel)'; // VENDOR
                        if (param === 37446) return 'ANGLE (Intel, Intel(R) UHD Graphics (0x00009BC4) Direct3D11 vs_5_0 ps_5_0, D3D11)'; // RENDERER
                        return originalGetParameter.apply(this, arguments);
                    };

                    // readPixels noise
                    const originalReadPixels = t.prototype.readPixels;
                    t.prototype.readPixels = function() {
                        originalReadPixels.apply(this, arguments);
                        const buffer = arguments[6]; // pixels argument
                        if (buffer instanceof Uint8Array || buffer instanceof Uint8ClampedArray) {
                            for (let i = 0; i < buffer.length; i += 256) {
                                buffer[i] = (buffer[i] + (prng() > 0.5 ? 1 : -1)) & 0xFF;
                            }
                        }
                    };
                });
            } catch(e) {}

            // 5. Audio Farbling
            try {
                const originalGetChannelData = AudioBuffer.prototype.getChannelData;
                AudioBuffer.prototype.getChannelData = function() {
                    const data = originalGetChannelData.apply(this, arguments);
                    for (let i = 0; i < data.length; i += 100) {
                        data[i] += (prng() - 0.5) * 0.00001;
                    }
                    return data;
                };

                if (window.AnalyserNode) {
                    const originalGetByteFreq = AnalyserNode.prototype.getByteFrequencyData;
                    AnalyserNode.prototype.getByteFrequencyData = function(array) {
                        originalGetByteFreq.apply(this, arguments);
                        for (let i = 0; i < array.length; i += 16) {
                            array[i] = (array[i] + (prng() > 0.5 ? 1 : -1)) & 0xFF;
                        }
                    };
                }
            } catch(e) {}

            // 6. Battery API Blocking (Static full)
            if (navigator.getBattery) {
                const mockBattery = {
                    level: 1,
                    charging: true,
                    chargingTime: 0,
                    dischargingTime: Infinity,
                    addEventListener: () => {},
                    removeEventListener: () => {}
                };
                navigator.getBattery = () => Promise.resolve(mockBattery);
            }

            // 7. Standardize Plugins (Minimal Chrome-like)
            try {
                const fakePlugins = [
                    { name: 'PDF Viewer', filename: 'internal-pdf-viewer', description: 'Portable Document Format' },
                    { name: 'Chrome PDF Viewer', filename: 'internal-pdf-viewer', description: 'Portable Document Format' },
                    { name: 'Chromium PDF Viewer', filename: 'internal-pdf-viewer', description: 'Portable Document Format' },
                    { name: 'Microsoft Edge PDF Viewer', filename: 'internal-pdf-viewer', description: 'Portable Document Format' },
                    { name: 'WebKit built-in PDF', filename: 'internal-pdf-viewer', description: 'Portable Document Format' }
                ];
                defineSafeProp(navigator, 'plugins', () => fakePlugins);
                defineSafeProp(navigator, 'mimeTypes', () => []);
            } catch(e) {}

            // 8. Screen Resolution Jitter
            try {
                const w = screen.width;
                const h = screen.height;
                // Add tiny jitter to availWidth/availHeight to break exact matches
                const jitterW = Math.floor(prng() * 10);
                const jitterH = Math.floor(prng() * 10);
                defineSafeProp(screen, 'availWidth', () => w - jitterW);
                defineSafeProp(screen, 'availHeight', () => h - jitterH);
                defineSafeProp(screen, 'colorDepth', () => 24);
                defineSafeProp(screen, 'pixelDepth', () => 24);
            } catch(e) {}

            console.log('[JusBrowse] Randomiser Engine (Brave Farbling) Active for domain: ' + hostname);
        })();
    """.trimIndent()
}

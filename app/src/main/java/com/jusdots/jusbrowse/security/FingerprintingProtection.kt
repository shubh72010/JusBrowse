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
    fun getProtectionScript(seed: Int, whitelist: List<String> = emptyList()): String {
        val whitelistJson = whitelist.joinToString(",", "[", "]") { "'$it'" }
        return """
        (function() {
            'use strict';
            const whitelist = $whitelistJson;
            if (whitelist.some(domain => window.location.hostname.endsWith(domain))) {
                return;
            }
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

            // --- UTILS: Native Function Masquerade ---
            const originalToString = Function.prototype.toString;
            const fakeToStringSymbol = Symbol('fakeToString');
            
            const makeNative = (fn, name) => {
                if (name) {
                    try { Object.defineProperty(fn, 'name', { value: name, configurable: true }); } catch(e) {}
                }
                const nativeString = `function ${"$"}{name || fn.name || ''}() { [native code] }`;
                fn[fakeToStringSymbol] = nativeString;
                return fn;
            };

            Function.prototype.toString = makeNative(function() {
                if (typeof this === 'function' && this[fakeToStringSymbol]) {
                    return this[fakeToStringSymbol];
                }
                return originalToString.call(this);
            }, 'toString');

            const defineSafeProp = (obj, prop, getter) => {
                Object.defineProperty(obj, prop, {
                    get: makeNative(getter, 'get ' + prop),
                    enumerable: true,
                    configurable: true
                });
            };

            // --- 0. API OCCLUSION (Fix 16) ---
            if (navigator.gpu) {
                try { Object.defineProperty(navigator, 'gpu', { value: undefined }); } catch(e) {}
            }
            if (window.SpeechRecognition || window.webkitSpeechRecognition) {
                window.SpeechRecognition = undefined;
                window.webkitSpeechRecognition = undefined;
            }
            if (window.speechSynthesis) {
                try { Object.defineProperty(window, 'speechSynthesis', { value: undefined }); } catch(e) {}
            }

            // Block navigator.deviceMemory
            try {
                defineSafeProp(navigator, 'deviceMemory', () => 8);
            } catch(e) { }
            
            // Block navigator.hardwareConcurrency
            try {
                defineSafeProp(navigator, 'hardwareConcurrency', () => 8);
            } catch(e) { }
            
            // Block Battery API
            try {
                if (navigator.getBattery) {
                    const batteryMock = {
                        charging: true,
                        chargingTime: 3600,
                        dischargingTime: Infinity,
                        level: 1.0,
                        addEventListener: makeNative(() => {}, 'addEventListener'),
                        removeEventListener: makeNative(() => {}, 'removeEventListener'),
                    };
                    navigator.getBattery = makeNative(() => Promise.resolve(batteryMock), 'getBattery');
                }
            } catch(e) { }
            
            // WebGL debug info
            try {
                const webglTargets = [WebGLRenderingContext, (typeof WebGL2RenderingContext !== 'undefined' ? WebGL2RenderingContext : null)];
                webglTargets.forEach(t => {
                    if (!t) return;
                    const originalGetParameter = t.prototype.getParameter;
                    t.prototype.getParameter = makeNative(function(parameter) {
                        if (parameter === 37445) return 'Google Inc. (ARM)'; // VENDOR
                        if (parameter === 37446) return 'Google Inc. (ARM)'; // UNMASKED_VENDOR
                        if (parameter === 37447) return 'ANGLE (ARM, Mali-G715, OpenGL ES 3.2)'; // UNMASKED_RENDERER
                        return originalGetParameter.apply(this, arguments);
                    }, 'getParameter');
                });
            } catch(e) { }
            
            // Screen resolution
            try {
                // Pixel 7a logical pixels
                defineSafeProp(screen, 'width', () => 412);
                defineSafeProp(screen, 'height', () => 915);
                defineSafeProp(screen, 'availWidth', () => 412);
                defineSafeProp(screen, 'availHeight', () => 915);
                defineSafeProp(window, 'innerHeight', () => 915);
                defineSafeProp(window, 'innerWidth', () => 412);
            } catch(e) { }
            
            // Timezone (Fix 13)
            try {
                const originalGetTimezoneOffset = Date.prototype.getTimezoneOffset;
                Date.prototype.getTimezoneOffset = makeNative(function() { return 0; }, 'getTimezoneOffset');
            } catch(e) { }
            
            // Canvas Seeded Noise
            try {
                const originalGetImageData = CanvasRenderingContext2D.prototype.getImageData;
                CanvasRenderingContext2D.prototype.getImageData = makeNative(function() {
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
                }, 'getImageData');
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
            
            // WebRTC Local IP Masking
            try {
                if (window.RTCPeerConnection) {
                    const OriginalRPC = window.RTCPeerConnection;
                    const maskSDP = (sdp) => {
                        if (!sdp) return sdp;
                        return sdp.split('\r\n').map(line => {
                            if (line.startsWith('a=candidate') && (line.includes('192.168.') || line.includes('10.') || line.includes('172.'))) {
                                return line.replace(/(\s)(192\.168\.|10\.|172\.(1[6-9]|2[0-9]|3[0-1])\.)[^\s]+(\s)/, "$1192.168.1.1$4");
                            }
                            if (line.startsWith('c=IN IP4') && (line.includes('192.168.') || line.includes('10.') || line.includes('172.'))) {
                                return 'c=IN IP4 192.168.1.1';
                            }
                            return line;
                        }).join('\r\n');
                    };

                    window.RTCPeerConnection = function(config) {
                        const pc = new OriginalRPC(config);
                        const wrap = (fn) => function() {
                            return fn.apply(this, arguments).then(d => {
                                if (d && d.sdp) d.sdp = maskSDP(d.sdp);
                                return d;
                            });
                        };
                        pc.createOffer = wrap(pc.createOffer);
                        pc.createAnswer = wrap(pc.createAnswer);
                        const origSetLocal = pc.setLocalDescription;
                        pc.setLocalDescription = function(d) {
                            if (d && d.sdp) d.sdp = maskSDP(d.sdp);
                            return origSetLocal.apply(this, arguments);
                        };
                        return pc;
                    };
                    window.RTCPeerConnection.prototype = OriginalRPC.prototype;
                }
            } catch(e) { }

            // Protection enabled — no logging to prevent fingerprinting
        })();
    """.trimIndent()
    }

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

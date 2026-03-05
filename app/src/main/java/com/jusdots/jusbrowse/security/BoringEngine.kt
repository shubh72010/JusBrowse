package com.jusdots.jusbrowse.security

import android.annotation.SuppressLint

/**
 * Boring Engine: The "Head Tracking" Model.
 *
 * Implements a "Stable, Boring, Session-Locked" identity.
 * - Variances are derived *only* from the sessionSeed.
 * - No random jitter (same input = same output).
 * - Occludes high-entropy/impossible APIs (WebGPU, WebSpeech).
 * - Matches real Android WebView behavior where possible.
 */
object BoringEngine {

    /**
     * Pillar 1: Synchronize the Stage (User-Agent & Client Hints)
     * Generates metadata for Chrome v145 on Android 14 (Pixel 8 Pro).
     */
    @SuppressLint("RestrictedApi")
    fun getUserAgentMetadata(webViewVersion: String): androidx.webkit.UserAgentMetadata {
        return androidx.webkit.UserAgentMetadata.Builder()
            .setBrandVersionList(listOf(
                androidx.webkit.UserAgentMetadata.BrandVersion.Builder()
                    .setBrand("Google Chrome")
                    .setFullVersion("133.0.0.0")
                    .setMajorVersion("133")
                    .build(),
                androidx.webkit.UserAgentMetadata.BrandVersion.Builder()
                    .setBrand("Not:A-Brand")
                    .setFullVersion("99.0.0.0")
                    .setMajorVersion("99")
                    .build(),
                androidx.webkit.UserAgentMetadata.BrandVersion.Builder()
                    .setBrand("Chromium")
                    .setFullVersion("133.0.0.0")
                    .setMajorVersion("133")
                    .build()
            ))
            .setFullVersion("133.0.0.0")
            .setPlatform("Android")
            .setPlatformVersion("15.0.0")
            .setArchitecture("arm")
            .setModel("Pixel 7a")
            .setMobile(true)
            .setBitness(64)
            .build()
    }

    /**
     * Pillar 1: Static User-Agent String matching metadata.
     */
    fun getFormattedUserAgent(webViewVersion: String): String {
        return "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Mobile Safari/537.36"
    }

    /**
     * Generate the "Boring" injection script.
     * @param sessionSeed A persistent seed for this session.
     */
    fun generateScript(sessionSeed: Long, webViewVersion: String, normalizeLanguage: Boolean = true): String {
        return """
            (function() {
                'use strict';
                
                const SESSION_SEED = $sessionSeed;
                
                // --- PRNG: Improved seeded random for noise ---
                function seededRandom(index) {
                    let x = SESSION_SEED ^ (index * 1664525 + 1013904223);
                    x = ((x >> 16) ^ x) * 0x45d9f3b;
                    x = ((x >> 16) ^ x) * 0x45d9f3b;
                    x = (x >> 16) ^ x;
                    return (x & 0xFF);
                }

                function getNoise(index, magnitude) {
                    return ((seededRandom(index) / 255) - 0.5) * 2 * magnitude;
                }

                function clamp(v) { return Math.max(0, Math.min(255, Math.round(v))); }

                // --- NATIVE FUNCTION MASQUERADE ---
                const originalToString = Function.prototype.toString;
                const fakeToStringSymbol = Symbol('fakeToString');

                const makeNative = (fn, name) => {
                    if (name) {
                        try { Object.defineProperty(fn, 'name', { value: name, configurable: true }); } catch(e) {}
                    }
                    fn[fakeToStringSymbol] = `function ${'$'}{name || fn.name || ''}() { [native code] }`;
                    return fn;
                };

                const defineSafeProp = (obj, prop, get) => {
                    Object.defineProperty(obj, prop, {
                        get: makeNative(get, `get ${'$'}{prop}`),
                        configurable: true
                    });
                };

                Function.prototype.toString = makeNative(function() {
                    if (typeof this === 'function' && this[fakeToStringSymbol]) {
                        return this[fakeToStringSymbol];
                    }
                    return originalToString.call(this);
                }, 'toString');

                // --- CANVAS ---
                function applyCanvasNoise(data) {
                    for (let i = 0; i < data.length; i += 4) {
                        data[i]     = clamp(data[i]     + getNoise(i,     3));
                        data[i + 1] = clamp(data[i + 1] + getNoise(i + 1, 3));
                        data[i + 2] = clamp(data[i + 2] + getNoise(i + 2, 3));
                    }
                }

                try {
                    const origGetImageData = CanvasRenderingContext2D.prototype.getImageData;
                    CanvasRenderingContext2D.prototype.getImageData = makeNative(function() {
                        try {
                            const imageData = origGetImageData.apply(this, arguments);
                            applyCanvasNoise(imageData.data);
                            return imageData;
                        } catch(e) {
                            return origGetImageData.apply(this, arguments);
                        }
                    }, 'getImageData');
                } catch(e) {}

                try {
                    const origMeasureText = CanvasRenderingContext2D.prototype.measureText;
                    CanvasRenderingContext2D.prototype.measureText = makeNative(function(text) {
                        const metrics = origMeasureText.apply(this, arguments);
                        try {
                            const noise = getNoise(text.length * 7, 0.3);
                            Object.defineProperty(metrics, 'width', {
                                value: metrics.width + noise,
                                writable: false, configurable: true
                            });
                        } catch(e) {}
                        return metrics;
                    }, 'measureText');
                } catch(e) {}

                // --- WEBGL ---
                const FAKE_VENDOR   = "Google Inc. (ARM)";
                const FAKE_RENDERER = "ANGLE (ARM, Mali-G715, OpenGL ES 3.2)";

                function patchWebGL(proto) {
                    try {
                        const origGetParam = proto.getParameter;
                        proto.getParameter = makeNative(function(param) {
                            try {
                                if (param === 0x9245) return FAKE_VENDOR;
                                if (param === 0x9246) return FAKE_RENDERER;
                                if (param === 0x1F01) return "WebKit WebGL";
                                if (param === 0x1F00) return "WebKit";
                                return origGetParam.apply(this, arguments);
                            } catch(e) { return origGetParam.apply(this, arguments); }
                        }, 'getParameter');
                    } catch(e) {}

                    try {
                        const origGetExtension = proto.getExtension;
                        proto.getExtension = makeNative(function(name) {
                            if (name === 'WEBGL_debug_renderer_info') return null;
                            return origGetExtension.apply(this, arguments);
                        }, 'getExtension');
                    } catch(e) {}

                    try {
                        const origGetSupportedExtensions = proto.getSupportedExtensions;
                        proto.getSupportedExtensions = makeNative(function() {
                            const exts = origGetSupportedExtensions.apply(this, arguments) || [];
                            return exts.filter(e => e !== 'WEBGL_debug_renderer_info');
                        }, 'getSupportedExtensions');
                    } catch(e) {}

                    try {
                        const origReadPixels = proto.readPixels;
                        proto.readPixels = makeNative(function(x, y, width, height, format, type, pixels) {
                            try {
                                origReadPixels.apply(this, arguments);
                                if (pixels instanceof Uint8Array) {
                                    for (let i = 0; i < pixels.length; i += 4) {
                                        pixels[i]     = clamp(pixels[i]     + getNoise(i,     3));
                                        pixels[i + 1] = clamp(pixels[i + 1] + getNoise(i + 1, 3));
                                        pixels[i + 2] = clamp(pixels[i + 2] + getNoise(i + 2, 3));
                                    }
                                }
                            } catch(e) { origReadPixels.apply(this, arguments); }
                        }, 'readPixels');
                    } catch(e) {}

                    try {
                        const origGetShaderPrecisionFormat = proto.getShaderPrecisionFormat;
                        proto.getShaderPrecisionFormat = makeNative(function() {
                            try {
                                const result = origGetShaderPrecisionFormat.apply(this, arguments);
                                if (result) return { rangeMin: 127, rangeMax: 127, precision: 23 };
                                return result;
                            } catch(e) { return origGetShaderPrecisionFormat.apply(this, arguments); }
                        }, 'getShaderPrecisionFormat');
                    } catch(e) {}
                }

                if (window.WebGLRenderingContext)  patchWebGL(WebGLRenderingContext.prototype);
                if (window.WebGL2RenderingContext) patchWebGL(WebGL2RenderingContext.prototype);

                // --- AUDIO ---
                try {
                    const AudioContext = window.AudioContext || window.webkitAudioContext;
                    if (AudioContext) {
                        const origCreateAnalyser = AudioContext.prototype.createAnalyser;
                        AudioContext.prototype.createAnalyser = makeNative(function() {
                            const analyser = origCreateAnalyser.apply(this, arguments);
                            const origGetFloat = analyser.getFloatFrequencyData.bind(analyser);
                            analyser.getFloatFrequencyData = makeNative(function(array) {
                                origGetFloat(array);
                                for (let i = 0; i < array.length; i++) {
                                    array[i] += getNoise(i, 0.1);
                                }
                            }, 'getFloatFrequencyData');
                            return analyser;
                        }, 'createAnalyser');
                    }
                } catch(e) {}

                // --- SCREEN & VIEWPORT (Synchronized Dynamic Bucketing) ---
                try {
                    const COMMON_W = [360, 400];
                    const COMMON_H = [600, 700, 800, 900];
                    const snap = (v, list) => list.reduce((p, c) => Math.abs(c - v) < Math.abs(p - v) ? c : p);

                    // Capture raw values BEFORE overrides to prevent infinite recursion
                    const rawWidth = window.screen.width;
                    const rawHeight = window.screen.height;
                    const rawAvailWidth = window.screen.availWidth;
                    const rawAvailHeight = window.screen.availHeight;
                    const rawInnerWidth = window.innerWidth;
                    const rawInnerHeight = window.innerHeight;
                    const rawOuterWidth = window.outerWidth;
                    const rawOuterHeight = window.outerHeight;

                    const getMetrics = () => {
                        const isPortrait = rawWidth < rawHeight;
                        const sW = isPortrait ? snap(rawWidth, COMMON_W) : snap(rawWidth, COMMON_H);
                        const sH = isPortrait ? snap(rawHeight, COMMON_H) : snap(rawHeight, COMMON_W);
                        
                        // Maintain aspect ratio for viewport/inner metrics relative to screen
                        const ratioW = rawInnerWidth / (rawWidth || 1);
                        const ratioH = rawInnerHeight / (rawHeight || 1);
                        
                        return {
                            width: sW,
                            height: sH,
                            availWidth: sW,
                            availHeight: sH,
                            innerWidth: Math.round(sW * ratioW),
                            innerHeight: Math.round(sH * ratioH),
                            outerWidth: sW,
                            outerHeight: sH,
                            type: isPortrait ? 'portrait-primary' : 'landscape-primary'
                        };
                    };

                    const metrics = getMetrics();

                    const props = ['width', 'height', 'availWidth', 'availHeight'];
                    props.forEach(prop => {
                        Object.defineProperty(screen, prop, {
                            get: makeNative(() => metrics[prop], `get ${'$'}{prop}`),
                            configurable: true
                        });
                    });

                    // Zero out multi-monitor offsets
                    ['availLeft', 'availTop', 'left', 'top'].forEach(prop => {
                        Object.defineProperty(screen, prop, { get: makeNative(() => 0, `get ${'$'}{prop}`), configurable: true });
                    });

                    Object.defineProperty(screen, 'colorDepth', { get: makeNative(() => 24, 'get colorDepth'), configurable: true });
                    Object.defineProperty(screen, 'pixelDepth', { get: makeNative(() => 24, 'get pixelDepth'), configurable: true });

                    const winProps = ['innerWidth', 'innerHeight', 'outerWidth', 'outerHeight'];
                    winProps.forEach(prop => {
                        Object.defineProperty(window, prop, {
                            get: makeNative(() => metrics[prop], `get ${'$'}{prop}`),
                            configurable: true
                        });
                    });

                    if (screen.orientation) {
                        defineSafeProp(screen.orientation, 'type', () => metrics.type);
                        defineSafeProp(screen.orientation, 'angle', () => 0);
                    }

                    if (window.visualViewport) {
                        ['width', 'height'].forEach(prop => {
                            Object.defineProperty(window.visualViewport, prop, {
                                get: makeNative(() => metrics[prop === 'width' ? 'innerWidth' : 'innerHeight'], `get ${'$'}{prop}`),
                                configurable: true
                            });
                        });
                        ['scale'].forEach(prop => {
                            Object.defineProperty(window.visualViewport, prop, { get: makeNative(() => 1, `get ${'$'}{prop}`), configurable: true });
                        });
                    }
                } catch(e) {}

                // --- HARDWARE ---
                try {
                    Object.defineProperty(navigator, 'hardwareConcurrency', { get: makeNative(() => 4, 'get hardwareConcurrency'), configurable: true });
                    Object.defineProperty(navigator, 'deviceMemory',        { get: makeNative(() => 4, 'get deviceMemory'),        configurable: true });
                    Object.defineProperty(navigator, 'platform',            { get: makeNative(() => 'Linux armv8l', 'get platform'), configurable: true });
                    Object.defineProperty(navigator, 'webdriver',           { get: makeNative(() => false, 'get webdriver'),       configurable: true });
                    Object.defineProperty(navigator, 'pdfViewerEnabled',    { get: makeNative(() => true, 'get pdfViewerEnabled'),  configurable: true });
                    if ($normalizeLanguage) {
                        Object.defineProperty(navigator, 'language',  { get: makeNative(() => 'en-US', 'get language'), configurable: true });
                        Object.defineProperty(navigator, 'languages', { get: makeNative(() => ['en-US', 'en'], 'get languages'), configurable: true });
                    }
                } catch(e) {}

                // --- API OCCLUSION ---
                try { if (navigator.gpu) Object.defineProperty(navigator, 'gpu', { value: undefined, configurable: true }); } catch(e) {}
                try { window.SpeechRecognition = undefined; window.webkitSpeechRecognition = undefined; } catch(e) {}
                try { Object.defineProperty(window, 'speechSynthesis', { value: undefined, configurable: true }); } catch(e) {}

                // --- WEBRTC ---
                try {
                    if (window.RTCPeerConnection) {
                        const OriginalRPC = window.RTCPeerConnection;
                        const maskSDP = (sdp) => {
                            if (!sdp) return sdp;
                            return sdp.split('\r\n').map(line => {
                                if (line.startsWith('a=candidate') &&
                                   (line.includes('192.168.') || line.includes('10.') || line.includes('172.'))) {
                                    return line.replace(/(\s)(192\.168\.|10\.|172\.(1[6-9]|2[0-9]|3[0-1])\.)[^\s]+(\s)/, '$1192.168.1.1$4');
                                }
                                if (line.startsWith('c=IN IP4') &&
                                   (line.includes('192.168.') || line.includes('10.') || line.includes('172.'))) {
                                    return 'c=IN IP4 192.168.1.1';
                                }
                                return line;
                            }).join('\r\n');
                        };

                        window.RTCPeerConnection = makeNative(function(config) {
                            const pc = new OriginalRPC(config);
                            const wrap = (fn) => makeNative(function() {
                                return fn.apply(this, arguments).then(d => {
                                    if (d && d.sdp) d.sdp = maskSDP(d.sdp);
                                    return d;
                                });
                            }, fn.name);
                            pc.createOffer  = wrap(pc.createOffer);
                            pc.createAnswer = wrap(pc.createAnswer);
                            const origSetLocal = pc.setLocalDescription;
                            pc.setLocalDescription = makeNative(function(d) {
                                if (d && d.sdp) d.sdp = maskSDP(d.sdp);
                                return origSetLocal.apply(this, arguments);
                            }, 'setLocalDescription');
                            return pc;
                        }, 'RTCPeerConnection');
                        window.RTCPeerConnection.prototype = OriginalRPC.prototype;
                    }
                } catch(e) {}

                // --- CLIENT HINTS ---
                try {
                    if (navigator.userAgentData) {
                        const brands = [
                            {brand: 'Google Chrome', version: '133'},
                            {brand: 'Not:A-Brand', version: '99'},
                            {brand: 'Chromium', version: '133'}
                        ];
                        defineSafeProp(navigator.userAgentData, 'brands', () => brands);
                        defineSafeProp(navigator.userAgentData, 'mobile', () => true);
                        defineSafeProp(navigator.userAgentData, 'platform', () => 'Android');
                        
                        navigator.userAgentData.getHighEntropyValues = makeNative((hints) => {
                            return Promise.resolve({
                                brands: brands,
                                mobile: true,
                                platform: 'Android',
                                platformVersion: "15.0.0",
                                model: "Pixel 7a",
                                uaFullVersion: "133.0.0.0",
                                architecture: 'arm',
                                bitness: '64'
                            });
                        }, 'getHighEntropyValues');
                    }
                } catch(e) {}

                // --- MATCHMEDIA NORMALIZATION ---
                try {
                    const originalMatchMedia = window.matchMedia;
                    const bucket = (v, s) => Math.round(v / s) * s;
                    window.matchMedia = makeNative(function(query) {
                        if (query.includes('width') || query.includes('height')) {
                            const m = originalMatchMedia.call(window, query);
                            const bucketSize = 50;
                            const fakeMatch = (q) => {
                                const widthMatch = q.match(/(min-width|max-width|width):\s*(\d+)px/);
                                if (widthMatch) {
                                    const type = widthMatch[1];
                                    const val = parseInt(widthMatch[2]);
                                    const bucketedWidth = bucket(window.innerWidth, bucketSize);
                                    if (type === 'width') return bucketedWidth === val;
                                    if (type === 'min-width') return bucketedWidth >= val;
                                    if (type === 'max-width') return bucketedWidth <= val;
                                }
                                const heightMatch = q.match(/(min-height|max-height|height):\s*(\d+)px/);
                                if (heightMatch) {
                                    const type = heightMatch[1];
                                    const val = parseInt(heightMatch[2]);
                                    const bucketedHeight = bucket(window.innerHeight, bucketSize);
                                    if (type === 'height') return bucketedHeight === val;
                                    if (type === 'min-height') return bucketedHeight >= val;
                                    if (type === 'max-height') return bucketedHeight <= val;
                                }
                                return m.matches;
                            };
                            return {
                                matches: fakeMatch(query),
                                media: query,
                                onchange: null,
                                addListener: makeNative(function() {}, 'addListener'),
                                removeListener: makeNative(function() {}, 'removeListener'),
                                addEventListener: makeNative(function() {}, 'addEventListener'),
                                removeEventListener: makeNative(function() {}, 'removeEventListener'),
                                dispatchEvent: makeNative(function() { return false; }, 'dispatchEvent')
                            };
                        }
                        const result = originalMatchMedia.call(window, query);
                        const fakeResult = (matches) => ({
                            matches: matches,
                            media: query,
                            onchange: null,
                            addListener: makeNative(function() {}, 'addListener'),
                            removeListener: makeNative(function() {}, 'removeListener'),
                            addEventListener: makeNative(function() {}, 'addEventListener'),
                            removeEventListener: makeNative(function() {}, 'removeEventListener'),
                            dispatchEvent: makeNative(function() { return false; }, 'dispatchEvent')
                        });
                        if (query.includes('orientation')) {
                            const isLandscape = window.innerWidth >= window.innerHeight;
                            return fakeResult(query.includes('landscape') ? isLandscape : !isLandscape);
                        }
                        return result;
                    }, 'matchMedia');
                } catch(e) {}

                console.log('[JusBrowse] Fingerprint defense v3 active. Seed:', SESSION_SEED.toString(16));
            })();
        """.trimIndent()
    }
}

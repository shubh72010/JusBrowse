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
    fun generateScript(sessionSeed: Long, webViewVersion: String): String {
        return """
            (function() {
                'use strict';
                
                const SESSION_SEED = $sessionSeed;
                
                // --- CORE: Mulberry32 Deterministic PRNG ---
                const prng = (function(seed) {
                    return function() {
                        let t = seed += 0x6D2B79F5;
                        t = Math.imul(t ^ t >>> 15, t | 1);
                        t ^= t + Math.imul(t ^ t >>> 7, t | 61);
                        return ((t ^ t >>> 14) >>> 0) / 4294967296;
                    };
                })(SESSION_SEED);

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

                // --- PILLAR 4: GATING (API Occlusion) ---
                
                // WebGPU Removal
                if (navigator.gpu) {
                    try { Object.defineProperty(navigator, 'gpu', { value: undefined }); } catch(e) {}
                }

                // WebSpeech Removal (Instruments we cannot safely spoof yet)
                if (window.SpeechRecognition || window.webkitSpeechRecognition) {
                    window.SpeechRecognition = undefined;
                    window.webkitSpeechRecognition = undefined;
                }
                if (window.speechSynthesis) {
                    try { Object.defineProperty(window, 'speechSynthesis', { value: undefined }); } catch(e) {}
                }

                // --- 2. CANVAS API (Seeded Drift) ---
                try {
                    const originalGetImageData = CanvasRenderingContext2D.prototype.getImageData;
                    CanvasRenderingContext2D.prototype.getImageData = makeNative(function(x, y, w, h) {
                        const imageData = originalGetImageData.apply(this, arguments);
                        const buffer = imageData.data;
                        for (let i = 0; i < buffer.length; i += 4) {
                            if ((i + SESSION_SEED) % 103 === 0) { 
                                const channel = (i + SESSION_SEED) % 3;
                                const val = buffer[i + channel];
                                if (val > 0 && val < 255) {
                                     buffer[i + channel] = val + ((SESSION_SEED % 2 === 0) ? 1 : -1);
                                }
                            }
                        }
                        return imageData;
                    }, 'getImageData');
                } catch(e) {}

                // --- 3. AUDIO CONTEXT (Seeded Drift) ---
                try {
                    if (window.OfflineAudioContext) {
                        const OriginalOAC = OfflineAudioContext;
                        window.OfflineAudioContext = makeNative(function(c, l, s) {
                            const ctx = new OriginalOAC(c, l, s);
                            const originalStartRendering = ctx.startRendering.bind(ctx);
                            ctx.startRendering = makeNative(function() {
                                return originalStartRendering().then(buffer => {
                                    const channel = buffer.getChannelData(0);
                                    for (let i = 0; i < channel.length; i+=100) {
                                         const seedOffset = (SESSION_SEED + i) * 1664525 + 1013904223;
                                         const noise = (seedOffset % 100) / 1000000.0;
                                         channel[i] += noise;
                                    }
                                    return buffer;
                                });
                            }, 'startRendering');
                            return ctx;
                        }, 'OfflineAudioContext');
                        window.OfflineAudioContext.prototype = OriginalOAC.prototype;
                    }
                } catch(e) {}

                // --- LAYER 1: CONTEXT AWARENESS ---
                let lastScrollTime = 0;
                window.addEventListener('scroll', () => { lastScrollTime = Date.now(); }, { passive: true });
                
                // Track call frequency for fingerprinting detection
                const callLog = new Map();
                const isHighFrequency = (prop) => {
                    const now = Date.now();
                    const log = callLog.get(prop) || [];
                    const recent = log.filter(t => now - t < 1000);
                    recent.push(now);
                    callLog.set(prop, recent);
                    return recent.length > 15; // >15 calls per second is suspicious
                };

                const getContextualDrift = (el, prop, drift) => {
                    const now = Date.now();
                    const isScrolling = (now - lastScrollTime) < 800;
                    
                    // If we're scrolling and it's a normal frequency call, give clean values
                    // This fixes Kaggle, Notion, and BuiltWith layout engines.
                    if (isScrolling && !isHighFrequency(prop)) {
                        return 0;
                    }
                    
                    // If element is hidden or call is high frequency, it's likely fingerprinting.
                    // Keep the protective drift.
                    if (el && el.isConnected) {
                        try {
                            // Use native style lookup to avoid recursion
                            const style = window.getComputedStyle(el);
                            if (style.display === 'none' || style.visibility === 'hidden' || parseFloat(style.opacity) === 0) {
                                return drift;
                            }
                        } catch(e) {}
                    }
                    
                    return drift;
                };

                const driftX = (SESSION_SEED % 100) / 4000.0; // +/- 0.025px
                const driftY = ((SESSION_SEED >> 4) % 100) / 4000.0;

                // getBoundingClientRect
                try {
                    const lWidth = 412;   // Pixel 7a logical width (1080 / 2.625)
                    const lHeight = 915;  // Pixel 7a logical height (2400 / 2.625)
                    const originalGetBCR = Element.prototype.getBoundingClientRect;
                    Element.prototype.getBoundingClientRect = makeNative(function() {
                        const rect = originalGetBCR.apply(this, arguments);
                        const dx = getContextualDrift(this, 'bcr', driftX);
                        const dy = getContextualDrift(this, 'bcr', driftY);
                        
                        return {
                           x: rect.x + dx, y: rect.y + dy,
                           width: rect.width, height: rect.height,
                           top: rect.top + dy, right: rect.right + dx,
                           bottom: rect.bottom + dy, left: rect.left + dx,
                           toJSON: () => JSON.stringify(this)
                        };
                    }, 'getBoundingClientRect');

                    // Screen/Viewport Mocking
                    const screenProps = {
                        width: lWidth, height: lHeight,
                        availWidth: lWidth, availHeight: lHeight,
                        colorDepth: 24, pixelDepth: 24
                    };
                    for (const prop in screenProps) {
                        defineSafeProp(screen, prop, () => screenProps[prop]);
                    }
                    defineSafeProp(window, 'innerHeight', () => lHeight);
                    defineSafeProp(window, 'innerWidth', () => lWidth);
                    defineSafeProp(window, 'outerHeight', () => lHeight);
                    defineSafeProp(window, 'outerWidth', () => lWidth);
                } catch(e) {}

                // getComputedStyle (Drift on computed dimensions)
                try {
                    const originalGCS = window.getComputedStyle;
                    window.getComputedStyle = makeNative(function(el, pseudo) {
                        const style = originalGCS.call(window, el, pseudo);
                        if (!style) return style;
                        
                        const proxy = new Proxy(style, {
                            get(target, prop) {
                                const val = target[prop];
                                if ((prop === 'width' || prop === 'height' || prop === 'top' || prop === 'left') && typeof val === 'string' && val.endsWith('px')) {
                                    const numeric = parseFloat(val);
                                    const rawDrift = (prop === 'width' || prop === 'left') ? driftX : driftY;
                                    const drift = getContextualDrift(el, 'gcs', rawDrift);
                                    return (numeric + drift) + 'px';
                                }
                                return typeof val === 'function' ? val.bind(target) : val;
                            }
                        });
                        return proxy;
                    }, 'getComputedStyle');
                } catch(e) {}


                // --- 5. BATTERY (Session Fixed) ---
                try {
                    if (navigator.getBattery) {
                        const level = 0.20 + ((SESSION_SEED % 70) / 100.0);
                        const charging = (SESSION_SEED % 2) === 0;
                        const mockBattery = {
                            level: level, charging: charging,
                            chargingTime: charging ? 3600 : Infinity,
                            dischargingTime: charging ? Infinity : 18000,
                            addEventListener: makeNative(() => {}, 'addEventListener'),
                            removeEventListener: makeNative(() => {}, 'removeEventListener'),
                        };
                        navigator.getBattery = makeNative(() => Promise.resolve(mockBattery), 'getBattery');
                    }
                } catch(e) {}

                // --- 6. HARDWARE CONCURRENCY, MEMORY & GPU (Fixed to Pixel 7a) ---
                try {
                    defineSafeProp(navigator, 'hardwareConcurrency', () => 8);
                    defineSafeProp(navigator, 'deviceMemory', () => 8);
                    defineSafeProp(navigator, 'platform', () => 'Linux armv8l');
                    
                    // Spoof WebGL GPU (Tensor G3 / Mali-G715)
                    const getParameter = WebGLRenderingContext.prototype.getParameter;
                    WebGLRenderingContext.prototype.getParameter = makeNative(function(parameter) {
                        if (parameter === 37445) return 'ARM'; // VENDOR
                        if (parameter === 37446) return 'ARM'; // UNMASKED_VENDOR_WEBGL
                        if (parameter === 37447) return 'Mali-G715'; // UNMASKED_RENDERER_WEBGL
                        return getParameter.apply(this, arguments);
                    }, 'getParameter');
                    
                    if (typeof WebGL2RenderingContext !== 'undefined') {
                        const getParameter2 = WebGL2RenderingContext.prototype.getParameter;
                        WebGL2RenderingContext.prototype.getParameter = makeNative(function(parameter) {
                            if (parameter === 37445) return 'ARM';
                            if (parameter === 37446) return 'ARM';
                            if (parameter === 37447) return 'Mali-G715';
                            return getParameter2.apply(this, arguments);
                        }, 'getParameter');
                    }
                } catch(e) {}

                // --- 7. CLIENT HINTS (Ghost Tier Chrome 145) ---
                try {
                    if (navigator.userAgentData) {
                        const brands = [
                            {brand: 'Google Chrome', version: '133'},
                            {brand: 'Not:A-Brand', version: '99'},
                            {brand: 'Chromium', version: '133'}
                        ];
                        const mockData = {
                            brands: brands,
                            mobile: true,
                            platform: 'Android'
                        };
                        
                        defineSafeProp(navigator.userAgentData, 'brands', () => brands);
                        defineSafeProp(navigator.userAgentData, 'mobile', () => true);
                        defineSafeProp(navigator.userAgentData, 'platform', () => 'Android');
                        
                        navigator.userAgentData.getHighEntropyValues = makeNative((hints) => {
                            return Promise.resolve({
                                ...mockData,
                                platformVersion: "15.0.0",
                                model: "Pixel 7a",
                                uaFullVersion: "133.0.0.0",
                                architecture: 'arm',
                                bitness: '64'
                            });
                        }, 'getHighEntropyValues');
                    }
                } catch(e) {}
                
            })();
        """.trimIndent()
    }
}

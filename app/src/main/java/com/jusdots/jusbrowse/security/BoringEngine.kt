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
    fun getUserAgentMetadata(): androidx.webkit.UserAgentMetadata {
        return androidx.webkit.UserAgentMetadata.Builder()
            .setBrandVersionList(listOf(
                androidx.webkit.UserAgentMetadata.BrandVersion.Builder()
                    .setBrand("Google Chrome")
                    .setFullVersion("145.0.0.0")
                    .setMajorVersion("145")
                    .build(),
                androidx.webkit.UserAgentMetadata.BrandVersion.Builder()
                    .setBrand("Chromium")
                    .setFullVersion("145.0.0.0")
                    .setMajorVersion("145")
                    .build(),
                androidx.webkit.UserAgentMetadata.BrandVersion.Builder()
                    .setBrand("Not=A?Brand")
                    .setFullVersion("99.0.0.0")
                    .setMajorVersion("99")
                    .build()
            ))
            .setFullVersion("145.0.0.0")
            .setPlatform("Android")
            .setPlatformVersion("14")
            .setArchitecture("arm")
            .setModel("Pixel 8 Pro")
            .setMobile(true)
            .setBitness(64)
            .build()
    }

    /**
     * Pillar 1: Static User-Agent String matching metadata.
     */
    const val CHROME_145_UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Mobile Safari/537.36"

    /**
     * Generate the "Boring" injection script.
     * @param sessionSeed A persistent seed for this session.
     */
    fun generateScript(sessionSeed: Long): String {
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

                const makeNative = (fn, name) => {
                    const nativeString = `function ${"$"}{name || ''}() { [native code] }`;
                    Object.defineProperty(fn, 'toString', {
                        value: () => nativeString,
                        configurable: true,
                        writable: true
                    });
                    try {
                        Object.defineProperty(fn, 'name', { value: name, configurable: true });
                    } catch(e) {}
                    return fn;
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

                // --- PILLAR 3: SEEDED DRIFT (Head Tracking) ---
                
                const driftX = (SESSION_SEED % 100) / 5000.0; // +/- 0.02px
                const driftY = ((SESSION_SEED >> 4) % 100) / 5000.0;

                // getBoundingClientRect
                try {
                    const originalGetBCR = Element.prototype.getBoundingClientRect;
                    Element.prototype.getBoundingClientRect = makeNative(function() {
                        const rect = originalGetBCR.apply(this, arguments);
                        return {
                           x: rect.x + driftX, y: rect.y + driftY,
                           width: rect.width, height: rect.height,
                           top: rect.top + driftY, right: rect.right + driftX,
                           bottom: rect.bottom + driftY, left: rect.left + driftX,
                           toJSON: () => JSON.stringify(this),
                           width_drift: rect.width, // maintain ref
                           height_drift: rect.height
                        };
                    }, 'getBoundingClientRect');
                } catch(e) {}

                // getComputedStyle (Drift on computed dimensions)
                try {
                    const originalGCS = window.getComputedStyle;
                    window.getComputedStyle = makeNative(function(el, pseudo) {
                        const style = originalGCS.call(window, el, pseudo);
                        const proxy = new Proxy(style, {
                            get(target, prop) {
                                const val = target[prop];
                                if ((prop === 'width' || prop === 'height' || prop === 'top' || prop === 'left') && typeof val === 'string' && val.endsWith('px')) {
                                    const numeric = parseFloat(val);
                                    const drift = (prop === 'width' || prop === 'left') ? driftX : driftY;
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

                // --- 6. HARWARE CONCURRENCY & MEMORY (Fixed) ---
                try {
                    const cores = (SESSION_SEED % 2 === 0) ? 8 : 4;
                    Object.defineProperty(navigator, 'hardwareConcurrency', { get: makeNative(() => cores, 'get hardwareConcurrency') });
                    if (navigator.deviceMemory) {
                        const mem = (SESSION_SEED % 2 === 0) ? 8 : 4;
                        Object.defineProperty(navigator, 'deviceMemory', { get: makeNative(() => mem, 'get deviceMemory') });
                    }
                } catch(e) {}
                
            })();
        """.trimIndent()
    }
}

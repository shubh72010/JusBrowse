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
    fun getProtectionScript(seed: Int, normalizeLanguage: Boolean = true, whitelist: List<String> = emptyList()): String {
        val whitelistJson = whitelist.joinToString(",", "[", "]") { "'$it'" }
        return """
        (function() {
            'use strict';

            const whitelist = $whitelistJson;
            if (whitelist.some(domain => window.location.hostname.endsWith(domain))) {
                return;
            }
            const SESSION_SEED = $seed;

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

            Function.prototype.toString = makeNative(function() {
                if (typeof this === 'function' && this[fakeToStringSymbol]) {
                    return this[fakeToStringSymbol];
                }
                return originalToString.call(this);
            }, 'toString');

            // --- CANVAS ---
            // Store ORIGINAL getImageData before hooking, so poisonCanvas doesn't double-noise
            const _origGetImageData = CanvasRenderingContext2D.prototype.getImageData;

            function poisonCanvas(canvas) {
                try {
                    const ctx = canvas.getContext('2d');
                    if (!ctx) return;
                    const w = canvas.width, h = canvas.height;
                    if (w === 0 || h === 0) return;
                    // Use ORIGINAL getImageData to avoid double-noising
                    const imageData = _origGetImageData.call(ctx, 0, 0, w, h);
                    const data = imageData.data;
                    const magnitude = w < 500 && h < 500 ? 3 : 1;
                    for (let i = 0; i < data.length; i += 4) {
                        data[i]     = clamp(data[i]     + getNoise(i,     magnitude));
                        data[i + 1] = clamp(data[i + 1] + getNoise(i + 1, magnitude));
                        data[i + 2] = clamp(data[i + 2] + getNoise(i + 2, magnitude));
                    }
                    ctx.putImageData(imageData, 0, 0);
                } catch(e) {}
            }

            try {
                CanvasRenderingContext2D.prototype.getImageData = makeNative(function(sx, sy, sw, sh) {
                    try {
                        const imageData = _origGetImageData.apply(this, arguments);
                        const w = this.canvas.width, h = this.canvas.height;
                        const data = imageData.data;
                        const magnitude = w < 500 && h < 500 ? 3 : 1;
                        for (let i = 0; i < data.length; i += 4) {
                            data[i]     = clamp(data[i]     + getNoise(i,     magnitude));
                            data[i + 1] = clamp(data[i + 1] + getNoise(i + 1, magnitude));
                            data[i + 2] = clamp(data[i + 2] + getNoise(i + 2, magnitude));
                        }
                        return imageData;
                    } catch(e) {
                        return _origGetImageData.apply(this, arguments);
                    }
                }, 'getImageData');
            } catch(e) {}

            // Hook toDataURL — this is what fingerprinters actually call
            try {
                const origToDataURL = HTMLCanvasElement.prototype.toDataURL;
                HTMLCanvasElement.prototype.toDataURL = makeNative(function() {
                    try { poisonCanvas(this); } catch(e) {}
                    return origToDataURL.apply(this, arguments);
                }, 'toDataURL');
            } catch(e) {}

            // Hook toBlob
            try {
                const origToBlob = HTMLCanvasElement.prototype.toBlob;
                HTMLCanvasElement.prototype.toBlob = makeNative(function() {
                    try { poisonCanvas(this); } catch(e) {}
                    return origToBlob.apply(this, arguments);
                }, 'toBlob');
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
                        const subMetrics = [
                            'actualBoundingBoxAscent', 'actualBoundingBoxDescent',
                            'actualBoundingBoxLeft', 'actualBoundingBoxRight',
                            'fontBoundingBoxAscent', 'fontBoundingBoxDescent',
                            'alphabeticBaseline', 'hangingBaseline', 'ideographicBaseline',
                            'emHeightAscent', 'emHeightDescent'
                        ];
                        for (const prop of subMetrics) {
                            const orig = metrics[prop];
                            if (typeof orig === 'number') {
                                Object.defineProperty(metrics, prop, {
                                    value: orig + getNoise(prop.length, 0.15),
                                    writable: false, configurable: true
                                });
                            }
                        }
                    } catch(e) {}
                    return metrics;
                }, 'measureText');
            } catch(e) {}

            // --- WEBGL ---
            const FAKE_VENDOR   = "Google Inc. (Qualcomm)";
            const FAKE_RENDERER = "ANGLE (Qualcomm, Adreno (TM) 619, OpenGL ES 3.2)";

            const WEBGL_PARAM_SPOOF = {
                0x8B4F: 256,   // MAX_VERTEX_UNIFORM_VECTORS
                0x8B49: 224,   // MAX_FRAGMENT_UNIFORM_VECTORS
                0x8B4D: 15,    // MAX_VARYING_VECTORS
                0x0D33: 8192,  // MAX_TEXTURE_SIZE
                0x8069: 16,    // MAX_TEXTURE_IMAGE_UNITS
                0x8DFB: 1,     // MAX_SAMPLES (WebGL2)
                0x8B4C: 16,    // MAX_VERTEX_TEXTURE_IMAGE_UNITS
                0x8872: 32,    // MAX_COMBINED_TEXTURE_IMAGE_UNITS
            };

            function patchWebGL(proto) {
                try {
                    const origGetParam = proto.getParameter;
                    proto.getParameter = makeNative(function(param) {
                        try {
                            if (param === 0x9245) return FAKE_VENDOR;
                            if (param === 0x9246) return FAKE_RENDERER;
                            if (param === 0x1F01) return "WebKit WebGL";
                            if (param === 0x1F00) return "WebKit";
                            if (WEBGL_PARAM_SPOOF[param] !== undefined) return WEBGL_PARAM_SPOOF[param];
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
                    const COMMON_EXTENSIONS = [
                        'ANGLE_instanced_arrays', 'EXT_blend_minmax', 'EXT_color_buffer_half_float',
                        'EXT_float_blend', 'EXT_frag_depth', 'EXT_shader_texture_lod',
                        'EXT_texture_filter_anisotropic', 'EXT_sRGB', 'OES_element_index_uint',
                        'OES_standard_derivatives', 'OES_texture_float', 'OES_texture_float_linear',
                        'OES_texture_half_float', 'OES_texture_half_float_linear', 'OES_vertex_array_object',
                        'WEBGL_color_buffer_float', 'WEBGL_compressed_texture_astc',
                        'WEBGL_compressed_texture_etc', 'WEBGL_compressed_texture_etc1',
                        'WEBGL_depth_texture', 'WEBGL_draw_buffers', 'WEBGL_lose_context'
                    ];
                    const origGetSupportedExtensions = proto.getSupportedExtensions;
                    proto.getSupportedExtensions = makeNative(function() {
                        const real = origGetSupportedExtensions.apply(this, arguments) || [];
                        return COMMON_EXTENSIONS.filter(e => real.includes(e));
                    }, 'getSupportedExtensions');
                } catch(e) {}

                try {
                    const origReadPixels = proto.readPixels;
                    proto.readPixels = makeNative(function(x, y, width, height, format, type, pixels) {
                            origReadPixels.apply(this, arguments);
                            if (pixels instanceof Uint8Array) {
                                for (let i = 0; i < pixels.length; i += 4) {
                                    pixels[i]     = clamp(pixels[i]     + getNoise(i,     3));
                                    pixels[i + 1] = clamp(pixels[i + 1] + getNoise(i + 1, 3));
                                    pixels[i + 2] = clamp(pixels[i + 2] + getNoise(i + 2, 3));
                                }
                            }
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

            // --- AUDIO: OfflineAudioContext fingerprint defense ---
            try {
                const OriginalOfflineAudioContext = window.OfflineAudioContext;
                if (OriginalOfflineAudioContext) {
                    window.OfflineAudioContext = makeNative(function(channels, length, sampleRate) {
                        const ctx = new OriginalOfflineAudioContext(channels, length, sampleRate);
                        const origStartRendering = ctx.startRendering.bind(ctx);
                        ctx.startRendering = makeNative(function() {
                            return origStartRendering().then(function(buffer) {
                                for (let ch = 0; ch < buffer.numberOfChannels; ch++) {
                                    const data = buffer.getChannelData(ch);
                                    for (let i = 0; i < data.length; i++) {
                                        data[i] += getNoise(i + ch * 1000, 0.0001);
                                    }
                                }
                                return buffer;
                            });
                        }, 'startRendering');
                        return ctx;
                    }, 'OfflineAudioContext');
                    window.OfflineAudioContext.prototype = OriginalOfflineAudioContext.prototype;
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

                Object.defineProperty(window, 'devicePixelRatio', { get: makeNative(() => 2, 'get devicePixelRatio'), configurable: true });

                // Dynamic Orientation derived from snapped dimensions
                if (screen.orientation) {
                    Object.defineProperty(screen.orientation, 'type', {
                        get: makeNative(() => metrics.type, 'get type'),
                        configurable: true
                    });
                    Object.defineProperty(screen.orientation, 'angle', { get: makeNative(() => 0, 'get angle'), configurable: true });
                }

                // Synchronized VisualViewport
                if (window.visualViewport) {
                    ['width', 'height'].forEach(prop => {
                        Object.defineProperty(window.visualViewport, prop, {
                            get: makeNative(() => getMetrics()[prop === 'width' ? 'innerWidth' : 'innerHeight'], `get ${'$'}{prop}`),
                            configurable: true
                        });
                    });
                    ['offsetLeft', 'offsetTop', 'pageLeft', 'pageTop', 'scale'].forEach(prop => {
                        const val = prop === 'scale' ? 1 : 0;
                        Object.defineProperty(window.visualViewport, prop, { get: makeNative(() => val, `get ${'$'}{prop}`), configurable: true });
                    });
                }
            } catch(e) {}

            // --- HARDWARE ---
            try {
                Object.defineProperty(navigator, 'hardwareConcurrency', { get: makeNative(() => 4, 'get hardwareConcurrency'), configurable: true });
                Object.defineProperty(navigator, 'deviceMemory',        { get: makeNative(() => 4, 'get deviceMemory'),        configurable: true });
            } catch(e) {}

            // --- NAVIGATOR UA SPOOF ---
            try {
                const SPOOFED_UA = 'Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Mobile Safari/537.36';
                Object.defineProperty(navigator, 'userAgent', {
                    get: makeNative(() => SPOOFED_UA, 'get userAgent'),
                    configurable: true
                });
                Object.defineProperty(navigator, 'appVersion', {
                    get: makeNative(() => '5.0 (Linux; Android 10; K)', 'get appVersion'),
                    configurable: true
                });
                Object.defineProperty(navigator, 'platform', {
                    get: makeNative(() => 'Linux armv8l', 'get platform'),
                    configurable: true
                });
                Object.defineProperty(navigator, 'vendor', {
                    get: makeNative(() => 'Google Inc.', 'get vendor'),
                    configurable: true
                });
                if ($normalizeLanguage) {
                    Object.defineProperty(navigator, 'language', {
                        get: makeNative(() => 'en-US', 'get language'),
                        configurable: true
                    });
                    Object.defineProperty(navigator, 'languages', {
                        get: makeNative(() => ['en-US', 'en'], 'get languages'),
                        configurable: true
                    });
                }
                Object.defineProperty(navigator, 'maxTouchPoints', {
                    get: makeNative(() => 5, 'get maxTouchPoints'),
                    configurable: true
                });
            } catch(e) {}

            // --- NAVIGATOR OCCLUSIONS ---
            try {
                if (navigator.connection) Object.defineProperty(navigator, 'connection', { value: undefined, configurable: true });
                Object.defineProperty(navigator, 'plugins', {
                    get: makeNative(() => { const p = []; p.__proto__ = PluginArray.prototype; return p; }, 'get plugins'),
                    configurable: true
                });
                Object.defineProperty(navigator, 'mimeTypes', {
                    get: makeNative(() => { const m = []; m.__proto__ = MimeTypeArray.prototype; return m; }, 'get mimeTypes'),
                    configurable: true
                });
                Object.defineProperty(navigator, 'webdriver', { get: makeNative(() => false, 'get webdriver'), configurable: true });
                Object.defineProperty(navigator, 'pdfViewerEnabled', { get: makeNative(() => true, 'get pdfViewerEnabled'), configurable: true });
                Object.defineProperty(navigator, 'doNotTrack', { get: makeNative(() => '1', 'get doNotTrack'), configurable: true });
                if (navigator.bluetooth) Object.defineProperty(navigator, 'bluetooth', { value: undefined, configurable: true });
                if (navigator.getGamepads) navigator.getGamepads = makeNative(() => [], 'getGamepads');
            } catch(e) {}

            // --- MEDIA DEVICES ---
            try {
                if (navigator.mediaDevices && navigator.mediaDevices.enumerateDevices) {
                    navigator.mediaDevices.enumerateDevices = makeNative(function() {
                        return Promise.resolve([]);
                    }, 'enumerateDevices');
                }
            } catch(e) {}

            // --- USER AGENT DATA (Client Hints) ---
            try {
                if (navigator.userAgentData) {
                    const fakeUAData = {
                        brands: [
                            { brand: 'Google Chrome', version: '133' },
                            { brand: 'Not:A-Brand', version: '99' },
                            { brand: 'Chromium', version: '133' }
                        ],
                        mobile: true,
                        platform: 'Android',
                        getHighEntropyValues: makeNative(function() {
                            return Promise.resolve({
                                architecture: 'arm',
                                bitness: '64',
                                brands: [{ brand: 'Google Chrome', version: '133' }, { brand: 'Not:A-Brand', version: '99' }, { brand: 'Chromium', version: '133' }],
                                fullVersionList: [{ brand: 'Google Chrome', version: '133.0.0.0' }, { brand: 'Not:A-Brand', version: '99.0.0.0' }, { brand: 'Chromium', version: '133.0.0.0' }],
                                mobile: true,
                                model: 'Pixel 7a',
                                platform: 'Android',
                                platformVersion: '15.0.0',
                                uaFullVersion: '133.0.0.0',
                                wow64: false
                            });
                        }, 'getHighEntropyValues'),
                        toJSON: makeNative(function() {
                            return { brands: this.brands, mobile: this.mobile, platform: this.platform };
                        }, 'toJSON')
                    };
                    Object.defineProperty(navigator, 'userAgentData', {
                        get: makeNative(() => fakeUAData, 'get userAgentData'),
                        configurable: true
                    });
                }
            } catch(e) {}

            // --- API OCCLUSION ---
            try { if (navigator.gpu) Object.defineProperty(navigator, 'gpu', { value: undefined, configurable: true }); } catch(e) {}
            try { window.SpeechRecognition = undefined; window.webkitSpeechRecognition = undefined; } catch(e) {}
            try { Object.defineProperty(window, 'speechSynthesis', { value: undefined, configurable: true }); } catch(e) {}

            // --- TIMEZONE ---
            try {
                const OrigDateTimeFormat = Intl.DateTimeFormat;
                Intl.DateTimeFormat = makeNative(function(locales, options) {
                    const fmt = new OrigDateTimeFormat(locales, options);
                    const origResolvedOptions = fmt.resolvedOptions.bind(fmt);
                    fmt.resolvedOptions = makeNative(function() {
                        const opts = origResolvedOptions();
                        opts.timeZone = 'UTC';
                        return opts;
                    }, 'resolvedOptions');
                    return fmt;
                }, 'DateTimeFormat');
                Intl.DateTimeFormat.prototype = OrigDateTimeFormat.prototype;
                Intl.DateTimeFormat.supportedLocalesOf = OrigDateTimeFormat.supportedLocalesOf;

                Date.prototype.getTimezoneOffset = makeNative(function() {
                    return 0;
                }, 'getTimezoneOffset');
            } catch(e) {}

            // --- SCREEN ORIENTATION (DEPRECATED IN FAVOR OF DYNAMIC ABOVE) ---
            // Intentionally left empty as orientation logic is now integrated into Screen & Viewport section above for consistency.
            try {} catch(e) {}

            // --- PERFORMANCE TIMING ---
            try {
                const origPerfNow = Performance.prototype.now;
                Performance.prototype.now = makeNative(function() {
                    return Math.round(origPerfNow.call(this) * 10) / 10;
                }, 'now');
            } catch(e) {}

            // --- FONT ENUMERATION PROTECTION ---
            try {
                const allowedFonts = [
                    'Roboto', 'sans-serif', 'serif', 'monospace',
                    'Arial', 'Helvetica', 'Times New Roman', 'Courier New',
                    'Droid Sans', 'Noto Sans', 'Noto Serif'
                ];
                if (document.fonts && document.fonts.check) {
                    const originalCheck = document.fonts.check.bind(document.fonts);
                    document.fonts.check = makeNative(function(font, text) {
                        const fontFamily = font.split(' ').slice(1).join(' ').replace(/["']/g, '');
                        const isAllowed = allowedFonts.some(f => fontFamily.toLowerCase().includes(f.toLowerCase()));
                        if (!isAllowed) return false;
                        return originalCheck(font, text);
                    }, 'check');
                }
                if (document.fonts) {
                    document.fonts.forEach = makeNative(function(callback) {}, 'forEach');
                }
            } catch(e) {}

            // --- MATCHMEDIA NORMALIZATION ---
            try {
                const originalMatchMedia = window.matchMedia;
                const bucket = (v, s) => Math.round(v / s) * s;
                window.matchMedia = makeNative(function(query) {
                    if (query.includes('width') || query.includes('height')) {
                        // Resolve resolution queries against bucketed values
                        const m = originalMatchMedia.call(window, query);
                        const bucketSize = 100;
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
                    if (query.includes('prefers-color-scheme')) return fakeResult(query.includes('light'));
                    if (query.includes('prefers-contrast')) return fakeResult(query.includes('no-preference'));
                    if (query.includes('prefers-reduced-motion')) return fakeResult(false);
                    if (query.includes('color-gamut')) return fakeResult(query.includes('srgb'));
                    if (query.includes('dynamic-range')) return fakeResult(query.includes('standard'));
                    if (query.includes('orientation')) {
                        const isLandscape = window.innerWidth >= window.innerHeight;
                        return fakeResult(query.includes('landscape') ? isLandscape : !isLandscape);
                    }
                    return result;
                }, 'matchMedia');
            } catch(e) {}

            // --- ERROR STACK SANITIZATION ---
            try {
                const sanitizeStack = (stack) => {
                    if (!stack) return stack;
                    return stack
                        .replace(/file:\/\/\/data\/[^\s]+/g, 'file:///app/script.js')
                        .replace(/chrome-extension:\/\/[^\s]+/g, '')
                        .replace(/at\s+[A-Za-z]+\s+\(native\)/g, '')
                        .replace(/\/storage\/emulated\/[^\s]+/g, '/app/');
                };
                const originalStackDescriptor = Object.getOwnPropertyDescriptor(Error.prototype, 'stack');
                if (originalStackDescriptor && originalStackDescriptor.get) {
                    Object.defineProperty(Error.prototype, 'stack', {
                        get: makeNative(function() {
                            const stack = originalStackDescriptor.get.call(this);
                            return sanitizeStack(stack);
                        }, 'get stack'),
                        set: originalStackDescriptor.set,
                        configurable: true
                    });
                }
            } catch(e) {}

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

            console.log('[JusBrowse] Fingerprint defense v5 active. Seed:', SESSION_SEED.toString(16));
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

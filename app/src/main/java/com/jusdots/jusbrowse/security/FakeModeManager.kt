package com.jusdots.jusbrowse.security

import android.content.Context
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebStorage
import android.webkit.WebView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Properties

/**
 * Manages Fake Mode state and current persona.
 * Enforces strict isolation: switching personas wipes storage.
 */
object FakeModeManager {

    private const val PREF_NAME = "fake_mode_prefs"
    private const val KEY_PERSONA_ID = "active_persona_id"

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _currentPersona = MutableStateFlow<FakePersona?>(null)
    val currentPersona: StateFlow<FakePersona?> = _currentPersona.asStateFlow()

    private val secureRandom = java.security.SecureRandom()

    private var sessionBatteryLevel: Double = 0.85
    private var sessionBatteryCharging: Boolean = true
    private var sessionIsFlagship: Boolean = true
    private var networkTimezone: String? = null
    private var sessionStartTime: Long = System.currentTimeMillis()
    var sessionSeed: Long = System.currentTimeMillis()
        private set
    var sessionLocalIp: String = "192.168.1.1"
        private set

    // Per-session randomized bridge names to prevent browser fingerprinting
    var bridgeNameSurgical: String = generateBridgeName()
        private set
    var bridgeNamePrivacy: String = generateBridgeName()
        private set

    var isLanguageNormalizationEnabled: Boolean = true

    private fun generateBridgeName(): String {
        val chars = ('a'..'z') + ('A'..'Z')
        return (1..12).map { chars[secureRandom.nextInt(chars.size)] }.joinToString("")
    }

    /**
     * Calculate battery level with linear drift (0.5% per 10 minutes)
     */
    fun getDriftedBatteryLevel(): Double {
        val elapsedMinutes = (System.currentTimeMillis() - sessionStartTime) / 60000.0
        val drift = (elapsedMinutes / 10.0) * 0.005
        return (sessionBatteryLevel - drift).coerceAtLeast(0.01)
    }

    /**
     * Randomize session-specific values (Battery, Performance Tier)
     * Called when fake mode is enabled or initialized.
     */
    private fun randomizeSessionState() {
        // Battery between 20% and 98%
        sessionBatteryLevel = 0.20 + (secureRandom.nextDouble() * 0.78)
        // Charging 50/50
        sessionBatteryCharging = secureRandom.nextBoolean()
        // Toggle Flagship vs Budget for this session
        sessionIsFlagship = secureRandom.nextBoolean()
        networkTimezone = null // Reset for new session
        sessionStartTime = System.currentTimeMillis()
        sessionSeed = secureRandom.nextLong()
        sessionLocalIp = NetworkUtils.getWeightedRandomLocalIp()
        // Rotate bridge names per session
        bridgeNameSurgical = generateBridgeName()
        bridgeNamePrivacy = generateBridgeName()
    }

    /**
     * Trigger an async fetch of the current network timezone.
     */
    fun syncTimezoneWithNetwork(scope: kotlinx.coroutines.CoroutineScope) {
        scope.launch {
            networkTimezone = NetworkUtils.fetchCurrentTimezone()
        }
    }

    /**
     * Initialize logic (load saved persona)
     */
    fun init(context: Context) {
        val savedId = getSavedPersonaId(context)
        
        if (savedId != null) {
            val savedPersona = PersonaRepository.getPersonaById(savedId)
            if (savedPersona != null) {
                randomizeSessionState() 
                // Load the persona from the same group but matching current session's tier
                val persona = PersonaRepository.getPersonaInGroup(savedPersona.groupId, sessionIsFlagship)
                _currentPersona.value = persona
                _isEnabled.value = true
            }
        }
    }

    /**
     * Helper to get saved ID synchronously for Application.onCreate
     */
    fun getSavedPersonaId(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PERSONA_ID, null)
    }

    /**
     * Enable Fake Mode with specific persona.
     * RESTARTS APP to apply new Data Directory Suffix.
     */
    fun enableFakeMode(context: Context, persona: FakePersona) {
        if (_currentPersona.value?.id != persona.id) {
            randomizeSessionState() 
            // Save the specific persona selected (it will be used as a group anchor on next init)
            saveState(context, persona.id)
            // Restart to apply namespace
            restartApp(context)
        }
    }

    /**
     * Disable Fake Mode and clear persona.
     * RESTARTS APP to return to default storage.
     */
    fun disableFakeMode(context: Context) {
        if (_isEnabled.value) {
            saveState(context, null)
            restartApp(context)
        }
    }
    
    /**
     * Trigger a hard app restart to ensure new WebView process binding
     */
    private fun restartApp(context: Context) {
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        val componentName = intent?.component
        val mainIntent = android.content.Intent.makeRestartActivityTask(componentName)
        context.startActivity(mainIntent)
        Runtime.getRuntime().exit(0)
    }

    private fun saveState(context: Context, personaId: String?) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PERSONA_ID, personaId).commit()
    }

    /**
     * WIPES EVERYTHING: Cookies, LocalStorage, Cache, Web Databases.
     * Essential for persona isolation.
     */
    private fun clearWebViewData(context: Context) {
        try {
            // CookieManager
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()

            // WebStorage (LocalStorage, IndexedDB)
            WebStorage.getInstance().deleteAllData()

            // Context-based cleanup (Cache, WebView db)
            context.deleteDatabase("webview.db")
            context.deleteDatabase("webviewCache.db")
            context.cacheDir.deleteRecursively()
            
            // Note: We can't easily clear 'WebView' instance caches here without a WebView instance
            // But Cookie/Storage clearing is the 90% solution.
        } catch (e: Exception) {
            // Log error
        }
    }

    /**
     * Get User-Agent for current state
     */
    fun getUserAgent(): String? {
        return if (_isEnabled.value) {
            _currentPersona.value?.userAgent
        } else null
    }
    
    /**
     * Get headers to inject for interceptors
     */
    fun getHeaders(): Map<String, String> {
        return _currentPersona.value?.headers ?: emptyMap()
    }

    /**
     * Get UserAgentMetadata for current state
     */
    fun getUserAgentMetadata(): androidx.webkit.UserAgentMetadata? {
        return if (_isEnabled.value) {
            _currentPersona.value?.toUserAgentMetadata()
        } else null
    }

    /**
     * Generate JS injection script for fingerprinting protection based on active engine
     */
    fun generateFingerprintScript(
        webViewVersion: String,
        defaultEnabled: Boolean = true,
        jusFakeEnabled: Boolean = false,
        boringEnabled: Boolean = false,
        whitelist: List<String> = emptyList()
    ): String {
        val persona = if (jusFakeEnabled) _currentPersona.value else null
        
        return when {
            jusFakeEnabled && persona != null -> generatePersonaScript(persona, whitelist)
            boringEnabled -> BoringEngine.generateScript(sessionSeed, webViewVersion, isLanguageNormalizationEnabled)
            defaultEnabled -> FingerprintingProtection.getProtectionScript(sessionSeed.toInt(), isLanguageNormalizationEnabled, whitelist)
            else -> FingerprintingProtection.minimalProtectionScript
        }
    }

    /**
     * Generate persona-specific fingerprinting script
     */
    /**
     * Generate persona-specific fingerprinting script using the Privacy Bus
     */
    private fun generatePersonaScript(persona: FakePersona, whitelist: List<String> = emptyList()): String {
        val whitelistJson = whitelist.joinToString(",", "[", "]") { "'${it.replace("'", "\\'")}'" }
        // Escape all persona strings to prevent JS injection via single-quote breakout
        fun escapeJs(s: String): String = s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r")
        // 1. Collect Raw OS Data (Mocking the capture for now)
        val rawData = mapOf(
            PrivacyPacket.KEY_SCREEN_WIDTH to 1080, // Real would come from DisplayMetrics
            PrivacyPacket.KEY_SCREEN_HEIGHT to 2412,
            PrivacyPacket.KEY_PIXEL_RATIO to 3.0,
            PrivacyPacket.KEY_BATTERY_LEVEL to getDriftedBatteryLevel(),
            PrivacyPacket.KEY_BATTERY_CHARGING to sessionBatteryCharging,
            PrivacyPacket.KEY_TIMEZONE to (networkTimezone ?: persona.timezone ?: "UTC")
        )
        val rawPacket = PrivacyPacket(PrivacyState.RAW, rawData)

        // 2. Process through Privacy Bus (Priv8 -> RLE)
        val processedPacket = PrivacyBus.process(rawPacket, persona)
        val data = processedPacket.data

        // 3. Extract Glowed values
        val logicWidth = data[PrivacyPacket.KEY_SCREEN_WIDTH] as? Int ?: 360
        val logicHeight = data[PrivacyPacket.KEY_SCREEN_HEIGHT] as? Int ?: 800
        val pixelRatio = data[PrivacyPacket.KEY_PIXEL_RATIO] as? Double ?: 2.0
        val tzId = escapeJs(data[PrivacyPacket.KEY_TIMEZONE] as? String ?: "UTC")
        val userAgent = escapeJs(data[PrivacyPacket.KEY_USER_AGENT] as? String ?: persona.userAgent)
        val platform = escapeJs(data[PrivacyPacket.KEY_PLATFORM] as? String ?: "Android")
        val platformString = escapeJs(data[PrivacyPacket.KEY_PLATFORM_STRING] as? String ?: "Linux aarch64")
        val language = escapeJs(data[PrivacyPacket.KEY_LANGUAGE] as? String ?: "en-US")
        val languages = "['en-US', 'en']" // Default fallback for spoofing
        
        // Calculate dynamic JS offset for the processed timezone
        val tz = java.util.TimeZone.getTimeZone(tzId)
        val offsetMillis = tz.getOffset(System.currentTimeMillis())
        val jsOffsetMinutes = -(offsetMillis / 60000)
        val tzDisplayName = tz.getDisplayName(tz.inDaylightTime(java.util.Date()), java.util.TimeZone.LONG)

        val roundedBatteryLevel = "%.2f".format(data[PrivacyPacket.KEY_BATTERY_LEVEL] as? Double ?: 0.5)
        val batteryCharging = data[PrivacyPacket.KEY_BATTERY_CHARGING] as? Boolean ?: false
        
        val gpuRenderer = escapeJs(data[PrivacyPacket.KEY_VIDEO_CARD_RENDERER] as? String ?: persona.videoCardRenderer)
        val gpuVendor = escapeJs(data[PrivacyPacket.KEY_VIDEO_CARD_VENDOR] as? String ?: persona.videoCardVendor)

        return """
            (function() {
                'use strict';
                const whitelist = $whitelistJson;
                if (whitelist.some(domain => window.location.hostname.endsWith(domain))) {
                    return;
                }
                const NOISE_SEED = ${persona.noiseSeed};
                const PRIVACY_STATE = '${processedPacket.state}';
                const normalizeLanguage = $isLanguageNormalizationEnabled;
                
                // --- TELEMETRY BRIDGE ---
                const reportSuspicion = (pts, reason) => {
                    if (window.${bridgeNamePrivacy} && window.${bridgeNamePrivacy}.reportSuspicion) {
                        window.${bridgeNamePrivacy}.reportSuspicion(pts, reason);
                    }
                };

                // --- PRNG: Improved seeded random for noise ---
                function seededRandom(index) {
                    let x = NOISE_SEED ^ (index * 1664525 + 1013904223);
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
                    fn[fakeToStringSymbol] = `function ${"$"}{name || fn.name || ''}() { [native code] }`;
                    return fn;
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
                            const noise = getNoise(text.length * 7 + NOISE_SEED % 100, 0.3);
                            Object.defineProperty(metrics, 'width', {
                                value: metrics.width + noise,
                                writable: false, configurable: true
                            });
                        } catch(e) {}
                        return metrics;
                    }, 'measureText');
                } catch(e) {}

                // --- WEBGL ---
                const gpuVendor   = "$gpuVendor";
                const gpuRenderer = "$gpuRenderer";

                function patchWebGL(proto) {
                    try {
                        const origGetParam = proto.getParameter;
                        proto.getParameter = makeNative(function(param) {
                            try {
                                if (param === 0x9245) return gpuVendor;
                                if (param === 0x9246) return gpuRenderer;
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

                // --- AUDIO: Frequency data noise ---
                try {
                    const AudioContext = window.AudioContext || window.webkitAudioContext;
                    if (AudioContext) {
                        const origCreateAnalyser = AudioContext.prototype.createAnalyser;
                        AudioContext.prototype.createAnalyser = makeNative(function() {
                            const analyser = origCreateAnalyser.apply(this, arguments);
                            const origGetFloatFrequencyData = analyser.getFloatFrequencyData.bind(analyser);
                            analyser.getFloatFrequencyData = makeNative(function(array) {
                                origGetFloatFrequencyData(array);
                                for (let i = 0; i < array.length; i++) {
                                    array[i] += getNoise(i, 0.1);
                                }
                            }, 'getFloatFrequencyData');
                            return analyser;
                        }, 'createAnalyser');
                    }
                } catch(e) {}

                // --- SCREEN ---
                try {
                    const COMMON_W = [360, 400];
                    const COMMON_H = [600, 700, 800, 900];
                    const snap = (v, list) => list.reduce((p, c) => Math.abs(c - v) < Math.abs(p - v) ? c : p);
                    
                    // Logic Width/Height are already Persona-processed, but we snap to common real values
                    const personaWidth  = $logicWidth;
                    const personaHeight = $logicHeight;
                    const isPortrait = personaWidth < personaHeight;

                    const sW = isPortrait ? snap(personaWidth, COMMON_W) : snap(personaWidth, COMMON_H);
                    const sH = isPortrait ? snap(personaHeight, COMMON_H) : snap(personaHeight, COMMON_W);

                    Object.defineProperty(screen, 'width',       { get: makeNative(() => sW, 'get width'),       configurable: true });
                    Object.defineProperty(screen, 'height',      { get: makeNative(() => sH, 'get height'),      configurable: true });
                    Object.defineProperty(screen, 'availWidth',  { get: makeNative(() => sW, 'get availWidth'),  configurable: true });
                    Object.defineProperty(screen, 'availHeight', { get: makeNative(() => sH, 'get availHeight'), configurable: true });
                    Object.defineProperty(screen, 'colorDepth',  { get: makeNative(() => 24,                         'get colorDepth'),  configurable: true });
                    Object.defineProperty(screen, 'pixelDepth',  { get: makeNative(() => 24,                         'get pixelDepth'),  configurable: true });
                    Object.defineProperty(window, 'innerWidth',  { get: makeNative(() => sW, 'get innerWidth'),  configurable: true });
                    Object.defineProperty(window, 'innerHeight', { get: makeNative(() => sH, 'get innerHeight'), configurable: true });
                    Object.defineProperty(window, 'devicePixelRatio', { get: makeNative(() => $pixelRatio,      'get devicePixelRatio'), configurable: true });
                    
                    if (screen.orientation) {
                        const type = isPortrait ? 'portrait-primary' : 'landscape-primary';
                        Object.defineProperty(screen.orientation, 'type', { get: makeNative(() => type, 'get type'), configurable: true });
                        Object.defineProperty(screen.orientation, 'angle', { get: makeNative(() => 0, 'get angle'), configurable: true });
                    }
                } catch(e) {}

                // --- HARDWARE ---
                try {
                    Object.defineProperty(navigator, 'hardwareConcurrency', { get: makeNative(() => 4, 'get hardwareConcurrency'), configurable: true });
                    Object.defineProperty(navigator, 'deviceMemory',        { get: makeNative(() => 4, 'get deviceMemory'),        configurable: true });
                    Object.defineProperty(navigator, 'platform',            { get: makeNative(() => '$platformString', 'get platform'), configurable: true });
                    Object.defineProperty(navigator, 'vendor',              { get: makeNative(() => 'Google Inc.', 'get vendor'), configurable: true });
                    Object.defineProperty(navigator, 'userAgent',           { get: makeNative(() => '$userAgent', 'get userAgent'), configurable: true });
                    
                    if (normalizeLanguage) {
                        Object.defineProperty(navigator, 'language',  { get: makeNative(() => '$language', 'get language'), configurable: true });
                        Object.defineProperty(navigator, 'languages', { get: makeNative(() => $languages, 'get languages'), configurable: true });
                    }
                } catch(e) {}

                // --- 7. OCCLUSION (WebGPU) ---
                if (navigator.gpu) {
                    try { Object.defineProperty(navigator, 'gpu', { value: undefined }); } catch(e) {}
                }
                try {
                    // matchMedia - return persona-consistent values
                    const originalMatchMedia = window.matchMedia;
                    window.matchMedia = makeNative(function(query) {
                        const result = originalMatchMedia.call(window, query);
                        
                        // Intercept color scheme and contrast to prevent fingerprinting
                        if (query.includes('prefers-color-scheme')) {
                            return {
                                matches: query.includes('light'),
                                media: query,
                                onchange: null,
                                addListener: makeNative(function() {}, 'addListener'),
                                removeListener: makeNative(function() {}, 'removeListener'),
                                addEventListener: makeNative(function() {}, 'addEventListener'),
                                removeEventListener: makeNative(function() {}, 'removeEventListener'),
                                dispatchEvent: makeNative(function() { return false; }, 'dispatchEvent')
                            };
                        }
                        if (query.includes('prefers-contrast')) {
                            return {
                                matches: query.includes('no-preference'),
                                media: query,
                                onchange: null,
                                addListener: makeNative(function() {}, 'addListener'),
                                removeListener: makeNative(function() {}, 'removeListener'),
                                addEventListener: makeNative(function() {}, 'addEventListener'),
                                removeEventListener: makeNative(function() {}, 'removeEventListener'),
                                dispatchEvent: makeNative(function() { return false; }, 'dispatchEvent')
                            };
                        }
                        if (query.includes('prefers-reduced-motion')) {
                            return {
                                matches: false,
                                media: query,
                                onchange: null,
                                addListener: makeNative(function() {}, 'addListener'),
                                removeListener: makeNative(function() {}, 'removeListener'),
                                addEventListener: makeNative(function() {}, 'addEventListener'),
                                removeEventListener: makeNative(function() {}, 'removeEventListener'),
                                dispatchEvent: makeNative(function() { return false; }, 'dispatchEvent')
                            };
                        }
                        if (query.includes('color-gamut')) {
                            return {
                                matches: query.includes('srgb'),
                                media: query,
                                onchange: null,
                                addListener: makeNative(function() {}, 'addListener'),
                                removeListener: makeNative(function() {}, 'removeListener'),
                                addEventListener: makeNative(function() {}, 'addEventListener'),
                                removeEventListener: makeNative(function() {}, 'removeEventListener'),
                                dispatchEvent: makeNative(function() { return false; }, 'dispatchEvent')
                            };
                        }
                        if (query.includes('dynamic-range')) {
                            return {
                                matches: query.includes('standard'),
                                media: query,
                                onchange: null,
                                addListener: makeNative(function() {}, 'addListener'),
                                removeListener: makeNative(function() {}, 'removeListener'),
                                addEventListener: makeNative(function() {}, 'addEventListener'),
                                removeEventListener: makeNative(function() {}, 'removeEventListener'),
                                dispatchEvent: makeNative(function() { return false; }, 'dispatchEvent')
                            };
                        }
                        return result;
                    }, 'matchMedia');

                    // CSS.supports - limit fingerprinting surface
                    if (window.CSS && CSS.supports) {
                        const originalSupports = CSS.supports;
                        CSS.supports = makeNative(function(prop, val) {
                            // Allow normal checks but normalize some edge cases
                            return originalSupports.apply(CSS, arguments);
                        }, 'supports');
                    }
                } catch(e) {}

                // ===== FONT ENUMERATION PROTECTION =====
                try {
                    // Common Android fonts - limit to common subset
                    const allowedFonts = [
                        'Roboto', 'sans-serif', 'serif', 'monospace',
                        'Arial', 'Helvetica', 'Times New Roman', 'Courier New',
                        'Droid Sans', 'Noto Sans', 'Noto Serif'
                    ];
                    
                    // Intercept font loading API
                    if (document.fonts && document.fonts.check) {
                        const originalCheck = document.fonts.check.bind(document.fonts);
                        document.fonts.check = makeNative(function(font, text) {
                            // Extract font family from font string (e.g., "12px Arial")
                            const fontFamily = font.split(' ').slice(1).join(' ').replace(/["']/g, '');
                            
                            // Only allow whitelisted fonts
                            const isAllowed = allowedFonts.some(f => 
                                fontFamily.toLowerCase().includes(f.toLowerCase())
                            );
                            
                            if (!isAllowed) {
                                reportSuspicion(5, 'Font Probe: ' + fontFamily);
                                return false; // Claim font not available
                            }
                            return originalCheck(font, text);
                        }, 'check');
                    }

                    // Limit fonts.forEach / iterate
                    if (document.fonts) {
                        document.fonts.forEach = makeNative(function(callback) {
                            // Don't iterate - prevents enumeration
                            reportSuspicion(15, 'Font Enumeration');
                        }, 'forEach');
                    }
                } catch(e) {}

                // ===== ERROR STACK TRACE SANITIZATION =====
                try {
                    const originalPrepareStackTrace = Error.prepareStackTrace;
                    const sanitizeStack = (stack) => {
                        if (!stack) return stack;
                        // Remove internal paths that leak device info
                        return stack
                            .replace(/file:\/\/\/data\/[^\s]+/g, 'file:///app/script.js')
                            .replace(/chrome-extension:\/\/[^\s]+/g, '')
                            .replace(/at\s+[A-Za-z]+\s+\(native\)/g, '')
                            .replace(/\/storage\/emulated\/[^\s]+/g, '/app/');
                    };

                    // Override Error.prototype.stack getter
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

                // --- WEBRTC ---
                try {
                    if (window.RTCPeerConnection) {
                        const OriginalRPC = window.RTCPeerConnection;
                        const maskSDP = (sdp) => {
                            if (!sdp) return sdp;
                            return sdp.split('\r\n').map(line => {
                                if (line.startsWith('a=candidate') &&
                                   (line.includes('192.168.') || line.includes('10.') || line.includes('172.'))) {
                                    return line.replace(/(\s)(192\.168\.|10\.|172\.(1[6-9]|2[0-9]|3[0-1])\.)[^\s]+(\s)/, '$1$sessionLocalIp${"$"}{"4"}');
                                }
                                if (line.startsWith('c=IN IP4') &&
                                   (line.includes('192.168.') || line.includes('10.') || line.includes('172.'))) {
                                    return 'c=IN IP4 $sessionLocalIp';
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

                // --- 9. POST INTERCEPTION HOOKS ---
                try {
                    const originalFetch = window.fetch;
                    window.fetch = makeNative(function(input, init) {
                        if (init && init.method && init.method.toUpperCase() === 'POST') {
                            const url = (typeof input === 'string') ? input : input.url;
                            const body = init.body || '';
                            if (window.${bridgeNameSurgical}) {
                                const cleaned = window.${bridgeNameSurgical}.onInterceptPost(url, 'POST', JSON.stringify(init.headers || {}), String(body));
                                if (cleaned === '__BLOCKED__') return Promise.reject(new Error('Privacy Shield: Request Blocked'));
                                if (cleaned !== undefined && cleaned !== null) init.body = cleaned;
                            }
                        }
                        return originalFetch.apply(this, arguments);
                    }, 'fetch');

                    const originalXHRSend = XMLHttpRequest.prototype.send;
                    XMLHttpRequest.prototype.send = makeNative(function(body) {
                        if (this._method === 'POST' && window.${bridgeNameSurgical}) {
                            const cleaned = window.${bridgeNameSurgical}.onInterceptPost(this._url, 'POST', '{}', String(body || ''));
                            if (cleaned === '__BLOCKED__') {
                                console.warn('Privacy Shield: Blocked tracker POST');
                                return;
                            }
                            if (cleaned !== undefined && cleaned !== null) body = cleaned;
                        }
                        return originalXHRSend.apply(this, arguments);
                    }, 'send');
                    
                    const originalXHROpen = XMLHttpRequest.prototype.open;
                    XMLHttpRequest.prototype.open = makeNative(function(method, url) {
                        this._method = method;
                        this._url = url;
                        return originalXHROpen.apply(this, arguments);
                    }, 'open');

                    HTMLFormElement.prototype.submit = (function(original) {
                        return makeNative(function() {
                            if (this.method && this.method.toUpperCase() === 'POST' && window.${bridgeNameSurgical}) {
                                const formData = new FormData(this);
                                const entries = {};
                                for (const [key, value] of formData.entries()) {
                                    entries[key] = value;
                                }
                                const cleaned = window.${bridgeNameSurgical}.onInterceptPost(this.action, 'POST', '{}', JSON.stringify(entries));
                                if (cleaned === '__BLOCKED__') {
                                    console.warn('Privacy Shield: Blocked form POST');
                                    return;
                                }
                                // Note: Form body modification is limited in standard submits. 
                                // We trust the user to use Fetch/XHR for advanced usage.
                            }
                            return original.apply(this, arguments);
                        }, 'submit');
                    })(HTMLFormElement.prototype.submit);
                } catch(e) {}

                // --- CLIENT HINTS ---
                try {
                    if (navigator.userAgentData) {
                        const origGetHighEntropyValues = navigator.userAgentData.getHighEntropyValues;
                        navigator.userAgentData.getHighEntropyValues = makeNative(function(hints) {
                            return origGetHighEntropyValues.call(navigator.userAgentData, hints).then(values => {
                                if (hints.includes('architecture')) values.architecture = 'arm';
                                if (hints.includes('bitness'))     values.bitness      = '64';
                                if (hints.includes('model'))       values.model        = 'Pixel 7a';
                                if (hints.includes('platformVersion')) values.platformVersion = '13';
                                return values;
                            });
                        }, 'getHighEntropyValues');
                    }
                } catch(e) {}

                // --- HARDWARE & NAVIGATOR (Phase 2) ---
                try {
                    Object.defineProperty(navigator, 'hardwareConcurrency', { get: makeNative(() => 4, 'get hardwareConcurrency'), configurable: true });
                    Object.defineProperty(navigator, 'deviceMemory',        { get: makeNative(() => 4, 'get deviceMemory'),        configurable: true });
                    Object.defineProperty(navigator, 'webdriver',           { get: makeNative(() => false, 'get webdriver'),       configurable: true });
                    Object.defineProperty(navigator, 'pdfViewerEnabled',    { get: makeNative(() => true, 'get pdfViewerEnabled'),  configurable: true });
                } catch(e) {}

                // --- 10. ANOMALY REPORTING BRIDGE ---
                window.addEventListener('error', (e) => {
                    if (e.message && (e.message.includes('fingerprint') || e.message.includes('canvas'))) {
                        if (window.${bridgeNameSurgical}) window.${bridgeNameSurgical}.reportSuspicion(10, 'Script Error: ' + e.message);
                    }
                });

            })();
        """.trimIndent()
    }

    /**
     * Bridge for receiving telemetry from WebView
     */
    class PrivacyBridge {
        @JavascriptInterface
        fun reportSuspicion(points: Int, reason: String) {
            SuspicionScorer.reportSuspiciousActivity(points)
        }
    }
}

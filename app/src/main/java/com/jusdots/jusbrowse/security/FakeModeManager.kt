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
            boringEnabled -> BoringEngine.generateScript(sessionSeed, webViewVersion)
            defaultEnabled -> FingerprintingProtection.getProtectionScript(sessionSeed.toInt(), whitelist)
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
                
                // --- TELEMETRY BRIDGE ---
                const reportSuspicion = (pts, reason) => {
                    if (window.${bridgeNamePrivacy} && window.${bridgeNamePrivacy}.reportSuspicion) {
                        window.${bridgeNamePrivacy}.reportSuspicion(pts, reason);
                    }
                };

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
                        Object.defineProperty(fn, 'name', { value: name, configurable: true });
                    }
                    const nativeString = `function ${"$"}{fn.name || ''}() { [native code] }`;
                    fn[fakeToStringSymbol] = nativeString;
                    return fn;
                };

                Function.prototype.toString = function() {
                    if (typeof this === 'function' && this[fakeToStringSymbol]) {
                        return this[fakeToStringSymbol];
                    }
                    return originalToString.call(this);
                };
                makeNative(Function.prototype.toString, 'toString');

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

                // --- 1. TIME & PERFORMANCE (Boring Clamp) ---
                try {
                    const TIME_PRECISION_MS = 100;
                    const originalNow = Performance.prototype.now;
                    Performance.prototype.now = makeNative(function() {
                        return Math.floor(originalNow.call(this) / TIME_PRECISION_MS) * TIME_PRECISION_MS;
                    }, 'now');
                    
                    const originalDateNow = Date.now;
                    Date.now = makeNative(function() {
                        return Math.floor(originalDateNow.call(Date) / TIME_PRECISION_MS) * TIME_PRECISION_MS;
                    }, 'now');
                } catch(e) {}

                // --- 2. BATTERY API (Boring Standard) ---
                try {
                    const batteryMock = {
                        charging: $batteryCharging,
                        chargingTime: $batteryCharging ? 3600 : Infinity,
                        dischargingTime: $batteryCharging ? Infinity : 18000,
                        level: $roundedBatteryLevel,
                        addEventListener: makeNative(() => {}, 'addEventListener'),
                        removeEventListener: makeNative(() => {}, 'removeEventListener'),
                    };
                    navigator.getBattery = makeNative(() => Promise.resolve(batteryMock), 'getBattery');
                } catch(e) {}

                // --- 3. SCREEN & VIEWPORT ---
                try {
                    const lWidth = $logicWidth;
                    const lHeight = $logicHeight;
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
                    defineSafeProp(window, 'devicePixelRatio', () => $pixelRatio);
                } catch(e) {}
                
                // --- 4. NAVIGATOR & CLIENT HINTS ---
                try {
                    defineSafeProp(navigator, 'userAgent', () => '$userAgent');
                    defineSafeProp(navigator, 'platform', () => '$platformString'); 
                    defineSafeProp(navigator, 'vendor', () => ${if (persona.platform == "Android") "'Google Inc.'" else "'Apple Computer, Inc.'"});
                    defineSafeProp(navigator, 'maxTouchPoints', () => ${if (persona.mobile) 5 else 0});
                    defineSafeProp(navigator, 'hardwareConcurrency', () => 8);
                    defineSafeProp(navigator, 'webdriver', () => false);
                    defineSafeProp(navigator, 'doNotTrack', () => '${persona.doNotTrack}');
                    defineSafeProp(navigator, 'deviceMemory', () => 8);
                    defineSafeProp(navigator, 'pdfViewerEnabled', () => false);

                    // Spoof Plugins (Chrome Android has 0 plugins normally)
                    const emptyPluginArray = [];
                    emptyPluginArray.item = makeNative(() => null, 'item');
                    emptyPluginArray.namedItem = makeNative(() => null, 'namedItem');
                    emptyPluginArray.refresh = makeNative(() => {}, 'refresh');
                    defineSafeProp(navigator, 'plugins', () => emptyPluginArray);
                    defineSafeProp(navigator, 'mimeTypes', () => emptyPluginArray);

                    // Spoof WebGL GPU (Persona-specific)
                    const getParameter = WebGLRenderingContext.prototype.getParameter;
                    WebGLRenderingContext.prototype.getParameter = makeNative(function(parameter) {
                        if (parameter === 37445) return '$gpuVendor';
                        if (parameter === 37446) return '$gpuVendor';
                        if (parameter === 37447) return '$gpuRenderer';
                        return getParameter.apply(this, arguments);
                    }, 'getParameter');

                    if (typeof WebGL2RenderingContext !== 'undefined') {
                        const getParameter2 = WebGL2RenderingContext.prototype.getParameter;
                        WebGL2RenderingContext.prototype.getParameter = makeNative(function(parameter) {
                            if (parameter === 37445) return '$gpuVendor';
                            if (parameter === 37446) return '$gpuVendor';
                            if (parameter === 37447) return '$gpuRenderer';
                            return getParameter2.apply(this, arguments);
                        }, 'getParameter');
                    }

                    // Robust Client Hints Spoofing
                    if (!navigator.userAgentData) {
                        try {
                            const UserAgentData = makeNative(function() {}, 'NavigatorUAData');
                            const uad = new UserAgentData();
                            Object.defineProperty(navigator, 'userAgentData', {
                                value: uad, enumerable: true, configurable: true
                            });
                        } catch(e) {}
                    }

                    if (navigator.userAgentData) {
                        const brands = [${persona.brands.joinToString(",") { "{brand: '${it.brand}', version: '${it.version}'}" }}];
                        const highEntropyValues = {
                            architecture: 'arm', bitness: '64', brands: brands,
                            mobile: ${persona.mobile}, model: '${persona.model}',
                            platform: '${persona.platform}', platformVersion: '${persona.platformVersion}',
                        };
                        
                        // Override properties on the instance or prototype
                        try {
                            const proto = Object.getPrototypeOf(navigator.userAgentData);
                            defineSafeProp(navigator.userAgentData, 'brands', () => brands);
                            defineSafeProp(navigator.userAgentData, 'mobile', () => ${persona.mobile});
                            defineSafeProp(navigator.userAgentData, 'platform', () => '${persona.platform}');
                            
                            navigator.userAgentData.getHighEntropyValues = makeNative((hints) => {
                                if (!hints) return Promise.resolve(highEntropyValues);
                                const result = {};
                                hints.forEach(h => { if (highEntropyValues[h] !== undefined) result[h] = highEntropyValues[h]; });
                                return Promise.resolve(result);
                            }, 'getHighEntropyValues');
                        } catch(e) {}
                    }
                } catch(e) {}
                
                // --- 5. LOCALE & TIMEZONE ---
                try {
                    const locale = '$language'; 
                    defineSafeProp(navigator, 'language', () => locale);
                    defineSafeProp(navigator, 'languages', () => ${persona.languages.joinToString(",", "[", "]") { "'$it'" }});
                    
                    const originalDTF = Intl.DateTimeFormat;
                    Intl.DateTimeFormat = makeNative(function(locales, options) {
                        options = options || {};
                        if (!options.timeZone) options.timeZone = '$tzId';
                        return new originalDTF([locale], options);
                    }, 'DateTimeFormat');
                    Intl.DateTimeFormat.prototype = originalDTF.prototype;
                    
                    Date.prototype.getTimezoneOffset = makeNative(function() { return $jsOffsetMinutes; }, 'getTimezoneOffset');
                } catch(e) {}

                // --- 6. CANVAS & AUDIO (Surgical Seeded Noise) ---
                try {
                    const originalGetImageData = CanvasRenderingContext2D.prototype.getImageData;
                    CanvasRenderingContext2D.prototype.getImageData = makeNative(function() {
                        const imageData = originalGetImageData.apply(this, arguments);
                        const buffer = imageData.data;
                        // Imperceptible jitter on 1.5% of pixels
                        for (let i = 0; i < buffer.length; i += 64) {
                            if ((i + NOISE_SEED) % 71 === 0) {
                                const channel = (i + NOISE_SEED) % 3;
                                buffer[i + channel] = buffer[i + channel] ^ 1;
                            }
                        }
                        return imageData;
                    }, 'getImageData');

                    if (window.OfflineAudioContext) {
                        const OriginalOAC = OfflineAudioContext;
                        window.OfflineAudioContext = makeNative(function(c, l, s) {
                            const ctx = new OriginalOAC(c, l, s);
                            const originalStartRendering = ctx.startRendering.bind(ctx);
                            ctx.startRendering = makeNative(function() {
                                return originalStartRendering().then(buffer => {
                                    const channel = buffer.getChannelData(0);
                                    for (let i = 0; i < channel.length; i += 100) {
                                         const noise = ((NOISE_SEED + i) % 100) / 1000000.0;
                                         channel[i] += noise;
                                    }
                                    return buffer;
                                });
                            }, 'startRendering');
                            return ctx;
                        }, 'OfflineAudioContext');
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

                // ===== ENHANCED AUDIOCONTEXT FINGERPRINTING =====
                try {
                    if (window.AudioContext || window.webkitAudioContext) {
                        const AC = window.AudioContext || window.webkitAudioContext;
                        
                        // Intercept createOscillator
                        const originalCreateOscillator = AC.prototype.createOscillator;
                        AC.prototype.createOscillator = makeNative(function() {
                            const osc = originalCreateOscillator.call(this);
                            // Add micro-jitter to frequency
                            const originalFreqSet = Object.getOwnPropertyDescriptor(osc.frequency, 'value');
                            if (originalFreqSet && originalFreqSet.set) {
                                Object.defineProperty(osc.frequency, 'value', {
                                    get: originalFreqSet.get,
                                    set: function(v) {
                                        // Add persona-seeded micro-jitter
                                        const jitter = (prng() - 0.5) * 0.001;
                                        originalFreqSet.set.call(this, v + jitter);
                                    },
                                    configurable: true
                                });
                            }
                            return osc;
                        }, 'createOscillator');

                        // Intercept createAnalyser for getByteFrequencyData
                        const originalCreateAnalyser = AC.prototype.createAnalyser;
                        AC.prototype.createAnalyser = makeNative(function() {
                            const analyser = originalCreateAnalyser.call(this);
                            
                            // Noise getFloatFrequencyData
                            const originalGetFloatFrequencyData = analyser.getFloatFrequencyData;
                            analyser.getFloatFrequencyData = makeNative(function(array) {
                                originalGetFloatFrequencyData.call(this, array);
                                // Add consistent persona-seeded noise
                                for (let i = 0; i < array.length; i++) {
                                    array[i] += (prng() - 0.5) * 0.0001;
                                }
                            }, 'getFloatFrequencyData');

                            // Noise getByteFrequencyData
                            const originalGetByteFrequencyData = analyser.getByteFrequencyData;
                            analyser.getByteFrequencyData = makeNative(function(array) {
                                originalGetByteFrequencyData.call(this, array);
                                // Add subtle noise
                                for (let i = 0; i < array.length; i += 8) {
                                    if (prng() > 0.95) {
                                        array[i] = Math.max(0, Math.min(255, array[i] + (prng() > 0.5 ? 1 : -1)));
                                    }
                                }
                            }, 'getByteFrequencyData');

                            return analyser;
                        }, 'createAnalyser');

                        // Intercept getChannelData
                        const originalCreateBuffer = AC.prototype.createBuffer;
                        AC.prototype.createBuffer = makeNative(function(numChannels, length, sampleRate) {
                            const buffer = originalCreateBuffer.call(this, numChannels, length, sampleRate);
                            const originalGetChannelData = buffer.getChannelData;
                            buffer.getChannelData = makeNative(function(channel) {
                                const data = originalGetChannelData.call(this, channel);
                                // Add imperceptible noise
                                for (let i = 0; i < data.length; i += 128) {
                                    if (prng() > 0.99) {
                                        data[i] += (prng() - 0.5) * 0.0000001;
                                    }
                                }
                                return data;
                            }, 'getChannelData');
                            return buffer;
                        }, 'createBuffer');
                    }
                } catch(e) {}

                // --- 8. WebRTC & SDP MUNGING (Privacy Hardening) ---
                try {
                    if (window.RTCPeerConnection) {
                        const OriginalRPC = window.RTCPeerConnection;
                        const mungeSDP = (sdp) => {
                            if (!sdp) return sdp;
                            let lines = sdp.split('\r\n');
                            
                            // 1. Mask Local IP (Regex for LAN patterns)
                            lines = lines.map(line => {
                                if (line.startsWith('a=candidate') && (line.includes('192.168.') || line.includes('10.') || line.includes('172.'))) {
                                    return line.replace(/(\s)(192\.168\.|10\.|172\.(1[6-9]|2[0-9]|3[0-1])\.)[^\s]+(\s)/, "$1$sessionLocalIp${"$"}{"4"}");
                                }
                                if (line.startsWith('c=IN IP4') && (line.includes('192.168.') || line.includes('10.') || line.includes('172.'))) {
                                    return 'c=IN IP4 $sessionLocalIp';
                                }
                                return line;
                            });

                            // 2. Filter Verbose Codecs (AV1, H265) to match standard Chrome Mobile
                            const forbiddenCodecs = ['AV1', 'H265', 'hevc', 'av01'];
                            const forbiddenPayloadTypes = [];
                            
                            lines = lines.filter(line => {
                                const rtpMatch = line.match(/^a=rtpmap:(\d+)\s+([^\/]+)/);
                                if (rtpMatch) {
                                    const pt = rtpMatch[1];
                                    const codec = rtpMatch[2];
                                    if (forbiddenCodecs.some(c => codec.toUpperCase().includes(c))) {
                                        forbiddenPayloadTypes.push(pt);
                                        return false;
                                    }
                                }
                                return true;
                            });

                            // Remove secondary lines for forbidden payloads
                            lines = lines.filter(line => {
                                const fmtpMatch = line.match(/^a=(fmtp|rtcp-fb):(\d+)\s/);
                                if (fmtpMatch && forbiddenPayloadTypes.includes(fmtpMatch[2])) {
                                    return false;
                                }
                                return true;
                            });

                            return lines.join('\r\n');
                        };

                        window.RTCPeerConnection = makeNative(function(config) {
                            const pc = new OriginalRPC(config);
                            
                            const wrapCreate = (original) => {
                                return makeNative(function() {
                                    return original.apply(this, arguments).then(desc => {
                                        if (desc && desc.sdp) {
                                            desc.sdp = mungeSDP(desc.sdp);
                                        }
                                        return desc;
                                    });
                                }, original.name);
                            };

                            pc.createOffer = wrapCreate(pc.createOffer);
                            pc.createAnswer = wrapCreate(pc.createAnswer);

                            const originalSetLocal = pc.setLocalDescription;
                            pc.setLocalDescription = makeNative(function(desc) {
                                if (desc && desc.sdp) {
                                    desc.sdp = mungeSDP(desc.sdp);
                                }
                                return originalSetLocal.apply(this, arguments);
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

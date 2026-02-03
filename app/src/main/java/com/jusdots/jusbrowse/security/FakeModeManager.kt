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

    private var sessionBatteryLevel: Double = 0.85
    private var sessionBatteryCharging: Boolean = true
    private var sessionIsFlagship: Boolean = true
    private var networkTimezone: String? = null
    private var sessionStartTime: Long = System.currentTimeMillis()

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
        sessionBatteryLevel = 0.20 + (Math.random() * 0.78)
        // Charging 50/50
        sessionBatteryCharging = Math.random() > 0.5
        // Toggle Flagship vs Budget for this session
        sessionIsFlagship = Math.random() > 0.5
        networkTimezone = null // Reset for new session
        sessionStartTime = System.currentTimeMillis()
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
     * Generate JS injection script for fingerprinting protection based on active engine
     */
    fun generateFingerprintScript(
        defaultEnabled: Boolean = true,
        jusFakeEnabled: Boolean = false,
        randomiserEnabled: Boolean = false
    ): String {
        val persona = if (jusFakeEnabled) _currentPersona.value else null
        
        return when {
            jusFakeEnabled && persona != null -> generatePersonaScript(persona)
            randomiserEnabled -> RandomiserEngine.generateScript((System.currentTimeMillis() % 10000).toInt())
            defaultEnabled -> FingerprintingProtection.getProtectionScript(1337)
            else -> FingerprintingProtection.minimalProtectionScript
        }
    }

    /**
     * Generate persona-specific fingerprinting script
     */
    /**
     * Generate persona-specific fingerprinting script using the Privacy Bus
     */
    private fun generatePersonaScript(persona: FakePersona): String {
        // 1. Collect Raw OS Data (Mocking the capture for now)
        val rawData = mapOf(
            PrivacyPacket.KEY_SCREEN_WIDTH to 1080, // Real would come from DisplayMetrics
            PrivacyPacket.KEY_SCREEN_HEIGHT to 2412,
            PrivacyPacket.KEY_PIXEL_RATIO to 3.0,
            PrivacyPacket.KEY_BATTERY_LEVEL to getDriftedBatteryLevel(),
            PrivacyPacket.KEY_BATTERY_CHARGING to sessionBatteryCharging,
            PrivacyPacket.KEY_TIMEZONE to (networkTimezone ?: java.util.TimeZone.getDefault().id)
        )
        val rawPacket = PrivacyPacket(PrivacyState.RAW, rawData)

        // 2. Process through Privacy Bus (Priv8 -> RLE)
        val processedPacket = PrivacyBus.process(rawPacket, persona)
        val data = processedPacket.data

        // 3. Extract Glowed values
        val logicWidth = data[PrivacyPacket.KEY_SCREEN_WIDTH] as? Int ?: 360
        val logicHeight = data[PrivacyPacket.KEY_SCREEN_HEIGHT] as? Int ?: 800
        val pixelRatio = data[PrivacyPacket.KEY_PIXEL_RATIO] as? Double ?: 2.0
        val tzId = data[PrivacyPacket.KEY_TIMEZONE] as? String ?: "UTC"
        val userAgent = data[PrivacyPacket.KEY_USER_AGENT] as? String ?: persona.userAgent
        val platform = data[PrivacyPacket.KEY_PLATFORM] as? String ?: "Android"
        val platformString = data[PrivacyPacket.KEY_PLATFORM_STRING] as? String ?: "Linux aarch64"
        val language = data[PrivacyPacket.KEY_LANGUAGE] as? String ?: "en-US"
        
        // Calculate dynamic JS offset for the processed timezone
        val tz = java.util.TimeZone.getTimeZone(tzId)
        val offsetMillis = tz.getOffset(System.currentTimeMillis())
        val jsOffsetMinutes = -(offsetMillis / 60000)
        val tzDisplayName = tz.getDisplayName(tz.inDaylightTime(java.util.Date()), java.util.TimeZone.LONG)

        val roundedBatteryLevel = "%.2f".format(data[PrivacyPacket.KEY_BATTERY_LEVEL] as? Double ?: 0.5)
        val batteryCharging = data[PrivacyPacket.KEY_BATTERY_CHARGING] as? Boolean ?: false

        return """
            (function() {
                'use strict';
                const NOISE_SEED = ${persona.noiseSeed};
                const CLOCK_SKEW_MS = ${persona.clockSkewMs};
                const PRIVACY_STATE = '${processedPacket.state}';
                
                // Mulberry32 PRNG for stable, seeded noise
                const prng = (function(seed) {
                    return function() {
                        let t = seed += 0x6D2B79F5;
                        t = Math.imul(t ^ t >>> 15, t | 1);
                        t ^= t + Math.imul(t ^ t >>> 7, t | 61);
                        return ((t ^ t >>> 14) >>> 0) / 4294967296;
                    };
                })(NOISE_SEED);

                // ===== PHASE 4: NATIVE FUNCTION CAMOUFLAGE =====
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

                // ===== TIME & PERFORMANCE (Precision Rounding) =====
                try {
                    const TIME_PRECISION_MS = ${data[PrivacyPacket.KEY_TIME_PRECISION_MS] as? Int ?: 100};
                    const perfOffset = (prng() * 10) - 5; 
                    
                    const originalNow = Performance.prototype.now;
                    Performance.prototype.now = makeNative(function() {
                        const t = originalNow.call(this) + perfOffset;
                        return Math.floor(t / TIME_PRECISION_MS) * TIME_PRECISION_MS;
                    }, 'now');
                    
                    const originalDateNow = Date.now;
                    Date.now = makeNative(function() {
                        const t = originalDateNow.call(Date) + Math.round(perfOffset);
                        return Math.floor(t / TIME_PRECISION_MS) * TIME_PRECISION_MS;
                    }, 'now');

                    const originalGetTime = Date.prototype.getTime;
                    Date.prototype.getTime = makeNative(function() {
                        const t = originalGetTime.call(this) + Math.round(perfOffset);
                        return Math.floor(t / TIME_PRECISION_MS) * TIME_PRECISION_MS;
                    }, 'getTime');

                    // Block performance.getEntries() leaks
                    const originalGetEntries = Performance.prototype.getEntries;
                    Performance.prototype.getEntries = makeNative(function() {
                        return []; 
                    }, 'getEntries');

                    const originalGetEntriesByType = Performance.prototype.getEntriesByType;
                    Performance.prototype.getEntriesByType = makeNative(function() {
                        return [];
                    }, 'getEntriesByType');
                } catch(e) {}

                // ===== MATH EDGE CASES (Normalization) =====
                try {
                    const originalMathTan = Math.tan;
                    const originalMathAcosh = Math.acosh;
                    
                    Math.tan = makeNative(function(x) {
                        if (Math.abs(x) > 1e100) return -1.4214488238747245;
                        return originalMathTan.call(Math, x);
                    }, 'tan');
                    
                    Math.acosh = makeNative(function(x) {
                        if (x > 1e308) return Infinity;
                        return originalMathAcosh.call(Math, x);
                    }, 'acosh');
                } catch(e) {}

                // ===== PERMISSIONS SPOOFING =====
                try {
                    if (navigator.permissions && navigator.permissions.query) {
                        const originalQuery = navigator.permissions.query;
                        navigator.permissions.query = makeNative(function(params) {
                            const name = params.name;
                            if (name === 'accelerometer' || name === 'magnetometer' || name === 'gyroscope') {
                                return Promise.resolve({ state: 'prompt', onchange: null });
                            }
                            return originalQuery.apply(this, arguments);
                        }, 'query');
                    }
                } catch(e) {}

                // ===== BATTERY API (Low Precision) =====
                try {
                    const batteryMock = {
                        charging: $batteryCharging,
                        chargingTime: $batteryCharging ? 3600 : Infinity,
                        dischargingTime: $batteryCharging ? Infinity : 18000,
                        level: $roundedBatteryLevel,
                        addEventListener: makeNative(function() {}, 'addEventListener'),
                        removeEventListener: makeNative(function() {}, 'removeEventListener'),
                        dispatchEvent: makeNative(function() { return false; }, 'dispatchEvent'),
                        onchargingchange: null, onchargingtimechange: null, ondischargingtimechange: null, onlevelchange: null
                    };
                    navigator.getBattery = makeNative(() => Promise.resolve(batteryMock), 'getBattery');
                } catch(e) {}

                // ===== SCREEN & VIEWPORT =====
                try {
                    const lWidth = Math.round($logicWidth);
                    const lHeight = Math.round($logicHeight);
                    const chromeHeight = 70;
                    const screenProps = {
                        width: lWidth, height: lHeight,
                        availWidth: lWidth, availHeight: lHeight,
                        colorDepth: 24, pixelDepth: 24
                    };
                    for (const prop in screenProps) {
                        defineSafeProp(screen, prop, () => screenProps[prop]);
                    }
                    defineSafeProp(window, 'devicePixelRatio', () => $pixelRatio);
                    defineSafeProp(window, 'outerWidth', () => lWidth);
                    defineSafeProp(window, 'outerHeight', () => lHeight);
                    defineSafeProp(window, 'innerWidth', () => lWidth);
                    defineSafeProp(window, 'innerHeight', () => lHeight - chromeHeight);
                } catch(e) {}
                
                // ===== NAVIGATOR & CLIENT HINTS =====
                try {
                    defineSafeProp(navigator, 'userAgent', () => '$userAgent');
                    defineSafeProp(navigator, 'appVersion', () => '$userAgent'.replace('Mozilla/', ''));
                    defineSafeProp(navigator, 'platform', () => '$platformString'); 
                    defineSafeProp(navigator, 'vendor', () => ${if (persona.platform == "Android") "'Google Inc.'" else "'Apple Computer, Inc.'"});
                    defineSafeProp(navigator, 'maxTouchPoints', () => ${if (persona.mobile) 5 else 0});
                    defineSafeProp(navigator, 'hardwareConcurrency', () => ${data[PrivacyPacket.KEY_HARDWARE_CONCURRENCY] as? Int ?: 8});
                    defineSafeProp(navigator, 'webdriver', () => false);
                    defineSafeProp(navigator, 'doNotTrack', () => '${persona.doNotTrack}');
                    if (navigator.deviceMemory !== undefined) {
                        defineSafeProp(navigator, 'deviceMemory', () => ${data[PrivacyPacket.KEY_DEVICE_MEMORY] as? Int ?: 8});
                    }

                    const createMockArray = (type, tag) => {
                        const arr = Object.create(type.prototype);
                        Object.defineProperty(arr, 'length', { value: 0 });
                        Object.defineProperty(arr, Symbol.toStringTag, { value: tag });
                        arr.item = makeNative(() => null, 'item');
                        arr.namedItem = makeNative(() => null, 'namedItem');
                        if (type === PluginArray) arr.refresh = makeNative(() => {}, 'refresh');
                        return arr;
                    };

                    try {
                        defineSafeProp(navigator, 'plugins', () => createMockArray(PluginArray, 'PluginArray'));
                        defineSafeProp(navigator, 'mimeTypes', () => createMockArray(MimeTypeArray, 'MimeTypeArray'));
                    } catch(e) {
                         defineSafeProp(navigator, 'plugins', () => []);
                         defineSafeProp(navigator, 'mimeTypes', () => []);
                    }

                    if (navigator.userAgentData) {
                        const brands = [${persona.brands.joinToString(",") { "{brand: '${it.brand}', version: '${it.version}'}" }}];
                        const highEntropyValues = {
                            architecture: 'arm', bitness: '64', brands: brands,
                            mobile: ${persona.mobile}, model: '${persona.model}',
                            platform: '${persona.platform}', platformVersion: '${persona.platformVersion}',
                            uaFullVersion: '${persona.userAgent.split(" ").last()}'
                        };
                        defineSafeProp(navigator.userAgentData, 'brands', () => brands);
                        defineSafeProp(navigator.userAgentData, 'mobile', () => ${persona.mobile});
                        defineSafeProp(navigator.userAgentData, 'platform', () => '${persona.platform}');
                        navigator.userAgentData.getHighEntropyValues = makeNative((hints) => Promise.resolve(highEntropyValues), 'getHighEntropyValues');
                    }
                } catch(e) {}
                
                // ===== LOCALE & TIMEZONE =====
                try {
                    const locale = '$language'; 
                    defineSafeProp(navigator, 'language', () => locale);
                    defineSafeProp(navigator, 'languages', () => ${persona.languages.joinToString(",", "[", "]") { "'$it'" }});
                    
                    ['DateTimeFormat', 'NumberFormat', 'Collator', 'PluralRules', 'RelativeTimeFormat', 'ListFormat', 'DisplayNames'].forEach(api => {
                        if (Intl[api]) {
                            const original = Intl[api];
                            Intl[api] = makeNative(function(locales, options) {
                                return new original([locale], options);
                            }, api);
                            Intl[api].prototype = original.prototype;
                        }
                    });

                    const originalDTF = Intl.DateTimeFormat;
                    Intl.DateTimeFormat = makeNative(function(locales, options) {
                        options = options || {};
                        if (!options.timeZone) options.timeZone = '$tzId';
                        return new originalDTF([locale], options);
                    }, 'DateTimeFormat');
                    Intl.DateTimeFormat.prototype = originalDTF.prototype;
                    
                    Date.prototype.getTimezoneOffset = makeNative(function() { return $jsOffsetMinutes; }, 'getTimezoneOffset');
                    
                    const targetOffsetMin = $jsOffsetMinutes;
                    const targetTzName = '$tzDisplayName';
                    const originalToStringDate = Date.prototype.toString;
                    
                    Date.prototype.toString = makeNative(function() {
                        const utcMillis = this.getTime();
                        if (isNaN(utcMillis)) return originalToStringDate.call(this);
                        const options = {
                            weekday: 'short', month: 'short', day: '2-digit', year: 'numeric',
                            hour: '2-digit', minute: '2-digit', second: '2-digit',
                            hour12: false, timeZone: '$tzId'
                        };
                        const formatter = new originalDTF('en-US', options);
                        const parts = formatter.formatToParts(this);
                        const p = {}; parts.forEach(part => p[part.type] = part.value);
                        const offsetHours = Math.floor(Math.abs(targetOffsetMin) / 60).toString().padStart(2, '0');
                        const offsetMins = (Math.abs(targetOffsetMin) % 60).toString().padStart(2, '0');
                        const sign = targetOffsetMin > 0 ? '-' : '+';
                        return `${"$"}{p.weekday} ${"$"}{p.month} ${"$"}{p.day} ${"$"}{p.year} ${"$"}{p.hour}:${"$"}{p.minute}:${"$"}{p.second} GMT${"$"}{sign}${"$"}{offsetHours}${"$"}{offsetMins} (${"$"}{targetTzName})`;
                    }, 'toString');
                } catch(e) {}

                // ===== WEBGL PARAMETRIC SPOOFING =====
                try {
                    const webglTargets = [WebGLRenderingContext, WebGL2RenderingContext];
                    const webglParams = { ${persona.webglParams.entries.joinToString(",") { "${it.key}: ${it.value}" }} };
                    const webglLimits = {
                        37445: '${persona.videoCardVendor}', 37446: '${persona.videoCardRenderer}',
                        3379: ${persona.webglMaxTextureSize}, 3413: ${persona.webglMaxRenderBufferSize}, 34076: ${persona.webglMaxTextureSize}
                    };
                    const allParams = Object.assign({}, webglLimits, webglParams);
                    const extensions = ${persona.webglExtensions.joinToString(",", "[", "]") { "'$it'" }};

                    webglTargets.forEach(t => {
                        if (!t) return;
                        const originalGetParameter = t.prototype.getParameter;
                        t.prototype.getParameter = makeNative(function(param) {
                            if (allParams[param] !== undefined) return allParams[param];
                            try { return originalGetParameter.apply(this, arguments); } catch(e) { return null; }
                        }, 'getParameter');
                        
                        t.prototype.getSupportedExtensions = makeNative(() => extensions, 'getSupportedExtensions');

                        const originalGetExtension = t.prototype.getExtension;
                        t.prototype.getExtension = makeNative(function(name) {
                            const ext = originalGetExtension.apply(this, arguments);
                            if (name === 'WEBGL_debug_renderer_info' && ext) {
                                const originalExtGetParam = ext.getParameter || (ext.__proto__ && ext.__proto__.getParameter);
                                if (originalExtGetParam) {
                                    ext.getParameter = makeNative(function(p) {
                                        if (p === 37445) return '${persona.videoCardVendor}';
                                        if (p === 37446) return '${persona.videoCardRenderer}';
                                        return originalExtGetParam.apply(this, arguments);
                                    }, 'getParameter');
                                }
                            }
                            return ext;
                        }, 'getExtension');
                    });
                } catch(e) {}

                // ===== HEURISTIC TELEMETRY =====
                const reportSuspicion = (points, reason) => {
                    try { window.jusPrivacyBridge.reportSuspicion(points, reason); } catch(e) {}
                };

                // ===== CANVAS SEEDED NOISE (Origin Salted) =====
                try {
                    const originSalt = (function(str) {
                        let hash = 0;
                        for (let i = 0; i < str.length; i++) {
                            hash = ((hash << 5) - hash) + str.charCodeAt(i);
                            hash |= 0;
                        }
                        return hash;
                    })(window.location.origin);

                    const originalGetImageData = CanvasRenderingContext2D.prototype.getImageData;
                    CanvasRenderingContext2D.prototype.getImageData = makeNative(function(x, y, w, h) {
                        reportSuspicion(20, 'Canvas Access');
                        const imageData = originalGetImageData.apply(this, arguments);
                        const buffer = imageData.data;
                        const canvasPrng = (function(seed) { 
                            return function() {
                                let t = seed += 0x6D2B79F5;
                                t = Math.imul(t ^ t >>> 15, t | 1);
                                return ((t ^ t >>> 14) >>> 0) / 4294967296;
                            };
                        })(NOISE_SEED ^ originSalt);

                        for (let i = 0; i < buffer.length; i += 64) {
                            if (canvasPrng() > 0.98) {
                                buffer[i] = buffer[i] ^ 1; // Subtle noise
                            }
                        }
                        return imageData;
                    }, 'getImageData');

                    const originalToDataURL = HTMLCanvasElement.prototype.toDataURL;
                    HTMLCanvasElement.prototype.toDataURL = makeNative(function() {
                        reportSuspicion(25, 'Canvas Export');
                        return originalToDataURL.apply(this, arguments);
                    }, 'toDataURL');
                    
                    // ===== AUDIO BUFFER NOISE (OfflineAudioContext) =====
                    if (window.OfflineAudioContext) {
                        const OriginalOAC = OfflineAudioContext;
                        window.OfflineAudioContext = makeNative(function(channels, length, sampleRate) {
                            const ctx = new OriginalOAC(channels, length, sampleRate);
                            const originalStartRendering = ctx.startRendering.bind(ctx);
                            
                            ctx.startRendering = makeNative(function() {
                                return originalStartRendering().then(buffer => {
                                    // Add seeded noise to FingerprintJS hash range (4500-5000)
                                    const channel = buffer.getChannelData(0);
                                    const audioPrng = (function(seed) {
                                        return function() {
                                            let t = seed += 0x6D2B79F5;
                                            t = Math.imul(t ^ t >>> 15, t | 1);
                                            return ((t ^ t >>> 14) >>> 0) / 4294967296;
                                        };
                                    })(NOISE_SEED + 1000);
                                    
                                    for (let i = 4500; i < Math.min(5000, channel.length); i++) {
                                        if (audioPrng() > 0.95) {
                                            channel[i] += (audioPrng() - 0.5) * 0.001;
                                        }
                                    }
                                    return buffer;
                                });
                            }, 'startRendering');
                            
                            return ctx;
                        }, 'OfflineAudioContext');
                        
                        if (window.webkitOfflineAudioContext) {
                            window.webkitOfflineAudioContext = window.OfflineAudioContext;
                        }
                    }
                    
                    if (window.AudioContext || window.webkitAudioContext) {
                        const AC = window.AudioContext || window.webkitAudioContext;
                        defineSafeProp(AC.prototype, 'baseLatency', () => ${persona.audioBaseLatency});
                        defineSafeProp(AC.prototype, 'outputLatency', () => ${persona.audioBaseLatency + 0.005});
                        
                        const originalAC = AC.prototype.constructor;
                        window.AudioContext = makeNative(function() {
                            reportSuspicion(30, 'AudioContext Created');
                            return new originalAC();
                        }, 'AudioContext');
                    }
                } catch(e) {}

                // ===== SENSOR MICRO-JITTER (Ambient Motion) =====
                try {
                    const originalAddEventListener = window.addEventListener;
                    window.addEventListener = makeNative(function(type, listener, options) {
                        if (type === 'devicemotion' || type === 'deviceorientation') {
                            reportSuspicion(50, 'Sensor Access');
                        }
                        return originalAddEventListener.apply(this, arguments);
                    }, 'addEventListener');
                } catch(e) {}

                // ===== WEBRTC LOCAL IP PROTECTION =====
                try {
                    const originalRTCPeerConnection = window.RTCPeerConnection || window.webkitRTCPeerConnection;
                    if (originalRTCPeerConnection) {
                        window.RTCPeerConnection = makeNative(function(config, constraints) {
                            reportSuspicion(40, 'WebRTC PeerConnection');
                            // Force STUN-only to prevent local IP leak
                            if (config && config.iceServers) {
                                config.iceServers = config.iceServers.filter(s => 
                                    s.urls && (Array.isArray(s.urls) ? s.urls : [s.urls])
                                        .some(u => u.startsWith('stun:') || u.startsWith('turn:'))
                                );
                            }
                            const pc = new originalRTCPeerConnection(config, constraints);
                            
                            // Filter local candidates
                            const originalAddIceCandidate = pc.addIceCandidate.bind(pc);
                            pc.addIceCandidate = makeNative(function(candidate) {
                                if (candidate && candidate.candidate) {
                                    // Block local IP candidates
                                    if (candidate.candidate.includes('192.168.') ||
                                        candidate.candidate.includes('10.') ||
                                        candidate.candidate.includes('172.16.')) {
                                        return Promise.resolve();
                                    }
                                }
                                return originalAddIceCandidate(candidate);
                            }, 'addIceCandidate');
                            
                            return pc;
                        }, 'RTCPeerConnection');
                        window.RTCPeerConnection.prototype = originalRTCPeerConnection.prototype;
                        if (window.webkitRTCPeerConnection) {
                            window.webkitRTCPeerConnection = window.RTCPeerConnection;
                        }
                    }
                } catch(e) {}

                // ===== MEDIA DEVICES ENUMERATION SPOOFING =====
                try {
                    if (navigator.mediaDevices && navigator.mediaDevices.enumerateDevices) {
                        const fakeDevices = [
                            { deviceId: 'default', kind: 'audioinput', label: '', groupId: 'default' },
                            { deviceId: 'default', kind: 'audiooutput', label: '', groupId: 'default' },
                            { deviceId: 'camera1', kind: 'videoinput', label: '', groupId: 'camera' }
                        ];
                        navigator.mediaDevices.enumerateDevices = makeNative(function() {
                            reportSuspicion(25, 'MediaDevices Enumeration');
                            return Promise.resolve(fakeDevices.map(d => ({
                                deviceId: d.deviceId,
                                kind: d.kind,
                                label: d.label,
                                groupId: d.groupId,
                                toJSON: makeNative(function() { return d; }, 'toJSON')
                            })));
                        }, 'enumerateDevices');
                    }
                } catch(e) {}

                // ===== NAVIGATOR.CONNECTION FULL API =====
                try {
                    if (navigator.connection) {
                        const connProps = {
                            effectiveType: '${persona.networkType}',
                            downlink: ${persona.networkDownlink},
                            rtt: ${persona.networkRtt},
                            saveData: false,
                            type: 'cellular'
                        };
                        for (const prop in connProps) {
                            try {
                                defineSafeProp(navigator.connection, prop, () => connProps[prop]);
                            } catch(e) {}
                        }
                    }
                } catch(e) {}

                // ===== SPEECH SYNTHESIS PROTECTION =====
                try {
                    if (window.speechSynthesis) {
                        window.speechSynthesis.getVoices = makeNative(function() {
                            reportSuspicion(15, 'Speech Voices');
                            // Return minimal Android-consistent voice list
                            return [];
                        }, 'getVoices');
                    }
                } catch(e) {}

                // ===== STORAGE QUOTA SPOOFING =====
                try {
                    if (navigator.storage && navigator.storage.estimate) {
                        const originalEstimate = navigator.storage.estimate;
                        navigator.storage.estimate = makeNative(function() {
                            reportSuspicion(10, 'Storage Estimate');
                            // Return generic values
                            return Promise.resolve({
                                quota: 1073741824, // 1GB - common value
                                usage: Math.floor(Math.random() * 10485760), // Random 0-10MB
                                usageDetails: {}
                            });
                        }, 'estimate');
                    }
                } catch(e) {}

                // ===== OFFSCREEN CANVAS PROTECTION =====
                try {
                    if (typeof OffscreenCanvas !== 'undefined') {
                        const OriginalOffscreenCanvas = OffscreenCanvas;
                        window.OffscreenCanvas = makeNative(function(width, height) {
                            reportSuspicion(30, 'OffscreenCanvas');
                            const canvas = new OriginalOffscreenCanvas(width, height);
                            const ctx = canvas.getContext('2d');
                            if (ctx) {
                                const originalGetImageData = ctx.getImageData;
                                ctx.getImageData = makeNative(function(x, y, w, h) {
                                    const imageData = originalGetImageData.apply(this, arguments);
                                    // Apply same noise as regular canvas
                                    const buffer = imageData.data;
                                    for (let i = 0; i < buffer.length; i += 64) {
                                        if (prng() > 0.98) buffer[i] = buffer[i] ^ 1;
                                    }
                                    return imageData;
                                }, 'getImageData');
                            }
                            return canvas;
                        }, 'OffscreenCanvas');
                    }
                } catch(e) {}

                // ===== CSS.SUPPORTS() AND MATCHMEDIA() INTERCEPTION =====
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

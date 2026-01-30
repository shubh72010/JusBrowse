package com.jusdots.jusbrowse.security

import android.content.Context
import android.webkit.CookieManager
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
     * Generate JS injection script for fingerprinting protection
     */
    fun generateFingerprintScript(): String {
        val persona = if (_isEnabled.value) _currentPersona.value else null
        
        return if (persona != null) {
            generatePersonaScript(persona)
        } else {
            FingerprintingProtection.getProtectionScript(1337)
        }
    }

    /**
     * Generate persona-specific fingerprinting script
     */
    private fun generatePersonaScript(persona: FakePersona): String {
        // Calculate Timezone Offset (Phase 1 Fix)
        // SMART TIMEZONE HARMONIZER: Prefer Network/IP timezone, fallback to System/VPN
        val tz = networkTimezone?.let { java.util.TimeZone.getTimeZone(it) } ?: java.util.TimeZone.getDefault()
        val offsetMillis = tz.getOffset(System.currentTimeMillis())
        val jsOffsetMinutes = -(offsetMillis / 60000)
        val tzId = tz.id
        val tzDisplayName = tz.getDisplayName(tz.inDaylightTime(java.util.Date()), java.util.TimeZone.LONG)
        
        // Logical Screen (Phase 1 Fix)
        val logicWidth = (persona.screenWidth / persona.pixelRatio).toInt()
        val logicHeight = (persona.screenHeight / persona.pixelRatio).toInt()

        // Round battery level to 2 decimal places to avoid high-precision fingerprinting
        val roundedBatteryLevel = "%.2f".format(sessionBatteryLevel)

        return """
            (function() {
                'use strict';
                const NOISE_SEED = ${persona.noiseSeed};
                const CLOCK_SKEW_MS = ${persona.clockSkewMs};
                
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

                // ===== TIME & PERFORMANCE (Seeded Jitter) =====
                try {
                    const perfOffset = (prng() * 10) - 5; 
                    const originalNow = Performance.prototype.now;
                    Performance.prototype.now = makeNative(function() {
                        return originalNow.call(this) + perfOffset;
                    }, 'now');
                    
                    const originalDateNow = Date.now;
                    Date.now = makeNative(function() {
                        return originalDateNow.call(Date) + Math.round(perfOffset);
                    }, 'now');

                    const originalGetTime = Date.prototype.getTime;
                    Date.prototype.getTime = makeNative(function() {
                        return originalGetTime.call(this) + Math.round(perfOffset);
                    }, 'getTime');
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
                        charging: $sessionBatteryCharging,
                        chargingTime: $sessionBatteryCharging ? 3600 : Infinity,
                        dischargingTime: $sessionBatteryCharging ? Infinity : 18000,
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
                    defineSafeProp(window, 'devicePixelRatio', () => ${persona.pixelRatio});
                    defineSafeProp(window, 'outerWidth', () => lWidth);
                    defineSafeProp(window, 'outerHeight', () => lHeight);
                    defineSafeProp(window, 'innerWidth', () => lWidth);
                    defineSafeProp(window, 'innerHeight', () => lHeight - chromeHeight);
                } catch(e) {}
                
                // ===== NAVIGATOR & CLIENT HINTS =====
                try {
                    defineSafeProp(navigator, 'userAgent', () => '${persona.userAgent}');
                    defineSafeProp(navigator, 'platform', () => 'Linux armv8l'); 
                    defineSafeProp(navigator, 'maxTouchPoints', () => ${if (persona.mobile) 5 else 0});
                    defineSafeProp(navigator, 'hardwareConcurrency', () => ${persona.cpuCores});
                    defineSafeProp(navigator, 'webdriver', () => false);
                    defineSafeProp(navigator, 'doNotTrack', () => '${persona.doNotTrack}');
                    if (navigator.deviceMemory !== undefined) {
                        defineSafeProp(navigator, 'deviceMemory', () => ${persona.ramGB});
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
                    const locale = '${persona.locale}';
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

                // ===== CANVAS SEEDED NOISE =====
                try {
                    const originalGetImageData = CanvasRenderingContext2D.prototype.getImageData;
                    CanvasRenderingContext2D.prototype.getImageData = makeNative(function(x, y, w, h) {
                        const imageData = originalGetImageData.apply(this, arguments);
                        const buffer = imageData.data;
                        const callPrng = (function(seed) { 
                            return function() {
                                let t = seed += 0x6D2B79F5;
                                t = Math.imul(t ^ t >>> 15, t | 1);
                                return ((t ^ t >>> 14) >>> 0) / 4294967296;
                            };
                        })(NOISE_SEED);

                        for (let i = 0; i < buffer.length; i += 160) {
                            if (callPrng() > 0.5) buffer[i] = buffer[i] ^ 1;
                        }
                        return imageData;
                    }, 'getImageData');
                    
                    if (window.AudioContext || window.webkitAudioContext) {
                        const AC = window.AudioContext || window.webkitAudioContext;
                        defineSafeProp(AC.prototype, 'baseLatency', () => ${persona.audioBaseLatency});
                        defineSafeProp(AC.prototype, 'outputLatency', () => ${persona.audioBaseLatency + 0.005});
                    }
                } catch(e) {}

            })();
        """.trimIndent()
    }
}

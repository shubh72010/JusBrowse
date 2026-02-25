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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.jusdots.jusbrowse.BrowserApplication
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

    private val sessionLock = Any()
    private var sessionBatteryLevel: Double = 0.85
    private var sessionBatteryCharging: Boolean = true
    private var sessionIsFlagship: Boolean = true
    @Volatile private var networkTimezone: String? = null
    private var sessionStartTime: Long = System.currentTimeMillis()
    private var sessionSeed: Long = System.currentTimeMillis()

    /**
     * Calculate battery level with linear drift (0.5% per 10 minutes)
     */
    fun getDriftedBatteryLevel(): Double = synchronized(sessionLock) {
        val elapsedMinutes = (System.currentTimeMillis() - sessionStartTime) / 60000.0
        val drift = (elapsedMinutes / 10.0) * 0.005
        (sessionBatteryLevel - drift).coerceAtLeast(0.01)
    }

    /**
     * Randomize session-specific values (Battery, Performance Tier)
     * Called when fake mode is enabled or initialized.
     */
    private fun randomizeSessionState() = synchronized(sessionLock) {
        // Battery between 20% and 98%
        sessionBatteryLevel = 0.20 + (Math.random() * 0.78)
        // Charging 50/50
        sessionBatteryCharging = Math.random() > 0.5
        // Toggle Flagship vs Budget for this session
        sessionIsFlagship = Math.random() > 0.5
        networkTimezone = null // Reset for new session
        sessionStartTime = System.currentTimeMillis()
        sessionSeed = (Math.random() * Long.MAX_VALUE).toLong()
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
    fun init(context: Context, scope: kotlinx.coroutines.CoroutineScope) {
        PersonaRepository.init(context)
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val savedId = prefs.getString(KEY_PERSONA_ID, null)
        
        if (savedId != null) {
            val savedPersona = PersonaRepository.getPersonaById(savedId)
            if (savedPersona != null) {
                randomizeSessionState() 
                val persona = PersonaRepository.getPersonaInGroup(savedPersona.groupId, sessionIsFlagship)
                _currentPersona.value = persona
                _isEnabled.value = true
                
            } else {
                // Invalid persona ID saved, clear it
                _isEnabled.value = false
                prefs.edit().putString(KEY_PERSONA_ID, null).apply()
            }
        } else {
            _isEnabled.value = false
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
     * RESTARTS APP after execution of preRestartAction.
     */
    suspend fun enableFakeMode(context: Context, persona: FakePersona, preRestartAction: (suspend () -> Unit)? = null) {
        // Atomic state updates before we kill the process
        _currentPersona.value = persona
        _isEnabled.value = true
        
        // Randomize session state
        randomizeSessionState()
        
        // Always save and restart to apply the persona
        saveState(context, persona.id)
        preRestartAction?.invoke()
        restartApp(context)
    }

    /**
     * Disable Fake Mode and clear persona.
     * RESTARTS APP after execution of preRestartAction.
     */
    suspend fun disableFakeMode(context: Context, preRestartAction: (suspend () -> Unit)? = null) {
        saveState(context, null)
        clearWebViewData(context) 
        
        _isEnabled.value = false
        _currentPersona.value = null
        
        preRestartAction?.invoke()
        restartApp(context)
    }
    
    /**
     * Trigger a hard app restart to ensure new WebView process binding.
     * Includes a delay to allow DataStore/SharedPrefs to flush to disk.
     */
    fun restartApp(context: Context) {
        val appContext = context.applicationContext
        val packageManager = appContext.packageManager
        val intent = packageManager.getLaunchIntentForPackage(appContext.packageName) ?: return
        val componentName = intent.component
        val mainIntent = android.content.Intent.makeRestartActivityTask(componentName)
        
        // Use AlarmManager to schedule a restart after process exit.
        // This ensures a clean new process is spawned, which is required for 
        // WebView.setDataDirectorySuffix isolation.
        val pendingIntent = android.app.PendingIntent.getActivity(
            appContext, 
            0, 
            mainIntent, 
            android.app.PendingIntent.FLAG_CANCEL_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        val alarmManager = appContext.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        alarmManager.set(android.app.AlarmManager.RTC, System.currentTimeMillis() + 300, pendingIntent)
        
        // Kill process immediately
        android.os.Process.killProcess(android.os.Process.myPid())
        System.exit(0)
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
            
            // Filesystem & Cache cleanup
            context.getDatabasePath("AppCache.db")?.delete()
            val webViewDir = java.io.File(context.filesDir.parentFile, "app_webview")
            if (webViewDir.exists()) {
                // Targeted cleanup of persistent storage within WebView
                listOf("Service Worker", "FileSystem", "GPUCache", "IndexedDB", "Local Storage").forEach {
                    java.io.File(webViewDir, it).deleteRecursively()
                }
            }
            
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
        val rawUa = if (_isEnabled.value) _currentPersona.value?.userAgent else null
        return rawUa?.replace(Regex("; wv\\b", RegexOption.IGNORE_CASE), "")
            ?.replace(Regex("Version/\\d+\\.\\d+\\s?", RegexOption.IGNORE_CASE), "")
    }
    
    /**
     * Get headers to inject for interceptors
     */
    fun getHeaders(): Map<String, String> {
        val persona = _currentPersona.value ?: return emptyMap()
        val headers = persona.headers.toMutableMap()
        
        // Inject sanitized Client Hints for consistency
        headers["Sec-CH-UA"] = persona.getSecChUaHeader()
        headers["Sec-CH-UA-Mobile"] = if (persona.mobile) "?1" else "?0"
        headers["Sec-CH-UA-Platform"] = "\"${persona.platform}\""
        headers["Sec-CH-UA-Platform-Version"] = "\"${persona.platformVersion}\""
        headers["Sec-CH-UA-Model"] = "\"${persona.model}\""
        
        // Inject Full Version List (filtering out WebView)
        headers["Sec-CH-UA-Full-Version-List"] = persona.brands
            .filter { !it.brand.contains("Android WebView", ignoreCase = true) }
            .joinToString(", ") {
                "\"${it.brand}\";v=\"${it.version}\""
            }
        
        // Safety: ensure User-Agent header matches persona and is sanitized
        headers["User-Agent"] = getUserAgent() ?: persona.userAgent
        
        return headers
    }

    fun generateFingerprintScript(
        screenWidth: Int = 0,
        screenHeight: Int = 0
    ): String {
        if (!_isEnabled.value) return ""
        val persona = _currentPersona.value ?: return ""
        return generatePersonaScript(persona, screenWidth, screenHeight)
    }

    /**
     * Generate persona-specific fingerprinting script using the Privacy Bus
     */
    private fun generatePersonaScript(persona: FakePersona, screenWidth: Int = 0, screenHeight: Int = 0): String {
        // 1. Collect Raw OS Data (Mocking the capture for now)
        // Use provided dimensions if valid (> 0), otherwise fallback to persona defaults to avoid 411x0
        val finalWidth = if (screenWidth > 0) screenWidth else persona.screenWidth
        val finalHeight = if (screenHeight > 0) screenHeight else persona.screenHeight
        
        val rawData = mapOf(
            PrivacyPacket.KEY_SCREEN_WIDTH to finalWidth,
            PrivacyPacket.KEY_SCREEN_HEIGHT to finalHeight,
            PrivacyPacket.KEY_PIXEL_RATIO to persona.pixelRatio,
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
                if (window.__JUS_FAKE_INJECTED__) return;
                window.__JUS_FAKE_INJECTED__ = true;

                const NOISE_SEED = ${persona.noiseSeed + (sessionSeed % 100000).toInt()};
                const PRIVACY_STATE = '${processedPacket.state}';
                
                // --- BRIDGE: reportSuspicion ---
                const reportSuspicion = (points, reason) => {
                    if (window.PrivacyBridge && window.PrivacyBridge.reportSuspicion) {
                        window.PrivacyBridge.reportSuspicion(points, reason);
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

                // --- 1. TIME & PERFORMANCE (Boring Clamp) ---
                try {
                    const originalNow = Performance.prototype.now;
                    Performance.prototype.now = makeNative(function() {
                        const precision = ${data[PrivacyPacket.KEY_TIME_PRECISION_MS] ?: 20};
                        return Math.floor(originalNow.call(this) / precision) * precision;
                    }, 'now');
                    
                    const originalDateNow = Date.now;
                    Date.now = makeNative(function() {
                        const precision = ${data[PrivacyPacket.KEY_TIME_PRECISION_MS] ?: 20};
                        return Math.floor(originalDateNow.call(Date) / precision) * precision;
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
                    const lWidth = Math.round($logicWidth);
                    const lHeight = Math.round($logicHeight);
                    const screenProps = {
                        width: lWidth, height: lHeight,
                        availWidth: lWidth, availHeight: lHeight,
                        colorDepth: 24, pixelDepth: 24
                    };
                    for (const prop in screenProps) {
                        defineSafeProp(screen, prop, () => screenProps[prop]);
                    }
                    defineSafeProp(window, 'devicePixelRatio', () => $pixelRatio);
                } catch(e) {}
                
                // --- 4. NAVIGATOR & CLIENT HINTS ---
                try {
                    defineSafeProp(navigator, 'userAgent', () => '$userAgent');
                    defineSafeProp(navigator, 'appVersion', () => '$userAgent');
                    defineSafeProp(navigator, 'platform', () => '$platformString'); 
                    defineSafeProp(navigator, 'vendor', () => ${if (persona.platform == "Android") "'Google Inc.'" else "'Apple Computer, Inc.'"});
                    defineSafeProp(navigator, 'appName', () => 'Netscape');
                    defineSafeProp(navigator, 'appCodeName', () => 'Mozilla');
                    defineSafeProp(navigator, 'maxTouchPoints', () => ${if (persona.mobile) 5 else 0});
                    defineSafeProp(navigator, 'hardwareConcurrency', () => ${data[PrivacyPacket.KEY_HARDWARE_CONCURRENCY] as? Int ?: 8});
                    defineSafeProp(navigator, 'webdriver', () => false);
                    defineSafeProp(navigator, 'doNotTrack', () => '${persona.doNotTrack}');
                    
                    // Harden Plugins & MimeTypes (Mobile typically has none)
                    if (window.navigator.plugins) {
                        defineSafeProp(navigator, 'plugins', () => {
                            const p = [];
                            p.item = p.namedItem = () => null;
                            p.refresh = () => {};
                            return p;
                        });
                    }
                    if (window.navigator.mimeTypes) {
                        defineSafeProp(navigator, 'mimeTypes', () => {
                            const m = [];
                            m.item = m.namedItem = () => null;
                            return m;
                        });
                    }

                    if (navigator.deviceMemory !== undefined) {
                        defineSafeProp(navigator, 'deviceMemory', () => ${data[PrivacyPacket.KEY_DEVICE_MEMORY] as? Int ?: 8});
                    }

                    if (navigator.userAgentData) {
                        const brands = [${persona.brands.joinToString(",") { "{brand: '${it.brand}', version: '${it.version}'}" }}];
                        const highEntropyValues = {
                            architecture: 'arm', bitness: '64', brands: brands,
                            mobile: ${persona.mobile}, model: '${persona.model}',
                            platform: '${persona.platform}', platformVersion: '${persona.platformVersion}',
                            fullVersionList: brands
                        };
                        defineSafeProp(navigator.userAgentData, 'brands', () => brands);
                        defineSafeProp(navigator.userAgentData, 'mobile', () => ${persona.mobile});
                        defineSafeProp(navigator.userAgentData, 'platform', () => '${persona.platform}');
                        navigator.userAgentData.getHighEntropyValues = makeNative((hints) => {
                            return Promise.resolve(highEntropyValues);
                        }, 'getHighEntropyValues');
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

                // --- 6. CANVAS & AUDIO (Seeded Drift) ---
                try {
                    const originalGetImageData = CanvasRenderingContext2D.prototype.getImageData;
                    CanvasRenderingContext2D.prototype.getImageData = makeNative(function() {
                        const imageData = originalGetImageData.apply(this, arguments);
                        const buffer = imageData.data;
                        for (let i = 0; i < buffer.length; i += 64) {
                            if ((i + NOISE_SEED) % 101 === 0) buffer[i] = buffer[i] ^ 1;
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
                
                // --- 8. WEBGL INVARIANTS ---
                try {
                    const originalGetParameter = WebGLRenderingContext.prototype.getParameter;
                    const originalGetExtension = WebGLRenderingContext.prototype.getExtension;
                    
                    const parameterMap = {
                        0x9245: '${persona.videoCardRenderer}', // UNMASKED_RENDERER_WEBGL
                        0x9246: '${persona.videoCardVendor}',   // UNMASKED_VENDOR_WEBGL
                        0x8DF4: ${persona.shaderPrecision},     // LOW_FLOAT
                        0x8DF5: ${persona.shaderPrecision},     // MEDIUM_FLOAT
                        0x8DF6: ${persona.shaderPrecision}      // HIGH_FLOAT
                    };

                    const handleGetParameter = function(p) {
                        return parameterMap[p] !== undefined ? parameterMap[p] : originalGetParameter.call(this, p);
                    };

                    WebGLRenderingContext.prototype.getParameter = makeNative(handleGetParameter, 'getParameter');
                    
                    WebGLRenderingContext.prototype.getExtension = makeNative(function(name) {
                        const extension = originalGetExtension.call(this, name);
                        if (name === 'WEBGL_debug_renderer_info' && extension) {
                            return {
                                UNMASKED_VENDOR_WEBGL: 0x9245,
                                UNMASKED_RENDERER_WEBGL: 0x9246,
                                getParameter: makeNative(function(p) {
                                    return handleGetParameter.call(this, p);
                                }, 'getParameter')
                            };
                        }
                        return extension;
                    }, 'getExtension');

                    if (window.WebGL2RenderingContext) {
                        WebGL2RenderingContext.prototype.getParameter = WebGLRenderingContext.prototype.getParameter;
                        WebGL2RenderingContext.prototype.getExtension = WebGLRenderingContext.prototype.getExtension;
                    }
                } catch(e) {}
                
                // --- 9. WEBRTC BLOCKING (Local IP Protection) ---
                try {
                    const blockRTC = () => {
                        return makeNative(function() {
                            throw new Error('WebRTC is disabled for privacy');
                        }, 'RTCPeerConnection');
                    };
                    window.RTCPeerConnection = blockRTC();
                    window.webkitRTCPeerConnection = window.RTCPeerConnection;
                    window.mozRTCPeerConnection = window.RTCPeerConnection;
                    window.rtcPeerConnection = window.RTCPeerConnection;
                } catch(e) {}
                try {
                    // matchMedia - return persona-consistent values
                    const originalMatchMedia = window.matchMedia;
                    window.matchMedia = makeNative(function(query) {
                        const result = originalMatchMedia.call(window, query);
                        
                        // Intercept color scheme and contrast to prevent fingerprinting
                        if (query.includes('prefers-color-scheme')) {
                            // Default to light to reduce uniqueness, or sync with persona if we add a 'theme' field
                            const isDark = query.includes('dark');
                            return {
                                matches: !isDark, 
                                media: query,
                                onchange: null,
                                addListener: makeNative(function() {}, 'addListener'),
                                removeListener: makeNative(function() {}, 'removeListener'),
                                addEventListener: makeNative(function() {}, 'addEventListener'),
                                removeEventListener: makeNative(function() {}, 'removeEventListener'),
                                dispatchEvent: makeNative(function() { return false; }, 'dispatchEvent'),
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
                                // Relax: return false but don't report suspicion for common layout checks
                                return false; 
                            }
                            return originalCheck(font, text);
                        }, 'check');
                    }

                    // Limit fonts.forEach / iterate
                    if (document.fonts) {
                        document.fonts.forEach = makeNative(function(callback) {
                            // No-op instead of reporting suspicion to prevent crashes
                        }, 'forEach');
                        
                        // Fake an iterator to prevent crashes on some engines
                        if (Symbol.iterator) {
                            document.fonts[Symbol.iterator] = makeNative(function* () {
                                yield* [];
                            }, 'Symbol.iterator');
                        }
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

                // ===== 9. STORAGE PARTITIONING SIMULATION =====
                try {
                    const originalSetItem = Storage.prototype.setItem;
                    Storage.prototype.setItem = makeNative(function(key, value) {
                        // Simulate partitioning by noting if we're in a third-party context
                        // (Rough heuristic: if top != self)
                        if (window.top !== window.self) {
                            // In a real browser, this would be isolated. 
                            // We can't easily isolate in current WebView, but we can prevent 
                            // cross-site correlation for well-known tracking keys.
                            const suspiciousKeys = ['_fbp', '_ga', '_gid', 'ajs_user_id', 'ajs_anonymous_id'];
                            if (suspiciousKeys.some(k => key.includes(k))) {
                                // Add persona-seeded noise to the key/value to break correlation
                                arguments[1] = value + (NOISE_SEED % 100);
                            }
                        }
                        return originalSetItem.apply(this, arguments);
                    }, 'setItem');
                } catch(e) {}

                // ===== 10. PRIVACY SANDBOX & CHIPS =====
                try {
                    // Simulate Cookie Healthy Incremental Partitioning (CHIPS) support
                    // We can't enforce partitioning at the protocol level easily here, 
                    // but we can signal support via navigator.cookieDeprecationLabel if it existed,
                    // or by ensuring document.cookie parsing doesn't reveal partition logic.
                    
                    if (navigator.userAgentData) {
                        const personaBrands = [
                            ${persona.brands.filter { !it.brand.contains("Android WebView", true) }.joinToString(",") { "{ brand: '${it.brand}', version: '${it.version.split(".").first()}' }" }}
                        ];
                        const personaFullBrands = [
                            ${persona.brands.filter { !it.brand.contains("Android WebView", true) }.joinToString(",") { "{ brand: '${it.brand}', version: '${it.version}' }" }}
                        ];

                        // Mock brands
                        try {
                            Object.defineProperty(navigator.userAgentData, 'brands', {
                                get: makeNative(() => personaBrands, 'get brands')
                            });
                        } catch(e) {}

                        // Ensure Privacy Sandbox attributes are consistent
                        const originalGetHighEntropyValues = navigator.userAgentData.getHighEntropyValues;
                        navigator.userAgentData.getHighEntropyValues = makeNative(function(hints) {
                            return originalGetHighEntropyValues.call(this, hints).then(values => {
                                if (hints.includes('brands')) values.brands = personaBrands;
                                if (hints.includes('fullVersionList')) values.fullVersionList = personaFullBrands;
                                if (hints.includes('model')) values.model = '${persona.model}';
                                if (hints.includes('platformVersion')) values.platformVersion = '${persona.platformVersion}';
                                if (hints.includes('uaFullVersion')) values.uaFullVersion = '${persona.browserVersion}';
                                if (hints.includes('bitness')) values.bitness = '64';
                                if (hints.includes('architecture')) values.architecture = 'arm';
                                return values;
                            });
                        }, 'getHighEntropyValues');
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

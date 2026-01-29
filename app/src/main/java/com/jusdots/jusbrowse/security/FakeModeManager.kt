package com.jusdots.jusbrowse.security

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /**
     * Randomize session-specific values (Battery)
     * Called when fake mode is enabled or initialized.
     */
    private fun randomizeSessionState() {
        // Battery between 20% and 98%
        sessionBatteryLevel = 0.20 + (Math.random() * 0.78)
        // Charging 50/50
        sessionBatteryCharging = Math.random() > 0.5
    }

    /**
     * Initialize logic (load saved persona)
     */
    fun init(context: Context) {
        val savedId = getSavedPersonaId(context)
        
        if (savedId != null) {
            val persona = PersonaRepository.getPersonaById(savedId)
            if (persona != null) {
                _currentPersona.value = persona
                _isEnabled.value = true
                randomizeSessionState() // Randomize battery for this session
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
        if (_currentPersona.value != persona) {
            randomizeSessionState() // Randomize battery for this session
            // Save new state
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
            FingerprintingProtection.protectionScript
        }
    }

    /**
     * Generate persona-specific fingerprinting script
     */
    private fun generatePersonaScript(persona: FakePersona): String {
        // Calculate Timezone Offset (Phase 1 Fix)
        val tz = java.util.TimeZone.getTimeZone(persona.timezone)
        val offsetMillis = tz.getOffset(System.currentTimeMillis())
        val jsOffsetMinutes = -(offsetMillis / 60000)
        
        val tzDisplayName = tz.getDisplayName(tz.inDaylightTime(java.util.Date()), java.util.TimeZone.LONG)
        
        // Logical Screen (Phase 1 Fix)
        val logicWidth = (persona.screenWidth / persona.pixelRatio).toInt()
        val logicHeight = (persona.screenHeight / persona.pixelRatio).toInt()

        return """
            (function() {
                'use strict';
                const NOISE_SEED = ${persona.noiseSeed};
                const CLOCK_SKEW_MS = ${persona.clockSkewMs};
                
                // Helper: Pseudo-random generator using seeds
                const rnd = (seed) => {
                    const x = Math.sin(seed) * 10000;
                    return x - Math.floor(x);
                };

                // ===== PHASE 4: NATIVE FUNCTION CAMOUFLAGE =====
                // This prevents scripts from detecting our overrides via .toString()
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

                // ===== TIME & PERFORMANCE (Phase 3+4: Precision Refinement) =====
                try {
                    const perfOffset = (rnd(NOISE_SEED) * 5) - 2.5; // Subtle skew
                    
                    const originalNow = Performance.prototype.now;
                    Performance.prototype.now = makeNative(function() {
                        return originalNow.call(this) + perfOffset;
                    }, 'now');
                    
                    const originalDate = Date;
                    const originalDateNow = Date.now;
                    
                    Date.now = makeNative(function() {
                        return originalDateNow() + CLOCK_SKEW_MS;
                    }, 'now');
                    
                    window.Date = makeNative(function(...args) {
                        if (args.length === 0) {
                            return new originalDate(originalDateNow() + CLOCK_SKEW_MS);
                        }
                        return new originalDate(...args);
                    }, 'Date');
                    
                    window.Date.prototype = originalDate.prototype;
                    window.Date.now = Date.now;
                    window.Date.parse = makeNative(originalDate.parse, 'parse');
                    window.Date.UTC = makeNative(originalDate.UTC, 'UTC');
                } catch(e) {}

                // ===== AUTOMATION & PRIVACY =====
                try {
                    Object.defineProperty(navigator, 'webdriver', { get: makeNative(() => false, 'get webdriver') });
                    Object.defineProperty(navigator, 'doNotTrack', { get: makeNative(() => '${persona.doNotTrack}', 'get doNotTrack') });
                } catch(e) {}
                
                // ===== BATTERY API =====
                try {
                    const batteryMock = {
                        charging: $sessionBatteryCharging,
                        chargingTime: $sessionBatteryCharging ? 3600 : Infinity,
                        dischargingTime: $sessionBatteryCharging ? Infinity : 18000,
                        level: $sessionBatteryLevel,
                        addEventListener: makeNative(function() {}, 'addEventListener'),
                        removeEventListener: makeNative(function() {}, 'removeEventListener'),
                        dispatchEvent: makeNative(function() { return false; }, 'dispatchEvent'),
                        onchargingchange: null,
                        onchargingtimechange: null,
                        ondischargingtimechange: null,
                        onlevelchange: null
                    };
                    
                    const p = Promise.resolve(batteryMock);
                    navigator.getBattery = makeNative(function() { return p; }, 'getBattery');
                } catch(e) {}

                // ===== NETWORK INFO =====
                try {
                    const connection = {
                        effectiveType: '${persona.networkType}',
                        rtt: ${persona.networkRtt},
                        downlink: ${persona.networkDownlink},
                        saveData: false,
                        addEventListener: makeNative(function() {}, 'addEventListener'),
                        removeEventListener: makeNative(function() {}, 'removeEventListener'),
                        dispatchEvent: makeNative(function() { return false; }, 'dispatchEvent')
                    };
                    Object.defineProperty(navigator, 'connection', { get: makeNative(() => connection, 'get connection') });
                } catch(e) {}

                // ===== MEDIA DEVICES (Phase 4: Accurate Labels) =====
                try {
                    if (navigator.mediaDevices && navigator.mediaDevices.enumerateDevices) {
                        const labels = { ${persona.mediaDeviceLabels.entries.joinToString(",") { "'${it.key}': '${it.value}'" }} };
                        const fakeDevices = [
                            { deviceId: 'mic_' + NOISE_SEED, kind: 'audioinput', label: labels['audioinput'] || 'Internal Microphone', groupId: 'group1_' + NOISE_SEED },
                            { deviceId: 'spk_' + NOISE_SEED, kind: 'audiooutput', label: labels['audiooutput'] || 'Speaker', groupId: 'group1_' + NOISE_SEED },
                            { deviceId: 'back_' + NOISE_SEED, kind: 'videoinput', label: labels['videoinput_back'] || 'Back Camera', groupId: 'group2_' + NOISE_SEED }
                        ];
                        if (${persona.mobile}) {
                             fakeDevices.push({ deviceId: 'front_' + NOISE_SEED, kind: 'videoinput', label: labels['videoinput_front'] || 'Front Camera', groupId: 'group2_' + NOISE_SEED });
                        }

                        navigator.mediaDevices.enumerateDevices = makeNative(function() {
                            return Promise.resolve(fakeDevices);
                        }, 'enumerateDevices');
                    }
                } catch(e) {}

                // ===== WebRTC LEAK PREVENTION (Phase 4: Robust Candidate Filtering) =====
                try {
                    const originalRTCPeerConnection = window.RTCPeerConnection;
                    if (originalRTCPeerConnection) {
                         window.RTCPeerConnection = makeNative(function(config) {
                             const pc = new originalRTCPeerConnection(config);
                             
                             // Intercepting onicecandidate is the best JS-only way to block leaks
                             let originalOnIceCandidate = null;
                             Object.defineProperty(pc, 'onicecandidate', {
                                 get: () => originalOnIceCandidate,
                                 set: (val) => {
                                     originalOnIceCandidate = makeNative(function(event) {
                                         if (event.candidate && (event.candidate.candidate.includes('.local') || 
                                             /192\.168\.|10\.|172\.(1[6-9]|2[0-9]|3[0-1])\./.test(event.candidate.candidate))) {
                                             // Drop local candidates
                                             return;
                                         }
                                         if (val) val.call(pc, event);
                                     }, 'onicecandidate');
                                 }
                             });
                             return pc;
                         }, 'RTCPeerConnection');
                         window.RTCPeerConnection.prototype = originalRTCPeerConnection.prototype;
                    }
                } catch(e) {}

                // ===== SCREEN & VIEWPORT =====
                try {
                    const screenProps = {
                        width: $logicWidth, height: $logicHeight,
                        availWidth: $logicWidth, availHeight: $logicHeight,
                        colorDepth: 24, pixelDepth: 24
                    };
                    for (const prop in screenProps) {
                        Object.defineProperty(screen, prop, { get: makeNative(() => screenProps[prop], 'get ' + prop) });
                    }
                    Object.defineProperty(window, 'devicePixelRatio', { get: makeNative(() => ${persona.pixelRatio}, 'get devicePixelRatio') });
                    ['innerWidth', 'innerHeight', 'outerWidth', 'outerHeight'].forEach(prop => {
                        Object.defineProperty(window, prop, { get: makeNative(() => (prop.includes('Width') ? $logicWidth : $logicHeight), 'get ' + prop) });
                    });
                } catch(e) {}
                
                // ===== NAVIGATOR & CLIENT HINTS =====
                try {
                    Object.defineProperty(navigator, 'userAgent', { get: makeNative(() => '${persona.userAgent}', 'get userAgent') });
                    Object.defineProperty(navigator, 'platform', { get: makeNative(() => 'Linux armv8l', 'get platform') }); 
                    Object.defineProperty(navigator, 'maxTouchPoints', { get: makeNative(() => ${if (persona.mobile) 5 else 0}, 'get maxTouchPoints') });
                    Object.defineProperty(navigator, 'hardwareConcurrency', { get: makeNative(() => ${persona.cpuCores}, 'get hardwareConcurrency') });
                    if (navigator.deviceMemory !== undefined) {
                        Object.defineProperty(navigator, 'deviceMemory', { get: makeNative(() => ${persona.ramGB}, 'get deviceMemory') });
                    }

                    if (navigator.userAgentData) {
                        const brands = [${persona.brands.joinToString(",") { "{brand: '${it.brand}', version: '${it.version}'}" }}];
                        const highEntropyValues = {
                            architecture: 'arm', bitness: '64', brands: brands,
                            mobile: ${persona.mobile}, model: '${persona.model}',
                            platform: '${persona.platform}', platformVersion: '${persona.platformVersion}',
                            uaFullVersion: '${persona.browserVersion}'
                        };
                        Object.defineProperty(navigator.userAgentData, 'brands', { get: makeNative(() => brands, 'get brands') });
                        Object.defineProperty(navigator.userAgentData, 'mobile', { get: makeNative(() => ${persona.mobile}, 'get mobile') });
                        Object.defineProperty(navigator.userAgentData, 'platform', { get: makeNative(() => '${persona.platform}', 'get platform') });
                        navigator.userAgentData.getHighEntropyValues = makeNative((hints) => Promise.resolve(highEntropyValues), 'getHighEntropyValues');
                    }
                } catch(e) {}
                
                // ===== LOCALE & TIMEZONE =====
                try {
                    Object.defineProperty(navigator, 'language', { get: makeNative(() => '${persona.locale}', 'get language') });
                    Object.defineProperty(navigator, 'languages', { get: makeNative(() => ${persona.languages.joinToString(",", "[", "]") { "'$it'" }}, 'get languages') });
                    
                    const originalDateTimeFormat = Intl.DateTimeFormat;
                    window.Intl.DateTimeFormat = makeNative(function(locales, options) {
                        options = options || {};
                        if (!options.timeZone) options.timeZone = '${persona.timezone}';
                        return new originalDateTimeFormat(locales, options);
                    }, 'DateTimeFormat');
                    window.Intl.DateTimeFormat.prototype = originalDateTimeFormat.prototype;
                    Object.setPrototypeOf(window.Intl.DateTimeFormat, originalDateTimeFormat);
                    
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
                            hour12: false, timeZone: '${persona.timezone}'
                        };
                        const formatter = new originalDateTimeFormat('en-US', options);
                        const parts = formatter.formatToParts(this);
                        const p = {}; parts.forEach(part => p[part.type] = part.value);
                        
                        const offsetHours = Math.floor(Math.abs(targetOffsetMin) / 60).toString().padStart(2, '0');
                        const offsetMins = (Math.abs(targetOffsetMin) % 60).toString().padStart(2, '0');
                        const sign = targetOffsetMin > 0 ? '-' : '+';
                        return `${"$"}{p.weekday} ${"$"}{p.month} ${"$"}{p.day} ${"$"}{p.year} ${"$"}{p.hour}:${"$"}{p.minute}:${"$"}{p.second} GMT${"$"}{sign}${"$"}{offsetHours}${"$"}{offsetMins} (${"$"}{targetTzName})`;
                    }, 'toString');
                } catch(e) {}

                // ===== PHASE 4: WEBGL PARAMETRIC SPOOFING =====
                try {
                    const webglTargets = [WebGLRenderingContext, WebGL2RenderingContext];
                    const webglLimits = {
                        37445: '${persona.videoCardVendor}', // UNMASKED_VENDOR_WEBGL
                        37446: '${persona.videoCardRenderer}', // UNMASKED_RENDERER_WEBGL
                        3379: ${persona.webglMaxTextureSize}, // MAX_TEXTURE_SIZE
                        3413: ${persona.webglMaxRenderBufferSize}, // MAX_RENDERBUFFER_SIZE
                        34076: ${persona.webglMaxTextureSize}, // MAX_CUBE_MAP_TEXTURE_SIZE
                    };
                    const extensions = ${persona.webglExtensions.joinToString(",", "[", "]") { "'$it'" }};

                    webglTargets.forEach(t => {
                        if (!t) return;
                        const originalGetParameter = t.prototype.getParameter;
                        t.prototype.getParameter = makeNative(function(param) {
                            if (webglLimits[param] !== undefined) return webglLimits[param];
                            return originalGetParameter.apply(this, arguments);
                        }, 'getParameter');
                        
                        const originalGetSupportedExtensions = t.prototype.getSupportedExtensions;
                        t.prototype.getSupportedExtensions = makeNative(function() {
                            return extensions;
                        }, 'getSupportedExtensions');
                    });
                } catch(e) {}

                // ===== PHASE 4: CANVAS & FONT NOISE (Advanced) =====
                try {
                    const originalGetImageData = CanvasRenderingContext2D.prototype.getImageData;
                    CanvasRenderingContext2D.prototype.getImageData = makeNative(function(x, y, w, h) {
                        const imageData = originalGetImageData.apply(this, arguments);
                        const buffer = imageData.data;
                        // Add extremely subtle entropy (1 bit flip per 400 pixels)
                        for (let i = 0; i < buffer.length; i += 400) {
                            buffer[i] = buffer[i] ^ (NOISE_SEED & 1);
                        }
                        return imageData;
                    }, 'getImageData');
                    
                    // Subtle Font Measurement Jitter
                    const originalMeasureText = CanvasRenderingContext2D.prototype.measureText;
                    CanvasRenderingContext2D.prototype.measureText = makeNative(function(text) {
                        const metrics = originalMeasureText.apply(this, arguments);
                        // We can't easily modify the TextMetrics object as it's often read-only
                        // But we can return a proxy or just leave it for now.
                        // Better: Font fingerprinting is usually done via height/width of spans.
                        return metrics;
                    }, 'measureText');
                } catch(e) {}
                
                // ===== AUDIO NOISE =====
                try {
                    const originalGetChannelData = AudioBuffer.prototype.getChannelData;
                    AudioBuffer.prototype.getChannelData = makeNative(function(channel) {
                        const results = originalGetChannelData.apply(this, arguments);
                        const noise = rnd(NOISE_SEED + channel) * 0.00000001; 
                        for (let i = 0; i < results.length; i+=200) {
                             results[i] += noise;
                        }
                        return results;
                    }, 'getChannelData');
                } catch(e) {}

            })();
        """.trimIndent()
    }
}

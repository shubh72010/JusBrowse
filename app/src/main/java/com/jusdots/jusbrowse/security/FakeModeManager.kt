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
    private var sessionSeed: Long = System.currentTimeMillis()

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
        boringEnabled: Boolean = false
    ): String {
        val persona = if (jusFakeEnabled) _currentPersona.value else null
        
        return when {
            jusFakeEnabled && persona != null -> generatePersonaScript(persona)
            boringEnabled -> BoringEngine.generateScript(sessionSeed)
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
                    defineSafeProp(navigator, 'platform', () => '$platformString'); 
                    defineSafeProp(navigator, 'vendor', () => ${if (persona.platform == "Android") "'Google Inc.'" else "'Apple Computer, Inc.'"});
                    defineSafeProp(navigator, 'maxTouchPoints', () => ${if (persona.mobile) 5 else 0});
                    defineSafeProp(navigator, 'hardwareConcurrency', () => ${data[PrivacyPacket.KEY_HARDWARE_CONCURRENCY] as? Int ?: 8});
                    defineSafeProp(navigator, 'webdriver', () => false);
                    defineSafeProp(navigator, 'doNotTrack', () => '${persona.doNotTrack}');
                    if (navigator.deviceMemory !== undefined) {
                        defineSafeProp(navigator, 'deviceMemory', () => ${data[PrivacyPacket.KEY_DEVICE_MEMORY] as? Int ?: 8});
                    }

                    if (navigator.userAgentData) {
                        const brands = [${persona.brands.joinToString(",") { "{brand: '${it.brand}', version: '${it.version}'}" }}];
                        const highEntropyValues = {
                            architecture: 'arm', bitness: '64', brands: brands,
                            mobile: ${persona.mobile}, model: '${persona.model}',
                            platform: '${persona.platform}', platformVersion: '${persona.platformVersion}',
                        };
                        defineSafeProp(navigator.userAgentData, 'brands', () => brands);
                        defineSafeProp(navigator.userAgentData, 'mobile', () => ${persona.mobile});
                        defineSafeProp(navigator.userAgentData, 'platform', () => '${persona.platform}');
                        navigator.userAgentData.getHighEntropyValues = makeNative(() => Promise.resolve(highEntropyValues), 'getHighEntropyValues');
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

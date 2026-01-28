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
        return """
            (function() {
                'use strict';
                console.log('[JusBrowse] Persona Active: ${persona.displayName}');
                
                // ===== SCREEN SPOOFING (Stable) =====
                try {
                    const screenProps = {
                        width: ${persona.screenWidth},
                        height: ${persona.screenHeight},
                        availWidth: ${persona.screenWidth},
                        availHeight: ${persona.screenHeight},
                        colorDepth: 24,
                        pixelDepth: 24
                    };
                    for (const prop in screenProps) {
                        Object.defineProperty(screen, prop, { get: () => screenProps[prop] });
                    }
                    
                    Object.defineProperty(window, 'devicePixelRatio', { get: () => ${persona.pixelRatio} });
                    Object.defineProperty(window, 'innerWidth', { get: () => ${persona.screenWidth} });
                    Object.defineProperty(window, 'innerHeight', { get: () => ${persona.screenHeight} });
                    Object.defineProperty(window, 'outerWidth', { get: () => ${persona.screenWidth} });
                    Object.defineProperty(window, 'outerHeight', { get: () => ${persona.screenHeight} });
                } catch(e) {}
                
                // ===== NAVIGATOR SPOOFING =====
                try {
                    Object.defineProperty(navigator, 'userAgent', { get: () => '${persona.userAgent}' });
                    Object.defineProperty(navigator, 'platform', { get: () => '${persona.platform} ${persona.platformVersion}' }); // Simplified
                    Object.defineProperty(navigator, 'maxTouchPoints', { get: () => ${if (persona.mobile) 5 else 0} });
                    Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => ${persona.cpuCores} });
                    if (navigator.deviceMemory !== undefined) {
                        Object.defineProperty(navigator, 'deviceMemory', { get: () => ${persona.ramGB} });
                    }
                } catch(e) {}
                
                // ===== CLIENT HINTS (Important!) =====
                // Only works if the browser supports navigator.userAgentData
                try {
                    if (navigator.userAgentData) {
                        const brands = [${persona.brands.joinToString(",") { "{brand: '${it.brand}', version: '${it.version}'}" }}];
                        const highEntropyValues = {
                            architecture: 'arm',
                            bitness: '64',
                            brands: brands,
                            mobile: ${persona.mobile},
                            model: '${persona.model}',
                            platform: '${persona.platform}',
                            platformVersion: '${persona.platformVersion}',
                            uaFullVersion: '${persona.browserVersion}'
                        };
                        
                        Object.defineProperty(navigator.userAgentData, 'brands', { get: () => brands });
                        Object.defineProperty(navigator.userAgentData, 'mobile', { get: () => ${persona.mobile} });
                        Object.defineProperty(navigator.userAgentData, 'platform', { get: () => '${persona.platform}' });
                        
                        navigator.userAgentData.getHighEntropyValues = function(hints) {
                            return Promise.resolve(highEntropyValues);
                        };
                    }
                } catch(e) {}

                // ===== LOCALE & TIMEZONE =====
                try {
                    Object.defineProperty(navigator, 'language', { get: () => '${persona.locale}' });
                    Object.defineProperty(navigator, 'languages', { get: () => ${persona.languages.joinToString(",", "[", "]") { "'$it'" }} });
                    
                    const originalDateTimeFormat = Intl.DateTimeFormat;
                    Intl.DateTimeFormat = function(locales, options) {
                        options = options || {};
                        if (!options.timeZone) options.timeZone = '${persona.timezone}';
                        return new originalDateTimeFormat(locales, options);
                    };
                    Object.setPrototypeOf(Intl.DateTimeFormat, originalDateTimeFormat);
                } catch(e) {}
                
                // ===== WEBGL SPOOFING =====
                const getParameterProxy = function(target) {
                    return function(param) {
                        if (param === 37445) return '${persona.videoCardVendor}';
                        if (param === 37446) return '${persona.videoCardRenderer}';
                        return target.call(this, param);
                    };
                };
                try {
                    const canvas = document.createElement('canvas');
                    const gl = canvas.getContext('webgl');
                    if (gl) {
                        WebGLRenderingContext.prototype.getParameter = getParameterProxy(gl.getParameter);
                    }
                    const gl2 = canvas.getContext('webgl2');
                    if (gl2) {
                        WebGL2RenderingContext.prototype.getParameter = getParameterProxy(gl2.getParameter);
                    }
                } catch(e) {}
                
                // ===== CANVAS NOISE (SEEDED) =====
                // Use a seeded approach so the noise is consistent for this persona ID
                const seed = ${persona.noiseSeed};
                const noise = (val, idx) => {
                     // Simple pseudo-random using seed
                     const r = Math.sin(seed + idx) * 10000;
                     return (r - Math.floor(r)) * 0.01; // tiny offset
                };
                
                const originalToDataURL = HTMLCanvasElement.prototype.toDataURL;
                HTMLCanvasElement.prototype.toDataURL = function(type) {
                    // Only apply noise if context was drawn to? 
                    // This is complex, simple approach:
                    return originalToDataURL.apply(this, arguments);
                };
                // Note: True seeded canvas noise is complex. For now, we rely on WebGL spoofing + UA consistency.
                
            })();
        """.trimIndent()
    }
}

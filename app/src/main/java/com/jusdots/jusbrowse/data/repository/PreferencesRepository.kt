package com.jusdots.jusbrowse.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import androidx.datastore.core.DataStoreFactory
import com.jusdots.jusbrowse.security.EncryptedPreferenceSerializer
import java.io.File

object LocalDataStore {
    private var instance: DataStore<Preferences>? = null

    fun getInstance(context: Context): DataStore<Preferences> {
        val appContext = context.applicationContext
        return instance ?: synchronized(this) {
            instance ?: DataStoreFactory.create(
                serializer = EncryptedPreferenceSerializer,
                produceFile = { File(appContext.filesDir, "datastore/encrypted_preferences.pb") },
                migrations = listOf(
                    androidx.datastore.preferences.SharedPreferencesMigration(
                        context = appContext,
                        sharedPreferencesName = "browser_preferences"
                    )
                )
            ).also { instance = it }
        }
    }
}

class PreferencesRepository(private val context: Context) {
    private val dataStore = LocalDataStore.getInstance(context)
    
    private object PreferenceKeys {
        val SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val HOME_PAGE = stringPreferencesKey("home_page")
        val JAVASCRIPT_ENABLED = booleanPreferencesKey("javascript_enabled")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val SAVED_TABS = stringPreferencesKey("saved_tabs")
        val SAVED_WINDOW_STATES = stringPreferencesKey("saved_window_states")
        val ACTIVE_TAB_INDEX = stringPreferencesKey("active_tab_index")
        val AD_BLOCK_ENABLED = booleanPreferencesKey("ad_block_enabled")
        val HTTPS_ONLY = booleanPreferencesKey("https_only")
        val FLAG_SECURE_ENABLED = booleanPreferencesKey("flag_secure_enabled")
        val DO_NOT_TRACK_ENABLED = booleanPreferencesKey("do_not_track_enabled")
        val COOKIE_BLOCKER_ENABLED = booleanPreferencesKey("cookie_blocker_enabled")
        val POPUP_BLOCKER_ENABLED = booleanPreferencesKey("popup_blocker_enabled")
        val SAVED_SHORTCUTS = stringPreferencesKey("saved_shortcuts")
        val SHOW_TAB_ICONS = booleanPreferencesKey("show_tab_icons")
        val THEME_PRESET = stringPreferencesKey("theme_preset")
        val VIRUSTOTAL_API_KEY = stringPreferencesKey("virustotal_api_key")
        val KOODOUS_API_KEY = stringPreferencesKey("koodous_api_key")
        // New UI customization preferences
        val FOLLIAN_MODE = booleanPreferencesKey("follian_mode")
        val TOOLBAR_POSITION = stringPreferencesKey("toolbar_position")
        val COMPACT_MODE = booleanPreferencesKey("compact_mode")
        val ADDRESS_BAR_STYLE = stringPreferencesKey("address_bar_style")
        val AMOLED_BLACK_ENABLED = booleanPreferencesKey("amoled_black_enabled")
        val START_PAGE_WALLPAPER_URI = stringPreferencesKey("start_page_wallpaper_uri")
        val START_PAGE_BLUR_AMOUNT = stringPreferencesKey("start_page_blur_amount")
        val CUSTOM_DOH_URL = stringPreferencesKey("custom_doh_url")
        
        // Engines
        val DEFAULT_ENGINE_ENABLED = booleanPreferencesKey("default_engine_enabled")
        val JUS_FAKE_ENGINE_ENABLED = booleanPreferencesKey("jus_fake_engine_enabled")
        val BORING_ENGINE_ENABLED = booleanPreferencesKey("boring_engine_enabled")
        val MULTI_MEDIA_PLAYBACK_ENABLED = booleanPreferencesKey("multi_media_playback_enabled")
        val APP_FONT = stringPreferencesKey("app_font")
        val BACKGROUND_PRESET = stringPreferencesKey("background_preset")
        val SAVED_STICKERS = stringPreferencesKey("saved_stickers")
        val STICKERS_ENABLED = booleanPreferencesKey("stickers_enabled")
        val FORCE_DARK_MODE = booleanPreferencesKey("force_dark_mode")
        val SETTINGS_VERSION = androidx.datastore.preferences.core.intPreferencesKey("settings_version")
    }

    val searchEngine: Flow<String> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.SEARCH_ENGINE] ?: "DuckDuckGo"
    }

    /**
     * One-time migration for users upgrading from v0.0.4-5A.
     * Syncs engine state from FakeModeManager if it was enabled before DataStore tracked it.
     */
    suspend fun migrateIfNeeded() {
        val prefs = dataStore.data.first()
        val version = prefs[PreferenceKeys.SETTINGS_VERSION] ?: 0
        if (version < 1) {
            // If FakeModeManager was active but DataStore didn't know, sync it
            if (com.jusdots.jusbrowse.security.FakeModeManager.isEnabled.value) {
                setActiveEngine(jusFake = true)
            }
            dataStore.edit { it[PreferenceKeys.SETTINGS_VERSION] = 1 }
        }
    }

    val homePage: Flow<String> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.HOME_PAGE] ?: "about:blank"
    }

    val javascriptEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.JAVASCRIPT_ENABLED] ?: true
    }

    val darkMode: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.DARK_MODE] ?: true
    }

    val savedTabs: Flow<String?> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.SAVED_TABS]
    }

    val savedWindowStates: Flow<String?> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.SAVED_WINDOW_STATES]
    }

    val activeTabIndex: Flow<Int> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.ACTIVE_TAB_INDEX]?.toIntOrNull() ?: 0
    }

    val adBlockEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.AD_BLOCK_ENABLED] ?: true
    }

    val httpsOnly: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.HTTPS_ONLY] ?: false
    }

    val flagSecureEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.FLAG_SECURE_ENABLED] ?: true
    }

    val doNotTrackEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.DO_NOT_TRACK_ENABLED] ?: false
    }

    val cookieBlockerEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.COOKIE_BLOCKER_ENABLED] ?: false
    }

    val popupBlockerEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.POPUP_BLOCKER_ENABLED] ?: true
    }

    val showTabIcons: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.SHOW_TAB_ICONS] ?: false
    }

    val themePreset: Flow<String> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.THEME_PRESET] ?: "SYSTEM"
    }

    val virusTotalApiKey: Flow<String> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.VIRUSTOTAL_API_KEY] ?: ""
    }

    val koodousApiKey: Flow<String> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.KOODOUS_API_KEY] ?: ""
    }

    val customDohUrl: Flow<String> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.CUSTOM_DOH_URL] ?: ""
    }

    suspend fun setSearchEngine(searchEngine: String) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.SEARCH_ENGINE] = searchEngine
        }
    }

    suspend fun setHomePage(homePage: String) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.HOME_PAGE] = homePage
        }
    }

    suspend fun setJavascriptEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.JAVASCRIPT_ENABLED] = enabled
        }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.DARK_MODE] = enabled
        }
    }

    suspend fun saveSession(tabsJson: String, windowStatesJson: String, activeIndex: Int) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.SAVED_TABS] = tabsJson
            preferences[PreferenceKeys.SAVED_WINDOW_STATES] = windowStatesJson
            preferences[PreferenceKeys.ACTIVE_TAB_INDEX] = activeIndex.toString()
        }
    }

    suspend fun setAdBlockEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.AD_BLOCK_ENABLED] = enabled
        }
    }

    suspend fun setHttpsOnly(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.HTTPS_ONLY] = enabled
        }
    }

    suspend fun setFlagSecureEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.FLAG_SECURE_ENABLED] = enabled
        }
    }

    suspend fun setDoNotTrackEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.DO_NOT_TRACK_ENABLED] = enabled
        }
    }

    val savedShortcuts: Flow<String?> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.SAVED_SHORTCUTS]
    }

    suspend fun saveShortcuts(shortcutsJson: String) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.SAVED_SHORTCUTS] = shortcutsJson
        }
    }

    suspend fun setCookieBlockerEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.COOKIE_BLOCKER_ENABLED] = enabled
        }
    }

    suspend fun setPopupBlockerEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.POPUP_BLOCKER_ENABLED] = enabled
        }
    }

    suspend fun setShowTabIcons(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.SHOW_TAB_ICONS] = enabled
        }
    }

    suspend fun setThemePreset(preset: String) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.THEME_PRESET] = preset
        }
    }

    suspend fun setVirusTotalApiKey(key: String) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.VIRUSTOTAL_API_KEY] = key
        }
    }

    suspend fun setKoodousApiKey(key: String) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.KOODOUS_API_KEY] = key
        }
    }

    suspend fun setCustomDohUrl(url: String) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.CUSTOM_DOH_URL] = url
        }
    }

    // ============ NEW UI CUSTOMIZATION PREFERENCES ============

    val follianMode: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.FOLLIAN_MODE] ?: false
    }

    val toolbarPosition: Flow<String> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.TOOLBAR_POSITION] ?: "TOP"
    }

    val compactMode: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.COMPACT_MODE] ?: false
    }

    val addressBarStyle: Flow<String> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.ADDRESS_BAR_STYLE] ?: "ROUNDED"
    }

    suspend fun setFollianMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.FOLLIAN_MODE] = enabled
        }
    }

    suspend fun setToolbarPosition(position: String) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.TOOLBAR_POSITION] = position
        }
    }

    suspend fun setCompactMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.COMPACT_MODE] = enabled
        }
    }

    suspend fun setAddressBarStyle(style: String) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.ADDRESS_BAR_STYLE] = style
        }
    }

    val amoledBlackEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.AMOLED_BLACK_ENABLED] ?: false
    }


    suspend fun setAmoledBlackEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.AMOLED_BLACK_ENABLED] = enabled
        }
    }


    val startPageWallpaperUri: Flow<String?> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.START_PAGE_WALLPAPER_URI]
    }

    val startPageBlurAmount: Flow<Float> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.START_PAGE_BLUR_AMOUNT]?.toFloat() ?: 0f
    }

    suspend fun setStartPageWallpaperUri(uri: String?) {
        dataStore.edit { preferences ->
            if (uri != null) {
                preferences[PreferenceKeys.START_PAGE_WALLPAPER_URI] = uri
            } else {
                preferences.remove(PreferenceKeys.START_PAGE_WALLPAPER_URI)
            }
        }
    }

    suspend fun setStartPageBlurAmount(amount: Float) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.START_PAGE_BLUR_AMOUNT] = amount.toString()
        }
    }

    // Engines
    val defaultEngineEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.DEFAULT_ENGINE_ENABLED] ?: true
    }

    val jusFakeEngineEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.JUS_FAKE_ENGINE_ENABLED] ?: false
    }

    val boringEngineEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.BORING_ENGINE_ENABLED] ?: false
    }

    val multiMediaPlaybackEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.MULTI_MEDIA_PLAYBACK_ENABLED] ?: false
    }

    val appFont: Flow<String> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.APP_FONT] ?: "SYSTEM"
    }

    val backgroundPreset: Flow<String> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.BACKGROUND_PRESET] ?: "NONE"
    }

    suspend fun setActiveEngine(default: Boolean = false, jusFake: Boolean = false, boring: Boolean = false) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.DEFAULT_ENGINE_ENABLED] = default
            preferences[PreferenceKeys.JUS_FAKE_ENGINE_ENABLED] = jusFake
            preferences[PreferenceKeys.BORING_ENGINE_ENABLED] = boring
        }
    }



    suspend fun setMultiMediaPlaybackEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.MULTI_MEDIA_PLAYBACK_ENABLED] = enabled
        }
    }

    suspend fun setAppFont(font: String) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.APP_FONT] = font
        }
    }

    suspend fun setBackgroundPreset(preset: String) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.BACKGROUND_PRESET] = preset
        }
    }

    val stickers: Flow<String?> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.SAVED_STICKERS]
    }

    suspend fun saveStickers(stickersJson: String) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.SAVED_STICKERS] = stickersJson
        }
    }

    val stickersEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.STICKERS_ENABLED] ?: true
    }

    suspend fun setStickersEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.STICKERS_ENABLED] = enabled
        }
    }

    val forceDarkMode: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.FORCE_DARK_MODE] ?: false
    }

    suspend fun setForceDarkMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.FORCE_DARK_MODE] = enabled
        }
    }
}

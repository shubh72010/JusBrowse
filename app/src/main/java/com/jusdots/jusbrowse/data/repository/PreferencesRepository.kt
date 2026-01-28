package com.jusdots.jusbrowse.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "browser_preferences")

class PreferencesRepository(private val context: Context) {
    
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
        val SAVED_SHORTCUTS = stringPreferencesKey("saved_shortcuts")
    }

    val searchEngine: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.SEARCH_ENGINE] ?: "DuckDuckGo"
    }

    val homePage: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.HOME_PAGE] ?: "about:blank"
    }

    val javascriptEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.JAVASCRIPT_ENABLED] ?: true
    }

    val darkMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.DARK_MODE] ?: true
    }

    val savedTabs: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.SAVED_TABS]
    }

    val savedWindowStates: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.SAVED_WINDOW_STATES]
    }

    val activeTabIndex: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.ACTIVE_TAB_INDEX]?.toIntOrNull() ?: 0
    }

    val adBlockEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.AD_BLOCK_ENABLED] ?: true
    }

    val httpsOnly: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.HTTPS_ONLY] ?: false
    }

    val flagSecureEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.FLAG_SECURE_ENABLED] ?: true
    }

    val doNotTrackEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.DO_NOT_TRACK_ENABLED] ?: false
    }

    val cookieBlockerEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.COOKIE_BLOCKER_ENABLED] ?: false
    }

    suspend fun setSearchEngine(searchEngine: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.SEARCH_ENGINE] = searchEngine
        }
    }

    suspend fun setHomePage(homePage: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.HOME_PAGE] = homePage
        }
    }

    suspend fun setJavascriptEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.JAVASCRIPT_ENABLED] = enabled
        }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.DARK_MODE] = enabled
        }
    }

    suspend fun saveSession(tabsJson: String, windowStatesJson: String, activeIndex: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.SAVED_TABS] = tabsJson
            preferences[PreferenceKeys.SAVED_WINDOW_STATES] = windowStatesJson
            preferences[PreferenceKeys.ACTIVE_TAB_INDEX] = activeIndex.toString()
        }
    }

    suspend fun setAdBlockEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.AD_BLOCK_ENABLED] = enabled
        }
    }

    suspend fun setHttpsOnly(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.HTTPS_ONLY] = enabled
        }
    }

    suspend fun setFlagSecureEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.FLAG_SECURE_ENABLED] = enabled
        }
    }

    suspend fun setDoNotTrackEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.DO_NOT_TRACK_ENABLED] = enabled
        }
    }

    val savedShortcuts: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.SAVED_SHORTCUTS]
    }

    suspend fun saveShortcuts(shortcutsJson: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.SAVED_SHORTCUTS] = shortcutsJson
        }
    }

    suspend fun setCookieBlockerEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.COOKIE_BLOCKER_ENABLED] = enabled
        }
    }
}

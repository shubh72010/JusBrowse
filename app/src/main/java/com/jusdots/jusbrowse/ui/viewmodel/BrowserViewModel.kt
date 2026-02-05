package com.jusdots.jusbrowse.ui.viewmodel

import android.app.Application
import android.webkit.WebView
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.jusdots.jusbrowse.ui.components.MediaData
import com.jusdots.jusbrowse.ui.components.MediaItem
import com.jusdots.jusbrowse.data.models.TrackerInfo
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jusdots.jusbrowse.BrowserApplication
import com.jusdots.jusbrowse.data.models.Bookmark
import com.jusdots.jusbrowse.data.models.BrowserTab
import com.jusdots.jusbrowse.data.models.HistoryItem
import com.jusdots.jusbrowse.data.repository.BookmarkRepository
import com.jusdots.jusbrowse.data.repository.HistoryRepository
import com.jusdots.jusbrowse.data.repository.DownloadRepository
import com.jusdots.jusbrowse.data.repository.PreferencesRepository
import com.jusdots.jusbrowse.data.repository.SiteSettingsRepository
import com.jusdots.jusbrowse.data.models.DownloadItem
import com.jusdots.jusbrowse.data.models.Shortcut
import com.jusdots.jusbrowse.data.models.Sticker
import com.jusdots.jusbrowse.ui.screens.Screen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jusdots.jusbrowse.security.ContentBlocker
import java.util.UUID

data class WindowState(
    var x: Float = 0f,
    var y: Float = 0f,
    var scale: Float = 1f,
    var zIndex: Float = 0f
)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val database = BrowserApplication.database
    private val bookmarkRepository = BookmarkRepository(database.bookmarkDao())
    private val historyRepository = HistoryRepository(database.historyDao())
    private val downloadRepository = DownloadRepository(database.downloadDao())
    private val preferencesRepository = PreferencesRepository(application)
    val siteSettingsRepository = SiteSettingsRepository(database.siteSettingsDao())

    // Security
    val contentBlocker = ContentBlocker(application)

    // Tab Management
    val tabs: SnapshotStateList<BrowserTab> = mutableStateListOf()
    private val _activeTabIndex = MutableStateFlow(0)
    val activeTabIndex: StateFlow<Int> = _activeTabIndex.asStateFlow()

    // Current global URL (mainly for single-view mode)
    private val _currentUrl = MutableStateFlow("about:blank")
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    // Window Management
    val tabWindowStates: SnapshotStateMap<String, WindowState> = mutableStateMapOf()

    // WebView Pool to keep instances alive
    private val webViewPool = mutableMapOf<String, WebView>()

    // Bookmarks, History & Downloads
    val bookmarks = bookmarkRepository.getAllBookmarks()
    val history = historyRepository.getAllHistory()
    val recentHistory = historyRepository.getRecentHistory(10)
    val downloads = downloadRepository.allDownloads

    // Desktop Shortcuts
    val pinnedShortcuts: SnapshotStateList<Shortcut> = mutableStateListOf()

    // Stickers
    val stickers: SnapshotStateList<Sticker> = mutableStateListOf()

    // Preferences
    val searchEngine = preferencesRepository.searchEngine
    val homePage = preferencesRepository.homePage
    val javascriptEnabled = preferencesRepository.javascriptEnabled
    val darkMode = preferencesRepository.darkMode
    val adBlockEnabled = preferencesRepository.adBlockEnabled
    val httpsOnly = preferencesRepository.httpsOnly
    val flagSecureEnabled = preferencesRepository.flagSecureEnabled
    val doNotTrackEnabled = preferencesRepository.doNotTrackEnabled
    val cookieBlockerEnabled = preferencesRepository.cookieBlockerEnabled
    val popupBlockerEnabled = preferencesRepository.popupBlockerEnabled
    val showTabIcons = preferencesRepository.showTabIcons
    val themePreset = preferencesRepository.themePreset
    val virusTotalApiKey = preferencesRepository.virusTotalApiKey
    val koodousApiKey = preferencesRepository.koodousApiKey
    val amoledBlackEnabled = preferencesRepository.amoledBlackEnabled
    val startPageWallpaperUri = preferencesRepository.startPageWallpaperUri
    val startPageBlurAmount = preferencesRepository.startPageBlurAmount
    val backgroundPreset = preferencesRepository.backgroundPreset
    
    // Engines
    val defaultEngineEnabled = preferencesRepository.defaultEngineEnabled
    val jusFakeEngineEnabled = preferencesRepository.jusFakeEngineEnabled
    val randomiserEngineEnabled = preferencesRepository.randomiserEngineEnabled
    val multiMediaPlaybackEnabled = preferencesRepository.multiMediaPlaybackEnabled
    val appFont = preferencesRepository.appFont

    // WallTheme Color Extraction
    private val _extractedWallColor = MutableStateFlow<androidx.compose.ui.graphics.Color?>(null)
    val extractedWallColor: StateFlow<androidx.compose.ui.graphics.Color?> = _extractedWallColor.asStateFlow()

    private val _isStickerMode = MutableStateFlow(false)
    val isStickerMode: StateFlow<Boolean> = _isStickerMode.asStateFlow()

    val stickersEnabled = preferencesRepository.stickersEnabled

    // Multi-View Mode
    private val _isMultiViewMode = MutableStateFlow(false)
    val isMultiViewMode: StateFlow<Boolean> = _isMultiViewMode.asStateFlow()

    // Screen Navigation
    private val _currentScreen = MutableStateFlow(Screen.BROWSER)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // Intent Handling
    private var isSessionLoaded = false
    private var pendingIntentUrl: String? = null

    fun handleIntentURL(url: String) {
        if (!isSessionLoaded) {
            pendingIntentUrl = url
        } else {
            // Check if URL is already open in any tab to avoid duplicates? 
            // For now, just open new tab for every external intent
            createNewTab(url)
        }
    }

    // Airlock State (Global Overlays)
    var showAirlock by mutableStateOf(false)
    var airlockUrl by mutableStateOf("")
    var airlockMimeType by mutableStateOf("")
    
    var showGallery by mutableStateOf(false)
    var galleryMediaData by mutableStateOf<MediaData?>(null)
    var isVaulting by mutableStateOf(false)
    var vaultProgress by mutableStateOf(0f)
    

    
    // Tracker Visualization
    val blockedTrackers = mutableStateMapOf<String, SnapshotStateList<TrackerInfo>>()

    fun recordBlockedTracker(tabId: String, domain: String) {
        val list = blockedTrackers.getOrPut(tabId) { mutableStateListOf() }
        // Keep only unique domains per tab for visualization simplicity, or all with timestamps
        if (list.none { it.domain == domain }) {
            list.add(0, TrackerInfo(domain))
        }
    }
    
    // Viewer State
    var viewerMediaList by mutableStateOf<List<MediaItem>>(emptyList())
    var viewerInitialIndex by mutableStateOf(0)
    
    fun openAirlockViewer(url: String, mimeType: String, list: List<MediaItem> = emptyList(), index: Int = 0) {
        airlockUrl = url
        airlockMimeType = mimeType
        viewerMediaList = list
        viewerInitialIndex = index
        showAirlock = true
    }
    
    fun openAirlockGallery(data: MediaData) {
        galleryMediaData = data
        showGallery = true // Show the UI immediately
        
        // Start isolation process in background
        viewModelScope.launch {
            isVaulting = true
            vaultProgress = 0f
            val context = getApplication<Application>()
            val vaultedData = com.jusdots.jusbrowse.utils.AirlockVaultManager.processAndVaultMedia(context, data) { current, total ->
                vaultProgress = current.toFloat() / total.toFloat()
            }
            galleryMediaData = vaultedData
            isVaulting = false
        }
    }
    
    fun closeAirlock() {
        showAirlock = false
        showGallery = false
    }


    init {
        viewModelScope.launch {
            loadSession()
            // Sync timezone with network for airtight spoofing
            com.jusdots.jusbrowse.security.FakeModeManager.syncTimezoneWithNetwork(this)
        }
        
        // Watch for wallpaper changes to extract color
        viewModelScope.launch {
            startPageWallpaperUri.collect { uri ->
                if (uri != null) {
                    extractColorFromUri(uri)
                } else {
                    _extractedWallColor.value = null
                }
            }
        }
    }

    private val gson = Gson()

    private fun saveSession() {
        viewModelScope.launch {
            val tabsJson = gson.toJson(tabs.toList())
            val windowStatesJson = gson.toJson(tabWindowStates.toMap())
            preferencesRepository.saveSession(tabsJson, windowStatesJson, _activeTabIndex.value)
        }
    }

    private suspend fun loadSession() {
        val savedTabsJson = preferencesRepository.savedTabs.first()
        val savedWindowStatesJson = preferencesRepository.savedWindowStates.first()
        val savedActiveIndex = preferencesRepository.activeTabIndex.first()

        if (!savedTabsJson.isNullOrBlank()) {
            try {
                val tabsType = object : TypeToken<List<BrowserTab>>() {}.type
                val loadedTabs: List<BrowserTab> = gson.fromJson(savedTabsJson, tabsType)
                
                val statesType = object : TypeToken<Map<String, WindowState>>() {}.type
                val loadedStates: Map<String, WindowState> = if (!savedWindowStatesJson.isNullOrBlank()) {
                    gson.fromJson(savedWindowStatesJson, statesType)
                } else emptyMap()

                tabs.clear()
                tabWindowStates.clear()
                tabWindowStates.putAll(loadedStates)

                // Sanitize and LOAD STAGGERED to avoid ANR from 10+ webviews at once
                val sanitizedTabs = loadedTabs.map { tab ->
                    val cid = try { tab.containerId } catch (e: Exception) { null }
                    if (cid == null) tab.copy(containerId = "default") else tab
                }
                
                // Add active tab first if any
                val activeIdx = if (savedActiveIndex in sanitizedTabs.indices) savedActiveIndex else 0
                if (sanitizedTabs.isNotEmpty()) {
                    tabs.add(sanitizedTabs[activeIdx])
                    _activeTabIndex.value = 0
                    _currentUrl.value = sanitizedTabs[activeIdx].url
                }
                
                // Add others with delay
                viewModelScope.launch {
                    sanitizedTabs.forEachIndexed { index, tab ->
                        if (index != activeIdx) {
                            kotlinx.coroutines.delay(300) // 300ms gap
                            tabs.add(tab)
                        }
                    }
                }
                
                isSessionLoaded = true
                pendingIntentUrl?.let { url ->
                    createNewTab(url)
                    pendingIntentUrl = null
                }
            } catch (e: Exception) {
                // Layer 12: No debug logging in release - silently handle
                createNewTab()
            }
        }

        if (tabs.isEmpty()) {
            createNewTab()
        }

        val savedShortcutsJson = preferencesRepository.savedShortcuts.first()
        if (!savedShortcutsJson.isNullOrBlank()) {
             try {
                 val shortcutsType = object : TypeToken<List<Shortcut>>() {}.type
                 val loadedShortcuts: List<Shortcut> = gson.fromJson(savedShortcutsJson, shortcutsType)
                 pinnedShortcuts.clear()
                 pinnedShortcuts.addAll(loadedShortcuts)
             } catch (e: Exception) {
                 // Ignore
             }
        }

        val savedStickersJson = preferencesRepository.stickers.first()
        if (!savedStickersJson.isNullOrBlank()) {
             try {
                 val stickerType = object : TypeToken<List<Sticker>>() {}.type
                 val loadedStickers: List<Sticker> = gson.fromJson(savedStickersJson, stickerType)
                 stickers.clear()
                 stickers.addAll(loadedStickers)
             } catch (e: Exception) {
                 // Ignore
             }
        }
    }

    private fun saveShortcuts() {
        viewModelScope.launch {
            val json = gson.toJson(pinnedShortcuts.toList())
            preferencesRepository.saveShortcuts(json)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Cleanup WebViews
        webViewPool.values.forEach { it.destroy() }
        webViewPool.clear()
    }

    // WebView Management
    fun getWebView(tabId: String): WebView? {
        return webViewPool[tabId]
    }

    fun registerWebView(tabId: String, webView: WebView) {
        webViewPool[tabId] = webView
    }

    // Window Management
    fun updateWindowPosition(tabId: String, x: Float, y: Float) {
        tabWindowStates[tabId]?.let {
            it.x = x
            it.y = y
            saveSession()
        }
    }

    fun updateWindowScale(tabId: String, scale: Float) {
        tabWindowStates[tabId]?.let {
            // Clamp scale
            it.scale = scale.coerceIn(0.5f, 3.0f)
            saveSession()
        }
    }

    fun bringToFront(tabId: String) {
        // Find max Z
        val maxZ = tabWindowStates.values.maxOfOrNull { it.zIndex } ?: 0f
        tabWindowStates[tabId]?.zIndex = maxZ + 1f
    }

    fun navigateToScreen(screen: Screen) {
        _currentScreen.value = screen
    }

    fun createNewTab(url: String = "about:blank", isPrivate: Boolean = false, containerId: String = "default", select: Boolean = true) {
        val newTabId = UUID.randomUUID().toString()
        val newTab = BrowserTab(
            id = newTabId,
            url = url,
            isPrivate = isPrivate,
            containerId = containerId
        )
        tabs.add(newTab)
        
        // Initialize window state with a cascade effect
        val offset = (tabs.size * 20).toFloat()
        tabWindowStates[newTabId] = WindowState(
            x = offset,
            y = offset,
            zIndex = (tabs.size).toFloat()
        )

        if (select) {
            _activeTabIndex.value = tabs.lastIndex
            _currentUrl.value = url
        }
        saveSession()
    }

    fun switchTab(index: Int) {
        if (index in tabs.indices) {
            _activeTabIndex.value = index
            _currentUrl.value = tabs[index].url
            
            // Bring window to front if in multi-view
            val tabId = tabs[index].id
            bringToFront(tabId)
        }
    }

    fun closeTab(index: Int) {
        if (tabs.size > 1 && index in tabs.indices) {
            val tabToRemove = tabs[index]
            
            // Cleanup WebView
            val webView = webViewPool[tabToRemove.id]
            if (tabToRemove.isPrivate) {
                webView?.clearCache(true)
                webView?.clearHistory()
                webView?.clearFormData()
                android.webkit.WebStorage.getInstance().deleteAllData()
                android.webkit.CookieManager.getInstance().removeAllCookies(null)
            }
            webView?.destroy()
            webViewPool.remove(tabToRemove.id)
            tabWindowStates.remove(tabToRemove.id)

            tabs.removeAt(index)
            
            // Adjust active tab index
            when {
                index < _activeTabIndex.value -> _activeTabIndex.value--
                index == _activeTabIndex.value && index == tabs.size -> {
                    _activeTabIndex.value = tabs.lastIndex
                }
            }
            
            // Update current URL
            if (_activeTabIndex.value in tabs.indices) {
                _currentUrl.value = tabs[_activeTabIndex.value].url
            }
            saveSession()
        } else if (tabs.size == 1) {
            // If closing last tab, create a new one
            val oldTabId = tabs[0].id
            webViewPool[oldTabId]?.destroy()
            webViewPool.remove(oldTabId)
            tabWindowStates.remove(oldTabId)

            val newId = UUID.randomUUID().toString()
            tabs[0] = BrowserTab(
                id = newId,
                url = "about:blank"
            )
            tabWindowStates[newId] = WindowState()
            _currentUrl.value = "about:blank"
            saveSession()
        }
    }

    fun closeAllTabs() {
        // Cleanup all WebViews
        webViewPool.values.forEach { it.destroy() }
        webViewPool.clear()
        
        // Clear states
        tabWindowStates.clear()
        tabs.clear()
        
        // Re-initialize with one fresh tab
        createNewTab()
        saveSession()
    }

    fun updateTab(index: Int, updatedTab: BrowserTab) {
        if (index in tabs.indices) {
            tabs[index] = updatedTab
            if (index == _activeTabIndex.value) {
                _currentUrl.value = updatedTab.url
            }
            saveSession()
        }
    }

    // Navigation
    // DEPRECATED: Uses active tab. Use navigateToUrl(tabId, url) instead for multi-window correctness.
    fun navigateToUrl(url: String) {
        navigateToUrlForIndex(_activeTabIndex.value, url)
    }

    fun navigateToUrlForIndex(index: Int, url: String) {
        val normalizedUrl = normalizeUrl(url)
        
        if (index in tabs.indices) {
            val tab = tabs[index]
            // Clear trackers for the new navigation
            blockedTrackers.remove(tab.id)
            
            val updatedTab = tab.copy(url = normalizedUrl)
            updateTab(index, updatedTab)
            
            // Add to history only if NOT private
            if (!tab.isPrivate) {
                viewModelScope.launch {
                    historyRepository.addToHistory(
                        title = normalizedUrl,
                        url = normalizedUrl
                    )
                }
            }
        }
    }
    
    fun navigateToUrlByTabId(tabId: String, url: String) {
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index != -1) {
            navigateToUrlForIndex(index, url)
        }
    }

    fun updateTabTitle(index: Int, title: String) {
        if (index in tabs.indices) {
            val updatedTab = tabs[index].copy(title = title)
            updateTab(index, updatedTab)
        }
    }

    fun updateTabLoadingState(index: Int, isLoading: Boolean, progress: Float = 0f) {
        if (index in tabs.indices) {
            val updatedTab = tabs[index].copy(
                isLoading = isLoading,
                progress = progress
            )
            updateTab(index, updatedTab)
        }
    }

    fun updateTabNavigationState(index: Int, canGoBack: Boolean, canGoForward: Boolean) {
        if (index in tabs.indices) {
            val updatedTab = tabs[index].copy(
                canGoBack = canGoBack,
                canGoForward = canGoForward
            )
            updateTab(index, updatedTab)
        }
    }

    private fun normalizeUrl(url: String): String {
        return if (!url.startsWith("http://") && !url.startsWith("https://") && url != "about:blank") {
            "https://$url"
        } else {
            url
        }
    }

    fun isUrlQuery(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return true

        // 1. Explicit protocol
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || 
            trimmed.startsWith("file://") || trimmed.startsWith("about:")) {
            return false
        }

        // 2. Contains spaces -> definitely search
        if (trimmed.contains(" ")) return true

        // 3. Common TLDs check or localhost
        val commonTlds = listOf(".com", ".net", ".org", ".io", ".gov", ".edu", ".dev", ".me", ".info", ".biz", ".top")
        if (commonTlds.any { trimmed.lowercase().endsWith(it) } || 
            trimmed.lowercase() == "localhost" || 
            trimmed.contains("localhost:")) {
            return false
        }

        // 4. IP Address check (simple)
        val ipRegex = "^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(:\\d+)?.*$".toRegex()
        if (ipRegex.matches(trimmed)) return false

        // Default: Search Query
        return true
    }

    fun getSearchUrl(query: String, engine: String = "DuckDuckGo"): String {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        return when (engine.lowercase()) {
            "google" -> "https://www.google.com/search?q=$encodedQuery"
            "bing" -> "https://www.bing.com/search?q=$encodedQuery"
            else -> "https://duckduckgo.com/?q=$encodedQuery"
        }
    }

    // Bookmarks
    fun addBookmark(title: String, url: String) {
        viewModelScope.launch {
            bookmarkRepository.addBookmark(title, url)
        }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            bookmarkRepository.deleteBookmark(bookmark)
        }
    }

    suspend fun isBookmarked(url: String): Boolean {
        return bookmarkRepository.isBookmarked(url)
    }

    // History
    fun clearHistory() {
        viewModelScope.launch {
            historyRepository.clearAllHistory()
        }
    }

    fun deleteHistoryItem(historyItem: HistoryItem) {
        viewModelScope.launch {
            historyRepository.deleteHistory(historyItem)
        }
    }

    // Preferences
    fun setSearchEngine(engine: String) {
        viewModelScope.launch {
            preferencesRepository.setSearchEngine(engine)
        }
    }

    fun setHomePage(homePage: String) {
        viewModelScope.launch {
            preferencesRepository.setHomePage(homePage)
        }
    }

    fun setStartPageWallpaperUri(uri: String?) {
        viewModelScope.launch {
            preferencesRepository.setStartPageWallpaperUri(uri)
            if (uri != null) {
                preferencesRepository.setBackgroundPreset("NONE")
            }
        }
    }

    private fun extractColorFromUri(uriString: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val uri = android.net.Uri.parse(uriString)
                val inputStream = context.contentResolver.openInputStream(uri)
                val options = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = false
                    inSampleSize = 8 // Downsample for speed
                }
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream, null, options)
                inputStream?.close()

                if (bitmap != null) {
                    // Extract color from center area or average
                    // Using center 10x10 area average
                    val centerX = bitmap.width / 2
                    val centerY = bitmap.height / 2
                    
                    var r = 0L
                    var g = 0L
                    var b = 0L
                    var count = 0
                    
                    for (x in (centerX - 5).coerceAtLeast(0) until (centerX + 5).coerceAtMost(bitmap.width)) {
                        for (y in (centerY - 5).coerceAtLeast(0) until (centerY + 5).coerceAtMost(bitmap.height)) {
                            val pixel = bitmap.getPixel(x, y)
                            r += android.graphics.Color.red(pixel)
                            g += android.graphics.Color.green(pixel)
                            b += android.graphics.Color.blue(pixel)
                            count++
                        }
                    }
                    
                    if (count > 0) {
                        val finalColor = android.graphics.Color.rgb((r/count).toInt(), (g/count).toInt(), (b/count).toInt())
                        _extractedWallColor.value = androidx.compose.ui.graphics.Color(finalColor)
                    }
                    bitmap.recycle()
                }
            } catch (e: Exception) {
                // Ignore errors
            }
        }
    }

    fun setStartPageBlurAmount(amount: Float) {
        viewModelScope.launch {
            preferencesRepository.setStartPageBlurAmount(amount)
        }
    }

    fun setBackgroundPreset(preset: String) {
        viewModelScope.launch {
            preferencesRepository.setBackgroundPreset(preset)
            if (preset != "NONE") {
                preferencesRepository.setStartPageWallpaperUri(null)
            }
        }
    }

    fun setJavascriptEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setJavascriptEnabled(enabled)
        }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDarkMode(enabled)
        }
    }

    fun setAdBlockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAdBlockEnabled(enabled)
        }
    }

    fun setHttpsOnly(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setHttpsOnly(enabled)
        }
    }

    fun setFlagSecureEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setFlagSecureEnabled(enabled)
        }
    }

    fun setDoNotTrackEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDoNotTrackEnabled(enabled)
        }
    }

    fun setCookieBlockerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setCookieBlockerEnabled(enabled)
        }
    }

    fun setMultiMediaPlaybackEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setMultiMediaPlaybackEnabled(enabled)
        }
    }

    fun setAppFont(font: String) {
        viewModelScope.launch {
            preferencesRepository.setAppFont(font)
        }
    }

    fun setPopupBlockerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setPopupBlockerEnabled(enabled)
        }
    }

    fun setShowTabIcons(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setShowTabIcons(enabled)
        }
    }

    fun setThemePreset(preset: String) {
        viewModelScope.launch {
            preferencesRepository.setThemePreset(preset)
        }
    }

    fun setVirusTotalApiKey(key: String) {
        viewModelScope.launch {
            preferencesRepository.setVirusTotalApiKey(key)
        }
    }

    fun setKoodousApiKey(key: String) {
        viewModelScope.launch {
            preferencesRepository.setKoodousApiKey(key)
        }
    }

    // ============ NEW UI CUSTOMIZATION PREFERENCES ============
    val follianMode = preferencesRepository.follianMode
    val toolbarPosition = preferencesRepository.toolbarPosition
    val compactMode = preferencesRepository.compactMode
    val addressBarStyle = preferencesRepository.addressBarStyle

    fun setFollianMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setFollianMode(enabled)
        }
    }

    fun setToolbarPosition(position: String) {
        viewModelScope.launch {
            preferencesRepository.setToolbarPosition(position)
        }
    }

    fun setCompactMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setCompactMode(enabled)
        }
    }

    fun setAddressBarStyle(style: String) {
        viewModelScope.launch {
            preferencesRepository.setAddressBarStyle(style)
        }
    }

    fun setAmoledBlackEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAmoledBlackEnabled(enabled)
        }
    }

    fun setStickersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setStickersEnabled(enabled)
        }
    }

    // Engines
    fun setDefaultEngineEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                preferencesRepository.setDefaultEngineEnabled(true)
                preferencesRepository.setRandomiserEngineEnabled(false)
                preferencesRepository.setJusFakeEngineEnabled(false)
            } else {
                preferencesRepository.setDefaultEngineEnabled(false)
            }
        }
    }

    fun setJusFakeEngineEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                // If enabling, we don't do it here anymore, we use activateJusFakeEngine from UI
                // to handle the restart/context correctly. 
                // However, for consistency, we update preferences.
                preferencesRepository.setJusFakeEngineEnabled(true)
                preferencesRepository.setRandomiserEngineEnabled(false)
                preferencesRepository.setDefaultEngineEnabled(false)
            } else {
                preferencesRepository.setJusFakeEngineEnabled(false)
            }
        }
    }

    fun activateJusFakeEngine(context: android.content.Context, persona: com.jusdots.jusbrowse.security.FakePersona) {
        viewModelScope.launch {
            // 1. Save preferences FIRST and Wait
            preferencesRepository.setJusFakeEngineEnabled(true)
            preferencesRepository.setRandomiserEngineEnabled(false)
            preferencesRepository.setDefaultEngineEnabled(false)
            
            // 2. Important: Sync other states if needed
            
            // 3. Trigger the restart through FakeModeManager
            com.jusdots.jusbrowse.security.FakeModeManager.enableFakeMode(context, persona)
        }
    }

    fun setRandomiserEngineEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                preferencesRepository.setRandomiserEngineEnabled(true)
                preferencesRepository.setDefaultEngineEnabled(false)
                preferencesRepository.setJusFakeEngineEnabled(false)
            } else {
                preferencesRepository.setRandomiserEngineEnabled(false)
            }
        }
    }

    // Site Settings
    fun updateSiteSettings(settings: com.jusdots.jusbrowse.data.models.SiteSettings) {
        viewModelScope.launch {
            siteSettingsRepository.updateSettings(settings)
        }
    }

    fun getSiteSettings(url: String): kotlinx.coroutines.flow.Flow<com.jusdots.jusbrowse.data.models.SiteSettings?> {
        val origin = try {
            val uri = android.net.Uri.parse(url)
            "${uri.scheme}://${uri.host}"
        } catch (e: Exception) {
            url
        }
        return siteSettingsRepository.getSettingsForOrigin(origin)
    }

    // Multi-View Mode
    fun toggleMultiViewMode() {
        // Only allow multi-view if we have 2+ tabs, OR if user just wants to see windows
        if (tabs.size >= 1) { 
            _isMultiViewMode.value = !_isMultiViewMode.value
        }
    }

    // Sticker Mode
    fun toggleStickerMode() {
        _isStickerMode.value = !_isStickerMode.value
    }

    fun getVisibleTabs(): List<BrowserTab> {
        // Return first 4 tabs for grid view
        return tabs.take(4)
    }

    // Downloads
    fun clearDownloads() {
        viewModelScope.launch {
            downloadRepository.clearAll()
        }
    }

    fun deleteDownload(item: DownloadItem) {
        viewModelScope.launch {
            downloadRepository.deleteDownload(item)
        }
    }

    fun addDownload(fileName: String, url: String, filePath: String, fileSize: Long, systemDownloadId: Long = -1) {
        viewModelScope.launch {
            downloadRepository.addDownload(
                DownloadItem(
                    fileName = fileName,
                    url = url,
                    filePath = filePath,
                    fileSize = fileSize,
                    securityStatus = "Pending Scan",
                    systemDownloadId = systemDownloadId
                )
            )
        }
    }

    fun startDownload(context: android.content.Context, url: String, fileName: String) {
        if (url.startsWith("/")) {
            // Local file (Vaulted)
            viewModelScope.launch {
                try {
                    val source = java.io.File(url)
                    val destDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    if (!destDir.exists()) destDir.mkdirs()
                    val destFile = java.io.File(destDir, fileName)
                    source.copyTo(destFile, overwrite = true)
                    
                    addDownload(fileName, "internal://vaulted", destFile.absolutePath, source.length())
                    android.widget.Toast.makeText(context, "Saved to Downloads", android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Export failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            return
        }
        try {
            val uri = android.net.Uri.parse(url)
            val request = android.app.DownloadManager.Request(uri)
                .setTitle(fileName)
                .setDescription("Downloading via JusBrowse...")
                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)

            val downloadManager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            val id = downloadManager.enqueue(request)

            // Add to database
            val fullPath = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).absolutePath + "/" + fileName
            addDownload(fileName, url, fullPath, 0L, id)
            
            android.widget.Toast.makeText(context, "Download started", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Download failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun updateDownloadSecurity(fileName: String, status: String, result: String) {
        viewModelScope.launch {
            // This is a simple implementation: find by filename and update
            // Ideally we'd have a downloadId from DownloadManager
            val allDownloads = downloadRepository.allDownloads.first()
            val item = allDownloads.find { it.fileName == fileName }
            if (item != null) {
                downloadRepository.addDownload(
                    item.copy(securityStatus = status, scanResult = result)
                )
            }
        }
    }

    // Shortcuts Management
    fun pinShortcut(title: String, url: String) {
        val shortcut = Shortcut(title = title, url = url)
        pinnedShortcuts.add(shortcut)
        saveShortcuts()
    }

    fun unpinShortcut(shortcut: Shortcut) {
        pinnedShortcuts.remove(shortcut)
        saveShortcuts()
    }

    fun pinCurrentTabToDesktop() {
        val activeTab = tabs.getOrNull(activeTabIndex.value)
        if (activeTab != null && activeTab.url != "about:blank") {
            pinShortcut(activeTab.title, activeTab.url)
        }
    }

    // Sticker Management
    fun addSticker(imageUri: String, link: String? = null) {
        val newSticker = Sticker(
            id = UUID.randomUUID().toString(),
            imageUri = imageUri,
            link = link,
            x = 0.5f,
            y = 0.5f,
            rotation = (-15..15).random().toFloat()
        )
        stickers.add(newSticker)
        saveStickers()
    }

    fun updateStickerPosition(stickerId: String, x: Float, y: Float, persist: Boolean = true) {
        val index = stickers.indexOfFirst { it.id == stickerId }
        if (index != -1) {
            stickers[index] = stickers[index].copy(x = x, y = y)
            if (persist) saveStickers()
        }
    }

    fun updateStickerLink(stickerId: String, link: String?) {
        val index = stickers.indexOfFirst { it.id == stickerId }
        if (index != -1) {
            stickers[index] = stickers[index].copy(link = link)
            saveStickers()
        }
    }

    fun removeSticker(stickerId: String) {
        stickers.removeIf { it.id == stickerId }
        saveStickers()
    }

    fun saveStickers() {
        viewModelScope.launch {
            val json = gson.toJson(stickers.toList())
            preferencesRepository.saveStickers(json)
        }
    }
}

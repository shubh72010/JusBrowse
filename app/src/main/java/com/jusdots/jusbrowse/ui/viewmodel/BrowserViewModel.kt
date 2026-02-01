package com.jusdots.jusbrowse.ui.viewmodel

import android.app.Application
import android.webkit.WebView
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import com.jusdots.jusbrowse.ui.screens.Screen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val showTabIcons = preferencesRepository.showTabIcons
    val themePreset = preferencesRepository.themePreset
    val virusTotalApiKey = preferencesRepository.virusTotalApiKey
    val koodousApiKey = preferencesRepository.koodousApiKey

    // Multi-View Mode
    private val _isMultiViewMode = MutableStateFlow(false)
    val isMultiViewMode: StateFlow<Boolean> = _isMultiViewMode.asStateFlow()

    // Screen Navigation
    private val _currentScreen = MutableStateFlow(Screen.BROWSER)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    init {
        viewModelScope.launch {
            loadSession()
            // Sync timezone with network for airtight spoofing
            com.jusdots.jusbrowse.security.FakeModeManager.syncTimezoneWithNetwork(this)
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
                // Sanitize loaded tabs to ensure no null containerId from old sessions
                val sanitizedTabs = loadedTabs.map { tab ->
                    // Use a safe check even if Kotlin thinks it's non-null
                    val cid = try { tab.containerId } catch (e: Exception) { null }
                    if (cid == null) tab.copy(containerId = "default") else tab
                }
                tabs.addAll(sanitizedTabs)
                
                tabWindowStates.clear()
                tabWindowStates.putAll(loadedStates)

                _activeTabIndex.value = if (savedActiveIndex in tabs.indices) savedActiveIndex else 0
                if (tabs.isNotEmpty()) {
                    _currentUrl.value = tabs[_activeTabIndex.value].url
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

    fun addDownload(fileName: String, url: String, filePath: String, fileSize: Long) {
        viewModelScope.launch {
            downloadRepository.addDownload(
                DownloadItem(
                    fileName = fileName,
                    url = url,
                    filePath = filePath,
                    fileSize = fileSize,
                    securityStatus = "Pending Scan"
                )
            )
        }
    }

    fun startDownload(context: android.content.Context, url: String, fileName: String) {
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
            downloadManager.enqueue(request)

            // Add to database
            val fullPath = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).absolutePath + "/" + fileName
            addDownload(fileName, url, fullPath, 0L)
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
        if (_activeTabIndex.value in tabs.indices) {
            val currentTab = tabs[_activeTabIndex.value]
            pinShortcut(currentTab.title.ifEmpty { currentTab.url }, currentTab.url)
        }
    }
}

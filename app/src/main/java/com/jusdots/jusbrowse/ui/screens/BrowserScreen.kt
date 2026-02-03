package com.jusdots.jusbrowse.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jusdots.jusbrowse.ui.components.AddressBarWithWebView
import com.jusdots.jusbrowse.ui.components.BottomTabBar
import com.jusdots.jusbrowse.ui.components.BrowserToolBar
import com.jusdots.jusbrowse.ui.components.FreeformWorkspace
import com.jusdots.jusbrowse.ui.viewmodel.BrowserViewModel
import com.jusdots.jusbrowse.ui.components.AirlockGallery
import com.jusdots.jusbrowse.ui.components.AirlockViewer
import com.jusdots.jusbrowse.ui.components.MediaData
import com.jusdots.jusbrowse.utils.MediaExtractor
import com.google.gson.Gson
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    modifier: Modifier = Modifier
) {
    // Collect active tab state - though we use Independent Windows mostly now
    val activeTabIndex by viewModel.activeTabIndex.collectAsStateWithLifecycle()
    val tabs = viewModel.tabs
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val isMultiView by viewModel.isMultiViewMode.collectAsStateWithLifecycle()
    val showTabIcons by viewModel.showTabIcons.collectAsStateWithLifecycle(initialValue = false)
    val bottomAddressBarEnabled by viewModel.bottomAddressBarEnabled.collectAsStateWithLifecycle(initialValue = false)

    val context = LocalContext.current
    
    // Helper to trigger extraction
    fun openAirlockGallery() {
        val currentTab = if (activeTabIndex in tabs.indices) tabs[activeTabIndex] else null
        if (currentTab != null) {
            viewModel.getWebView(currentTab.id)?.evaluateJavascript(MediaExtractor.EXTRACT_MEDIA_SCRIPT) { result ->
                if (result != null && result != "null") {
                     try {
                         val json = if (result.startsWith("\"") && result.endsWith("\"")) {
                             result.substring(1, result.length - 1)
                                 .replace("\\\"", "\"")
                                 .replace("\\\\", "\\")
                         } else result
                         
                         val data = Gson().fromJson(json, MediaData::class.java)
                         viewModel.openAirlockGallery(data)
                     } catch (e: Exception) {
                         e.printStackTrace()
                     }
                }
            }
        }
    }

    // Handle Back Press at high level
    androidx.activity.compose.BackHandler(enabled = true) {
        when (currentScreen) {
            Screen.SETTINGS, Screen.HISTORY, Screen.BOOKMARKS, Screen.DOWNLOADS -> {
                viewModel.navigateToScreen(Screen.BROWSER)
            }
            Screen.BROWSER -> {
                // If in multi-view, maybe exit multi-view?
                if (isMultiView) {
                    // For now, let it exit app or user logic preference
                    // Could toggle toggleMultiViewMode()
                } else {
                     // In single view, try to go back in active Webview
                    val currentTab = if (activeTabIndex in tabs.indices) tabs[activeTabIndex] else null
                    if (currentTab != null) {
                        val webView = viewModel.getWebView(currentTab.id)
                        if (webView?.canGoBack() == true) {
                            webView.goBack()
                        } else {
                            // Minimize/Close app if cant go back
                            // We don't have activity context easily here to separate exit
                        }
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            // Only show Bottom/Top bar when NOT in Multi-View (Windowed) mode?
            // User requested "Individual controls", so global toolbar is redundant in window mode.
            if (!bottomAddressBarEnabled) {
                if (!isMultiView) {
                    BrowserToolBar(
                        viewModel = viewModel,
                        currentTab = if (activeTabIndex in tabs.indices) tabs[activeTabIndex] else null,
                        onOpenAirlockGallery = { openAirlockGallery() }
                    )
                } else {
                    // In Multi-View, keep a minimal toolbar
                    BrowserToolBar(
                        viewModel = viewModel,
                        currentTab = null,
                        onOpenAirlockGallery = { openAirlockGallery() }
                    )
                }
            }
        },
        bottomBar = {
            Column {
                if (!isMultiView) {
                    if (bottomAddressBarEnabled) {
                        BrowserToolBar(
                            viewModel = viewModel,
                            currentTab = if (activeTabIndex in tabs.indices) tabs[activeTabIndex] else null,
                            onOpenAirlockGallery = { openAirlockGallery() }
                        )
                    }
                    
                    BottomTabBar(
                        tabs = tabs,
                        activeTabIndex = activeTabIndex,
                        onTabSelected = { index -> viewModel.switchTab(index) },
                        onTabClosed = { index -> viewModel.closeTab(index) },
                        onNewTab = { containerId -> viewModel.createNewTab(containerId = containerId) },
                        showIcons = showTabIcons
                    )
                } else if (bottomAddressBarEnabled) {
                    // Minimal toolbar at bottom if in multi-view
                    BrowserToolBar(
                        viewModel = viewModel,
                        currentTab = null,
                        onOpenAirlockGallery = { openAirlockGallery() }
                    )
                }
            }
        }
    ) { paddingValues ->
        
        when (currentScreen) {
            Screen.BROWSER -> {
                if (isMultiView) {
                    // Windowed Workspace Mode
                    FreeformWorkspace(
                        viewModel = viewModel,
                        tabs = tabs,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                } else {
                    // Single Tab Mode (Classic)
                    if (activeTabIndex in tabs.indices) {
                        AddressBarWithWebView(
                            viewModel = viewModel,
                            tabIndex = activeTabIndex,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                        )
                    }
                }
            }
            Screen.BOOKMARKS -> {
                BookmarksScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.navigateToScreen(Screen.BROWSER) }
                )
            }
            Screen.HISTORY -> {
                HistoryScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.navigateToScreen(Screen.BROWSER) }
                )
            }
            Screen.SETTINGS -> {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.navigateToScreen(Screen.BROWSER) }
                )
            }
            Screen.DOWNLOADS -> {
                DownloadsScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.navigateToScreen(Screen.BROWSER) }
                )
            }
        }
    }

        
         // Airlock Gallery Overlay (Global)
        if (viewModel.showGallery && viewModel.galleryMediaData != null) {
            AirlockGallery(
                mediaData = viewModel.galleryMediaData!!,
                isVaulting = viewModel.isVaulting,
                vaultProgress = viewModel.vaultProgress,
                onMediaClick = { url, mimeType, list, index ->
                    viewModel.openAirlockViewer(url, mimeType, list, index)
                    viewModel.showGallery = false
                },
                onClose = { viewModel.closeAirlock() },
                modifier = Modifier.align(Alignment.Center) 
            )
        }
        
        // Airlock Media Viewer Overlay (Global)
        if (viewModel.showAirlock) {
            AirlockViewer(
                initialUrl = viewModel.airlockUrl,
                initialMimeType = viewModel.airlockMimeType,
                mediaList = viewModel.viewerMediaList,
                initialIndex = viewModel.viewerInitialIndex,
                onDismiss = { 
                    viewModel.showAirlock = false
                    viewModel.showGallery = true 
                },
                onDownload = { url ->
                    viewModel.startDownload(context, url, android.webkit.URLUtil.guessFileName(url, null, null))
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

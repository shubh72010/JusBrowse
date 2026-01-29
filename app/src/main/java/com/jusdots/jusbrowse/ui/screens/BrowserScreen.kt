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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            // Only show Bottom/Top bar when NOT in Multi-View (Windowed) mode?
            // User requested "Individual controls", so global toolbar is redundant in window mode.
            if (!isMultiView) {
                BrowserToolBar(
                    viewModel = viewModel,
                    currentTab = if (activeTabIndex in tabs.indices) tabs[activeTabIndex] else null
                )
            } else {
                // In Multi-View, we might still want a way to access Settings/New Tab
                // Or we rely on the floating windows having everything?
                // Let's keep a minimal toolbar or floating action button.
                // For now, reuse toolbar but maybe hide address/nav buttons?
                BrowserToolBar(
                    viewModel = viewModel,
                    currentTab = null // No specific tab selected globally
                )
            }
        },
        bottomBar = {
            if (!isMultiView) {
                BottomTabBar(
                    tabs = tabs,
                    activeTabIndex = activeTabIndex,
                    onTabSelected = { index -> viewModel.switchTab(index) },
                    onTabClosed = { index -> viewModel.closeTab(index) },
                    onNewTab = { viewModel.createNewTab() },
                    showIcons = showTabIcons
                )
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
}

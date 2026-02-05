package com.jusdots.jusbrowse.ui.screens

import androidx.compose.foundation.background
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import com.jusdots.jusbrowse.ui.components.BackgroundRenderer
import com.jusdots.jusbrowse.ui.components.StickerPeel
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.VideoView
import android.net.Uri
import android.media.MediaPlayer
import androidx.compose.ui.input.pointer.pointerInput

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
        val wallpaperUri by viewModel.startPageWallpaperUri.collectAsStateWithLifecycle(initialValue = null)
        val blurAmount by viewModel.startPageBlurAmount.collectAsStateWithLifecycle(initialValue = 0f)
        val backgroundPresetName by viewModel.backgroundPreset.collectAsStateWithLifecycle(initialValue = "NONE")
        
        val backgroundPreset = try {
            com.jusdots.jusbrowse.ui.theme.BackgroundPreset.valueOf(backgroundPresetName)
        } catch (e: Exception) {
            com.jusdots.jusbrowse.ui.theme.BackgroundPreset.NONE
        }

        val stickers = viewModel.stickers

        // Global Background Layer
        Box(modifier = Modifier.fillMaxSize()) {
            // Animated Background Preset (if no custom wallpaper)
            if (wallpaperUri == null && backgroundPreset != com.jusdots.jusbrowse.ui.theme.BackgroundPreset.NONE) {
                BackgroundRenderer(
                    preset = backgroundPreset,
                    modifier = Modifier.fillMaxSize()
                )
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
            }
            
            // Custom Wallpaper (Image or Video)
            if (wallpaperUri != null) {
                val uri = Uri.parse(wallpaperUri)
                val isVideo = context.contentResolver.getType(uri)?.startsWith("video/") == true || 
                              wallpaperUri!!.lowercase().endsWith(".mp4") || 
                              wallpaperUri!!.lowercase().endsWith(".mkv") ||
                              wallpaperUri!!.lowercase().endsWith(".webm")

                if (isVideo) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoURI(uri)
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    mp.setVolume(0f, 0f)
                                    // Scale to fit center crop style
                                    val videoWidth = mp.videoWidth.toFloat()
                                    val videoHeight = mp.videoHeight.toFloat()
                                    val viewWidth = width.toFloat()
                                    val viewHeight = height.toFloat()
                                    val scale = Math.max(viewWidth / videoWidth, viewHeight / videoHeight)
                                    // This scaling is tricky with VideoView, usually requires an overlay or custom layout
                                    // For now, simple VideoView is enough
                                    start()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize().blur(blurAmount.dp),
                        update = { view ->
                             // Ensure it's still playing/correct URI if it changes
                             // view.setVideoURI(uri) // Careful with restarts
                        }
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(wallpaperUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().blur(blurAmount.dp)
                    )
                }
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
            }
        }

        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                // In Multi-View, keep a minimal toolbar at the top
                if (isMultiView) {
                    BrowserToolBar(
                        viewModel = viewModel,
                        currentTab = null,
                        onOpenAirlockGallery = { openAirlockGallery() }
                    )
                }
            },
            bottomBar = {
                Column {
                    if (!isMultiView) {
                        // Bottom Address Bar is now handled inside AddressBarWithWebView for Single View
                        BottomTabBar(
                            tabs = tabs,
                            activeTabIndex = activeTabIndex,
                            onTabSelected = { index -> viewModel.switchTab(index) },
                            onTabClosed = { index -> viewModel.closeTab(index) },
                            onNewTab = { containerId -> viewModel.createNewTab(containerId = containerId) },
                            showIcons = showTabIcons
                        )
                    }
                }
            }
        ) { paddingValues ->
        
        when (currentScreen) {
            Screen.BROWSER -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (isMultiView) {
                        FreeformWorkspace(
                            viewModel = viewModel,
                            tabs = tabs,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                        )
                    } else {
                        if (activeTabIndex in tabs.indices) {
                            AddressBarWithWebView(
                                viewModel = viewModel,
                                tabIndex = activeTabIndex,
                                onOpenAirlockGallery = { openAirlockGallery() },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(paddingValues)
                            )
                        }
                    }

                    // Sticker Layer - High-res and interactive
                    val stickersEnabled by viewModel.stickersEnabled.collectAsStateWithLifecycle(initialValue = true)
                    val isStickerMode by viewModel.isStickerMode.collectAsStateWithLifecycle(initialValue = false)
                    val activeTab = tabs.getOrNull(activeTabIndex)
                    val isStartPage = activeTab?.url == "about:blank" || activeTab?.url?.isEmpty() == true
                    
                    // Strict Gate: Only show on Browser Screen, NOT in Multi-view, and only on Start Page
                    if (stickersEnabled && currentScreen == Screen.BROWSER && !isMultiView && isStartPage) {
                        Box(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                val screenWidth = maxWidth
                                val screenHeight = maxHeight
                                val density = androidx.compose.ui.platform.LocalDensity.current
                                val halfSize = with(density) { 256.toDp() }
                                
                                stickers.forEach { sticker: com.jusdots.jusbrowse.data.models.Sticker ->
                                    // Clamp position to ensure visibility (no off-screen ghosts)
                                    val safeX = sticker.x.takeIf { it in 0.05f..0.95f } ?: 0.5f
                                    val safeY = sticker.y.takeIf { it in 0.05f..0.95f } ?: 0.5f

                                    StickerPeel(
                                        sticker = sticker,
                                        onPositionChange = { delta ->
                                            val newX = (safeX * screenWidth.value + delta.x / density.density) / screenWidth.value
                                            val newY = (safeY * screenHeight.value + delta.y / density.density) / screenHeight.value
                                            viewModel.updateStickerPosition(sticker.id, newX.coerceIn(0f, 1f), newY.coerceIn(0f, 1f), persist = false)
                                        },
                                        onDragEnd = { viewModel.saveStickers() },
                                        onClick = {
                                            sticker.link?.let { link ->
                                                val activeTab = tabs.getOrNull(activeTabIndex)
                                                activeTab?.let { tab ->
                                                    viewModel.navigateToUrlByTabId(tab.id, link)
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .offset(
                                                x = (screenWidth * safeX) - halfSize,
                                                y = (screenHeight * safeY) - halfSize
                                            )
                                    )
                                }
                            }
                        }
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
}

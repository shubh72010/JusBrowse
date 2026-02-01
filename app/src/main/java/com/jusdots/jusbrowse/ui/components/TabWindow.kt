package com.jusdots.jusbrowse.ui.components

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.view.ViewGroup
import android.webkit.DownloadListener
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.CookieManager
import android.view.DragEvent
import android.content.ClipData
import android.content.ClipDescription
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.layout.onSizeChanged
import java.io.ByteArrayInputStream
import com.jusdots.jusbrowse.data.models.BrowserTab
import com.jusdots.jusbrowse.ui.viewmodel.BrowserViewModel
import com.jusdots.jusbrowse.security.DownloadValidator
import com.jusdots.jusbrowse.security.FakeModeManager
import com.jusdots.jusbrowse.security.FingerprintingProtection
import com.jusdots.jusbrowse.security.PrivateBrowsingManager
import com.jusdots.jusbrowse.security.SecureWebChromeClient
import com.jusdots.jusbrowse.ui.components.BrowserContextMenu
import com.jusdots.jusbrowse.ui.components.ContextMenuData
import com.jusdots.jusbrowse.ui.components.ContextMenuType
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking

@Composable
fun TabWindow(
    viewModel: BrowserViewModel,
    tab: BrowserTab,
    tabIndex: Int,
    onClose: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val windowState = viewModel.tabWindowStates[tab.id] ?: return
    
    // Independent Search Engine State
    val searchEngine by viewModel.searchEngine.collectAsStateWithLifecycle(initialValue = "DuckDuckGo")
    val adBlockEnabled by viewModel.adBlockEnabled.collectAsStateWithLifecycle(initialValue = true)
    val httpsOnly by viewModel.httpsOnly.collectAsStateWithLifecycle(initialValue = false)
    val doNotTrackEnabled by viewModel.doNotTrackEnabled.collectAsStateWithLifecycle(initialValue = false)
    val cookieBlockerEnabled by viewModel.cookieBlockerEnabled.collectAsStateWithLifecycle(initialValue = false)
    val follianMode by viewModel.follianMode.collectAsStateWithLifecycle(initialValue = false)
    
    // ...

                 },
                         update = { webView ->
                             // DYNAMIC FOLLIAN MODE
                             if (follianMode) {
                                 com.jusdots.jusbrowse.security.FollianBlocker.applyToWebView(webView)
                             } else {
                                 // Restore normal behavior (allow site settings to dictate)
                                 // If we were in Follian Mode (JS disabled), we need to re-enable capabilities
                                 // But we rely on siteSettings logic below to set precise state
                                 if (!webView.settings.javaScriptEnabled) {
                                      // Tentatively restore, site settings will override below
                                      com.jusdots.jusbrowse.security.FollianBlocker.removeFromWebView(webView)
                                 }
                                 
                                 // Apply site settings
                                 siteSettings?.let { settings ->
                                     if (webView.settings.javaScriptEnabled != settings.javascriptEnabled) {
                                         webView.settings.javaScriptEnabled = settings.javascriptEnabled
                                     }
                                     if (webView.settings.domStorageEnabled != settings.domStorageEnabled) {
                                         webView.settings.domStorageEnabled = settings.domStorageEnabled
                                     }
                                 }
                             }
                                 val targetScale = (scale * 100).toInt()
                                 if (webView.settings.textZoom != targetScale) {
                                     webView.settings.textZoom = targetScale
                                 }

                                 val normalizedTabUrl = tab.url.removeSuffix("/")
                                 val normalizedWebViewUrl = webView.url?.removeSuffix("/") ?: ""
                                 
                                 if (normalizedWebViewUrl != normalizedTabUrl && tab.url != "about:blank" && !tab.isLoading) {
                                     val headers = mutableMapOf<String, String>()
                                     if (doNotTrackEnabled) {
                                         headers["DNT"] = "1"
                                     }
                                     webView.loadUrl(tab.url, headers)
                                 }
                             }
                         },
                         modifier = Modifier.fillMaxSize()
                     )
                 } else {
                     Box(
                         modifier = Modifier.fillMaxSize(),
                         contentAlignment = Alignment.Center
                     ) {
                         Text("New Tab", color = MaterialTheme.colorScheme.onSurfaceVariant)
                     }
                 }
                
                 // Loading Overlay
                 if (tab.isLoading) {
                     LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
                 }

                 // Drag Drop Overlay
                 DragDropOverlay(
                     isDragging = isDragging,
                     isHovering = isHoveringDropZone,
                     modifier = Modifier.align(Alignment.TopEnd)
                 )
            }
        }
        
        // Resize Handle (Bottom Right)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(32.dp)
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f), RoundedCornerShape(topStart = 12.dp))
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val scaleChange = 1f + (dragAmount.x + dragAmount.y) / 500f
                        scale *= scaleChange
                        onFocus()
                    }
                }
        )
    }
}

/**
 * Helper function to start a download using DownloadManager
 */
private fun startDownload(context: Context, url: String, fileName: String) {
    try {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setDescription("Downloading...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)
    } catch (e: Exception) {
        // Handle download error silently in release
    }
}

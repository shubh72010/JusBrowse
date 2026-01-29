package com.jusdots.jusbrowse.ui.components

import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.web.*
import com.jusdots.jusbrowse.security.ContentBlocker
import com.jusdots.jusbrowse.ui.viewmodel.BrowserViewModel
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.CookieManager
import java.io.ByteArrayInputStream
import android.content.ClipData
import android.view.DragEvent
import android.view.View
import android.widget.FrameLayout
import android.os.Build
import androidx.compose.ui.platform.LocalContext

@Composable
fun AddressBarWithWebView(
    viewModel: BrowserViewModel,
    tabIndex: Int,
    modifier: Modifier = Modifier
) {
    val tab = if (tabIndex in viewModel.tabs.indices) viewModel.tabs[tabIndex] else null
    val searchEngine by viewModel.searchEngine.collectAsStateWithLifecycle(initialValue = "DuckDuckGo")
    val adBlockEnabled by viewModel.adBlockEnabled.collectAsStateWithLifecycle(initialValue = true)
    val httpsOnly by viewModel.httpsOnly.collectAsStateWithLifecycle(initialValue = false)
    
    // Local state for the address bar text - initialized with current URL
    // CRITICAL: Observe current tab's URL specifically, not the global flow
    var urlText by remember(tab?.url) { mutableStateOf(tab?.url?.replace("about:blank", "") ?: "") }
    
    val focusManager = LocalFocusManager.current
    var isDragging by remember { mutableStateOf(false) }

    // Download warning dialog state
    var showDownloadWarning by remember { mutableStateOf(false) }
    var pendingDownloadInfo by remember { mutableStateOf<com.jusdots.jusbrowse.security.DownloadValidator.DownloadValidationResult?>(null) }
    var pendingDownloadUrl by remember { mutableStateOf("") }
    val context = LocalContext.current
    
    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
        // Address Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (tab?.isPrivate == true) {
                Icon(
                    imageVector = Icons.Default.VpnKey,
                    contentDescription = "Private",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                IconButton(
                    onClick = { /* Could show security details */ },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (tab?.url?.startsWith("https") == true) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "Security",
                        modifier = Modifier.size(16.dp),
                        tint = if (tab?.url?.startsWith("https") == true)  MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
            // Text Field
            BasicTextField(
                value = urlText,
                onValueChange = { urlText = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .onFocusChanged { focusState ->
                        // When focused, show full URL or empty if new tab
                        if (focusState.isFocused) {
                            urlText = if (tab?.url == "about:blank") "" else (tab?.url ?: "")
                        }
                    },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(
                    onGo = {
                        val query = urlText.trim()
                        if (query.isNotEmpty() && tab != null) {
                            if (viewModel.isUrlQuery(query)) {
                                val searchUrl = viewModel.getSearchUrl(query, searchEngine)
                                viewModel.navigateToUrlForIndex(tabIndex, searchUrl)
                            } else {
                                viewModel.navigateToUrlForIndex(tabIndex, query)
                            }
                            focusManager.clearFocus()
                        }
                    }
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
            )

            if (urlText.isNotEmpty()) {
                IconButton(
                    onClick = { urlText = "" },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            IconButton(
                onClick = {
                    // Reload
                    val currentUrl = tab?.url ?: return@IconButton
                    viewModel.navigateToUrlForIndex(tabIndex, currentUrl)
                }
            ) {
                Icon(
                    imageVector = if (tab?.isLoading == true) Icons.Default.Close else Icons.Default.Refresh,
                    contentDescription = if (tab?.isLoading == true) "Stop" else "Reload"
                )
            }
        }
        
        // Loading Progress
        if (tab?.isLoading == true && tab.progress < 1f) {
            LinearProgressIndicator(
                progress = { tab.progress },
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // WebView
        if (tab != null && tab.url != "about:blank") {
             // Use AndroidView to bind to the specific WebView from pool
             key(tab.id) {
                 AndroidView(
                     factory = { ctx ->
                     // Check pool first, if null create new
                     val existing = viewModel.getWebView(tab.id)
                     if (existing != null) {
                         (existing.parent as? ViewGroup)?.removeView(existing)
                         existing
                     } else {
                         WebView(ctx).apply {
                             settings.javaScriptEnabled = true
                             settings.domStorageEnabled = true
                             
                             setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
                                 // Post to main thread to show dialog
                                 val validation = com.jusdots.jusbrowse.security.DownloadValidator.validateDownload(
                                     url, userAgent, contentDisposition, mimeType, contentLength
                                 )
                                 
                                 pendingDownloadUrl = url
                                 pendingDownloadInfo = validation
                                 showDownloadWarning = true
                             }
                             
                             // Security Hardening
                             settings.safeBrowsingEnabled = true
                             settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                             CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

                             if (tab.isPrivate) {
                                 settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                             }

                             setOnLongClickListener { v ->
                                 val hitTest = (v as WebView).hitTestResult
                                 if (hitTest.type == WebView.HitTestResult.IMAGE_TYPE || 
                                     hitTest.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                                     
                                     val url = hitTest.extra
                                     if (url != null) {
                                         val item = ClipData.Item(url)
                                         val data = ClipData("Image", arrayOf("text/plain"), item)
                                         val shadow = View.DragShadowBuilder(v)
                                         
                                         if (Build.VERSION.SDK_INT >= 24) {
                                             v.startDragAndDrop(data, shadow, null, 0)
                                         } else {
                                             @Suppress("DEPRECATION")
                                             v.startDrag(data, shadow, null, 0)
                                         }
                                         isDragging = true
                                         true
                                     } else false
                                 } else false
                             }
                             
                             webViewClient = object : WebViewClient() {
                                 override fun shouldInterceptRequest(
                                     view: WebView?,
                                     request: WebResourceRequest?
                                 ): WebResourceResponse? {
                                     val url = request?.url?.toString() ?: return null
                                     
                                     // 1. Ad Blocking
                                     if (adBlockEnabled && viewModel.contentBlocker.shouldBlock(url)) {
                                         return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
                                     }
                                     
                                     return super.shouldInterceptRequest(view, request)
                                 }

                                 override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                      // 2. HTTPS Only Upgrade
                                      if (httpsOnly && url?.startsWith("http://") == true) {
                                          val httpsUrl = url.replaceFirst("http://", "https://")
                                          view?.post { view.loadUrl(httpsUrl) }
                                          return
                                      }
                                      
                                      // 3. Inject Persona Fingerprints (Early)
                                      val script = com.jusdots.jusbrowse.security.FakeModeManager.generateFingerprintScript()
                                      view?.evaluateJavascript(script, null)

                                      viewModel.updateTabLoadingState(tabIndex, true)
                                      url?.let { 
                                          if (it != tab.url) {
                                              viewModel.navigateToUrlForIndex(tabIndex, it) 
                                          }
                                      }
                                 }
                                 override fun onPageFinished(view: WebView?, url: String?) {
                                      // Re-inject to ensure persistence after some dynamic loads
                                      val script = com.jusdots.jusbrowse.security.FakeModeManager.generateFingerprintScript()
                                      view?.evaluateJavascript(script, null)

                                      viewModel.updateTabLoadingState(tabIndex, false)
                                      view?.title?.let { viewModel.updateTabTitle(tabIndex, it) }
                                      viewModel.updateTabNavigationState(tabIndex, view?.canGoBack() == true, view?.canGoForward() == true)
                                 }
                             }
                             viewModel.registerWebView(tab.id, this)
                             
                             val headers = com.jusdots.jusbrowse.security.FakeModeManager.getHeaders()
                             if (headers.isNotEmpty()) {
                                 loadUrl(tab.url, headers)
                             } else {
                                 loadUrl(tab.url)
                             }
                         }
                     }
                 },
                 update = { webView ->
                     // DYNAMIC PERSONA UPDATE
                     // Check if User-Agent needs update
                     val fakeModeUA = com.jusdots.jusbrowse.security.FakeModeManager.getUserAgent()
                     val targetUA = fakeModeUA ?: android.webkit.WebSettings.getDefaultUserAgent(webView.context)
                     
                     if (webView.settings.userAgentString != targetUA) {
                         webView.settings.userAgentString = targetUA
                         // If we changed UA, we might want to reload check? 
                         // But usually FakeModeManager handles the "toggle" logic which might force reload.
                     }

                     // PROTECT IME: Never update URL if user is typing
                     if (!webView.hasFocus()) {
                         val normalizedTabUrl = tab.url.removeSuffix("/")
                         val normalizedWebViewUrl = webView.url?.removeSuffix("/") ?: ""
                         
                         if (normalizedWebViewUrl != normalizedTabUrl && !tab.isLoading) {
                            // Apply custom headers for main frame load
                            val headers = com.jusdots.jusbrowse.security.FakeModeManager.getHeaders()
                            if (headers.isNotEmpty()) {
                                webView.loadUrl(tab.url, headers)
                            } else {
                                webView.loadUrl(tab.url)
                            }
                         }
                     }
                 },
                 modifier = Modifier.fillMaxSize()
             )
            }
        } else {
            // New Tab Page
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "JusBrowse",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Start browsing",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        }
        
        // Drop Zone Overlay
        if (isDragging) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 96.dp, end = 16.dp)
                    .size(120.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                // Visuals
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Drop",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "Drop to\nDownload",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                // Invisible AndroidView to catch the Drag Events
                AndroidView(
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            setOnDragListener { _, event ->
                                when (event.action) {
                                    DragEvent.ACTION_DRAG_STARTED -> true
                                    DragEvent.ACTION_DRAG_ENTERED -> true
                                    DragEvent.ACTION_DRAG_EXITED -> true
                                    DragEvent.ACTION_DROP -> {
                                        // Handle Drop
                                        val clipData = event.clipData
                                        if (clipData != null && clipData.itemCount > 0) {
                                            val url = clipData.getItemAt(0).text.toString()
                                            // Trigger Download
                                            // Mock file name and size since we don't have HEAD request here
                                            val fileName = "download_${System.currentTimeMillis()}.jpg" 
                                            // We could improve this by doing a proper download request or passing to viewmodel
                                            viewModel.addDownload(fileName, url, "Downloads/$fileName", 0L)
                                            android.widget.Toast.makeText(ctx, "Download started", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                        isDragging = false
                                        true
                                    }
                                    DragEvent.ACTION_DRAG_ENDED -> {
                                        isDragging = false
                                        true
                                    }
                                    else -> false
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        
        // Download Warning Dialog
        if (showDownloadWarning && pendingDownloadInfo != null) {
            AlertDialog(
                onDismissRequest = { showDownloadWarning = false },
                title = { Text("Download Security") },
                text = { Text(pendingDownloadInfo!!.warningMessage ?: "Do you want to download ${pendingDownloadInfo!!.fileName}?") },
                confirmButton = {
                    Button(
                        onClick = {
                            if (pendingDownloadInfo!!.isAllowed) {
                                viewModel.startDownload(context, pendingDownloadUrl, pendingDownloadInfo!!.fileName)
                            }
                            showDownloadWarning = false
                        }
                    ) {
                        Text("Download")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDownloadWarning = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

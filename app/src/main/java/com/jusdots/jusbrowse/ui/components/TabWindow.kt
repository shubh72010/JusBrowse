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
    
    // Fake Mode state
    val fakeModeEnabled by FakeModeManager.isEnabled.collectAsStateWithLifecycle()
    val currentPersona by FakeModeManager.currentPersona.collectAsStateWithLifecycle()
    
    // Fake Mode dialogs
    var showFakeModeDialog by remember { mutableStateOf(false) }
    var showPersonaDetails by remember { mutableStateOf(false) }

    // Gesture State
    var offsetX by remember { mutableFloatStateOf(windowState.x) }
    var offsetY by remember { mutableFloatStateOf(windowState.y) }
    var scale by remember { mutableFloatStateOf(windowState.scale) }
    
    // Site settings flow collected at composable level
    val siteSettings by viewModel.getSiteSettings(tab.url).collectAsState(initial = null)
    
    // Permission request dialog state
    var showPermissionDialog by remember { mutableStateOf(false) }
    var pendingPermissionRequest by remember { mutableStateOf<SecureWebChromeClient.PermissionRequestInfo?>(null) }
    
    // Download warning dialog state
    var showDownloadWarning by remember { mutableStateOf(false) }
    var pendingDownloadInfo by remember { mutableStateOf<DownloadValidator.DownloadValidationResult?>(null) }
    var pendingDownloadUrl by remember { mutableStateOf("") }

    // Drag and Drop State
    var isDragging by remember { mutableStateOf(false) }
    var isHoveringDropZone by remember { mutableStateOf(false) }
    var boxSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    // Sync back to ViewModel occasionally or on end
    LaunchedEffect(offsetX, offsetY) {
        viewModel.updateWindowPosition(tab.id, offsetX, offsetY)
    }
    LaunchedEffect(scale) {
        viewModel.updateWindowScale(tab.id, scale)
    }
    
    // Permission request dialog
    if (showPermissionDialog && pendingPermissionRequest != null) {
        PermissionRequestDialog(
            origin = pendingPermissionRequest!!.origin,
            permissions = pendingPermissionRequest!!.permissions,
            onGrant = {
                pendingPermissionRequest?.callback?.invoke(true)
                showPermissionDialog = false
                pendingPermissionRequest = null
            },
            onDeny = {
                pendingPermissionRequest?.callback?.invoke(false)
                showPermissionDialog = false
                pendingPermissionRequest = null
            }
        )
    }
    
    // Download warning dialog
    if (showDownloadWarning && pendingDownloadInfo != null) {
        DownloadWarningDialog(
            fileName = pendingDownloadInfo!!.fileName,
            warningMessage = pendingDownloadInfo!!.warningMessage 
                ?: "Download ${pendingDownloadInfo!!.fileName}?",
            isBlocked = !pendingDownloadInfo!!.isAllowed,
            onConfirm = {
                // Proceed with download
                if (pendingDownloadInfo!!.isAllowed) {
                    startDownload(context, pendingDownloadUrl, pendingDownloadInfo!!.fileName)
                    viewModel.addDownload(
                        fileName = pendingDownloadInfo!!.fileName,
                        url = pendingDownloadUrl,
                        filePath = Environment.DIRECTORY_DOWNLOADS + "/" + pendingDownloadInfo!!.fileName,
                        fileSize = 0
                    )
                }
                showDownloadWarning = false
                pendingDownloadInfo = null
            },
            onDismiss = {
                showDownloadWarning = false
                pendingDownloadInfo = null
            }
        )
    }
    
    // Fake Mode Dialog
    if (showFakeModeDialog) {
        FakeModeDialog(
            onDismiss = { showFakeModeDialog = false },
            onEnable = { persona ->
                FakeModeManager.enableFakeMode(context, persona)
                showFakeModeDialog = false
                // Reload page with new persona
                viewModel.getWebView(tab.id)?.reload()
            }
        )
    }
    
    // Persona Details Dialog
    if (showPersonaDetails && currentPersona != null) {
        PersonaDetailsDialog(
            persona = currentPersona!!,
            onDismiss = { showPersonaDetails = false },
            onDisable = {
                FakeModeManager.disableFakeMode(context)
                showPersonaDetails = false
                viewModel.getWebView(tab.id)?.reload()
            }
        )
    }

    // Context Menu State
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuData by remember { mutableStateOf(ContextMenuData()) }

    // Context Menu UI
    if (showContextMenu) {
        BrowserContextMenu(
            data = contextMenuData,
            onDismissRequest = { showContextMenu = false },
            onOpenInNewTab = { url -> 
                viewModel.createNewTab(url)
                showContextMenu = false
            },
            onOpenInBackgroundTab = { url ->
                viewModel.createNewTab(url, select = false)
                showContextMenu = false
            },
            onOpenInIncognito = { url ->
                viewModel.createNewTab(url, isPrivate = true)
                showContextMenu = false
            },
            onCopyLink = { url ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Link", url)
                clipboard.setPrimaryClip(clip)
                showContextMenu = false
            },
            onShareLink = { url ->
                val sendIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_TEXT, url)
                    type = "text/plain"
                }
                context.startActivity(android.content.Intent.createChooser(sendIntent, null))
                showContextMenu = false
            },
            onDownloadImage = { url ->
                startDownload(context, url, android.webkit.URLUtil.guessFileName(url, null, null))
                showContextMenu = false
            },
            onShareImage = { url ->
                val sendIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_TEXT, url)
                    type = "text/plain"
                }
                context.startActivity(android.content.Intent.createChooser(sendIntent, null))
                showContextMenu = false
            },
            onPinToDesktop = { title, url ->
                viewModel.pinShortcut(title, url)
                showContextMenu = false
            }
        )
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .zIndex(windowState.zIndex)
            .size(360.dp * scale, 600.dp * scale)
            .shadow(16.dp, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable { onFocus() }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // Window Title Bar (Drag Handle)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                            onFocus()
                        }
                    }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Window Title with Private Indicator
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (tab.isPrivate) {
                        PrivateTabIndicator()
                    } else {
                        Icon(
                            Icons.Default.VpnKey,
                            contentDescription = "Tab",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                        )
                    }
                    
                    // Fake Mode Indicator
                    if (fakeModeEnabled && currentPersona != null) {
                        FakeModeIndicator(
                            persona = currentPersona!!,
                            onClick = { showPersonaDetails = true }
                        )
                    }
                    
                    Text(
                        text = tab.title.take(15),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1
                    )
                }
                
                // Close Button
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "Close Window", modifier = Modifier.size(18.dp))
                }
            }

            // In-Window Navigation Controls (Address Bar + Nav Buttons + Security Indicators)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back
                IconButton(
                    onClick = {
                        viewModel.getWebView(tab.id)?.goBack()
                    },
                    modifier = Modifier.size(32.dp),
                    enabled = tab.canGoBack
                ) {
                    Icon(Icons.Default.ArrowBack, "Back", modifier = Modifier.size(16.dp))
                }

                // Forward
                IconButton(
                    onClick = {
                        viewModel.getWebView(tab.id)?.goForward()
                    },
                    modifier = Modifier.size(32.dp),
                    enabled = tab.canGoForward
                ) {
                    Icon(Icons.Default.ArrowForward, "Forward", modifier = Modifier.size(16.dp))
                }
                
                // Security Lock Icon (Layer 10)
                SecurityLockIcon(
                    url = tab.url,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                // Compact Address Bar
                var text by remember(tab.url) { mutableStateOf(tab.url.replace("about:blank", "")) }
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = {
                             if (text.isNotEmpty()) {
                                if (viewModel.isUrlQuery(text)) {
                                    val searchUrl = viewModel.getSearchUrl(text, searchEngine)
                                    viewModel.navigateToUrlByTabId(tab.id, searchUrl)
                                } else {
                                    viewModel.navigateToUrlByTabId(tab.id, text)
                                }
                             }
                        }
                    )
                )
                
                // JS Indicator (Layer 10)
                val jsEnabled = siteSettings?.javascriptEnabled ?: true
                JavaScriptIndicator(
                    isEnabled = jsEnabled,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            
            // WebView Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .dragAndDropTarget(
                        shouldStartDragAndDrop = { event ->
                            event.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN)
                        },
                        target = object : DragAndDropTarget {
                            override fun onStarted(event: DragAndDropEvent) {
                                isDragging = true
                            }
                            
                            override fun onEnded(event: DragAndDropEvent) {
                                isDragging = false
                                isHoveringDropZone = false
                            }

                            override fun onDrop(event: DragAndDropEvent): Boolean {
                                // Check if dropped in top-right corner (handled by overlay check logically or here)
                                // Since we have an overlay, we'll let the overlay visual guide the user,
                                // but the drop logic can be here or specialized.
                                
                                // Actually, let's detect if it was dropped on the TopRight area.
                                // For now, if we are hovering (tracked via moved), we accept.
                                if (isHoveringDropZone) {
                                    val data = event.toAndroidDragEvent().clipData
                                    if (data != null && data.itemCount > 0) {
                                        val url = data.getItemAt(0).text.toString()
                                        if (url.startsWith("http")) {
                                             viewModel.addDownload(
                                                fileName = android.webkit.URLUtil.guessFileName(url, null, null),
                                                url = url,
                                                filePath = Environment.DIRECTORY_DOWNLOADS + "/" + android.webkit.URLUtil.guessFileName(url, null, null),
                                                fileSize = 0 // Unknown
                                            )
                                            android.widget.Toast.makeText(context, "Download started", android.widget.Toast.LENGTH_SHORT).show()
                                            return true
                                        }
                                    }
                                }
                                return false
                            }

                            override fun onMoved(event: DragAndDropEvent) {
                                val x = event.toAndroidDragEvent().x
                                val y = event.toAndroidDragEvent().y
                                
                                // Overlay is top-right 125dp approx
                                // Check if x > width - 150dp and y < 150dp (roughly)
                                val density = context.resources.displayMetrics.density
                                val threshold = 150 * density
                                
                                if (boxSize.width > 0) {
                                    val inRegion = x > (boxSize.width - threshold) && y < threshold
                                    if (isHoveringDropZone != inRegion) {
                                        isHoveringDropZone = inRegion
                                    }
                                }
                            }
                        }
                    )
                    .onSizeChanged { boxSize = it }
            ) {
                 if (tab.url != "about:blank") {
                     AndroidView(
                         factory = { ctx ->
                             // Check for existing WebView in pool
                             val existing = viewModel.getWebView(tab.id)
                             if (existing != null) {
                                 (existing.parent as? ViewGroup)?.removeView(existing)
                                 existing
                             } else {
                                 WebView(ctx).apply {
                                     // CONTEXT MENU INTEGRATION
                                     setOnLongClickListener { view ->
                                         val hitTestResult = (view as WebView).hitTestResult
                                         when (hitTestResult.type) {
                                             WebView.HitTestResult.SRC_ANCHOR_TYPE,
                                             WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE,
                                             WebView.HitTestResult.IMAGE_TYPE -> {
                                                 val url = hitTestResult.extra
                                                 if (url != null) {
                                                     // Start Drag and Drop
                                                     val clipData = ClipData.newPlainText("url", url)
                                                     val shadowBuilder = android.view.View.DragShadowBuilder(view)
                                                     
                                                     // We need to allow local state updates to show overlay
                                                     isDragging = true
                                                     
                                                     try {
                                                         // Start standard Drag and Drop
                                                         if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                                             view.startDragAndDrop(clipData, shadowBuilder, null, android.view.View.DRAG_FLAG_GLOBAL)
                                                         } else {
                                                             view.startDrag(clipData, shadowBuilder, null, 0)
                                                         }
                                                     } catch (e: Exception) {
                                                         // Fallback to context menu if drag fails
                                                         isDragging = false
                                                         android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                                 val type = when(hitTestResult.type) {
                                                                     WebView.HitTestResult.IMAGE_TYPE -> ContextMenuType.IMAGE
                                                                     WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> ContextMenuType.IMAGE_LINK
                                                                     else -> ContextMenuType.LINK
                                                                 }
                                                                 contextMenuData = ContextMenuData(
                                                                     url = url,
                                                                     type = type,
                                                                     extra = if (type != ContextMenuType.LINK) url else null
                                                                 )
                                                                 showContextMenu = true
                                                         }
                                                     }
                                                     
                                                     true // Consume event
                                                 } else false
                                             }
                                             else -> false
                                         }
                                     }

                                     layoutParams = ViewGroup.LayoutParams(
                                         ViewGroup.LayoutParams.MATCH_PARENT,
                                         ViewGroup.LayoutParams.MATCH_PARENT
                                     )
                                     
                                     // ==============================
                                     // LAYER 1: Core WebView Hardening
                                     // ==============================
                                     settings.javaScriptEnabled = true // Controlled, not disabled
                                     settings.domStorageEnabled = true
                                     settings.databaseEnabled = true
                                     settings.loadWithOverviewMode = true
                                     settings.useWideViewPort = true
                                     
                                     // Security settings
                                     settings.safeBrowsingEnabled = true
                                     settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                     
                                     // Disable dangerous file access
                                     settings.allowFileAccess = false
                                     settings.allowContentAccess = false
                                     settings.allowFileAccessFromFileURLs = false
                                     settings.allowUniversalAccessFromFileURLs = false
                                     
                                     // Additional hardening
                                     settings.setSupportMultipleWindows(false) // Block window.open abuse
                                     settings.saveFormData = false // Don't save form data
                                     settings.setGeolocationEnabled(false) // Require explicit permission
                                     
                                     // ==============================
                                     // LAYER 7: Fingerprinting Resistance + Fake Mode
                                     // ==============================
                                     val userAgent = FakeModeManager.getUserAgent()
                                         ?: "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
                                     settings.userAgentString = userAgent

                                     // ==============================
                                     // LAYER 5: Cookie Control
                                     // ==============================
                                     CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

                                     // ==============================
                                     // LAYER 6: Private Mode Configuration
                                     // ==============================
                                     if (tab.isPrivate) {
                                         PrivateBrowsingManager.configurePrivateWebView(this)
                                     }
                                     
                                     // Zoom controls
                                     settings.setSupportZoom(true)
                                     settings.builtInZoomControls = true
                                     settings.displayZoomControls = false
                                     
                                     setInitialScale((scale * 100).toInt())
                                     
                                     // ==============================
                                     // LAYER 8: Permission Handling
                                     // ==============================
                                     webChromeClient = SecureWebChromeClient(
                                         onPermissionRequest = { request ->
                                             pendingPermissionRequest = request
                                             showPermissionDialog = true
                                         },
                                         onProgressChanged = { progress ->
                                             // Can update loading progress here
                                         },
                                         onTitleChanged = { title ->
                                             viewModel.updateTabTitle(tabIndex, title)
                                         }
                                     )
                                     
                                     // ==============================
                                     // LAYER 9: Download Safety
                                     // ==============================
                                     setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
                                         // Download listener runs on background thread, post to main thread
                                         android.os.Handler(android.os.Looper.getMainLooper()).post {
                                             val validation = DownloadValidator.validateDownload(
                                                 url, userAgent, contentDisposition, mimeType, contentLength
                                             )
                                             
                                             // Always show download dialog for user confirmation
                                             pendingDownloadUrl = url
                                             pendingDownloadInfo = validation
                                             showDownloadWarning = true
                                         }
                                     }
                                     
                                     webViewClient = object : WebViewClient() {
                                         override fun shouldInterceptRequest(
                                             view: WebView?,
                                             request: WebResourceRequest?
                                         ): WebResourceResponse? {
                                             val url = request?.url?.toString() ?: return null
                                             
                                             // Ad Blocking
                                             if (adBlockEnabled && viewModel.contentBlocker.shouldBlock(url)) {
                                                 return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
                                             }
                                             
                                             return super.shouldInterceptRequest(view, request)
                                         }

                                         override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                              // ==============================
                                              // LAYER 2: JS Control for Dangerous URIs
                                              // ==============================
                                              url?.let { currentUrl ->
                                                  val shouldDisableJS = currentUrl.startsWith("about:") ||
                                                          currentUrl.startsWith("file://") ||
                                                          currentUrl.startsWith("content://")
                                                  
                                                  if (shouldDisableJS) {
                                                      view?.settings?.javaScriptEnabled = false
                                                  } else {
                                                      // Restore JS setting based on site settings
                                                      view?.settings?.javaScriptEnabled = siteSettings?.javascriptEnabled ?: true
                                                  }
                                              }
                                              
                                              // HTTPS Only Upgrade (Layer 4)
                                              if (httpsOnly && url?.startsWith("http://") == true) {
                                                  val httpsUrl = url.replaceFirst("http://", "https://")
                                                  view?.post { view.loadUrl(httpsUrl) }
                                                  return
                                              }

                                              viewModel.updateTabLoadingState(tabIndex, true)
                                              url?.let { 
                                                  if (it != tab.url) {
                                                      viewModel.navigateToUrlByTabId(tab.id, it)
                                                  }
                                              }
                                         }
                                         
                                         override fun onPageFinished(view: WebView?, url: String?) {
                                              viewModel.updateTabLoadingState(tabIndex, false)
                                              view?.title?.let { viewModel.updateTabTitle(tabIndex, it) }
                                              viewModel.updateTabNavigationState(tabIndex, view?.canGoBack() == true, view?.canGoForward() == true)
                                              
                                      // ==============================
                                      // LAYER 7: Inject Fingerprinting Protection
                                      // Uses Fake Mode persona if enabled, else generic protection
                                      // ==============================
                                      val fingerprintScript = FakeModeManager.generateFingerprintScript()
                                      view?.evaluateJavascript(fingerprintScript, null)
                                      
                                      // Cookie Pop-up Blocker
                                      if (cookieBlockerEnabled) {
                                          view?.evaluateJavascript(com.jusdots.jusbrowse.security.CookieDialogBlocker.blockerScript, null)
                                      }
                                 }
                             }
                             
                             viewModel.registerWebView(tab.id, this)
                             
                             // Initial Load with Headers
                             val headers = mutableMapOf<String, String>()
                             if (doNotTrackEnabled) {
                                 headers["DNT"] = "1"
                             }
                             loadUrl(tab.url, headers)
                         }
                     }
                 },
                         update = { webView ->
                             // Apply site settings that were collected at the Composable level
                             siteSettings?.let { settings ->
                                 if (webView.settings.javaScriptEnabled != settings.javascriptEnabled) {
                                     webView.settings.javaScriptEnabled = settings.javascriptEnabled
                                 }
                                 if (webView.settings.domStorageEnabled != settings.domStorageEnabled) {
                                     webView.settings.domStorageEnabled = settings.domStorageEnabled
                                 }
                             }

                             // THE NUCLEAR GUARD: Never poke the WebView settings or scale while the user has focus
                             if (!webView.hasFocus()) {
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

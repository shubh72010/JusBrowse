package com.jusdots.jusbrowse.ui.components

import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.gson.Gson
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AddressBarWithWebView(
    viewModel: BrowserViewModel,
    tabIndex: Int,
    onOpenAirlockGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tab = if (tabIndex in viewModel.tabs.indices) viewModel.tabs[tabIndex] else null
    val searchEngine by viewModel.searchEngine.collectAsStateWithLifecycle(initialValue = "DuckDuckGo")
    val adBlockEnabled by viewModel.adBlockEnabled.collectAsStateWithLifecycle(initialValue = true)
    val httpsOnly by viewModel.httpsOnly.collectAsStateWithLifecycle(initialValue = false)
    val follianMode by viewModel.follianMode.collectAsStateWithLifecycle(initialValue = false)
    
    // Engines
    val defaultEngineEnabled by viewModel.defaultEngineEnabled.collectAsStateWithLifecycle(initialValue = true)
    val jusFakeEnabled by viewModel.jusFakeEngineEnabled.collectAsStateWithLifecycle(initialValue = false)
    val randomiserEnabled by viewModel.randomiserEngineEnabled.collectAsStateWithLifecycle(initialValue = false)
    
    // PILL BAR STATES
    var isPillExpanded by remember { mutableStateOf(false) }
    var showPillMenu by remember { mutableStateOf(false) }

    // Local state for the address bar text
    var urlText by remember { mutableStateOf(tab?.url?.replace("about:blank", "") ?: "") }
    
    // Sync URL text from tab changes, but ONLY if not expanded (not typing)
    LaunchedEffect(tab?.url, isPillExpanded) {
        if (!isPillExpanded) {
            urlText = tab?.url?.replace("about:blank", "") ?: ""
        }
    }
    
    val focusManager = LocalFocusManager.current
    var isDragging by remember { mutableStateOf(false) }

    val context = LocalContext.current
    
    // Download confirmation states
    var showDownloadWarning by remember { mutableStateOf(false) }
    var pendingDownloadInfo by remember { mutableStateOf<com.jusdots.jusbrowse.security.DownloadValidator.DownloadValidationResult?>(null) }
    var pendingDownloadUrl by remember { mutableStateOf("") }
    
    var showTrackerDetails by remember { mutableStateOf(false) }
    val trackers = if (tab != null) viewModel.blockedTrackers[tab.id] ?: emptyList() else emptyList()


    
    // Elastic Swipe State (Animatable for smooth physics)
    val pillOffset = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = rememberCoroutineScope()
    
    // Focus Requester for Search Bar
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    
    // Auto-focus when expanded
    var hasGainedFocus by remember { mutableStateOf(false) }

    LaunchedEffect(isPillExpanded) {
        if (isPillExpanded) {
            // Reset focus tracker
            hasGainedFocus = false 
            // Short delay to ensure composition
            kotlinx.coroutines.delay(100)
            focusRequester.requestFocus()
        }
    }


    // Scroll Hide State
    val bottomBarHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { 100.dp.toPx() }
    val bottomBarOffsetHeightPx = remember { Animatable(0f) }

    val nestedScrollConnection = remember(isPillExpanded) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // If the search bar is expanded, don't hide it on scroll
                if (isPillExpanded) return Offset.Zero
                
                val delta = available.y
                val newOffset = bottomBarOffsetHeightPx.value + (-delta)
                scope.launch {
                    bottomBarOffsetHeightPx.snapTo(newOffset.coerceIn(0f, bottomBarHeightPx))
                }
                return Offset.Zero
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().nestedScroll(nestedScrollConnection)) {
        // 1. WebView Content Layer (Full Screen)
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
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
                                 // State Partitioning: Apply isolated profile
                                 com.jusdots.jusbrowse.security.ContainerManager.applyContainer(this, tab.containerId ?: "default")
                                 
                                 // Follian Mode - Hard JavaScript Kill
                                 if (follianMode) {
                                     com.jusdots.jusbrowse.security.FollianBlocker.applyToWebView(this)
                                 } else {
                                     settings.javaScriptEnabled = true
                                 }
                                 
                                 settings.domStorageEnabled = true
                                 addJavascriptInterface(com.jusdots.jusbrowse.security.FakeModeManager.PrivacyBridge(), "jusPrivacyBridge")
                                 
                                 setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
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
                                          if (adBlockEnabled && runBlocking { 
                                              viewModel.contentBlocker.shouldBlock(url) { domain ->
                                                  viewModel.recordBlockedTracker(tab.id, domain)
                                              } 
                                          }) {
                                              return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
                                          }
                                         
                                         return super.shouldInterceptRequest(view, request)
                                     }

                                     override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                          if (httpsOnly && url?.startsWith("http://") == true) {
                                              val httpsUrl = url.replaceFirst("http://", "https://")
                                              view?.post { view.loadUrl(httpsUrl) }
                                              return
                                          }
                                          
                                          val script = com.jusdots.jusbrowse.security.FakeModeManager.generateFingerprintScript(
                                              defaultEnabled = defaultEngineEnabled,
                                              jusFakeEnabled = jusFakeEnabled,
                                              randomiserEnabled = randomiserEnabled
                                          )
                                          view?.evaluateJavascript(script, null)

                                          if (!tab.isPrivate) {
                                              view?.settings?.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                                          }

                                          viewModel.updateTabLoadingState(tabIndex, true)
                                          url?.let { 
                                              if (it != tab.url) {
                                                  viewModel.navigateToUrlForIndex(tabIndex, it) 
                                              }
                                          }
                                     }
                                     override fun onPageFinished(view: WebView?, url: String?) {
                                          val script = com.jusdots.jusbrowse.security.FakeModeManager.generateFingerprintScript(
                                              defaultEnabled = defaultEngineEnabled,
                                              jusFakeEnabled = jusFakeEnabled,
                                              randomiserEnabled = randomiserEnabled
                                          )
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
                         val fakeModeUA = com.jusdots.jusbrowse.security.FakeModeManager.getUserAgent()
                         val targetUA = fakeModeUA ?: android.webkit.WebSettings.getDefaultUserAgent(webView.context)
                         
                         if (webView.settings.userAgentString != targetUA) {
                             webView.settings.userAgentString = targetUA
                         }

                         // PROTECT IME: Never update URL if user is typing
                         if (!webView.hasFocus()) {
                             val normalizedTabUrl = tab.url.removeSuffix("/")
                             val normalizedWebViewUrl = webView.url?.removeSuffix("/") ?: ""
                             
                             if (normalizedWebViewUrl != normalizedTabUrl && !tab.isLoading) {
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
                // New Tab Page Logic
                val wallpaperUri by viewModel.startPageWallpaperUri.collectAsStateWithLifecycle(initialValue = null)
                val blurAmount by viewModel.startPageBlurAmount.collectAsStateWithLifecycle(initialValue = 0f)
                val backgroundPresetName by viewModel.backgroundPreset.collectAsStateWithLifecycle(initialValue = "NONE")
                
                val backgroundPreset = try {
                    com.jusdots.jusbrowse.ui.theme.BackgroundPreset.valueOf(backgroundPresetName)
                } catch (e: Exception) {
                    com.jusdots.jusbrowse.ui.theme.BackgroundPreset.NONE
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Animated Background Preset (if no custom wallpaper)
                    if (wallpaperUri == null && backgroundPreset != com.jusdots.jusbrowse.ui.theme.BackgroundPreset.NONE) {
                        BackgroundRenderer(
                            preset = backgroundPreset,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
                    }
                    
                    // Custom Wallpaper (takes priority over preset)
                    if (wallpaperUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(wallpaperUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().blur(blurAmount.dp)
                        )
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "JusBrowse",
                            style = MaterialTheme.typography.displayLarge,
                            color = if (wallpaperUri != null || backgroundPreset != com.jusdots.jusbrowse.ui.theme.BackgroundPreset.NONE) 
                                Color.White else MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Start browsing",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (wallpaperUri != null || backgroundPreset != com.jusdots.jusbrowse.ui.theme.BackgroundPreset.NONE) 
                                Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        // 2. Floating Pill Bar (Bottom)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 16.dp)
                .offset { 
                    androidx.compose.ui.unit.IntOffset(
                        pillOffset.value.roundToInt(), 
                        bottomBarOffsetHeightPx.value.roundToInt()
                    ) 
                }
                .fillMaxWidth(0.9f)
                .height(56.dp)
                .shadow(8.dp, CircleShape)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .clip(CircleShape)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val threshold = 200f // Swipe threshold
                            val offset = pillOffset.value
                            scope.launch {
                                if (offset > threshold) {
                                    // Swipe Right -> Back
                                    viewModel.getWebView(tab?.id ?: "")?.let { if (it.canGoBack()) it.goBack() }
                                } else if (offset < -threshold) {
                                    // Swipe Left -> Forward
                                    viewModel.getWebView(tab?.id ?: "")?.let { if (it.canGoForward()) it.goForward() }
                                }
                                // Elastic Snap Back
                                pillOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = androidx.compose.animation.core.spring(
                                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                        stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                                    )
                                )
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                pillOffset.animateTo(0f)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                // Apply resistance (dampening) as we drag further
                                val resistance = 0.6f
                                pillOffset.snapTo(pillOffset.value + (dragAmount * resistance))
                            }
                        }
                    )
                }
                .combinedClickable(
                    onClick = { isPillExpanded = true },
                    onLongClick = { showPillMenu = true }
                )
        ) {
            if (isPillExpanded) {
                // Expanded Edit Mode
                Row(
                   modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                   verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    hasGainedFocus = true
                                }
                                if (!focusState.isFocused && isPillExpanded && hasGainedFocus) {
                                    isPillExpanded = false
                                }
                            },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                val query = urlText.trim()
                                if (query.isNotEmpty() && tab != null) {
                                    if (viewModel.isUrlQuery(query)) {
                                        val searchUrl = viewModel.getSearchUrl(query, searchEngine)
                                        viewModel.navigateToUrlForIndex(tabIndex, searchUrl)
                                    } else {
                                        viewModel.navigateToUrlForIndex(tabIndex, query)
                                    }
                                    isPillExpanded = false
                                    focusManager.clearFocus()
                                }
                            },
                            onGo = { // Fallback support
                                val query = urlText.trim()
                                if (query.isNotEmpty() && tab != null) {
                                    if (viewModel.isUrlQuery(query)) {
                                        val searchUrl = viewModel.getSearchUrl(query, searchEngine)
                                        viewModel.navigateToUrlForIndex(tabIndex, searchUrl)
                                    } else {
                                        viewModel.navigateToUrlForIndex(tabIndex, query)
                                    }
                                    isPillExpanded = false
                                    focusManager.clearFocus()
                                }
                            }
                        ),
                         cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                    )
                    
                    if (urlText.isNotEmpty()) {
                        IconButton(onClick = { urlText = "" }) {
                             Icon(Icons.Default.Clear, "Clear")
                        }
                    }
                }
                // Auto-focus logic would be good here, but BasicTextField focus requester needs separate setup
            } else {
                // Collapsed Read Mode
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (tab?.isPrivate == true) {
                        Icon(Icons.Default.VpnKey, "Private", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                    } else if (trackers.isNotEmpty()) {
                         // Shield Icon for Trackers
                         Icon(Icons.Default.Shield, "Trackers Blocked", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                         Spacer(modifier = Modifier.width(8.dp))
                    } else if (httpsOnly || (tab?.url?.startsWith("https://") == true)) {
                        Icon(Icons.Default.Lock, "Secure", modifier = Modifier.size(14.dp), /* tint = MaterialTheme.colorScheme.primary */)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    
                    Text(
                        text = if (tab?.url == "about:blank") "Search or type URL" else (android.net.Uri.parse(tab?.url).host ?: tab?.url ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    
                    if (tab?.isLoading == true) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                }
            }
            
            // Custom Menu is now outside this box to center on screen
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
                                            val fileName = "download_${System.currentTimeMillis()}.jpg" 
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

        // Download Confirmation Dialog
        if (showDownloadWarning && pendingDownloadInfo != null) {
            AlertDialog(
                onDismissRequest = { showDownloadWarning = false },
                title = { Text("Download File") },
                text = { 
                    Column {
                        Text(pendingDownloadInfo!!.warningMessage ?: "Do you want to download this file?")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = pendingDownloadInfo!!.fileName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.startDownload(context, pendingDownloadUrl, pendingDownloadInfo!!.fileName)
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

        // 3. Elastic Centered Menu Overlay
        AnimatedVisibility(
            visible = showPillMenu,
            enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
             Box(
                 modifier = Modifier
                     .fillMaxSize()
                     .background(Color.Black.copy(alpha = 0.5f))
                     .pointerInput(Unit) { 
                         // Tap outside to dismiss
                         detectTapGestures(onTap = { showPillMenu = false }) 
                     },
                 contentAlignment = Alignment.Center
             ) {
                 Card(
                     modifier = Modifier
                         .width(280.dp)
                         .padding(16.dp)
                         .pointerInput(Unit) { /* Trap clicks */ },
                     shape = RoundedCornerShape(24.dp),
                     colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                     elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                 ) {
                     Column(
                         modifier = Modifier.padding(8.dp).verticalScroll(rememberScrollState())
                     ) {
                         // -- Menu Items --
                        DropdownMenuItem(
                            text = { Text("New Tab (Home)") },
                            onClick = { viewModel.createNewTab(); showPillMenu = false },
                            leadingIcon = { Icon(Icons.Default.Home, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Multi-View Window") },
                            onClick = { viewModel.toggleMultiViewMode(); showPillMenu = false },
                            leadingIcon = { Icon(Icons.Default.GridView, null) }
                        )
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                        
                        DropdownMenuItem(
                            text = { Text("Refresh") },
                            onClick = { viewModel.getWebView(tab?.id ?: "")?.reload(); showPillMenu = false },
                            leadingIcon = { Icon(Icons.Default.Refresh, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy URL") },
                            onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("URL", tab?.url ?: "")
                                clipboard.setPrimaryClip(clip)
                                showPillMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null) }
                        )
                        
                        Divider(modifier = Modifier.padding(vertical = 4.dp))

                        DropdownMenuItem(
                            text = { Text("Bookmarks") },
                            onClick = { viewModel.navigateToScreen(com.jusdots.jusbrowse.ui.screens.Screen.BOOKMARKS); showPillMenu = false },
                            leadingIcon = { Icon(Icons.Default.Star, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("History") },
                            onClick = { viewModel.navigateToScreen(com.jusdots.jusbrowse.ui.screens.Screen.HISTORY); showPillMenu = false },
                            leadingIcon = { Icon(Icons.Default.DateRange, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Downloads") },
                            onClick = { viewModel.navigateToScreen(com.jusdots.jusbrowse.ui.screens.Screen.DOWNLOADS); showPillMenu = false },
                            leadingIcon = { Icon(Icons.Default.Download, null) }
                        )
                        
                        Divider(modifier = Modifier.padding(vertical = 4.dp))

                        DropdownMenuItem(
                            text = { Text("New Private Tab") },
                            onClick = { viewModel.createNewTab(isPrivate = true); showPillMenu = false },
                            leadingIcon = { Icon(Icons.Default.VpnKey, null) }
                        )
                        
                        // Container Expander
                        var showContainers by remember { mutableStateOf(false) }
                        DropdownMenuItem(
                            text = { Text("New Container Tab") },
                            onClick = { showContainers = !showContainers },
                            leadingIcon = { Icon(Icons.Default.Layers, null) },
                            trailingIcon = { Icon(if (showContainers) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null) }
                        )
                        
                       if (showContainers) {
                            com.jusdots.jusbrowse.security.ContainerManager.AVAILABLE_CONTAINERS.filter { it != "default" }.forEach { container ->
                                DropdownMenuItem(
                                    text = { Text(com.jusdots.jusbrowse.security.ContainerManager.getContainerName(container)) },
                                    onClick = {
                                        viewModel.createNewTab(containerId = container)
                                        showPillMenu = false
                                    },
                                    modifier = Modifier.padding(start = 16.dp),
                                    leadingIcon = { Icon(androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_menu_agenda), null, modifier = Modifier.size(16.dp)) } 
                                )
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 4.dp))

                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = { viewModel.navigateToScreen(com.jusdots.jusbrowse.ui.screens.Screen.SETTINGS); showPillMenu = false },
                            leadingIcon = { Icon(Icons.Default.Settings, null) }
                        )
                        
                        // Tracker info needs to use the captured trackers list.
                        // I need to make sure 'trackers' is available here.
                        // 'trackers' variable is defined at the top level of the composable.
                        // So it should be captured.
                        if (trackers.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Trackers: ${trackers.size}") },
                                onClick = { showTrackerDetails = true; showPillMenu = false },
                                leadingIcon = { Icon(Icons.Default.Shield, null) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Airlock Gallery") },
                            onClick = { onOpenAirlockGallery(); showPillMenu = false },
                            leadingIcon = { Icon(Icons.Default.PhotoLibrary, null) }
                        )
                     }
                 }
             }
        }
    }

    if (showTrackerDetails && tab != null) {
        val trackers = viewModel.blockedTrackers[tab.id] ?: emptyList()
        ModalBottomSheet(
            onDismissRequest = { showTrackerDetails = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "Trackers Blocked",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "JusBrowse has blocked ${trackers.size} unwanted connection requests from this page to protect your privacy.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (trackers.isEmpty()) {
                    Text("No trackers detected on this page yet.")
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(trackers.size) { index ->
                            val tracker = trackers[index]
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Block, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(text = tracker.domain, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

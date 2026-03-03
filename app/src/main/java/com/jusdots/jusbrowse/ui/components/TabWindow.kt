package com.jusdots.jusbrowse.ui.components

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.view.ViewGroup
import android.webkit.DownloadListener
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Security
import com.google.gson.Gson
import com.jusdots.jusbrowse.utils.MediaExtractor
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
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
import com.jusdots.jusbrowse.data.models.BrowserTab
import com.jusdots.jusbrowse.ui.viewmodel.BrowserViewModel
import com.jusdots.jusbrowse.security.DownloadValidator
import com.jusdots.jusbrowse.security.FakeModeManager
import com.jusdots.jusbrowse.security.NetworkSurgeon
import com.jusdots.jusbrowse.security.FingerprintingProtection
import com.jusdots.jusbrowse.security.PrivateBrowsingManager
import com.jusdots.jusbrowse.security.SecureWebChromeClient
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jusdots.jusbrowse.ui.components.BrowserContextMenu
import com.jusdots.jusbrowse.ui.components.ContextMenuData
import com.jusdots.jusbrowse.ui.components.ContextMenuType
import com.jusdots.jusbrowse.security.BoringEngine
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import com.jusdots.jusbrowse.security.SuspicionScorer

@SuppressLint("RestrictedApi")
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
    val popupBlockerEnabled by viewModel.popupBlockerEnabled.collectAsStateWithLifecycle(initialValue = true)
    val multiMediaPlaybackEnabled by viewModel.multiMediaPlaybackEnabled.collectAsStateWithLifecycle(initialValue = false)
    
    // Fake Mode state
    val fakeModeEnabled by FakeModeManager.isEnabled.collectAsStateWithLifecycle()
    val currentPersona by FakeModeManager.currentPersona.collectAsStateWithLifecycle()

    // Engines
    val defaultEngineEnabled by viewModel.defaultEngineEnabled.collectAsStateWithLifecycle(initialValue = true)
    val jusFakeEnabled by viewModel.jusFakeEngineEnabled.collectAsStateWithLifecycle(initialValue = false)
    val boringEnabled by viewModel.boringEngineEnabled.collectAsStateWithLifecycle(initialValue = false)
    
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

    // Fullscreen Media State
    var fullscreenView by remember { mutableStateOf<android.view.View?>(null) }
    var fullscreenCallback by remember { mutableStateOf<android.webkit.WebChromeClient.CustomViewCallback?>(null) }
    
    // Download warning dialog state
    var showDownloadWarning by remember { mutableStateOf(false) }
    var pendingDownloadInfo by remember { mutableStateOf<DownloadValidator.DownloadValidationResult?>(null) }
    
    val vtApiKey by viewModel.virusTotalApiKey.collectAsStateWithLifecycle(initialValue = "")
    val koodousApiKey by viewModel.koodousApiKey.collectAsStateWithLifecycle(initialValue = "")
    var pendingDownloadUrl by remember { mutableStateOf("") }

    // Drag and Drop State
    var isDragging by remember { mutableStateOf(false) }
    var isHoveringDropZone by remember { mutableStateOf(false) }
    var boxSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    val isBoomerMode by viewModel.isBoomerMode.collectAsStateWithLifecycle()
    val protectionWhitelist by viewModel.protectionWhitelist.collectAsStateWithLifecycle(initialValue = "")

    // Airlock States
    var showMenu by remember { mutableStateOf(false) }

    // Sync back to ViewModel occasionally or on end
    LaunchedEffect(offsetX, offsetY, tab.id) {
        viewModel.updateWindowPosition(tab.id, offsetX, offsetY)
    }
    LaunchedEffect(scale, tab.id) {
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
                if (pendingDownloadInfo!!.isAllowed && pendingDownloadUrl != null) {
                    val id = startDownload(context, pendingDownloadUrl!!, pendingDownloadInfo!!.fileName)
                    viewModel.addDownload(
                        fileName = pendingDownloadInfo!!.fileName,
                        url = pendingDownloadUrl!!,
                        filePath = Environment.DIRECTORY_DOWNLOADS + "/" + pendingDownloadInfo!!.fileName,
                        fileSize = 0,
                        systemDownloadId = id
                    )
                }
                showDownloadWarning = false
                pendingDownloadInfo = null
            },
            onDismiss = {
                showDownloadWarning = false
                pendingDownloadInfo = null
            },
            onScanVirusTotal = if (vtApiKey.isNotBlank() && pendingDownloadUrl != null) { { viewModel.scanFile(pendingDownloadUrl!!, "VirusTotal", context); showDownloadWarning = false } } else null,
            onScanKoodous = if (koodousApiKey.isNotBlank() && pendingDownloadUrl != null) { { viewModel.scanFile(pendingDownloadUrl!!, "Koodous", context); showDownloadWarning = false } } else null
        )
    }
    
    // Fake Mode Dialog
    if (showFakeModeDialog) {
        FakeModeDialog(
            onDismiss = { showFakeModeDialog = false },
            onEnable = { persona ->
                FakeModeManager.enableFakeMode(context, persona)
                showFakeModeDialog = false
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
                val clip = ClipData.newPlainText("Link", url)
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
                val fileName = android.webkit.URLUtil.guessFileName(url, null, null)
                val id = startDownload(context, url, fileName)
                viewModel.addDownload(
                    fileName = fileName,
                    url = url,
                    filePath = Environment.DIRECTORY_DOWNLOADS + "/" + fileName,
                    fileSize = 0,
                    systemDownloadId = id
                )
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
            .shadow(16.dp, RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .clickable { onFocus() }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // Window Title Bar
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
                
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "Close Window", modifier = Modifier.size(18.dp))
                }
            }

            // Navigation Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.getWebView(tab.id)?.goBack() },
                    modifier = Modifier.size(32.dp),
                    enabled = tab.canGoBack
                ) {
                    Icon(Icons.Default.ArrowBack, "Back", modifier = Modifier.size(16.dp))
                }

                IconButton(
                    onClick = { viewModel.getWebView(tab.id)?.goForward() },
                    modifier = Modifier.size(32.dp),
                    enabled = tab.canGoForward
                ) {
                    Icon(Icons.Default.ArrowForward, "Forward", modifier = Modifier.size(16.dp))
                }

                SecurityLockIcon(url = tab.url, modifier = Modifier.padding(horizontal = 4.dp))

                var text by remember(tab.url) { mutableStateOf(tab.url.replace("about:blank", "")) }
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f).height(48.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Go),
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

                val jsEnabled = siteSettings?.javascriptEnabled ?: true
                JavaScriptIndicator(isEnabled = jsEnabled, modifier = Modifier.padding(horizontal = 4.dp))

                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.MoreVert, "Menu", modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("📸 Airlock Gallery") },
                            onClick = {
                                viewModel.getWebView(tab.id)?.evaluateJavascript(MediaExtractor.EXTRACT_MEDIA_SCRIPT) { result ->
                                    if (result != null && result != "null") {
                                        try {
                                            val json = if (result.startsWith("\"") && result.endsWith("\"")) {
                                                result.substring(1, result.length - 1)
                                                    .replace("\\\"", "\"")
                                                    .replace("\\\\", "\\")
                                            } else result
                                            val data = Gson().fromJson(json, MediaData::class.java)
                                            viewModel.openAirlockGallery(data)
                                        } catch (e: Exception) { e.printStackTrace() }
                                    }
                                }
                                showMenu = false
                            }
                        )
                        val currentDomain = try { android.net.Uri.parse(tab.url).host ?: "" } catch (e: Exception) { "" }
                        val whitelistedDomains = protectionWhitelist.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        val isWhitelisted = currentDomain.isNotEmpty() && whitelistedDomains.contains(currentDomain)

                        DropdownMenuItem(
                            text = { 
                                val text = if (isWhitelisted) "Unwhitelist Site" else "Whitelist Site"
                                Text(text, color = if (isWhitelisted) Color(0xFF4CAF50) else Color.Unspecified)
                            },
                            onClick = {
                                if (currentDomain.isNotEmpty()) {
                                    viewModel.toggleDomainWhitelist(currentDomain)
                                }
                                showMenu = false
                            },
                            leadingIcon = {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data("file:///c:/Users/Shubh/AndroidStudioProjects/JusBrowse-main/app/src/main/ic_launcher-playstore.png")
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "JusBrowse Logo",
                                    modifier = Modifier.size(24.dp),
                                    colorFilter = if (isWhitelisted) androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFF4CAF50)) else null
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { 
                                val text = if (isBoomerMode) "Disable Boomer Mode" else "Enable Boomer Mode"
                                Text(text, color = if (isBoomerMode) MaterialTheme.colorScheme.error else Color.Unspecified) 
                            },
                            onClick = {
                                viewModel.toggleBoomerMode()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Warning, 
                                    null,
                                    tint = if (isBoomerMode) MaterialTheme.colorScheme.error else LocalContentColor.current
                                )
                            }
                        )
                    }
                }
            }
            
            // WebView Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .dragAndDropTarget(
                        shouldStartDragAndDrop = { event ->
                            event.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN)
                        },
                        target = object : DragAndDropTarget {
                            override fun onStarted(event: DragAndDropEvent) { isDragging = true }
                            override fun onEnded(event: DragAndDropEvent) {
                                isDragging = false
                                isHoveringDropZone = false
                            }
                            override fun onDrop(event: DragAndDropEvent): Boolean {
                                if (isHoveringDropZone) {
                                    val data = event.toAndroidDragEvent().clipData
                                    if (data != null && data.itemCount > 0) {
                                        val url = data.getItemAt(0).text.toString()
                                        if (url.startsWith("http")) {
                                            val validation = DownloadValidator.validateDownload(url, null, null, null, 0)
                                            pendingDownloadUrl = url
                                            pendingDownloadInfo = validation
                                            showDownloadWarning = true
                                            return true
                                        }
                                    }
                                }
                                return false
                            }
                            override fun onMoved(event: DragAndDropEvent) {
                                val x = event.toAndroidDragEvent().x
                                val y = event.toAndroidDragEvent().y
                                val density = context.resources.displayMetrics.density
                                val threshold = 150 * density
                                if (boxSize.width > 0) {
                                    isHoveringDropZone = x > (boxSize.width - threshold) && y < threshold
                                }
                            }
                        }
                    )
                    .onSizeChanged { boxSize = it }
            ) {
                if (tab.url != "about:blank") {
                    AndroidView(
                        factory = { ctx ->
                            val existing = viewModel.getWebView(tab.id)
                            if (existing != null) {
                                (existing.parent as? ViewGroup)?.removeView(existing)
                                existing
                            } else {
                                val wv = WebView(ctx).apply {
                                    com.jusdots.jusbrowse.security.ContainerManager.applyContainer(this, tab.containerId ?: "default")
                                    
                                    setOnLongClickListener { view ->
                                        val hitTestResult = (view as WebView).hitTestResult
                                        when (hitTestResult.type) {
                                            WebView.HitTestResult.SRC_ANCHOR_TYPE,
                                            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE,
                                            WebView.HitTestResult.IMAGE_TYPE -> {
                                                val url = hitTestResult.extra
                                                if (url != null) {
                                                    val mimeType = getMimeType(url)
                                                    if (mimeType.startsWith("image/") || mimeType.startsWith("video/") || mimeType.startsWith("audio/")) {
                                                        viewModel.openAirlockViewer(url, mimeType)
                                                        return@setOnLongClickListener true
                                                    }
                                                    
                                                    val clipData = ClipData.newPlainText("url", url)
                                                    val shadowBuilder = android.view.View.DragShadowBuilder(view)
                                                    isDragging = true
                                                    try {
                                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                                            view.startDragAndDrop(clipData, shadowBuilder, null, android.view.View.DRAG_FLAG_GLOBAL)
                                                        } else {
                                                            view.startDrag(clipData, shadowBuilder, null, 0)
                                                        }
                                                    } catch (e: Exception) {
                                                        isDragging = false
                                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                            val type = when(hitTestResult.type) {
                                                                WebView.HitTestResult.IMAGE_TYPE -> ContextMenuType.IMAGE
                                                                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> ContextMenuType.IMAGE_LINK
                                                                else -> ContextMenuType.LINK
                                                            }
                                                            contextMenuData = ContextMenuData(url = url, type = type, extra = if (type != ContextMenuType.LINK) url else null)
                                                            showContextMenu = true
                                                        }
                                                    }
                                                    true
                                                } else false
                                            }
                                            else -> false
                                        }
                                    }

                                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                    
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.databaseEnabled = true
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = true
                                    settings.mediaPlaybackRequiresUserGesture = !multiMediaPlaybackEnabled
                                    settings.safeBrowsingEnabled = true
                                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                    settings.allowFileAccess = false
                                    settings.allowContentAccess = false
                                    settings.allowFileAccessFromFileURLs = false
                                    settings.allowUniversalAccessFromFileURLs = false
                                    settings.setSupportMultipleWindows(!popupBlockerEnabled)
                                    settings.saveFormData = false
                                    settings.setGeolocationEnabled(false)
                                    
                                    if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                                        androidx.webkit.WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, setOf("https://none.none"))
                                    }

                                    // Advanced Privacy: Unified User-Agent and Metadata setup
                                    val webViewVersion = androidx.webkit.WebViewCompat.getCurrentWebViewPackage(ctx)?.versionName ?: "131.0.0.0"
                                    val targetUA = if (boringEnabled) {
                                        com.jusdots.jusbrowse.security.BoringEngine.getFormattedUserAgent(webViewVersion)
                                    } else if (jusFakeEnabled) {
                                        com.jusdots.jusbrowse.security.FakeModeManager.getUserAgent()
                                            ?: "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.0.0 Mobile Safari/537.36"
                                    } else null

                                    if (targetUA != null) {
                                        settings.userAgentString = targetUA
                                    }

                                    val metadata = if (boringEnabled) {
                                        com.jusdots.jusbrowse.security.BoringEngine.getUserAgentMetadata(webViewVersion)
                                    } else if (jusFakeEnabled) {
                                        com.jusdots.jusbrowse.security.FakeModeManager.getUserAgentMetadata()
                                    } else null

                                    if (metadata != null && androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.USER_AGENT_METADATA)) {
                                        androidx.webkit.WebSettingsCompat.setUserAgentMetadata(settings, metadata)
                                    }

                                    // Advanced Privacy: Document-Start JavaScript Injection
                                    val fpScript = com.jusdots.jusbrowse.security.FakeModeManager.generateFingerprintScript(
                                        webViewVersion = webViewVersion,
                                        defaultEnabled = !jusFakeEnabled && !boringEnabled,
                                        jusFakeEnabled = jusFakeEnabled,
                                        boringEnabled = boringEnabled,
                                        whitelist = protectionWhitelist.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                    )

                                    if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT)) {
                                        androidx.webkit.WebViewCompat.addDocumentStartJavaScript(this, fpScript, setOf("*"))
                                    }

                                    // Surgical Bridge for POST interception and anomaly reporting
                                    addJavascriptInterface(
                                        com.jusdots.jusbrowse.security.SurgicalBridge(tab.id, viewModel),
                                        com.jusdots.jusbrowse.security.FakeModeManager.bridgeNameSurgical
                                    )
                                    addJavascriptInterface(
                                        com.jusdots.jusbrowse.security.FakeModeManager.PrivacyBridge(),
                                        com.jusdots.jusbrowse.security.FakeModeManager.bridgeNamePrivacy
                                    )

                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

                                    if (tab.isPrivate) {
                                        PrivateBrowsingManager.configurePrivateWebView(this)
                                    }
                                    
                                    settings.setSupportZoom(true)
                                    settings.builtInZoomControls = true
                                    settings.displayZoomControls = false
                                    setInitialScale((scale * 100).toInt())
                                    
                                    webChromeClient = SecureWebChromeClient(
                                        onPermissionRequest = { request ->
                                            pendingPermissionRequest = request
                                            showPermissionDialog = true
                                        },
                                        onProgressChanged = { /* can update progress */ },
                                        onTitleChanged = { title -> viewModel.updateTabTitle(tabIndex, title) },
                                        onShowCustomViewCallback = { view, callback ->
                                            fullscreenView = view
                                            fullscreenCallback = callback
                                        },
                                        onHideCustomViewCallback = {
                                            fullscreenView = null
                                            fullscreenCallback = null
                                        }
                                    )
                                    
                                    setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                            val validation = DownloadValidator.validateDownload(url, userAgent, contentDisposition, mimeType, contentLength)
                                            pendingDownloadUrl = url
                                            pendingDownloadInfo = validation
                                            showDownloadWarning = true
                                        }
                                    }
                                    
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                            val url = request?.url?.toString() ?: return null
                                            
                                            // 1. Reset suspicion score on each new request
                                            com.jusdots.jusbrowse.security.SuspicionScorer.reset()

                                            // 2. Ad Block / Tracker Block
                                            if (adBlockEnabled && viewModel.contentBlocker.shouldBlockFast(url)) {
                                                return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
                                            }

                                            // 3. Phase 3: The Surgeon: Strip X-Requested-With (and handle HTTPS-only)
                                            val whitelistedDomains = protectionWhitelist.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                            
                                            val webViewVersion = androidx.webkit.WebViewCompat.getCurrentWebViewPackage(context)?.versionName ?: "133.0.0.0"
                                            val currentUA = when {
                                                tab.isDesktopMode -> com.jusdots.jusbrowse.ui.viewmodel.BrowserViewModel.DESKTOP_USER_AGENT
                                                boringEnabled -> com.jusdots.jusbrowse.security.BoringEngine.getFormattedUserAgent(webViewVersion)
                                                jusFakeEnabled -> com.jusdots.jusbrowse.security.FakeModeManager.getUserAgent()
                                                else -> null
                                            }

                                            val containerId = if (tab.isPrivate) "private_${tab.id}" else tab.containerId
                                            val surgicallyCleanedResponse = NetworkSurgeon.intercept(request, whitelistedDomains, currentUA, httpsOnly, containerId)
                                            if (surgicallyCleanedResponse != null) {
                                                return surgicallyCleanedResponse
                                            }

                                            return super.shouldInterceptRequest(view, request)
                                        }

                                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                            url?.let { currentUrl ->
                                                val shouldDisableJS = currentUrl.startsWith("about:") || currentUrl.startsWith("file://") || currentUrl.startsWith("content://")
                                                if (shouldDisableJS) {
                                                    view?.settings?.javaScriptEnabled = false
                                                } else {
                                                    view?.settings?.javaScriptEnabled = siteSettings?.javascriptEnabled ?: true
                                                }
                                            }
                                            
                                            // Set loading state immediately to prevent stuck spinner if redirect fails
                                            viewModel.updateTabLoadingState(tabIndex, true)
                                            
                                            url?.let { if (it != tab.url) viewModel.navigateToUrlByTabId(tab.id, it) }
                                        }
                                        
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            viewModel.updateTabLoadingState(tabIndex, false)
                                            view?.title?.let { viewModel.updateTabTitle(tabIndex, it) }
                                            viewModel.updateTabNavigationState(tabIndex, view?.canGoBack() == true, view?.canGoForward() == true)
                                            if (cookieBlockerEnabled) {
                                                view?.evaluateJavascript(com.jusdots.jusbrowse.security.CookieDialogBlocker.blockerScript, null)
                                            }
                                            if (viewModel.isBoomerMode.value) {
                                                view?.evaluateJavascript(com.jusdots.jusbrowse.ui.viewmodel.BrowserViewModel.ENABLE_BOOMER_MODE_SCRIPT, null)
                                            } else {
                                                view?.evaluateJavascript(com.jusdots.jusbrowse.ui.viewmodel.BrowserViewModel.DISABLE_BOOMER_MODE_SCRIPT, null)
                                            }
                                        }
                                    }
                                }
                                viewModel.registerWebView(tab.id, wv)
                                val headers = mutableMapOf<String, String>()
                                if (doNotTrackEnabled) headers["DNT"] = "1"
                                wv.loadUrl(tab.url, headers)
                                wv
                            }
                        },
                        update = { webView ->
                            // Handle Boomer
                            if (isBoomerMode) {
                                webView.evaluateJavascript(com.jusdots.jusbrowse.ui.viewmodel.BrowserViewModel.ENABLE_BOOMER_MODE_SCRIPT, null)
                            } else {
                                webView.evaluateJavascript(com.jusdots.jusbrowse.ui.viewmodel.BrowserViewModel.DISABLE_BOOMER_MODE_SCRIPT, null)
                            }
                            
                            // Advanced Privacy: Re-apply UserAgentMetadata if engine state changed
                            val webViewVersion = androidx.webkit.WebViewCompat.getCurrentWebViewPackage(webView.context)?.versionName ?: "145.0.0.0"
                            val metadata = if (boringEnabled) {
                                com.jusdots.jusbrowse.security.BoringEngine.getUserAgentMetadata(webViewVersion)
                            } else if (jusFakeEnabled) {
                                com.jusdots.jusbrowse.security.FakeModeManager.getUserAgentMetadata()
                            } else null

                            if (metadata != null && WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
                                WebSettingsCompat.setUserAgentMetadata(webView.settings, metadata)
                            }
                            
                            // Re-apply UA String
                            val targetUA = if (boringEnabled) {
                                com.jusdots.jusbrowse.security.BoringEngine.getFormattedUserAgent(webViewVersion)
                            } else if (jusFakeEnabled) {
                                com.jusdots.jusbrowse.security.FakeModeManager.getUserAgent() 
                                    ?: "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.0.0 Mobile Safari/537.36"
                            } else null
                            
                            if (targetUA != null && webView.settings.userAgentString != targetUA) {
                                webView.settings.userAgentString = targetUA
                            }
                            
                            siteSettings?.let { settings ->
                                if (webView.settings.javaScriptEnabled != settings.javascriptEnabled) {
                                    webView.settings.javaScriptEnabled = settings.javascriptEnabled
                                }
                                if (webView.settings.domStorageEnabled != settings.domStorageEnabled) {
                                    webView.settings.domStorageEnabled = settings.domStorageEnabled
                                }
                            }
                            if (!webView.hasFocus()) {
                                val targetScale = (scale * 100).toInt()
                                if (webView.settings.textZoom != targetScale) {
                                    webView.settings.textZoom = targetScale
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("New Tab", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                
                if (tab.isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
                }
                DragDropOverlay(isDragging = isDragging, isHovering = isHoveringDropZone, modifier = Modifier.align(Alignment.TopEnd))
            }
        }
        
        // Resize Handle
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

        // Fullscreen overlay (Airlock Style)
        if (fullscreenView != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = { fullscreenView!! },
                    modifier = Modifier.fillMaxSize()
                )
                
                // Top Gradient Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                            )
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp)
                ) {
                    // Close Button (Top-Left, Airlock Style)
                    IconButton(
                        onClick = {
                            fullscreenCallback?.onCustomViewHidden()
                        },
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close, 
                            contentDescription = "Exit Fullscreen", 
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // Scan Result Dialog
        if (viewModel.showScanResultDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.showScanResultDialog = false },
                title = { Text("Scan Result") },
                text = { Text(viewModel.scanResultMessage) },
                confirmButton = {
                    TextButton(onClick = { viewModel.showScanResultDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

private fun startDownload(context: Context, url: String, fileName: String): Long {
    return try {
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
        -1L
    }
}

private fun getMimeType(url: String): String {
    return when {
        url.matches(Regex(".*\\.(jpg|jpeg|png|gif|webp|svg|bmp)($|\\?.*)")) -> "image/*"
        url.matches(Regex(".*\\.(mp4|webm|avi|mov|mkv|flv|m4v)($|\\?.*)")) -> "video/*"
        url.matches(Regex(".*\\.(mp3|wav|ogg|flac|m4a|aac|wma)($|\\?.*)")) -> "audio/*"
        else -> {
            val extension = url.substringAfterLast('.').substringBefore('?')
            android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
        }
    }
}

package com.jusdots.jusbrowse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Masks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.CircleShape
import coil.request.ImageRequest
import com.jusdots.jusbrowse.security.FakeModeManager
import com.jusdots.jusbrowse.security.PersonaPresets
import com.jusdots.jusbrowse.ui.components.FakeModeDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.Image
import coil.compose.AsyncImage
import com.jusdots.jusbrowse.ui.theme.BrowserTheme
import com.jusdots.jusbrowse.ui.theme.AppFont
import com.jusdots.jusbrowse.ui.theme.BackgroundPreset
import com.jusdots.jusbrowse.ui.viewmodel.BrowserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: BrowserViewModel,
    onBack: () -> Unit
) {
    val searchEngine by viewModel.searchEngine.collectAsStateWithLifecycle(initialValue = "DuckDuckGo")
    val javascriptEnabled by viewModel.javascriptEnabled.collectAsStateWithLifecycle(initialValue = true)
    val darkMode by viewModel.darkMode.collectAsStateWithLifecycle(initialValue = false)
    val adBlockEnabled by viewModel.adBlockEnabled.collectAsStateWithLifecycle(initialValue = true)
    val httpsOnly by viewModel.httpsOnly.collectAsStateWithLifecycle(initialValue = false)
    val flagSecureEnabled by viewModel.flagSecureEnabled.collectAsStateWithLifecycle(initialValue = true)
    val doNotTrackEnabled by viewModel.doNotTrackEnabled.collectAsStateWithLifecycle(initialValue = false)
    val cookieBlockerEnabled by viewModel.cookieBlockerEnabled.collectAsStateWithLifecycle(initialValue = false)
    val popupBlockerEnabled by viewModel.popupBlockerEnabled.collectAsStateWithLifecycle(initialValue = true)
    val showTabIcons by viewModel.showTabIcons.collectAsStateWithLifecycle(initialValue = false)
    val vtApiKey by viewModel.virusTotalApiKey.collectAsStateWithLifecycle(initialValue = "")
    val koodousApiKey by viewModel.koodousApiKey.collectAsStateWithLifecycle(initialValue = "")
    val follianMode by viewModel.follianMode.collectAsStateWithLifecycle(initialValue = false)
    val amoledBlackEnabled by viewModel.amoledBlackEnabled.collectAsStateWithLifecycle(initialValue = false)
    val appFont by viewModel.appFont.collectAsStateWithLifecycle(initialValue = "SYSTEM")
    val bottomAddressBarEnabled by viewModel.bottomAddressBarEnabled.collectAsStateWithLifecycle(initialValue = false)
    val wallColorExtracted by viewModel.extractedWallColor.collectAsStateWithLifecycle()

    // Engines
    val defaultEngineEnabled by viewModel.defaultEngineEnabled.collectAsStateWithLifecycle(initialValue = true)
    val jusFakeEngineEnabled by viewModel.jusFakeEngineEnabled.collectAsStateWithLifecycle(initialValue = false)
    val randomiserEngineEnabled by viewModel.randomiserEngineEnabled.collectAsStateWithLifecycle(initialValue = false)
    val multiMediaPlaybackEnabled by viewModel.multiMediaPlaybackEnabled.collectAsStateWithLifecycle(initialValue = false)
    
    // Fake Mode state
    val fakeModeEnabled by FakeModeManager.isEnabled.collectAsStateWithLifecycle()
    val currentPersona by FakeModeManager.currentPersona.collectAsStateWithLifecycle()
    var showFakeModeDialog by remember { mutableStateOf(false) }
    
    // Context for FakeModeManager (App Restart)
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // Fake Mode Dialog
    if (showFakeModeDialog) {
        FakeModeDialog(
            onDismiss = { showFakeModeDialog = false },
            onEnable = { persona ->
                viewModel.activateJusFakeEngine(context, persona)
                showFakeModeDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Search Engine
            Text(
                text = "General",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedTextField(
                    value = searchEngine,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Search Engine") },
                    trailingIcon = { 
                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    listOf("DuckDuckGo", "Google", "Bing").forEach { engine ->
                        DropdownMenuItem(
                            text = { Text(engine) },
                            onClick = { 
                                viewModel.setSearchEngine(engine)
                                expanded = false
                            }
                        )
                    }
                }
            }

            SettingsSwitch(
                title = "Bottom Address Bar",
                subtitle = "Move the address bar and controls to the bottom",
                checked = bottomAddressBarEnabled,
                onCheckedChange = { viewModel.setBottomAddressBarEnabled(it) }
            )

            // Custom Start Page
            val homePage by viewModel.homePage.collectAsStateWithLifecycle(initialValue = "about:blank")
            var homePageText by remember(homePage) { mutableStateOf(homePage) }
            
            OutlinedTextField(
                value = homePageText,
                onValueChange = { homePageText = it },
                label = { Text("Custom Start Page") },
                placeholder = { Text("https://example.com or about:blank") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text("Leave empty or use 'about:blank' for the default new tab page") },
                trailingIcon = {
                    if (homePageText != homePage) {
                        IconButton(onClick = { 
                            val finalUrl = if (homePageText.isBlank()) "about:blank" else homePageText
                            viewModel.setHomePage(finalUrl)
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Save")
                        }
                    }
                }
            )

            HorizontalDivider()

            // Appearance & Customization
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            // Wallpaper Picker
            val wallpaperUri by viewModel.startPageWallpaperUri.collectAsStateWithLifecycle(initialValue = null)
            val blurAmount by viewModel.startPageBlurAmount.collectAsStateWithLifecycle(initialValue = 0f)
            
            val context = LocalContext.current
            val launcher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                uri?.let {
                    // Take persistable permission
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            it,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    viewModel.setStartPageWallpaperUri(it.toString())
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Text(
                    text = "Start Page Wallpaper",
                    style = MaterialTheme.typography.titleSmall
                )
                
                if (wallpaperUri != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(wallpaperUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Wallpaper Preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(blurAmount.dp)
                        )
                        
                        // Overlay sample text to show effect
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "JusBrowse",
                                    style = MaterialTheme.typography.displayMedium, // Smaller than actual for preview
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.setStartPageWallpaperUri(null) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
                        ) {
                            Icon(Icons.Default.Delete, "Remove Wallpaper")
                        }
                    }
                    
                    // Blur Slider
                    Column {
                        Text(
                            text = "Blur: ${blurAmount.toInt()}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Slider(
                            value = blurAmount,
                            onValueChange = { viewModel.setStartPageBlurAmount(it) },
                            valueRange = 0f..25f,
                            steps = 24
                        )
                    }

                } else {
                    OutlinedButton(
                        onClick = { launcher.launch(arrayOf("image/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Image, null, modifier = Modifier.padding(end = 8.dp))
                        Text("Select Wallpaper Image")
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Background Presets
            val backgroundPreset by viewModel.backgroundPreset.collectAsStateWithLifecycle(initialValue = "NONE")
            Text(
                text = "Background Presets",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = "Add animated gradients to your browser interface",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(com.jusdots.jusbrowse.ui.theme.BackgroundPreset.values().size) { index ->
                    val preset = com.jusdots.jusbrowse.ui.theme.BackgroundPreset.values()[index]
                    BackgroundPresetCard(
                        preset = preset,
                        isSelected = backgroundPreset == preset.name,
                        onClick = { viewModel.setBackgroundPreset(preset.name) }
                    )
                }
            }

            HorizontalDivider()

            // Privacy & Security
            Text(
                text = "Privacy & Security",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            // 🛡️ Protection Engines
            Text(
                text = "Fingerprinting Protection Engines",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 8.dp)
            )

            SettingsSwitch(
                title = "Default Engine",
                subtitle = "Standard JusBrowse fingerprinting protection",
                checked = defaultEngineEnabled,
                onCheckedChange = { viewModel.setDefaultEngineEnabled(it) }
            )

            // 🎭 JusFake Engine Card (replaces old Fake Mode card)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (jusFakeEngineEnabled) {
                            viewModel.setJusFakeEngineEnabled(false)
                        } else {
                            showFakeModeDialog = true
                        }
                    },
                colors = CardDefaults.cardColors(
                    containerColor = if (jusFakeEngineEnabled) 
                        Color(0xFF7C4DFF).copy(alpha = 0.1f) 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mask icon
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (jusFakeEngineEnabled) Color(0xFF7C4DFF) 
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🎭", fontSize = 24.sp)
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "JusFake Engine",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        if (jusFakeEngineEnabled && currentPersona != null) {
                            Text(
                                text = "${currentPersona!!.flagEmoji} ${currentPersona!!.displayName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF7C4DFF)
                            )
                        } else {
                            Text(
                                text = "Priv8 + RLEngine (Persona Based)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Switch(
                        checked = jusFakeEngineEnabled,
                        onCheckedChange = {
                            if (it) {
                                showFakeModeDialog = true
                            } else {
                                viewModel.setJusFakeEngineEnabled(false)
                            }
                        }
                    )
                }
            }

            SettingsSwitch(
                title = "Randomiser Engine",
                subtitle = "Brave-style randomized fingerprinting protection",
                checked = randomiserEngineEnabled,
                onCheckedChange = { viewModel.setRandomiserEngineEnabled(it) }
            )

            SettingsSwitch(
                title = "Enable JavaScript",
                subtitle = "Allow sites to run JavaScript",
                checked = javascriptEnabled,
                onCheckedChange = { viewModel.setJavascriptEnabled(it) }
            )

            SettingsSwitch(
                title = "Ad Blocker",
                subtitle = "Block ads and trackers",
                checked = adBlockEnabled,
                onCheckedChange = { viewModel.setAdBlockEnabled(it) }
            )

            SettingsSwitch(
                title = "HTTPS Only Mode",
                subtitle = "Upgrade insecure connections to HTTPS",
                checked = httpsOnly,
                onCheckedChange = { viewModel.setHttpsOnly(it) }
            )

            SettingsSwitch(
                title = "Screenshot Protection",
                subtitle = "Prevent screenshots and hide content in recents",
                checked = flagSecureEnabled,
                onCheckedChange = { viewModel.setFlagSecureEnabled(it) }
            )

            SettingsSwitch(
                title = "Do Not Track",
                subtitle = "Send DNT header to websites",
                checked = doNotTrackEnabled,
                onCheckedChange = { viewModel.setDoNotTrackEnabled(it) }
            )

            SettingsSwitch(
                title = "Block Cookie Pop-ups",
                subtitle = "Hide annoying cookie consent banners",
                checked = cookieBlockerEnabled,
                onCheckedChange = { viewModel.setCookieBlockerEnabled(it) }
            )

            SettingsSwitch(
                title = "Popup Blocker",
                subtitle = "Block pop-ups and window.open() abuse",
                checked = popupBlockerEnabled,
                onCheckedChange = { viewModel.setPopupBlockerEnabled(it) }
            )

            SettingsSwitch(
                title = "Multi-Media Playback",
                subtitle = "Allow multiple tabs to play audio/video simultaneously",
                checked = multiMediaPlaybackEnabled,
                onCheckedChange = { viewModel.setMultiMediaPlaybackEnabled(it) }
            )

            // 🚫 Follian Mode - Hard JS Kill
            SettingsSwitch(
                title = "Follian Mode (JS Off)",
                subtitle = "⚠️ Hard JavaScript kill - sites WILL break",
                checked = follianMode,
                onCheckedChange = { viewModel.setFollianMode(it) }
            )

            HorizontalDivider()

            // Appearance
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            val themePreset by viewModel.themePreset.collectAsStateWithLifecycle(initialValue = "SYSTEM")

            Text(
                text = "Theme Preset",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp)
            )

            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(com.jusdots.jusbrowse.ui.theme.BrowserTheme.values().size) { index ->
                    val theme = com.jusdots.jusbrowse.ui.theme.BrowserTheme.values()[index]
                    ThemePreviewItem(
                        theme = theme,
                        isSelected = themePreset == theme.name,
                        wallColor = wallColorExtracted,
                        onClick = { viewModel.setThemePreset(theme.name) }
                    )
                }
            }


            SettingsSwitch(
                title = "Dark Mode",
                subtitle = "Use dark theme",
                checked = darkMode,
                onCheckedChange = { viewModel.setDarkMode(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Font Selection
            Text(
                text = "Application Font",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Choose a font style for the browser interface (Live Preview)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(AppFont.values().size) { index ->
                    val font = AppFont.values()[index]
                    val isSelected = appFont == font.name
                    
                    Surface(
                        onClick = { viewModel.setAppFont(font.name) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .width(140.dp)
                            .height(100.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Ag",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontFamily = font.fontFamily,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = font.displayName,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontFamily = font.fontFamily,
                                    fontSize = 12.sp
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (darkMode) {
                SettingsSwitch(
                    title = "AMOLED Black Mode",
                    subtitle = "Force pure black backgrounds (OLED only)",
                    checked = amoledBlackEnabled,
                    onCheckedChange = { viewModel.setAmoledBlackEnabled(it) }
                )
            }

            SettingsSwitch(
                title = "Show Tab Icons",
                subtitle = "Display website favicons instead of titles in tab bar",
                checked = showTabIcons,
                onCheckedChange = { viewModel.setShowTabIcons(it) }
            )

            HorizontalDivider()

            // Security API Keys
            Text(
                text = "Security API Keys (BYOK)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "Bring Your Own Key for zero-day malware protection. Your keys are stored only on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = vtApiKey,
                onValueChange = { viewModel.setVirusTotalApiKey(it) },
                label = { Text("VirusTotal API Key") },
                placeholder = { Text("Enter your VT key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
            )

            OutlinedTextField(
                value = koodousApiKey,
                onValueChange = { viewModel.setKoodousApiKey(it) },
                label = { Text("Koodous API Key") },
                placeholder = { Text("Enter your Koodous key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
            )
        }
    }
}

@Composable
fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Switch
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null // Handled by parent toggleable
        )
    }
}

@Composable
fun ThemePreviewItem(
    theme: com.jusdots.jusbrowse.ui.theme.BrowserTheme,
    isSelected: Boolean,
    wallColor: Color? = null,
    onClick: () -> Unit
) {
    val color = when (theme) {
        BrowserTheme.VIVALDI_RED -> Color(0xFFD32F2F)
        BrowserTheme.OCEAN_BLUE -> Color(0xFF0288D1)
        BrowserTheme.FOREST_GREEN -> Color(0xFF388E3C)
        BrowserTheme.MIDNIGHT_PURPLE -> Color(0xFF7B1FA2)
        BrowserTheme.SUNSET_ORANGE -> Color(0xFFF57C00)
        BrowserTheme.ABYSS_BLACK -> Color(0xFF000000)
        BrowserTheme.NORD_ICE -> Color(0xFF5E81AC)
        BrowserTheme.DRACULA -> Color(0xFFBD93F9)
        BrowserTheme.SOLARIZED -> Color(0xFF268BD2)
        BrowserTheme.CYBERPUNK -> Color(0xFFFF00FF)
        BrowserTheme.MINT_FRESH -> Color(0xFF00BFA5)
        BrowserTheme.ROSE_GOLD -> Color(0xFFB76E79)
        BrowserTheme.SYSTEM -> MaterialTheme.colorScheme.primary
        BrowserTheme.MATERIAL_YOU -> Color(0xFF6750A4)
        BrowserTheme.WALL_THEME -> wallColor ?: Color(0xFF3F51B5)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(color)
                .then(
                    if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, androidx.compose.foundation.shape.CircleShape)
                    else Modifier
                )
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = theme.name.replace("_", " ").toLowerCase().capitalize(),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun BackgroundPresetCard(
    preset: BackgroundPreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(80.dp, 60.dp)
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (isSelected) Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(8.dp)
                    )
                    else Modifier
                )
        ) {
            // Mini preview of the background
            com.jusdots.jusbrowse.ui.components.BackgroundRenderer(
                preset = preset,
                modifier = Modifier.fillMaxSize()
            )
            
            // Transparent overlay to capture clicks (WebView steals them otherwise)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onClick)
            )

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            CircleShape
                        )
                        .padding(4.dp)
                        .size(16.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = preset.displayName,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

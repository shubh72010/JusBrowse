package com.jusdots.jusbrowse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.jusdots.jusbrowse.security.FakeModeManager
import com.jusdots.jusbrowse.security.PersonaPresets
import com.jusdots.jusbrowse.ui.components.FakeModeDialog
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
    val showTabIcons by viewModel.showTabIcons.collectAsStateWithLifecycle(initialValue = false)
    
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
                FakeModeManager.enableFakeMode(context, persona)
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

            HorizontalDivider()

            // Privacy & Security
            Text(
                text = "Privacy & Security",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            // 🎭 Fake Mode Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (fakeModeEnabled) {
                            FakeModeManager.disableFakeMode(context)
                        } else {
                            showFakeModeDialog = true
                        }
                    },
                colors = CardDefaults.cardColors(
                    containerColor = if (fakeModeEnabled) 
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
                                if (fakeModeEnabled) Color(0xFF7C4DFF) 
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🎭", fontSize = 24.sp)
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Fake Mode",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        if (fakeModeEnabled && currentPersona != null) {
                            Text(
                                text = "${currentPersona!!.flagEmoji} ${currentPersona!!.displayName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF7C4DFF)
                            )
                        } else {
                            Text(
                                text = "Create a fake browser identity",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Switch(
                        checked = fakeModeEnabled,
                        onCheckedChange = {
                            if (it) {
                                showFakeModeDialog = true
                            } else {
                                FakeModeManager.disableFakeMode(context)
                            }
                        }
                    )
                }
            }

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
                        onClick = { viewModel.setThemePreset(theme.name) }
                    )
                }
            }



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

            SettingsSwitch(
                title = "Show Tab Icons",
                subtitle = "Display website favicons instead of titles in tab bar",
                checked = showTabIcons,
                onCheckedChange = { viewModel.setShowTabIcons(it) }
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
    onClick: () -> Unit
) {
    val color = when (theme) {
        com.jusdots.jusbrowse.ui.theme.BrowserTheme.VIVALDI_RED -> Color(0xFFD32F2F)
        com.jusdots.jusbrowse.ui.theme.BrowserTheme.OCEAN_BLUE -> Color(0xFF0288D1)
        com.jusdots.jusbrowse.ui.theme.BrowserTheme.FOREST_GREEN -> Color(0xFF388E3C)
        com.jusdots.jusbrowse.ui.theme.BrowserTheme.MIDNIGHT_PURPLE -> Color(0xFF7B1FA2)
        com.jusdots.jusbrowse.ui.theme.BrowserTheme.SUNSET_ORANGE -> Color(0xFFF57C00)
        com.jusdots.jusbrowse.ui.theme.BrowserTheme.SYSTEM -> MaterialTheme.colorScheme.primary
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

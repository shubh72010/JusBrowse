package com.jusdots.jusbrowse.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jusdots.jusbrowse.data.models.BrowserTab
import com.jusdots.jusbrowse.ui.viewmodel.BrowserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserToolBar(
    viewModel: BrowserViewModel,
    currentTab: BrowserTab?,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    
    TopAppBar(
        title = {},
        navigationIcon = {},
        actions = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Navigation controls
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { /* Back functionality handled by WebView */ },
                        enabled = currentTab?.canGoBack == true
                    ) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                    
                    IconButton(
                        onClick = { /* Forward functionality handled by WebView */ },
                        enabled = currentTab?.canGoForward == true
                    ) {
                        Icon(Icons.Default.ArrowForward, "Forward")
                    }
                    
                    IconButton(onClick = { viewModel.createNewTab() }) {
                        Icon(Icons.Default.Home, "Home")
                    }
                    
                    // Multi-view toggle
                    IconButton(
                        onClick = { viewModel.toggleMultiViewMode() },
                        enabled = viewModel.tabs.size >= 2
                    ) {
                        Icon(Icons.Default.GridView, "Multi-View")
                    }
                }
                
                // Menu
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "Menu")
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Bookmarks") },
                            onClick = { 
                                viewModel.navigateToScreen(com.jusdots.jusbrowse.ui.screens.Screen.BOOKMARKS)
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Star, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("History") },
                            onClick = { 
                                viewModel.navigateToScreen(com.jusdots.jusbrowse.ui.screens.Screen.HISTORY)
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.DateRange, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Downloads") },
                            onClick = { 
                                viewModel.navigateToScreen(com.jusdots.jusbrowse.ui.screens.Screen.DOWNLOADS)
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Download, null) }
                        )
                        Divider()
                        DropdownMenuItem(
                            text = { Text("New private tab") },
                            onClick = { 
                                viewModel.createNewTab(isPrivate = true)
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.VpnKey, null) } // VpnKey as a placeholder for incognito
                        )
                        DropdownMenuItem(
                            text = { Text("Close all tabs") },
                            onClick = { 
                                viewModel.closeAllTabs()
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Close, null) }
                        )
                        Divider()
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = { 
                                viewModel.navigateToScreen(com.jusdots.jusbrowse.ui.screens.Screen.SETTINGS)
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Settings, null) }
                        )
                    }
                }
            }
        },
        modifier = modifier
    )
}

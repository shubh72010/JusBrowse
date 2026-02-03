package com.jusdots.jusbrowse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jusdots.jusbrowse.data.models.BrowserTab

@Composable
fun BottomTabBar(
    tabs: SnapshotStateList<BrowserTab>,
    activeTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onTabClosed: (Int) -> Unit,
    onNewTab: (String) -> Unit,
    showIcons: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showContainerMenu by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tabs
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEachIndexed { index, tab ->
                    TabChip(
                        tab = tab,
                        isActive = index == activeTabIndex,
                        onClick = { onTabSelected(index) },
                        onClose = { onTabClosed(index) },
                        showIcon = showIcons
                    )
                }
            }
            
            // New Tab Button with Container support
            Box {
                IconButton(
                    onClick = { onNewTab("default") },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Tab",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                // Long-press or separate icon for containers
                IconButton(
                    onClick = { showContainerMenu = true },
                    modifier = Modifier.size(16.dp).align(Alignment.BottomEnd)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "Containers",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                }

                DropdownMenu(
                    expanded = showContainerMenu,
                    onDismissRequest = { showContainerMenu = false }
                ) {
                    com.jusdots.jusbrowse.security.ContainerManager.AVAILABLE_CONTAINERS.forEach { container ->
                        DropdownMenuItem(
                            text = { Text(com.jusdots.jusbrowse.security.ContainerManager.getContainerName(container)) },
                            onClick = {
                                onNewTab(container)
                                showContainerMenu = false
                            },
                             leadingIcon = {
                                Icon(
                                    imageVector = if (container == "default") Icons.Filled.Public else Icons.Filled.Layers,
                                    contentDescription = null
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabChip(
    tab: BrowserTab,
    isActive: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    showIcon: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.CircleShape)
            .clickable(onClick = onClick),
        color = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        tonalElevation = if (isActive) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (tab.isPrivate) {
                Icon(
                    imageVector = Icons.Default.VpnKey,
                    contentDescription = "Private",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            val currentContainerId = tab.containerId ?: "default"
            if (currentContainerId != "default") {
                Surface(
                    color = when(currentContainerId) {
                        "work" -> Color(0xFF4285F4)
                        "personal" -> Color(0xFF34A853)
                        "banking" -> Color(0xFFFBBC05)
                        "sandbox" -> Color(0xFFEA4335)
                        else -> MaterialTheme.colorScheme.secondary
                    },
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.size(8.dp)
                ) {}
            }
            
            if (showIcon) {
                // Show circular icon with first letter of domain
                val domain = try {
                    val url = if (tab.url == "about:blank") "N" else tab.url
                    val uri = android.net.Uri.parse(url)
                    uri.host?.firstOrNull()?.uppercase() ?: "N"
                } catch (e: Exception) {
                    "N"
                }
                
                Surface(
                    modifier = Modifier.size(24.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = domain,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                Text(
                    text = tab.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp)
                )
            }
            
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Tab",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

package com.jusdots.jusbrowse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Layer 10: UI/UX Security Indicators
 * Visual indicators for security status
 */

/**
 * HTTPS lock icon for address bar
 */
@Composable
fun SecurityLockIcon(
    url: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val isSecure = url.startsWith("https://")
    val isInsecure = url.startsWith("http://") && !url.startsWith("https://")
    val isAboutPage = url.startsWith("about:") || url.isEmpty()

    val icon = when {
        isSecure -> Icons.Filled.Lock
        isInsecure -> Icons.Filled.LockOpen
        isAboutPage -> Icons.Outlined.Info
        else -> Icons.Outlined.Warning
    }

    val tint = when {
        isSecure -> Color(0xFF4CAF50) // Green
        isInsecure -> Color(0xFFF44336) // Red
        isAboutPage -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> Color(0xFFFF9800) // Orange
    }

    val contentDescription = when {
        isSecure -> "Secure connection (HTTPS)"
        isInsecure -> "Insecure connection (HTTP)"
        isAboutPage -> "Local page"
        else -> "Unknown security status"
    }

    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier
            .size(18.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    )
}

/**
 * JavaScript status indicator
 */
@Composable
fun JavaScriptIndicator(
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val backgroundColor = if (isEnabled) {
        Color(0xFFFF9800).copy(alpha = 0.2f) // Orange for JS enabled
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (isEnabled) {
        Color(0xFFFF9800)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isEnabled) "JS" else "JS✕",
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}

/**
 * Private/Incognito mode indicator
 */
@Composable
fun PrivateTabIndicator(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                Color(0xFF6B4EE6).copy(alpha = 0.2f),
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.VisibilityOff,
            contentDescription = "Private tab",
            tint = Color(0xFF6B4EE6),
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = "Private",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF6B4EE6)
        )
    }
}

/**
 * Mixed content warning indicator
 */
@Composable
fun MixedContentWarning(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .background(
                Color(0xFFFF9800).copy(alpha = 0.2f),
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = "Mixed content warning",
            tint = Color(0xFFFF9800),
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = "Mixed",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFFF9800)
        )
    }
}

/**
 * Permission chip showing site permissions
 */
@Composable
fun PermissionChip(
    permission: String,
    isGranted: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val icon = when (permission.uppercase()) {
        "GEOLOCATION", "LOCATION" -> if (isGranted) Icons.Filled.LocationOn else Icons.Outlined.LocationOff
        "CAMERA" -> if (isGranted) Icons.Filled.Videocam else Icons.Outlined.VideocamOff
        "MICROPHONE", "MIC" -> if (isGranted) Icons.Filled.Mic else Icons.Outlined.MicOff
        "NOTIFICATIONS" -> if (isGranted) Icons.Filled.Notifications else Icons.Outlined.NotificationsOff
        else -> Icons.Outlined.Security
    }

    val backgroundColor = if (isGranted) {
        Color(0xFF4CAF50).copy(alpha = 0.2f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val tintColor = if (isGranted) {
        Color(0xFF4CAF50)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .background(backgroundColor, CircleShape)
            .padding(4.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "$permission: ${if (isGranted) "Granted" else "Denied"}",
            tint = tintColor,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * Download warning dialog for dangerous files
 */
@Composable
fun DownloadWarningDialog(
    fileName: String,
    warningMessage: String,
    isBlocked: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isSafeDownload = warningMessage.startsWith("Download") && !isBlocked
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (isBlocked) Icons.Filled.Block else if (isSafeDownload) Icons.Filled.Download else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (isBlocked) Color(0xFFF44336) else if (isSafeDownload) MaterialTheme.colorScheme.primary else Color(0xFFFF9800),
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = if (isBlocked) "Download Blocked" else if (isSafeDownload) "Confirm Download" else "Download Warning"
            )
        },
        text = {
            Text(text = warningMessage)
        },
        confirmButton = {
            if (!isBlocked) {
                TextButton(onClick = onConfirm) {
                    Text(if (isSafeDownload) "Download" else "Download Anyway")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isBlocked) "OK" else "Cancel")
            }
        }
    )
}

/**
 * Permission request dialog
 */
@Composable
fun PermissionRequestDialog(
    origin: String,
    permissions: List<String>,
    onGrant: () -> Unit,
    onDeny: () -> Unit
) {
    val permissionText = when {
        permissions.contains("GEOLOCATION") -> "access your location"
        permissions.contains("CAMERA") && permissions.contains("MICROPHONE") -> "access your camera and microphone"
        permissions.contains("CAMERA") -> "access your camera"
        permissions.contains("MICROPHONE") -> "access your microphone"
        else -> "access ${permissions.joinToString(", ")}"
    }

    val icon = when {
        permissions.contains("GEOLOCATION") -> Icons.Filled.LocationOn
        permissions.contains("CAMERA") -> Icons.Filled.Videocam
        permissions.contains("MICROPHONE") -> Icons.Filled.Mic
        else -> Icons.Filled.Security
    }

    AlertDialog(
        onDismissRequest = onDeny,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text("Permission Request")
        },
        text = {
            Column {
                Text(
                    text = origin,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "This site wants to $permissionText"
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onGrant) {
                Text("Allow")
            }
        },
        dismissButton = {
            TextButton(onClick = onDeny) {
                Text("Block")
            }
        }
    )
}

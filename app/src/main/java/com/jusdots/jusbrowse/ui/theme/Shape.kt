package com.jusdots.jusbrowse.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),   // Menus, tooltips
    small = RoundedCornerShape(16.dp),        // Chips, small buttons
    medium = RoundedCornerShape(24.dp),       // Cards, dialogs
    large = RoundedCornerShape(28.dp),        // Large containers
    extraLarge = RoundedCornerShape(32.dp)    // Very large containers
)

package com.jusdots.jusbrowse.ui.theme

import androidx.compose.ui.graphics.Color

enum class BackgroundPreset(
    val displayName: String,
    val colors: List<Color>
) {
    NONE(
        displayName = "None",
        colors = emptyList()
    ),

    BALATRO(
        displayName = "Balatro",
        colors = listOf(
            Color(0xFFDE443B),
            Color(0xFF006BB4),
            Color(0xFF162325)
        )
    ),
    COLOR_BENDS(
        displayName = "Color Bends",
        colors = listOf(
            Color(0xFFB19EEF),
            Color(0xFF67FFD4),
            Color(0xFFFF6B9D),
            Color(0xFFFFC876)
        )
    ),
    DARK_VEIL(
        displayName = "Dark Veil",
        colors = listOf(
            Color(0xFF1A1A2E),
            Color(0xFF16213E),
            Color(0xFF0F3460)
        )
    ),
    DITHER(
        displayName = "Dither",
        colors = listOf(
            Color(0xFF808080),
            Color(0xFF606060),
            Color(0xFF404040)
        )
    ),
    FAULTY_TERMINAL(
        displayName = "Faulty Terminal",
        colors = listOf(
            Color(0xFF00FF00),
            Color(0xFF003300)
        )
    ),
    PIXEL_BLAST(
        displayName = "Pixel Blast",
        colors = listOf(
            Color(0xFFB19EEF),
            Color(0xFF8B7EC8),
            Color(0xFF6B5EA0)
        )
    );

    companion object {
        fun fromName(name: String): BackgroundPreset {
            return values().find { it.name == name } ?: NONE
        }
    }
}

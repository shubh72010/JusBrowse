package com.jusdots.jusbrowse.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

enum class BrowserTheme {
    SYSTEM,
    VIVALDI_RED,
    OCEAN_BLUE,
    FOREST_GREEN,
    MIDNIGHT_PURPLE,
    SUNSET_ORANGE
}

// Vivaldi Red
val VivaldiRedLight = lightColorScheme(
    primary = Color(0xFFD32F2F),
    onPrimary = Color.White,
    secondary = Color(0xFFB71C1C),
    onSecondary = Color.White,
    background = Color(0xFFFFEBEE),
    surface = Color.White
)
val VivaldiRedDark = darkColorScheme(
    primary = Color(0xFFE57373),
    onPrimary = Color.Black,
    secondary = Color(0xFFEF5350),
    background = Color(0xFF2C2C2C),
    surface = Color(0xFF3E3E3E)
)

// Ocean Blue
val OceanBlueLight = lightColorScheme(
    primary = Color(0xFF0288D1),
    onPrimary = Color.White,
    secondary = Color(0xFF0277BD),
    background = Color(0xFFE1F5FE),
    surface = Color.White
)
val OceanBlueDark = darkColorScheme(
    primary = Color(0xFF29B6F6),
    onPrimary = Color.Black,
    secondary = Color(0xFF4FC3F7),
    background = Color(0xFF102027),
    surface = Color(0xFF263238)
)

// Forest Green
val ForestGreenLight = lightColorScheme(
    primary = Color(0xFF388E3C),
    onPrimary = Color.White,
    secondary = Color(0xFF2E7D32),
    background = Color(0xFFE8F5E9),
    surface = Color.White
)
val ForestGreenDark = darkColorScheme(
    primary = Color(0xFF66BB6A),
    onPrimary = Color.Black,
    secondary = Color(0xFF81C784),
    background = Color(0xFF1B5E20),
    surface = Color(0xFF2E7D32)
)

// Midnight Purple
val MidnightPurpleLight = lightColorScheme(
    primary = Color(0xFF7B1FA2),
    onPrimary = Color.White,
    secondary = Color(0xFF6A1B9A),
    background = Color(0xFFF3E5F5),
    surface = Color.White
)
val MidnightPurpleDark = darkColorScheme(
    primary = Color(0xFFAB47BC),
    onPrimary = Color.White,
    secondary = Color(0xFFBA68C8),
    background = Color(0xFF120022),
    surface = Color(0xFF240046)
)

// Sunset Orange
val SunsetOrangeLight = lightColorScheme(
    primary = Color(0xFFF57C00),
    onPrimary = Color.White,
    secondary = Color(0xFFEF6C00),
    background = Color(0xFFFFF3E0),
    surface = Color.White
)
val SunsetOrangeDark = darkColorScheme(
    primary = Color(0xFFFFB74D),
    onPrimary = Color.Black,
    secondary = Color(0xFFFF9800),
    background = Color(0xFF3E2723),
    surface = Color(0xFF4E342E)
)

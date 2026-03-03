package com.jusdots.jusbrowse.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

enum class BrowserTheme {
    SYSTEM,
    MATERIAL_YOU,
    WALL_THEME,
    VIVALDI_RED,
    OCEAN_BLUE,
    FOREST_GREEN,
    MIDNIGHT_PURPLE,
    SUNSET_ORANGE,
    // New themes
    ABYSS_BLACK,
    NORD_ICE,
    DRACULA,
    SOLARIZED,
    CYBERPUNK,
    MINT_FRESH,
    ROSE_GOLD
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

// ============ NEW THEMES ============

// Abyss Black - True AMOLED Black
val AbyssBlackLight = lightColorScheme(
    primary = Color(0xFF212121),
    onPrimary = Color.White,
    secondary = Color(0xFF424242),
    background = Color(0xFFFAFAFA),
    surface = Color.White
)
val AbyssBlackDark = darkColorScheme(
    primary = Color(0xFFBDBDBD),
    onPrimary = Color.Black,
    secondary = Color(0xFF757575),
    background = Color(0xFF000000), // True black
    surface = Color(0xFF121212)
)

// Nord Ice - Cool Nordic Palette
val NordIceLight = lightColorScheme(
    primary = Color(0xFF5E81AC),
    onPrimary = Color.White,
    secondary = Color(0xFF81A1C1),
    background = Color(0xFFECEFF4),
    surface = Color(0xFFE5E9F0)
)
val NordIceDark = darkColorScheme(
    primary = Color(0xFF88C0D0),
    onPrimary = Color(0xFF2E3440),
    secondary = Color(0xFF81A1C1),
    background = Color(0xFF2E3440),
    surface = Color(0xFF3B4252)
)

// Dracula - Classic Hacker Theme
val DraculaLight = lightColorScheme(
    primary = Color(0xFFBD93F9),
    onPrimary = Color(0xFF282A36),
    secondary = Color(0xFFFF79C6),
    background = Color(0xFFF8F8F2),
    surface = Color.White
)
val DraculaDark = darkColorScheme(
    primary = Color(0xFFBD93F9),
    onPrimary = Color(0xFF282A36),
    secondary = Color(0xFFFF79C6),
    background = Color(0xFF282A36),
    surface = Color(0xFF44475A)
)

// Solarized
val SolarizedLight = lightColorScheme(
    primary = Color(0xFF268BD2),
    onPrimary = Color(0xFFFDF6E3),
    secondary = Color(0xFF2AA198),
    background = Color(0xFFFDF6E3),
    surface = Color(0xFFEEE8D5)
)
val SolarizedDark = darkColorScheme(
    primary = Color(0xFF268BD2),
    onPrimary = Color(0xFF002B36),
    secondary = Color(0xFF2AA198),
    background = Color(0xFF002B36),
    surface = Color(0xFF073642)
)

// Cyberpunk - Neon Future
val CyberpunkLight = lightColorScheme(
    primary = Color(0xFFFF00FF),
    onPrimary = Color.Black,
    secondary = Color(0xFFFFFF00),
    background = Color(0xFFF5F5F5),
    surface = Color.White
)
val CyberpunkDark = darkColorScheme(
    primary = Color(0xFFFF00FF),
    onPrimary = Color.Black,
    secondary = Color(0xFFFFFF00),
    background = Color(0xFF0D0D0D),
    surface = Color(0xFF1A1A2E)
)

// Mint Fresh - Clean Green
val MintFreshLight = lightColorScheme(
    primary = Color(0xFF00BFA5),
    onPrimary = Color.White,
    secondary = Color(0xFF1DE9B6),
    background = Color(0xFFE0F2F1),
    surface = Color.White
)
val MintFreshDark = darkColorScheme(
    primary = Color(0xFF64FFDA),
    onPrimary = Color(0xFF003D33),
    secondary = Color(0xFF1DE9B6),
    background = Color(0xFF004D40),
    surface = Color(0xFF00695C)
)

// Rose Gold - Warm Elegant
val RoseGoldLight = lightColorScheme(
    primary = Color(0xFFB76E79),
    onPrimary = Color.White,
    secondary = Color(0xFFD4A5A5),
    background = Color(0xFFFFF0F0),
    surface = Color.White
)
val RoseGoldDark = darkColorScheme(
    primary = Color(0xFFE8B4B8),
    onPrimary = Color(0xFF3D2B2B),
    secondary = Color(0xFFD4A5A5),
    background = Color(0xFF2D1F1F),
    surface = Color(0xFF3D2B2B)
)


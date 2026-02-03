package com.jusdots.jusbrowse.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = BrowserPrimary,
    secondary = BrowserSecondary,
    background = BrowserBackground,
    surface = BrowserSurface,
    surfaceVariant = BrowserSurfaceVariant
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun JusBrowse2Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    themePreset: String = "SYSTEM",
    amoledBlackEnabled: Boolean = false,
    wallColor: Color? = null,
    appFont: String = "SYSTEM",
    content: @Composable () -> Unit
) {
    val preset = try {
        BrowserTheme.valueOf(themePreset)
    } catch (e: Exception) {
        BrowserTheme.SYSTEM
    }

    val colorScheme = when (preset) {
        BrowserTheme.WALL_THEME -> {
            val seed = wallColor ?: BrowserPrimary
            if (darkTheme) {
                darkColorScheme(
                    primary = seed,
                    secondary = seed.copy(alpha = 0.8f),
                    tertiary = seed.copy(alpha = 0.6f),
                    background = Color(0xFF1A1A1A),
                    surface = Color(0xFF242424)
                )
            } else {
                lightColorScheme(
                    primary = seed,
                    secondary = seed.copy(alpha = 0.8f),
                    tertiary = seed.copy(alpha = 0.6f),
                    background = seed.copy(alpha = 0.05f),
                    surface = Color.White
                )
            }
        }
        BrowserTheme.MATERIAL_YOU -> {
            // Material You dynamic colors (Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                // Fallback for older Android versions
                if (darkTheme) DarkColorScheme else LightColorScheme
            }
        }
        BrowserTheme.VIVALDI_RED -> if (darkTheme) VivaldiRedDark else VivaldiRedLight
        BrowserTheme.OCEAN_BLUE -> if (darkTheme) OceanBlueDark else OceanBlueLight
        BrowserTheme.FOREST_GREEN -> if (darkTheme) ForestGreenDark else ForestGreenLight
        BrowserTheme.MIDNIGHT_PURPLE -> if (darkTheme) MidnightPurpleDark else MidnightPurpleLight
        BrowserTheme.SUNSET_ORANGE -> if (darkTheme) SunsetOrangeDark else SunsetOrangeLight
        // New themes
        BrowserTheme.ABYSS_BLACK -> if (darkTheme) AbyssBlackDark else AbyssBlackLight
        BrowserTheme.NORD_ICE -> if (darkTheme) NordIceDark else NordIceLight
        BrowserTheme.DRACULA -> if (darkTheme) DraculaDark else DraculaLight
        BrowserTheme.SOLARIZED -> if (darkTheme) SolarizedDark else SolarizedLight
        BrowserTheme.CYBERPUNK -> if (darkTheme) CyberpunkDark else CyberpunkLight
        BrowserTheme.MINT_FRESH -> if (darkTheme) MintFreshDark else MintFreshLight
        BrowserTheme.ROSE_GOLD -> if (darkTheme) RoseGoldDark else RoseGoldLight
        BrowserTheme.SYSTEM -> {
            when {
                dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    val context = LocalContext.current
                    if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                }
                darkTheme -> DarkColorScheme
                else -> LightColorScheme
            }
        }
    }

    val finalColorScheme = if (amoledBlackEnabled && darkTheme) {
        colorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color.Black,
            surfaceContainer = Color.Black,
            surfaceContainerLow = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerHigh = Color.Black,
            surfaceContainerHighest = Color.Black
        )
    } else {
        colorScheme
    }

    val selectedAppFont = try {
        AppFont.valueOf(appFont)
    } catch (e: Exception) {
        AppFont.SYSTEM
    }

    MaterialTheme(
        colorScheme = finalColorScheme,
        typography = getTypography(selectedAppFont.fontFamily),
        shapes = Shapes,
        content = content
    )
}
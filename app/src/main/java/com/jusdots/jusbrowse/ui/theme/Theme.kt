package com.jusdots.jusbrowse.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
    content: @Composable () -> Unit
) {
    val preset = try {
        BrowserTheme.valueOf(themePreset)
    } catch (e: Exception) {
        BrowserTheme.SYSTEM
    }

    val colorScheme = when (preset) {
        BrowserTheme.VIVALDI_RED -> if (darkTheme) VivaldiRedDark else VivaldiRedLight
        BrowserTheme.OCEAN_BLUE -> if (darkTheme) OceanBlueDark else OceanBlueLight
        BrowserTheme.FOREST_GREEN -> if (darkTheme) ForestGreenDark else ForestGreenLight
        BrowserTheme.MIDNIGHT_PURPLE -> if (darkTheme) MidnightPurpleDark else MidnightPurpleLight
        BrowserTheme.SUNSET_ORANGE -> if (darkTheme) SunsetOrangeDark else SunsetOrangeLight
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
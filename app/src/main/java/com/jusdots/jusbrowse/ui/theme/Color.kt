package com.jusdots.jusbrowse.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Custom Browser Colors
val BrowserPrimary = Color(0xFF8B5CF6)     // Purple
val BrowserSecondary = Color(0xFFEC4899)   // Pink
val BrowserBackground = Color(0xFF0A0B0E)
val BrowserSurface = Color(0xFF13151A)
val BrowserSurfaceVariant = Color(0xFF1A1D26)

// ── Glassmorphism Design Tokens ──────────────────────────────────────────────
// Glass surfaces: translucent background layers for pill, cards, tab bar
val GlassSurface       = Color(0xFF1C1F2E)   // deep navy-purple tint for glass fill
val GlassBorderLight   = Color(0x33FFFFFF)   // 20% white — top/left highlight edge
val GlassBorderDark    = Color(0x14FFFFFF)   // 8% white — bottom/right shadow edge

// Glow halos: used in drawBehind bloom effects and shadow replacements
val GlowPrimary        = Color(0x268B5CF6)   // 15% purple — pill glow bloom
val GlowSecondary      = Color(0x14EC4899)   // 8% pink — secondary accent bloom

// Semantic security colors (consistent across SecurityIndicators + pill bar)
val SecureGreen        = Color(0xFF4CAF50)   // HTTPS lock, safe downloads
val InsecureRed        = Color(0xFFEF5350)   // HTTP indicator, blocked content
val WarningAmber       = Color(0xFFFF9800)   // Mixed content, caution signals
val PrivatePurple      = Color(0xFF7C4DFF)   // Private/incognito tab indicators

// Container alphas for glass chip fills
val SecureGreenContainer   = Color(0x1A4CAF50)  // 10% green
val InsecureRedContainer   = Color(0x1AEF5350)  // 10% red
val PrivatePurpleContainer = Color(0x1A7C4DFF)  // 10% purple
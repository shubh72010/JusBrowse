package com.jusdots.jusbrowse.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import com.jusdots.jusbrowse.ui.theme.BackgroundPreset
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun BackgroundRenderer(preset: BackgroundPreset, modifier: Modifier = Modifier) {
    when (preset) {
        BackgroundPreset.NONE -> Unit
        else -> {
            // Use WebGL for exact shader rendering
            WebGLBackgroundView(
                preset = preset,
                modifier = modifier
            )
        }
    }
}

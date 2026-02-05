package com.jusdots.jusbrowse.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.jusdots.jusbrowse.data.models.Sticker
import kotlinx.coroutines.launch
@Composable
fun StickerPeel(
    sticker: Sticker,
    onPositionChange: (Offset) -> Unit,
    onClick: () -> Unit,
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val corountineScope = rememberCoroutineScope()
    var dragOffset by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var touchPoint by remember { mutableStateOf(Offset.Zero) }
    
    // GSAP-style dynamic rotation (tilts when moving fast horizontally)
    val dynamicRotation = remember { Animatable(0f) }

    val peel by animateFloatAsState(
        targetValue = if (isDragging) dragOffset.coerceIn(0f, 0.45f) else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "peel"
    )

    val painter = rememberAsyncImagePainter(
        ImageRequest.Builder(LocalContext.current)
            .data(sticker.imageUri)
            .crossfade(true)
            .build()
    )

    Box(
        modifier = modifier
            .size(512.dp)
            .graphicsLayer {
                // Combine original sticker rotation with dynamic movement tilt
                rotationZ = sticker.rotation + dynamicRotation.value
                scaleX = sticker.scale
                scaleY = sticker.scale
                shadowElevation = 8.dp.toPx()
                shape = RoundedCornerShape(16.dp)
                clip = false
            }
            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(16.dp))
            .pointerInput(sticker.id) {
                val velocityTracker = VelocityTracker()
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        touchPoint = offset
                        corountineScope.launch {
                            dynamicRotation.stop()
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                        dragOffset = 0f
                        
                        // GSAP-style Inertia/Fling
                        val velocity = velocityTracker.calculateVelocity()
                        corountineScope.launch {
                            // Reset dynamic rotation smoothly
                            dynamicRotation.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                        }
                        
                        // Perform the fling if velocity is high enough
                        if (kotlin.math.abs(velocity.x) > 100 || kotlin.math.abs(velocity.y) > 100) {
                            corountineScope.launch {
                                var lastValue = Offset.Zero
                                val decayX = exponentialDecay<Float>(frictionMultiplier = 1.0f)
                                val decayY = exponentialDecay<Float>(frictionMultiplier = 1.0f)
                                
                                val animX = Animatable(0f)
                                val animY = Animatable(0f)
                                
                                launch { animX.animateDecay(velocity.x / 10f, decayX) {
                                    val delta = Offset(value - lastValue.x, 0f)
                                    onPositionChange(delta)
                                    lastValue = lastValue.copy(x = value)
                                }}
                                launch { animY.animateDecay(velocity.y / 10f, decayY) {
                                    val delta = Offset(0f, value - lastValue.y)
                                    onPositionChange(delta)
                                    lastValue = lastValue.copy(y = value)
                                }}
                            }.invokeOnCompletion { onDragEnd() }
                        } else {
                            onDragEnd()
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                        dragOffset = 0f
                        corountineScope.launch { dynamicRotation.animateTo(0f) }
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        touchPoint = change.position
                        onPositionChange(drag)
                        
                        // Track velocity for the fling
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        
                        // Update drag-peel visual
                        dragOffset += drag.y / size.height
                        
                        // Update dynamic tilt based on horizontal drag speed
                        corountineScope.launch {
                            val targetTilt = (drag.x / 10f).coerceIn(-15f, 15f)
                            dynamicRotation.animateTo(targetTilt, spring(stiffness = Spring.StiffnessHigh))
                        }
                    }
                )
            }
            .pointerInput(sticker.id) {
                detectTapGestures { if (!isDragging) onClick() }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val peelY = h * (1f - peel)

            // 1. Main Sticker Body
            clipPath(Path().apply {
                moveTo(0f, peelY)
                lineTo(w, peelY)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }) {
                with(painter) { draw(size = size) }
                
                // Specular Shine (Main)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.15f), Color.Transparent),
                        center = touchPoint,
                        radius = w * 0.8f
                    ),
                    blendMode = BlendMode.Overlay
                )
            }

            // 2. Peel Flap
            if (peel > 0f) {
                val flapPath = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w, 0f)
                    lineTo(w, peelY)
                    lineTo(0f, peelY)
                    close()
                }

                withTransform({
                    translate(0f, peelY)
                    scale(1f, -1f, pivot = Offset(w / 2f, 0f))
                }) {
                    clipPath(flapPath) {
                        // Flap Surface (Back of sticker)
                        drawRect(color = Color(0xFFFBFBFB))
                        
                        with(painter) {
                            draw(size = size, alpha = 0.05f, colorFilter = ColorFilter.tint(Color.Black, BlendMode.DstIn))
                        }

                        // Specular Shine (Flap)
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.White.copy(alpha = 0.25f), Color.Transparent),
                                center = Offset(touchPoint.x, h - touchPoint.y),
                                radius = w * 0.6f
                            ),
                            blendMode = BlendMode.Overlay
                        )
                    }
                }

                // Visual details: Highlight & Inner Shadow
                drawLine(color = Color.White.copy(alpha = 0.7f), start = Offset(0f, peelY), end = Offset(w, peelY), strokeWidth = 2.dp.toPx())
                drawRect(
                    brush = Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.1f), Color.Transparent), startY = peelY, endY = peelY + 20.dp.toPx()),
                    size = size.copy(height = 20.dp.toPx()),
                    topLeft = Offset(0f, peelY)
                )
            }
        }
    }
}

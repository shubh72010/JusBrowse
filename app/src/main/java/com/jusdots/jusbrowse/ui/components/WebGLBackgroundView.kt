package com.jusdots.jusbrowse.ui.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.jusdots.jusbrowse.ui.theme.BackgroundPreset

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebGLBackgroundView(
    preset: BackgroundPreset,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    // Transparent background
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                }
                
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                
                // Load the appropriate shader HTML
                val htmlFile = when (preset) {

                    BackgroundPreset.BALATRO -> "shaders/balatro.html"
                    BackgroundPreset.COLOR_BENDS -> "shaders/colorbends.html"
                    BackgroundPreset.DARK_VEIL -> "shaders/darkveil.html"
                    BackgroundPreset.DITHER -> "shaders/dither.html"
                    BackgroundPreset.FAULTY_TERMINAL -> "shaders/terminal.html"
                    BackgroundPreset.PIXEL_BLAST -> "shaders/pixelblast.html"
                    BackgroundPreset.NONE -> return@apply
                }
                
                loadUrl("file:///android_asset/$htmlFile")
            }
        },
        update = { webView ->
            // Reload if preset changes
            val htmlFile = when (preset) {

                BackgroundPreset.BALATRO -> "shaders/balatro.html"
                BackgroundPreset.COLOR_BENDS -> "shaders/colorbends.html"
                BackgroundPreset.DARK_VEIL -> "shaders/darkveil.html"
                BackgroundPreset.DITHER -> "shaders/dither.html"
                BackgroundPreset.FAULTY_TERMINAL -> "shaders/terminal.html"
                BackgroundPreset.PIXEL_BLAST -> "shaders/pixelblast.html"
                BackgroundPreset.NONE -> return@AndroidView
            }
            
            if (webView.url != "file:///android_asset/$htmlFile") {
                webView.loadUrl("file:///android_asset/$htmlFile")
            }
        },
        modifier = modifier
    )
}

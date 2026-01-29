package com.jusdots.jusbrowse

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jusdots.jusbrowse.ui.screens.BrowserScreen
import com.jusdots.jusbrowse.ui.theme.JusBrowse2Theme
import com.jusdots.jusbrowse.ui.viewmodel.BrowserViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure FakeModeManager state is loaded
        com.jusdots.jusbrowse.security.FakeModeManager.init(this)
        
        enableEdgeToEdge()
        
        setContent {
            val viewModel: BrowserViewModel = viewModel()
            val themePreset by viewModel.themePreset.collectAsStateWithLifecycle(initialValue = "SYSTEM")
            val darkMode by viewModel.darkMode.collectAsStateWithLifecycle(initialValue = true) // defaulting to true or system?
            // Note: darkMode is currently a preference. If we want to respect system, we might need a "SYSTEM" mode for dark mode too.
            // For now, let's assume the preference dictates it.

            JusBrowse2Theme(
                darkTheme = darkMode,
                themePreset = themePreset
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val flagSecureEnabled by viewModel.flagSecureEnabled.collectAsStateWithLifecycle(initialValue = true)

                    LaunchedEffect(flagSecureEnabled) {
                        if (flagSecureEnabled) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        }
                    }

                    BrowserScreen(viewModel = viewModel)
                }
            }
        }
    }
}
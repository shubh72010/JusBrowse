package com.jusdots.jusbrowse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.jusdots.jusbrowse.data.models.BrowserTab
import com.jusdots.jusbrowse.ui.viewmodel.BrowserViewModel

@Composable
fun FreeformWorkspace(
    viewModel: BrowserViewModel,
    tabs: List<BrowserTab>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Optional: Pan workspace itself? For now, just a canvas.
    ) {
        tabs.forEachIndexed { index, tab ->
            key(tab.id) {
                TabWindow(
                    viewModel = viewModel,
                    tab = tab,
                    tabIndex = index,
                    onClose = { viewModel.closeTab(index) },
                    onFocus = { viewModel.bringToFront(tab.id) }
                )
            }
        }
    }
}

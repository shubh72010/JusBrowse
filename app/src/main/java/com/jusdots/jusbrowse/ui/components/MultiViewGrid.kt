package com.jusdots.jusbrowse.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jusdots.jusbrowse.data.models.BrowserTab
import com.jusdots.jusbrowse.ui.viewmodel.BrowserViewModel

@Composable
fun MultiViewGrid(
    viewModel: BrowserViewModel,
    tabs: List<BrowserTab>,
    activeTabIndex: Int,
    modifier: Modifier = Modifier
) {
    // Calculate grid layout based on number of tabs
    val gridLayout = when (tabs.size) {
        1 -> 1 to 1    // 1x1 (shouldn't normally show multi-view with 1 tab)
        2 -> 2 to 1    // 2x1 horizontal split
        3 -> 2 to 2    // 2x2 with one empty
        else -> 2 to 2 // 2x2 full grid (max 4 tabs)
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // First row
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.take(gridLayout.first).forEachIndexed { index, tab ->
                GridCell(
                    viewModel = viewModel,
                    tab = tab,
                    tabIndex = index,
                    isActive = index == activeTabIndex,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Second row (if needed)
        if (gridLayout.second > 1 && tabs.size > 2) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tabs.drop(gridLayout.first).take(2).forEachIndexed { index, tab ->
                    GridCell(
                        viewModel = viewModel,
                        tab = tab,
                        tabIndex = index + gridLayout.first,
                        isActive = (index + gridLayout.first) == activeTabIndex,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Fill empty space if only 3 tabs
                if (tabs.size == 3) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun GridCell(
    viewModel: BrowserViewModel,
    tab: BrowserTab,
    tabIndex: Int,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isActive) 3.dp else 1.dp,
                color = if (isActive) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        // Render the WebView for each tab
        AddressBarWithWebView(
            viewModel = viewModel,
            tabIndex = tabIndex,
            modifier = Modifier.fillMaxSize()
        )
    }
}

package com.jusdots.jusbrowse.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

data class MediaData(
    val images: List<MediaItem> = emptyList(),
    val videos: List<MediaItem> = emptyList(),
    val audio: List<MediaItem> = emptyList()
) {
    fun isEmpty() = images.isEmpty() && videos.isEmpty() && audio.isEmpty()
}

data class MediaItem(
    val url: String,
    val title: String = "",
    val metadata: String = ""
)

@Composable
fun AirlockGallery(
    mediaData: MediaData,
    onMediaClick: (url: String, mimeType: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    
    BackHandler {
        onClose()
    }
    
    val tabs = listOf(
        "Images" to mediaData.images.size,
        "Videos" to mediaData.videos.size,
        "Audio" to mediaData.audio.size
    )
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Airlock Gallery",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 22.sp,
                style = MaterialTheme.typography.headlineMedium
            )
            
            IconButton(
                onClick = onClose,
                modifier = Modifier.background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Gallery",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Empty state
        if (mediaData.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "⛱️",
                        fontSize = 80.sp
                    )
                    Text(
                        text = "The airlock is empty",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
            return
        }
        
        // Tab row
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 16.dp,
            divider = {},
            indicator = { tabPositions ->
                if (selectedTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        ) {
            tabs.forEachIndexed { index, pair ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = pair.first,
                                style = if (selectedTab == index) 
                                    MaterialTheme.typography.titleSmall 
                                else 
                                    MaterialTheme.typography.bodyMedium
                            )
                            if (pair.second > 0) {
                                Box(
                                    modifier = Modifier
                                        .padding(start = 6.dp)
                                        .background(
                                            if (selectedTab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            CircleShape
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = pair.second.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selectedTab == index) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
        
        // Content based on selected tab with animation
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally { width -> width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> -width } + fadeOut()
                } else {
                    slideInHorizontally { width -> -width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> width } + fadeOut()
                }.using(SizeTransform(clip = false))
            },
            label = "TabContentAnimation"
        ) { targetTab ->
            when (targetTab) {
                0 -> ImageGrid(
                    images = mediaData.images,
                    onImageClick = { onMediaClick(it, "image/*") }
                )
                1 -> VideoList(
                    videos = mediaData.videos,
                    onVideoClick = { onMediaClick(it, "video/*") }
                )
                2 -> AudioList(
                    audio = mediaData.audio,
                    onAudioClick = { onMediaClick(it, "audio/*") }
                )
            }
        }
    }
}

@Composable
private fun ImageGrid(
    images: List<MediaItem>,
    onImageClick: (String) -> Unit
) {
    if (images.isEmpty()) {
        EmptyMediaState("The gallery is empty")
        return
    }
    
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp
    ) {
        items(images) { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onImageClick(item.url) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.url)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(if (item.url.length % 2 == 0) 0.8f else 1.2f),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
private fun VideoList(
    videos: List<MediaItem>,
    onVideoClick: (String) -> Unit
) {
    if (videos.isEmpty()) {
        EmptyMediaState("No videos in the airlock")
        return
    }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(videos) { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onVideoClick(item.url) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SlowMotionVideo,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = item.title.ifEmpty { "Video Content" },
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.metadata.ifEmpty { "Media discovered by JusBrowse" },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "Play",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioList(
    audio: List<MediaItem>,
    onAudioClick: (String) -> Unit
) {
    if (audio.isEmpty()) {
        EmptyMediaState("Silence in the airlock")
        return
    }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(audio) { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAudioClick(item.url) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, 
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Audiotrack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .size(40.dp)
                            .padding(8.dp)
                    )
                    
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = item.title.ifEmpty { "Audio Stream" },
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.metadata.isNotEmpty()) {
                            Text(
                                text = item.metadata,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyMediaState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}


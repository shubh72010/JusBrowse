package com.jusdots.jusbrowse.ui.components

data class MediaItem(
    val url: String,
    val title: String = "",
    val metadata: String = ""
)

data class MediaData(
    val images: List<MediaItem> = emptyList(),
    val videos: List<MediaItem> = emptyList(),
    val audio: List<MediaItem> = emptyList()
) {
    fun isEmpty() = images.isEmpty() && videos.isEmpty() && audio.isEmpty()
}

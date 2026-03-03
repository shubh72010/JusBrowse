package com.jusdots.jusbrowse.data.models

data class TrackerInfo(
    val domain: String,
    val timestamp: Long = System.currentTimeMillis(),
    val blocked: Boolean = true
)

package com.jusdots.jusbrowse.data.models

import java.util.UUID

data class Shortcut(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val iconUrl: String? = null // For favicon (optional for now)
)

package com.jusdots.jusbrowse.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Sticker(
    val id: String,
    val imageUri: String,
    val link: String? = null,
    val x: Float, // 0-1 relative to screen width
    val y: Float, // 0-1 relative to screen height
    val rotation: Float = 0f,
    val scale: Float = 1f
) : Parcelable

package com.jusdots.jusbrowse.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Sticker(
    val id: String,
    val imageUri: String,
    val x: Float, // 0-1 relative to screen width
    val y: Float, // 0-1 relative to screen height
    val widthDp: Float = 512f,
    val heightDp: Float = 512f,
    val rotation: Float = 0f,
    val link: String? = null
) : Parcelable

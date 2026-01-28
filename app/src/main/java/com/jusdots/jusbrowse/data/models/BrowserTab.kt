package com.jusdots.jusbrowse.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class BrowserTab(
    val id: String,
    val url: String = "about:blank",
    val title: String = "New Tab",
    val isLoading: Boolean = false,
    val progress: Float = 0f,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val favicon: String? = null,
    val isPrivate: Boolean = false
) : Parcelable

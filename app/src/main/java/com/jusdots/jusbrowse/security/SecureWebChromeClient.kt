package com.jusdots.jusbrowse.security

import android.Manifest
import android.content.pm.PackageManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Layer 8: API & Sensor Access Control
 * Secure WebChromeClient that handles permission requests with explicit user prompts
 */
class SecureWebChromeClient(
    private val onPermissionRequest: (PermissionRequestInfo) -> Unit,
    private val onProgressChanged: (Int) -> Unit = {},
    private val onTitleChanged: (String) -> Unit = {}
) : WebChromeClient() {

    data class PermissionRequestInfo(
        val origin: String,
        val permissions: List<String>,
        val callback: (granted: Boolean) -> Unit
    )

    // Pending permission requests
    private val _pendingGeolocationRequest = MutableStateFlow<GeolocationRequestData?>(null)
    val pendingGeolocationRequest: StateFlow<GeolocationRequestData?> = _pendingGeolocationRequest.asStateFlow()

    data class GeolocationRequestData(
        val origin: String,
        val callback: GeolocationPermissions.Callback
    )

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressChanged(newProgress)
    }

    override fun onReceivedTitle(view: WebView?, title: String?) {
        super.onReceivedTitle(view, title)
        title?.let { onTitleChanged(it) }
    }

    /**
     * Handle geolocation permission requests with explicit user prompt
     */
    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback?
    ) {
        if (origin == null || callback == null) {
            callback?.invoke(origin, false, false)
            return
        }

        // Store pending request and trigger UI prompt
        _pendingGeolocationRequest.value = GeolocationRequestData(origin, callback)
        
        onPermissionRequest(
            PermissionRequestInfo(
                origin = origin,
                permissions = listOf("GEOLOCATION"),
                callback = { granted ->
                    callback.invoke(origin, granted, false) // Don't retain for now
                    _pendingGeolocationRequest.value = null
                }
            )
        )
    }

    override fun onGeolocationPermissionsHidePrompt() {
        super.onGeolocationPermissionsHidePrompt()
        _pendingGeolocationRequest.value = null
    }

    /**
     * Handle camera/microphone permission requests with explicit user prompt
     */
    override fun onPermissionRequest(request: PermissionRequest?) {
        request ?: return

        val origin = request.origin?.toString() ?: "Unknown"
        val resources = request.resources.toList()
        
        val permissionNames = resources.mapNotNull { resource ->
            when (resource) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> "CAMERA"
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> "MICROPHONE"
                PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> "PROTECTED_MEDIA"
                PermissionRequest.RESOURCE_MIDI_SYSEX -> "MIDI"
                else -> null
            }
        }

        if (permissionNames.isEmpty()) {
            request.deny()
            return
        }

        onPermissionRequest(
            PermissionRequestInfo(
                origin = origin,
                permissions = permissionNames,
                callback = { granted ->
                    if (granted) {
                        request.grant(request.resources)
                    } else {
                        request.deny()
                    }
                }
            )
        )
    }

    override fun onPermissionRequestCanceled(request: PermissionRequest?) {
        super.onPermissionRequestCanceled(request)
        // Cleanup if needed
    }

    /**
     * Block window.open() popups by default (Layer 3)
     */
    override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: android.os.Message?
    ): Boolean {
        // Only allow popups initiated by user gesture
        if (!isUserGesture) {
            return false
        }
        // For now, block all popups. Can be enhanced to check SiteSettings.popupsAllowed
        return false
    }

    /**
     * Handle file selection for uploads (Layer 1)
     */
    var onShowFileChooser: ((WebView?, android.webkit.ValueCallback<Array<android.net.Uri>>?, FileChooserParams?) -> Boolean)? = null

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: android.webkit.ValueCallback<Array<android.net.Uri>>?,
        fileChooserParams: FileChooserParams?
    ): Boolean {
        return onShowFileChooser?.invoke(webView, filePathCallback, fileChooserParams) ?: super.onShowFileChooser(webView, filePathCallback, fileChooserParams)
    }
}

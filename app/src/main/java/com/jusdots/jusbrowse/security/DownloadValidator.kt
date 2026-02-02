package com.jusdots.jusbrowse.security

import android.webkit.MimeTypeMap
import android.webkit.URLUtil

/**
 * Layer 9: Download & File Safety
 * Validates downloads and blocks dangerous file types
 */
object DownloadValidator {

    /**
     * Dangerous file extensions that should be blocked or warned about
     */
    private val dangerousExtensions = setOf(
        // Android
        "apk", "aab", "dex",
        // Executables
        "exe", "msi", "bat", "cmd", "com", "scr", "pif",
        // Scripts
        "sh", "bash", "ps1", "vbs", "js", "jse", "wsf", "wsh",
        // Archives that can contain executables
        "jar", "war",
        // Other
        "dll", "sys", "drv", "bin"
    )

    /**
     * File extensions that require explicit confirmation
     */
    private val warnExtensions = setOf(
        "zip", "rar", "7z", "tar", "gz",
        "iso", "img", "dmg",
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx"
    )

    data class DownloadValidationResult(
        val isAllowed: Boolean,
        val requiresWarning: Boolean,
        val warningMessage: String?,
        val fileName: String,
        val mimeType: String?
    )

    /**
     * Validate a download request
     */
    fun validateDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long
    ): DownloadValidationResult {
        // Extract filename
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val extension = getFileExtension(fileName).lowercase()
        
        // Check if blocked extension
        if (extension in dangerousExtensions) {
            return DownloadValidationResult(
                isAllowed = false,
                requiresWarning = true,
                warningMessage = buildBlockedMessage(fileName, extension),
                fileName = fileName,
                mimeType = mimeType
            )
        }

        // Check if manual warning extension
        if (extension in warnExtensions) {
            return DownloadValidationResult(
                isAllowed = true,
                requiresWarning = true,
                warningMessage = buildWarningMessage(fileName, contentLength),
                fileName = fileName,
                mimeType = mimeType
            )
        }

        // Validate MIME type consistency (STRICT CHECK)
        val expectedMime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        if (mimeType != null && expectedMime != null) {
            // Strict equality check or specialized handling
            // We allow exact match OR if the server sends generic octet-stream we might trust extension (with warning if needed)
            // But if extension says image/png and mime says application/x-dosexec, that's a red flag.
            
            val cleanMime = mimeType.lowercase().substringBefore(";")
            val cleanExpected = expectedMime.lowercase()
            
            // Allow if server sends generic binary type
            val isGenericServerMime = cleanMime == "application/octet-stream" || cleanMime == "application/x-download"
            
            if (!isGenericServerMime && cleanMime != cleanExpected) {
                 return DownloadValidationResult(
                    isAllowed = true,
                    requiresWarning = true,
                    warningMessage = "Security Warning: File type mismatch.\n\nFile: $fileName\nExtension suggests: $expectedMime\nServer sent: $cleanMime\n\nThis could be an attempt to disguise a dangerous file.",
                    fileName = fileName,
                    mimeType = mimeType
                )
            }
        }

        return DownloadValidationResult(
            isAllowed = true,
            requiresWarning = false,
            warningMessage = null,
            fileName = fileName,
            mimeType = mimeType
        )
    }

    private fun getFileExtension(fileName: String): String {
        val lastDot = fileName.lastIndexOf('.')
        return if (lastDot >= 0) fileName.substring(lastDot + 1) else ""
    }

    private fun buildBlockedMessage(fileName: String, extension: String): String {
        return when (extension) {
            "apk", "aab" -> "⚠️ Android app files (.apk) can be dangerous and may contain malware. This download has been blocked for your safety."
            "exe", "msi", "bat", "cmd" -> "⚠️ Executable files (.${extension}) cannot run on Android and may indicate a malicious download attempt."
            "jar", "dex" -> "⚠️ Code files (.${extension}) can be dangerous. This download has been blocked."
            else -> "⚠️ File type .$extension has been blocked for security reasons."
        }
    }

    private fun buildWarningMessage(fileName: String, contentLength: Long): String {
        val sizeStr = formatFileSize(contentLength)
        return "Download $fileName ($sizeStr)?\n\nMake sure you trust this file before opening it."
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 0 -> "Unknown size"
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }

    /**
     * Check if auto-open is allowed for this file type
     */
    fun isAutoOpenAllowed(fileName: String): Boolean {
        val extension = getFileExtension(fileName).lowercase()
        // Never auto-open dangerous or warned files
        return extension !in dangerousExtensions && extension !in warnExtensions
    }
}

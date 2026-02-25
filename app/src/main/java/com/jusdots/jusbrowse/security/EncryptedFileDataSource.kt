package com.jusdots.jusbrowse.security

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DataSource
import androidx.security.crypto.EncryptedFile
import java.io.File
import java.io.InputStream

class EncryptedFileDataSource(
    private val context: Context
) : BaseDataSource(true) {

    private var inputStream: InputStream? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = 0
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        this.uri = uri
        
        transferInitializing(dataSpec)
        
        val file = File(uri.path ?: throw IllegalArgumentException("Uri path is null"))
        val masterKey = EncryptionManager.getMasterKey(context)
        
        val encryptedFile = EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
        
        val inputStream = encryptedFile.openFileInput()
        this.inputStream = inputStream
        
        // Handling skip for seeking (sequential decryption skip)
        val skipped = inputStream.skip(dataSpec.position)
        if (skipped < dataSpec.position) {
            // We couldn't skip enough
            throw java.io.IOException("Could not skip to position ${dataSpec.position}")
        }
        
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            // We don't know the exact decrypted length without reading, 
            // but for ExoPlayer, we can return C.LENGTH_UNSET or an estimate.
            // EncryptedFile doesn't expose the underlying plaintext length easily.
            C.LENGTH_UNSET.toLong()
        }
        
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        
        val bytesToRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            Math.min(bytesRemaining, length.toLong()).toInt()
        }
        
        val bytesRead = inputStream?.read(buffer, offset, bytesToRead) ?: -1
        if (bytesRead == -1) {
            return C.RESULT_END_OF_INPUT
        }
        
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= bytesRead
        }
        
        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        try {
            inputStream?.close()
        } finally {
            inputStream = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    class Factory(private val context: Context) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return EncryptedFileDataSource(context)
        }
    }
}

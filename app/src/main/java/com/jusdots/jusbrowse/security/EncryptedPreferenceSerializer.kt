package com.jusdots.jusbrowse.security

import androidx.datastore.core.Serializer
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.emptyPreferences
import okio.buffer
import okio.sink
import okio.source
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

object EncryptedPreferenceSerializer : Serializer<Preferences> {
    override val defaultValue: Preferences = emptyPreferences()

    override suspend fun readFrom(input: InputStream): Preferences {
        val encryptedBytes = input.readBytes()
        if (encryptedBytes.isEmpty()) return defaultValue
        
        return try {
            val decryptedBytes = EncryptionManager.decrypt(encryptedBytes)
            // Note: PreferencesSerializer is @InternalDataStoreApi but usable
            @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
            PreferencesSerializer.readFrom(decryptedBytes.inputStream().source().buffer())
        } catch (e: Exception) {
            // If decryption fails (e.g. first time or key issue), return empty
            defaultValue
        }
    }

    override suspend fun writeTo(t: Preferences, output: OutputStream) {
        val baos = ByteArrayOutputStream()
        @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
        PreferencesSerializer.writeTo(t, baos.sink().buffer())
        
        val encryptedBytes = EncryptionManager.encrypt(baos.toByteArray())
        output.write(encryptedBytes)
    }
}

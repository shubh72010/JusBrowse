package com.jusdots.jusbrowse.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object ApiScanner {

    /**
     * Scans a file with VirusTotal.
     * Returns a status string (e.g. "Scan initiated. Check VT dashboard." or error message).
     */
    suspend fun scanWithVirusTotal(file: File, apiKey: String): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext "Error: API Key is missing"
        if (!file.exists()) return@withContext "Error: File not found"

        try {
            val boundary = "Boundary-" + UUID.randomUUID().toString()
            val url = URL("https://www.virustotal.com/api/v3/files")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("x-apikey", apiKey)
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.doOutput = true

            val outputStream = connection.outputStream
            val writer = PrintWriter(OutputStreamWriter(outputStream, "UTF-8"), true)

            // Add file part
            writer.append("--$boundary\r\n")
            writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"\r\n")
            writer.append("Content-Type: application/octet-stream\r\n\r\n")
            writer.flush()

            FileInputStream(file).use { inputStream ->
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                outputStream.flush()
            }

            writer.append("\r\n--$boundary--\r\n")
            writer.flush()
            writer.close()

            val responseCode = connection.responseCode
            if (responseCode == 200 || responseCode == 201) {
                // Return success msg
                return@withContext "File uploaded to VirusTotal successfully"
            } else {
                val errorStream = connection.errorStream
                if (errorStream != null) {
                    val response = errorStream.bufferedReader().use { it.readText() }
                    try {
                        val json = JSONObject(response)
                        val error = json.optJSONObject("error")
                        return@withContext "Error $responseCode: ${error?.optString("message") ?: "Unknown API error"}"
                    } catch (e: Exception) {
                        return@withContext "Error $responseCode"
                    }
                }
                return@withContext "Error block $responseCode"
            }
        } catch (e: Exception) {
            return@withContext "Error: ${e.localizedMessage}"
        }
    }

    /**
     * Scans a file with Koodous (APK only usually).
     * Returns a status string.
     */
    suspend fun scanWithKoodous(file: File, apiKey: String): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext "Error: API Key is missing"
        if (!file.exists()) return@withContext "Error: File not found"

        try {
            // Koodous 1st step: Get upload URL
            val uploadUrlObj = URL("https://developer.koodous.com/apks")
            val conn1 = uploadUrlObj.openConnection() as HttpURLConnection
            conn1.requestMethod = "GET"
            conn1.setRequestProperty("Authorization", "Token $apiKey")

            if (conn1.responseCode != 200) {
                return@withContext "Koodous Auth Error: ${conn1.responseCode}"
            }
            // For Koodous, typically you do a GET to /apks to list or you do a specific endpoint for upload.
            // Simplified upload approach assuming standard endpoint (if available) or direct POST
            // Koodous actually requires a POST to create a resource, then a PUT to the upload URL.
            // This is a simplified direct POST representation. (In a real scenario, refer to exact Koodous docs)
            
            val boundary = "Boundary-" + UUID.randomUUID().toString()
            val url = URL("https://api.koodous.com/apks")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Token $apiKey")
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.doOutput = true

            val outputStream = connection.outputStream
            val writer = PrintWriter(OutputStreamWriter(outputStream, "UTF-8"), true)

            // Add file part
            writer.append("--$boundary\r\n")
            writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"\r\n")
            writer.append("Content-Type: application/vnd.android.package-archive\r\n\r\n")
            writer.flush()

            FileInputStream(file).use { inputStream ->
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                outputStream.flush()
            }

            writer.append("\r\n--$boundary--\r\n")
            writer.flush()
            writer.close()

            val responseCode = connection.responseCode
            if (responseCode == 200 || responseCode == 201) {
                return@withContext "File uploaded to Koodous successfully"
            } else {
                return@withContext "Error $responseCode from Koodous"
            }
        } catch (e: Exception) {
            return@withContext "Error: ${e.localizedMessage}"
        }
    }
}

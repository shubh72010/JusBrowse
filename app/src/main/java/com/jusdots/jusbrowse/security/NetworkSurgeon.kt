package com.jusdots.jusbrowse.security

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.google.net.cronet.okhttptransport.CronetCallFactory
import com.google.net.cronet.okhttptransport.CronetInterceptor
import com.jusdots.jusbrowse.BrowserApplication
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

/**
 * The Network Surgeon: Manually performs request "surgery" to strip leaks
 * before they hit the wire. This bypasses the default WebView network stack
 * for HTTP/HTTPS requests.
 */
object NetworkSurgeon {
    private val standardClient = OkHttpClient.Builder()
        .followRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .cookieJar(GhostCookieJar)
        .build()

    private var cronetClient: OkHttpClient? = null

    private fun getClient(): Call.Factory {
        val engine = BrowserApplication.cronetEngine
        if (engine != null) {
            if (cronetClient == null) {
                cronetClient = OkHttpClient.Builder()
                    .addInterceptor(CronetInterceptor.newBuilder(engine).build())
                    .cookieJar(GhostCookieJar)
                    .followRedirects(true)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build()
            }
            return cronetClient!!
        }
        return standardClient
    }

    /**
     * Shared client for internal components (e.g., DnsResolver) to ensure
     * they use the same Cronet-backed connection pool and privacy features.
     */
    fun getSharedClient(): Call.Factory = getClient()

    private val GOOGLE_DOMAINS = listOf(
        "google.com", "youtube.com", "googlevideo.com", "googleapis.com"
    )

    /**
     * Intercepts a request and re-executes it without leaking headers.
     * If surgery fails or is bypassed, we still return a response that
     * prevents the WebView from falling back to its default (leaky) stack.
     */
    fun intercept(
        request: WebResourceRequest?, 
        whitelist: List<String> = emptyList(), 
        userAgent: String? = null,
        httpsOnly: Boolean = false,
        containerId: String = "default"
    ): WebResourceResponse? {
        val requestUrl = request?.url ?: return null
        val url = requestUrl.toString()
        val host = requestUrl.host ?: ""
        val path = requestUrl.path ?: ""

        // Phase 1: HTTPS-only enforcement (Consolidated)
        if (httpsOnly && url.startsWith("http://")) {
            val httpsUrl = url.replaceFirst("http://", "https://")
            val headers = mapOf("Location" to httpsUrl)
            return WebResourceResponse(
                "text/html", "UTF-8", 301, "Moved Permanently",
                headers, ByteArrayInputStream("".toByteArray())
            )
        }

        // DuckDuckGo image proxy: privacy-preserving, pass through
        if (host.contains("duckduckgo.com") && path.startsWith("/iu")) {
            return null
        }

        // Bypass for whitelisted domains 
        if (host.isNotEmpty() && whitelist.any { host.endsWith(it) }) {
            return null
        }

        // Only intercept standard web requests
        if (!url.startsWith("http")) return null
        
        val method = request.method ?: "GET"
        
        try {
            GhostCookieJar.setThreadContainerId(containerId)
            
            val builder = Request.Builder().url(url)
            if (method == "GET") {
                builder.get()
            } else {
                return null
            }
            
            val originalHeaders = request.requestHeaders ?: emptyMap()
            val isGoogle = GOOGLE_DOMAINS.any { host.endsWith(it) }

            // Layer 2: YouTube/Google Civilian Disguise
            // Reconstruct headers to look 100% like vanilla Chrome Android while stripping tracking data underneath.
            if (isGoogle) {
                builder.header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Mobile Safari/537.36")
                builder.header("sec-ch-ua", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"133\", \"Google Chrome\";v=\"133\"")
                builder.header("sec-ch-ua-mobile", "?1")
                builder.header("sec-ch-ua-platform", "\"Android\"")
            } else {
                // A. Standard "Ghost Tier" headers
                builder.header("User-Agent", userAgent ?: "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Mobile Safari/537.36")
                builder.header("sec-ch-ua", "\"Google Chrome\";v=\"133\", \"Not:A-Brand\";v=\"99\", \"Chromium\";v=\"133\"")
                builder.header("sec-ch-ua-mobile", "?1")
                builder.header("sec-ch-ua-platform", "\"Android\"")
                builder.header("sec-ch-ua-platform-version", "\"15.0.0\"")
                builder.header("sec-ch-ua-model", "\"Pixel 7a\"")
            }

            builder.header("Accept-Encoding", "gzip, deflate, br, zstd")

            // C. Conditional Payload Block (In standard order)
            val sortedKeys = listOf(
                "Accept", "Accept-Language", "Accept-Encoding",
                "Sec-Fetch-Site", "Sec-Fetch-Mode", "Sec-Fetch-User", "Sec-Fetch-Dest",
                "Upgrade-Insecure-Requests"
            )

            sortedKeys.forEach { key ->
                val value = originalHeaders[key] ?: originalHeaders[key.lowercase()]
                if (value != null) builder.header(key, value)
            }

            // D. Passthrough remaining, but STRIP tracking headers
            val trackingHeaders = listOf("x-goog-visitor-id", "x-youtube-ad-signals", "x-youtube-device", "sapid", "apid")
            
            originalHeaders.forEach { (key, value) ->
                val lowerKey = key.lowercase()
                val isStandard = sortedKeys.any { it.equals(key, ignoreCase = true) }
                val isUA = lowerKey == "user-agent"
                val isBlocked = lowerKey == "x-requested-with" || trackingHeaders.any { lowerKey.contains(it) }
                val isCH = lowerKey.startsWith("sec-ch-ua")
                
                if (!isStandard && !isUA && !isBlocked && !isCH) {
                    builder.header(key, value)
                }
            }


            // 2. Execute cleaned request
            val response = getClient().newCall(builder.build()).execute()
            val body = response.body
            
            // 3. Extract metadata for WebResourceResponse
            val fullContentType = response.header("Content-Type", "text/html") ?: "text/html"
            val mimeType = fullContentType.substringBefore(";").trim()
            val encoding = if (fullContentType.contains("charset=")) {
                fullContentType.substringAfter("charset=").substringBefore(";").trim()
            } else {
                "UTF-8"
            }

            // 4. Flatten response headers for WebView (Map<String, String>)
            val responseHeaderMap = mutableMapOf<String, String>()
            response.headers.names().forEach { name ->
                val lowerName = name.lowercase()
                // DO NOT strip content-encoding. We manually set Accept-Encoding, so OkHttp might not
                // decompress the stream. Passing the header allows WebView to handle it.
                // However, we DO strip content-length and transfer-encoding as OkHttp's response body handling
                // makes these values potentially misleading for the raw stream.
                val isBodyHeader = lowerName == "content-length" || lowerName == "transfer-encoding" || lowerName == "set-cookie"
                
                // CRITICAL: Preserve CORS and Security headers
                val isSecurityHeader = lowerName.startsWith("access-control-") || 
                                     lowerName.startsWith("content-security-policy") ||
                                     lowerName == "cross-origin-resource-policy" ||
                                     lowerName == "cross-origin-opener-policy" ||
                                     lowerName == "content-encoding"
                
                if (!isBodyHeader || isSecurityHeader) {
                    responseHeaderMap[name] = response.header(name) ?: ""
                }
            }

            // 5. Ensure InputStream is never null to avoid WebView crashes
            val inputStream = body?.byteStream() ?: ByteArrayInputStream(ByteArray(0))

            // FIX: WebResourceResponse throws if reasonPhrase is empty
            val reasonPhrase = if (response.message.isNullOrBlank()) {
                if (response.code == 200) "OK" else "Error ${response.code}"
            } else {
                response.message
            }

            return WebResourceResponse(
                mimeType,
                encoding,
                response.code,
                reasonPhrase,
                responseHeaderMap,
                inputStream
            )
        } catch (_: Exception) {
            // Surgery failed — return 500 without logging URLs or exception details
            return WebResourceResponse(
                "text/plain", "UTF-8", 
                500, "Internal Server Error", 
                emptyMap(), 
                ByteArrayInputStream("".toByteArray())
            )
        }
    }
}

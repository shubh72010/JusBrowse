package com.jusdots.jusbrowse.security

/**
 * Static repository of verified "Golden Profiles".
 * These are consistent configurations of real devices.
 */
object PersonaRepository {

    private val PIXEL_8_PRO = FakePersona(
        id = "pixel_8_pro",
        displayName = "Pixel 8 Pro",
        flagEmoji = "🇺🇸",
        userAgent = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        brands = listOf(
            FakePersona.BrandVersion("Not_A Brand", "8"),
            FakePersona.BrandVersion("Chromium", "120"),
            FakePersona.BrandVersion("Google Chrome", "120")
        ),
        platform = "Android",
        platformVersion = "14",
        model = "Pixel 8 Pro",
        mobile = true,
        headers = mapOf(
            "sec-ch-ua" to "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"",
            "sec-ch-ua-mobile" to "?1",
            "sec-ch-ua-platform" to "\"Android\"",
            "Accept-Language" to "en-US,en;q=0.9",
        ),
        screenWidth = 1344,
        screenHeight = 2992,
        pixelRatio = 3.0,
        cpuCores = 9, // Tensor G3
        ramGB = 12,
        videoCardRenderer = "Mali-G715",
        videoCardVendor = "Google",
        noiseSeed = 1001L,
        locale = "en-US",
        languages = listOf("en-US", "en"),
        timezone = "America/New_York",
        clockSkewMs = 12,
        networkType = "4g",
        networkDownlink = 12.5,
        networkRtt = 45,
        webglMaxTextureSize = 8192,
        webglMaxRenderBufferSize = 8192,
        webglExtensions = listOf(
            "EXT_blend_minmax", "EXT_sRGB", "OES_texture_float", "OES_standard_derivatives",
            "WEBGL_debug_renderer_info", "WEBGL_lose_context", "ANGLE_instanced_arrays"
        ),
        mediaDeviceLabels = mapOf(
            "audioinput" to "Internal Microphone",
            "audiooutput" to "Phone Speaker",
            "videoinput_front" to "camera2 1, facing front",
            "videoinput_back" to "camera2 0, facing back"
        ),
        webglParams = mapOf(
            36347 to 1024, // MAX_VERTEX_UNIFORM_VECTORS
            36348 to 1024, // MAX_FRAGMENT_UNIFORM_VECTORS
            36349 to 31,   // MAX_VARYING_VECTORS
            35660 to 16,   // MAX_VERTEX_ATTRIBS
            35661 to 80,   // MAX_COMBINED_TEXTURE_IMAGE_UNITS
            33902 to listOf(1, 1024), // ALIASED_LINE_WIDTH_RANGE
            33901 to listOf(1, 1024), // ALIASED_POINT_SIZE_RANGE
            34930 to 16    // MAX_SAMPLES
        ),
        audioBaseLatency = 0.012,
        fontJitterSeed = 101L
    )

    private val GALAXY_S23_ULTRA = FakePersona(
        id = "s23_ultra",
        displayName = "Galaxy S23 Ultra",
        flagEmoji = "🇰🇷",
        userAgent = "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36",
        brands = listOf(
            FakePersona.BrandVersion("Not?A_Brand", "24"),
            FakePersona.BrandVersion("Chromium", "119"),
            FakePersona.BrandVersion("Google Chrome", "119")
        ),
        platform = "Android",
        platformVersion = "13.0.0",
        model = "SM-S918B",
        mobile = true,
        headers = mapOf(
            "Accept-Language" to "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
        ),
        screenWidth = 1440,
        screenHeight = 3088,
        pixelRatio = 3.0, // High density
        cpuCores = 8,
        ramGB = 12,
        videoCardRenderer = "Adreno (TM) 740",
        videoCardVendor = "Qualcomm",
        noiseSeed = 2002L,
        locale = "ko-KR",
        languages = listOf("ko-KR", "ko", "en-US", "en"),
        timezone = "Asia/Seoul",
        clockSkewMs = -24,
        networkType = "4g",
        networkDownlink = 10.0,
        networkRtt = 40,
        webglMaxTextureSize = 8192,
        webglMaxRenderBufferSize = 8192,
        webglExtensions = listOf(
            "EXT_blend_minmax", "EXT_sRGB", "OES_texture_float", "OES_standard_derivatives",
            "WEBGL_debug_renderer_info", "WEBGL_lose_context", "ANGLE_instanced_arrays"
        ),
        mediaDeviceLabels = mapOf(
            "audioinput" to "Internal Microphone",
            "audiooutput" to "Speaker",
            "videoinput_front" to "camera2 1, facing front",
            "videoinput_back" to "camera2 0, facing back"
        ),
        webglParams = mapOf(
            36347 to 4096, // MAX_VERTEX_UNIFORM_VECTORS
            36348 to 4096, // MAX_FRAGMENT_UNIFORM_VECTORS
            36349 to 32,   // MAX_VARYING_VECTORS
            35660 to 16,   // MAX_VERTEX_ATTRIBS
            35661 to 128,  // MAX_COMBINED_TEXTURE_IMAGE_UNITS
            33902 to listOf(1, 1), // Adreno typical
            33901 to listOf(1, 1024),
            34930 to 8
        ),
        audioBaseLatency = 0.008,
        fontJitterSeed = 202L
    )

    private val XIAOMI_13 = FakePersona(
        id = "xiaomi_13_pro",
        displayName = "Xiaomi 13 Pro",
        flagEmoji = "🇨🇳",
        userAgent = "Mozilla/5.0 (Linux; Android 13; 2210132C) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Mobile Safari/537.36",
        brands = listOf(
            FakePersona.BrandVersion("Chromium", "110"),
            FakePersona.BrandVersion("Not=A?Brand", "99")
        ),
        platform = "Android",
        platformVersion = "13",
        model = "2210132C",
        mobile = true,
        headers = mapOf(
            "Accept-Language" to "zh-CN,zh;q=0.9",
        ),
        screenWidth = 1440,
        screenHeight = 3200,
        pixelRatio = 3.0,
        cpuCores = 8,
        ramGB = 12,
        videoCardRenderer = "Adreno (TM) 740",
        videoCardVendor = "Qualcomm",
        noiseSeed = 3003L,
        locale = "zh-CN",
        languages = listOf("zh-CN", "zh"),
        timezone = "Asia/Shanghai",
        clockSkewMs = 5,
        networkType = "wifi",
        networkDownlink = 50.0,
        networkRtt = 15,
        webglMaxTextureSize = 8192,
        webglMaxRenderBufferSize = 8192,
        webglExtensions = listOf(
            "EXT_blend_minmax", "EXT_sRGB", "OES_texture_float", "OES_standard_derivatives",
            "WEBGL_debug_renderer_info", "WEBGL_lose_context", "ANGLE_instanced_arrays"
        ),
        mediaDeviceLabels = mapOf(
            "audioinput" to "Internal Microphone",
            "audiooutput" to "Speaker",
            "videoinput_front" to "camera2 1, facing front",
            "videoinput_back" to "camera2 0, facing back"
        ),
        webglParams = mapOf(
            36347 to 4096, // MAX_VERTEX_UNIFORM_VECTORS
            36348 to 4096, // MAX_FRAGMENT_UNIFORM_VECTORS
            36349 to 32,   // MAX_VARYING_VECTORS
            35660 to 16,   // MAX_VERTEX_ATTRIBS
            35661 to 128,  // MAX_COMBINED_TEXTURE_IMAGE_UNITS
            33902 to listOf(1, 1),
            33901 to listOf(1, 1024),
            34930 to 8
        ),
        audioBaseLatency = 0.009,
        fontJitterSeed = 303L
    )

    // A generic fallback or randomizer could pick one of these
    val GOLDEN_PROFILES = listOf(
        PIXEL_8_PRO,
        GALAXY_S23_ULTRA,
        XIAOMI_13
    )

    fun getPersonaById(id: String): FakePersona? {
        return GOLDEN_PROFILES.find { it.id == id }
    }

    fun getRandomPersona(): FakePersona {
        return GOLDEN_PROFILES.random()
    }
}

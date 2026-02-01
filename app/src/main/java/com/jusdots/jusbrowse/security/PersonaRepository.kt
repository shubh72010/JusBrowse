package com.jusdots.jusbrowse.security

/**
 * Static repository of verified "Golden Profiles".
 * These are consistent configurations of real devices.
 */
object PersonaRepository {

    // --- GOOGLE GROUP ---
    private val PIXEL_8_PRO = FakePersona(
        id = "pixel_8_pro",
        groupId = "google",
        isFlagship = true,
        displayName = "Pixel 8 Pro (Flagship)",
        flagEmoji = "🇺🇸",
        userAgent = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Mobile Safari/537.36",
        brands = listOf(
            FakePersona.BrandVersion("Not_A Brand", "8"),
            FakePersona.BrandVersion("Chromium", "145"),
            FakePersona.BrandVersion("Google Chrome", "145")
        ),
        platformVersion = "14",
        platform = "Android",
        platformString = "Linux aarch64",
        model = "Pixel 8 Pro",
        mobile = true,
        headers = mapOf(
            "sec-ch-ua" to "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"145\", \"Google Chrome\";v=\"145\"",
            "sec-ch-ua-mobile" to "?1",
            "sec-ch-ua-platform" to "\"Android\"",
            "Accept-Language" to "en-US,en;q=0.9",
        ),
        screenWidth = 1236, // 412 * 3
        screenHeight = 2745, // 915 * 3
        pixelRatio = 3.0,
        cpuCores = 8,
        ramGB = 12,
        videoCardRenderer = "Mali-G715 MC7",
        videoCardVendor = "ARM",
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
        audioBaseLatency = 0.012,
        fontJitterSeed = 101L
    )

    private val PIXEL_7A = FakePersona(
        id = "pixel_7a",
        groupId = "google",
        isFlagship = false,
        displayName = "Pixel 7a (Budget)",
        flagEmoji = "🇺🇸",
        userAgent = "Mozilla/5.0 (Linux; Android 14; Pixel 7a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Mobile Safari/537.36",
        brands = listOf(
            FakePersona.BrandVersion("Not_A Brand", "8"),
            FakePersona.BrandVersion("Chromium", "145"),
            FakePersona.BrandVersion("Google Chrome", "145")
        ),
        platformVersion = "14",
        platform = "Android",
        platformString = "Linux aarch64",
        model = "Pixel 7a",
        mobile = true,
        headers = mapOf(
            "sec-ch-ua" to "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"145\", \"Google Chrome\";v=\"145\"",
            "sec-ch-ua-mobile" to "?1",
            "sec-ch-ua-platform" to "\"Android\"",
            "Accept-Language" to "en-US,en;q=0.9",
        ),
        screenWidth = 1080,
        screenHeight = 2400,
        pixelRatio = 2.625, // Reports 411.4 -> 411
        cpuCores = 8,
        ramGB = 8,
        videoCardRenderer = "Mali-G710 MP7",
        videoCardVendor = "ARM",
        noiseSeed = 1002L,
        locale = "en-US",
        languages = listOf("en-US", "en"),
        timezone = "America/Chicago",
        clockSkewMs = -5,
        networkType = "4g",
        networkDownlink = 5.0,
        networkRtt = 60,
        webglMaxTextureSize = 4096,
        webglMaxRenderBufferSize = 4096,
        webglExtensions = listOf("EXT_blend_minmax", "EXT_sRGB", "OES_texture_float"),
        audioBaseLatency = 0.018,
        fontJitterSeed = 102L
    )

    // --- SAMSUNG GROUP ---
    private val GALAXY_S24_ULTRA = FakePersona(
        id = "s24_ultra",
        groupId = "samsung",
        isFlagship = true,
        displayName = "Galaxy S24 Ultra (Flagship)",
        flagEmoji = "🇰🇷",
        userAgent = "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Mobile Safari/537.36",
        brands = listOf(
            FakePersona.BrandVersion("Not_A Brand", "8"),
            FakePersona.BrandVersion("Chromium", "145"),
            FakePersona.BrandVersion("Google Chrome", "145")
        ),
        platformVersion = "14",
        platform = "Android",
        platformString = "Linux aarch64",
        model = "SM-S928B",
        mobile = true,
        headers = mapOf(
            "sec-ch-ua" to "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"145\", \"Google Chrome\";v=\"145\"",
            "sec-ch-ua-mobile" to "?1",
            "sec-ch-ua-platform" to "\"Android\"",
            "Accept-Language" to "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
        ),
        screenWidth = 1440,
        screenHeight = 3120,
        pixelRatio = 3.5, // 411x891
        cpuCores = 8,
        ramGB = 12,
        videoCardRenderer = "Adreno (TM) 750",
        videoCardVendor = "Qualcomm",
        noiseSeed = 2001L,
        locale = "ko-KR",
        languages = listOf("ko-KR", "ko", "en-US", "en"),
        timezone = "Asia/Seoul",
        clockSkewMs = 15,
        networkType = "5g",
        networkDownlink = 20.0,
        networkRtt = 30,
        webglMaxTextureSize = 16384,
        webglMaxRenderBufferSize = 16384,
        webglExtensions = listOf("EXT_blend_minmax", "EXT_sRGB", "OES_texture_float", "WEBGL_debug_renderer_info"),
        audioBaseLatency = 0.006,
        fontJitterSeed = 201L
    )

    private val GALAXY_A54 = FakePersona(
        id = "galaxy_a54",
        groupId = "samsung",
        isFlagship = false,
        displayName = "Galaxy A54 (Budget)",
        flagEmoji = "🇰🇷",
        userAgent = "Mozilla/5.0 (Linux; Android 13; SM-A546B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Mobile Safari/537.36",
        brands = listOf(
            FakePersona.BrandVersion("Not_A Brand", "8"),
            FakePersona.BrandVersion("Chromium", "145"),
            FakePersona.BrandVersion("Google Chrome", "145")
        ),
        platformVersion = "13",
        platform = "Android",
        platformString = "Linux aarch64",
        model = "SM-A546B",
        mobile = true,
        headers = mapOf(
            "sec-ch-ua" to "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"145\", \"Google Chrome\";v=\"145\"",
            "sec-ch-ua-mobile" to "?1",
            "sec-ch-ua-platform" to "\"Android\"",
            "Accept-Language" to "en-GB,en;q=0.9",
        ),
        screenWidth = 1080,
        screenHeight = 2400,
        pixelRatio = 3.0, // 360x800 - EXTREMELY COMMON BUCKET
        cpuCores = 8,
        ramGB = 6,
        videoCardRenderer = "Mali-G68 MP5",
        videoCardVendor = "ARM",
        noiseSeed = 2002L,
        locale = "en-GB",
        languages = listOf("en-GB", "en"),
        timezone = "Europe/London",
        clockSkewMs = 3,
        networkType = "4g",
        networkDownlink = 8.0,
        networkRtt = 50,
        webglMaxTextureSize = 8192,
        webglMaxRenderBufferSize = 8192,
        audioBaseLatency = 0.022,
        fontJitterSeed = 202L
    )

    // --- XIAOMI GROUP ---
    private val XIAOMI_14_PRO = FakePersona(
        id = "xiaomi_14_pro",
        groupId = "xiaomi",
        isFlagship = true,
        displayName = "Xiaomi 14 Pro (Flagship)",
        flagEmoji = "🇨🇳",
        userAgent = "Mozilla/5.0 (Linux; Android 14; 23116PN5BC) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Mobile Safari/537.36",
        brands = listOf(
            FakePersona.BrandVersion("Not_A Brand", "8"),
            FakePersona.BrandVersion("Chromium", "145"),
            FakePersona.BrandVersion("Google Chrome", "145")
        ),
        platformVersion = "14",
        platform = "Android",
        platformString = "Linux aarch64",
        model = "23116PN5BC",
        mobile = true,
        headers = mapOf(
            "sec-ch-ua" to "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"145\", \"Google Chrome\";v=\"145\"",
            "sec-ch-ua-mobile" to "?1",
            "sec-ch-ua-platform" to "\"Android\"",
            "Accept-Language" to "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7",
        ),
        screenWidth = 1572, // 393 * 4
        screenHeight = 3408, // 852 * 4
        pixelRatio = 4.0, // 393x852 - Modern High-End Bucket
        cpuCores = 8,
        ramGB = 16,
        videoCardRenderer = "Adreno (TM) 750",
        videoCardVendor = "Qualcomm",
        noiseSeed = 3001L,
        locale = "zh-CN",
        languages = listOf("zh-CN", "zh", "en-US", "en"),
        timezone = "Asia/Shanghai",
        clockSkewMs = -2,
        networkType = "wifi",
        networkDownlink = 100.0,
        networkRtt = 10,
        webglMaxTextureSize = 16384,
        webglMaxRenderBufferSize = 16384,
        audioBaseLatency = 0.007,
        fontJitterSeed = 301L
    )

    private val REDMI_NOTE_13 = FakePersona(
        id = "redmi_note_13",
        groupId = "xiaomi",
        isFlagship = false,
        displayName = "Redmi Note 13 (Budget)",
        flagEmoji = "🇨🇳",
        userAgent = "Mozilla/5.0 (Linux; Android 13; 23124RA7EO) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Mobile Safari/537.36",
        brands = listOf(
            FakePersona.BrandVersion("Not_A Brand", "8"),
            FakePersona.BrandVersion("Chromium", "145"),
            FakePersona.BrandVersion("Google Chrome", "145")
        ),
        platformVersion = "13",
        platform = "Android",
        platformString = "Linux aarch64",
        model = "23124RA7EO",
        mobile = true,
        headers = mapOf(
            "sec-ch-ua" to "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"145\", \"Google Chrome\";v=\"145\"",
            "sec-ch-ua-mobile" to "?1",
            "sec-ch-ua-platform" to "\"Android\"",
            "Accept-Language" to "en-US,en;q=0.9",
        ),
        screenWidth = 1080,
        screenHeight = 2400,
        pixelRatio = 3.0, // 360x800
        cpuCores = 8,
        ramGB = 6,
        videoCardRenderer = "Mali-G57",
        videoCardVendor = "ARM",
        noiseSeed = 3002L,
        locale = "en-US",
        languages = listOf("en-US", "en"),
        timezone = "Asia/Kolkata",
        clockSkewMs = 8,
        networkType = "4g",
        networkDownlink = 15.0,
        networkRtt = 40,
        webglMaxTextureSize = 8192,
        webglMaxRenderBufferSize = 8192,
        audioBaseLatency = 0.025,
        fontJitterSeed = 302L
    )

    private val MOTO_EDGE_50_ULTRA = FakePersona(
        id = "moto_edge_50_ultra",
        groupId = "motorola",
        isFlagship = true,
        displayName = "Moto Edge 50 Ultra (Flagship)",
        flagEmoji = "🇺🇸",
        userAgent = "Mozilla/5.0 (Linux; Android 14; Moto Edge 50 Ultra) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Mobile Safari/537.36",
        brands = listOf(
            FakePersona.BrandVersion("Not_A Brand", "8"),
            FakePersona.BrandVersion("Chromium", "145"),
            FakePersona.BrandVersion("Google Chrome", "145")
        ),
        platformVersion = "14",
        platform = "Android",
        platformString = "Linux aarch64",
        model = "Moto Edge 50 Ultra",
        mobile = true,
        headers = mapOf(
            "sec-ch-ua" to "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"145\", \"Google Chrome\";v=\"145\"",
            "sec-ch-ua-mobile" to "?1",
            "sec-ch-ua-platform" to "\"Android\"",
            "Accept-Language" to "en-US,en;q=0.9",
        ),
        screenWidth = 1220, // 406.66 -> 407
        screenHeight = 2712, 
        pixelRatio = 3.0,
        cpuCores = 8,
        ramGB = 12,
        videoCardRenderer = "Adreno (TM) 735",
        videoCardVendor = "Qualcomm",
        noiseSeed = 4002L,
        locale = "en-US",
        languages = listOf("en-US", "en"),
        timezone = "America/Chicago",
        clockSkewMs = -3,
        networkType = "5g",
        networkDownlink = 25.0,
        networkRtt = 35,
        webglMaxTextureSize = 16384,
        webglMaxRenderBufferSize = 16384,
        audioBaseLatency = 0.010,
        fontJitterSeed = 402L
    )

    private val MOTO_G54 = FakePersona(
        id = "moto_g54",
        groupId = "motorola",
        isFlagship = false,
        displayName = "Moto G54 (Budget)",
        flagEmoji = "🇺🇸",
        userAgent = "Mozilla/5.0 (Linux; Android 14; Moto G54) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Mobile Safari/537.36",
        brands = listOf(
            FakePersona.BrandVersion("Not_A Brand", "8"),
            FakePersona.BrandVersion("Chromium", "145"),
            FakePersona.BrandVersion("Google Chrome", "145")
        ),
        platformVersion = "14",
        platform = "Android",
        platformString = "Linux aarch64",
        model = "Moto G54",
        mobile = true,
        headers = mapOf(
            "sec-ch-ua" to "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"145\", \"Google Chrome\";v=\"145\"",
            "sec-ch-ua-mobile" to "?1",
            "sec-ch-ua-platform" to "\"Android\"",
            "Accept-Language" to "en-US,en;q=0.9",
        ),
        screenWidth = 1080,
        screenHeight = 2400,
        pixelRatio = 2.625, // 411x914
        cpuCores = 8,
        ramGB = 4, // THE 4GB BUCKET
        videoCardRenderer = "IMG BXM-8-256",
        videoCardVendor = "Imagination Technologies",
        noiseSeed = 4001L,
        locale = "en-US",
        languages = listOf("en-US", "en"),
        timezone = "America/Los_Angeles",
        clockSkewMs = 2,
        networkType = "4g",
        networkDownlink = 10.0,
        networkRtt = 55,
        webglMaxTextureSize = 4096,
        webglMaxRenderBufferSize = 4096,
        audioBaseLatency = 0.030,
        fontJitterSeed = 401L
    )

    // DISABLED: iPhone 15 Pro - Android WebView cannot emulate Safari.
    // Cross-API consistency checks will always fail (Safari-specific behavior,
    // WebKit rendering differences, no Client Hints support).
    // Keeping definition for reference only.
    /*
    private val IPHONE_15_PRO = FakePersona(
        id = "iphone_15_pro",
        groupId = "apple",
        isFlagship = true,
        displayName = "iPhone 15 Pro (Flagship)",
        flagEmoji = "🇺🇸",
        userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
        brands = emptyList(),
        platformVersion = "17.0",
        platform = "iOS",
        platformString = "iPhone",
        model = "iPhone 15 Pro",
        mobile = true,
        headers = mapOf(
            "Accept-Language" to "en-US,en;q=0.9",
        ),
        screenWidth = 1179,
        screenHeight = 2556,
        pixelRatio = 3.0,
        cpuCores = 6,
        ramGB = 8,
        videoCardRenderer = "Apple GPU",
        videoCardVendor = "Apple Inc.",
        noiseSeed = 5001L,
        locale = "en-US",
        languages = listOf("en-US", "en"),
        timezone = "America/Los_Angeles",
        audioBaseLatency = 0.005,
        fontJitterSeed = 501L
    )
    */

    val GOLDEN_PROFILES = listOf(
        PIXEL_8_PRO, PIXEL_7A,
        GALAXY_S24_ULTRA, GALAXY_A54,
        XIAOMI_14_PRO, REDMI_NOTE_13,
        MOTO_EDGE_50_ULTRA, MOTO_G54
        // IPHONE_15_PRO removed - cannot emulate iOS from Android WebView
    )

    fun getPersonaById(id: String): FakePersona? {
        return GOLDEN_PROFILES.find { it.id == id }
    }

    fun getRandomPersona(): FakePersona {
        return GOLDEN_PROFILES.random()
    }

    /**
     * Get a specific profile within a group based on Flagship/Budget preference
     */
    fun getPersonaInGroup(groupId: String, flagship: Boolean): FakePersona {
        return GOLDEN_PROFILES.find { it.groupId == groupId && it.isFlagship == flagship }
            ?: GOLDEN_PROFILES.first { it.groupId == groupId }
    }
}

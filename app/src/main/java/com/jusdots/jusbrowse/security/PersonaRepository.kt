package com.jusdots.jusbrowse.security

/**
 * Repository of "Golden Profiles"
 * These are internally consistent, verified fingerprints of real devices.
 * NEVER generate random values for these fields.
 */
object PersonaRepository {

    private val PIXEL_8_PRO = FakePersona(
        id = "pixel_8_pro_android_14",
        displayName = "Pixel 8 Pro",
        flagEmoji = "🇺🇸",
        userAgent = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.144 Mobile Safari/537.36",
        brands = listOf(
            FakePersona.BrandVersion("Not_A Brand", "8"),
            FakePersona.BrandVersion("Chromium", "120"),
            FakePersona.BrandVersion("Google Chrome", "120")
        ),
        platform = "Android",
        platformVersion = "14.0.0",
        model = "Pixel 8 Pro",
        mobile = true,
        headers = mapOf(
            "Accept-Language" to "en-US,en;q=0.9",
        ),
        screenWidth = 1344,
        screenHeight = 2992,
        pixelRatio = 3.0,
        cpuCores = 8,
        ramGB = 12,
        videoCardRenderer = "Mali-G715-Immortalis MC11",
        videoCardVendor = "ARM",
        noiseSeed = 1001L,
        locale = "en-US",
        languages = listOf("en-US", "en"),
        timezone = "America/New_York"
    )

    private val GALAXY_S23_ULTRA = FakePersona(
        id = "samsung_s23_ultra",
        displayName = "Galaxy S23 Ultra",
        flagEmoji = "🇰🇷",
        userAgent = "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.6045.193 Mobile Safari/537.36",
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
        timezone = "Asia/Seoul"
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
        timezone = "Asia/Shanghai"
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

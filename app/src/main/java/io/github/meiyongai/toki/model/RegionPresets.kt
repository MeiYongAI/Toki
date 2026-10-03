package io.github.meiyongai.toki.model

/**
 * 运营商与国家地区预设实体类。
 *
 * @property countryName 中文国家或地区名称。
 * @property englishName 英文国家或地区名称。
 * @property isoCode 两位 ISO 3166-1 标准国家或地区代号（大写）。
 * @property operatorCode 移动国家代码（MCC）与移动网络代码（MNC）组合。
 * @property operatorName 运营商官方显示名称。
 * @property defaultLanguage 地区推荐的 BCP-47 默认系统语言标签。
 * @property defaultTimeZone 地区推荐的 IANA 标准默认时区标识。
 * @property defaultLatitude 地区核心代表城市的纬度坐标。
 * @property defaultLongitude 地区核心代表城市的经度坐标。
 * @property flagEmoji 对应的地区旗帜表情字符。
 */
data class RegionPreset(
    val countryName: String,
    val englishName: String,
    val isoCode: String,
    val operatorCode: String,
    val operatorName: String,
    val defaultLanguage: String,
    val defaultTimeZone: String,
    val defaultLatitude: Double,
    val defaultLongitude: Double,
    val flagEmoji: String = RegionPresets.isoToFlagEmoji(isoCode)
)

/**
 * 全球主流国家与地区运营商参数预置数据库。
 *
 * 提供预设数据的枚举定义及多维度快速检索能力，收录全球主流核心国家及地区。
 */
object RegionPresets {

    /**
     * 将两位 ISO 3166-1 国家或地区代号转换为对应的 Unicode 旗帜表情字符。
     *
     * 依据 Unicode 规范，利用区域指示符符号（Regional Indicator Symbol，U+1F1E6 起始）
     * 将两位大写字母拼接映射为系统原生彩色旗帜表情。
     *
     * @param isoCode 两位国家或地区标准代号。
     * @return 对应地区的旗帜 Emoji 字符；若代码长度不为 2 或非纯字母则返回空字符串。
     *
     * Callers:
     * - `io.github.meiyongai.toki.model.RegionPreset`: 初始化预设实体的旗帜字符。
     * - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 为当前生效配置显示旗帜字符。
     */
    fun isoToFlagEmoji(isoCode: String): String {
        val trimmed = isoCode.trim().uppercase()
        if (trimmed.length != 2 || trimmed[0] !in 'A'..'Z' || trimmed[1] !in 'A'..'Z') {
            return ""
        }
        val firstChar = Character.toChars(0x1F1E6 + (trimmed[0] - 'A'))
        val secondChar = Character.toChars(0x1F1E6 + (trimmed[1] - 'A'))
        return String(firstChar) + String(secondChar)
    }

    /** 全球主流国家与地区预设全量列表，按英文字母序（A-Z）统一排序 */
    val ALL_PRESETS: List<RegionPreset> = listOf(
        // === 东亚 ===
        RegionPreset("日本", "Japan", "JP", "44010", "NTT DOCOMO", "ja-JP", "Asia/Tokyo", 35.6762, 139.6503),
        RegionPreset("韩国", "South Korea", "KR", "45005", "SKTelecom", "ko-KR", "Asia/Seoul", 37.5665, 126.9780),
        RegionPreset("中国台湾", "Taiwan", "TW", "46692", "Chunghwa Telecom", "zh-TW", "Asia/Taipei", 25.0330, 121.5654),
        RegionPreset("中国香港", "Hong Kong", "HK", "45412", "CMHK", "zh-HK", "Asia/Hong_Kong", 22.3193, 114.1694),
        RegionPreset("中国澳门", "Macau", "MO", "45501", "CTM", "zh-MO", "Asia/Macau", 22.1987, 113.5439),
        RegionPreset("蒙古", "Mongolia", "MN", "42899", "Mobicom", "mn-MN", "Asia/Ulaanbaatar", 47.8864, 106.9057),

        // === 北美洲 ===
        RegionPreset("美国", "United States", "US", "310410", "AT&T", "en-US", "America/New_York", 40.7128, -74.0060),
        RegionPreset("加拿大", "Canada", "CA", "302720", "Rogers", "en-CA", "America/Toronto", 43.6532, -79.3832),
        RegionPreset("墨西哥", "Mexico", "MX", "334020", "Telcel", "es-MX", "America/Mexico_City", 19.4326, -99.1332),

        // === 东南亚 ===
        RegionPreset("新加坡", "Singapore", "SG", "52501", "Singtel", "en-SG", "Asia/Singapore", 1.3521, 103.8198),
        RegionPreset("马来西亚", "Malaysia", "MY", "50212", "Maxis", "ms-MY", "Asia/Kuala_Lumpur", 3.1390, 101.6869),
        RegionPreset("泰国", "Thailand", "TH", "52001", "AIS", "th-TH", "Asia/Bangkok", 13.7563, 100.5018),
        RegionPreset("越南", "Vietnam", "VN", "45204", "Viettel", "vi-VN", "Asia/Ho_Chi_Minh", 10.8231, 106.6297),
        RegionPreset("印度尼西亚", "Indonesia", "ID", "51010", "Telkomsel", "id-ID", "Asia/Jakarta", -6.2088, 106.8456),
        RegionPreset("菲律宾", "Philippines", "PH", "51502", "Globe", "en-PH", "Asia/Manila", 14.5995, 120.9842),
        RegionPreset("柬埔寨", "Cambodia", "KH", "45601", "Smart", "km-KH", "Asia/Phnom_Penh", 11.5564, 104.9282),
        RegionPreset("老挝", "Laos", "LA", "45701", "LaoTelecom", "lo-LA", "Asia/Vientiane", 17.9757, 102.6331),
        RegionPreset("缅甸", "Myanmar", "MM", "41401", "MPT", "my-MM", "Asia/Yangon", 16.8661, 96.1951),

        // === 南亚与中亚 ===
        RegionPreset("印度", "India", "IN", "40445", "Airtel", "en-IN", "Asia/Kolkata", 28.6139, 77.2090),
        RegionPreset("巴基斯坦", "Pakistan", "PK", "41001", "Jazz", "ur-PK", "Asia/Karachi", 33.6844, 73.0479),
        RegionPreset("孟加拉国", "Bangladesh", "BD", "47001", "Grameenphone", "bn-BD", "Asia/Dhaka", 23.8103, 90.4125),
        RegionPreset("哈萨克斯坦", "Kazakhstan", "KZ", "40102", "Kcell", "kk-KZ", "Asia/Almaty", 43.2220, 76.8512),
        RegionPreset("乌兹别克斯坦", "Uzbekistan", "UZ", "43404", "Beeline UZ", "uz-UZ", "Asia/Tashkent", 41.2995, 69.2401),

        // === 欧洲（西欧与北欧） ===
        RegionPreset("英国", "United Kingdom", "GB", "23410", "O2", "en-GB", "Europe/London", 51.5074, -0.1278),
        RegionPreset("德国", "Germany", "DE", "26201", "Telekom", "de-DE", "Europe/Berlin", 52.5200, 13.4050),
        RegionPreset("法国", "France", "FR", "20801", "Orange", "fr-FR", "Europe/Paris", 48.8566, 2.3522),
        RegionPreset("荷兰", "Netherlands", "NL", "20408", "KPN", "nl-NL", "Europe/Amsterdam", 52.3676, 4.9041),
        RegionPreset("比利时", "Belgium", "BE", "20601", "Proximus", "nl-BE", "Europe/Brussels", 50.8503, 4.3517),
        RegionPreset("爱尔兰", "Ireland", "IE", "27201", "Vodafone IE", "en-IE", "Europe/Dublin", 53.3498, -6.2603),
        RegionPreset("瑞士", "Switzerland", "CH", "22801", "Swisscom", "de-CH", "Europe/Zurich", 47.3769, 8.5417),
        RegionPreset("奥地利", "Austria", "AT", "23201", "A1", "de-AT", "Europe/Vienna", 48.2082, 16.3738),
        RegionPreset("瑞典", "Sweden", "SE", "24001", "Telia", "sv-SE", "Europe/Stockholm", 59.3293, 18.0686),
        RegionPreset("挪威", "Norway", "NO", "24201", "Telenor", "nb-NO", "Europe/Oslo", 59.9139, 10.7522),
        RegionPreset("丹麦", "Denmark", "DK", "23801", "TDC", "da-DK", "Europe/Copenhagen", 55.6761, 12.5683),
        RegionPreset("芬兰", "Finland", "FI", "24405", "Elisa", "fi-FI", "Europe/Helsinki", 60.1699, 24.9384),
        RegionPreset("冰岛", "Iceland", "IS", "27401", "Siminn", "is-IS", "Atlantic/Reykjavik", 64.1466, -21.9426),

        // === 欧洲（南欧与东欧） ===
        RegionPreset("意大利", "Italy", "IT", "22201", "TIM", "it-IT", "Europe/Rome", 41.9028, 12.4964),
        RegionPreset("西班牙", "Spain", "ES", "21407", "Movistar", "es-ES", "Europe/Madrid", 40.4168, -3.7038),
        RegionPreset("葡萄牙", "Portugal", "PT", "26801", "Vodafone PT", "pt-PT", "Europe/Lisbon", 38.7223, -9.1393),
        RegionPreset("希腊", "Greece", "GR", "20201", "Cosmote", "el-GR", "Europe/Athens", 37.9838, 23.7275),
        RegionPreset("波兰", "Poland", "PL", "26001", "Plus", "pl-PL", "Europe/Warsaw", 52.2297, 21.0122),
        RegionPreset("捷克", "Czech Republic", "CZ", "23001", "T-Mobile CZ", "cs-CZ", "Europe/Prague", 50.0755, 14.4378),
        RegionPreset("匈牙利", "Hungary", "HU", "21630", "Telekom HU", "hu-HU", "Europe/Budapest", 47.4979, 19.0402),
        RegionPreset("罗马尼亚", "Romania", "RO", "22601", "Vodafone RO", "ro-RO", "Europe/Bucharest", 44.4268, 26.1025),
        RegionPreset("土耳其", "Turkey", "TR", "28601", "Turkcell", "tr-TR", "Europe/Istanbul", 41.0082, 28.9784),
        RegionPreset("俄罗斯", "Russia", "RU", "25001", "MTS", "ru-RU", "Europe/Moscow", 55.7558, 37.6173),
        RegionPreset("乌克兰", "Ukraine", "UA", "25501", "Vodafone UA", "uk-UA", "Europe/Kyiv", 50.4501, 30.5234),

        // === 大洋洲 ===
        RegionPreset("澳大利亚", "Australia", "AU", "50501", "Telstra", "en-AU", "Australia/Sydney", -33.8688, 151.2093),
        RegionPreset("新西兰", "New Zealand", "NZ", "53001", "Vodafone NZ", "en-NZ", "Pacific/Auckland", -36.8485, 174.7633),

        // === 中东 ===
        RegionPreset("阿联酋", "United Arab Emirates", "AE", "42402", "du", "ar-AE", "Asia/Dubai", 25.2048, 55.2708),
        RegionPreset("沙特阿拉伯", "Saudi Arabia", "SA", "42001", "STC", "ar-SA", "Asia/Riyadh", 24.7136, 46.6753),
        RegionPreset("卡塔尔", "Qatar", "QA", "42701", "Ooredoo", "ar-QA", "Asia/Qatar", 25.2854, 51.5310),
        RegionPreset("科威特", "Kuwait", "KW", "41902", "Zain KW", "ar-KW", "Asia/Kuwait", 29.3759, 47.9774),
        RegionPreset("以色列", "Israel", "IL", "42501", "Partner", "he-IL", "Asia/Jerusalem", 32.0853, 34.7818),

        // === 南美洲 ===
        RegionPreset("巴西", "Brazil", "BR", "72405", "Claro BR", "pt-BR", "America/Sao_Paulo", -23.5505, -46.6333),
        RegionPreset("阿根廷", "Argentina", "AR", "722310", "Claro AR", "es-AR", "America/Argentina/Buenos_Aires", -34.6037, -58.3816),
        RegionPreset("智利", "Chile", "CL", "73001", "Entel", "es-CL", "America/Santiago", -33.4489, -70.6693),
        RegionPreset("哥伦比亚", "Colombia", "CO", "732101", "Claro CO", "es-CO", "America/Bogota", 4.7110, -74.0721),
        RegionPreset("秘鲁", "Peru", "PE", "71610", "Claro PE", "es-PE", "America/Lima", -12.0464, -77.0428),

        // === 非洲 ===
        RegionPreset("南非", "South Africa", "ZA", "65501", "Vodacom", "en-ZA", "Africa/Johannesburg", -26.2041, 28.0473),
        RegionPreset("埃及", "Egypt", "EG", "60202", "Vodafone EG", "ar-EG", "Africa/Cairo", 30.0444, 31.2357),
        RegionPreset("尼日利亚", "Nigeria", "NG", "62120", "MTN NG", "en-NG", "Africa/Lagos", 6.5244, 3.3792),
        RegionPreset("肯尼亚", "Kenya", "KE", "63902", "Safaricom", "en-KE", "Africa/Nairobi", -1.2921, 36.8219),
        RegionPreset("摩洛哥", "Morocco", "MA", "60401", "Maroc Telecom", "ar-MA", "Africa/Casablanca", 33.5731, -7.5898)
    ).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.englishName })

    private val PRESET_BY_ISO: Map<String, RegionPreset> = ALL_PRESETS.associateBy { it.isoCode.uppercase() }

    /**
     * 根据 ISO 代号反查对应的推荐系统默认语言标签。
     *
     * @param isoCode 两位国家或地区标准代号。
     * @return 对应的 BCP-47 语言代码（如 ja-JP），无法查得时默认提供 en-US。
     *
     * Callers:
     * - `io.github.meiyongai.toki.hook.LocaleHook.refreshConfig`: 运行时跟随地区时解析语言。
     * - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 界面展示跟随地区的语言名称。
     */
    fun resolveLanguageForIso(isoCode: String): String {
        return findByIso(isoCode)?.defaultLanguage ?: "en-US"
    }

    /**
     * 根据 ISO 代号反查对应的推荐系统默认时区标识。
     *
     * @param isoCode 两位国家或地区标准代号。
     * @return 对应的 IANA 时区标识（如 Asia/Tokyo），无法查得时默认提供 UTC。
     *
     * Callers:
     * - `io.github.meiyongai.toki.hook.TimeZoneHook.refreshConfig`: 运行时跟随地区时解析时区。
     * - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 界面展示跟随地区的时区标识。
     */
    fun resolveTimeZoneForIso(isoCode: String): String {
        return findByIso(isoCode)?.defaultTimeZone ?: "UTC"
    }

    /**
     * 根据 ISO 代号反查对应的推荐系统默认纬度坐标。
     *
     * @param isoCode 两位国家或地区标准代号。
     * @return 对应的纬度值（Double），未收录时默认返回东京纬度 35.6762。
     *
     * Callers:
     * - `io.github.meiyongai.toki.hook.GpsHook.refreshConfig`: 运行时跟随地区时解析经纬度。
     * - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 界面展示跟随地区的坐标值。
     */
    fun resolveLatitudeForIso(isoCode: String): Double {
        return findByIso(isoCode)?.defaultLatitude ?: 35.6762
    }

    /**
     * 根据 ISO 代号反查对应的推荐系统默认经度坐标。
     *
     * @param isoCode 两位国家或地区标准代号。
     * @return 对应的经度值（Double），未收录时默认返回东京经度 139.6503。
     *
     * Callers:
     * - `io.github.meiyongai.toki.hook.GpsHook.refreshConfig`: 运行时跟随地区时解析经纬度。
     * - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 界面展示跟随地区的坐标值。
     */
    fun resolveLongitudeForIso(isoCode: String): Double {
        return findByIso(isoCode)?.defaultLongitude ?: 139.6503
    }

    /**
     * 根据 ISO 代码快速查询预设运营商信息。
     *
     * @param isoCode 两位国家或地区标准代号。
     * @return 匹配的预设实体对象，未收录时返回 null。
     *
     * Callers:
     * - `io.github.meiyongai.toki.hook.SimHook.resolveCarrier`: 运行时根据用户配置查询默认运营商。
     * - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 用户界面根据当前代号反查国家名称。
     */
    fun findByIso(isoCode: String): RegionPreset? {
        return PRESET_BY_ISO[isoCode.trim().uppercase()]
    }

    /**
     * 根据关键字综合模糊搜索预设国家与地区。
     *
     * 支持匹配中文名称、英文名称以及 ISO 代码。
     *
     * @param query 搜索关键词。
     * @return 包含匹配项的预设列表。
     *
     * Callers:
     * - `io.github.meiyongai.toki.ui.RegionSelectionDialog`: 地区选择弹窗内的即时搜索过滤。
     */
    fun search(query: String): List<RegionPreset> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return ALL_PRESETS
        }
        return ALL_PRESETS.filter { preset ->
            preset.countryName.contains(trimmed, ignoreCase = true) ||
                preset.englishName.contains(trimmed, ignoreCase = true) ||
                preset.isoCode.contains(trimmed, ignoreCase = true)
        }
    }
}

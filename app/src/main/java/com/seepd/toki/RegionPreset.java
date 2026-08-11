package com.seepd.toki;

enum RegionPreset {
    US("US", "美国", "310260", "T-Mobile"),
    CA("CA", "加拿大", "302720", "Rogers"),
    MX("MX", "墨西哥", "334020", "Telcel"),
    BR("BR", "巴西", "72405", "Claro"),
    AR("AR", "阿根廷", "722310", "Claro"),
    CL("CL", "智利", "73001", "Entel"),
    CO("CO", "哥伦比亚", "732101", "Claro"),
    PE("PE", "秘鲁", "71610", "Claro"),
    VE("VE", "委内瑞拉", "73404", "Movistar"),
    EC("EC", "厄瓜多尔", "74001", "Claro"),
    UY("UY", "乌拉圭", "74801", "Antel"),
    CR("CR", "哥斯达黎加", "71203", "Claro"),
    PA("PA", "巴拿马", "71403", "Claro"),
    GT("GT", "危地马拉", "70401", "Claro"),
    DO("DO", "多米尼加", "37002", "Claro"),
    GB("GB", "英国", "23410", "O2"),
    IE("IE", "爱尔兰", "27201", "Vodafone"),
    DE("DE", "德国", "26202", "Vodafone"),
    FR("FR", "法国", "20801", "Orange"),
    IT("IT", "意大利", "22201", "TIM"),
    ES("ES", "西班牙", "21403", "Vodafone"),
    NL("NL", "荷兰", "20404", "Vodafone"),
    BE("BE", "比利时", "20601", "Proximus"),
    AT("AT", "奥地利", "23201", "A1"),
    CH("CH", "瑞士", "22801", "Swisscom"),
    PL("PL", "波兰", "26001", "Plus"),
    SE("SE", "瑞典", "24001", "Telia"),
    NO("NO", "挪威", "24201", "Telenor"),
    DK("DK", "丹麦", "23801", "TDC"),
    FI("FI", "芬兰", "24405", "Elisa"),
    PT("PT", "葡萄牙", "26801", "Vodafone"),
    CZ("CZ", "捷克", "23001", "T-Mobile"),
    SK("SK", "斯洛伐克", "23102", "Telekom"),
    HU("HU", "匈牙利", "21630", "Telekom"),
    RO("RO", "罗马尼亚", "22601", "Vodafone"),
    BG("BG", "保加利亚", "28401", "A1"),
    GR("GR", "希腊", "20205", "Vodafone"),
    HR("HR", "克罗地亚", "21901", "Hrvatski Telekom"),
    RS("RS", "塞尔维亚", "22003", "A1"),
    SI("SI", "斯洛文尼亚", "29340", "A1"),
    EE("EE", "爱沙尼亚", "24801", "Telia"),
    LV("LV", "拉脱维亚", "24701", "LMT"),
    LT("LT", "立陶宛", "24601", "Telia"),
    IS("IS", "冰岛", "27401", "Siminn"),
    LU("LU", "卢森堡", "27001", "POST"),
    MT("MT", "马耳他", "27801", "Epic"),
    CY("CY", "塞浦路斯", "28001", "Cyta"),
    TR("TR", "土耳其", "28601", "Turkcell"),
    RU("RU", "俄罗斯", "25001", "MTS"),
    UA("UA", "乌克兰", "25501", "Vodafone"),
    IL("IL", "以色列", "42501", "Partner"),
    AE("AE", "阿联酋", "42402", "Etisalat"),
    SA("SA", "沙特阿拉伯", "42001", "STC"),
    QA("QA", "卡塔尔", "42701", "Ooredoo"),
    KW("KW", "科威特", "41902", "Zain"),
    BH("BH", "巴林", "42601", "Batelco"),
    OM("OM", "阿曼", "42202", "Omantel"),
    JO("JO", "约旦", "41601", "Zain"),
    LB("LB", "黎巴嫩", "41501", "Alfa"),
    IQ("IQ", "伊拉克", "41820", "Zain"),
    EG("EG", "埃及", "60201", "Orange"),
    ZA("ZA", "南非", "65501", "Vodacom"),
    MA("MA", "摩洛哥", "60400", "Orange"),
    DZ("DZ", "阿尔及利亚", "60301", "Mobilis"),
    TN("TN", "突尼斯", "60501", "Orange"),
    NG("NG", "尼日利亚", "62130", "MTN"),
    GH("GH", "加纳", "62001", "MTN"),
    KE("KE", "肯尼亚", "63902", "Safaricom"),
    TZ("TZ", "坦桑尼亚", "64004", "Vodacom"),
    UG("UG", "乌干达", "64110", "MTN"),
    ET("ET", "埃塞俄比亚", "63601", "Ethio Telecom"),
    SN("SN", "塞内加尔", "60801", "Orange"),
    AU("AU", "澳大利亚", "50501", "Telstra"),
    NZ("NZ", "新西兰", "53001", "One NZ"),
    FJ("FJ", "斐济", "54201", "Vodafone"),
    IN("IN", "印度", "40445", "Airtel"),
    PK("PK", "巴基斯坦", "41001", "Jazz"),
    BD("BD", "孟加拉国", "47001", "Grameenphone"),
    LK("LK", "斯里兰卡", "41302", "Dialog"),
    NP("NP", "尼泊尔", "42901", "Nepal Telecom"),
    JP("JP", "日本", "44010", "NTT DOCOMO"),
    KR("KR", "韩国", "45005", "SK Telecom"),
    CN("CN", "中国大陆", "46000", "China Mobile"),
    TW("TW", "中国台湾", "46692", "Chunghwa"),
    HK("HK", "中国香港", "45400", "HKT"),
    SG("SG", "新加坡", "52501", "Singtel"),
    MY("MY", "马来西亚", "50212", "Maxis"),
    TH("TH", "泰国", "52001", "AIS"),
    PH("PH", "菲律宾", "51502", "Globe"),
    ID("ID", "印度尼西亚", "51010", "Telkomsel"),
    VN("VN", "越南", "45204", "Viettel"),
    KH("KH", "柬埔寨", "45601", "Cellcard"),
    LA("LA", "老挝", "45701", "Lao Telecom"),
    MM("MM", "缅甸", "41401", "MPT"),
    MN("MN", "蒙古", "42899", "Unitel"),
    KZ("KZ", "哈萨克斯坦", "40101", "Beeline"),
    UZ("UZ", "乌兹别克斯坦", "43404", "Beeline"),
    GE("GE", "格鲁吉亚", "28201", "Silknet"),
    AM("AM", "亚美尼亚", "28301", "Team"),
    AZ("AZ", "阿塞拜疆", "40001", "Azercell");

    final String code;
    final String displayName;
    final String operator;
    final String operatorName;

    RegionPreset(String code, String displayName, String operator, String operatorName) {
        this.code = code;
        this.displayName = displayName;
        this.operator = operator;
        this.operatorName = operatorName;
    }

    static RegionPreset fromCode(String code) {
        for (RegionPreset preset : values()) {
            if (preset.code.equalsIgnoreCase(code)) {
                return preset;
            }
        }
        return US;
    }
}

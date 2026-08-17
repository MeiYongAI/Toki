package com.seepd.toki;

import java.util.Locale;

enum RegionPreset {
    US("US", "United States", "310260", "T-Mobile"),
    CA("CA", "Canada", "302720", "Rogers"),
    MX("MX", "Mexico", "334020", "Telcel"),
    BR("BR", "Brazil", "72405", "Claro"),
    AR("AR", "Argentina", "722310", "Claro"),
    CL("CL", "Chile", "73001", "Entel"),
    CO("CO", "Colombia", "732101", "Claro"),
    PE("PE", "Peru", "71610", "Claro"),
    VE("VE", "Venezuela", "73404", "Movistar"),
    EC("EC", "Ecuador", "74001", "Claro"),
    UY("UY", "Uruguay", "74801", "Antel"),
    CR("CR", "Costa Rica", "71203", "Claro"),
    PA("PA", "Panama", "71403", "Claro"),
    GT("GT", "Guatemala", "70401", "Claro"),
    DO("DO", "Dominican Republic", "37002", "Claro"),
    GB("GB", "United Kingdom", "23410", "O2"),
    IE("IE", "Ireland", "27201", "Vodafone"),
    DE("DE", "Germany", "26202", "Vodafone"),
    FR("FR", "France", "20801", "Orange"),
    IT("IT", "Italy", "22201", "TIM"),
    ES("ES", "Spain", "21403", "Vodafone"),
    NL("NL", "Netherlands", "20404", "Vodafone"),
    BE("BE", "Belgium", "20601", "Proximus"),
    AT("AT", "Austria", "23201", "A1"),
    CH("CH", "Switzerland", "22801", "Swisscom"),
    PL("PL", "Poland", "26001", "Plus"),
    SE("SE", "Sweden", "24001", "Telia"),
    NO("NO", "Norway", "24201", "Telenor"),
    DK("DK", "Denmark", "23801", "TDC"),
    FI("FI", "Finland", "24405", "Elisa"),
    PT("PT", "Portugal", "26801", "Vodafone"),
    CZ("CZ", "Czech Republic", "23001", "T-Mobile"),
    SK("SK", "Slovakia", "23102", "Telekom"),
    HU("HU", "Hungary", "21630", "Telekom"),
    RO("RO", "Romania", "22601", "Vodafone"),
    BG("BG", "Bulgaria", "28401", "A1"),
    GR("GR", "Greece", "20205", "Vodafone"),
    HR("HR", "Croatia", "21901", "Hrvatski Telekom"),
    RS("RS", "Serbia", "22003", "A1"),
    SI("SI", "Slovenia", "29340", "A1"),
    EE("EE", "Estonia", "24801", "Telia"),
    LV("LV", "Latvia", "24701", "LMT"),
    LT("LT", "Lithuania", "24601", "Telia"),
    IS("IS", "Iceland", "27401", "Siminn"),
    LU("LU", "Luxembourg", "27001", "POST"),
    MT("MT", "Malta", "27801", "Epic"),
    CY("CY", "Cyprus", "28001", "Cyta"),
    TR("TR", "Turkey", "28601", "Turkcell"),
    RU("RU", "Russia", "25001", "MTS"),
    UA("UA", "Ukraine", "25501", "Vodafone"),
    IL("IL", "Israel", "42501", "Partner"),
    AE("AE", "United Arab Emirates", "42402", "Etisalat"),
    SA("SA", "Saudi Arabia", "42001", "STC"),
    QA("QA", "Qatar", "42701", "Ooredoo"),
    KW("KW", "Kuwait", "41902", "Zain"),
    BH("BH", "Bahrain", "42601", "Batelco"),
    OM("OM", "Oman", "42202", "Omantel"),
    JO("JO", "Jordan", "41601", "Zain"),
    LB("LB", "Lebanon", "41501", "Alfa"),
    IQ("IQ", "Iraq", "41820", "Zain"),
    EG("EG", "Egypt", "60201", "Orange"),
    ZA("ZA", "South Africa", "65501", "Vodacom"),
    MA("MA", "Morocco", "60400", "Orange"),
    DZ("DZ", "Algeria", "60301", "Mobilis"),
    TN("TN", "Tunisia", "60501", "Orange"),
    NG("NG", "Nigeria", "62130", "MTN"),
    GH("GH", "Ghana", "62001", "MTN"),
    KE("KE", "Kenya", "63902", "Safaricom"),
    TZ("TZ", "Tanzania", "64004", "Vodacom"),
    UG("UG", "Uganda", "64110", "MTN"),
    ET("ET", "Ethiopia", "63601", "Ethio Telecom"),
    SN("SN", "Senegal", "60801", "Orange"),
    AU("AU", "Australia", "50501", "Telstra"),
    NZ("NZ", "New Zealand", "53001", "One NZ"),
    FJ("FJ", "Fiji", "54201", "Vodafone"),
    IN("IN", "India", "40445", "Airtel"),
    PK("PK", "Pakistan", "41001", "Jazz"),
    BD("BD", "Bangladesh", "47001", "Grameenphone"),
    LK("LK", "Sri Lanka", "41302", "Dialog"),
    NP("NP", "Nepal", "42901", "Nepal Telecom"),
    JP("JP", "Japan", "44010", "NTT DOCOMO"),
    KR("KR", "South Korea", "45005", "SK Telecom"),
    CN("CN", "Mainland China", "46000", "China Mobile"),
    TW("TW", "Taiwan", "46692", "Chunghwa"),
    HK("HK", "Hong Kong", "45400", "HKT"),
    SG("SG", "Singapore", "52501", "Singtel"),
    MY("MY", "Malaysia", "50212", "Maxis"),
    TH("TH", "Thailand", "52001", "AIS"),
    PH("PH", "Philippines", "51502", "Globe"),
    ID("ID", "Indonesia", "51010", "Telkomsel"),
    VN("VN", "Vietnam", "45204", "Viettel"),
    KH("KH", "Cambodia", "45601", "Cellcard"),
    LA("LA", "Laos", "45701", "Lao Telecom"),
    MM("MM", "Myanmar", "41401", "MPT"),
    MN("MN", "Mongolia", "42899", "Unitel"),
    KZ("KZ", "Kazakhstan", "40101", "Beeline"),
    UZ("UZ", "Uzbekistan", "43404", "Beeline"),
    GE("GE", "Georgia", "28201", "Silknet"),
    AM("AM", "Armenia", "28301", "Team"),
    AZ("AZ", "Azerbaijan", "40001", "Azercell");

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

    String localizedDisplayName(Locale locale) {
        String localizedName = new Locale("", code).getDisplayCountry(locale);
        return localizedName.isEmpty() ? displayName : localizedName;
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

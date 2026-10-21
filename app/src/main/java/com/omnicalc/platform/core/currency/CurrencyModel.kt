package com.omnicalc.platform.core.currency

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.telephony.TelephonyManager
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.TimeZone

enum class FreshnessStatus(val label: String) {
    CURRENT("Live"),
    RECENT("Recent"),
    STALE("Stale (Older than 24h)"),
    OFFLINE("Offline (Saved rates)"),
    UNAVAILABLE("No rates available")
}

enum class RateSource(val label: String) {
    LIVE("Live"),
    RECENT("Recent"),
    STALE("Stale (Older than 24h)"),
    OFFLINE_REFERENCE("Offline Reference"),
    MANUAL("Manual Override")
}

enum class ConversionSourceType(val label: String) {
    LIVE("Live"),
    MIXED("Mixed"),
    RECENT("Recent"),
    STALE("Stale"),
    OFFLINE_REFERENCE("Offline Reference"),
    MANUAL("Manual Override")
}

data class ConversionResolution(
    val sourceType: ConversionSourceType,
    val fromSource: RateSource,
    val toSource: RateSource,
    val summary: String,
    val fromCode: String,
    val toCode: String
)

data class CurrencyInfo(
    val code: String,
    val name: String,
    val symbol: String,
    val country: String,
    val decimalPlaces: Int = 2,
    val flag: String = ""
)

data class CurrencyRateSet(
    val baseCurrency: String,
    val rates: Map<String, Double>,
    val timestampMs: Long,
    val providerName: String,
    val status: FreshnessStatus = FreshnessStatus.RECENT,
    val rateSources: Map<String, RateSource> = emptyMap()
) {
    fun resolveConversion(
        fromCode: String,
        toCode: String,
        overrides: Map<String, Double> = emptyMap()
    ): ConversionResolution {
        val fromUpper = fromCode.uppercase()
        val toUpper = toCode.uppercase()

        val fromSrc = if (overrides.containsKey(fromUpper)) {
            RateSource.MANUAL
        } else if (fromUpper == baseCurrency) {
            when (status) {
                FreshnessStatus.CURRENT -> RateSource.LIVE
                FreshnessStatus.RECENT -> RateSource.RECENT
                FreshnessStatus.STALE -> RateSource.STALE
                FreshnessStatus.OFFLINE, FreshnessStatus.UNAVAILABLE -> RateSource.OFFLINE_REFERENCE
            }
        } else {
            rateSources[fromUpper] ?: if (rateSources.isNotEmpty()) {
                RateSource.OFFLINE_REFERENCE
            } else {
                when (status) {
                    FreshnessStatus.CURRENT -> RateSource.LIVE
                    FreshnessStatus.RECENT -> RateSource.RECENT
                    FreshnessStatus.STALE -> RateSource.STALE
                    FreshnessStatus.OFFLINE, FreshnessStatus.UNAVAILABLE -> RateSource.OFFLINE_REFERENCE
                }
            }
        }

        val toSrc = if (overrides.containsKey(toUpper)) {
            RateSource.MANUAL
        } else if (toUpper == baseCurrency) {
            when (status) {
                FreshnessStatus.CURRENT -> RateSource.LIVE
                FreshnessStatus.RECENT -> RateSource.RECENT
                FreshnessStatus.STALE -> RateSource.STALE
                FreshnessStatus.OFFLINE, FreshnessStatus.UNAVAILABLE -> RateSource.OFFLINE_REFERENCE
            }
        } else {
            rateSources[toUpper] ?: if (rateSources.isNotEmpty()) {
                RateSource.OFFLINE_REFERENCE
            } else {
                when (status) {
                    FreshnessStatus.CURRENT -> RateSource.LIVE
                    FreshnessStatus.RECENT -> RateSource.RECENT
                    FreshnessStatus.STALE -> RateSource.STALE
                    FreshnessStatus.OFFLINE, FreshnessStatus.UNAVAILABLE -> RateSource.OFFLINE_REFERENCE
                }
            }
        }

        // Determine combined conversion source type
        val isDirectBasePair = (fromUpper == baseCurrency && toSrc == RateSource.MANUAL) ||
                (toUpper == baseCurrency && fromSrc == RateSource.MANUAL) ||
                (fromSrc == RateSource.MANUAL && toSrc == RateSource.MANUAL)

        val type: ConversionSourceType
        val summary: String

        if (fromSrc == RateSource.MANUAL && toSrc == RateSource.MANUAL) {
            type = ConversionSourceType.MANUAL
            summary = "Source: Manual Override"
        } else if (isDirectBasePair) {
            type = ConversionSourceType.MANUAL
            summary = "Source: Manual Override"
        } else if (fromSrc == RateSource.MANUAL || toSrc == RateSource.MANUAL) {
            type = ConversionSourceType.MIXED
            summary = "Source: Mixed ($fromUpper: ${fromSrc.label}, $toUpper: ${toSrc.label})"
        } else if (fromSrc == RateSource.OFFLINE_REFERENCE && toSrc == RateSource.OFFLINE_REFERENCE) {
            type = ConversionSourceType.OFFLINE_REFERENCE
            summary = "Source: Offline Reference"
        } else if (fromSrc == RateSource.OFFLINE_REFERENCE || toSrc == RateSource.OFFLINE_REFERENCE) {
            type = ConversionSourceType.MIXED
            summary = "Source: Mixed ($fromUpper: ${fromSrc.label}, $toUpper: ${toSrc.label})"
        } else if (fromSrc == RateSource.STALE || toSrc == RateSource.STALE) {
            type = ConversionSourceType.STALE
            summary = "Source: Stale"
        } else if (fromSrc == RateSource.RECENT || toSrc == RateSource.RECENT) {
            type = ConversionSourceType.RECENT
            summary = "Source: Recent"
        } else if (fromSrc == RateSource.LIVE && toSrc == RateSource.LIVE) {
            type = ConversionSourceType.LIVE
            summary = "Source: Live"
        } else {
            type = ConversionSourceType.OFFLINE_REFERENCE
            summary = "Source: Offline Reference"
        }

        return ConversionResolution(
            sourceType = type,
            fromSource = fromSrc,
            toSource = toSrc,
            summary = summary,
            fromCode = fromUpper,
            toCode = toUpper
        )
    }
}

object CurrencyCatalog {
    val ALL_CURRENCIES: List<CurrencyInfo> = listOf(
        // East Africa & Horn of Africa (First-Class Regional Priority)
        CurrencyInfo("KES", "Kenyan Shilling", "KSh", "Kenya", 2, "🇰🇪"),
        CurrencyInfo("TZS", "Tanzanian Shilling", "TSh", "Tanzania", 2, "🇹🇿"),
        CurrencyInfo("KMF", "Comorian Franc", "CF", "Comoros", 0, "🇰🇲"),
        CurrencyInfo("UGX", "Ugandan Shilling", "USh", "Uganda", 0, "🇺🇬"),
        CurrencyInfo("RWF", "Rwandan Franc", "FRw", "Rwanda", 0, "🇷🇼"),
        CurrencyInfo("BIF", "Burundian Franc", "FBu", "Burundi", 0, "🇧🇮"),
        CurrencyInfo("ETB", "Ethiopian Birr", "Br", "Ethiopia", 2, "🇪🇹"),
        CurrencyInfo("SOS", "Somali Shilling", "Sh", "Somalia", 2, "🇸🇴"),
        CurrencyInfo("SSP", "South Sudanese Pound", "SS£", "South Sudan", 2, "🇸🇸"),
        CurrencyInfo("DJF", "Djiboutian Franc", "Fdj", "Djibouti", 0, "🇩🇯"),
        CurrencyInfo("SCR", "Seychellois Rupee", "SR", "Seychelles", 2, "🇸🇨"),
        CurrencyInfo("MUR", "Mauritian Rupee", "Rs", "Mauritius", 2, "🇲🇺"),
        CurrencyInfo("MGA", "Malagasy Ariary", "Ar", "Madagascar", 0, "🇲🇬"),
        CurrencyInfo("ERN", "Eritrean Nakfa", "Nfk", "Eritrea", 2, "🇪🇷"),

        // West & Central Africa
        CurrencyInfo("NGN", "Nigerian Naira", "₦", "Nigeria", 2, "🇳🇬"),
        CurrencyInfo("GHS", "Ghanaian Cedi", "GH₵", "Ghana", 2, "🇬🇭"),
        CurrencyInfo("XOF", "West African CFA Franc", "CFA", "West Africa (BCEAO)", 0, "🌍"),
        CurrencyInfo("XAF", "Central African CFA Franc", "FCFA", "Central Africa (BEAC)", 0, "🌍"),
        CurrencyInfo("GMD", "Gambian Dalasi", "D", "Gambia", 2, "🇬🇲"),
        CurrencyInfo("GNF", "Guinean Franc", "FG", "Guinea", 0, "🇬🇳"),
        CurrencyInfo("SLL", "Sierra Leonean Leone (Old)", "Le", "Sierra Leone", 2, "🇸🇱"),
        CurrencyInfo("SLE", "Sierra Leonean Leone", "Le", "Sierra Leone", 2, "🇸🇱"),
        CurrencyInfo("LRD", "Liberian Dollar", "L$", "Liberia", 2, "🇱🇷"),
        CurrencyInfo("CVE", "Cape Verdean Escudo", "Esc", "Cape Verde", 2, "🇨🇻"),
        CurrencyInfo("STN", "São Tomé & Príncipe Dobra", "Db", "São Tomé & Príncipe", 2, "🇸🇹"),
        CurrencyInfo("MRU", "Mauritanian Ouguiya", "UM", "Mauritania", 2, "🇲🇷"),
        CurrencyInfo("CDF", "Congolese Franc", "FC", "DR Congo", 2, "🇨🇩"),
        CurrencyInfo("AOA", "Angolan Kwanza", "Kz", "Angola", 2, "🇦🇴"),

        // North & Southern Africa
        CurrencyInfo("ZAR", "South African Rand", "R", "South Africa", 2, "🇿🇦"),
        CurrencyInfo("EGP", "Egyptian Pound", "E£", "Egypt", 2, "🇪🇬"),
        CurrencyInfo("MAD", "Moroccan Dirham", "MAD", "Morocco", 2, "🇲🇦"),
        CurrencyInfo("DZD", "Algerian Dinar", "DA", "Algeria", 2, "🇩🇿"),
        CurrencyInfo("TND", "Tunisian Dinar", "DT", "Tunisia", 3, "🇹🇳"),
        CurrencyInfo("LYD", "Libyan Dinar", "LD", "Libya", 3, "🇱🇾"),
        CurrencyInfo("SDG", "Sudanese Pound", "SDG", "Sudan", 2, "🇸🇩"),
        CurrencyInfo("ZMW", "Zambian Kwacha", "ZK", "Zambia", 2, "🇿🇲"),
        CurrencyInfo("MWK", "Malawian Kwacha", "MK", "Malawi", 2, "🇲🇼"),
        CurrencyInfo("MZN", "Mozambican Metical", "MT", "Mozambique", 2, "🇲🇿"),
        CurrencyInfo("BWP", "Botswana Pula", "P", "Botswana", 2, "🇧🇼"),
        CurrencyInfo("NAD", "Namibian Dollar", "N$", "Namibia", 2, "🇳🇦"),
        CurrencyInfo("SZL", "Eswatini Lilangeni", "E", "Eswatini", 2, "🇸🇿"),
        CurrencyInfo("LSL", "Lesotho Loti", "L", "Lesotho", 2, "🇱🇸"),
        CurrencyInfo("ZWL", "Zimbabwean Dollar", "Z$", "Zimbabwe", 2, "🇿🇼"),

        // Major Global Currencies
        CurrencyInfo("USD", "United States Dollar", "$", "United States", 2, "🇺🇸"),
        CurrencyInfo("EUR", "Euro", "€", "Eurozone", 2, "🇪🇺"),
        CurrencyInfo("GBP", "British Pound", "£", "United Kingdom", 2, "🇬🇧"),
        CurrencyInfo("JPY", "Japanese Yen", "¥", "Japan", 0, "🇯🇵"),
        CurrencyInfo("CNY", "Chinese Yuan", "¥", "China", 2, "🇨🇳"),
        CurrencyInfo("CNH", "Chinese Yuan Offshore", "¥", "China (Offshore)", 2, "🇨🇳"),
        CurrencyInfo("CAD", "Canadian Dollar", "CA$", "Canada", 2, "🇨🇦"),
        CurrencyInfo("AUD", "Australian Dollar", "A$", "Australia", 2, "🇦🇺"),
        CurrencyInfo("CHF", "Swiss Franc", "CHF", "Switzerland", 2, "🇨🇭"),
        CurrencyInfo("INR", "Indian Rupee", "₹", "India", 2, "🇮🇳"),

        // Middle East & Central Asia
        CurrencyInfo("AED", "UAE Dirham", "AED", "United Arab Emirates", 2, "🇦🇪"),
        CurrencyInfo("SAR", "Saudi Riyal", "SAR", "Saudi Arabia", 2, "🇸🇦"),
        CurrencyInfo("QAR", "Qatari Riyal", "QR", "Qatar", 2, "🇶🇦"),
        CurrencyInfo("KWD", "Kuwaiti Dinar", "KD", "Kuwait", 3, "🇰🇼"),
        CurrencyInfo("BHD", "Bahraini Dinar", "BD", "Bahrain", 3, "🇧🇭"),
        CurrencyInfo("OMR", "Omani Rial", "OMR", "Oman", 3, "🇴🇲"),
        CurrencyInfo("ILS", "Israeli New Shekel", "₪", "Israel", 2, "🇮🇱"),
        CurrencyInfo("JOD", "Jordanian Dinar", "JD", "Jordan", 3, "🇯🇴"),
        CurrencyInfo("LBP", "Lebanese Pound", "L£", "Lebanon", 2, "🇱🇧"),
        CurrencyInfo("IQD", "Iraqi Dinar", "IQD", "Iraq", 3, "🇮🇶"),
        CurrencyInfo("SYP", "Syrian Pound", "S£", "Syria", 2, "🇸🇾"),
        CurrencyInfo("YER", "Yemeni Rial", "YR", "Yemen", 2, "🇾🇪"),
        CurrencyInfo("IRR", "Iranian Rial", "IRR", "Iran", 2, "🇮🇷"),

        // Europe & Eastern Europe
        CurrencyInfo("SEK", "Swedish Krona", "kr", "Sweden", 2, "🇸🇪"),
        CurrencyInfo("NOK", "Norwegian Krone", "kr", "Norway", 2, "🇳🇴"),
        CurrencyInfo("DKK", "Danish Krone", "kr", "Denmark", 2, "🇩🇰"),
        CurrencyInfo("ISK", "Icelandic Króna", "kr", "Iceland", 0, "🇮🇸"),
        CurrencyInfo("PLN", "Polish Zloty", "zł", "Poland", 2, "🇵🇱"),
        CurrencyInfo("CZK", "Czech Koruna", "Kč", "Czech Republic", 2, "🇨🇿"),
        CurrencyInfo("HUF", "Hungarian Forint", "Ft", "Hungary", 2, "🇭🇺"),
        CurrencyInfo("RON", "Romanian Leu", "lei", "Romania", 2, "🇷🇴"),
        CurrencyInfo("BGN", "Bulgarian Lev", "лв", "Bulgaria", 2, "🇧🇬"),
        CurrencyInfo("RSD", "Serbian Dinar", "дин.", "Serbia", 2, "🇷🇸"),
        CurrencyInfo("BAM", "Bosnia Convertible Mark", "KM", "Bosnia & Herzegovina", 2, "🇧🇦"),
        CurrencyInfo("MKD", "Macedonian Denar", "ден", "North Macedonia", 2, "🇲🇰"),
        CurrencyInfo("ALL", "Albanian Lek", "L", "Albania", 2, "🇦🇱"),
        CurrencyInfo("MDL", "Moldovan Leu", "L", "Moldova", 2, "🇲🇩"),
        CurrencyInfo("UAH", "Ukrainian Hryvnia", "₴", "Ukraine", 2, "🇺🇦"),
        CurrencyInfo("BYN", "Belarusian Ruble", "Br", "Belarus", 2, "🇧🇾"),
        CurrencyInfo("RUB", "Russian Ruble", "₽", "Russia", 2, "🇷🇺"),
        CurrencyInfo("TRY", "Turkish Lira", "₺", "Turkey", 2, "🇹🇷"),
        CurrencyInfo("GEL", "Georgian Lari", "₾", "Georgia", 2, "🇬🇪"),
        CurrencyInfo("AMD", "Armenian Dram", "֏", "Armenia", 2, "🇦🇲"),
        CurrencyInfo("AZN", "Azerbaijani Manat", "₼", "Azerbaijan", 2, "🇦🇿"),
        CurrencyInfo("GIP", "Gibraltar Pound", "£", "Gibraltar", 2, "🇬🇮"),
        CurrencyInfo("JEP", "Jersey Pound", "£", "Jersey", 2, "🇯🇪"),
        CurrencyInfo("GGP", "Guernsey Pound", "£", "Guernsey", 2, "🇬🇬"),
        CurrencyInfo("IMP", "Isle of Man Pound", "£", "Isle of Man", 2, "🇮🇲"),

        // Asia & Southeast Asia
        CurrencyInfo("SGD", "Singapore Dollar", "S$", "Singapore", 2, "🇸🇬"),
        CurrencyInfo("HKD", "Hong Kong Dollar", "HK$", "Hong Kong", 2, "🇭🇰"),
        CurrencyInfo("KRW", "South Korean Won", "₩", "South Korea", 0, "🇰🇷"),
        CurrencyInfo("KPW", "North Korean Won", "₩", "North Korea", 0, "🇰🇵"),
        CurrencyInfo("TWD", "New Taiwan Dollar", "NT$", "Taiwan", 2, "🇹🇼"),
        CurrencyInfo("IDR", "Indonesian Rupiah", "Rp", "Indonesia", 0, "🇮🇩"),
        CurrencyInfo("MYR", "Malaysian Ringgit", "RM", "Malaysia", 2, "🇲🇾"),
        CurrencyInfo("PHP", "Philippine Peso", "₱", "Philippines", 2, "🇵🇭"),
        CurrencyInfo("THB", "Thai Baht", "฿", "Thailand", 2, "🇹🇭"),
        CurrencyInfo("VND", "Vietnamese Dong", "₫", "Vietnam", 0, "🇻🇳"),
        CurrencyInfo("PKR", "Pakistani Rupee", "₨", "Pakistan", 2, "🇵🇰"),
        CurrencyInfo("BDT", "Bangladeshi Taka", "৳", "Bangladesh", 2, "🇧🇩"),
        CurrencyInfo("LKR", "Sri Lankan Rupee", "Rs", "Sri Lanka", 2, "🇱🇰"),
        CurrencyInfo("NPR", "Nepalese Rupee", "Rs", "Nepal", 2, "🇳🇵"),
        CurrencyInfo("AFN", "Afghan Afghani", "؋", "Afghanistan", 2, "🇦🇫"),
        CurrencyInfo("MVR", "Maldivian Rufiyaa", "Rf", "Maldives", 2, "🇲🇻"),
        CurrencyInfo("BTN", "Bhutanese Ngultrum", "Nu.", "Bhutan", 2, "🇧🇹"),
        CurrencyInfo("MMK", "Myanmar Kyat", "K", "Myanmar", 2, "🇲🇲"),
        CurrencyInfo("KHR", "Cambodian Riel", "៛", "Cambodia", 2, "🇰🇭"),
        CurrencyInfo("LAK", "Lao Kip", "₭", "Laos", 0, "🇱🇦"),
        CurrencyInfo("BND", "Brunei Dollar", "B$", "Brunei", 2, "🇧🇳"),
        CurrencyInfo("MNT", "Mongolian Tugrik", "₮", "Mongolia", 2, "🇲🇳"),
        CurrencyInfo("KZT", "Kazakhstani Tenge", "₸", "Kazakhstan", 2, "🇰🇿"),
        CurrencyInfo("UZS", "Uzbekistani Som", "so'm", "Uzbekistan", 2, "🇺🇿"),
        CurrencyInfo("TMT", "Turkmenistani Manat", "m", "Turkmenistan", 2, "🇹🇲"),
        CurrencyInfo("TJS", "Tajikistani Somoni", "SM", "Tajikistan", 2, "🇹🇯"),
        CurrencyInfo("KGS", "Kyrgyzstani Som", "с", "Kyrgyzstan", 2, "🇰🇬"),

        // Americas & Caribbean
        CurrencyInfo("BRL", "Brazilian Real", "R$", "Brazil", 2, "🇧🇷"),
        CurrencyInfo("MXN", "Mexican Peso", "Mex$", "Mexico", 2, "🇲🇽"),
        CurrencyInfo("ARS", "Argentine Peso", "ARS$", "Argentina", 2, "🇦🇷"),
        CurrencyInfo("CLP", "Chilean Peso", "CLP$", "Chile", 0, "🇨🇱"),
        CurrencyInfo("COP", "Colombian Peso", "COL$", "Colombia", 2, "🇨🇴"),
        CurrencyInfo("PEN", "Peruvian Sol", "S/", "Peru", 2, "🇵🇪"),
        CurrencyInfo("UYU", "Uruguayan Peso", "\$U", "Uruguay", 2, "🇺🇾"),
        CurrencyInfo("PYG", "Paraguayan Guaraní", "₲", "Paraguay", 0, "🇵🇾"),
        CurrencyInfo("BOB", "Bolivian Boliviano", "Bs.", "Bolivia", 2, "🇧🇴"),
        CurrencyInfo("VES", "Venezuelan Bolívar", "Bs.D", "Venezuela", 2, "🇻🇪"),
        CurrencyInfo("GTQ", "Guatemalan Quetzal", "Q", "Guatemala", 2, "🇬🇹"),
        CurrencyInfo("HNL", "Honduran Lempira", "L", "Honduras", 2, "🇭🇳"),
        CurrencyInfo("NIO", "Nicaraguan Córdoba", "C$", "Nicaragua", 2, "🇳🇮"),
        CurrencyInfo("CRC", "Costa Rican Colón", "₡", "Costa Rica", 2, "🇨🇷"),
        CurrencyInfo("PAB", "Panamanian Balboa", "B/.", "Panama", 2, "🇵🇦"),
        CurrencyInfo("DOP", "Dominican Peso", "RD$", "Dominican Republic", 2, "🇩🇴"),
        CurrencyInfo("JMD", "Jamaican Dollar", "J$", "Jamaica", 2, "🇯🇲"),
        CurrencyInfo("TTD", "Trinidad & Tobago Dollar", "TT$", "Trinidad & Tobago", 2, "🇹🇹"),
        CurrencyInfo("BSD", "Bahamian Dollar", "B$", "Bahamas", 2, "🇧🇸"),
        CurrencyInfo("BBD", "Barbadian Dollar", "Bds$", "Barbados", 2, "🇧🇧"),
        CurrencyInfo("BZD", "Belize Dollar", "BZ$", "Belize", 2, "🇧🇿"),
        CurrencyInfo("GYD", "Guyanese Dollar", "G$", "Guyana", 2, "🇬🇾"),
        CurrencyInfo("SRD", "Surinamese Dollar", "Sr$", "Suriname", 2, "🇸🇷"),
        CurrencyInfo("HTG", "Haitian Gourde", "G", "Haiti", 2, "🇭🇹"),
        CurrencyInfo("XCD", "East Caribbean Dollar", "EC$", "East Caribbean States", 2, "🇦🇬"),
        CurrencyInfo("AWG", "Aruban Florin", "Afl.", "Aruba", 2, "🇦🇼"),
        CurrencyInfo("ANG", "Netherlands Antillean Guilder", "NAƒ", "Curaçao & Sint Maarten", 2, "🇨🇼"),
        CurrencyInfo("KYD", "Cayman Islands Dollar", "CI$", "Cayman Islands", 2, "🇰🇾"),
        CurrencyInfo("BMD", "Bermudian Dollar", "BD$", "Bermuda", 2, "🇧🇲"),
        CurrencyInfo("FKP", "Falkland Islands Pound", "FK£", "Falkland Islands", 2, "🇫🇰"),

        // Oceania & Pacific
        CurrencyInfo("NZD", "New Zealand Dollar", "NZ$", "New Zealand", 2, "🇳🇿"),
        CurrencyInfo("PGK", "Papua New Guinean Kina", "K", "Papua New Guinea", 2, "🇵🇬"),
        CurrencyInfo("FJD", "Fijian Dollar", "FJ$", "Fiji", 2, "🇫🇯"),
        CurrencyInfo("SBD", "Solomon Islands Dollar", "SI$", "Solomon Islands", 2, "🇸🇧"),
        CurrencyInfo("VUV", "Vanuatu Vatu", "VT", "Vanuatu", 0, "🇻🇺"),
        CurrencyInfo("WST", "Samoan Tala", "WS$", "Samoa", 2, "🇼🇸"),
        CurrencyInfo("TOP", "Tongan Paʻanga", "T$", "Tonga", 2, "🇹🇴"),
        CurrencyInfo("XPF", "CFP Franc", "F", "French Pacific (Tahiti, New Caledonia)", 0, "🇵🇫")
    )

    /**
     * All world currencies sorted strictly in alphabetical order by currency name.
     */
    val ALL_ALPHABETICAL: List<CurrencyInfo> by lazy {
        ALL_CURRENCIES.sortedWith(compareBy({ it.name.lowercase(Locale.ROOT) }, { it.code }))
    }

    private val lookupMap: Map<String, CurrencyInfo> = ALL_CURRENCIES.associateBy { it.code.uppercase() }

    fun find(code: String): CurrencyInfo {
        val upper = code.uppercase()
        return lookupMap[upper] ?: CurrencyInfo(upper, upper, upper, "", 2, "🌐")
    }

    fun search(query: String): List<CurrencyInfo> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return ALL_ALPHABETICAL
        return ALL_ALPHABETICAL.filter {
            it.code.lowercase(Locale.ROOT).contains(q) ||
            it.name.lowercase(Locale.ROOT).contains(q) ||
            it.country.lowercase(Locale.ROOT).contains(q) ||
            it.symbol.lowercase(Locale.ROOT).contains(q)
        }
    }

    data class DetectedLocationInfo(
        val currency: CurrencyInfo,
        val countryName: String,
        val countryCode: String
    )

    fun detectLocalCurrency(context: Context? = null): DetectedLocationInfo {
        val isoToCode = mapOf(
            "KE" to "KES", "TZ" to "TZS", "UG" to "UGX", "RW" to "RWF", "BI" to "BIF",
            "ET" to "ETB", "SO" to "SOS", "SS" to "SSP", "KM" to "KMF", "DJ" to "DJF",
            "ER" to "ERN", "SC" to "SCR", "MU" to "MUR", "MG" to "MGA", "NG" to "NGN",
            "GH" to "GHS", "EG" to "EGP", "MA" to "MAD", "DZ" to "DZD", "TN" to "TND",
            "LY" to "LYD", "SD" to "SDG", "ZA" to "ZAR", "ZW" to "ZWL", "ZM" to "ZMW",
            "MW" to "MWK", "MZ" to "MZN", "BW" to "BWP", "NA" to "NAD", "SZ" to "SZL",
            "LS" to "LSL", "AO" to "AOA", "CD" to "CDF", "US" to "USD", "GB" to "GBP",
            "CA" to "CAD", "AU" to "AUD", "IN" to "INR", "CN" to "CNY", "JP" to "JPY",
            "AE" to "AED", "SA" to "SAR", "QA" to "QAR", "KW" to "KWD", "BH" to "BHD",
            "OM" to "OMR", "IL" to "ILS", "JO" to "JOD", "LB" to "LBP", "IQ" to "IQD",
            "KR" to "KRW", "KP" to "KPW", "BR" to "BRL", "MX" to "MXN", "AR" to "ARS",
            "CL" to "CLP", "CO" to "COP", "PE" to "PEN", "CH" to "CHF", "NO" to "NOK",
            "SE" to "SEK", "DK" to "DKK", "PL" to "PLN", "CZ" to "CZK", "TR" to "TRY",
            "RU" to "RUB", "SG" to "SGD", "MY" to "MYR", "TH" to "THB", "ID" to "IDR",
            "PH" to "PHP", "VN" to "VND", "PK" to "PKR", "BD" to "BDT", "LK" to "LKR",
            "DE" to "EUR", "FR" to "EUR", "IT" to "EUR", "ES" to "EUR", "PT" to "EUR",
            "NL" to "EUR", "BE" to "EUR", "AT" to "EUR", "GR" to "EUR", "FI" to "EUR",
            "IE" to "EUR", "SK" to "EUR", "SI" to "EUR", "CY" to "EUR", "MT" to "EUR",
            "EE" to "EUR", "LV" to "EUR", "LT" to "EUR", "HR" to "EUR"
        )

        // Tier 1: Telephony Manager (Cellular Network & SIM Card) - instant physical device truth (< 0.1ms, zero network lag)
        if (context != null) {
            try {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                if (telephonyManager != null) {
                    // 1a: Network Country ISO (registered cell tower country)
                    val networkCountry = telephonyManager.networkCountryIso?.trim()?.uppercase(Locale.ROOT)
                    if (!networkCountry.isNullOrBlank() && isoToCode.containsKey(networkCountry)) {
                        val currCode = isoToCode[networkCountry]!!
                        val info = find(currCode)
                        val displayName = if (info.country.isNotBlank()) info.country else networkCountry
                        return DetectedLocationInfo(info, displayName, networkCountry)
                    }

                    // 1b: SIM Country ISO (inserted SIM card country)
                    val simCountry = telephonyManager.simCountryIso?.trim()?.uppercase(Locale.ROOT)
                    if (!simCountry.isNullOrBlank() && isoToCode.containsKey(simCountry)) {
                        val currCode = isoToCode[simCountry]!!
                        val info = find(currCode)
                        val displayName = if (info.country.isNotBlank()) info.country else simCountry
                        return DetectedLocationInfo(info, displayName, simCountry)
                    }
                }
            } catch (_: Exception) {}
        }

        // Tier 2: Physical Device TimeZone - instantaneous physical device location (< 0.01ms)
        val tz = TimeZone.getDefault().id
        val tzMapping = mapOf(
            "Africa/Nairobi" to Pair("KES", "Kenya"),
            "Africa/Dar_es_Salaam" to Pair("TZS", "Tanzania"),
            "Africa/Kampala" to Pair("UGX", "Uganda"),
            "Africa/Kigali" to Pair("RWF", "Rwanda"),
            "Africa/Bujumbura" to Pair("BIF", "Burundi"),
            "Africa/Addis_Ababa" to Pair("ETB", "Ethiopia"),
            "Africa/Mogadishu" to Pair("SOS", "Somalia"),
            "Africa/Cairo" to Pair("EGP", "Egypt"),
            "Africa/Lagos" to Pair("NGN", "Nigeria"),
            "Africa/Accra" to Pair("GHS", "Ghana"),
            "Africa/Johannesburg" to Pair("ZAR", "South Africa"),
            "Africa/Lusaka" to Pair("ZMW", "Zambia"),
            "Africa/Harare" to Pair("ZWL", "Zimbabwe"),
            "Europe/London" to Pair("GBP", "United Kingdom"),
            "America/New_York" to Pair("USD", "United States"),
            "America/Chicago" to Pair("USD", "United States"),
            "America/Los_Angeles" to Pair("USD", "United States")
        )
        for ((tzKey, pair) in tzMapping) {
            if (tz.contains(tzKey, ignoreCase = true)) {
                return DetectedLocationInfo(find(pair.first), pair.second, pair.first.take(2))
            }
        }

        // Tier 3: Instant Locale Fallback (<0.01ms) - synchronous path never blocks on network Geocoder

        // Tier 4: Locale Fallback
        val defaultLocale = Locale.getDefault()
        val countryIso = defaultLocale.country?.uppercase(Locale.ROOT) ?: ""
        val countryDisplay = defaultLocale.displayCountry.ifBlank { "Local Region" }

        if (countryIso.isNotEmpty() && isoToCode.containsKey(countryIso)) {
            val currCode = isoToCode[countryIso]!!
            val info = find(currCode)
            val name = if (info.country.isNotBlank()) info.country else countryDisplay
            return DetectedLocationInfo(info, name, countryIso)
        }

        try {
            val javaCurr = java.util.Currency.getInstance(defaultLocale)
            if (javaCurr != null) {
                val info = find(javaCurr.currencyCode)
                if (info.code == javaCurr.currencyCode) {
                    return DetectedLocationInfo(info, countryDisplay, countryIso)
                }
            }
        } catch (_: Exception) {}

        return DetectedLocationInfo(find("KES"), "Kenya", "KE")
    }

    fun detectLocalCurrency(): DetectedLocationInfo = detectLocalCurrency(null)

    fun formatAmount(amount: BigDecimal, currency: CurrencyInfo, locale: Locale = Locale.getDefault()): String {
        val symbols = DecimalFormatSymbols.getInstance(locale)
        val df = DecimalFormat().apply {
            decimalFormatSymbols = symbols
            maximumFractionDigits = currency.decimalPlaces
            minimumFractionDigits = currency.decimalPlaces
            isGroupingUsed = true
        }
        return df.format(amount)
    }
}

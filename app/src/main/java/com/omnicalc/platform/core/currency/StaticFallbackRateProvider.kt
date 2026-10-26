package com.omnicalc.platform.core.currency

data class StaticDatasetMetadata(
    val version: String = "2024.10-REF",
    val datasetDate: String = "October 2024",
    val lastUpdateDate: String = "2024-10-01",
    val sourceReference: String = "Consolidated Central Bank, ECB & IMF Reference Rates",
    val description: String = "Static embedded rates provide an emergency offline baseline and reference values for currencies unavailable in live feeds. They are not live financial market data and can differ substantially from current rates."
) {
    /** Currencies in the embedded table, counted rather than stated (it once claimed 172). */
    val currencyCount: Int get() = StaticFallbackRateProvider.STATIC_RATES.size
}

class StaticFallbackRateProvider : CurrencyRateProvider {
    override val providerName: String = "Offline Reference Rates"

    override suspend fun fetchRates(baseCurrency: String): Result<CurrencyRateSet> {
        return Result.success(
            CurrencyRateSet(
                baseCurrency = "USD",
                rates = STATIC_RATES,
                timestampMs = METADATA_TIMESTAMP_MS,
                providerName = providerName,
                status = FreshnessStatus.OFFLINE,
                rateSources = STATIC_RATES.keys.associateWith { RateSource.OFFLINE_REFERENCE }
            )
        )
    }

    fun getStaticRatesMap(): Map<String, Double> = STATIC_RATES

    companion object {
        val METADATA = StaticDatasetMetadata()
        const val METADATA_TIMESTAMP_MS = 1727740800000L // 2024-10-01 00:00:00 UTC

        val STATIC_RATES: Map<String, Double> = mapOf(
            // Base & East / Horn of Africa
            "USD" to 1.0,
            "KES" to 129.50,
            "TZS" to 2605.00,
            "KMF" to 452.80,
            "UGX" to 3690.00,
            "RWF" to 1355.00,
            "BIF" to 2915.00,
            "ETB" to 122.50,
            "SOS" to 571.00,
            "SSP" to 130.00,
            "DJF" to 177.72,
            "SCR" to 14.15,
            "MUR" to 46.20,
            "MGA" to 4625.00,
            "ERN" to 15.00,

            // West & Central Africa
            "NGN" to 1645.00,
            "GHS" to 15.75,
            "XOF" to 604.20,
            "XAF" to 604.20,
            "GMD" to 71.50,
            "GNF" to 8610.00,
            "SLL" to 22600.00,
            "SLE" to 22.60,
            "LRD" to 195.00,
            "CVE" to 101.55,
            "STN" to 22.55,
            "MRU" to 39.80,
            "CDF" to 2850.00,
            "AOA" to 930.00,

            // North & Southern Africa
            "ZAR" to 17.58,
            "EGP" to 48.65,
            "MAD" to 9.85,
            "DZD" to 133.50,
            "TND" to 3.08,
            "LYD" to 4.82,
            "SDG" to 601.00,
            "ZMW" to 26.50,
            "MWK" to 1735.00,
            "MZN" to 63.90,
            "BWP" to 13.50,
            "NAD" to 17.58,
            "SZL" to 17.58,
            "LSL" to 17.58,
            "ZWL" to 322.00,
            "ZWG" to 26.50,

            // Major Global Currencies
            "EUR" to 0.921,
            "GBP" to 0.776,
            "JPY" to 152.40,
            "CNY" to 7.12,
            "CNH" to 7.12,
            "CAD" to 1.385,
            "AUD" to 1.515,
            "CHF" to 0.865,
            "INR" to 84.08,

            // Middle East & Central Asia
            "AED" to 3.6725,
            "SAR" to 3.751,
            "QAR" to 3.641,
            "KWD" to 0.307,
            "BHD" to 0.377,
            "OMR" to 0.385,
            "ILS" to 3.72,
            "JOD" to 0.709,
            "LBP" to 89500.00,
            "IQD" to 1310.00,
            "SYP" to 13000.00,
            "YER" to 250.25,
            "IRR" to 42000.00,

            // Europe & Eastern Europe
            "SEK" to 10.55,
            "NOK" to 10.95,
            "DKK" to 6.87,
            "ISK" to 138.20,
            "PLN" to 4.02,
            "CZK" to 23.35,
            "HUF" to 376.50,
            "RON" to 4.58,
            "BGN" to 1.80,
            "RSD" to 108.00,
            "BAM" to 1.80,
            "MKD" to 56.60,
            "ALL" to 90.50,
            "MDL" to 17.85,
            "UAH" to 41.30,
            "BYN" to 3.28,
            "RUB" to 96.50,
            "TRY" to 34.30,
            "GEL" to 2.74,
            "AMD" to 387.50,
            "AZN" to 1.70,
            "GIP" to 0.776,
            "JEP" to 0.776,
            "GGP" to 0.776,
            "IMP" to 0.776,

            // Asia & Southeast Asia
            "SGD" to 1.325,
            "HKD" to 7.77,
            "KRW" to 1380.00,
            "KPW" to 900.00,
            "TWD" to 32.10,
            "IDR" to 15650.00,
            "MYR" to 4.35,
            "PHP" to 58.20,
            "THB" to 33.80,
            "VND" to 25300.00,
            "PKR" to 277.50,
            "BDT" to 119.50,
            "LKR" to 293.00,
            "NPR" to 134.50,
            "AFN" to 66.80,
            "MVR" to 15.40,
            "BTN" to 84.08,
            "MMK" to 2098.00,
            "KHR" to 4050.00,
            "LAK" to 21950.00,
            "BND" to 1.325,
            "MNT" to 3400.00,
            "KZT" to 488.00,
            "UZS" to 12780.00,
            "TMT" to 3.50,
            "TJS" to 10.65,
            "KGS" to 85.70,

            // Americas & Caribbean
            "BRL" to 5.75,
            "MXN" to 19.85,
            "ARS" to 985.00,
            "CLP" to 950.00,
            "COP" to 4320.00,
            "PEN" to 3.76,
            "UYU" to 41.60,
            "PYG" to 7900.00,
            "BOB" to 6.91,
            "VES" to 42.50,
            "GTQ" to 7.72,
            "HNL" to 25.10,
            "NIO" to 36.80,
            "CRC" to 515.00,
            "PAB" to 1.0,
            "DOP" to 60.10,
            "JMD" to 158.00,
            "TTD" to 6.78,
            "BSD" to 1.0,
            "BBD" to 2.0,
            "BZD" to 2.0,
            "GYD" to 209.00,
            "SRD" to 30.50,
            "HTG" to 131.50,
            "XCD" to 2.70,
            "AWG" to 1.80,
            "ANG" to 1.80,
            "KYD" to 0.833,
            "BMD" to 1.0,
            "FKP" to 0.776,

            // Oceania & Pacific
            "NZD" to 1.665,
            "PGK" to 3.96,
            "FJD" to 2.26,
            "SBD" to 8.45,
            "VUV" to 119.50,
            "WST" to 2.76,
            "TOP" to 2.37,
            "XPF" to 109.90
        )
    }
}

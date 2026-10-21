package com.omnicalc.platform.core.currency

interface CurrencyRateProvider {
    val providerName: String
    suspend fun fetchRates(baseCurrency: String = "USD"): Result<CurrencyRateSet>
}

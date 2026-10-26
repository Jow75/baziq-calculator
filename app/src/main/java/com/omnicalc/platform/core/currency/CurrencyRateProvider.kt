package com.omnicalc.platform.core.currency

import com.omnicalc.platform.BuildConfig
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

interface CurrencyRateProvider {
    val providerName: String
    suspend fun fetchRates(baseCurrency: String = "USD"): Result<CurrencyRateSet>
}

/**
 * Networking shared by the live rate providers. Built on first use, which happens inside
 * fetchRates on the IO dispatcher, so TLS and client setup never run on the main thread.
 */
internal object CurrencyNetwork {
    const val USER_AGENT = "BaziqCalculator-Android/" + BuildConfig.VERSION_NAME

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            // Bounds the whole call including DNS, which the connect timeout does not cover
            .callTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    // Adapters come from Moshi codegen (@JsonClass(generateAdapter = true)); no kotlin-reflect.
    val moshi: Moshi by lazy { Moshi.Builder().build() }
}

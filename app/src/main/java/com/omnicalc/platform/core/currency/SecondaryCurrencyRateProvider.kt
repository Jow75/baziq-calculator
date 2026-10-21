package com.omnicalc.platform.core.currency

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class FrankfurterResponse(
    @Json(name = "amount") val amount: Double?,
    @Json(name = "base") val base: String?,
    @Json(name = "date") val date: String?,
    @Json(name = "rates") val rates: Map<String, Double>?
)

class SecondaryCurrencyRateProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()
) : CurrencyRateProvider {

    override val providerName: String = "ECB Reference (Frankfurter)"
    private val moshi by lazy { Moshi.Builder().add(KotlinJsonAdapterFactory()).build() }
    private val adapter by lazy { moshi.adapter(FrankfurterResponse::class.java) }

    override suspend fun fetchRates(baseCurrency: String): Result<CurrencyRateSet> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.frankfurter.app/latest?from=$baseCurrency"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "OmniCalc-Android/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Secondary provider HTTP error ${response.code}"))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty body from secondary provider"))
            val parsed = adapter.fromJson(body) ?: return@withContext Result.failure(Exception("Failed to parse secondary provider JSON"))

            val ratesMap = parsed.rates ?: return@withContext Result.failure(Exception("No rates in secondary provider response"))
            val mutableRates = ratesMap.toMutableMap()
            mutableRates[baseCurrency] = 1.0 // Base rate

            Result.success(
                CurrencyRateSet(
                    baseCurrency = parsed.base ?: baseCurrency,
                    rates = mutableRates,
                    timestampMs = System.currentTimeMillis(),
                    providerName = providerName,
                    status = FreshnessStatus.CURRENT
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

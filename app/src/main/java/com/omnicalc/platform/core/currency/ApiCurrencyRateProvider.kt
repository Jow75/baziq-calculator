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
data class ErApiResponse(
    @Json(name = "result") val result: String?,
    @Json(name = "base_code") val baseCode: String?,
    @Json(name = "time_last_update_unix") val timeLastUpdateUnix: Long?,
    @Json(name = "rates") val rates: Map<String, Double>?
)

class ApiCurrencyRateProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()
) : CurrencyRateProvider {

    override val providerName: String = "Open Exchange Reference"
    private val moshi by lazy { Moshi.Builder().add(KotlinJsonAdapterFactory()).build() }
    private val adapter by lazy { moshi.adapter(ErApiResponse::class.java) }

    override suspend fun fetchRates(baseCurrency: String): Result<CurrencyRateSet> = withContext(Dispatchers.IO) {
        try {
            val url = "https://open.er-api.com/v6/latest/$baseCurrency"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "OmniCalc-Android/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP error ${response.code}: ${response.message}"))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty API response"))
            val parsed = adapter.fromJson(body) ?: return@withContext Result.failure(Exception("JSON parsing failed"))

            val ratesMap = parsed.rates ?: return@withContext Result.failure(Exception("No rates in response"))
            val base = parsed.baseCode ?: baseCurrency
            val timestamp = (parsed.timeLastUpdateUnix ?: (System.currentTimeMillis() / 1000)) * 1000

            Result.success(
                CurrencyRateSet(
                    baseCurrency = base,
                    rates = ratesMap,
                    timestampMs = timestamp,
                    providerName = providerName,
                    status = FreshnessStatus.CURRENT
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

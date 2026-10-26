package com.omnicalc.platform.core.currency

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@JsonClass(generateAdapter = true)
data class ErApiResponse(
    @Json(name = "result") val result: String?,
    @Json(name = "base_code") val baseCode: String?,
    @Json(name = "time_last_update_unix") val timeLastUpdateUnix: Long?,
    @Json(name = "rates") val rates: Map<String, Double>?
)

class ApiCurrencyRateProvider(
    clientProvider: () -> OkHttpClient = { CurrencyNetwork.client }
) : CurrencyRateProvider {

    override val providerName: String = "Open Exchange Reference"
    private val client by lazy(clientProvider)
    private val adapter by lazy { CurrencyNetwork.moshi.adapter(ErApiResponse::class.java) }

    override suspend fun fetchRates(baseCurrency: String): Result<CurrencyRateSet> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://open.er-api.com/v6/latest/$baseCurrency")
                .header("User-Agent", CurrencyNetwork.USER_AGENT)
                .build()

            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP error ${response.code}: ${response.message}"))
                }
                response.body?.string()
            } ?: return@withContext Result.failure(Exception("Empty API response"))

            Result.success(parse(body, baseCurrency))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Parses an open.er-api.com v6 "latest" response. */
    internal fun parse(body: String, requestedBase: String): CurrencyRateSet {
        val parsed = adapter.fromJson(body) ?: throw IllegalStateException("JSON parsing failed")
        if (parsed.result != null && parsed.result != "success") {
            throw IllegalStateException("Rate service returned result '${parsed.result}'")
        }
        val ratesMap = parsed.rates?.takeIf { it.isNotEmpty() } ?: throw IllegalStateException("No rates in response")
        val timestamp = (parsed.timeLastUpdateUnix ?: (System.currentTimeMillis() / 1000)) * 1000
        return CurrencyRateSet(
            baseCurrency = parsed.baseCode ?: requestedBase,
            rates = ratesMap,
            timestampMs = timestamp,
            providerName = providerName,
            status = FreshnessStatus.CURRENT
        )
    }
}

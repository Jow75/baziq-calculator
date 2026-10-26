package com.omnicalc.platform.core.currency

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@JsonClass(generateAdapter = true)
data class FrankfurterResponse(
    @Json(name = "amount") val amount: Double?,
    @Json(name = "base") val base: String?,
    @Json(name = "date") val date: String?,
    @Json(name = "rates") val rates: Map<String, Double>?
)

class SecondaryCurrencyRateProvider(
    clientProvider: () -> OkHttpClient = { CurrencyNetwork.client }
) : CurrencyRateProvider {

    override val providerName: String = "ECB Reference (Frankfurter)"
    private val client by lazy(clientProvider)
    private val adapter by lazy { CurrencyNetwork.moshi.adapter(FrankfurterResponse::class.java) }

    override suspend fun fetchRates(baseCurrency: String): Result<CurrencyRateSet> = withContext(Dispatchers.IO) {
        try {
            // api.frankfurter.app now answers with a 301 to this host; calling it directly
            // avoids the extra round trip, which was slow enough to exceed the read timeout.
            val request = Request.Builder()
                .url("https://api.frankfurter.dev/v1/latest?from=$baseCurrency")
                .header("User-Agent", CurrencyNetwork.USER_AGENT)
                .build()

            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Secondary provider HTTP error ${response.code}"))
                }
                response.body?.string()
            } ?: return@withContext Result.failure(Exception("Empty body from secondary provider"))

            Result.success(parse(body, baseCurrency))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Parses a Frankfurter v1 "latest" response. */
    internal fun parse(body: String, requestedBase: String): CurrencyRateSet {
        val parsed = adapter.fromJson(body) ?: throw IllegalStateException("Failed to parse secondary provider JSON")
        val ratesMap = parsed.rates?.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("No rates in secondary provider response")
        val mutableRates = ratesMap.toMutableMap()
        mutableRates[requestedBase] = 1.0 // Base rate

        return CurrencyRateSet(
            baseCurrency = parsed.base ?: requestedBase,
            rates = mutableRates,
            timestampMs = System.currentTimeMillis(),
            providerName = providerName,
            status = FreshnessStatus.CURRENT
        )
    }
}

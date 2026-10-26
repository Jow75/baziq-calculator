package com.omnicalc.platform.core.currency

import java.util.Locale

/**
 * Maps an ISO 3166 country code to the currency the app should use as its local base.
 *
 * The mapping comes from the platform's ISO 4217 data (java.util.Currency, maintained with the
 * OS/JDK), limited to currencies the app has rates for. A few entries are corrected where that
 * data lags behind real changes, and countries without one unambiguous local currency return
 * null so the user chooses manually instead of getting a guess.
 */
object CountryCurrency {

    /** Changes newer than some platform data sets. */
    private val CORRECTIONS = mapOf(
        "HR" to "EUR", // Croatia adopted the euro on 2023-01-01
        "BG" to "EUR", // Bulgaria adopted the euro on 2026-01-01
        "SL" to "SLE", // Sierra Leone redenominated (SLL -> SLE) in 2022
        "VE" to "VES",
        "MR" to "MRU",
        "ST" to "STN"
    )

    /** Several currencies circulate side by side; no single one can be chosen automatically. */
    private val AMBIGUOUS = setOf(
        "ZW", // ZWG alongside widespread USD
        "CU", // CUP alongside foreign currency
        "PS"  // ILS, JOD and USD
    )

    fun currencyFor(countryCode: String): String? {
        val iso = countryCode.trim().uppercase(Locale.ROOT)
        if (iso.length != 2 || !iso.all { it in 'A'..'Z' } || iso in AMBIGUOUS) return null
        val code = CORRECTIONS[iso]
            ?: runCatching { java.util.Currency.getInstance(Locale("", iso))?.currencyCode }.getOrNull()
            ?: return null
        return code.takeIf { CurrencyCatalog.isSupported(it) }
    }

    fun countryName(countryCode: String): String =
        Locale("", countryCode.uppercase(Locale.ROOT)).getDisplayCountry(Locale.ENGLISH)
            .ifBlank { countryCode.uppercase(Locale.ROOT) }
}

/** Outcome of asking the device which country it is in. Coordinates never leave the locator. */
sealed interface CountryLookup {
    data class Found(val countryCode: String, val countryName: String) : CountryLookup
    object PermissionDenied : CountryLookup
    object LocationDisabled : CountryLookup
    /** No position fix within the time limit. */
    object NoLocation : CountryLookup
    /** A position was found but could not be turned into a country (no geocoder, offline, timeout). */
    object CountryUnresolved : CountryLookup
}

/** Finds the country the device is currently in, from its location. */
interface CountryLocator {
    fun hasPermission(): Boolean
    suspend fun currentCountry(): CountryLookup
}

/** What to do with a lookup result. */
data class LocalCurrencyDecision(
    /** Currency to make the base (source) currency, or null to keep the current one. */
    val applyCurrency: String?,
    /** Country to remember as the one whose currency was last applied automatically. */
    val rememberCountry: String?,
    /** Short status to show the user, or null to stay silent. */
    val message: String?,
    /** Persistent indication of the detected country and its currency, when known. */
    val detectedLabel: String?
)

/**
 * Rules for location-driven base-currency selection.
 *
 * - A user-requested check (tapping "Local Currency", enabling the feature) applies the detected
 *   currency and always explains failures.
 * - A background check (opening the converter with automatic mode on) changes the base only when
 *   the country differs from the one last applied, i.e. after travelling. Within the same country
 *   a currency the user picked by hand is left alone. Background failures stay silent.
 * - Failures and unmapped countries never change the base currency.
 */
object LocalCurrencyPolicy {

    fun decide(
        lookup: CountryLookup,
        autoEnabled: Boolean,
        userRequested: Boolean,
        lastAppliedCountry: String?
    ): LocalCurrencyDecision = when (lookup) {
        is CountryLookup.Found -> {
            val currency = CountryCurrency.currencyFor(lookup.countryCode)
            val changedCountry = !lookup.countryCode.equals(lastAppliedCountry, ignoreCase = true)
            if (currency == null) {
                LocalCurrencyDecision(
                    applyCurrency = null,
                    rememberCountry = null,
                    message = if (userRequested || (autoEnabled && changedCountry))
                        "📍 ${lookup.countryName}: no single local currency is known. Choose your base currency manually."
                    else null,
                    detectedLabel = "📍 ${lookup.countryName}"
                )
            } else if (userRequested || (autoEnabled && changedCountry)) {
                LocalCurrencyDecision(
                    applyCurrency = currency,
                    rememberCountry = lookup.countryCode.uppercase(Locale.ROOT),
                    message = if (userRequested) "📍 ${lookup.countryName}: base currency set to $currency"
                    else "📍 You are in ${lookup.countryName}: base currency changed to $currency",
                    detectedLabel = "📍 ${lookup.countryName} · $currency"
                )
            } else {
                LocalCurrencyDecision(null, null, null, "📍 ${lookup.countryName} · $currency")
            }
        }
        else -> LocalCurrencyDecision(
            applyCurrency = null,
            rememberCountry = null,
            message = if (userRequested) failureMessage(lookup) else null,
            detectedLabel = null
        )
    }

    fun failureMessage(lookup: CountryLookup): String = when (lookup) {
        CountryLookup.PermissionDenied -> "Location not allowed. Your base currency was not changed; choose it manually."
        CountryLookup.LocationDisabled -> "Location is turned off in Android settings. Your base currency was not changed."
        CountryLookup.NoLocation -> "Your location could not be determined. Your base currency was not changed."
        CountryLookup.CountryUnresolved -> "Your country could not be determined (check the internet connection). Your base currency was not changed."
        is CountryLookup.Found -> ""
    }
}

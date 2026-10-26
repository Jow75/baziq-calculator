package com.omnicalc.platform

import com.omnicalc.platform.core.currency.CountryCurrency
import com.omnicalc.platform.core.currency.CountryLookup
import com.omnicalc.platform.core.currency.CurrencyCatalog
import com.omnicalc.platform.core.currency.CurrencyFormatter
import com.omnicalc.platform.core.currency.LocalCurrencyPolicy
import com.omnicalc.platform.core.math.QuickCalculationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Release 1.1.1: exchange-rate labels keep their decimals, and the local currency comes from
 * the country the device is in.
 */
class RateLabelAndLocalCurrencyTest {

    private fun rate(value: String, digits: Int = CurrencyFormatter.MAX_RATE_SIGNIFICANT_DIGITS) =
        CurrencyFormatter.formatRate(BigDecimal(value), digits)

    // --- Rate labels ---

    // Defect: rates of 100 or more were shown as whole numbers ("1 USD = 130 KES" for 129.84).
    @Test
    fun rateAbove100KeepsItsDecimals() {
        assertEquals("129.84", rate("129.84"))
        assertEquals("2,643.054368", rate("2643.054368"))
        assertEquals("129.84786", rate("129.84786"))
    }

    @Test
    fun ratesBelowOneKeepTheirSignificantDigits() {
        assertEquals("0.892784", rate("0.892784"))
        assertEquals("0.0077018", rate("0.0077018"))
        assertEquals("0.000268", rate("0.000268"))
    }

    @Test
    fun wholeNumberRatesAreNotAltered() {
        assertEquals("3,550.00", rate("3550"))
        assertEquals("1.00", rate("1"))
        assertEquals("130.00", rate("130"))
    }

    @Test
    fun twoDecimalsArePaddedNotRounded() {
        assertEquals("1.10", rate("1.1"))
        assertEquals("129.80", rate("129.8"))
    }

    @Test
    fun crossRateIsLimitedToInputPrecisionWithoutBinaryNoise() {
        // 1 / 129.84 = 0.00770178681454...: inputs have 5 significant digits
        val inverse = BigDecimal.ONE.divide(BigDecimal("129.84"), java.math.MathContext(18))
        assertEquals("0.0077018", rate(inverse.toPlainString(), CurrencyFormatter.rateSignificantDigits(BigDecimal.ONE, BigDecimal("129.84"))))
        // Double noise is never shown: 129.84 via binary floating point
        assertEquals("129.84", rate(BigDecimal(129.84.toString()).toPlainString(), 5))
    }

    @Test
    fun integerPartIsNeverRoundedAway() {
        // A cross rate limited to 5 significant digits still keeps all six integer digits,
        // and is not padded with zeros it does not have
        assertEquals("123,457", rate("123456.78", 5))
        assertEquals("129.8", rate("129.7999", 4))
        assertEquals("123,456.78", rate("123456.78"))
    }

    @Test
    fun quickConversionShowsTheSameUnroundedRate() {
        val result = QuickCalculationEngine.evaluateQuick("1 USD to KES", liveRates = mapOf("USD" to 1.0, "KES" to 129.84))
        assertNotNull(result)
        assertTrue(result!!.secondaryResult, result.secondaryResult!!.contains("Rate: 1 USD = 129.84 KES"))
    }

    // --- Country -> currency ---

    @Test
    fun missionCountriesMapToTheirCurrencies() {
        val expected = mapOf(
            "US" to "USD", "UG" to "UGX", "RW" to "RWF", "BI" to "BIF", "GB" to "GBP",
            "KE" to "KES", "TZ" to "TZS", "KM" to "KMF"
        )
        expected.forEach { (country, currency) -> assertEquals(country, currency, CountryCurrency.currencyFor(country)) }
    }

    @Test
    fun mappingCoversCountriesBeyondTheExamples() {
        val expected = mapOf(
            "FR" to "EUR", "DE" to "EUR", "HR" to "EUR", "BG" to "EUR", "JP" to "JPY", "IN" to "INR",
            "NG" to "NGN", "ZA" to "ZAR", "CD" to "CDF", "ET" to "ETB", "AE" to "AED", "BR" to "BRL",
            "EC" to "USD", "SN" to "XOF", "CM" to "XAF", "SS" to "SSP", "SO" to "SOS"
        )
        expected.forEach { (country, currency) -> assertEquals(country, currency, CountryCurrency.currencyFor(country)) }
        assertEquals("lowercase input", "KES", CountryCurrency.currencyFor("ke"))
    }

    @Test
    fun ambiguousOrUnknownCountriesAreNotGuessed() {
        assertNull(CountryCurrency.currencyFor("ZW"))  // several currencies in use
        assertNull(CountryCurrency.currencyFor("AQ"))  // Antarctica: no currency
        assertNull(CountryCurrency.currencyFor("XX"))
        assertNull(CountryCurrency.currencyFor(""))
        assertNull(CountryCurrency.currencyFor("USA"))
    }

    @Test
    fun everyMappedCurrencyIsOneTheAppCanConvert() {
        java.util.Locale.getISOCountries().forEach { country ->
            CountryCurrency.currencyFor(country)?.let { assertTrue("$country -> $it", CurrencyCatalog.isSupported(it)) }
        }
    }

    // --- Policy ---

    private val uganda = CountryLookup.Found("UG", "Uganda")

    @Test
    fun userRequestedLookupAppliesTheCountryCurrency() {
        val d = LocalCurrencyPolicy.decide(uganda, autoEnabled = false, userRequested = true, lastAppliedCountry = null)
        assertEquals("UGX", d.applyCurrency)
        assertEquals("UG", d.rememberCountry)
        assertEquals("📍 Uganda · UGX", d.detectedLabel)
    }

    @Test
    fun automaticModeAppliesOnlyAfterTheCountryChanges() {
        val travelled = LocalCurrencyPolicy.decide(uganda, autoEnabled = true, userRequested = false, lastAppliedCountry = "US")
        assertEquals("UGX", travelled.applyCurrency)
        assertTrue(travelled.message!!.contains("Uganda"))

        // Same country again: a currency the user picked by hand is left alone, silently
        val sameCountry = LocalCurrencyPolicy.decide(uganda, autoEnabled = true, userRequested = false, lastAppliedCountry = "UG")
        assertNull(sameCountry.applyCurrency)
        assertNull(sameCountry.message)
        assertEquals("📍 Uganda · UGX", sameCountry.detectedLabel)
    }

    @Test
    fun manualModeNeverChangesTheBaseInTheBackground() {
        val d = LocalCurrencyPolicy.decide(uganda, autoEnabled = false, userRequested = false, lastAppliedCountry = "US")
        assertNull(d.applyCurrency)
        assertNull(d.message)
    }

    @Test
    fun failuresNeverChangeTheBaseCurrency() {
        listOf(CountryLookup.PermissionDenied, CountryLookup.LocationDisabled, CountryLookup.NoLocation, CountryLookup.CountryUnresolved)
            .forEach { failure ->
                val asked = LocalCurrencyPolicy.decide(failure, autoEnabled = true, userRequested = true, lastAppliedCountry = "KM")
                assertNull(asked.applyCurrency)
                assertNull(asked.rememberCountry)
                assertTrue(asked.message!!.contains("not changed") || asked.message!!.contains("manually"))

                val background = LocalCurrencyPolicy.decide(failure, autoEnabled = true, userRequested = false, lastAppliedCountry = "KM")
                assertNull(background.applyCurrency)
                assertNull("background failures stay quiet", background.message)
            }
    }

    @Test
    fun unmappedCountryAsksForManualChoice() {
        val d = LocalCurrencyPolicy.decide(CountryLookup.Found("ZW", "Zimbabwe"), autoEnabled = true, userRequested = true, lastAppliedCountry = null)
        assertNull(d.applyCurrency)
        assertFalse(d.message.isNullOrBlank())
        assertTrue(d.message!!.contains("manually"))
    }
}

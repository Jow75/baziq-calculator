package com.omnicalc.platform.core.currency

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * Finds the current country from the device location: a recent last-known position when one
 * exists, otherwise a single fresh fix, then reverse geocoding to a country code.
 *
 * Coarse (approximate) location is enough to tell the country. The position is used only to
 * look up the country and is never stored or logged.
 */
class DeviceCountryLocator(
    context: Context,
    private val fixTimeoutMs: Long = 12_000,
    private val geocodeTimeoutMs: Long = 8_000
) : CountryLocator {

    private val appContext = context.applicationContext

    override fun hasPermission(): Boolean =
        isGranted(Manifest.permission.ACCESS_COARSE_LOCATION) || isGranted(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    override suspend fun currentCountry(): CountryLookup = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext CountryLookup.PermissionDenied
        val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext CountryLookup.NoLocation
        if (!LocationManagerCompat.isLocationEnabled(manager)) return@withContext CountryLookup.LocationDisabled

        val location = try {
            recentLastKnown(manager) ?: freshLocation(manager)
        } catch (_: SecurityException) {
            return@withContext CountryLookup.PermissionDenied
        } ?: return@withContext CountryLookup.NoLocation

        val iso = countryCodeOf(location)?.uppercase(Locale.ROOT)
            ?: return@withContext CountryLookup.CountryUnresolved
        CountryLookup.Found(iso, CountryCurrency.countryName(iso))
    }

    private fun usableProviders(manager: LocationManager): List<String> {
        val preferred = buildList {
            add(LocationManager.NETWORK_PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }
        val enabled = manager.getProviders(true)
        return preferred.filter { it in enabled }
    }

    @SuppressLint("MissingPermission")
    private fun recentLastKnown(manager: LocationManager): Location? =
        usableProviders(manager)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .filter { ageMs(it) <= LAST_KNOWN_MAX_AGE_MS }
            .maxByOrNull { it.elapsedRealtimeNanos }

    private fun ageMs(location: Location): Long =
        (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000

    @SuppressLint("MissingPermission")
    private suspend fun freshLocation(manager: LocationManager): Location? {
        val provider = usableProviders(manager).firstOrNull { it != LocationManager.PASSIVE_PROVIDER }
            ?: return null
        return withTimeoutOrNull(fixTimeoutMs) {
            suspendCancellableCoroutine { cont ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val cancel = CancellationSignal()
                    val executor = Executors.newSingleThreadExecutor()
                    cont.invokeOnCancellation { cancel.cancel(); executor.shutdown() }
                    manager.getCurrentLocation(provider, cancel, executor) { location ->
                        executor.shutdown()
                        if (cont.isActive) cont.resume(location)
                    }
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            manager.removeUpdates(this)
                            if (cont.isActive) cont.resume(location)
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }
                    cont.invokeOnCancellation { manager.removeUpdates(listener) }
                    @Suppress("DEPRECATION")
                    manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                }
            }
        }
    }

    private suspend fun countryCodeOf(location: Location): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(appContext, Locale.ENGLISH)
        return withTimeoutOrNull(geocodeTimeoutMs) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(location.latitude, location.longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (cont.isActive) cont.resume(addresses.firstOrNull()?.countryCode)
                        }
                        override fun onError(errorMessage: String?) {
                            if (cont.isActive) cont.resume(null)
                        }
                    })
                }
            } else {
                runCatching {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()?.countryCode
                }.getOrNull()
            }
        }?.takeIf { it.isNotBlank() }
    }

    companion object {
        /** A last-known position younger than this is good enough to tell the country. */
        const val LAST_KNOWN_MAX_AGE_MS = 30 * 60_000L
    }
}

package com.omnicalc.platform

import android.app.Application
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.omnicalc.platform.core.currency.CurrencyRepository
import com.omnicalc.platform.core.database.OmniCalcDatabase
import com.omnicalc.platform.core.preferences.UserPreferencesRepository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OmniCalcApplication : Application() {
    companion object {
        var processStartTime: Long = 0L
        var appOnCreateTime: Long = 0L
    }

    val database: OmniCalcDatabase by lazy {
        OmniCalcDatabase.getInstance(this)
    }

    val currencyRepository: CurrencyRepository by lazy {
        CurrencyRepository(database.currencyDao())
    }

    val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(this)
    }

    override fun onCreate() {
        processStartTime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Process.getStartElapsedRealtime()
        } else {
            SystemClock.elapsedRealtime()
        }
        appOnCreateTime = SystemClock.elapsedRealtime()
        super.onCreate()
        Log.i("PERF_OMNICALC", "STAGE_1_PROCESS_START_TO_APP: ${appOnCreateTime - processStartTime} ms")

        // Pure ultra-low-latency launch: Room and Currency initialization are strictly deferred
        // off the critical path and loaded lazily on demand.
    }
}


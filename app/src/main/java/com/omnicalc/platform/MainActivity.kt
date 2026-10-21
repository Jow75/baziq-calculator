package com.omnicalc.platform

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.omnicalc.platform.core.preferences.AppTheme
import com.omnicalc.platform.core.preferences.UserPreferences
import com.omnicalc.platform.ui.navigation.OmniCalcNavHost
import com.omnicalc.platform.ui.navigation.safeNavigateBackImmediate
import com.omnicalc.platform.ui.theme.OmniCalcTheme

class MainActivity : ComponentActivity() {
    companion object {
        var activityCreateTime: Long = 0L
        var isWarmLaunch: Boolean = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val tCreate = SystemClock.elapsedRealtime()
        activityCreateTime = tCreate
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val processStart = OmniCalcApplication.processStartTime
        val appCreate = OmniCalcApplication.appOnCreateTime
        Log.i("PERF_OMNICALC", "STAGE_2_APP_TO_ACTIVITY: ${tCreate - appCreate} ms (Total from process start: ${tCreate - processStart} ms)")

        Choreographer.getInstance().postFrameCallback {
            val tFrame = SystemClock.elapsedRealtime()
            val totalCold = tFrame - processStart
            val actToFrame = tFrame - tCreate
            Log.i("PERF_OMNICALC", "STAGE_3_ACTIVITY_TO_FIRST_FRAME: $actToFrame ms")
            Log.i("PERF_OMNICALC", "STAGE_4_COLD_LAUNCH_FIRST_FRAME: $totalCold ms")
        }

        val app = application as OmniCalcApplication

        setContent {
            val navController = rememberNavController()
            val prefs by app.userPreferencesRepository.userPreferencesFlow.collectAsState(initial = UserPreferences())

            DisposableEffect(navController) {
                val callback = object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        if (!navController.safeNavigateBackImmediate()) {
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                            isEnabled = true
                        }
                    }
                }
                onBackPressedDispatcher.addCallback(this@MainActivity, callback)
                onDispose {
                    callback.remove()
                }
            }

            OmniCalcTheme(appTheme = prefs.theme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    OmniCalcNavHost(app = app, navController = navController)
                }
            }
        }
    }


    override fun onResume() {
        super.onResume()
        if (isWarmLaunch) {
            val tResume = SystemClock.elapsedRealtime()
            Choreographer.getInstance().postFrameCallback {
                val tWarmFrame = SystemClock.elapsedRealtime()
                Log.i("PERF_OMNICALC", "STAGE_WARM_LAUNCH_FRAME: ${tWarmFrame - tResume} ms")
            }
        }
        isWarmLaunch = true
    }
}


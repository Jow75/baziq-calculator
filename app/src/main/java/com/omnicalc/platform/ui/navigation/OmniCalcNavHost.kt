package com.omnicalc.platform.ui.navigation

import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.omnicalc.platform.OmniCalcApplication
import com.omnicalc.platform.core.currency.DeviceCountryLocator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import com.omnicalc.platform.feature.calculator.CalculatorScreen
import com.omnicalc.platform.feature.calculator.CalculatorViewModel
import com.omnicalc.platform.feature.constants.ConstantsScreen
import com.omnicalc.platform.feature.currency.CurrencyScreen
import com.omnicalc.platform.feature.currency.CurrencyViewModel
import com.omnicalc.platform.feature.dates.*
import com.omnicalc.platform.feature.education.GpaScreen
import com.omnicalc.platform.feature.education.GradeScreen
import com.omnicalc.platform.feature.finance.*
import com.omnicalc.platform.feature.fuel.FuelCostScreen
import com.omnicalc.platform.feature.fuel.FuelEconomyScreen
import com.omnicalc.platform.feature.geometry.GeometryScreen
import com.omnicalc.platform.feature.geometry.TriangleSolverScreen
import com.omnicalc.platform.feature.health.BmiScreen
import com.omnicalc.platform.feature.health.BmrTdeeScreen
import com.omnicalc.platform.feature.health.OvulationScreen
import com.omnicalc.platform.feature.history.HistoryScreen
import com.omnicalc.platform.feature.programmer.ProgrammerScreen
import com.omnicalc.platform.feature.search.SearchScreen
import com.omnicalc.platform.feature.settings.SettingsScreen
import com.omnicalc.platform.feature.statistics.ProbabilityScreen
import com.omnicalc.platform.feature.statistics.StatisticsScreen
import com.omnicalc.platform.feature.units.UnitsScreen
import com.omnicalc.platform.feature.units.UnitsViewModel

/**
 * Ultra-low-latency state-based navigation guard.
 *
 * Replaces artificial time-based debouncing with immediate lifecycle and backstack state checks:
 * 1. Executes IMMEDIATELY (0 ms artificial delay) on valid user intent.
 * 2. Checks current entry lifecycle: Must be at least RESUMED. When a pop begins, the entry
 *    transitions away from RESUMED in the very same frame, automatically discarding any
 *    duplicate/rapid back taps without arbitrary timers.
 * 3. Never pops the root calculator screens, mathematically eliminating blank-screen crashes.
 * 4. Provides guaranteed single-top root fallback if back stack state is ever unresolvable.
 */
private var isNavigatingBackInProgress = false

fun NavHostController.safeNavigateBackImmediate(): Boolean {
    val tBackStart = SystemClock.elapsedRealtime()

    // 1. Serialization guard: If a navigation pop is already in-flight for this frame,
    // suppress duplicate rapid taps immediately without arbitrary delays.
    if (isNavigatingBackInProgress) {
        Log.d("PERF_OMNICALC", "NAV_BACK_GUARD: Suppressed duplicate in-flight back press")
        return true
    }

    val currentEntry = currentBackStackEntry ?: return false
    val currentRoute = currentEntry.destination.route ?: "unknown"

    // 2. Root screen protection: Never pop root calculator screens
    if (currentRoute == "calculator/basic" || currentRoute == "calculator/scientific") {
        return false
    }

    // 3. Lifecycle state guard: Only allow popping if the current screen is actively RESUMED
    if (!currentEntry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
        Log.d("PERF_OMNICALC", "NAV_BACK_GUARD: Suppressed unresumed back press (state=${currentEntry.lifecycle.currentState})")
        return true
    }

    val prevRoute = previousBackStackEntry?.destination?.route ?: "calculator/basic"
    val prevEntry = previousBackStackEntry

    isNavigatingBackInProgress = true

    // 4. Resilience fallback: If no previous entry exists, navigate to root safely
    if (prevEntry == null) {
        navigate("calculator/basic") {
            popUpTo("calculator/basic") { inclusive = true }
            launchSingleTop = true
        }
        Choreographer.getInstance().postFrameCallback {
            isNavigatingBackInProgress = false
            val tFrame = SystemClock.elapsedRealtime()
            Log.i("PERF_OMNICALC", "NAV_BACK_METRIC: $currentRoute -> calculator/basic (fallback) rendered in ${tFrame - tBackStart} ms")
        }
        return true
    }

    // 5. Instant pop execution
    val popped = popBackStack()
    Choreographer.getInstance().postFrameCallback {
        isNavigatingBackInProgress = false
        val tFrame = SystemClock.elapsedRealtime()
        Log.i("PERF_OMNICALC", "NAV_BACK_METRIC: $currentRoute -> $prevRoute rendered in ${tFrame - tBackStart} ms")
    }
    if (!popped) {
        navigate("calculator/basic") {
            popUpTo("calculator/basic") { inclusive = true }
            launchSingleTop = true
        }
    }
    return true
}

/** Backward compatibility alias */
fun NavHostController.safeNavigateBackDebounced(): Boolean = safeNavigateBackImmediate()

private fun NavGraphBuilder.toolComposable(
    route: String,
    navController: NavHostController,
    content: @Composable () -> Unit
) {
    composable(route) {
        BackHandler {
            navController.safeNavigateBackImmediate()
        }
        content()
    }
}

@Composable
fun OmniCalcNavHost(
    app: OmniCalcApplication,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    // Lazily inject database DAO provider so Room SQLite is NEVER loaded on the main thread during app launch
    val calculatorViewModel = viewModel {
        CalculatorViewModel(
            historyDaoProvider = { app.database.historyDao() },
            preferencesRepository = app.userPreferencesRepository
        )
    }

    val currencyViewModel = viewModel {
        CurrencyViewModel(
            currencyRepository = app.currencyRepository,
            countryLocator = DeviceCountryLocator(app.applicationContext),
            localCurrencyPreferences = app.userPreferencesRepository
        )
    }

    val navigateToToolWithMetrics: (String) -> Unit = { route ->
        val tNavStart = SystemClock.elapsedRealtime()
        navController.navigate(route) { launchSingleTop = true }
        Choreographer.getInstance().postFrameCallback {
            val tFrame = SystemClock.elapsedRealtime()
            Log.i("PERF_OMNICALC", "NAV_FORWARD_METRIC: -> $route rendered in ${tFrame - tNavStart} ms")
        }
    }

    val navigateToSearchWithMetrics: () -> Unit = {
        val tNavStart = SystemClock.elapsedRealtime()
        navController.navigate("search") { launchSingleTop = true }
        Choreographer.getInstance().postFrameCallback {
            val tFrame = SystemClock.elapsedRealtime()
            Log.i("PERF_OMNICALC", "NAV_FORWARD_METRIC: -> search rendered in ${tFrame - tNavStart} ms")
        }
    }

    val navigateToSettingsWithMetrics: () -> Unit = {
        val tNavStart = SystemClock.elapsedRealtime()
        navController.navigate("settings") { launchSingleTop = true }
        Choreographer.getInstance().postFrameCallback {
            val tFrame = SystemClock.elapsedRealtime()
            Log.i("PERF_OMNICALC", "NAV_FORWARD_METRIC: -> settings rendered in ${tFrame - tNavStart} ms")
        }
    }

    val navigateToHistoryWithMetrics: () -> Unit = {
        val tNavStart = SystemClock.elapsedRealtime()
        navController.navigate("history") { launchSingleTop = true }
        Choreographer.getInstance().postFrameCallback {
            val tFrame = SystemClock.elapsedRealtime()
            Log.i("PERF_OMNICALC", "NAV_FORWARD_METRIC: -> history rendered in ${tFrame - tNavStart} ms")
        }
    }

    // Read only in the draw phase below, so navigation does not recompose this host
    val currentEntry by navController.currentBackStackEntryAsState()
    val isToolOnTop by remember {
        derivedStateOf { currentEntry?.destination?.route?.startsWith("calculator/") == false }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Persistent base layer: The Calculator is always kept alive in composition,
        // eliminating 400ms rebuild passes when navigating back from any tool.
        // While an opaque tool screen covers it, it is not drawn, avoiding a full-screen
        // layer of overdraw on every frame of the tool screen.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent { if (!isToolOnTop) drawContent() }
        ) {
            CalculatorScreen(
                viewModel = calculatorViewModel,
                onNavigateToTool = navigateToToolWithMetrics,
                onNavigateToSearch = navigateToSearchWithMetrics,
                onNavigateToSettings = navigateToSettingsWithMetrics,
                onNavigateToHistory = navigateToHistoryWithMetrics
            )
        }

        NavHost(
            navController = navController,
            startDestination = "calculator/basic",
            modifier = Modifier.fillMaxSize(),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable("calculator/basic") {
                calculatorViewModel.setScientificExpanded(false)
            }

            composable("calculator/scientific") {
                calculatorViewModel.setScientificExpanded(true)
            }

            toolComposable("currency", navController) {
                CurrencyScreen(
                    viewModel = currencyViewModel,
                    onNavigateBack = { navController.safeNavigateBackDebounced() }
                )
            }

            toolComposable("units", navController) {
                val unitsViewModel = viewModel { UnitsViewModel() }
                UnitsScreen(
                    viewModel = unitsViewModel,
                    onNavigateBack = { navController.safeNavigateBackDebounced() }
                )
            }

            // Finance Routes
            toolComposable("finance/discount", navController) {
                DiscountScreen(onNavigateBack = { navController.safeNavigateBackDebounced() })
            }
            toolComposable("finance/discount_tax", navController) {
                DiscountTaxScreen(onNavigateBack = { navController.safeNavigateBackDebounced() })
            }
            toolComposable("finance/tax_vat", navController) {
                TaxVatScreen(onNavigateBack = { navController.safeNavigateBackDebounced() })
            }
            toolComposable("finance/loan", navController) {
                LoanScreen(onNavigateBack = { navController.safeNavigateBackDebounced() })
            }
            toolComposable("finance/savings", navController) {
                SavingsScreen(onNavigateBack = { navController.safeNavigateBackDebounced() })
            }
            toolComposable("finance/profit_margin", navController) {
                ProfitMarginScreen(onNavigateBack = { navController.safeNavigateBackDebounced() })
            }
            toolComposable("finance/breakeven", navController) {
                BreakEvenScreen(onNavigateBack = { navController.safeNavigateBackDebounced() })
            }
            toolComposable("finance/tip", navController) {
                TipScreen(onNavigateBack = { navController.safeNavigateBackDebounced() })
            }
            toolComposable("finance/unit_price", navController) {
                UnitPriceScreen(onNavigateBack = { navController.safeNavigateBackDebounced() })
            }

            // Date & Time Routes
            toolComposable("dates/age", navController) {
                AgeScreen(onNavigateBack = { navController.safeNavigateBackDebounced() })
            }
            toolComposable("dates/difference", navController) {
                DateDiffScreen(onNavigateBack = { navController.safeNavigateBackDebounced() })
            }
            toolComposable("dates/time_duration", navController) {
                TimeDurationScreen(onNavigateBack = { navController.safeNavigateBackDebounced() })
            }
            toolComposable("dates/timezone", navController) {
                TimezoneScreen(onNavigateBack = { navController.safeNavigateBackDebounced() })
            }

            // Health Routes
            toolComposable("health/bmi", navController) {
                BmiScreen(onNavigateBack = { navController.safeNavigateBackDebounced() })
            }
            toolComposable("health/bmr_tdee", navController) {
                BmrTdeeScreen(onNavigateBack = { navController.safeNavigateBackDebounced() })
            }
            toolComposable("health/ovulation", navController) {
                OvulationScreen(onNavigateBack = { navController.safeNavigateBackDebounced() })
            }

            // Fuel Routes
            toolComposable("fuel/cost", navController) {
                FuelCostScreen(onNavigateBack = { navController.safeNavigateBackDebounced() })
            }
            toolComposable("fuel/economy", navController) {
                FuelEconomyScreen(onNavigateBack = { navController.safeNavigateBackDebounced() })
            }

            // Programmer Route
            toolComposable("programmer", navController) {
                ProgrammerScreen(onNavigateBack = { navController.safeNavigateBackDebounced() })
            }

            // Geometry Routes
            toolComposable("geometry", navController) {
                GeometryScreen(onNavigateBack = { navController.safeNavigateBackDebounced() })
            }
            toolComposable("geometry/triangle", navController) {
                TriangleSolverScreen(onNavigateBack = { navController.safeNavigateBackDebounced() })
            }

            // Statistics Routes
            toolComposable("statistics", navController) {
                StatisticsScreen(onNavigateBack = { navController.safeNavigateBackDebounced() })
            }
            toolComposable("statistics/probability", navController) {
                ProbabilityScreen(onNavigateBack = { navController.safeNavigateBackDebounced() })
            }

            // Education Routes
            toolComposable("education/grade", navController) {
                GradeScreen(onNavigateBack = { navController.safeNavigateBackDebounced() })
            }
            toolComposable("education/gpa", navController) {
                GpaScreen(onNavigateBack = { navController.safeNavigateBackDebounced() })
            }

            // Constants Route
            toolComposable("constants", navController) {
                ConstantsScreen(onNavigateBack = { navController.safeNavigateBackDebounced() })
            }

            // Universal Search
            toolComposable("search", navController) {
                SearchScreen(
                    currentRates = { app.currencyRepository.rateState.value },
                    onNavigateBack = { navController.safeNavigateBackDebounced() },
                    onNavigateToTool = { route ->
                        navController.navigate(route) {
                            popUpTo("search") {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    }
                )
            }

            // History
            toolComposable("history", navController) {
                HistoryScreen(
                    historyDao = app.database.historyDao(),
                    onNavigateBack = { navController.safeNavigateBackDebounced() },
                    onRestoreCalculation = { expr ->
                        calculatorViewModel.restoreExpression(expr)
                        navController.safeNavigateBackDebounced()
                    }
                )
            }

            // Settings
            toolComposable("settings", navController) {
                SettingsScreen(
                    preferencesRepository = app.userPreferencesRepository,
                    backupRepository = app.backupRepository,
                    onNavigateBack = { navController.safeNavigateBackDebounced() }
                )
            }
        }
    }
}



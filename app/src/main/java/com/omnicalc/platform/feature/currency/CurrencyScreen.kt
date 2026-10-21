package com.omnicalc.platform.feature.currency

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.omnicalc.platform.core.currency.CurrencyCatalog
import com.omnicalc.platform.core.currency.CurrencyFormatter
import com.omnicalc.platform.core.currency.CurrencyInfo
import com.omnicalc.platform.core.currency.CurrencyRateSet
import com.omnicalc.platform.core.currency.StaticFallbackRateProvider
import com.omnicalc.platform.ui.components.ConversionSourceBadge
import com.omnicalc.platform.ui.components.DisclaimerCard
import com.omnicalc.platform.ui.components.OfflineStatusBadge
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun CurrencyScreen(
    viewModel: CurrencyViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    var showAddCurrencySheet by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }

    var dragOrder by remember { mutableStateOf<List<String>?>(null) }
    var draggingCode by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    BackHandler {
        if (state.isReorderMode) {
            viewModel.setReorderMode(false)
        } else {
            onNavigateBack()
        }
    }


    var measuredItemHeightPx by remember { mutableFloatStateOf(0f) }
    val spacingPx = with(density) { 10.dp.toPx() }

    // Authoritative single-source list: derived from state.items, rearranged by dragOrder only when dragging
    val displayItems = remember(state.items, dragOrder) {
        val order = dragOrder ?: return@remember state.items
        val itemMap = state.items.associateBy { it.currency.code.uppercase() }
        val ordered = order.mapNotNull { itemMap[it.uppercase()] }
        val missing = state.items.filter { item -> ordered.none { it.currency.code.equals(item.currency.code, ignoreCase = true) } }
        ordered + missing
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val detected = viewModel.detectAndSelectLocalCurrency(context)
        coroutineScope.launch {
            val idx = displayItems.indexOfFirst {
                it.currency.code.equals(detected.currency.code, ignoreCase = true)
            }
            if (idx >= 0) {
                lazyListState.animateScrollToItem(idx)
            }
        }
    }

    // Scroll-into-view is now handled exclusively by BringIntoViewRequester
    // on each currency row card, triggered naturally when focus is acquired.
    // This eliminates the previous dual-scroll (animateScrollToItem + bringIntoView)
    // that caused competing layout transitions on a single tap.

    val onDragStart: (String) -> Unit = { code ->
        draggingCode = code
        dragOrder = displayItems.map { it.currency.code }
        dragOffsetY = 0f
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val onDragDelta: (Float) -> Unit = { dy ->
        dragOffsetY += dy
        val slotPitch = if (measuredItemHeightPx > 0f) measuredItemHeightPx + spacingPx else with(density) { 110.dp.toPx() }
        val threshold = slotPitch * 0.5f

        val currentOrder = dragOrder ?: displayItems.map { it.currency.code }
        val currentIdx = currentOrder.indexOfFirst { it.equals(draggingCode, ignoreCase = true) }
        if (currentIdx != -1) {
            if (dragOffsetY > threshold && currentIdx < currentOrder.size - 1) {
                val targetIdx = currentIdx + 1
                val list = currentOrder.toMutableList()
                val moved = list.removeAt(currentIdx)
                list.add(targetIdx, moved)
                dragOrder = list
                dragOffsetY -= slotPitch
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            } else if (dragOffsetY < -threshold && currentIdx > 0) {
                val targetIdx = currentIdx - 1
                val list = currentOrder.toMutableList()
                val moved = list.removeAt(currentIdx)
                list.add(targetIdx, moved)
                dragOrder = list
                dragOffsetY += slotPitch
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    val onDragEnd: () -> Unit = {
        val newOrder = dragOrder ?: displayItems.map { it.currency.code }
        viewModel.reorderCurrencies(newOrder)
        draggingCode = null
        dragOrder = null
        dragOffsetY = 0f
    }

    val isImeOpen = WindowInsets.ime.getBottom(density) > 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.isReorderMode) "Reorder Currencies" else "Currency Converter",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.isReorderMode) {
                            viewModel.setReorderMode(false)
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            imageVector = if (state.isReorderMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (state.isReorderMode) "Cancel Reorder" else "Back"
                        )
                    }
                },
                actions = {
                    if (state.isReorderMode) {
                        TextButton(onClick = { viewModel.setReorderMode(false) }) {
                            Text("Done", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        IconButton(onClick = { viewModel.setReorderMode(true) }) {
                            Icon(Icons.Default.SwapVert, contentDescription = "Reorder mode")
                        }
                        IconButton(onClick = { showDetailsDialog = true }) {
                            Icon(Icons.Default.Info, contentDescription = "Rate Details")
                        }
                        IconButton(
                            onClick = viewModel::refreshRates,
                            enabled = !state.isRefreshing
                        ) {
                            if (state.isRefreshing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh rates")
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Status row: Offline/Live badge + timestamp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OfflineStatusBadge(
                        status = state.rateSet.status,
                        timestampMs = state.rateSet.timestampMs,
                        providerName = "Reference",
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    val dateStr = remember(state.rateSet.timestampMs) {
                        val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                        sdf.format(Date(state.rateSet.timestampMs))
                    }
                    Text(
                        text = "Updated: $dateStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                // Temporary status banner (e.g. Location auto-detected)
                state.statusMessage?.let { msg ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                            IconButton(
                                onClick = viewModel::clearStatusMessage,
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                // Main multi-currency list with left mini-scroll rail indicator
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // Mini scrolling rail indicator on the left
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .fillMaxHeight()
                            .padding(vertical = 12.dp)
                    ) {
                        val totalItems = displayItems.size.coerceAtLeast(1)
                        val firstVisible = lazyListState.firstVisibleItemIndex.toFloat()
                        val scrollRatio = (firstVisible / totalItems).coerceIn(0f, 1f)

                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(3.dp)
                                .align(Alignment.Center)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                        )

                        val animatedOffset by animateFloatAsState(targetValue = scrollRatio, label = "scrollRail")
                        Box(
                            modifier = Modifier
                                .fillMaxHeight(0.25f)
                                .width(4.dp)
                                .align(Alignment.TopCenter)
                                .offset { IntOffset(0, (animatedOffset * 300).dp.roundToPx()) }
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.75f), RoundedCornerShape(2.dp))
                        )
                    }

                    // Currency Cards LazyColumn
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 6.dp, end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(
                            top = 4.dp,
                            bottom = if (isImeOpen || state.isReorderMode) 16.dp else 88.dp
                        )
                    ) {
                        itemsIndexed(displayItems, key = { _, item -> item.currency.code }) { index, item ->
                            val isDragging = item.currency.code == draggingCode
                            CurrencyBoardRowOption2(
                                item = item,
                                index = index,
                                totalItems = displayItems.size,
                                activeCode = state.activeCurrencyCode,
                                rateSet = state.rateSet,
                                isDragging = isDragging,
                                isReorderMode = state.isReorderMode,
                                onAmountChanged = { newAmt ->
                                    viewModel.onAmountChanged(item.currency.code, newAmt)
                                },
                                onSelectAsSource = {
                                    viewModel.selectCurrencyAsInput(item.currency.code)
                                },
                                onMoveUp = {
                                    if (index > 0) {
                                        viewModel.moveCurrency(index, index - 1)
                                    }
                                },
                                onMoveDown = {
                                    if (index < displayItems.size - 1) {
                                        viewModel.moveCurrency(index, index + 1)
                                    }
                                },
                                onDragStart = { onDragStart(item.currency.code) },
                                onDragDelta = onDragDelta,
                                onDragEnd = onDragEnd,
                                onRemove = {
                                    if (displayItems.size > 2) {
                                        viewModel.removeCurrency(item.currency.code)
                                    }
                                },
                                canRemove = displayItems.size > 2,
                                modifier = Modifier
                                    .animateItemPlacement()
                                    .onGloballyPositioned { coordinates ->
                                        if (coordinates.size.height > 0) {
                                            measuredItemHeightPx = coordinates.size.height.toFloat()
                                        }
                                    }
                                    .zIndex(if (isDragging) 10f else 0f)
                                    .offset { IntOffset(0, if (isDragging) dragOffsetY.roundToInt() else 0) }
                            )
                        }

                        item {
                            DisclaimerCard(
                                text = "Rates are indicative reference rates provided for informational purposes. Bureau and retail transaction rates may vary.",
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Floating Pill Bar: Location Auto-Detect + Add Currency (Option 2 style)
            if (!isImeOpen && !state.isReorderMode) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 📍 Location Auto-Detect Pill Button
                        FilledTonalButton(
                            onClick = {
                                val fineGranted = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                                val coarseGranted = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED

                                if (fineGranted || coarseGranted) {
                                    val detected = viewModel.detectAndSelectLocalCurrency(context)
                                    coroutineScope.launch {
                                        val idx = displayItems.indexOfFirst {
                                            it.currency.code.equals(detected.currency.code, ignoreCase = true)
                                        }
                                        if (idx >= 0) {
                                            lazyListState.animateScrollToItem(idx)
                                        }
                                    }
                                } else {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Use My Location",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // + Add Currency Button
                        Button(
                            onClick = {
                                try {
                                    focusManager.clearFocus()
                                } catch (_: Exception) {}
                                showAddCurrencySheet = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Add Currency",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // Add Currency Modal Sheet (Option 3 Design: Alphabetical A-Z, Search, Location Banner, Index Rail)
    if (showAddCurrencySheet) {
        AlphabeticalAddCurrencySheet(
            state = state,
            onDismiss = {
                showAddCurrencySheet = false
            },
            onSearchQueryChanged = viewModel::searchCurrencies,
            onCurrencySelected = { code ->
                viewModel.addCurrency(code)
                showAddCurrencySheet = false
            },
            onAddLocationCurrency = { code ->
                viewModel.addCurrency(code)
                viewModel.selectCurrencyAsInput(code)
                showAddCurrencySheet = false
            }
        )
    }

    // Rate Details Dialog
    if (showDetailsDialog) {
        val meta = StaticFallbackRateProvider.METADATA
        AlertDialog(
            onDismissRequest = { showDetailsDialog = false },
            title = { Text("Currency Rate & Dataset Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Source Provider: ${state.rateSet.providerName}", fontWeight = FontWeight.Bold)
                    Text("Base Currency: ${state.rateSet.baseCurrency}")
                    Text("Active Freshness: ${state.rateSet.status.label}")
                    HorizontalDivider()
                    Text("Reference Dataset:", fontWeight = FontWeight.Bold)
                    Text("• Version: ${meta.version}")
                    Text("• Edition Date: ${meta.datasetDate}")
                    Text("• Supported Currencies: ${meta.currencyCount} ISO 4217")
                    Text("• Reference: ${meta.sourceReference}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        meta.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetailsDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

/**
 * Option 2 Currency Board Row:
 * - Left column: Round Flag + Bold Code + Symbol + Protected Small Conversion Subtext
 *   (Strictly constrained so it never bleeds or wraps under the number)
 * - Right area: Expansive, wide number display taking all remaining width (over 60% of card)
 *   Accommodates 1,000,000 and 140,400,000+ with zero clipping.
 * - Reorder protection: Drag handle is guarded and visible only in Reorder Mode,
 *   with dedicated Up/Down buttons for touchscreens.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CurrencyBoardRowOption2(
    item: CurrencyRowItem,
    index: Int,
    totalItems: Int,
    activeCode: String,
    rateSet: CurrencyRateSet,
    isDragging: Boolean,
    isReorderMode: Boolean,
    onAmountChanged: (String) -> Unit,
    onSelectAsSource: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    val localFocusManager = LocalFocusManager.current

    var textFieldValue by remember(item.currency.code) {
        mutableStateOf(
            TextFieldValue(
                text = item.rawAmount,
                selection = TextRange(item.rawAmount.length)
            )
        )
    }

    var wasSource by remember(item.currency.code) { mutableStateOf(item.isSource) }

    // Sync external amount changes into the text field:
    // 1. Non-source rows mirror the calculated conversion amounts.
    // 2. When a row transitions to source, reset text to the authoritative initial source amount ("0").
    // 3. When source amount is cleared externally (e.g. Clear button pressed), sync the text field.
    LaunchedEffect(item.rawAmount, item.isSource) {
        if (!item.isSource) {
            if (textFieldValue.text != item.rawAmount) {
                textFieldValue = TextFieldValue(
                    text = item.rawAmount,
                    selection = TextRange(item.rawAmount.length)
                )
            }
        } else {
            if (!wasSource) {
                textFieldValue = TextFieldValue(
                    text = item.rawAmount,
                    selection = TextRange(item.rawAmount.length)
                )
            } else if (textFieldValue.text != item.rawAmount && (item.rawAmount == "0" || item.rawAmount.isEmpty())) {
                textFieldValue = TextFieldValue(
                    text = item.rawAmount,
                    selection = TextRange(item.rawAmount.length)
                )
            }
        }
        wasSource = item.isSource
    }

    // Keep active row visible when IME appears
    val isImeVisible = WindowInsets.isImeVisible
    LaunchedEffect(isImeVisible, item.isSource) {
        if (isImeVisible && item.isSource) {
            try {
                bringIntoViewRequester.bringIntoView()
            } catch (_: Exception) {}
        }
    }

    // Dynamic font sizing for number field:
    // Fits numbers like 1,000,000, 140,400,000, and billions on a single line with zero clipping
    val rawLen = item.rawAmount.length
    val dynamicFontSize = when {
        rawLen <= 7 -> 24.sp   // e.g. "100,000"
        rawLen <= 10 -> 21.sp  // e.g. "1,000,000"
        rawLen <= 13 -> 18.sp  // e.g. "140,400,000"
        rawLen <= 16 -> 16.sp  // e.g. "140,400,000.00"
        else -> 14.sp          // e.g. "1,400,000,000.00"
    }

    // Stable visual transformation instance — avoid recreating on every recomposition
    val thousandsTransformation = remember { ThousandsVisualTransformation() }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .scale(if (isDragging) 1.02f else 1.0f)
            .clickable(enabled = !isReorderMode) {
                // Single authoritative tap handler:
                // 1. Set this currency as source (one atomic state mutation) if not already source
                // 2. Request focus (keyboard appears naturally)
                // BringIntoViewRequester handles scrolling when focus + keyboard settle
                if (!item.isSource) {
                    onSelectAsSource()
                }
                try {
                    focusRequester.requestFocus()
                } catch (_: Exception) {}
            },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (isDragging) 2.dp else if (item.isSource) 1.5.dp else 1.dp,
            color = when {
                isDragging -> MaterialTheme.colorScheme.primary
                item.isSource -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            }
        ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isDragging -> MaterialTheme.colorScheme.surfaceVariant
                item.isSource -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.14f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 6.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reorder controls: ONLY active when isReorderMode is true!
            if (isReorderMode) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = index > 0,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Move Up",
                            tint = if (index > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = index < totalItems - 1,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Move Down",
                            tint = if (index < totalItems - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .pointerInput(item.currency.code) {
                                detectDragGestures(
                                    onDragStart = { onDragStart() },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        onDragDelta(dragAmount.y)
                                    },
                                    onDragEnd = { onDragEnd() },
                                    onDragCancel = { onDragEnd() }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "Drag to reorder",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Left Section: National Flag Avatar
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = item.currency.flag, fontSize = 20.sp)
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Currency metadata Column: Code + Symbol on top, Rate subtitle underneath
            // Width constrained so the rate subtitle never spills into the number field
            Column(
                modifier = Modifier.widthIn(min = 60.dp, max = 100.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.currency.code,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 17.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "(${item.currency.symbol})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Small conversion relation subtitle:
                // Formatted in small font (10.5.sp) strictly inside this left column
                val rateSubtitle = remember(item.currency.code, activeCode, rateSet.rates, item.hasCustomOverride, item.customRate) {
                    if (item.isSource) {
                        "Base Input"
                    } else if (item.hasCustomOverride && item.customRate != null) {
                        "1 $activeCode = ${item.customRate}"
                    } else {
                        val fromRate = rateSet.rates[activeCode] ?: 1.0
                        val toRate = rateSet.rates[item.currency.code] ?: 1.0
                        if (fromRate > 0 && toRate > 0) {
                            val ratio = toRate / fromRate
                            val formatted = formatRatio(ratio)
                            "1 $activeCode = $formatted ${item.currency.code}"
                        } else {
                            item.currency.name
                        }
                    }
                }

                Text(
                    text = rateSubtitle,
                    fontSize = 10.5.sp,
                    lineHeight = 12.sp,
                    color = if (item.isSource) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    fontWeight = if (item.isSource) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right Section: Wide, spacious numeric display occupying all remaining width!
            // Clean, borderless, sleek typography directly matching Option 2 Mock
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { newTfv ->
                        val rawText = newTfv.text
                        val isPrevZero = textFieldValue.text.isEmpty() ||
                            textFieldValue.text == "0" ||
                            textFieldValue.text == "0.0" ||
                            textFieldValue.text == "0.00"

                        val sanitizedText = if (isPrevZero) {
                            if (rawText.startsWith("0.") || rawText == "0." || rawText == ".") {
                                "0."
                            } else {
                                val nonZeroDigits = rawText.filter { it in '1'..'9' }
                                if (nonZeroDigits.isNotEmpty() && !rawText.contains('.')) {
                                    // User typed a digit into 0 -> immediately replaces 0 with the typed digit!
                                    // e.g. "06" -> "6", "05800" -> "5800"
                                    rawText.trimStart('0').ifEmpty { "0" }
                                } else if (rawText == "0" || rawText == "00") {
                                    "0"
                                } else {
                                    rawText.filter { it.isDigit() || it == '.' }
                                }
                            }
                        } else if (rawText.length > 1 && rawText.startsWith("0") && rawText[1] != '.') {
                            rawText.trimStart('0').ifEmpty { "0" }
                        } else {
                            rawText.filter { it.isDigit() || it == '.' }
                        }

                        val textChanged = sanitizedText != textFieldValue.text
                        val wasAtEnd = textFieldValue.selection.start >= textFieldValue.text.length
                        val isAtEnd = newTfv.selection.start >= newTfv.text.length

                        val targetCursor = rawText.take(newTfv.selection.start)
                            .count { it.isDigit() || it == '.' }
                            .coerceIn(0, sanitizedText.length)

                        val newSelection = if (wasAtEnd || isAtEnd || isPrevZero || newTfv.selection.start >= rawText.length) {
                            TextRange(sanitizedText.length)
                        } else {
                            TextRange(targetCursor)
                        }

                        textFieldValue = TextFieldValue(text = sanitizedText, selection = newSelection)
                        if (textChanged) {
                            onAmountChanged(sanitizedText)
                        }
                    },
                    visualTransformation = thousandsTransformation,
                    singleLine = true,
                    maxLines = 1,
                    readOnly = !item.isSource || isReorderMode,
                    textStyle = TextStyle(
                        fontSize = dynamicFontSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = if (item.isSource && !isReorderMode) SolidColor(MaterialTheme.colorScheme.primary) else SolidColor(Color.Transparent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            try {
                                localFocusManager.clearFocus()
                            } catch (_: Exception) {}
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                // When focus arrives via direct text field tap (not card click),
                                // ensure this currency becomes source if it isn't already.
                                // This is the ONLY place onSelectAsSource is called from focus
                                // — the card clickable does NOT call it redundantly because
                                // the card click already calls onSelectAsSource before requestFocus.
                                if (!item.isSource) {
                                    onSelectAsSource()
                                }
                                // Place cursor at end for immediate typing readiness
                                textFieldValue = textFieldValue.copy(
                                    selection = TextRange(textFieldValue.text.length)
                                )
                                // BringIntoView is handled by the bringIntoViewRequester
                                // on the Card modifier — it fires automatically when
                                // focus is acquired and keyboard appears, providing
                                // one authoritative scroll mechanism.
                                coroutineScope.launch {
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        }
                )

                // Inline Clear button when focused & non-empty
                if (item.isSource && item.rawAmount.isNotEmpty() && item.rawAmount != "0") {
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { onAmountChanged("0") },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // In Reorder Mode ONLY: show a clear delete button on the right
                if (isReorderMode && canRemove) {
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove currency",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Option 3 Add Currency Bottom Sheet:
 * - Search bar with instant query filter
 * - Highlighted Location Auto-Detect Banner with 1-tap "Add"
 * - Alphabetical world currency list (A to Z strictly sorted)
 * - Vertical A–Z alphabet jump rail along the right edge
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlphabeticalAddCurrencySheet(
    state: CurrencyUiState,
    onDismiss: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onCurrencySelected: (String) -> Unit,
    onAddLocationCurrency: (String) -> Unit
) {
    val context = LocalContext.current
    val sheetListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val currentCodes = remember(state.items) { state.items.map { it.currency.code.uppercase() } }

    val alphabetLetters = remember {
        ('A'..'Z').toList()
    }

    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            searchFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxHeight(0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header: Title + Close Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Add Currency",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Search Bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = { Text("Search by currency, country, or code (e.g. KES, Kenya, EUR)...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = if (state.searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(searchFocusRequester)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Location Auto-Detect Highlighted Banner (Option 3 Design)
            val detectedLoc = state.detectedLocation ?: remember { CurrencyCatalog.detectLocalCurrency(context) }
            val isLocAlreadyAdded = currentCodes.contains(detectedLoc.currency.code.uppercase())

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!isLocAlreadyAdded) {
                            onAddLocationCurrency(detectedLoc.currency.code)
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Location Detected: ${detectedLoc.countryName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "• Tap to add ${detectedLoc.currency.name} (${detectedLoc.currency.code})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (isLocAlreadyAdded) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Added",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = { onAddLocationCurrency(detectedLoc.currency.code) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("Add", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Alphabetical list with A-Z jump rail
            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = sheetListState,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    itemsIndexed(state.availableCurrencies, key = { _, curr -> curr.code }) { _, curr ->
                        val isAdded = currentCodes.contains(curr.code.uppercase())
                        Surface(
                            onClick = {
                                if (!isAdded) {
                                    onCurrencySelected(curr.code)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isAdded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (isAdded) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = curr.flag, fontSize = 26.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = curr.code,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = curr.symbol,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Text(
                                        text = "${curr.name} • ${curr.country}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isAdded) {
                                    Text(
                                        text = "Added",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                } else {
                                    FilledIconButton(
                                        onClick = { onCurrencySelected(curr.code) },
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer
                                        ),
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Add",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Vertical A–Z Fast-Scroll Alphabet Index Rail
                if (state.searchQuery.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .width(22.dp)
                            .fillMaxHeight()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        alphabetLetters.forEach { letter ->
                            Text(
                                text = letter.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        val targetIdx = state.availableCurrencies.indexOfFirst {
                                            it.name.startsWith(letter.toString(), ignoreCase = true) ||
                                            it.code.startsWith(letter.toString(), ignoreCase = true)
                                        }
                                        if (targetIdx >= 0) {
                                            coroutineScope.launch {
                                                sheetListState.scrollToItem(targetIdx)
                                            }
                                        }
                                    }
                                    .padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Formats exchange ratios with adaptive decimal precision and comma grouping.
 * - e.g. 0.0071, 0.000071, 1.08, 129.50, 3,950
 */
private fun formatRatio(ratio: Double): String {
    if (ratio <= 0.0) return "0.00"
    return when {
        ratio < 0.0001 -> {
            val symbols = DecimalFormatSymbols.getInstance(Locale.US)
            DecimalFormat("0.000000", symbols).format(ratio)
        }
        ratio < 0.01 -> {
            val symbols = DecimalFormatSymbols.getInstance(Locale.US)
            DecimalFormat("0.0000", symbols).format(ratio)
        }
        ratio < 100.0 -> {
            val symbols = DecimalFormatSymbols.getInstance(Locale.US)
            DecimalFormat("#,##0.00", symbols).format(ratio)
        }
        else -> {
            val symbols = DecimalFormatSymbols.getInstance(Locale.US)
            DecimalFormat("#,##0", symbols).format(ratio)
        }
    }
}

/**
 * VisualTransformation that adds thousands-separator commas to numeric text.
 * The TextField value stays as raw digits (e.g., "12345"), while the display
 * shows formatted text (e.g., "12,345"). Cursor position is automatically
 * mapped through the OffsetMapping so typing never causes cursor jumping.
 */
class ThousandsVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val originalText = text.text
        if (originalText.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val isNegative = originalText.startsWith("-")
        val startIndex = if (isNegative) 1 else 0
        val dotIndex = originalText.indexOf('.').let { if (it == -1) originalText.length else it }
        val intLength = dotIndex - startIndex

        if (intLength <= 3) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val formattedSb = StringBuilder()
        val origToTrans = IntArray(originalText.length + 1)
        val transToOrig = ArrayList<Int>()

        if (isNegative) {
            origToTrans[0] = 0
            transToOrig.add(0)
            formattedSb.append('-')
        }

        for (i in startIndex until dotIndex) {
            val digitsFromRight = dotIndex - i
            if (i > startIndex && digitsFromRight % 3 == 0) {
                transToOrig.add(i)
                formattedSb.append(',')
            }
            origToTrans[i] = formattedSb.length
            transToOrig.add(i)
            formattedSb.append(originalText[i])
        }

        for (i in dotIndex until originalText.length) {
            origToTrans[i] = formattedSb.length
            transToOrig.add(i)
            formattedSb.append(originalText[i])
        }

        // Map the end-of-string cursor position
        origToTrans[originalText.length] = formattedSb.length
        transToOrig.add(originalText.length)

        val formatted = formattedSb.toString()
        val transToOrigArray = transToOrig.toIntArray()

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val clamped = offset.coerceIn(0, originalText.length)
                return origToTrans[clamped].coerceIn(0, formatted.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val clamped = offset.coerceIn(0, transToOrigArray.size - 1)
                return transToOrigArray[clamped].coerceIn(0, originalText.length)
            }
        }

        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

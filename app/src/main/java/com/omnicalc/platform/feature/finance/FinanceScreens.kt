package com.omnicalc.platform.feature.finance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnicalc.platform.ui.components.DisclaimerCard
import com.omnicalc.platform.ui.components.NumberInputField
import com.omnicalc.platform.ui.components.ResultCard
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscountScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var originalPriceStr by remember { mutableStateOf("1000") }
    var discountPercentStr by remember { mutableStateOf("20") }

    val result = remember(originalPriceStr, discountPercentStr) {
        try {
            val price = BigDecimal(originalPriceStr)
            val disc = BigDecimal(discountPercentStr)
            FinanceCalculators.calculateDiscount(price, disc)
        } catch (_: Exception) {
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Discount Calculator", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            NumberInputField(
                value = originalPriceStr,
                onValueChange = { originalPriceStr = it },
                label = "Original Price"
            )

            NumberInputField(
                value = discountPercentStr,
                onValueChange = { discountPercentStr = it },
                label = "Discount Percentage",
                suffix = "%"
            )

            // Quick discount chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("5", "10", "15", "20", "25", "30", "50", "70").forEach { pct ->
                    SuggestionChip(
                        onClick = { discountPercentStr = pct },
                        label = { Text("$pct%") },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            if (result != null) {
                ResultCard(
                    title = "Final Price After Discount",
                    primaryValue = result.finalPrice.toPlainString(),
                    secondaryValue = "You save: ${result.amountSaved.toPlainString()}",
                    formula = "Original × (1 - Discount/100)"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaxVatScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var amountStr by remember { mutableStateOf("1000") }
    var taxRateStr by remember { mutableStateOf("16") } // Kenya standard VAT 16%
    var isTaxInclusive by remember { mutableStateOf(false) }

    val result = remember(amountStr, taxRateStr, isTaxInclusive) {
        try {
            val amt = BigDecimal(amountStr)
            val rate = BigDecimal(taxRateStr)
            FinanceCalculators.calculateTax(amt, rate, isTaxInclusive)
        } catch (_: Exception) {
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sales Tax & VAT", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Mode toggle: Tax Exclusive vs Tax Inclusive
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !isTaxInclusive,
                    onClick = { isTaxInclusive = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("Tax Exclusive (+Tax)")
                }
                SegmentedButton(
                    selected = isTaxInclusive,
                    onClick = { isTaxInclusive = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("Tax Inclusive (Extract)")
                }
            }

            NumberInputField(
                value = amountStr,
                onValueChange = { amountStr = it },
                label = if (isTaxInclusive) "Gross Amount (Including Tax)" else "Net Amount (Before Tax)"
            )

            NumberInputField(
                value = taxRateStr,
                onValueChange = { taxRateStr = it },
                label = "Tax Rate",
                suffix = "%"
            )

            // Preset regional VAT chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Pair("16", "Kenya VAT (16%)"),
                    Pair("18", "Tanzania/Uganda/Rwanda (18%)"),
                    Pair("15", "South Africa (15%)"),
                    Pair("7.5", "Nigeria (7.5%)"),
                    Pair("20", "UK/France (20%)"),
                    Pair("5", "UAE (5%)")
                ).forEach { (rate, label) ->
                    SuggestionChip(
                        onClick = { taxRateStr = rate },
                        label = { Text(label) },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            if (result != null) {
                ResultCard(
                    title = if (isTaxInclusive) "Net Price Without Tax" else "Total Price With Tax",
                    primaryValue = (if (isTaxInclusive) result.netAmount else result.grossTotal).toPlainString(),
                    secondaryValue = "Tax Amount (${taxRateStr}%): ${result.taxAmount.toPlainString()} • Net: ${result.netAmount.toPlainString()}",
                    formula = if (isTaxInclusive) "Gross / (1 + Rate/100)" else "Net × (1 + Rate/100)"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var principalStr by remember { mutableStateOf("100000") }
    var interestRateStr by remember { mutableStateOf("12") }
    var termYearsStr by remember { mutableStateOf("2") }
    var showAmortization by remember { mutableStateOf(false) }

    val result = remember(principalStr, interestRateStr, termYearsStr) {
        try {
            val p = BigDecimal(principalStr)
            val r = BigDecimal(interestRateStr)
            val y = termYearsStr.toInt()
            FinanceCalculators.calculateLoan(p, r, y * 12)
        } catch (_: Exception) {
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Loan & Amortization", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                NumberInputField(
                    value = principalStr,
                    onValueChange = { principalStr = it },
                    label = "Loan Principal Amount"
                )
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberInputField(
                        value = interestRateStr,
                        onValueChange = { interestRateStr = it },
                        label = "Interest Rate (Annual)",
                        suffix = "%",
                        modifier = Modifier.weight(1f)
                    )
                    NumberInputField(
                        value = termYearsStr,
                        onValueChange = { termYearsStr = it },
                        label = "Term (Years)",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (result != null) {
                item {
                    ResultCard(
                        title = "Monthly Payment",
                        primaryValue = result.periodicPayment.toPlainString(),
                        secondaryValue = "Total Interest: ${result.totalInterest.toPlainString()} • Total Repayment: ${result.totalPayment.toPlainString()}",
                        formula = "P × [r(1+r)ⁿ] / [(1+r)ⁿ - 1]"
                    )
                }

                item {
                    DisclaimerCard(
                        text = "Calculations are mathematical projections assuming a fixed interest rate. Bank loan fees, insurance, or variable rate adjustments are not included."
                    )
                }

                item {
                    OutlinedButton(
                        onClick = { showAmortization = !showAmortization },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (showAmortization) "Hide Amortization Schedule" else "Show Full Amortization Schedule (${result.schedule.size} Months)")
                    }
                }

                if (showAmortization) {
                    item {
                        Text(
                            text = "Payment Schedule Breakdown",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    items(result.schedule) { row ->
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Month ${row.paymentNumber}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "Bal: ${row.endingBalance.toPlainString()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Principal: ${row.principalPayment.toPlainString()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Interest: ${row.interestPayment.toPlainString()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

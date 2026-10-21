package com.omnicalc.platform.feature.finance

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
fun SavingsScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    var initialDepositStr by remember { mutableStateOf("10000") }
    var monthlyDepositStr by remember { mutableStateOf("1000") }
    var interestRateStr by remember { mutableStateOf("8") }
    var yearsStr by remember { mutableStateOf("5") }

    val result = remember(initialDepositStr, monthlyDepositStr, interestRateStr, yearsStr) {
        try {
            val init = BigDecimal(initialDepositStr)
            val month = BigDecimal(monthlyDepositStr)
            val rate = BigDecimal(interestRateStr)
            val yrs = yearsStr.toInt()
            FinanceCalculators.calculateSavings(init, month, rate, yrs)
        } catch (_: Exception) {
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Savings & Compound Interest", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            NumberInputField(value = initialDepositStr, onValueChange = { initialDepositStr = it }, label = "Initial Deposit")
            NumberInputField(value = monthlyDepositStr, onValueChange = { monthlyDepositStr = it }, label = "Monthly Contribution")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberInputField(value = interestRateStr, onValueChange = { interestRateStr = it }, label = "Annual Interest Rate", suffix = "%", modifier = Modifier.weight(1f))
                NumberInputField(value = yearsStr, onValueChange = { yearsStr = it }, label = "Duration (Years)", modifier = Modifier.weight(1f))
            }

            if (result != null) {
                ResultCard(
                    title = "Projected Future Balance",
                    primaryValue = result.finalBalance.toPlainString(),
                    secondaryValue = "Total Principal: ${result.totalPrincipal.toPlainString()} • Total Interest: ${result.totalInterestEarned.toPlainString()}",
                    formula = "A = P(1 + r/n)ⁿᵗ + PMT × [(1 + r/n)ⁿᵗ - 1] / (r/n)"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfitMarginScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    var costStr by remember { mutableStateOf("100") }
    var priceStr by remember { mutableStateOf("150") }
    var quantityStr by remember { mutableStateOf("1") }

    val result = remember(costStr, priceStr, quantityStr) {
        try {
            val cost = BigDecimal(costStr)
            val price = BigDecimal(priceStr)
            val qty = quantityStr.toInt()
            FinanceCalculators.calculateProfitMargin(cost, price, qty)
        } catch (_: Exception) {
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profit & Margin", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            NumberInputField(value = costStr, onValueChange = { costStr = it }, label = "Cost Price (per item)")
            NumberInputField(value = priceStr, onValueChange = { priceStr = it }, label = "Selling Price (per item)")
            NumberInputField(value = quantityStr, onValueChange = { quantityStr = it }, label = "Quantity Sold")

            if (result != null) {
                ResultCard(
                    title = "Gross Profit",
                    primaryValue = result.grossProfit.toPlainString(),
                    secondaryValue = "Profit Margin: ${result.profitMarginPercent.toPlainString()}% • Markup: ${result.markupPercent.toPlainString()}%",
                    explanation = "Margin is profit as % of selling price. Markup is profit as % of cost price."
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TipScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    var billStr by remember { mutableStateOf("2500") }
    var tipPercentStr by remember { mutableStateOf("15") }
    var numPeople by remember { mutableStateOf(2) }

    val result = remember(billStr, tipPercentStr, numPeople) {
        try {
            val bill = BigDecimal(billStr)
            val tip = BigDecimal(tipPercentStr)
            FinanceCalculators.calculateTip(bill, tip, numPeople)
        } catch (_: Exception) {
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tip & Bill Split", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            NumberInputField(value = billStr, onValueChange = { billStr = it }, label = "Bill Amount")
            NumberInputField(value = tipPercentStr, onValueChange = { tipPercentStr = it }, label = "Tip Percentage", suffix = "%")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("10", "15", "18", "20", "25").forEach { t ->
                    SuggestionChip(onClick = { tipPercentStr = t }, label = { Text("$t%") })
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Split Between ($numPeople people)", fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { if (numPeople > 1) numPeople-- }) { Text("-") }
                    FilledTonalButton(onClick = { numPeople++ }) { Text("+") }
                }
            }

            if (result != null) {
                ResultCard(
                    title = "Total Per Person",
                    primaryValue = result.perPersonTotal.toPlainString(),
                    secondaryValue = "Tip per person: ${result.perPersonTip.toPlainString()} • Overall Total: ${result.totalBill.toPlainString()}"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscountTaxScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    var priceStr by remember { mutableStateOf("10000") }
    var discountStr by remember { mutableStateOf("20") }
    var taxStr by remember { mutableStateOf("16") }
    var taxAfterDiscount by remember { mutableStateOf(true) }

    val result = remember(priceStr, discountStr, taxStr, taxAfterDiscount) {
        try {
            val p = BigDecimal(priceStr)
            val d = BigDecimal(discountStr)
            val t = BigDecimal(taxStr)
            FinanceCalculators.calculateDiscountAndTax(p, d, t, taxAfterDiscount)
        } catch (_: Exception) {
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Discount + Tax Calculator", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            NumberInputField(value = priceStr, onValueChange = { priceStr = it }, label = "Original Price")
            NumberInputField(value = discountStr, onValueChange = { discountStr = it }, label = "Discount Percentage", suffix = "%")
            NumberInputField(value = taxStr, onValueChange = { taxStr = it }, label = "Tax Rate (VAT / Sales Tax)", suffix = "%")

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = taxAfterDiscount,
                    onClick = { taxAfterDiscount = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("Tax After Discount") }
                SegmentedButton(
                    selected = !taxAfterDiscount,
                    onClick = { taxAfterDiscount = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("Tax Before Discount") }
            }

            if (result != null) {
                ResultCard(
                    title = "Final Payable Price",
                    primaryValue = result.finalPrice.toPlainString(),
                    secondaryValue = "Amount Saved: ${result.amountSaved.toPlainString()} • Tax Added: ${result.taxAmount.toPlainString()}",
                    explanation = if (taxAfterDiscount) "Tax is applied on the discounted price." else "Tax is applied on the original list price."
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreakEvenScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    var fixedCostsStr by remember { mutableStateOf("50000") }
    var variableCostStr by remember { mutableStateOf("150") }
    var sellingPriceStr by remember { mutableStateOf("250") }

    val result = remember(fixedCostsStr, variableCostStr, sellingPriceStr) {
        try {
            val fc = BigDecimal(fixedCostsStr)
            val vc = BigDecimal(variableCostStr)
            val sp = BigDecimal(sellingPriceStr)
            FinanceCalculators.calculateBreakEven(fc, vc, sp)
        } catch (_: Exception) {
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Break-Even Analysis", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            NumberInputField(value = fixedCostsStr, onValueChange = { fixedCostsStr = it }, label = "Total Fixed Costs (Rent, Salaries, etc.)")
            NumberInputField(value = variableCostStr, onValueChange = { variableCostStr = it }, label = "Variable Cost per Unit (Materials, Labor)")
            NumberInputField(value = sellingPriceStr, onValueChange = { sellingPriceStr = it }, label = "Selling Price per Unit")

            if (result != null) {
                ResultCard(
                    title = "Break-Even Point",
                    primaryValue = "${result.breakEvenUnits.toPlainString()} Units",
                    secondaryValue = "Break-Even Revenue: ${result.breakEvenRevenue.toPlainString()}",
                    explanation = "Contribution Margin: ${result.contributionMargin.toPlainString()} per unit (${result.contributionMarginRatio.toPlainString()}%)"
                )
            } else {
                Text(
                    text = "Selling price must be greater than variable cost per unit.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitPriceScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    var priceAStr by remember { mutableStateOf("500") }
    var qtyAStr by remember { mutableStateOf("2") }

    var priceBStr by remember { mutableStateOf("700") }
    var qtyBStr by remember { mutableStateOf("3") }

    val comparison = remember(priceAStr, qtyAStr, priceBStr, qtyBStr) {
        try {
            val pA = BigDecimal(priceAStr)
            val qA = BigDecimal(qtyAStr)
            val pB = BigDecimal(priceBStr)
            val qB = BigDecimal(qtyBStr)

            if (qA > BigDecimal.ZERO && qB > BigDecimal.ZERO) {
                val unitPriceA = pA.divide(qA, 4, java.math.RoundingMode.HALF_UP)
                val unitPriceB = pB.divide(qB, 4, java.math.RoundingMode.HALF_UP)

                val cheaper = if (unitPriceA < unitPriceB) "Product A" else if (unitPriceB < unitPriceA) "Product B" else "Both equal"
                val percentSaved = if (cheaper == "Product A") {
                    unitPriceB.subtract(unitPriceA).divide(unitPriceB, 4, java.math.RoundingMode.HALF_UP).multiply(BigDecimal(100))
                } else if (cheaper == "Product B") {
                    unitPriceA.subtract(unitPriceB).divide(unitPriceA, 4, java.math.RoundingMode.HALF_UP).multiply(BigDecimal(100))
                } else BigDecimal.ZERO

                Triple(unitPriceA, unitPriceB, "$cheaper is cheaper by ${percentSaved.setScale(1, java.math.RoundingMode.HALF_UP)}%")
            } else null
        } catch (_: Exception) {
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Unit Price Comparator", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Product A", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberInputField(value = priceAStr, onValueChange = { priceAStr = it }, label = "Price", modifier = Modifier.weight(1f))
                        NumberInputField(value = qtyAStr, onValueChange = { qtyAStr = it }, label = "Quantity", modifier = Modifier.weight(1f))
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Product B", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberInputField(value = priceBStr, onValueChange = { priceBStr = it }, label = "Price", modifier = Modifier.weight(1f))
                        NumberInputField(value = qtyBStr, onValueChange = { qtyBStr = it }, label = "Quantity", modifier = Modifier.weight(1f))
                    }
                }
            }

            if (comparison != null) {
                ResultCard(
                    title = "Comparison Verdict",
                    primaryValue = comparison.third,
                    secondaryValue = "Product A: ${comparison.first.setScale(2, java.math.RoundingMode.HALF_UP)} / unit • Product B: ${comparison.second.setScale(2, java.math.RoundingMode.HALF_UP)} / unit"
                )
            }
        }
    }
}


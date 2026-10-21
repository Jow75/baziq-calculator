# OmniCalc — Professional Offline-First Calculator & Conversion Platform for Android

**OmniCalc** is a high-performance, modular, offline-first calculation and conversion ecosystem engineered for Android (API 26+). It starts as an instantaneous, zero-delay standard and scientific calculator, but houses an extensible multi-domain suite spanning finance, unit conversions, multi-currency live boards, dates/times, health metrics, programmer bitwise manipulation, geometry, statistics, and physical constants.

---

## 1. Product Vision & Architectural Tenets

1. **Instant First-Interaction (Calculator-First)**:
   - On cold launch, OmniCalc immediately renders the interactive calculation surface.
   - **Zero splash delays, zero authentication gates, zero onboarding questionnaires, zero full-screen advertisements.**
2. **Infinite Multi-Currency Workspace**:
   - Currency converter is **not restricted to 3 or 4 currencies**. Users can add tens of currencies simultaneously (e.g., KES, USD, EUR, KMF, TZS, GBP, AED, UGX, RWF, BIF, JPY, CNY).
   - Editing **any currency row** recalculates all other visible currencies in real time.
   - First-class support for African currencies including Kenyan Shilling (KES), Tanzanian Shilling (TZS), and Comorian Franc (KMF).
3. **True Offline-First Design**:
   - Arithmetic, scientific functions, all unit conversions, finance calculators, dates, health metrics, geometry, and programmer logic are 100% deterministic and execute completely offline.
   - Currency rates utilize a multi-tiered repository pattern: Local Room Cache $\rightarrow$ Static Reference Rates $\rightarrow$ Optional Remote API Proxy.
   - Real-time data freshness badges indicate whether rates are `CURRENT`, `RECENT`, `STALE`, or `OFFLINE`.
4. **Authoritative Units Engine**:
   - Conversion factors are sourced directly from BIPM (SI standard) and NIST guidelines.
   - Supports 16 dimensions with strict dimension-safety (cannot convert meters to kilograms).
   - Distinguishes US Customary vs. Imperial liquid measures, SI decimal storage ($10^3$) vs. IEC binary storage ($2^{10}$), affine temperatures ($^\circ\text{C}, ^\circ\text{F}, \text{K}$), and reciprocal fuel consumption ($\text{L}/100\text{km} \leftrightarrow \text{MPG}$).
5. **Universal Search**:
   - Instant local search indexed across titles, keywords, symbols, categories, aliases, and abbreviations.
   - Handles multi-word queries, currency codes, and common aliases (e.g. typing `"kes"` or `"kenya"` locates Currency Converter; `"loan"` locates Loan Payment & Amortization).
6. **Arbitrary Precision Arithmetic**:
   - Financial, discount, tax, and currency engines operate strictly using Java `BigDecimal` to eliminate IEEE-754 binary floating-point representation and rounding drift.
   - Intermediate calculations maintain higher internal scale before applying display rounding.

---

## 2. Technology Stack

- **Language**: Kotlin 1.9.24 (Coroutines, Flow, StateFlow)
- **UI Framework**: Jetpack Compose 1.6.8 + Material 3 (1.2.1)
- **Architecture**: MVVM with Unidirectional Data Flow (UDF) & Clean Architecture
- **Dependency Injection / Lifecycle**: Android Jetpack ViewModel & Lifecycle Runtime Compose
- **Local Persistence**:
  - **Room Database 2.6.1**: Calculation history, pinned favorites, cached exchange rates, custom rate overrides, active currency workspace.
  - **Jetpack DataStore (Preferences)**: User settings (precision, number formatting, dark/light theme, haptics, measurement systems).
- **Network / Serialization**:
  - OkHttp 4.12.0
  - Moshi 1.15.1 (Reflective and Kotlin code-gen)
- **Target Platform**: Android 8.0 (API level 26) through Android 14+ (API level 34). Optimized for mid-range SoCs (e.g., MediaTek Helio G88, 4–8 GB RAM).

---

## 3. Modular Architecture Overview

```
com.omnicalc.platform/
├── core/
│   ├── math/              # BigDecimalMath, ExpressionEvaluator (RPN/Shunting-yard), FractionEngine, ProgrammerEngine, Constants
│   ├── units/             # Dimension, UnitDefinition, UnitConverterEngine (BIPM/NIST)
│   ├── currency/          # CurrencyModel, CurrencyRateProvider, ApiCurrencyRateProvider, StaticFallbackRateProvider, CurrencyRepository
│   ├── database/          # Room Database, Daos, Entities (History, Favorites, CachedRates, Currencies)
│   ├── preferences/       # UserPreferencesRepository (DataStore)
│   └── registry/          # ToolRegistry & Universal Search Index
├── feature/
│   ├── calculator/        # Basic & Scientific Keypad, Expression Display, Memory (MC, MR, M+, M-)
│   ├── currency/          # Multi-Currency Live Board, Currency Picker with Search, Manual Refresh, Rate Source Badging
│   ├── units/             # 16-Dimension Converter, Swap, Copy
│   ├── finance/           # Discount, Sales Tax/VAT, Loan + Amortization, Savings, Profit/Margin, Break-Even, Tip
│   ├── dates/             # Age Calculator, Date Difference & Duration, Time Duration (overnight aware), Timezone Converter
│   ├── health/            # WHO BMI, Harris-Benedict & Mifflin-St Jeor BMR/TDEE, Ovulation Calendar Estimate (with disclaimers)
│   ├── fuel/              # Fuel Cost, Fuel Efficiency (L/100km, km/L, MPG US, MPG Imperial)
│   ├── programmer/        # HEX, DEC, OCT, BIN 4-Base live view, Bitwise (AND, OR, XOR, NOT, SHL, SHR), Bit Toggling
│   ├── geometry/          # 2D/3D Shapes, Triangle Inequality Solver (SSS, SAS, ASA, AAS)
│   ├── statistics/        # Mean, Median, Mode, Sample & Population Variance/Std Dev, Range, nCr, nPr
│   ├── education/         # Grade Calculator, Weighted GPA Calculator
│   ├── constants/         # CODATA 2019 Physical Constants with Search
│   ├── search/            # Universal Tool Search
│   ├── history/           # Local Calculation History (Filter by tool, copy, delete, clear all)
│   └── settings/          # Decimal precision, formatting locale, dark/light theme, haptic feedback
└── ui/
    ├── components/        # Reusable Compose widgets (CalculatorDisplay, CalculatorKeypad, QuickToolsBar, CommonUiComponents)
    ├── navigation/        # OmniCalcNavHost with typed navigation routes
    └── theme/             # Material 3 Color tokens, Typography, Theme wrapper
```

---

## 4. Calculator Catalog

| Tool ID | Title | Category | Description | Primary Engine / Formula |
|---|---|---|---|---|
| `basic_calc` | Standard & Scientific Calculator | Basic Math / Scientific | Arithmetic, scientific functions, trigonometry, logarithms, memory | Shunting-Yard AST / RPN Parser |
| `currency` | Multi-Currency Converter | Currency | Unlimited live conversion board with offline cache & freshness badges | Cross-rate: $A_{target} = A_{src} \cdot \frac{R_{target}}{R_{src}}$ |
| `units` | Unit Converter | Conversions | 16 dimensions, affine conversions, reciprocal fuel, SI vs IEC | BIPM/NIST Canonical Unit Normalization |
| `discount` | Discount Calculator | Finance / Shopping | Original price, discount percent, savings, final price, reverse mode | $S = P \cdot d$, $P_{final} = P - S$ |
| `tax` | Sales Tax & VAT Calculator | Finance / Business | Inclusive/exclusive tax, VAT calculation, reverse gross-to-net | Exclusive: $T = P \cdot r$, $Tot = P + T$; Inclusive: $P = \frac{Tot}{1+r}$ |
| `loan` | Loan & Amortization | Finance | Fixed-rate loans, periodic payments, monthly amortisation schedule | $M = P \frac{r(1+r)^n}{(1+r)^n - 1}$ |
| `savings` | Savings & Compound Interest | Finance | Compound interest with periodic contributions | $A = P(1+\frac{r}{n})^{nt} + PMT \frac{(1+\frac{r}{n})^{nt}-1}{r/n}$ |
| `profit_margin` | Profit, Margin & Markup | Business / Shopping | Cost, selling price, gross profit, margin vs markup distinction | $\text{Margin} = \frac{\text{Profit}}{\text{Price}}$, $\text{Markup} = \frac{\text{Profit}}{\text{Cost}}$ |
| `break_even` | Break-Even Analysis | Business | Fixed costs, variable costs per unit, unit price | $Q_{BE} = \frac{FC}{P - VC}$ |
| `tip` | Tip & Bill Split | Finance / Shopping | Bill amount, tip percentage, split across $N$ guests | $\text{Tip} = B \cdot t$, $\text{PerPerson} = \frac{B + \text{Tip}}{N}$ |
| `age` | Age & Milestone Calculator | Dates & Time | Precise calendar age (years, months, days), days to next birthday | `java.time.Period` & `java.time.temporal.ChronoUnit` |
| `date_diff` | Date Difference | Dates & Time | Duration between two calendar dates in days, weeks, months | Calendar-accurate date diff with leap year support |
| `time_duration` | Time Calculator | Dates & Time | Duration between start and end times, handles overnight spans | Modulo 24-hour time difference math |
| `timezone` | Timezone Converter | Dates & Time | Real-time conversion across IANA global timezones | IANA ZoneId / ZonedDateTime rules |
| `bmi` | Body Mass Index (BMI) | Health & Fitness | Weight and height metric/imperial input, WHO classification | $\text{BMI} = \frac{\text{weight (kg)}}{(\text{height (m)})^2}$ |
| `bmr_tdee` | BMR & TDEE Calorie Needs | Health & Fitness | Basal Metabolic Rate and Total Daily Energy Expenditure | Mifflin-St Jeor & Harris-Benedict formulas |
| `ovulation` | Ovulation & Fertility Estimate | Health & Fitness | Calendar-based fertile window and estimated ovulation | 14-day luteal estimation with explicit disclaimers |
| `fuel_cost` | Fuel Trip Cost | Travel / Fuel | Fuel efficiency, trip distance, fuel unit price | $\text{Cost} = \frac{\text{Distance}}{\text{Efficiency}} \cdot \text{Price}$ |
| `fuel_efficiency` | Fuel Efficiency Converter | Travel / Fuel | Reciprocal conversion across km/L, L/100km, US MPG, Imp MPG | Non-linear: $\text{L}/100\text{km} = \frac{100}{\text{km/L}}$ |
| `programmer` | Programmer Calculator | Programming | 4-base live sync (HEX, DEC, OCT, BIN), bitwise ops, word-size toggles | Pure 64-bit integer bitwise & bit masking |
| `geometry` | 2D & 3D Geometry | Geometry | Perimeter, area, surface area, volume for 15 geometric solids | Standard Euclidean geometry definitions |
| `triangle_solver`| Triangle Solver | Geometry | SSS, SAS, ASA, AAS solver with triangle inequality validation | Law of Cosines & Law of Sines |
| `statistics` | Descriptive Statistics | Statistics | Mean, median, mode, sample & population variance / std dev | Two-pass numerically stable statistics |
| `probability` | Permutations & Combinations | Statistics / Math | Factorial, $n\text{P}r$, $n\text{C}r$ combinations and permutations | BigInteger factorial-based exact computation |
| `grade` | Grade Calculator | Education | Points earned vs possible, custom grading scales | Percentage to Letter Grade mapping |
| `gpa` | GPA Calculator | Education | Weighted & unweighted grade point average across courses | Credit-weighted grade point summation |
| `constants` | Physical Constants Reference | Basic Math / Scientific | Speed of light, Planck constant, Gravitation, Avogadro, etc. | CODATA 2019 reference dataset |

---

## 5. Currency Provider Architecture & Offline Fallback

### Core Principles
- **No Embedded Client Secrets**: The Android application never bundles production API keys.
- **Provider Swappability**: The domain layer only interfaces with `CurrencyRateProvider`.
- **Offline Tolerance**: Exchange rates are cached into the local Room database (`cached_rates`). When disconnected, OmniCalc calculates using the last known rates and displays an amber status badge (e.g., `Offline — Last updated 2 hours ago`).

### Deriving Cross Rates
If rates are indexed against base currency $B$ (default USD):
$$\text{Rate}(S \to T) = \frac{\text{Rate}(B \to T)}{\text{Rate}(B \to S)}$$
OmniCalc handles cross-rates at 12 decimal places using `BigDecimal` before formatting to the target currency's standard decimal places.

---

## 6. How-To Developer Guides

### 6.1 How to Add a New Calculator

1. **Define the Tool in `ToolRegistry.kt`**:
   ```kotlin
   ToolDefinition(
       id = "concrete_volume",
       title = "Concrete Volume Calculator",
       shortDescription = "Calculate required concrete volume and bags for slabs and footings",
       category = ToolCategory.ENGINEERING,
       iconName = "construction",
       searchKeywords = listOf("concrete", "slab", "cement", "construction", "volume")
   )
   ```
2. **Implement the Math Engine**:
   Create `com.omnicalc.platform.feature.engineering.ConcreteCalculator.kt`:
   ```kotlin
   data class ConcreteResult(val cubicMeters: BigDecimal, val bagsNeeded: Int)
   object ConcreteEngine {
       fun calculate(lengthM: BigDecimal, widthM: BigDecimal, depthM: BigDecimal): ConcreteResult {
           val volume = lengthM.multiply(widthM).multiply(depthM)
           val bags = volume.divide(BigDecimal("0.015"), 0, RoundingMode.CEILING).toInt()
           return ConcreteResult(volume, bags)
       }
   }
   ```
3. **Build the Compose Screen**:
   Create a composable leveraging `NumberInputRow` and `ResultCard` from `CommonUiComponents.kt`.
4. **Register Route in `OmniCalcNavHost.kt`**:
   Add `composable("tool_concrete_volume") { ConcreteScreen(onBack = { navController.popBackStack() }) }`.
5. **Add Unit Tests in `app/src/test/java/com/omnicalc/platform/`**.

### 6.2 How to Add a New Unit Dimension or Unit

1. **Add to `UnitDefinition.kt`**:
   ```kotlin
   // For an existing dimension (e.g. Length)
   UnitDefinition(
       id = "furlong",
       dimension = Dimension.LENGTH,
       name = "Furlong",
       symbol = "fur",
       conversionFactorToBase = BigDecimal("201.168") // 1 furlong = 201.168 meters
   )
   ```
2. **If adding a completely new Dimension**:
   - Add entry to enum `enum class Dimension` in `Dimension.kt`.
   - Add units in `UnitDefinition.getAllUnits()`.
   - Update `UnitConverterEngineTest.kt` to verify bidirectional accuracy ($A \to B \to A$).

### 6.3 How to Add or Replace a Currency Provider

1. Implement the `CurrencyRateProvider` interface:
   ```kotlin
   class CustomCentralBankProvider(private val okHttpClient: OkHttpClient) : CurrencyRateProvider {
       override val providerName: String = "Central Bank Reference"
       override suspend fun getLatestRates(baseCurrency: String): Result<CurrencyRateResponse> {
           // Fetch and map to normalized CurrencyRateResponse
       }
   }
   ```
2. Inject or supply the provider in `CurrencyRepository`:
   ```kotlin
   val repository = CurrencyRepository(
       database = db,
       remoteProvider = CustomCentralBankProvider(okHttpClient)
   )
   ```

---

## 7. Verification & Quality Report

- **Test Suite**: 40 Automated JVM Unit Tests covering:
  - High-precision arithmetic (`BigDecimalMathTest`): Square roots, factorials, powers, rounding policies.
  - Expression evaluation (`ExpressionEvaluatorTest`): Operator precedence, nested parentheses, unary negation, DEG/RAD trig functions, log/ln, divide-by-zero protection.
  - Fractions (`FractionEngineTest`): Mixed fraction parsing, continued fraction approximations, simplification.
  - Unit conversions (`UnitConverterEngineTest`): Reversibility tests across all 16 dimensions, affine temperature ($^\circ\text{C} \leftrightarrow ^\circ\text{F} \leftrightarrow \text{K}$), reciprocal fuel economy, SI vs IEC byte definitions.
  - Currency calculations (`CurrencyConversionTest`): Multi-currency cross-rate formulas, zero handling, arbitrary precision.
  - Finance calculations (`FinanceCalculatorsTest`): Loan amortization monthly schedule, compound interest with contributions, discounts, sales tax inclusive/exclusive, margins vs markups.
  - Dates and Health (`DateAndHealthCalculatorsTest`): Calendar age calculation, leap-year boundary conditions, BMI category classification, Mifflin-St Jeor TDEE calorie requirements.
- **Test Execution Result**: `100% Passed (40/40 tests)`.
- **Build Status**: `assembleDebug` successfully compiled and packaged into `app-debug.apk` (18.6 MB).

---

## 8. Build & Execution Instructions

### Prerequisites
- JDK 17 or JDK 21 (e.g., Android Studio bundled JBR at `C:\Program Files\Android\Android Studio\jbr`)
- Android SDK 34 with Build-Tools 34.0.0

### Run Tests
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest
```

### Build Debug APK
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```
The compiled package will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

### Build Production Release AAB / APK
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat bundleRelease
```
*(Requires signing keys configured in `app/build.gradle.kts`)*

---

## 9. Security & Privacy Disclaimers

1. **No External Telemetry or Tracking**: OmniCalc does not transmit user calculations, search queries, or entered numbers to any external server.
2. **Medical Disclaimers**: Health tools (BMI, BMR/TDEE, Ovulation) are informational mathematical estimates. The Ovulation tool explicitly presents calendar-based estimates and must not be used as clinical contraception or pregnancy diagnostic.
3. **Financial Disclaimers**: Loan amortization, savings forecasts, and investment calculations are mathematical models and do not constitute financial advice.
4. **Currency Notice**: Exchange rates are market reference rates. Institutional or bureau de change rates may differ due to spreads and transaction fees.

package com.omnicalc.platform.core.registry

enum class ToolCategory(val displayName: String) {
    BASIC("Basic & Math"),
    SCIENTIFIC("Scientific"),
    CURRENCY("Currency"),
    CONVERSIONS("Unit Conversions"),
    FINANCE("Finance & Banking"),
    SHOPPING("Shopping & Sales"),
    DATES_TIME("Dates & Time"),
    HEALTH("Health & Fitness"),
    PROGRAMMER("Programmer"),
    GEOMETRY("Geometry"),
    STATISTICS("Statistics & Probability"),
    FUEL("Fuel & Automotive"),
    EDUCATION("Education & GPA"),
    CONSTANTS("Physical Constants")
}

data class ToolDefinition(
    val id: String,
    val title: String,
    val category: ToolCategory,
    val shortDescription: String,
    val searchKeywords: List<String>,
    val route: String,
    val isShortcutDefault: Boolean = false
)

object ToolRegistry {
    val ALL_TOOLS: List<ToolDefinition> = listOf(
        // Basic & Scientific
        ToolDefinition(
            id = "basic_calc",
            title = "Basic Calculator",
            category = ToolCategory.BASIC,
            shortDescription = "Instant arithmetic, memory, parentheses & percentages",
            searchKeywords = listOf("calc", "arithmetic", "add", "subtract", "multiply", "divide", "percentage", "math"),
            route = "calculator/basic",
            isShortcutDefault = true
        ),
        ToolDefinition(
            id = "scientific_calc",
            title = "Scientific Calculator",
            category = ToolCategory.SCIENTIFIC,
            shortDescription = "Trigonometry, logarithms, powers, factorials & roots",
            searchKeywords = listOf("sin", "cos", "tan", "log", "ln", "sqrt", "power", "exponent", "pi", "factorial", "scientific", "rad", "deg"),
            route = "calculator/scientific",
            isShortcutDefault = true
        ),
        ToolDefinition(
            id = "constants_browser",
            title = "Scientific Constants",
            category = ToolCategory.CONSTANTS,
            shortDescription = "CODATA physical, chemical & universal constants",
            searchKeywords = listOf("constant", "planck", "speed of light", "gravitational", "boltzmann", "avogadro", "pi", "physics"),
            route = "constants"
        ),

        // Currency & Units
        ToolDefinition(
            id = "currency_converter",
            title = "Currency Converter",
            category = ToolCategory.CURRENCY,
            shortDescription = "Multi-currency live board (KES, USD, EUR, KMF, TZS, GBP...)",
            searchKeywords = listOf("currency", "exchange", "forex", "money", "usd", "kes", "eur", "kmf", "tzs", "gbp", "ugx", "rwf", "shilling", "dollar", "franc", "rate", "convert"),
            route = "currency",
            isShortcutDefault = true
        ),
        ToolDefinition(
            id = "unit_converter",
            title = "Unit Converter",
            category = ToolCategory.CONVERSIONS,
            shortDescription = "Length, mass, area, volume, temperature, pressure, data & more",
            searchKeywords = listOf("units", "convert", "length", "weight", "kg", "lb", "km", "miles", "celsius", "fahrenheit", "meter", "liter", "gallon", "mb", "gb", "storage"),
            route = "units",
            isShortcutDefault = true
        ),

        // Shopping & Finance
        ToolDefinition(
            id = "discount_calc",
            title = "Discount Calculator",
            category = ToolCategory.SHOPPING,
            shortDescription = "Calculate savings, final price & reverse discounts",
            searchKeywords = listOf("discount", "sale", "percentage off", "savings", "reduction", "clearance", "price"),
            route = "finance/discount",
            isShortcutDefault = true
        ),
        ToolDefinition(
            id = "discount_tax_calc",
            title = "Discount + Tax Calculator",
            category = ToolCategory.SHOPPING,
            shortDescription = "Discount and tax combined (pre-tax or post-tax discount)",
            searchKeywords = listOf("discount tax", "sale tax", "price with tax", "store discount"),
            route = "finance/discount_tax"
        ),
        ToolDefinition(
            id = "tax_vat_calc",
            title = "Sales Tax & VAT Calculator",
            category = ToolCategory.FINANCE,
            shortDescription = "Tax inclusive & exclusive calculations with custom rates",
            searchKeywords = listOf("tax", "vat", "gst", "sales tax", "tax inclusive", "tax exclusive", "duty"),
            route = "finance/tax_vat",
            isShortcutDefault = true
        ),
        ToolDefinition(
            id = "loan_calc",
            title = "Loan & Amortization",
            category = ToolCategory.FINANCE,
            shortDescription = "Monthly repayment, total interest & complete amortization table",
            searchKeywords = listOf("loan", "mortgage", "emi", "borrow", "interest", "amortization", "schedule", "repayment", "bank"),
            route = "finance/loan",
            isShortcutDefault = true
        ),
        ToolDefinition(
            id = "savings_calc",
            title = "Savings & Compound Interest",
            category = ToolCategory.FINANCE,
            shortDescription = "Compound interest, recurring deposits & investment growth",
            searchKeywords = listOf("savings", "interest", "compound", "investment", "deposit", "future value", "growth", "apy"),
            route = "finance/savings"
        ),
        ToolDefinition(
            id = "profit_margin_calc",
            title = "Profit & Margin Calculator",
            category = ToolCategory.FINANCE,
            shortDescription = "Cost, selling price, gross profit, margin % and markup %",
            searchKeywords = listOf("profit", "margin", "markup", "cost", "selling price", "revenue", "business"),
            route = "finance/profit_margin"
        ),
        ToolDefinition(
            id = "breakeven_calc",
            title = "Break-Even Calculator",
            category = ToolCategory.FINANCE,
            shortDescription = "Calculate break-even units and revenue from fixed & variable costs",
            searchKeywords = listOf("breakeven", "break-even", "fixed cost", "variable cost", "contribution margin"),
            route = "finance/breakeven"
        ),
        ToolDefinition(
            id = "tip_calc",
            title = "Tip & Bill Splitter",
            category = ToolCategory.SHOPPING,
            shortDescription = "Bill amount, tip percentage & per-person split",
            searchKeywords = listOf("tip", "bill", "split", "restaurant", "gratuity", "per person", "dining"),
            route = "finance/tip"
        ),
        ToolDefinition(
            id = "unit_price_calc",
            title = "Unit Price Comparator",
            category = ToolCategory.SHOPPING,
            shortDescription = "Compare value across package sizes (e.g. price per kg/liter)",
            searchKeywords = listOf("unit price", "compare", "grocery", "cheapest", "cost per kg", "value"),
            route = "finance/unit_price"
        ),

        // Dates & Time
        ToolDefinition(
            id = "age_calc",
            title = "Age Calculator",
            category = ToolCategory.DATES_TIME,
            shortDescription = "Exact years, months, days & next birthday countdown",
            searchKeywords = listOf("age", "birthday", "birth", "years old", "days lived", "next birthday", "dob"),
            route = "dates/age",
            isShortcutDefault = true
        ),
        ToolDefinition(
            id = "date_diff_calc",
            title = "Date Difference & Math",
            category = ToolCategory.DATES_TIME,
            shortDescription = "Calendar difference between dates, add or subtract days/months",
            searchKeywords = listOf("date", "calendar", "difference", "days between", "add days", "subtract days", "leap year"),
            route = "dates/difference"
        ),
        ToolDefinition(
            id = "time_duration_calc",
            title = "Time Duration Calculator",
            category = ToolCategory.DATES_TIME,
            shortDescription = "Hours, minutes, seconds difference & overnight intervals",
            searchKeywords = listOf("time", "duration", "hours", "minutes", "shift", "overnight", "elapsed"),
            route = "dates/time_duration"
        ),
        ToolDefinition(
            id = "timezone_converter",
            title = "Time Zone Converter",
            category = ToolCategory.DATES_TIME,
            shortDescription = "Convert time across world timezones with daylight savings",
            searchKeywords = listOf("timezone", "time zone", "world clock", "gmt", "utc", "eat", "nairobi", "london", "new york"),
            route = "dates/timezone"
        ),

        // Health & Fitness
        ToolDefinition(
            id = "bmi_calc",
            title = "BMI Calculator",
            category = ToolCategory.HEALTH,
            shortDescription = "Body Mass Index screening (metric & imperial) with WHO categories",
            searchKeywords = listOf("bmi", "body mass", "weight", "height", "obese", "underweight", "healthy weight"),
            route = "health/bmi",
            isShortcutDefault = true
        ),
        ToolDefinition(
            id = "bmr_tdee_calc",
            title = "BMR & TDEE Calculator",
            category = ToolCategory.HEALTH,
            shortDescription = "Basal Metabolic Rate & Total Daily Energy Expenditure estimate",
            searchKeywords = listOf("bmr", "tdee", "calories", "metabolism", "energy expenditure", "maintenance calories"),
            route = "health/bmr_tdee"
        ),
        ToolDefinition(
            id = "ovulation_calc",
            title = "Ovulation & Fertility Estimator",
            category = ToolCategory.HEALTH,
            shortDescription = "Calendar-based estimated fertile window & next cycle date",
            searchKeywords = listOf("ovulation", "fertility", "period", "menstrual", "cycle", "fertile window"),
            route = "health/ovulation"
        ),

        // Automotive & Fuel
        ToolDefinition(
            id = "fuel_cost_calc",
            title = "Fuel Cost Calculator",
            category = ToolCategory.FUEL,
            shortDescription = "Trip fuel cost, fuel required & cost per km/mile",
            searchKeywords = listOf("fuel", "petrol", "diesel", "gas", "trip cost", "mileage", "consumption", "travel"),
            route = "fuel/cost",
            isShortcutDefault = true
        ),
        ToolDefinition(
            id = "fuel_economy_calc",
            title = "Fuel Economy Converter",
            category = ToolCategory.FUEL,
            shortDescription = "L/100 km, km/L, MPG (US) & MPG (Imperial) conversions",
            searchKeywords = listOf("mpg", "l/100km", "km/l", "fuel economy", "efficiency", "consumption"),
            route = "fuel/economy"
        ),

        // Programmer
        ToolDefinition(
            id = "programmer_calc",
            title = "Programmer Calculator",
            category = ToolCategory.PROGRAMMER,
            shortDescription = "HEX, DEC, OCT, BIN conversions, bitwise logic & word sizes",
            searchKeywords = listOf("hex", "binary", "octal", "decimal", "bitwise", "and", "or", "xor", "not", "shift", "programmer", "byte", "dword", "qword", "bits"),
            route = "programmer",
            isShortcutDefault = true
        ),

        // Geometry & Trigonometry
        ToolDefinition(
            id = "geometry_calc",
            title = "Geometry 2D & 3D",
            category = ToolCategory.GEOMETRY,
            shortDescription = "Area, perimeter, surface area & volume of geometric shapes",
            searchKeywords = listOf("geometry", "area", "perimeter", "volume", "surface area", "circle", "rectangle", "triangle", "cylinder", "sphere", "cube", "cone"),
            route = "geometry"
        ),
        ToolDefinition(
            id = "triangle_solver",
            title = "Triangle Solver",
            category = ToolCategory.GEOMETRY,
            shortDescription = "Solve any triangle (SSS, SAS, ASA, AAS, right-angled)",
            searchKeywords = listOf("triangle", "pythagoras", "hypotenuse", "sss", "sas", "asa", "angles", "sides"),
            route = "geometry/triangle"
        ),

        // Statistics & Probability
        ToolDefinition(
            id = "statistics_calc",
            title = "Statistics Calculator",
            category = ToolCategory.STATISTICS,
            shortDescription = "Mean, median, mode, range, sample & population standard deviation",
            searchKeywords = listOf("statistics", "mean", "median", "mode", "standard deviation", "variance", "average", "sum", "stats"),
            route = "statistics"
        ),
        ToolDefinition(
            id = "probability_calc",
            title = "Combinations & Permutations",
            category = ToolCategory.STATISTICS,
            shortDescription = "nCr (combinations), nPr (permutations), factorials & probability",
            searchKeywords = listOf("ncr", "npr", "combinations", "permutations", "probability", "factorial", "chance"),
            route = "statistics/probability"
        ),

        // Education
        ToolDefinition(
            id = "grade_calc",
            title = "Grade Calculator",
            category = ToolCategory.EDUCATION,
            shortDescription = "Points earned vs total possible, percentage & letter grade",
            searchKeywords = listOf("grade", "exam", "score", "test", "percentage", "letter grade", "school", "marks"),
            route = "education/grade"
        ),
        ToolDefinition(
            id = "gpa_calc",
            title = "GPA Calculator",
            category = ToolCategory.EDUCATION,
            shortDescription = "Weighted & unweighted Grade Point Average across courses",
            searchKeywords = listOf("gpa", "college", "university", "credits", "courses", "grade point average", "weighted gpa"),
            route = "education/gpa"
        )
    )

    fun find(id: String): ToolDefinition? =
        ALL_TOOLS.find { it.id.equals(id, ignoreCase = true) }

    fun getDefaultShortcuts(): List<ToolDefinition> =
        ALL_TOOLS.filter { it.isShortcutDefault }

    fun search(query: String): List<ToolDefinition> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return ALL_TOOLS

        return ALL_TOOLS
            .map { tool ->
                var score = 0
                val titleLower = tool.title.lowercase()
                val descLower = tool.shortDescription.lowercase()
                val catLower = tool.category.displayName.lowercase()

                if (titleLower == q) score += 100
                else if (titleLower.startsWith(q)) score += 60
                else if (titleLower.contains(q)) score += 40

                tool.searchKeywords.forEach { kw ->
                    val kwLower = kw.lowercase()
                    if (kwLower == q) score += 80
                    else if (kwLower.startsWith(q)) score += 35
                    else if (kwLower.contains(q)) score += 20
                }

                if (descLower.contains(q)) score += 15
                if (catLower.contains(q)) score += 10

                tool to score
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }
}

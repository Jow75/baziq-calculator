package com.omnicalc.platform.core.units

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.Locale

object UnitConverterEngine {
    private val MC = MathContext(20, RoundingMode.HALF_EVEN)

    val ALL_UNITS: List<UnitDefinition> = listOf(
        // LENGTH (Base: Meter)
        UnitDefinition("mm", "Millimeter", "mm", Dimension.LENGTH, factorToBase = BigDecimal("0.001")),
        UnitDefinition("cm", "Centimeter", "cm", Dimension.LENGTH, factorToBase = BigDecimal("0.01")),
        UnitDefinition("m", "Meter", "m", Dimension.LENGTH, factorToBase = BigDecimal.ONE, isBase = true),
        UnitDefinition("km", "Kilometer", "km", Dimension.LENGTH, factorToBase = BigDecimal("1000")),
        UnitDefinition("in", "Inch", "in", Dimension.LENGTH, factorToBase = BigDecimal("0.0254"), system = MeasurementSystem.US_CUSTOMARY),
        UnitDefinition("ft", "Foot", "ft", Dimension.LENGTH, factorToBase = BigDecimal("0.3048"), system = MeasurementSystem.US_CUSTOMARY),
        UnitDefinition("yd", "Yard", "yd", Dimension.LENGTH, factorToBase = BigDecimal("0.9144"), system = MeasurementSystem.US_CUSTOMARY),
        UnitDefinition("mi", "Mile", "mi", Dimension.LENGTH, factorToBase = BigDecimal("1609.344"), system = MeasurementSystem.US_CUSTOMARY),
        UnitDefinition("nmi", "Nautical Mile", "nmi", Dimension.LENGTH, factorToBase = BigDecimal("1852")),
        UnitDefinition("um", "Micrometer", "µm", Dimension.LENGTH, factorToBase = BigDecimal("0.000001")),
        UnitDefinition("nm", "Nanometer", "nm", Dimension.LENGTH, factorToBase = BigDecimal("0.000000001")),

        // MASS & WEIGHT (Base: Kilogram)
        UnitDefinition("ug", "Microgram", "µg", Dimension.MASS, factorToBase = BigDecimal("0.000000001")),
        UnitDefinition("mg", "Milligram", "mg", Dimension.MASS, factorToBase = BigDecimal("0.000001")),
        UnitDefinition("g", "Gram", "g", Dimension.MASS, factorToBase = BigDecimal("0.001")),
        UnitDefinition("kg", "Kilogram", "kg", Dimension.MASS, factorToBase = BigDecimal.ONE, isBase = true),
        UnitDefinition("t", "Metric Ton", "t", Dimension.MASS, factorToBase = BigDecimal("1000")),
        UnitDefinition("oz", "Ounce (avdp)", "oz", Dimension.MASS, factorToBase = BigDecimal("0.028349523125"), system = MeasurementSystem.US_CUSTOMARY),
        UnitDefinition("lb", "Pound (avdp)", "lb", Dimension.MASS, factorToBase = BigDecimal("0.45359237"), system = MeasurementSystem.US_CUSTOMARY),
        UnitDefinition("st", "Stone", "st", Dimension.MASS, factorToBase = BigDecimal("6.35029318"), system = MeasurementSystem.IMPERIAL),
        UnitDefinition("ton_us", "Short Ton (US)", "ton (US)", Dimension.MASS, factorToBase = BigDecimal("907.18474"), system = MeasurementSystem.US_CUSTOMARY),
        UnitDefinition("ton_imp", "Long Ton (Imperial)", "ton (Imp)", Dimension.MASS, factorToBase = BigDecimal("1016.0469088"), system = MeasurementSystem.IMPERIAL),

        // AREA (Base: Square Meter)
        UnitDefinition("sq_mm", "Square Millimeter", "mm²", Dimension.AREA, factorToBase = BigDecimal("0.000001")),
        UnitDefinition("sq_cm", "Square Centimeter", "cm²", Dimension.AREA, factorToBase = BigDecimal("0.0001")),
        UnitDefinition("sq_m", "Square Meter", "m²", Dimension.AREA, factorToBase = BigDecimal.ONE, isBase = true),
        UnitDefinition("ha", "Hectare", "ha", Dimension.AREA, factorToBase = BigDecimal("10000")),
        UnitDefinition("sq_km", "Square Kilometer", "km²", Dimension.AREA, factorToBase = BigDecimal("1000000")),
        UnitDefinition("sq_in", "Square Inch", "sq in", Dimension.AREA, factorToBase = BigDecimal("0.00064516"), system = MeasurementSystem.US_CUSTOMARY),
        UnitDefinition("sq_ft", "Square Foot", "sq ft", Dimension.AREA, factorToBase = BigDecimal("0.09290304"), system = MeasurementSystem.US_CUSTOMARY),
        UnitDefinition("sq_yd", "Square Yard", "sq yd", Dimension.AREA, factorToBase = BigDecimal("0.83612736"), system = MeasurementSystem.US_CUSTOMARY),
        UnitDefinition("acre", "Acre", "ac", Dimension.AREA, factorToBase = BigDecimal("4046.8564224"), system = MeasurementSystem.US_CUSTOMARY),
        UnitDefinition("sq_mi", "Square Mile", "sq mi", Dimension.AREA, factorToBase = BigDecimal("2589988.110336"), system = MeasurementSystem.US_CUSTOMARY),

        // VOLUME (Base: Liter)
        UnitDefinition("ml", "Milliliter", "mL", Dimension.VOLUME, factorToBase = BigDecimal("0.001")),
        UnitDefinition("l", "Liter", "L", Dimension.VOLUME, factorToBase = BigDecimal.ONE, isBase = true),
        UnitDefinition("cu_m", "Cubic Meter", "m³", Dimension.VOLUME, factorToBase = BigDecimal("1000")),
        UnitDefinition("cu_in", "Cubic Inch", "cu in", Dimension.VOLUME, factorToBase = BigDecimal("0.016387064"), system = MeasurementSystem.US_CUSTOMARY),
        UnitDefinition("cu_ft", "Cubic Foot", "cu ft", Dimension.VOLUME, factorToBase = BigDecimal("28.316846592"), system = MeasurementSystem.US_CUSTOMARY),
        UnitDefinition("gal_us", "Gallon (US Liquid)", "gal (US)", Dimension.VOLUME, factorToBase = BigDecimal("3.785411784"), system = MeasurementSystem.US_CUSTOMARY),
        UnitDefinition("gal_imp", "Gallon (Imperial)", "gal (Imp)", Dimension.VOLUME, factorToBase = BigDecimal("4.54609"), system = MeasurementSystem.IMPERIAL),
        UnitDefinition("qt_us", "Quart (US)", "qt (US)", Dimension.VOLUME, factorToBase = BigDecimal("0.946352946"), system = MeasurementSystem.US_CUSTOMARY),
        UnitDefinition("qt_imp", "Quart (Imperial)", "qt (Imp)", Dimension.VOLUME, factorToBase = BigDecimal("1.1365225"), system = MeasurementSystem.IMPERIAL),
        UnitDefinition("pt_us", "Pint (US)", "pt (US)", Dimension.VOLUME, factorToBase = BigDecimal("0.473176473"), system = MeasurementSystem.US_CUSTOMARY),
        UnitDefinition("pt_imp", "Pint (Imperial)", "pt (Imp)", Dimension.VOLUME, factorToBase = BigDecimal("0.56826125"), system = MeasurementSystem.IMPERIAL),
        UnitDefinition("cup_us", "Cup (US Customary)", "cup (US)", Dimension.VOLUME, factorToBase = BigDecimal("0.2365882365"), system = MeasurementSystem.US_CUSTOMARY),
        UnitDefinition("cup_metric", "Cup (Metric)", "cup (metric)", Dimension.VOLUME, factorToBase = BigDecimal("0.25")),
        UnitDefinition("tbsp_us", "Tablespoon (US)", "tbsp", Dimension.VOLUME, factorToBase = BigDecimal("0.01478676478125"), system = MeasurementSystem.US_CUSTOMARY),
        UnitDefinition("tsp_us", "Teaspoon (US)", "tsp", Dimension.VOLUME, factorToBase = BigDecimal("0.00492892159375"), system = MeasurementSystem.US_CUSTOMARY),

        // TEMPERATURE (Base: Kelvin)
        UnitDefinition("k", "Kelvin", "K", Dimension.TEMPERATURE, conversionType = ConversionType.AFFINE, factorToBase = BigDecimal.ONE, offset = BigDecimal.ZERO, isBase = true),
        UnitDefinition("c", "Celsius", "°C", Dimension.TEMPERATURE, conversionType = ConversionType.AFFINE, factorToBase = BigDecimal.ONE, offset = BigDecimal("273.15")),
        UnitDefinition("f", "Fahrenheit", "°F", Dimension.TEMPERATURE, conversionType = ConversionType.AFFINE, factorToBase = BigDecimal("0.55555555555555555556"), offset = BigDecimal("459.67")),

        // TIME (Base: Second)
        UnitDefinition("ns", "Nanosecond", "ns", Dimension.TIME, factorToBase = BigDecimal("0.000000001")),
        UnitDefinition("us", "Microsecond", "µs", Dimension.TIME, factorToBase = BigDecimal("0.000001")),
        UnitDefinition("ms", "Millisecond", "ms", Dimension.TIME, factorToBase = BigDecimal("0.001")),
        UnitDefinition("s", "Second", "s", Dimension.TIME, factorToBase = BigDecimal.ONE, isBase = true),
        UnitDefinition("min", "Minute", "min", Dimension.TIME, factorToBase = BigDecimal("60")),
        UnitDefinition("h", "Hour", "h", Dimension.TIME, factorToBase = BigDecimal("3600")),
        UnitDefinition("d", "Day", "d", Dimension.TIME, factorToBase = BigDecimal("86400")),
        UnitDefinition("wk", "Week", "wk", Dimension.TIME, factorToBase = BigDecimal("604800")),
        UnitDefinition("yr", "Year (Calendar 365.25d)", "yr", Dimension.TIME, factorToBase = BigDecimal("31557600")),

        // SPEED (Base: m/s)
        UnitDefinition("mps", "Meters per second", "m/s", Dimension.SPEED, factorToBase = BigDecimal.ONE, isBase = true),
        UnitDefinition("kph", "Kilometers per hour", "km/h", Dimension.SPEED, factorToBase = BigDecimal("0.27777777777777777778")),
        UnitDefinition("mph", "Miles per hour", "mph", Dimension.SPEED, factorToBase = BigDecimal("0.44704"), system = MeasurementSystem.US_CUSTOMARY),
        UnitDefinition("knot", "Knot", "kn", Dimension.SPEED, factorToBase = BigDecimal("0.51444444444444444444")),
        UnitDefinition("fps", "Feet per second", "ft/s", Dimension.SPEED, factorToBase = BigDecimal("0.3048"), system = MeasurementSystem.US_CUSTOMARY),

        // PRESSURE (Base: Pascal)
        UnitDefinition("pa", "Pascal", "Pa", Dimension.PRESSURE, factorToBase = BigDecimal.ONE, isBase = true),
        UnitDefinition("kpa", "Kilopascal", "kPa", Dimension.PRESSURE, factorToBase = BigDecimal("1000")),
        UnitDefinition("mpa", "Megapascal", "MPa", Dimension.PRESSURE, factorToBase = BigDecimal("1000000")),
        UnitDefinition("bar", "Bar", "bar", Dimension.PRESSURE, factorToBase = BigDecimal("100000")),
        UnitDefinition("mbar", "Millibar", "mbar", Dimension.PRESSURE, factorToBase = BigDecimal("100")),
        UnitDefinition("psi", "Pound per sq inch", "psi", Dimension.PRESSURE, factorToBase = BigDecimal("6894.757293168"), system = MeasurementSystem.US_CUSTOMARY),
        UnitDefinition("atm", "Standard Atmosphere", "atm", Dimension.PRESSURE, factorToBase = BigDecimal("101325")),
        UnitDefinition("torr", "Torr / mmHg", "Torr", Dimension.PRESSURE, factorToBase = BigDecimal("133.322368421")),

        // ENERGY (Base: Joule)
        UnitDefinition("j", "Joule", "J", Dimension.ENERGY, factorToBase = BigDecimal.ONE, isBase = true),
        UnitDefinition("kj", "Kilojoule", "kJ", Dimension.ENERGY, factorToBase = BigDecimal("1000")),
        UnitDefinition("cal", "Calorie (thermochem)", "cal", Dimension.ENERGY, factorToBase = BigDecimal("4.184")),
        UnitDefinition("kcal", "Kilocalorie (Food cal)", "kcal", Dimension.ENERGY, factorToBase = BigDecimal("4184")),
        UnitDefinition("wh", "Watt-hour", "Wh", Dimension.ENERGY, factorToBase = BigDecimal("3600")),
        UnitDefinition("kwh", "Kilowatt-hour", "kWh", Dimension.ENERGY, factorToBase = BigDecimal("3600000")),
        UnitDefinition("btu", "BTU (IT)", "BTU", Dimension.ENERGY, factorToBase = BigDecimal("1055.05585262"), system = MeasurementSystem.IMPERIAL),
        UnitDefinition("ev", "Electronvolt", "eV", Dimension.ENERGY, factorToBase = BigDecimal("1.602176634E-19")),

        // POWER (Base: Watt)
        UnitDefinition("w", "Watt", "W", Dimension.POWER, factorToBase = BigDecimal.ONE, isBase = true),
        UnitDefinition("kw", "Kilowatt", "kW", Dimension.POWER, factorToBase = BigDecimal("1000")),
        UnitDefinition("mw", "Megawatt", "MW", Dimension.POWER, factorToBase = BigDecimal("1000000")),
        UnitDefinition("hp_mech", "Horsepower (Mechanical)", "hp", Dimension.POWER, factorToBase = BigDecimal("745.699871582"), system = MeasurementSystem.IMPERIAL),
        UnitDefinition("hp_metric", "Horsepower (Metric PS)", "PS", Dimension.POWER, factorToBase = BigDecimal("735.49875")),

        // FORCE (Base: Newton)
        UnitDefinition("n", "Newton", "N", Dimension.FORCE, factorToBase = BigDecimal.ONE, isBase = true),
        UnitDefinition("kn", "Kilonewton", "kN", Dimension.FORCE, factorToBase = BigDecimal("1000")),
        UnitDefinition("dyn", "Dyne", "dyn", Dimension.FORCE, factorToBase = BigDecimal("0.00001")),
        UnitDefinition("lbf", "Pound-force", "lbf", Dimension.FORCE, factorToBase = BigDecimal("4.44822161526"), system = MeasurementSystem.US_CUSTOMARY),

        // TORQUE (Base: Newton-meter)
        UnitDefinition("nm_torque", "Newton-meter", "N·m", Dimension.TORQUE, factorToBase = BigDecimal.ONE, isBase = true),
        UnitDefinition("lbf_ft", "Pound-foot", "lbf·ft", Dimension.TORQUE, factorToBase = BigDecimal("1.35581794833"), system = MeasurementSystem.US_CUSTOMARY),
        UnitDefinition("lbf_in", "Pound-inch", "lbf·in", Dimension.TORQUE, factorToBase = BigDecimal("0.112984829028"), system = MeasurementSystem.US_CUSTOMARY),

        // DATA STORAGE (Base: Byte)
        UnitDefinition("bit", "Bit", "b", Dimension.DATA, factorToBase = BigDecimal("0.125")),
        UnitDefinition("byte", "Byte", "B", Dimension.DATA, factorToBase = BigDecimal.ONE, isBase = true),
        UnitDefinition("kb", "Kilobyte (SI decimal)", "KB", Dimension.DATA, factorToBase = BigDecimal("1000"), system = MeasurementSystem.SI_DECIMAL),
        UnitDefinition("mb", "Megabyte (SI decimal)", "MB", Dimension.DATA, factorToBase = BigDecimal("1000000"), system = MeasurementSystem.SI_DECIMAL),
        UnitDefinition("gb", "Gigabyte (SI decimal)", "GB", Dimension.DATA, factorToBase = BigDecimal("1000000000"), system = MeasurementSystem.SI_DECIMAL),
        UnitDefinition("tb", "Terabyte (SI decimal)", "TB", Dimension.DATA, factorToBase = BigDecimal("1000000000000"), system = MeasurementSystem.SI_DECIMAL),
        UnitDefinition("kib", "Kibibyte (IEC binary)", "KiB", Dimension.DATA, factorToBase = BigDecimal("1024"), system = MeasurementSystem.IEC_BINARY),
        UnitDefinition("mib", "Mebibyte (IEC binary)", "MiB", Dimension.DATA, factorToBase = BigDecimal("1048576"), system = MeasurementSystem.IEC_BINARY),
        UnitDefinition("gib", "Gibibyte (IEC binary)", "GiB", Dimension.DATA, factorToBase = BigDecimal("1073741824"), system = MeasurementSystem.IEC_BINARY),
        UnitDefinition("tib", "Tebibyte (IEC binary)", "TiB", Dimension.DATA, factorToBase = BigDecimal("1099511627776"), system = MeasurementSystem.IEC_BINARY),

        // FREQUENCY (Base: Hertz)
        UnitDefinition("hz", "Hertz", "Hz", Dimension.FREQUENCY, factorToBase = BigDecimal.ONE, isBase = true),
        UnitDefinition("khz", "Kilohertz", "kHz", Dimension.FREQUENCY, factorToBase = BigDecimal("1000")),
        UnitDefinition("mhz", "Megahertz", "MHz", Dimension.FREQUENCY, factorToBase = BigDecimal("1000000")),
        UnitDefinition("ghz", "Gigahertz", "GHz", Dimension.FREQUENCY, factorToBase = BigDecimal("1000000000")),

        // ANGLE (Base: Degree)
        UnitDefinition("deg", "Degree", "°", Dimension.ANGLE, factorToBase = BigDecimal.ONE, isBase = true),
        UnitDefinition("rad", "Radian", "rad", Dimension.ANGLE, factorToBase = BigDecimal("57.2957795130823208768")),
        UnitDefinition("grad", "Gradian", "grad", Dimension.ANGLE, factorToBase = BigDecimal("0.9")),
        UnitDefinition("arcmin", "Arcminute", "arcmin", Dimension.ANGLE, factorToBase = BigDecimal("0.01666666666666666667")),
        UnitDefinition("arcsec", "Arcsecond", "arcsec", Dimension.ANGLE, factorToBase = BigDecimal("0.00027777777777777778")),

        // FUEL ECONOMY (Base: km/L)
        UnitDefinition("km_l", "Kilometers per liter", "km/L", Dimension.FUEL_ECONOMY, conversionType = ConversionType.MULTIPLICATIVE, factorToBase = BigDecimal.ONE, isBase = true),
        UnitDefinition("l_100km", "Liters per 100 km", "L/100km", Dimension.FUEL_ECONOMY, conversionType = ConversionType.RECIPROCAL, factorToBase = BigDecimal("100")),
        UnitDefinition("mpg_us", "Miles per gallon (US)", "MPG (US)", Dimension.FUEL_ECONOMY, conversionType = ConversionType.MULTIPLICATIVE, factorToBase = BigDecimal("0.425143707"), system = MeasurementSystem.US_CUSTOMARY),
        UnitDefinition("mpg_imp", "Miles per gallon (Imperial)", "MPG (Imp)", Dimension.FUEL_ECONOMY, conversionType = ConversionType.MULTIPLICATIVE, factorToBase = BigDecimal("0.354006189"), system = MeasurementSystem.IMPERIAL)
    )

    fun getUnitsForDimension(dimension: Dimension): List<UnitDefinition> =
        ALL_UNITS.filter { it.dimension == dimension }

    fun findUnit(id: String): UnitDefinition? =
        ALL_UNITS.find { it.id.equals(id, ignoreCase = true) }

    fun convert(value: BigDecimal, from: UnitDefinition, to: UnitDefinition): BigDecimal {
        if (from.id == to.id) return value
        require(from.dimension == to.dimension) {
            "Cannot convert across different dimensions: ${from.dimension} to ${to.dimension}"
        }

        // Special handling for Fuel Economy (Reciprocal vs Multiplicative)
        if (from.dimension == Dimension.FUEL_ECONOMY) {
            return convertFuelEconomy(value, from, to)
        }

        // Temperature (Affine)
        if (from.dimension == Dimension.TEMPERATURE) {
            return convertTemperature(value, from, to)
        }

        // Standard Multiplicative Conversion
        // canonical = value * from.factorToBase
        // target = canonical / to.factorToBase
        val canonical = value.multiply(from.factorToBase, MC)
        return canonical.divide(to.factorToBase, MC)
    }

    private fun convertTemperature(value: BigDecimal, from: UnitDefinition, to: UnitDefinition): BigDecimal {
        // Convert to Kelvin: K = (value + offset) * factorToBase
        val kelvin = when (from.id.lowercase(Locale.ROOT)) {
            "k" -> value
            "c" -> value.add(BigDecimal("273.15"), MC)
            "f" -> value.add(BigDecimal("459.67"), MC).multiply(BigDecimal("5"), MC).divide(BigDecimal("9"), MC)
            else -> throw IllegalArgumentException("Unsupported temperature unit: ${from.id}")
        }

        // Convert Kelvin to Target
        return when (to.id.lowercase(Locale.ROOT)) {
            "k" -> kelvin
            "c" -> kelvin.subtract(BigDecimal("273.15"), MC)
            "f" -> kelvin.multiply(BigDecimal("9"), MC).divide(BigDecimal("5"), MC).subtract(BigDecimal("459.67"), MC)
            else -> throw IllegalArgumentException("Unsupported temperature unit: ${to.id}")
        }
    }

    private fun convertFuelEconomy(value: BigDecimal, from: UnitDefinition, to: UnitDefinition): BigDecimal {
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            throw ArithmeticException("Fuel consumption value cannot be zero")
        }

        // 1. Convert source to canonical km/L
        val kmPerL: BigDecimal = when (from.id) {
            "km_l" -> value
            "l_100km" -> BigDecimal("100").divide(value, MC)
            "mpg_us" -> value.multiply(from.factorToBase, MC)
            "mpg_imp" -> value.multiply(from.factorToBase, MC)
            else -> value.multiply(from.factorToBase, MC)
        }

        // 2. Convert km/L to target
        return when (to.id) {
            "km_l" -> kmPerL
            "l_100km" -> BigDecimal("100").divide(kmPerL, MC)
            "mpg_us" -> kmPerL.divide(to.factorToBase, MC)
            "mpg_imp" -> kmPerL.divide(to.factorToBase, MC)
            else -> kmPerL.divide(to.factorToBase, MC)
        }
    }

    fun formatResult(value: BigDecimal, maxDecimals: Int = 6): String {
        if (value.compareTo(BigDecimal.ZERO) == 0) return "0"
        val absVal = value.abs()
        if (absVal < BigDecimal("0.000001") || absVal > BigDecimal("1000000000000")) {
            return value.round(MathContext(6, RoundingMode.HALF_EVEN)).stripTrailingZeros().toEngineeringString()
        }
        val rounded = value.setScale(maxDecimals, RoundingMode.HALF_EVEN).stripTrailingZeros()
        return rounded.toPlainString()
    }
}

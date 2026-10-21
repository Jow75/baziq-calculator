package com.omnicalc.platform.core.units

enum class Dimension(val displayName: String, val baseUnitSymbol: String) {
    LENGTH("Length", "m"),
    MASS("Mass & Weight", "kg"),
    AREA("Area", "m²"),
    VOLUME("Volume", "L"),
    TEMPERATURE("Temperature", "K"),
    TIME("Time", "s"),
    SPEED("Speed", "m/s"),
    PRESSURE("Pressure", "Pa"),
    ENERGY("Energy", "J"),
    POWER("Power", "W"),
    FORCE("Force", "N"),
    TORQUE("Torque", "N·m"),
    DATA("Data & Storage", "B"),
    FREQUENCY("Frequency", "Hz"),
    ANGLE("Angle", "deg"),
    FUEL_ECONOMY("Fuel Economy", "km/L")
}

enum class ConversionType {
    MULTIPLICATIVE,
    AFFINE,
    RECIPROCAL
}

enum class MeasurementSystem {
    METRIC,
    US_CUSTOMARY,
    IMPERIAL,
    IEC_BINARY,
    SI_DECIMAL,
    UNIVERSAL
}

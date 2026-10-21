package com.omnicalc.platform.core.units

import java.math.BigDecimal

data class UnitDefinition(
    val id: String,
    val name: String,
    val symbol: String,
    val dimension: Dimension,
    val conversionType: ConversionType = ConversionType.MULTIPLICATIVE,
    val factorToBase: BigDecimal = BigDecimal.ONE,
    val offset: BigDecimal = BigDecimal.ZERO,
    val system: MeasurementSystem = MeasurementSystem.METRIC,
    val isBase: Boolean = false,
    val description: String = ""
)

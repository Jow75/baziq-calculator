package com.omnicalc.platform.core.math

import java.math.BigDecimal

data class ScientificConstant(
    val id: String,
    val name: String,
    val symbol: String,
    val valueStr: String,
    val value: BigDecimal,
    val unit: String,
    val category: String,
    val citation: String,
    val description: String
)

object ScientificConstants {
    val ALL: List<ScientificConstant> = listOf(
        // Mathematical Constants
        ScientificConstant(
            id = "pi",
            name = "Archimedes' Constant (Pi)",
            symbol = "π",
            valueStr = "3.14159265358979323846",
            value = BigDecimal("3.14159265358979323846"),
            unit = "",
            category = "Mathematical",
            citation = "Universal",
            description = "Ratio of a circle's circumference to its diameter."
        ),
        ScientificConstant(
            id = "e",
            name = "Euler's Number",
            symbol = "e",
            valueStr = "2.71828182845904523536",
            value = BigDecimal("2.71828182845904523536"),
            unit = "",
            category = "Mathematical",
            citation = "Universal",
            description = "Base of the natural logarithm."
        ),
        ScientificConstant(
            id = "phi",
            name = "Golden Ratio",
            symbol = "φ",
            valueStr = "1.61803398874989484820",
            value = BigDecimal("1.61803398874989484820"),
            unit = "",
            category = "Mathematical",
            citation = "Universal",
            description = "Extreme and mean ratio in mathematics and geometry."
        ),
        ScientificConstant(
            id = "euler_mascheroni",
            name = "Euler–Mascheroni Constant",
            symbol = "γ",
            valueStr = "0.57721566490153286060",
            value = BigDecimal("0.57721566490153286060"),
            unit = "",
            category = "Mathematical",
            citation = "Universal",
            description = "Limiting difference between the harmonic series and natural logarithm."
        ),

        // Universal Physical Constants (CODATA 2018 / 2022)
        ScientificConstant(
            id = "c",
            name = "Speed of Light in Vacuum",
            symbol = "c",
            valueStr = "299792458",
            value = BigDecimal("299792458"),
            unit = "m/s",
            category = "Universal Physics",
            citation = "CODATA / SI exact (1983)",
            description = "Exact speed at which conventional light and electromagnetic radiation propagate in a vacuum."
        ),
        ScientificConstant(
            id = "h",
            name = "Planck Constant",
            symbol = "h",
            valueStr = "6.62607015E-34",
            value = BigDecimal("6.62607015E-34"),
            unit = "J·s",
            category = "Universal Physics",
            citation = "CODATA / SI exact (2019)",
            description = "Quantum of electromagnetic action relating photon energy to frequency."
        ),
        ScientificConstant(
            id = "hbar",
            name = "Reduced Planck Constant",
            symbol = "ħ",
            valueStr = "1.054571817E-34",
            value = BigDecimal("1.054571817E-34"),
            unit = "J·s",
            category = "Universal Physics",
            citation = "CODATA h / (2π)",
            description = "Planck constant divided by 2π (Dirac constant)."
        ),
        ScientificConstant(
            id = "G",
            name = "Newtonian Constant of Gravitation",
            symbol = "G",
            valueStr = "6.67430E-11",
            value = BigDecimal("6.67430E-11"),
            unit = "m³/(kg·s²)",
            category = "Universal Physics",
            citation = "CODATA 2018",
            description = "Empirical gravitational constant in Newton's law of universal gravitation."
        ),
        ScientificConstant(
            id = "g_0",
            name = "Standard Acceleration of Gravity",
            symbol = "g₀",
            valueStr = "9.80665",
            value = BigDecimal("9.80665"),
            unit = "m/s²",
            category = "Universal Physics",
            citation = "ISO 80000-3 standard",
            description = "Standard nominal gravitational acceleration of an object in a vacuum near the surface of the Earth."
        ),
        ScientificConstant(
            id = "e_charge",
            name = "Elementary Charge",
            symbol = "e",
            valueStr = "1.602176634E-19",
            value = BigDecimal("1.602176634E-19"),
            unit = "C",
            category = "Electromagnetism",
            citation = "CODATA / SI exact (2019)",
            description = "Electric charge carried by a single proton or electron magnitude."
        ),
        ScientificConstant(
            id = "k_B",
            name = "Boltzmann Constant",
            symbol = "k",
            valueStr = "1.380649E-23",
            value = BigDecimal("1.380649E-23"),
            unit = "J/K",
            category = "Thermodynamics",
            citation = "CODATA / SI exact (2019)",
            description = "Relates the average relative thermal kinetic energy of particles in a gas with temperature."
        ),
        ScientificConstant(
            id = "N_A",
            name = "Avogadro Constant",
            symbol = "N_A",
            valueStr = "6.02214076E+23",
            value = BigDecimal("6.02214076E+23"),
            unit = "mol⁻¹",
            category = "Chemistry & Atomic",
            citation = "CODATA / SI exact (2019)",
            description = "Number of constituent particles per mole of substance."
        ),
        ScientificConstant(
            id = "R",
            name = "Molar Gas Constant",
            symbol = "R",
            valueStr = "8.314462618",
            value = BigDecimal("8.314462618"),
            unit = "J/(mol·K)",
            category = "Thermodynamics",
            citation = "CODATA N_A * k (2019)",
            description = "Physical constant in the ideal gas equation (PV = nRT)."
        ),
        ScientificConstant(
            id = "sigma",
            name = "Stefan–Boltzmann Constant",
            symbol = "σ",
            valueStr = "5.670374419E-8",
            value = BigDecimal("5.670374419E-8"),
            unit = "W/(m²·K⁴)",
            category = "Thermodynamics",
            citation = "CODATA (2019)",
            description = "Constant of proportionality in the Stefan–Boltzmann law for blackbody radiation."
        ),
        ScientificConstant(
            id = "m_e",
            name = "Electron Rest Mass",
            symbol = "m_e",
            valueStr = "9.1093837015E-31",
            value = BigDecimal("9.1093837015E-31"),
            unit = "kg",
            category = "Atomic & Nuclear",
            citation = "CODATA 2018",
            description = "Stationary mass of an electron."
        ),
        ScientificConstant(
            id = "m_p",
            name = "Proton Rest Mass",
            symbol = "m_p",
            valueStr = "1.67262192369E-27",
            value = BigDecimal("1.67262192369E-27"),
            unit = "kg",
            category = "Atomic & Nuclear",
            citation = "CODATA 2018",
            description = "Stationary mass of a proton."
        ),
        ScientificConstant(
            id = "u",
            name = "Unified Atomic Mass Unit",
            symbol = "u",
            valueStr = "1.66053906660E-27",
            value = BigDecimal("1.66053906660E-27"),
            unit = "kg",
            category = "Atomic & Nuclear",
            citation = "CODATA 2018",
            description = "One twelfth of the mass of an unbound neutral atom of carbon-12."
        )
    )

    fun find(query: String): List<ScientificConstant> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return ALL
        return ALL.filter {
            it.name.lowercase().contains(q) ||
            it.symbol.lowercase().contains(q) ||
            it.category.lowercase().contains(q) ||
            it.description.lowercase().contains(q)
        }
    }
}

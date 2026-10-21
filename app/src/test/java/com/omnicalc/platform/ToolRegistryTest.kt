package com.omnicalc.platform

import com.omnicalc.platform.core.registry.ToolRegistry
import org.junit.Assert.*
import org.junit.Test

class ToolRegistryTest {

    @Test
    fun testAllToolsHaveValidRoutesAndMetadata() {
        assertFalse(ToolRegistry.ALL_TOOLS.isEmpty())
        for (tool in ToolRegistry.ALL_TOOLS) {
            assertTrue("Tool ${tool.id} has empty ID", tool.id.isNotBlank())
            assertTrue("Tool ${tool.id} has empty title", tool.title.isNotBlank())
            assertTrue("Tool ${tool.id} has empty route", tool.route.isNotBlank())
            assertFalse("Tool ${tool.id} has no keywords", tool.searchKeywords.isEmpty())
        }
    }

    @Test
    fun testSearchQueriesMatchExpectedTools() {
        val testQueries = mapOf(
            "kes" to "currency_converter",
            "usd" to "currency_converter",
            "vat" to "tax_vat_calc",
            "loan" to "loan_calc",
            "bmi" to "bmi_calc",
            "hex" to "programmer_calc",
            "km" to "unit_converter",
            "miles" to "unit_converter",
            "temperature" to "unit_converter",
            "fuel" to "fuel_cost_calc",
            "constants" to "constants_browser"
        )

        for ((query, expectedToolId) in testQueries) {
            val results = ToolRegistry.search(query)
            assertFalse("Query '$query' yielded 0 search results", results.isEmpty())
            val matched = results.any { it.id == expectedToolId }
            assertTrue("Query '$query' did not match expected tool '$expectedToolId'", matched)
        }
    }

    @Test
    fun testEmptySearchReturnsAllTools() {
        val results = ToolRegistry.search("")
        assertEquals(ToolRegistry.ALL_TOOLS.size, results.size)
    }

    @Test
    fun testDefaultShortcutsIncludeEssentialTools() {
        val shortcuts = ToolRegistry.getDefaultShortcuts()
        assertTrue("Shortcuts should not be empty", shortcuts.isNotEmpty())
        val shortcutIds = shortcuts.map { it.id }.toSet()
        assertTrue(shortcutIds.contains("basic_calc"))
        assertTrue(shortcutIds.contains("currency_converter"))
        assertTrue(shortcutIds.contains("unit_converter"))
        assertTrue(shortcutIds.contains("loan_calc"))
    }
}

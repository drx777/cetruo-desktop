package com.example.cardforge

import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfContactSheetExporterTest {
    private fun card(name: String, width: Double = 180.0, height: Double = 252.0) =
        PdfContactSheetExporter.CardSpec(Paths.get(name), width, height)

    @Test
    fun emptyInputProducesNoPages() {
        assertTrue(PdfContactSheetExporter.planA4(emptyList()).isEmpty())
    }

    @Test
    fun slotsStayInsidePageBounds() {
        val pages = PdfContactSheetExporter.planA4(List(12) { card("card-$it.png") })
        assertTrue(pages.isNotEmpty())

        pages.forEach { page ->
            page.slots.forEach { slot ->
                assertTrue(slot.x >= 0.0)
                assertTrue(slot.y >= 0.0)
                assertTrue(slot.x + slot.widthPt <= page.pageSize.width + 0.001)
                assertTrue(slot.y + slot.heightPt <= page.pageSize.height + 0.001)
            }
        }
    }

    @Test
    fun cardDimensionsAreScaledConsistently() {
        val pages = PdfContactSheetExporter.planA4(listOf(card("one.png")), scale = 0.5)
        val slot = pages.single().slots.single()
        assertEquals(90.0, slot.widthPt, 0.001)
        assertEquals(126.0, slot.heightPt, 0.001)
    }

    @Test
    fun differentlySizedCardsArePlannedSeparately() {
        val pages = PdfContactSheetExporter.planA4(
            listOf(card("portrait.png"), card("landscape.png", 252.0, 180.0))
        )
        assertEquals(2, pages.size)
    }
}

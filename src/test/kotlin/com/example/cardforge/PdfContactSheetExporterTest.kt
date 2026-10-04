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
    @Test
    fun singleCardPagesUseExactCardDimensionsWithoutMargins() {
        val first = PdfContactSheetExporter.CardSpec(Path.of("first.png"), 180.0, 252.0)
        val second = PdfContactSheetExporter.CardSpec(Path.of("second.png"), 200.0, 280.0)

        val pages = PdfContactSheetExporter.planSingleCardPages(listOf(first, second))

        assertEquals(2, pages.size)

        val firstPage = pages[0]
        assertEquals(180f, firstPage.pageSize.width)
        assertEquals(252f, firstPage.pageSize.height)
        assertEquals(1, firstPage.slots.size)
        assertEquals(0.0, firstPage.slots[0].x)
        assertEquals(0.0, firstPage.slots[0].y)
        assertEquals(180.0, firstPage.slots[0].widthPt)
        assertEquals(252.0, firstPage.slots[0].heightPt)

        val secondPage = pages[1]
        assertEquals(200f, secondPage.pageSize.width)
        assertEquals(280f, secondPage.pageSize.height)
        assertEquals(1, secondPage.slots.size)
        assertEquals(0.0, secondPage.slots[0].x)
        assertEquals(0.0, secondPage.slots[0].y)
        assertEquals(200.0, secondPage.slots[0].widthPt)
        assertEquals(280.0, secondPage.slots[0].heightPt)
    }

    @Test
    fun singleCardPagesReturnEmptyForNoCards() {
        assertTrue(PdfContactSheetExporter.planSingleCardPages(emptyList()).isEmpty())
    }

}

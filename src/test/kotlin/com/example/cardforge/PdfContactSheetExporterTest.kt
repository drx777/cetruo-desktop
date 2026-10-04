package com.example.cardforge

import org.apache.pdfbox.Loader
import java.nio.file.Files
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
        val first = PdfContactSheetExporter.CardSpec(Paths.get("first.png"), 180.0, 252.0)
        val second = PdfContactSheetExporter.CardSpec(Paths.get("second.png"), 200.0, 280.0)

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

    @Test
    fun a4ScaleIsClampedToSupportedRange() {
        val tooSmall = PdfContactSheetExporter.planA4(listOf(card("small.png")), scale = 0.01)
            .single().slots.single()
        val tooLarge = PdfContactSheetExporter.planA4(listOf(card("large.png")), scale = 5.0)
            .single().slots.single()

        assertEquals(45.0, tooSmall.widthPt, 0.001)
        assertEquals(63.0, tooSmall.heightPt, 0.001)
        assertEquals(180.0, tooLarge.widthPt, 0.001)
        assertEquals(252.0, tooLarge.heightPt, 0.001)
    }

    @Test
    fun a4MarginAndGapAreClampedWithoutPushingSlotsOutsidePage() {
        val pages = PdfContactSheetExporter.planA4(
            cards = List(20) { card("card-$it.png") },
            marginMm = 999.0,
            gapMm = 999.0
        )

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
    fun plannerChoosesLandscapeWhenItFitsMoreCards() {
        val wide = card("wide.png", width = 300.0, height = 120.0)
        val page = PdfContactSheetExporter.planA4(List(4) { wide }).first()

        assertTrue(page.pageSize.width > page.pageSize.height)
    }

    @Test
    fun mixedSizePlanningPreservesEveryCardExactlyOnce() {
        val cards = listOf(
            card("a.png"),
            card("b.png"),
            card("c.png", 252.0, 180.0),
            card("d.png", 252.0, 180.0),
            card("e.png", 200.0, 280.0)
        )

        val plannedPaths = PdfContactSheetExporter.planA4(cards)
            .flatMap { it.slots }
            .map { it.card.path.toString() }

        assertEquals(cards.map { it.path.toString() }.sorted(), plannedPaths.sorted())
        assertEquals(cards.size, plannedPaths.size)
    }

    @Test
    fun vectorPdfExportPreservesPlannedPageSizeWithoutRasterizingPureSvg() {
        val root = Files.createTempDirectory("cardforge-vector-pdf")
        val target = root.resolve("vector.pdf")
        val spec = card("vector.svg", width = 180.0, height = 252.0)
        val plans = PdfContactSheetExporter.planSingleCardPages(listOf(spec))

        PdfContactSheetExporter.exportVector(target, plans) {
            """
                <svg xmlns="http://www.w3.org/2000/svg" width="180" height="252" viewBox="0 0 180 252">
                  <rect x="5" y="5" width="170" height="242" rx="8" fill="#ffffff" stroke="#111111" stroke-width="2"/>
                  <text x="20" y="40" font-family="serif" font-size="18">Vector Card</text>
                </svg>
            """.trimIndent()
        }

        Loader.loadPDF(target.toFile()).use { document ->
            assertEquals(1, document.numberOfPages)
            val page = document.getPage(0)
            assertEquals(180f, page.mediaBox.width, 0.01f)
            assertEquals(252f, page.mediaBox.height, 0.01f)
            assertTrue(page.resources.xObjectNames.none(), "Pure SVG page should not contain raster/image XObjects")
        }
    }

}

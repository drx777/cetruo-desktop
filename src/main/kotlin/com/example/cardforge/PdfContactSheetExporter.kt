package com.example.cardforge

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.nio.file.Path
import kotlin.math.floor

object PdfContactSheetExporter {
    data class CardSpec(val path: Path, val widthPt: Double, val heightPt: Double)
    data class Slot(
        val card: CardSpec,
        val x: Double,
        val y: Double,
        val widthPt: Double,
        val heightPt: Double
    )
    data class PagePlan(val slots: List<Slot>, val pageSize: PDRectangle)

    private const val DEFAULT_MARGIN_MM = 10.0
    private const val DEFAULT_GAP_MM = 3.0
    private const val PT_PER_MM = 72.0 / 25.4

    fun planA4(
        cards: List<CardSpec>,
        scale: Double = 1.0,
        marginMm: Double = DEFAULT_MARGIN_MM,
        gapMm: Double = DEFAULT_GAP_MM
    ): List<PagePlan> {
        if (cards.isEmpty()) return emptyList()
        val safeScale = scale.coerceIn(0.25, 1.0)
        val marginPt = marginMm.coerceIn(0.0, 30.0) * PT_PER_MM
        val gapPt = gapMm.coerceIn(0.0, 15.0) * PT_PER_MM

        return cards.groupBy { (it.widthPt * 100).toLong() to (it.heightPt * 100).toLong() }
            .values.flatMap { group ->
                val sampleW = group.first().widthPt * safeScale
                val sampleH = group.first().heightPt * safeScale
                val portrait = PDRectangle.A4
                val landscape = PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth())

                fun capacity(page: PDRectangle): Pair<Int, Int> {
                    val availableW = page.getWidth().toDouble() - 2.0 * marginPt
                    val availableH = page.getHeight().toDouble() - 2.0 * marginPt
                    val cols = floor((availableW + gapPt) / (sampleW + gapPt)).toInt().coerceAtLeast(0)
                    val rows = floor((availableH + gapPt) / (sampleH + gapPt)).toInt().coerceAtLeast(0)
                    return cols to rows
                }

                val pCap = capacity(portrait)
                val lCap = capacity(landscape)
                val pCount = pCap.first * pCap.second
                val lCount = lCap.first * lCap.second
                val selectedPageAndCapacity: Pair<PDRectangle, Pair<Int, Int>> =
                    if (lCount > pCount) Pair(landscape, lCap) else Pair(portrait, pCap)
                val page: PDRectangle = selectedPageAndCapacity.first
                val dims: Pair<Int, Int> = selectedPageAndCapacity.second
                val columns = dims.first.coerceAtLeast(1)
                val rows = dims.second.coerceAtLeast(1)
                val perPage = (columns * rows).coerceAtLeast(1)

                group.chunked(perPage).map { chunk ->
                    PagePlan(
                        slots = chunk.mapIndexed { index, card ->
                            val width = card.widthPt * safeScale
                            val height = card.heightPt * safeScale
                            val col = index % columns
                            val row = index / columns
                            val x = marginPt + col * (sampleW + gapPt)
                            val y = page.getHeight().toDouble() - marginPt - sampleH - row * (sampleH + gapPt)
                            Slot(card, x, y, width, height)
                        },
                        pageSize = page
                    )
                }
            }
    }

    fun export(target: Path, pages: List<PagePlan>, renderPng: (Path) -> ByteArray) {
        PDDocument().use { document ->
            pages.forEach { plan ->
                val page = PDPage(plan.pageSize)
                document.addPage(page)
                PDPageContentStream(document, page).use { content ->
                    plan.slots.forEach { slot ->
                        val png = renderPng(slot.card.path)
                        val image = PDImageXObject.createFromByteArray(document, png, slot.card.path.fileName.toString())
                        content.drawImage(
                            image,
                            slot.x.toFloat(),
                            slot.y.toFloat(),
                            slot.widthPt.toFloat(),
                            slot.heightPt.toFloat()
                        )
                    }
                }
            }
            document.save(target.toFile())
        }
    }
}

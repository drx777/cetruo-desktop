package com.example.cardforge

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput
import org.apache.fop.svg.PDFTranscoder
import java.io.ByteArrayOutputStream
import java.io.StringReader
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

    fun planSingleCardPages(cards: List<CardSpec>): List<PagePlan> =
        cards.map { card ->
            val page = PDRectangle(card.widthPt.toFloat(), card.heightPt.toFloat())
            PagePlan(
                slots = listOf(
                    Slot(
                        card = card,
                        x = 0.0,
                        y = 0.0,
                        widthPt = card.widthPt,
                        heightPt = card.heightPt
                    )
                ),
                pageSize = page
            )
        }

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


    private data class SvgBody(val viewBox: String, val inner: String)

    private fun svgBody(svg: String): SvgBody {
        val start = svg.indexOf("<svg")
        require(start >= 0) { "Card SVG has no <svg> root" }
        val openEnd = svg.indexOf('>', start)
        val close = svg.lastIndexOf("</svg>")
        require(openEnd > start && close > openEnd) { "Card SVG root is incomplete" }
        val opening = svg.substring(start, openEnd + 1)
        val viewBox = Regex("""viewBox\\s*=\\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(opening)?.groupValues?.getOrNull(1)
            ?: error("Card SVG is missing viewBox")
        return SvgBody(viewBox, svg.substring(openEnd + 1, close))
    }

    private fun scopedSvgBody(body: String, prefix: String): String {
        val ids = Regex("""\\bid=["']([^"']+)["']""").findAll(body)
            .map { it.groupValues[1] }
            .toSet()
        var scoped = body
        ids.forEach { id ->
            val replacement = "${prefix}-${id}"
            scoped = scoped
                .replace("id=\"${id}\"", "id=\"${replacement}\"")
                .replace("id='${id}'", "id='${replacement}'")
                .replace("url(#${id})", "url(#${replacement})")
                .replace("href=\"#${id}\"", "href=\"#${replacement}\"")
                .replace("href='#${id}'", "href='#${replacement}'")
                .replace("xlink:href=\"#${id}\"", "xlink:href=\"#${replacement}\"")
                .replace("xlink:href='#${id}'", "xlink:href='#${replacement}'")
        }
        return scoped
    }

    private fun vectorPageSvg(plan: PagePlan, renderSvg: (Path) -> String): String {
        val pageW = plan.pageSize.width.toDouble()
        val pageH = plan.pageSize.height.toDouble()
        val cards = plan.slots.mapIndexed { index, slot ->
            val parsed = svgBody(renderSvg(slot.card.path))
            val inner = scopedSvgBody(parsed.inner, "card${index}")
            val yFromTop = pageH - slot.y - slot.heightPt
            """
                <svg x="${slot.x}" y="${yFromTop}" width="${slot.widthPt}" height="${slot.heightPt}"
                     viewBox="${parsed.viewBox}" preserveAspectRatio="none">
                  ${inner}
                </svg>
            """.trimIndent()
        }.joinToString("\n")

        return """
            <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink"
                 width="${pageW}pt" height="${pageH}pt" viewBox="0 0 ${pageW} ${pageH}">
              ${cards}
            </svg>
        """.trimIndent()
    }

    fun exportVector(target: Path, pages: List<PagePlan>, renderSvg: (Path) -> String) {
        PDDocument().use { document ->
            pages.forEach { plan ->
                val pageSvg = vectorPageSvg(plan, renderSvg)
                val pdfBytes = ByteArrayOutputStream().use { output ->
                    PDFTranscoder().transcode(
                        TranscoderInput(StringReader(pageSvg)),
                        TranscoderOutput(output)
                    )
                    output.toByteArray()
                }
                Loader.loadPDF(pdfBytes).use { source ->
                    source.pages.forEach { page -> document.importPage(page) }
                }
            }
            document.save(target.toFile())
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

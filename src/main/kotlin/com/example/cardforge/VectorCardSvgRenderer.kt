package com.example.cardforge

import javafx.embed.swing.SwingFXUtils
import javafx.scene.image.Image
import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.geom.PathIterator
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.math.max

object VectorCardSvgRenderer {
    private fun esc(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    private fun fmt(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else "%.3f".format(java.util.Locale.US, value)

    private fun imageDataUri(image: Image): String {
        val buffered = SwingFXUtils.fromFXImage(image, null) ?: error("Could not read card artwork")
        val bytes = ByteArrayOutputStream().use { out ->
            check(ImageIO.write(buffered, "png", out)) { "Could not encode card artwork" }
            out.toByteArray()
        }
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes)
    }

    private data class SvgFragment(val viewBox: String?, val inner: String)

    private fun svgFragment(raw: String): SvgFragment? {
        val start = raw.indexOf("<svg")
        if (start < 0) return null
        val openEnd = raw.indexOf('>', start)
        val close = raw.lastIndexOf("</svg>")
        if (openEnd < 0 || close <= openEnd) return null
        val opening = raw.substring(start, openEnd + 1)
        val viewBox = Regex("""viewBox\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(opening)?.groupValues?.getOrNull(1)
        return SvgFragment(viewBox, raw.substring(openEnd + 1, close))
    }

    private fun nativeSvg(raw: String, width: Double, height: Double): String {
        val fragment = svgFragment(raw) ?: return ""
        val viewBox = fragment.viewBox ?: "0 0 ${fmt(width)} ${fmt(height)}"
        return """<svg x="0" y="0" width="${fmt(width)}" height="${fmt(height)}" viewBox="${esc(viewBox)}" preserveAspectRatio="none">${fragment.inner}</svg>"""
    }

    private fun rect(rect: TemplateRect, fill: String, stroke: String, strokeWidth: Double, opacity: Double = 1.0): String =
        """<rect x="${fmt(rect.x)}" y="${fmt(rect.y)}" width="${fmt(rect.width)}" height="${fmt(rect.height)}" rx="${fmt(rect.radius)}" ry="${fmt(rect.radius)}" fill="${esc(fill)}" fill-opacity="${fmt(opacity.coerceIn(0.0, 1.0))}" stroke="${esc(stroke)}" stroke-width="${fmt(strokeWidth)}"/>"""

    private val fontRenderContext = FontRenderContext(null, true, true)

    private fun awtFont(size: Double, bold: Boolean, italic: Boolean): Font {
        var style = Font.PLAIN
        if (bold) style = style or Font.BOLD
        if (italic) style = style or Font.ITALIC
        return Font("Georgia", style, size.coerceAtLeast(1.0).toInt())
            .deriveFont(size.toFloat())
    }

    private fun wrap(text: String, maxWidth: Double, font: Font): List<String> {
        if (text.isBlank()) return listOf("")
        fun width(value: String): Double =
            font.getStringBounds(value, fontRenderContext).width

        return text.lines().flatMap { paragraph ->
            if (paragraph.isBlank()) return@flatMap listOf("")
            val words = paragraph.trim().split(Regex("\\s+"))
            val lines = mutableListOf<String>()
            var current = ""
            words.forEach { word ->
                val candidate = if (current.isBlank()) word else "$current $word"
                if (current.isBlank() || width(candidate) <= maxWidth) {
                    current = candidate
                } else {
                    lines += current
                    current = word
                }
            }
            if (current.isNotEmpty()) lines += current
            lines
        }
    }

    private fun shapePath(shape: java.awt.Shape): String {
        val iterator = shape.getPathIterator(null)
        val coords = DoubleArray(6)
        val out = StringBuilder()
        while (!iterator.isDone) {
            when (iterator.currentSegment(coords)) {
                PathIterator.SEG_MOVETO -> out.append("M").append(fmt(coords[0])).append(" ").append(fmt(coords[1]))
                PathIterator.SEG_LINETO -> out.append("L").append(fmt(coords[0])).append(" ").append(fmt(coords[1]))
                PathIterator.SEG_QUADTO -> out.append("Q")
                    .append(fmt(coords[0])).append(" ").append(fmt(coords[1])).append(" ")
                    .append(fmt(coords[2])).append(" ").append(fmt(coords[3]))
                PathIterator.SEG_CUBICTO -> out.append("C")
                    .append(fmt(coords[0])).append(" ").append(fmt(coords[1])).append(" ")
                    .append(fmt(coords[2])).append(" ").append(fmt(coords[3])).append(" ")
                    .append(fmt(coords[4])).append(" ").append(fmt(coords[5]))
                PathIterator.SEG_CLOSE -> out.append("Z")
            }
            iterator.next()
        }
        return out.toString()
    }

    private fun textBlock(
        text: String,
        spec: TemplateText,
        size: Double,
        color: String,
        bold: Boolean = false,
        italic: Boolean = false
    ): String {
        val font = awtFont(size, bold, italic)
        val lines = wrap(text, spec.width, font)
        val lineHeight = size * 1.16
        val blockHeight = lines.size * lineHeight
        val firstBaseline = spec.y + (spec.height - blockHeight) / 2.0 + size
        return lines.mapIndexedNotNull { index, line ->
            if (line.isEmpty()) return@mapIndexedNotNull null
            val glyphs = font.createGlyphVector(fontRenderContext, line)
            val bounds = glyphs.visualBounds
            val x = when (spec.align.uppercase()) {
                "CENTER" -> spec.x + (spec.width - bounds.width) / 2.0 - bounds.x
                "RIGHT" -> spec.x + spec.width - bounds.width - bounds.x
                else -> spec.x - bounds.x
            }
            val y = firstBaseline + index * lineHeight
            val shape = glyphs.getOutline(x.toFloat(), y.toFloat())
            """<path d="${shapePath(shape)}" fill="${esc(color)}"/>"""
        }.joinToString("")
    }

    fun svgFor(
        image: Image,
        data: CardData,
        template: CardTemplate,
        collectionPresentation: CollectionPresentation,
        artworkHref: String? = null
    ): String {
        val artUri = artworkHref ?: imageDataUri(image)
        val imageLayout = CardRenderer.imageLayout(image, data, template)
        val outerClipId = "card-outer-clip"
        val artClipId = "card-art-clip"
        val bleedClipId = "card-bleed-clip"
        val overlayTintId = "card-overlay-tint"

        val templateSvg = TemplateRepository.resolveSvg(template)
            ?.let { path -> runCatching { Files.readString(path) }.getOrNull() }
            ?.let { nativeSvg(it, template.width, template.height) }.orEmpty()
        val overlaySvg = data.backgroundOverlay.takeIf { it.isNotBlank() }
            ?.let(OverlayRepository::resolve)
            ?.let { path -> runCatching { Files.readString(path) }.getOrNull() }
            ?.let { nativeSvg(it, template.width, template.height) }.orEmpty()

        val foregroundOpacity = CollectionVisualSettings.foregroundOpacity(collectionPresentation)
        val descriptionOpacity = (data.panelOpacity.coerceIn(0.0, 1.0) * foregroundOpacity).coerceIn(0.0, 1.0)
        val artRadius = template.art.radius / 2.0
        val bleedInset = CollectionVisualSettings.bleedInset(data)
        val bleedRadius = (data.cornerRadius - bleedInset).coerceAtLeast(0.0)
        val bleedOpacity = CollectionVisualSettings.bleedOpacity(data, collectionPresentation)
        val copyright = if (collectionPresentation.showArtistCopyright) "© " else ""

        val framesOverlay = if (overlaySvg.isNotEmpty() && data.backgroundOverlayPlacement == OverlayPlacement.FRAMES_ONLY)
            """<g opacity="${fmt(data.backgroundOverlayOpacity.coerceIn(0.0, 1.0))}" filter="url(#${overlayTintId})">${overlaySvg}</g>""" else ""
        val contentOverlay = if (overlaySvg.isNotEmpty() && data.backgroundOverlayPlacement == OverlayPlacement.OVER_CONTENT)
            """<g opacity="${fmt(data.backgroundOverlayOpacity.coerceIn(0.0, 1.0))}" filter="url(#${overlayTintId})">${overlaySvg}</g>""" else ""

        val bleed = if (data.imageBleedOverFrame) """
            <g clip-path="url(#${bleedClipId})" opacity="${fmt(bleedOpacity)}">
              <image href="${artUri}" xlink:href="${artUri}" x="${fmt(template.art.x + imageLayout.x)}" y="${fmt(template.art.y + imageLayout.y)}"
                     width="${fmt(imageLayout.width)}" height="${fmt(imageLayout.height)}" preserveAspectRatio="none"/>
            </g>
        """.trimIndent() else ""

        return """
            <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink"
                 width="${fmt(template.width)}" height="${fmt(template.height)}"
                 viewBox="0 0 ${fmt(template.width)} ${fmt(template.height)}">
              <defs>
                <clipPath id="${outerClipId}"><rect x="0" y="0" width="${fmt(template.width)}" height="${fmt(template.height)}" rx="${fmt(data.cornerRadius)}" ry="${fmt(data.cornerRadius)}"/></clipPath>
                <clipPath id="${artClipId}"><rect x="${fmt(template.art.x)}" y="${fmt(template.art.y)}" width="${fmt(template.art.width)}" height="${fmt(template.art.height)}" rx="${fmt(artRadius)}" ry="${fmt(artRadius)}"/></clipPath>
                <clipPath id="${bleedClipId}"><rect x="${fmt(bleedInset)}" y="${fmt(bleedInset)}" width="${fmt((template.width - bleedInset * 2).coerceAtLeast(0.0))}" height="${fmt((template.height - bleedInset * 2).coerceAtLeast(0.0))}" rx="${fmt(bleedRadius)}" ry="${fmt(bleedRadius)}"/></clipPath>
                <filter id="${overlayTintId}" x="-10%" y="-10%" width="120%" height="120%">
                  <feFlood flood-color="${esc(data.overlayColor)}" result="tint"/>
                  <feComposite in="tint" in2="SourceAlpha" operator="in"/>
                </filter>
              </defs>
              <g clip-path="url(#${outerClipId})">
                <rect x="0" y="0" width="${fmt(template.width)}" height="${fmt(template.height)}" rx="${fmt(data.cornerRadius)}" ry="${fmt(data.cornerRadius)}" fill="${esc(data.backgroundColor)}" stroke="${esc(data.accentColor)}" stroke-width="${fmt(data.borderWidth)}"/>
                ${templateSvg}
                ${bleed}
                ${framesOverlay}

                ${rect(template.titleBox, data.backgroundColor, data.accentColor, 2.0, foregroundOpacity)}
                ${textBlock(data.title, template.titleText, data.titleFontSize, data.darkTextColor, bold = true)}
                ${textBlock("◇ ${data.cost}", template.costText, 19.0, data.frameColor, bold = true)}

                <rect x="${fmt(template.art.x)}" y="${fmt(template.art.y)}" width="${fmt(template.art.width)}" height="${fmt(template.art.height)}" rx="${fmt(artRadius)}" ry="${fmt(artRadius)}" fill="${esc(data.imagePadColor)}" stroke="${esc(data.accentColor)}" stroke-width="4"/>
                <g clip-path="url(#${artClipId})">
                  <image href="${artUri}" xlink:href="${artUri}" x="${fmt(template.art.x + imageLayout.x)}" y="${fmt(template.art.y + imageLayout.y)}" width="${fmt(imageLayout.width)}" height="${fmt(imageLayout.height)}" preserveAspectRatio="none"/>
                </g>
                <rect x="${fmt(template.art.x)}" y="${fmt(template.art.y)}" width="${fmt(template.art.width)}" height="${fmt(template.art.height)}" rx="${fmt(artRadius)}" ry="${fmt(artRadius)}" fill="none" stroke="${esc(data.accentColor)}" stroke-width="4"/>

                ${rect(template.typeBox, data.backgroundColor, data.accentColor, 2.0, foregroundOpacity)}
                ${textBlock(data.typeLine, template.typeText, 18.0, data.darkTextColor, bold = true)}
                ${textBlock("✦ ${data.rarity}", template.rarityText, 16.0, data.frameColor, bold = true)}

                ${rect(template.descriptionBox, data.panelColor, data.accentColor, 4.0, descriptionOpacity)}
                ${textBlock(collectionPresentation.descriptionHeading, template.descriptionHeading, 17.0, data.textColor, bold = true)}
                ${textBlock(data.description, template.descriptionText, data.bodyFontSize, data.textColor)}
                ${textBlock(data.flavorText, template.flavorText, 14.0, data.textColor, italic = true)}
                ${textBlock("${data.setName} • ${data.collectorNumber} • ${copyright}${data.artist}", template.footerText, 10.0, data.textColor)}

                ${rect(template.statsBox, data.backgroundColor, data.accentColor, 3.0, foregroundOpacity)}
                ${textBlock(data.stats, template.statsText, 25.0, data.darkTextColor, bold = true)}
                ${contentOverlay}
              </g>
            </svg>
        """.trimIndent()
    }
}

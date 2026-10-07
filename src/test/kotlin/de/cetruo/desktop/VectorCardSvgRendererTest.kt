package de.cetruo.desktop

import javafx.scene.image.Image
import java.io.ByteArrayInputStream
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VectorCardSvgRendererTest {
    private fun image(): Image {
        val png = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9Z0iAAAAAASUVORK5CYII="
        )
        return Image(ByteArrayInputStream(png))
    }

    private fun template() = CardTemplate(
        name = "Test",
        svgFile = "missing.svg",
        width = 630.0,
        height = 880.0,
        art = TemplateRect(42.0, 112.0, 546.0, 386.0, 18.0),
        titleBox = TemplateRect(35.0, 35.0, 560.0, 58.0, 12.0),
        titleText = TemplateText(52.0, 38.0, 390.0, 52.0),
        costText = TemplateText(450.0, 38.0, 120.0, 52.0, "RIGHT"),
        typeBox = TemplateRect(35.0, 515.0, 560.0, 48.0, 10.0),
        typeText = TemplateText(52.0, 517.0, 390.0, 44.0),
        rarityText = TemplateText(450.0, 517.0, 120.0, 44.0, "RIGHT"),
        descriptionBox = TemplateRect(35.0, 580.0, 560.0, 220.0, 14.0),
        descriptionHeading = TemplateText(52.0, 590.0, 520.0, 30.0),
        descriptionText = TemplateText(52.0, 625.0, 520.0, 100.0),
        flavorText = TemplateText(52.0, 730.0, 520.0, 32.0),
        footerText = TemplateText(52.0, 765.0, 430.0, 24.0),
        statsBox = TemplateRect(480.0, 778.0, 115.0, 70.0, 12.0),
        statsText = TemplateText(490.0, 786.0, 95.0, 54.0, "CENTER")
    )

    @Test
    fun outerBorderIsInsetByHalfStrokeSoFullWidthRemainsVisible() {
        val data = CardData(borderWidth = 8.0, cornerRadius = 24.0)
        val svg = VectorCardSvgRenderer.svgFor(
            image = image(),
            data = data,
            template = template(),
            collectionPresentation = CollectionPresentation(),
            artworkHref = "file:/tmp/art.png"
        )

        assertTrue(svg.contains("""<rect x="4" y="4""""))
        assertTrue(svg.contains("""width="622""""))
        assertTrue(svg.contains("""height="872""""))
        assertTrue(svg.contains("""rx="20""""))
        assertTrue(svg.contains("""stroke-width="8""""))
    }

    @Test
    fun generatedTextIsVectorPathsNotSvgTextElements() {
        val svg = VectorCardSvgRenderer.svgFor(
            image = image(),
            data = CardData(title = "Vector Title", stats = "4 / 5"),
            template = template(),
            collectionPresentation = CollectionPresentation(),
            artworkHref = "file:/tmp/art.png"
        )

        assertFalse(svg.contains("<text"))
        assertTrue(svg.contains("<path d="))
    }

    @Test
    fun artworkUsesHrefAndLegacyXlinkHref() {
        val href = "file:/tmp/card-art.png"
        val svg = VectorCardSvgRenderer.svgFor(
            image = image(),
            data = CardData(),
            template = template(),
            collectionPresentation = CollectionPresentation(),
            artworkHref = href
        )

        assertTrue(svg.contains("""href="$href""""))
        assertTrue(svg.contains("""xlink:href="$href""""))
    }
    @Test
    fun rarityControlsOuterFrameAndLegacyPanelGeometry() {
        val data = CardData(rarity = "Rare")
        val svg = VectorCardSvgRenderer.svgFor(
            image = image(),
            data = data,
            template = template().copy(visualStyle = TemplateVisualStyle(rarityFrames = true)),
            collectionPresentation = CollectionPresentation(),
            artworkHref = "file:/tmp/art.png"
        )

        assertTrue(svg.contains("""stroke="#D6B45A""""))
        assertTrue(svg.contains("""fill="${data.panelColor}" stroke="#D6B45A""""))
        assertTrue(svg.contains("""<rect x="35" y="580" width="560" height="220" rx="14" ry="14""""))
        assertTrue(svg.contains("""stroke-width="4""""))
        assertTrue(svg.contains("""<rect x="480" y="778" width="115" height="70" rx="12" ry="12""""))
        assertTrue(svg.contains("""stroke-width="3""""))
    }

    @Test
    fun templateVisualStyleRemainsOptionalForExistingTemplateJson() {
        val visual = template().visualStyle
        assertFalse(visual.rarityFrames)
        assertTrue(visual.statsJewelCut > 0.0)
    }
}

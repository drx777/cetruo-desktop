package com.example.cardforge

import javafx.scene.image.Image
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

/**
 * Self-contained SVG export. The visible card is a canonical JavaFX render embedded
 * as a PNG, which keeps PNG/SVG/PDF visually identical and avoids reader-specific
 * problems with nested SVG/filter/font rendering.
 */
object SvgExporter {
    private fun esc(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    fun svgFor(
        image: Image,
        data: CardData,
        template: CardTemplate,
        templateImage: Image?,
        backgroundOverlay: Image?,
        collectionPresentation: CollectionPresentation
    ): String {
        val png = ExportRenderer.pngBytes(
            image = image,
            data = data,
            template = template,
            templateImage = templateImage,
            backgroundOverlay = backgroundOverlay,
            collectionPresentation = collectionPresentation,
            scale = 2.0
        )
        val dataUri = Base64.getEncoder().encodeToString(png)
        return """
            <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink"
                 width="${template.width}"
                 height="${template.height}"
                 viewBox="0 0 ${template.width} ${template.height}">
              <!-- Card Forge canonical raster render: ${esc(data.assetId)} -->
              <image x="0" y="0" width="${template.width}" height="${template.height}"
                     preserveAspectRatio="none"
                     href="data:image/png;base64,$dataUri"
                     xlink:href="data:image/png;base64,$dataUri"/>
            </svg>
        """.trimIndent()
    }

    fun export(
        image: Image,
        data: CardData,
        template: CardTemplate,
        target: Path,
        templateImage: Image?,
        backgroundOverlay: Image?,
        collectionPresentation: CollectionPresentation
    ) {
        Files.writeString(target, svgFor(image, data, template, templateImage, backgroundOverlay, collectionPresentation))
    }
}

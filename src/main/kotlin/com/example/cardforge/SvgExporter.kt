package com.example.cardforge

import javafx.scene.image.Image
import java.nio.file.Files
import java.nio.file.Path

/**
 * Standalone vector SVG export. The source artwork remains raster, while Card Forge generated
 * template/frame/panel/text content remains SVG.
 */
object SvgExporter {
    fun svgFor(
        image: Image,
        data: CardData,
        template: CardTemplate,
        collectionPresentation: CollectionPresentation
    ): String = VectorCardSvgRenderer.svgFor(image, data, template, collectionPresentation)

    fun export(
        image: Image,
        data: CardData,
        template: CardTemplate,
        target: Path,
        collectionPresentation: CollectionPresentation
    ) {
        Files.writeString(target, svgFor(image, data, template, collectionPresentation))
    }
}

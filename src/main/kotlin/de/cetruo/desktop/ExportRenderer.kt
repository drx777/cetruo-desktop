package de.cetruo.desktop

import javafx.embed.swing.SwingFXUtils
import javafx.scene.Scene
import javafx.scene.SnapshotParameters
import javafx.scene.image.Image
import javafx.scene.image.WritableImage
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.ceil

/**
 * Single source of truth for rendered card output. The same JavaFX card tree is used
 * for PNG, SVG, PDF and the in-app contact sheet.
 *
 * A separate unmanaged Pane is used as the snapshot root. This is important when
 * scale < 1: placing the CardRenderer directly in a StackPane would center it inside
 * the smaller Scene and clip most of the card.
 */
object ExportRenderer {
    fun snapshotImage(
        image: Image,
        data: CardData,
        template: CardTemplate,
        templateImage: Image?,
        backgroundOverlay: Image?,
        collectionPresentation: CollectionPresentation,
        scale: Double = 1.0
    ): WritableImage {
        val safeScale = scale.coerceAtLeast(0.01)
        val root = CardRenderer.build(
            data = data,
            image = image,
            template = template,
            templateImage = templateImage,
            backgroundOverlay = backgroundOverlay,
            collectionPresentation = collectionPresentation,
            onImageDragged = { _, _ -> },
            onImageZoomed = { _ -> },
            onImageReset = {},
            showCropGuides = false
        ).root

        // Always snapshot the complete card at native template dimensions first.
        // Do not scale the JavaFX node itself: Node scaling is centered on the node's
        // bounds and a small off-screen scene can therefore clip most of the card.
        val sceneWidth = ceil(template.width).toInt().coerceAtLeast(1)
        val sceneHeight = ceil(template.height).toInt().coerceAtLeast(1)
        val snapshotHost = Pane().apply {
            prefWidth = sceneWidth.toDouble()
            prefHeight = sceneHeight.toDouble()
            minWidth = sceneWidth.toDouble()
            minHeight = sceneHeight.toDouble()
            maxWidth = sceneWidth.toDouble()
            maxHeight = sceneHeight.toDouble()
            isManaged = false
        }
        root.isManaged = false
        root.resize(template.width, template.height)
        root.relocate(0.0, 0.0)
        root.scaleX = 1.0
        root.scaleY = 1.0
        snapshotHost.children.add(root)
        snapshotHost.resize(sceneWidth.toDouble(), sceneHeight.toDouble())

        val scene = Scene(snapshotHost, sceneWidth.toDouble(), sceneHeight.toDouble(), true)
        scene.fill = Color.TRANSPARENT

        // Off-screen renders do not get a normal pulse/layout pass. Explicitly apply
        // CSS and lay out every parent before taking the snapshot; otherwise a managed
        // child of CardRenderer's StackPane can remain at an incorrect/default position.
        snapshotHost.applyCss()
        root.applyCss()
        root.layout()
        snapshotHost.layout()

        val nativeSnapshot = WritableImage(sceneWidth, sceneHeight)
        snapshotHost.snapshot(
            SnapshotParameters().apply { fill = Color.TRANSPARENT },
            nativeSnapshot
        )
        if (safeScale == 1.0) return nativeSnapshot

        // Resize the already-complete bitmap instead of scaling the JavaFX node. This
        // keeps previews small while preserving the exact full-card composition.
        val targetWidth = ceil(template.width * safeScale).toInt().coerceAtLeast(1)
        val targetHeight = ceil(template.height * safeScale).toInt().coerceAtLeast(1)
        val source = SwingFXUtils.fromFXImage(nativeSnapshot, null) ?: return nativeSnapshot
        val resized = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB)
        val graphics = resized.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null)
        } finally {
            graphics.dispose()
        }
        return SwingFXUtils.toFXImage(resized, null) ?: nativeSnapshot
    }

    fun pngBytes(
        image: Image,
        data: CardData,
        template: CardTemplate,
        templateImage: Image?,
        backgroundOverlay: Image?,
        collectionPresentation: CollectionPresentation,
        scale: Double = 1.0
    ): ByteArray {
        val snapshot = snapshotImage(image, data, template, templateImage, backgroundOverlay, collectionPresentation, scale)
        val buffered = SwingFXUtils.fromFXImage(snapshot, null) ?: error("Could not create card bitmap")
        return ByteArrayOutputStream().use { output ->
            check(ImageIO.write(buffered, "png", output)) { "Could not encode card PNG" }
            output.toByteArray()
        }
    }
}

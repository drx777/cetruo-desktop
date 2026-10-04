package com.example.cardforge

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Cursor
import javafx.scene.control.Label
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.input.ScrollEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.scene.shape.Line
import javafx.scene.shape.Rectangle
import javafx.scene.text.Font
import javafx.scene.text.FontPosture
import javafx.scene.text.FontWeight
import kotlin.math.max
import kotlin.math.min

object CardRenderer {
    const val DEFAULT_W = 630.0
    const val DEFAULT_H = 880.0
    const val DEFAULT_ART_W = 546.0
    const val DEFAULT_ART_H = 386.0

    data class Rendered(
        val root: StackPane,
        val imageView: ImageView,
        val viewport: StackPane,
        val width: Double,
        val height: Double
    )

    fun build(
        data: CardData,
        image: Image?,
        template: CardTemplate,
        templateImage: Image?,
        backgroundOverlay: Image?,
        collectionPresentation: CollectionPresentation = CollectionPresentation(),
        onImageDragged: (dx: Double, dy: Double) -> Unit,
        onImageZoomed: (delta: Double) -> Unit = {},
        onImageReset: () -> Unit = {},
        showCropGuides: Boolean = false
    ): Rendered {
        val root = StackPane().apply {
            styleClass.add("card-rendered")
            prefWidth = template.width
            prefHeight = template.height
            minWidth = template.width
            minHeight = template.height
            maxWidth = template.width
            maxHeight = template.height
        }

        val outer = StackPane().apply {
            isManaged = false
            prefWidth = template.width
            prefHeight = template.height
            minWidth = template.width
            minHeight = template.height
            maxWidth = template.width
            maxHeight = template.height
            resize(template.width, template.height)
            relocate(0.0, 0.0)
            style = "-fx-background-color:${data.backgroundColor};-fx-background-radius:${data.cornerRadius}px;" +
                "-fx-border-color:${data.accentColor};-fx-border-width:${data.borderWidth}px;" +
                "-fx-border-radius:${data.cornerRadius}px;"
        }

        val templateView = templateImage?.let {
            ImageView(it).apply {
                isPreserveRatio = false
                fitWidth = template.width
                fitHeight = template.height
                isMouseTransparent = true
            }
        }
        if (templateView != null) outer.children.add(templateView)

        if (backgroundOverlay != null && data.backgroundOverlayPlacement == OverlayPlacement.FRAMES_ONLY) {
            outer.children.add(overlayLayer(backgroundOverlay, template, data))
        }

        val content = Pane().apply {
            isManaged = false
            prefWidth = template.width
            prefHeight = template.height
            minWidth = template.width
            minHeight = template.height
            maxWidth = template.width
            maxHeight = template.height
            resize(template.width, template.height)
            relocate(0.0, 0.0)
        }

        fun label(text: String, size: Double, bold: Boolean, color: String): Label = Label(text).apply {
            val weight = if (bold) FontWeight.BOLD else FontWeight.NORMAL
            font = Font.font("Georgia", weight, size)
            textFill = Color.web(color)
            style = "-fx-font-family:'Georgia';-fx-text-fill:$color;"
            isWrapText = true
        }

        val titleBox = decorativeBox(template.titleBox, data.backgroundColor, data.accentColor, 2.0)
        position(titleBox, template.titleBox)
        val titleLabel = label(data.title, data.titleFontSize, true, data.darkTextColor)
        place(titleLabel, template.titleText)
        val costLabel = label("◇ ${data.cost}", 19.0, true, data.frameColor)
        place(costLabel, template.costText)
        content.children.addAll(titleBox, titleLabel, costLabel)

        val artFrame = Pane().apply {
            isManaged = false
            prefWidth = template.art.width
            prefHeight = template.art.height
            minWidth = template.art.width
            minHeight = template.art.height
            maxWidth = template.art.width
            maxHeight = template.art.height
            resize(template.art.width, template.art.height)
        }
        val frameRect = Rectangle(template.art.width, template.art.height).apply {
            arcWidth = template.art.radius
            arcHeight = template.art.radius
            fill = Color.web(data.imagePadColor)
            stroke = Color.web(data.accentColor)
            strokeWidth = 4.0
        }
        val viewport = StackPane().apply {
            isManaged = false
            prefWidth = template.art.width
            prefHeight = template.art.height
            minWidth = template.art.width
            minHeight = template.art.height
            maxWidth = template.art.width
            maxHeight = template.art.height
            resize(template.art.width, template.art.height)
            relocate(0.0, 0.0)
            clip = Rectangle(template.art.width, template.art.height).apply {
                arcWidth = template.art.radius
                arcHeight = template.art.radius
            }
        }
        val imageView = ImageView(image).apply { isSmooth = true; isManaged = false }
        updateImageView(imageView, image, data, template)
        viewport.children.add(imageView)
        if (showCropGuides) addCropGuides(viewport)
        val foregroundBorder = Rectangle(template.art.width, template.art.height).apply {
            isMouseTransparent = true
            fill = Color.TRANSPARENT
            stroke = Color.web(data.accentColor)
            strokeWidth = 4.0
            arcWidth = template.art.radius
            arcHeight = template.art.radius
        }
        artFrame.children.addAll(frameRect, viewport, foregroundBorder)
        position(artFrame, template.art)
        content.children.add(artFrame)
        installImageInteractions(viewport, onImageDragged, onImageZoomed, onImageReset)

        val typeBox = decorativeBox(template.typeBox, data.backgroundColor, data.accentColor, 2.0)
        position(typeBox, template.typeBox)
        val typeLabel = label(data.typeLine, 18.0, true, data.darkTextColor)
        place(typeLabel, template.typeText)
        val rarityLabel = label("✦ ${data.rarity}", 16.0, true, data.frameColor)
        place(rarityLabel, template.rarityText)
        content.children.addAll(typeBox, typeLabel, rarityLabel)

        val descPanel = decorativeBox(template.descriptionBox, data.panelColor, data.accentColor, 4.0, data.panelOpacity)
        position(descPanel, template.descriptionBox)
        content.children.add(descPanel)
        val heading = label(collectionPresentation.descriptionHeading, 17.0, true, data.textColor)
        place(heading, template.descriptionHeading)
        val description = label(data.description, data.bodyFontSize, false, data.textColor)
        description.font = Font.font("Georgia", FontWeight.NORMAL, data.bodyFontSize)
        place(description, template.descriptionText)
        val flavor = label(data.flavorText, 14.0, false, data.textColor).apply {
            font = Font.font("Georgia", FontPosture.ITALIC, 14.0)
        }
        place(flavor, template.flavorText)
        val copyright = if (collectionPresentation.showArtistCopyright) "© " else ""
        val footer = label("${data.setName} • ${data.collectorNumber} • ${copyright}${data.artist}", 10.0, false, data.textColor)
        place(footer, template.footerText)
        content.children.addAll(heading, description, flavor, footer)

        val statsBox = decorativeBox(template.statsBox, data.backgroundColor, data.accentColor, 3.0)
        position(statsBox, template.statsBox)
        val stats = label(data.stats, 25.0, true, data.darkTextColor)
        place(stats, template.statsText)
        content.children.addAll(statsBox, stats)

        outer.children.add(content)

        if (backgroundOverlay != null && data.backgroundOverlayPlacement == OverlayPlacement.OVER_CONTENT) {
            outer.children.add(overlayLayer(backgroundOverlay, template, data))
        }

        root.children.add(outer)
        return Rendered(root, imageView, viewport, template.width, template.height)
    }

    private fun decorativeBox(rect: TemplateRect, fill: String, stroke: String, strokeWidth: Double, opacity: Double = 1.0): StackPane =
        StackPane().apply {
            isManaged = false
            prefWidth = rect.width
            prefHeight = rect.height
            minWidth = rect.width
            minHeight = rect.height
            maxWidth = rect.width
            maxHeight = rect.height
            resize(rect.width, rect.height)
            style = "-fx-background-color:${fill.withOpacity(opacity)};-fx-background-radius:${rect.radius}px;" +
                "-fx-border-color:${stroke};-fx-border-width:${strokeWidth}px;-fx-border-radius:${rect.radius}px;"
        }

    private fun String.withOpacity(opacity: Double): String = runCatching {
        val c = Color.web(this)
        "rgba(${(c.red * 255).toInt()},${(c.green * 255).toInt()},${(c.blue * 255).toInt()},${opacity.coerceIn(0.0, 1.0)})"
    }.getOrElse { this }

    private fun place(node: javafx.scene.layout.Region, spec: TemplateText) {
        // Template coordinates are absolute. Keep these nodes out of parent layout so
        // StackPane/Pane cannot resize or relocate them after we position them.
        node.isManaged = false
        node.prefWidth = spec.width
        node.prefHeight = spec.height
        node.minWidth = spec.width
        node.minHeight = spec.height
        node.maxWidth = spec.width
        node.maxHeight = spec.height
        node.resize(spec.width, spec.height)
        node.relocate(spec.x, spec.y)
        val alignment = when (spec.align.uppercase()) {
            "CENTER" -> Pos.CENTER
            "RIGHT" -> Pos.CENTER_RIGHT
            else -> Pos.CENTER_LEFT
        }
        if (node is Label) node.alignment = alignment
    }

    private fun position(node: javafx.scene.Node, rect: TemplateRect) {
        node.isManaged = false
        node.relocate(rect.x, rect.y)
        if (node is javafx.scene.layout.Region) node.resize(rect.width, rect.height)
    }

    private fun overlayLayer(image: Image, template: CardTemplate, data: CardData): ImageView =
        ImageView(image).apply {
            isPreserveRatio = false
            fitWidth = template.width
            fitHeight = template.height
            opacity = data.backgroundOverlayOpacity.coerceIn(0.0, 1.0)
            isMouseTransparent = true
        }

    data class ImageLayout(
        val width: Double,
        val height: Double,
        val x: Double,
        val y: Double,
        val minOffsetX: Double,
        val maxOffsetX: Double,
        val minOffsetY: Double,
        val maxOffsetY: Double
    )

    fun imageLayout(image: Image?, data: CardData, template: CardTemplate): ImageLayout {
        if (image == null || image.width <= 0.0 || image.height <= 0.0) {
            return ImageLayout(template.art.width, template.art.height, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        }
        val sourceW = image.width
        val sourceH = image.height
        val (width, height) = when (data.imageMode) {
            ImageMode.STRETCH -> template.art.width * data.imageZoom to template.art.height * data.imageZoom
            ImageMode.COVER, ImageMode.CONTAIN -> {
                val baseScale = when (data.imageMode) {
                    ImageMode.COVER -> max(template.art.width / sourceW, template.art.height / sourceH)
                    ImageMode.CONTAIN -> min(template.art.width / sourceW, template.art.height / sourceH)
                    ImageMode.STRETCH -> 1.0
                }
                sourceW * baseScale * data.imageZoom to sourceH * baseScale * data.imageZoom
            }
        }
        val centeredX = (template.art.width - width) / 2.0
        val centeredY = (template.art.height - height) / 2.0
        val leftmostX = min(0.0, template.art.width - width)
        val rightmostX = max(0.0, template.art.width - width)
        val topmostY = min(0.0, template.art.height - height)
        val bottommostY = max(0.0, template.art.height - height)
        val minX = leftmostX - centeredX
        val maxX = rightmostX - centeredX
        val minY = topmostY - centeredY
        val maxY = bottommostY - centeredY
        val ox = data.imageOffsetX.coerceIn(minX, maxX)
        val oy = data.imageOffsetY.coerceIn(minY, maxY)
        val x = (template.art.width - width) / 2.0 + ox
        val y = (template.art.height - height) / 2.0 + oy
        return ImageLayout(width, height, x, y, minX, maxX, minY, maxY)
    }

    fun updateImageView(view: ImageView, image: Image?, data: CardData, template: CardTemplate) {
        val layout = imageLayout(image, data, template)
        view.isPreserveRatio = false
        view.fitWidth = layout.width
        view.fitHeight = layout.height
        view.translateX = layout.x
        view.translateY = layout.y
    }

    private fun addCropGuides(viewport: StackPane) {
        val guide = StackPane().apply { isMouseTransparent = true }
        val artW = viewport.prefWidth
        val artH = viewport.prefHeight
        val vertical = Line(artW / 2.0, 0.0, artW / 2.0, artH)
        val horizontal = Line(0.0, artH / 2.0, artW, artH / 2.0)
        listOf(vertical, horizontal).forEach {
            it.stroke = Color.rgb(255, 255, 255, 0.28)
            it.strokeWidth = 1.0
        }
        guide.children.addAll(vertical, horizontal)
        val help = Label("DRAG TO POSITION  •  SCROLL TO ZOOM").apply {
            isMouseTransparent = true
            textFill = Color.rgb(255, 255, 255, 0.82)
            style = "-fx-background-color:rgba(0,0,0,0.42);-fx-background-radius:8px;-fx-padding:5 8 5 8;-fx-font-size:11px;-fx-font-weight:bold;"
        }
        StackPane.setAlignment(help, Pos.BOTTOM_CENTER)
        StackPane.setMargin(help, Insets(0.0, 0.0, 10.0, 0.0))
        guide.children.add(help)
        viewport.children.add(guide)
    }

    private fun installImageInteractions(
        viewport: StackPane,
        onDragged: (dx: Double, dy: Double) -> Unit,
        onZoomed: (delta: Double) -> Unit,
        onReset: () -> Unit
    ) {
        var lastSceneX = 0.0
        var lastSceneY = 0.0
        var dragging = false
        var dragScene: javafx.scene.Scene? = null

        val dragHandler = javafx.event.EventHandler<MouseEvent> { event ->
            if (!dragging || !event.isPrimaryButtonDown) return@EventHandler
            val previous = viewport.sceneToLocal(lastSceneX, lastSceneY)
            val current = viewport.sceneToLocal(event.sceneX, event.sceneY)
            val dx = current.x - previous.x
            val dy = current.y - previous.y
            lastSceneX = event.sceneX
            lastSceneY = event.sceneY
            if (dx != 0.0 || dy != 0.0) onDragged(dx, dy)
            event.consume()
        }

        lateinit var releaseHandler: javafx.event.EventHandler<MouseEvent>
        releaseHandler = javafx.event.EventHandler { event ->
            dragging = false
            viewport.cursor = Cursor.OPEN_HAND
            dragScene?.removeEventFilter(MouseEvent.MOUSE_DRAGGED, dragHandler)
            dragScene?.removeEventFilter(MouseEvent.MOUSE_RELEASED, releaseHandler)
            dragScene = null
            event.consume()
        }

        viewport.cursor = Cursor.OPEN_HAND
        viewport.addEventFilter(MouseEvent.MOUSE_PRESSED) { event ->
            if (event.button == MouseButton.PRIMARY) {
                lastSceneX = event.sceneX
                lastSceneY = event.sceneY
                dragging = true
                viewport.cursor = Cursor.CLOSED_HAND
                dragScene = viewport.scene
                dragScene?.addEventFilter(MouseEvent.MOUSE_DRAGGED, dragHandler)
                dragScene?.addEventFilter(MouseEvent.MOUSE_RELEASED, releaseHandler)
                event.consume()
            }
        }
        viewport.addEventHandler(MouseEvent.MOUSE_CLICKED) { event ->
            if (event.button == MouseButton.PRIMARY && event.clickCount == 2) {
                onReset()
                event.consume()
            }
        }
        viewport.addEventHandler(ScrollEvent.SCROLL) { event ->
            if (event.deltaY != 0.0) {
                onZoomed(if (event.deltaY > 0.0) 0.1 else -0.1)
                event.consume()
            }
        }
    }
}

package de.cetruo.desktop.editor

import de.cetruo.desktop.*
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.Slider
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.paint.Color

class EditorUiFactory(
    private val statusLabel: (CardStatus) -> String
) {
    fun statusCell() = object : ListCell<CardStatus>() {
        override fun updateItem(item: CardStatus?, empty: Boolean) {
            super.updateItem(item, empty)
            text = if (empty || item == null) null else statusLabel(item)
        }
    }

    fun schemeCell() = object : ListCell<ColorScheme>() {
        override fun updateItem(item: ColorScheme?, empty: Boolean) {
            super.updateItem(item, empty)
            if (empty || item == null) {
                text = null
                graphic = null
                tooltip = null
                return
            }
            val tone = if (item.resolvedTone == SchemeTone.LIGHT) "☀ Light" else "◐ Dark"
            text = "$tone · ${item.name}"
            graphic = HBox(4.0).apply {
                listOf(item.backgroundColor, item.panelColor, item.frameColor, item.accentColor, item.overlayColor).forEach { hex ->
                    children.add(Region().apply {
                        minWidth = 12.0
                        maxWidth = 12.0
                        minHeight = 12.0
                        maxHeight = 12.0
                        style = "-fx-background-color:$hex;-fx-background-radius:3px;-fx-border-color:rgba(255,255,255,0.25);-fx-border-radius:3px;"
                    })
                }
            }
            val warnings = SchemeContrast.warnings(item)
            tooltip = Tooltip(buildString {
                append(item.description)
                append("\nCard background: ${item.backgroundColor}\nOverlay tint: ${item.overlayColor}")
                if (warnings.isNotEmpty()) {
                    append("\n\nContrast warnings:")
                    warnings.forEach { append("\n• ").append(it) }
                }
            })
        }
    }

    fun templateCell() = object : ListCell<CardTemplate>() {
        override fun updateItem(item: CardTemplate?, empty: Boolean) {
            super.updateItem(item, empty)
            text = if (empty || item == null) null else item.name
            tooltip = if (empty || item == null) null else Tooltip(item.description)
        }
    }

    fun imageModeCell() = object : ListCell<ImageMode>() {
        override fun updateItem(item: ImageMode?, empty: Boolean) {
            super.updateItem(item, empty)
            text = when {
                empty || item == null -> null
                item == ImageMode.COVER -> "Crop to Fill"
                item == ImageMode.CONTAIN -> "Fit + Pad"
                else -> "Stretch"
            }
        }
    }

    fun overlayCell() = object : ListCell<BackgroundOverlay>() {
        override fun updateItem(item: BackgroundOverlay?, empty: Boolean) {
            super.updateItem(item, empty)
            text = if (empty || item == null) null else item.name
            tooltip = if (empty || item == null) null else Tooltip(item.description)
        }
    }

    fun overlayPlacementCell() = object : ListCell<OverlayPlacement>() {
        override fun updateItem(item: OverlayPlacement?, empty: Boolean) {
            super.updateItem(item, empty)
            text = when {
                empty || item == null -> null
                item == OverlayPlacement.FRAMES_ONLY -> "Frames only"
                else -> "Over content"
            }
        }
    }

    fun helperLabel(text: String) = Label(text).apply {
        isWrapText = true
        textFill = Color.web("#8B949E")
        style = "-fx-font-size:12px;"
    }

    fun section(text: String) = Label(text).apply {
        style = "-fx-font-weight:bold;-fx-font-size:16px;-fx-padding:8 0 3 0;"
    }

    fun row(label: String, node: Node): HBox = HBox(8.0).apply {
        alignment = Pos.CENTER_LEFT
        children.add(Label(label).apply { minWidth = 112.0 })
        HBox.setHgrow(node, Priority.ALWAYS)
        children.add(node)
    }

    fun rowWithDice(label: String, node: Node, action: () -> Unit): HBox = HBox(6.0).apply {
        alignment = Pos.CENTER_LEFT
        children.add(Label(label).apply { minWidth = 112.0 })
        children.add(Button("⚄").apply {
            accessibleText = "Randomize $label"
            tooltip = Tooltip("Randomize $label")
            minWidth = 28.0
            maxWidth = 28.0
            setOnAction { action() }
        })
        HBox.setHgrow(node, Priority.ALWAYS)
        children.add(node)
    }

    fun sliderRow(label: String, slider: Slider, value: Label, format: String): HBox = HBox(8.0).apply {
        alignment = Pos.CENTER_LEFT
        children.add(Label(label).apply { minWidth = 112.0 })
        children.add(slider)
        children.add(value)
        HBox.setHgrow(slider, Priority.ALWAYS)
        updateValueLabel(value, slider.value, format)
    }

    fun updateValueLabel(label: Label, value: Double, format: String) {
        label.text = format.format(value)
    }
}

package com.example.cardforge

import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox

/**
 * Collection-scoped controls extracted from MainApp.
 *
 * This pane deliberately owns no database state. MainApp supplies the current presentation and
 * callbacks, while persistence stays in CollectionEditorActions/CollectionDatabase. That keeps
 * collection-wide controls independent from the selected card and prevents card changes from
 * accidentally overwriting collection settings.
 */
class CollectionSettingsPane(
    initial: CollectionPresentation,
    private val onPresentationChanged: (CollectionPresentation) -> Unit,
    private val onApplyDefaultTemplateToAll: () -> Unit,
    private val onApplySetNameToAll: () -> Unit,
    private val onNormalizeCollectorTotals: () -> Unit
) : VBox(8.0) {
    private var suppress = false
    private var presentation = initial.copy()

    private val descriptionHeading = TextField()
    private val artistCopyright = CheckBox("Show © before artist")
    private val bleedOpacity = Slider(0.0, 1.0, 1.0)
    private val foregroundOpacity = Slider(0.0, 1.0, 1.0)
    private val bleedValue = Label()
    private val foregroundValue = Label()
    private val cardCount = Label("0 cards").apply { styleClass.add("browser-meta") }

    init {
        StartupProfiler.installFxStallMonitor()
        descriptionHeading.promptText = "ABILITY / DESCRIPTION"
        descriptionHeading.tooltip = Tooltip("Heading used above the rules/description text on every card in this collection.")
        bleedOpacity.tooltip = Tooltip("Opacity of artwork only where it bleeds outside the image aperture. Bleed remains clipped inside the outer card frame.")
        foregroundOpacity.tooltip = Tooltip("Opacity of foreground card surfaces: title/type rails, description panel, and P/T box.")

        children.add(HBox(8.0, Label("Collection").apply { minWidth = 112.0 }, cardCount).apply {
            alignment = Pos.CENTER_LEFT
        })
        children.add(row("Description label", descriptionHeading))
        children.add(artistCopyright)
        children.add(sliderRow("Bleed opacity", bleedOpacity, bleedValue))
        children.add(sliderRow("Foreground opacity", foregroundOpacity, foregroundValue))

        val applyTemplateToAll = Button("Apply selected template to all cards").apply {
            maxWidth = Double.MAX_VALUE
            tooltip = Tooltip("Use the template currently selected in Card template as the collection default, and remove every per-card template override.")
            setOnAction { onApplyDefaultTemplateToAll() }
        }
        val applySetToAll = Button("Apply current set name to all cards").apply {
            maxWidth = Double.MAX_VALUE
            tooltip = Tooltip("Apply the selected card's set name to every image in this collection.")
            setOnAction { onApplySetNameToAll() }
        }
        val fixTotals = Button("Recalculate collector-number totals").apply {
            maxWidth = Double.MAX_VALUE
            tooltip = Tooltip("Set each card-number total to the number of images with the same set name.")
            setOnAction { onNormalizeCollectorTotals() }
        }
        children.add(VBox(6.0, applyTemplateToAll, applySetToAll, fixTotals))

        descriptionHeading.textProperty().addListener { _, _, value ->
            if (!suppress) publish { this.descriptionHeading = value.ifBlank { "ABILITY / DESCRIPTION" } }
        }
        artistCopyright.selectedProperty().addListener { _, _, value ->
            if (!suppress) publish { this.showArtistCopyright = value }
        }
        bleedOpacity.valueProperty().addListener { _, _, value ->
            updateValueLabels()
            if (!suppress) publish { this.bleedOpacity = value.toDouble().coerceIn(0.0, 1.0) }
        }
        foregroundOpacity.valueProperty().addListener { _, _, value ->
            updateValueLabels()
            if (!suppress) publish { this.foregroundOpacity = value.toDouble().coerceIn(0.0, 1.0) }
        }

        installReset(descriptionHeading) { descriptionHeading.text = "ABILITY / DESCRIPTION" }
        installReset(bleedOpacity, 1.0)
        installReset(foregroundOpacity, 1.0)
        setPresentation(initial)
    }

    fun setCardCount(total: Int) {
        cardCount.text = if (total == 1) "1 card" else "$total cards"
    }

    fun setPresentation(value: CollectionPresentation) {
        presentation = value.copy()
        suppress = true
        try {
            descriptionHeading.text = presentation.descriptionHeading.ifBlank { "ABILITY / DESCRIPTION" }
            artistCopyright.isSelected = presentation.showArtistCopyright
            bleedOpacity.value = presentation.bleedOpacity.coerceIn(0.0, 1.0)
            foregroundOpacity.value = presentation.foregroundOpacity.coerceIn(0.0, 1.0)
            updateValueLabels()
        } finally {
            suppress = false
        }
    }

    private fun publish(change: CollectionPresentation.() -> Unit) {
        presentation = presentation.copy().apply(change)
        onPresentationChanged(presentation.copy())
    }

    private fun updateValueLabels() {
        bleedValue.text = "%.0f%%".format(bleedOpacity.value * 100.0)
        foregroundValue.text = "%.0f%%".format(foregroundOpacity.value * 100.0)
    }

    private fun row(label: String, field: TextField) = HBox(8.0, Label(label).apply { minWidth = 112.0 }, field).apply {
        alignment = Pos.CENTER_LEFT
        HBox.setHgrow(field, Priority.ALWAYS)
    }

    private fun sliderRow(label: String, slider: Slider, value: Label) = HBox(8.0,
        Label(label).apply { minWidth = 112.0 }, slider, value
    ).apply {
        alignment = Pos.CENTER_LEFT
        HBox.setHgrow(slider, Priority.ALWAYS)
    }

    private fun installReset(field: TextField, reset: () -> Unit) {
        field.setOnMouseClicked { event -> if (event.clickCount == 2) reset() }
    }

    private fun installReset(slider: Slider, defaultValue: Double) {
        slider.setOnMouseClicked { event -> if (event.clickCount == 2) slider.value = defaultValue }
    }
}

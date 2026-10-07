package de.cetruo.desktop.ui

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox

class CardPreviewPane(
    initialShowGuides: Boolean,
    onInspectSource: () -> Unit,
    onSizeChanged: () -> Unit
) : VBox(8.0) {
    val host = StackPane()
    val showEditorGuides = CheckBox("Editor guides").apply {
        isSelected = initialShowGuides
        tooltip = Tooltip(
            "Show center alignment guides and drag/zoom help. Turn off to see the card exactly as it will print/export."
        )
    }

    init {
        host.alignment = Pos.CENTER
        host.styleClass.add("card-preview-host")
        host.widthProperty().addListener { _, _, _ -> onSizeChanged() }
        host.heightProperty().addListener { _, _, _ -> onSizeChanged() }

        val inspectSource = Button("⤢ Source").apply {
            tooltip = Tooltip("Inspect the original source image at native resolution (Cmd/Ctrl+I).")
            setOnAction { onInspectSource() }
        }
        val spacer = Region()
        val header = HBox(10.0, Label("Card Preview"), spacer, inspectSource, showEditorGuides).apply {
            alignment = Pos.CENTER_LEFT
            HBox.setHgrow(spacer, Priority.ALWAYS)
        }

        minWidth = 500.0
        prefWidth = 800.0
        alignment = Pos.TOP_CENTER
        padding = Insets(12.0)
        styleClass.add("card-preview-pane")
        children.addAll(header, host)
        VBox.setVgrow(host, Priority.ALWAYS)
    }
}

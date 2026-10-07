package de.cetruo.desktop.editor

import de.cetruo.desktop.ColorScheme
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox

class SchemeEditorPane(
    private val ui: EditorUiFactory,
    onRandomize: () -> Unit,
    onApply: () -> Unit,
    onFromImage: () -> Unit,
    onReload: () -> Unit,
    onSchemeChanged: (ColorScheme?, ColorScheme?) -> Unit
) : VBox(10.0) {
    val schemeChoice = ComboBox<ColorScheme>()

    init {
        children.add(ui.section("Scheme"))

        schemeChoice.setCellFactory { ui.schemeCell() }
        schemeChoice.buttonCell = ui.schemeCell()
        schemeChoice.valueProperty().addListener { _, old, value ->
            if (value != old) onSchemeChanged(old, value)
        }
        children.add(ui.rowWithDice("Color scheme", schemeChoice, onRandomize))

        children.add(
            HBox(8.0).apply {
                children.add(Button("Apply scheme").apply {
                    setOnAction { onApply() }
                    maxWidth = Double.MAX_VALUE
                    HBox.setHgrow(this, Priority.ALWAYS)
                })
                children.add(Button("From image").apply {
                    tooltip = Tooltip("Choose the closest coordinated color scheme from the dominant artwork color.")
                    setOnAction { onFromImage() }
                })
                children.add(Button("Reload schemes").apply {
                    setOnAction { onReload() }
                })
            }
        )

        children.add(
            ui.helperLabel(
                "Schemes are editable JSON files under schemes/. Each scheme includes a distinct card backgroundColor."
            )
        )
    }
}

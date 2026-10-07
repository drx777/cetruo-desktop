package de.cetruo.desktop.editor

import de.cetruo.desktop.CardStatus
import javafx.scene.control.ComboBox
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
import javafx.scene.layout.VBox

enum class MetadataField {
    TITLE,
    COST,
    TYPE_LINE,
    RARITY,
    STATS,
    ARTIST,
    SET_NAME,
    COLLECTOR_NUMBER
}

class CardMetadataPane(
    private val ui: EditorUiFactory,
    onChanged: () -> Unit,
    onRandomize: (MetadataField) -> Unit
) : VBox(10.0) {
    val fields = linkedMapOf<String, TextField>()
    val description = TextArea()
    val flavor = TextArea()
    val statusChoice = ComboBox<CardStatus>()

    init {
        children.add(ui.section("Card Metadata"))
        children.add(ui.rowWithDice("Title", textField("title", onChanged)) { onRandomize(MetadataField.TITLE) })
        children.add(ui.rowWithDice("Cost", textField("cost", onChanged)) { onRandomize(MetadataField.COST) })
        children.add(ui.rowWithDice("Type line", textField("typeLine", onChanged)) { onRandomize(MetadataField.TYPE_LINE) })
        children.add(ui.rowWithDice("Rarity", textField("rarity", onChanged)) { onRandomize(MetadataField.RARITY) })
        children.add(ui.rowWithDice("Stats", textField("stats", onChanged)) { onRandomize(MetadataField.STATS) })
        children.add(ui.rowWithDice("Artist", textField("artist", onChanged)) { onRandomize(MetadataField.ARTIST) })
        children.add(ui.rowWithDice("Set", textField("setName", onChanged)) { onRandomize(MetadataField.SET_NAME) })
        children.add(ui.rowWithDice("Number", textField("collectorNumber", onChanged)) {
            onRandomize(MetadataField.COLLECTOR_NUMBER)
        })

        statusChoice.items.setAll(CardStatus.entries)
        statusChoice.setCellFactory { ui.statusCell() }
        statusChoice.buttonCell = ui.statusCell()
        statusChoice.tooltip = Tooltip("Workflow state saved with the card.")
        statusChoice.valueProperty().addListener { _, _, value ->
            if (value != null) onChanged()
        }
        children.add(ui.row("Status", statusChoice))

        description.isWrapText = true
        description.prefRowCount = 4
        description.textProperty().addListener { _, _, _ -> onChanged() }

        flavor.isWrapText = true
        flavor.prefRowCount = 3
        flavor.textProperty().addListener { _, _, _ -> onChanged() }

        children.add(ui.row("Description", description))
        children.add(ui.row("Flavor", flavor))
    }

    private fun textField(key: String, onChanged: () -> Unit): TextField =
        TextField().also { field ->
            fields[key] = field
            field.textProperty().addListener { _, _, _ -> onChanged() }
        }
}

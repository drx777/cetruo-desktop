package com.example.cardforge

import javafx.geometry.Insets
import javafx.scene.control.ButtonBar
import javafx.scene.control.ButtonType
import javafx.scene.control.Dialog
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.control.Tooltip
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.Window
import java.nio.file.Path

/** Startup-only chooser for the five most recently opened collection roots. */
object StartupCatalogChooser {
    sealed interface Choice {
        data class Open(val path: Path) : Choice
        data object Browse : Choice
        data object Cancel : Choice
    }

    fun choose(owner: Window?, recent: List<Path>): Choice {
        if (recent.isEmpty()) return Choice.Browse

        val list = ListView<Path>().apply {
            items.setAll(recent.take(5))
            prefHeight = (items.size.coerceAtMost(5) * 48.0 + 8.0).coerceAtLeast(96.0)
            setCellFactory {
                object : ListCell<Path>() {
                    override fun updateItem(item: Path?, empty: Boolean) {
                        super.updateItem(item, empty)
                        if (empty || item == null) {
                            text = null
                            tooltip = null
                        } else {
                            text = item.fileName?.toString()?.ifBlank { item.toString() } ?: item.toString()
                            tooltip = Tooltip(item.toString())
                        }
                    }
                }
            }
            selectionModel.selectFirst()
        }

        val open = ButtonType("Open", ButtonBar.ButtonData.OK_DONE)
        val browse = ButtonType("Choose another…", ButtonBar.ButtonData.OTHER)
        val cancel = ButtonType.CANCEL
        val dialog = Dialog<Choice>().apply {
            title = "Open Card Forge collection"
            headerText = "Recently opened catalogs"
            if (owner != null) initOwner(owner)
            dialogPane.buttonTypes.setAll(open, browse, cancel)
            dialogPane.content = VBox(8.0,
                Label("Select a recent collection, or choose another directory."),
                list
            ).apply {
                padding = Insets(4.0)
                VBox.setVgrow(list, Priority.ALWAYS)
            }
            setResultConverter { button ->
                when (button) {
                    open -> list.selectionModel.selectedItem?.let(Choice::Open) ?: Choice.Browse
                    browse -> Choice.Browse
                    else -> Choice.Cancel
                }
            }
        }
        list.setOnMouseClicked { event ->
            if (event.clickCount == 2) {
                list.selectionModel.selectedItem?.let {
                    dialog.result = Choice.Open(it)
                    dialog.close()
                }
            }
        }
        return dialog.showAndWait().orElse(Choice.Cancel)
    }
}

package com.example.cardforge

import javafx.geometry.Insets
import javafx.scene.control.ButtonBar
import javafx.scene.control.ButtonType
import javafx.scene.control.Dialog
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.Window
import java.nio.file.Path

/** Startup-only chooser for the five most recently opened collection roots. */
object StartupCatalogChooser {
    fun choose(owner: Window?, recent: List<Path>): Path? {
        if (recent.isEmpty()) return null

        val list = ListView<Path>().apply {
            items.setAll(recent)
            prefHeight = (recent.size.coerceAtMost(5) * 48.0 + 8.0).coerceAtLeast(96.0)
            setCellFactory {
                object : ListCell<Path>() {
                    override fun updateItem(item: Path?, empty: Boolean) {
                        super.updateItem(item, empty)
                        if (empty || item == null) {
                            text = null
                            tooltip = null
                        } else {
                            text = item.fileName?.toString()?.ifBlank { item.toString() } ?: item.toString()
                            tooltip = javafx.scene.control.Tooltip(item.toString())
                        }
                    }
                }
            }
            selectionModel.selectFirst()
        }

        val open = ButtonType("Open", ButtonBar.ButtonData.OK_DONE)
        val browse = ButtonType("Choose another…", ButtonBar.ButtonData.OTHER)
        val cancel = ButtonType.CANCEL
        val dialog = Dialog<Path?>().apply {
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
                    open -> list.selectionModel.selectedItem
                    else -> null
                }
            }
        }
        list.setOnMouseClicked { event ->
            if (event.clickCount == 2 && list.selectionModel.selectedItem != null) {
                dialog.result = list.selectionModel.selectedItem
                dialog.close()
            }
        }
        return dialog.showAndWait().orElse(null)
    }
}

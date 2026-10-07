package de.cetruo.desktop

import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.control.Pagination
import javafx.scene.control.ScrollPane
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.BorderPane
import javafx.scene.layout.GridPane
import javafx.scene.layout.StackPane
import javafx.stage.Stage
import javafx.util.Callback
import java.nio.file.Path

/** Window and paging concerns for the in-app rendered-card contact sheet. */
class ContactSheetWindow(
    private val resourceOwner: Class<*>,
    private val isDarkTheme: () -> Boolean,
    private val requestPreview: (Path, (Image?) -> Unit) -> Unit
) {
    fun show(owner: Stage, paths: List<Path>) {
        if (paths.isEmpty()) return

        val perPage = 6
        val pageCount = ((paths.size + perPage - 1) / perPage).coerceAtLeast(1)
        val pagination = Pagination(pageCount, 0).apply {
            maxPageIndicatorCount = 9
            pageFactory = Callback { pageIndex: Int ->
                createPage(paths.drop(pageIndex * perPage).take(perPage))
            }
        }

        val root = BorderPane(pagination).apply {
            padding = Insets(14.0)
            style = if (isDarkTheme()) {
                "-fx-background-color:#20242A;"
            } else {
                "-fx-background-color:#F6F7F9;"
            }
        }
        val scene = Scene(root, 1040.0, 820.0)
        AppPlatform.attachStylesheet(scene, resourceOwner)

        Stage().apply {
            initOwner(owner)
            title = "Card Forge · Contact Sheet"
            owner.icons.firstOrNull()?.let { icons.add(it) }
            this.scene = scene
            show()
        }
    }

    private fun createPage(paths: List<Path>): Node {
        val grid = GridPane().apply {
            hgap = 18.0
            vgap = 18.0
            alignment = Pos.CENTER
        }

        paths.forEachIndexed { index, path ->
            val slot = StackPane().apply {
                prefWidth = 310.0
                prefHeight = 370.0
                minWidth = 310.0
                minHeight = 370.0
                style = "-fx-background-color:rgba(255,255,255,0.035);-fx-background-radius:12px;"
            }
            slot.children.add(Label("Rendering…").apply {
                style = "-fx-text-fill:#AEB7C2;-fx-font-size:12px;"
            })
            grid.add(slot, index % 3, index / 3)

            Platform.runLater {
                requestPreview(path) { preview ->
                    slot.children.clear()
                    if (preview != null) {
                        slot.children.add(ImageView(preview).apply {
                            fitWidth = 285.0
                            fitHeight = 345.0
                            isPreserveRatio = true
                            isSmooth = true
                        })
                    } else {
                        slot.children.add(Label("Preview unavailable").apply {
                            style = "-fx-text-fill:#AEB7C2;-fx-font-size:12px;"
                        })
                    }
                }
            }
        }

        return ScrollPane(StackPane(grid)).apply {
            isFitToWidth = true
            isFitToHeight = true
            style = "-fx-background-color:transparent;"
        }
    }
}

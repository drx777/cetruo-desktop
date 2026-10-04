package com.example.cardforge

import javafx.application.Platform
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import java.nio.file.Path

/**
 * Coordinates selection and viewport anchoring between the browser's list and grid views.
 *
 * Card/image state remains owned by MainApp; this class only maps a selected Path to the
 * appropriate JavaFX list row and keeps scrolling behavior consistent between both modes.
 */
class BrowserSelectionCoordinator<G>(
    private val listView: ListView<Path>,
    private val gridView: ListView<G>,
    private val visiblePaths: () -> List<Path>,
    private val isGridMode: () -> Boolean,
    private val columns: () -> Int,
    private val firstPathInGridRow: (G) -> Path?
) {
    fun activeList(): ListView<*> = if (isGridMode()) gridView else listView

    fun refresh() {
        listView.refresh()
        gridView.refresh()
    }

    fun scrollToPath(path: Path?, force: Boolean) {
        if (path == null) return
        val paths = visiblePaths()
        val index = paths.indexOf(path)
        if (index !in paths.indices) return

        val view = activeList()
        val row = if (isGridMode()) index / columns().coerceAtLeast(1) else index
        Platform.runLater {
            if (force) {
                view.scrollTo(row)
                return@runLater
            }

            val cells = view.lookupAll(".list-cell").filterIsInstance<ListCell<*>>()
            val target = cells.firstOrNull { it.index == row }
            if (target == null) {
                view.scrollTo(row)
                return@runLater
            }

            val viewportTop = view.localToScene(0.0, 0.0).y
            val viewportBottom = viewportTop + view.height
            val cellTop = target.localToScene(0.0, 0.0).y
            val cellBottom = cellTop + target.height
            if (cellTop < viewportTop || cellBottom > viewportBottom) {
                view.scrollTo(row)
            }
        }
    }

    fun firstVisiblePath(view: ListView<*>, grid: Boolean): Path? {
        val cells = view.lookupAll(".list-cell").filterIsInstance<ListCell<*>>()
        if (cells.isEmpty()) return null

        val viewportTop = view.localToScene(0.0, 0.0).y
        val cell = cells
            .filter { it.index >= 0 && it.localToScene(0.0, 0.0).y + it.height >= viewportTop }
            .minByOrNull { it.localToScene(0.0, 0.0).y }
            ?: return null

        val item = cell.item
        return if (grid) {
            @Suppress("UNCHECKED_CAST")
            firstPathInGridRow(item as G)
        } else {
            item as? Path
        }
    }

    fun syncSelection(path: Path?, forceScroll: Boolean) {
        if (path == null) return
        val paths = visiblePaths()
        val index = paths.indexOf(path)
        if (index !in paths.indices) return

        if (!isGridMode()) {
            listView.selectionModel.select(path)
            listView.focusModel.focus(index)
        } else {
            val row = index / columns().coerceAtLeast(1)
            gridView.selectionModel.select(row)
            gridView.focusModel.focus(row)
        }
        scrollToPath(path, forceScroll)
    }
}

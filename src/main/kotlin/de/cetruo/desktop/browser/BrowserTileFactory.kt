package de.cetruo.desktop.browser

import de.cetruo.desktop.*
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Cursor
import javafx.scene.Node
import javafx.scene.control.ContextMenu
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import java.nio.file.Path
import java.util.WeakHashMap

data class GridRow(val paths: List<Path>)

/**
 * Builds browser list/grid cells and tiles.
 *
 * Preview loading, card data lookup, selection state, and context-menu actions are supplied by
 * MainApp so this class remains a view factory rather than another owner of browser state.
 */
class BrowserTileFactory(
    private val requestPreview: (Path, (Image?) -> Unit) -> Unit,
    private val cardTitle: (Path) -> String,
    private val relativeFolder: (Path) -> String,
    private val currentPath: () -> Path?,
    private val contextMenuFor: (Path) -> ContextMenu,
    private val onListSelected: (Path) -> Unit,
    private val onGridSelected: (Path) -> Unit
) {
    private data class TileState(
        val path: Path,
        val thumbnail: Boolean,
        val previewView: ImageView,
        val loadingLabel: Label,
        val cardNameLabel: Label,
        var hovered: Boolean = false
    )

    private val liveTiles = WeakHashMap<VBox, TileState>()

    /**
     * Selection-only updates must not rebuild ListView cells: rebuilding them re-requests
     * thumbnails and causes visible flicker. Restyle the currently live tile nodes instead.
     */
    fun refreshSelectionStyles() {
        liveTiles.forEach { (tile, state) ->
            tile.style = tileStyle(state.path == currentPath(), state.hovered, state.thumbnail)
        }
    }

    /**
     * Refresh only the currently live tile(s) for one card. This avoids rebuilding the
     * entire ListView/GridView when a single edited card's rendered preview becomes stale.
     */
    fun refreshPath(path: Path) {
        val normalized = path.toAbsolutePath().normalize()
        liveTiles.forEach { (_, state) ->
            if (state.path.toAbsolutePath().normalize() != normalized) return@forEach

            state.cardNameLabel.text = cardTitle(state.path).ifBlank { "Untitled card" }
            // Keep the previous preview visible while the replacement renders.
            requestPreview(state.path) { image ->
                state.previewView.image = image
                state.loadingLabel.text = when {
                    image != null -> ""
                    state.thumbnail -> "Preview unavailable"
                    else -> "?"
                }
                state.loadingLabel.isVisible = image == null
                state.loadingLabel.isManaged = image == null
            }
        }
    }

    fun listCell() = object : ListCell<Path>() {
        override fun updateItem(item: Path?, empty: Boolean) {
            super.updateItem(item, empty)
            style = CELL_STYLE
            graphic = if (empty || item == null) null else createListTile(item)
            text = null
            isFocusTraversable = false
        }
    }

    fun gridCell() = object : ListCell<GridRow>() {
        override fun updateItem(item: GridRow?, empty: Boolean) {
            super.updateItem(item, empty)
            style = CELL_STYLE
            graphic = if (empty || item == null) null else createThumbnailRow(item.paths)
            text = null
            isFocusTraversable = false
        }
    }

    private fun createThumbnailRow(paths: List<Path>): HBox = HBox(10.0).apply {
        alignment = Pos.TOP_LEFT
        padding = Insets(2.0, 6.0, 6.0, 6.0)
        prefHeight = 186.0
        minHeight = 186.0
        maxHeight = 186.0
        isFillHeight = false
        paths.forEach { path -> children.add(createImageTile(path, thumbnail = true)) }
    }

    private fun createListTile(path: Path): Node {
        val tile = VBox(2.0).apply {
            maxWidth = Double.MAX_VALUE
            padding = Insets(6.0, 8.0, 6.0, 8.0)
            cursor = Cursor.HAND
            isFocusTraversable = false
        }
        val preview = ImageView().apply {
            fitWidth = 64.0
            fitHeight = 64.0
            isPreserveRatio = true
            isSmooth = true
            isMouseTransparent = true
        }
        val previewBox = StackPane(preview).apply {
            minWidth = 64.0
            prefWidth = 64.0
            maxWidth = 64.0
            minHeight = 64.0
            prefHeight = 64.0
            maxHeight = 64.0
            styleClass.add("browser-preview-box")
        }
        val loading = Label("…").apply {
            styleClass.add("browser-loading")
            isMouseTransparent = true
        }
        previewBox.children.add(loading)
        requestPreview(path) { image ->
            preview.image = image
            loading.text = if (image == null) "?" else ""
            loading.isVisible = image == null
            loading.isManaged = image == null
        }

        val cardName = Label(cardTitle(path).ifBlank { "Untitled card" }).apply {
            styleClass.add("browser-card-name")
            style = "-fx-font-size:13px;-fx-font-weight:bold;"
            textOverrun = javafx.scene.control.OverrunStyle.ELLIPSIS
            maxWidth = Double.MAX_VALUE
            isMouseTransparent = true
        }
        val name = Label(path.fileName.toString()).apply {
            styleClass.add("browser-filename")
            style = "-fx-font-size:11px;"
            textOverrun = javafx.scene.control.OverrunStyle.ELLIPSIS
            maxWidth = Double.MAX_VALUE
            isMouseTransparent = true
        }
        val folder = Label(relativeFolder(path.parent ?: path)).apply {
            styleClass.add("browser-folder")
            style = "-fx-font-size:10px;"
            textOverrun = javafx.scene.control.OverrunStyle.ELLIPSIS
            maxWidth = Double.MAX_VALUE
            isMouseTransparent = true
        }
        val text = VBox(2.0, cardName, name, folder).apply {
            alignment = Pos.CENTER_LEFT
            maxWidth = Double.MAX_VALUE
        }
        HBox.setHgrow(text, Priority.ALWAYS)
        tile.children.add(HBox(10.0, previewBox, text).apply { alignment = Pos.CENTER_LEFT })

        installInteractions(
            tile = tile,
            path = path,
            thumbnail = false,
            previewView = preview,
            loadingLabel = loading,
            cardNameLabel = cardName,
            onSelected = onListSelected
        )
        return tile
    }

    private fun createImageTile(path: Path, thumbnail: Boolean): VBox {
        val tile = VBox(4.0).apply {
            prefWidth = 156.0
            minWidth = 156.0
            maxWidth = 156.0
            alignment = Pos.TOP_CENTER
            padding = Insets(6.0)
            cursor = Cursor.HAND
            isFocusTraversable = false
        }
        val thumbView = ImageView().apply {
            fitWidth = 132.0
            fitHeight = 132.0
            isPreserveRatio = true
            isSmooth = true
            isMouseTransparent = true
        }
        val imageBox = StackPane().apply {
            prefWidth = 132.0
            prefHeight = 132.0
            minWidth = 132.0
            minHeight = 132.0
            styleClass.add("browser-preview-box")
            children.add(thumbView)
        }
        val loadingLabel = Label("Loading…").apply {
            styleClass.add("browser-loading")
            isMouseTransparent = true
        }
        imageBox.children.add(loadingLabel)

        val cardName = Label(cardTitle(path).ifBlank { "Untitled card" }).apply {
            styleClass.add("browser-card-name")
            maxWidth = 144.0
            isWrapText = true
            alignment = Pos.TOP_CENTER
            style = "-fx-font-size:11px;-fx-font-weight:bold;"
            isMouseTransparent = true
        }
        val name = Label(path.fileName.toString()).apply {
            styleClass.add("browser-filename")
            maxWidth = 144.0
            isWrapText = true
            alignment = Pos.TOP_CENTER
            textOverrun = javafx.scene.control.OverrunStyle.ELLIPSIS
            style = "-fx-font-size:9px;"
            isMouseTransparent = true
        }
        tile.children.addAll(imageBox, cardName, name)

        requestPreview(path) { image ->
            thumbView.image = image
            loadingLabel.text = if (image == null) "Preview unavailable" else ""
            loadingLabel.isVisible = image == null
            loadingLabel.isManaged = image == null
        }

        installInteractions(
            tile = tile,
            path = path,
            thumbnail = thumbnail,
            previewView = thumbView,
            loadingLabel = loadingLabel,
            cardNameLabel = cardName,
            onSelected = onGridSelected
        )
        return tile
    }

    private fun installInteractions(
        tile: VBox,
        path: Path,
        thumbnail: Boolean,
        previewView: ImageView,
        loadingLabel: Label,
        cardNameLabel: Label,
        onSelected: (Path) -> Unit
    ) {
        val state = TileState(
            path = path,
            thumbnail = thumbnail,
            previewView = previewView,
            loadingLabel = loadingLabel,
            cardNameLabel = cardNameLabel
        )
        liveTiles[tile] = state

        tile.setOnContextMenuRequested { event ->
            contextMenuFor(path).show(tile, event.screenX, event.screenY)
            event.consume()
        }
        tile.addEventFilter(MouseEvent.MOUSE_PRESSED) { event ->
            if (event.button == MouseButton.PRIMARY) {
                event.consume()
                onSelected(path)
            }
        }

        fun applyStyle() {
            tile.style = tileStyle(path == currentPath(), state.hovered, thumbnail)
        }
        applyStyle()
        tile.setOnMouseEntered {
            state.hovered = true
            applyStyle()
        }
        tile.setOnMouseExited {
            state.hovered = false
            applyStyle()
        }
    }

    private fun tileStyle(selected: Boolean, hovered: Boolean, thumbnail: Boolean): String {
        val radius = if (thumbnail) 8 else 6
        val border = "-fx-border-radius:${radius}px;-fx-border-width:1.5px;"
        return when {
            selected -> "-fx-background-color:rgba(88,166,255,0.11);-fx-background-radius:${radius}px;-fx-border-color:#58A6FF;$border"
            hovered -> "-fx-background-color:rgba(255,255,255,0.05);-fx-background-radius:${radius}px;-fx-border-color:transparent;$border"
            else -> "-fx-background-color:transparent;-fx-border-color:transparent;$border"
        }
    }

    private companion object {
        const val CELL_STYLE =
            "-fx-background-color:transparent;-fx-control-inner-background:transparent;" +
                "-fx-selection-bar:transparent;-fx-selection-bar-non-focused:transparent;-fx-padding:2px;"
    }
}

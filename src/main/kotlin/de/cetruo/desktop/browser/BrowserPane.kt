package de.cetruo.desktop.browser

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.control.TextField
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.scene.control.Tooltip
import javafx.scene.control.TreeView
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import java.nio.file.Path
import kotlin.math.floor

enum class ImageBrowserMode {
    LIST,
    THUMBNAILS
}

enum class BrowserPreviewMode {
    ORIGINAL,
    CARD
}

data class CollectionOption(
    val root: Path,
    val label: String
)

class BrowserPane(
    initialMode: ImageBrowserMode,
    initialPreviewMode: BrowserPreviewMode,
    initialSort: BrowserSort,
    initialSortDescending: Boolean
) : VBox(10.0) {
    val folderTree = TreeView<Path>()
    val imageList = ListView<Path>()
    val gridList = ListView<GridRow>()
    val filterField = TextField()
    val collectionChoice = ComboBox<CollectionOption>()
    val countLabel = Label("0 images")
    val sortChoice = ComboBox<BrowserSort>()
    val sortDescending = CheckBox("Descending")

    var onFilterChanged: () -> Unit = {}
    var onModeRequested: (ImageBrowserMode) -> Unit = {}
    var onPreviewModeRequested: (BrowserPreviewMode) -> Unit = {}
    var onCollectionSelected: (CollectionOption) -> Unit = {}
    var onSortChanged: (BrowserSort) -> Unit = {}
    var onSortDescendingChanged: (Boolean) -> Unit = {}
    var onFolderSelected: (Path) -> Unit = {}
    var onBrowserFocused: () -> Unit = {}
    var onGridWidthChanged: () -> Unit = {}

    private val modeToggleGroup = ToggleGroup()
    private val previewToggleGroup = ToggleGroup()
    private val listToggle = ToggleButton("☷")
    private val gridToggle = ToggleButton("▦")
    private val originalToggle = ToggleButton("◎")
    private val cardToggle = ToggleButton("▣")
    private val browserStack = StackPane(imageList, gridList)

    init {
        filterField.promptText = "Filter cards, images and folders…"
        filterField.styleClass.add("browser-filter")
        filterField.tooltip = Tooltip("Search across filenames, folders, IDs, status and every stored card property.")
        filterField.textProperty().addListener { _, _, _ -> onFilterChanged() }

        configureModeToggle(listToggle, modeToggleGroup, "List view") {
            onModeRequested(ImageBrowserMode.LIST)
        }
        configureModeToggle(gridToggle, modeToggleGroup, "Thumbnail grid") {
            onModeRequested(ImageBrowserMode.THUMBNAILS)
        }
        configureModeToggle(originalToggle, previewToggleGroup, "Original image previews") {
            onPreviewModeRequested(BrowserPreviewMode.ORIGINAL)
        }
        configureModeToggle(cardToggle, previewToggleGroup, "Card previews") {
            onPreviewModeRequested(BrowserPreviewMode.CARD)
        }

        collectionChoice.apply {
            setCellFactory { collectionOptionCell() }
            buttonCell = collectionOptionCell()
            prefWidth = 260.0
            tooltip = Tooltip("Switch between the selected collection and collections discovered in its subfolders.")
            valueProperty().addListener { _, old, value ->
                if (value != null && value != old) onCollectionSelected(value)
            }
        }

        sortChoice.apply {
            items.setAll(BrowserSort.entries)
            value = initialSort
            setCellFactory { browserSortCell() }
            buttonCell = browserSortCell()
            tooltip = Tooltip("Sort visible cards by title, last word, card number, folder, status, or filename.")
            valueProperty().addListener { _, old, value ->
                if (value != null && value != old) onSortChanged(value)
            }
        }

        sortDescending.apply {
            isSelected = initialSortDescending
            tooltip = Tooltip("Reverse the current card ordering.")
            selectedProperty().addListener { _, _, value -> onSortDescendingChanged(value) }
        }

        countLabel.styleClass.add("browser-meta")
        folderTree.apply {
            isShowRoot = true
            prefHeight = 190.0
            selectionModel.selectedItemProperty().addListener { _, _, item ->
                item?.value?.let(onFolderSelected)
            }
        }

        imageList.apply {
            placeholder = Label("No images match the current filter.")
            styleClass.add("image-browser-list")
            isFocusTraversable = true
            focusedProperty().addListener { _, _, focused -> if (focused) onBrowserFocused() }
        }
        gridList.apply {
            placeholder = Label("No images match the current filter.")
            styleClass.add("image-browser-grid")
            isFocusTraversable = true
            focusedProperty().addListener { _, _, focused -> if (focused) onBrowserFocused() }
            widthProperty().addListener { _, _, width ->
                if (width.toDouble() > 0.0) onGridWidthChanged()
            }
        }

        browserStack.apply {
            minHeight = 220.0
            maxWidth = Double.MAX_VALUE
        }

        val tools = VBox(
            6.0,
            HBox(8.0, Label("Collection"), collectionChoice).apply {
                alignment = Pos.CENTER_LEFT
            },
            HBox(
                8.0,
                filterField,
                HBox(2.0, listToggle, gridToggle),
                HBox(2.0, originalToggle, cardToggle)
            ).apply {
                alignment = Pos.CENTER_LEFT
                HBox.setHgrow(filterField, Priority.ALWAYS)
            },
            HBox(8.0, Label("Sort"), sortChoice, sortDescending).apply {
                alignment = Pos.CENTER_LEFT
            }
        )
        val header = HBox(8.0, Label("Images"), countLabel).apply {
            alignment = Pos.CENTER_LEFT
        }

        padding = Insets(12.0)
        minWidth = 300.0
        prefWidth = 470.0
        maxWidth = Double.MAX_VALUE
        children.addAll(tools, header, folderTree, browserStack)
        VBox.setVgrow(browserStack, Priority.ALWAYS)

        syncMode(initialMode)
        syncPreviewMode(initialPreviewMode)
    }

    fun configureCells(
        listCellFactory: () -> ListCell<Path>,
        gridCellFactory: () -> ListCell<GridRow>
    ) {
        imageList.setCellFactory { listCellFactory() }
        gridList.setCellFactory { gridCellFactory() }
    }

    fun syncMode(mode: ImageBrowserMode) {
        listToggle.isSelected = mode == ImageBrowserMode.LIST
        gridToggle.isSelected = mode == ImageBrowserMode.THUMBNAILS
        imageList.isVisible = mode == ImageBrowserMode.LIST
        imageList.isManaged = mode == ImageBrowserMode.LIST
        gridList.isVisible = mode == ImageBrowserMode.THUMBNAILS
        gridList.isManaged = mode == ImageBrowserMode.THUMBNAILS
    }

    fun syncPreviewMode(mode: BrowserPreviewMode) {
        originalToggle.isSelected = mode == BrowserPreviewMode.ORIGINAL
        cardToggle.isSelected = mode == BrowserPreviewMode.CARD
    }

    fun calculateColumns(): Int {
        val available = gridList.width - 36.0
        return floor((available + 10.0) / 166.0).toInt().coerceAtLeast(1)
    }

    private fun configureModeToggle(
        button: ToggleButton,
        group: ToggleGroup,
        description: String,
        action: () -> Unit
    ) {
        button.toggleGroup = group
        button.accessibleText = description
        button.tooltip = Tooltip(description)
        button.setOnAction { action() }
    }

    private fun browserSortCell() = object : ListCell<BrowserSort>() {
        override fun updateItem(item: BrowserSort?, empty: Boolean) {
            super.updateItem(item, empty)
            text = when {
                empty || item == null -> null
                item == BrowserSort.NAME -> "Name"
                item == BrowserSort.LAST_WORD -> "Last word"
                item == BrowserSort.COLLECTOR_NUMBER -> "Card number"
                item == BrowserSort.FOLDER -> "Folder"
                item == BrowserSort.STATUS -> "Status"
                item == BrowserSort.FILE_NAME -> "File name"
                else -> "Unknown"
            }
        }
    }

    private fun collectionOptionCell() = object : ListCell<CollectionOption>() {
        override fun updateItem(item: CollectionOption?, empty: Boolean) {
            super.updateItem(item, empty)
            text = if (empty || item == null) null else item.label
            tooltip = if (empty || item == null) null else Tooltip(item.root.toString())
        }
    }
}

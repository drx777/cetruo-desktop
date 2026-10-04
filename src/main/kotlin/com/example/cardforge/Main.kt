package com.example.cardforge

import javafx.animation.PauseTransition
import javafx.application.Application
import javafx.application.Platform
import javafx.concurrent.Task
import javafx.embed.swing.SwingFXUtils
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Cursor
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.CheckBox
import javafx.scene.control.ColorPicker
import javafx.scene.control.ComboBox
import javafx.scene.control.ContextMenu
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.control.MenuItem
import javafx.scene.control.ScrollPane
import javafx.scene.control.Separator
import javafx.scene.control.Slider
import javafx.scene.control.Spinner
import javafx.scene.control.SpinnerValueFactory
import javafx.scene.control.SplitPane
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.control.TextInputControl
import javafx.scene.control.ToolBar
import javafx.scene.control.Tooltip
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.scene.control.Dialog
import javafx.scene.control.Pagination
import javafx.scene.control.TreeCell
import javafx.scene.control.TreeItem
import javafx.scene.control.TreeView
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyEvent
import javafx.scene.input.KeyCombination
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import javafx.stage.Screen
import javafx.stage.Stage
import javafx.util.Duration
import java.awt.Desktop
import java.awt.Taskbar
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.nio.file.attribute.BasicFileAttributes
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.prefs.Preferences
import java.util.UUID
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.max
import kotlin.math.min

private enum class ImageBrowserMode {
    LIST,
    THUMBNAILS
}

private enum class UiTheme {
    LIGHT, DARK
}

private enum class BrowserPreviewMode {
    ORIGINAL, CARD
}

private data class CollectionOption(val root: Path, val label: String)

private enum class BrowserSort {
    NAME,
    LAST_WORD,
    COLLECTOR_NUMBER,
    FOLDER,
    STATUS,
    FILE_NAME
}

private data class GridRow(val paths: List<Path>)

class MainApp : Application() {
    private val supportedExtensions = setOf("jpg", "jpeg", "png", "gif")
    private val allImages = mutableListOf<Path>()
    private val visibleImages = mutableListOf<Path>()
    private var currentIndex = -1
    private var currentLoadedPath: Path? = null
    private var currentData = CardData()
    private val undoManager = CardUndoManager(100)
    private var suppressUndoCapture = false
    private var ignoreNextUndoCapture = false
    private var collectionRoot: Path? = null
    private var database: CollectionDatabase? = null
    private var cropImage: Image? = null
    private var backgroundOverlayImage: Image? = null
    private var templateImage: Image? = null
    private var renderedCard: CardRenderer.Rendered? = null
    private var suppressEditorUpdates = false
    private var suppressFolderSelection = false
    private var nestedCollectionsSkipped = 0
    private var schemes: List<ColorScheme> = emptyList()
    private var overlays: List<BackgroundOverlay> = emptyList()
    private var templates: List<CardTemplate> = emptyList()
    private var imageBrowserMode = ImageBrowserMode.LIST
    private var browserPreviewMode = BrowserPreviewMode.ORIGINAL
    private val showEditorGuides = CheckBox("Editor guides").apply {
        isSelected = Preferences.userNodeForPackage(MainApp::class.java).getBoolean("showEditorGuides", false)
        tooltip = Tooltip("Show center alignment guides and drag/zoom help. Turn off to see the card exactly as it will print/export.")
    }
    private var selectedFolder: Path? = null
    private var collectionDefaultTemplateName: String = ""
    private var collectionPresentation = CollectionPresentation()
    private var nestedCollectionRoots: List<Path> = emptyList()
    private var suppressCollectionChoice = false
    private var browserColumns = 1
    private val searchIndex = mutableMapOf<String, String>()
    private val cardDataCache = mutableMapOf<Path, CardData>()
    private lateinit var uiThemeButton: Button
    private var uiTheme: UiTheme = runCatching {
        UiTheme.valueOf(Preferences.userNodeForPackage(MainApp::class.java).get("uiTheme", UiTheme.DARK.name))
    }.getOrDefault(UiTheme.DARK)
    private lateinit var scene: Scene
    private lateinit var appRoot: BorderPane
    private var watchService: WatchService? = null
    private var watchThread: Thread? = null
    private val watchScanExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "card-forge-watch-scan").apply { isDaemon = true }
    }
    private val watchScanScheduled = AtomicBoolean(false)
    private val randomSequence = AtomicLong()

    private data class CachedImage(val size: Long, val modified: Long, val image: Image)
    private val thumbnailCache = object : LinkedHashMap<Path, CachedImage>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Path, CachedImage>?): Boolean = size > 1200
    }
    private val fullImageCache = object : LinkedHashMap<Path, CachedImage>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Path, CachedImage>?): Boolean = size > 32
    }
    private val thumbnailLoadExecutor: ExecutorService = Executors.newFixedThreadPool(4) { runnable ->
        Thread(runnable, "card-forge-thumbnail").apply { isDaemon = true }
    }
    private val thumbnailLoads = ConcurrentHashMap.newKeySet<Path>()
    private val thumbnailWaiters = ConcurrentHashMap<Path, CopyOnWriteArrayList<(Image?) -> Unit>>()
    private val thumbnailLastFailure = ConcurrentHashMap<Path, Long>()
    private val thumbnailRefreshPause = PauseTransition(Duration.millis(75.0))
    private data class CardPreviewEntry(val image: Image, val sourceSize: Long, val sourceModified: Long)
    private val cardThumbnailCache = object : LinkedHashMap<Path, CardPreviewEntry>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Path, CardPreviewEntry>?): Boolean = size > 240
    }
    private val cardThumbnailLoads = ConcurrentHashMap.newKeySet<Path>()
    private val cardThumbnailWaiters = ConcurrentHashMap<Path, CopyOnWriteArrayList<(Image?) -> Unit>>()

    private val folderTree = TreeView<Path>()
    private val imageList = ListView<Path>()
    private val gridList = ListView<GridRow>()
    private lateinit var browserStack: StackPane
    private val filterField = TextField()
    private val collectionChoice = ComboBox<CollectionOption>()
    private lateinit var browserModeToggleGroup: ToggleGroup
    private lateinit var browserPreviewToggleGroup: ToggleGroup
    private lateinit var browserListToggle: ToggleButton
    private lateinit var browserGridToggle: ToggleButton
    private lateinit var browserOriginalToggle: ToggleButton
    private lateinit var browserCardToggle: ToggleButton
    private val browserCountLabel = Label("0 images")
    private val browserSortChoice = ComboBox<BrowserSort>()
    private val browserSortDescending = CheckBox("Descending")
    private val previewHost = StackPane()
    private val statusBarLabel = Label("Open an image directory.")
    private val fields = linkedMapOf<String, TextField>()
    private val description = TextArea()
    private val flavor = TextArea()
    private val imageMode = ComboBox<ImageMode>()
    private val imageBleedOverFrame = CheckBox("Artwork bleeds over frame")
    private val imageBleedOpacity = Slider(0.0, 1.0, 1.0)
    private val imageBleedOpacityValue = Label("100%")
    private val statusChoice = ComboBox<CardStatus>()
    private lateinit var collectionSettingsPane: CollectionSettingsPane
    private val schemeChoice = ComboBox<ColorScheme>()
    private val templateChoice = ComboBox<CardTemplate>()
    private val collectionTemplateChoice = ComboBox<CardTemplate>()
    private val templateOverride = CheckBox("Override for this card")
    private val backgroundOverlayChoice = ComboBox<BackgroundOverlay>()
    private val overlayPlacementChoice = ComboBox<OverlayPlacement>()
    private val zoom = Slider(0.1, 4.0, 1.0).apply { blockIncrement = 0.1; majorTickUnit = 1.0 }
    private val offsetX = Slider(-1.0, 1.0, 0.0).apply { blockIncrement = 0.1 }
    private val offsetY = Slider(-1.0, 1.0, 0.0).apply { blockIncrement = 0.1 }
    private val imagePadColor = ColorPicker(Color.web("#0A0D10"))
    private val backgroundColor = ColorPicker(Color.web("#161B22"))
    private val panelColor = ColorPicker(Color.web("#EFE8D7"))
    private val frameColor = ColorPicker(Color.web("#D9C28E"))
    private val accentColor = ColorPicker(Color.web("#8C8068"))
    private val overlayColor = ColorPicker(Color.web("#C9B37A"))
    private val border = doubleSpinner(0.0, 20.0, 8.0, 0.5)
    private val radius = doubleSpinner(0.0, 80.0, 24.0, 1.0)
    private val panelOpacity = Slider(0.1, 1.0, 0.96)
    private val overlayOpacity = Slider(0.0, 1.0, 1.0)
    private val titleSize = doubleSpinner(14.0, 42.0, 27.0, 1.0)
    private val bodySize = doubleSpinner(10.0, 24.0, 16.0, 1.0)
    private val xValueLabel = Label("0 px")
    private val yValueLabel = Label("0 px")
    private val zoomValueLabel = Label("1.00×")
    private val filterApplyPause = PauseTransition(Duration.millis(180.0))
    private val generation = AtomicInteger()
    private var scanTask: Task<ScanResult>? = null
    private var filterTask: Task<List<Path>>? = null

    override fun start(stage: Stage) {
        stage.title = "Card Forge"
        AppPlatform.setApplicationDockIcon(javaClass)
        AppPlatform.installWindowIcon(stage, javaClass)
        loadSchemes()
        loadOverlays()
        loadTemplates()

        val mainSplit = SplitPane(browser(), previewPane()).apply {
            setDividerPosition(0, 0.34)
        }
        appRoot = BorderPane().apply {
            styleClass.add("cardforge-root")
            top = toolbar(stage)
            center = mainSplit
            right = editor()
            bottom = statusBar()
        }
        applyUiTheme()

        val initialWindow = AppPlatform.initialWindowSize()
        scene = Scene(appRoot, initialWindow.width, initialWindow.height)
        stage.minWidth = 1120.0
        stage.minHeight = 720.0
        AppPlatform.attachStylesheet(scene, javaClass)
        applyUiTheme()
        scene.accelerators[KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN)] = Runnable { saveCurrent() }
        installUiThemeKey(scene)
        showEditorGuides.selectedProperty().addListener { _, _, value ->
            Preferences.userNodeForPackage(MainApp::class.java).putBoolean("showEditorGuides", value)
            if (currentIndex in visibleImages.indices) render()
        }
        scene.accelerators[KeyCodeCombination(KeyCode.G, KeyCombination.SHORTCUT_DOWN)] = Runnable {
            showEditorGuides.isSelected = !showEditorGuides.isSelected
        }
        scene.addEventFilter(KeyEvent.KEY_PRESSED) { event ->
            if (event.code == KeyCode.Z && (event.isMetaDown || event.isControlDown) && !isTextInputFocus()) {
                if (event.isShiftDown) redoCardChange() else undoCardChange()
                event.consume()
                return@addEventFilter
            }
            if (!isBrowserFocus()) return@addEventFilter
            val grid = imageBrowserMode == ImageBrowserMode.THUMBNAILS
            val columns = if (grid) browserColumns.coerceAtLeast(1) else 1
            val shortcut = event.isMetaDown || event.isControlDown
            when {
                shortcut && !event.isShiftDown && event.code == KeyCode.UP -> {
                    selectFirstVisible(forceScroll = true); event.consume()
                }
                shortcut && !event.isShiftDown && event.code == KeyCode.DOWN -> {
                    selectLastVisible(forceScroll = true); event.consume()
                }
                event.code == KeyCode.HOME -> {
                    selectFirstVisible(forceScroll = true); event.consume()
                }
                event.code == KeyCode.END -> {
                    selectLastVisible(forceScroll = true); event.consume()
                }
                event.isShiftDown && !shortcut && event.code == KeyCode.UP -> {
                    selectFirstVisible(forceScroll = true); event.consume()
                }
                event.isShiftDown && !shortcut && event.code == KeyCode.DOWN -> {
                    selectLastVisible(forceScroll = true); event.consume()
                }
                event.isAltDown && !event.isShiftDown && !shortcut && event.code == KeyCode.UP -> {
                    navigate(-1, scrollIntoView = false); event.consume()
                }
                event.isAltDown && !event.isShiftDown && !shortcut && event.code == KeyCode.DOWN -> {
                    navigate(1, scrollIntoView = false); event.consume()
                }
                event.code == KeyCode.UP -> {
                    navigate(if (grid) -columns else -1, scrollIntoView = false); event.consume()
                }
                event.code == KeyCode.DOWN -> {
                    navigate(if (grid) columns else 1, scrollIntoView = false); event.consume()
                }
                event.code == KeyCode.LEFT && grid -> {
                    navigate(-1, scrollIntoView = false); event.consume()
                }
                event.code == KeyCode.RIGHT && grid -> {
                    navigate(1, scrollIntoView = false); event.consume()
                }
            }
        }
        stage.scene = scene
        stage.setOnCloseRequest {
            saveCurrent(showStatus = false)
            scanTask?.cancel()
            filterTask?.cancel()
            filterApplyPause.stop()
            thumbnailRefreshPause.stop()
            stopCollectionWatcher()
            closeCollection()
            thumbnailLoadExecutor.shutdownNow()
        }
        stage.show()
        stage.centerOnScreen()
        Platform.runLater {
            chooseRecentCollectionOnStartup(stage)
            // AppKit can ignore the Dock icon if it is changed before the JavaFX
            // window/application has entered its native event loop.
            AppPlatform.setApplicationDockIcon(javaClass)
            resizePreview()
        }
    }

    private fun toolbar(stage: Stage): ToolBar {
        val open = Button("Open Directory").apply { setOnAction { openDirectory(stage) } }
        val previous = Button("← Previous").apply { setOnAction { navigate(-1) } }
        val next = Button("Next →").apply { setOnAction { navigate(1) } }
        val save = Button("Save ⌘S").apply { setOnAction { saveCurrent() } }
        val randomize = Button("Randomize").apply {
            tooltip = Tooltip("Randomize the color scheme, cost, and attack/defense values. Layout is unchanged.")
            setOnAction { randomizeCardStyleAndNumbers() }
        }
        uiThemeButton = Button(if (uiTheme == UiTheme.DARK) "☀ Light UI" else "◐ Dark UI").apply {
            tooltip = Tooltip("Switch the Card Forge application UI theme. This does not change card colors.")
            setOnAction {
                uiTheme = if (uiTheme == UiTheme.DARK) UiTheme.LIGHT else UiTheme.DARK
                Preferences.userNodeForPackage(MainApp::class.java).put("uiTheme", uiTheme.name)
                text = if (uiTheme == UiTheme.DARK) "☀ Light UI" else "◐ Dark UI"
                applyUiTheme()
            }
        }
        val undo = Button("Undo").apply {
            tooltip = Tooltip("Undo the most recent change on the current card (⌘Z / Ctrl+Z).")
            setOnAction { undoCardChange() }
        }
        val redo = Button("Redo").apply {
            tooltip = Tooltip("Redo the most recent undone card change (⇧⌘Z / Ctrl+Shift+Z).")
            setOnAction { redoCardChange() }
        }
        val history = Button("History").apply { setOnAction { showHistory() } }
        val databaseInfo = Button("Database").apply { setOnAction { showDatabaseInfo() } }
        val backup = Button("Backup catalog").apply { setOnAction { backupCatalog(stage) } }
        val shareSidecar = Button("Share sidecar").apply {
            tooltip = Tooltip("Explicitly create a portable .card.json sidecar for the selected card. Normal saves use SQLite only.")
            setOnAction { createSharingSidecar() }
        }
        val exportSvg = Button("Export SVG").apply { setOnAction { exportSvg(stage) } }
        val exportPng = Button("Export PNG").apply { setOnAction { exportPng(stage) } }
        val exportPdf = Button("A4 Contact Sheet PDF").apply {
            tooltip = Tooltip("Export all currently visible cards (current folder/filter scope) onto A4 pages at the card's physical size.")
            setOnAction { exportPdf(stage) }
        }
        val contactSheet = Button("Contact Sheet").apply {
            tooltip = Tooltip("Open a paged visual contact sheet for the current image scope.")
            setOnAction { showContactSheet(stage) }
        }
        return ToolBar(open, Separator(), previous, next, Separator(), save, undo, redo, randomize, uiThemeButton, history, databaseInfo, backup, shareSidecar, Separator(), exportSvg, exportPng, exportPdf, contactSheet)
    }

    private fun browser(): VBox {
        filterField.promptText = "Filter cards, images and folders…"
        filterField.styleClass.add("browser-filter")
        filterField.tooltip = Tooltip("Search across filenames, folders, IDs, status and every stored card property.")
        filterField.textProperty().addListener { _, _, _ ->
            filterApplyPause.stop()
            filterApplyPause.setOnFinished { applyBrowserFilter() }
            filterApplyPause.playFromStart()
        }

        browserModeToggleGroup = ToggleGroup()
        browserListToggle = ToggleButton("☷").apply {
            toggleGroup = browserModeToggleGroup
            isSelected = imageBrowserMode == ImageBrowserMode.LIST
            accessibleText = "List view"
            tooltip = Tooltip("List view")
            setOnAction {
                val old = imageBrowserMode
                switchBrowserMode(ImageBrowserMode.LIST)
                if (imageBrowserMode != ImageBrowserMode.LIST) isSelected = old == ImageBrowserMode.LIST
            }
        }
        browserGridToggle = ToggleButton("▦").apply {
            toggleGroup = browserModeToggleGroup
            isSelected = imageBrowserMode == ImageBrowserMode.THUMBNAILS
            accessibleText = "Thumbnail grid"
            tooltip = Tooltip("Thumbnail grid")
            setOnAction {
                val old = imageBrowserMode
                switchBrowserMode(ImageBrowserMode.THUMBNAILS)
                if (imageBrowserMode != ImageBrowserMode.THUMBNAILS) isSelected = old == ImageBrowserMode.THUMBNAILS
            }
        }

        browserPreviewToggleGroup = ToggleGroup()
        browserOriginalToggle = ToggleButton("◎").apply {
            toggleGroup = browserPreviewToggleGroup
            isSelected = browserPreviewMode == BrowserPreviewMode.ORIGINAL
            accessibleText = "Original image previews"
            tooltip = Tooltip("Original image previews")
            setOnAction { switchBrowserPreviewMode(BrowserPreviewMode.ORIGINAL) }
        }
        browserCardToggle = ToggleButton("▣").apply {
            toggleGroup = browserPreviewToggleGroup
            isSelected = browserPreviewMode == BrowserPreviewMode.CARD
            accessibleText = "Card previews"
            tooltip = Tooltip("Card previews")
            setOnAction { switchBrowserPreviewMode(BrowserPreviewMode.CARD) }
        }
        collectionChoice.apply {
            setCellFactory { collectionOptionCell() }
            buttonCell = collectionOptionCell()
            prefWidth = 260.0
            tooltip = Tooltip("Switch between the selected collection and collections discovered in its subfolders.")
            valueProperty().addListener { _, old, value ->
                if (!suppressCollectionChoice && value != null && value != old) openCollectionPath(value.root)
            }
        }
        browserSortChoice.apply {
            items.setAll(BrowserSort.entries)
            value = runCatching {
                BrowserSort.valueOf(Preferences.userNodeForPackage(MainApp::class.java).get("browserSort", BrowserSort.FILE_NAME.name))
            }.getOrDefault(BrowserSort.FILE_NAME)
            setCellFactory { browserSortCell() }
            buttonCell = browserSortCell()
            tooltip = Tooltip("Sort visible cards by title, last word, card number, folder, status, or filename.")
            valueProperty().addListener { _, old, value ->
                if (value != null && value != old) {
                    Preferences.userNodeForPackage(MainApp::class.java).put("browserSort", value.name)
                    rebuildVisibleSorted()
                }
            }
        }
        browserSortDescending.apply {
            isSelected = Preferences.userNodeForPackage(MainApp::class.java).getBoolean("browserSortDescending", false)
            tooltip = Tooltip("Reverse the current card ordering.")
            selectedProperty().addListener { _, _, value ->
                Preferences.userNodeForPackage(MainApp::class.java).putBoolean("browserSortDescending", value)
                rebuildVisibleSorted()
            }
        }

        val tools = VBox(6.0,
            HBox(8.0, Label("Collection"), collectionChoice).apply { alignment = Pos.CENTER_LEFT },
            HBox(8.0, filterField, HBox(2.0, browserListToggle, browserGridToggle), HBox(2.0, browserOriginalToggle, browserCardToggle)).apply {
                alignment = Pos.CENTER_LEFT
                HBox.setHgrow(filterField, Priority.ALWAYS)
            },
            HBox(8.0, Label("Sort"), browserSortChoice, browserSortDescending).apply {
                alignment = Pos.CENTER_LEFT
            }
        )
        val header = HBox(8.0, Label("Images"), browserCountLabel).apply {
            alignment = Pos.CENTER_LEFT
            browserCountLabel.styleClass.add("browser-meta")
        }

        folderTree.isShowRoot = true
        folderTree.setCellFactory {
            object : TreeCell<Path>() {
                override fun updateItem(item: Path?, empty: Boolean) {
                    super.updateItem(item, empty)
                    if (empty || item == null) {
                        text = null; tooltip = null; graphic = null; return
                    }
                    val root = collectionRoot
                    val isCollection = item != root && nestedCollectionRoots.contains(item.toAbsolutePath().normalize())
                    text = when {
                        root != null && item == root -> "📁 ${item.fileName} · All Images"
                        isCollection -> "◈ ${item.fileName} · Collection"
                        else -> "📁 ${item.fileName}"
                    }
                    tooltip = Tooltip(if (root != null) relativePath(item) else item.toString())
                    contextMenu = ContextMenu(
                        MenuItem("Open in Finder").apply { setOnAction { revealInFinder(item) } },
                        MenuItem("Copy Path").apply { setOnAction {
                            val content = ClipboardContent().apply { putString(item.toAbsolutePath().toString()) }
                            Clipboard.getSystemClipboard().setContent(content)
                        } }
                    )
                }
            }
        }
        folderTree.selectionModel.selectedItemProperty().addListener { _, _, item ->
            if (suppressFolderSelection) return@addListener
            val folder = item?.value ?: return@addListener
            val normalizedFolder = folder.toAbsolutePath().normalize()
            if (nestedCollectionRoots.contains(normalizedFolder)) {
                openCollectionPath(normalizedFolder)
                return@addListener
            }
            if (folder != selectedFolder) {
                if (currentIndex in visibleImages.indices && !saveCurrent(showStatus = false)) return@addListener
                selectedFolder = folder
                requestVisibleImagesRebuild()
            }
        }

        configureListView(imageList)
        configureGridView(gridList)
        browserStack = StackPane(imageList, gridList).apply {
            minHeight = 220.0
            maxWidth = Double.MAX_VALUE
        }
        imageList.isVisible = true
        imageList.isManaged = true
        gridList.isVisible = false
        gridList.isManaged = false

        val root = VBox(10.0, tools, header, folderTree, browserStack).apply {
            padding = Insets(12.0)
            minWidth = 300.0
            prefWidth = 470.0
            maxWidth = Double.MAX_VALUE
            folderTree.prefHeight = 190.0
            VBox.setVgrow(browserStack, Priority.ALWAYS)
        }
        return root
    }

    private fun configureListView(view: ListView<Path>) {
        view.placeholder = Label("No images match the current filter.")
        view.styleClass.add("image-browser-list")
        view.style = "-fx-background-color:transparent;-fx-control-inner-background:transparent;-fx-selection-bar:transparent;-fx-selection-bar-non-focused:transparent;"
        view.isFocusTraversable = true
        view.setCellFactory { browserListCell() }
        view.focusedProperty().addListener { _, _, focused -> if (focused) refreshBrowserSelectionStyles() }
    }

    private fun configureGridView(view: ListView<GridRow>) {
        view.placeholder = Label("No images match the current filter.")
        view.styleClass.add("image-browser-grid")
        view.style = "-fx-background-color:transparent;-fx-control-inner-background:transparent;-fx-selection-bar:transparent;-fx-selection-bar-non-focused:transparent;"
        view.isFocusTraversable = true
        view.setCellFactory { browserGridCell() }
        view.focusedProperty().addListener { _, _, focused -> if (focused) refreshBrowserSelectionStyles() }
        view.widthProperty().addListener { _, _, newWidth ->
            if (newWidth.toDouble() > 0.0) {
                val newColumns = calculateBrowserColumns()
                if (newColumns != browserColumns) {
                    val anchor = firstVisibleBrowserPath(view, true)
                    browserColumns = newColumns
                    rebuildGrid(anchor)
                }
            }
        }
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

    private fun browserListCell() = object : ListCell<Path>() {
        override fun updateItem(item: Path?, empty: Boolean) {
            super.updateItem(item, empty)
            style = "-fx-background-color:transparent;-fx-control-inner-background:transparent;-fx-selection-bar:transparent;-fx-selection-bar-non-focused:transparent;-fx-padding:2px;"
            graphic = if (empty || item == null) null else createListTile(item)
            text = null
            isFocusTraversable = false
        }
    }

    private fun browserGridCell() = object : ListCell<GridRow>() {
        override fun updateItem(item: GridRow?, empty: Boolean) {
            super.updateItem(item, empty)
            style = "-fx-background-color:transparent;-fx-control-inner-background:transparent;-fx-selection-bar:transparent;-fx-selection-bar-non-focused:transparent;-fx-padding:2px;"
            graphic = if (empty || item == null) null else createThumbnailRow(item.paths)
            text = null
            isFocusTraversable = false
        }
    }

    private fun createThumbnailRow(paths: List<Path>): HBox {
        return HBox(10.0).apply {
            alignment = Pos.TOP_LEFT
            padding = Insets(2.0, 6.0, 6.0, 6.0)
            prefHeight = 186.0
            minHeight = 186.0
            maxHeight = 186.0
            isFillHeight = false
            paths.forEach { path -> children.add(createImageTile(path, thumbnail = true)) }
        }
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
            minWidth = 64.0; prefWidth = 64.0; maxWidth = 64.0
            minHeight = 64.0; prefHeight = 64.0; maxHeight = 64.0
            styleClass.add("browser-preview-box")
        }
        val loading = Label("…").apply {
            styleClass.add("browser-loading")
            isMouseTransparent = true
        }
        previewBox.children.add(loading)
        requestBrowserPreview(path, browserPreviewMode) { image ->
            preview.image = image
            loading.text = if (image == null) "?" else ""
            loading.isVisible = image == null
            loading.isManaged = image == null
        }
        val cardName = Label(cardDataForSorting(path).title.ifBlank { "Untitled card" }).apply {
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
        val folder = Label(relativePath(path.parent ?: path)).apply {
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
        val row = HBox(10.0, previewBox, text).apply { alignment = Pos.CENTER_LEFT }
        tile.children.add(row)
        tile.setOnContextMenuRequested { event ->
            imageContextMenu(path).show(tile, event.screenX, event.screenY)
            event.consume()
        }
        tile.addEventFilter(MouseEvent.MOUSE_PRESSED) { event ->
            if (event.button == MouseButton.PRIMARY) {
                event.consume()
                selectPath(path, scrollIntoView = false)
                imageList.requestFocus()
            }
        }
        tile.style = tileStyle(path == imagesCurrentPath(), hovered = false, thumbnail = false)
        tile.setOnMouseEntered { tile.style = tileStyle(path == imagesCurrentPath(), true, false) }
        tile.setOnMouseExited { tile.style = tileStyle(path == imagesCurrentPath(), false, false) }
        return tile
    }

    private fun createImageTile(path: Path, thumbnail: Boolean): VBox {
        val tileWidth = 156.0
        val tile = VBox(4.0).apply {
            prefWidth = tileWidth; minWidth = tileWidth; maxWidth = tileWidth
            alignment = Pos.TOP_CENTER; padding = Insets(6.0)
            cursor = Cursor.HAND
            isFocusTraversable = false
        }
        val thumbView = ImageView().apply {
            fitWidth = 132.0; fitHeight = 132.0
            isPreserveRatio = true; isSmooth = true; isMouseTransparent = true
        }
        val imageBox = StackPane().apply {
            prefWidth = 132.0; prefHeight = 132.0
            minWidth = 132.0; minHeight = 132.0
            styleClass.add("browser-preview-box")
            children.add(thumbView)
        }
        val loadingLabel = Label("Loading…").apply {
            styleClass.add("browser-loading")
            isMouseTransparent = true
        }
        imageBox.children.add(loadingLabel)
        val cardName = Label(cardDataForSorting(path).title.ifBlank { "Untitled card" }).apply {
            styleClass.add("browser-card-name")
            maxWidth = 144.0; isWrapText = true; alignment = Pos.TOP_CENTER
            style = "-fx-font-size:11px;-fx-font-weight:bold;"
            isMouseTransparent = true
        }
        val name = Label(path.fileName.toString()).apply {
            styleClass.add("browser-filename")
            maxWidth = 144.0; isWrapText = true; alignment = Pos.TOP_CENTER
            textOverrun = javafx.scene.control.OverrunStyle.ELLIPSIS
            style = "-fx-font-size:9px;"
            isMouseTransparent = true
        }
        tile.children.addAll(imageBox, cardName, name)
        requestBrowserPreview(path, browserPreviewMode) { image ->
            thumbView.image = image
            loadingLabel.text = if (image == null) "Preview unavailable" else ""
            loadingLabel.isVisible = image == null
            loadingLabel.isManaged = image == null
        }
        tile.setOnContextMenuRequested { event ->
            imageContextMenu(path).show(tile, event.screenX, event.screenY)
            event.consume()
        }
        tile.addEventFilter(MouseEvent.MOUSE_PRESSED) { event ->
            if (event.button == MouseButton.PRIMARY) {
                event.consume()
                selectPath(path, scrollIntoView = false)
                gridList.requestFocus()
            }
        }
        tile.style = tileStyle(path == imagesCurrentPath(), hovered = false, thumbnail = thumbnail)
        tile.setOnMouseEntered { tile.style = tileStyle(path == imagesCurrentPath(), true, thumbnail) }
        tile.setOnMouseExited { tile.style = tileStyle(path == imagesCurrentPath(), false, thumbnail) }
        return tile
    }

    private fun tileStyle(selected: Boolean, hovered: Boolean, thumbnail: Boolean): String {
        val radius = if (thumbnail) 8 else 6
        return when {
            selected -> "-fx-background-color:rgba(88,166,255,0.11);-fx-background-radius:${radius}px;-fx-border-color:#58A6FF;-fx-border-radius:${radius}px;-fx-border-width:1.5px;"
            hovered -> "-fx-background-color:rgba(255,255,255,0.05);-fx-background-radius:${radius}px;"
            else -> "-fx-background-color:transparent;"
        }
    }

    private fun calculateBrowserColumns(): Int {
        val available = gridList.width - 36.0
        return floor((available + 10.0) / 166.0).toInt().coerceAtLeast(1)
    }

    private fun switchBrowserPreviewMode(mode: BrowserPreviewMode) {
        if (mode == browserPreviewMode) return
        if (currentIndex in visibleImages.indices && !saveCurrent(showStatus = false)) {
            suppressEditorUpdates = true
            try {
                browserOriginalToggle.isSelected = browserPreviewMode == BrowserPreviewMode.ORIGINAL
                browserCardToggle.isSelected = browserPreviewMode == BrowserPreviewMode.CARD
            } finally { suppressEditorUpdates = false }
            return
        }
        browserPreviewMode = mode
        browserOriginalToggle.isSelected = mode == BrowserPreviewMode.ORIGINAL
        browserCardToggle.isSelected = mode == BrowserPreviewMode.CARD
        refreshBrowserSelectionStyles()
    }

    private fun switchBrowserMode(mode: ImageBrowserMode) {
        if (mode == imageBrowserMode) return
        if (currentIndex in visibleImages.indices && !saveCurrent(showStatus = false)) return
        val anchor = firstVisibleBrowserPath(activeBrowserNodeList(), imageBrowserMode == ImageBrowserMode.THUMBNAILS)
        imageBrowserMode = mode
        browserListToggle.isSelected = mode == ImageBrowserMode.LIST
        browserGridToggle.isSelected = mode == ImageBrowserMode.THUMBNAILS
        browserColumns = calculateBrowserColumns()
        imageList.isVisible = mode == ImageBrowserMode.LIST
        imageList.isManaged = mode == ImageBrowserMode.LIST
        gridList.isVisible = mode == ImageBrowserMode.THUMBNAILS
        gridList.isManaged = mode == ImageBrowserMode.THUMBNAILS
        if (mode == ImageBrowserMode.LIST) {
            rebuildImageList(anchor)
        } else {
            rebuildGrid(anchor)
        }
        syncBrowserSelection(imagesCurrentPath(), forceScroll = false)
        refreshBrowserSelectionStyles()
    }

    private fun activeBrowserNodeList(): javafx.scene.control.ListView<*> = if (imageBrowserMode == ImageBrowserMode.THUMBNAILS) gridList else imageList

    private fun rebuildGrid(anchorPath: Path? = null) {
        browserColumns = calculateBrowserColumns()
        val rows = visibleImages.chunked(browserColumns.coerceAtLeast(1)).map(::GridRow)
        gridList.items.clear()
        gridList.items.addAll(rows)
        gridList.refresh()
        if (anchorPath != null) scrollToBrowserPath(anchorPath, false)
    }

    private fun rebuildImageList(anchorPath: Path? = null) {
        browserColumns = calculateBrowserColumns()
        imageList.items.clear()
        imageList.items.addAll(visibleImages)
        imageList.refresh()
        rebuildGrid(anchorPath)
        if (imageBrowserMode == ImageBrowserMode.LIST && anchorPath != null) scrollToBrowserPath(anchorPath, false)
    }

    private fun refreshBrowserSelectionStyles() {
        imageList.refresh()
        gridList.refresh()
    }

    private fun scrollToBrowserPath(path: Path?, force: Boolean) {
        if (path == null) return
        val index = visibleImages.indexOf(path)
        if (index !in visibleImages.indices) return
        val view = activeBrowserNodeList()
        val row = if (imageBrowserMode == ImageBrowserMode.THUMBNAILS) index / browserColumns.coerceAtLeast(1) else index
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
            if (cellTop < viewportTop || cellBottom > viewportBottom) view.scrollTo(row)
        }
    }

    private fun firstVisibleBrowserPath(view: javafx.scene.control.ListView<*>, grid: Boolean): Path? {
        val cells = view.lookupAll(".list-cell").filterIsInstance<ListCell<*>>()
        if (cells.isEmpty()) return null
        val viewportTop = view.localToScene(0.0, 0.0).y
        val cell = cells.filter { it.index >= 0 && it.localToScene(0.0, 0.0).y + it.height >= viewportTop }
            .minByOrNull { it.localToScene(0.0, 0.0).y } ?: return null
        val item = cell.item
        return if (grid) (item as? GridRow)?.paths?.firstOrNull() else item as? Path
    }

    private fun syncBrowserSelection(path: Path?, forceScroll: Boolean) {
        if (path == null) return
        val index = visibleImages.indexOf(path)
        if (index !in visibleImages.indices) return
        if (imageBrowserMode == ImageBrowserMode.LIST) {
            imageList.selectionModel.select(path)
            imageList.focusModel.focus(index)
        } else {
            val row = index / browserColumns.coerceAtLeast(1)
            gridList.selectionModel.select(row)
            gridList.focusModel.focus(row)
        }
        if (forceScroll) scrollToBrowserPath(path, true) else scrollToBrowserPath(path, false)
    }

    private fun selectPath(path: Path, scrollIntoView: Boolean = false) {
        val normalized = path.toAbsolutePath().normalize()
        val index = visibleImages.indexOfFirst { it.toAbsolutePath().normalize() == normalized }
        if (index >= 0) select(index, scrollIntoView)
    }

    private fun navigate(delta: Int, scrollIntoView: Boolean = false) {
        if (visibleImages.isEmpty()) return
        val base = if (currentIndex < 0) 0 else currentIndex
        select((base + delta).coerceIn(0, visibleImages.lastIndex), scrollIntoView = scrollIntoView)
    }

    private fun selectFirstVisible(forceScroll: Boolean = false) {
        if (visibleImages.isNotEmpty()) select(0, forceScroll)
    }

    private fun selectLastVisible(forceScroll: Boolean = false) {
        if (visibleImages.isNotEmpty()) select(visibleImages.lastIndex, forceScroll)
    }

    private fun isBrowserFocus(): Boolean {
        var node: Node? = scene.focusOwner
        while (node != null) {
            if (node === imageList || node === gridList) return true
            node = node.parent
        }
        return false
    }

    private fun imageContextMenu(path: Path): ContextMenu {
        val reveal = MenuItem("Reveal in Finder")
        reveal.setOnAction { revealInFinder(path) }
        val copy = MenuItem("Copy Path")
        copy.setOnAction {
            val content = ClipboardContent().apply { putString(path.toAbsolutePath().toString()) }
            Clipboard.getSystemClipboard().setContent(content)
        }
        return ContextMenu(reveal, copy)
    }

    private fun cachedThumbnail(path: Path): Image? = synchronized(thumbnailCache) {
        val absolute = path.toAbsolutePath().normalize()
        val size = runCatching { Files.size(absolute) }.getOrDefault(-1L)
        val modified = runCatching { Files.getLastModifiedTime(absolute).toMillis() }.getOrDefault(-1L)
        thumbnailCache[absolute]?.takeIf { it.size == size && it.modified == modified }?.image
    }

    private fun cachedFullImage(path: Path): Image? = synchronized(fullImageCache) { cachedFullImageUnlocked(path) }

    private fun cachedFullImageUnlocked(path: Path): Image? {
        val absolute = path.toAbsolutePath().normalize()
        val size = runCatching { Files.size(absolute) }.getOrDefault(-1L)
        val modified = runCatching { Files.getLastModifiedTime(absolute).toMillis() }.getOrDefault(-1L)
        fullImageCache[absolute]?.takeIf { it.size == size && it.modified == modified }?.let { return it.image }
        val image = runCatching { Image(absolute.toUri().toString(), false) }.getOrNull() ?: return null
        fullImageCache[absolute] = CachedImage(size, modified, image)
        return image
    }

    private fun requestBrowserPreview(path: Path, mode: BrowserPreviewMode, onLoaded: (Image?) -> Unit) {
        when (mode) {
            BrowserPreviewMode.ORIGINAL -> requestThumbnail(path, onLoaded)
            BrowserPreviewMode.CARD -> requestCardThumbnail(path, onLoaded)
        }
    }

    private fun requestThumbnail(path: Path, onLoaded: (Image?) -> Unit = {}) {
        val absolute = path.toAbsolutePath().normalize()
        val size = runCatching { Files.size(absolute) }.getOrDefault(-1L)
        val modified = runCatching { Files.getLastModifiedTime(absolute).toMillis() }.getOrDefault(-1L)
        synchronized(thumbnailCache) {
            thumbnailCache[absolute]?.takeIf { it.size == size && it.modified == modified }?.let { cached ->
                Platform.runLater { onLoaded(cached.image) }
                return
            }
        }
        thumbnailWaiters.computeIfAbsent(absolute) { CopyOnWriteArrayList() }.add(onLoaded)
        if (!thumbnailLoads.add(absolute)) return
        thumbnailLoadExecutor.submit {
            val fxImage = runCatching {
                // Decode off the FX thread with ImageIO, then cross the JavaFX boundary
                // only once. This avoids the intermittent blank-thumbnail behaviour of
                // constructing Image objects from worker threads.
                val buffered = ImageIO.read(absolute.toFile()) ?: return@runCatching null
                val scaled = scaleThumbnail(buffered, 180)
                SwingFXUtils.toFXImage(scaled, null)
            }.getOrNull()?.takeIf { it.width > 0.0 && it.height > 0.0 }
            Platform.runLater {
                try {
                    val finalImage = fxImage ?: runCatching {
                        Image(absolute.toUri().toString(), 180.0, 180.0, true, true, false)
                    }.getOrNull()?.takeIf { !it.isError && it.width > 0.0 && it.height > 0.0 }
                    if (finalImage != null) {
                        synchronized(thumbnailCache) { thumbnailCache[absolute] = CachedImage(size, modified, finalImage) }
                        thumbnailLastFailure.remove(absolute)
                    } else {
                        thumbnailLastFailure[absolute] = System.currentTimeMillis()
                    }
                    val waiters = thumbnailWaiters.remove(absolute).orEmpty()
                    waiters.forEach { callback -> callback(finalImage) }
                } finally {
                    thumbnailLoads.remove(absolute)
                }
            }
        }
    }

    private fun requestCardThumbnail(path: Path, onLoaded: (Image?) -> Unit) {
        val absolute = path.toAbsolutePath().normalize()
        val size = runCatching { Files.size(absolute) }.getOrDefault(-1L)
        val modified = runCatching { Files.getLastModifiedTime(absolute).toMillis() }.getOrDefault(-1L)
        synchronized(cardThumbnailCache) {
            cardThumbnailCache[absolute]?.takeIf { it.sourceSize == size && it.sourceModified == modified }?.let { cached ->
                Platform.runLater { onLoaded(cached.image) }
                return
            }
        }
        cardThumbnailWaiters.computeIfAbsent(absolute) { CopyOnWriteArrayList() }.add(onLoaded)
        if (!cardThumbnailLoads.add(absolute)) return
        thumbnailLoadExecutor.submit {
            // Decode once off the JavaFX thread. Reuse that decode both for the thumbnail
            // source and for initial image-derived scheme selection, so card preview creation
            // never blocks the UI by re-reading the original image.
            val decoded = runCatching {
                val buffered = ImageIO.read(absolute.toFile()) ?: return@runCatching null
                val derivedColor = ImageColorAnalyzer.dominantColor(buffered)
                val scaled = scaleThumbnail(buffered, 900)
                val fxImage = SwingFXUtils.toFXImage(scaled, null)
                fxImage to derivedColor
            }.getOrNull()
            val source = decoded?.first?.takeIf { it.width > 0.0 && it.height > 0.0 }
            val derivedColor = decoded?.second
            Platform.runLater {
                try {
                    val data = savedDataForPath(absolute, derivedColor, analyzeImageIfNeeded = false)
                    val effectiveSource = source ?: runCatching {
                        Image(absolute.toUri().toString(), 900.0, 900.0, true, true, false)
                    }.getOrNull()?.takeIf { !it.isError && it.width > 0.0 && it.height > 0.0 }
                    val preview = if (effectiveSource != null) renderCardPreviewImage(effectiveSource, data) else null
                    if (preview != null) {
                        synchronized(cardThumbnailCache) {
                            cardThumbnailCache[absolute] = CardPreviewEntry(preview, size, modified)
                        }
                    }
                    val waiters = cardThumbnailWaiters.remove(absolute).orEmpty()
                    waiters.forEach { callback -> callback(preview) }
                } finally {
                    cardThumbnailLoads.remove(absolute)
                }
            }
        }
    }

    private fun renderCardPreviewImage(image: Image, data: CardData): Image? {
        // Reuse the exact export renderer so the in-app contact sheet cannot diverge
        // from PNG/PDF output. This method is only invoked on the JavaFX thread.
        val template = templateForData(data) ?: return null
        val templateImage = TemplateRepository.rasterize(template)
        val overlay = data.backgroundOverlay.takeIf { it.isNotBlank() }?.let { name ->
            OverlayRepository.resolve(name)?.let {
                OverlayRepository.rasterize(it, template.width, template.height, data.overlayColor)
            }
        }
        return runCatching {
            ExportRenderer.snapshotImage(
                image = image,
                data = data,
                template = template,
                templateImage = templateImage,
                backgroundOverlay = overlay,
                collectionPresentation = collectionPresentation,
                scale = (360.0 / max(template.width, template.height)).coerceIn(0.35, 0.65)
            )
        }.onFailure { error ->
            System.err.println("Card Forge: card preview failed for ${data.assetId.ifBlank { "<no-id>" }}: ${error.message}")
            error.printStackTrace()
        }.getOrNull()
    }

    private fun savedDataForPath(
        path: Path,
        derivedColorOverride: Color? = null,
        analyzeImageIfNeeded: Boolean = true
    ): CardData {
        val normalized = path.toAbsolutePath().normalize()
        cardDataCache[normalized]?.let { return it.copy() }
        val result = database?.dataSnapshotForPath(normalized)?.takeIf(::hasMeaningfulCardData)?.copy()
            ?: newCardDefaults(normalized, derivedColorOverride, analyzeImageIfNeeded)
        cardDataCache[normalized] = result.copy()
        return result
    }

    private fun hasMeaningfulCardData(data: CardData): Boolean = listOf(
        data.title, data.cost, data.typeLine, data.rarity, data.description,
        data.flavorText, data.artist, data.setName, data.collectorNumber, data.stats
    ).count { it.isNotBlank() } >= 3

    private fun templateForData(data: CardData): CardTemplate? {
        val effectiveName = data.templateName.ifBlank { collectionDefaultTemplateName }
        return templates.firstOrNull { it.name == effectiveName } ?: templates.firstOrNull()
    }

    private fun scaleThumbnail(source: BufferedImage, maxSize: Int): BufferedImage {
        val sourceW = source.width.coerceAtLeast(1)
        val sourceH = source.height.coerceAtLeast(1)
        val scale = min(maxSize.toDouble() / sourceW, maxSize.toDouble() / sourceH).coerceAtMost(1.0)
        val width = (sourceW * scale).roundToInt().coerceAtLeast(1)
        val height = (sourceH * scale).roundToInt().coerceAtLeast(1)
        val result = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = result.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED)
        graphics.drawImage(source, 0, 0, width, height, null)
        graphics.dispose()
        return result
    }

    private fun previewPane(): VBox {
        previewHost.alignment = Pos.CENTER
        previewHost.style = "-fx-background-color:#20242A;"
        previewHost.widthProperty().addListener { _, _, _ -> resizePreview() }
        previewHost.heightProperty().addListener { _, _, _ -> resizePreview() }
        val header = HBox(10.0, Label("Card Preview"), Region()).apply {
            alignment = Pos.CENTER_LEFT
            HBox.setHgrow(children[1], Priority.ALWAYS)
            children.add(showEditorGuides)
        }
        return VBox(8.0, header, previewHost).apply {
            minWidth = 500.0
            prefWidth = 800.0
            alignment = Pos.TOP_CENTER
            padding = Insets(12.0)
            VBox.setVgrow(previewHost, Priority.ALWAYS)
        }
    }

    private fun editor(): ScrollPane {
        val form = VBox(10.0).apply { padding = Insets(12.0); prefWidth = 430.0 }

        fun textField(key: String): TextField = TextField().also { field ->
            fields[key] = field
            field.textProperty().addListener { _, _, _ -> updateFromEditor() }
        }

        form.children.add(section("Collection"))
        collectionSettingsPane = CollectionSettingsPane(
            initial = collectionPresentation,
            onPresentationChanged = { value ->
                collectionPresentation = value
                persistCollectionPresentation()
                render()
            },
            onApplyDefaultTemplateToAll = { applyCollectionDefaultTemplateToAll() },
            onApplySetNameToAll = { applyCurrentSetNameToAll() },
            onNormalizeCollectorTotals = { normalizeCollectorTotals() }
        )
        form.children.add(collectionSettingsPane)
        form.children.add(helperLabel("These presentation options belong to the collection and apply to every card."))

        form.children.add(section("Card Metadata"))
        form.children.add(rowWithDice("Title", textField("title")) { randomizeTitle() })
        form.children.add(rowWithDice("Cost", textField("cost")) { randomizeCost() })
        form.children.add(rowWithDice("Type line", textField("typeLine")) { randomizeTypeLine() })
        form.children.add(rowWithDice("Rarity", textField("rarity")) { randomizeRarity() })
        form.children.add(rowWithDice("Stats", textField("stats")) { randomizeStats() })
        form.children.add(rowWithDice("Artist", textField("artist")) { randomizeArtistPattern() })
        form.children.add(rowWithDice("Set", textField("setName")) { randomizeSetName() })
        form.children.add(rowWithDice("Number", textField("collectorNumber")) { randomizeCollectorNumber() })

        statusChoice.items.setAll(CardStatus.entries)
        statusChoice.setCellFactory { statusCell() }
        statusChoice.buttonCell = statusCell()
        statusChoice.tooltip = Tooltip("Workflow state saved with the card.")
        statusChoice.valueProperty().addListener { _, _, value -> if (!suppressEditorUpdates && value != null) updateFromEditor() }
        form.children.add(row("Status", statusChoice))

        description.isWrapText = true
        description.prefRowCount = 4
        description.textProperty().addListener { _, _, _ -> updateFromEditor() }
        flavor.isWrapText = true
        flavor.prefRowCount = 3
        flavor.textProperty().addListener { _, _, _ -> updateFromEditor() }
        form.children.add(row("Description", description))
        form.children.add(row("Flavor", flavor))

        form.children.add(section("Scheme"))
        schemeChoice.setCellFactory { schemeCell() }
        schemeChoice.buttonCell = schemeCell()
        form.children.add(rowWithDice("Color scheme", schemeChoice) { randomizeScheme() })
        form.children.add(HBox(8.0).apply {
            children.add(Button("Apply scheme").apply {
                setOnAction { if (currentIndex in visibleImages.indices) applySelectedScheme() }
                maxWidth = Double.MAX_VALUE
                HBox.setHgrow(this, Priority.ALWAYS)
            })
            children.add(Button("From image").apply {
                tooltip = Tooltip("Choose the closest coordinated color scheme from the dominant artwork color.")
                setOnAction { applySchemeFromCurrentImage() }
            })
            children.add(Button("Reload schemes").apply { setOnAction { loadSchemes() } })
        })
        schemeChoice.valueProperty().addListener { _, old, value ->
            if (!suppressEditorUpdates && value != null && value != old && currentIndex in visibleImages.indices) {
                applySelectedScheme()
            }
        }
        form.children.add(helperLabel("Schemes are editable JSON files under schemes/. Each scheme includes a distinct card backgroundColor."))

        form.children.add(section("Layout"))
        templateChoice.setCellFactory { templateCell() }
        templateChoice.buttonCell = templateCell()
        templateChoice.valueProperty().addListener { _, _, value ->
            if (!suppressEditorUpdates && value != null && currentIndex in visibleImages.indices) {
                captureUndoSnapshot()
                templateOverride.isSelected = true
                currentData.templateName = value.name
                loadTemplateAndOverlay()
                recalculatePanControls(resetPan = false)
                updateFromEditor()
            }
        }
        templateOverride.selectedProperty().addListener { _, _, selected ->
            if (suppressEditorUpdates || currentIndex !in visibleImages.indices) return@addListener
            captureUndoSnapshot()
            currentData.templateName = if (selected) {
                templateChoice.value?.name ?: currentTemplate()?.name.orEmpty()
            } else ""
            loadTemplateAndOverlay()
            recalculatePanControls(resetPan = false)
            render()
            updateFromEditor(renderPreview = false)
        }
        collectionTemplateChoice.items.setAll(templates)
        collectionTemplateChoice.setCellFactory { templateCell() }
        collectionTemplateChoice.buttonCell = templateCell()
        collectionTemplateChoice.valueProperty().addListener { _, old, value ->
            if (!suppressEditorUpdates && value != null && value != old) {
                setCollectionDefaultTemplate(value)
            }
        }
        form.children.add(row("Card template", templateChoice))
        form.children.add(templateOverride.apply { tooltip = Tooltip("On: keep a template override for this card. Off: follow the collection default.") })
        form.children.add(row("Collection default", collectionTemplateChoice))
        form.children.add(Button("Reset card to collection default").apply {
            maxWidth = Double.MAX_VALUE
            setOnAction { templateOverride.isSelected = false }
        })
        val reloadTemplates = Button("Reload templates").apply { setOnAction { loadTemplates() } }
        form.children.add(reloadTemplates)
        form.children.add(helperLabel("Choose a collection default once; cards follow it unless they have an explicit override. Add templates under templates/."))

        form.children.add(section("Artwork"))
        imageMode.items.setAll(ImageMode.entries)
        imageMode.setCellFactory { imageModeCell() }
        imageMode.buttonCell = imageModeCell()
        imageMode.tooltip = Tooltip("Crop to Fill, Fit + Pad, and Stretch can all zoom below 1×; the image frame then shows the pad color.")
        imageMode.valueProperty().addListener { _, _, value ->
            if (!suppressEditorUpdates && value != null) {
                recalculatePanControls(resetPan = false)
                updateFromEditor(renderPreview = false)
                refreshArtworkOnly()
            }
        }
        form.children.add(row("Fit", imageMode))
        imageBleedOverFrame.apply {
            tooltip = Tooltip("Extend artwork behind the surrounding card frame. The description panel remains above it; description background opacity controls how much artwork can show through there.")
            selectedProperty().addListener { _, _, _ -> if (!suppressEditorUpdates) updateFromEditor() }
        }
        form.children.add(imageBleedOverFrame)
        imageBleedOpacity.tooltip = Tooltip("Per-card opacity for artwork that extends over the frame; multiplied by the collection bleed opacity.")
        imageBleedOpacity.valueProperty().addListener { _, _, value ->
            imageBleedOpacityValue.text = "%.0f%%".format(value.toDouble() * 100.0)
            if (!suppressEditorUpdates) updateFromEditor()
        }
        installSliderReset(imageBleedOpacity, 1.0)
        form.children.add(sliderRow("Bleed opacity", imageBleedOpacity, imageBleedOpacityValue, "%.0f%%"))
        form.children.add(helperLabel("Drag the artwork to pan. Scroll to zoom. Double-click the artwork or use Reset to return to centered 1×."))

        val cropActions = HBox(8.0).apply {
            val fill = Button("Crop to Fill").apply { setOnAction { setArtwork(ImageMode.COVER, 1.0, 0.0, 0.0) } }
            val contain = Button("Fit + Pad").apply { setOnAction { setArtwork(ImageMode.CONTAIN, 1.0, 0.0, 0.0) } }
            val center = Button("Center").apply { setOnAction { centerArtwork() } }
            val reset = Button("Reset").apply { setOnAction { resetArtworkPositionAndZoom() } }
            fill.tooltip = Tooltip("Fill the artwork frame at 1×.")
            contain.tooltip = Tooltip("Show the whole image with padding when aspect ratio differs.")
            center.tooltip = Tooltip("Center at the current zoom.")
            reset.tooltip = Tooltip("Return to centered 1× Crop to Fill.")
            children.addAll(fill, contain, center, reset)
        }
        form.children.add(cropActions)
        form.children.add(sliderRow("Zoom", zoom, zoomValueLabel, "%.2f×"))
        offsetX.isShowTickMarks = true
        offsetX.isShowTickLabels = true
        offsetX.majorTickUnit = 1.0
        offsetY.isShowTickMarks = true
        offsetY.isShowTickLabels = true
        offsetY.majorTickUnit = 1.0
        form.children.add(sliderRow("Position X", offsetX, xValueLabel, "%+.0f px"))
        form.children.add(sliderRow("Position Y", offsetY, yValueLabel, "%+.0f px"))
        form.children.add(row("Pad color", imagePadColor))
        installSliderReset(zoom, 1.0)
        installSliderReset(offsetX, 0.0)
        installSliderReset(offsetY, 0.0)

        zoom.valueProperty().addListener { _, _, value ->
            if (!suppressEditorUpdates) {
                recalculatePanControls(resetPan = false)
                updateFromEditor(renderPreview = false)
                refreshArtworkOnly()
                updateValueLabel(zoomValueLabel, value.toDouble(), "%.2f×")
            }
        }
        offsetX.valueProperty().addListener { _, _, _ ->
            if (!suppressEditorUpdates) {
                updateValueLabelFromActualPan()
                updateFromEditor(renderPreview = false)
                refreshArtworkOnly()
            }
        }
        offsetY.valueProperty().addListener { _, _, _ ->
            if (!suppressEditorUpdates) {
                updateValueLabelFromActualPan()
                updateFromEditor(renderPreview = false)
                refreshArtworkOnly()
            }
        }
        imagePadColor.valueProperty().addListener { _, _, _ -> if (!suppressEditorUpdates) updateFromEditor() }

        form.children.add(section("Background"))
        form.children.add(row("Card background", backgroundColor))
        backgroundOverlayChoice.setCellFactory { overlayCell() }
        backgroundOverlayChoice.buttonCell = overlayCell()
        form.children.add(rowWithDice("SVG overlay", backgroundOverlayChoice) { randomizeOverlay() })
        form.children.add(row("Overlay tint", overlayColor))
        overlayPlacementChoice.items.setAll(OverlayPlacement.entries)
        overlayPlacementChoice.setCellFactory { overlayPlacementCell() }
        overlayPlacementChoice.buttonCell = overlayPlacementCell()
        form.children.add(row("Overlay layer", overlayPlacementChoice))
        form.children.add(sliderRow("Overlay opacity", overlayOpacity, Label(), "%.2f"))
        installSliderReset(panelOpacity, 0.96)
        installSliderReset(overlayOpacity, 1.0)
        installSchemeColorReset(imagePadColor, { it.imagePadColor }, "#0A0D10")
        installSchemeColorReset(backgroundColor, { it.backgroundColor }, "#161B22")
        installSchemeColorReset(panelColor, { it.panelColor }, "#EFE8D7")
        installSchemeColorReset(frameColor, { it.frameColor }, "#D9C28E")
        installSchemeColorReset(accentColor, { it.accentColor }, "#8C8068")
        installSchemeColorReset(overlayColor, { it.overlayColor }, "#C9B37A")
        form.children.add(Button("Reload overlays").apply { setOnAction { loadOverlays() } })
        form.children.add(helperLabel("Frames only puts the overlay behind the image and text boxes; Over content places it on top of the complete card."))

        backgroundColor.valueProperty().addListener { _, _, _ -> if (!suppressEditorUpdates) updateFromEditor() }
        overlayOpacity.valueProperty().addListener { _, _, _ -> if (!suppressEditorUpdates) updateFromEditor() }
        overlayColor.valueProperty().addListener { _, _, _ -> if (!suppressEditorUpdates && currentIndex in visibleImages.indices) { loadBackgroundOverlayImage(); updateFromEditor() } }
        backgroundOverlayChoice.valueProperty().addListener { _, _, option ->
            if (!suppressEditorUpdates && currentIndex in visibleImages.indices) {
                captureUndoSnapshot()
                currentData.backgroundOverlay = option?.path?.fileName?.toString() ?: ""
                loadBackgroundOverlayImage()
                updateFromEditor()
            }
        }
        overlayPlacementChoice.valueProperty().addListener { _, _, value ->
            if (!suppressEditorUpdates && value != null && currentIndex in visibleImages.indices) {
                captureUndoSnapshot()
                currentData.backgroundOverlayPlacement = value
                render()
                updateFromEditor(renderPreview = false)
            }
        }

        form.children.add(section("Appearance"))
        listOf(panelColor, frameColor, accentColor).forEach { picker ->
            picker.valueProperty().addListener { _, _, _ -> if (!suppressEditorUpdates) updateFromEditor() }
        }
        form.children.add(row("Panel", panelColor))
        form.children.add(row("Frame", frameColor))
        form.children.add(row("Accent", accentColor))
        form.children.add(row("Border", border))
        form.children.add(row("Corner radius", radius))
        form.children.add(sliderRow("Description background opacity", panelOpacity, Label(), "%.2f"))
        form.children.add(row("Title size", titleSize))
        form.children.add(row("Body size", bodySize))

        return ScrollPane(form).apply { isFitToWidth = true }
    }

    private fun statusCell() = object : ListCell<CardStatus>() {
        override fun updateItem(item: CardStatus?, empty: Boolean) {
            super.updateItem(item, empty)
            text = if (empty || item == null) null else statusLabel(item)
        }
    }

    private fun schemeCell() = object : ListCell<ColorScheme>() {
        override fun updateItem(item: ColorScheme?, empty: Boolean) {
            super.updateItem(item, empty)
            if (empty || item == null) {
                text = null
                graphic = null
                tooltip = null
                return
            }
            val tone = if (item.resolvedTone == SchemeTone.LIGHT) "☀ Light" else "◐ Dark"
            text = "$tone · ${item.name}"
            graphic = HBox(4.0).apply {
                listOf(item.backgroundColor, item.panelColor, item.frameColor, item.accentColor, item.overlayColor).forEach { hex ->
                    children.add(Region().apply {
                        minWidth = 12.0; maxWidth = 12.0; minHeight = 12.0; maxHeight = 12.0
                        style = "-fx-background-color:$hex;-fx-background-radius:3px;-fx-border-color:rgba(255,255,255,0.25);-fx-border-radius:3px;"
                    })
                }
            }
            val warnings = SchemeContrast.warnings(item)
            tooltip = Tooltip(buildString {
                append(item.description)
                append("\nCard background: ${item.backgroundColor}\nOverlay tint: ${item.overlayColor}")
                if (warnings.isNotEmpty()) {
                    append("\n\nContrast warnings:")
                    warnings.forEach { append("\n• ").append(it) }
                }
            })
        }
    }

    private fun templateCell() = object : ListCell<CardTemplate>() {
        override fun updateItem(item: CardTemplate?, empty: Boolean) {
            super.updateItem(item, empty)
            text = if (empty || item == null) null else item.name
            tooltip = if (empty || item == null) null else Tooltip(item.description)
        }
    }

    private fun imageModeCell() = object : ListCell<ImageMode>() {
        override fun updateItem(item: ImageMode?, empty: Boolean) {
            super.updateItem(item, empty)
            text = when {
                empty || item == null -> null
                item == ImageMode.COVER -> "Crop to Fill"
                item == ImageMode.CONTAIN -> "Fit + Pad"
                else -> "Stretch"
            }
        }
    }

    private fun overlayCell() = object : ListCell<BackgroundOverlay>() {
        override fun updateItem(item: BackgroundOverlay?, empty: Boolean) {
            super.updateItem(item, empty)
            text = if (empty || item == null) null else item.name
            tooltip = if (empty || item == null) null else Tooltip(item.description)
        }
    }

    private fun overlayPlacementCell() = object : ListCell<OverlayPlacement>() {
        override fun updateItem(item: OverlayPlacement?, empty: Boolean) {
            super.updateItem(item, empty)
            text = when {
                empty || item == null -> null
                item == OverlayPlacement.FRAMES_ONLY -> "Frames only"
                else -> "Over content"
            }
        }
    }

    private fun statusLabel(status: CardStatus): String = when (status) {
        CardStatus.NEW -> "New"
        CardStatus.IN_PROGRESS -> "In progress"
        CardStatus.READY -> "Ready"
        CardStatus.EXPORTED -> "Exported"
        CardStatus.ARCHIVED -> "Archived"
    }

    private fun helperLabel(text: String) = Label(text).apply {
        isWrapText = true
        textFill = Color.web("#8B949E")
        style = "-fx-font-size:12px;"
    }

    private fun section(text: String) = Label(text).apply {
        style = "-fx-font-weight:bold;-fx-font-size:16px;-fx-padding:8 0 3 0;"
    }

    private fun doubleSpinner(minimum: Double, maximum: Double, initial: Double, amount: Double): Spinner<Double> =
        Spinner<Double>().apply {
            valueFactory = SpinnerValueFactory.DoubleSpinnerValueFactory(minimum, maximum, initial, amount)
            valueFactory.valueProperty().addListener { _, _, _ -> if (!suppressEditorUpdates) updateFromEditor() }
            addEventFilter(MouseEvent.MOUSE_CLICKED) { event ->
                if (event.button == MouseButton.PRIMARY && event.clickCount == 2) {
                    suppressEditorUpdates = true
                    try { valueFactory.value = initial } finally { suppressEditorUpdates = false }
                    updateFromEditor()
                    event.consume()
                }
            }
        }

    private fun installSliderReset(slider: Slider, defaultValue: Double) {
        slider.addEventFilter(MouseEvent.MOUSE_CLICKED) { event ->
            if (event.button == MouseButton.PRIMARY && event.clickCount == 2) {
                suppressEditorUpdates = true
                try { slider.value = defaultValue } finally { suppressEditorUpdates = false }
                updateFromEditor()
                event.consume()
            }
        }
    }

    private fun installSchemeColorReset(picker: ColorPicker, color: (ColorScheme) -> String, fallback: String) {
        picker.addEventFilter(MouseEvent.MOUSE_CLICKED) { event ->
            if (event.button == MouseButton.PRIMARY && event.clickCount == 2) {
                val target = schemeChoice.value?.let { safeColor(color(it), fallback) } ?: safeColor(fallback, fallback)
                suppressEditorUpdates = true
                try { picker.value = target } finally { suppressEditorUpdates = false }
                updateFromEditor()
                event.consume()
            }
        }
    }

    private fun row(label: String, node: Node): HBox = HBox(8.0).apply {
        alignment = Pos.CENTER_LEFT
        children.add(Label(label).apply { minWidth = 112.0 })
        HBox.setHgrow(node, Priority.ALWAYS)
        children.add(node)
    }

    private fun rowWithDice(label: String, node: Node, action: () -> Unit): HBox = HBox(6.0).apply {
        alignment = Pos.CENTER_LEFT
        children.add(Label(label).apply { minWidth = 112.0 })
        children.add(Button("⚄").apply {
            accessibleText = "Randomize $label"
            tooltip = Tooltip("Randomize $label")
            minWidth = 28.0
            maxWidth = 28.0
            setOnAction { action() }
        })
        HBox.setHgrow(node, Priority.ALWAYS)
        children.add(node)
    }

    private fun sliderRow(label: String, slider: Slider, value: Label, format: String): HBox = HBox(8.0).apply {
        alignment = Pos.CENTER_LEFT
        children.add(Label(label).apply { minWidth = 112.0 })
        children.add(slider)
        children.add(value)
        HBox.setHgrow(slider, Priority.ALWAYS)
        updateValueLabel(value, slider.value, format)
    }

    private fun updateValueLabel(label: Label, value: Double, format: String) { label.text = format.format(value) }

    private fun statusBar() = HBox(statusBarLabel).apply { padding = Insets(6.0, 12.0, 6.0, 12.0) }

    private fun randomGenerator(): kotlin.random.Random {
        val now = java.time.Instant.now()
        val micros = now.epochSecond * 1_000_000L + now.nano / 1_000L
        val seed = micros xor System.nanoTime() xor randomSequence.incrementAndGet() xor UUID.randomUUID().mostSignificantBits
        return kotlin.random.Random(seed)
    }

    private fun randomizeCardStyleAndNumbers() {
        captureUndoSnapshot()
        if (currentIndex !in visibleImages.indices) return
        val random = randomGenerator()
        suppressEditorUpdates = true
        try {
            applyRandomScheme(random)
            currentData.cost = random.nextInt(0, 10).toString()
            val attack = random.nextInt(0, 13)
            val defense = random.nextInt(0, 13)
            currentData.stats = "$attack / $defense"
            fields["cost"]?.text = currentData.cost
            fields["stats"]?.text = currentData.stats
            populateColorPickersFromData()
        } finally {
            suppressEditorUpdates = false
        }
        recalculatePanControls(resetPan = false, syncFromData = true)
        updateFromEditor(renderPreview = false)
        render()
        statusBarLabel.text = "Randomized scheme and numeric values • ${relativePath(visibleImages[currentIndex])}"
    }

    private fun applyRandomScheme(random: kotlin.random.Random = randomGenerator()) {
        val scheme = schemes.randomOrNull(random) ?: return
        scheme.applyTo(currentData)
        schemeChoice.value = scheme
        populateColorPickersFromData()
    }

    private fun randomizeScheme() {
        if (currentIndex !in visibleImages.indices) return
        captureUndoSnapshot()
        suppressEditorUpdates = true
        try { applyRandomScheme() } finally { suppressEditorUpdates = false }
        updateFromEditor(renderPreview = false)
        render()
    }

    private fun randomizeCost() {
        if (currentIndex !in visibleImages.indices) return
        captureUndoSnapshot()
        suppressEditorUpdates = true
        try {
            val value = randomGenerator().nextInt(0, 10).toString()
            currentData.cost = value
            fields["cost"]?.text = value
        } finally { suppressEditorUpdates = false }
        updateFromEditor()
    }

    private fun randomizeStats() {
        if (currentIndex !in visibleImages.indices) return
        captureUndoSnapshot()
        suppressEditorUpdates = true
        try {
            val r = randomGenerator()
            val value = "${r.nextInt(0, 13)} / ${r.nextInt(0, 13)}"
            currentData.stats = value
            fields["stats"]?.text = value
        } finally { suppressEditorUpdates = false }
        updateFromEditor()
    }

    private fun randomizeCollectorNumber() {
        if (currentIndex !in visibleImages.indices) return
        val db = database ?: return
        val current = currentData.collectorNumber
        val total = current.substringAfter('/', "100").toIntOrNull()?.coerceAtLeast(1) ?: 100
        val used = usedCollectorNumbersForCollection(currentData.assetId)
        val available = (1..total).filter { "%03d/%d".format(it, total) !in used }
        if (available.isEmpty()) {
            statusBarLabel.text = "No unused collector numbers remain in this collection."
            return
        }
        captureUndoSnapshot()
        val picked = available[randomGenerator().nextInt(available.size)]
        suppressEditorUpdates = true
        try {
            val formatted = "%03d/%d".format(picked, total)
            currentData.collectorNumber = formatted
            fields["collectorNumber"]?.text = formatted
        } finally { suppressEditorUpdates = false }
        updateFromEditor()
    }

    private fun randomizeArtistPattern() {
        if (currentIndex !in visibleImages.indices) return
        captureUndoSnapshot()
        suppressEditorUpdates = true
        try {
            val pattern = artistPatterns.random()
            currentData.artist = pattern
            fields["artist"]?.text = pattern
        } finally { suppressEditorUpdates = false }
        updateFromEditor()
    }

    private fun randomizeTitle() {
        if (currentIndex !in visibleImages.indices) return
        captureUndoSnapshot()
        val first = listOf("Aether", "Silent", "Astral", "Gilded", "Crimson", "Verdant", "Moonlit", "Arcane", "Runed", "Fallen").random()
        val second = listOf("Warden", "Oracle", "Pilgrim", "Herald", "Guardian", "Voyager", "Seer", "Sovereign", "Relic", "Champion").random()
        val value = "$first $second"
        suppressEditorUpdates = true
        try { currentData.title = value; fields["title"]?.text = value } finally { suppressEditorUpdates = false }
        updateFromEditor()
    }

    private fun randomizeTypeLine() {
        if (currentIndex !in visibleImages.indices) return
        captureUndoSnapshot()
        val value = listOf("CREATURE — MYSTIC", "LEGENDARY CHARACTER", "ARTIFACT — RELIC", "SORCERY — RITUAL", "SPELL — ARCANE", "ALLY — KNIGHT").random()
        suppressEditorUpdates = true
        try { currentData.typeLine = value; fields["typeLine"]?.text = value } finally { suppressEditorUpdates = false }
        updateFromEditor()
    }

    private fun randomizeRarity() {
        if (currentIndex !in visibleImages.indices) return
        captureUndoSnapshot()
        val value = listOf("COMMON", "UNCOMMON", "RARE", "MYTHIC", "LEGENDARY").random()
        suppressEditorUpdates = true
        try { currentData.rarity = value; fields["rarity"]?.text = value } finally { suppressEditorUpdates = false }
        updateFromEditor()
    }

    private fun randomizeSetName() {
        if (currentIndex !in visibleImages.indices) return
        captureUndoSnapshot()
        val value = listOf("ECLIPSE", "VERDANT ARCHIVES", "CROWN OF STARS", "FORGOTTEN REALMS", "IRON HORIZON", "MOONFALL", "ASHEN OATH").random()
        suppressEditorUpdates = true
        try { currentData.setName = value; fields["setName"]?.text = value } finally { suppressEditorUpdates = false }
        updateFromEditor()
    }

    private fun randomizeOverlay() {
        if (currentIndex !in visibleImages.indices || overlays.isEmpty()) return
        captureUndoSnapshot()
        val option = overlays.random()
        suppressEditorUpdates = true
        try {
            backgroundOverlayChoice.value = option
            currentData.backgroundOverlay = option.path?.fileName?.toString().orEmpty()
            loadBackgroundOverlayImage(allowRender = false)
        } finally { suppressEditorUpdates = false }
        updateFromEditor(renderPreview = false)
        render()
    }

    private val artistPatterns = listOf(
        "˚₊‧꒰ა ☆ ໒꒱ ‧₊˚",
        "⋆｡°✩༺☆༻✩°｡⋆",
        "｡₊˚༺❦༻˚₊｡",
        "༄︵‿︵༄",
        "✧･ﾟ: *✧･ﾟ:*",
        "⋆｡ﾟ✶°✧⋆",
        "༶•┈┈୨♡୧┈┈•༶",
        "╰┈➤ ✦ ╰┈➤",
        "˚ ༘♡ ⋆｡˚",
        "⟡ ─── ✦ ─── ⟡",
        "୨୧ ‧₊˚ ⋅",
        "☾⋆⁺₊✧",
        "༺═────────═༻",
        "✦₊˚.⋆ ☽ ⋆⁺₊✧",
        "꧁༺ ✧ ༻꧂"
    )

    private fun applySelectedScheme() {
        captureUndoSnapshot()
        if (currentIndex !in visibleImages.indices) return
        val scheme = schemeChoice.value ?: return
        suppressEditorUpdates = true
        try {
            currentData.schemeName = scheme.name
            scheme.applyTo(currentData)
            populateColorPickersFromData()
        } finally {
            suppressEditorUpdates = false
        }
        updateFromEditor(renderPreview = false)
        render()
        statusBarLabel.text = "Applied scheme '${scheme.name}' • ${relativePath(visibleImages[currentIndex])}"
    }

    private fun loadSchemes() {
        val (loaded, errors) = SchemeRepository.load()
        schemes = loaded
        suppressEditorUpdates = true
        try {
            schemeChoice.items.setAll(schemes)
            schemeChoice.setCellFactory { schemeCell() }
            schemeChoice.buttonCell = schemeCell()
            schemeChoice.value = schemes.firstOrNull { it.name == currentData.schemeName } ?: schemes.firstOrNull()
        } finally {
            suppressEditorUpdates = false
        }
        val contrastWarningCount = schemes.sumOf { SchemeContrast.warnings(it).size }
        statusBarLabel.text = when {
            schemes.isEmpty() -> "No color schemes found in schemes/."
            errors.isEmpty() -> "Loaded ${schemes.size} color schemes."
            else -> "Loaded ${schemes.size} color schemes; ${errors.size} issue(s) reported."
        } + if (contrastWarningCount > 0) " • ${contrastWarningCount} contrast warning(s)" else ""
    }

    private fun loadOverlays() {
        val (loaded, errors) = OverlayRepository.load()
        overlays = loaded
        suppressEditorUpdates = true
        try {
            backgroundOverlayChoice.items.setAll(overlays)
            backgroundOverlayChoice.value = overlays.firstOrNull { it.path?.fileName?.toString() == currentData.backgroundOverlay }
                ?: overlays.firstOrNull { it.path == null }
                ?: overlays.firstOrNull()
        } finally {
            suppressEditorUpdates = false
        }
        loadBackgroundOverlayImage()
        statusBarLabel.text = when {
            loaded.size <= 1 -> "No SVG overlays found in overlays/."
            errors.isEmpty() -> "Loaded ${loaded.size - 1} SVG overlays."
            else -> "Loaded ${loaded.size - 1} SVG overlays; ${errors.size} invalid file(s) ignored."
        }
    }

    private fun loadTemplates() {
        val (loaded, errors) = TemplateRepository.load()
        templates = loaded
        if (collectionDefaultTemplateName.isBlank()) {
            collectionDefaultTemplateName = database?.getDefaultTemplateName().orEmpty().ifBlank { templates.firstOrNull()?.name.orEmpty() }
        }
        suppressEditorUpdates = true
        try {
            templateChoice.items.setAll(templates)
            templateChoice.value = templates.firstOrNull { it.name == currentData.templateName }
                ?: templates.firstOrNull { it.name == collectionDefaultTemplateName }
                ?: templates.firstOrNull()
            collectionTemplateChoice.items.setAll(templates)
            collectionTemplateChoice.value = templates.firstOrNull { it.name == collectionDefaultTemplateName } ?: templates.firstOrNull()
        } finally {
            suppressEditorUpdates = false
        }
        if (currentIndex in visibleImages.indices) {
            currentData.templateName = if (templateOverride.isSelected) (templateChoice.value?.name ?: currentData.templateName) else ""
            loadTemplateAndOverlay()
            render()
        }
        statusBarLabel.text = when {
            templates.isEmpty() -> "No templates found in templates/."
            errors.isEmpty() -> "Loaded ${templates.size} card templates."
            else -> "Loaded ${templates.size} card templates; ${errors.size} invalid file(s) ignored."
        }
    }

    private fun setCollectionDefaultTemplate(template: CardTemplate) {
        val db = database ?: return
        if (currentIndex in visibleImages.indices && !saveCurrent(showStatus = false)) return
        collectionDefaultTemplateName = template.name
        db.setDefaultTemplateName(template.name)
        synchronized(cardThumbnailCache) { cardThumbnailCache.clear() }
        suppressEditorUpdates = true
        try {
            collectionTemplateChoice.value = template
        } finally {
            suppressEditorUpdates = false
        }
        if (!templateOverride.isSelected && currentIndex in visibleImages.indices) {
            loadTemplateAndOverlay()
            recalculatePanControls(resetPan = false)
            render()
            populateEditor()
        }
        statusBarLabel.text = "Collection layout: ${template.name} · cards without overrides will follow it"
    }

    private fun currentTemplate(): CardTemplate? = templateForData(currentData)


    private fun loadTemplateAndOverlay() {
        val template = currentTemplate() ?: return
        templateImage = TemplateRepository.rasterize(template)
        backgroundOverlayImage = overlays.firstOrNull { it.path?.fileName?.toString() == currentData.backgroundOverlay }
            ?.path?.let { OverlayRepository.rasterize(it, template.width, template.height, currentData.overlayColor) }
    }

    private fun loadBackgroundOverlayImage(allowRender: Boolean = true) {
        loadTemplateAndOverlay()
        if (allowRender && currentIndex in visibleImages.indices) render()
    }

    private fun chooseRecentCollectionOnStartup(stage: Stage) {
        if (collectionRoot != null) return
        when (val choice = StartupCatalogChooser.choose(stage, RecentCatalogs.list())) {
            is StartupCatalogChooser.Choice.Open -> openCollectionPath(choice.path)
            StartupCatalogChooser.Choice.Browse -> {
                val directory = DirectoryChooser().apply { title = "Choose Image Directory" }.showDialog(stage)?.toPath()
                if (directory != null) openCollectionPath(directory)
            }
            StartupCatalogChooser.Choice.Cancel -> Unit
        }
    }

    private fun collectionActions(): CollectionEditorActions? = database?.let { db ->
        CollectionEditorActions(db) { allImages.toList() }
    }

    private fun applyCollectionDefaultTemplateToAll() {
        val result = collectionActions()?.applyDefaultTemplateToAll() ?: return
        cardDataCache.clear()
        synchronized(cardThumbnailCache) { cardThumbnailCache.clear() }
        if (currentIndex in visibleImages.indices) select(currentIndex)
        statusBarLabel.text = "Collection default template applied to ${result.changedCards} card(s)."
    }

    private fun applyCurrentSetNameToAll() {
        val setName = currentData.setName.trim()
        if (setName.isBlank()) { statusBarLabel.text = "Enter a set name on the selected card first."; return }
        val result = collectionActions()?.applySetNameToAll(setName) ?: return
        cardDataCache.clear()
        synchronized(cardThumbnailCache) { cardThumbnailCache.clear() }
        if (currentIndex in visibleImages.indices) select(currentIndex)
        statusBarLabel.text = "Set name applied to ${result.changedCards} card(s)."
    }

    private fun normalizeCollectorTotals() {
        val result = collectionActions()?.normalizeCollectorTotals() ?: return
        cardDataCache.clear()
        synchronized(cardThumbnailCache) { cardThumbnailCache.clear() }
        if (currentIndex in visibleImages.indices) select(currentIndex)
        statusBarLabel.text = "Collector-number totals fixed on ${result.changedCards} card(s)."
    }

    private fun openDirectory(stage: Stage) {
        val directory = DirectoryChooser().apply { title = "Choose Image Directory" }.showDialog(stage)?.toPath() ?: return
        openCollectionPath(directory)
    }

    private fun openCollectionPath(directory: Path) {
        if (!saveCurrent(showStatus = false)) return
        val normalized = directory.toAbsolutePath().normalize()
        scanTask?.cancel()
        filterTask?.cancel()
        val token = generation.incrementAndGet()
        statusBarLabel.text = "Scanning ${normalized.fileName}…"
        val task = object : Task<ScanResult>() {
            override fun call(): ScanResult = scanImages(normalized)
        }
        scanTask = task
        task.setOnSucceeded {
            if (generation.get() != token) return@setOnSucceeded
            try {
                val newDatabase = CollectionDatabase.open(normalized)
                database?.close()
                database = newDatabase
                collectionRoot = normalized
                collectionPresentation = newDatabase.getCollectionPresentation()
                collectionDefaultTemplateName = newDatabase.getDefaultTemplateName().ifBlank { templates.firstOrNull()?.name.orEmpty() }
                if (collectionDefaultTemplateName.isNotBlank()) newDatabase.setDefaultTemplateName(collectionDefaultTemplateName)
                nestedCollectionRoots = task.value.nestedCollections
                suppressEditorUpdates = true
                try {
                    collectionTemplateChoice.items.setAll(templates)
                    collectionTemplateChoice.value = templates.firstOrNull { it.name == collectionDefaultTemplateName } ?: templates.firstOrNull()
                    if (::collectionSettingsPane.isInitialized) collectionSettingsPane.setPresentation(collectionPresentation)
                    refreshCollectionChoices()
                } finally { suppressEditorUpdates = false }
                RecentCatalogs.record(normalized)
                startCollectionWatcher(normalized)
                allImages.clear()
                allImages.addAll(task.value.images)
                migrateLegacySidecars(newDatabase, allImages)
                visibleImages.clear()
                currentIndex = -1
                currentLoadedPath = null
                currentData = newCardDefaults()
                selectedFolder = normalized
                synchronized(thumbnailCache) { thumbnailCache.clear() }
                synchronized(cardThumbnailCache) { cardThumbnailCache.clear() }
                fullImageCache.clear()
                TemplateRepository.clearCache()
                OverlayRepository.clearCache()
                searchIndex.clear()
                searchIndex.putAll(newDatabase.searchIndex())
                cardDataCache.clear()
                cropImage = null
                backgroundOverlayImage = null
                templateImage = null
                renderedCard = null
                previewHost.children.clear()
                StartupProfiler.measure("folder tree build") {
                    buildFolderTree(normalized, normalized)
                }
                StartupProfiler.measure("browser rebuild/sort", detail = { "${visibleImages.size} visible" }) {
                    rebuildBrowserImmediately()
                    visibleImages.size
                }
                StartupProfiler.measure("first card select/render") {
                    if (visibleImages.isNotEmpty()) select(0) else clearEditorForNoSelection()
                }
                val nestedNote = if (nestedCollectionRoots.isNotEmpty()) "; ${nestedCollectionRoots.size} nested collection(s) available" else ""
                statusBarLabel.text = "Collection: ${normalized.fileName} • ${allImages.size} images • DB ${newDatabase.path.fileName}$nestedNote"
            } catch (e: Exception) {
                showError("Could not open collection", e)
            }
        }
        task.setOnFailed {
            if (generation.get() == token) showError("Could not scan collection", task.exception ?: RuntimeException("Unknown scanning error"))
        }
        Thread(task, "card-forge-scan").apply { isDaemon = true }.start()
    }

    private data class ScanResult(val images: List<Path>, val nestedCollections: List<Path>)

    private fun scanImages(root: Path): ScanResult {
        val found = mutableListOf<Path>()
        val nested = mutableSetOf<Path>()
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (Thread.currentThread().isInterrupted) return FileVisitResult.TERMINATE
                val name = dir.fileName?.toString()?.lowercase(Locale.ROOT).orEmpty()
                if (dir != root && (name == "card forge exports" || name == ".cardforge" || name == ".cardforge-exports" || name == "exports" && dir.parent?.fileName?.toString() == ".cardforge")) return FileVisitResult.SKIP_SUBTREE
                if (dir != root && Files.isRegularFile(dir.resolve(CollectionDatabase.FILE_NAME))) {
                    nested.add(dir.toAbsolutePath().normalize())
                    return FileVisitResult.SKIP_SUBTREE
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (Thread.currentThread().isInterrupted) return FileVisitResult.TERMINATE
                val extension = file.fileName.toString().substringAfterLast('.', "").lowercase(Locale.ROOT)
                if (attrs.isRegularFile && extension in supportedExtensions && !isGeneratedExportFile(file)) found.add(file.toAbsolutePath().normalize())
                return FileVisitResult.CONTINUE
            }
        })
        found.sortBy { root.relativize(it).toString().lowercase() }
        return ScanResult(found, nested.sortedBy { root.relativize(it).toString().lowercase() })
    }

    private fun isGeneratedExportFile(path: Path): Boolean {
        val name = path.fileName.toString().lowercase(Locale.ROOT)
        return name.endsWith(".card.png") ||
            name.endsWith(".card.svg") ||
            name.endsWith("-card.png") ||
            name.endsWith("-card.svg") ||
            name.contains("card-forge-export")
    }

    private fun refreshCollectionChoices() {
        val root = collectionRoot ?: return
        val options = buildList {
            add(CollectionOption(root, "${root.fileName} · Current collection"))
            nestedCollectionRoots.sortedBy { root.relativize(it).toString().lowercase() }.forEach {
                add(CollectionOption(it, "↳ ${root.relativize(it)}"))
            }
        }
        suppressCollectionChoice = true
        try {
            collectionChoice.items.setAll(options)
            collectionChoice.value = options.firstOrNull { it.root == root }
        } finally { suppressCollectionChoice = false }
    }

    private fun sortVisibleImagesInPlace() {
        if (visibleImages.size <= 1) return
        val comparator = Comparator<Path> { left, right ->
            val leftData = cardDataForSorting(left)
            val rightData = cardDataForSorting(right)
            val result = when (browserSortChoice.value ?: BrowserSort.NAME) {
                BrowserSort.NAME -> compareNatural(leftData.title, rightData.title)
                BrowserSort.LAST_WORD -> compareNatural(lastWord(leftData.title), lastWord(rightData.title))
                BrowserSort.COLLECTOR_NUMBER -> compareCollectorNumber(leftData.collectorNumber, rightData.collectorNumber)
                BrowserSort.FOLDER -> compareNatural(relativeFolder(left), relativeFolder(right))
                BrowserSort.STATUS -> compareNatural(leftData.status.name, rightData.status.name)
                BrowserSort.FILE_NAME -> compareNatural(left.fileName.toString(), right.fileName.toString())
            }
            if (result != 0) result else compareNatural(relativePath(left), relativePath(right))
        }
        visibleImages.sortWith(if (browserSortDescending.isSelected) comparator.reversed() else comparator)
    }

    private fun rebuildVisibleSorted() {
        val previousPath = imagesCurrentPath()
        sortVisibleImagesInPlace()
        currentIndex = previousPath?.let { visibleImages.indexOf(it) } ?: -1
        rebuildImageList(previousPath)
        if (previousPath != null && currentIndex >= 0) syncBrowserSelection(previousPath, false)
    }

    private fun cardDataForSorting(path: Path): CardData {
        val normalized = path.toAbsolutePath().normalize()
        cardDataCache[normalized]?.let { return it }
        val saved = runCatching { database?.dataSnapshotForPath(normalized) }.getOrNull()
        if (saved != null) {
            cardDataCache[normalized] = saved.copy()
            return saved
        }
        // Browser sorting must stay metadata-only. newCardDefaults() intentionally performs
        // image-derived scheme analysis, which is far too expensive to run for every unsaved
        // image while sorting on the JavaFX application thread.
        return lightweightCardData(normalized)
    }

    private fun lightweightCardData(path: Path): CardData = CardData(
        assetId = "",
        status = CardStatus.NEW,
        title = path.fileName?.toString()?.substringBeforeLast('.', path.fileName.toString()) ?: "",
        cost = "",
        typeLine = "",
        rarity = "",
        description = "",
        flavorText = "",
        artist = "",
        setName = "",
        collectorNumber = "",
        stats = "",
        templateName = ""
    )

    private fun lastWord(value: String): String = value.trim().split(Regex("\\s+")).lastOrNull().orEmpty()

    private fun relativeFolder(path: Path): String = path.parent?.let { relativePath(it) }.orEmpty()

    private fun compareNatural(a: String, b: String): Int {
        // Human-style comparison: digits sort numerically while text remains case-insensitive.
        val aa = a.trim()
        val bb = b.trim()
        val re = Regex("(\\d+|\\D+)")
        val at = re.findAll(aa).toList()
        val bt = re.findAll(bb).toList()
        val count = min(at.size, bt.size)
        for (i in 0 until count) {
            val ax = at[i].value
            val bx = bt[i].value
            val cmp = if (ax.all(Char::isDigit) && bx.all(Char::isDigit)) {
                (ax.trimStart('0').ifBlank { "0" }.toBigIntegerOrNull() ?: java.math.BigInteger.ZERO)
                    .compareTo(bx.trimStart('0').ifBlank { "0" }.toBigIntegerOrNull() ?: java.math.BigInteger.ZERO)
            } else ax.compareTo(bx, ignoreCase = true)
            if (cmp != 0) return cmp
        }
        return at.size.compareTo(bt.size).takeIf { it != 0 } ?: aa.compareTo(bb, ignoreCase = true)
    }

    private fun compareCollectorNumber(a: String, b: String): Int {
        fun parsed(value: String): Pair<Int, Int> {
            val match = Regex("^(\\d+)\\s*/\\s*(\\d+)").find(value.trim())
            return if (match != null) (match.groupValues[1].toIntOrNull() ?: Int.MAX_VALUE) to (match.groupValues[2].toIntOrNull() ?: Int.MAX_VALUE)
            else (Int.MAX_VALUE) to (Int.MAX_VALUE)
        }
        val pa = parsed(a); val pb = parsed(b)
        val first = pa.first.compareTo(pb.first)
        return if (first != 0) first else {
            val second = pa.second.compareTo(pb.second)
            if (second != 0) second else compareNatural(a, b)
        }
    }

    private fun rebuildBrowserImmediately() {
        val rootPath = collectionRoot ?: return
        val scope = selectedFolder?.takeIf { it.startsWith(rootPath) } ?: rootPath
        val query = filterField.text.trim().lowercase()
        val previousPath = imagesCurrentPath()
        visibleImages.clear()
        visibleImages.addAll(allImages.filter { it.startsWith(scope) }.filter { query.isBlank() || searchableText(it).contains(query) })
        sortVisibleImagesInPlace()
        val targetIndex = previousPath?.let { visibleImages.indexOf(it) } ?: -1
        browserCountLabel.text = if (query.isBlank() && scope == rootPath) "${visibleImages.size} images" else "${visibleImages.size}/${allImages.size} images"
        rebuildImageList()
        when {
            targetIndex >= 0 -> select(targetIndex, scrollIntoView = false)
            visibleImages.isNotEmpty() -> select(0, scrollIntoView = false)
            else -> { currentIndex = -1; clearEditorForNoSelection() }
        }
    }

    private fun requestVisibleImagesRebuild() {
        val rootPath = collectionRoot ?: return
        val scope = selectedFolder?.takeIf { it.startsWith(rootPath) } ?: rootPath
        val query = filterField.text.trim().lowercase()
        val previousPath = imagesCurrentPath()
        if (query.isBlank()) {
            visibleImages.clear()
            visibleImages.addAll(allImages.filter { it.startsWith(scope) })
            sortVisibleImagesInPlace()
            setCurrentPathAfterRebuild(previousPath)
            browserCountLabel.text = if (scope == rootPath) "${visibleImages.size} images" else "${visibleImages.size}/${allImages.size} images"
            rebuildImageList()
            return
        }
        filterTask?.cancel()
        val token = generation.get()
        val snapshot = HashMap(searchIndex)
        statusBarLabel.text = "Filtering ${allImages.size} images…"
        val task = object : Task<List<Path>>() {
            override fun call(): List<Path> {
                val result = ArrayList<Path>()
                for (path in allImages) {
                    if (isCancelled) return emptyList()
                    if (!path.startsWith(scope)) continue
                    if (searchableText(path, snapshot).contains(query)) result.add(path)
                }
                return result
            }
        }
        filterTask = task
        task.setOnSucceeded {
            if (generation.get() != token) return@setOnSucceeded
            visibleImages.clear()
            visibleImages.addAll(task.value)
            sortVisibleImagesInPlace()
            setCurrentPathAfterRebuild(previousPath)
            browserCountLabel.text = "${visibleImages.size}/${allImages.size} images"
            rebuildImageList()
            if (currentIndex >= 0) refreshBrowserSelectionStyles()
            statusBarLabel.text = "Filter: ${visibleImages.size}/${allImages.size} images match."
        }
        task.setOnFailed {
            if (generation.get() == token) showError("Could not filter images", task.exception ?: RuntimeException("Unknown filtering error"))
        }
        Thread(task, "card-forge-filter").apply { isDaemon = true }.start()
    }

    private fun setCurrentPathAfterRebuild(previousPath: Path?) {
        val found = previousPath?.let { visibleImages.indexOf(it) } ?: -1
        when {
            found >= 0 -> select(found, scrollIntoView = false)
            visibleImages.isNotEmpty() -> select(0, scrollIntoView = false)
            else -> {
                currentIndex = -1
                clearEditorForNoSelection()
            }
        }
    }

    private fun buildFolderTree(rootPath: Path, selected: Path) {
        val expandedBefore = mutableSetOf<Path>()
        folderTree.root?.let { rememberExpanded(it, expandedBefore) }
        val rootItem = TreeItem(rootPath)
        val directoryItems = mutableMapOf(rootPath to rootItem)
        val dirs = mutableSetOf(rootPath)
        allImages.forEach { image ->
            var dir = image.parent
            while (dir != null && dir.startsWith(rootPath) && dir != rootPath) {
                dirs.add(dir)
                dir = dir.parent
            }
        }
        nestedCollectionRoots.forEach { collection ->
            var dir: Path? = collection
            while (dir != null && dir.startsWith(rootPath) && dir != rootPath) {
                dirs.add(dir)
                dir = dir.parent
            }
        }
        dirs.filter { it != rootPath }
            .sortedBy { rootPath.relativize(it).toString().lowercase() }
            .forEach { dir ->
                val parentItem = directoryItems[dir.parent] ?: return@forEach
                val item = TreeItem(dir)
                directoryItems[dir] = item
                parentItem.children.add(item)
            }
        fun sort(item: TreeItem<Path>) {
            item.children.sortBy { it.value.fileName.toString().lowercase() }
            item.children.forEach(::sort)
        }
        sort(rootItem)
        directoryItems.values.forEach { item ->
            val path = item.value
            item.isExpanded = path in expandedBefore || path == rootPath || selected.startsWith(path)
        }
        suppressFolderSelection = true
        try {
            folderTree.root = rootItem
            folderTree.selectionModel.select(directoryItems[selected] ?: rootItem)
        } finally {
            suppressFolderSelection = false
        }
    }

    private fun rememberExpanded(item: TreeItem<Path>, into: MutableSet<Path>) {
        if (item.isExpanded) into.add(item.value)
        item.children.forEach { rememberExpanded(it, into) }
    }

    private fun applyBrowserFilter() {
        if (currentIndex in visibleImages.indices && !saveCurrent(showStatus = false)) return
        requestVisibleImagesRebuild()
    }

    private fun searchableText(path: Path, snapshot: Map<String, String> = searchIndex): String {
        val relative = relativePath(path)
        val cached = snapshot[relative]
        if (cached != null) return cached
        val data = database?.dataSnapshotForPath(path) ?: lightweightCardData(path)
        val json = runCatching { JsonSupport.mapper.writeValueAsString(data) }.getOrDefault("")
        val searchable = ("$relative ${path.fileName} ${data.assetId} ${data.status.name} $json").lowercase()
        if (snapshot === searchIndex) searchIndex[relative] = searchable
        return searchable
    }

    private fun resolveCardDataForSelection(path: Path): CardData? {
        val normalized = path.toAbsolutePath().normalize()
        val db = database ?: return cardDataCache[normalized]?.copy() ?: newCardDefaults(normalized)
        val dbData = db.dataSnapshotForPath(normalized)
        if (dbData != null && hasMeaningfulCardData(dbData)) {
            if (dbData.collectorNumber.isBlank()) {
                dbData.collectorNumber = nextUnusedCollectorNumber()
                db.save(normalized, dbData)
            }
            return dbData
        }
        // Unsaved cards may already have deterministic in-memory defaults generated for a
        // card thumbnail. Reuse them so selecting the card does not randomize it again or
        // decode the image a second time.
        cardDataCache[normalized]?.let { cached ->
            return cached.copy().also { data ->
                dbData?.assetId?.takeIf { it.isNotBlank() }?.let { data.assetId = it }
            }
        }
        return newCardDefaults(normalized).also { defaults ->
            dbData?.assetId?.takeIf { it.isNotBlank() }?.let { defaults.assetId = it }
        }
    }

    private fun dataEquivalent(a: CardData, b: CardData): Boolean = runCatching {
        JsonSupport.mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(a) ==
            JsonSupport.mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(b)
    }.getOrDefault(false)

    private fun newCardDefaults(
        path: Path? = null,
        derivedColorOverride: Color? = null,
        analyzeImageIfNeeded: Boolean = true
    ): CardData {
        val random = randomGenerator()
        val data = CardData(
            assetId = UUID.randomUUID().toString(),
            status = CardStatus.NEW,
            title = path?.fileName?.toString()?.substringBeforeLast('.', path.fileName.toString()) ?: "CARD NAME",
            cost = random.nextInt(0, 10).toString(),
            typeLine = listOf("CREATURE — MYSTIC", "LEGENDARY CHARACTER", "ARTIFACT — RELIC", "SORCERY — RITUAL", "SPELL — ARCANE", "ALLY — KNIGHT").random(random),
            rarity = weightedRarity(random),
            artist = artistPatterns.random(random),
            collectorNumber = nextUnusedCollectorNumber(random),
            stats = "${random.nextInt(0, 13)} / ${random.nextInt(0, 13)}",
            templateName = templates.randomOrNull(random)?.name.orEmpty(),
            backgroundOverlay = "",
            imageMode = ImageMode.COVER,
            imageBleedOverFrame = false
        )
        val derived = derivedColorOverride ?: if (analyzeImageIfNeeded) path?.let(ImageColorAnalyzer::dominantColor) else null
        val scheme = derived?.let { ImageColorAnalyzer.bestMatchingScheme(it, schemes) } ?: schemes.randomOrNull(random)
        scheme?.applyTo(data)
        return data
    }

    private fun weightedRarity(random: kotlin.random.Random): String = when (random.nextInt(100)) {
        in 0..49 -> "COMMON"
        in 50..74 -> "UNCOMMON"
        in 75..91 -> "RARE"
        in 92..97 -> "MYTHIC"
        else -> "LEGENDARY"
    }

    private fun nextUnusedCollectorNumber(random: kotlin.random.Random = randomGenerator(), total: Int = 100): String {
        val used = database?.usedCollectorNumbers(null).orEmpty()
        val available = (1..total).map { "%03d/%d".format(it, total) }.filterNot(used::contains)
        return available.randomOrNull(random) ?: "%03d/%d".format(random.nextInt(1, total + 1), total)
    }

    private fun select(newIndex: Int, scrollIntoView: Boolean = false) {
        if (newIndex !in visibleImages.indices) return
        val imagePath = visibleImages[newIndex].toAbsolutePath().normalize()
        if (newIndex == currentIndex && currentLoadedPath == imagePath) {
            if (scrollIntoView) scrollToBrowserPath(imagePath, true)
            return
        }
        if (currentIndex in visibleImages.indices && !saveCurrent(showStatus = false)) return
        val resolvedData = resolveCardDataForSelection(imagePath) ?: return
        currentIndex = newIndex
        currentLoadedPath = imagePath
        currentData = resolvedData
        cardDataCache[imagePath] = resolvedData.copy()
        undoManager.clear()
        ignoreNextUndoCapture = false
        cropImage = cachedFullImage(imagePath)
        loadTemplateAndOverlay()
        populateEditor()
        render()
        refreshBrowserSelectionStyles()
        syncBrowserSelection(imagePath, scrollIntoView)
        statusBarLabel.text = "${newIndex + 1}/${visibleImages.size} • ${relativePath(imagePath)} • ${statusLabel(currentData.status)} • ID ${currentData.assetId.take(8)}"
    }

    private fun currentVisibleIndex(): Int {
        val loaded = currentLoadedPath?.toAbsolutePath()?.normalize()
        if (loaded != null) {
            return visibleImages.indexOfFirst { it.toAbsolutePath().normalize() == loaded }
        }
        return currentIndex.takeIf { it in visibleImages.indices } ?: -1
    }

    private fun imagesCurrentPath(): Path? {
        // Once a card has an identity, never infer another path from currentIndex.
        // This prevents insertion/deletion/reordering from saving one card into its neighbour.
        currentLoadedPath?.let { return it.toAbsolutePath().normalize() }
        return visibleImages.getOrNull(currentIndex)
    }

    private fun populateEditor() {
        suppressEditorUpdates = true
        try {
            fields["title"]?.text = currentData.title
            fields["cost"]?.text = currentData.cost
            fields["typeLine"]?.text = currentData.typeLine
            fields["rarity"]?.text = currentData.rarity
            fields["stats"]?.text = currentData.stats
            fields["artist"]?.text = currentData.artist
            fields["setName"]?.text = currentData.setName
            fields["collectorNumber"]?.text = currentData.collectorNumber
            description.text = currentData.description
            flavor.text = currentData.flavorText
            statusChoice.value = currentData.status
            schemeChoice.value = schemes.firstOrNull { it.name == currentData.schemeName } ?: schemes.firstOrNull()
            val effectiveTemplate = currentTemplate()
            templateChoice.value = effectiveTemplate
            templateOverride.isSelected = currentData.templateName.isNotBlank()
            imageMode.value = currentData.imageMode
            imageBleedOverFrame.isSelected = currentData.imageBleedOverFrame
            imageBleedOpacity.value = currentData.imageBleedOpacity.coerceIn(0.0, 1.0)
            imageBleedOpacityValue.text = "%.0f%%".format(imageBleedOpacity.value * 100.0)
            zoom.value = currentData.imageZoom.coerceIn(0.1, 4.0)
            populateColorPickersFromData()
            border.valueFactory.value = currentData.borderWidth
            radius.valueFactory.value = currentData.cornerRadius
            panelOpacity.value = currentData.panelOpacity
            overlayOpacity.value = currentData.backgroundOverlayOpacity.coerceIn(0.0, 1.0)
            titleSize.valueFactory.value = currentData.titleFontSize
            bodySize.valueFactory.value = currentData.bodyFontSize
            backgroundOverlayChoice.value = overlays.firstOrNull { it.path?.fileName?.toString() == currentData.backgroundOverlay }
                ?: overlays.firstOrNull { it.path == null }
                ?: overlays.firstOrNull()
            overlayPlacementChoice.value = currentData.backgroundOverlayPlacement
        } finally {
            suppressEditorUpdates = false
        }
        recalculatePanControls(resetPan = false, syncFromData = true)
        suppressUndoCapture = true
        try { updateFromEditor(renderPreview = false) } finally { suppressUndoCapture = false; ignoreNextUndoCapture = false }
    }

    private fun populateColorPickersFromData() {
        imagePadColor.value = safeColor(currentData.imagePadColor, "#0A0D10")
        backgroundColor.value = safeColor(currentData.backgroundColor, "#161B22")
        panelColor.value = safeColor(currentData.panelColor, "#EFE8D7")
        frameColor.value = safeColor(currentData.frameColor, "#D9C28E")
        accentColor.value = safeColor(currentData.accentColor, "#8C8068")
        overlayColor.value = safeColor(currentData.overlayColor, "#C9B37A")
    }

    private fun safeColor(hex: String, fallback: String): Color = runCatching { Color.web(hex) }.getOrElse { Color.web(fallback) }

    private fun setArtwork(mode: ImageMode, newZoom: Double, normalizedX: Double, normalizedY: Double) {
        captureUndoSnapshot()
        suppressEditorUpdates = true
        try {
            imageMode.value = mode
            zoom.value = newZoom.coerceIn(zoom.min, zoom.max)
            offsetX.value = normalizedX.coerceIn(-1.0, 1.0)
            offsetY.value = normalizedY.coerceIn(-1.0, 1.0)
        } finally {
            suppressEditorUpdates = false
        }
        recalculatePanControls(resetPan = false)
        updateFromEditor(renderPreview = false)
        refreshArtworkOnly()
    }

    private fun resetArtworkPositionAndZoom() {
        captureUndoSnapshot()
        suppressEditorUpdates = true
        try {
            zoom.value = 1.0
            offsetX.value = 0.0
            offsetY.value = 0.0
        } finally {
            suppressEditorUpdates = false
        }
        recalculatePanControls(resetPan = false)
        updateValueLabel(zoomValueLabel, zoom.value, "%.2f×")
        updateFromEditor(renderPreview = false)
        refreshArtworkOnly()
    }

    private fun centerArtwork() {
        captureUndoSnapshot()
        suppressEditorUpdates = true
        try {
            offsetX.value = 0.0
            offsetY.value = 0.0
        } finally {
            suppressEditorUpdates = false
        }
        updateValueLabelFromActualPan()
        updateFromEditor(renderPreview = false)
        refreshArtworkOnly()
    }

    private fun recalculatePanControls(resetPan: Boolean, syncFromData: Boolean = false) {
        currentData.imageZoom = zoom.value
        currentData.imageMode = imageMode.value ?: currentData.imageMode
        val template = currentTemplate() ?: return
        val layout = CardRenderer.imageLayout(cropImage, currentData, template)
        suppressEditorUpdates = true
        try {
            if (resetPan) {
                offsetX.value = 0.0
                offsetY.value = 0.0
            } else if (syncFromData) {
                offsetX.value = actualToSlider(currentData.imageOffsetX, layout.minOffsetX, layout.maxOffsetX)
                offsetY.value = actualToSlider(currentData.imageOffsetY, layout.minOffsetY, layout.maxOffsetY)
            } else {
                offsetX.value = offsetX.value.coerceIn(-1.0, 1.0)
                offsetY.value = offsetY.value.coerceIn(-1.0, 1.0)
            }
        } finally {
            suppressEditorUpdates = false
        }
        val actualX = sliderToActual(offsetX.value, layout.minOffsetX, layout.maxOffsetX)
        val actualY = sliderToActual(offsetY.value, layout.minOffsetY, layout.maxOffsetY)
        currentData.imageOffsetX = actualX
        currentData.imageOffsetY = actualY
        updateValueLabel(xValueLabel, actualX, "%+.0f px")
        updateValueLabel(yValueLabel, actualY, "%+.0f px")
    }

    private fun sliderToActual(value: Double, min: Double, max: Double): Double {
        if (max - min <= 1e-9) return 0.0
        val t = ((value + 1.0) / 2.0).coerceIn(0.0, 1.0)
        return min + (max - min) * t
    }

    private fun actualToSlider(value: Double, min: Double, max: Double): Double {
        if (max - min <= 1e-9) return 0.0
        val t = ((value.coerceIn(min, max) - min) / (max - min)).coerceIn(0.0, 1.0)
        return t * 2.0 - 1.0
    }

    private fun updateValueLabelFromActualPan() {
        val template = currentTemplate() ?: return
        val layout = CardRenderer.imageLayout(cropImage, currentData.copy(imageZoom = zoom.value, imageMode = imageMode.value ?: currentData.imageMode), template)
        updateValueLabel(xValueLabel, sliderToActual(offsetX.value, layout.minOffsetX, layout.maxOffsetX), "%+.0f px")
        updateValueLabel(yValueLabel, sliderToActual(offsetY.value, layout.minOffsetY, layout.maxOffsetY), "%+.0f px")
    }

    private fun currentActualPanX(): Double {
        val template = currentTemplate() ?: return 0.0
        val dataForLayout = currentData.copy(imageZoom = zoom.value, imageMode = imageMode.value ?: currentData.imageMode)
        val layout = CardRenderer.imageLayout(cropImage, dataForLayout, template)
        return sliderToActual(offsetX.value, layout.minOffsetX, layout.maxOffsetX)
    }

    private fun currentActualPanY(): Double {
        val template = currentTemplate() ?: return 0.0
        val dataForLayout = currentData.copy(imageZoom = zoom.value, imageMode = imageMode.value ?: currentData.imageMode)
        val layout = CardRenderer.imageLayout(cropImage, dataForLayout, template)
        return sliderToActual(offsetY.value, layout.minOffsetY, layout.maxOffsetY)
    }

    private fun cardSnapshotSignature(data: CardData): String = JsonSupport.mapper.writeValueAsString(data)

    private fun captureUndoSnapshot() {
        if (suppressUndoCapture || currentIndex !in visibleImages.indices) return
        undoManager.record(currentData)
        ignoreNextUndoCapture = true
    }

    private fun undoCardChange() {
        if (currentIndex !in visibleImages.indices) return
        val previous = undoManager.undo(currentData) ?: return
        applyUndoState(previous, "Undid card change")
    }

    private fun redoCardChange() {
        if (currentIndex !in visibleImages.indices) return
        val next = undoManager.redo(currentData) ?: return
        applyUndoState(next, "Redid card change")
    }

    private fun applyUndoState(state: CardData, message: String) {
        suppressUndoCapture = true
        try {
            currentData = state.copy()
            ignoreNextUndoCapture = false
            populateEditor()
            render()
            statusBarLabel.text = "$message • ${visibleImages.getOrNull(currentIndex)?.let(::relativePath).orEmpty()}"
        } finally {
            suppressUndoCapture = false
        }
    }

    private fun isTextInputFocus(): Boolean = scene.focusOwner is TextInputControl

    private fun updateFromEditor(renderPreview: Boolean = true) {
        if (suppressEditorUpdates) return
        val activeIndex = currentVisibleIndex()
        if (activeIndex < 0) return
        val beforeSignature = cardSnapshotSignature(currentData)
        currentData.title = fields["title"]?.text ?: currentData.title
        currentData.cost = fields["cost"]?.text ?: currentData.cost
        currentData.typeLine = fields["typeLine"]?.text ?: currentData.typeLine
        currentData.rarity = fields["rarity"]?.text ?: currentData.rarity
        currentData.stats = fields["stats"]?.text ?: currentData.stats
        currentData.artist = fields["artist"]?.text ?: currentData.artist
        currentData.setName = fields["setName"]?.text ?: currentData.setName
        currentData.collectorNumber = fields["collectorNumber"]?.text ?: currentData.collectorNumber
        currentData.description = description.text
        currentData.flavorText = flavor.text
        currentData.status = statusChoice.value ?: currentData.status
        currentData.templateName = if (templateOverride.isSelected) (templateChoice.value?.name ?: currentData.templateName) else ""
        currentData.imageMode = imageMode.value ?: currentData.imageMode
        currentData.imageBleedOverFrame = imageBleedOverFrame.isSelected
        currentData.imageBleedOpacity = imageBleedOpacity.value.coerceIn(0.0, 1.0)
        currentData.imageZoom = zoom.value
        currentData.imageOffsetX = currentActualPanX()
        currentData.imageOffsetY = currentActualPanY()
        currentData.imagePadColor = imagePadColor.value.toHex()
        currentData.backgroundColor = backgroundColor.value.toHex()
        currentData.panelColor = panelColor.value.toHex()
        currentData.frameColor = frameColor.value.toHex()
        currentData.accentColor = accentColor.value.toHex()
        currentData.overlayColor = overlayColor.value.toHex()
        currentData.backgroundOverlay = backgroundOverlayChoice.value?.path?.fileName?.toString() ?: ""
        currentData.backgroundOverlayPlacement = overlayPlacementChoice.value ?: currentData.backgroundOverlayPlacement
        currentData.backgroundOverlayOpacity = overlayOpacity.value
        currentData.borderWidth = border.value
        currentData.cornerRadius = radius.value
        currentData.panelOpacity = panelOpacity.value
        currentData.titleFontSize = titleSize.value
        currentData.bodyFontSize = bodySize.value
        val currentPath = visibleImages[activeIndex]
        val afterSignature = cardSnapshotSignature(currentData)
        if (beforeSignature != afterSignature) {
            if (!suppressUndoCapture && !ignoreNextUndoCapture) {
                undoManager.record(JsonSupport.mapper.readValue(beforeSignature, CardData::class.java))
            }
            ignoreNextUndoCapture = false
        }
        cardDataCache[currentPath.toAbsolutePath().normalize()] = currentData.copy()
        searchIndex[relativePath(currentPath)] = searchableTextFromData(currentPath, currentData)
        if (renderPreview) render()
    }

    private fun searchableTextFromData(path: Path, data: CardData): String {
        val json = runCatching { JsonSupport.mapper.writeValueAsString(data) }.getOrDefault("")
        return ("${relativePath(path)} ${path.fileName} ${data.assetId} ${data.status.name} $json").lowercase()
    }

    private fun render() {
        val activeIndex = currentVisibleIndex()
        if (activeIndex < 0) return
        val template = currentTemplate() ?: return
        loadTemplateAndOverlay()
        val image = cropImage ?: cachedFullImage(visibleImages[activeIndex]).also { cropImage = it }
        lateinit var rendered: CardRenderer.Rendered
        rendered = CardRenderer.build(
            data = currentData,
            image = image,
            template = template,
            templateImage = templateImage,
            backgroundOverlay = backgroundOverlayImage,
            collectionPresentation = collectionPresentation,
            onImageDragged = { dx, dy ->
                val layout = CardRenderer.imageLayout(image, currentData, template)
                val actualX = (currentData.imageOffsetX + dx).coerceIn(layout.minOffsetX, layout.maxOffsetX)
                val actualY = (currentData.imageOffsetY + dy).coerceIn(layout.minOffsetY, layout.maxOffsetY)
                suppressEditorUpdates = true
                try {
                    offsetX.value = actualToSlider(actualX, layout.minOffsetX, layout.maxOffsetX)
                    offsetY.value = actualToSlider(actualY, layout.minOffsetY, layout.maxOffsetY)
                } finally {
                    suppressEditorUpdates = false
                }
                currentData.imageOffsetX = actualX
                currentData.imageOffsetY = actualY
                updateValueLabel(xValueLabel, actualX, "%+.0f px")
                updateValueLabel(yValueLabel, actualY, "%+.0f px")
                updateFromEditor(renderPreview = false)
                refreshArtworkOnly()
            },
            onImageZoomed = { delta ->
                suppressEditorUpdates = true
                try {
                    zoom.value = (zoom.value + delta).coerceIn(zoom.min, zoom.max)
                } finally {
                    suppressEditorUpdates = false
                }
                recalculatePanControls(resetPan = false)
                updateFromEditor(renderPreview = false)
                refreshArtworkOnly()
            },
            onImageReset = { resetArtworkPositionAndZoom() },
            showCropGuides = showEditorGuides.isSelected
        )
        renderedCard = rendered
        previewHost.children.setAll(rendered.root)
        resizePreview()
    }

    private fun refreshArtworkOnly() {
        val rendered = renderedCard ?: return
        val image = cropImage ?: return
        val template = currentTemplate() ?: return
        CardRenderer.updateImageView(rendered.imageView, image, currentData, template)
    }

    private fun resizePreview() {
        val rendered = renderedCard ?: return
        val scale = min(
            ((previewHost.width - 24.0) / rendered.width).coerceAtLeast(0.1),
            ((previewHost.height - 24.0) / rendered.height).coerceAtLeast(0.1)
        )
        rendered.root.scaleX = scale
        rendered.root.scaleY = scale
    }

    private fun usedCollectorNumbersForCollection(excludingAssetId: String? = null): Set<String> =
        database?.usedCollectorNumbers(excludingAssetId).orEmpty()

    private fun migrateLegacySidecars(db: CollectionDatabase, images: List<Path>) {
        var imported = 0
        var removed = 0
        images.forEach { image ->
            if (!Sidecar.exists(image) || Sidecar.isExplicitShare(image)) return@forEach
            val legacy = Sidecar.load(image)
            if (legacy != null) {
                val existing = db.dataSnapshotForPath(image)
                if (existing == null || !hasMeaningfulCardData(existing)) {
                    db.save(image, legacy)
                    imported++
                }
            }
            if (Sidecar.remove(image)) removed++
        }
        if (removed > 0) statusBarLabel.text = "Migrated $imported legacy sidecars; removed $removed legacy files."
    }

    private fun createSharingSidecar() {
        val path = imagesCurrentPath() ?: return
        if (!saveCurrent(showStatus = false)) return
        runCatching { Sidecar.createForSharing(path, currentData) }
            .onSuccess { target -> statusBarLabel.text = "Sharing sidecar created • ${target.fileName}" }
            .onFailure { showError("Could not create sharing sidecar", it) }
    }

    private fun applySchemeFromCurrentImage() {
        val path = imagesCurrentPath() ?: return
        val dominant = ImageColorAnalyzer.dominantColor(path) ?: run {
            statusBarLabel.text = "Could not determine a useful dominant image color."
            return
        }
        val scheme = ImageColorAnalyzer.bestMatchingScheme(dominant, schemes) ?: return
        captureUndoSnapshot()
        suppressEditorUpdates = true
        try {
            scheme.applyTo(currentData)
            schemeChoice.value = scheme
            populateColorPickersFromData()
        } finally { suppressEditorUpdates = false }
        updateFromEditor(renderPreview = false)
        render()
        statusBarLabel.text = "Matched image color to scheme '${scheme.name}'"
    }

    private fun saveCurrent(showStatus: Boolean = true): Boolean {
        val path = imagesCurrentPath() ?: return true
        // A file that disappeared from disk is no longer a save target. Most importantly,
        // do not fall back to the new occupant of its old list index.
        if (!Files.isRegularFile(path) || !visibleImages.any { it.toAbsolutePath().normalize() == path.toAbsolutePath().normalize() }) return true
        val db = database ?: return false
        return try {
            updateFromEditor(renderPreview = false)
            val previous = currentData.assetId.takeIf { it.isNotBlank() }?.let { db.dataSnapshot(it) } ?: db.dataSnapshotForPath(path)
            val collectorNumber = currentData.collectorNumber.trim()
            if (collectorNumber.isNotBlank() && usedCollectorNumbersForCollection(currentData.assetId).contains(collectorNumber)) {
                val result = Alert(Alert.AlertType.WARNING).apply {
                    title = "Duplicate collector number"
                    headerText = "${collectorNumber} is already used in this collection"
                    contentText = "Another saved card already uses this collector number. Save this card with the duplicate number anyway?"
                    buttonTypes.setAll(ButtonType("Save duplicate"), ButtonType.CANCEL)
                }.showAndWait().orElse(ButtonType.CANCEL)
                if (result == ButtonType.CANCEL) return false
            }
            if (previous != null && wouldClearCardAccidentally(previous, currentData)) {
                val confirm = Alert(Alert.AlertType.CONFIRMATION).apply {
                    title = "Protect card data"
                    headerText = "This save would clear many card fields"
                    contentText = "The current editor state would remove several previously populated card values. This can happen if an editor was reset or partially initialized. Save these cleared values anyway?"
                    buttonTypes.setAll(ButtonType("Save anyway"), ButtonType.CANCEL)
                }.showAndWait().orElse(ButtonType.CANCEL)
                if (confirm == ButtonType.CANCEL) return false
            }
            val result = db.save(path, currentData)
            synchronized(cardThumbnailCache) { cardThumbnailCache.remove(path.toAbsolutePath().normalize()) }
            if (result.changed) db.recordActivity(result.assetId, "SAVE", "revision=${result.revisionNumber}")
            searchIndex[relativePath(path)] = searchableTextFromData(path, currentData)
            cardDataCache[path.toAbsolutePath().normalize()] = currentData.copy()
            if (showStatus) {
                statusBarLabel.text = if (result.changed) {
                    "Saved • ${relativePath(path)} • revision ${result.revisionNumber} • ${statusLabel(currentData.status)}"
                } else {
                    "No changes to save • ${relativePath(path)}"
                }
            }
            true
        } catch (e: Exception) {
            if (showStatus) showError("Could not save card", e)
            false
        }
    }

    private fun wouldClearCardAccidentally(before: CardData, after: CardData): Boolean {
        val beforeValues = listOf(before.title, before.cost, before.typeLine, before.rarity, before.stats, before.artist, before.setName, before.collectorNumber, before.description, before.flavorText)
        val afterValues = listOf(after.title, after.cost, after.typeLine, after.rarity, after.stats, after.artist, after.setName, after.collectorNumber, after.description, after.flavorText)
        val meaningfulBefore = beforeValues.count { it.isNotBlank() }
        val blanked = beforeValues.zip(afterValues).count { (old, new) -> old.isNotBlank() && new.isBlank() }
        return meaningfulBefore >= 5 && blanked >= 5 && blanked >= meaningfulBefore * 0.5
    }

    private fun backupCatalog(stage: Stage) {
        val db = database ?: return
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        val chooser = FileChooser().apply {
            title = "Backup Card Forge catalog"
            extensionFilters.add(FileChooser.ExtensionFilter("SQLite database", "*.sqlite"))
            initialFileName = "cardforge-backup-${LocalDateTime.now().format(formatter)}.sqlite"
        }
        val target = chooser.showSaveDialog(stage)?.toPath() ?: return
        try {
            db.backupTo(target)
            statusBarLabel.text = "Catalog backup created • ${target.fileName}"
        } catch (e: Exception) {
            showError("Could not create catalog backup", e)
        }
    }

    private fun defaultExportDirectory(): Path? {
        val root = collectionRoot ?: return null
        // Keep generated files in a visible, purpose-named folder. The browser excludes
        // this folder from source-image discovery, so exported cards do not become assets.
        val dir = root.resolve("Card Forge Exports")
        return runCatching { Files.createDirectories(dir); dir }.getOrNull()
    }

    private fun exportSvg(stage: Stage) {
        if (currentIndex !in visibleImages.indices) return
        if (!saveCurrent(showStatus = false)) return
        val path = visibleImages[currentIndex]
        val template = currentTemplate() ?: return
        val chooser = FileChooser().apply {
            title = "Export SVG"
            extensionFilters.add(FileChooser.ExtensionFilter("SVG", "*.svg"))
            initialDirectory = defaultExportDirectory()?.toFile()
            initialFileName = path.fileName.toString().substringBeforeLast('.') + ".card.svg"
        }
        val target = chooser.showSaveDialog(stage)?.toPath() ?: return
        try {
            loadTemplateAndOverlay()
            val image = cachedFullImage(path) ?: error("Could not load image ${path.fileName}")
            SvgExporter.export(
                image = image,
                data = currentData,
                template = template,
                target = target,
                templateImage = templateImage,
                backgroundOverlay = backgroundOverlayImage,
                collectionPresentation = collectionPresentation
            )
            database?.recordActivity(currentData.assetId, "EXPORT_SVG", target.toAbsolutePath().toString())
            markExported()
            saveCurrent(showStatus = false)
            statusBarLabel.text = "Exported ${target.fileName} • full card SVG"
        } catch (e: Exception) {
            showError("Could not export SVG", e)
        }
    }

    private fun exportPng(stage: Stage) {
        if (currentIndex !in visibleImages.indices) return
        if (!saveCurrent(showStatus = false)) return
        val path = visibleImages[currentIndex]
        val template = currentTemplate() ?: return
        val chooser = FileChooser().apply {
            title = "Export PNG"
            extensionFilters.add(FileChooser.ExtensionFilter("PNG", "*.png"))
            initialDirectory = defaultExportDirectory()?.toFile()
            initialFileName = path.fileName.toString().substringBeforeLast('.') + ".card.png"
        }
        val target = chooser.showSaveDialog(stage)?.toPath() ?: return
        try {
            loadTemplateAndOverlay()
            val image = cachedFullImage(path) ?: error("Could not load image ${path.fileName}")
            val png = ExportRenderer.pngBytes(
                image = image,
                data = currentData,
                template = template,
                templateImage = templateImage,
                backgroundOverlay = backgroundOverlayImage,
                collectionPresentation = collectionPresentation,
                scale = 1.0
            )
            Files.write(target, png)
            database?.recordActivity(currentData.assetId, "EXPORT_PNG", target.toAbsolutePath().toString())
            markExported()
            saveCurrent(showStatus = false)
            statusBarLabel.text = "Exported ${target.fileName} • full card PNG"
        } catch (e: Exception) {
            showError("Could not export PNG", e)
        }
    }

    private fun exportPdf(stage: Stage) {
        if (visibleImages.isEmpty()) return
        if (!saveCurrent(showStatus = false)) return
        val chooser = FileChooser().apply {
            title = "Export A4 Contact Sheet PDF"
            extensionFilters.add(FileChooser.ExtensionFilter("PDF", "*.pdf"))
            initialDirectory = defaultExportDirectory()?.toFile()
            initialFileName = "card-forge-contact-sheet.pdf"
        }
        val target = chooser.showSaveDialog(stage)?.toPath() ?: return
        val paths = visibleImages.toList()
        val options = promptPdfExportOptions(stage) ?: return
        val plans = buildPdfPlans(paths, options)
        statusBarLabel.text = "Exporting ${paths.size} card(s) to PDF at ${"%.0f".format(options.scale * 100)}%…"
        val task = object : Task<Unit>() {
            override fun call() {
                PdfContactSheetExporter.export(target, plans) { path ->
                    val future = CompletableFuture<ByteArray>()
                    Platform.runLater {
                        try { future.complete(renderCardPngForPdf(path)) }
                        catch (t: Throwable) { future.completeExceptionally(t) }
                    }
                    future.get()
                }
            }
        }
        task.setOnSucceeded {
            database?.recordActivity(currentData.assetId, "EXPORT_PDF", target.toAbsolutePath().toString())
            statusBarLabel.text = "Exported ${target.fileName} • ${paths.size} full card(s)"
        }
        task.setOnFailed { showError("Could not export PDF", task.exception ?: RuntimeException("Unknown PDF export error")) }
        Thread(task, "card-forge-pdf-export").apply { isDaemon = true }.start()
    }

    private data class PdfExportOptions(val scale: Double, val marginMm: Double, val gapMm: Double)

    private fun promptPdfExportOptions(owner: Stage): PdfExportOptions? {
        val dialog = Dialog<ButtonType>().apply {
            title = "A4 Contact Sheet PDF"
            headerText = "Contact sheet layout"
            dialogPane.buttonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)
        }
        val scale = Slider(0.5, 1.0, 1.0).apply { blockIncrement = 0.05; majorTickUnit = 0.1 }
        val margin = Slider(0.0, 20.0, 10.0).apply { blockIncrement = 1.0; majorTickUnit = 5.0 }
        val gap = Slider(0.0, 10.0, 3.0).apply { blockIncrement = 0.5; majorTickUnit = 2.0 }
        installSliderReset(scale, 1.0)
        installSliderReset(margin, 10.0)
        installSliderReset(gap, 3.0)
        val scaleLabel = Label()
        val marginLabel = Label()
        val gapLabel = Label()
        fun updateLabels() {
            scaleLabel.text = "${"%.0f".format(scale.value * 100)}%"
            marginLabel.text = "${"%.1f".format(margin.value)} mm"
            gapLabel.text = "${"%.1f".format(gap.value)} mm"
        }
        scale.valueProperty().addListener { _, _, _ -> updateLabels() }
        margin.valueProperty().addListener { _, _, _ -> updateLabels() }
        gap.valueProperty().addListener { _, _, _ -> updateLabels() }
        updateLabels()
        val grid = GridPane().apply {
            hgap = 10.0; vgap = 10.0; padding = Insets(10.0);
            add(Label("Card scale"), 0, 0); add(scale, 1, 0); add(scaleLabel, 2, 0)
            add(Label("Page margin"), 0, 1); add(margin, 1, 1); add(marginLabel, 2, 1)
            add(Label("Card gap"), 0, 2); add(gap, 1, 2); add(gapLabel, 2, 2)
            add(Label("100% keeps the template's physical card size. Smaller scales fit more cards per A4 page."), 0, 3, 3, 1)
        }
        dialog.dialogPane.content = grid
        dialog.dialogPane.minWidth = 560.0
        val result = dialog.showAndWait().orElse(ButtonType.CANCEL)
        return if (result == ButtonType.OK) PdfExportOptions(scale.value, margin.value, gap.value) else null
    }

    private fun buildPdfPlans(paths: List<Path>, options: PdfExportOptions): List<PdfContactSheetExporter.PagePlan> {
        val cards = paths.mapNotNull { path ->
            val data = savedDataForPath(path)
            val template = templateForData(data) ?: return@mapNotNull null
            val widthPt = (template.width / 10.0) * 72.0 / 25.4
            val heightPt = (template.height / 10.0) * 72.0 / 25.4
            PdfContactSheetExporter.CardSpec(path, widthPt, heightPt)
        }
        return PdfContactSheetExporter.planA4(cards, options.scale, options.marginMm, options.gapMm)
    }

    private fun renderCardPngForPdf(path: Path): ByteArray {
        check(Platform.isFxApplicationThread()) { "PDF card rendering must run on the JavaFX application thread" }
        val data = savedDataForPath(path)
        val template = templateForData(data) ?: error("No card template available for ${path.fileName}")
        val sourceImage = cachedFullImage(path) ?: error("Could not load image ${path.fileName}")
        val templateImage = TemplateRepository.rasterize(template)
        val overlayImage = data.backgroundOverlay.takeIf { it.isNotBlank() }?.let { name ->
            OverlayRepository.resolve(name)?.let { overlayPath ->
                OverlayRepository.rasterize(overlayPath, template.width, template.height, data.overlayColor)
            }
        }
        return ExportRenderer.pngBytes(
            image = sourceImage,
            data = data,
            template = template,
            templateImage = templateImage,
            backgroundOverlay = overlayImage,
            collectionPresentation = collectionPresentation,
            scale = 1.0
        )
    }

    private fun showContactSheet(owner: Stage) {
        if (visibleImages.isEmpty()) return
        if (!saveCurrent(showStatus = false)) return
        val paths = visibleImages.toList()
        val perPage = 6
        val pageCount = ((paths.size + perPage - 1) / perPage).coerceAtLeast(1)
        val pagination = Pagination(pageCount, 0).apply {
            maxPageIndicatorCount = 9
            pageFactory = javafx.util.Callback { pageIndex: Int ->
                val pagePaths = paths.drop(pageIndex * perPage).take(perPage)
                createContactSheetPage(pagePaths)
            }
        }
        val stage = Stage()
        stage.initOwner(owner)
        stage.title = "Card Forge · Contact Sheet"
        owner.icons.firstOrNull()?.let { stage.icons.add(it) }
        val root = BorderPane(pagination).apply {
            padding = Insets(14.0)
            style = if (uiTheme == UiTheme.DARK) "-fx-background-color:#20242A;" else "-fx-background-color:#F6F7F9;"
        }
        val scene = Scene(root, 1040.0, 820.0)
        javaClass.getResource("/cardforge.css")?.toExternalForm()?.let { scene.stylesheets.add(it) }
        stage.scene = scene
        stage.show()
    }

    private fun createContactSheetPage(paths: List<Path>): Node {
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
            val loading = Label("Rendering…").apply {
                style = "-fx-text-fill:#AEB7C2;-fx-font-size:12px;"
            }
            slot.children.add(loading)
            grid.add(slot, index % 3, index / 3)
            Platform.runLater {
                requestCardThumbnail(path) { preview ->
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

    private fun markExported() {
        suppressEditorUpdates = true
        try {
            currentData.status = CardStatus.EXPORTED
            statusChoice.value = CardStatus.EXPORTED
        } finally {
            suppressEditorUpdates = false
        }
    }

    private fun showHistory() {
        val db = database ?: return
        if (currentData.assetId.isBlank()) return
        val history = db.history(currentData.assetId)
        val text = if (history.isEmpty()) "No saved revisions yet." else buildString {
            history.forEach {
                appendLine("Revision ${it.revisionNumber} • ${it.savedAt} • ${statusLabel(it.status)}")
                appendLine(prettyJson(it.changesJson))
                appendLine()
            }
        }
        showTextDialog("Card history", text)
    }

    private fun showDatabaseInfo() {
        val db = database ?: return
        val revisions = if (currentData.assetId.isBlank()) 0 else db.countRevisions(currentData.assetId)
        val message = buildString {
            appendLine("Collection: $collectionRoot")
            appendLine("Database: ${db.path}")
            appendLine("Images tracked: ${db.countAssets()}")
            appendLine("Images visible: ${visibleImages.size}/${allImages.size}")
            appendLine()
            appendLine("Current image ID: ${currentData.assetId.ifBlank { "not assigned" }}")
            appendLine("Relative path: ${visibleImages.getOrNull(currentIndex)?.let(::relativePath) ?: "-"}")
            appendLine("Status: ${statusLabel(currentData.status)}")
            appendLine("Saved revisions: $revisions")
            appendLine("Template: ${currentTemplate()?.name ?: "-"} ${if (currentData.templateName.isBlank()) "(collection default)" else "(card override)"}")
            appendLine("Description label: ${collectionPresentation.descriptionHeading}")
            appendLine("Show artist ©: ${collectionPresentation.showArtistCopyright}")
            appendLine("Background color: ${currentData.backgroundColor}")
            appendLine("Background overlay: ${currentData.backgroundOverlay.ifBlank { "None" }} (${currentData.backgroundOverlayPlacement})")
            appendLine()
            appendLine("Nested collection directories skipped: $nestedCollectionsSkipped")
            appendLine("Nested directories containing their own .cardforge.sqlite are treated as separate collections.")
        }
        showTextDialog("Collection database", message)
    }

    private fun showTextDialog(title: String, content: String) {
        val area = TextArea(content).apply { isEditable = false; isWrapText = false; prefRowCount = 24; prefColumnCount = 90 }
        Alert(Alert.AlertType.INFORMATION).apply {
            this.title = title
            headerText = null
            dialogPane.content = area
            dialogPane.minWidth = 800.0
            dialogPane.buttonTypes.setAll(ButtonType.CLOSE)
            showAndWait()
        }
    }

    private fun startCollectionWatcher(root: Path) {
        stopCollectionWatcher()
        val service = FileSystems.getDefault().newWatchService()
        watchService = service
        val thread = Thread({
            runCatching { registerWatchTree(service, root) }
            while (!Thread.currentThread().isInterrupted) {
                val key = runCatching { service.take() }.getOrNull() ?: break
                var relevant = false
                for (event in key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        relevant = true
                        continue
                    }
                    val dir = key.watchable() as? Path ?: continue
                    val rel = event.context() as? Path ?: continue
                    val child = dir.resolve(rel).toAbsolutePath().normalize()
                    when (event.kind()) {
                        StandardWatchEventKinds.ENTRY_CREATE -> {
                            if (Files.isDirectory(child) && !Files.isRegularFile(child.resolve(CollectionDatabase.FILE_NAME))) {
                                runCatching { registerWatchTree(service, child) }
                            }
                            relevant = true
                        }
                        StandardWatchEventKinds.ENTRY_DELETE -> relevant = true
                        StandardWatchEventKinds.ENTRY_MODIFY -> {
                            invalidateImageCaches(child)
                            if (child.fileName.toString() == CollectionDatabase.FILE_NAME || child.fileName.toString().endsWith(".card.json", ignoreCase = true)) relevant = true
                        }
                    }
                }
                key.reset()
                if (relevant) scheduleWatcherRefresh()
            }
        }, "card-forge-filesystem-watcher")
        thread.isDaemon = true
        watchThread = thread
        thread.start()
    }

    private fun registerWatchTree(service: WatchService, root: Path) {
        if (!Files.isDirectory(root)) return
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (dir != root && Files.isRegularFile(dir.resolve(CollectionDatabase.FILE_NAME))) return FileVisitResult.SKIP_SUBTREE
                dir.register(service, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY)
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun scheduleWatcherRefresh() {
        if (!watchScanScheduled.compareAndSet(false, true)) return
        val root = collectionRoot ?: run { watchScanScheduled.set(false); return }
        val token = generation.get()
        watchScanExecutor.submit {
            val result = runCatching { scanImages(root) }.getOrNull()
            Platform.runLater {
                watchScanScheduled.set(false)
                if (generation.get() != token || collectionRoot != root || result == null) return@runLater
                applyFilesystemScanResult(result)
            }
        }
    }

    private fun applyFilesystemScanResult(result: ScanResult) {
        val root = collectionRoot ?: return
        val previousPath = currentLoadedPath?.toAbsolutePath()?.normalize()
        val previousIndex = currentVisibleIndex()
        val previousSet = allImages.map { it.toAbsolutePath().normalize() }.toSet()
        val nextSet = result.images.map { it.toAbsolutePath().normalize() }.toSet()
        val previousNested = nestedCollectionRoots.toSet()
        val nextNested = result.nestedCollections.toSet()
        val changed = previousSet != nextSet
        val nestedChanged = previousNested != nextNested
        if (!changed && !nestedChanged) return

        allImages.clear()
        allImages.addAll(result.images)
        nestedCollectionRoots = result.nestedCollections
        nestedCollectionsSkipped = nestedCollectionRoots.size
        if (changed) {
            val livePaths = nextSet
            searchIndex.keys.retainAll(result.images.map(::relativePath).toSet())
            cardDataCache.keys.retainAll(livePaths)
            previousSet.asSequence().filter { it !in nextSet }.forEach { removed ->
                synchronized(thumbnailCache) { thumbnailCache.remove(removed) }
                synchronized(fullImageCache) { fullImageCache.remove(removed) }
                synchronized(cardThumbnailCache) { cardThumbnailCache.remove(removed) }
                searchIndex.remove(relativePath(removed))
            }
        }

        refreshCollectionChoices()
        buildFolderTree(root, selectedFolder ?: root)
        if (changed) {
            val scope = selectedFolder?.takeIf { it.startsWith(root) } ?: root
            val query = filterField.text.trim().lowercase()
            visibleImages.clear()
            visibleImages.addAll(allImages.filter { it.startsWith(scope) }
                .filter { query.isBlank() || searchableText(it).contains(query) })
            sortVisibleImagesInPlace()
            browserCountLabel.text = if (query.isBlank() && scope == root) "${visibleImages.size} images" else "${visibleImages.size}/${allImages.size} images"
            rebuildImageList()
        }

        val preservedIndex = previousPath?.let { visibleImages.indexOf(it) } ?: -1
        when {
            preservedIndex >= 0 -> select(preservedIndex, scrollIntoView = false)
            visibleImages.isNotEmpty() -> {
                // If the selected file disappeared, move to the natural successor at the
                // old index. saveCurrent() cannot save the removed path, so this cannot
                // transfer its data to the successor.
                val successor = previousIndex.coerceIn(0, visibleImages.lastIndex)
                select(successor, scrollIntoView = false)
            }
            else -> {
                currentIndex = -1
                currentLoadedPath = null
                clearEditorForNoSelection()
            }
        }
        statusBarLabel.text = "Collection updated • ${allImages.size} images"
    }

    private fun invalidateImageCaches(path: Path) {
        val normalized = path.toAbsolutePath().normalize()
        val ext = normalized.fileName.toString().substringAfterLast('.', "").lowercase()
        if (ext !in supportedExtensions) return
        synchronized(thumbnailCache) { thumbnailCache.remove(normalized) }
        synchronized(fullImageCache) { fullImageCache.remove(normalized) }
        synchronized(cardThumbnailCache) { cardThumbnailCache.remove(normalized) }
        Platform.runLater {
            if (currentIndex in visibleImages.indices && visibleImages[currentIndex].toAbsolutePath().normalize() == normalized) {
                cropImage = cachedFullImage(normalized)
                refreshArtworkOnly()
            }
        }
    }

    private fun stopCollectionWatcher() {
        watchThread?.interrupt()
        watchThread = null
        runCatching { watchService?.close() }
        watchService = null
        watchScanScheduled.set(false)
    }

    private fun applyUiTheme() {
        if (!::appRoot.isInitialized) return
        appRoot.styleClass.remove("theme-light")
        appRoot.styleClass.remove("theme-dark")
        appRoot.styleClass.add(if (uiTheme == UiTheme.DARK) "theme-dark" else "theme-light")
    }

    private fun installUiThemeKey(scene: Scene) {
        scene.accelerators[KeyCodeCombination(KeyCode.T, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN)] = Runnable {
            uiTheme = if (uiTheme == UiTheme.DARK) UiTheme.LIGHT else UiTheme.DARK
            Preferences.userNodeForPackage(MainApp::class.java).put("uiTheme", uiTheme.name)
            if (::uiThemeButton.isInitialized) uiThemeButton.text = if (uiTheme == UiTheme.DARK) "☀ Light UI" else "◐ Dark UI"
            applyUiTheme()
        }
    }

    private fun installDoubleClickReset(node: Node, reset: () -> Unit) {
        node.addEventFilter(MouseEvent.MOUSE_PRESSED) { event ->
            if (event.button == MouseButton.PRIMARY && event.clickCount == 2) {
                reset()
                event.consume()
            }
        }
    }

    private fun persistCollectionPresentation() {
        database?.setCollectionPresentation(collectionPresentation)
        synchronized(cardThumbnailCache) { cardThumbnailCache.clear() }
    }

    private fun clearEditorForNoSelection() {
        currentLoadedPath = null
        undoManager.clear()
        suppressEditorUpdates = true
        try {
            fields.values.forEach { it.clear() }
            if (::collectionSettingsPane.isInitialized) collectionSettingsPane.setPresentation(collectionPresentation)
            description.clear()
            flavor.clear()
            statusChoice.value = null
            templateChoice.value = templates.firstOrNull { it.name == collectionDefaultTemplateName } ?: templates.firstOrNull()
            collectionTemplateChoice.items.setAll(templates)
            collectionTemplateChoice.value = templates.firstOrNull { it.name == collectionDefaultTemplateName } ?: templates.firstOrNull()
            templateOverride.isSelected = false
            imageMode.value = null
            imageBleedOpacity.value = 1.0
            imageBleedOpacityValue.text = "100%"
            backgroundOverlayChoice.value = overlays.firstOrNull { it.path == null } ?: overlays.firstOrNull()
            overlayPlacementChoice.value = OverlayPlacement.FRAMES_ONLY
            zoom.value = 1.0
            offsetX.value = 0.0
            offsetY.value = 0.0
            imagePadColor.value = Color.web("#0A0D10")
            backgroundColor.value = Color.web("#161B22")
            panelColor.value = Color.web("#EFE8D7")
            frameColor.value = Color.web("#D9C28E")
            accentColor.value = Color.web("#8C8068")
            overlayColor.value = Color.web("#C9B37A")
            border.valueFactory.value = 8.0
            radius.valueFactory.value = 24.0
            panelOpacity.value = 0.96
            overlayOpacity.value = 1.0
            titleSize.valueFactory.value = 27.0
            bodySize.valueFactory.value = 16.0
        } finally {
            suppressEditorUpdates = false
        }
    }

    private fun relativePath(path: Path): String {
        val root = collectionRoot ?: return path.fileName.toString()
        return root.relativize(path).toString().replace('\\', '/')
    }

    private fun revealInFinder(path: Path) {
        try {
            when {
                System.getProperty("os.name").contains("Mac", ignoreCase = true) -> ProcessBuilder("open", "-R", path.toAbsolutePath().toString()).start()
                Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN) -> Desktop.getDesktop().open(path.parent.toFile())
                else -> showTextDialog("Reveal in Finder", path.toAbsolutePath().toString())
            }
        } catch (e: Exception) {
            showError("Could not open Finder", e)
        }
    }

    private fun prettyJson(value: String): String = runCatching {
        JsonSupport.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(JsonSupport.mapper.readTree(value))
    }.getOrDefault(value)

    private fun showError(title: String, e: Throwable) {
        Alert(Alert.AlertType.ERROR).apply {
            this.title = title
            headerText = e.message ?: e.javaClass.simpleName
            dialogPane.content = Label("Check the collection directory and try again.")
            showAndWait()
        }
    }

    override fun stop() {
        scanTask?.cancel()
        filterTask?.cancel()
        filterApplyPause.stop()
        thumbnailLoadExecutor.shutdownNow()
        database?.close()
        database = null
    }

    private fun closeCollection() {
        stopCollectionWatcher()
        database?.close()
        database = null
        collectionRoot = null
        allImages.clear()
        visibleImages.clear()
        currentIndex = -1
        renderedCard = null
        undoManager.clear()
        backgroundOverlayImage = null
        templateImage = null
        cropImage = null
        selectedFolder = null
        nestedCollectionRoots = emptyList()
        collectionPresentation = CollectionPresentation()
        searchIndex.clear()
        cardDataCache.clear()
        suppressCollectionChoice = true
        try { collectionChoice.items.clear() } finally { suppressCollectionChoice = false }
        synchronized(thumbnailCache) { thumbnailCache.clear() }
        synchronized(cardThumbnailCache) { cardThumbnailCache.clear() }
        synchronized(fullImageCache) { fullImageCache.clear() }
        imageList.items.clear()
        gridList.items.clear()
        folderTree.root = null
        previewHost.children.clear()
    }

    private fun Path.extensionLabel(): String = fileName.toString().substringAfterLast('.', "IMG").uppercase()
}

private class RegionPlaceholder : Region()

fun main() {
    Application.launch(MainApp::class.java)
}

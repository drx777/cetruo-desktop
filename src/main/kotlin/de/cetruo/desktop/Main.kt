package de.cetruo.desktop

import de.cetruo.desktop.browser.*
import de.cetruo.desktop.editor.*
import de.cetruo.desktop.visual.CardVisualDefaults
import de.cetruo.desktop.ui.AppToolbar
import de.cetruo.desktop.ui.CardPreviewPane
import javafx.animation.PauseTransition
import javafx.application.Application
import javafx.application.Platform
import javafx.concurrent.Task
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
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import javafx.stage.Stage
import javafx.util.Duration
import java.awt.Desktop
import java.awt.Taskbar
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.UUID
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private enum class UiTheme {
    LIGHT, DARK
}

class MainApp : Application() {
    private val imageScanner = CollectionImageScanner()
    private val cardSelectionResolver = CardSelectionResolver()
    private val collectionOpenController by lazy {
        CollectionOpenController(
            scanner = imageScanner,
            onScanning = { root -> statusBarLabel.text = "Scanning ${root.fileName}…" },
            onOpened = ::applyOpenedCollection,
            onError = ::showError
        )
    }
    private val browserCardData by lazy {
        BrowserCardDataProvider { path -> cardStore.snapshot(path) }
    }
    private val browserImageSorter by lazy {
        BrowserImageSorter(
            cardDataFor = browserCardData::forSorting,
            relativePathFor = ::relativePath
        )
    }
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
    private val cardPreviewPane by lazy {
        CardPreviewPane(
            initialShowGuides = AppPreferences.getBoolean("showEditorGuides", false),
            onInspectSource = { showSourceImageInspector() },
            onSizeChanged = ::resizePreview
        )
    }
    private val previewHost get() = cardPreviewPane.host
    private val showEditorGuides get() = cardPreviewPane.showEditorGuides
    private var selectedFolder: Path? = null
    private var collectionDefaultTemplateName: String = ""
    private var collectionPresentation = CollectionPresentation()
    private var nestedCollectionRoots: List<Path> = emptyList()
    private var suppressCollectionChoice = false
    private var browserColumns = 1
    private val browserSearchIndex by lazy {
        BrowserSearchIndex(
            relativePathFor = ::relativePath,
            cardDataFor = { path -> database?.dataSnapshotForPath(path) ?: browserCardData.lightweight(path) }
        )
    }
    private val browserImageFilter by lazy { BrowserImageFilter(browserSearchIndex) }
    private val browserImageView by lazy { BrowserImageView(browserImageFilter, browserImageSorter) }
    private val browserFolderTreeBuilder = BrowserFolderTreeBuilder()
    private val cardDefaultsGenerator by lazy {
        CardDefaultsGenerator(
            randomProvider = ::randomGenerator,
            templatesProvider = { templates },
            schemesProvider = { schemes },
            usedCollectorNumbers = { database?.usedCollectorNumbers(null).orEmpty() }
        )
    }
    private val collectionDiagnostics by lazy { CollectionDiagnosticsFormatter(::statusLabel) }
    private val editorUi by lazy { EditorUiFactory(::statusLabel) }
    private val cardMetadataPane by lazy {
        CardMetadataPane(
            ui = editorUi,
            onChanged = { updateFromEditor() },
            onRandomize = { field ->
                when (field) {
                    MetadataField.TITLE -> cardRandomizationController.randomizeTitle()
                    MetadataField.COST -> cardRandomizationController.randomizeCost()
                    MetadataField.TYPE_LINE -> cardRandomizationController.randomizeTypeLine()
                    MetadataField.RARITY -> cardRandomizationController.randomizeRarity()
                    MetadataField.STATS -> cardRandomizationController.randomizeStats()
                    MetadataField.ARTIST -> cardRandomizationController.randomizeArtistPattern()
                    MetadataField.SET_NAME -> cardRandomizationController.randomizeSetName()
                    MetadataField.COLLECTOR_NUMBER -> cardRandomizationController.randomizeCollectorNumber()
                }
            }
        )
    }
    private val cardPersistence = CardPersistenceService()
    private val cardSaveController by lazy {
        CardSaveController(
            persistence = cardPersistence,
            database = { database },
            currentPath = { imagesCurrentPath() },
            isVisiblePath = { path ->
                val normalized = path.toAbsolutePath().normalize()
                visibleImages.any { it.toAbsolutePath().normalize() == normalized }
            },
            currentData = { currentData },
            allImages = { allImages.toList() },
            updateFromEditor = { updateFromEditor(renderPreview = false) },
            fields = fields,
            withSuppressedUpdates = { action ->
                val previous = suppressEditorUpdates
                suppressEditorUpdates = true
                try {
                    action()
                } finally {
                    suppressEditorUpdates = previous
                }
            },
            clearCardStore = { cardStore.clear() },
            clearCardPreviews = { browserPreviews.clearCards() },
            rebuildSearchIndex = { db ->
                browserSearchIndex.clear()
                browserSearchIndex.replaceAll(db.searchIndex())
            },
            removeCardPreview = { path -> browserPreviews.removeCard(path) },
            updateSearchIndex = { path, data -> browserSearchIndex.update(path, data) },
            cacheCard = { path, data -> cardStore.put(path, data) },
            relativePath = ::relativePath,
            statusLabel = ::statusLabel,
            setStatus = { statusBarLabel.text = it },
            showError = ::showError
        )
    }
    private val cardStore = CollectionCardStore(
        databaseProvider = { database },
        newCardFactory = { path -> newCardDefaults(path) }
    )
    private lateinit var appToolbar: AppToolbar
    private var uiTheme: UiTheme = runCatching {
        UiTheme.valueOf(AppPreferences.get("uiTheme", UiTheme.DARK.name))
    }.getOrDefault(UiTheme.DARK)
    private lateinit var scene: Scene
    private lateinit var appRoot: BorderPane
    private val sourceImageInspector = SourceImageInspector(MainApp::class.java) { uiTheme == UiTheme.DARK }
    private val exportCoordinator = ExportCoordinator()
    private val exportController by lazy {
        ExportController(
            coordinator = exportCoordinator,
            currentData = { currentData },
            currentPath = { visibleImages.getOrNull(currentIndex) },
            visiblePaths = { visibleImages.toList() },
            collectionRoot = { collectionRoot },
            currentTemplate = { currentTemplate() },
            templateForData = ::templateForData,
            savedDataForPath = { path -> savedDataForPath(path) },
            cachedFullImage = ::cachedFullImage,
            loadTemplateAndOverlay = ::loadTemplateAndOverlay,
            templateImage = { templateImage },
            backgroundOverlayImage = { backgroundOverlayImage },
            collectionPresentation = { collectionPresentation },
            saveCurrent = { saveCurrent(showStatus = false) },
            recordActivity = { assetId, action, details ->
                database?.recordActivity(assetId, action, details)
            },
            statusChoice = statusChoice,
            withSuppressedUpdates = { action ->
                val previous = suppressEditorUpdates
                suppressEditorUpdates = true
                try {
                    action()
                } finally {
                    suppressEditorUpdates = previous
                }
            },
            setStatus = { statusBarLabel.text = it },
            showError = ::showError,
            installSliderReset = ::installSliderReset,
            isDarkTheme = { uiTheme == UiTheme.DARK },
            requestCardThumbnail = { path, callback -> requestCardThumbnail(path, callback) },
            resourceOwner = MainApp::class.java
        )
    }
    private val collectionWatcher by lazy {
        CollectionWatcher(
            onImageModified = ::invalidateImageCaches,
            onRefreshRequested = ::scheduleWatcherRefresh
        )
    }
    private val filesystemRefreshController by lazy {
        FilesystemRefreshController(
            scanner = imageScanner,
            onResult = { root, result ->
                if (collectionRoot == root) applyFilesystemScanResult(result)
            }
        )
    }
    private val randomSequence = AtomicLong()

    private data class CachedImage(val size: Long, val modified: Long, val image: Image)
    private val fullImageCache = object : LinkedHashMap<Path, CachedImage>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Path, CachedImage>?): Boolean = size > 32
    }
    private val thumbnailRefreshPause = PauseTransition(Duration.seconds(3.0))
    private val autosaveGuard by lazy { AutosaveGuard { showStatus -> saveCurrent(showStatus) } }
    private val browserPreviews by lazy {
        BrowserPreviewCoordinator(
            cardSignature = { path, data -> cardPreviewSignature(path, data) },
            cardDataFor = { path, color -> savedDataForPath(path, color, analyzeImageIfNeeded = false) },
            renderCardPreview = { image, data -> renderCardPreviewImage(image, data) }
        )
    }

    private val browserPane = BrowserPane(
        initialMode = ImageBrowserMode.LIST,
        initialPreviewMode = BrowserPreviewMode.ORIGINAL,
        initialSort = runCatching {
            BrowserSort.valueOf(AppPreferences.get("browserSort", BrowserSort.FILE_NAME.name))
        }.getOrDefault(BrowserSort.FILE_NAME),
        initialSortDescending = AppPreferences.getBoolean("browserSortDescending", false)
    )
    private val folderTree get() = browserPane.folderTree
    private val imageList get() = browserPane.imageList
    private val gridList get() = browserPane.gridList
    private val filterField get() = browserPane.filterField
    private val collectionChoice get() = browserPane.collectionChoice
    private val browserCountLabel get() = browserPane.countLabel
    private val browserSortChoice get() = browserPane.sortChoice
    private val browserSortDescending get() = browserPane.sortDescending
    private val browserSelection by lazy {
        BrowserSelectionCoordinator(
            listView = imageList,
            gridView = gridList,
            visiblePaths = { visibleImages },
            isGridMode = { imageBrowserMode == ImageBrowserMode.THUMBNAILS },
            columns = { browserColumns.coerceAtLeast(1) },
            firstPathInGridRow = { row -> row.paths.firstOrNull() }
        )
    }
    private val browserTiles by lazy {
        BrowserTileFactory(
            requestPreview = { path, callback -> requestBrowserPreview(path, browserPreviewMode, callback) },
            cardTitle = { path -> browserCardData.forSorting(path).title },
            relativeFolder = { path -> relativePath(path) },
            currentPath = { imagesCurrentPath() },
            contextMenuFor = { path -> imageContextMenu(path) },
            onListSelected = { path ->
                selectPath(path, scrollIntoView = false)
                imageList.requestFocus()
            },
            onGridSelected = { path ->
                selectPath(path, scrollIntoView = false)
                gridList.requestFocus()
            }
        )
    }
    private val statusBarLabel = Label("Open an image directory.")
    private val fields get() = cardMetadataPane.fields
    private val description get() = cardMetadataPane.description
    private val flavor get() = cardMetadataPane.flavor
    private val imageMode = ComboBox<ImageMode>()
    private val imageBleedOverFrame = CheckBox("Artwork bleeds over frame")
    private val imageBleedOpacity = Slider(0.0, 1.0, 1.0)
    private val imageBleedOpacityValue = Label("100%")
    private val statusChoice get() = cardMetadataPane.statusChoice
    private lateinit var collectionSettingsPane: CollectionSettingsPane
    private val schemeChoice = ComboBox<ColorScheme>()
    private val templateChoice = ComboBox<CardTemplate>()
    private val collectionTemplateChoice = ComboBox<CardTemplate>()
    private val templateOverride = CheckBox("Use a custom template for this card")
    private val backgroundOverlayChoice = ComboBox<BackgroundOverlay>()
    private val overlayPlacementChoice = ComboBox<OverlayPlacement>()
    private val zoom = Slider(0.1, 4.0, 1.0).apply { blockIncrement = 0.1; majorTickUnit = 1.0 }
    private val offsetX = Slider(-1.0, 1.0, 0.0).apply { blockIncrement = 0.1 }
    private val offsetY = Slider(-1.0, 1.0, 0.0).apply { blockIncrement = 0.1 }
    private val imagePadColor = ColorPicker(Color.web(CardVisualDefaults.IMAGE_PAD_COLOR))
    private val backgroundColor = ColorPicker(Color.web(CardVisualDefaults.BACKGROUND_COLOR))
    private val panelColor = ColorPicker(Color.web(CardVisualDefaults.PANEL_COLOR))
    private val frameColor = ColorPicker(Color.web(CardVisualDefaults.FRAME_COLOR))
    private val accentColor = ColorPicker(Color.web(CardVisualDefaults.ACCENT_COLOR))
    private val overlayColor = ColorPicker(Color.web(CardVisualDefaults.OVERLAY_COLOR))
    private val border = doubleSpinner(0.0, 20.0, CardVisualDefaults.BORDER_WIDTH, 0.5)
    private val radius = doubleSpinner(0.0, 80.0, CardVisualDefaults.CORNER_RADIUS, 1.0)
    private val panelOpacity = Slider(0.1, 1.0, CardVisualDefaults.PANEL_OPACITY)
    private val overlayOpacity = Slider(0.0, 1.0, 1.0)
    private val titleSize = doubleSpinner(14.0, 42.0, CardVisualDefaults.TITLE_FONT_SIZE, 1.0)
    private val bodySize = doubleSpinner(10.0, 24.0, CardVisualDefaults.BODY_FONT_SIZE, 1.0)
    private val cardEditorBinding by lazy {
        CardEditorBinding(
            fields = fields,
            description = description,
            flavor = flavor,
            statusChoice = statusChoice,
            schemeChoice = schemeChoice,
            templateChoice = templateChoice,
            templateOverride = templateOverride,
            imageMode = imageMode,
            imageBleedOverFrame = imageBleedOverFrame,
            imageBleedOpacity = imageBleedOpacity,
            imageBleedOpacityValue = imageBleedOpacityValue,
            zoom = zoom,
            imagePadColor = imagePadColor,
            backgroundColor = backgroundColor,
            panelColor = panelColor,
            frameColor = frameColor,
            accentColor = accentColor,
            overlayColor = overlayColor,
            backgroundOverlayChoice = backgroundOverlayChoice,
            overlayPlacementChoice = overlayPlacementChoice,
            overlayOpacity = overlayOpacity,
            border = border,
            radius = radius,
            panelOpacity = panelOpacity,
            titleSize = titleSize,
            bodySize = bodySize
        )
    }
    private val xValueLabel = Label("0 px")
    private val yValueLabel = Label("0 px")
    private val zoomValueLabel = Label("1.00×")
    private val artworkEditorController by lazy {
        ArtworkEditorController(
            imageMode = imageMode,
            zoom = zoom,
            offsetX = offsetX,
            offsetY = offsetY,
            xValueLabel = xValueLabel,
            yValueLabel = yValueLabel,
            zoomValueLabel = zoomValueLabel,
            currentData = { currentData },
            currentImage = { cropImage },
            currentTemplate = { currentTemplate() },
            withSuppressedUpdates = { action ->
                val previous = suppressEditorUpdates
                suppressEditorUpdates = true
                try {
                    action()
                } finally {
                    suppressEditorUpdates = previous
                }
            }
        )
    }
    private val cardRandomizationController by lazy {
        CardRandomizationController(
            fields = fields,
            schemeChoice = schemeChoice,
            backgroundOverlayChoice = backgroundOverlayChoice,
            currentData = { currentData },
            hasSelection = { currentIndex in visibleImages.indices },
            hasDatabase = { database != null },
            schemes = { schemes },
            overlays = { overlays },
            randomGenerator = ::randomGenerator,
            usedCollectorNumbers = ::usedCollectorNumbersForCollection,
            randomArtistPattern = { cardDefaultsGenerator.randomArtistPattern() },
            captureUndo = ::captureUndoSnapshot,
            withSuppressedUpdates = { action ->
                val previous = suppressEditorUpdates
                suppressEditorUpdates = true
                try {
                    action()
                } finally {
                    suppressEditorUpdates = previous
                }
            },
            populateColors = ::populateColorPickersFromData,
            recalculatePanControls = { syncFromData ->
                recalculatePanControls(resetPan = false, syncFromData = syncFromData)
            },
            updateFromEditor = { renderPreview -> updateFromEditor(renderPreview) },
            render = ::render,
            loadBackgroundOverlayImage = { loadBackgroundOverlayImage(allowRender = false) },
            setStatus = { statusBarLabel.text = it },
            currentPathLabel = { visibleImages.getOrNull(currentIndex)?.let(::relativePath).orEmpty() }
        )
    }
    private val collectionMutationController by lazy {
        CollectionMutationController(
            database = { database },
            actions = { collectionActions() },
            currentData = { currentData },
            hasSelection = { currentIndex in visibleImages.indices },
            saveCurrent = { saveCurrent(showStatus = false) },
            currentTemplate = { currentTemplate() },
            selectedTemplate = { templateChoice.value },
            templateOverrideSelected = { templateOverride.isSelected },
            collectionTemplateChoice = collectionTemplateChoice,
            setCollectionDefaultTemplateName = { collectionDefaultTemplateName = it },
            clearCardPreviews = { browserPreviews.clearCards() },
            withSuppressedUpdates = { action ->
                val previous = suppressEditorUpdates
                suppressEditorUpdates = true
                try {
                    action()
                } finally {
                    suppressEditorUpdates = previous
                }
            },
            reloadCurrentTemplate = {
                loadTemplateAndOverlay()
                recalculatePanControls(resetPan = false)
                render()
                populateEditor()
            },
            refreshAfterMutation = ::refreshAfterCollectionMutation,
            setStatus = { statusBarLabel.text = it }
        )
    }
    private val filterApplyPause = PauseTransition(Duration.millis(180.0))
    private val generation = AtomicInteger()
    private var filterTask: Task<List<Path>>? = null

    override fun start(stage: Stage) {
        stage.title = "Cetruo Desktop"
        AppPlatform.setApplicationDockIcon(javaClass)
        AppPlatform.installWindowIcon(stage, javaClass)
        loadSchemes()
        loadOverlays()
        loadTemplates()

        val mainSplit = SplitPane(browser(), cardPreviewPane).apply {
            setDividerPosition(0, 0.34)
        }
        appToolbar = AppToolbar(
            initialDarkTheme = uiTheme == UiTheme.DARK,
            onOpen = { openDirectory(stage) },
            onPrevious = { navigate(-1) },
            onNext = { navigate(1) },
            onSave = ::saveCurrentExplicitly,
            onUndo = ::undoCardChange,
            onRedo = ::redoCardChange,
            onRandomize = { cardRandomizationController.randomizeStyleAndNumbers() },
            onToggleTheme = {
                uiTheme = if (uiTheme == UiTheme.DARK) UiTheme.LIGHT else UiTheme.DARK
                AppPreferences.put("uiTheme", uiTheme.name)
                applyUiTheme()
                uiTheme == UiTheme.DARK
            },
            onHistory = ::showHistory,
            onDatabaseInfo = ::showDatabaseInfo,
            onBackupCatalog = { backupCatalog(stage) },
            onShareSidecar = ::createSharingSidecar,
            onExportSvg = { exportController.exportSvg(stage) },
            onExportPng = { exportController.exportPng(stage) },
            onExportContactSheetPdf = { exportController.exportContactSheetPdf(stage) },
            onExportCardsPdf = { exportController.exportCardsPdf(stage) },
            onContactSheet = { exportController.showContactSheet(stage) }
        )
        appRoot = BorderPane().apply {
            styleClass.add("cetruo-root")
            top = appToolbar
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
        scene.accelerators[KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN)] = Runnable { saveCurrentExplicitly() }
        installUiThemeKey(scene)
        showEditorGuides.selectedProperty().addListener { _, _, value ->
            AppPreferences.putBoolean("showEditorGuides", value)
            if (currentIndex in visibleImages.indices) render()
        }
        scene.accelerators[KeyCodeCombination(KeyCode.G, KeyCombination.SHORTCUT_DOWN)] = Runnable {
            showEditorGuides.isSelected = !showEditorGuides.isSelected
        }
        scene.accelerators[KeyCodeCombination(KeyCode.I, KeyCombination.SHORTCUT_DOWN)] = Runnable {
            showSourceImageInspector()
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
        stage.setOnCloseRequest { event ->
            // A failed or cancelled save must not silently discard the current card.
            // Keep the application open so the user can resolve the problem and retry.
            if (!autosaveGuard.ensureSaved(showStatus = true)) {
                event.consume()
                return@setOnCloseRequest
            }
            collectionOpenController.cancel()
            filterTask?.cancel()
            filterApplyPause.stop()
            thumbnailRefreshPause.stop()
            collectionWatcher.stop()
            sourceImageInspector.close()
            closeCollection()
            browserPreviews.shutdown()
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

    private fun browser(): BrowserPane {
        browserPane.onFilterChanged = {
            filterApplyPause.stop()
            filterApplyPause.setOnFinished { applyBrowserFilter() }
            filterApplyPause.playFromStart()
        }
        browserPane.onModeRequested = ::switchBrowserMode
        browserPane.onPreviewModeRequested = ::switchBrowserPreviewMode
        browserPane.onCollectionSelected = { option ->
            if (!suppressCollectionChoice) openCollectionPath(option.root)
        }
        browserPane.onSortChanged = { value ->
            AppPreferences.put("browserSort", value.name)
            rebuildVisibleSorted()
        }
        browserPane.onSortDescendingChanged = { value ->
            AppPreferences.putBoolean("browserSortDescending", value)
            rebuildVisibleSorted()
        }
        browserPane.onFolderSelected = { folder ->
            if (!suppressFolderSelection) {
                val normalizedFolder = folder.toAbsolutePath().normalize()
                if (nestedCollectionRoots.contains(normalizedFolder)) {
                    openCollectionPath(normalizedFolder)
                } else if (folder != selectedFolder) {
                    if (currentIndex !in visibleImages.indices || autosaveGuard.ensureSaved()) {
                        selectedFolder = folder
                        requestVisibleImagesRebuild()
                    }
                }
            }
        }
        browserPane.onBrowserFocused = ::refreshBrowserSelectionStyles
        browserPane.onGridWidthChanged = {
            val newColumns = calculateBrowserColumns()
            if (newColumns != browserColumns) {
                val anchor = firstVisibleBrowserPath(gridList, true)
                browserColumns = newColumns
                rebuildGrid(anchor)
            }
        }

        folderTree.setCellFactory {
            object : TreeCell<Path>() {
                override fun updateItem(item: Path?, empty: Boolean) {
                    super.updateItem(item, empty)
                    if (empty || item == null) {
                        text = null
                        tooltip = null
                        graphic = null
                        return
                    }
                    val root = collectionRoot
                    val isCollection =
                        item != root && nestedCollectionRoots.contains(item.toAbsolutePath().normalize())
                    text = when {
                        root != null && item == root -> "📁 ${item.fileName} · All Images"
                        isCollection -> "◈ ${item.fileName} · Collection"
                        else -> "📁 ${item.fileName}"
                    }
                    tooltip = Tooltip(if (root != null) relativePath(item) else item.toString())
                    contextMenu = ContextMenu(
                        MenuItem("Open in Finder").apply { setOnAction { revealInFinder(item) } },
                        MenuItem("Copy Path").apply {
                            setOnAction {
                                val content = ClipboardContent().apply {
                                    putString(item.toAbsolutePath().toString())
                                }
                                Clipboard.getSystemClipboard().setContent(content)
                            }
                        }
                    )
                }
            }
        }

        browserPane.configureCells(
            listCellFactory = { browserTiles.listCell() },
            gridCellFactory = { browserTiles.gridCell() }
        )
        return browserPane
    }

    private fun calculateBrowserColumns(): Int = browserPane.calculateColumns()

    private fun switchBrowserPreviewMode(mode: BrowserPreviewMode) {
        if (mode == browserPreviewMode) return
        if (currentIndex in visibleImages.indices && !saveCurrent(showStatus = false)) {
            browserPane.syncPreviewMode(browserPreviewMode)
            return
        }
        browserPreviewMode = mode
        browserPane.syncPreviewMode(mode)

        // Preview mode is part of tile content, not just selection styling. Recreate the
        // visible cells once so each tile requests the newly selected preview type immediately.
        // This is intentionally limited to mode changes; ordinary selection still avoids
        // ListView.refresh() to prevent thumbnail flicker.
        imageList.refresh()
        gridList.refresh()
        refreshBrowserSelectionStyles()
    }

    private fun switchBrowserMode(mode: ImageBrowserMode) {
        if (mode == imageBrowserMode) return
        if (currentIndex in visibleImages.indices && !saveCurrent(showStatus = false)) {
            browserPane.syncMode(imageBrowserMode)
            return
        }
        val anchor = firstVisibleBrowserPath(activeBrowserNodeList(), imageBrowserMode == ImageBrowserMode.THUMBNAILS)
        imageBrowserMode = mode
        browserPane.syncMode(mode)
        browserColumns = calculateBrowserColumns()
        if (mode == ImageBrowserMode.LIST) {
            rebuildImageList(anchor)
        } else {
            rebuildGrid(anchor)
        }
        syncBrowserSelection(imagesCurrentPath(), forceScroll = false)
        refreshBrowserSelectionStyles()
    }

    private fun activeBrowserNodeList(): ListView<*> = browserSelection.activeList()

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

    private fun refreshBrowserSelectionStyles() = browserTiles.refreshSelectionStyles()

    private fun scrollToBrowserPath(path: Path?, force: Boolean) =
        browserSelection.scrollToPath(path, force)

    private fun firstVisibleBrowserPath(view: ListView<*>, grid: Boolean): Path? =
        browserSelection.firstVisiblePath(view, grid)

    private fun syncBrowserSelection(path: Path?, forceScroll: Boolean) =
        browserSelection.syncSelection(path, forceScroll)

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
        val inspect = MenuItem("Inspect Source Image")
        inspect.setOnAction { showSourceImageInspector(path) }
        val reveal = MenuItem("Reveal in Finder")
        reveal.setOnAction { revealInFinder(path) }
        val copy = MenuItem("Copy Path")
        copy.setOnAction {
            val content = ClipboardContent().apply { putString(path.toAbsolutePath().toString()) }
            Clipboard.getSystemClipboard().setContent(content)
        }
        return ContextMenu(inspect, reveal, copy)
    }

    private fun showSourceImageInspector(path: Path? = imagesCurrentPath()) {
        val source = path?.toAbsolutePath()?.normalize() ?: return
        sourceImageInspector.show(
            owner = scene.window as? Stage,
            source = source,
            onUnavailable = { statusBarLabel.text = "Source image is no longer available." }
        )
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
            BrowserPreviewMode.ORIGINAL -> browserPreviews.requestOriginal(path, onLoaded)
            BrowserPreviewMode.CARD -> browserPreviews.requestCard(path, onLoaded)
        }
    }

    private fun requestCardThumbnail(path: Path, onLoaded: (Image?) -> Unit) =
        browserPreviews.requestCard(path, onLoaded)

    private fun cardPreviewSignature(path: Path, dataOverride: CardData? = null): String {
        val normalized = path.toAbsolutePath().normalize()
        val data = dataOverride
            ?: if (currentLoadedPath?.toAbsolutePath()?.normalize() == normalized) currentData
            else cardStore.snapshot(normalized)
        val dataSignature = data?.let { runCatching { JsonSupport.mapper.writeValueAsString(it) }.getOrDefault(it.toString()) }
            ?: "UNINITIALIZED"
        val presentationSignature = runCatching {
            JsonSupport.mapper.writeValueAsString(collectionPresentation)
        }.getOrDefault(collectionPresentation.toString())
        return "$collectionDefaultTemplateName|$presentationSignature|$dataSignature"
    }

    private fun renderCardPreviewImage(image: Image, data: CardData): Image? {
        // Reuse the exact export renderer so the in-app contact sheet cannot diverge
        // from PNG/PDF output. This method is only invoked on the JavaFX thread.
        val template = templateForData(data) ?: return null
        val templateImage = TemplateRepository.rasterize(template, data.rarity)
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
            System.err.println("Cetruo Desktop: card preview failed for ${data.assetId.ifBlank { "<no-id>" }}: ${error.message}")
            error.printStackTrace()
        }.getOrNull()
    }

    private fun savedDataForPath(
        path: Path,
        derivedColorOverride: Color? = null,
        analyzeImageIfNeeded: Boolean = true
    ): CardData {
        val normalized = path.toAbsolutePath().normalize()
        cardStore.snapshot(normalized)?.takeIf(CardPersistenceService::hasMeaningfulCardData)?.let { return it.copy() }
        val result = newCardDefaults(normalized, derivedColorOverride, analyzeImageIfNeeded)
        cardStore.put(normalized, result)
        return result
    }

    private fun templateForData(data: CardData): CardTemplate? {
        val effectiveName = data.templateName.ifBlank { collectionDefaultTemplateName }
        return templates.firstOrNull { it.name == effectiveName } ?: templates.firstOrNull()
    }

    private fun editor(): ScrollPane {
        val form = VBox(10.0).apply { padding = Insets(12.0); prefWidth = 430.0 }

        form.children.add(editorUi.section("Collection"))
        collectionSettingsPane = CollectionSettingsPane(
            initial = collectionPresentation,
            onPresentationChanged = { value ->
                collectionPresentation = value
                persistCollectionPresentation()
                render()
            },
            onApplySetNameToAll = { collectionMutationController.applyCurrentSetNameToAll() },
            onNormalizeCollectorTotals = { collectionMutationController.normalizeCollectorTotals() }
        )
        collectionSettingsPane.setCardCount(allImages.size)
        form.children.add(collectionSettingsPane)
        form.children.add(editorUi.helperLabel("These presentation options belong to the collection and apply to every card."))

        form.children.add(cardMetadataPane)

        form.children.add(editorUi.section("Scheme"))
        schemeChoice.setCellFactory { editorUi.schemeCell() }
        schemeChoice.buttonCell = editorUi.schemeCell()
        form.children.add(editorUi.rowWithDice("Color scheme", schemeChoice) { cardRandomizationController.randomizeScheme() })
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
        form.children.add(editorUi.helperLabel("Schemes are editable JSON files under schemes/. Each scheme includes a distinct card backgroundColor."))

        form.children.add(editorUi.section("Layout"))
        templateChoice.setCellFactory { editorUi.templateCell() }
        templateChoice.buttonCell = editorUi.templateCell()
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
        collectionTemplateChoice.setCellFactory { editorUi.templateCell() }
        collectionTemplateChoice.buttonCell = editorUi.templateCell()
        collectionTemplateChoice.valueProperty().addListener { _, old, value ->
            if (!suppressEditorUpdates && value != null && value != old) {
                collectionMutationController.setCollectionDefaultTemplate(value)
            }
        }
        form.children.add(editorUi.row("This card template", templateChoice))
        form.children.add(templateOverride.apply {
            tooltip = Tooltip("On: this card stores its own template. Off: this card follows the collection default template.")
        })
        form.children.add(editorUi.row("Collection default template", collectionTemplateChoice))
        form.children.add(HBox(8.0).apply {
            children.add(Button("Use collection default for this card").apply {
                maxWidth = Double.MAX_VALUE
                setOnAction { useCollectionDefaultForCurrentCard() }
                HBox.setHgrow(this, Priority.ALWAYS)
            })
            children.add(Button("Apply this template to all cards").apply {
                maxWidth = Double.MAX_VALUE
                tooltip = Tooltip("Write the selected This card template explicitly to every card and also make it the collection default.")
                setOnAction { collectionMutationController.applyCollectionDefaultTemplateToAll() }
                HBox.setHgrow(this, Priority.ALWAYS)
            })
        })
        val reloadTemplates = Button("Reload templates").apply { setOnAction { loadTemplates() } }
        form.children.add(reloadTemplates)
        form.children.add(editorUi.helperLabel("Collection default is the fallback for cards without a custom template. Applying this template to all cards writes it explicitly to every card and also updates the collection default."))

        form.children.add(editorUi.section("Artwork"))
        imageMode.items.setAll(ImageMode.entries)
        imageMode.setCellFactory { editorUi.imageModeCell() }
        imageMode.buttonCell = editorUi.imageModeCell()
        imageMode.tooltip = Tooltip("Crop to Fill, Fit + Pad, and Stretch can all zoom below 1×; the image frame then shows the pad color.")
        imageMode.valueProperty().addListener { _, _, value ->
            if (!suppressEditorUpdates && value != null) {
                recalculatePanControls(resetPan = false)
                updateFromEditor(renderPreview = false)
                refreshArtworkOnly()
            }
        }
        form.children.add(editorUi.row("Fit", imageMode))
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
        form.children.add(editorUi.sliderRow("Bleed opacity", imageBleedOpacity, imageBleedOpacityValue, "%.0f%%"))
        form.children.add(editorUi.helperLabel("Drag the artwork to pan. Scroll to zoom. Double-click the artwork or use Reset to return to centered 1×."))

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
        form.children.add(editorUi.sliderRow("Zoom", zoom, zoomValueLabel, "%.2f×"))
        offsetX.isShowTickMarks = true
        offsetX.isShowTickLabels = true
        offsetX.majorTickUnit = 1.0
        offsetY.isShowTickMarks = true
        offsetY.isShowTickLabels = true
        offsetY.majorTickUnit = 1.0
        form.children.add(editorUi.sliderRow("Position X", offsetX, xValueLabel, "%+.0f px"))
        form.children.add(editorUi.sliderRow("Position Y", offsetY, yValueLabel, "%+.0f px"))
        form.children.add(editorUi.row("Pad color", imagePadColor))
        installSliderReset(zoom, 1.0)
        installSliderReset(offsetX, 0.0)
        installSliderReset(offsetY, 0.0)

        zoom.valueProperty().addListener { _, _, value ->
            if (!suppressEditorUpdates) {
                recalculatePanControls(resetPan = false)
                updateFromEditor(renderPreview = false)
                refreshArtworkOnly()
                editorUi.updateValueLabel(zoomValueLabel, value.toDouble(), "%.2f×")
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

        form.children.add(editorUi.section("Background"))
        form.children.add(editorUi.row("Card background", backgroundColor))
        backgroundOverlayChoice.setCellFactory { editorUi.overlayCell() }
        backgroundOverlayChoice.buttonCell = editorUi.overlayCell()
        form.children.add(editorUi.rowWithDice("SVG overlay", backgroundOverlayChoice) { cardRandomizationController.randomizeOverlay() })
        form.children.add(editorUi.row("Overlay tint", overlayColor))
        overlayPlacementChoice.items.setAll(OverlayPlacement.entries)
        overlayPlacementChoice.setCellFactory { editorUi.overlayPlacementCell() }
        overlayPlacementChoice.buttonCell = editorUi.overlayPlacementCell()
        form.children.add(editorUi.row("Overlay layer", overlayPlacementChoice))
        form.children.add(editorUi.sliderRow("Overlay opacity", overlayOpacity, Label(), "%.2f"))
        installSliderReset(panelOpacity, 0.96)
        installSliderReset(overlayOpacity, 1.0)
        installSchemeColorReset(imagePadColor, { it.imagePadColor }, "#0A0D10")
        installSchemeColorReset(backgroundColor, { it.backgroundColor }, "#161B22")
        installSchemeColorReset(panelColor, { it.panelColor }, "#EFE8D7")
        installSchemeColorReset(frameColor, { it.frameColor }, "#D9C28E")
        installSchemeColorReset(accentColor, { it.accentColor }, "#8C8068")
        installSchemeColorReset(overlayColor, { it.overlayColor }, "#C9B37A")
        form.children.add(Button("Reload overlays").apply { setOnAction { loadOverlays() } })
        form.children.add(editorUi.helperLabel("Frames only puts the overlay behind the image and text boxes; Over content places it on top of the complete card."))

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

        form.children.add(editorUi.section("Appearance"))
        listOf(panelColor, frameColor, accentColor).forEach { picker ->
            picker.valueProperty().addListener { _, _, _ -> if (!suppressEditorUpdates) updateFromEditor() }
        }
        form.children.add(editorUi.row("Panel", panelColor))
        form.children.add(editorUi.row("Frame", frameColor))
        form.children.add(editorUi.row("Accent", accentColor))
        form.children.add(editorUi.row("Border", border))
        form.children.add(editorUi.row("Corner radius", radius))
        form.children.add(editorUi.sliderRow("Description background opacity", panelOpacity, Label(), "%.2f"))
        form.children.add(editorUi.row("Title size", titleSize))
        form.children.add(editorUi.row("Body size", bodySize))

        return ScrollPane(form).apply { isFitToWidth = true }
    }

    private fun statusLabel(status: CardStatus): String = when (status) {
        CardStatus.NEW -> "New"
        CardStatus.IN_PROGRESS -> "In progress"
        CardStatus.READY -> "Ready"
        CardStatus.EXPORTED -> "Exported"
        CardStatus.ARCHIVED -> "Archived"
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

    private fun statusBar() = HBox(statusBarLabel).apply { padding = Insets(6.0, 12.0, 6.0, 12.0) }

    private fun randomGenerator(): kotlin.random.Random {
        val now = java.time.Instant.now()
        val micros = now.epochSecond * 1_000_000L + now.nano / 1_000L
        val seed = micros xor System.nanoTime() xor randomSequence.incrementAndGet() xor UUID.randomUUID().mostSignificantBits
        return kotlin.random.Random(seed)
    }

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
            schemeChoice.setCellFactory { editorUi.schemeCell() }
            schemeChoice.buttonCell = editorUi.schemeCell()
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

    private fun useCollectionDefaultForCurrentCard() {
        if (currentIndex !in visibleImages.indices) return
        val collectionTemplate = templates.firstOrNull { it.name == collectionDefaultTemplateName }
            ?: collectionTemplateChoice.value
            ?: return

        // Clearing the override changes the persisted model/effective renderer template.
        // Synchronize the visible per-card selector as well so the UI cannot display a
        // stale custom template while the card is actually following the collection default.
        templateOverride.isSelected = false
        suppressEditorUpdates = true
        try {
            templateChoice.value = collectionTemplate
        } finally {
            suppressEditorUpdates = false
        }
    }

    private fun currentTemplate(): CardTemplate? = templateForData(currentData)


    private fun loadTemplateAndOverlay() {
        val template = currentTemplate() ?: return
        templateImage = TemplateRepository.rasterize(template, currentData.rarity)
        backgroundOverlayImage = overlays.firstOrNull { it.path?.fileName?.toString() == currentData.backgroundOverlay }
            ?.path?.let { OverlayRepository.rasterize(it, template.width, template.height, currentData.overlayColor) }
    }

    private fun loadBackgroundOverlayImage(allowRender: Boolean = true) {
        loadTemplateAndOverlay()
        if (allowRender && currentIndex in visibleImages.indices) render()
    }

    private fun chooseRecentCollectionOnStartup(stage: Stage) {
        if (collectionRoot != null) return
        when (val choice = StartupCatalogChooser.choose(stage, RecentCatalogs.list(), RecentCatalogs::forget)) {
            is StartupCatalogChooser.Choice.Open -> openCollectionPath(choice.path)
            StartupCatalogChooser.Choice.Browse -> {
                val directory = DirectoryChooser().apply { title = "Choose Image Directory" }.showDialog(stage)?.toPath()
                if (directory != null) openCollectionPath(directory)
            }
            StartupCatalogChooser.Choice.Cancel -> Unit
        }
    }

    private fun collectionActions(): CollectionEditorActions? = database?.let { db ->
        CollectionEditorActions(
            database = db,
            images = { allImages.toList() },
            initializeCard = { path -> newCardDefaults(path) }
        )
    }

    private fun refreshAfterCollectionMutation() {
        val selectedPath = currentLoadedPath?.toAbsolutePath()?.normalize()
        cardStore.clear()
        browserSearchIndex.clear()
        database?.searchIndex()?.let(browserSearchIndex::replaceAll)
        browserPreviews.clearCards()
        if (selectedPath != null) {
            val index = visibleImages.indexOfFirst { it.toAbsolutePath().normalize() == selectedPath }
            if (index >= 0) {
                currentIndex = -1
                currentLoadedPath = null
                select(index, scrollIntoView = false)
            }
        }
        imageList.refresh()
        gridList.refresh()
    }

    private fun openDirectory(stage: Stage) {
        val directory = DirectoryChooser().apply { title = "Choose Image Directory" }.showDialog(stage)?.toPath() ?: return
        openCollectionPath(directory)
    }

    private fun openCollectionPath(directory: Path) {
        if (!autosaveGuard.ensureSaved()) return
        generation.incrementAndGet()
        filesystemRefreshController.invalidate()
        filterTask?.cancel()
        collectionOpenController.open(directory)
    }

    private fun applyOpenedCollection(opened: OpenedCollection) {
        val normalized = opened.root
        val newDatabase = opened.database
        try {
            database?.close()
            database = newDatabase
            collectionRoot = normalized
            collectionPresentation = newDatabase.getCollectionPresentation()
            collectionDefaultTemplateName =
                newDatabase.getDefaultTemplateName().ifBlank { templates.firstOrNull()?.name.orEmpty() }
            if (collectionDefaultTemplateName.isNotBlank()) {
                newDatabase.setDefaultTemplateName(collectionDefaultTemplateName)
            }
            nestedCollectionRoots = opened.scan.nestedCollections
            suppressEditorUpdates = true
            try {
                collectionTemplateChoice.items.setAll(templates)
                collectionTemplateChoice.value =
                    templates.firstOrNull { it.name == collectionDefaultTemplateName } ?: templates.firstOrNull()
                if (::collectionSettingsPane.isInitialized) {
                    collectionSettingsPane.setPresentation(collectionPresentation)
                }
                refreshCollectionChoices()
            } finally {
                suppressEditorUpdates = false
            }
            RecentCatalogs.record(normalized)
            collectionWatcher.start(normalized)
            allImages.clear()
            allImages.addAll(opened.scan.images)
            if (::collectionSettingsPane.isInitialized) collectionSettingsPane.setCardCount(allImages.size)
            migrateLegacySidecars(newDatabase, allImages)
            visibleImages.clear()
            currentIndex = -1
            currentLoadedPath = null
            currentData = newCardDefaults()
            selectedFolder = normalized
            browserPreviews.clearOriginals()
            browserPreviews.clearCards()
            fullImageCache.clear()
            TemplateRepository.clearCache()
            OverlayRepository.clearCache()
            browserSearchIndex.clear()
            browserSearchIndex.replaceAll(newDatabase.searchIndex())
            cardStore.clear()
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
            val nestedNote =
                if (nestedCollectionRoots.isNotEmpty()) "; ${nestedCollectionRoots.size} nested collection(s) available" else ""
            statusBarLabel.text =
                "Collection: ${normalized.fileName} • ${allImages.size} images • DB ${newDatabase.path.fileName}$nestedNote"
        } catch (e: Exception) {
            if (database !== newDatabase) runCatching { newDatabase.close() }
            showError("Could not open collection", e)
        }
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
        browserImageSorter.sort(
            paths = visibleImages,
            sort = browserSortChoice.value ?: BrowserSort.NAME,
            descending = browserSortDescending.isSelected
        )
    }

    private fun rebuildVisibleSorted() {
        val previousPath = imagesCurrentPath()
        sortVisibleImagesInPlace()
        currentIndex = previousPath?.let { visibleImages.indexOf(it) } ?: -1
        rebuildImageList(previousPath)
        if (previousPath != null && currentIndex >= 0) syncBrowserSelection(previousPath, false)
    }

    private fun rebuildBrowserImmediately() {
        val rootPath = collectionRoot ?: return
        val scope = selectedFolder?.takeIf { it.startsWith(rootPath) } ?: rootPath
        val query = filterField.text.trim().lowercase()
        val previousPath = imagesCurrentPath()
        visibleImages.clear()
        visibleImages.addAll(
            browserImageView.build(
                paths = allImages,
                scope = scope,
                query = query,
                sort = browserSortChoice.value ?: BrowserSort.NAME,
                descending = browserSortDescending.isSelected
            )
        )
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
            visibleImages.addAll(
                browserImageView.build(
                    paths = allImages,
                    scope = scope,
                    query = query,
                    sort = browserSortChoice.value ?: BrowserSort.NAME,
                    descending = browserSortDescending.isSelected
                )
            )
            setCurrentPathAfterRebuild(previousPath)
            browserCountLabel.text = if (scope == rootPath) "${visibleImages.size} images" else "${visibleImages.size}/${allImages.size} images"
            rebuildImageList()
            return
        }
        filterTask?.cancel()
        val token = generation.get()
        val snapshot = browserSearchIndex.snapshot()
        val sort = browserSortChoice.value ?: BrowserSort.NAME
        val descending = browserSortDescending.isSelected
        statusBarLabel.text = "Filtering ${allImages.size} images…"
        val task = object : Task<List<Path>>() {
            override fun call(): List<Path> =
                browserImageView.build(
                    paths = allImages,
                    scope = scope,
                    query = query,
                    sort = sort,
                    descending = descending,
                    snapshot = snapshot,
                    isCancelled = { isCancelled }
                )
        }
        filterTask = task
        task.setOnSucceeded {
            if (generation.get() != token) return@setOnSucceeded
            visibleImages.clear()
            visibleImages.addAll(task.value)
            setCurrentPathAfterRebuild(previousPath)
            browserCountLabel.text = "${visibleImages.size}/${allImages.size} images"
            rebuildImageList()
            if (currentIndex >= 0) refreshBrowserSelectionStyles()
            statusBarLabel.text = "Filter: ${visibleImages.size}/${allImages.size} images match."
        }
        task.setOnFailed {
            if (generation.get() == token) showError("Could not filter images", task.exception ?: RuntimeException("Unknown filtering error"))
        }
        Thread(task, "cetruo-filter").apply { isDaemon = true }.start()
    }

    private fun setCurrentPathAfterRebuild(previousPath: Path?) {
        val index = cardSelectionResolver.indexAfterRebuild(visibleImages, previousPath)
        if (index != null) {
            select(index, scrollIntoView = false)
        } else {
            currentIndex = -1
            clearEditorForNoSelection()
        }
    }

    private fun buildFolderTree(rootPath: Path, selected: Path) {
        val tree = browserFolderTreeBuilder.build(
            rootPath = rootPath,
            images = allImages,
            nestedCollections = nestedCollectionRoots,
            selected = selected,
            expandedBefore = browserFolderTreeBuilder.expandedPaths(folderTree.root)
        )
        suppressFolderSelection = true
        try {
            folderTree.root = tree.root
            folderTree.selectionModel.select(tree.itemsByPath[selected] ?: tree.root)
        } finally {
            suppressFolderSelection = false
        }
    }

    private fun applyBrowserFilter() {
        if (currentIndex in visibleImages.indices && !saveCurrent(showStatus = false)) return
        requestVisibleImagesRebuild()
    }

    private fun resolveCardDataForSelection(path: Path): CardData =
        cardPersistence.resolveForSelection(
            database = database,
            cardStore = cardStore,
            path = path,
            newCard = { newCardDefaults(it) },
            nextCollectorNumber = {
                cardDefaultsGenerator.nextUnusedCollectorNumber(randomGenerator())
            }
        )

    private fun dataEquivalent(a: CardData, b: CardData): Boolean = runCatching {
        JsonSupport.mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(a) ==
            JsonSupport.mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(b)
    }.getOrDefault(false)

    private fun newCardDefaults(
        path: Path? = null,
        derivedColorOverride: Color? = null,
        analyzeImageIfNeeded: Boolean = true
    ): CardData = cardDefaultsGenerator.create(path, derivedColorOverride, analyzeImageIfNeeded)

    private fun select(newIndex: Int, scrollIntoView: Boolean = false) {
        if (newIndex !in visibleImages.indices) return
        val imagePath = visibleImages[newIndex].toAbsolutePath().normalize()
        if (newIndex == currentIndex && currentLoadedPath == imagePath) {
            if (scrollIntoView) scrollToBrowserPath(imagePath, true)
            return
        }
        if (currentIndex in visibleImages.indices && !autosaveGuard.ensureSaved()) return
        val resolvedData = resolveCardDataForSelection(imagePath)
        currentIndex = newIndex
        currentLoadedPath = imagePath
        currentData = resolvedData
        cardStore.put(imagePath, resolvedData)
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

    private fun currentVisibleIndex(): Int =
        cardSelectionResolver.currentVisibleIndex(visibleImages, currentLoadedPath, currentIndex)

    private fun imagesCurrentPath(): Path? =
        cardSelectionResolver.currentPath(visibleImages, currentLoadedPath, currentIndex)

    private fun populateEditor() {
        suppressEditorUpdates = true
        try {
            cardEditorBinding.populate(
                data = currentData,
                schemes = schemes,
                effectiveTemplate = currentTemplate(),
                overlays = overlays
            )
        } finally {
            suppressEditorUpdates = false
        }
        recalculatePanControls(resetPan = false, syncFromData = true)
        suppressUndoCapture = true
        try {
            updateFromEditor(renderPreview = false)
        } finally {
            suppressUndoCapture = false
            ignoreNextUndoCapture = false
        }
    }

    private fun populateColorPickersFromData() {
        cardEditorBinding.populateColors(currentData)
    }

    private fun safeColor(hex: String, fallback: String): Color = runCatching { Color.web(hex) }.getOrElse { Color.web(fallback) }

    private fun setArtwork(mode: ImageMode, newZoom: Double, normalizedX: Double, normalizedY: Double) {
        captureUndoSnapshot()
        artworkEditorController.setArtwork(mode, newZoom, normalizedX, normalizedY)
        updateFromEditor(renderPreview = false)
        refreshArtworkOnly()
    }

    private fun resetArtworkPositionAndZoom() {
        captureUndoSnapshot()
        artworkEditorController.resetPositionAndZoom()
        updateFromEditor(renderPreview = false)
        refreshArtworkOnly()
    }

    private fun centerArtwork() {
        captureUndoSnapshot()
        artworkEditorController.center()
        updateFromEditor(renderPreview = false)
        refreshArtworkOnly()
    }

    private fun recalculatePanControls(resetPan: Boolean, syncFromData: Boolean = false) {
        artworkEditorController.recalculatePanControls(resetPan, syncFromData)
    }

    private fun updateValueLabelFromActualPan() {
        artworkEditorController.updatePanLabelsFromControls()
    }

    private fun currentActualPanX(): Double = artworkEditorController.currentActualPanX()

    private fun currentActualPanY(): Double = artworkEditorController.currentActualPanY()

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
        cardEditorBinding.applyTo(
            current = currentData,
            imageOffsetX = currentActualPanX(),
            imageOffsetY = currentActualPanY()
        )
        val currentPath = visibleImages[activeIndex]
        val afterSignature = cardSnapshotSignature(currentData)
        if (beforeSignature != afterSignature) {
            if (!suppressUndoCapture && !ignoreNextUndoCapture) {
                undoManager.record(JsonSupport.mapper.readValue(beforeSignature, CardData::class.java))
            }
            ignoreNextUndoCapture = false
        }
        val normalizedCurrentPath = currentPath.toAbsolutePath().normalize()
        val editorChanged = beforeSignature != afterSignature
        cardStore.put(normalizedCurrentPath, currentData)
        browserSearchIndex.update(currentPath, currentData)

        if (editorChanged) {
            // Debounce rendered browser thumbnails while editing. Keep the existing tile/image
            // visible during the pause and refresh only this card after editing has been idle.
            browserPreviews.removeCard(normalizedCurrentPath)
            thumbnailRefreshPause.stop()
            thumbnailRefreshPause.setOnFinished {
                if (currentLoadedPath?.toAbsolutePath()?.normalize() == normalizedCurrentPath) {
                    browserTiles.refreshPath(normalizedCurrentPath)
                }
            }
            thumbnailRefreshPause.playFromStart()
        }

        if (renderPreview) render()
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
                artworkEditorController.dragBy(dx, dy)
                updateFromEditor(renderPreview = false)
                refreshArtworkOnly()
            },
            onImageZoomed = { delta ->
                artworkEditorController.zoomBy(delta)
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
                if (existing == null || !CardPersistenceService.hasMeaningfulCardData(existing)) {
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

    private fun saveCurrentExplicitly() {
        val path = imagesCurrentPath()?.toAbsolutePath()?.normalize()
        if (!saveCurrent(showStatus = true)) return

        // Explicit save should make the visible rendered-card thumbnail current immediately
        // instead of waiting for the edit debounce. Keep this scoped to the active tile.
        thumbnailRefreshPause.stop()
        if (path != null && browserPreviewMode == BrowserPreviewMode.CARD) {
            browserPreviews.removeCard(path)
            browserTiles.refreshPath(path)
        }
    }

    private fun saveCurrent(showStatus: Boolean = true): Boolean =
        cardSaveController.save(showStatus)

    private fun backupCatalog(stage: Stage) {
        val db = database ?: return
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        val chooser = FileChooser().apply {
            title = "Backup Cetruo Desktop catalog"
            extensionFilters.add(FileChooser.ExtensionFilter("SQLite database", "*.sqlite"))
            initialFileName = "cetruo-backup-${LocalDateTime.now().format(formatter)}.sqlite"
        }
        val target = chooser.showSaveDialog(stage)?.toPath() ?: return
        try {
            db.backupTo(target)
            statusBarLabel.text = "Catalog backup created • ${target.fileName}"
        } catch (e: Exception) {
            showError("Could not create catalog backup", e)
        }
    }

    private fun showHistory() {
        val db = database ?: return
        if (currentData.assetId.isBlank()) return
        showTextDialog("Card history", collectionDiagnostics.history(db.history(currentData.assetId)))
    }

    private fun showDatabaseInfo() {
        val db = database ?: return
        val revisions = if (currentData.assetId.isBlank()) 0 else db.countRevisions(currentData.assetId)
        val message = collectionDiagnostics.databaseInfo(
            CollectionDiagnosticsInfo(
                collectionRoot = collectionRoot,
                databasePath = db.path,
                trackedImages = db.countAssets(),
                visibleImages = visibleImages.size,
                totalImages = allImages.size,
                assetId = currentData.assetId,
                relativePath = visibleImages.getOrNull(currentIndex)?.let(::relativePath),
                statusLabel = statusLabel(currentData.status),
                revisionCount = revisions,
                templateName = currentTemplate()?.name,
                usesCollectionDefaultTemplate = currentData.templateName.isBlank(),
                descriptionHeading = collectionPresentation.descriptionHeading,
                showArtistCopyright = collectionPresentation.showArtistCopyright,
                backgroundColor = currentData.backgroundColor,
                backgroundOverlay = currentData.backgroundOverlay,
                backgroundOverlayPlacement = currentData.backgroundOverlayPlacement.toString(),
                nestedCollectionsSkipped = nestedCollectionsSkipped
            )
        )
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

    private fun scheduleWatcherRefresh() {
        val root = collectionRoot ?: return
        filesystemRefreshController.schedule(root)
    }

    private fun applyFilesystemScanResult(result: CollectionScanResult) {
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

        if (changed) {
            runCatching {
                FileIdentityReconciler.reconcile(previousSet, nextSet, database)
            }
        }

        allImages.clear()
        allImages.addAll(result.images)
        if (::collectionSettingsPane.isInitialized) collectionSettingsPane.setCardCount(allImages.size)
        nestedCollectionRoots = result.nestedCollections
        nestedCollectionsSkipped = nestedCollectionRoots.size
        if (changed) {
            val livePaths = nextSet
            browserSearchIndex.retainOnly(result.images)
            cardStore.retainOnly(livePaths)
            previousSet.asSequence().filter { it !in nextSet }.forEach { removed ->
                browserPreviews.removeOriginal(removed)
                synchronized(fullImageCache) { fullImageCache.remove(removed) }
                browserPreviews.removeCard(removed)
                browserSearchIndex.remove(removed)
            }
        }

        refreshCollectionChoices()
        buildFolderTree(root, selectedFolder ?: root)
        if (changed) {
            val scope = selectedFolder?.takeIf { it.startsWith(root) } ?: root
            val query = filterField.text.trim().lowercase()
            visibleImages.clear()
            visibleImages.addAll(
                browserImageView.build(
                    paths = allImages,
                    scope = scope,
                    query = query,
                    sort = browserSortChoice.value ?: BrowserSort.NAME,
                    descending = browserSortDescending.isSelected
                )
            )
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
        if (!imageScanner.supports(normalized)) return
        browserPreviews.removeOriginal(normalized)
        synchronized(fullImageCache) { fullImageCache.remove(normalized) }
        browserPreviews.removeCard(normalized)
        Platform.runLater {
            if (currentIndex in visibleImages.indices && visibleImages[currentIndex].toAbsolutePath().normalize() == normalized) {
                cropImage = cachedFullImage(normalized)
                refreshArtworkOnly()
            }
        }
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
            AppPreferences.put("uiTheme", uiTheme.name)
            if (::appToolbar.isInitialized) appToolbar.setDarkTheme(uiTheme == UiTheme.DARK)
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
        browserPreviews.clearCards()
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

    private fun showError(title: String, e: Throwable) {
        Alert(Alert.AlertType.ERROR).apply {
            this.title = title
            headerText = e.message ?: e.javaClass.simpleName
            dialogPane.content = Label("Check the collection directory and try again.")
            showAndWait()
        }
    }

    override fun stop() {
        // JavaFX normally reaches here after the window close handler has already saved
        // and closed the collection. Keep a final best-effort save for non-window shutdown
        // paths (for example programmatic Platform.exit()) while the database is still open.
        if (database != null) saveCurrent(showStatus = false)
        collectionOpenController.cancel()
        filterTask?.cancel()
        filterApplyPause.stop()
        browserPreviews.shutdown()
        database?.close()
        database = null
    }

    private fun closeCollection() {
        collectionWatcher.stop()
        filesystemRefreshController.invalidate()
        database?.close()
        database = null
        collectionRoot = null
        allImages.clear()
        visibleImages.clear()
        if (::collectionSettingsPane.isInitialized) collectionSettingsPane.setCardCount(0)
        currentIndex = -1
        renderedCard = null
        undoManager.clear()
        backgroundOverlayImage = null
        templateImage = null
        cropImage = null
        selectedFolder = null
        nestedCollectionRoots = emptyList()
        collectionPresentation = CollectionPresentation()
        browserSearchIndex.clear()
        cardStore.clear()
        suppressCollectionChoice = true
        try { collectionChoice.items.clear() } finally { suppressCollectionChoice = false }
        browserPreviews.clearOriginals()
        browserPreviews.clearCards()
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

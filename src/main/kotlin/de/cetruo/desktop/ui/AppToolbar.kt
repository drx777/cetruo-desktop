package de.cetruo.desktop.ui

import javafx.scene.control.Button
import javafx.scene.control.Separator
import javafx.scene.control.ToolBar
import javafx.scene.control.Tooltip

class AppToolbar(
    initialDarkTheme: Boolean,
    onOpen: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSave: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onRandomize: () -> Unit,
    onToggleTheme: () -> Boolean,
    onHistory: () -> Unit,
    onDatabaseInfo: () -> Unit,
    onBackupCatalog: () -> Unit,
    onShareSidecar: () -> Unit,
    onExportSvg: () -> Unit,
    onExportPng: () -> Unit,
    onExportContactSheetPdf: () -> Unit,
    onExportCardsPdf: () -> Unit,
    onContactSheet: () -> Unit
) : ToolBar() {
    private val themeButton = Button()

    init {
        val open = Button("Open Directory").apply { setOnAction { onOpen() } }
        val previous = Button("← Previous").apply { setOnAction { onPrevious() } }
        val next = Button("Next →").apply { setOnAction { onNext() } }
        val save = Button("Save ⌘S").apply { setOnAction { onSave() } }
        val randomize = Button("Randomize").apply {
            tooltip = Tooltip("Randomize the color scheme, cost, and attack/defense values. Layout is unchanged.")
            setOnAction { onRandomize() }
        }
        themeButton.apply {
            tooltip = Tooltip("Switch the Cetruo Desktop application UI theme. This does not change card colors.")
            setDarkTheme(initialDarkTheme)
            setOnAction { setDarkTheme(onToggleTheme()) }
        }
        val undo = Button("Undo").apply {
            tooltip = Tooltip("Undo the most recent change on the current card (⌘Z / Ctrl+Z).")
            setOnAction { onUndo() }
        }
        val redo = Button("Redo").apply {
            tooltip = Tooltip("Redo the most recent undone card change (⇧⌘Z / Ctrl+Shift+Z).")
            setOnAction { onRedo() }
        }
        val history = Button("History").apply { setOnAction { onHistory() } }
        val databaseInfo = Button("Database").apply { setOnAction { onDatabaseInfo() } }
        val backup = Button("Backup catalog").apply { setOnAction { onBackupCatalog() } }
        val shareSidecar = Button("Share sidecar").apply {
            tooltip = Tooltip("Explicitly create a portable .card.json sidecar for the selected card. Normal saves use SQLite only.")
            setOnAction { onShareSidecar() }
        }
        val exportSvg = Button("Export SVG").apply { setOnAction { onExportSvg() } }
        val exportPng = Button("Export PNG").apply { setOnAction { onExportPng() } }
        val exportPdf = Button("A4 Contact Sheet PDF").apply {
            tooltip = Tooltip("Export all currently visible cards (current folder/filter scope) onto A4 pages at the card's physical size.")
            setOnAction { onExportContactSheetPdf() }
        }
        val exportCardsPdf = Button("Cards PDF").apply {
            tooltip = Tooltip("Export all currently visible cards as a PDF with one borderless card-sized page per card.")
            setOnAction { onExportCardsPdf() }
        }
        val contactSheet = Button("Contact Sheet").apply {
            tooltip = Tooltip("Open a paged visual contact sheet for the current image scope.")
            setOnAction { onContactSheet() }
        }

        items.setAll(
            open,
            Separator(),
            previous,
            next,
            Separator(),
            save,
            undo,
            redo,
            randomize,
            themeButton,
            history,
            databaseInfo,
            backup,
            shareSidecar,
            Separator(),
            exportSvg,
            exportPng,
            exportPdf,
            exportCardsPdf,
            contactSheet
        )
    }

    fun setDarkTheme(dark: Boolean) {
        themeButton.text = if (dark) "☀ Light UI" else "◐ Dark UI"
    }
}

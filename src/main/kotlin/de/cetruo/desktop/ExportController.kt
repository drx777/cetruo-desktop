package de.cetruo.desktop

import javafx.application.Platform
import javafx.scene.control.ComboBox
import javafx.scene.control.Slider
import javafx.scene.image.Image
import javafx.stage.Stage
import java.nio.file.Files
import java.nio.file.Path

class ExportController(
    private val coordinator: ExportCoordinator,
    private val currentData: () -> CardData,
    private val currentPath: () -> Path?,
    private val visiblePaths: () -> List<Path>,
    private val collectionRoot: () -> Path?,
    private val currentTemplate: () -> CardTemplate?,
    private val templateForData: (CardData) -> CardTemplate?,
    private val savedDataForPath: (Path) -> CardData,
    private val cachedFullImage: (Path) -> Image?,
    private val loadTemplateAndOverlay: () -> Unit,
    private val templateImage: () -> Image?,
    private val backgroundOverlayImage: () -> Image?,
    private val collectionPresentation: () -> CollectionPresentation,
    private val saveCurrent: () -> Boolean,
    private val recordActivity: (assetId: String, action: String, details: String) -> Unit,
    private val statusChoice: ComboBox<CardStatus>,
    private val withSuppressedUpdates: (() -> Unit) -> Unit,
    private val setStatus: (String) -> Unit,
    private val showError: (String, Throwable) -> Unit,
    private val installSliderReset: (Slider, Double) -> Unit,
    private val isDarkTheme: () -> Boolean,
    private val requestCardThumbnail: (Path, (Image?) -> Unit) -> Unit,
    private val resourceOwner: Class<*>
) {
    fun exportSvg(stage: Stage) {
        val path = currentPath() ?: return
        if (!saveCurrent()) return
        val template = currentTemplate() ?: return
        val target = ExportUi.chooseTarget(
            owner = stage,
            title = "Export SVG",
            filterLabel = "SVG",
            extensionPattern = "*.svg",
            initialDirectory = defaultExportDirectory(),
            initialFileName = path.fileName.toString().substringBeforeLast('.') + ".card.svg"
        ) ?: return

        try {
            loadTemplateAndOverlay()
            val image = cachedFullImage(path) ?: error("Could not load image ${path.fileName}")
            coordinator.exportSvg(target, exportInput(image, template))
            recordActivity(currentData().assetId, "EXPORT_SVG", target.toAbsolutePath().toString())
            markExported()
            saveCurrent()
            setStatus("Exported ${target.fileName} • full card SVG")
        } catch (e: Exception) {
            showError("Could not export SVG", e)
        }
    }

    fun exportPng(stage: Stage) {
        val path = currentPath() ?: return
        if (!saveCurrent()) return
        val template = currentTemplate() ?: return
        val target = ExportUi.chooseTarget(
            owner = stage,
            title = "Export PNG",
            filterLabel = "PNG",
            extensionPattern = "*.png",
            initialDirectory = defaultExportDirectory(),
            initialFileName = path.fileName.toString().substringBeforeLast('.') + ".card.png"
        ) ?: return

        try {
            loadTemplateAndOverlay()
            val image = cachedFullImage(path) ?: error("Could not load image ${path.fileName}")
            coordinator.exportPng(target, exportInput(image, template))
            recordActivity(currentData().assetId, "EXPORT_PNG", target.toAbsolutePath().toString())
            markExported()
            saveCurrent()
            setStatus("Exported ${target.fileName} • full card PNG")
        } catch (e: Exception) {
            showError("Could not export PNG", e)
        }
    }

    fun exportContactSheetPdf(stage: Stage) {
        val paths = visiblePaths()
        if (paths.isEmpty() || !saveCurrent()) return
        val target = ExportUi.chooseTarget(
            owner = stage,
            title = "Export A4 Contact Sheet PDF",
            filterLabel = "PDF",
            extensionPattern = "*.pdf",
            initialDirectory = defaultExportDirectory(),
            initialFileName = "cetruo-contact-sheet.pdf"
        ) ?: return
        val options = ExportUi.promptPdfOptions(stage, installSliderReset) ?: return
        val plans = coordinator.planPdf(paths, options, ::pdfCardSpec)
        setStatus("Exporting ${paths.size} card(s) to PDF at ${"%.0f".format(options.scale * 100)}%…")
        coordinator.exportPdfAsync(
            target = target,
            plans = plans,
            renderSvgOnFxThread = ::renderCardSvgForPdf,
            onSucceeded = {
                recordActivity(currentData().assetId, "EXPORT_PDF", target.toAbsolutePath().toString())
                setStatus("Exported ${target.fileName} • ${paths.size} full card(s)")
            },
            onFailed = { error -> showError("Could not export PDF", error) }
        )
    }

    fun exportCardsPdf(stage: Stage) {
        val paths = visiblePaths()
        if (paths.isEmpty() || !saveCurrent()) return
        val target = ExportUi.chooseTarget(
            owner = stage,
            title = "Export Borderless Cards PDF",
            filterLabel = "PDF",
            extensionPattern = "*.pdf",
            initialDirectory = defaultExportDirectory(),
            initialFileName = "cetruo-cards.pdf"
        ) ?: return
        val plans = coordinator.planSingleCardPdf(paths, ::pdfCardSpec)
        setStatus("Exporting ${paths.size} card(s) as borderless PDF pages…")
        coordinator.exportPdfAsync(
            target = target,
            plans = plans,
            renderSvgOnFxThread = ::renderCardSvgForPdf,
            onSucceeded = {
                recordActivity(currentData().assetId, "EXPORT_PDF_CARDS", target.toAbsolutePath().toString())
                setStatus("Exported ${target.fileName} • ${paths.size} borderless card page(s)")
            },
            onFailed = { error -> showError("Could not export cards PDF", error) }
        )
    }

    fun showContactSheet(owner: Stage) {
        val paths = visiblePaths()
        if (paths.isEmpty() || !saveCurrent()) return
        ContactSheetWindow(
            resourceOwner = resourceOwner,
            isDarkTheme = isDarkTheme,
            requestPreview = requestCardThumbnail
        ).show(owner, paths)
    }

    private fun exportInput(image: Image, template: CardTemplate): ExportCardInput =
        ExportCardInput(
            image = image,
            data = currentData(),
            template = template,
            templateImage = templateImage(),
            backgroundOverlay = backgroundOverlayImage(),
            collectionPresentation = collectionPresentation()
        )

    private fun defaultExportDirectory(): Path? {
        val root = collectionRoot() ?: return null
        val dir = root.resolve("Cetruo Desktop Exports")
        return runCatching {
            Files.createDirectories(dir)
            dir
        }.getOrNull()
    }

    private fun pdfCardSpec(path: Path): PdfContactSheetExporter.CardSpec? {
        val data = savedDataForPath(path)
        val template = templateForData(data) ?: return null
        val widthPt = (template.width / 10.0) * 72.0 / 25.4
        val heightPt = (template.height / 10.0) * 72.0 / 25.4
        return PdfContactSheetExporter.CardSpec(path, widthPt, heightPt)
    }

    private fun renderCardSvgForPdf(path: Path): String {
        check(Platform.isFxApplicationThread()) {
            "PDF card SVG rendering must run on the JavaFX application thread"
        }
        val data = savedDataForPath(path)
        val template = templateForData(data)
            ?: error("No card template available for ${path.fileName}")
        val sourceImage = cachedFullImage(path)
            ?: error("Could not load image ${path.fileName}")
        return VectorCardSvgRenderer.svgFor(
            image = sourceImage,
            data = data,
            template = template,
            collectionPresentation = collectionPresentation(),
            artworkHref = path.toAbsolutePath().normalize().toUri().toString()
        )
    }

    private fun markExported() {
        withSuppressedUpdates {
            currentData().status = CardStatus.EXPORTED
            statusChoice.value = CardStatus.EXPORTED
        }
    }
}

package com.example.cardforge

import javafx.application.Platform
import javafx.concurrent.Task
import javafx.scene.image.Image
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

data class ExportCardInput(
    val image: Image,
    val data: CardData,
    val template: CardTemplate,
    val templateImage: Image?,
    val backgroundOverlay: Image?,
    val collectionPresentation: CollectionPresentation
)

/**
 * Executes canonical card exports without owning editor, database, or chooser state.
 *
 * MainApp resolves the current card/input and handles status/activity bookkeeping.
 */
class ExportCoordinator {
    fun exportSvg(target: Path, input: ExportCardInput) {
        SvgExporter.export(
            image = input.image,
            data = input.data,
            template = input.template,
            target = target,
            templateImage = input.templateImage,
            backgroundOverlay = input.backgroundOverlay,
            collectionPresentation = input.collectionPresentation
        )
    }

    fun exportPng(target: Path, input: ExportCardInput) {
        val png = ExportRenderer.pngBytes(
            image = input.image,
            data = input.data,
            template = input.template,
            templateImage = input.templateImage,
            backgroundOverlay = input.backgroundOverlay,
            collectionPresentation = input.collectionPresentation,
            scale = 1.0
        )
        Files.write(target, png)
    }

    fun planPdf(
        paths: List<Path>,
        options: PdfExportOptions,
        cardSpecFor: (Path) -> PdfContactSheetExporter.CardSpec?
    ): List<PdfContactSheetExporter.PagePlan> =
        PdfContactSheetExporter.planA4(
            cards = paths.mapNotNull(cardSpecFor),
            scale = options.scale,
            marginMm = options.marginMm,
            gapMm = options.gapMm
        )

    fun planSingleCardPdf(
        paths: List<Path>,
        cardSpecFor: (Path) -> PdfContactSheetExporter.CardSpec?
    ): List<PdfContactSheetExporter.PagePlan> =
        PdfContactSheetExporter.planSingleCardPages(paths.mapNotNull(cardSpecFor))

    fun exportPdfAsync(
        target: Path,
        plans: List<PdfContactSheetExporter.PagePlan>,
        renderPngOnFxThread: (Path) -> ByteArray,
        onSucceeded: () -> Unit,
        onFailed: (Throwable) -> Unit
    ) {
        val task = object : Task<Unit>() {
            override fun call() {
                PdfContactSheetExporter.export(target, plans) { path ->
                    val future = CompletableFuture<ByteArray>()
                    Platform.runLater {
                        try {
                            future.complete(renderPngOnFxThread(path))
                        } catch (t: Throwable) {
                            future.completeExceptionally(t)
                        }
                    }
                    future.get()
                }
            }
        }
        task.setOnSucceeded { onSucceeded() }
        task.setOnFailed { onFailed(task.exception ?: RuntimeException("Unknown PDF export error")) }
        Thread(task, "card-forge-pdf-export").apply { isDaemon = true }.start()
    }
}

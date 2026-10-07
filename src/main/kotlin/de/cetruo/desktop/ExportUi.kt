package de.cetruo.desktop

import javafx.geometry.Insets
import javafx.scene.control.ButtonType
import javafx.scene.control.Dialog
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.layout.GridPane
import javafx.stage.FileChooser
import javafx.stage.Stage
import java.nio.file.Path

data class PdfExportOptions(
    val scale: Double,
    val marginMm: Double,
    val gapMm: Double
)

/** File-target and export-option UI kept separate from rendering/export execution. */
object ExportUi {
    fun chooseTarget(
        owner: Stage,
        title: String,
        filterLabel: String,
        extensionPattern: String,
        initialDirectory: Path?,
        initialFileName: String
    ): Path? {
        val chooser = FileChooser().apply {
            this.title = title
            extensionFilters.add(FileChooser.ExtensionFilter(filterLabel, extensionPattern))
            initialDirectory?.toFile()?.takeIf { it.isDirectory }?.let { this.initialDirectory = it }
            this.initialFileName = initialFileName
        }
        return chooser.showSaveDialog(owner)?.toPath()
    }

    fun promptPdfOptions(
        owner: Stage,
        installSliderReset: (Slider, Double) -> Unit
    ): PdfExportOptions? {
        val dialog = Dialog<ButtonType>().apply {
            initOwner(owner)
            title = "A4 Contact Sheet PDF"
            headerText = "Contact sheet layout"
            dialogPane.buttonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)
        }
        val scale = Slider(0.5, 1.0, 1.0).apply {
            blockIncrement = 0.05
            majorTickUnit = 0.1
        }
        val margin = Slider(0.0, 20.0, 10.0).apply {
            blockIncrement = 1.0
            majorTickUnit = 5.0
        }
        val gap = Slider(0.0, 10.0, 3.0).apply {
            blockIncrement = 0.5
            majorTickUnit = 2.0
        }
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

        dialog.dialogPane.content = GridPane().apply {
            hgap = 10.0
            vgap = 10.0
            padding = Insets(10.0)
            add(Label("Card scale"), 0, 0)
            add(scale, 1, 0)
            add(scaleLabel, 2, 0)
            add(Label("Page margin"), 0, 1)
            add(margin, 1, 1)
            add(marginLabel, 2, 1)
            add(Label("Card gap"), 0, 2)
            add(gap, 1, 2)
            add(gapLabel, 2, 2)
            add(
                Label("100% keeps the template's physical card size. Smaller scales fit more cards per A4 page."),
                0, 3, 3, 1
            )
        }
        dialog.dialogPane.minWidth = 560.0

        return if (dialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            PdfExportOptions(scale.value, margin.value, gap.value)
        } else {
            null
        }
    }
}

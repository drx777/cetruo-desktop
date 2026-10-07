package de.cetruo.desktop.editor

import de.cetruo.desktop.*
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.image.Image

class ArtworkEditorController(
    private val imageMode: ComboBox<ImageMode>,
    private val zoom: Slider,
    private val offsetX: Slider,
    private val offsetY: Slider,
    private val xValueLabel: Label,
    private val yValueLabel: Label,
    private val zoomValueLabel: Label,
    private val currentData: () -> CardData,
    private val currentImage: () -> Image?,
    private val currentTemplate: () -> CardTemplate?,
    private val withSuppressedUpdates: (() -> Unit) -> Unit
) {
    fun setArtwork(mode: ImageMode, newZoom: Double, normalizedX: Double, normalizedY: Double) {
        withSuppressedUpdates {
            imageMode.value = mode
            zoom.value = newZoom.coerceIn(zoom.min, zoom.max)
            offsetX.value = normalizedX.coerceIn(-1.0, 1.0)
            offsetY.value = normalizedY.coerceIn(-1.0, 1.0)
        }
        recalculatePanControls(resetPan = false)
    }

    fun resetPositionAndZoom() {
        withSuppressedUpdates {
            zoom.value = 1.0
            offsetX.value = 0.0
            offsetY.value = 0.0
        }
        recalculatePanControls(resetPan = false)
        updateZoomLabel()
    }

    fun center() {
        withSuppressedUpdates {
            offsetX.value = 0.0
            offsetY.value = 0.0
        }
        updatePanLabelsFromControls()
    }

    fun recalculatePanControls(resetPan: Boolean, syncFromData: Boolean = false) {
        val data = currentData()
        data.imageZoom = zoom.value
        data.imageMode = imageMode.value ?: data.imageMode
        val layout = layout() ?: return

        withSuppressedUpdates {
            if (resetPan) {
                offsetX.value = 0.0
                offsetY.value = 0.0
            } else if (syncFromData) {
                offsetX.value = ArtworkPanMath.actualToSlider(data.imageOffsetX, layout.minOffsetX, layout.maxOffsetX)
                offsetY.value = ArtworkPanMath.actualToSlider(data.imageOffsetY, layout.minOffsetY, layout.maxOffsetY)
            } else {
                offsetX.value = offsetX.value.coerceIn(-1.0, 1.0)
                offsetY.value = offsetY.value.coerceIn(-1.0, 1.0)
            }
        }

        val actualX = ArtworkPanMath.sliderToActual(offsetX.value, layout.minOffsetX, layout.maxOffsetX)
        val actualY = ArtworkPanMath.sliderToActual(offsetY.value, layout.minOffsetY, layout.maxOffsetY)
        data.imageOffsetX = actualX
        data.imageOffsetY = actualY
        updatePanLabels(actualX, actualY)
    }

    fun currentActualPanX(): Double {
        val layout = layout() ?: return 0.0
        return ArtworkPanMath.sliderToActual(offsetX.value, layout.minOffsetX, layout.maxOffsetX)
    }

    fun currentActualPanY(): Double {
        val layout = layout() ?: return 0.0
        return ArtworkPanMath.sliderToActual(offsetY.value, layout.minOffsetY, layout.maxOffsetY)
    }

    fun dragBy(dx: Double, dy: Double) {
        val data = currentData()
        val layout = layout() ?: return
        val actualX = (data.imageOffsetX + dx).coerceIn(layout.minOffsetX, layout.maxOffsetX)
        val actualY = (data.imageOffsetY + dy).coerceIn(layout.minOffsetY, layout.maxOffsetY)

        withSuppressedUpdates {
            offsetX.value = ArtworkPanMath.actualToSlider(actualX, layout.minOffsetX, layout.maxOffsetX)
            offsetY.value = ArtworkPanMath.actualToSlider(actualY, layout.minOffsetY, layout.maxOffsetY)
        }

        data.imageOffsetX = actualX
        data.imageOffsetY = actualY
        updatePanLabels(actualX, actualY)
    }

    fun zoomBy(delta: Double) {
        withSuppressedUpdates {
            zoom.value = (zoom.value + delta).coerceIn(zoom.min, zoom.max)
        }
        recalculatePanControls(resetPan = false)
    }

    fun updatePanLabelsFromControls() {
        val layout = layout() ?: return
        updatePanLabels(
            ArtworkPanMath.sliderToActual(offsetX.value, layout.minOffsetX, layout.maxOffsetX),
            ArtworkPanMath.sliderToActual(offsetY.value, layout.minOffsetY, layout.maxOffsetY)
        )
    }

    private fun updatePanLabels(actualX: Double, actualY: Double) {
        xValueLabel.text = "%+.0f px".format(actualX)
        yValueLabel.text = "%+.0f px".format(actualY)
    }

    private fun updateZoomLabel() {
        zoomValueLabel.text = "%.2f×".format(zoom.value)
    }

    private fun layout(): CardRenderer.ImageLayout? {
        val template = currentTemplate() ?: return null
        val data = currentData()
        val dataForLayout = data.copy(
            imageZoom = zoom.value,
            imageMode = imageMode.value ?: data.imageMode
        )
        return CardRenderer.imageLayout(currentImage(), dataForLayout, template)
    }
}

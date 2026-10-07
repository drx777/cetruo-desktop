package de.cetruo.desktop

import javafx.scene.control.CheckBox
import javafx.scene.control.ColorPicker
import javafx.scene.control.ComboBox
import javafx.scene.control.Slider
import javafx.scene.control.Spinner
import javafx.scene.control.TextArea
import javafx.scene.control.TextField

data class CardEditorValues(
    val title: String,
    val cost: String,
    val typeLine: String,
    val rarity: String,
    val stats: String,
    val artist: String,
    val setName: String,
    val collectorNumber: String,
    val description: String,
    val flavorText: String,
    val status: CardStatus,
    val templateName: String,
    val imageMode: ImageMode,
    val imageBleedOverFrame: Boolean,
    val imageBleedOpacity: Double,
    val imageZoom: Double,
    val imageOffsetX: Double,
    val imageOffsetY: Double,
    val imagePadColor: String,
    val backgroundColor: String,
    val panelColor: String,
    val frameColor: String,
    val accentColor: String,
    val overlayColor: String,
    val backgroundOverlay: String,
    val backgroundOverlayPlacement: OverlayPlacement,
    val backgroundOverlayOpacity: Double,
    val borderWidth: Double,
    val cornerRadius: Double,
    val panelOpacity: Double,
    val titleFontSize: Double,
    val bodyFontSize: Double
) {
    fun applyTo(data: CardData) {
        data.title = title
        data.cost = cost
        data.typeLine = typeLine
        data.rarity = rarity
        data.stats = stats
        data.artist = artist
        data.setName = setName
        data.collectorNumber = collectorNumber
        data.description = description
        data.flavorText = flavorText
        data.status = status
        data.templateName = templateName
        data.imageMode = imageMode
        data.imageBleedOverFrame = imageBleedOverFrame
        data.imageBleedOpacity = imageBleedOpacity
        data.imageZoom = imageZoom
        data.imageOffsetX = imageOffsetX
        data.imageOffsetY = imageOffsetY
        data.imagePadColor = imagePadColor
        data.backgroundColor = backgroundColor
        data.panelColor = panelColor
        data.frameColor = frameColor
        data.accentColor = accentColor
        data.overlayColor = overlayColor
        data.backgroundOverlay = backgroundOverlay
        data.backgroundOverlayPlacement = backgroundOverlayPlacement
        data.backgroundOverlayOpacity = backgroundOverlayOpacity
        data.borderWidth = borderWidth
        data.cornerRadius = cornerRadius
        data.panelOpacity = panelOpacity
        data.titleFontSize = titleFontSize
        data.bodyFontSize = bodyFontSize
    }
}

class CardEditorBinding(
    private val fields: Map<String, TextField>,
    private val description: TextArea,
    private val flavor: TextArea,
    private val statusChoice: ComboBox<CardStatus>,
    private val templateChoice: ComboBox<CardTemplate>,
    private val templateOverride: CheckBox,
    private val imageMode: ComboBox<ImageMode>,
    private val imageBleedOverFrame: CheckBox,
    private val imageBleedOpacity: Slider,
    private val zoom: Slider,
    private val imagePadColor: ColorPicker,
    private val backgroundColor: ColorPicker,
    private val panelColor: ColorPicker,
    private val frameColor: ColorPicker,
    private val accentColor: ColorPicker,
    private val overlayColor: ColorPicker,
    private val backgroundOverlayChoice: ComboBox<BackgroundOverlay>,
    private val overlayPlacementChoice: ComboBox<OverlayPlacement>,
    private val overlayOpacity: Slider,
    private val border: Spinner<Double>,
    private val radius: Spinner<Double>,
    private val panelOpacity: Slider,
    private val titleSize: Spinner<Double>,
    private val bodySize: Spinner<Double>
) {
    fun read(current: CardData, imageOffsetX: Double, imageOffsetY: Double): CardEditorValues =
        CardEditorValues(
            title = fields["title"]?.text ?: current.title,
            cost = fields["cost"]?.text ?: current.cost,
            typeLine = fields["typeLine"]?.text ?: current.typeLine,
            rarity = fields["rarity"]?.text ?: current.rarity,
            stats = fields["stats"]?.text ?: current.stats,
            artist = fields["artist"]?.text ?: current.artist,
            setName = fields["setName"]?.text ?: current.setName,
            collectorNumber = fields["collectorNumber"]?.text ?: current.collectorNumber,
            description = description.text,
            flavorText = flavor.text,
            status = statusChoice.value ?: current.status,
            templateName = if (templateOverride.isSelected) {
                templateChoice.value?.name ?: current.templateName
            } else {
                ""
            },
            imageMode = imageMode.value ?: current.imageMode,
            imageBleedOverFrame = imageBleedOverFrame.isSelected,
            imageBleedOpacity = imageBleedOpacity.value.coerceIn(0.0, 1.0),
            imageZoom = zoom.value,
            imageOffsetX = imageOffsetX,
            imageOffsetY = imageOffsetY,
            imagePadColor = imagePadColor.value.toHex(),
            backgroundColor = backgroundColor.value.toHex(),
            panelColor = panelColor.value.toHex(),
            frameColor = frameColor.value.toHex(),
            accentColor = accentColor.value.toHex(),
            overlayColor = overlayColor.value.toHex(),
            backgroundOverlay = backgroundOverlayChoice.value?.path?.fileName?.toString().orEmpty(),
            backgroundOverlayPlacement = overlayPlacementChoice.value ?: current.backgroundOverlayPlacement,
            backgroundOverlayOpacity = overlayOpacity.value,
            borderWidth = border.value,
            cornerRadius = radius.value,
            panelOpacity = panelOpacity.value,
            titleFontSize = titleSize.value,
            bodyFontSize = bodySize.value
        )

    fun applyTo(current: CardData, imageOffsetX: Double, imageOffsetY: Double) {
        read(current, imageOffsetX, imageOffsetY).applyTo(current)
    }
}

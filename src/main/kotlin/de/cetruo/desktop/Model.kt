package de.cetruo.desktop

import de.cetruo.desktop.visual.CardVisualDefaults
data class CardData(
    var assetId: String = "",
    var status: CardStatus = CardStatus.NEW,
    var schemeName: String = "Classic",
    var templateName: String = "",
    var title: String = "CARD NAME",
    var cost: String = "4",
    var typeLine: String = "LEGENDARY CHARACTER",
    var rarity: String = "RARE",
    var description: String = "Write your card's rules, biography, abilities, or description here.",
    var flavorText: String = "“Add a short flavor quote or memorable detail here.”",
    var artist: String = "YOUR NAME",
    var setName: String = "CARD SET",
    var collectorNumber: String = "001/100",
    var stats: String = "4 / 5",
    var imageZoom: Double = 1.0,
    var imageOffsetX: Double = 0.0,
    var imageOffsetY: Double = 0.0,
    var imageMode: ImageMode = ImageMode.COVER,
    var imagePadColor: String = CardVisualDefaults.IMAGE_PAD_COLOR,
    var imageBleedOverFrame: Boolean = false,
    var imageBleedOpacity: Double = 1.0,
    var backgroundOverlay: String = "",
    var backgroundOverlayPlacement: OverlayPlacement = OverlayPlacement.FRAMES_ONLY,
    var backgroundOverlayOpacity: Double = 1.0,
    var backgroundColor: String = CardVisualDefaults.BACKGROUND_COLOR,
    var panelColor: String = CardVisualDefaults.PANEL_COLOR,
    var frameColor: String = CardVisualDefaults.FRAME_COLOR,
    var accentColor: String = CardVisualDefaults.ACCENT_COLOR,
    var overlayColor: String = CardVisualDefaults.OVERLAY_COLOR,
    var textColor: String = CardVisualDefaults.TEXT_COLOR,
    var darkTextColor: String = CardVisualDefaults.DARK_TEXT_COLOR,
    var borderWidth: Double = CardVisualDefaults.BORDER_WIDTH,
    var cornerRadius: Double = CardVisualDefaults.CORNER_RADIUS,
    var panelOpacity: Double = CardVisualDefaults.PANEL_OPACITY,
    var titleFontSize: Double = CardVisualDefaults.TITLE_FONT_SIZE,
    var bodyFontSize: Double = CardVisualDefaults.BODY_FONT_SIZE
)

enum class ImageMode { COVER, CONTAIN, STRETCH }
enum class OverlayPlacement { FRAMES_ONLY, OVER_CONTENT }
enum class CardStatus { NEW, IN_PROGRESS, READY, EXPORTED, ARCHIVED }

data class CollectionPresentation(
    var descriptionHeading: String = "ABILITY / DESCRIPTION",
    var showArtistCopyright: Boolean = true,
    var bleedOpacity: Double = 1.0,
    var foregroundOpacity: Double = 1.0
)

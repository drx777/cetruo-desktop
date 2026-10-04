package com.example.cardforge

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
    var imagePadColor: String = "#0A0D10",
    var imageBleedOverFrame: Boolean = false,
    var imageBleedOpacity: Double = 1.0,
    var backgroundOverlay: String = "",
    var backgroundOverlayPlacement: OverlayPlacement = OverlayPlacement.FRAMES_ONLY,
    var backgroundOverlayOpacity: Double = 1.0,
    var backgroundColor: String = "#161B22",
    var panelColor: String = "#EFE8D7",
    var frameColor: String = "#D9C28E",
    var accentColor: String = "#8C8068",
    var overlayColor: String = "#C9B37A",
    var textColor: String = "#29251F",
    var darkTextColor: String = "#F3EAD6",
    var borderWidth: Double = 8.0,
    var cornerRadius: Double = 24.0,
    var panelOpacity: Double = 0.96,
    var titleFontSize: Double = 27.0,
    var bodyFontSize: Double = 16.0
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

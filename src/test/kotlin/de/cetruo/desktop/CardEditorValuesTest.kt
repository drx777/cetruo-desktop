package de.cetruo.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CardEditorValuesTest {
    @Test
    fun applyToCopiesAllEditorValuesIntoCardData() {
        val data = CardData()
        val values = CardEditorValues(
            title = "Updated",
            cost = "7",
            typeLine = "ALLY — KNIGHT",
            rarity = "MYTHIC",
            stats = "8 / 9",
            artist = "Artist",
            setName = "Set",
            collectorNumber = "007/100",
            description = "Description",
            flavorText = "Flavor",
            status = CardStatus.READY,
            templateName = "Template",
            imageMode = ImageMode.CONTAIN,
            imageBleedOverFrame = true,
            imageBleedOpacity = 0.4,
            imageZoom = 1.8,
            imageOffsetX = 12.0,
            imageOffsetY = -8.0,
            imagePadColor = "#010203",
            backgroundColor = "#111213",
            panelColor = "#212223",
            frameColor = "#313233",
            accentColor = "#414243",
            overlayColor = "#515253",
            backgroundOverlay = "overlay.svg",
            backgroundOverlayPlacement = OverlayPlacement.OVER_CONTENT,
            backgroundOverlayOpacity = 0.6,
            borderWidth = 5.0,
            cornerRadius = 18.0,
            panelOpacity = 0.8,
            titleFontSize = 30.0,
            bodyFontSize = 17.0
        )

        values.applyTo(data)

        assertEquals("Updated", data.title)
        assertEquals("7", data.cost)
        assertEquals(CardStatus.READY, data.status)
        assertEquals("Template", data.templateName)
        assertEquals(ImageMode.CONTAIN, data.imageMode)
        assertEquals(12.0, data.imageOffsetX)
        assertEquals(-8.0, data.imageOffsetY)
        assertEquals("#515253", data.overlayColor)
        assertEquals(OverlayPlacement.OVER_CONTENT, data.backgroundOverlayPlacement)
        assertEquals(30.0, data.titleFontSize)
        assertEquals(17.0, data.bodyFontSize)
        assertFalse(data.assetId.isNotBlank())
    }
}

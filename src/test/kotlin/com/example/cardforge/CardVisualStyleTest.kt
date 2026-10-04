package com.example.cardforge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CardVisualStyleTest {
    @Test
    fun rarityClassificationKeepsUncommonOutOfRareBucket() {
        assertEquals(RarityTier.COMMON, CardVisualSystem.rarityTier("Common"))
        assertEquals(RarityTier.UNCOMMON, CardVisualSystem.rarityTier("Uncommon"))
        assertEquals(RarityTier.RARE, CardVisualSystem.rarityTier("Rare"))
        assertEquals(RarityTier.MYTHIC, CardVisualSystem.rarityTier("Mythic Rare"))
    }

    @Test
    fun rarityFramesUseOnlySchemeColorsPlusGoldAndGrayUtilities() {
        val data = CardData(
            rarity = "Rare",
            backgroundColor = "#101820",
            panelColor = "#E8E0D0",
            frameColor = "#7289A8",
            accentColor = "#A24B3D",
            overlayColor = "#4A7562"
        )
        val palette = CardVisualSystem.palette(data)

        assertEquals(CardVisualSystem.GOLD, palette.outerFrame)
        assertEquals(data.backgroundColor, palette.background)
        assertEquals(data.panelColor, palette.panel)
        assertEquals(data.frameColor, palette.frame)
        assertEquals(data.accentColor, palette.accent)
        assertEquals(data.overlayColor, palette.overlay)
    }

    @Test
    fun textFitShrinksLongCopyButRespectsMinimum() {
        val short = CardVisualSystem.fitFontSize("Short title", 27.0, 18.0, 420.0, 38.0, bold = true)
        val long = CardVisualSystem.fitFontSize(
            "A deliberately very long title that has to fit inside a compact title rail",
            27.0,
            18.0,
            420.0,
            38.0,
            bold = true
        )
        assertEquals(27.0, short)
        assertTrue(long < short)
        assertTrue(long >= 18.0)
    }
}

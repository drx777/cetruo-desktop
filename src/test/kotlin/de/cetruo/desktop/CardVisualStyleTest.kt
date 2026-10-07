package de.cetruo.desktop

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
    @Test
    fun templateCanSwapSvgAndVisualStyleByRarityTier() {
        val baseVisual = TemplateVisualStyle(rarityFrames = false)
        val mythicVisual = TemplateVisualStyle(materialDepth = 4.0, rarityFrames = true)
        val template = CardTemplate(
            name = "Variant test",
            svgFile = "base.svg",
            width = 630.0,
            height = 880.0,
            art = TemplateRect(0.0, 0.0, 1.0, 1.0),
            titleBox = TemplateRect(0.0, 0.0, 1.0, 1.0),
            titleText = TemplateText(0.0, 0.0, 1.0, 1.0),
            costText = TemplateText(0.0, 0.0, 1.0, 1.0),
            typeBox = TemplateRect(0.0, 0.0, 1.0, 1.0),
            typeText = TemplateText(0.0, 0.0, 1.0, 1.0),
            rarityText = TemplateText(0.0, 0.0, 1.0, 1.0),
            descriptionBox = TemplateRect(0.0, 0.0, 1.0, 1.0),
            descriptionHeading = TemplateText(0.0, 0.0, 1.0, 1.0),
            descriptionText = TemplateText(0.0, 0.0, 1.0, 1.0),
            flavorText = TemplateText(0.0, 0.0, 1.0, 1.0),
            footerText = TemplateText(0.0, 0.0, 1.0, 1.0),
            statsBox = TemplateRect(0.0, 0.0, 1.0, 1.0),
            statsText = TemplateText(0.0, 0.0, 1.0, 1.0),
            visualStyle = baseVisual,
            rarityVariants = mapOf(
                "MYTHIC" to TemplateRarityVariant(
                    svgFile = "mythic.svg",
                    visualStyle = mythicVisual
                )
            )
        )

        assertEquals("base.svg", template.svgFileFor("Rare"))
        assertEquals("mythic.svg", template.svgFileFor("Mythic Rare"))
        assertEquals(baseVisual, template.visualStyleFor("Rare"))
        assertEquals(mythicVisual, template.visualStyleFor("Legendary"))
    }
}

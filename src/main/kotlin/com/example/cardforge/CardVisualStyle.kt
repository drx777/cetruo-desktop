package com.example.cardforge

import kotlin.math.ceil
import kotlin.math.max

enum class RarityTier { COMMON, UNCOMMON, RARE, MYTHIC }

data class CardVisualPalette(
    val background: String,
    val panel: String,
    val frame: String,
    val accent: String,
    val overlay: String,
    val text: String,
    val darkText: String,
    val imagePad: String,
    val gold: String,
    val gray: String,
    val outerFrame: String,
    val outerFrameSecondary: String
)

object CardVisualSystem {
    const val GOLD = "#D6B45A"
    const val GRAY = "#7B8491"

    fun rarityTier(value: String): RarityTier {
        val rarity = value.trim().uppercase()
        return when {
            rarity.contains("MYTH") || rarity.contains("LEGEND") || rarity.contains("EPIC") -> RarityTier.MYTHIC
            rarity.contains("RARE") && !rarity.contains("UNCOMMON") -> RarityTier.RARE
            rarity.contains("UNCOMMON") -> RarityTier.UNCOMMON
            else -> RarityTier.COMMON
        }
    }

    fun palette(data: CardData): CardVisualPalette {
        val tier = rarityTier(data.rarity)
        val outer = when (tier) {
            RarityTier.COMMON -> GRAY
            RarityTier.UNCOMMON -> data.frameColor
            RarityTier.RARE -> GOLD
            RarityTier.MYTHIC -> mix(GOLD, data.accentColor, 0.38)
        }
        val secondary = when (tier) {
            RarityTier.COMMON -> mix(GRAY, data.backgroundColor, 0.48)
            RarityTier.UNCOMMON -> mix(data.frameColor, data.backgroundColor, 0.42)
            RarityTier.RARE -> mix(GOLD, data.backgroundColor, 0.34)
            RarityTier.MYTHIC -> mix(GOLD, data.accentColor, 0.58)
        }
        return CardVisualPalette(
            background = data.backgroundColor,
            panel = data.panelColor,
            frame = data.frameColor,
            accent = data.accentColor,
            overlay = data.overlayColor,
            text = data.textColor,
            darkText = data.darkTextColor,
            imagePad = data.imagePadColor,
            gold = GOLD,
            gray = GRAY,
            outerFrame = outer,
            outerFrameSecondary = secondary
        )
    }

    fun fitFontSize(
        text: String,
        preferred: Double,
        min: Double,
        width: Double,
        height: Double,
        bold: Boolean = false,
        lineHeight: Double = 1.16
    ): Double {
        if (text.isBlank() || width <= 0.0 || height <= 0.0) return preferred.coerceAtLeast(min)
        var size = preferred.coerceAtLeast(min)
        while (size > min) {
            val widthFactor = if (bold) 0.59 else 0.54
            val charsPerLine = max(1.0, width / (size * widthFactor))
            val paragraphs = text.lines()
            val estimatedLines = paragraphs.sumOf { paragraph ->
                if (paragraph.isBlank()) 1 else ceil(paragraph.length / charsPerLine).toInt().coerceAtLeast(1)
            }
            if (estimatedLines * size * lineHeight <= height * 0.94) break
            size -= 0.5
        }
        return size.coerceAtLeast(min)
    }

    fun mix(a: String, b: String, weightB: Double): String {
        val wa = 1.0 - weightB.coerceIn(0.0, 1.0)
        val wb = weightB.coerceIn(0.0, 1.0)
        val ca = rgb(a)
        val cb = rgb(b)
        val r = (ca[0] * wa + cb[0] * wb).toInt().coerceIn(0, 255)
        val g = (ca[1] * wa + cb[1] * wb).toInt().coerceIn(0, 255)
        val bl = (ca[2] * wa + cb[2] * wb).toInt().coerceIn(0, 255)
        return "#%02X%02X%02X".format(r, g, bl)
    }

    fun lighten(color: String, amount: Double): String = mix(color, "#FFFFFF", amount)
    fun darken(color: String, amount: Double): String = mix(color, "#000000", amount)

    private fun rgb(value: String): IntArray {
        val raw = value.trim().removePrefix("#")
        val normalized = when (raw.length) {
            3 -> raw.flatMap { listOf(it, it) }.joinToString("")
            6 -> raw
            8 -> raw.substring(0, 6)
            else -> "808080"
        }
        return runCatching {
            intArrayOf(
                normalized.substring(0, 2).toInt(16),
                normalized.substring(2, 4).toInt(16),
                normalized.substring(4, 6).toInt(16)
            )
        }.getOrElse { intArrayOf(128, 128, 128) }
    }
}

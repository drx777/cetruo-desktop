package de.cetruo.desktop

import javafx.scene.paint.Color
import java.nio.file.Path
import java.util.UUID
import kotlin.random.Random

class CardDefaultsGenerator(
    private val randomProvider: () -> Random,
    private val templatesProvider: () -> List<CardTemplate>,
    private val schemesProvider: () -> List<ColorScheme>,
    private val usedCollectorNumbers: () -> Set<String>
) {
    fun create(
        path: Path? = null,
        derivedColorOverride: Color? = null,
        analyzeImageIfNeeded: Boolean = true
    ): CardData {
        val random = randomProvider()
        val data = CardData(
            assetId = UUID.randomUUID().toString(),
            status = CardStatus.NEW,
            title = path?.fileName?.toString()?.substringBeforeLast('.', path.fileName.toString()) ?: "CARD NAME",
            cost = random.nextInt(0, 10).toString(),
            typeLine = TYPE_LINES.random(random),
            rarity = weightedRarity(random),
            artist = ARTIST_PATTERNS.random(random),
            collectorNumber = nextUnusedCollectorNumber(random),
            stats = "${random.nextInt(0, 13)} / ${random.nextInt(0, 13)}",
            templateName = templatesProvider().randomOrNull(random)?.name.orEmpty(),
            backgroundOverlay = "",
            imageMode = ImageMode.COVER,
            imageBleedOverFrame = false
        )

        val derived = derivedColorOverride
            ?: if (analyzeImageIfNeeded) path?.let(ImageColorAnalyzer::dominantColor) else null
        val schemes = schemesProvider()
        val scheme = derived?.let { ImageColorAnalyzer.bestMatchingScheme(it, schemes) }
            ?: schemes.randomOrNull(random)
        scheme?.applyTo(data)
        return data
    }

    fun randomArtistPattern(random: Random = randomProvider()): String =
        ARTIST_PATTERNS.random(random)

    internal fun weightedRarity(random: Random): String =
        rarityForRoll(random.nextInt(100))

    internal fun nextUnusedCollectorNumber(random: Random, total: Int = 100): String {
        val used = usedCollectorNumbers()
        val available = (1..total)
            .map { "%03d/%d".format(it, total) }
            .filterNot(used::contains)
        return available.randomOrNull(random)
            ?: "%03d/%d".format(random.nextInt(1, total + 1), total)
    }

    companion object {
        internal fun rarityForRoll(roll: Int): String = when (roll) {
            in 0..49 -> "COMMON"
            in 50..74 -> "UNCOMMON"
            in 75..91 -> "RARE"
            in 92..97 -> "MYTHIC"
            else -> "LEGENDARY"
        }

        private val TYPE_LINES = listOf(
            "CREATURE — MYSTIC",
            "LEGENDARY CHARACTER",
            "ARTIFACT — RELIC",
            "SORCERY — RITUAL",
            "SPELL — ARCANE",
            "ALLY — KNIGHT"
        )

        private val ARTIST_PATTERNS = listOf(
            "˚₊‧꒰ა ☆ ໒꒱ ‧₊˚",
            "⋆｡°✩༺☆༻✩°｡⋆",
            "｡₊˚༺❦༻˚₊｡",
            "༄︵‿︵༄",
            "✧･ﾟ: *✧･ﾟ:*",
            "⋆｡ﾟ✶°✧⋆",
            "༶•┈┈୨♡୧┈┈•༶",
            "╰┈➤ ✦ ╰┈➤",
            "˚ ༘♡ ⋆｡˚",
            "⟡ ─── ✦ ─── ⟡",
            "୨୧ ‧₊˚ ⋅",
            "☾⋆⁺₊✧",
            "༺═────────═༻",
            "✦₊˚.⋆ ☽ ⋆⁺₊✧",
            "꧁༺ ✧ ༻꧂"
        )
    }
}

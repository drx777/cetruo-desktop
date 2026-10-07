package de.cetruo.desktop.editor

import de.cetruo.desktop.*
import kotlin.random.Random

object CardRandomizer {
    private val titleFirst = listOf(
        "Aether", "Silent", "Astral", "Gilded", "Crimson",
        "Verdant", "Moonlit", "Arcane", "Runed", "Fallen"
    )
    private val titleSecond = listOf(
        "Warden", "Oracle", "Pilgrim", "Herald", "Guardian",
        "Voyager", "Seer", "Sovereign", "Relic", "Champion"
    )
    private val typeLines = listOf(
        "CREATURE — MYSTIC",
        "LEGENDARY CHARACTER",
        "ARTIFACT — RELIC",
        "SORCERY — RITUAL",
        "SPELL — ARCANE",
        "ALLY — KNIGHT"
    )
    private val rarities = listOf("COMMON", "UNCOMMON", "RARE", "MYTHIC", "LEGENDARY")
    private val setNames = listOf(
        "ECLIPSE",
        "VERDANT ARCHIVES",
        "CROWN OF STARS",
        "FORGOTTEN REALMS",
        "IRON HORIZON",
        "MOONFALL",
        "ASHEN OATH"
    )

    data class StyleAndNumbers(
        val scheme: ColorScheme?,
        val cost: String,
        val stats: String
    )

    fun styleAndNumbers(schemes: List<ColorScheme>, random: Random): StyleAndNumbers =
        StyleAndNumbers(
            scheme = scheme(schemes, random),
            cost = cost(random),
            stats = stats(random)
        )

    fun scheme(schemes: List<ColorScheme>, random: Random): ColorScheme? =
        schemes.randomOrNull(random)

    fun cost(random: Random): String =
        random.nextInt(0, 10).toString()

    fun stats(random: Random): String =
        "${random.nextInt(0, 13)} / ${random.nextInt(0, 13)}"

    fun collectorNumber(current: String, used: Set<String>, random: Random): String? {
        val total = current.substringAfter('/', "100").toIntOrNull()?.coerceAtLeast(1) ?: 100
        val available = (1..total).filter { "%03d/%d".format(it, total) !in used }
        if (available.isEmpty()) return null
        return "%03d/%d".format(available[random.nextInt(available.size)], total)
    }

    fun title(random: Random): String =
        "${titleFirst.random(random)} ${titleSecond.random(random)}"

    fun typeLine(random: Random): String =
        typeLines.random(random)

    fun rarity(random: Random): String =
        rarities.random(random)

    fun setName(random: Random): String =
        setNames.random(random)

    fun overlay(overlays: List<BackgroundOverlay>, random: Random): BackgroundOverlay? =
        overlays.randomOrNull(random)
}

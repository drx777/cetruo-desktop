package de.cetruo.desktop

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardDefaultsGeneratorTest {
    @Test
    fun rarityBoundariesMatchConfiguredWeights() {
        assertEquals("COMMON", CardDefaultsGenerator.rarityForRoll(0))
        assertEquals("COMMON", CardDefaultsGenerator.rarityForRoll(49))
        assertEquals("UNCOMMON", CardDefaultsGenerator.rarityForRoll(50))
        assertEquals("UNCOMMON", CardDefaultsGenerator.rarityForRoll(74))
        assertEquals("RARE", CardDefaultsGenerator.rarityForRoll(75))
        assertEquals("RARE", CardDefaultsGenerator.rarityForRoll(91))
        assertEquals("MYTHIC", CardDefaultsGenerator.rarityForRoll(92))
        assertEquals("MYTHIC", CardDefaultsGenerator.rarityForRoll(97))
        assertEquals("LEGENDARY", CardDefaultsGenerator.rarityForRoll(98))
        assertEquals("LEGENDARY", CardDefaultsGenerator.rarityForRoll(99))
    }

    @Test
    fun collectorNumberAvoidsAlreadyUsedValuesWhenCapacityRemains() {
        val generator = CardDefaultsGenerator(
            randomProvider = { Random(1) },
            templatesProvider = { emptyList() },
            schemesProvider = { emptyList() },
            usedCollectorNumbers = { setOf("001/3", "003/3") }
        )

        assertEquals("002/3", generator.nextUnusedCollectorNumber(Random(2), total = 3))
    }

    @Test
    fun createUsesFileNameAsDefaultTitle() {
        val generator = CardDefaultsGenerator(
            randomProvider = { Random(3) },
            templatesProvider = { emptyList() },
            schemesProvider = { emptyList() },
            usedCollectorNumbers = { emptySet() }
        )

        val data = generator.create(
            path = java.nio.file.Path.of("/collection/Forest Guardian.png"),
            analyzeImageIfNeeded = false
        )

        assertEquals("Forest Guardian", data.title)
        assertEquals(CardStatus.NEW, data.status)
        assertFalse(data.assetId.isBlank())
        assertTrue(data.collectorNumber.endsWith("/100"))
    }
}

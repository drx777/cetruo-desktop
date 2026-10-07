package de.cetruo.desktop

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardRandomizerTest {
    @Test
    fun numericValuesStayWithinEditorRanges() {
        val random = Random(1234)

        repeat(100) {
            val cost = CardRandomizer.cost(random).toInt()
            assertTrue(cost in 0..9)

            val parts = CardRandomizer.stats(random).split(" / ").map(String::toInt)
            assertEquals(2, parts.size)
            assertTrue(parts.all { it in 0..12 })
        }
    }

    @Test
    fun collectorNumberAvoidsUsedValues() {
        val value = CardRandomizer.collectorNumber(
            current = "001/3",
            used = setOf("001/3", "002/3"),
            random = Random(1)
        )

        assertEquals("003/3", value)
    }

    @Test
    fun collectorNumberReturnsNullWhenRangeIsExhausted() {
        assertNull(
            CardRandomizer.collectorNumber(
                current = "001/2",
                used = setOf("001/2", "002/2"),
                random = Random(1)
            )
        )
    }

    @Test
    fun textRandomizersReturnConfiguredValues() {
        val random = Random(42)

        assertNotNull(CardRandomizer.title(random).takeIf { it.contains(' ') })
        assertTrue(CardRandomizer.typeLine(random).isNotBlank())
        assertTrue(CardRandomizer.rarity(random) in setOf("COMMON", "UNCOMMON", "RARE", "MYTHIC", "LEGENDARY"))
        assertTrue(CardRandomizer.setName(random).isNotBlank())
    }

    @Test
    fun emptyCollectionsProduceNoChoice() {
        val random = Random(0)
        assertNull(CardRandomizer.scheme(emptyList(), random))
        assertNull(CardRandomizer.overlay(emptyList(), random))
    }
}

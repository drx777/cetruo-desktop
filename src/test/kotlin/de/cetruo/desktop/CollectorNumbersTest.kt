package de.cetruo.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class CollectorNumbersTest {
    @Test
    fun preservesUniqueExistingNumberAndNormalizesWidth() {
        val used = linkedSetOf<Int>()
        assertEquals("007/012", CollectorNumbers.withUniqueTotal("7/99", 12, used, 1))
        assertEquals(setOf(7), used)
    }

    @Test
    fun duplicateOrInvalidNumbersUseStableFallbacks() {
        val used = linkedSetOf<Int>()
        assertEquals("002/003", CollectorNumbers.withUniqueTotal("2/3", 3, used, 1))
        assertEquals("001/003", CollectorNumbers.withUniqueTotal("2/3", 3, used, 1))
        assertEquals("003/003", CollectorNumbers.withUniqueTotal("abc", 3, used, 3))
    }

    @Test
    fun widthExpandsForLargeNumbers() {
        val used = linkedSetOf<Int>()
        assertEquals("1234/1234", CollectorNumbers.withUniqueTotal("1234", 1234, used, 1))
    }
}

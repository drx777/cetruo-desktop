package de.cetruo.desktop

import kotlin.test.Test
import kotlin.test.assertTrue

class BrowserImageSorterTest {
    @Test
    fun naturalSortComparesNumericRunsNumerically() {
        assertTrue(BrowserImageSorter.compareNaturally("Card 2", "Card 10") < 0)
        assertTrue(BrowserImageSorter.compareNaturally("Card 10", "Card 2") > 0)
    }

    @Test
    fun naturalSortIsCaseInsensitiveForText() {
        assertTrue(BrowserImageSorter.compareNaturally("alpha 2", "Beta 1") < 0)
    }

    @Test
    fun collectorNumbersSortByNumberThenTotal() {
        assertTrue(BrowserImageSorter.compareCollectorNumbers("002/100", "010/100") < 0)
        assertTrue(BrowserImageSorter.compareCollectorNumbers("002/100", "002/200") < 0)
    }

    @Test
    fun malformedCollectorNumbersSortAfterValidNumbers() {
        assertTrue(BrowserImageSorter.compareCollectorNumbers("not set", "010/100") > 0)
    }
}

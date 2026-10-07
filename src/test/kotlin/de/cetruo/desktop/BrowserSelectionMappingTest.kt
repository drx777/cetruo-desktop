package de.cetruo.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class BrowserSelectionMappingTest {
    @Test
    fun listModeUsesIdentityMapping() {
        assertEquals(0, BrowserSelectionMapping.rowForIndex(0, grid = false, columns = 4))
        assertEquals(7, BrowserSelectionMapping.rowForIndex(7, grid = false, columns = 4))
        assertEquals(7, BrowserSelectionMapping.firstIndexForRow(7, grid = false, columns = 4))
    }

    @Test
    fun gridModeMapsIndexesIntoRows() {
        assertEquals(0, BrowserSelectionMapping.rowForIndex(0, grid = true, columns = 3))
        assertEquals(0, BrowserSelectionMapping.rowForIndex(2, grid = true, columns = 3))
        assertEquals(1, BrowserSelectionMapping.rowForIndex(3, grid = true, columns = 3))
        assertEquals(2, BrowserSelectionMapping.rowForIndex(8, grid = true, columns = 3))
        assertEquals(6, BrowserSelectionMapping.firstIndexForRow(2, grid = true, columns = 3))
    }

    @Test
    fun invalidOrZeroColumnInputsAreSafe() {
        assertEquals(-1, BrowserSelectionMapping.rowForIndex(-1, grid = true, columns = 0))
        assertEquals(-1, BrowserSelectionMapping.firstIndexForRow(-1, grid = true, columns = 0))
        assertEquals(5, BrowserSelectionMapping.rowForIndex(5, grid = true, columns = 0))
        assertEquals(4, BrowserSelectionMapping.firstIndexForRow(4, grid = true, columns = 0))
    }
}

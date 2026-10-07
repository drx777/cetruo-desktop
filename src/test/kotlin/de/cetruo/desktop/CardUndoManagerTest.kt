package de.cetruo.desktop

import de.cetruo.desktop.browser.*
import de.cetruo.desktop.editor.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardUndoManagerTest {
    @Test
    fun undoAndRedoUseSnapshots() {
        val manager = CardUndoManager(limit = 3)
        val original = CardData(title = "Original")
        val edited = original.copy(title = "Edited")

        manager.capture(original, edited)
        original.title = "Mutated after capture"

        val undone = manager.undo(edited)
        assertEquals("Original", undone?.title)
        assertTrue(manager.canRedo)

        val redone = manager.redo(undone!!)
        assertEquals("Edited", redone?.title)
    }

    @Test
    fun duplicateCaptureDoesNothing() {
        val manager = CardUndoManager()
        val data = CardData(title = "Same")
        manager.capture(data, data.copy())

        assertFalse(manager.canUndo)
        assertNull(manager.undo(data))
    }

    @Test
    fun historyLimitDropsOldestEntries() {
        val manager = CardUndoManager(limit = 2)
        val one = CardData(title = "1")
        val two = CardData(title = "2")
        val three = CardData(title = "3")
        val four = CardData(title = "4")

        manager.capture(one, two)
        manager.capture(two, three)
        manager.capture(three, four)

        assertEquals("3", manager.undo(four)?.title)
        assertEquals("2", manager.undo(three)?.title)
        assertNull(manager.undo(two))
    }
}

package com.example.cardforge

import java.util.ArrayDeque

/** Per-card in-memory undo/redo history. Persistence remains the responsibility of the caller. */
class CardUndoManager(private val limit: Int = 100) {
    private val undo = ArrayDeque<CardData>()
    private val redo = ArrayDeque<CardData>()

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    fun clear() {
        undo.clear()
        redo.clear()
    }

    fun capture(previous: CardData, current: CardData) {
        if (previous == current) return
        undo.addLast(previous.copy())
        while (undo.size > limit) undo.removeFirst()
        redo.clear()
    }

    fun undo(current: CardData): CardData? {
        if (undo.isEmpty()) return null
        redo.addLast(current.copy())
        return undo.removeLast().copy()
    }

    fun redo(current: CardData): CardData? {
        if (redo.isEmpty()) return null
        undo.addLast(current.copy())
        while (undo.size > limit) undo.removeFirst()
        return redo.removeLast().copy()
    }
}

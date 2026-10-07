package de.cetruo.desktop

/**
 * Centralizes the rule that navigation/destructive UI transitions must not proceed unless
 * the current card saves successfully.
 */
class AutosaveGuard(
    private val save: (showStatus: Boolean) -> Boolean
) {
    fun ensureSaved(showStatus: Boolean = false): Boolean = save(showStatus)
}

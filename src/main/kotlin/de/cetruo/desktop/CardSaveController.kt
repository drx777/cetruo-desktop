package de.cetruo.desktop

import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
import javafx.scene.control.TextField
import java.nio.file.Files
import java.nio.file.Path

class CardSaveController(
    private val persistence: CardPersistenceService,
    private val database: () -> CollectionDatabase?,
    private val currentPath: () -> Path?,
    private val isVisiblePath: (Path) -> Boolean,
    private val currentData: () -> CardData,
    private val allImages: () -> List<Path>,
    private val updateFromEditor: () -> Unit,
    private val fields: Map<String, TextField>,
    private val withSuppressedUpdates: (() -> Unit) -> Unit,
    private val clearCardStore: () -> Unit,
    private val clearCardPreviews: () -> Unit,
    private val rebuildSearchIndex: (CollectionDatabase) -> Unit,
    private val removeCardPreview: (Path) -> Unit,
    private val updateSearchIndex: (Path, CardData) -> Unit,
    private val cacheCard: (Path, CardData) -> Unit,
    private val relativePath: (Path) -> String,
    private val statusLabel: (CardStatus) -> String,
    private val setStatus: (String) -> Unit,
    private val showError: (String, Throwable) -> Unit
) {
    fun save(showStatus: Boolean = true): Boolean {
        val path = currentPath() ?: return true
        if (!Files.isRegularFile(path) || !isVisiblePath(path)) return true
        val db = database() ?: return false

        return try {
            updateFromEditor()
            val data = currentData()
            val preparation = persistence.prepare(db, path, data)

            if (!confirmDuplicateCollectorNumber(preparation)) return false
            if (!confirmPotentialDataClear(preparation)) return false

            val outcome = persistence.save(db, path, data, allImages(), preparation)
            outcome.persistedCollectorNumber?.let { collectorNumber ->
                data.collectorNumber = collectorNumber
                withSuppressedUpdates {
                    fields["collectorNumber"]?.text = collectorNumber
                }
            }

            if (outcome.normalizedSetTotals) {
                clearCardStore()
                clearCardPreviews()
                rebuildSearchIndex(db)
            } else {
                val normalizedPath = path.toAbsolutePath().normalize()
                if (outcome.result.changed) {
                    removeCardPreview(normalizedPath)
                }
                updateSearchIndex(path, data)
                cacheCard(path, data)
            }

            if (outcome.result.changed) {
                db.recordActivity(
                    outcome.result.assetId,
                    "SAVE",
                    "revision=${outcome.result.revisionNumber}"
                )
            }
            if (showStatus) {
                setStatus(
                    if (outcome.result.changed) {
                        "Saved • ${relativePath(path)} • revision ${outcome.result.revisionNumber} • ${statusLabel(data.status)}"
                    } else {
                        "No changes to save • ${relativePath(path)}"
                    }
                )
            }
            true
        } catch (e: Exception) {
            if (showStatus) showError("Could not save card", e)
            false
        }
    }

    private fun confirmDuplicateCollectorNumber(preparation: CardSavePreparation): Boolean {
        val collectorNumber = preparation.duplicateCollectorNumber ?: return true
        val result = Alert(Alert.AlertType.WARNING).apply {
            title = "Duplicate collector number"
            headerText = "$collectorNumber is already used in this collection"
            contentText =
                "Another saved card already uses this collector number. Save this card with the duplicate number anyway?"
            buttonTypes.setAll(ButtonType("Save duplicate"), ButtonType.CANCEL)
        }.showAndWait().orElse(ButtonType.CANCEL)
        return result != ButtonType.CANCEL
    }

    private fun confirmPotentialDataClear(preparation: CardSavePreparation): Boolean {
        if (!preparation.wouldClearManyFields) return true
        val result = Alert(Alert.AlertType.CONFIRMATION).apply {
            title = "Protect card data"
            headerText = "This save would clear many card fields"
            contentText =
                "The current editor state would remove several previously populated card values. This can happen if an editor was reset or partially initialized. Save these cleared values anyway?"
            buttonTypes.setAll(ButtonType("Save anyway"), ButtonType.CANCEL)
        }.showAndWait().orElse(ButtonType.CANCEL)
        return result != ButtonType.CANCEL
    }
}

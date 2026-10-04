package com.example.cardforge

import java.nio.file.Path

/**
 * UI-facing collection operations. Keeps MainApp listeners thin and centralizes cache-neutral
 * database mutations that must affect every card.
 */
class CollectionEditorActions(
    private val database: CollectionDatabase,
    private val images: () -> Collection<Path>
) {
    fun applyDefaultTemplateToAll(): CollectionBulkActions.Result =
        CollectionBulkActions.applyCollectionDefaultTemplate(images(), database)

    fun applySetNameToAll(setName: String): CollectionBulkActions.Result =
        CollectionBulkActions.applySetName(images(), database, setName)

    fun normalizeCollectorTotals(sets: Set<String>? = null): CollectionBulkActions.Result =
        CollectionBulkActions.normalizeCollectorTotals(images(), database, sets)

    fun updatePresentation(transform: (CollectionPresentation) -> Unit): CollectionPresentation {
        val presentation = database.getCollectionPresentation()
        transform(presentation)
        presentation.bleedOpacity = presentation.bleedOpacity.coerceIn(0.0, 1.0)
        presentation.foregroundOpacity = presentation.foregroundOpacity.coerceIn(0.0, 1.0)
        database.setCollectionPresentation(presentation)
        return presentation
    }
}

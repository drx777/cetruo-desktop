package com.example.cardforge

/** Resolves collection/card opacity rules in one place for editor, preview and export renderers. */
object CollectionVisualSettings {
    fun bleedOpacity(card: CardData, collection: CollectionPresentation): Double =
        (card.imageBleedOpacity.coerceIn(0.0, 1.0) * collection.bleedOpacity.coerceIn(0.0, 1.0))
            .coerceIn(0.0, 1.0)

    fun foregroundOpacity(collection: CollectionPresentation): Double =
        collection.foregroundOpacity.coerceIn(0.0, 1.0)

    /** Artwork bleed is allowed only inside the outer frame's inner edge. */
    fun bleedInset(card: CardData): Double = card.borderWidth.coerceAtLeast(0.0)
}

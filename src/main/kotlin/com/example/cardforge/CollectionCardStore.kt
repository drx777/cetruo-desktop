package com.example.cardforge

import java.nio.file.Path

/**
 * One place for resolving card data by image path.
 * MainApp should not need to know whether a snapshot came from memory or SQLite.
 */
class CollectionCardStore(
    private val databaseProvider: () -> CollectionDatabase?,
    private val newCardFactory: (Path) -> CardData
) {
    private val cache = mutableMapOf<Path, CardData>()

    fun normalized(path: Path): Path = path.toAbsolutePath().normalize()

    fun get(path: Path, currentPath: Path? = null, currentData: CardData? = null): CardData {
        val key = normalized(path)
        if (currentPath?.toAbsolutePath()?.normalize() == key && currentData != null) return currentData.copy()
        return cache[key]?.copy()
            ?: databaseProvider()?.dataSnapshotForPath(key)?.also { cache[key] = it.copy() }?.copy()
            ?: newCardFactory(key)
    }

    fun snapshot(path: Path): CardData? {
        val key = normalized(path)
        return cache[key]?.copy()
            ?: databaseProvider()?.dataSnapshotForPath(key)?.also { cache[key] = it.copy() }?.copy()
    }

    fun put(path: Path, data: CardData) {
        cache[normalized(path)] = data.copy()
    }

    fun remove(path: Path) {
        cache.remove(normalized(path))
    }

    fun clear() = cache.clear()

    fun invalidate(paths: Iterable<Path>) {
        paths.forEach { remove(it) }
    }
}

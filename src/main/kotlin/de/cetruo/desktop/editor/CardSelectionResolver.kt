package de.cetruo.desktop.editor

import de.cetruo.desktop.*
import java.nio.file.Path

class CardSelectionResolver {
    fun currentVisibleIndex(
        visibleImages: List<Path>,
        currentLoadedPath: Path?,
        currentIndex: Int
    ): Int {
        val loaded = currentLoadedPath?.toAbsolutePath()?.normalize()
        if (loaded != null) {
            return visibleImages.indexOfFirst { it.toAbsolutePath().normalize() == loaded }
        }
        return currentIndex.takeIf { it in visibleImages.indices } ?: -1
    }

    fun currentPath(
        visibleImages: List<Path>,
        currentLoadedPath: Path?,
        currentIndex: Int
    ): Path? {
        currentLoadedPath?.let { return it.toAbsolutePath().normalize() }
        return visibleImages.getOrNull(currentIndex)
    }

    fun indexAfterRebuild(
        visibleImages: List<Path>,
        previousPath: Path?
    ): Int? {
        val found = previousPath?.let { visibleImages.indexOf(it) } ?: -1
        return when {
            found >= 0 -> found
            visibleImages.isNotEmpty() -> 0
            else -> null
        }
    }
}

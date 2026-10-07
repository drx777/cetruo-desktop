package de.cetruo.desktop

import java.nio.file.Path

class BrowserImageFilter(
    private val searchIndex: BrowserSearchIndex
) {
    fun filter(
        paths: Iterable<Path>,
        scope: Path,
        query: String,
        snapshot: Map<String, String>? = null,
        isCancelled: () -> Boolean = { false }
    ): List<Path> {
        val normalizedQuery = query.trim().lowercase()
        val result = ArrayList<Path>()

        for (path in paths) {
            if (isCancelled()) return emptyList()
            if (!path.startsWith(scope)) continue
            if (normalizedQuery.isBlank() || searchIndex.textFor(path, snapshot).contains(normalizedQuery)) {
                result.add(path)
            }
        }

        return result
    }
}

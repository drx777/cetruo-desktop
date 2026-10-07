package de.cetruo.desktop

import java.nio.file.Path

class BrowserImageView(
    private val filter: BrowserImageFilter,
    private val sorter: BrowserImageSorter
) {
    fun build(
        paths: Iterable<Path>,
        scope: Path,
        query: String,
        sort: BrowserSort,
        descending: Boolean,
        snapshot: Map<String, String>? = null,
        isCancelled: () -> Boolean = { false }
    ): List<Path> {
        val result = filter.filter(
            paths = paths,
            scope = scope,
            query = query,
            snapshot = snapshot,
            isCancelled = isCancelled
        ).toMutableList()

        sorter.sort(result, sort, descending)
        return result
    }
}

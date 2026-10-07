package de.cetruo.desktop

import javafx.scene.control.TreeItem
import java.nio.file.Path

data class BrowserFolderTree(
    val root: TreeItem<Path>,
    val itemsByPath: Map<Path, TreeItem<Path>>
)

class BrowserFolderTreeBuilder {
    fun expandedPaths(root: TreeItem<Path>?): Set<Path> {
        if (root == null) return emptySet()
        val expanded = mutableSetOf<Path>()

        fun collect(item: TreeItem<Path>) {
            if (item.isExpanded) expanded.add(item.value)
            item.children.forEach(::collect)
        }

        collect(root)
        return expanded
    }

    fun build(
        rootPath: Path,
        images: Collection<Path>,
        nestedCollections: Collection<Path>,
        selected: Path,
        expandedBefore: Set<Path>
    ): BrowserFolderTree {
        val rootItem = TreeItem(rootPath)
        val itemsByPath = mutableMapOf(rootPath to rootItem)
        val directories = mutableSetOf(rootPath)

        images.forEach { image ->
            addAncestors(image.parent, rootPath, directories)
        }
        nestedCollections.forEach { collection ->
            addAncestors(collection, rootPath, directories)
        }

        directories
            .filter { it != rootPath }
            .sortedBy { rootPath.relativize(it).toString().lowercase() }
            .forEach { directory ->
                val parentItem = itemsByPath[directory.parent] ?: return@forEach
                val item = TreeItem(directory)
                itemsByPath[directory] = item
                parentItem.children.add(item)
            }

        sortRecursively(rootItem)
        itemsByPath.values.forEach { item ->
            val path = item.value
            item.isExpanded = path in expandedBefore || path == rootPath || selected.startsWith(path)
        }

        return BrowserFolderTree(rootItem, itemsByPath)
    }

    private fun addAncestors(start: Path?, rootPath: Path, into: MutableSet<Path>) {
        var directory = start
        while (directory != null && directory.startsWith(rootPath) && directory != rootPath) {
            into.add(directory)
            directory = directory.parent
        }
    }

    private fun sortRecursively(item: TreeItem<Path>) {
        item.children.sortBy { it.value.fileName.toString().lowercase() }
        item.children.forEach(::sortRecursively)
    }
}

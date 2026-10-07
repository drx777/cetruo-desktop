package de.cetruo.desktop

import javafx.scene.control.TreeItem
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BrowserFolderTreeBuilderTest {
    @Test
    fun buildsSortedHierarchyFromImagesAndNestedCollections() {
        val root = Path.of("/collection")
        val alpha = root.resolve("alpha")
        val beta = root.resolve("beta")
        val nested = beta.resolve("nested")
        val builder = BrowserFolderTreeBuilder()

        val tree = builder.build(
            rootPath = root,
            images = listOf(beta.resolve("b.png"), alpha.resolve("a.png")),
            nestedCollections = listOf(nested),
            selected = alpha,
            expandedBefore = emptySet()
        )

        assertEquals(listOf(alpha, beta), tree.root.children.map { it.value })
        assertSame(tree.itemsByPath[alpha], tree.root.children[0])
        assertTrue(tree.itemsByPath[alpha]!!.isExpanded)
        assertTrue(tree.itemsByPath[root]!!.isExpanded)
        assertFalse(tree.itemsByPath[nested]!!.isExpanded)
    }

    @Test
    fun preservesPreviouslyExpandedDirectories() {
        val root = Path.of("/collection")
        val folder = root.resolve("folder")
        val builder = BrowserFolderTreeBuilder()
        val existingRoot = TreeItem(root)
        val existingFolder = TreeItem(folder).apply { isExpanded = true }
        existingRoot.children.add(existingFolder)

        val expanded = builder.expandedPaths(existingRoot)
        val rebuilt = builder.build(
            rootPath = root,
            images = listOf(folder.resolve("card.png")),
            nestedCollections = emptyList(),
            selected = root,
            expandedBefore = expanded
        )

        assertTrue(rebuilt.itemsByPath[folder]!!.isExpanded)
    }
}

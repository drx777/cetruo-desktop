package de.cetruo.desktop

import de.cetruo.desktop.browser.*
import de.cetruo.desktop.editor.*
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrowserImageFilterTest {
    private fun searchIndex(): BrowserSearchIndex =
        BrowserSearchIndex(
            relativePathFor = { it.fileName.toString() },
            cardDataFor = { path ->
                CardData(title = path.fileName.toString().substringBeforeLast('.'))
            }
        )

    @Test
    fun filtersByScopeBeforeSearchText() {
        val root = Path.of("/collection")
        val sub = root.resolve("sub")
        val other = root.resolve("other")
        val inScope = sub.resolve("Moon.png")
        val outOfScope = other.resolve("Moon.png")
        val filter = BrowserImageFilter(searchIndex())

        val result = filter.filter(listOf(inScope, outOfScope), sub, "moon")

        assertEquals(listOf(inScope), result)
    }

    @Test
    fun blankQueryReturnsAllPathsInScope() {
        val root = Path.of("/collection")
        val first = root.resolve("one.png")
        val second = root.resolve("two.png")
        val filter = BrowserImageFilter(searchIndex())

        assertEquals(listOf(first, second), filter.filter(listOf(first, second), root, ""))
    }

    @Test
    fun cancellationReturnsEmptyResult() {
        val root = Path.of("/collection")
        val filter = BrowserImageFilter(searchIndex())

        val result = filter.filter(
            paths = listOf(root.resolve("one.png")),
            scope = root,
            query = "one",
            isCancelled = { true }
        )

        assertTrue(result.isEmpty())
    }
}

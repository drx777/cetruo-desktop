package de.cetruo.desktop

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CardSelectionResolverTest {
    private val resolver = CardSelectionResolver()

    @Test
    fun loadedPathWinsOverStaleIndex() {
        val first = Path.of("/collection/a.png")
        val second = Path.of("/collection/b.png")

        assertEquals(
            1,
            resolver.currentVisibleIndex(
                visibleImages = listOf(first, second),
                currentLoadedPath = second,
                currentIndex = 0
            )
        )
        assertEquals(
            second,
            resolver.currentPath(
                visibleImages = listOf(first, second),
                currentLoadedPath = second,
                currentIndex = 0
            )
        )
    }

    @Test
    fun rebuildPreservesPathOrFallsBackToFirst() {
        val first = Path.of("/collection/a.png")
        val second = Path.of("/collection/b.png")

        assertEquals(1, resolver.indexAfterRebuild(listOf(first, second), second))
        assertEquals(0, resolver.indexAfterRebuild(listOf(first, second), Path.of("/collection/missing.png")))
        assertNull(resolver.indexAfterRebuild(emptyList(), second))
    }
}

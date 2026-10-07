package de.cetruo.desktop

import de.cetruo.desktop.browser.*
import de.cetruo.desktop.editor.*
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserSearchIndexTest {
    @Test
    fun updateStoresLowercaseSearchableCardMetadata() {
        val path = Path.of("/collection/sub/Card10.png")
        val index = BrowserSearchIndex(
            relativePathFor = { "sub/Card10.png" },
            cardDataFor = { error("not needed") }
        )
        val data = CardData(
            assetId = "ABC123",
            status = CardStatus.READY,
            title = "Moon Warden"
        )

        index.update(path, data)
        val text = index.textFor(path)

        assertTrue(text.contains("sub/card10.png"))
        assertTrue(text.contains("abc123"))
        assertTrue(text.contains("ready"))
        assertTrue(text.contains("moon warden"))
    }

    @Test
    fun snapshotReadsDoNotPopulateLiveCache() {
        val path = Path.of("/collection/Card.png")
        var calls = 0
        val index = BrowserSearchIndex(
            relativePathFor = { "Card.png" },
            cardDataFor = {
                calls++
                CardData(title = "Generated")
            }
        )

        val snapshot = index.snapshot()
        index.textFor(path, snapshot)
        index.textFor(path, snapshot)

        assertEquals(2, calls)
        assertTrue(index.snapshot().isEmpty())
    }

    @Test
    fun retainOnlyDropsEntriesForRemovedPaths() {
        val first = Path.of("/collection/one.png")
        val second = Path.of("/collection/two.png")
        val index = BrowserSearchIndex(
            relativePathFor = { it.fileName.toString() },
            cardDataFor = { CardData() }
        )
        index.update(first, CardData(title = "One"))
        index.update(second, CardData(title = "Two"))

        index.retainOnly(listOf(second))

        assertFalse(index.snapshot().containsKey("one.png"))
        assertTrue(index.snapshot().containsKey("two.png"))
    }
}

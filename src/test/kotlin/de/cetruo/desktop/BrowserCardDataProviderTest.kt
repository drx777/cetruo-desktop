package de.cetruo.desktop

import de.cetruo.desktop.browser.*
import de.cetruo.desktop.editor.*
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class BrowserCardDataProviderTest {
    @Test
    fun returnsCachedCardForSorting() {
        val path = Path.of("/collection/card.png")
        val expected = CardData(title = "Saved title")
        val provider = BrowserCardDataProvider { expected }

        assertEquals(expected, provider.forSorting(path))
    }

    @Test
    fun buildsMetadataOnlyFallback() {
        val path = Path.of("/collection/card-name.png")
        val provider = BrowserCardDataProvider { null }

        val data = provider.forSorting(path)

        assertEquals("card-name", data.title)
        assertEquals(CardStatus.NEW, data.status)
        assertEquals("", data.assetId)
    }
}

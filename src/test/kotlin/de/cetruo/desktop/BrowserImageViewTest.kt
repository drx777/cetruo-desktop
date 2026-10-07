package de.cetruo.desktop

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class BrowserImageViewTest {
    @Test
    fun filtersAndSortsInOnePass() {
        val root = Path.of("/collection")
        val alpha = root.resolve("alpha.png")
        val beta = root.resolve("nested/beta.png")
        val outside = Path.of("/other/gamma.png")

        val data = mapOf(
            alpha to CardData(title = "Zeta"),
            beta to CardData(title = "Alpha"),
            outside to CardData(title = "Gamma")
        )
        val searchIndex = BrowserSearchIndex(
            relativePathFor = { root.relativize(it).toString() },
            cardDataFor = { data.getValue(it) }
        )
        data.forEach { (path, card) -> searchIndex.update(path, card) }

        val view = BrowserImageView(
            filter = BrowserImageFilter(searchIndex),
            sorter = BrowserImageSorter(
                cardDataFor = { data.getValue(it) },
                relativePathFor = { root.relativize(it).toString() }
            )
        )

        val result = view.build(
            paths = listOf(alpha, beta, outside),
            scope = root,
            query = "",
            sort = BrowserSort.NAME,
            descending = false
        )

        assertEquals(listOf(beta, alpha), result)
    }
}

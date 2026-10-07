package de.cetruo.desktop.browser

import de.cetruo.desktop.*
import java.nio.file.Path

class BrowserCardDataProvider(
    private val cachedCardFor: (Path) -> CardData?
) {
    fun forSorting(path: Path): CardData {
        val normalized = path.toAbsolutePath().normalize()
        return runCatching { cachedCardFor(normalized) }.getOrNull() ?: lightweight(normalized)
    }

    fun lightweight(path: Path): CardData = CardData(
        assetId = "",
        status = CardStatus.NEW,
        title = path.fileName?.toString()?.substringBeforeLast('.', path.fileName.toString()) ?: "",
        cost = "",
        typeLine = "",
        rarity = "",
        description = "",
        flavorText = "",
        artist = "",
        setName = "",
        collectorNumber = "",
        stats = "",
        templateName = ""
    )
}

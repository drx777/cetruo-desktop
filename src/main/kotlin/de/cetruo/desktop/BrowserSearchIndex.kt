package de.cetruo.desktop

import java.nio.file.Path

class BrowserSearchIndex(
    private val relativePathFor: (Path) -> String,
    private val cardDataFor: (Path) -> CardData
) {
    private val entries = mutableMapOf<String, String>()

    fun clear() = entries.clear()

    fun replaceAll(values: Map<String, String>) {
        entries.clear()
        entries.putAll(values)
    }

    fun snapshot(): Map<String, String> = HashMap(entries)

    fun retainOnly(paths: Collection<Path>) {
        val live = paths.mapTo(mutableSetOf(), relativePathFor)
        entries.keys.retainAll(live)
    }

    fun remove(path: Path) {
        entries.remove(relativePathFor(path))
    }

    fun update(path: Path, data: CardData) {
        entries[relativePathFor(path)] = searchableText(path, data)
    }

    fun textFor(path: Path, snapshot: Map<String, String>? = null): String {
        val relative = relativePathFor(path)
        val source = snapshot ?: entries
        source[relative]?.let { return it }

        val searchable = searchableText(path, cardDataFor(path))
        if (snapshot == null) entries[relative] = searchable
        return searchable
    }

    internal fun searchableText(path: Path, data: CardData): String {
        val json = runCatching { JsonSupport.mapper.writeValueAsString(data) }.getOrDefault("")
        return ("${relativePathFor(path)} ${path.fileName} ${data.assetId} ${data.status.name} $json").lowercase()
    }
}

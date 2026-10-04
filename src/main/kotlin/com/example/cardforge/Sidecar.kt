package com.example.cardforge

import java.nio.file.Files
import java.nio.file.Path

/** Portable card metadata files. Sidecars are never part of normal persistence. */
object Sidecar {
    fun pathFor(image: Path): Path = image.resolveSibling("${image.fileName}.card.json")

    fun exists(image: Path): Boolean = Files.exists(pathFor(image))

    /** Returns null when the sidecar is missing or cannot be parsed. Never manufactures blank card data. */
    fun load(image: Path): CardData? = runCatching {
        val p = pathFor(image)
        if (!Files.exists(p)) null
        else JsonSupport.mapper.readValue(Files.readString(p), CardData::class.java)
    }.getOrNull()

    /** Explicit portability/share operation only. Normal saves must use CollectionDatabase. */
    fun createForSharing(image: Path, data: CardData): Path {
        val target = pathFor(image)
        Files.writeString(target, JsonSupport.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(data))
        return target
    }

    /** Remove a legacy/generated sidecar after its useful data has been imported into SQLite. */
    fun remove(image: Path): Boolean = Files.deleteIfExists(pathFor(image))
}

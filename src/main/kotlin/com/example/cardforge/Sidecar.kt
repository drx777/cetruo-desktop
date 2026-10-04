package com.example.cardforge

import java.nio.file.Files
import java.nio.file.Path

/** Portable card metadata files. Sidecars are intended for explicit sharing, not primary persistence. */
object Sidecar {
    fun pathFor(image: Path): Path = image.resolveSibling("${image.fileName}.card.json")
    fun exists(image: Path): Boolean = Files.exists(pathFor(image))

    fun load(image: Path): CardData? = runCatching {
        val p = pathFor(image)
        if (!Files.exists(p)) null else JsonSupport.mapper.readValue(Files.readString(p), CardData::class.java)
    }.getOrNull()

    fun createForSharing(image: Path, data: CardData): Path {
        val target = pathFor(image)
        Files.writeString(target, JsonSupport.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(data))
        return target
    }

    fun remove(image: Path): Boolean = Files.deleteIfExists(pathFor(image))

    /** Compatibility shim for old callers; remove once MainApp's migration is complete. */
    @Deprecated("Normal saves belong in CollectionDatabase; use createForSharing only for explicit sharing")
    fun save(image: Path, data: CardData) { createForSharing(image, data) }
}

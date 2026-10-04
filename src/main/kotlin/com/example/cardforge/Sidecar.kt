package com.example.cardforge

import java.nio.file.Files
import java.nio.file.Path

object Sidecar {
    fun pathFor(image: Path): Path = image.resolveSibling("${image.fileName}.card.json")

    fun exists(image: Path): Boolean = Files.exists(pathFor(image))

    /** Returns null when the sidecar is missing or cannot be parsed. Never manufactures blank card data. */
    fun load(image: Path): CardData? = runCatching {
        val p = pathFor(image)
        if (!Files.exists(p)) null
        else JsonSupport.mapper.readValue(Files.readString(p), CardData::class.java)
    }.getOrNull()

    fun save(image: Path, data: CardData) {
        val target = pathFor(image)
        Files.writeString(
            target,
            JsonSupport.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(data)
        )
    }
}

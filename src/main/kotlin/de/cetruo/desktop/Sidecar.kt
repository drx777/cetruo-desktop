package de.cetruo.desktop

import java.nio.file.Files
import java.nio.file.Path

/** Portable card metadata files. Sidecars are intended for explicit sharing, not primary persistence. */
object Sidecar {
    private const val SHARE_MARKER = "cardForgeShareSidecar"

    fun pathFor(image: Path): Path = image.resolveSibling("${image.fileName}.card.json")
    fun exists(image: Path): Boolean = Files.exists(pathFor(image))

    /** Reads either a legacy raw CardData sidecar or the explicit sharing envelope. */
    fun load(image: Path): CardData? = runCatching {
        val p = pathFor(image)
        if (!Files.exists(p)) return@runCatching null
        val tree = JsonSupport.mapper.readTree(Files.readString(p))
        val dataNode = if (tree.path(SHARE_MARKER).asBoolean(false)) tree.path("data") else tree
        JsonSupport.mapper.treeToValue(dataNode, CardData::class.java)
    }.getOrNull()

    fun isExplicitShare(image: Path): Boolean = runCatching {
        val p = pathFor(image)
        Files.exists(p) && JsonSupport.mapper.readTree(Files.readString(p)).path(SHARE_MARKER).asBoolean(false)
    }.getOrDefault(false)

    fun createForSharing(image: Path, data: CardData): Path {
        val target = pathFor(image)
        val envelope = JsonSupport.mapper.createObjectNode().apply {
            put(SHARE_MARKER, true)
            put("formatVersion", 1)
            set<com.fasterxml.jackson.databind.JsonNode>("data", JsonSupport.mapper.valueToTree(data))
        }
        Files.writeString(target, JsonSupport.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(envelope))
        return target
    }

    fun remove(image: Path): Boolean = Files.deleteIfExists(pathFor(image))

    /** Temporary source compatibility while Main.kt is migrated; intentionally writes nothing. */
    @Deprecated("Normal saves are SQLite-only; use createForSharing() for an explicit portable sidecar")
    fun save(@Suppress("UNUSED_PARAMETER") image: Path, @Suppress("UNUSED_PARAMETER") data: CardData) = Unit
}

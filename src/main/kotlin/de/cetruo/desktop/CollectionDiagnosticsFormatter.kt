package de.cetruo.desktop

import java.nio.file.Path

data class CollectionDiagnosticsInfo(
    val collectionRoot: Path?,
    val databasePath: Path,
    val trackedImages: Int,
    val visibleImages: Int,
    val totalImages: Int,
    val assetId: String,
    val relativePath: String?,
    val statusLabel: String,
    val revisionCount: Int,
    val templateName: String?,
    val usesCollectionDefaultTemplate: Boolean,
    val descriptionHeading: String,
    val showArtistCopyright: Boolean,
    val backgroundColor: String,
    val backgroundOverlay: String,
    val backgroundOverlayPlacement: String,
    val nestedCollectionsSkipped: Int
)

class CollectionDiagnosticsFormatter(
    private val statusLabel: (CardStatus) -> String
) {
    fun history(records: List<CollectionDatabase.HistoryRecord>): String {
        if (records.isEmpty()) return "No saved revisions yet."

        return buildString {
            records.forEach {
                appendLine("Revision ${it.revisionNumber} • ${it.savedAt} • ${statusLabel(it.status)}")
                appendLine(prettyJson(it.changesJson))
                appendLine()
            }
        }
    }

    fun databaseInfo(info: CollectionDiagnosticsInfo): String = buildString {
        appendLine("Collection: ${info.collectionRoot}")
        appendLine("Database: ${info.databasePath}")
        appendLine("Images tracked: ${info.trackedImages}")
        appendLine("Images visible: ${info.visibleImages}/${info.totalImages}")
        appendLine()
        appendLine("Current image ID: ${info.assetId.ifBlank { "not assigned" }}")
        appendLine("Relative path: ${info.relativePath ?: "-"}")
        appendLine("Status: ${info.statusLabel}")
        appendLine("Saved revisions: ${info.revisionCount}")
        appendLine("Template: ${info.templateName ?: "-"} ${if (info.usesCollectionDefaultTemplate) "(collection default)" else "(card override)"}")
        appendLine("Description label: ${info.descriptionHeading}")
        appendLine("Show artist ©: ${info.showArtistCopyright}")
        appendLine("Background color: ${info.backgroundColor}")
        appendLine("Background overlay: ${info.backgroundOverlay.ifBlank { "None" }} (${info.backgroundOverlayPlacement})")
        appendLine()
        appendLine("Nested collection directories skipped: ${info.nestedCollectionsSkipped}")
        appendLine("Nested directories containing their own .cetruo.sqlite (or legacy .cardforge.sqlite) are treated as separate collections.")
    }

    private fun prettyJson(value: String): String = runCatching {
        JsonSupport.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(JsonSupport.mapper.readTree(value))
    }.getOrDefault(value)
}

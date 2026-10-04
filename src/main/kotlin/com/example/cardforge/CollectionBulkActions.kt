package com.example.cardforge

import java.nio.file.Path

/**
 * Collection-wide mutations live here instead of in MainApp.
 *
 * The caller owns UI refresh/cache invalidation. This service only performs model/database
 * changes, which keeps bulk operations deterministic and independently testable.
 */
object CollectionBulkActions {
    data class Result(
        val changedCards: Int,
        val affectedSets: Set<String> = emptySet()
    )

    /** Clear every per-card template override so all cards follow the collection default. */
    fun applyCollectionDefaultTemplate(
        images: Collection<Path>,
        database: CollectionDatabase
    ): Result {
        var changed = 0
        for (image in images) {
            val data = database.dataSnapshotForPath(image) ?: continue
            if (data.templateName.isBlank()) continue
            data.templateName = ""
            if (database.save(image, data).changed) changed++
        }
        return Result(changed)
    }

    /** Apply one set name to every image/card and normalize collector denominators. */
    fun applySetName(
        images: Collection<Path>,
        database: CollectionDatabase,
        setName: String,
        initialize: (Path) -> CardData
    ): Result {
        val normalizedSet = setName.trim()
        val cards = images.map { image ->
            image to (database.dataSnapshotForPath(image) ?: initialize(image))
        }
        val oldSets = cards.map { it.second.setName.trim() }.filter { it.isNotBlank() }.toSet()
        val total = cards.size
        var changed = 0

        cards.forEachIndexed { index, (image, data) ->
            data.setName = normalizedSet
            data.collectorNumber = CollectorNumbers.withTotal(
                data.collectorNumber,
                total,
                fallbackNumber = index + 1
            )
            if (database.save(image, data).changed) changed++
        }
        return Result(changed, oldSets + normalizedSet)
    }

    /**
     * Recalculate /total for each persisted card from the number of cards with the same set
     * name. Existing numeric prefixes are retained. Missing/invalid prefixes get a stable
     * position within that set for this pass.
     */
    fun normalizeCollectorTotals(
        images: Collection<Path>,
        database: CollectionDatabase,
        onlySets: Set<String>? = null
    ): Result {
        val cards = images.mapNotNull { image ->
            database.dataSnapshotForPath(image)?.let { image to it }
        }
        val groups = cards.groupBy { it.second.setName.trim() }
        var changed = 0
        val affected = linkedSetOf<String>()

        for ((setName, members) in groups) {
            if (onlySets != null && setName !in onlySets) continue
            affected += setName
            val total = members.size
            members.forEachIndexed { index, (image, data) ->
                val normalized = CollectorNumbers.withTotal(
                    data.collectorNumber,
                    total,
                    fallbackNumber = index + 1
                )
                if (normalized != data.collectorNumber) {
                    data.collectorNumber = normalized
                    if (database.save(image, data).changed) changed++
                }
            }
        }
        return Result(changed, affected)
    }
}

object CollectorNumbers {
    private val leadingNumber = Regex("^\\s*(\\d+)")

    fun withTotal(value: String, total: Int, fallbackNumber: Int): String {
        val safeTotal = total.coerceAtLeast(1)
        val parsed = leadingNumber.find(value)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val number = (parsed ?: fallbackNumber).coerceAtLeast(1)
        val width = maxOf(3, safeTotal.toString().length, number.toString().length)
        return number.toString().padStart(width, '0') + "/" + safeTotal.toString().padStart(width, '0')
    }
}

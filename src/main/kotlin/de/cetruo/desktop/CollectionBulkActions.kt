package de.cetruo.desktop

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

    /**
     * Apply one template explicitly to every image/card in the collection.
     *
     * The selected template is also stored as the collection default so newly added cards start
     * from the same layout. Existing cards receive an explicit templateName; we do not merely
     * clear overrides and rely on default resolution because the user's action is "apply to all".
     */
    fun applyTemplateToAll(
        images: Collection<Path>,
        database: CollectionDatabase,
        templateName: String,
        initialize: (Path) -> CardData
    ): Result {
        val normalizedTemplate = templateName.trim()
        require(normalizedTemplate.isNotBlank()) { "Template name must not be blank" }

        database.setDefaultTemplateName(normalizedTemplate)

        val targets = images.toList()
        var changed = 0
        for (image in targets) {
            val data = database.dataSnapshotForPath(image) ?: initialize(image)
            data.templateName = normalizedTemplate
            if (database.save(image, data).changed) changed++
        }

        val mismatches = targets.count { image ->
            database.dataSnapshotForPath(image)?.templateName != normalizedTemplate
        }
        check(mismatches == 0) {
            "Template bulk apply incomplete: $mismatches card(s) did not persist '$normalizedTemplate'"
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
        val orderedImages = images.toList()
        val total = orderedImages.size
        val oldSets = linkedSetOf<String>()
        val usedNumbers = linkedSetOf<Int>()
        var changed = 0

        orderedImages.forEachIndexed { index, image ->
            val data = database.dataSnapshotForPath(image) ?: initialize(image)
            data.setName.trim().takeIf { it.isNotBlank() }?.let(oldSets::add)
            data.setName = normalizedSet
            data.collectorNumber = CollectorNumbers.withUniqueTotal(
                value = data.collectorNumber,
                total = total,
                usedNumbers = usedNumbers,
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
            val usedNumbers = linkedSetOf<Int>()
            members.forEachIndexed { index, (image, data) ->
                val normalized = CollectorNumbers.withUniqueTotal(
                    value = data.collectorNumber,
                    total = total,
                    usedNumbers = usedNumbers,
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

    fun withUniqueTotal(
        value: String,
        total: Int,
        usedNumbers: MutableSet<Int>,
        fallbackNumber: Int
    ): String {
        val safeTotal = total.coerceAtLeast(1)
        val parsed = leadingNumber.find(value)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val preferred = parsed?.takeIf { it > 0 && it !in usedNumbers }
        val fallback = generateSequence(fallbackNumber.coerceAtLeast(1)) { it + 1 }
            .firstOrNull { it !in usedNumbers }
            ?: 1
        val number = preferred ?: fallback
        usedNumbers += number
        val width = maxOf(3, safeTotal.toString().length, number.toString().length)
        return number.toString().padStart(width, '0') + "/" + safeTotal.toString().padStart(width, '0')
    }

}

package de.cetruo.desktop

import java.nio.file.Path

data class CardSavePreparation(
    val previous: CardData?,
    val duplicateCollectorNumber: String?,
    val wouldClearManyFields: Boolean,
    val previousSet: String,
    val nextSet: String
)

data class CardSaveOutcome(
    val result: CollectionDatabase.SaveResult,
    val normalizedSetTotals: Boolean,
    val persistedCollectorNumber: String?
)

class CardPersistenceService {

    fun resolveForSelection(
        database: CollectionDatabase?,
        cardStore: CollectionCardStore,
        path: Path,
        newCard: (Path) -> CardData,
        nextCollectorNumber: () -> String
    ): CardData {
        val normalized = path.toAbsolutePath().normalize()
        if (database == null) {
            return cardStore.snapshot(normalized)?.copy() ?: newCard(normalized)
        }

        val databaseData = database.dataSnapshotForPath(normalized)
        if (databaseData != null && hasMeaningfulCardData(databaseData)) {
            if (databaseData.collectorNumber.isBlank()) {
                databaseData.collectorNumber = nextCollectorNumber()
                database.save(normalized, databaseData)
            }
            return databaseData
        }

        cardStore.cached(normalized)?.let { cached ->
            return cached.copy().also { data ->
                databaseData?.assetId?.takeIf { it.isNotBlank() }?.let { data.assetId = it }
            }
        }

        return newCard(normalized).also { defaults ->
            databaseData?.assetId?.takeIf { it.isNotBlank() }?.let { defaults.assetId = it }
        }
    }

    fun prepare(
        database: CollectionDatabase,
        path: Path,
        data: CardData
    ): CardSavePreparation {
        val previous = data.assetId
            .takeIf { it.isNotBlank() }
            ?.let(database::dataSnapshot)
            ?: database.dataSnapshotForPath(path)

        val collectorNumber = data.collectorNumber.trim()
        val duplicate = collectorNumber
            .takeIf { it.isNotBlank() }
            ?.takeIf { database.usedCollectorNumbers(data.assetId).contains(it) }

        return CardSavePreparation(
            previous = previous,
            duplicateCollectorNumber = duplicate,
            wouldClearManyFields = previous?.let { wouldClearCardAccidentally(it, data) } == true,
            previousSet = previous?.setName?.trim().orEmpty(),
            nextSet = data.setName.trim()
        )
    }

    fun save(
        database: CollectionDatabase,
        path: Path,
        data: CardData,
        allImages: List<Path>,
        preparation: CardSavePreparation
    ): CardSaveOutcome {
        val shouldNormalizeSetTotals =
            preparation.previous == null || preparation.previousSet != preparation.nextSet

        val result = database.save(path, data)

        if (!shouldNormalizeSetTotals) {
            return CardSaveOutcome(
                result = result,
                normalizedSetTotals = false,
                persistedCollectorNumber = null
            )
        }

        val affectedSets = linkedSetOf<String>().apply {
            preparation.previousSet.takeIf { it.isNotBlank() }?.let(::add)
            preparation.nextSet.takeIf { it.isNotBlank() }?.let(::add)
        }

        if (affectedSets.isNotEmpty()) {
            CollectionBulkActions.normalizeCollectorTotals(allImages, database, affectedSets)
        }

        return CardSaveOutcome(
            result = result,
            normalizedSetTotals = affectedSets.isNotEmpty(),
            persistedCollectorNumber = database.dataSnapshotForPath(path)?.collectorNumber
        )
    }

    companion object {
        fun hasMeaningfulCardData(data: CardData): Boolean = listOf(
            data.title,
            data.cost,
            data.typeLine,
            data.rarity,
            data.description,
            data.flavorText,
            data.artist,
            data.setName,
            data.collectorNumber,
            data.stats
        ).count { it.isNotBlank() } >= 3

        internal fun wouldClearCardAccidentally(before: CardData, after: CardData): Boolean {
            val beforeValues = listOf(
                before.title,
                before.cost,
                before.typeLine,
                before.rarity,
                before.stats,
                before.artist,
                before.setName,
                before.collectorNumber,
                before.description,
                before.flavorText
            )
            val afterValues = listOf(
                after.title,
                after.cost,
                after.typeLine,
                after.rarity,
                after.stats,
                after.artist,
                after.setName,
                after.collectorNumber,
                after.description,
                after.flavorText
            )
            val meaningfulBefore = beforeValues.count { it.isNotBlank() }
            val blanked = beforeValues.zip(afterValues).count { (old, new) ->
                old.isNotBlank() && new.isBlank()
            }
            return meaningfulBefore >= 5 && blanked >= 5 && blanked >= meaningfulBefore * 0.5
        }
    }
}

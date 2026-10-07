package de.cetruo.desktop

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CollectionBulkActionsTest {
    @Test
    fun applyTemplateToAllPersistsExplicitTemplateAndCollectionDefault() {
        val root = createTempDirectory("cardforge-bulk-template")
        val initialized = root.resolve("initialized.png")
        val uninitialized = root.resolve("uninitialized.png")
        Files.write(initialized, byteArrayOf(1, 2, 3))
        Files.write(uninitialized, byteArrayOf(4, 5, 6))

        CollectionDatabase.open(root).use { db ->
            val existing = CardData(
                title = "Existing title",
                artist = "Existing artist",
                templateName = "Old Template"
            )
            db.save(initialized, existing)

            var initializedByBulkAction = 0
            val result = CollectionBulkActions.applyTemplateToAll(
                images = listOf(initialized, uninitialized),
                database = db,
                templateName = "  New Template  "
            ) { path ->
                initializedByBulkAction++
                CardData(
                    title = path.fileName.toString().substringBeforeLast('.'),
                    artist = "Generated artist"
                )
            }

            assertEquals(2, result.changedCards)
            assertEquals(1, initializedByBulkAction)
            assertEquals("New Template", db.getDefaultTemplateName())

            val persistedExisting = db.dataSnapshotForPath(initialized)!!
            assertEquals("New Template", persistedExisting.templateName)
            assertEquals("Existing title", persistedExisting.title)
            assertEquals("Existing artist", persistedExisting.artist)

            val persistedInitialized = db.dataSnapshotForPath(uninitialized)!!
            assertEquals("New Template", persistedInitialized.templateName)
            assertEquals("uninitialized", persistedInitialized.title)
            assertEquals("Generated artist", persistedInitialized.artist)
        }
    }

    @Test
    fun reapplyingSameTemplateIsIdempotentButKeepsExplicitValues() {
        val root = createTempDirectory("cardforge-bulk-template")
        val first = root.resolve("first.png")
        val second = root.resolve("second.png")
        Files.write(first, byteArrayOf(7, 8, 9))
        Files.write(second, byteArrayOf(10, 11, 12))

        CollectionDatabase.open(root).use { db ->
            listOf(first, second).forEachIndexed { index, path ->
                db.save(path, CardData(title = "Card ${index + 1}", templateName = "Target"))
            }
            db.setDefaultTemplateName("Different default")

            var initializeCalls = 0
            val result = CollectionBulkActions.applyTemplateToAll(
                images = listOf(first, second),
                database = db,
                templateName = "Target"
            ) {
                initializeCalls++
                CardData(title = "Should not initialize")
            }

            assertEquals(0, result.changedCards)
            assertEquals(0, initializeCalls)
            assertEquals("Target", db.getDefaultTemplateName())
            assertEquals("Target", db.dataSnapshotForPath(first)?.templateName)
            assertEquals("Target", db.dataSnapshotForPath(second)?.templateName)
        }
    }

    @Test
    fun blankTemplateNameIsRejectedBeforeChangingCollectionDefault() {
        val root = createTempDirectory("cardforge-bulk-template")
        val image = root.resolve("card.png")
        Files.write(image, byteArrayOf(13, 14, 15))

        CollectionDatabase.open(root).use { db ->
            db.setDefaultTemplateName("Original")

            assertFailsWith<IllegalArgumentException> {
                CollectionBulkActions.applyTemplateToAll(
                    images = listOf(image),
                    database = db,
                    templateName = "   "
                ) { CardData(title = "Card") }
            }

            assertEquals("Original", db.getDefaultTemplateName())
            assertEquals(null, db.dataSnapshotForPath(image))
        }
    }
    @Test
    fun applySetNameToAllInitializesMissingCardsPreservesMetadataAndNormalizesCollectorNumbers() {
        val root = createTempDirectory("cardforge-bulk-set")
        val first = root.resolve("first.png")
        val second = root.resolve("second.png")
        val third = root.resolve("third.png")
        Files.write(first, byteArrayOf(1))
        Files.write(second, byteArrayOf(2))
        Files.write(third, byteArrayOf(3))

        CollectionDatabase.open(root).use { db ->
            db.save(
                first,
                CardData(
                    title = "First",
                    artist = "Artist A",
                    setName = "Old Set",
                    collectorNumber = "007/099"
                )
            )
            db.save(
                second,
                CardData(
                    title = "Second",
                    artist = "Artist B",
                    setName = "Another Set",
                    collectorNumber = "007/099"
                )
            )

            var initializeCalls = 0
            val result = CollectionBulkActions.applySetName(
                images = listOf(first, second, third),
                database = db,
                setName = "  Unified Set  "
            ) { path ->
                initializeCalls++
                CardData(
                    title = path.fileName.toString().substringBeforeLast('.'),
                    artist = "Generated",
                    setName = "",
                    collectorNumber = ""
                )
            }

            assertEquals(3, result.changedCards)
            assertEquals(1, initializeCalls)
            assertEquals(setOf("Old Set", "Another Set", "Unified Set"), result.affectedSets)

            val firstData = db.dataSnapshotForPath(first)!!
            val secondData = db.dataSnapshotForPath(second)!!
            val thirdData = db.dataSnapshotForPath(third)!!

            assertEquals("Unified Set", firstData.setName)
            assertEquals("Unified Set", secondData.setName)
            assertEquals("Unified Set", thirdData.setName)

            assertEquals("007/003", firstData.collectorNumber)
            assertEquals("002/003", secondData.collectorNumber)
            assertEquals("003/003", thirdData.collectorNumber)

            assertEquals("First", firstData.title)
            assertEquals("Artist A", firstData.artist)
            assertEquals("Second", secondData.title)
            assertEquals("Artist B", secondData.artist)
            assertEquals("third", thirdData.title)
            assertEquals("Generated", thirdData.artist)
        }
    }

    @Test
    fun reapplyingSameSetNameIsIdempotentWhenCollectorNumbersAlreadyNormalized() {
        val root = createTempDirectory("cardforge-bulk-set")
        val first = root.resolve("first.png")
        val second = root.resolve("second.png")
        Files.write(first, byteArrayOf(4))
        Files.write(second, byteArrayOf(5))

        CollectionDatabase.open(root).use { db ->
            db.save(first, CardData(title = "First", setName = "Set", collectorNumber = "001/002"))
            db.save(second, CardData(title = "Second", setName = "Set", collectorNumber = "002/002"))

            var initializeCalls = 0
            val result = CollectionBulkActions.applySetName(
                images = listOf(first, second),
                database = db,
                setName = "Set"
            ) {
                initializeCalls++
                CardData(title = "Unexpected")
            }

            assertEquals(0, result.changedCards)
            assertEquals(0, initializeCalls)
            assertEquals(setOf("Set"), result.affectedSets)
            assertEquals("001/002", db.dataSnapshotForPath(first)?.collectorNumber)
            assertEquals("002/002", db.dataSnapshotForPath(second)?.collectorNumber)
        }
    }

}

package com.example.cardforge

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
}

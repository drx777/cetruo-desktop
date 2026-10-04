package com.example.cardforge

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileIdentityReconcilerTest {
    @Test
    fun scanCycleReattachesRenamedFileByContentHash() {
        val root = createTempDirectory("cardforge-watcher-identity")
        val original = root.resolve("original.png")
        Files.write(original, byteArrayOf(1, 2, 3, 4))

        CollectionDatabase.open(root).use { db ->
            val data = CardData(title = "Preserved", artist = "Artist")
            db.save(original, data)
            val assetId = data.assetId

            Files.delete(original)
            val renamed = root.resolve("renamed.png")
            Files.write(renamed, byteArrayOf(1, 2, 3, 4))

            val result = FileIdentityReconciler.reconcile(
                previousPaths = listOf(original),
                nextPaths = listOf(renamed),
                database = db
            )

            assertEquals(setOf(original.toAbsolutePath().normalize()), result.removed)
            assertEquals(setOf(renamed.toAbsolutePath().normalize()), result.added)
            assertEquals(assetId, result.reattachedAssetIds[renamed.toAbsolutePath().normalize()])
            assertEquals(assetId, db.assetIdForPath(renamed))
            assertEquals("Preserved", db.dataSnapshotForPath(renamed)?.title)
            assertEquals("Artist", db.dataSnapshotForPath(renamed)?.artist)
        }
    }

    @Test
    fun scanCycleDoesNotTransferMetadataToUnrelatedReplacement() {
        val root = createTempDirectory("cardforge-watcher-identity")
        val original = root.resolve("card.png")
        Files.write(original, byteArrayOf(5, 6, 7, 8))

        CollectionDatabase.open(root).use { db ->
            val old = CardData(title = "Old metadata")
            db.save(original, old)
            val oldAssetId = old.assetId

            Files.delete(original)
            Files.write(original, byteArrayOf(9, 9, 9, 9))

            // Same path in the scan means no add/remove event by path alone; this helper
            // intentionally does nothing in that case. Content changes are handled by
            // invalidateImageCaches rather than identity replacement.
            val unchangedPathResult = FileIdentityReconciler.reconcile(
                previousPaths = listOf(original),
                nextPaths = listOf(original),
                database = db
            )
            assertTrue(unchangedPathResult.removed.isEmpty())
            assertTrue(unchangedPathResult.added.isEmpty())
            assertEquals(oldAssetId, db.assetIdForPath(original))

            // Model the actual replacement case observed by the watcher: the old path
            // disappears from one scan, then a new unrelated file appears at that path.
            val removal = FileIdentityReconciler.reconcile(
                previousPaths = listOf(original),
                nextPaths = emptyList(),
                database = db
            )
            assertEquals(setOf(original.toAbsolutePath().normalize()), removal.removed)

            val addition = FileIdentityReconciler.reconcile(
                previousPaths = emptyList(),
                nextPaths = listOf(original),
                database = db
            )
            assertNull(addition.reattachedAssetIds[original.toAbsolutePath().normalize()])

            val replacement = CardData(title = "Replacement")
            db.save(original, replacement)
            assertNotEquals(oldAssetId, replacement.assetId)
            assertEquals("Replacement", db.dataSnapshotForPath(original)?.title)
        }
    }

    @Test
    fun nullDatabaseStillReportsNormalizedDiff() {
        val root = createTempDirectory("cardforge-watcher-identity")
        val previous = root.resolve("old.png")
        val next = root.resolve("new.png")

        val result = FileIdentityReconciler.reconcile(
            previousPaths = listOf(previous),
            nextPaths = listOf(next),
            database = null
        )

        assertEquals(setOf(previous.toAbsolutePath().normalize()), result.removed)
        assertEquals(setOf(next.toAbsolutePath().normalize()), result.added)
        assertTrue(result.reattachedAssetIds.isEmpty())
    }
}
